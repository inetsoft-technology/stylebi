/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.jdbc;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WindowFunctionCapabilityTest {

   // ── ANSI-compliant dialects support it by default ──────────────────────

   @Test
   void postgreSQLSupportsWindowFunction() {
      assertTrue(new PostgreSQLHelper().supportsOperation(SQLHelper.WINDOW_FUNCTION));
   }

   @Test
   void oracleSupportsWindowFunction() {
      assertTrue(new OracleSQLHelper().supportsOperation(SQLHelper.WINDOW_FUNCTION));
   }

   @Test
   void db2SupportsWindowFunction() {
      assertTrue(new DB2SQLHelper().supportsOperation(SQLHelper.WINDOW_FUNCTION));
   }

   // ── Dialects with no window function support gate it off ──────────────

   @Test
   void derbyDoesNotSupportWindowFunction() {
      assertFalse(new DerbyHelper().supportsOperation(SQLHelper.WINDOW_FUNCTION));
   }

   @Test
   void accessDoesNotSupportWindowFunction() {
      assertFalse(new AccessSQLHelper().supportsOperation(SQLHelper.WINDOW_FUNCTION));
   }

   @Test
   void mongoDoesNotSupportWindowFunction() {
      assertFalse(new MongoHelper().supportsOperation(SQLHelper.WINDOW_FUNCTION));
   }

   // ── MySQL version gate ──────────────────────────────────────────────────
   // NOTE: exercising the >= 8 / MariaDB >= 10 version gate requires a
   // UniformSQL wired to a JDBCDataSource with a mocked getProductVersion().
   // Per task brief, that mock is skipped here (kept minimal) — this is
   // covered by the live check in a follow-on task instead. With no
   // UniformSQL attached, uniformSql is null, so the helper falls back to
   // the permissive super.supportsOperation(...) result (denylist default
   // = supported).
   @Test
   void mySQLWithNoDataSourceFallsBackToPermissiveDefault() {
      assertTrue(new MySQLHelper().supportsOperation(SQLHelper.WINDOW_FUNCTION));
   }
}
