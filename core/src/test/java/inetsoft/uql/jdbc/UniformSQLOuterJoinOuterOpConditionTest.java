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

import antlr.RecognitionException;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77491, an outer join marker ((+), *= or =*) on a condition that isn't a join
 * between two columns of different tables, such as "b.code(+) = 'X'", fails the parse,
 * so the original sql runs. The condition can't be an outer join, so it was kept as a
 * plain condition with the outer join operator, which generated invalid sql such as
 * "where b.code =* 'X'" next to the ANSI join. "e.deptno *=* d.deptno" lexes as *=
 * followed by the operand "* d.deptno", so it is the same condition.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLOuterJoinOuterOpConditionTest {
   @ParameterizedTest
   @ValueSource(strings = {
      // reporter's example, a filter on the outer table of a (+) join
      "select a.x from a, b where a.status = 'A' and a.id = b.id(+) and b.code(+) = 'X'",
      // other operands that are not a column of another table
      "select a.x from a, b where a.id = b.id(+) and b.code(+) = 5",
      "select a.x from a, b where a.id = b.id(+) and b.code(+) = :p",
      "select a.x from a, b where a.id = b.id(+) and b.code(+) = a.k + 1",
      "select a.x from a, b where a.id = b.id(+) and b.code(+) = $(v)",
      "select a.x from a, b where a.id = b.id(+) and 'X' = b.code(+)",
      "select a.x from a, b where a.id = b.id(+) and b.code = 'X'(+)",
      "select a.x from a, b where a.id *= b.id and b.code =* 'X'",
      "select a.x from a, b where a.k *= 5",
      "select a.x from a, b where a.id = $(v)(+)",
      // both columns of the same table
      "select a.x from a where a.x = a.y(+)",
      // *=* is *= with the operand "* d.deptno", alone and next to a valid join
      "select e.x from e, d where e.deptno *=* d.deptno",
      "select e.x from e, d where e.id *= d.id and e.deptno *=* d.deptno",
      // inner join ON, select list, having and subquery positions
      "select a.x from a join b on a.id = b.id and b.code(+) = 'X'",
      "select a.x from a join b on a.id = b.id and a.k *= 'X'",
      "select case when b.code(+) = 'X' then 1 else 0 end from a, b where a.id = b.id(+)",
      "select a.x, max(b.code) from a, b where a.id = b.id(+) group by a.x " +
         "having max(b.code)(+) = 1",
      "select a.x from a where a.id in (select b.id from b where b.code(+) = 'X')",
      "select a.x from a where exists (select 1 from c where c.k(+) = 'X')",
      // a $ variable is a column, so this is a join to an unknown table (Bug #77439)
      "select a.x from a, b where $v *= b.id"
   })
   void outerOpOnNonJoinConditionFailsCleanly(String text) {
      assertRefused(text);
   }

   // a quantified comparison with an outer join operator, which Oracle refuses too
   // (ORA-01799)
   @ParameterizedTest
   @ValueSource(strings = {
      "select e.x from e where e.deptno *= any (select d.deptno from d)",
      "select e.x from e where e.deptno =* all (select d.deptno from d)",
      "select e.x from e where e.deptno (+)= any (select d.deptno from d)"
   })
   void outerOpOnQuantifiedConditionFailsCleanly(String text) {
      assertRefused(text);
   }

   // an outer join in the on condition of an inner join, in any position. (+) in an ANSI
   // join is ORA-25156, and *= or =* can't be mixed with ANSI joins, so it isn't valid
   // on any database. The ANSI generation hoisted it into an outer join, which lost the
   // or, is or not around it and the comparison op ("a.k > b.k(+)" became ON a.k = b.k)
   @ParameterizedTest
   @ValueSource(strings = {
      "select a.x from a join b on a.k *= b.k",
      "select a.x from a join b on a.k = b.k(+)",
      "select a.x from a join b on a.id = b.id and a.k = b.k(+)",
      "select a.x from a join b on a.id = b.id and a.k =* b.k",
      "select a.x from a join b on a.id = b.id and a.k > b.k(+)",
      "select a.x from a join b on a.id = b.id and a.k <> b.k(+)",
      "select a.x from a join b on a.id(+) = b.id(+)",
      "select a.x from a join b on a.id = b.id and ((a.k = b.k(+)))",
      "select a.x from a join b on not (a.k = b.k(+))",
      "select a.x from a join b on a.id = b.id and not (a.k = b.k(+))",
      "select a.x from a join b on a.id = b.id and not ((a.k = b.k(+)))",
      "select a.x from a join b on a.id = b.id or a.k *= b.k",
      "select a.x from a join b on a.id = b.id or a.k = b.k(+)",
      "select a.x from a join b on a.id = b.id or a.k =* b.k",
      "select a.x from a join b on a.k *= b.k or a.id = b.id",
      "select a.x from a join b on a.k = b.k(+) or a.id = b.id",
      "select a.x from a join b on a.id = b.id and (a.f = 1 or a.k = b.k(+))",
      "select a.x from a join b on (a.f = 1 or a.k *= b.k) and a.id = b.id",
      "select a.x from a join b on (a.id = b.id(+) or a.k = b.k(+))",
      "select a.x from a join b on a.id = b.id and not (a.k = b.k(+) and a.f = 1)",
      "select a.x from a join b on not (a.k *= b.k and a.f = 1)",
      "select a.x from a left join b on a.id = b.id join c on b.id = c.id or c.k *= b.k",
      "select a.x from a left join b on a.id = b.id join c on b.id = c.id and c.k *= b.k",
      // a subquery's inner join is checked at its own level
      "select a.x from a join b on a.id = b.id and " +
         "a.k in (select c.k from c join d on c.id = d.id or c.k *= d.k)",
      "select a.x from a where a.k in (select c.k from c join d on c.id = d.id and c.k *= d.k)"
   })
   void outerJoinInInnerJoinOnFailsCleanly(String text) {
      assertRefused(text);
   }

   // a truth test fails the parse at the IS (#77735), before the outer join is checked
   @ParameterizedTest
   @ValueSource(strings = {
      "select a.x from a join b on (a.id = b.id(+)) is true",
      "select a.x from a join b on (a.id = b.id(+)) is not true",
      "select a.x from a join b on (a.k *= b.k) is false",
      "select a.x from a join b on a.id = b.id and (a.k = b.k(+)) is true",
   })
   void outerJoinUnderTruthTestInInnerJoinOnFailsCleanly(String text) {
      assertRefused(text, "Unsupported truth test");
   }

   // an outer join in a having clause, which Oracle only allows in a where clause. The
   // generation dropped it, or printed *= or =*
   @ParameterizedTest
   @ValueSource(strings = {
      "select a.x from a, b where a.id = b.id(+) group by a.x having a.k = b.k(+)",
      "select a.x from a, b where a.id = b.id(+) group by a.x having a.k > b.k(+)",
      "select a.x from a, b where a.id = b.id group by a.x having a.k = 1 or a.id *= b.id",
      "select a.x from a, b group by a.x having a.id = b.id(+)",
      "select a.x from a, b group by a.x having a.id =* b.id and max(a.k) = 1",
      "select a.x from a, b group by a.x having not (a.id = b.id(+))"
   })
   void outerJoinInHavingFailsCleanly(String text) {
      assertRefused(text);
   }

   // an outer join in the search condition of a case, which the generation printed as
   // *= or =*, or took out of the case and left an empty when
   @ParameterizedTest
   @ValueSource(strings = {
      "select case when a.k = b.k(+) then 1 else 0 end from a, b",
      "select case when a.k *= b.k then 1 else 0 end from a, b where a.id = b.id",
      "select case when a.f = 1 and a.k =* b.k then 1 end from a, b",
      "select a.x from a, b where case when a.k = b.k(+) then 1 else 0 end = 1",
      "select a.x from a join b on a.id = b.id and case when a.k *= b.k then 1 else 0 end = 1",
      "select a.x from a, b group by a.x having max(case when a.k = b.k(+) then 1 end) = 1",
      "select a.x from a, b order by case when a.k = b.k(+) then 1 else 0 end"
   })
   void outerJoinInCaseFailsCleanly(String text) {
      assertRefused(text);
   }

   /**
    * An Oracle data source in its default non-ANSI mode regenerates "b.code(+) = 'X'"
    * as the same valid (+) condition. The parse is not dialect aware (the data source can
    * be attached after the parse, and a parsed query can be generated for another data
    * source or with ANSI joins), so it is refused on Oracle too, which only means the
    * original sql runs and the query isn't merged. This is the trade-off Bug #77439
    * accepted, see UniformSQLOuterJoinUnknownTableTest.unqualifiedOuterJoinIsRefusedOnOracleNonAnsi.
    */
   @Test
   void outerOpOnNonJoinConditionIsRefusedOnOracleNonAnsi() {
      JDBCDataSource ds = oracle(false);
      String text = "select a.x from a, b where a.id = b.id(+) and b.code(+) = 'X'";
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      // the helper table key is lowercase, "Oracle" would get the base SQLHelper
      assertEquals(OracleSQLHelper.class, SQLHelper.getSQLHelper(sql).getClass());
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), () -> regenerate(sql));

      // a (+) join is still regenerated as one
      assertEquals("select a.x from a, b where a.id = b.id(+)",
                   regenerate(parse("select a.x from a, b where a.id = b.id(+)", ds)));
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select a.x from a, b where a.id = b.id(+) | " +
         "select a.x from a LEFT OUTER JOIN b ON a.id = b.id",
      "select a.x from a, b where a.id = b.id(+) and b.code = 'X' | " +
         "select a.x from a LEFT OUTER JOIN b ON a.id = b.id where b.code = 'X'",
      "select a.x from a left join b on a.id = b.id | " +
         "select a.x from a LEFT OUTER JOIN b ON a.id = b.id",
      "select e.x from e where e.deptno = any (select d.deptno from d) | " +
         "select e.x from e where e.deptno = any (select d.deptno from d )",
      // an inner join in a case is kept in the case
      "select case when a.k = b.k then 1 else 0 end from a, b | " +
         "select case when a.k = b.k then 1 else 0 END from a, b",
      // a subquery's own (+) join under a having or a case is checked at its own level
      "select a.x from a, b where a.id = b.id(+) group by a.x " +
         "having exists (select 1 from c, d where c.id = d.id(+)) | " +
         "select a.x from a LEFT OUTER JOIN b ON a.id = b.id group by a.x " +
         "having EXISTS ( select 1 from c LEFT OUTER JOIN d ON c.id = d.id)",
      "select case when exists (select 1 from c, d where c.id = d.id(+)) then 1 else 0 end from a | " +
         "select case when EXISTS ( select 1 from c LEFT OUTER JOIN d ON c.id = d.id) " +
         "then 1 else 0 END from a",
      "select a.x from a join b on a.id = b.id or a.k = b.k | " +
         "select a.x from a, b where (a.id = b.id or a.k = b.k)",
      "select a.x from a join b on a.id = b.id, c where a.k = c.k(+) | " +
         "select a.x from (a INNER JOIN b ON a.id = b.id ) LEFT OUTER JOIN c ON a.k = c.k"
   })
   void outerJoinAndPlainConditionRegenerate(String text, String expected) {
      for(JDBCDataSource ds : new JDBCDataSource[] { null, GenericJDBCDataSource.create(), oracle(true) }) {
         UniformSQL sql = parseStructure(text, ds);
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
         assertEquals(expected, regenerate(sql));
      }
   }

   // a subquery's or derived table's own (+) join under an inner join ON is checked at
   // its own level. The inner join is generated in the where clause without ANSI joins
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select a.x from a join b on a.id = b.id and exists (select 1 from c, d where c.id = d.id(+)) | " +
         "select a.x from a, b where a.id = b.id and " +
         "EXISTS ( select 1 from c LEFT OUTER JOIN d ON c.id = d.id) | " +
         "select a.x from a INNER JOIN b ON a.id = b.id where " +
         "EXISTS ( select 1 from c LEFT OUTER JOIN d ON c.id = d.id)",
      "select c.x from c join (select a.x, a.id from a, b where a.id = b.id(+)) t on c.id = t.id | " +
         "select c.x from c, ( select a.id, a.x from a LEFT OUTER JOIN b ON a.id = b.id) t " +
         "where c.id = t.id | " +
         "select c.x from c INNER JOIN ( select a.id, a.x from a LEFT OUTER JOIN b ON a.id = b.id) t " +
         "ON c.id = t.id"
   })
   void subqueryOuterJoinInInnerJoinOnRegenerates(String text, String expected, String ansi) {
      JDBCDataSource[] list = { null, GenericJDBCDataSource.create(), oracle(true) };

      for(int i = 0; i < list.length; i++) {
         UniformSQL sql = parseStructure(text, list[i]);
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
         assertEquals(i < 2 ? expected : ansi, regenerate(sql));
      }
   }

   // refused with or without a data source, and whether or not it uses ANSI joins
   private static void assertRefused(String text) {
      assertRefused(text, "Unsupported outer join condition");
   }

   private static void assertRefused(String text, String message) {
      for(JDBCDataSource ds : new JDBCDataSource[] { null, GenericJDBCDataSource.create(), oracle(true) }) {
         UniformSQL sql = parse(text, ds);
         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(),
                      () -> "parsed, regenerated as: " + regenerate(sql));
      }

      RecognitionException ex = assertThrows(RecognitionException.class, () -> {
         UniformSQL sql = new UniformSQL();
         sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      });
      assertTrue(ex.getMessage().contains(message), ex.getMessage());
   }

   private static JDBCDataSource oracle(boolean ansi) {
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getRuntimeProductName()).thenReturn("oracle");
      when(ds.getProductVersion()).thenReturn("19");
      when(ds.isAnsiJoin()).thenReturn(ansi);
      return ds;
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      new SQLProcessor(sql).parse(text);
      return sql;
   }

   // a where clause (+) join is refused with a data source that writes ANSI joins (Bug #77548),
   // so its structure is parsed without one and regenerated with the data source, as the
   // structure of sql parsed without a data source is
   private static UniformSQL parseStructure(String text, JDBCDataSource ds) {
      if(ds == null || !text.contains("(+)")) {
         return parse(text, ds);
      }

      assertEquals(UniformSQL.PARSE_FAILED, parse(text, ds).getParseResult(), text);
      UniformSQL sql = parse(text, null);
      sql.clearSQLString();
      sql.setDataSource(ds);
      return sql;
   }

   // regenerate from the parsed model, the generated sql is pretty-printed
   private static String regenerate(UniformSQL sql) {
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }
}
