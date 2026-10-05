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
import inetsoft.uql.XRepository;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77548, a parsed query saved before the parser recorded join clauses (Bug #77475) whose
 * clauses can't be recorded from the parse of its sql string (#6291), or that has a join cycle
 * and an outer join, was only lossy. Vpm regenerates a lossy query: it checks XUtil.isParsedSQL,
 * which ignores lossy, then only warns about lossy and clears the sql string, so the joins were
 * regenerated in the outer-last order and returned different rows. Such a query is now refused
 * (PARSE_FAILED) with a data source, and its sql string runs as written. It's also checked when
 * a query of a data source is loaded, since vpm decides before it calls isLossy().
 * <p>
 * The saved queries are real 1.1.0 XML (UniformSQLLegacyJoinRefusalTest-1.1.0.txt).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 UniformSQLLegacyJoinRefusalTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLLegacyJoinRefusalTest {
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

   private static final String DB = "jdbc:derby:memory:bug77548r";
   private static final String[] TABLES = { "a", "b", "c", "d", "e", "g", "p", "q", "r", "x" };

   // the old parser recorded the outer join of a parenthesized operand from b (Bug #77516)
   private static final String ORIENTATION = "select a.id, b.id, c.id from a left join " +
      "(b join c on b.id = c.id) on b.id = a.id";
   // the old parser recorded the where clause comparison of the outer joined pair as a join
   private static final String WHERE_PAIR = "select a.id, b.id, c.id from a left join b on " +
      "a.id = b.id join c on a.id = c.id where a.k = b.k";
   // the issue's example, a join cycle with outer joins
   private static final String CYCLE = "select a.id, b.id, c.id, d.id, e.id from a left join b " +
      "on a.id = b.id left join c on a.id = c.id join d on c.id = d.id join e on c.id = e.id " +
      "and d.k = e.k";
   // Bug #77674 (V19), refused by the current parser
   private static final String REFUSED_PARSE = "select c.id, d.id, e.id, p.id, q.id, q2.id, " +
      "r.id from d left join c on d.id = c.id join e on e.id = c.id, (p join q on p.id = q.id) " +
      "join (r join q q2 on r.id = q2.id) on p.k = q.k and r.k = q2.k";
   // matched by #6291
   private static final String NESTED = "select b.id, e.id, g.id, p.id from b left join " +
      "(g join e on g.id = e.id) on b.id = e.id join p on e.id = p.id";
   private static final String INNER_CYCLE = "select a.id, b.id, c.id from a join b on " +
      "a.id = b.id join c on b.id = c.id and a.k = c.k";

   static Stream<String> unrecordedQueries() {
      return Stream.of(ORIENTATION, WHERE_PAIR, CYCLE, REFUSED_PARSE);
   }

   @ParameterizedTest
   @MethodSource("unrecordedQueries")
   void unrecordedJoinsAreRefused(String text) throws Exception {
      // with the data source set, in isLossy() (#6291 kept these lossy)
      UniformSQL sql = saved(text, GenericJDBCDataSource.create());
      assertTrue(XUtil.isParsedSQL(sql), text);
      assertTrue(sql.isLossy(), text);
      assertRefused(text, sql);

      // loaded by a query of the data source, before vpm checks it
      JDBCQuery query = loadQuery(text, GenericJDBCDataSource.create());
      UniformSQL loaded = (UniformSQL) query.getSQLDefinition();
      assertNull(loaded.getDataSource());
      assertRefused(text, loaded);
      assertFalse(XUtil.isQueryMergeable(query), text);
   }

   /**
    * The call order of the enterprise VpmUtil.applyConditions for a query that is parsed
    * (XUtil.isParsedSQL): it warns about a lossy sql string, then clears it and regenerates
    * the query from the joins. For these saved queries, that returned different rows.
    */
   @ParameterizedTest
   @MethodSource("unrecordedQueries")
   void vpmRegenerationOfALossyQueryReturnedOtherRows(String text) throws Exception {
      if(REFUSED_PARSE.equals(text)) {
         return;
      }

      // without the refusal: lossy, but parsed, so vpm regenerates it
      UniformSQL sql = saved(text, null);
      sql.isLossy();
      assertTrue(XUtil.isParsedSQL(sql), text);
      UniformSQL copy = sql.clone();
      copy.setSQLString(null);
      copy.setDataSource(GenericJDBCDataSource.create());
      assertNotNull(rowMismatch(text, normalize(copy.getSQLString())), text);

      // a loaded query is refused before vpm checks it, its sql string runs as written
      UniformSQL loaded = (UniformSQL) loadQuery(text, GenericJDBCDataSource.create())
         .getSQLDefinition();
      assertFalse(XUtil.isParsedSQL(loaded), text);
   }

   @Test
   void recordedJoinsAreNotRefused() throws Exception {
      // #6291 records the clauses, the query is regenerated in text order
      UniformSQL sql = saved(NESTED, GenericJDBCDataSource.create());
      assertFalse(sql.isLossy());
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());

      JDBCQuery query = loadQuery(NESTED, GenericJDBCDataSource.create());
      UniformSQL loaded = (UniformSQL) query.getSQLDefinition();
      assertTrue(XUtil.isParsedSQL(loaded));
      assertNull(loaded.getDataSource());

      for(XJoin join : loaded.getJoins()) {
         assertNotEquals(XJoin.UNKNOWN_CLAUSE, join.getJoinClause(), join.toString());
      }

      UniformSQL copy = loaded.clone();
      copy.setDataSource(GenericJDBCDataSource.create());
      copy.setSQLString(null);
      assertNull(rowMismatch(NESTED, normalize(copy.getSQLString())));
   }

   @Test
   void innerJoinCycleStaysLossy() throws Exception {
      // an inner cycle keeps its meaning when it's regenerated (Bug #77489)
      UniformSQL sql = saved(INNER_CYCLE, GenericJDBCDataSource.create());
      assertTrue(sql.isLossy());
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
   }

   @ParameterizedTest
   @MethodSource("unrecordedQueries")
   void withoutDataSourceNothingIsRefused(String text) throws Exception {
      UniformSQL sql = saved(text, null);
      assertTrue(sql.isLossy(), text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);

      JDBCQuery query = new JDBCQuery();
      query.parseXML(element("<query_jdbc>" + savedXml(text, "none") + "</query_jdbc>"));
      assertEquals(UniformSQL.PARSE_SUCCESS,
                   ((UniformSQL) query.getSQLDefinition()).getParseResult(), text);
   }

   @Test
   void legacyWhereClauseOuterJoins() throws Exception {
      // the parse of the sql string is refused with a data source that writes ANSI joins
      String text = "select a.id, b.id, c.id from a, b, c where a.id = b.id(+) and b.id = c.id";
      UniformSQL sql = saved(text, GenericJDBCDataSource.create());
      assertTrue(sql.isLossy());
      assertRefused(text, sql);

      JDBCDataSource oracle = new JDBCDataSource();
      oracle.setName("ds77548r_oracle");
      oracle.setDriver("oracle.jdbc.OracleDriver");
      oracle.setURL("jdbc:oracle:thin:@localhost:1521:orcl");
      oracle.setRuntimeProductName("oracle");
      oracle.setProductVersion("19");
      sql = saved(text, oracle);
      assertFalse(sql.isLossy());
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
   }

   @Test
   void editorJoinsAreUnchanged() throws Exception {
      // joins without a sql string (query editor, data model) aren't checked
      UniformSQL editor = new UniformSQL();
      editor.setDataSource(GenericJDBCDataSource.create());
      editor.parse(ORIENTATION, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);

      for(XJoin join : editor.getJoins()) {
         join.setJoinClause(XJoin.UNKNOWN_CLAUSE);
      }

      editor.clearSQLString();
      assertFalse(editor.isLossy());
      assertEquals(UniformSQL.PARSE_SUCCESS, editor.getParseResult());

      StringWriter buf = new StringWriter();
      editor.writeXML(new PrintWriter(buf));
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(GenericJDBCDataSource.create());
      query.parseXML(element("<query_jdbc>" + buf + "</query_jdbc>"));
      assertEquals(UniformSQL.PARSE_SUCCESS,
                   ((UniformSQL) query.getSQLDefinition()).getParseResult());
   }

   private static void assertRefused(String text, UniformSQL sql) {
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);
      assertEquals(text, sql.getSQLString());
      assertTrue(sql.isLossy(), text);
      // vpm treats it as sql it can't parse
      assertFalse(XUtil.isParsedSQL(sql), text);
   }

   // a query saved by 1.1.0 that has a data source (or none) when it's loaded
   private static UniformSQL saved(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parseXML(element(savedXml(text, "none")));
      return sql;
   }

   // a query of a data source, loaded with the saved sql
   private static JDBCQuery loadQuery(String text, JDBCDataSource ds) throws Exception {
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.parseXML(element("<query_jdbc>" + savedXml(text, "none") + "</query_jdbc>"));
      return query;
   }

   private static String savedXml(String text, String type) throws IOException {
      try(InputStream in = UniformSQLLegacyJoinRefusalTest.class.getResourceAsStream(
         "UniformSQLLegacyJoinRefusalTest-1.1.0.txt");
          BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
      {
         String line;

         while((line = reader.readLine()) != null) {
            String[] parts = line.split("\\|", 3);

            if(!line.startsWith("#") && parts.length == 3 && parts[0].equals(type) &&
               parts[1].equals(text))
            {
               return parts[2];
            }
         }
      }

      throw new IllegalArgumentException("no saved xml: " + type + " " + text);
   }

   private static Element element(String xml) throws Exception {
      return DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
   }

   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   @BeforeAll
   static void createTables() throws SQLException {
      try(Connection conn = DriverManager.getConnection(DB + ";create=true");
          Statement stmt = conn.createStatement())
      {
         for(String table : TABLES) {
            stmt.executeUpdate("create table " + table + " (id int, k int)");
         }
      }
   }

   @AfterAll
   static void dropDatabase() {
      try {
         DriverManager.getConnection(DB + ";drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   /**
    * Run both queries on random data with nulls, and return the first dataset whose rows
    * differ, null if none does.
    */
   private static String rowMismatch(String text, String generated) throws SQLException {
      Random random = new Random(77548);

      try(Connection conn = DriverManager.getConnection(DB)) {
         for(int i = 0; i < 100; i++) {
            fillTables(conn, random);
            List<String> rows1 = rows(conn, text);
            List<String> rows2 = rows(conn, generated);

            if(!rows1.equals(rows2)) {
               return "dataset " + i + ": " + rows1 + " vs " + rows2 + "\n" + generated;
            }
         }
      }

      return null;
   }

   private static void fillTables(Connection conn, Random random) throws SQLException {
      try(Statement stmt = conn.createStatement()) {
         for(String table : TABLES) {
            stmt.executeUpdate("delete from " + table);
         }
      }

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
