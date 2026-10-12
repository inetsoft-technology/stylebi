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
package inetsoft.web.portal.controller.database;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.ColumnCache;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.util.credential.*;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.model.database.events.*;
import inetsoft.web.portal.model.database.graph.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Point;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77487, a model fetch no longer clears the sql the user typed, so a Links tab edit
 * (add/remove a table, create/edit/delete a join) must clear it itself. Otherwise the save
 * keeps the typed sql and the edit is lost.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, QueryGraphModelServiceTypedSqlTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class QueryGraphModelServiceTypedSqlTest {
   private static final String RID = "rq-77487-graph";
   private static final String JOIN_SQL =
      "SELECT a.x, b.z\n  FROM a, b\n WHERE a.id = b.id AND a.x > 1";
   private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

   private RuntimeQueryService rqs;
   private DataSourceService dss;
   private QueryManagerService qms;
   private QueryGraphModelService graph;
   private RuntimeQueryService.RuntimeXQuery runtimeQuery;
   private JDBCDataSource ds;

   @BeforeEach
   void setUp() {
      rqs = mock(RuntimeQueryService.class);
      dss = mock(DataSourceService.class);
      qms = new QueryManagerService(rqs, mock(XRepository.class), dss,
                                    mock(SecurityEngine.class), mock(ColumnCache.class));
      graph = new QueryGraphModelService(rqs, dss, qms, mock(DataSourceRegistry.class));
   }

   @Test
   void addTableIsSaved() throws Exception {
      typeSql("SELECT a.x\n  FROM a\n WHERE a.x > 1");
      AssetEntry table = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                        AssetEntry.Type.PHYSICAL_TABLE, "ds/c", null);
      table.setProperty("source_with_no_quote", "c");
      table.setProperty("source", "c");

      graph.addTables(RID, List.of(table), new Point(10, 10), principal());

      String saved = save();
      assertTrue(saved.matches(".*from a, c .*"), saved);
   }

   @Test
   void removeTableIsSaved() throws Exception {
      typeSql("SELECT a.x\n  FROM a, b\n WHERE a.x > 1");
      RemoveGraphTableEvent.RemoveTableInfo table =
         mock(RemoveGraphTableEvent.RemoveTableInfo.class);
      when(table.getFullName()).thenReturn("b");

      graph.removeTables(RID, List.of(table), principal());

      String saved = save();
      assertEquals("select a.x from a where a.x > 1", saved);
   }

   // Bug #77544, removing one of two tables whose names differ only in case ("A" and "a")
   // removes that table, not the first case-insensitive match. The quotes are kept (#77569)
   @Test
   void removeCaseDistinctTable() throws Exception {
      typeSql("SELECT \"a\".id\n  FROM \"a\", \"A\"\n WHERE \"a\".id > 1");
      removeTable("A");
      assertEquals("select \"a\".id from \"a\" where \"a\".id > 1", save());

      typeSql("SELECT \"A\".id\n  FROM \"A\", \"a\"\n WHERE \"A\".id > 1");
      removeTable("a");
      assertEquals("select \"A\".id from \"A\" where \"A\".id > 1", save());
   }

   @Test
   void createJoinIsSaved() throws Exception {
      typeSql("SELECT a.x, b.z\n  FROM a, b\n WHERE a.x > 1");
      DefaultMetaDataProvider metaData = mock(DefaultMetaDataProvider.class);
      when(metaData.getDataSource()).thenReturn(ds);
      when(dss.getDefaultMetaDataProvider(any(), any())).thenReturn(metaData);

      graph.createJoin(joinInfo());

      String saved = save();
      assertTrue(saved.contains("a.id = b.id"), saved);
      assertTrue(saved.contains("a.x > 1"), saved);
   }

   @Test
   void editJoinIsSaved() throws Exception {
      typeSql(JOIN_SQL);
      JoinModel join = new JoinModel();
      join.setType(JoinType.GREATER);
      EditJoinEvent event = new EditJoinEvent();
      event.setDetailJoinInfo(joinInfo());
      event.setJoinModel(join);

      graph.editJoin(event);

      String saved = save();
      assertTrue(saved.contains("a.id > b.id"), saved);
   }

   @Test
   void deleteJoinIsSaved() throws Exception {
      typeSql(JOIN_SQL);
      TableJoinInfo info = new TableJoinInfo();
      info.setRuntimeId(RID);
      info.setSourceTable("a");
      info.setTargetTable("b");

      graph.deleteJoins(info);

      String saved = save();
      assertFalse(saved.contains("a.id = b.id"), saved);
      assertTrue(saved.contains("a.x > 1"), saved);
   }

   // open the Links tab without an edit, then OK: the typed sql is kept
   @Test
   void linksTabWithoutEditKeepsTypedSql() throws Exception {
      typeSql(JOIN_SQL);
      runtimeQuery.setSelectedTables(new HashMap<>());

      graph.getQueryGraphModel(RID, null, principal());
      qms.updateQuery(RID, clientEcho(), null, true);

      UniformSQL sql = currentSql();
      assertTrue(sql.hasSQLString());
      assertEquals(JOIN_SQL, sql.getSQLString());
   }

   // a real JDBCDataSource creates its credential through the CredentialService bean
   @Configuration
   static class Config {
      @Bean
      CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         when(service.createCredential(any(), anyBoolean()))
            .thenAnswer(inv -> new LocalPasswordCredential());
         return service;
      }
   }

   // ---- helpers ----

   // type the sql and leave the sql tab: the client posts it (save/freeSQLModel) and refreshes
   private void typeSql(String text) throws Exception {
      install(new UniformSQL());
      FreeFormSQLPaneModel pane = new FreeFormSQLPaneModel();
      pane.setSqlString(text);
      pane.setParseSql(true);
      UpdateFreeFormSQLPaneEvent event = new UpdateFreeFormSQLPaneEvent();
      event.setRuntimeId(RID);
      event.setFreeFormSqlPaneModel(pane);
      qms.setFreeFormSQLPaneModel(event, principal());

      UniformSQL sql = currentSql();
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertFalse(QueryManagerService.isSqlOnly(sql));
      clientEcho();
      assertEquals(text, currentSql().getSQLString());
   }

   // OK after the edit's model refresh: sqlEdited is false, so save goes through updateQuery(all)
   private String save() throws Exception {
      AdvancedSQLQueryModel model = clientEcho();
      assertFalse(model.isSqlEdited());
      qms.updateQuery(RID, model, null, true);

      UniformSQL sql = currentSql();
      assertFalse(sql.hasSQLString());
      // the sorted sql of the cache normalizer would sort the generated select list
      sql.setHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, true);
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private void removeTable(String name) {
      RemoveGraphTableEvent.RemoveTableInfo table =
         mock(RemoveGraphTableEvent.RemoveTableInfo.class);
      when(table.getFullName()).thenReturn(name);
      graph.removeTables(RID, List.of(table), principal());
   }

   private AdvancedSQLQueryModel clientEcho() throws Exception {
      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, null);
      return MAPPER.readValue(MAPPER.writeValueAsString(model), AdvancedSQLQueryModel.class);
   }

   private UniformSQL currentSql() {
      return (UniformSQL) runtimeQuery.getQuery().getSQLDefinition();
   }

   private static TableDetailJoinInfo joinInfo() {
      TableDetailJoinInfo info = new TableDetailJoinInfo();
      info.setRuntimeId(RID);
      info.setSourceTable("a");
      info.setSourceColumn("id");
      info.setTargetTable("b");
      info.setTargetColumn("id");
      return info;
   }

   private static Principal principal() {
      Principal principal = mock(Principal.class);
      when(principal.getName()).thenReturn("admin");
      return principal;
   }

   private void install(UniformSQL sql) {
      JDBCQuery query = new JDBCQuery();
      ds = new JDBCDataSource();
      ds.setName("ds");
      ds.setDriver("org.hsqldb.jdbcDriver");
      ds.setURL("jdbc:hsqldb:mem:db");
      ds.setRuntimeProductName("hsql");
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      sql.setDataSource(ds);
      runtimeQuery = new RuntimeQueryService.RuntimeXQuery(query, RID, "ds");
      runtimeQuery.setVariables(new VariableTable());
      when(rqs.getRuntimeQuery(RID)).thenReturn(runtimeQuery);
   }
}
