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
package inetsoft.uql.util;

import inetsoft.test.*;
import inetsoft.uql.jdbc.UniformSQL;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77706, the scanner of the subqueries in an expression operand (findSubqueries) and the
 * single-row check of a subquery (isSingleRow).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class XUtilOperandSubqueryTest {
   @Test
   void findSubqueries() {
      assertEquals(List.of("(select min(b.id) from b where b.k = $(p))"),
                   spans("0+(select min(b.id) from b where b.k = $(p))"));
      assertEquals(List.of("(select b.id from b)"), spans("ANY (select b.id from b)"));
      assertEquals(List.of("( SELECT 1 from b)"), spans("ANY ( SELECT 1 from b)"));
      // a nested subquery is part of the outer one
      assertEquals(List.of("(select min(b.id) from b where b.id in (select c.id from c))"),
                   spans("0+(select min(b.id) from b where b.id in (select c.id from c))"));
      // two subqueries, one inside a function call
      assertEquals(List.of("(select 1 from b)", "(select 2 from b)"),
                   spans("coalesce((select 1 from b), 0) + (select 2 from b)"));
      // a parenthesis and select in a literal, a quoted name and a comment
      assertEquals(List.of("(select max(b.k) from b where b.k <> ')(select' and b.k = $(p))"),
                   spans("'x'||(select max(b.k) from b where b.k <> ')(select' and " +
                            "b.k = $(p))"));
      assertEquals(List.of("(select max(b.k) from b where b.k <> 'it''s)' and b.k = $(p))"),
                   spans("'x'||(select max(b.k) from b where b.k <> 'it''s)' and " +
                            "b.k = $(p))"));
      assertEquals(List.of("(select \"x)\" from b)"),
                   spans("'(select 1' || (select \"x)\" from b)"));
      assertEquals(List.of(), spans("'(select 1 from b)' || \"(select a)\" || `(select b)`"));
      assertEquals(List.of("(select 1 /* ) */ from b)"),
                   spans("0 /* (select 2) */ + (select 1 /* ) */ from b)"));
      // not a select keyword, not closed
      assertEquals(List.of(), spans("(selection + 1)"));
      assertEquals(List.of(), spans("(select_x)"));
      assertEquals(List.of(), spans("0 + (select min(b.id) from b"));
      assertEquals(List.of(), spans("a.id + 1"));
   }

   @Test
   void isSingleRow() throws Exception {
      String[] singleRow = {
         "select count(*) from b",
         "select min(b.id) from b where b.k = 'x'",
         "select MAX(b.id) from b",
         "select avg(b.id) from b",
         "select sum(b.id) from b",
         "select count(distinct b.k) from b",
         "select max(x.id) from (select b.id from b where b.k = 'x') x",
      };

      for(String sql : singleRow) {
         assertTrue(XUtil.isSingleRow(parse(sql)), sql);
      }

      String[] notSingleRow = {
         "select b.id from b",
         // a window aggregate returns a row for each row
         "select max(b.id) over (partition by b.k) from b",
         // grouped, or with HAVING (excluded to be safe)
         "select max(b.id) from b group by b.k",
         "select max(b.id) from b having max(b.id) > 1",
         // two columns
         "select max(b.id), min(b.id) from b",
         // safe false negatives: an aggregate in an expression
         "select count(*) + 1 from b",
         "select coalesce(max(b.id), 0) from b",
         "select max(b.id) - min(b.id) from b",
         // a function whose name starts as an aggregate
         "select maxvalue(b.id) from b",
      };

      for(String sql : notSingleRow) {
         assertFalse(XUtil.isSingleRow(parse(sql)), sql);
      }

      assertFalse(XUtil.isSingleRow(null));
   }

   private static List<String> spans(String text) {
      List<String> list = new ArrayList<>();

      for(int[] span : XUtil.findSubqueries(text)) {
         list.add(text.substring(span[0], span[1]));
      }

      return list;
   }

   // UniformSQL.parse is package-private, it's what the parser of a data source query calls
   private static UniformSQL parse(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class,
                                                        long.class);
      parse.setAccessible(true);
      // UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD (package-private)
      parse.invoke(sql, text, 0, 4000L);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      return sql;
   }
}
