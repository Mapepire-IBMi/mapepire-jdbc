# Mapepire JDBC

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mapepire-ibmi/mapepire-jdbc.svg?label=Maven%20Central&logo=apachemaven)](https://central.sonatype.com/artifact/io.github.mapepire-ibmi/mapepire-jdbc/)
[![Maven Build](https://github.com/Mapepire-IBMi/mapepire-jdbc/actions/workflows/build.yml/badge.svg)](https://github.com/Mapepire-IBMi/mapepire-jdbc/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/Mapepire-IBMi/mapepire-jdbc.svg?color=blue)](https://github.com/Mapepire-IBMi/mapepire-jdbc/blob/main/LICENSE)

## Overview

Mapepire JDBC driver for communicating with Db2 on IBM i. It talks to the
[`mapepire-server`](https://github.com/Mapepire-IBMi/mapepire-server) daemon over a
TLS-secured connection, so no ODBC/native driver or IBM i Access Client Solutions
install is required on the client machine — just this JDBC driver.

Full Documentation: https://mapepire-ibmi.github.io

## Setup

### Requirements

* Java 8 or later
* A running [`mapepire-server`](#server-component-setup) daemon on the target IBM i

### Install with `maven`

Add the dependency to your `pom.xml`:

```xml
<dependency>
  <groupId>io.github.mapepire-ibmi</groupId>
  <artifactId>mapepire-jdbc</artifactId>
  <version>2.0.0</version>
</dependency>
```

Check [Maven Central](https://central.sonatype.com/artifact/io.github.mapepire-ibmi/mapepire-jdbc/) for the latest released version.

### Server Component Setup

In order for applications to use Db2 for i through this JDBC driver, the `mapepire-server` daemon must be installed and started-up on each IBM i. Follow the instructions [here](https://mapepire-ibmi.github.io/guides/sysadmin/) to learn about the installation and startup process of the server component.

## Getting Started

### Connection String

The following is the database connection URL syntax:

```
jdbc:mapepire://[host][:port][;propertyName1][=propertyValue1][;propertyName2][=propertyValue2]...
```

* `host` (*required*): The hostname or IP address of the IBM i.
* `port` (*optional*): The port number where the `mapepire-server` is running. If not specified, the default port is `8076`.
* `propertyName=propertyValue` (*optional*): Represents an ampersand-separated list of properties.

### Connection Properties

The following connection properties are supported:

* `USER` (*required*): The IBM i user ID.
* `PASSWORD` (*required*): The IBM i user password.
* `REJECTUNAUTHORIZED` (*optional*, default `true`): Whether to verify the `mapepire-server` daemon's TLS certificate. Only set this to `false` for local development against a server with a self-signed certificate — leaving verification enabled is required to protect credentials in transit on any network you don't fully control.
* Any [JDBC property](https://www.ibm.com/docs/en/i/7.4?topic=jdbc-toolbox-java-properties) (*optional*)

### Example Connections

1. Using `Properties` object:

```java
Properties p = new Properties();
p.put("USER", "myuser");
p.put("PASSWORD", "mypassword");
p.put("naming", "system");
p.put("errors", "full");

Connection connection = DriverManager.getConnection("jdbc:mapepire://myhost.example.com:8076", p);
```

2. Using connection string:

```java
Connection connection = DriverManager.getConnection("jdbc:mapepire://myhost.example.com:8076;USER=myuser;PASSWORD=mypassword;naming=system;errors=full");
```

## Examples

### Running a query

```java
Connection connection = DriverManager.getConnection(
        "jdbc:mapepire://myhost.example.com:8076;USER=myuser;PASSWORD=mypassword");

try (Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("SELECT * FROM SAMPLE.DEPARTMENT")) {
    while (rs.next()) {
        System.out.println(rs.getString("DEPTNO") + ": " + rs.getString("DEPTNAME"));
    }
}
```

### Running a parameterized query

Use a `PreparedStatement` to safely pass user-supplied values without building SQL strings by hand:

```java
try (PreparedStatement statement = connection.prepareStatement(
        "SELECT * FROM SAMPLE.EMPLOYEE WHERE WORKDEPT = ?")) {
    statement.setString(1, "A00");

    try (ResultSet rs = statement.executeQuery()) {
        while (rs.next()) {
            System.out.println(rs.getString("LASTNAME"));
        }
    }
}
```

### Running an update

```java
try (Statement statement = connection.createStatement()) {
    int updated = statement.executeUpdate("UPDATE SAMPLE.EMPLOYEE SET SALARY = SALARY * 1.05 WHERE WORKDEPT = 'A00'");
    System.out.println(updated + " rows updated");
}
```

### Transactions

Auto-commit is on by default, same as the JDBC spec. Turn it off to group statements into a
single transaction:

```java
connection.setAutoCommit(false);
try (Statement statement = connection.createStatement()) {
    statement.executeUpdate("UPDATE SAMPLE.EMPLOYEE SET SALARY = SALARY * 1.05 WHERE WORKDEPT = 'A00'");
    statement.executeUpdate("INSERT INTO SAMPLE.AUDIT_LOG (MESSAGE) VALUES ('Applied raise for A00')");
    connection.commit();
} catch (SQLException e) {
    connection.rollback();
    throw e;
}
```

### Read-only connections

Read-only mode is chosen when connecting, with the `access` property, and the server enforces it
for the whole connection:

```java
Connection connection = DriverManager.getConnection(
        "jdbc:mapepire://myibmi.example.com;access=read only", user, password);
```

* `read only` allows only queries (`SELECT`, `WITH`, `VALUES`). `read call` also allows `CALL`,
  which CL commands need. `all` (the default) allows everything.
* `isReadOnly()` reports the mode. `setReadOnly` cannot change it on an open connection: it throws
  unless the value matches. For a HikariCP pool with `readOnly=true`, add `access=read only` too.
* On a read-only connection `commit` and `rollback` do nothing, and `SET` statements fail, including
  `setSchema` and `setTransactionIsolation`.

### Scrollable and updatable result sets

Pass a result set type and concurrency when creating the statement:

```java
try (Statement statement = connection.createStatement(
        ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_UPDATABLE);
        ResultSet rs = statement.executeQuery("SELECT EMPNO, SALARY FROM SAMPLE.EMPLOYEE")) {
    rs.last();                       // move to any row: first, last, absolute, relative, previous
    rs.updateBigDecimal("SALARY", new BigDecimal("52000"));
    rs.updateRow();                  // or deleteRow(), or moveToInsertRow() + insertRow()
}
```

* A `TYPE_SCROLL_INSENSITIVE` result set fetches every row when it is created and keeps them in
  memory, so it does not see changes others make afterwards.
* A `CONCUR_UPDATABLE` result set writes each change as a separate `UPDATE`, `DELETE`, or `INSERT`
  that finds the row by its primary key. The query must select from a single table that has a
  primary key, and must select every primary key column. If the table name is unqualified, its
  primary key is looked up in the current schema, so qualify it when using system naming or the
  library list.

### Binary and large object values

Read `BINARY`, `VARBINARY`, `FOR BIT DATA` and `BLOB` columns with `getBytes`, `getBinaryStream`
or `getBlob`, and set them with `setBytes`, `setBinaryStream` or `setBlob`. `CLOB`, `DBCLOB` and
`NCLOB` columns are text: use `getString`, `getCharacterStream` or `getClob`, and `setString`,
`setCharacterStream` or `setClob`.

```java
try (PreparedStatement insert = connection.prepareStatement(
        "INSERT INTO MYLIB.DOCUMENTS (ID, CONTENT, NOTES) VALUES (?, ?, ?)")) {
    insert.setInt(1, 1);
    insert.setBinaryStream(2, Files.newInputStream(Paths.get("report.pdf")));
    insert.setString(3, "Quarterly report");
    insert.executeUpdate();
}

try (PreparedStatement select = connection.prepareStatement(
        "SELECT CONTENT, NOTES FROM MYLIB.DOCUMENTS WHERE ID = ?")) {
    select.setInt(1, 1);
    try (ResultSet rs = select.executeQuery()) {
        while (rs.next()) {
            byte[] content = rs.getBytes("CONTENT");
            Clob notes = rs.getClob("NOTES");
        }
    }
}
```

* Each value is sent and received whole, so the driver holds it in memory. A stream you set is
  read to its end before the statement runs. A `Blob` or `Clob` you get is a copy, so changing it
  does not change the database until you write it back.
* `mapepire-server` 2.4.0 and later return binary values as links, which the driver downloads over
  HTTPS from the server's port using the connection's credentials. The server keeps each value for
  `BLOB_TOKEN_TTL` seconds (60 by default) after returning its row, and serves it only once, so
  read binary columns soon after fetching their rows. Once downloaded, a value can be read again.
* Older servers send and receive binary values as hex. To choose the format, the driver asks the
  server for its version the first time a statement on the connection has a binary parameter.
* `getObject` returns `byte[]` for `BINARY` and `VARBINARY` columns, `Blob` for `BLOB`, and `Clob`
  for `CLOB` and `DBCLOB` (`NClob` for `NCLOB`), the classes `ResultSetMetaData.getColumnClassName`
  reports. `getString` on a binary column returns hex, as with JTOpen.

### Running a CL command

CL commands are a driver extension, so unwrap the connection to `MapepireConnection` first:

```java
MapepireConnection mapepire = connection.unwrap(MapepireConnection.class);
CLCommandResult result = mapepire.rcclCommand("CRTLIB LIB(MYLIB)");
if (!result.getSuccess()) {
    // The most recent escape message, e.g.
    // "CL command execution failed: [CPF2111] Library MYLIB already exists."
    System.err.println(result.getError());
}
for (CLMessage message : result.getData()) {
    System.out.println(message.getId() + ": " + message.getText());
}
```

There are two ways to run a command:

| Method        | Runs in                                               | `getData()` contains                   |
|---------------|-------------------------------------------------------|----------------------------------------|
| `clCommand`   | The SQL job, through `QSYS2.QCMDEXC`                  | Job log entries written by the command |
| `rcclCommand` | A separate remote command job (Toolbox `CommandCall`) | Messages returned by the command       |

Use `clCommand` when the command must affect the SQL job itself, such as `CHGJOB` or `ADDLIBLE`.
With a connection pool, those changes stay on the connection for whoever borrows it next.
`rcclCommand` requires `mapepire-server` 2.5.0 or later.

A failed command does not throw: check `getSuccess()`, and `getData()` still holds the messages.
An `SQLException` is thrown only when the request itself fails, for example on a closed connection
or a timeout.

## Supported JDBC API

This driver targets the core of the JDBC API needed to run queries, updates, and transactions
against Db2 for i — it is not yet a complete `java.sql` implementation. If your use case needs
something from the "not yet supported" list below, please
[open an issue](https://github.com/Mapepire-IBMi/mapepire-jdbc/issues).

**Supported**

* `Statement` and `PreparedStatement`: `executeQuery`, `executeUpdate`, `execute`, parameter
  binding, fetch size, query timeout
* `ResultSet` reading by column index or label, forward-only or scrollable (`TYPE_SCROLL_INSENSITIVE`)
* Updatable result sets (`CONCUR_UPDATABLE`): `updateXxx`, `updateRow`, `deleteRow`, `insertRow`
* Binary and LOB values: `getBytes` / `setBytes`, binary, character and ASCII streams, `Blob`,
  `Clob` and `NClob`, and `Connection.createBlob` / `createClob` / `createNClob` (see
  [Binary and large object values](#binary-and-large-object-values))
* Transactions: `setAutoCommit`, `commit`, `rollback`, `setTransactionIsolation`, `setReadOnly`, `isReadOnly`
* `setSchema` / `getSchema`
* `Connection.isValid()` for pool health checks
* Batch execution (`addBatch` / `executeBatch` / `executeLargeBatch`). A `PreparedStatement` batch
  is sent to the server up to 1000 rows per request, and each row's update count is
  `SUCCESS_NO_INFO` because the server reports only a total. A `Statement` batch runs each SQL
  statement in its own request. Execution stops at the first failure with a
  `BatchUpdateException`
* Query and network timeouts
* `getWarnings` / `clearWarnings` (the driver never raises SQL warnings, so these always return `null`)
* `DatabaseMetaData`: tables, columns, schemas, primary/foreign keys, indexes, procedures, functions,
  type info, UDTs, and privileges, read from the Db2 for i `SYSIBM` catalog views, plus product,
  version, and capability information
* `ResultSetMetaData` from `ResultSet.getMetaData()`, and from `PreparedStatement.getMetaData()` once
  the statement has been executed (it returns `null` before execution)
* CL commands via `MapepireConnection.clCommand` / `rcclCommand` (driver extension, see
  [Running a CL command](#running-a-cl-command))

**Not yet supported** (these throw `SQLFeatureNotSupportedException`)

* `TYPE_SCROLL_SENSITIVE` result sets and `refreshRow`
* `CallableStatement`, savepoints, generated keys
* Array / Ref / RowId / SQLXML types, and the deprecated `getUnicodeStream` / `setUnicodeStream`

## Contributing

Bug reports, feature requests, and pull requests are welcome — see
[CONTRIBUTING.md](CONTRIBUTING.md) for the development workflow and
[CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) for community guidelines. Release notes for every
version are tracked in [CHANGELOG.md](CHANGELOG.md).

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
