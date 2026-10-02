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
import inetsoft.uql.jdbc.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.ColumnCache;
import inetsoft.uql.util.DefaultMetaDataProvider;
import inetsoft.util.credential.*;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.portal.model.database.*;
import inetsoft.web.portal.model.database.events.RemoveGraphTableEvent;
import inetsoft.web.portal.model.database.graph.TableJoinInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77487, #77496, review r1 of PR #6103. A model fetch no longer clears the sql the user
 * typed, so each structure edit that doesn't go through updateQuery must clear it itself, and
 * the edit must survive the save (updateQuery(all)) and the tab-leaves (per-tab updateQuery).
 * Also pins that the pane comparison applies a value-only condition edit, a regroup, an
 * operator change and an alias case change.
 *
 * The save path parameter: "ALL" is OK after the edit's refresh (updateQuery(all)), "TABS" is
 * leaving fields, conditions, sort and grouping in turn, each with a fresh echo, then OK.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, QueryManagerServiceStructureEditTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class QueryManagerServiceStructureEditTest {
   private static final String RID = "rq-77487-edit";
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

   // ---- QueryGraphModelService.removeTables ----

   // tables left (the save-all path is in QueryGraphModelServiceTypedSqlTest)
   @Test
   void removeTableWithTablesLeftSurvivesTabLeaves() throws Exception {
      typeSql("SELECT a.x\n  FROM a, b\n WHERE a.x > 1");

      graph.removeTables(RID, List.of(removeInfo("b")), principal());

      assertEquals("select a.x from a where a.x > 1", save("TABS"));
   }

   // no table left: the structure is emptied, the typed sql must not come back
   @ParameterizedTest
   @ValueSource(strings = { "ALL", "TABS" })
   void removeLastTableIsSaved(String path) throws Exception {
      typeSql("SELECT a.x\n  FROM a\n WHERE a.x > 1");

      graph.removeTables(RID, List.of(removeInfo("a")), principal());

      String saved = save(path);
      assertFalse(saved.contains("a.x"), saved);
      assertFalse(saved.contains("from a"), saved);
   }

   // ---- QueryGraphModelService.autoCreateJoinByColumn (join edit pane open) ----

   @ParameterizedTest
   @ValueSource(strings = { "ALL", "TABS" })
   void autoCreatedJoinIsSaved(String path) throws Exception {
      typeSql("SELECT s.id, t.id\n  FROM (SELECT a.id FROM a) s, (SELECT b.id FROM b) t");
      openJoinEditPane("s", "t");

      String saved = save(path);
      assertTrue(saved.contains("s.id = t.id"), saved);
   }

   // nothing to join on: the pane open must not regenerate the typed sql
   @Test
   void noAutoCreatedJoinKeepsTypedSql() throws Exception {
      String text = "SELECT s.id, t.k\n  FROM (SELECT a.id FROM a) s, (SELECT b.k FROM b) t";
      typeSql(text);
      openJoinEditPane("s", "t");

      qms.updateQuery(RID, clientEcho(), null, true);

      assertTrue(currentSql().hasSQLString());
      assertEquals(text, currentSql().getSQLString());
   }

   // ---- QueryManagerService.saveExpression ----

   @ParameterizedTest
   @ValueSource(strings = { "ALL", "TABS" })
   void addedExpressionIsSaved(String path) throws Exception {
      typeSql("SELECT a.x,\n       a.y\n  FROM a");

      qms.saveExpression(RID, "a.x + 1", null, null, true, principal());

      String saved = save(path);
      assertTrue(saved.startsWith("select a.x, a.y, a.x + 1"), saved);
   }

   @ParameterizedTest
   @ValueSource(strings = { "ALL", "TABS" })
   void editedExpressionIsSaved(String path) throws Exception {
      typeSql("SELECT a.x + 1 AS p,\n       a.y\n  FROM a");
      JDBCSelection selection = (JDBCSelection) currentSql().getSelection();
      String column = selection.getColumn(0);
      assertEquals("p", selection.getAlias(0));
      // fixUniformSQLInfo lists the expression as a field once the metadata is read, which
      // the mocked repository here can't do; add it the way addExpression() does
      currentSql().addField(new XField(null, column, "", XField.STRING_TYPE));

      qms.saveExpression(RID, "a.x + 2", column, "p", false, principal());

      String saved = save(path);
      assertTrue(saved.startsWith("select a.x + 2 as p, a.y"), saved);
   }

   // ---- M3: edits the pane comparison must not miss ----

   @ParameterizedTest
   @ValueSource(strings = { "conditions", "ALL" })
   void valueOnlyConditionEditIsApplied(String tab) throws Exception {
      typeSql("SELECT a.x\n  FROM a\n WHERE a.x > 1");
      AdvancedSQLQueryModel model = clientEcho();
      firstClause(model.getConditionPaneModel()).getValue2().setExpression("2");

      update(model, tab);

      assertEquals("select a.x from a where a.x > 2", generated());
   }

   @ParameterizedTest
   @ValueSource(strings = { "conditions", "ALL" })
   void operatorChangeIsApplied(String tab) throws Exception {
      typeSql("SELECT a.x\n  FROM a\n WHERE a.x > 1");
      AdvancedSQLQueryModel model = clientEcho();
      Operation operation = firstClause(model.getConditionPaneModel()).getOperation();
      operation.setName("less than");
      operation.setSymbol("<");

      update(model, tab);

      assertEquals("select a.x from a where a.x < 1", generated());
   }

   // only the levels change: (x or y) and z -> x or (y and z)
   @ParameterizedTest
   @ValueSource(strings = { "conditions", "ALL" })
   void regroupIsApplied(String tab) throws Exception {
      typeSql("SELECT a.x\n  FROM a\n WHERE (a.x > 1 OR a.y < 2) AND a.z = 3");
      String before = regenerated();
      AdvancedSQLQueryModel model = clientEcho();
      List<DataConditionItem> items = model.getConditionPaneModel().getConditions();
      assertTrue(items.stream().anyMatch(c -> c.getLevel() > 0), "grouped condition expected");
      items.forEach(c -> c.setLevel(0));

      update(model, tab);

      String after = generated();
      assertNotEquals(before, after);
      // and binds tighter than or, the generator brackets it
      assertEquals("select a.x from a where a.x > 1 or (a.y < 2 and a.z = 3)", after);
   }

   // the editor renames through column/update (alias), the following save keeps the new case
   @ParameterizedTest
   @ValueSource(strings = { "ALL", "TABS" })
   void aliasCaseChangeIsSaved(String path) throws Exception {
      typeSql("SELECT a.x AS p,\n       a.y\n  FROM a");
      QueryFieldModel field = clientEcho().getFieldPaneModel().getFields().get(0);
      field.setAlias("P");

      qms.updateColumn(RID, field, "alias", "p");

      String saved = save(path);
      assertTrue(saved.startsWith("select a.x as P, a.y"), saved);
   }

   // a case-only alias difference in the posted fields pane counts as a change
   @Test
   void aliasCaseDifferenceIsAFieldPaneChange() throws Exception {
      typeSql("SELECT a.x AS p,\n       a.y\n  FROM a");
      AdvancedSQLQueryModel model = clientEcho();
      assertFalse(QueryManagerService.isFieldPaneChanged(currentSql(), model.getFieldPaneModel()));

      model.getFieldPaneModel().getFields().get(0).setAlias("P");

      assertTrue(QueryManagerService.isFieldPaneChanged(currentSql(), model.getFieldPaneModel()));
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
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(QueryManagerService.isSqlOnly(sql), text);
      clientEcho();
      assertEquals(text, currentSql().getSQLString());
   }

   private void openJoinEditPane(String source, String target) throws Exception {
      DefaultMetaDataProvider metaData = mock(DefaultMetaDataProvider.class);
      when(metaData.getDataSource()).thenReturn(ds);
      when(dss.getDefaultMetaDataProvider(any(), any())).thenReturn(metaData);
      runtimeQuery.setSelectedTables(new HashMap<>());
      TableJoinInfo info = new TableJoinInfo();
      info.setRuntimeId(RID);
      info.setSourceTable(source);
      info.setTargetTable(target);
      info.setAutoCreateColumnJoin(true);

      graph.getQueryGraphModel(RID, info, principal());
   }

   // the editor's save after the edit's model refresh, through either path; returns the
   // saved sql on one line
   private String save(String path) throws Exception {
      if("TABS".equals(path)) {
         for(String tab : new String[] { "fields", "conditions", "sort", "grouping" }) {
            qms.updateQuery(RID, clientEcho(), tab, false);
         }
      }

      AdvancedSQLQueryModel model = clientEcho();
      assertFalse(model.isSqlEdited());
      qms.updateQuery(RID, model, null, true);
      return generated();
   }

   private void update(AdvancedSQLQueryModel model, String tab) throws Exception {
      if("ALL".equals(tab)) {
         qms.updateQuery(RID, model, null, true);
      }
      else {
         qms.updateQuery(RID, model, tab, false);
      }
   }

   // the saved sql must be regenerated from the structure
   private String generated() {
      UniformSQL sql = currentSql();
      assertFalse(sql.hasSQLString(), "the typed sql was kept: " + sql.getSQLString());
      return oneLine(withoutSortedSql(sql));
   }

   // what the structure generates now, without touching the runtime sql
   private String regenerated() {
      UniformSQL copy = currentSql().clone();
      copy.clearSQLString();
      return oneLine(withoutSortedSql(copy));
   }

   // the sorted sql of the cache normalizer would sort the generated select list
   private static String withoutSortedSql(UniformSQL sql) {
      sql.setHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, true);

      try {
         String text = sql.getSQLString();
         return text == null ? "" : text;
      }
      finally {
         sql.setHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, false);
      }
   }

   private AdvancedSQLQueryModel clientEcho() throws Exception {
      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, null);
      return MAPPER.readValue(MAPPER.writeValueAsString(model), AdvancedSQLQueryModel.class);
   }

   private UniformSQL currentSql() {
      return (UniformSQL) runtimeQuery.getQuery().getSQLDefinition();
   }

   private static Clause firstClause(QueryConditionPaneModel pane) {
      return (Clause) pane.getConditions().stream().filter(c -> c instanceof Clause)
         .findFirst().orElseThrow();
   }

   private static RemoveGraphTableEvent.RemoveTableInfo removeInfo(String name) {
      RemoveGraphTableEvent.RemoveTableInfo table =
         mock(RemoveGraphTableEvent.RemoveTableInfo.class);
      when(table.getFullName()).thenReturn(name);
      return table;
   }

   private static String oneLine(String text) {
      return text.replaceAll("\\s+", " ").trim();
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
