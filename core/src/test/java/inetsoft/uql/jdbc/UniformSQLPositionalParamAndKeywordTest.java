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
package inetsoft.uql.jdbc;

import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77664. A PostgreSQL positional parameter ({@code $1}) and the reserved
 * {@code current_date} niladic keyword-function both reach {@code quoteDot()} as a plain
 * identifier and get wrapped in quotes by {@code XUtil.isSpecialName()}'s generic
 * special-character/keyword checks, changing their meaning on regeneration: a quoted
 * {@code "$1"} is no longer PostgreSQL's positional bind parameter, and a quoted
 * {@code "current_date"} is a column reference instead of the keyword-function. Both are
 * now exempted from quoting via {@code XUtil.shouldNotQuote()}, narrowly scoped to the
 * {@code $}-followed-by-only-digits shape and the exact {@code current_date} keyword, so
 * other {@code $}-containing identifiers and other reserved keywords are still quoted as
 * before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLPositionalParamAndKeywordTest {
   @ParameterizedTest
   @ValueSource(strings = { "postgresql", "h2", "oracle" })
   void dollarPositionalParameterStaysUnquoted(String type) {
      String text = "select * from t where b = $1";
      String generated = regenerate(text, dataSource(type));

      assertTrue(generated.contains("$1"), type + ": " + generated);
      assertFalse(generated.contains("\"$1\""), type + ": " + generated);
   }

   @ParameterizedTest
   @ValueSource(strings = { "postgresql", "h2", "oracle" })
   void currentDateStaysUnquoted(String type) {
      String text = "select * from t where d > current_date - interval '1' day";
      String generated = regenerate(text, dataSource(type));

      assertTrue(generated.contains("current_date"), type + ": " + generated);
      assertFalse(generated.contains("\"current_date\""), type + ": " + generated);
      assertFalse(generated.contains("`current_date`"), type + ": " + generated);
   }

   // regression: current_timestamp is a distinct reserved keyword, not in #77664's narrow scope,
   // and must continue to be quoted as before
   @Test
   void currentTimestampStillQuoted() {
      String generated = regenerate("select current_timestamp from t", dataSource("postgresql"));
      assertTrue(generated.contains("\"current_timestamp\""), generated);
   }

   // regression: a column name that merely starts with "current_date" must still be quoted --
   // the exemption is an exact keyword match, not a prefix match
   @Test
   void currentDatePrefixedColumnStillQuoted() {
      String generated = regenerate("select current_date_x from t", dataSource("postgresql"));
      assertTrue(generated.contains("\"current_date_x\""), generated);
   }

   private static String regenerate(String text, JDBCDataSource ds) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      sql.clearSQLString();
      return normalize(sql.getSQLString());
   }

   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static JDBCDataSource dataSource(String type) {
      return SQLHelperNotEqualJoinTest.RowCompare.dataSource(type);
   }
}
