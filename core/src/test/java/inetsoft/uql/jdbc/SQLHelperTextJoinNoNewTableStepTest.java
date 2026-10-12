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
import inetsoft.uql.XRepository;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77674, the text order ANSI FROM clause (generateFromClauseText) gave up on a whole query
 * level when one ON named no new table, e.g. join r on p.k = q.k after p join q, and the joins
 * fell back to the outer-last order (OuterJoinComparator) when they weren't one chain. That
 * wrote d left join c on d.id = c.id join e on e.id = c.id as (e JOIN c) RIGHT JOIN d, which
 * keeps the d rows the inner join removed. Such an inner ON is now a where condition of its
 * group, and parsed joins that still reach the outer-last order fail the parse.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 SQLHelperTextJoinNoNewTableStepTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLHelperTextJoinNoNewTableStepTest {
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

   private static final String DB = "jdbc:derby:memory:bug77674";
   private static final String HSQLDB = "jdbc:hsqldb:mem:bug77674";
   private static final String[] TABLES = { "c", "d", "e", "f", "g", "p", "q", "r", "x" };

   // the report query
   private static final String V0 = "select c.id, d.id, e.id, p.id, q.id, r.id from d left " +
      "join c on d.id = c.id join e on e.id = c.id, p join q on p.id = q.id join r on p.k = q.k";
   // a step between two groups that no condition crosses, still not written in text order
   private static final String V19 = "select c.id, d.id, e.id, p.id, q.id, q2.id, r.id from d " +
      "left join c on d.id = c.id join e on e.id = c.id, (p join q on p.id = q.id) join " +
      "(r join q q2 on r.id = q2.id) on p.k = q.k and r.k = q2.k";

   // parsed queries with an inner ON that names no new table, which fell back to the
   // outer-last order
   static Stream<String> noNewTableQueries() {
      return Stream.of(
         V0,
         // the ON in the group of the left join (V17)
         "select c.id, d.id, e.id, p.id, q.id, r.id from d left join c on d.id = c.id join e " +
            "on e.id = c.id join r on c.k = e.k, p join q on p.id = q.id",
         // two left joins, and the table of the ON is joined again (V23)
         "select c.id, d.id, e.id, p.id, q.id, r.id, r2.id from d left join c on d.id = c.id " +
            "left join e on e.id = c.id join r on r.id = e.id, p join q on p.id = q.id " +
            "join r r2 on p.k = q.k",
         // one join group, which the comma join group check didn't regenerate (S1)
         "select c.id, d.id, e.id, f.id, r.id from d left join c on d.id = c.id left join f " +
            "on d.id = f.id join e on e.id = c.id join r on c.k = e.k",
         // ... with an unrelated join group
         "select c.id, d.id, e.id, f.id, g.id, r.id, x.id from d left join c on d.id = c.id " +
            "left join f on d.id = f.id join e on e.id = c.id join r on c.k = e.k, g join x " +
            "on g.id = x.id",
         // the table of the ON is joined by a later left join
         "select c.id, d.id, e.id, f.id, r.id from d left join c on d.id = c.id join e on " +
            "e.id = c.id join r on c.k = e.k left join f on f.id = r.id",
         // two conditions and a <> in the ON
         "select c.id, d.id, e.id, p.id, q.id, r.id from d left join c on d.id = c.id join e " +
            "on e.id = c.id, p join q on p.id = q.id join r on p.k = q.k and p.id <> q.k",
         // the group of the ON is the right operand of an inner join
         "select c.id, d.id, p.id, q.id, r.id, x.id from d left join c on d.id = c.id, x join " +
            "(p join q on p.id = q.id join r on p.k = q.k) on x.id = p.id",
         // the table of the ON is joined to a table after it, so the two are a later join
         // group
         "select c.id, d.id, e.id, f.id, r.id from d left join c on d.id = c.id join e on " +
            "e.id = c.id join r on c.k = e.k join f on f.id = r.id",
         // a join cycle closed by the ON of a no new table step
         "select c.id, d.id, e.id, g.id, p.id, q.id from d left join c on d.id = c.id join e " +
            "on e.id = c.id, p join q on p.id = q.id join g on g.id = q.id join r on p.k = g.k"
      );
   }

   static Stream<Arguments> noNewTableCases() {
      return noNewTableQueries().flatMap(text -> Stream.of("generic", "h2", "h2-ansi", "mongo")
         .map(type -> Arguments.of(text, type)));
   }

   @ParameterizedTest
   @MethodSource("noNewTableCases")
   void noNewTableStepKeepsTheTextOrder(String text, String type) throws Exception {
      UniformSQL sql = parse(text, dataSource(type));
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);

      String generated = regenerate(sql);
      assertFalse(generated.contains("RIGHT OUTER JOIN"), generated);
      assertSameRows(DB, text, generated);
      assertSameRows(HSQLDB, text, generated);
   }

   @ParameterizedTest
   @MethodSource("noNewTableQueries")
   void regeneratedNoNewTableStepRoundTrips(String text) throws Exception {
      // the where condition of the step is written with an upper case AND, and with the case
      // of the where clause once it's parsed again, so require a fixed point after that
      JDBCDataSource ds = dataSource("generic");
      String generated = regenerate(parse(text, ds));
      UniformSQL reparsed = parse(generated, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, reparsed.getParseResult(), generated);

      String regenerated = regenerate(reparsed);
      assertEquals(from(generated), from(regenerated));
      assertEquals(regenerated, regenerate(parse(regenerated, ds)));
      assertSameRows(DB, text, regenerated);
   }

   // the from clause of generated sql, without the where clause
   private static String from(String sql) {
      int where = sql.indexOf(" where ");
      return where < 0 ? sql : sql.substring(0, where);
   }

   @Test
   void reportQueryWritesTheStepInTheWhereClause() throws Exception {
      String generated = regenerate(parse(V0, dataSource("generic")));
      assertTrue(generated.endsWith("from (d LEFT OUTER JOIN c ON d.id = c.id ) INNER JOIN e ON " +
                                    "e.id = c.id , p INNER JOIN q ON p.id = q.id , r where " +
                                    "p.k = q.k"), generated);
   }

   @Test
   void reportQueryReturnsTheRowsOfTheReportData() throws Exception {
      String generated = regenerate(parse(V0, dataSource("generic")));

      try(Connection conn = DriverManager.getConnection(DB)) {
         clearTables(conn);

         try(Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("insert into d values (1, 1)");
            stmt.executeUpdate("insert into p values (1, 1)");
            stmt.executeUpdate("insert into q values (1, 1)");
            stmt.executeUpdate("insert into r values (1, 1)");
         }

         assertEquals(List.of(), rows(conn, V0));
         assertEquals(List.of(), rows(conn, generated));
      }
   }

   @ParameterizedTest
   @ValueSource(strings = { "generic", "h2", "h2-ansi", "mongo", "none" })
   void stepTheTextOrderCantWriteFailsTheParse(String type) {
      // the outer-last order would write (e JOIN c) RIGHT JOIN d
      JDBCDataSource ds = "none".equals(type) ? null : dataSource(type);
      RecognitionException ex =
         assertThrows(RecognitionException.class, () -> parse(V19, ds));
      assertTrue(ex.getMessage().contains("Unsupported join order"), ex.getMessage());
   }

   @Test
   void stepJoiningTheTableOfANoNewTableStepAndANewTableFailsTheParse() {
      // join e on x.k = c.k leaves e a comma item, so the next ON joins two new tables, g and
      // e, to the group, which the text order can't write. This one returned the same rows in
      // the outer-last order, with the left join last in its own group, but it is refused like
      // every query that reaches that order
      String text = "select c.id, e.id, f.id, g.id, p.id, q.id, r.id, x.id from x join c on " +
         "c.id = x.id join e on x.k = c.k join g on g.id = e.id and g.k = c.k, r join q on " +
         "q.id = r.id join f on f.id = r.id left join p on p.id = f.id";
      RecognitionException ex =
         assertThrows(RecognitionException.class, () -> parse(text, dataSource("generic")));
      assertTrue(ex.getMessage().contains("Unsupported join order"), ex.getMessage());
   }

   @Test
   void stepTheTextOrderCantWriteIsAValidScalarSubquery() {
      // the refusal checks the regenerated sql after the parse, which the expression validity
      // check doesn't do
      String exp = "(select max(d.id) from d left join c on d.id = c.id join e on e.id = c.id, " +
         "(p join q on p.id = q.id) join (r join q q2 on r.id = q2.id) on p.k = q.k and " +
         "r.k = q2.k)";
      assertTrue(XUtil.isSQLExpressionValid(exp), exp);
   }

   @Test
   void savedStepTheTextOrderCantWriteIsLossy() throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(dataSource("generic"));
      sql.setSQLString(V19, false);
      assertTrue(sql.isLossy());
      assertEquals(V19, sql.getSQLString());

      // without a data source, the base sql helper checks it
      sql = new UniformSQL();
      sql.setSQLString(V19, false);
      assertTrue(sql.isLossy());
   }

   @Test
   void savedSingleGroupQueryIsNotLossyWithoutDataSource() {
      UniformSQL sql = new UniformSQL();
      sql.setSQLString("select c.id, d.id, e.id, f.id, r.id from d left join c on d.id = c.id " +
                       "left join f on d.id = f.id join e on e.id = c.id join r on c.k = e.k",
                       false);
      assertFalse(sql.isLossy());
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void joinsWithoutRecordedClausesKeepTheOuterLastOrder(boolean ansiJoin) throws Exception {
      // editor, data model and saved before #77475 joins: the order is unchanged
      UniformSQL sql = parse(V0, dataSource("generic"));

      for(XJoin join : sql.getJoins()) {
         join.setJoinClause(XJoin.UNKNOWN_CLAUSE);
      }

      SQLHelper helper = new SQLHelper();
      String generated = generate(helper, sql, ansiJoin);
      assertTrue(generated.endsWith(OUTER_LAST), generated);
      assertFalse(helper.isOuterLastTextJoins());
   }

   @Test
   void editorJoinBetweenJoinedTablesKeepsTheOuterLastOrder() throws Exception {
      // a join added in the query editor isn't in an ON, and a later RIGHT join of the text may
      // null-extend its tables, so it isn't written as a where condition. Such a query has no
      // sql text to keep, its joins are generated as before
      UniformSQL sql = parse("select c.id, d.id, e.id, p.id, q.id, r.id from d left join c on " +
                             "d.id = c.id join e on e.id = c.id, p join q on p.id = q.id, r",
                             dataSource("generic"));
      sql.addJoin(new XJoin(new XExpression("p.k", XExpression.FIELD),
                            new XExpression("q.k", XExpression.FIELD), "="));

      SQLHelper helper = new SQLHelper();
      String generated = generate(helper, sql, false);
      assertTrue(generated.endsWith(OUTER_LAST), generated);
      assertTrue(helper.isOuterLastTextJoins());
   }

   private static final String OUTER_LAST = "from (e INNER JOIN c ON e.id = c.id ) RIGHT OUTER " +
      "JOIN d ON d.id = c.id , p INNER JOIN q ON p.id = q.id AND p.k = q.k , r";

   @ParameterizedTest
   @ValueSource(strings = {
      // the where condition would remove the null-extended rows of the group
      "select d.id, p.id, q.id, r.id from d left join (p join q on p.id = q.id join r on " +
         "p.k = q.k) on d.id = p.id",
      "select d.id, e.id, p.id, q.id, r.id from d left join (p join q on p.id = q.id join r " +
         "on p.k = q.k join e on e.id = q.id) on d.id = p.id",
      "select d.id, p.id, q.id, r.id from (p join q on p.id = q.id join r on p.k = q.k) right " +
         "join d on p.id = d.id",
      "select d.id, p.id, q.id, r.id from p join q on p.id = q.id join r on p.k = q.k right " +
         "join d on p.id = d.id",
      "select d.id, p.id, q.id, r.id from p left join q on p.id = q.id join r on p.k = q.k " +
         "right join d on r.id = d.id",
      "select d.id, p.id, q.id, r.id from p join q on p.id = q.id join r on p.k = q.k full " +
         "join d on p.id = d.id"
   })
   void nullExtendedNoNewTableStepIsStillRefused(String text) {
      for(String type : new String[] { "generic", "h2", "mongo" }) {
         JDBCDataSource ds = dataSource(type);
         assertThrows(RecognitionException.class, () -> parse(text, ds), type + ": " + text);
      }
   }

   @ParameterizedTest
   @ValueSource(strings = {
      // the step is the outermost join, the same as a where condition, so the regenerated sql
      // has the same joins. It failed the join order check while the step fell back to the
      // outer-last order
      "select c.id, e.id, p.id, r.id from p join c on p.id = c.id right join r on c.id = r.id " +
         "join e on r.k = p.k",
      "select c.id, d.id, e.id, f.id, g.id, p.id, r.id, x.id from d left join (x join e on " +
         "x.id = e.id) on d.id = x.id join f on x.id = f.id and x.k = f.k join p on x.id = p.id " +
         "join c on f.k = p.k, g join r on g.id = r.id and g.k = r.k"
   })
   void outermostStepAfterRightOrNestedJoinParses(String text) throws Exception {
      UniformSQL sql = parse(text, dataSource("generic"));
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);

      String generated = regenerate(sql);
      assertSameRows(DB, text, generated);
      assertSameRows(HSQLDB, text, generated);
   }

   @Test
   void outerPairConditionIsUnchanged() throws Exception {
      // the parser records no join for an inner ON between an outer joined pair, it's a where
      // condition already
      String text = "select c.id, d.id, e.id, p.id, q.id, r.id from d left join c on " +
         "d.id = c.id join r on c.k = d.k join e on e.id = c.id, p join q on p.id = q.id";
      UniformSQL sql = parse(text, dataSource("generic"));
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());

      String generated = regenerate(sql);
      assertTrue(generated.endsWith("from (d LEFT OUTER JOIN c ON d.id = c.id ) INNER JOIN e " +
                                    "ON e.id = c.id , p INNER JOIN q ON p.id = q.id , r where " +
                                    "c.k = d.k"), generated);
      assertSameRows(DB, text, generated);
   }

   @BeforeAll
   static void createTables() throws SQLException {
      for(String url : new String[] { DB + ";create=true", HSQLDB }) {
         try(Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement())
         {
            for(String table : TABLES) {
               stmt.executeUpdate("create table " + table + " (id int, k int)");
            }
         }
      }
   }

   @AfterAll
   static void dropDatabase() throws SQLException {
      try(Connection conn = DriverManager.getConnection(HSQLDB);
          Statement stmt = conn.createStatement())
      {
         stmt.execute("shutdown");
      }

      try {
         DriverManager.getConnection(DB + ";drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   private static JDBCDataSource dataSource(String type) {
      if("generic".equals(type)) {
         return GenericJDBCDataSource.create();
      }

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77674_" + type);
      ds.setProductVersion("19.0");

      switch(type.replace("-ansi", "")) {
      case "h2" -> {
         ds.setDriver("org.h2.Driver");
         ds.setURL("jdbc:h2:mem:test");
      }
      case "mongo" -> {
         ds.setDriver("mongodb.jdbc.MongoDriver");
         ds.setURL("jdbc:mongo://localhost:27017/test");
      }
      default -> throw new IllegalArgumentException(type);
      }

      ds.setAnsiJoin(type.endsWith("-ansi"));
      assertEquals(type.replace("-ansi", ""), SQLHelper.getSQLHelper(ds).getSQLHelperType());
      return ds;
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }

   // the sql a merge or the query cache runs, generated from the structure
   private static String regenerate(UniformSQL sql) {
      UniformSQL copy = sql.clone();
      copy.clearSQLString();
      return normalize(copy.getSQLString());
   }

   private static String generate(SQLHelper helper, UniformSQL sql, boolean ansiJoin) {
      helper.setUniformSql(sql);
      helper.setAnsiJoin(ansiJoin);
      return normalize(helper.generateSentence());
   }

   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   /**
    * Runs both queries on random data with nulls, and requires the same rows from both.
    */
   private static void assertSameRows(String url, String expected, String generated)
      throws SQLException
   {
      Random random = new Random(77674);

      try(Connection conn = DriverManager.getConnection(url)) {
         for(int i = 0; i < 300; i++) {
            fillTables(conn, random);
            List<String> rows1 = rows(conn, expected);
            List<String> rows2 = rows(conn, generated);
            assertEquals(rows1, rows2, url + " dataset " + i + "\nexpected: " + expected +
               "\ngenerated: " + generated);
         }
      }
   }

   private static void clearTables(Connection conn) throws SQLException {
      try(Statement stmt = conn.createStatement()) {
         for(String table : TABLES) {
            stmt.executeUpdate("delete from " + table);
         }
      }
   }

   private static void fillTables(Connection conn, Random random) throws SQLException {
      clearTables(conn);

      for(String table : TABLES) {
         try(PreparedStatement insert =
                conn.prepareStatement("insert into " + table + " values (?, ?)"))
         {
            int count = random.nextInt(4);

            for(int i = 0; i < count; i++) {
               for(int column = 1; column <= 2; column++) {
                  int value = random.nextInt(4);

                  if(value == 0) {
                     insert.setNull(column, Types.INTEGER);
                  }
                  else {
                     insert.setInt(column, value);
                  }
               }

               insert.addBatch();
            }

            // hsqldb rejects an empty batch
            if(count > 0) {
               insert.executeBatch();
            }
         }
      }
   }

   // the result rows as a sorted multiset
   private static List<String> rows(Connection conn, String query) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(Statement stmt = conn.createStatement(); ResultSet result = stmt.executeQuery(query)) {
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
}
