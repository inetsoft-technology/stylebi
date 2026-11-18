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
import inetsoft.uql.util.XUtil;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81185, time(...) was not accepted as a function call, so a statement with it failed to
 * parse (unexpected token: "("), unlike date(...), hour(...) and the other functions. The
 * statement now parses without loss and its function text is regenerated unchanged. The cases
 * where time is not a function (a column, an alias, a typed literal, a cast type) are
 * unchanged.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLParserTimeFunctionTest {
   private static final String[] TYPES =
      { "default", "oracle", "postgresql", "derby", "h2", "h2-ansi" };

   // the two statements of the report
   @Test
   void reportedStatements() throws Exception {
      for(String type : TYPES) {
         assertEquals("select time(concat(hour(order_time),':00:00')) as t from orders.orders",
                      normalize(generate(
                         "select time(concat(hour(order_time),':00:00')) as t " +
                            "from orders.orders", type)).replace("\"", ""), type);
         String where = normalize(generate(
            "select a from orders.orders where order_time > time('10:00:00')", type))
            .replace("\"", "");
         assertEquals("select a from orders.orders where order_time > time('10:00:00')",
                      where.toLowerCase(java.util.Locale.ROOT), type);
         assertTrue(where.endsWith("where order_time > time('10:00:00')"), type + ": " + where);
      }
   }

   // every position of a value expression, mixed case and a space before the parenthesis
   @ParameterizedTest
   @ValueSource(strings = {
      "select time(a.x) from a",
      "select a.id, time (a.x) t from a",
      "select Time(a.x) as t from a",
      "select TIME(a.x) from a",
      "select a.id from a where time(a.x) > time('10:00:00')",
      "select a.id from a where time(a.x) in (time('10:00:00'), time('11:00:00'))",
      "select a.id from a where time(a.x) between time('10:00:00') and time('11:00:00')",
      "select time(a.x) t, count(*) n from a group by time(a.x) having time(a.x) > 1 " +
         "order by time(a.x)",
      "select case when time(a.x) > 1 then 1 else 0 end c from a",
      "select coalesce(time(a.x), time(a.y)) c from a",
      "select sum(time(a.x)) s from a",
      "select a.id from a where a.x = (select max(time(b.x)) from b)",
      "select a.id from a, b where time(a.x) = time(b.x)",
      "select a.id from a inner join b on time(a.x) = time(b.x)",
   })
   void parsesAsFunction(String text) throws Exception {
      for(String type : TYPES) {
         String generated = generate(text, type);
         assertTrue(generated.toLowerCase().contains("time"), type + ": " + generated);
         assertEquals(generated, generate(generated, type), type + " round trip: " + text);
      }
   }

   // the function text is kept as written, whatever the helper does around it
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select time(a.x) from a|time(",
      "select a.id from a where time(a.x) > 1|time(",
   })
   void functionTextKept(String text, String expected) throws Exception {
      for(String type : TYPES) {
         assertTrue(generate(text, type).contains(expected), type + ": " + text);
      }
   }

   // time as a name or a type is unchanged: it still parses, without loss. Its regeneration is
   // not compared here, a column named time is quoted when regenerated, before and after.
   @ParameterizedTest
   @ValueSource(strings = {
      "select time from a",
      "select a.time from a",
      "select a.id, time from a where time > 1",
      "select a.id from a order by time",
      "select a.id as time from a",
      "select a.id time from a",
      "select time + 1 from a",
      "select (time) from a",
      "select a.id from a where a.x in (time, 3)",
      "select cast(a.x as time) from a",
      "select cast(time as varchar(10)) from a",
      "select a.id from a where a.x > time '10:00:00'",
      "select a.id from a where a.x > {t '10:00:00'}",
      "select a.id from time",
   })
   void timeAsNameUnchanged(String text) throws Exception {
      for(String type : TYPES) {
         generate(text, type);
      }
   }

   // a regenerated cast type and typed literal text of main stay as they were, the fix does not
   // touch them
   @Test
   void castTimeKeepsType() throws Exception {
      assertTrue(generate("select cast(a.x as time) from a", "default").contains("cast("));
   }

   // not a function call and not valid before either, still refused
   @ParameterizedTest
   @ValueSource(strings = {
      "select a.time(a.x) from a",
      "select time(a.x)(a.y) from a",
      "select time (select 1) from a",
   })
   void stillRefused(String text) throws Exception {
      UniformSQL sql = new UniformSQL();

      try {
         sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      }
      catch(Exception expected) {
         // the parse error is rethrown
      }

      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);
   }

   // a parsed time(...) statement is a parsed, mergeable statement, so VPM sees its tables
   @Test
   void parsedStatementIsMergeable() throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse("select time(a.x) from a where time(a.x) > time('10:00:00')",
                UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertTrue(XUtil.isParsedSQL(sql));
      assertEquals(1, sql.getTableCount());
   }

   // time is a keyword token, so a locale that folds i differently must not matter
   @Test
   void turkishLocale() throws Exception {
      java.util.Locale old = java.util.Locale.getDefault();

      try {
         java.util.Locale.setDefault(new java.util.Locale("tr", "TR"));
         assertTrue(generate("select TIME(a.x) from a", "default").toLowerCase(java.util.Locale.ROOT)
                       .contains("time("));
         assertTrue(generate("select time(a.x) from a where a.x > time('10:00:00')", "default")
                       .contains("time("));
      }
      finally {
         java.util.Locale.setDefault(old);
      }
   }

   private static String generate(String text, String type) throws Exception {
      UniformSQL sql = new UniformSQL();

      if(!"default".equals(type)) {
         sql.setDataSource(SQLHelperNotEqualJoinTest.RowCompare.dataSource(type));
      }

      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type + ": " + text);
      assertFalse(sql.isLossy(), type + ": " + text);
      sql.clearSQLString();
      return normalize(sql.getSQLString());
   }

   private static String normalize(String text) {
      return text.replaceAll("\\s+", " ").trim();
   }
}
