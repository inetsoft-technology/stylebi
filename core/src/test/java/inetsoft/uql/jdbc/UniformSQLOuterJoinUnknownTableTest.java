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

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77439, an outer join whose tables are not two different from clause tables of
 * its query fails the parse, so the original sql runs instead of a regenerated from
 * clause such as "from LEFT OUTER JOIN ON id = bid , a, b". This covers the legacy
 * where clause outer joins (*=, =* and (+)) and the ANSI ON shapes of the bug.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLOuterJoinUnknownTableTest {
   @ParameterizedTest
   @ValueSource(strings = {
      // reporter's example and the other ANSI outer join types
      "select a.x from a left join b on id = bid",
      "select a.x from a right join b on id = bid",
      "select a.x from a full outer join b on id = bid",
      // partly qualified, an unknown qualifier, a name hidden by its alias, and a bare
      // name against a schema-qualified table
      "select a.x from a left join b on a.id = bid",
      "select a.x from a left join b on id = b.bid",
      "select a.x from a left join b on x.id = b.id",
      "select a.x from a t1 left join b t2 on a.id = t2.id",
      "select a.x from s.a left join s.b on a.id = b.id",
      // chained and subquery positions
      "select a.x from a left join b on a.id = b.id left join c on id = cid",
      "select a.x from a left join b on id = bid left join c on a.id = c.id",
      "select a.x from a where exists (select 1 from c left join d on cid = did)",
      // legacy where clause outer joins on unqualified columns
      "select a.x from a, b where id *= bid",
      "select a.x from a, b where id =* bid",
      "select a.x from a, b where id = bid(+)",
      "select a.x from a, b where id (+)= bid",
      "select a.x from a, b where id = bid(+) and k = 5",
      "select a.x from a, b where k = 5 or id = bid(+)",
      // legacy, partly qualified, an unknown table, a name hidden by its alias, and a
      // bare name against a schema-qualified table
      "select a.x from a, b where a.id = bid(+)",
      "select a.x from a, b where id *= b.bid",
      "select a.x from a, b where a.id *= x.id",
      "select a.x from a t1, b t2 where a.id = t2.id(+)",
      "select a.x from s.a, s.b where a.id = b.id(+)",
      // an unaliased quoted table name with spaces doesn't resolve to its table
      "select * from \"my a\", \"my b\" where \"my a\".id = \"my b\".id(+)",
      // legacy outer joins in a subquery are checked against the subquery's tables, so
      // a correlated outer join to the outer query's table is refused too
      "select a.x from a where exists (select 1 from c, d where cid *= did)",
      "select a.x from a where exists (select 1 from c where c.id = a.id(+))",
      "select * from (select a.id from a, b where id = bid(+)) t",
      // a legacy outer join next to an ANSI join
      "select a.x from a join b on a.id = b.id, c where id = cid(+)"
   })
   void outerJoinOnUnknownTableFailsCleanly(String text) {
      RecognitionException ex = assertThrows(RecognitionException.class, () -> parse(text));
      assertTrue(ex.getMessage().contains("Unsupported outer join condition"), ex.getMessage());

      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      // qualified outer joins between two different from clause tables
      "select a.x from a left join b on a.id = b.id | a.id *= b.id",
      "select a.x from A left join B on a.id = b.id | a.id *= b.id",
      "select t1.x from a T1 left join b T2 on t1.id = t2.id | t1.id *= t2.id",
      "select sa.a.x from sa.a left join SA.B on SA.A.id = sa.b.id | SA.A.id *= sa.b.id",
      "select * from \"A\" left join \"B\" on \"A\".\"id\" = \"B\".\"id\" | A.id *= B.id",
      "select * from [s].[a] left join [s].[b] on [s].[a].[id] = [s].[b].[id] | " +
         "s.a.id *= s.b.id",
      "select * from `s`.`a` left join `s`.`b` on `s`.`a`.`id` = `s`.`b`.`id` | " +
         "s.a.id *= s.b.id",
      // self joins with distinct aliases are two different tables
      "select t1.id from a t1 left join a t2 on t1.id = t2.pid | t1.id *= t2.pid",
      "select a.id from a left join a t2 on a.id = t2.pid | a.id *= t2.pid",
      // legacy qualified outer joins
      "select a.x from a, b where a.id = b.id(+) | a.id *= b.id",
      "select a.x from a, b where a.id (+)= b.id | a.id =* b.id",
      "select a.x from a, b where a.id *= b.bid | a.id *= b.bid",
      "select a.x from a, b where a.id =* b.bid | a.id =* b.bid",
      "select a.x from A, B where a.id = b.id(+) | a.id *= b.id",
      "select a.x from a, b t2 where a.id = t2.id(+) | a.id *= t2.id",
      "select s.a.x from s.a, s.b where s.a.id = s.b.id(+) | s.a.id *= s.b.id",
      "select t1.id from a t1, a t2 where t1.id = t2.pid(+) | t1.id *= t2.pid",
      "select a.x from a, b, c where a.id = b.id(+) and b.k = c.k(+) | " +
         "a.id *= b.id, b.k *= c.k",
      "select a.x from a where exists (select 1 from c, d where c.id = d.id(+)) | ",
      // inner joins on unqualified columns are not checked
      "select a.x from a join b on id = bid | id = bid",
      "select a.x from a inner join b on id = bid and k = bk | id = bid, k = bk",
      "select a.x from a, b where id = bid | id = bid"
   })
   void outerJoinOnKnownTablesIsAccepted(String text, String expected) throws Exception {
      UniformSQL sql = parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertEquals(expected == null ? "" : expected, joins(sql));
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select a.x from a, b where a.id = b.id(+) | from a LEFT OUTER JOIN b ON a.id = b.id",
      "select a.x from a, b where a.id *= b.id | from a LEFT OUTER JOIN b ON a.id = b.id",
      "select t1.id from a t1, a t2 where t1.id = t2.pid(+) | " +
         "from a t1 LEFT OUTER JOIN a t2 ON t1.id = t2.pid",
   })
   void legacyOuterJoinGeneratesJoin(String text, String expected) throws Exception {
      String generated = normalize(parse(text).getSQLString());
      assertTrue(generated.contains(expected), generated);
   }

   /**
    * An Oracle data source in its default non-ANSI mode would regenerate an unqualified
    * outer join as a valid where clause (+) join. The parse is not dialect aware (the data
    * source can be attached after the parse, and a parsed query can be generated for
    * another data source), so these are refused on Oracle too, which only means the
    * original sql runs and the query isn't merged. This pins that accepted trade-off.
    */
   @Test
   void unqualifiedOuterJoinIsRefusedOnOracleNonAnsi() throws Exception {
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getRuntimeProductName()).thenReturn("Oracle");
      when(ds.getProductVersion()).thenReturn("19");
      when(ds.isAnsiJoin()).thenReturn(false);

      for(String text : new String[] {
         "select a.x from a left join b on id = bid",
         "select a.x from a, b where id = bid(+)",
         "select a.x from a, b where id *= bid"
      })
      {
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(ds);
         new SQLProcessor(sql).parse(text);
         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);
      }

      // a qualified (+) join is still accepted
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      new SQLProcessor(sql).parse("select a.x from a, b where a.id = b.id(+)");
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertEquals("a.id *= b.id", joins(sql));
   }

   private static String joins(UniformSQL sql) {
      return Arrays.stream(sql.getJoins())
         .map(j -> j.getExpression1().getValue() + " " + j.getOp() + " " +
            j.getExpression2().getValue())
         .collect(Collectors.joining(", "));
   }

   // the generated sql is pretty-printed
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }
}
