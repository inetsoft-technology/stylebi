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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77440, an outer join ON condition must join the table being joined with a table
 * that is already in the from clause before it. Any other outer join ON condition fails
 * the parse, and an accepted one is recorded with the earlier table first so the outer
 * join op preserves the right side.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLOuterJoinTableTest {
   @ParameterizedTest
   @ValueSource(strings = {
      // reporter's example, the second ON doesn't mention the joined table c
      "select a.x from a left join b on a.k = b.k left join c on a.id = b.id",
      "select a.x from a left join b on a.k = b.k left outer join c on b.id = a.id",
      "select a.x from a left join b on a.k = b.k right join c on a.id = b.id",
      "select a.x from a left join b on a.k = b.k full outer join c on a.id = b.id",
      "select a.x from a right join b on a.k = b.k right join c on a.id = b.id",
      "select a.x from a join b on a.k = b.k left join c on a.id = b.id",
      "select a.x from a left join b on a.k = b.k left join c on a.id = b.id and a.j = b.j",
      // aliased, derived and nested right operands
      "select a.x from a left join b on a.k = b.k left join c x on a.id = b.id",
      "select a.x from a left join b on a.k = b.k left join (select * from c) t on a.id = b.id",
      "select a.x from a left join b on a.k = b.k left join (c join d on c.id = d.id) on a.id = b.id",
      // comma-listed left tables and a later position
      "select a.x from a, b left join c on a.id = b.id",
      "select a.x from a left join b on a.k = b.k left join c on c.id = a.id " +
         "left join d on a.id = b.id",
      "select a.x from a left join b on a.k = b.k left join c on c.id = a.id " +
         "left join d on c.id = a.id",
      // a table that is not in the from clause, or not yet in it
      "select a.x from a left join b on a.k = b.k left join c on b.id = d.id",
      "select a.x from a left join b on a.k = b.k left join c on c.id = zz.id",
      "select a.x from a left join b on b.id = c.id left join c on c.id = a.id",
      // unqualified columns (#77439 shapes on the ANSI path). An Oracle data source in
      // its default non-ANSI mode could regenerate these as a valid where (+) join, but
      // the parse doesn't know the data source, and a failed parse runs the original sql
      "select * from a left join b on id = bid",
      "select * from a right join b on id = bid",
      "select * from a left join b on id = b.id",
      "select * from a left join b on a.id = bid",
      "select * from a full join b on b.id = id",
      // the table name where the table has an alias is not a from clause table
      "select * from a t1 left join b t2 on a.id = t2.id",
      // subquery positions share the rule
      "select * from (select a.id from a left join b on a.k = b.k left join c on a.id = b.id) t",
      "select * from a where exists (select 1 from b left join c on b.k = b.j)",
      // an ON between two earlier tables or within the joined table, and schema-qualified
      "select a.x from a left join b on a.k = b.k left join c on a.id = a.j",
      "select a.x from a left join b on a.k = b.k left join c on c.id = c.j",
      "select sch.a.x from sch.a left join sch.b on sch.a.k = sch.b.k " +
         "left join sch.c on sch.a.id = sch.b.id",
      // a correlated outer query table is not in the subquery's from clause
      "select a.x from a where exists " +
         "(select 1 from b left join c on c.id = a.id where b.id = a.id)"
   })
   void outerJoinNotOnJoinedTableFailsCleanly(String text) {
      RecognitionException ex = assertThrows(RecognitionException.class, () -> parse(text));
      assertTrue(ex.getMessage().contains("Unsupported outer join condition"), ex.getMessage());

      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      // controls, the ON names the joined table and an earlier one
      "select a.x from a left join b on a.id = b.id | a.id *= b.id",
      "select a.x from a left join b on b.id = a.id | a.id *= b.id",
      "select a.x from a right join b on b.id = a.id | a.id =* b.id",
      "select a.x from a full join b on b.id = a.id | a.id *=* b.id",
      "select a.x from a left join b on a.k = b.k left join c on c.id = b.id | " +
         "a.k *= b.k, b.id *= c.id",
      "select a.x from a left join b on a.k = b.k right join c on a.id = c.id | " +
         "a.k *= b.k, a.id =* c.id",
      "select a.x from a left join b on a.k = b.k left join c on c.id = a.id | " +
         "a.k *= b.k, a.id *= c.id",
      "select a.x from (a left join b on a.k = b.k) left join c on c.id = b.id | " +
         "a.k *= b.k, b.id *= c.id",
      "select a.x from a left join b on a.id = b.id and b.k = a.k | a.id *= b.id, a.k *= b.k",
      // nested right operands are oriented by the joined tables, not by the last table
      "select a.x from a left join (b left join c on c.id = b.id) on b.id = a.id | " +
         "b.id *= c.id, a.id *= b.id",
      "select a.x from a left join b left join c on c.id = b.id on b.id = a.id | " +
         "b.id *= c.id, a.id *= b.id",
      "select a.x from a right join (b left join c on c.id = b.id) on c.id = a.id | " +
         "b.id *= c.id, a.id =* c.id",
      "select a.x from a left join b on a.k = b.k left join (c join d on c.id = d.id) " +
         "on c.id = a.id | a.k *= b.k, c.id = d.id, a.id *= c.id",
      // a nested right operand after a parenthesized left operand, and a full join chain
      "select a.x from (a left join b on a.id = b.id) left join (c left join d on d.id = c.id) " +
         "on c.id = a.id | a.id *= b.id, c.id *= d.id, a.id *= c.id",
      "select a.x from a full join b on b.k = a.k full join c on c.id = b.id | " +
         "a.k *=* b.k, b.id *=* c.id",
      // unquoted identifiers are case-insensitive
      "select o.x from Orders o left join Customers c on O.cid = C.id | O.cid *= C.id",
      "select a.x from a left join b on a.id = b.id left join c on C.id = A.id | " +
         "a.id *= b.id, A.id *= C.id",
      // self join, the joined alias and the earlier table name are the same table
      "select a.x from a left join a y on a.id = y.pid | a.id *= y.pid",
      "select a.x from a left join a y on y.pid = a.id | a.id *= y.pid",
      "select x.id from a x left join a y on y.pid = x.id | x.id *= y.pid",
      "select t1.id from a t1 left join a t2 on t1.id = t2.pid | t1.id *= t2.pid",
      "select a.id from a left join a t2 on a.id = t2.pid | a.id *= t2.pid",
      // schema-qualified tables
      "select x.id from sch.a x left join sch.b y on y.id = x.id | x.id *= y.id",
      "select sch.a.id from sch.a left join sch.b on sch.b.id = sch.a.id | sch.a.id *= sch.b.id",
      "select sa.a.id from sa.a left join SA.B on SA.A.id = sa.b.id | SA.A.id *= sa.b.id",
      // quoted, bracket and backtick qualifiers, the parser removes the quotes
      "select * from \"A\" left join \"B\" on \"B\".\"id\" = \"A\".\"id\" | A.id *= B.id",
      "select * from [s].[a] left join [s].[b] on [s].[b].[id] = [s].[a].[id] | " +
         "s.a.id *= s.b.id",
      "select * from `a` left join `b` on `b`.`id` = `a`.`id` | a.id *= b.id",
      "select * from a \"my t\" left join b \"my u\" on \"my u\".id = \"my t\".id | " +
         "\"my t\".id *= \"my u\".id",
      // a comma-listed earlier table
      "select x.id from x, a left join b on b.id = x.id | x.id *= b.id",
      // inner joins are not checked and stay in the where clause
      "select a.x from a join b on a.k = b.k join c on a.id = b.id | a.k = b.k, a.id = b.id",
      "select a.x from a inner join b on b.id = a.id | b.id = a.id"
   })
   void outerJoinOnJoinedTableIsOriented(String text, String expected) throws Exception {
      UniformSQL sql = parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertEquals(expected, joins(sql));

      // the generated sql parses back to the same query. A right join to a nested join
      // is generated as a left join with the ON columns in the other order, so compare
      // from the second generation on.
      String generated = normalize(parse(normalize(sql.getSQLString())).getSQLString());
      assertEquals(generated, normalize(parse(generated).getSQLString()));
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select a.x from a left join b on a.k = b.k left join c on c.id = b.id | " +
         "select a.x from (a LEFT OUTER JOIN b ON a.k = b.k ) LEFT OUTER JOIN c ON b.id = c.id",
      // a left join to a nested join is generated as a right join in text order (#77475)
      "select a.x from a left join (b left join c on c.id = b.id) on b.id = a.id | " +
         "(b LEFT OUTER JOIN c ON b.id = c.id ) RIGHT OUTER JOIN a ON a.id = b.id",
      "select a.x from a left join a y on a.id = y.pid | a LEFT OUTER JOIN a y ON a.id = y.pid",
      "select a.x from a right join b on b.id = a.id | a RIGHT OUTER JOIN b ON a.id = b.id",
      // the nested left join stays a left join, it used to be generated as a right join
      "select a.x from (a left join b on a.id = b.id) left join (c left join d on d.id = c.id) " +
         "on c.id = a.id | (a LEFT OUTER JOIN b ON a.id = b.id ) LEFT OUTER JOIN " +
         "(c LEFT OUTER JOIN d ON c.id = d.id ) ON a.id = c.id",
      // an unaliased quoted table keeps its quotes in the from clause table, and the
      // join column's table doesn't, they must still resolve to the same table
      "select * from \"my a\" left join \"my b\" on \"my b\".\"id\" = \"my a\".\"id\" | " +
         "select * from \"my a\" LEFT OUTER JOIN \"my b\" ON \"my a\".id = \"my b\".id"
   })
   void outerJoinOnJoinedTableGeneratesJoin(String text, String expected) throws Exception {
      String generated = normalize(parse(text).getSQLString());
      assertTrue(generated.contains(expected), generated);
      assertFalse(generated.contains(" where "), generated);
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
      // Bug #77434 refuses a nested join on the right of an outer join without a data
      // source, and accepts it with one when the generated sql has the same joins
      sql.setDataSource(GenericJDBCDataSource.create());
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }
}
