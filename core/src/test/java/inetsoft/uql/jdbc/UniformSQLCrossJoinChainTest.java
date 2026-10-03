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
 * so it now fails the parse, including after a cross join chain.
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
         // a right join before a cross join (no inner join after it)
         Arguments.of(COLS3 + "from a right join b on a.id = b.id cross join c", "a.id =* b.id",
                      COLS3 + "from a RIGHT OUTER JOIN b ON a.id = b.id , c"),
         // the refuter's controls: an ON-less join after a cross join, then a LEFT join
         Arguments.of(COLS4 + "from a cross join b join c left join d on c.id = d.id", "c.id *= d.id",
                      COLS4 + "from c LEFT OUTER JOIN d ON c.id = d.id , a, b"),
         Arguments.of(COLS4 + "from a cross join b join c left join d on a.id = d.id", "a.id *= d.id",
                      COLS4 + "from a LEFT OUTER JOIN d ON a.id = d.id , b, c"),
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
         // an ON-less join followed by a LEFT join is unchanged
         Arguments.of(COLS3 + "from a join b left join c on b.id = c.id", "b.id *= c.id",
                      COLS3 + "from b LEFT OUTER JOIN c ON b.id = c.id , a")
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
            assertEquals("oracle", type, text + ": " + ex.getMessage());
            assertTrue(ex.getMessage().startsWith("Unsupported"), ex.getMessage());
            continue;
         }

         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type + ": " + text);
         assertFalse(sql.isLossy(), type + ": " + text);
         assertRoundTrip(regenerate(sql), ds);
      }
   }

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
         "select * from (a join b on a.id = b.id) x"
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
         COLS3 + "from x where exists (select 1 from a join b right join c on b.id = c.id)"
      );
   }

   @ParameterizedTest
   @MethodSource({ "refused", "onlessJoinBeforeRightJoin" })
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
   @ValueSource(strings = {
      "select * from a cross join b cross join c",
      "select * from ((a join b on a.id = b.id)) left join c on b.id = c.id",
      "select * from a cross join b join c left join d on c.id = d.id",
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
         COLS3 + "from a right join b on a.id = b.id cross join c",
         COLS4 + "from a join b on a.id = b.id cross join c left join d on a.id = d.id",
         COLS4 + "from a left join b on a.id = b.id cross join c left join d on c.id = d.id",
         COLS4 + "from a cross join b left join c on a.id = c.id join d on c.id = d.id",
         COLS3 + "from ((a join b on a.id = b.id)) left join c on b.id = c.id",
         COLS3 + "from ((a left join b on a.id = b.id)) left join c on b.id = c.id",
         COLS3 + "from ((a join b on a.id = b.id) left join c on b.id = c.id)",
         COLS3 + "from (((a left join b on a.id = b.id))) left join c on a.id = c.id",
         COLS3 + "from ((a join b on a.id = b.id)), c where b.id = c.id",
         COLS4 + "from ((a join b on a.id = b.id)) left join ((c join d on c.id = d.id)) on b.id = c.id",
         COLS3 + "from (a cross join b) cross join c",
         COLS3 + "from ((a cross join b)) left join c on b.id = c.id",
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

   // FULL joins, which Derby doesn't support
   @ParameterizedTest
   @ValueSource(strings = {
      COLS3 + "from a full join b on a.id = b.id cross join c",
      COLS4 + "from a full join b on a.id = b.id cross join c cross join d",
   })
   void fullJoinReturnsSameRowsOnHsqldb(String text) throws Exception {
      try(Connection conn = DriverManager.getConnection("jdbc:hsqldb:mem:bug77495", "SA", "")) {
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

      // MongoHelper writes the joins in text order, a different generation path
      for(String type : new String[] { "default", "h2", "h2-ansi", "mongo", "mongo-ansi" }) {
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
