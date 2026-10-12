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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.util.Config;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.Plugins;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77478, a WHERE comparison (or a later inner join ON comparison) between two tables
 * that are outer joined was stored as a join and regenerated inside the outer join's ON
 * condition, where it no longer drops the null extended rows. It is now stored as a plain
 * condition and regenerated in the where clause.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  UniformSQLWhereOnOuterPairTest.DataSourceConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLWhereOnOuterPairTest {
   private static final String COLS = "select a.id, a.k, b.id, b.k from ";

   /**
    * JDBCDataSource needs CredentialService, and SQLHelper.getSQLHelper(ds) maps the driver to
    * a database type through Config. BaseTestConfiguration has neither.
    */
   @Configuration
   static class DataSourceConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      @Bean
      public Plugins plugins(BlobStorageManager blobStorageManager, Cluster cluster,
                             ApplicationEventPublisher eventPublisher)
      {
         return new Plugins(blobStorageManager.getStorage("plugins", true), cluster, eventPublisher);
      }

      @Bean
      public Config config(Plugins plugins) {
         return new Config(plugins);
      }
   }

   static Stream<Arguments> convertedShapes() {
      return Stream.of(
         // WHERE-sourced, two tables
         Arguments.of(COLS + "a left join b on a.id = b.id where a.k = b.k",
                      COLS + "a LEFT OUTER JOIN b ON a.id = b.id where a.k = b.k"),
         Arguments.of(COLS + "a left join b on a.id = b.id where b.k = a.k",
                      COLS + "a LEFT OUTER JOIN b ON a.id = b.id where b.k = a.k"),
         Arguments.of(COLS + "a left join b on a.id = b.id where a.k < b.k",
                      COLS + "a LEFT OUTER JOIN b ON a.id = b.id where a.k < b.k"),
         Arguments.of(COLS + "a left join b on a.id = b.id where a.k <> b.k",
                      COLS + "a LEFT OUTER JOIN b ON a.id = b.id where a.k <> b.k"),
         Arguments.of(COLS + "a left join b on a.id = b.id where a.id = b.id",
                      COLS + "a LEFT OUTER JOIN b ON a.id = b.id where a.id = b.id"),
         Arguments.of(COLS + "a left join b on a.id = b.id where a.k = b.k and a.id > 0",
                      COLS + "a LEFT OUTER JOIN b ON a.id = b.id where a.k = b.k and a.id > 0"),
         Arguments.of(COLS + "a left join b on a.id = b.id where a.k = b.k or a.id = 1",
                      COLS + "a LEFT OUTER JOIN b ON a.id = b.id where (a.k = b.k or a.id = 1)"),
         Arguments.of(COLS + "a left join b on a.id = b.id where not (a.k = b.k)",
                      COLS + "a LEFT OUTER JOIN b ON a.id = b.id where not (a.k = b.k)"),
         Arguments.of(COLS + "a right join b on a.id = b.id where a.k = b.k",
                      COLS + "a RIGHT OUTER JOIN b ON a.id = b.id where a.k = b.k"),
         Arguments.of(COLS + "a full join b on a.id = b.id where a.k = b.k",
                      COLS + "a FULL OUTER JOIN b ON a.id = b.id where a.k = b.k"),
         // qualifiers that differ in case from the FROM and ON (SQLHelper matches ignoring case)
         Arguments.of(COLS + "a left join b on a.id = b.id where A.k = B.k",
                      COLS + "a LEFT OUTER JOIN b ON a.id = b.id where A.k = B.k"),
         Arguments.of("select x.id from Orders x left join Lines y on x.id = y.id where X.k = Y.k",
                      "select x.id from Orders x LEFT OUTER JOIN Lines y ON x.id = y.id where X.k = Y.k"),
         Arguments.of(COLS + "a left join b on a.id = b.id join c on a.k = B.k",
                      COLS + "a LEFT OUTER JOIN b ON a.id = b.id , c where a.k = B.k"),
         Arguments.of("select x1.id from a x1 left join a x2 on x1.id = x2.id where x1.k = x2.k",
                      "select x1.id from a x1 LEFT OUTER JOIN a x2 ON x1.id = x2.id where x1.k = x2.k"),
         // WHERE-sourced, three tables. The joins keep the nesting of the text (#77475)
         Arguments.of(COLS + "a left join b on a.id = b.id join c on a.id = c.id where a.k = b.k",
                      COLS + "(a LEFT OUTER JOIN b ON a.id = b.id ) INNER JOIN c ON a.id = c.id " +
                      "where a.k = b.k"),
         Arguments.of(COLS + "a left join b on a.id = b.id left join c on b.id = c.id where b.k = c.k",
                      COLS + "(a LEFT OUTER JOIN b ON a.id = b.id ) LEFT OUTER JOIN c ON b.id = c.id " +
                      "where b.k = c.k"),
         Arguments.of(COLS + "a left join b on a.id = b.id left join c on a.id = c.id where a.k = b.k",
                      COLS + "(a LEFT OUTER JOIN b ON a.id = b.id ) LEFT OUTER JOIN c ON a.id = c.id " +
                      "where a.k = b.k"),
         Arguments.of(COLS + "a left join b on a.id = b.id full join c on a.id = c.id where a.k = b.k",
                      COLS + "(a LEFT OUTER JOIN b ON a.id = b.id ) FULL OUTER JOIN c ON a.id = c.id " +
                      "where a.k = b.k"),
         Arguments.of(COLS + "a left join (b join c on b.id = c.id) on a.id = b.id where a.k = b.k",
                      COLS + "(b INNER JOIN c ON b.id = c.id ) RIGHT OUTER JOIN a ON a.id = b.id " +
                      "where a.k = b.k"),
         // legacy outer joins in the where clause, in both orders
         Arguments.of(COLS + "a, b where a.k = b.k and a.id = b.id(+)",
                      COLS + "a LEFT OUTER JOIN b ON a.id = b.id where a.k = b.k"),
         Arguments.of(COLS + "a, b where a.id = b.id(+) and a.k = b.k",
                      COLS + "a LEFT OUTER JOIN b ON a.id = b.id where a.k = b.k"),
         Arguments.of(COLS + "a, b where a.id *= b.id and a.k = b.k",
                      COLS + "a LEFT OUTER JOIN b ON a.id = b.id where a.k = b.k"),
         // ON-sourced: a later inner join's ON compares the outer joined tables
         Arguments.of(COLS + "a left join b on a.id = b.id join c on a.k = b.k",
                      COLS + "a LEFT OUTER JOIN b ON a.id = b.id , c where a.k = b.k"),
         Arguments.of(COLS + "a left join b on a.id = b.id join c on a.id = b.id",
                      COLS + "a LEFT OUTER JOIN b ON a.id = b.id , c where a.id = b.id"),
         Arguments.of(COLS + "a left join b on a.id = b.id join c on a.k = b.k and a.id = c.id",
                      COLS + "(a LEFT OUTER JOIN b ON a.id = b.id ) INNER JOIN c ON a.id = c.id " +
                      "where a.k = b.k"),
         // a later LEFT join only null-supplies the new table
         Arguments.of(COLS + "a left join b on a.id = b.id join c on a.id = c.id and a.k = b.k " +
                      "left join d on c.id = d.id",
                      COLS + "((a LEFT OUTER JOIN b ON a.id = b.id ) INNER JOIN c ON a.id = c.id ) " +
                      "LEFT OUTER JOIN d ON c.id = d.id where a.k = b.k"),
         // each query level is converted on its own
         Arguments.of("select a.id from a where a.id in " +
                      "(select b.id from b left join c on b.id = c.id where b.k = c.k)",
                      "select a.id from a where a.id IN ( select b.id from b " +
                      "LEFT OUTER JOIN c ON b.id = c.id where b.k = c.k)"),
         Arguments.of("select t.id from (select b.id from b left join c on b.id = c.id where b.k = c.k) t",
                      "select t.id from ( select b.id from b LEFT OUTER JOIN c ON b.id = c.id " +
                      "where b.k = c.k) t")
      );
   }

   @ParameterizedTest
   @MethodSource("convertedShapes")
   void comparisonOnOuterPairStaysInWhere(String text, String expected) throws Exception {
      UniformSQL sql = parse(text, null);
      assertAccepted(text, sql);

      // subquery shapes are checked by the regenerated text
      if(!text.contains("(select")) {
         assertTrue(containsCondition(sql.getWhere()), String.valueOf(sql.getWhere()));
      }

      String generated = normalize(sql.getSQLString());
      assertEquals(expected, generated);
      assertRoundTrip(generated, null);
   }

   /**
    * A RIGHT/FULL join, or an outer join of a parenthesized operand, after the inner join
    * makes the outer joined pair null supplying, so the ON comparison is not a where filter.
    * Those are left exactly as before (no conversion and no refusal).
    */
   @ParameterizedTest
   @ValueSource(strings = {
      COLS + "a left join b on a.id = b.id join c on a.id = c.id and a.k = b.k full join d on c.id = d.id",
      COLS + "a left join b on a.id = b.id join c on a.id = c.id and a.k = b.k right join d on c.id = d.id"
   })
   void onComparisonBeforeNullSupplyingJoinIsUnchanged(String text) throws Exception {
      UniformSQL sql = parse(text, null);
      assertAccepted(text, sql);
      assertTrue(Arrays.stream(joins(sql)).anyMatch(
         j -> !j.isOuterJoin() && "a.k = b.k".equals(j.toString())), Arrays.toString(joins(sql)));
      assertFalse(containsCondition(sql.getWhere()), String.valueOf(sql.getWhere()));
   }

   /**
    * Bug #77434 (#77515), an inner join ON that doesn't name its joined table (c) leaves c
    * with no join, so the generated sql joins c outside of the outer join that follows it
    * and returns other rows. The generated sql doesn't have the same joins, so the parse
    * fails with every data source.
    */
   @ParameterizedTest
   @ValueSource(strings = {
      COLS + "a left join b on a.id = b.id join c on a.k = b.k right join d on c.id = d.id",
      COLS + "a left join b on a.id = b.id join c on a.k = b.k full join d on c.id = d.id",
      COLS + "d left join (a left join b on a.id = b.id join c on a.k = b.k) on d.id = a.id"
   })
   void onComparisonOfUnjoinedTableBeforeNullSupplyingJoinFails(String text) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(GenericJDBCDataSource.create());
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);
      assertEquals(text, sql.getSQLString());
   }

   @ParameterizedTest
   @ValueSource(strings = {
      COLS + "a join b on a.id = b.id where a.k = b.k",
      COLS + "a, b where a.id = b.id and a.k = b.k",
      COLS + "a left join b on a.id = b.id and a.k = b.k",
      COLS + "a left join b on a.id = b.id join c on b.id = c.id"
   })
   void comparisonWithoutOuterPairIsStillJoin(String text) throws Exception {
      UniformSQL sql = parse(text, null);
      assertAccepted(text, sql);
      assertFalse(containsCondition(sql.getWhere()), String.valueOf(sql.getWhere()));
   }

   @Test
   void correlatedSubqueryLevelIsConverted() throws Exception {
      // the regenerated sql of this shape is still wrong because of #77480, so only check
      // that the subquery's own WHERE comparison became a plain condition
      String text = "select a.id from a where exists (select 1 from b left join c " +
         "on b.id = c.id where b.k = c.k and b.id = a.id)";
      UniformSQL sql = parse(text, null);
      assertAccepted(text, sql);
      UniformSQL sub = findSubquery(sql.getWhere());
      assertNotNull(sub);
      assertTrue(containsCondition(sub.getWhere()), String.valueOf(sub.getWhere()));
      assertTrue(Arrays.stream(joins(sub)).anyMatch(XJoin::isOuterJoin));
   }

   static Stream<Arguments> dataSources() {
      return Stream.of(
         Arguments.of("default", null),
         Arguments.of("H2", dataSource("org.h2.Driver", "jdbc:h2:mem:bug77478")),
         Arguments.of("PostgreSQL", dataSource("org.postgresql.Driver", "jdbc:postgresql://localhost:5432/db")),
         Arguments.of("Oracle", dataSource("oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@localhost:1521:orcl"))
      );
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("dataSources")
   void dataSourceHelpersKeepFilterOutOfOn(String name, JDBCDataSource ds) throws Exception {
      // the data source loads the dialect's own helper
      assertEquals(name.toLowerCase(), SQLHelper.getSQLHelper(ds).getSQLHelperType());

      for(String text : new String[] {
         COLS + "a left join b on a.id = b.id where a.k = b.k",
         COLS + "a left join b on a.id = b.id join c on a.id = c.id where a.k = b.k" })
      {
         UniformSQL sql = parse(text, ds);
         assertAccepted(text, sql);

         for(XJoin join : joins(sql)) {
            assertFalse("a.k = b.k".equals(join.toString().replace("\"", "")), join.toString());
         }

         String generated = normalize(sql.getSQLString());
         String unquoted = generated.replace("\"", "");
         int where = unquoted.indexOf(" where ");
         assertTrue(where > 0, generated);

         if("Oracle".equals(name)) {
            assertTrue(unquoted.contains("a.id = b.id(+)"), generated);
         }
         else {
            assertTrue(unquoted.contains("LEFT OUTER JOIN b ON a.id = b.id"), generated);
            assertFalse(unquoted.substring(0, where).contains("a.k = b.k"), generated);
         }

         assertTrue(unquoted.substring(where).contains("a.k = b.k"), generated);
         assertRoundTrip(generated, ds);
      }
   }

   /**
    * Join models built by the query editor (two links on one table pair) don't go through
    * the parser, and must regenerate exactly as before.
    */
   @Test
   void editorJoinModelsAreUnchanged() throws Exception {
      UniformSQL e1 = editorModel("*=", "=");
      String g1 = normalize(e1.getSQLString());
      assertEquals(COLS + "a LEFT OUTER JOIN b ON a.id = b.id AND a.k = b.k", g1);
      // the regenerated text is an outer ON with two equalities, which is still one join
      UniformSQL r1 = parse(g1, null);
      assertEquals(2, r1.getJoins().length);
      assertEquals(g1, normalize(r1.getSQLString()));

      assertEquals(COLS + "a INNER JOIN b ON a.id = b.id AND a.k = b.k",
                   normalize(editorModel("=", "*=").getSQLString()));
      assertEquals(COLS + "a LEFT OUTER JOIN b ON a.id = b.id AND a.k < b.k",
                   normalize(editorModel("*=", "<").getSQLString()));
      assertEquals(COLS + "a RIGHT OUTER JOIN b ON a.id = b.id AND a.k = b.k",
                   normalize(editorModel("=*", "=").getSQLString()));
   }

   /**
    * Original and regenerated SQL must return the same rows. Derby has LEFT and RIGHT
    * joins but no FULL join.
    */
   @ParameterizedTest
   @ValueSource(strings = {
      COLS + "a left join b on a.id = b.id where a.k = b.k",
      COLS + "a left join b on a.id = b.id where b.k = a.k",
      COLS + "a left join b on a.id = b.id where A.k = B.k",
      COLS + "a left join b on a.id = b.id join c on a.k = B.k",
      COLS + "a left join b on a.id = b.id where a.k < b.k",
      COLS + "a left join b on a.id = b.id where a.k = b.k or a.id = 1",
      COLS + "a left join b on a.id = b.id where not (a.k = b.k)",
      COLS + "a right join b on a.id = b.id where a.k = b.k",
      COLS + "a left join b on a.id = b.id join c on a.id = c.id where a.k = b.k",
      COLS + "a left join b on a.id = b.id left join c on b.id = c.id where b.k = c.k",
      COLS + "a left join b on a.id = b.id left join c on a.id = c.id where a.k = b.k",
      COLS + "a left join (b join c on b.id = c.id) on a.id = b.id where a.k = b.k",
      COLS + "a left join b on a.id = b.id join c on a.k = b.k",
      COLS + "a left join b on a.id = b.id join c on a.k = b.k and a.id = c.id",
      COLS + "a left join b on a.id = b.id join c on a.id = c.id and a.k = b.k left join d on c.id = d.id",
      "select x1.id, x2.id from a x1 left join a x2 on x1.id = x2.id where x1.k = x2.k",
      "select a.id from a where a.id in (select b.id from b left join c on b.id = c.id where b.k = c.k)"
   })
   void regeneratedSqlReturnsSameRows(String text) throws Exception {
      assertSameRows(text, parse(text, null).getSQLString());
   }

   /**
    * A plain (unmerged) SQL query on the data cache path also runs regenerated SQL:
    * XSessionManager creates a JDBCQueryCacheNormalizer, which clears the SQL string of a
    * parsed query, so the SQL is generated from the parsed structure.
    */
   @ParameterizedTest
   @ValueSource(strings = {
      COLS + "a left join b on a.id = b.id where a.k = b.k",
      COLS + "a left join b on a.id = b.id join c on a.k = b.k",
      COLS + "a right join d on a.id = d.id left join b on a.id = b.id join c on a.k = b.k"
   })
   void dataCacheNormalizerRegeneratesSameRows(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(GenericJDBCDataSource.create());

      synchronized(sql) {
         sql.setSQLString(text);
         sql.wait(20000);
      }

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      JDBCQuery query = new JDBCQuery();
      query.setSQLDefinition(sql);
      JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query);
      assertTrue(normalizer.isClearedSqlString(), text);

      String generated = sql.getSQLString();
      assertNotEquals(text, generated);
      assertSameRows(text, generated);
   }

   @AfterAll
   static void dropDerbyDatabase() {
      try {
         DriverManager.getConnection("jdbc:derby:memory:bug77478;drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a successful drop (or a database never created) as an exception
      }
   }

   private static void assertSameRows(String text, String generated) throws Exception {
      Random random = new Random(77478);

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77478;create=true")) {
         try(Statement stmt = conn.createStatement()) {
            for(String table : TABLES) {
               try {
                  stmt.execute("drop table " + table);
               }
               catch(SQLException ignore) {
                  // first use
               }

               stmt.execute("create table " + table + " (id int, k int)");
            }
         }

         for(int n = 0; n < 150; n++) {
            fill(conn, random);
            assertEquals(rows(conn, text), rows(conn, generated),
                         "data set " + n + ": " + generated);
         }
      }
   }

   private static void fill(Connection conn, Random random) throws SQLException {
      try(Statement stmt = conn.createStatement()) {
         for(String table : TABLES) {
            stmt.execute("delete from " + table);
         }
      }

      for(String table : TABLES) {
         try(PreparedStatement ps = conn.prepareStatement("insert into " + table + " values (?, ?)")) {
            int count = random.nextInt(5);

            for(int i = 0; i < count; i++) {
               setValue(ps, 1, random);
               setValue(ps, 2, random);
               ps.executeUpdate();
            }
         }
      }
   }

   private static void setValue(PreparedStatement ps, int idx, Random random) throws SQLException {
      int value = random.nextInt(4);

      if(value == 3) {
         ps.setNull(idx, Types.INTEGER);
      }
      else {
         ps.setInt(idx, value);
      }
   }

   private static List<String> rows(Connection conn, String query) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
         int count = rs.getMetaData().getColumnCount();

         while(rs.next()) {
            StringBuilder row = new StringBuilder();

            for(int i = 1; i <= count; i++) {
               row.append(rs.getObject(i)).append('|');
            }

            rows.add(row.toString());
         }
      }

      Collections.sort(rows);
      return rows;
   }

   private static UniformSQL editorModel(String op1, String op2) throws Exception {
      UniformSQL sql = parse(COLS + "a, b", null);
      sql.addJoin(new XJoin(new XExpression("a.id", XExpression.FIELD),
                            new XExpression("b.id", XExpression.FIELD), op1));
      sql.addJoin(new XJoin(new XExpression("a.k", XExpression.FIELD),
                            new XExpression("b.k", XExpression.FIELD), op2));
      sql.clearSQLString();
      return sql;
   }

   private static JDBCDataSource dataSource(String driver, String url) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77478");
      ds.setDriver(driver);
      ds.setURL(url);
      // keeps SQLHelper from asking the database for its version
      ds.setProductVersion("19");
      return ds;
   }

   private static void assertAccepted(String text, UniformSQL sql) {
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);

      UniformSQL processed = new UniformSQL();
      processed.setDataSource(GenericJDBCDataSource.create());
      new SQLProcessor(processed).parse(text);
      // a where clause outer join is refused with a data source that writes ANSI joins
      // (Bug #77548)
      boolean whereOuter = text.contains("(+)") || text.contains("*=");
      assertEquals(whereOuter ? UniformSQL.PARSE_FAILED : UniformSQL.PARSE_SUCCESS,
                   processed.getParseResult(), text);
   }

   private static void assertRoundTrip(String generated, JDBCDataSource ds) throws Exception {
      UniformSQL reparsed = parse(generated, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, reparsed.getParseResult(), generated);
      String regenerated = normalize(reparsed.getSQLString());
      String swapped = SWAPPED_ON.get(generated);

      // a right join of a parenthesized joined table is written back once with its ON
      // operands swapped (#77475), and is stable from then on
      if(swapped != null) {
         assertEquals(swapped, regenerated);
         assertRoundTrip(swapped, ds);
         return;
      }

      assertEquals(generated, regenerated);
   }

   private static final Map<String, String> SWAPPED_ON = Map.of(
      COLS + "(b INNER JOIN c ON b.id = c.id ) RIGHT OUTER JOIN a ON a.id = b.id where a.k = b.k",
      COLS + "(b INNER JOIN c ON b.id = c.id ) RIGHT OUTER JOIN a ON b.id = a.id where a.k = b.k");

   private static XJoin[] joins(UniformSQL sql) {
      XJoin[] joins = sql.getJoins();
      return joins == null ? new XJoin[0] : joins;
   }

   // true if the tree has a plain column comparison between two different tables
   private static boolean containsCondition(XFilterNode node) {
      if(node instanceof XJoin) {
         return false;
      }

      if(node instanceof XBinaryCondition cond) {
         XExpression e1 = cond.getExpression1();
         XExpression e2 = cond.getExpression2();
         return e1 != null && e2 != null && XExpression.FIELD.equals(e1.getType()) &&
            XExpression.FIELD.equals(e2.getType()) && !table(e1).equals(table(e2));
      }

      for(int i = 0; node != null && i < node.getChildCount(); i++) {
         if(containsCondition((XFilterNode) node.getChild(i))) {
            return true;
         }
      }

      return false;
   }

   private static String table(XExpression exp) {
      String value = String.valueOf(exp.getValue());
      int idx = value.indexOf('.');
      return idx < 0 ? "" : value.substring(0, idx);
   }

   private static UniformSQL findSubquery(XFilterNode node) {
      if(node instanceof XBinaryCondition cond) {
         for(XExpression exp : new XExpression[] { cond.getExpression1(), cond.getExpression2() }) {
            if(exp != null && exp.getValue() instanceof UniformSQL sub) {
               return sub;
            }
         }
      }
      else if(node instanceof XUnaryCondition cond && cond.getExpression1() != null &&
         cond.getExpression1().getValue() instanceof UniformSQL sub)
      {
         return sub;
      }

      for(int i = 0; node != null && i < node.getChildCount(); i++) {
         UniformSQL sub = findSubquery((XFilterNode) node.getChild(i));

         if(sub != null) {
            return sub;
         }
      }

      return null;
   }

   // the generated sql is pretty-printed
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      // Bug #77434 refuses a RIGHT or FULL join mixed with an inner join, and a nested
      // join on the right of an outer join, without a data source. A where clause outer join
      // is refused with a data source that writes ANSI joins (Bug #77548), so it's parsed
      // without one, and generated with the generic data source
      boolean whereOuter = ds == null && (text.contains("(+)") || text.contains("*="));
      sql.setDataSource(ds != null ? ds : whereOuter ? null : GenericJDBCDataSource.create());
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);

      if(whereOuter) {
         sql.setDataSource(GenericJDBCDataSource.create());
      }

      return sql;
   }

   private static final String[] TABLES = { "a", "b", "c", "d" };
}
