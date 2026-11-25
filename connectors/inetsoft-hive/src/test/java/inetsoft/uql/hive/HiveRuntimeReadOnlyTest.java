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
package inetsoft.uql.hive;

import inetsoft.uql.VariableTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82333: the Hive connector must refuse a write or DDL statement before anything is
 * sent to Hive.
 */
@Tag("core")
class HiveRuntimeReadOnlyTest {
   @ParameterizedTest
   @ValueSource(strings = {
      "SELECT * FROM victim",
      "select * from victim;",
      "  -- comment\n SELECT 1",
      "/* block */ SELECT 1 ;  -- trailing comment",
      "WITH q AS (SELECT * FROM victim) SELECT * FROM q",
      "SELECT 'a;b DROP TABLE x' AS s FROM victim",
      "SELECT 'it\\'s; DROP' FROM victim",
      "SELECT `insert` FROM victim",
      "SHOW TABLES",
      "DESCRIBE victim",
      "desc victim"
   })
   void acceptsSingleReadOnlyStatement(String sql) {
      assertDoesNotThrow(() -> HiveRuntime.requireReadOnlyQuery(sql));
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "DROP TABLE victim",
      "drop table victim",
      "INSERT INTO victim VALUES (1, 'a')",
      "INSERT OVERWRITE TABLE victim SELECT * FROM other",
      "TRUNCATE TABLE victim",
      "CREATE TABLE t (a INT)",
      "ALTER TABLE victim RENAME TO v2",
      "LOAD DATA INPATH '/x' INTO TABLE victim",
      "UPDATE victim SET a = 1",
      "DELETE FROM victim",
      "MERGE INTO victim USING other ON (1 = 1) WHEN MATCHED THEN DELETE",
      "SET hive.exec.dynamic.partition=true",
      "EXPLAIN ANALYZE INSERT INTO victim VALUES (1)",
      "FROM other INSERT OVERWRITE TABLE victim SELECT *",
      "WITH q AS (SELECT * FROM other) INSERT INTO victim SELECT * FROM q",
      "SELECT * FROM victim; DROP TABLE victim",
      "/* comment */ DROP TABLE victim",
      "-- SELECT\nDROP TABLE victim",
      "SELECT 'unterminated",
      "SELECTX * FROM victim",
      "",
      "   ;"
   })
   void refusesOtherStatements(String sql) {
      assertThrows(IllegalArgumentException.class, () -> HiveRuntime.requireReadOnlyQuery(sql));
   }

   @Test
   void keywordsAreMatchedIndependentOfTheDefaultLocale() {
      Locale saved = Locale.getDefault();

      try {
         // in Turkish, "i".toUpperCase() is a dotted capital I
         Locale.setDefault(Locale.forLanguageTag("tr-TR"));
         assertDoesNotThrow(() -> HiveRuntime.requireReadOnlyQuery(
            "with q as (select * from victim) select * from q"));
         assertDoesNotThrow(() -> HiveRuntime.requireReadOnlyQuery("describe victim"));
         assertThrows(IllegalArgumentException.class, () -> HiveRuntime.requireReadOnlyQuery(
            "with q as (select * from other) insert into victim select * from q"));
      }
      finally {
         Locale.setDefault(saved);
      }
   }

   @Test
   void refusesNull() {
      assertThrows(IllegalArgumentException.class, () -> HiveRuntime.requireReadOnlyQuery(null));
   }

   @Test
   void dropIsRefusedBeforeAnyConnectionIsOpened() {
      AtomicInteger connections = new AtomicInteger();
      HiveRuntime runtime = new HiveRuntime() {
         @Override
         Connection getConnection(HiveDataSource ds) {
            connections.incrementAndGet();
            throw new AssertionError("a refused statement must not open a connection");
         }
      };

      HiveQuery query = mock(HiveQuery.class);
      HiveDataSource ds = mock(HiveDataSource.class);
      when(query.getDataSource()).thenReturn(ds);
      when(query.getQueryString()).thenReturn("DROP TABLE victim");

      assertNull(runtime.runQuery(query, new VariableTable()));
      assertEquals(0, connections.get());
   }
}
