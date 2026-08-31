# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- `MapepirePreparedStatement` — parameterized queries with server-side parameter
  binding via `Connection.prepareStatement(sql)` (#15)
- `ResultSet.getObject()` by column index and label (#8)
- `ResultSet.wasNull()` to distinguish SQL NULL from default values like `0` (#6)
- `ResultSet.isClosed()` and `Statement.isClosed()` (#9, #10)
- `ResultSet.getBigDecimal(int)` / `getBigDecimal(String)` (no-scale variants)
- `Connection.setAutoCommit()` / `getAutoCommit()` — enables Spring
  `@Transactional` and other framework-managed transactions (#16)
- `Connection.isValid()` — enables connection pool health checks (HikariCP
  and friends) (#19)

### Security

- TLS certificate validation (`rejectUnauthorized`) is now enabled by default
  instead of being permanently disabled. It can be turned off per-connection
  via the `REJECTUNAUTHORIZED=false` connection property for local
  development against self-signed certificates

### Fixed

- Fixed resource leaks, fetch size consistency, `isValid` ping, `BigDecimal` error handling, and `REJECTUNAUTHORIZED` validation
- JDBC URLs without an explicit port now default to 8076 instead of crashing
  with `NumberFormatException` (#12)
- `Statement.getResultSet()`, `getUpdateCount()`, and `getMoreResults()` now
  throw a clear `SQLException` when called before any SQL has been executed,
  instead of a cryptic `NullPointerException` (#11)
- Statement fetch size now defaults to 100 instead of 0, fixing silently
  broken result set pagination (#14)
- `Statement.close()` no longer throws `NullPointerException` when called
  before any SQL has been executed, and is now idempotent
- Operations on a closed `ResultSet` or `Statement` now throw a clear
  `SQLException` instead of `NullPointerException`

### Changed

- Bumped JaCoCo from 0.8.4 to 0.8.12 to support running tests on modern JDKs
