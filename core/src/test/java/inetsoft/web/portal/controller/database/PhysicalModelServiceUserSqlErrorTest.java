/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.portal.controller.database;

import com.zaxxer.hikari.pool.HikariPool;
import inetsoft.uql.jdbc.SQLExpressionFailedException;
import inetsoft.util.CancelledException;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.FileNotFoundException;
import java.sql.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Bug #77826. Only a database rejection of the user's inline view SQL is a bad request. A
 * connection failure, timeout, cancel or other server-side error must not be classified as one,
 * or an outage would be reported to the user as "invalid SQL" with only a DEBUG log.
 */
@Tag("core")
class PhysicalModelServiceUserSqlErrorTest {
   @ParameterizedTest(name = "{0}")
   @MethodSource("userErrors")
   void userSqlErrorIsBadRequest(String name, Throwable ex) {
      assertEquals(true, PhysicalModelService.isUserSqlError(ex));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("otherErrors")
   void otherErrorIsNotUserSqlError(String name, Throwable ex) {
      assertEquals(false, PhysicalModelService.isUserSqlError(ex));
   }

   static Stream<Arguments> userErrors() {
      return Stream.of(
         // Derby and H2 syntax errors, wrapped by JDBCHandler
         arguments("Derby missing table", wrap(new SQLSyntaxErrorException("no table", "42X05"))),
         arguments("Derby bad syntax", wrap(new SQLSyntaxErrorException("syntax", "42X01"))),
         arguments("H2 unknown function (state 90022)",
                   wrap(new SQLSyntaxErrorException("no function", "90022", 90022))),
         // H2 data errors are not wrapped
         arguments("H2 divide by zero", new SQLDataException("by zero", "22012")),
         arguments("data exception, no state", new SQLDataException("bad cast")),
         // PostgreSQL errors are plain SQLExceptions, not wrapped
         arguments("PG undefined table", new SQLException("relation does not exist", "42P01")),
         arguments("PG syntax error", new SQLException("syntax error", "42601")),
         arguments("PG undefined column", new SQLException("column does not exist", "42703")),
         arguments("PG undefined function", new SQLException("function does not exist", "42883")),
         arguments("PG insufficient privilege", new SQLException("permission denied", "42501")),
         arguments("PG division by zero", new SQLException("division by zero", "22012")),
         // MySQL and Oracle
         arguments("MySQL no such table", wrap(new SQLSyntaxErrorException("no table", "42S02", 1146))),
         arguments("Oracle ORA-00942", wrap(new SQLSyntaxErrorException("ORA-00942", "42000", 942))),
         arguments("ODBC-2 syntax error", new SQLException("syntax", "37000")),
         // SQL Server, default (non-X/Open) states, gated on the server error number
         arguments("mssql invalid object name 208", new SQLException("Invalid object", "S0002", 208)),
         arguments("mssql invalid column 207", wrap(new SQLException("Invalid column", "S0001", 207))),
         arguments("mssql syntax 102", wrap(new SQLException("Incorrect syntax", "S0001", 102))),
         arguments("mssql syntax 156", wrap(new SQLException("Incorrect syntax", "S0001", 156))),
         arguments("mssql multi-part identifier 4104",
                   wrap(new SQLException("could not be bound", "S0001", 4104))),
         arguments("mssql unknown function 195", wrap(new SQLException("not a function", "S0001", 195))),
         arguments("mssql not in GROUP BY 8120", wrap(new SQLException("group by", "S0001", 8120))),
         arguments("mssql S0022 with a user error number",
                   new SQLException("Invalid column", "S0022", 207)),
         arguments("mssql X/Open invalid object", new SQLException("Invalid object", "42S02", 208))
      );
   }

   static Stream<Arguments> otherErrors() {
      return Stream.of(
         // connection failures and pool timeouts
         arguments("Hikari pool timeout, state 08001",
                   new SQLTransientConnectionException("Connection is not available", "08001")),
         arguments("Hikari pool timeout, null state",
                   new SQLTransientConnectionException("Connection is not available")),
         arguments("Hikari lazy pool, Derby DB missing (XJ004)",
                   new SQLTransientConnectionException("Database not found", "XJ004")),
         arguments("transient connection with a class 42 state",
                   new SQLTransientConnectionException("not available", "42000")),
         arguments("Hikari eager pool initialization",
                   new HikariPool.PoolInitializationException(new SQLException("refused", "08001"))),
         arguments("MySQL communications failure",
                   new SQLRecoverableException("Communications link failure", "08S01")),
         arguments("Oracle connection reset",
                   new SQLRecoverableException("IO Error", "08006", 17002)),
         arguments("non-transient connection",
                   new SQLNonTransientConnectionException("closed", "08003")),
         arguments("PG connection refused", new SQLException("Connection refused", "08001")),
         arguments("JDBCHandler failed to connect",
                   new SQLException("Failed to connect to datasource[x]!")),
         // timeouts and cancels
         arguments("query timeout", new SQLTimeoutException("timeout", "HYT00")),
         arguments("Oracle ORA-01013 cancel", new SQLTimeoutException("cancelled", "72000", 1013)),
         arguments("MySQL timeout", new SQLTimeoutException("timeout", "70100")),
         arguments("PG statement timeout 57014", new SQLException("canceling statement", "57014")),
         arguments("JDBCHandler timeout",
                   new MessageException("Query timeout.", new SQLTimeoutException("timeout"))),
         arguments("JDBCHandler cancel", new CancelledException("Query is cancelled: select 1")),
         // server-side failures
         arguments("PG admin shutdown 57P01", new SQLException("terminating", "57P01")),
         arguments("PG disk full 53100", new SQLException("disk full", "53100")),
         arguments("PG too many connections 53300", new SQLException("too many", "53300")),
         arguments("PG serialization failure 40001", new SQLException("serialization", "40001")),
         arguments("PG internal error XX000", new SQLException("internal", "XX000")),
         arguments("PG io error 58030", new SQLException("io error", "58030")),
         arguments("generic HY000, wrapped", wrap(new SQLException("general error", "HY000"))),
         arguments("mssql S0001 with a non-user error number",
                   wrap(new SQLException("insufficient memory", "S0001", 701))),
         arguments("mssql S0002 with a non-user error number",
                   new SQLException("server error", "S0002", 1105)),
         arguments("mssql S0022 with a non-user error number",
                   new SQLException("server error", "S0022", 50000)),
         arguments("mssql deadlock 1205", new SQLException("deadlock", "40001", 1205)),
         arguments("null state", new SQLException("unknown")),
         // not SQL errors at all
         arguments("missing data source", new FileNotFoundException()),
         arguments("runtime exception", new IllegalStateException("bug")),
         arguments("wrapped runtime exception", new SQLExpressionFailedException(
            new IllegalStateException("bug"))),
         arguments("null", null)
      );
   }

   private static SQLExpressionFailedException wrap(SQLException ex) {
      return new SQLExpressionFailedException(ex);
   }
}
