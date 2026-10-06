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
import inetsoft.uql.util.Config;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.util.sqlparser.SQLLexer;
import inetsoft.uql.util.sqlparser.SQLParser;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77576, #77577. {@link UniformSQL#isLossy()} must report the join order refusal of the
 * sql helper that will regenerate the query: for a saved query parsed before the refusal
 * existed (#6150 re-derives it on load), and when the data source of a loaded query changes,
 * e.g. ansi join is turned off, so the Oracle helper regenerates its joins as (+) joins.
 * A lossy query keeps its sql string, so it is executed as written and not merged.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, UniformSQLLossySourceChangeTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLLossySourceChangeTest {
   // refused by every sql helper
   // #77577, the reporter's query: an inner ON condition of a nested join moved to WHERE
   private static final String R1 = "select a.id, b.id, c.id, d.id from a join b on a.id = b.id " +
      "left join (c join d on c.id = d.id and d.x = 1) on b.id = c.id";
   // #77577, an inner ON condition before a RIGHT join
   private static final String R2 = "select a.id, b.id, c.id from a join b on a.id = b.id " +
      "and b.x = 1 right join c on b.id = c.id";
   // #77577, a three level nest
   private static final String NEST3 = "select a.id, b.id, c.id, d.id from a left join " +
      "(b left join (c join d on c.id = d.id and d.x = 1) on b.id = c.id) on a.id = b.id";
   // #77576, a nested join on the right side of a LEFT join
   private static final String NESTED_FILTER = "select a.id, b.id, c.id from a left join " +
      "(b join c on c.k = 1) on a.id = b.id";
   // #77576, accepted by an ansi helper and refused by Oracle without ansi join
   private static final String NESTED = "select a.id, b.id, c.id from a left join " +
      "(b join c on b.id = c.id) on a.id = b.id";
   private static final String RIGHT_MIXED = "select a.id, b.id, c.id from a join b " +
      "on a.id = b.id right join c on b.id = c.id";

   private static final String[] ALL_REFUSED = { R1, R2, NEST3, NESTED_FILTER };
   private static final String[] ORACLE_REFUSED = { NESTED, RIGHT_MIXED };

   /**
    * Saved before #6034 with lossy="false". Loaded on any helper it is lossy, not merged, and
    * plain execution runs the saved sql string, which returns the rows of the original sql on
    * Derby. The structure regenerated from the saved parse returns other rows.
    */
   @Test
   void savedRefusedQueryRunsAsWritten() throws Exception {
      for(String text : ALL_REFUSED) {
         for(String key : new String[] { "h2", "oracle", "oracle-ansi" }) {
            UniformSQL sql = load(savedBeforeJoinOrderCheck(text), helpers().get(key));

            assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), key + ": " + text);
            assertTrue(sql.isLossy(), key + ": " + text);
            assertFalse(XUtil.isQueryMergeable(query(sql)), key + ": " + text);
         }

         UniformSQL sql = load(savedBeforeJoinOrderCheck(text), helpers().get("h2"));
         String regenerated = regenerate(sql.clone());
         JDBCQuery query = query(sql);
         JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query);

         assertFalse(normalizer.isClearedSqlString(), text);
         assertEquals(text, query.getSQLAsString(), text);
         assertNotEquals(rows(text), rows(regenerated), regenerated);
      }
   }

   /**
    * A query parsed on an ansi helper and saved, then loaded on Oracle without ansi join, is
    * lossy there, since its (+) joins regenerate other rows.
    */
   @Test
   void ansiSavedQueryLoadedOnOracleIsLossy() throws Exception {
      for(String text : ORACLE_REFUSED) {
         for(String from : new String[] { "h2", "oracle-ansi" }) {
            UniformSQL saved = parse(text, helpers().get(from));
            assertFalse(saved.isLossy(), from + ": " + text);
            String xml = toXML(saved);
            assertTrue(xml.contains("lossy=\"false\""), xml);

            UniformSQL sql = load(xml, helpers().get("oracle"));

            assertTrue(sql.isLossy(), from + ": " + text);
            assertFalse(XUtil.isQueryMergeable(query(sql)), from + ": " + text);
            assertEquals(text, query(sql).getSQLAsString());

            assertFalse(load(xml, helpers().get(from)).isLossy(), from + ": " + text);
         }
      }
   }

   /**
    * The same object when its data source changes: lossy is re-derived with the new helper.
    */
   @Test
   void sourceChangeRederivesLossy() throws Exception {
      for(String text : ORACLE_REFUSED) {
         for(String from : new String[] { "h2", "oracle-ansi" }) {
            UniformSQL sql = load(toXML(parse(text, helpers().get(from))), helpers().get(from));
            assertFalse(sql.isLossy(), from + ": " + text);

            sql.setDataSource(helpers().get("oracle"));

            assertTrue(sql.isLossy(), from + " -> oracle: " + text);
            assertFalse(XUtil.isQueryMergeable(query(sql)), from + " -> oracle: " + text);
            assertEquals(text, query(sql).getSQLAsString());
         }

         // and back, the query is mergeable again
         UniformSQL sql = load(toXML(parse(text, helpers().get("h2"))), helpers().get("oracle"));
         assertTrue(sql.isLossy(), text);

         sql.setDataSource(helpers().get("h2"));

         assertFalse(sql.isLossy(), "oracle -> h2: " + text);
         assertTrue(XUtil.isQueryMergeable(query(sql)), "oracle -> h2: " + text);
      }

      // refused by every helper, so it stays lossy
      for(String text : ALL_REFUSED) {
         UniformSQL sql = load(savedBeforeJoinOrderCheck(text), helpers().get("h2"));
         assertTrue(sql.isLossy(), text);

         sql.setDataSource(helpers().get("oracle"));

         assertTrue(sql.isLossy(), text);
      }
   }

   /**
    * #77577. An inner ON condition moved to WHERE is harmless when no later outer join makes
    * its table optional. Such a saved query stays non-lossy and mergeable, and its regenerated
    * sql returns the rows of the original on Derby. The shapes with a RIGHT join are refused by
    * Oracle without ansi join, so they become lossy when the data source changes to it.
    */
   @Test
   void savedHarmlessInnerOnConditionStaysMergeable() throws Exception {
      String leftAfter = "select a.id, b.id, c.id from a join b on a.id = b.id and b.x = 2 " +
         "left join c on b.id = c.id";
      String innerAfterRight = "select a.id, b.id, c.id from a right join b on a.id = b.id " +
         "join c on b.id = c.id and c.x = 2";
      String twoTableBeforeRight = "select a.id, b.id, c.id from a join b on a.id = b.id " +
         "and a.x < b.x right join c on b.id = c.id";

      for(String text : new String[] { leftAfter, innerAfterRight, twoTableBeforeRight }) {
         assertFalse(rows(text).isEmpty(), text);

         String xml = toXML(parse(text, helpers().get("h2")));
         UniformSQL sql = load(xml, helpers().get("h2"));
         assertFalse(sql.isLossy(), text);
         assertTrue(XUtil.isQueryMergeable(query(sql)), text);

         JDBCQuery query = query(sql);
         JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query);
         assertTrue(normalizer.isClearedSqlString(), text);
         String regenerated = query.getSQLAsString();
         assertNotEquals(text, regenerated);
         assertEquals(rows(text), rows(regenerated), regenerated);

         sql = load(xml, helpers().get("h2"));
         assertFalse(sql.isLossy(), text);
         sql.setDataSource(helpers().get("oracle"));
         assertEquals(!leftAfter.equals(text), sql.isLossy(), "h2 -> oracle: " + text);
      }
   }

   /**
    * Turning off ansi join on a data source makes a copy that isn't equal to the old one.
    */
   @Test
   void ansiJoinToggleRederivesLossy() throws Exception {
      JDBCDataSource ds = (JDBCDataSource) helpers().get("oracle-ansi").clone();
      UniformSQL sql = load(toXML(parse(NESTED, ds)), ds);
      assertFalse(sql.isLossy());

      JDBCDataSource changed = (JDBCDataSource) ds.clone();
      changed.setAnsiJoin(false);
      sql.setDataSource(changed);

      assertTrue(sql.isLossy());
   }

   /**
    * The map key access check (Bug #72243) needs a ClickHouse source, so a verdict derived
    * without a data source must not survive setting one, from none or back from none.
    */
   @Test
   void clickHouseMapKeySourceSetAfterNone() throws Exception {
      String text = "select t.m['k'] from t";
      JDBCDataSource ch = dataSource("com.clickhouse.jdbc.ClickHouseDriver",
                                     "jdbc:clickhouse://localhost:8123/x", "clickhouse", false);
      assertEquals(JDBCDataSource.JDBC_CLICKHOUSE, ch.getDatabaseType());
      // parsed with the ClickHouse source so the map key access is pre-quoted before lexing
      // (Bug #77661: without that, t.m['k'] has no dedicated subscript grammar and is
      // ambiguous with an implicit alias, so a non-ClickHouse/Databricks dialect now refuses
      // the parse instead of silently misreading it as t.m aliased "k")
      String xml = toXML(parse(text, ch));

      // null -> ch
      UniformSQL sql = load(xml, null);
      // without a data source, the generic re-parse used to verify the cached text can
      // still be safely regenerated has no CH-aware pre-quoting to fall back on, so the
      // ambiguous t.m['k'] now refuses to parse there too (Bug #77661) and the verdict is
      // conservatively lossy, instead of the old, incorrect non-lossy verdict that came from
      // a misparse of t.m['k'] as t.m aliased "k"
      assertTrue(sql.isLossy());
      sql.setDataSource(ch);
      assertTrue(sql.isLossy(), "null -> ch");
      assertFalse(XUtil.isQueryMergeable(query(sql)), "null -> ch");

      // ch -> null -> ch
      sql = load(xml, ch);
      assertTrue(sql.isLossy());
      sql.setDataSource(null);
      assertTrue(sql.isLossy());
      sql.setDataSource(ch);
      assertTrue(sql.isLossy(), "ch -> null -> ch");

      JDBCQuery query = query(sql);
      assertFalse(XUtil.isQueryMergeable(query), "ch -> null -> ch");
      assertFalse(new JDBCQueryCacheNormalizer(query).isClearedSqlString(), "ch -> null -> ch");
      assertEquals(text, query.getSQLAsString());
   }

   /**
    * isLossy() without a data source doesn't cache the skipped join order check (#6150), and
    * the data source set afterwards decides it.
    */
   @Test
   void sourceSetAfterLoadDecidesLossy() throws Exception {
      String xml = toXML(parse(NESTED, helpers().get("h2")));

      UniformSQL sql = load(xml, null);
      sql.isLossy();
      sql.setDataSource(helpers().get("h2"));
      assertFalse(sql.isLossy());
      assertTrue(XUtil.isQueryMergeable(query(sql)));

      sql = load(xml, null);
      sql.isLossy();
      sql.setDataSource(helpers().get("oracle"));
      assertTrue(sql.isLossy());
   }

   /**
    * With parsing off, the saved lossy flag can't be re-derived and is kept (#77477).
    */
   @Test
   void parseOffKeepsSavedLossy() throws Exception {
      for(String lossy : new String[] { "true", "false" }) {
         Element xml = Tool.parseXML(new StringReader(toXML(parse(NESTED, helpers().get("h2")))))
            .getDocumentElement();
         xml.setAttribute("parse", "false");
         xml.setAttribute("lossy", lossy);

         UniformSQL sql = load(toXML(xml), helpers().get("h2"));
         assertEquals(Boolean.parseBoolean(lossy), sql.isLossy());

         sql.setDataSource(helpers().get("oracle"));

         assertEquals(Boolean.parseBoolean(lossy), sql.isLossy());
      }
   }

   /**
    * Changing to an equal data source keeps the derived lossy, and a query without a sql string
    * is not lossy whatever its data source.
    */
   @Test
   void unchangedSourceOrNoSqlString() throws Exception {
      UniformSQL sql = load(toXML(parse(NESTED, helpers().get("oracle-ansi"))),
                            helpers().get("oracle-ansi"));
      assertFalse(sql.isLossy());
      sql.setDataSource((JDBCDataSource) helpers().get("oracle-ansi").clone());
      assertFalse(sql.isLossy());

      sql.clearSQLString();
      sql.setDataSource(helpers().get("oracle"));
      assertFalse(sql.isLossy());
   }

   @BeforeEach
   void createTables() throws Exception {
      try(Connection conn = derby(); Statement stmt = conn.createStatement()) {
         for(String table : new String[] { "a", "b", "c", "d" }) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(SQLException ignore) {
               // first run
            }

            stmt.executeUpdate("create table " + table + " (id int, x int, k int)");
         }

         // every inner ON condition is false, so the outer join keeps its null-extended rows
         stmt.executeUpdate("insert into a values (1, 1, 1), (2, 2, 2)");
         stmt.executeUpdate("insert into b values (1, 2, 1)");
         stmt.executeUpdate("insert into c values (1, 2, 2), (4, 1, 1)");
         stmt.executeUpdate("insert into d values (1, 2, 1)");
      }
   }

   private static Connection derby() throws SQLException {
      return DriverManager.getConnection("jdbc:derby:memory:bug77576;create=true");
   }

   private static List<String> rows(String query) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(Connection conn = derby(); Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(query))
      {
         int count = rs.getMetaData().getColumnCount();

         while(rs.next()) {
            List<String> row = new ArrayList<>();

            for(int i = 1; i <= count; i++) {
               row.add(String.valueOf(rs.getObject(i)));
            }

            rows.add(row.toString());
         }
      }

      Collections.sort(rows);
      return rows;
   }

   /**
    * The XML of a query parsed and saved before the join order check (#6034): the grammar
    * accepted it, parse result success, and isLossy() had saved lossy="false".
    */
   private static String savedBeforeJoinOrderCheck(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(helpers().get("h2"));
      SQLParser parser = new SQLParser(new SQLLexer(new StringReader(text)));
      parser.direct_select_stmt_n_rows(sql);
      assertFalse(parser.getJoinOrderChecks().isEmpty(), text);
      sql.setParseResult(UniformSQL.PARSE_SUCCESS);
      sql.setSQLString(text, false);
      sql.setLossy(false);

      String xml = toXML(sql);
      assertTrue(xml.contains("lossy=\"false\""), xml);
      assertTrue(xml.contains("parseResult=\"0\""), xml);
      return xml;
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      sql.setSQLString(text, false);
      return sql;
   }

   private static UniformSQL load(String xml, JDBCDataSource ds) throws Exception {
      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());

      if(ds != null) {
         loaded.setDataSource(ds);
      }

      return loaded;
   }

   private static String regenerate(UniformSQL sql) {
      sql.clearSQLString();
      return sql.getSQLString();
   }

   private static JDBCQuery query(UniformSQL sql) {
      JDBCQuery query = new JDBCQuery();
      query.setName("q77576");
      query.setDataSource(sql.getDataSource());
      query.setSQLDefinition(sql);
      return query;
   }

   private static String toXML(UniformSQL sql) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      return buffer.toString();
   }

   private static String toXML(Element elem) throws Exception {
      StringWriter buffer = new StringWriter();
      javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(
         new javax.xml.transform.dom.DOMSource(elem), new javax.xml.transform.stream.StreamResult(buffer));
      return buffer.toString();
   }

   private static Map<String, JDBCDataSource> helpers() {
      Map<String, JDBCDataSource> helpers = new LinkedHashMap<>();
      helpers.put("h2", dataSource("org.h2.Driver", "jdbc:h2:mem:x", "h2", false));
      helpers.put("oracle", dataSource("oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@localhost:1521:x",
                                       "oracle", false));
      helpers.put("oracle-ansi", dataSource("oracle.jdbc.OracleDriver",
                                            "jdbc:oracle:thin:@localhost:1521:x", "oracle", true));
      return helpers;
   }

   private static JDBCDataSource dataSource(String driver, String url, String product, boolean ansiJoin) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77576" + product);
      ds.setDriver(driver);
      ds.setURL(url);
      ds.setRuntimeProductName(product);
      // otherwise the oracle helper asks the repository for it
      ds.setProductVersion("19.0");
      ds.setAnsiJoin(ansiJoin);
      return ds;
   }

   // a JDBCDataSource creates its credential when it is constructed
   @Configuration
   static class Beans {
      @Bean
      Config config() {
         Config config = mock(Config.class);
         when(config.getJDBCType(any())).thenAnswer(
            inv -> String.valueOf((Object) inv.getArgument(0)).contains("oracle") ? "oracle" : "H2");
         return config;
      }

      @Bean
      CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         when(service.createCredential(any(), anyBoolean())).thenAnswer(inv -> new LocalPasswordCredential());
         return service;
      }
   }
}
