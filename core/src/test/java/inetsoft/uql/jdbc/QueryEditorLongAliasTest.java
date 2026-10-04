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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.ColumnCache;
import inetsoft.uql.util.Config;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.composer.model.ws.AddColumnInfoResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77711. The query editor stored the result of getValidAlias as a column's alias. For
 * a name the database can't take as an alias (over 28 bytes on Oracle, PostgreSQL, DB2 and
 * Sybase, or with limit.alias.length=true), that is a per-generation ALIAS_n. ALIAS_n is
 * itself a valid alias, so it was generated as is and never mapped back: the field pane, the
 * saved query and the result header all showed ALIAS_0. The editor now stores the name, and
 * the sql generation substitutes ALIAS_n each time, as it already does for a long alias in
 * typed sql.
 *
 * The HAVING single-column subquery wrapper selected the stored alias of the subquery, which
 * only matched the generated subquery while the stored alias was the ALIAS_n. It now selects
 * the alias the subquery was generated with, which also fixes a parsed subquery with a long
 * alias.
 *
 * No case has two columns of the same long name, which still get the same ALIAS_n
 * (Bug #77713).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, QueryEditorLongAliasTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class QueryEditorLongAliasTest {
   // 35 bytes, over the 28 of isValidAlias
   private static final String LONG = "CUSTOMER_ACCOUNT_OPENING_DATE_LOCAL";

   @AfterEach
   void resetLimit() {
      SreeEnv.setProperty("limit.alias.length", null);
   }

   @ParameterizedTest
   @ValueSource(strings = { "oracle", "postgresql", "db2", "sybase" })
   void createSqlStoresTheName(String product) throws Exception {
      JDBCDataSource ds = dataSource(product);
      UniformSQL sql = createSQL(ds, "EMP.ID", "EMP." + LONG);
      assertFalse(SQLHelper.getSQLHelper(sql).isValidAlias(LONG), product);

      JDBCSelection selection = (JDBCSelection) sql.getSelection();
      assertEquals(LONG, selection.getAlias(1), product);

      // the generated sql still uses an alias the database takes
      String text = generate(sql);
      assertTrue(text.contains("ALIAS_0"), text);
      assertFalse(text.contains(" as " + LONG) || text.contains(" as \"" + LONG), text);

      // the name is what is saved, and it generates the same sql when loaded
      UniformSQL loaded = reload(sql);
      assertEquals(LONG, loaded.getSelection().getAlias(1), product);
      assertEquals(text, generate(loaded), product);
   }

   @ParameterizedTest
   @ValueSource(strings = { "oracle", "postgresql", "db2", "sybase" })
   void addColumnsStoresTheName(String product) throws Exception {
      JDBCDataSource ds = dataSource(product);
      UniformSQL sql = parse("select EMP.ID from EMP", ds);
      QueryManagerService service = service(sql, ds);

      AddColumnInfoResult result = service.addColumns(RID, List.of(column(LONG)));
      JDBCSelection selection = (JDBCSelection) sql.getSelection();
      assertEquals(LONG, selection.getAlias(1), product);
      // the column the client selects after the add is a column of the query
      assertEquals(Map.of(LONG, "EMP." + LONG), result.getColumnMap(), product);

      String text = generate(sql);
      assertTrue(text.contains("ALIAS_0"), text);

      // a later add of the same column sees the stored name and makes its own one
      result = service.addColumns(RID, List.of(column(LONG)));
      assertEquals(LONG + "_1", selection.getAlias(2), product);
      assertEquals(Map.of(LONG + "_1", "EMP." + LONG), result.getColumnMap(), product);
   }

   @ParameterizedTest
   @ValueSource(strings = { "oracle", "postgresql", "db2", "sybase" })
   void addExpressionStoresTheName(String product) throws Exception {
      JDBCDataSource ds = dataSource(product);
      UniformSQL sql = parse("select EMP.ID from EMP", ds);
      QueryManagerService service = service(sql, ds);

      String[] result = service.addExpression(sql, (JDBCSelection) sql.getSelection(),
                                              "EMP." + LONG, ds, null);

      assertEquals(LONG, result[0], product);
      assertEquals(LONG, sql.getSelection().getAlias(1), product);
      String text = generate(sql);
      assertTrue(text.contains("ALIAS_0"), text);
   }

   /**
    * The result header is the name, also after a save and load. Run on Derby with the H2
    * helper and limit.alias.length=true, which rejects the name as Oracle does.
    */
   @Test
   void headerIsTheName() throws Exception {
      SreeEnv.setProperty("limit.alias.length", "true");
      JDBCDataSource ds = dataSource("h2");

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77711a;create=true");
          Statement stmt = conn.createStatement())
      {
         stmt.execute("create table EMP (ID int, " + LONG + " int)");
         stmt.execute("insert into EMP values (1, 2)");

         UniformSQL created = createSQL(ds, "EMP.ID", "EMP." + LONG);
         assertFalse(SQLHelper.getSQLHelper(created).isValidAlias(LONG));
         assertEquals(List.of("ID", LONG), header(conn, created));
         assertEquals(List.of("ID", LONG), header(conn, reload(created)));

         UniformSQL added = parse("select EMP.ID from EMP", ds);
         service(added, ds).addColumns(RID, List.of(column(LONG)));
         assertEquals(List.of("EMP.ID", LONG), header(conn, added));
         assertEquals(List.of("EMP.ID", LONG), header(conn, reload(added)));
      }
      finally {
         drop("bug77711a");
      }
   }

   /**
    * A HAVING condition compared with a single-column subquery made in the editor (the
    * subquery value of the SQL query dialog, JDBCUtil.createXExpression) selects the
    * subquery's column by the alias the subquery is generated with.
    */
   @Test
   void havingSubqueryOfALongName() throws Exception {
      SreeEnv.setProperty("limit.alias.length", "true");
      JDBCDataSource ds = dataSource("h2");

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77711b;create=true");
          Statement stmt = conn.createStatement())
      {
         createTable(stmt);

         UniformSQL sub = createSQL(ds, "EMP." + LONG);
         assertEquals(LONG, sub.getSelection().getAlias(0));
         String text = generate(havingIn(ds, sub));
         assertTrue(text.contains("select ALIAS_0 from"), text);
         assertEquals(List.of("10"), rows(stmt, text));
      }
      finally {
         drop("bug77711b");
      }
   }

   /**
    * A typed subquery that is parsed is generated, with an ALIAS_n for its long alias, so
    * the wrapper selects the ALIAS_n. It used to select the long alias, which the generated
    * subquery doesn't have.
    */
   @Test
   void havingParsedSubqueryOfALongAlias() throws Exception {
      SreeEnv.setProperty("limit.alias.length", "true");
      JDBCDataSource ds = dataSource("h2");

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77711c;create=true");
          Statement stmt = conn.createStatement())
      {
         createTable(stmt);

         UniformSQL sub = parse("select EMP." + LONG + " as " + LONG + " from EMP", ds);
         assertFalse(sub.hasSQLString());
         assertEquals(LONG, sub.getSelection().getAlias(0));

         String text = generate(havingIn(ds, sub));
         assertTrue(text.contains("select ALIAS_0 from"), text);
         assertEquals(List.of("10"), rows(stmt, text));
      }
      finally {
         drop("bug77711c");
      }
   }

   /**
    * A subquery whose text is kept (e.g. sql the parser can't represent) is run as it is
    * written, so the wrapper keeps selecting its stored alias.
    */
   @Test
   void havingKeptSubqueryTextKeepsItsAlias() throws Exception {
      SreeEnv.setProperty("limit.alias.length", "true");
      JDBCDataSource ds = dataSource("h2");

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77711e;create=true");
          Statement stmt = conn.createStatement())
      {
         createTable(stmt);

         String typed = "select EMP." + LONG + " as " + LONG + " from EMP";
         UniformSQL sub = parse(typed, ds);
         sub.setSQLString(typed, false);
         assertTrue(sub.hasSQLString());
         assertEquals(LONG, sub.getSelection().getAlias(0));

         String text = generate(havingIn(ds, sub));
         assertTrue(text.contains("select " + LONG + " from"), text);
         assertTrue(text.contains(typed), text);
         assertEquals(List.of("10"), rows(stmt, text));
      }
      finally {
         drop("bug77711e");
      }
   }

   /**
    * A stored ALIAS_n (a query saved before the fix) and a short name are selected as
    * before.
    */
   @Test
   void havingSubqueryOfAStoredOrShortAlias() throws Exception {
      SreeEnv.setProperty("limit.alias.length", "true");
      JDBCDataSource ds = dataSource("h2");

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77711d;create=true");
          Statement stmt = conn.createStatement())
      {
         createTable(stmt);

         UniformSQL saved = createSQL(ds, "EMP." + LONG);
         saved.getSelection().setAlias(0, "ALIAS_0");
         String text = generate(havingIn(ds, saved));
         assertTrue(text.contains("select ALIAS_0 from"), text);
         assertEquals(List.of("10"), rows(stmt, text));

         UniformSQL shortName = createSQL(ds, "EMP.ID");
         text = generate(havingIn(ds, shortName));
         assertTrue(text.contains("select ID from"), text);
         // max(ID) of both departments (2 and 9) is an ID
         assertEquals(List.of("10", "20"), rows(stmt, text));
      }
      finally {
         drop("bug77711d");
      }
   }

   // DEPT 10 has max(ID) 2, which is a value of the subquery column; DEPT 20 has 9, which isn't
   private static void createTable(Statement stmt) throws SQLException {
      stmt.execute("create table EMP (ID int, DEPT int, " + LONG + " int)");
      stmt.execute("insert into EMP values (1, 10, 2), (2, 10, 1), (9, 20, 3)");
   }

   // select EMP.DEPT from EMP group by EMP.DEPT having max(EMP.ID) in (<sub>)
   private static UniformSQL havingIn(JDBCDataSource ds, UniformSQL sub) throws Exception {
      UniformSQL sql = parse("select EMP.DEPT from EMP group by EMP.DEPT", ds);
      sql.setHaving(new XBinaryCondition(new XExpression("max(EMP.ID)", XExpression.EXPRESSION),
                                         new XExpression(sub, XExpression.SUBQUERY), "in"));
      return sql;
   }

   private static UniformSQL createSQL(JDBCDataSource ds, String... columns) throws Exception {
      AssetEntry table = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.PHYSICAL_TABLE,
                                        "EMP", null);
      table.setProperty("source", "EMP");
      Map<String, AssetEntry> tables = new LinkedHashMap<>();
      // not keyed by the table name, so the columns' types are not looked up
      tables.put("not-a-table", table);
      return JDBCUtil.createSQL(ds, tables, columns, new XJoin[0], new ArrayList<>(), null);
   }

   private static QueryManagerService service(UniformSQL sql, JDBCDataSource ds) throws Exception {
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      RuntimeQueryService rqs = mock(RuntimeQueryService.class);
      when(rqs.getRuntimeQuery(RID))
         .thenReturn(new RuntimeQueryService.RuntimeXQuery(query, RID, ds.getFullName()));
      return new QueryManagerService(rqs, repository(), mock(DataSourceService.class),
                                     mock(SecurityEngine.class), mock(ColumnCache.class));
   }

   // a column dragged from the tree into the field pane
   private static AssetEntry column(String name) {
      AssetEntry col = new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.PHYSICAL_COLUMN,
                                      "EMP/" + name, null);
      col.setProperty("attribute", name);
      col.setProperty(QueryManagerService.SOURCE_ALIAS, "EMP");
      return col;
   }

   // the result header, as JDBCTableNode names the columns
   private static List<String> header(Connection conn, UniformSQL sql) throws SQLException {
      String text = generate(sql);

      try(Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(text)) {
         return Arrays.asList(SQLSelection.getColumnNames(sql.getSelection(), rs.getMetaData(),
                                                          sql.getDataSource().getDriver(), conn, null));
      }
   }

   private static List<String> rows(Statement stmt, String query) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(ResultSet rs = stmt.executeQuery(query)) {
         while(rs.next()) {
            rows.add(String.valueOf(rs.getObject(1)));
         }
      }

      return rows;
   }

   private static void drop(String db) {
      try {
         DriverManager.getConnection("jdbc:derby:memory:" + db + ";drop=true");
      }
      catch(SQLException ignore) {
         // a successful drop is reported as an exception
      }
   }

   private static String generate(UniformSQL sql) {
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      return sql;
   }

   private static UniformSQL reload(UniformSQL sql) throws Exception {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(buffer.toString())).getDocumentElement());
      loaded.setDataSource(sql.getDataSource());
      return loaded;
   }

   private static JDBCDataSource dataSource(String product) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77711" + product + RUN);
      ds.setDriver("h2".equals(product) ? "org.h2.Driver" : "x." + product + ".Driver");
      ds.setURL("h2".equals(product) ? "jdbc:h2:mem:x" : "jdbc:" + product + ":x");
      ds.setRuntimeProductName(product);
      // otherwise some helpers ask the repository for it
      ds.setProductVersion("10.0");
      return ds;
   }

   // the metadata of EMP, for JDBCUtil.fixUniformSQLInfo
   private static XRepository repository() throws Exception {
      XRepository repository = mock(XRepository.class);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);

         if("DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            return new XNode("properties");
         }

         XTypeNode result = new XTypeNode("Result");

         for(String column : new String[] { "ID", "DEPT", LONG }) {
            result.addChild(XSchema.createPrimitiveType(column, Integer.class));
         }

         XTypeNode meta = new XTypeNode("meta");
         meta.addChild(result);
         return meta;
      });

      return repository;
   }

   // a JDBCDataSource creates its credential when it is constructed, and
   // JDBCUtil.fixUniformSQLInfo looks up the driver type
   @Configuration
   static class Beans {
      @Bean
      Config config() {
         Config config = mock(Config.class);
         when(config.getJDBCType(any())).thenReturn("H2");
         return config;
      }

      @Bean
      CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         when(service.createCredential(any(), anyBoolean())).thenAnswer(inv -> new LocalPasswordCredential());
         return service;
      }
   }

   private static final String RID = "rq-77711";
   private static final long RUN = System.nanoTime();
}
