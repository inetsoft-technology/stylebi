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

import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.ColumnCache;
import inetsoft.uql.util.Config;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.portal.model.database.QueryTableModel;
import inetsoft.web.portal.model.database.events.EditQueryTableEvent;
import inetsoft.web.portal.model.database.events.RemoveGraphTableEvent;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77648. On postgresql, snowflake and exasol the parser stores the alias of an
 * unaliased table with in-band quotes ("q"), while UniformSQL.syncTableAlias resolved the
 * qualifier of each column with the quotes stripped (q). Renaming the table in the query
 * graph then dropped every select column, the WHERE and HAVING conditions, GROUP BY and
 * ORDER BY of that table. Removing another table dropped the conditions of the table that
 * was kept when there is no table metadata.
 *
 * The rename and remove go through QueryGraphModelService with a real QueryManagerService,
 * as the query graph does. h2, oracle and an explicitly aliased table are the controls.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, UniformSQLUnaliasedTableRenameTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLUnaliasedTableRenameTest {
   private static final String[] QUOTING = { "postgresql", "snowflake", "exasol" };
   private static final String[] CONTROLS = { "h2", "oracle" };

   @Test
   void renameKeepsSelectColumns() throws Exception {
      for(String helper : QUOTING) {
         for(String query : new String[] {
            "select q.\"MixedCase\", q.k from q",
            "select \"q\".\"MixedCase\", \"q\".k from \"q\"",
            "select q.k from q" })
         {
            String generated = rename(helper, query, "q", "x");
            assertFalse(generated.isEmpty(), helper + " " + query);
            assertTrue(generated.startsWith("select "), helper + " " + query + " -> " + generated);
            assertTrue(generated.endsWith("from \"q\" x"), helper + " " + query + " -> " + generated);
            assertTrue(generated.contains("x.\"k\""), helper + " " + query + " -> " + generated);
            assertEquals(query.contains("MixedCase"), generated.contains("x.\"MixedCase\""),
                         helper + " " + query + " -> " + generated);
            assertNoOldQualifier(helper, query, generated);
         }
      }
   }

   @Test
   void renameKeepsGroupByAndOrderBy() throws Exception {
      String query = "select q.\"MixedCase\" as a, count(*) from q group by q.\"MixedCase\" order by a";

      for(String helper : QUOTING) {
         String generated = rename(helper, query, "q", "x");
         // snowflake doesn't quote the alias, and sorts by its column
         assertTrue(generated.contains("x.\"MixedCase\" as "), helper + " -> " + generated);
         assertTrue(generated.contains("group by x.\"MixedCase\""), helper + " -> " + generated);
         assertTrue(generated.contains("order by "), helper + " -> " + generated);
         assertNoOldQualifier(helper, query, generated);
      }

      query = "select k, count(*) from q group by q.k order by q.k";

      for(String helper : QUOTING) {
         String generated = rename(helper, query, "q", "x");
         assertTrue(generated.contains("group by x.\"k\""), helper + " -> " + generated);
         assertTrue(generated.contains("order by x.\"k\""), helper + " -> " + generated);
         assertNoOldQualifier(helper, query, generated);
      }
   }

   @Test
   void renameKeepsQuotedAndUnquotedTwins() throws Exception {
      String query = "select q.\"MixedCase\", q.MixedCase as b from q";

      // both spellings of the name are refused where they are two names (Bug #77643), the
      // sql runs as written and isn't renamed
      for(String helper : QUOTING) {
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(dataSource(helper));
         assertThrows(antlr.SemanticException.class,
                      () -> sql.parse(query, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD), helper);
      }

      for(String helper : CONTROLS) {
         String generated = rename(helper, query, "q", "x");
         // both columns are kept under the new name
         assertTrue(generated.contains("x.\"MixedCase\""), helper + " -> " + generated);
         assertTrue(generated.matches(".* as \"?b\"? from .*"), helper + " -> " + generated);
         assertEquals(2, generated.split(" x\\.").length - 1, helper + " -> " + generated);
         assertNoOldQualifier(helper, query, generated);
      }
   }

   @Test
   void renameKeepsWhereConditions() throws Exception {
      String query = "select k from q where q.k > 1 and q.\"MixedCase\" < 5";

      for(String helper : QUOTING) {
         String generated = rename(helper, query, "q", "x");
         assertTrue(generated.contains(" where "), helper + " -> " + generated);
         assertTrue(generated.contains("x.\"k\" > 1"), helper + " -> " + generated);
         assertTrue(generated.contains("x.\"MixedCase\" < 5"), helper + " -> " + generated);
         assertNoOldQualifier(helper, query, generated);
      }

      query = "select q.k, u.k from q, u where q.k = u.k and q.k > 1";

      for(String helper : QUOTING) {
         String generated = rename(helper, query, "q", "x");
         assertTrue(generated.contains("x.\"k\" = \"u\".\"k\""), helper + " -> " + generated);
         assertTrue(generated.contains("x.\"k\" > 1"), helper + " -> " + generated);
         assertEquals(2, generated.substring(0, generated.indexOf(" from ")).split(",").length,
                      helper + " -> " + generated);
         assertNoOldQualifier(helper, query, generated);
      }
   }

   @Test
   void renameKeepsHavingOrAndSchemaQualifiedTable() throws Exception {
      for(String helper : QUOTING) {
         String query = "select q.k, count(*) from q group by q.k having q.k > 1";
         String generated = rename(helper, query, "q", "x");
         assertTrue(generated.contains("group by x.\"k\" having x.\"k\" > 1"), helper + " -> " + generated);
         assertNoOldQualifier(helper, query, generated);

         query = "select k from q where q.k > 1 or q.\"MixedCase\" < 5";
         generated = rename(helper, query, "q", "x");
         assertTrue(generated.endsWith(" where x.\"k\" > 1 or x.\"MixedCase\" < 5"), helper + " -> " + generated);
         assertNoOldQualifier(helper, query, generated);

         // the alias of a schema qualified table is stored as "public"."q"
         query = "select public.q.k from public.q where public.q.k > 1";
         assertEquals("select x.\"k\" from \"public\".\"q\" x where x.\"k\" > 1",
                      rename(helper, query, "public.q", "x"), helper);
      }
   }

   /**
    * The rows of the renamed query are the rows of the original one. Derby folds unquoted
    * names to upper case, so the tables are created with the lower case names postgresql
    * folds to, and the original query is written as postgresql reads it.
    */
   @Test
   void renamedWhereReturnsTheSameRows() throws Exception {
      String url = "jdbc:derby:memory:bug77648;create=true";

      try(Connection conn = DriverManager.getConnection(url);
          Statement stmt = conn.createStatement())
      {
         stmt.execute("create table \"q\" (\"k\" int, \"MixedCase\" int)");
         stmt.execute("insert into \"q\" values (1, 1), (2, 2), (3, 9), (4, 4), (5, 7)");

         String original = "select \"k\" from \"q\" where \"q\".\"k\" > 1 and \"q\".\"MixedCase\" < 5";
         List<String> expected = rows(stmt, original);
         assertEquals(List.of("2", "4"), expected);

         for(String helper : QUOTING) {
            String generated = rename(helper, "select k from q where q.k > 1 and q.\"MixedCase\" < 5",
                                      "q", "x");
            assertEquals(expected, rows(stmt, generated), helper + " -> " + generated);
         }
      }
      finally {
         try {
            DriverManager.getConnection("jdbc:derby:memory:bug77648;drop=true");
         }
         catch(SQLException ignore) {
            // a dropped in-memory database reports an exception
         }
      }
   }

   @Test
   void controlsAreUnchanged() throws Exception {
      for(String helper : CONTROLS) {
         assertEquals("select x.\"MixedCase\", x.k from " + table(helper) + " x",
                      rename(helper, "select q.\"MixedCase\", q.k from q", "q", "x"), helper);
         assertEquals("select x.k from " + table(helper) + " x where x.k > 1 and x.\"MixedCase\" < 5",
                      rename(helper, "select q.k from q where q.k > 1 and q.\"MixedCase\" < 5", "q", "x"),
                      helper);
      }

      // an explicit alias is stored without quotes on every helper
      for(String helper : QUOTING) {
         assertEquals("select x.\"MixedCase\", x.\"k\" from \"q\" x",
                      rename(helper, "select x1.\"MixedCase\", x1.k from q x1", "x1", "x"), helper);
      }
   }

   /**
    * Without table metadata the fields have no table, and the in-band quoted ORDER BY
    * and GROUP BY items of the renamed table keep their column quoting, so they still
    * match the select column x."k" and aren't dropped.
    */
   @Test
   void renameWithoutMetadataKeepsGroupByAndOrderBy() throws Exception {
      for(String helper : QUOTING) {
         // an unqualified name, resolved to the select column
         assertEquals("select x.\"k\" from \"q\" x order by x.\"k\" asc",
                      rename(helper, "select q.k from q order by k", "q", "x", null), helper);
         assertEquals("select x.\"k\" from \"q\" x group by x.\"k\" order by x.\"k\" asc",
                      rename(helper, "select q.k from q group by k order by k", "q", "x", null), helper);
         assertEquals("select x.\"k\" from \"q\" x order by x.\"k\" asc",
                      rename(helper, "select q.k from q order by q.k", "q", "x", null), helper);
         assertEquals("select x.\"k\" from \"q\" x group by x.\"k\" order by x.\"k\" desc",
                      rename(helper, "select \"q\".k from \"q\" group by \"q\".k order by \"q\".k desc",
                             "q", "x", null), helper);
         assertEquals("select x.\"MixedCase\" from \"q\" x order by x.\"MixedCase\" asc",
                      rename(helper, "select q.\"MixedCase\" from q order by \"MixedCase\"", "q", "x", null),
                      helper);
         assertEquals("select x.\"MixedCase\" from \"q\" x group by x.\"MixedCase\" " +
                         "order by x.\"MixedCase\" asc",
                      rename(helper, "select q.\"MixedCase\" from q group by q.\"MixedCase\" " +
                                "order by q.\"MixedCase\"", "q", "x", null), helper);
      }

      assertEquals("select x.k from q x group by x.k order by x.k asc",
                   rename("h2", "select q.k from q group by k order by k", "q", "x", null), "h2");
      assertEquals("select x.k from q x order by x.k asc",
                   rename("h2", "select q.k from q order by q.k", "q", "x", null), "h2");
      assertEquals("select x.\"MixedCase\" from q x order by x.\"MixedCase\" asc",
                   rename("h2", "select q.\"MixedCase\" from q order by q.\"MixedCase\"", "q", "x", null),
                   "h2");
   }

   /**
    * Removing a table keeps the conditions of the other tables when there is no table
    * metadata, so the fields have no table.
    */
   @Test
   void removeTableKeepsConditionsOfOtherTables() throws Exception {
      String query = "select q.k, u.k from q, u where q.k = u.k and q.k > 1";

      for(String helper : QUOTING) {
         String generated = remove(helper, query, "u");
         assertTrue(generated.contains(" from \"q\""), helper + " -> " + generated);
         assertTrue(generated.contains(" where \"q\".\"k\" > 1"), helper + " -> " + generated);
         assertFalse(generated.contains("\"u\".\"k\" ="), helper + " -> " + generated);
      }

      String generated = remove("h2", query, "u");
      assertTrue(generated.contains(" where q.k > 1"), "h2 -> " + generated);
   }

   private static void assertNoOldQualifier(String helper, String query, String generated) {
      assertFalse(generated.contains("\"q\"."), helper + " " + query + " -> " + generated);
      assertFalse(generated.contains(" q."), helper + " " + query + " -> " + generated);
   }

   private static String table(String helper) {
      return "oracle".equals(helper) ? "Q" : "q";
   }

   // renames the table as the query graph does, after the graph has opened the query
   private static String rename(String helper, String query, String from, String to) throws Exception {
      return rename(helper, query, from, to, COLUMNS);
   }

   // renames the table with the metadata of the columns, or without table metadata
   private static String rename(String helper, String query, String from, String to, String[] columns)
      throws Exception
   {
      Fixture fixture = new Fixture(helper, query, columns);
      SelectTable table = fixture.table(from);
      QueryTableModel model = new QueryTableModel();
      model.setName(Tool.toString(table.getName()));
      model.setAlias(to);
      EditQueryTableEvent event = new EditQueryTableEvent();
      event.setId(RID);
      event.setOldName(table.getAlias());
      event.setTables(List.of(model));
      fixture.graph.editQueryTableProperties(event, null);
      return regenerate(fixture.sql());
   }

   // removes a table as the query graph does, without table metadata
   private static String remove(String helper, String query, String name) throws Exception {
      Fixture fixture = new Fixture(helper, query, null);
      RemoveGraphTableEvent.RemoveTableInfo info = new RemoveGraphTableEvent.RemoveTableInfo();
      info.setFullName(Tool.toString(fixture.table(name).getName()));
      info.setTableName(info.getFullName());
      fixture.graph.removeTables(RID, List.of(info), null);
      return regenerate(fixture.sql());
   }

   private static final class Fixture {
      Fixture(String helper, String query, String[] columns) throws Exception {
         ds = dataSource(helper);
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(ds);
         sql.parse(query, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), query);

         JDBCQuery jquery = new JDBCQuery();
         jquery.setDataSource(ds);
         jquery.setSQLDefinition(sql);
         runtimeQuery = new RuntimeQueryService.RuntimeXQuery(jquery, RID, ds.getFullName());

         RuntimeQueryService rqs = mock(RuntimeQueryService.class);
         when(rqs.getRuntimeQuery(RID)).thenReturn(runtimeQuery);
         QueryManagerService qms = new QueryManagerService(
            rqs, repository(columns), mock(DataSourceService.class), mock(SecurityEngine.class),
            mock(ColumnCache.class));
         graph = new QueryGraphModelService(rqs, mock(DataSourceService.class), qms,
                                            mock(DataSourceRegistry.class));
         // the graph opens the query with the table metadata
         qms.fixUniformSQLInfo(sql, ds, null);
      }

      UniformSQL sql() {
         return (UniformSQL) runtimeQuery.getQuery().getSQLDefinition();
      }

      // the table of a name, written without quotes
      SelectTable table(String name) {
         for(SelectTable table : sql().getSelectTable()) {
            if(name.equalsIgnoreCase(Tool.toString(table.getAlias()).replace("\"", ""))) {
               return table;
            }
         }

         throw new AssertionError("no table " + name);
      }

      private final JDBCDataSource ds;
      private final RuntimeQueryService.RuntimeXQuery runtimeQuery;
      private final QueryGraphModelService graph;
   }

   private static String regenerate(UniformSQL sql) {
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static List<String> rows(Statement stmt, String query) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(ResultSet rs = stmt.executeQuery(query)) {
         while(rs.next()) {
            rows.add(String.valueOf(rs.getObject(1)));
         }
      }

      Collections.sort(rows);
      return rows;
   }

   // the metadata of every table, or none
   private static XRepository repository(String[] columns) throws Exception {
      XRepository repository = mock(XRepository.class);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);

         if("DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            return new XNode("properties");
         }

         if(columns == null) {
            return null;
         }

         XTypeNode result = new XTypeNode("Result");

         for(String column : columns) {
            result.addChild(XSchema.createPrimitiveType(column, Integer.class));
         }

         XTypeNode meta = new XTypeNode("meta");
         meta.addChild(result);
         return meta;
      });

      return repository;
   }

   // the table metadata is cached by data source, so each query gets its own
   private static JDBCDataSource dataSource(String helper) {
      JDBCDataSource ds = new JDBCDataSource();

      switch(helper) {
      case "h2" -> {
         ds.setDriver("org.h2.Driver");
         ds.setURL("jdbc:h2:mem:x");
      }
      case "oracle" -> {
         ds.setDriver("oracle.jdbc.OracleDriver");
         ds.setURL("jdbc:oracle:thin:@localhost:1521:x");
      }
      case "postgresql" -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost/db");
      }
      case "snowflake" -> {
         ds.setDriver("net.snowflake.client.jdbc.SnowflakeDriver");
         ds.setURL("jdbc:snowflake://x");
      }
      case "exasol" -> {
         ds.setDriver("com.exasol.jdbc.EXADriver");
         ds.setURL("jdbc:exa:x");
      }
      default -> throw new IllegalArgumentException(helper);
      }

      ds.setName("ds77648" + helper + RUN + "_" + (++sources));
      ds.setRuntimeProductName(helper);
      // otherwise the mysql and oracle helpers ask the repository for it
      ds.setProductVersion("10.0");
      return ds;
   }

   // a JDBCDataSource creates its credential when it is constructed, and
   // JDBCUtil.fixUniformSQLInfo looks up the driver type
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

   private static final String RID = "rq-77648";
   private static final String[] COLUMNS = { "MixedCase", "k" };
   private static final long RUN = System.nanoTime();
   private static int sources;
}
