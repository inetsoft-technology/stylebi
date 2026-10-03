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

import antlr.SemanticException;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77495, a chained CROSS JOIN (a cross join b cross join c, or a cross join after or
 * before another join) and a parenthesized join with no join after it (((a join b on ..))
 * left join c on .., or a parenthesized from clause) failed to parse. A cross join is now a
 * join of a join chain whose right operand is one table, recorded left associative with no
 * join condition and regenerated as a comma item, and the join after a parenthesized join
 * is optional. Every refusal of the joins inside still applies. A join with no join
 * condition followed by a RIGHT or FULL join (a join b right join c on ..) is read as
 * (a join b) right join c by MySQL and SQLite, but was recorded as a join (b right join c),
 * so it now fails the parse, including after a cross join chain. In a from clause with a
 * cross join chain or a parenthesized join with no join after it, at any query level of the
 * statement, a LEFT, RIGHT or FULL join in a group after another from item fails the parse
 * too, since SQLite reads its regenerated sql differently (SQLHelper writes a LEFT join over
 * a nested join as a RIGHT join), and so does every RIGHT or FULL join, at the start of its
 * from clause too, since SQLHelper writes it after the other groups when it leaves the text
 * order. So does a LEFT join at the start of its from clause whose right side a later join
 * condition joins, since SQLHelper then writes the inner join first and the LEFT join as a
 * RIGHT join after it, and every outer join of a where clause (*=, =* or (+)).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 UniformSQLCrossJoinChainTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLCrossJoinChainTest {
   // a data source with a real driver and URL needs the credential service, and the JDBC
   // driver types of Config to pick its SQL helper
   @Configuration
   @Import(CredentialService.class)
   static class Config {
      @Bean
      inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }
   }

   private static final String[] DATA_SOURCES = {
      "h2", "h2-ansi", "mysql", "mysql-ansi", "postgresql", "postgresql-ansi", "oracle",
      "oracle-ansi", "sql server", "mongo", "mongo-ansi"
   };

   private static final String COLS3 = "select a.id, b.id, c.id ";
   private static final String COLS4 = "select a.id, b.id, c.id, d.id ";
   private static final String COLS5 = "select a.id, b.id, c.id, d.id, e.id ";
   // a RIGHT join at the start of its from clause, with the new syntax at another level
   // (accepted in review r2-r5, refused since review r5 B6)
   private static final String RIGHT_FIRST_EXISTS = "select c.id, d.id from c right join d " +
      "on c.id = d.id where exists (select 1 from a cross join b cross join e)";
   private static final String RIGHT_FIRST_COMMA = "select c.id, d.id, x.id from c right join d " +
      "on c.id = d.id, x where exists (select 1 from ((a join b on a.id = b.id)))";
   // a LEFT join at the start of its from clause, with the new syntax (review r4)
   private static final String LEFT_FIRST_CHAIN = "select a.id, b.id, c.id, x.id from x cross join a " +
      "cross join b left join c on x.id = c.id";
   private static final String LEFT_FIRST_NESTED = "select a.id, b.id, c.id, e.id from " +
      "((a join b on a.id = b.id)) " +
      "left join (c join e on c.id = e.id) on b.id = c.id";
   // refused since review r6 B7, the later LEFT join condition names b
   private static final String LEFT_FIRST_CHAIN_LEFT = COLS4 + "from a left join b on a.id = b.id " +
      "cross join c left join d on b.id = d.id";
   private static final String LEFT_FIRST_EXISTS = "select c.id, d.id from d left join c " +
      "on d.id = c.id where exists (select 1 from a cross join b cross join x)";
   // a LEFT join at the start of its from clause before a group whose join condition
   // doesn't name the table it joins (join e on b.id = a.id), so SQLHelper leaves the
   // text order and writes the outer join after the inner join group (review r5 B6)
   private static final String LEFT_FIRST_FALLBACK = "select a.id, b.id, c.id, d.id, e.id from " +
      "d left join c on d.id = c.id, ((a join b on a.id = b.id join e on b.id = a.id))";
   // the same over a nested join, which SQLHelper writes as a RIGHT join group, (c join e)
   // right join d. That group is still written first (review r5)
   private static final String LEFT_FIRST_NESTED_FALLBACK = "select a.id, b.id, c.id, d.id, e.id, " +
      "x.id from d left join (c join e on c.id = e.id) on d.id = c.id, ((a join b on a.id = b.id " +
      "join x on b.id = a.id))";
   // a LEFT join at the start of its from clause with later joins that don't join its right
   // side in a column join, before a group that makes SQLHelper leave the text order. The
   // inner joins are written first and the LEFT join after them, which joins the same rows
   // (review r6)
   private static final String COLS6 = "select a.id, b.id, c.id, d.id, e.id, x.id ";
   private static final String FALLBACK_GROUP = ", ((a join b on a.id = b.id join x on b.id = a.id))";
   private static final String LEFT_FIRST_LATER_INNER = COLS6 + "from d left join c on d.id = c.id " +
      "join e on d.id = e.id" + FALLBACK_GROUP;
   private static final String LEFT_FIRST_LATER_LEFT = COLS6 + "from d left join c on d.id = c.id " +
      "left join e on d.id = e.id" + FALLBACK_GROUP;
   // a filter on the right side in a later inner join condition, and a column join to it in
   // the where clause, are where conditions, which apply after every join
   private static final String LEFT_FIRST_LATER_FILTER = COLS6 + "from d left join c " +
      "on d.id = c.id join e on d.id = e.id and c.id > 1" + FALLBACK_GROUP;
   private static final String LEFT_FIRST_WHERE_JOIN = COLS6 + "from d left join c on d.id = c.id " +
      "cross join e" + FALLBACK_GROUP + " where e.id = c.id";
   private static final String RIGHT_FIRST_IN_SUBQUERY = "select x.id from x cross join a " +
      "cross join b where exists (select 1 from c right join d on c.id = d.id where d.id = x.id)";

   /**
    * Accepted queries: the text, the joins recorded by the parser, and the sql regenerated
    * with an H2 data source.
    */
   static Stream<Arguments> accepted() {
      return Stream.of(
         // the reported shapes
         Arguments.of("select * from a cross join b cross join c", "",
                      "select * from a, b, c"),
         Arguments.of("select * from ((a join b on a.id = b.id)) left join c on b.id = c.id",
                      "a.id = b.id; b.id *= c.id",
                      "select * from (a INNER JOIN b ON a.id = b.id ) LEFT OUTER JOIN c ON b.id = c.id"),
         // cross join chains of 3 and more, mixed with other joins
         Arguments.of("select * from a cross join b cross join c cross join d", "",
                      "select * from a, b, c, d"),
         Arguments.of("select * from a CROSS JOIN b Cross Join c", "", "select * from a, b, c"),
         Arguments.of(COLS3 + "from a cross join b join c on b.id = c.id", "b.id = c.id",
                      COLS3 + "from a, b, c where b.id = c.id"),
         Arguments.of(COLS3 + "from a join b on a.id = b.id cross join c", "a.id = b.id",
                      COLS3 + "from a, b, c where a.id = b.id"),
         Arguments.of(COLS3 + "from a cross join b left join c on b.id = c.id", "b.id *= c.id",
                      COLS3 + "from b LEFT OUTER JOIN c ON b.id = c.id , a"),
         Arguments.of(COLS3 + "from a cross join b left join c on a.id = c.id", "a.id *= c.id",
                      COLS3 + "from a LEFT OUTER JOIN c ON a.id = c.id , b"),
         Arguments.of(COLS3 + "from a left join b on a.id = b.id cross join c", "a.id *= b.id",
                      COLS3 + "from a LEFT OUTER JOIN b ON a.id = b.id , c"),
         Arguments.of(COLS4 + "from a join b on a.id = b.id cross join c left join d on a.id = d.id",
                      "a.id = b.id; a.id *= d.id",
                      COLS4 + "from (a INNER JOIN b ON a.id = b.id ) LEFT OUTER JOIN d ON a.id = d.id , c"),
         Arguments.of(COLS4 + "from a cross join b cross join c join d on c.id = d.id", "c.id = d.id",
                      COLS4 + "from a, b, c, d where c.id = d.id"),
         // redundant parentheses and a parenthesized from clause
         Arguments.of("select * from (a join b on a.id = b.id)", "a.id = b.id",
                      "select * from a, b where a.id = b.id"),
         Arguments.of("select * from ((a join b on a.id = b.id))", "a.id = b.id",
                      "select * from a, b where a.id = b.id"),
         Arguments.of("select * from ((a join b on a.id = b.id) left join c on b.id = c.id)",
                      "a.id = b.id; b.id *= c.id",
                      "select * from (a INNER JOIN b ON a.id = b.id ) LEFT OUTER JOIN c ON b.id = c.id"),
         Arguments.of("select * from (((a left join b on a.id = b.id))) left join c on a.id = c.id",
                      "a.id *= b.id; a.id *= c.id",
                      "select * from (a LEFT OUTER JOIN b ON a.id = b.id ) LEFT OUTER JOIN c ON a.id = c.id"),
         Arguments.of(COLS3 + "from ((a join b on a.id = b.id)), c where b.id = c.id",
                      "a.id = b.id; b.id = c.id",
                      COLS3 + "from a, b, c where a.id = b.id and b.id = c.id"),
         Arguments.of(COLS4 + "from ((a join b on a.id = b.id)) left join ((c join d on c.id = d.id)) " +
                         "on b.id = c.id",
                      "a.id = b.id; c.id = d.id; b.id *= c.id",
                      COLS4 + "from (a INNER JOIN b ON a.id = b.id ) LEFT OUTER JOIN " +
                         "(c INNER JOIN d ON c.id = d.id ) ON b.id = c.id"),
         Arguments.of("select * from (a cross join b) cross join c", "", "select * from a, b, c"),
         Arguments.of("select * from ((a cross join b cross join c))", "", "select * from a, b, c"),
         // a comma item before a cross join chain, and subqueries
         Arguments.of("select x.id from x, a cross join b cross join c", "",
                      "select x.id from x, a, b, c"),
         Arguments.of("select x.id from x where exists (select 1 from a cross join b cross join c " +
                         "where a.id = x.id)", "",
                      "select x.id from x where EXISTS ( select 1 from a, b, c where a.id = x.id)"),
         Arguments.of("select t.id from (select a.id from a cross join b cross join c) t", "",
                      "select t.id from ( select a.id from a, b, c) t"),
         Arguments.of("select x.id from x where x.id in (select b.id from ((a join b on a.id = b.id)) " +
                         "left join c on b.id = c.id)", "",
                      "select x.id from x where x.id IN ( select b.id from (a INNER JOIN b ON a.id = b.id ) " +
                         "LEFT OUTER JOIN c ON b.id = c.id)"),
         // a parenthesized join on the right of a join with no join condition is explicit
         Arguments.of(COLS3 + "from a join (b right join c on b.id = c.id)", "b.id =* c.id",
                      COLS3 + "from b RIGHT OUTER JOIN c ON b.id = c.id , a"),
         Arguments.of(COLS3 + "from a join ((b right join c on b.id = c.id))", "b.id =* c.id",
                      COLS3 + "from b RIGHT OUTER JOIN c ON b.id = c.id , a"),
         Arguments.of("select a.id, c.id, t.id from a join ((select id from b) t right join c " +
                         "on t.id = c.id)", "t.id =* c.id",
                      "select a.id, c.id, t.id from ( select id from b) t RIGHT OUTER JOIN c " +
                         "ON t.id = c.id , a"),
         // an ON-less join followed by a LEFT join is unchanged
         Arguments.of(COLS3 + "from a join b left join c on b.id = c.id", "b.id *= c.id",
                      COLS3 + "from b LEFT OUTER JOIN c ON b.id = c.id , a"),
         // a LEFT join at the start of its from clause in a statement with the new syntax,
         // including over a nested join, which SQLHelper writes as a RIGHT join (review r4)
         Arguments.of(LEFT_FIRST_CHAIN, "x.id *= c.id",
                      "select a.id, b.id, c.id, x.id from x LEFT OUTER JOIN c ON x.id = c.id , a, b"),
         Arguments.of(LEFT_FIRST_NESTED, "a.id = b.id; c.id = e.id; b.id *= c.id",
                      "select a.id, b.id, c.id, e.id from (a INNER JOIN b ON a.id = b.id ) LEFT OUTER " +
                         "JOIN (c INNER JOIN e ON c.id = e.id ) ON b.id = c.id"),
         Arguments.of(LEFT_FIRST_EXISTS, "d.id *= c.id",
                      "select c.id, d.id from d LEFT OUTER JOIN c ON d.id = c.id where EXISTS " +
                         "( select 1 from a, b, x)"),
         // written after the inner join group and a comma, it still joins the same rows,
         // since its join condition names only d and c (review r5)
         Arguments.of(LEFT_FIRST_FALLBACK, "d.id *= c.id; a.id = b.id; b.id = a.id",
                      "select a.id, b.id, c.id, d.id, e.id from a INNER JOIN b ON a.id = b.id AND " +
                         "b.id = a.id , d LEFT OUTER JOIN c ON d.id = c.id , e"),
         // later joins that don't join the right side in a column join, written after the
         // inner joins when SQLHelper leaves the text order (review r6)
         Arguments.of(LEFT_FIRST_LATER_INNER, "d.id *= c.id; d.id = e.id; a.id = b.id; b.id = a.id",
                      COLS6 + "from (d INNER JOIN e ON d.id = e.id ) LEFT OUTER JOIN c ON d.id = c.id , " +
                         "a INNER JOIN b ON a.id = b.id AND b.id = a.id , x"),
         Arguments.of(LEFT_FIRST_LATER_LEFT, "d.id *= c.id; d.id *= e.id; a.id = b.id; b.id = a.id",
                      COLS6 + "from a INNER JOIN b ON a.id = b.id AND b.id = a.id , (d LEFT OUTER JOIN " +
                         "c ON d.id = c.id ) LEFT OUTER JOIN e ON d.id = e.id , x"),
         Arguments.of(LEFT_FIRST_LATER_FILTER, "d.id *= c.id; d.id = e.id; a.id = b.id; b.id = a.id",
                      COLS6 + "from (d INNER JOIN e ON d.id = e.id ) LEFT OUTER JOIN c ON d.id = c.id , " +
                         "a INNER JOIN b ON a.id = b.id AND b.id = a.id , x where c.id > 1"),
         Arguments.of(LEFT_FIRST_WHERE_JOIN, "d.id *= c.id; a.id = b.id; b.id = a.id; e.id = c.id",
                      COLS6 + "from a INNER JOIN b ON a.id = b.id AND b.id = a.id , d LEFT OUTER JOIN " +
                         "c ON d.id = c.id , e, x where e.id = c.id")
      );
   }

   @ParameterizedTest
   @MethodSource("accepted")
   void acceptedShapeRegenerates(String text, String joins, String expected) throws Exception {
      JDBCDataSource ds = dataSource("h2");
      UniformSQL sql = parse(text, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      assertEquals(joins, joins(sql), text);

      String generated = regenerate(sql);
      assertEquals(expected, generated);
      assertRoundTrip(generated, ds);
      assertTrue(XUtil.isQueryMergeable(query(text, ds)), text);
   }

   // every dialect and the ansi join option accept the shapes, and their sql round trips
   @ParameterizedTest
   @MethodSource("accepted")
   void acceptedShapeRoundTripsOnEveryDialect(String text) throws Exception {
      for(String type : DATA_SOURCES) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql;

         try {
            sql = parse(text, ds);
         }
         catch(SemanticException ex) {
            // Oracle without ansi join writes (+) joins, which can't keep a RIGHT join
            // mixed with an inner or cross join, or a nested join on the right of an outer
            // join. The join order check refuses these, as with one pair of parentheses
            assertTrue("oracle".equals(type) && ORACLE_REFUSED.contains(text),
                       type + ": " + text + ": " + ex.getMessage());
            assertTrue(ex.getMessage().startsWith("Unsupported"), ex.getMessage());
            continue;
         }

         assertFalse("oracle".equals(type) && ORACLE_REFUSED.contains(text),
                     "oracle should refuse: " + text);

         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type + ": " + text);
         assertFalse(sql.isLossy(), type + ": " + text);
         assertRoundTrip(regenerate(sql), ds);
      }
   }

   // the accepted shapes Oracle without ansi join refuses
   private static final Set<String> ORACLE_REFUSED = Set.of(
      COLS4 + "from ((a join b on a.id = b.id)) left join ((c join d on c.id = d.id)) on b.id = c.id",
      COLS3 + "from a join (b right join c on b.id = c.id)",
      COLS3 + "from a join ((b right join c on b.id = c.id))",
      "select a.id, c.id, t.id from a join ((select id from b) t right join c on t.id = c.id)",
      LEFT_FIRST_NESTED);

   @Test
   void oracleWritesTheOuterJoinAfterACrossJoinInWhere() throws Exception {
      UniformSQL sql = parse(COLS3 + "from a cross join b left join c on a.id = c.id",
                             dataSource("oracle"));
      assertEquals("select A.ID, B.ID, C.ID from a, b, c where a.id = c.id(+)", regenerate(sql));
   }

   // the refusals of the joins of a cross join chain or inside parentheses
   static Stream<String> refused() {
      return Stream.of(
         // a cross join mixed with a RIGHT or FULL join (#77434)
         "select * from a cross join b right join c on b.id = c.id",
         "select * from a cross join b full join c on b.id = c.id",
         "select * from a cross join b cross join c right join d on c.id = d.id",
         "select * from a left join b on a.id = b.id cross join c right join d on c.id = d.id",
         "select * from ((a cross join b)) right join c on b.id = c.id",
         "select * from ((a join b on a.id = b.id)) cross join c right join d on c.id = d.id",
         // a natural join (#77435), at any depth of parentheses
         "select * from a cross join b natural join c",
         "select * from ((d natural join c))",
         "select * from ((d natural join c)) left join e on c.id = e.id",
         // a USING join whose left operand has more than one table (#77490)
         "select * from a cross join b join c using (id)",
         "select * from ((a join b on a.id = b.id)) join c using (id)",
         // a nested join on the right of a LEFT join (#77515)
         "select * from a left join (b cross join c) on a.id = b.id",
         "select * from a left join b cross join c on a.id = b.id",
         // an outer join ON that names both sides of a cross join (#77440)
         "select * from a cross join b left join c on a.id = c.id and b.id = c.id",
         "select * from a cross join b cross join c left join d on b.id = d.id and c.id = d.id",
         // an outer join with no ON after a cross join (#77486)
         "select * from a cross join b left join c",
         // a cross join takes no join condition or parenthesized operand
         "select * from a cross join b on a.id = b.id",
         "select * from a cross join (b join c on b.id = c.id)",
         "select * from (a join b on a.id = b.id) x",
         // no join follows a parenthesized join operand
         COLS5 + "from a join b on a.id = b.id join (c right join d on c.id = d.id) cross join e",
         // a UNION doesn't parse in a subquery or at the top, so a RIGHT join and the new
         // syntax in different UNION branches fail to parse (review r3)
         "select c.id, d.id, x.id from x, c right join d on c.id = d.id where x.id in " +
            "(select p.id from p union select q.id from q cross join r cross join e)",
         "select a.id from a cross join b cross join e where a.id in (select p.id from p union " +
            "select d.id from x, c right join d on c.id = d.id)",
         "select p.id from p cross join q cross join r union select d.id from x, c right join d " +
            "on c.id = d.id",
         // a doubly parenthesized derived table doesn't parse as a join operand
         "select a.id, c.id, t.id from a join ((select id from b)) t right join c on t.id = c.id"
      );
   }

   // a join with no join condition followed by a RIGHT or FULL join, after a cross join
   // chain or not
   static Stream<String> onlessJoinBeforeRightJoin() {
      return Stream.of(
         COLS4 + "from a cross join b join c right join d on c.id = d.id",
         COLS4 + "from a cross join b join c full join d on c.id = d.id",
         COLS5 + "from a join b on a.id = b.id cross join c join d right join e on d.id = e.id",
         COLS3 + "from a join b right join c on b.id = c.id",
         COLS3 + "from a inner join b full outer join c on b.id = c.id",
         COLS4 + "from a join b left join c on b.id = c.id right join d on c.id = d.id",
         COLS4 + "from a join b join c on b.id = c.id right join d on c.id = d.id",
         COLS3 + "from x where exists (select 1 from a join b right join c on b.id = c.id)",
         // the nested join starts with a derived table, not a parenthesized join, so its
         // first token ( doesn't make it explicit (review r1)
         "select a.id, b.id, d.id, t.id from a cross join b join (select id from c) t " +
            "right join d on t.id = d.id",
         "select a.id, b.id, d.id, t.id from a cross join b join (select id from c) t " +
            "full join d on t.id = d.id",
         "select a.id, b.id, d.id, e.id, t.id from a join b on a.id = b.id cross join e " +
            "join (select id from c) t right join d on t.id = d.id",
         "select a.id, c.id, t.id from a join (select id from b) t right join c on t.id = c.id",
         "select a.id, c.id, t.id from a inner join (select id from b) t full join c on t.id = c.id",
         "select a.id, c.id, d.id, t.id from a join (select id from b) as t left join c " +
            "on t.id = c.id right join d on c.id = d.id",
         // the same nested join inside an explicitly parenthesized operand, the parentheses
         // around it don't exempt the join inside them
         "select a.id, b.id, d.id, t.id from a join ((b join (select id from c) t " +
            "right join d on t.id = d.id))",
         // refused though every reading is a x b x (c right join d): the R join recorded
         // inside the parenthesized operand of the second join is seen by the first
         COLS4 + "from a join b join (c right join d on c.id = d.id)",
         // an explicitly parenthesized operand of an earlier join doesn't make a later
         // operand explicit (review r2 m5)
         "select a.id, b.id, c.id, e.id, t.id from a join (b right join c on b.id = c.id) " +
            "on a.id = b.id join (select id from d) t right join e on t.id = e.id",
         "select a.id, b.id, c.id, e.id, t.id from a join ((b right join c on b.id = c.id)) " +
            "on a.id = b.id join (select id from d) t right join e on t.id = e.id",
         "select a.id, b.id, c.id, e.id from a join (b right join c on b.id = c.id) " +
            "on a.id = b.id join d right join e on d.id = e.id"
      );
   }

   /**
    * A RIGHT or FULL join in a group of joined tables after another from item, a comma
    * item or the operand of another join with or without a join condition, in a from
    * clause with a cross join chain or a parenthesized join with no join after it (review
    * r2, r3).
    * SQLHelper writes the group as a comma item after another group, a INNER JOIN b ON ..
    * , c RIGHT OUTER JOIN d ON .., or moves the tables before it after it, and SQLite
    * joins a comma and a JOIN left to right, so the regenerated sql returns different
    * rows there. These failed to parse before, and still do.
    */
   static Stream<String> rightJoinAfterFromItem() {
      return Stream.of(
         // the reviewer's shapes
         COLS5 + "from a join b on a.id = b.id cross join e join (c right join d on c.id = d.id)",
         COLS5 + "from a cross join e join b on a.id = b.id join (c right join d on c.id = d.id)",
         COLS4 + "from ((a join b on a.id = b.id)) join (c right join d on c.id = d.id)",
         COLS4 + "from a join b on a.id = b.id, (c right join d on c.id = d.id)",
         COLS4 + "from (a join b on a.id = b.id), (c full join d on c.id = d.id)",
         // the parenthesized join in another comma item
         COLS5 + "from a join b on a.id = b.id join (c right join d on c.id = d.id), " +
            "((e join x on e.id = x.id))",
         // a comma item with a RIGHT or FULL join that is not parenthesized
         COLS5 + "from a cross join e cross join x, c right join d on c.id = d.id",
         COLS4 + "from ((a join b on a.id = b.id)), c right join d on c.id = d.id",
         COLS5 + "from (a join b on a.id = b.id), (c join d on c.id = d.id) full join e " +
            "on d.id = e.id",
         // a group with an inner join before the RIGHT join, and two RIGHT join groups
         COLS5 + "from (a join b on a.id = b.id), ((c join d on c.id = d.id) right join e " +
            "on d.id = e.id)",
         COLS4 + "from (a right join b on a.id = b.id), (c right join d on c.id = d.id)",
         // refused though SQLHelper writes the group first: no join of an earlier from item
         // has a join condition. These failed to parse before
         COLS4 + "from a cross join b join (c right join d on c.id = d.id)",
         COLS4 + "from x, ((c right join d on c.id = d.id))",
         COLS4 + "from a cross join b, (c right join d on c.id = d.id)",
         // in subqueries
         "select x.id from x where exists (select 1 from ((a join b on a.id = b.id)), " +
            "(c right join d on c.id = d.id))",
         "select t.id from (select a.id from a join b on a.id = b.id cross join e " +
            "join (c right join d on c.id = d.id)) t",
         // the group nested in a join with a join condition: SQLHelper writes it as a comma
         // item when the condition doesn't join it to the tables before it, so no join
         // condition exempts it (review r3 B3). These failed to parse before
         COLS5 + "from a join b on a.id = b.id cross join e join (c right join d on c.id = d.id) " +
            "on a.id = e.id",
         COLS5 + "from a join b on a.id = b.id cross join e join (c right join d on c.id = d.id) " +
            "on b.id = e.id",
         COLS4 + "from ((a join b on a.id = b.id)) join (c right join d on c.id = d.id) " +
            "on a.id = b.id",
         "select a.id, b.id, c.id, d.id, x.id from (a join b on a.id = b.id), c join " +
            "(d right join x on d.id = x.id) on c.id = c.id",
         // refused though SQLHelper nests the group when the condition joins it
         COLS5 + "from a join b on a.id = b.id cross join e join (c right join d on c.id = d.id) " +
            "on a.id = c.id",
         COLS4 + "from ((a join b on a.id = b.id)) inner join (c full join d on c.id = d.id) " +
            "on b.id = c.id"
      );
   }

   /**
    * A RIGHT or FULL join after another from item at one query level, and the new syntax at
    * another level of the statement: the outer query, an EXISTS, IN or scalar subquery, a
    * subquery in a join condition, a derived table or a UNION branch (review r3 B4). The
    * from clause with the RIGHT join parses alone, but SQLite reads its regenerated sql
    * differently. The statement failed to parse before, so it still does.
    */
   static Stream<String> rightJoinElsewhereInStatement() {
      return Stream.of(
         // the new syntax in a subquery, the RIGHT join in the outer query
         X_RIGHT + "where exists (select 1 from p cross join q cross join r)",
         X_RIGHT + "where exists (select 1 from ((p join q on p.id = q.id)))",
         X_RIGHT + "where x.id in (select p.id from p cross join q cross join r)",
         X_RIGHT + "where x.id = (select max(p.id) from ((p join q on p.id = q.id)))",
         "select c.id, d.id, x.id from x, c full join d on c.id = d.id where exists " +
            "(select 1 from p cross join q cross join r)",
         GROUP_RIGHT + "where exists (select 1 from p cross join q cross join r)",
         GROUP_RIGHT + "where exists (select 1 from (p join q on p.id = q.id))",
         GROUP_RIGHT + "where exists (select 1 from ((p join q on p.id = q.id)) left join r " +
            "on q.id = r.id)",
         GROUP_RIGHT + "where exists (select 1 from p join q on p.id = q.id cross join r)",
         COLS4 + "from a join b on a.id = b.id join (c right join d on c.id = d.id), " +
            "(select p.id from p cross join q cross join r) t",
         // in a subquery in a join condition, of the RIGHT join or of an earlier join
         COLS4 + "from a join b on a.id = b.id and a.id in (select p.id from p cross join q " +
            "cross join r) join (c right join d on c.id = d.id)",
         "select c.id, d.id, x.id from x, c right join d on c.id = d.id and c.id in " +
            "(select p.id from p cross join q cross join r)",
         // in a scalar subquery of the select list, parsed before the from clause
         "select (select count(*) from p cross join q cross join r), c.id, d.id, x.id " +
            "from x, c right join d on c.id = d.id",
         // the new syntax in the outer query, the RIGHT join in a subquery
         "select x.id from x cross join p cross join q where exists (select 1 from a join b " +
            "on a.id = b.id join (c right join d on c.id = d.id) where a.id = x.id or d.id = x.id)",
         "select a.id, (select max(d.id) from x, c right join d on c.id = d.id) from a " +
            "cross join b cross join e",
         "select a.id from a cross join b cross join e where a.id = (select max(d.id) from x, " +
            "c right join d on c.id = d.id)",
         "select t.id, x.id, e.id from (select d.id from a join b on a.id = b.id join " +
            "(c right join d on c.id = d.id)) t cross join x cross join e",
         "select a.id, b.id, t.id from (select d.id from x, c right join d on c.id = d.id) t " +
            "cross join a cross join b",
         // both in different subqueries
         "select y.id from y where exists (select 1 from x, c right join d on c.id = d.id) " +
            "and exists (select 1 from ((p join q on p.id = q.id)))"
      );
   }

   /**
    * A LEFT join after another from item, at any query level, in a statement with the new
    * syntax at any level (review r4, verify r3 B5). SQLHelper writes t LEFT JOIN (nested
    * join) as (nested join) RIGHT OUTER JOIN t, the shape of rightJoinAfterFromItem, and
    * SQLite reads it differently. Every LEFT join after another from item is refused, not
    * only one over a nested join. These failed to parse before, so they still do.
    */
   static Stream<String> leftJoinAfterFromItem() {
      return Stream.of(
         // B5: a LEFT join over a nested join after another from item (L04, L13, L12, L11)
         COLS5 + "from ((a join b on a.id = b.id)), d left join (c join e on c.id = e.id) " +
            "on d.id = c.id",
         COLS5 + "from x join a on x.id = a.id cross join b, d left join (e join c " +
            "on e.id = c.id) on d.id = c.id",
         COLS4 + "from x full join a on x.id = a.id cross join c, d left join (e join y " +
            "on e.id = y.id left join b on y.id = b.id) on d.id = b.id",
         "select a.id, b.id, c.id, d.id, e.id, x.id, y.id from d join a on d.id = a.id " +
            "full join y on a.id = y.id left join e on d.id = e.id, ((b left join " +
            "(x join c on x.id = c.id) on b.id = x.id))",
         // B5 with the new syntax in a subquery, a from clause main parses (L06)
         COLS5 + "from a join b on a.id = b.id, d left join (c join e on c.id = e.id) " +
            "on d.id = c.id where exists (select 1 from ((p join q on p.id = q.id)))",
         // and in the outer query, the LEFT join in a subquery
         "select x.id from x cross join p cross join q where exists (select 1 from a, " +
            "d left join (c join e on c.id = e.id) on d.id = c.id where a.id = x.id)",
         // a LEFT join after a comma item, over one table (formerly accepted)
         "select a.id, b.id, c.id, e.id, x.id from x, a cross join b cross join e left join c " +
            "on e.id = c.id",
         "select a.id, b.id, c.id, x.id from x, ((a join b on a.id = b.id)) left join c " +
            "on b.id = c.id",
         COLS4 + "from a cross join b cross join c, d left join e on d.id = e.id",
         "select a.id, b.id, c.id, x.id from x, a cross join b left join c on b.id = c.id",
         "select a.id, c.id, d.id, x.id from x, a left join c on a.id = c.id cross join d",
         "select a.id, c.id, d.id, e.id, x.id from x, ((c join d on c.id = d.id)) left join e " +
            "on d.id = e.id, a",
         "select a.id, b.id, t.id, x.id from x, (select p.id from p cross join q cross join r) t " +
            "left join a on t.id = a.id, b",
         "select a.id, c.id, x.id from x, a left join c on a.id = c.id where exists " +
            "(select 1 from p cross join q cross join r)",
         // a LEFT join after an ON-less join of a cross join chain (formerly accepted, the
         // refuter's controls)
         COLS4 + "from a cross join b join c left join d on c.id = d.id",
         COLS4 + "from a cross join b join c left join d on a.id = d.id",
         // a LEFT join in the right operand of another join
         COLS4 + "from a cross join d cross join e join (b left join c on b.id = c.id) " +
            "on a.id = b.id"
      );
   }

   /**
    * A RIGHT or FULL join at the start of its from clause (lstart 0), in a statement with
    * the new syntax at any level (review r5 B6). SQLHelper writes the group first only while
    * it keeps the text order. When it can't (a join condition that doesn't name the table it
    * joins, e.g. join r on a.k = b.k, makes generateFromClauseText give up), it writes the
    * outer joins last, a INNER JOIN b ON .. , c RIGHT OUTER JOIN d ON .., and SQLite reads
    * that differently. Every RIGHT or FULL join in such a statement is refused. These failed
    * to parse before, so they still do.
    */
   static Stream<String> rightJoinAtFromStart() {
      return Stream.of(
         // the B6 shapes, the tables of review r5
         B6_COLS + "from c right join d on c.id = d.id, ((p join q on p.id = q.id join r " +
            "on p.k = q.k))",
         B6_COLS + "from c full join d on c.id = d.id, ((p join q on p.id = q.id join r " +
            "on p.k = q.k))",
         B6_COLS + "from c right join d on c.id = d.id, ((p join q on p.id = q.id)) join r " +
            "on p.k = q.k",
         B6_COLS + "from ((c right join d on c.id = d.id)), p join q on p.id = q.id join r " +
            "on q.k = p.k",
         B6_COLS + "from ((c full join d on c.id = d.id)), ((p join q on p.id = q.id join r " +
            "on p.k = q.k))",
         B6_COLS + "from c right join d on c.id = d.id, p join q on p.id = q.id cross join r " +
            "join x on p.k = q.k",
         // a single from item, no comma
         B6_COLS + "from ((c right join d on c.id = d.id)) cross join p join q on p.id = q.id " +
            "join r on p.k = q.k",
         // the new syntax only in a subquery, the outer from clause parses on main (B4 path)
         B6_RIGHT + "where exists (select 1 from a cross join b cross join e)",
         B6_RIGHT + "where exists (select 1 from ((a join b on a.id = b.id)))",
         // accepted before review r5, the RIGHT or FULL join written first in text order
         COLS3 + "from a right join b on a.id = b.id cross join c",
         COLS3 + "from a full join b on a.id = b.id cross join c",
         COLS4 + "from a full join b on a.id = b.id cross join c cross join d",
         COLS3 + "from ((a join b on a.id = b.id)) right join c on b.id = c.id",
         RIGHT_FIRST_EXISTS,
         RIGHT_FIRST_COMMA,
         RIGHT_FIRST_IN_SUBQUERY,
         // in a derived table, and in a scalar subquery of the select list
         "select a.id, b.id, t.id from (select d.id from c right join d on c.id = d.id) t " +
            "cross join a cross join b",
         "select (select max(d.id) from c full join d on c.id = d.id), a.id from a cross join b " +
            "cross join e"
      );
   }

   /**
    * A LEFT join at the start of its from clause whose right side a later join condition of
    * the from clause joins, in a statement with the new syntax at any level (review r6 B7).
    * When a later group makes SQLHelper leave the text order, it writes the inner joins
    * first and the outer joins last, d left join c on d.id = c.id join e on e.id = c.id as
    * (e INNER JOIN c ON e.id = c.id) RIGHT OUTER JOIN d ON d.id = c.id, which keeps the d
    * rows the inner join drops, on every database. Refused whatever the type of the later
    * join. These failed to parse before, so they still do.
    */
   static Stream<String> leftJoinFollowedByJoinToItsRightSide() {
      return Stream.of(
         // the B7 shapes, the tables of review r6
         B7_COLS + "from d left join c on d.id = c.id join e on e.id = c.id, ((p join q on " +
            "p.id = q.id join r on p.k = q.k))",
         B7_COLS + "from ((d left join c on d.id = c.id)) join e on e.id = c.id, ((p join q on " +
            "p.id = q.id join r on p.k = q.k))",
         B7_COLS + "from ((d left join c on d.id = c.id join e on e.id = c.id)), p join q on " +
            "p.id = q.id join r on p.k = q.k",
         B7_COLS + "from d left join c on d.id = c.id cross join x join e on e.id = c.id, ((p join " +
            "q on p.id = q.id join r on p.k = q.k))",
         B7_COLS + "from d left join c on d.id = c.id left join e on e.id = c.id join x on " +
            "x.id = e.id, ((p join q on p.id = q.id join r on p.k = q.k))",
         // the new syntax only in a subquery, the outer from clause parses on main (B4 path)
         B7_LEFT + "where exists (select 1 from a cross join b cross join x)",
         // the right side named by other column joins: a second condition, a comparison
         // other than =, under an OR, a LEFT join, a later comma item, a nested right side
         B7_COLS + "from d left join c on d.id = c.id join e on d.id = e.id and d.k = c.k, " +
            "((p join q on p.id = q.id join r on p.k = q.k))",
         B7_COLS + "from d left join c on d.id = c.id join e on d.id = e.id and e.k < c.k, " +
            "((p join q on p.id = q.id join r on p.k = q.k))",
         B7_COLS + "from d left join c on d.id = c.id join e on d.id = e.id or e.k = c.k, " +
            "((p join q on p.id = q.id join r on p.k = q.k))",
         B7_COLS + "from d left join c on d.id = c.id left join e on c.id = e.id, ((p join q on " +
            "p.id = q.id join r on p.k = q.k))",
         B7_COLS + "from d left join c on d.id = c.id, p join q on q.id = c.id join r on " +
            "p.k = q.k cross join e cross join x",
         B7_COLS + "from d left join (c join x on c.id = x.id) on d.id = c.id join e on " +
            "e.id = x.id, ((p join q on p.id = q.id join r on p.k = q.k))",
         // a later join after another LEFT join at the start, and in a subquery
         B7_COLS + "from x join d on x.id = d.id left join c on d.id = c.id join e on " +
            "e.id = c.id cross join p cross join q cross join r",
         "select x.id from x cross join p cross join q where exists (select 1 from d left join " +
            "c on d.id = c.id join e on e.id = c.id where d.id = x.id)",
         // accepted in review r4-r6, they keep the text order, but a later join names the
         // right side
         LEFT_FIRST_CHAIN_LEFT,
         COLS4 + "from a cross join b left join c on a.id = c.id join d on c.id = d.id",
         COLS3 + "from ((a left join b on a.id = b.id)) left join c on b.id = c.id"
      );
   }

   private static final String B7_COLS = "select c.id, d.id, e.id, p.id, q.id, r.id ";
   private static final String B7_LEFT = B7_COLS + "from d left join c on d.id = c.id join e " +
      "on e.id = c.id, p join q on p.id = q.id join r on p.k = q.k ";

   /**
    * An outer join of a where clause (*=, =* or (+)) in a statement with the new syntax at
    * any level (review r6). It has no position, so SQLHelper orders it with the inner joins
    * of the where clause, d.id = c.id(+) and e.id = c.id as (e INNER JOIN c ON e.id = c.id)
    * RIGHT OUTER JOIN d ON d.id = c.id, which keeps the d rows Oracle drops. These failed to
    * parse before, so they still do.
    */
   static Stream<String> whereOuterJoinInNewSyntax() {
      return Stream.of(
         WHERE_OUTER + "and exists (select 1 from a cross join b cross join x)",
         WHERE_OUTER.replace("d.id = c.id(+)", "d.id *= c.id") +
            "and exists (select 1 from ((a join b on a.id = b.id)))",
         "select t.id from (" + WHERE_OUTER.trim() + ") t cross join a cross join b",
         "select c.id from d, c where d.id = c.id(+) and exists (select 1 from a cross join b " +
            "cross join x)",
         "select c.id, d.id, e.id from a cross join b cross join x, d, c, e where d.id = c.id(+) " +
            "and e.id = c.id"
      );
   }

   private static final String WHERE_OUTER = "select c.id, d.id, e.id from d, c, e, p, q where " +
      "d.id = c.id(+) and e.id = c.id and p.id = q.id ";

   private static final String B6_COLS = "select c.id, d.id, p.id, q.id, r.id ";
   private static final String B6_RIGHT = B6_COLS + "from c right join d on c.id = d.id, p join q " +
      "on p.id = q.id join r on p.k = q.k ";

   private static final String X_RIGHT =
      "select c.id, d.id, x.id from x, c right join d on c.id = d.id ";
   private static final String GROUP_RIGHT =
      COLS4 + "from a join b on a.id = b.id join (c right join d on c.id = d.id) ";

   /**
    * The from clauses of rightJoinElsewhereInStatement in statements with no syntax that
    * failed to parse before. They parse as before (review r3).
    */
   static Stream<String> mainShapes() {
      return Stream.of(
         X_RIGHT + "where exists (select 1 from p cross join q)",
         X_RIGHT + "where exists (select 1 from (p join q on p.id = q.id) left join r " +
            "on q.id = r.id)",
         X_RIGHT + "where x.id in (select p.id from p, q, r)",
         GROUP_RIGHT + "where exists (select 1 from p cross join q)",
         COLS4 + "from a join b on a.id = b.id join ((c right join d on c.id = d.id))",
         COLS4 + "from a left join b on a.id = b.id join (c right join d on c.id = d.id)",
         "select x.id from x, p cross join q where exists (select 1 from a join b on a.id = b.id " +
            "join (c right join d on c.id = d.id) where a.id = x.id)",
         "select a.id, b.id, t.id from (select d.id from x, c right join d on c.id = d.id) t, " +
            "a cross join b",
         // comma lists, chains, SQLHelper output and Oracle joins
         COLS4 + "from a join b on a.id = b.id, c right join d on c.id = d.id",
         COLS4 + "from a INNER JOIN b ON a.id = b.id , c RIGHT OUTER JOIN d ON c.id = d.id",
         COLS4 + "from c RIGHT OUTER JOIN d ON c.id = d.id , a, b",
         COLS3 + "from (a INNER JOIN b ON a.id = b.id ) LEFT OUTER JOIN c ON b.id = c.id",
         COLS4 + "from a INNER JOIN (c FULL OUTER JOIN d ON c.id = d.id ) ON a.id = c.id, b",
         COLS3 + "from a full join b on a.id = b.id, c",
         COLS3 + "from a cross join b, c",
         "select x.id, a.id, b.id from x, a cross join b",
         COLS3 + "from a, b, c where a.id = b.id(+) and b.id = c.id",
         COLS4 + "from a join b on a.id = b.id join c on b.id = c.id left join d on c.id = d.id",
         // the from clauses of leftJoinAfterFromItem with no new syntax (review r4)
         "select a.id, c.id, x.id from x, a left join c on a.id = c.id",
         COLS3 + "from a join b left join c on b.id = c.id",
         COLS4 + "from d, a join (b left join c on b.id = c.id) on a.id = b.id",
         // the from clause of rightJoinAtFromStart with no new syntax (review r5)
         B6_RIGHT.trim()
      );
   }

   @ParameterizedTest
   @MethodSource("mainShapes")
   void mainShapeParses(String text) throws Exception {
      for(String type : new String[] { "h2", "mysql-ansi" }) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = parse(text, ds);
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type + ": " + text);
         assertFalse(sql.isLossy(), type + ": " + text);
         assertRoundTrip(regenerate(sql), ds);
      }
   }

   /**
    * The B5 from clauses with no new syntax parse and regenerate as before, the LEFT join
    * over a nested join written as a RIGHT join after a comma item (the known gap on main).
    * The regenerated join condition is reversed by the next regeneration, so these are
    * checked without the round trip.
    */
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select c.id, d.id, e.id, x.id from x, d left join (c join e on c.id = e.id) on d.id = c.id" +
         "|select c.id, d.id, e.id, x.id from (c INNER JOIN e ON c.id = e.id ) RIGHT OUTER JOIN " +
         "d ON d.id = c.id , x",
      "select a.id, b.id, c.id, d.id, e.id from a join b on a.id = b.id, d left join (c join e " +
         "on c.id = e.id) on d.id = c.id|select a.id, b.id, c.id, d.id, e.id from a INNER JOIN b " +
         "ON a.id = b.id , (c INNER JOIN e ON c.id = e.id ) RIGHT OUTER JOIN d ON d.id = c.id"
   })
   void mainLeftJoinOverNestedJoinRegeneratesAsBefore(String text, String expected) throws Exception {
      UniformSQL sql = parse(text, dataSource("h2"));
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      assertEquals(expected, regenerate(sql));
   }

   @ParameterizedTest
   @MethodSource({ "refused", "onlessJoinBeforeRightJoin", "rightJoinAfterFromItem",
                   "rightJoinElsewhereInStatement", "leftJoinAfterFromItem",
                   "rightJoinAtFromStart", "leftJoinFollowedByJoinToItsRightSide",
                   "whereOuterJoinInNewSyntax" })
   void refusedShapeFailsParse(String text) {
      for(String type : DATA_SOURCES) {
         assertRefused(text, dataSource(type), type);
      }

      assertRefused(text, null, "no data source");
   }

   // H2 reads the original the way it was recorded, and Derby and PostgreSQL reject it, so
   // only MySQL and SQLite show the different rows. The refusal is asserted instead
   @ParameterizedTest
   @MethodSource("onlessJoinBeforeRightJoin")
   void onlessJoinBeforeRightJoinIsRefused(String text) {
      Exception ex = assertThrows(Exception.class, () -> parse(text, dataSource("mysql")));
      assertTrue(ex.getMessage().contains(
         "Unsupported RIGHT or FULL join after a join without a join condition"), ex.getMessage());
   }

   @ParameterizedTest
   @MethodSource({ "rightJoinAfterFromItem", "rightJoinElsewhereInStatement" })
   void rightJoinAfterFromItemIsRefused(String text) {
      Exception ex = assertThrows(Exception.class, () -> parse(text, dataSource("mysql")));
      assertTrue(ex.getMessage().contains(
         "Unsupported RIGHT or FULL join after another from item"), ex.getMessage());
   }

   // a statement with a FULL join too (L11, L12) reports the RIGHT or FULL join first
   @ParameterizedTest
   @MethodSource("leftJoinAfterFromItem")
   void leftJoinAfterFromItemIsRefused(String text) {
      Exception ex = assertThrows(Exception.class, () -> parse(text, dataSource("mysql")));
      assertTrue(ex.getMessage().contains(text.contains(" full join ") ? RIGHT_IN_NEW_SYNTAX :
         "Unsupported LEFT join after another from item"), ex.getMessage());
   }

   @ParameterizedTest
   @MethodSource("rightJoinAtFromStart")
   void rightJoinAtFromStartIsRefused(String text) {
      Exception ex = assertThrows(Exception.class, () -> parse(text, dataSource("mysql")));
      assertTrue(ex.getMessage().contains(RIGHT_IN_NEW_SYNTAX), ex.getMessage());
   }

   @ParameterizedTest
   @MethodSource("leftJoinFollowedByJoinToItsRightSide")
   void leftJoinFollowedByJoinToItsRightSideIsRefused(String text) {
      Exception ex = assertThrows(Exception.class, () -> parse(text, dataSource("mysql")));
      assertTrue(ex.getMessage().contains("Unsupported LEFT join followed by a join to its " +
         "right side in a statement with a cross join chain or a parenthesized join"),
         ex.getMessage());
   }

   @ParameterizedTest
   @MethodSource("whereOuterJoinInNewSyntax")
   void whereOuterJoinInNewSyntaxIsRefused(String text) {
      Exception ex = assertThrows(Exception.class, () -> parse(text, dataSource("oracle-ansi")));
      assertTrue(ex.getMessage().contains("Unsupported outer join in the where clause of a " +
         "statement with a cross join chain or a parenthesized join"), ex.getMessage());
   }

   /**
    * The RIGHT or FULL join reported in a statement with the new syntax is the first one in
    * the text, whatever the order of the queries in the parser's maps (review r6).
    */
   @Test
   void firstRightJoinIsReportedInTextOrder() {
      String text = "select (select max(d.id) from c full join d on c.id = d.id),\n" +
         "(select max(d.id) from c right join d on c.id = d.id),\n" +
         "(select max(d.id) from c full join d on c.id = d.id),\n" +
         "(select max(d.id) from c right join d on c.id = d.id), a.id\n" +
         "from a right join b on a.id = b.id cross join e cross join x";
      int column = text.indexOf(" join d") + 2;

      for(int i = 0; i < 20; i++) {
         Exception ex = assertThrows(Exception.class, () -> parse(text, dataSource("mysql")));
         assertTrue(ex.getMessage().contains(RIGHT_IN_NEW_SYNTAX), ex.getMessage());
         // SemanticException.toString() starts with "line <line>:<column>: "
         assertTrue(ex.toString().contains("line 1:" + column + ":"), ex.toString());
      }
   }

   /**
    * The from clauses of leftJoinFollowedByJoinToItsRightSide and whereOuterJoinInNewSyntax
    * with no new syntax parse and regenerate as before, the inner join first and the outer
    * join written as a RIGHT join after it (the known gap on main, review r6). The next
    * regeneration reverses the ON, as for the B5 and B6 twins, so there's no round trip.
    */
   static Stream<Arguments> mainOuterJoinFollowedByJoinToItsRightSide() {
      return Stream.of(
         Arguments.of(B7_LEFT.trim(), B7_COLS + "from (e INNER JOIN c ON e.id = c.id ) RIGHT " +
            "OUTER JOIN d ON d.id = c.id , p INNER JOIN q ON p.id = q.id AND p.k = q.k , r"),
         Arguments.of(WHERE_OUTER.trim(), "select c.id, d.id, e.id from (e INNER JOIN c ON " +
            "e.id = c.id ) RIGHT OUTER JOIN d ON d.id = c.id , p INNER JOIN q ON p.id = q.id"));
   }

   @ParameterizedTest
   @MethodSource("mainOuterJoinFollowedByJoinToItsRightSide")
   void mainOuterJoinFollowedByJoinToItsRightSideRegeneratesAsBefore(String text, String expected)
      throws Exception
   {
      UniformSQL sql = parse(text, dataSource("h2-ansi"));
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      assertEquals(expected, regenerate(sql));
   }

   /**
    * A LEFT join over a nested join at the start of its from clause, written as a RIGHT
    * join group, stays the first group when a later group makes SQLHelper leave the text
    * order, so SQLite reads it the same (rows in rowQueries). Its regenerated join
    * condition is reversed by the next regeneration, as on main, so there's no round trip.
    */
   @Test
   void leftJoinOverNestedJoinStaysFirstWithoutTextOrder() throws Exception {
      for(String type : new String[] { "h2", "h2-ansi", "mysql-ansi" }) {
         UniformSQL sql = parse(LEFT_FIRST_NESTED_FALLBACK, dataSource(type));
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type);
         assertFalse(sql.isLossy(), type);
         assertEquals("select a.id, b.id, c.id, d.id, e.id, x.id from (c INNER JOIN e ON c.id = e.id ) " +
                         "RIGHT OUTER JOIN d ON d.id = c.id , a INNER JOIN b ON a.id = b.id AND " +
                         "b.id = a.id , x", regenerate(sql), type);
      }
   }

   private static final String RIGHT_IN_NEW_SYNTAX = "Unsupported RIGHT or FULL join in a " +
      "statement with a cross join chain or a parenthesized join";

   /**
    * The from clause of the B6 shapes with no new syntax parses and regenerates as before,
    * the RIGHT join group after the inner join group (the known gap on main, review r5).
    */
   @Test
   void mainRightJoinFirstRegeneratesAsBefore() throws Exception {
      String text = B6_RIGHT.trim();
      UniformSQL sql = parse(text, dataSource("h2-ansi"));
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      assertEquals("select c.id, d.id, p.id, q.id, r.id from p INNER JOIN q ON p.id = q.id AND " +
         "p.k = q.k , c RIGHT OUTER JOIN d ON c.id = d.id , r", regenerate(sql));
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "select * from a cross join b cross join c",
      "select * from ((a join b on a.id = b.id)) left join c on b.id = c.id",
      "select * from x cross join a cross join b left join c on x.id = c.id",
      "select * from d LEFT JOIN c ON d.id = c.id, ((a joIN b ON a.id = b.id joIN e " +
         "ON a.id = b.id))",
   })
   void acceptedInTurkishLocale(String text) throws Exception {
      Locale locale = Locale.getDefault();

      try {
         Locale.setDefault(Locale.forLanguageTag("tr-TR"));
         UniformSQL sql = parse(text, dataSource("h2"));
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
         assertRoundTrip(regenerate(sql), dataSource("h2"));
      }
      finally {
         Locale.setDefault(locale);
      }
   }

   // the join types are compared without the default locale, a dotted or dotless i must
   // not change them
   @ParameterizedTest
   @ValueSource(strings = {
      "select * from a cross join b rIGHT join c on b.id = c.id",
      "select * from a CROSS JOIN b JOIN c RIGHT JOIN d ON c.id = d.id",
      "select * from a INNER JOIN b RIGHT JOIN c ON b.id = c.id",
      "select * from a inner join b right join c on b.id = c.id",
      "select * from ((a join b on a.id = b.id)), (c rIGHT join d on c.id = d.id)",
      "select * from ((a JOIN b ON a.id = b.id)), (c FULL JOIN d ON c.id = d.id)",
      "select * from x, c rIGHT join d on c.id = d.id where exists (select 1 from p " +
         "cRoss join q cross join r)",
      "select * from a cross join b JOIN c LEFT JOIN d ON c.id = d.id",
      "select * from x, ((a joIN b on a.id = b.id)) left join c on b.id = c.id",
      "select * from c rIGHT join d on c.id = d.id cRoss join a cross join b",
      "select * from ((c FULL JOIN d ON c.id = d.id)), ((a JOIN b ON a.id = b.id JOIN e " +
         "ON a.id = b.id))",
      "select * from d LEFT join c ON d.id = c.id joIN e ON e.id = c.id cRoss join a cross join b",
      "select * from d, c where d.id = c.id(+) and exists (select 1 from a cRoss join b cross join e)",
   })
   void refusedInTurkishLocale(String text) {
      Locale locale = Locale.getDefault();

      try {
         Locale.setDefault(Locale.forLanguageTag("tr-TR"));
         assertRefused(text, dataSource("h2"), "tr_TR");
      }
      finally {
         Locale.setDefault(locale);
      }
   }

   // the expression check judges the syntax only, so a refused join in a scalar subquery is
   // still a valid expression (#77493), and the new shapes are valid too
   @Test
   void scalarSubqueryExpressionIsValid() {
      assertTrue(XUtil.isSQLExpressionValid("(select count(*) from a cross join b cross join c)"));
      assertTrue(XUtil.isSQLExpressionValid(
         "(select count(*) from ((a join b on a.id = b.id)) left join c on b.id = c.id)"));
      assertTrue(XUtil.isSQLExpressionValid(
         "(select count(*) from a cross join b join c right join d on c.id = d.id)"));
      assertTrue(XUtil.isSQLExpressionValid(
         "(select count(*) from a join b right join c on b.id = c.id)"));
      assertTrue(XUtil.isSQLExpressionValid(
         "(select count(*) from a join b on a.id = b.id, (c right join d on c.id = d.id))"));
      assertTrue(XUtil.isSQLExpressionValid(
         "(select count(*) from x, c right join d on c.id = d.id where exists " +
            "(select 1 from p cross join q cross join r))"));
      assertTrue(XUtil.isSQLExpressionValid(
         "(select count(*) from ((a join b on a.id = b.id)), d left join (c join e " +
            "on c.id = e.id) on d.id = c.id)"));
      assertTrue(XUtil.isSQLExpressionValid(
         "(select count(*) from c right join d on c.id = d.id, ((a join b on a.id = b.id " +
            "join e on a.id = b.id)))"));
      assertTrue(XUtil.isSQLExpressionValid(
         "(select count(*) from d left join c on d.id = c.id join e on e.id = c.id, " +
            "((a join b on a.id = b.id join x on a.id = b.id)))"));
      assertTrue(XUtil.isSQLExpressionValid(
         "(select count(*) from d, c where d.id = c.id(+) and exists (select 1 from a cross " +
            "join b cross join x))"));
      assertFalse(XUtil.isSQLExpressionValid("(select count(*) from a cross join b on)"));
   }

   /**
    * Join models as the query editor builds them, each "table1.id op table2.id" link with
    * op =, *=, =* or *=*, plus a table with no join. The sql that SQLHelper generates for
    * them must still parse and regenerate the same.
    */
   static Stream<String> editorJoinModels() {
      List<String> models = new ArrayList<>();
      String[] ops = { "=", "*=", "=*", "*=*" };
      String[][] shapes = { { "a-b", "b-c" }, { "a-b", "a-c" }, { "b-c" }, { "a-b", "c-d" } };

      for(String[] shape : shapes) {
         int n = (int) Math.pow(ops.length, shape.length);

         for(int i = 0; i < n; i++) {
            StringBuilder model = new StringBuilder();

            for(int j = 0, k = i; j < shape.length; j++, k /= ops.length) {
               model.append(j == 0 ? "" : ",").append(shape[j]).append(":")
                  .append(ops[k % ops.length]);
            }

            models.add(model.toString());
         }
      }

      return models.stream();
   }

   @ParameterizedTest
   @MethodSource("editorJoinModels")
   void editorGeneratedSqlReparses(String model) throws Exception {
      for(String type : DATA_SOURCES) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = new UniformSQL();

         for(String table : new String[] { "a", "b", "c", "d" }) {
            sql.addTable(table);
         }

         sql.getSelection().addColumn("a.id");
         sql.getSelection().addColumn("d.id");

         for(String link : model.split(",")) {
            String[] parts = link.split("[-:]");
            sql.addJoin(new XJoin(new XExpression(parts[0] + ".id", XExpression.FIELD),
                                  new XExpression(parts[1] + ".id", XExpression.FIELD), parts[2]));
         }

         sql.setDataSource(ds);
         sql.clearSQLString();
         String generated = normalize(sql.getSQLString());
         UniformSQL reparsed = parse(generated, ds);
         assertEquals(UniformSQL.PARSE_SUCCESS, reparsed.getParseResult(), type + ": " + generated);
         assertFalse(reparsed.isLossy(), type + ": " + generated);
         assertEquals(model.split(",").length, reparsed.getJoins().length, type + ": " + generated);
         assertRoundTrip(regenerate(reparsed), ds);
      }
   }

   /**
    * Queries for the row comparison, with a sorted select list since the regenerated sql
    * sorts it.
    */
   static Stream<String> rowQueries() {
      return Stream.of(
         COLS3 + "from a cross join b cross join c",
         COLS4 + "from a cross join b cross join c cross join d",
         COLS3 + "from a cross join b join c on b.id = c.id",
         COLS3 + "from a join b on a.id = b.id cross join c",
         COLS3 + "from a cross join b left join c on b.id = c.id",
         COLS3 + "from a cross join b left join c on a.id = c.id",
         COLS3 + "from a left join b on a.id = b.id cross join c",
         COLS4 + "from a join b on a.id = b.id cross join c left join d on a.id = d.id",
         COLS4 + "from a left join b on a.id = b.id cross join c left join d on c.id = d.id",
         COLS3 + "from ((a join b on a.id = b.id)) left join c on b.id = c.id",
         COLS3 + "from ((a join b on a.id = b.id) left join c on b.id = c.id)",
         COLS3 + "from (((a left join b on a.id = b.id))) left join c on a.id = c.id",
         COLS3 + "from ((a join b on a.id = b.id)), c where b.id = c.id",
         COLS4 + "from ((a join b on a.id = b.id)) left join ((c join d on c.id = d.id)) on b.id = c.id",
         COLS3 + "from (a cross join b) cross join c",
         COLS3 + "from ((a cross join b)) left join c on b.id = c.id",
         LEFT_FIRST_CHAIN,
         LEFT_FIRST_NESTED,
         LEFT_FIRST_EXISTS,
         LEFT_FIRST_FALLBACK,
         LEFT_FIRST_NESTED_FALLBACK,
         LEFT_FIRST_LATER_INNER,
         LEFT_FIRST_LATER_LEFT,
         LEFT_FIRST_LATER_FILTER,
         LEFT_FIRST_WHERE_JOIN,
         "select a.id from a where exists (select 1 from b cross join c cross join d " +
            "where b.id = a.id)",
         "select a.id, b.id from a, b where a.id in (select c.id from ((c join d on c.id = d.id)) " +
            "left join e on d.id = e.id)"
      );
   }

   @ParameterizedTest
   @MethodSource("rowQueries")
   void regeneratedSqlReturnsSameRowsOnDerby(String text) throws Exception {
      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77495;create=true")) {
         assertSameRows(conn, text);
      }
   }

   // H2 and SQLite are not on the core test classpath, add them with
   // -Dmaven.test.additionalClasspath to run these
   @ParameterizedTest
   @MethodSource("rowQueries")
   void regeneratedSqlReturnsSameRowsOnH2(String text) throws Exception {
      Assumptions.assumeTrue(hasDriver("org.h2.Driver"), "H2 is not on the classpath");

      try(Connection conn = DriverManager.getConnection("jdbc:h2:mem:bug77495")) {
         assertSameRows(conn, text);
      }
   }

   @ParameterizedTest
   @MethodSource("rowQueries")
   void regeneratedSqlReturnsSameRowsOnSqlite(String text) throws Exception {
      Assumptions.assumeTrue(hasDriver("org.sqlite.JDBC"), "SQLite is not on the classpath");

      try(Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
         assertSameRows(conn, text);
      }
   }

   @AfterAll
   static void dropDatabase() {
      try {
         DriverManager.getConnection("jdbc:derby:memory:bug77495;drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   private static boolean hasDriver(String name) {
      try {
         Class.forName(name);
         return true;
      }
      catch(ClassNotFoundException ex) {
         return false;
      }
   }

   private void assertSameRows(Connection conn, String text) throws Exception {
      createTables(conn);
      List<String> generated = new ArrayList<>();

      // MongoHelper writes the joins in text order, a different generation path. MySQL is
      // the database that reads an ON-less join before a RIGHT join left associative
      for(String type : new String[] { "default", "h2", "h2-ansi", "mongo", "mongo-ansi", "mysql",
                                       "mysql-ansi" })
      {
         JDBCDataSource ds = "default".equals(type) ? GenericJDBCDataSource.create() : dataSource(type);
         UniformSQL sql = parse(text, ds);
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type + ": " + text);
         generated.add(regenerate(sql));
      }

      Random random = new Random(77495);

      for(int i = 0; i < 200; i++) {
         fillTables(conn, random);
         List<String> expected = rows(conn, text);

         for(String query : generated) {
            assertEquals(expected, rows(conn, query),
                         "dataset " + i + "\noriginal: " + text + "\ngenerated: " + query);
         }
      }
   }

   private static final String[] TABLES = { "a", "b", "c", "d", "e", "x" };

   private static void createTables(Connection conn) throws SQLException {
      try(Statement stmt = conn.createStatement()) {
         for(String table : TABLES) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(SQLException ignore) {
               // first run
            }

            stmt.executeUpdate("create table " + table + " (id int)");
         }
      }
   }

   private static void fillTables(Connection conn, Random random) throws SQLException {
      try(Statement stmt = conn.createStatement()) {
         for(String table : TABLES) {
            stmt.executeUpdate("delete from " + table);

            for(int i = random.nextInt(4); i > 0; i--) {
               int value = random.nextInt(4);
               stmt.executeUpdate("insert into " + table + " values (" +
                                  (value == 0 ? "null" : value) + ")");
            }
         }
      }
   }

   // the result rows as a sorted multiset
   private static List<String> rows(Connection conn, String query) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(Statement stmt = conn.createStatement(); ResultSet result = executeQuery(stmt, query)) {
         int columns = result.getMetaData().getColumnCount();

         while(result.next()) {
            StringBuilder row = new StringBuilder();

            for(int i = 1; i <= columns; i++) {
               row.append(result.getObject(i)).append('|');
            }

            rows.add(row.toString());
         }
      }

      Collections.sort(rows);
      return rows;
   }

   private static ResultSet executeQuery(Statement stmt, String query) throws SQLException {
      try {
         return stmt.executeQuery(query);
      }
      catch(SQLException ex) {
         throw new SQLException(ex.getMessage() + ": " + query, ex);
      }
   }

   private static void assertRefused(String text, JDBCDataSource ds, String type) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), type + ": " + text);
      assertEquals(text, sql.getSQLString(), type);

      // without a data source the join order check is skipped by isLossy
      if(ds != null) {
         UniformSQL fresh = new UniformSQL();
         fresh.setDataSource(ds);
         fresh.setSQLString(text, false);
         assertTrue(fresh.isLossy(), type + ": " + text);
         assertFalse(XUtil.isQueryMergeable(query(text, ds)), type + ": " + text);
      }
   }

   // the regenerated sql parses and regenerates to itself
   private static void assertRoundTrip(String generated, JDBCDataSource ds) throws Exception {
      UniformSQL reparsed = parse(generated, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, reparsed.getParseResult(), generated);
      assertEquals(generated, regenerate(reparsed), "round trip");
   }

   // the joins as "column op column" in the order of the where clause
   private static String joins(UniformSQL sql) {
      StringBuilder joins = new StringBuilder();

      for(XJoin join : sql.getJoins() == null ? new XJoin[0] : sql.getJoins()) {
         joins.append(joins.length() == 0 ? "" : "; ").append(join);
      }

      return joins.toString();
   }

   private static String regenerate(UniformSQL sql) {
      UniformSQL copy = (UniformSQL) sql.clone();
      copy.clearSQLString();
      return normalize(copy.getSQLString());
   }

   private static JDBCQuery query(String text, JDBCDataSource ds) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      new SQLProcessor(sql).parse(text);
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      return query;
   }

   private static JDBCDataSource dataSource(String type) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds_" + type);
      ds.setProductVersion("19.0");

      switch(type.replace("-ansi", "")) {
      case "h2" -> {
         ds.setDriver("org.h2.Driver");
         ds.setURL("jdbc:h2:mem:test");
      }
      case "mysql" -> {
         ds.setDriver("com.mysql.cj.jdbc.Driver");
         ds.setURL("jdbc:mysql://localhost:3306/test");
      }
      case "postgresql" -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/test");
      }
      case "oracle" -> {
         ds.setDriver("oracle.jdbc.OracleDriver");
         ds.setURL("jdbc:oracle:thin:@localhost:1521:test");
      }
      case "mongo" -> {
         ds.setDriver("mongodb.jdbc.MongoDriver");
         ds.setURL("jdbc:mongo://localhost:27017/test");
      }
      case "sql server" -> {
         ds.setDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver");
         ds.setURL("jdbc:sqlserver://localhost:1433;databaseName=test");
      }
      default -> throw new IllegalArgumentException(type);
      }

      ds.setAnsiJoin(type.endsWith("-ansi"));
      String helper = SQLHelper.getSQLHelper(ds).getSQLHelperType();
      assertEquals(type.replace("-ansi", ""), helper, "helper for " + type);
      return ds;
   }

   // the generated sql is pretty-printed
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }
}
