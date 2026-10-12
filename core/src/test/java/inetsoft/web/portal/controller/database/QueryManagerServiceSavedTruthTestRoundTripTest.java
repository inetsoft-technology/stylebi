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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.report.composition.RuntimeWorksheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.util.ColumnCache;
import inetsoft.util.Tool;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.portal.model.database.AdvancedSQLQueryModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78387, the edit query dialog round trip of the reported pre-1.2.0 saved table
 * (advanced editing, quoted identifiers, a where truth test), with and without its saved sql
 * string: open as sql only, post the model back as the editor does (JSON), then OK without
 * a change (updateQuery) and OK after a sql text edit (setFreeFormSQLPaneModel). The where
 * condition must be kept and the query must keep the same sql. A convertible saved query
 * with its sql string still fills the condition pane.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class QueryManagerServiceSavedTruthTestRoundTripTest {
   private static final String RID = "rq-78387-rt";
   private static final String REPORTED =
      "select o.\"ORDER_ID\", o.\"QUANTITY\" from \"ORDER_DETAILS\" o " +
      "where (o.\"QUANTITY\" > 1) is not true";
   private static final String BASE =
      "select o.\"ORDER_ID\", o.\"QUANTITY\" from \"ORDER_DETAILS\" o where o.\"QUANTITY\" > 1";
   private static final String TRUTH = "<XUnaryCondition isnot=\"false\" clause=\"\">" +
      "<expression1><expression type=\"Expression\"><![CDATA[true]]></expression>" +
      "</expression1><op><![CDATA[]]></op></XUnaryCondition>";

   private RuntimeQueryService rqs;
   private QueryManagerService qms;
   private RuntimeQueryService.RuntimeXQuery runtimeQuery;
   private final Principal principal = mock(Principal.class);
   private JDBCDataSource ds;
   private final ObjectMapper mapper = new ObjectMapper();

   @BeforeEach
   void setUp() throws Exception {
      ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn("ds");
      when(ds.getRuntimeProductName()).thenReturn("postgresql");
      when(principal.getName()).thenReturn("admin");
      rqs = mock(RuntimeQueryService.class);
      SecurityEngine security = mock(SecurityEngine.class);
      XRepository repository = mock(XRepository.class);
      when(repository.getDataSourceFullNames()).thenReturn(new String[0]);
      when(security.checkPermission(any(), any(ResourceType.class), any(String.class),
                                    any(ResourceAction.class))).thenReturn(true);
      qms = new QueryManagerService(rqs, repository, mock(DataSourceService.class), security,
                                    mock(ColumnCache.class));
      when(rqs.createRuntimeQuery(any(), any(), any(), any())).thenAnswer(inv -> {
         JDBCQuery query = inv.<JDBCQuery>getArgument(1).clone();
         query.setDataSource(ds);
         runtimeQuery = new RuntimeQueryService.RuntimeXQuery(query, RID, "ds");
         return runtimeQuery;
      });
      when(rqs.getRuntimeQuery(RID)).thenAnswer(inv -> runtimeQuery);
   }

   // the pre-1.2.0 saved XML: real parser output of the base query, with the where comparison
   // wrapped in the truth-test set the old parser wrote, and the reported sql string
   private String savedXml(boolean withString) throws Exception {
      UniformSQL base = new UniformSQL();

      synchronized(base) {
         base.setSQLString(BASE);
         base.wait(20000);
      }

      assertEquals(UniformSQL.PARSE_SUCCESS, base.getParseResult());
      StringWriter sw = new StringWriter();
      base.writeXML(new PrintWriter(sw));
      String xml = sw.toString();
      xml = xml.replaceFirst("(?s)(<XBinaryCondition.*?</XBinaryCondition>)",
         "<XSet relation=\"is not\" isnot=\"false\" group=\"false\" clause=\"where\">$1" +
         java.util.regex.Matcher.quoteReplacement(TRUTH) + "</XSet>");
      assertTrue(xml.contains("relation=\"is not\""), xml);
      assertTrue(xml.contains(BASE), xml);
      xml = xml.replace(BASE, REPORTED);

      if(!withString) {
         xml = xml.replaceAll("(?s)<sqlstring.*?</sqlstring>", "");
      }

      return xml;
   }

   @Test
   void reportedWithString() throws Exception {
      run(true);
   }

   @Test
   void reportedWithoutString() throws Exception {
      run(false);
   }

   private void run(boolean withString) throws Exception {
      String xml = savedXml(withString);
      UniformSQL loaded = load(xml);
      assertTrue(hasTruthTest(loaded.getWhere()), String.valueOf(loaded.getWhere()));
      String loadedSql = loaded.getSQLString();

      // open
      AdvancedSQLQueryModel model = openDialog(loaded);
      FreeFormSQLPaneModel pane = model.getFreeFormSQLPaneModel();
      assertTrue(pane.isHasSqlString());
      assertTrue(pane.isLossy());
      assertTrue(model.getConditionPaneModel().getConditions().isEmpty());
      assertTrue(norm(pane.getGeneratedSqlString()).contains("is not true"),
                 pane.getGeneratedSqlString());

      if(withString) {
         assertEquals(REPORTED, pane.getSqlString());
      }
      else {
         assertEquals(norm(pane.getGeneratedSqlString()), norm(pane.getSqlString()));
      }

      // the FE posts the model back as JSON
      AdvancedSQLQueryModel posted = mapper.readValue(mapper.writeValueAsString(model),
                                                      AdvancedSQLQueryModel.class);
      assertFalse(posted.isSqlEdited());

      // unchanged OK: setUpAdvancedQueryModel -> updateQuery(all)
      qms.updateQuery(RID, posted, null, true);
      JDBCQuery saved = runtimeQuery.getQuery().clone(); // info.setQuery(query.clone())
      UniformSQL ssql = (UniformSQL) saved.getSQLDefinition();
      assertTrue(hasTruthTest(ssql.getWhere()));
      assertEquals(withString, ssql.hasSQLString());
      assertEquals(norm(loadedSql), norm(ssql.getSQLString()));

      // reopen after save still opens
      AdvancedSQLQueryModel reopened = qms.getAdvancedQueryModel(runtimeQuery, null);
      assertTrue(reopened.getFreeFormSQLPaneModel().isLossy());

      // SQL text edit only: setUpAdvancedQueryModel -> setFreeFormSQLPaneModel
      String edited = norm(posted.getFreeFormSQLPaneModel().getSqlString())
         .replace("o.\"ORDER_ID\", ", "");
      assertNotEquals(norm(posted.getFreeFormSQLPaneModel().getSqlString()), edited);
      posted.setSqlEdited(true);
      posted.getFreeFormSQLPaneModel().setSqlString(edited);
      UpdateFreeFormSQLPaneEvent event = new UpdateFreeFormSQLPaneEvent();
      event.setRuntimeId(RID);
      event.setFreeFormSqlPaneModel(posted.getFreeFormSQLPaneModel());
      qms.setFreeFormSQLPaneModel(event, principal);
      UniformSQL esql = (UniformSQL) runtimeQuery.getQuery().clone().getSQLDefinition();
      assertEquals(edited, esql.getSQLString());
      assertTrue(norm(esql.getSQLString()).contains("is not true"));
      AdvancedSQLQueryModel afterEdit = qms.getAdvancedQueryModel(runtimeQuery, null);
      assertEquals(edited, afterEdit.getFreeFormSQLPaneModel().getSqlString());
   }

   // control: a convertible saved advanced query keeps its condition pane
   @Test
   void convertibleSavedQueryStillPopulatesPane() throws Exception {
      String xml = savedXml(true).replace(REPORTED, BASE)
         .replaceFirst("(?s)<XSet relation=\"is not\"[^>]*>" +
                       "(<XBinaryCondition.*?</XBinaryCondition>).*?</XUnaryCondition></XSet>",
                       "$1");
      UniformSQL loaded = load(xml);
      assertFalse(hasTruthTest(loaded.getWhere()));
      AdvancedSQLQueryModel model = openDialog(loaded);
      FreeFormSQLPaneModel pane = model.getFreeFormSQLPaneModel();
      assertFalse(pane.isLossy());
      assertEquals(1, model.getConditionPaneModel().getConditions().size());
      qms.updateQuery(RID, mapper.readValue(mapper.writeValueAsString(model),
                                            AdvancedSQLQueryModel.class), null, true);
      UniformSQL ssql = (UniformSQL) runtimeQuery.getQuery().getSQLDefinition();
      assertEquals(BASE, ssql.getSQLString());
   }

   private AdvancedSQLQueryModel openDialog(UniformSQL sql) throws Exception {
      JDBCQuery query = new JDBCQuery();
      query.setName("T");
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly assembly = new SQLBoundTableAssembly(ws, "T");
      ((SQLBoundTableAssemblyInfo) assembly.getTableInfo()).setQuery(query);
      assembly.setAdvancedEditing(true);
      ws.addAssembly(assembly);
      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getVariableTable()).thenReturn(new VariableTable());
      SQLQueryDialogModel dialog = qms.getSqlQueryDialogModel(rws, "T", "ds", principal);
      assertTrue(dialog.isAdvancedEdit());
      return dialog.getAdvancedModel();
   }

   private static UniformSQL load(String xml) throws Exception {
      Element root = Tool.parseXML(new StringReader(xml)).getDocumentElement();
      UniformSQL sql = new UniformSQL();
      sql.parseXML(root);
      return sql;
   }

   private static String norm(String s) {
      return s == null ? "" : s.replaceAll("\\s+", " ").trim();
   }

   private static boolean hasTruthTest(XNode node) {
      if(node instanceof XSet set && SQLHelper.isTruthTest(set)) {
         return true;
      }

      for(int i = 0; node != null && i < node.getChildCount(); i++) {
         if(hasTruthTest(node.getChild(i))) {
            return true;
         }
      }

      return false;
   }
}
