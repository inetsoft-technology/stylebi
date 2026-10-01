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
import inetsoft.uql.util.ColumnCache;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.credential.*;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.portal.model.database.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77487, #77496 (d), the advanced query editor must keep the sql the user typed when a
 * model fetch, a tab-leave or a save posts panes that are unchanged echoes of its structure,
 * and must still apply a pane the user changed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, QueryManagerServiceTypedSqlTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class QueryManagerServiceTypedSqlTest {
   private static final String RID = "rq-77487";
   private static final String TYPED_SQL =
      "SELECT a.x,\n       a.y\n  FROM a\n WHERE a.x > 1\n ORDER BY a.x";
   private static final String ALIAS_SQL =
      "SELECT a.x AS \"X col\", a.y\n  FROM a\n WHERE a.x > 1 AND a.y IN (1, 2)\n ORDER BY a.x";
   private static final String COMMENT_SQL =
      "select a.x, a.y /* keep me */ from a where a.x > 1 order by a.x";
   private static final String GROUP_SQL =
      "SELECT a.x, sum(a.y) AS s\n  FROM a\n GROUP BY a.x\nHAVING sum(a.y) > 10";
   private static final String IMPLICIT_JOIN_SQL =
      "SELECT a.x, b.z\n  FROM a, b\n WHERE a.id = b.id AND a.x > 1";
   private static final String INNER_JOIN_SQL =
      "SELECT a.x, b.z\n  FROM a INNER JOIN b ON a.id = b.id\n WHERE b.z LIKE 'q%'";
   private static final String TOP_SQL = "select top 10 a.x, a.y from a where a.x > 1 order by a.x";
   private static final String FAILED_SQL = "select a.x from a where a.x ~ 'q'";
   private static final String[] SHAPES = {
      TYPED_SQL, ALIAS_SQL, COMMENT_SQL, GROUP_SQL, IMPLICIT_JOIN_SQL, INNER_JOIN_SQL,
      "select a.x, b.z from a, b where a.x > 1 and a.id = b.id",
      "select a.x, b.z from a left outer join b on a.id = b.id",
      "select a.x, b.z, c.w from a inner join b on a.id = b.id inner join c on b.cid = c.id",
      "select a.x from a where (a.x > 1 or a.y < 2) and a.z = 3",
      "select a.x + 1 as p, upper(a.n) as u from a",
      "select a.x from a where a.x in (select b.x from b)",
      "select distinct a.x, a.y from a order by a.y desc, a.x",
      "select a.x from a where a.x = $(v)",
      "select a.x, a.y from a order by a.z",
      // an unchanged echo of these used to fail in JDBCUtil.createXFilterNode
      "select a.x from a where a.x between 1 and 5 and a.y is not null and not a.z = 1",
      "select a.x from a where a.x like 'z%' or a.y is null",
   };
   private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

   private RuntimeQueryService rqs;
   private QueryManagerService qms;
   private RuntimeQueryService.RuntimeXQuery runtimeQuery;
   private String product = "hsql";

   @BeforeEach
   void setUp() {
      rqs = mock(RuntimeQueryService.class);
      qms = new QueryManagerService(rqs, mock(XRepository.class), mock(DataSourceService.class),
                                    mock(SecurityEngine.class), mock(ColumnCache.class));
   }

   // ---- step 1: a model fetch must not clear the typed sql ----

   @Test
   void modelFetchKeepsTypedSql() throws Exception {
      UniformSQL sql = install(TYPED_SQL);

      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, null);

      assertTrue(sql.hasSQLString());
      assertEquals(TYPED_SQL, sql.getSQLString());
      assertEquals(TYPED_SQL, model.getFreeFormSQLPaneModel().getSqlString());
      String generated = oneLine(model.getFreeFormSQLPaneModel().getGeneratedSqlString());
      assertTrue(generated.startsWith("select a.x, a.y from a where a.x > 1"), generated);
      assertEquals(false, sql.getHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, false));
   }

   @Test
   void modelFetchRestoresSetHint() throws Exception {
      UniformSQL sql = install(TYPED_SQL);
      sql.setHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, true);

      qms.getAdvancedQueryModel(runtimeQuery, null);

      assertEquals(true, sql.getHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, false));
   }

   // ---- the reported paths ----

   // reopen a saved query with typed sql, press OK without edits
   @Test
   void reopenThenOkWithoutEditsKeepsTypedSql() throws Exception {
      UniformSQL saved = parse(TYPED_SQL);
      UniformSQL sql = install(saved.clone());

      save(clientEcho());

      assertTrue(sql.hasSQLString());
      assertEquals(TYPED_SQL, sql.getSQLString());
   }

   // type sql -> Preview (or any tab, the sql tab-leave parses) -> back -> OK, and type sql ->
   // Parse Now -> OK. Both post the sql to save/freeSQLModel and refresh the model, which
   // resets the client's sqlEdited, so the OK goes through updateQuery(all).
   @ParameterizedTest
   @ValueSource(strings = { TYPED_SQL, ALIAS_SQL, COMMENT_SQL, IMPLICIT_JOIN_SQL, INNER_JOIN_SQL })
   void parseThenOkKeepsTypedSql(String text) throws Exception {
      UniformSQL sql = installEmpty();
      parseFromClient(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());

      AdvancedSQLQueryModel model = clientEcho();
      assertEquals(text, model.getFreeFormSQLPaneModel().getSqlString());
      assertFalse(model.isSqlEdited());

      save(model);

      assertTrue(sql.hasSQLString());
      assertEquals(text, sql.getSQLString());
   }

   // SQL -> Conditions -> edit a condition -> OK while still on Conditions: applied
   @Test
   void conditionEditOnConditionsTabIsApplied() throws Exception {
      UniformSQL sql = installEmpty();
      parseFromClient(TYPED_SQL);
      AdvancedSQLQueryModel model = clientEcho();
      negateFirstClause(model.getConditionPaneModel());

      save(model);

      assertFalse(sql.hasSQLString());
      String saved = oneLine(sql.getSQLString());
      assertTrue(saved.contains("not"), saved);
      assertRoundTrip(sql);
   }

   // ---- each pane, unchanged vs changed ----

   @ParameterizedTest
   @MethodSource("unchangedCases")
   void unchangedPaneKeepsTypedSql(String text, String tab) throws Exception {
      UniformSQL sql = install(text);

      update(clientEcho(), tab);

      assertTrue(sql.hasSQLString(), tab);
      assertEquals(text, sql.getSQLString(), tab);
   }

   static Stream<Arguments> unchangedCases() {
      List<Arguments> list = new ArrayList<>();

      for(String text : new String[] { TYPED_SQL, ALIAS_SQL, COMMENT_SQL, GROUP_SQL }) {
         for(String tab : new String[] { "fields", "conditions", "sort", "grouping", "ALL" }) {
            list.add(Arguments.of(text, tab));
         }
      }

      return list.stream();
   }

   // the tab-leaves of a whole session, then the save: still the typed sql
   @Test
   void everyTabLeaveThenSaveKeepsTypedSql() throws Exception {
      UniformSQL sql = install(ALIAS_SQL);

      for(String tab : new String[] { "fields", "conditions", "sort", "grouping", "ALL" }) {
         update(clientEcho(), tab);
         assertEquals(ALIAS_SQL, sql.getSQLString(), "after " + tab);
      }
   }

   @ParameterizedTest
   @ValueSource(strings = { "fields", "ALL" })
   void changedDistinctIsApplied(String tab) throws Exception {
      UniformSQL sql = install(TYPED_SQL);
      AdvancedSQLQueryModel model = clientEcho();
      model.getFieldPaneModel().setDistinct(true);

      update(model, tab);

      assertFalse(sql.hasSQLString());
      assertTrue(oneLine(sql.getSQLString()).startsWith("select distinct a.x, a.y from a"));
      assertRoundTrip(sql);
   }

   @ParameterizedTest
   @ValueSource(strings = { "fields", "ALL" })
   void changedFieldOrderIsApplied(String tab) throws Exception {
      UniformSQL sql = install(TYPED_SQL);
      AdvancedSQLQueryModel model = clientEcho();
      Collections.reverse(model.getFieldPaneModel().getFields());

      update(model, tab);

      assertFalse(sql.hasSQLString());
      // the sorted sql of the cache normalizer would sort the generated select list
      sql.setHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, true);
      String saved = oneLine(sql.getSQLString());
      assertTrue(saved.startsWith("select a.y, a.x from a"), saved);
   }

   @ParameterizedTest
   @ValueSource(strings = { "conditions", "ALL" })
   void changedConditionIsApplied(String tab) throws Exception {
      UniformSQL sql = install(TYPED_SQL);
      AdvancedSQLQueryModel model = clientEcho();
      negateFirstClause(model.getConditionPaneModel());

      update(model, tab);

      assertFalse(sql.hasSQLString());
      assertTrue(oneLine(sql.getSQLString()).contains("not"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "conditions", "ALL" })
   void removedConditionIsApplied(String tab) throws Exception {
      UniformSQL sql = install(TYPED_SQL);
      AdvancedSQLQueryModel model = clientEcho();
      model.getConditionPaneModel().setConditions(new ArrayList<>());

      update(model, tab);

      assertFalse(sql.hasSQLString());
      assertFalse(oneLine(sql.getSQLString()).contains("where"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "sort", "ALL" })
   void changedSortIsApplied(String tab) throws Exception {
      UniformSQL sql = install(TYPED_SQL);
      AdvancedSQLQueryModel model = clientEcho();
      model.getSortPaneModel().getOrders().set(0, "desc");

      update(model, tab);

      assertFalse(sql.hasSQLString());
      assertTrue(oneLine(sql.getSQLString()).endsWith("order by a.x desc"));
      assertRoundTrip(sql);
   }

   @ParameterizedTest
   @ValueSource(strings = { "grouping", "ALL" })
   void changedGroupByIsApplied(String tab) throws Exception {
      UniformSQL sql = install(GROUP_SQL);
      AdvancedSQLQueryModel model = clientEcho();
      model.getGroupingPaneModel().getGroupByFields().add("a.y");

      update(model, tab);

      assertFalse(sql.hasSQLString());
      assertTrue(oneLine(sql.getSQLString()).contains("group by a.x, a.y"),
                 sql.getSQLString());
   }

   @ParameterizedTest
   @ValueSource(strings = { "grouping", "ALL" })
   void changedHavingIsApplied(String tab) throws Exception {
      UniformSQL sql = install(GROUP_SQL);
      AdvancedSQLQueryModel model = clientEcho();
      negateFirstClause(model.getGroupingPaneModel().getHavingConditions());

      update(model, tab);

      assertFalse(sql.hasSQLString());
      String saved = oneLine(sql.getSQLString());
      assertTrue(saved.contains("having") && saved.contains("not"), saved);
   }

   // ---- join + where: an unchanged where echo re-appends the joins after the condition ----

   @ParameterizedTest
   @MethodSource("joinCases")
   void joinWithWhereUnchangedKeepsTypedSql(String text, String tab) throws Exception {
      UniformSQL sql = install(text);

      update(clientEcho(), tab);

      assertTrue(sql.hasSQLString());
      assertEquals(text, sql.getSQLString());
   }

   @ParameterizedTest
   @MethodSource("joinCases")
   void joinWithWhereChangedKeepsJoin(String text, String tab) throws Exception {
      UniformSQL sql = install(text);
      AdvancedSQLQueryModel model = clientEcho();
      negateFirstClause(model.getConditionPaneModel());

      update(model, tab);

      assertFalse(sql.hasSQLString());
      String saved = oneLine(sql.getSQLString());
      assertTrue(saved.contains("not"), saved);
      assertTrue(saved.contains("a.id = b.id"), saved);
      assertRoundTrip(sql);
   }

   static Stream<Arguments> joinCases() {
      return Stream.of(
         Arguments.of(IMPLICIT_JOIN_SQL, "conditions"), Arguments.of(IMPLICIT_JOIN_SQL, "ALL"),
         Arguments.of(INNER_JOIN_SQL, "conditions"), Arguments.of(INNER_JOIN_SQL, "ALL"));
   }

   // ---- every shape, pane and dialect: an unchanged echo keeps the typed sql ----

   @ParameterizedTest
   // the helpers core can load in a test: the oracle, db2 and mysql helpers fail
   // to install here (every parse ends lossy) and the sql server helper lives in a connector
   @ValueSource(strings = { "hsql", "postgresql" })
   void unchangedEchoKeepsEveryShape(String dialect) throws Exception {
      product = dialect;
      int checked = 0;

      for(String text : SHAPES) {
         // a quoting dialect generates the IN subquery of the condition echo differently on
         // the next fetch (a subquery takes the parent's data source only when the parent is
         // generated), so the echo counts as changed and falls back to today's update
         if(text.contains("in (select") && "postgresql".equals(dialect)) {
            continue;
         }

         for(String tab : new String[] { "fields", "conditions", "sort", "grouping", "ALL" }) {
            UniformSQL sql = install(text);

            if(QueryManagerService.isSqlOnly(sql)) {
               continue;
            }

            update(clientEcho(), tab);

            assertEquals(text, sql.getSQLString(), dialect + " " + tab + " " + text);
            checked++;
         }
      }

      assertTrue(checked >= 50, "checked " + checked);
   }

   // ---- #77437 isSqlOnly stays first: a changed pane does not touch sql-only text ----

   @ParameterizedTest
   @ValueSource(strings = { TOP_SQL, FAILED_SQL })
   void sqlOnlyKeepsTextEvenWithChangedPane(String text) throws Exception {
      UniformSQL sql = install(text);
      assertTrue(QueryManagerService.isSqlOnly(sql));
      AdvancedSQLQueryModel model = clientEcho();
      model.getFieldPaneModel().setDistinct(true);

      save(model);

      assertTrue(sql.hasSQLString());
      assertEquals(text, sql.getSQLString());
   }

   @Test
   void parseOffKeepsTextEvenWithChangedPane() throws Exception {
      UniformSQL off = new UniformSQL();
      off.setParseSQL(false);
      off.setSQLString(TYPED_SQL);
      UniformSQL sql = install(off);
      AdvancedSQLQueryModel model = clientEcho();
      model.getFieldPaneModel().setDistinct(true);

      save(model);

      assertTrue(sql.hasSQLString());
      assertEquals(TYPED_SQL, sql.getSQLString());
   }

   // ---- structure edits that don't go through updateQuery still regenerate ----

   @Test
   void clearJoinsRegenerates() throws Exception {
      UniformSQL sql = install(INNER_JOIN_SQL);
      QueryGraphModelService graph = new QueryGraphModelService(
         rqs, mock(DataSourceService.class), qms, mock(DataSourceRegistry.class));

      graph.clearJoins(RID);

      assertFalse(sql.hasSQLString());
      assertFalse(oneLine(sql.getSQLString()).contains("join"));
   }

   @Test
   void addExpressionRegenerates() throws Exception {
      UniformSQL sql = install(TYPED_SQL);

      qms.saveExpression(RID, "a.x + 1", null, null, true, principal());

      assertFalse(sql.hasSQLString());
      assertTrue(oneLine(sql.getSQLString()).contains("a.x + 1"), sql.getSQLString());
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

   private void save(AdvancedSQLQueryModel model) throws Exception {
      update(model, "ALL");
   }

   private void update(AdvancedSQLQueryModel model, String tab) throws Exception {
      if("ALL".equals(tab)) {
         qms.updateQuery(RID, model, null, true);
      }
      else {
         qms.updateQuery(RID, model, tab, false);
      }
   }

   // the model as the client posts it back: fetched, sent as json and read back
   private AdvancedSQLQueryModel clientEcho() throws Exception {
      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, null);
      String json = MAPPER.writeValueAsString(model);
      return MAPPER.readValue(json, AdvancedSQLQueryModel.class);
   }

   // what the sql tab-leave and Parse Now post (save/freeSQLModel)
   private void parseFromClient(String text) {
      FreeFormSQLPaneModel pane = new FreeFormSQLPaneModel();
      pane.setSqlString(text);
      pane.setParseSql(true);
      UpdateFreeFormSQLPaneEvent event = new UpdateFreeFormSQLPaneEvent();
      event.setRuntimeId(RID);
      event.setFreeFormSqlPaneModel(pane);
      qms.setFreeFormSQLPaneModel(event, principal());
   }

   private static Principal principal() {
      Principal principal = mock(Principal.class);
      when(principal.getName()).thenReturn("admin");
      return principal;
   }

   private static void negateFirstClause(QueryConditionPaneModel pane) {
      List<DataConditionItem> conds = pane.getConditions();
      assertNotNull(conds);
      Clause clause = (Clause) conds.stream().filter(c -> c instanceof Clause).findFirst()
         .orElseThrow();
      clause.setNegated(!clause.isNegated());
   }

   // the regenerated sql must re-parse and regenerate to itself
   private void assertRoundTrip(UniformSQL sql) throws Exception {
      String generated = sql.getSQLString();
      UniformSQL again = parse(generated);
      again.setDataSource(sql.getDataSource());
      assertEquals(UniformSQL.PARSE_SUCCESS, again.getParseResult(), generated);
      again.clearSQLString();
      assertEquals(oneLine(generated), oneLine(again.getSQLString()));
   }

   private static String oneLine(String text) {
      return text.replaceAll("\\s+", " ").trim();
   }

   private UniformSQL installEmpty() {
      return install(new UniformSQL());
   }

   private UniformSQL install(String text) throws Exception {
      return install(parse(text));
   }

   private UniformSQL install(UniformSQL sql) {
      JDBCQuery query = new JDBCQuery();
      // a real data source with a driver and url, and a known product name that keeps
      // SQLHelper.getSQLHelper() from looking it up through a connection
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds");

      switch(product) {
      case "postgresql":
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/db");
         break;
      default:
         ds.setDriver("org.hsqldb.jdbcDriver");
         ds.setURL("jdbc:hsqldb:mem:db");
      }

      ds.setRuntimeProductName(product);
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      sql.setDataSource(ds);
      runtimeQuery = new RuntimeQueryService.RuntimeXQuery(query, RID, "ds");
      runtimeQuery.setVariables(new VariableTable());
      when(rqs.getRuntimeQuery(RID)).thenReturn(runtimeQuery);
      return sql;
   }

   // same as QueryManagerService.parseSqlString: the parse is asynchronous
   private static UniformSQL parse(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setParseSQL(true);

      synchronized(sql) {
         sql.setSQLString(text);
         sql.wait();
      }

      return sql;
   }
}
