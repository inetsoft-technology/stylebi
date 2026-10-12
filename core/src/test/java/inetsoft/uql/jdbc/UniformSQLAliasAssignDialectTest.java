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
import inetsoft.util.Tool;
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

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77785, {@code select a = b} is the T-SQL alias form ({@code select b as a}) only on
 * SQL Server and Sybase. Every other database reads it as a boolean comparison, but the parser
 * stored it as an alias on every dialect, so the regenerated {@code select b as a} (or
 * {@code select 1 as flag} for {@code select flag = 1}) silently returned other rows. On other
 * dialects, and with no data source, the parse must fail so the original sql runs.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  UniformSQLAliasAssignDialectTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLAliasAssignDialectTest {
   // a data source with a real driver and URL needs the credential service, and the JDBC
   // driver types of Config to pick its SQL helper
   @Configuration
   @Import(CredentialService.class)
   static class Config {
      @Bean
      inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      // the derby and mysql helpers ask the repository for the product version
      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }
   }

   private static final String DERBY_URL = "jdbc:derby:memory:bug77785";
   private static final String HSQLDB_URL = "jdbc:hsqldb:mem:bug77785";

   private static final String[] COMPARISON_DIALECTS =
      { "postgresql", "h2", "derby", "derby-ansi", "mysql", "oracle", "access" };
   private static final String[] TSQL_DIALECTS = { "sql server", "sybase" };

   // the alias form at every query level. a comparison on every database but SQL Server and
   // Sybase, and each is sql Derby and HSQLDB run
   private static final String[] TOP_LEVEL = {
      "select flag = 1 from t",
      "select a = b from t",
      "select id, flag = 1 from t",
      "select c = count(*) from t",
      "select d.x from (select x = a from t) d",
      "select id, (select max(f) from (select f = a from t) s) from t",
   };

   // a subquery in the where clause. The statement fails, and the SELECT_FROM fallback
   // keeps a partial parse (PARSE_PARTIALLY), which is not a parsed sql either
   private static final String[] WHERE_SUBQUERY = {
      "select id from t where 1 in (select flag = 1 from t)",
      "select id from t where exists (select x = a from t)",
   };

   static Stream<Arguments> refused() {
      List<Arguments> list = new ArrayList<>();

      for(String type : COMPARISON_DIALECTS) {
         for(String text : TOP_LEVEL) {
            list.add(Arguments.of(type, text, true));
         }

         for(String text : WHERE_SUBQUERY) {
            list.add(Arguments.of(type, text, false));
         }
      }

      return list.stream();
   }

   @ParameterizedTest
   @MethodSource("refused")
   void aliasFormFailsOnComparisonDialects(String type, String text, boolean topLevel)
      throws Exception
   {
      JDBCDataSource ds = dataSource(type);

      // the statement parse (UniformSQL.parse) throws
      UniformSQL direct = new UniformSQL();
      direct.setDataSource(ds);
      assertThrows(Exception.class,
                   () -> direct.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD),
                   type + ": " + text);

      // the production parse, with the data source set first as JDBCQuery does
      UniformSQL sql = process(text, ds);

      if(topLevel) {
         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), type + ": " + text);
      }
      else {
         assertNotEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type + ": " + text);
      }

      assertFalse(XUtil.isParsedSQL(sql), type + ": " + text);
      assertEquals(text, sql.getSQLString());

      UniformSQL fresh = new UniformSQL();
      fresh.setDataSource(ds);
      fresh.setSQLString(text, false);
      assertTrue(fresh.isLossy(), type + ": " + text);

      assertFalse(XUtil.isQueryMergeable(query(text, ds)), type + ": " + text);
   }

   // the dialect is unknown without a data source, so the form is refused too
   @ParameterizedTest
   @ValueSource(strings = { "select flag = 1 from t", "select id, a = b from t",
                            "select d.x from (select x = a from t) d" })
   void aliasFormFailsWithoutDataSource(String text) {
      UniformSQL sql = new UniformSQL();
      assertThrows(Exception.class,
                   () -> sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD), text);

      UniformSQL processed = new UniformSQL();
      new SQLProcessor(processed).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, processed.getParseResult(), text);
      assertEquals(text, processed.getSQLString());
   }

   static Stream<Arguments> accepted() {
      return Stream.of(TSQL_DIALECTS).flatMap(type -> Stream.of(
         Arguments.of(type, "select flag = 1 from t", "select 1 as flag from t"),
         Arguments.of(type, "select a = b from t", "select b as a from t"),
         Arguments.of(type, "select id, flag = 1 from t", "select 1 as flag, id from t"),
         Arguments.of(type, "select c = count(*) from t", "select count(*) as c from t"),
         // inside subqueries, whose own UniformSQL has no data source at parse time
         Arguments.of(type, "select d.x from (select x = a from t) d",
                      "( select a as x from t) d"),
         Arguments.of(type, "select id from t where 1 in (select flag = 1 from t)",
                      "IN ( select 1 as flag from t)"),
         Arguments.of(type, "select id from t where exists (select x = a from t)",
                      "EXISTS ( select a as x from t)"),
         Arguments.of(type, "select id, (select max(f) from (select f = a from t) s) from t",
                      "( select a as f from t) s")));
   }

   // the T-SQL alias form still parses, at every query level, and regenerates to itself
   @ParameterizedTest
   @MethodSource("accepted")
   void aliasFormParsesOnTsqlDialects(String type, String text, String expected) throws Exception {
      JDBCDataSource ds = dataSource(type);
      UniformSQL sql = parse(text, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type + ": " + text);
      assertFalse(sql.isLossy(), text);

      UniformSQL processed = process(text, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, processed.getParseResult(), type + ": " + text);
      assertTrue(XUtil.isParsedSQL(processed), text);

      sql.clearSQLString();
      String generated = normalize(sql.getSQLString());
      assertTrue(generated.contains(expected), type + ": " + text + " -> " + generated);

      // round trip
      UniformSQL reparsed = parse(generated, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, reparsed.getParseResult(), generated);
      reparsed.clearSQLString();
      assertEquals(generated, normalize(reparsed.getSQLString()), type + " round trip");
   }

   // the helper type is compared as a lowercase literal, without a default-locale fold
   @Test
   void dialectCheckIgnoresDefaultLocale() throws Exception {
      Locale old = Locale.getDefault();

      try {
         Locale.setDefault(new Locale("tr", "TR"));

         for(String type : TSQL_DIALECTS) {
            UniformSQL sql = parse("select id, flag = 1 from t", dataSource(type));
            assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type);
         }

         for(String type : new String[] { "postgresql", "h2", "derby" }) {
            UniformSQL sql = process("select id, flag = 1 from t", dataSource(type));
            assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), type);
         }
      }
      finally {
         Locale.setDefault(old);
      }
   }

   // the validity-only callers parse an expression without building a UniformSQL, so they
   // never regenerate it, and keep accepting the form in a scalar subquery
   @Test
   void expressionValidityIsUnchanged() {
      assertTrue(XUtil.isSQLExpressionValid("(select flag = 1 from t)"));
      assertTrue(XUtil.isSQLExpressionValid("(select top 1 x = a from t)"));
      // a comparison in the select list was never valid
      assertFalse(XUtil.isSQLExpressionValid("(select flag <> 1 from t)"));
   }

   // a comparison inside an expression, and the aliased form, are unaffected
   @ParameterizedTest
   @ValueSource(strings = { "postgresql", "h2", "derby", "sql server", "sybase" })
   void otherShapesAreUnchanged(String type) throws Exception {
      JDBCDataSource ds = dataSource(type);

      for(String text : new String[] {
         "select case when flag = 1 then 1 else 0 end from t",
         "select id from t where flag = 1",
         "select a as x from t" })
      {
         UniformSQL sql = parse(text, ds);
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type + ": " + text);
         assertFalse(sql.isLossy(), text);
      }
   }

   // a select list alias the editor builds is written as expr as alias, which still parses
   @ParameterizedTest
   @ValueSource(strings = { "postgresql", "h2", "derby", "oracle", "sql server", "sybase" })
   void generatedAliasReparses(String type) throws Exception {
      JDBCDataSource ds = dataSource(type);
      UniformSQL sql = new UniformSQL();
      sql.addTable("t");
      sql.getSelection().addColumn("t.a");
      sql.getSelection().setAlias(0, "x");
      sql.setDataSource(ds);
      sql.clearSQLString();
      String generated = normalize(sql.getSQLString());

      UniformSQL reparsed = parse(generated, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, reparsed.getParseResult(), type + ": " + generated);
      assertFalse(reparsed.isLossy(), generated);
      assertEquals(1, reparsed.getSelection().getColumnCount(), generated);
   }

   // the reported damage: the original returns the comparison, the old regeneration returned
   // the constant. With the fix the data cache path sends the original sql unchanged
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select id, flag = 1 from t|select 1 as flag, id from t",
      "select a = b from t|select b as a from t",
   })
   void originalSqlIsSentAndKeepsItsRows(String text, String oldRegenerated) throws Exception {
      for(String type : new String[] { "derby", "derby-ansi" }) {
         JDBCDataSource ds = dataSource(type);
         JDBCQuery query = query(text, ds);
         UniformSQL sql = (UniformSQL) query.getSQLDefinition();
         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);
         assertFalse(XUtil.isQueryMergeable(query), text);

         JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query);
         assertFalse(normalizer.isClearedSqlString(), text);
         assertEquals(text, normalize(query.getSQLAsString()));
      }

      for(String url : new String[] { DERBY_URL, HSQLDB_URL }) {
         try(Connection conn = connect(url); Statement stmt = conn.createStatement()) {
            List<String> original = rows(stmt, text);
            // the comparison is null, true or false, not the constant or the other column
            assertNotEquals(original, rows(stmt, oldRegenerated), url + ": " + text);
            assertTrue(original.toString().contains("true"), url + ": " + original);
            assertTrue(original.toString().contains("false"), url + ": " + original);
         }
      }
   }

   // a query saved before the fix holds the old structure (column 1 as flag) with
   // parseResult SUCCESS. lossy is re-derived with the current grammar on load (#77477), so
   // the worksheet merge and the cache normalizer keep its sql string at once
   @ParameterizedTest
   @ValueSource(strings = { "derby", "postgresql" })
   void savedOldParseLoadsLossy(String type) throws Exception {
      String text = "select flag = 1 from t";
      JDBCDataSource ds = dataSource(type);
      // the structure the old parse built, saved with the text, parseResult SUCCESS and lossy
      UniformSQL old = parse("select 1 as flag from t", ds);
      old.setSQLString(text, false);
      old.setParseResult(UniformSQL.PARSE_SUCCESS);
      old.setLossy(false);
      String xml = toXML(old);
      assertTrue(xml.contains("<sqlstring parseResult=\"0\"><![CDATA[" + text + "]]>"), xml);
      assertTrue(xml.contains(" lossy=\"false\""), xml);

      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      assertEquals(UniformSQL.PARSE_SUCCESS, loaded.getParseResult());
      assertEquals("1", loaded.getSelection().getColumn(0));
      assertEquals("flag", loaded.getSelection().getAlias(0));

      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.setSQLDefinition(loaded);
      assertTrue(loaded.isLossy(), type);
      assertFalse(XUtil.isQueryMergeable(query), type);

      JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query);
      assertFalse(normalizer.isClearedSqlString(), type);
      assertEquals(text, normalize(query.getSQLAsString()));
   }

   @AfterAll
   static void dropDatabases() throws SQLException {
      try(Connection conn = DriverManager.getConnection(HSQLDB_URL);
          Statement stmt = conn.createStatement())
      {
         stmt.execute("shutdown");
      }

      try {
         DriverManager.getConnection(DERBY_URL + ";drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   // t = (1,1,5,5), (2,2,5,7), (3,null,null,7)
   private static Connection connect(String url) throws SQLException {
      Connection conn = DriverManager.getConnection(url + (url.equals(DERBY_URL) ? ";create=true" : ""));

      try(Statement stmt = conn.createStatement()) {
         try {
            stmt.execute("drop table t");
         }
         catch(SQLException ignore) {
            // not created yet
         }

         stmt.execute("create table t (id int, flag int, a int, b int)");
         stmt.execute("insert into t values (1, 1, 5, 5), (2, 2, 5, 7), (3, null, null, 7)");
      }

      return conn;
   }

   // each row as a map from the column label to its value, the rows as a multiset
   private static List<String> rows(Statement stmt, String query) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(ResultSet rs = stmt.executeQuery(query)) {
         ResultSetMetaData meta = rs.getMetaData();

         while(rs.next()) {
            List<String> row = new ArrayList<>();

            for(int i = 1; i <= meta.getColumnCount(); i++) {
               row.add(String.valueOf(rs.getObject(i)));
            }

            Collections.sort(row);
            rows.add(row.toString());
         }
      }

      Collections.sort(rows);
      return rows;
   }

   // the production parse: JDBCQuery sets the data source before the sql string is parsed
   private static JDBCQuery query(String text, JDBCDataSource ds) {
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.setSQLDefinition(process(text, ds));
      return query;
   }

   private static UniformSQL process(String text, JDBCDataSource ds) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      new SQLProcessor(sql).parse(text);
      return sql;
   }

   private static JDBCDataSource dataSource(String type) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77785_" + type);
      ds.setProductVersion("19.0");

      switch(type.replace("-ansi", "")) {
      case "derby" -> {
         ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
         ds.setURL(DERBY_URL);
         ds.setProductVersion("10.17");
      }
      case "h2" -> {
         ds.setDriver("org.h2.Driver");
         ds.setURL("jdbc:h2:mem:test");
      }
      case "postgresql" -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/test");
      }
      case "mysql" -> {
         ds.setDriver("com.mysql.cj.jdbc.Driver");
         ds.setURL("jdbc:mysql://localhost/test");
         ds.setProductVersion("8.0");
      }
      case "oracle" -> {
         ds.setDriver("oracle.jdbc.OracleDriver");
         ds.setURL("jdbc:oracle:thin:@localhost:1521:test");
      }
      case "access" -> {
         ds.setDriver("net.ucanaccess.jdbc.UcanaccessDriver");
         ds.setURL("jdbc:ucanaccess://test.accdb");
      }
      case "sql server" -> {
         ds.setDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver");
         ds.setURL("jdbc:sqlserver://localhost:1433;databaseName=test");
      }
      case "sybase" -> {
         ds.setDriver("net.sourceforge.jtds.jdbc.Driver");
         ds.setURL("jdbc:jtds:sybase://localhost/test");
      }
      default -> throw new IllegalArgumentException(type);
      }

      ds.setAnsiJoin(type.endsWith("-ansi"));
      String helper = SQLHelper.getSQLHelper(ds).getSQLHelperType();
      assertEquals(type.replace("-ansi", ""), helper, "helper for " + type);
      return ds;
   }

   private static String toXML(UniformSQL sql) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      return buffer.toString();
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
