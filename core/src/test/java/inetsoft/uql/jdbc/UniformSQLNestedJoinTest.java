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
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77434, UniformSQL keeps parsed joins only as XJoins without their order or
 * nesting, and the regenerated from clause picks its own join order. A join group on
 * the right side of an outer join, and a RIGHT or FULL join mixed with an inner or
 * cross join (from a join keyword or a where clause column join), can't be
 * regenerated with the same results, so they fail the parse and keep the original sql.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLNestedJoinTest {
   @ParameterizedTest
   @ValueSource(strings = {
      // reporter's example and its spellings
      "select * from a left join (b join c on b.id = c.id) on a.id = b.id",
      "select * from a left join (b inner join c on b.id = c.id) on a.id = b.id",
      "select * from a left join ((b join c on b.id = c.id)) on a.id = b.id",
      "select * from a left join b join c on b.id = c.id on a.id = b.id",
      "select * from a left outer join (b join c on b.id = c.id) on b.id = a.id",
      // non-join and missing inner ON, cross and USING inner joins in the group
      "select * from a left join (b join c on c.k = 1) on a.id = b.id",
      "select * from a left join (b cross join c) on a.id = b.id",
      "select * from a left join b cross join c on a.id = b.id",
      "select * from a left join (b join c using (id)) on a.id = b.id",
      // right and full joins
      "select * from a right join (b join c on b.id = c.id) on a.id = b.id",
      "select * from a right join (b join c on b.id = c.id) on b.id = a.id",
      "select * from a full join (b join c on b.id = c.id) on a.id = b.id",
      "select * from a full outer join (b join c on b.id = c.id) on a.id = b.id",
      // outer joins in the group
      "select * from a left join (b left join c on b.id = c.id) on a.id = b.id",
      "select * from a left join (b left join c on b.id = c.id) on a.id = c.id",
      "select * from a left join (b left join c on b.id = c.id) on b.id = a.id",
      "select * from (a left join b on a.id = b.id) left join (c left join d on d.id = c.id) " +
         "on c.id = a.id",
      // later positions
      "select * from a left join b on a.id = b.id left join (c join d on c.id = d.id) " +
         "on b.id = c.id",
      "select * from a left join (b join c on b.id = c.id) on a.id = b.id " +
         "left join d on a.id = d.id",
      "select * from (a left join b on a.id = b.id) left join (c join d on c.id = d.id) " +
         "on a.id = c.id",
      // subqueries
      "select * from (select a.id from a left join (b join c on b.id = c.id) " +
         "on a.id = b.id) t",
      "select * from x where exists (select 1 from a left join (b join c on b.id = c.id) " +
         "on a.id = b.id)",
      "select * from x where x.id in (select a.id from a left join (b join c on b.id = c.id) " +
         "on a.id = b.id)"
   })
   void outerJoinOfNestedJoinFailsCleanly(String text) {
      assertRefused(text, "Unsupported nested join");
   }

   @ParameterizedTest
   @ValueSource(strings = {
      // an inner join ON with a non-join condition left of a right join
      "select * from a join b on b.k = 1 right join c on b.id = c.id",
      "select * from a join b on a.id = b.id and b.k = 1 right join c on b.id = c.id",
      "select * from (b join c on c.k = 1) right join a on a.id = b.id",
      // column joins only, regenerated in another order
      "select * from a join b on a.id = b.id join d on a.id = d.id right join c on b.id = c.id",
      "select * from a join (b join c on b.id = c.id) on a.id = b.id right join d on c.id = d.id",
      "select * from a right join b on a.id = b.id join c on a.id = c.id",
      "select * from a full join b on a.id = b.id join c on a.id = c.id",
      "select * from a right join b on a.id = b.id join c on b.id = c.id join d on d.id = a.id",
      // cross and USING inner joins
      "select * from (a cross join b) right join c on b.id = c.id",
      "select * from a join b using (id) right join c on b.id = c.id",
      // shapes that are generated correctly today, refused by the same rule
      "select * from a join b on a.id = b.id right join c on b.id = c.id",
      "select * from a join b on a.id = b.id right join c on a.id = c.id",
      "select * from a inner join b on a.id = b.id full join c on b.id = c.id",
      "select * from (a join b on a.id = b.id) full join c on b.id = c.id",
      "select * from a full join b on a.id = b.id join c on b.id = c.id",
      "select * from a right join b on a.id = b.id join c on b.id = c.id",
      "select * from a join b on a.id = b.id join d on b.id = d.id right join c on a.id = c.id",
      "select * from (b join c on b.id = c.id) right join a on a.id = b.id",
      // inner joins from where clause column joins, across comma-listed tables
      "select * from a right join b on a.id = b.id, c where a.id = c.id",
      "select * from a, b right join c on b.id = c.id where a.id = b.id",
      "select * from a right join b on a.id = b.id, c, d where a.id = c.id and c.id = d.id",
      "select * from a right join b on a.id = b.id, c join d on c.id = d.id where a.id = d.id",
      "select * from a right join b on a.id = b.id where a.id = b.k",
      "select * from a right join b on a.id = b.id, c where b.id = c.id",
      "select * from a full join b on a.id = b.id, c where b.id = c.id",
      // an inner join keyword in another comma-listed table
      "select * from a right join b on a.id = b.id, c join d on c.id = d.id",
      "select * from a join b on a.id = b.id, c right join d on c.id = d.id",
      // a subquery in the inner ON doesn't hide the inner join
      "select * from a join b on a.id = b.id and b.k in (select c.k from c) " +
         "right join d on b.id = d.id",
      // subqueries
      "select * from x where exists (select 1 from b join c on b.id = c.id " +
         "right join d on c.id = d.id)",
      "select * from (select a.id from a right join b on a.id = b.id, c " +
         "where a.id = c.id) t"
   })
   void rightJoinWithInnerJoinFailsCleanly(String text) {
      assertRefused(text, "Unsupported RIGHT or FULL join");
   }

   @ParameterizedTest
   @ValueSource(strings = {
      // left and inner join chains
      "select * from a left join b on a.id = b.id join c on b.id = c.id",
      "select * from a left join b on a.id = b.id join c on c.k = 1",
      "select * from a left join b on a.id = b.id inner join c on a.id = c.id",
      "select * from a join b on a.id = b.id left join c on b.id = c.id",
      "select * from a left join b on a.id = b.id left join c on b.id = c.id",
      "select * from a left join b on a.id = b.id, c where a.id = c.id",
      // nested joins under an inner join, or on the left side
      "select * from a join (b join c on b.id = c.id) on a.id = b.id",
      "select * from a join (b left join c on b.id = c.id) on a.id = b.id",
      "select * from (a inner join b on a.id = b.id) left join c on b.id = c.id",
      // derived tables are a single table
      "select * from a left join (select id, k from b) t on a.id = t.id",
      "select * from a left join (select b.id from b join c on b.id = c.id) t on a.id = t.id",
      "select * from a right join (select c.id, c.k from c join d on c.id = d.id) t " +
         "on a.id = t.id",
      // right and full joins with no inner join
      "select * from a right join b on a.id = b.id",
      "select * from a full outer join b on a.id = b.id",
      "select * from a right join b on a.id = b.id right join c on b.id = c.id",
      "select * from a left join b on a.id = b.id right join c on b.id = c.id",
      "select * from a right join b on a.id = b.id where a.k = 1",
      "select * from a, b right join c on b.id = c.id",
      // a right join in a subquery doesn't refuse the inner join of the query
      "select * from a join b on a.id = b.id where b.id in " +
         "(select c.id from c right join d on c.id = d.id)",
      "select * from a right join b on a.id = b.id where b.id in " +
         "(select c.id from c join d on c.id = d.id)"
   })
   void supportedJoinParses(String text) throws Exception {
      UniformSQL sql = parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertFalse(sql.isLossy());

      // the generated sql is accepted too
      String generated = normalize(sql.getSQLString());
      assertEquals(UniformSQL.PARSE_SUCCESS, parse(generated).getParseResult(), generated);

      UniformSQL processed = new UniformSQL();
      new SQLProcessor(processed).parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, processed.getParseResult());
   }

   @Test
   void refusedQueryKeepsOriginalSql() {
      String text = "select * from a left join (b join c on b.id = c.id) on a.id = b.id";
      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
      assertEquals(text, sql.getSQLString());
   }

   @ParameterizedTest
   @ValueSource(strings = { "inner join", "join" })
   void innerJoinConditionMovesToWhere(String type) throws Exception {
      UniformSQL sql = parse("select a.x from a " + type + " b on a.id = b.id join c on c.k = 1");
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertEquals("select a.x from a, b, c where a.id = b.id and c.k = 1",
                   normalize(sql.getSQLString()));
   }

   private static void assertRefused(String text, String message) {
      RecognitionException ex = assertThrows(RecognitionException.class, () -> parse(text));
      assertTrue(ex.getMessage().contains(message), ex.getMessage());

      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
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
