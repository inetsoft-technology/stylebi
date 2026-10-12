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
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77541. The parser keeps a condition operator as typed ("is null", "Like", "!="). The
 * conditions pane echoes the clauses back on save, so after any condition edit every clause
 * must turn back into a condition node: the operator is matched ignoring case and saved with
 * its canonical symbol, and the pane gets the canonical symbol on load ("!=" as "<>").
 * Runs the real pane round trip: parse, getAdvancedQueryModel, a JSON echo, an edit to one
 * clause, updateQuery, on the hsql, oracle, sql server and default helpers.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  QueryManagerServiceConditionOpCaseTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class QueryManagerServiceConditionOpCaseTest {
   private static final String RID = "rq-77541";
   private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

   // product name, driver, url, expected helper class
   private static final String[][] HELPERS = {
      // there is no hsql helper, hsql uses the default one
      { "hsql", "org.hsqldb.jdbcDriver", "jdbc:hsqldb:mem:db", "SQLHelper" },
      { "mysql", "com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost:3306/db", "MySQLHelper" },
      { "oracle", "oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@localhost:1521:orcl",
        "OracleSQLHelper" },
      { "sql server", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "jdbc:sqlserver://localhost:1433", "SQLServerHelper" },
      { "unknowndb", "unknown.Driver", "jdbc:unknown://localhost", "SQLHelper" },
   };

   // where clause, the value to edit (the n-th clause value2, or value3 when "3"), new value,
   // expected regenerated where clause ("||" separates the forms the helpers generate)
   private static final String[][] WHERE_CASES = {
      // the two reported shapes
      { "a.x like 'z%' or a.y is null", "0", "'q%'",
        "where a.x LIKE 'q%' or a.y IS NULL" },
      { "a.x between 1 and 5 and a.y is not null and not a.z = 1", "0:3", "6",
        "where a.x BETWEEN 1 and 6 and a.y is not null and not (a.z = 1)||" +
        "where a.x BETWEEN 1 and 6 and a.y is not null and not a.z = 1" },
      { "a.x Like 'z%' and a.w = 2", "1", "3", "where a.x LIKE 'z%' and a.w = 3" },
      { "a.x Between 1 and 5 and a.w = 2", "1", "3",
        "where a.x BETWEEN 1 and 5 and a.w = 3" },
      { "a.x In (1, 2) and a.w = 2", "1", "3", "where a.x IN (1,2) and a.w = 3" },
      { "Exists (select b.k from b) and a.w = 2", "1", "3",
        "where EXISTS ( select b.k from b) and a.w = 3" },
      { "a.y is NULL and a.w = 2", "1", "3", "where a.y IS NULL and a.w = 3" },
      { "a.y Is Not Null and a.w = 2", "1", "3", "where a.y is not null and a.w = 3" },
      { "a.x != 1 and a.w = 2", "1", "3", "where a.x <> 1 and a.w = 3" },
   };

   private RuntimeQueryService rqs;
   private QueryManagerService qms;
   private RuntimeQueryService.RuntimeXQuery runtimeQuery;

   @BeforeEach
   void setUp() {
      rqs = mock(RuntimeQueryService.class);
      qms = new QueryManagerService(rqs, mock(XRepository.class), mock(DataSourceService.class),
                                    mock(SecurityEngine.class), mock(ColumnCache.class));
   }

   static Stream<Arguments> whereCases() {
      return Arrays.stream(HELPERS).flatMap(
         h -> Arrays.stream(WHERE_CASES).map(c -> Arguments.of(h[0], c[0], c[1], c[2], c[3])));
   }

   static Stream<String> helpers() {
      return Arrays.stream(HELPERS).map(h -> h[0]);
   }

   @ParameterizedTest(name = "{0}: {1}")
   @MethodSource("whereCases")
   void editedWhereIsSaved(String helper, String where, String edit, String value,
                           String expected) throws Exception
   {
      typeSql(helper, "SELECT a.x\n  FROM a\n WHERE " + where);
      AdvancedSQLQueryModel model = clientEcho();
      List<Clause> clauses = clauses(model.getConditionPaneModel());
      setValue(clauses, edit, value);

      qms.updateQuery(RID, model, "conditions", false);

      assertWhere(expected, generated());
      assertCanonicalSymbols(clauses);
   }

   // the operator the pane shows for a parsed clause is the canonical one
   @ParameterizedTest
   @MethodSource("helpers")
   void echoedSymbolsAreCanonical(String helper) throws Exception {
      typeSql(helper, "SELECT a.x\n  FROM a\n WHERE a.y is null and a.x Like 'z%' and " +
         "a.x between 1 and 5 and a.x != 1 and a.x In (1, 2)");
      List<Clause> clauses = clauses(clientEcho().getConditionPaneModel());

      assertEquals(List.of("IS NULL", "LIKE", "BETWEEN", "<>", "IN"),
                   clauses.stream().map(c -> c.getOperation().getSymbol()).toList());
   }

   @ParameterizedTest
   @MethodSource("helpers")
   void editedHavingIsSaved(String helper) throws Exception {
      typeSql(helper, "SELECT a.x, max(a.y)\n  FROM a\n GROUP BY a.x\n" +
         " HAVING max(a.y) is null and max(a.z) is not null and max(a.v) != 1 and max(a.w) = 2");
      AdvancedSQLQueryModel model = clientEcho();
      List<Clause> clauses = clauses(model.getGroupingPaneModel().getHavingConditions());
      clauses.get(3).getValue2().setExpression("3");

      qms.updateQuery(RID, model, "grouping", false);

      // the having pane nests the and chain to the right
      String saved = generated();
      assertTrue(saved.endsWith("having max(a.y) IS NULL and (max(a.z) is not null and " +
                                "(max(a.v) <> 1 and max(a.w) = 3))"), saved);
      assertCanonicalSymbols(clauses);
   }

   // control: canonical spellings save the same way (and did before the fix)
   @ParameterizedTest
   @MethodSource("helpers")
   void editedCanonicalHavingIsSaved(String helper) throws Exception {
      typeSql(helper, "SELECT a.x, max(a.y)\n  FROM a\n GROUP BY a.x\n" +
         " HAVING max(a.y) IS NULL and max(a.z) IS NOT NULL and max(a.v) <> 1 and max(a.w) = 2");
      AdvancedSQLQueryModel model = clientEcho();
      clauses(model.getGroupingPaneModel().getHavingConditions()).get(3)
         .getValue2().setExpression("3");

      qms.updateQuery(RID, model, "grouping", false);

      String saved = generated();
      assertTrue(saved.endsWith("having max(a.y) IS NULL and (max(a.z) is not null and " +
                                "(max(a.v) <> 1 and max(a.w) = 3))"), saved);
   }

   // an edit of the lowercase clause itself
   @ParameterizedTest
   @MethodSource("helpers")
   void editOfLowercaseClauseItselfIsSaved(String helper) throws Exception {
      typeSql(helper, "SELECT a.x\n  FROM a\n WHERE a.y is null and a.w = 2");
      AdvancedSQLQueryModel model = clientEcho();
      clauses(model.getConditionPaneModel()).get(0).setNegated(true);

      qms.updateQuery(RID, model, "conditions", false);

      assertWhere("where a.y is not null and a.w = 2", generated());
   }

   // ---- controls ----

   // an unedited save keeps the parsed condition as it was
   @ParameterizedTest
   @MethodSource("helpers")
   void uneditedSaveIsUnchanged(String helper) throws Exception {
      String text = "SELECT a.x\n  FROM a\n WHERE a.x like 'z%' or a.y is null";
      typeSql(helper, text);
      String before = currentSql().getWhere().toString();

      qms.updateQuery(RID, clientEcho(), "conditions", false);
      qms.updateQuery(RID, clientEcho(), null, true);

      assertEquals(before, currentSql().getWhere().toString());
      assertTrue(currentSql().getWhere().toString().contains("is null"),
                 currentSql().getWhere().toString());
   }

   // a two-table != comparison is a join, it keeps its operator
   @ParameterizedTest
   @MethodSource("helpers")
   void joinKeepsItsOperator(String helper) throws Exception {
      typeSql(helper, "SELECT a.x\n  FROM a, b\n WHERE a.id != b.id and a.w = 2");
      AdvancedSQLQueryModel model = clientEcho();
      List<Clause> clauses = clauses(model.getConditionPaneModel());
      clauses.get(clauses.size() - 1).getValue2().setExpression("3");

      qms.updateQuery(RID, model, "conditions", false);

      String saved = generated();
      assertTrue(saved.contains("a.id != b.id"), saved);
      assertTrue(saved.contains("a.w = 3"), saved);
   }

   // a subquery value keeps its own text
   @ParameterizedTest
   @MethodSource("helpers")
   void subqueryTextIsUnchanged(String helper) throws Exception {
      typeSql(helper, "SELECT a.x\n  FROM a\n WHERE a.w = 2 and " +
         "exists (select b.k from b where b.y is null) and " +
         "a.x in (select b.k from b where b.y Like 'q%')");
      AdvancedSQLQueryModel model = clientEcho();
      clauses(model.getConditionPaneModel()).get(0).getValue2().setExpression("3");

      qms.updateQuery(RID, model, "conditions", false);

      String saved = generated();
      assertTrue(saved.contains("a.w = 3"), saved);
      assertTrue(saved.contains("EXISTS ( select b.k from b where b.y is null)"), saved);
      assertTrue(saved.contains("IN ( select b.k from b where b.y Like 'q%')"), saved);
   }

   // a real JDBCDataSource creates its credential through the CredentialService bean; the
   // oracle helper asks the repository for the database version
   @Configuration
   static class Config {
      @Bean
      CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         when(service.createCredential(any(), anyBoolean()))
            .thenAnswer(inv -> new LocalPasswordCredential());
         return service;
      }

      @Bean
      XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   // ---- helpers ----

   private static void assertCanonicalSymbols(List<Clause> clauses) {
      for(Clause clause : clauses) {
         String symbol = clause.getOperation().getSymbol();
         assertEquals(symbol.toUpperCase(), symbol, clause.getValue());
         assertNotEquals("!=", symbol, clause.getValue());
      }
   }

   // the regenerated sql from the outer where on (the oracle helper upper-cases the select
   // list)
   private static void assertWhere(String expected, String saved) {
      String where = saved.substring(saved.indexOf(" from a ") + " from a ".length());
      assertTrue(Arrays.asList(expected.split("\\|\\|")).contains(where), saved);
   }

   // "n" edits value2 of the n-th clause, "n:3" its value3
   private static void setValue(List<Clause> clauses, String edit, String value) {
      String[] parts = edit.split(":");
      Clause clause = clauses.get(Integer.parseInt(parts[0]));
      Consumer<String> setter = parts.length > 1 ? clause.getValue3()::setExpression
         : clause.getValue2()::setExpression;
      setter.accept(value);
   }

   private void typeSql(String helper, String text) throws Exception {
      install(helper);
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
   }

   // the saved sql must be regenerated from the structure
   private String generated() {
      UniformSQL sql = currentSql();
      assertFalse(sql.hasSQLString(), "the typed sql was kept: " + sql.getSQLString());
      sql.setHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, true);

      try {
         String text = sql.getSQLString();
         return text == null ? "" : text.replaceAll("\\s+", " ").trim();
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

   private static List<Clause> clauses(QueryConditionPaneModel pane) {
      return pane.getConditions().stream().filter(c -> c instanceof Clause)
         .map(c -> (Clause) c).toList();
   }

   private static Principal principal() {
      Principal principal = mock(Principal.class);
      when(principal.getName()).thenReturn("admin");
      return principal;
   }

   private void install(String helper) {
      String[] info = Arrays.stream(HELPERS).filter(h -> h[0].equals(helper))
         .findFirst().orElseThrow();
      UniformSQL sql = new UniformSQL();
      JDBCQuery query = new JDBCQuery();
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds");
      ds.setDriver(info[1]);
      ds.setURL(info[2]);
      ds.setRuntimeProductName(info[0]);
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      sql.setDataSource(ds);
      assertEquals(info[3], SQLHelper.getSQLHelper(ds).getClass().getSimpleName(), helper);
      runtimeQuery = new RuntimeQueryService.RuntimeXQuery(query, RID, "ds");
      runtimeQuery.setVariables(new VariableTable());
      when(rqs.getRuntimeQuery(RID)).thenReturn(runtimeQuery);
   }
}
