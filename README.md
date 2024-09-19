# Mapepire JDBC

[![Maven Central](https://img.shields.io/maven-central/v/io.github.mapepire-ibmi/mapepire-jdbc.svg?label=Maven%20Central&logo=apachemaven)](https://central.sonatype.com/artifact/io.github.mapepire-ibmi/mapepire-jdbc/)
[![Maven Build](https://github.com/Mapepire-IBMi/mapepire-jdbc/actions/workflows/build.yml/badge.svg)](https://github.com/Mapepire-IBMi/mapepire-jdbc/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/allenai/tango.svg?color=blue&cachedrop)](https://github.com/Mapepire-IBMi/mapepire-jdbc/blob/main/LICENSE)

## Overview

Mapepire JDBC driver for communicating with Db2 on IBM i.

> [!WARNING]
> ⚠️ This project is still work in progress!

Full Documentation: https://mapepire-ibmi.github.io

## Setup

### Requirements

* Java 8 or later

### Install with `maven`

> [!WARNING]
> ⚠️ To be added

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
* Any [JDBC property](https://www.ibm.com/docs/en/i/7.4?topic=jdbc-toolbox-java-properties) (*optional*)

### Example Connections

1. Using `Properties` object:

```java
Properties p = new Properties();
p.put("USER", "myuser");
p.put("PASSWORD", "mypassword");
p.put("naming", "system");
p.put("errors", "full");

Connection connection = DriverManager.getConnection("jdbc:mapepire://ossbuild.rzke.de:8076", p);
```

2. Using connection string:

```java
Connection connection = DriverManager.getConnection("jdbc:mapepire://ossbuild.rzke.de:8076;USER=myuser;PASSWORD=mypassword;naming=system;errors=full");
```

## Examples

> [!WARNING]
> ⚠️ To be added
