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

import inetsoft.report.composition.RuntimeWorksheet;
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
import inetsoft.report.composition.execution.AssetQuerySandbox;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.StringReader;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78387, a worksheet sql table saved before 1.2.0 with advanced editing keeps its parsed
 * where tree (#77477), which may hold a truth test, x IS [NOT] TRUE/FALSE/UNKNOWN. The
 * condition pane has no op for the truth value, so opening the edit query dialog failed with
 * "Unsupported operation found". The dialog now shows such a query as sql only, with and
 * without a saved sql string, and a save keeps the saved conditions.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class QueryManagerServiceSavedTruthTestPaneTest {
   private static final String RID = "rq-78387";
   private static final String TABLE = "T";
   private static final String TEXT = "select a.x from a where (a.k = 1) is not true";

   private static final String TABLE_A = """
      <table>
      <alias><![CDATA[a]]></alias>
      <name><![CDATA[a]]></name>
      <issql><![CDATA[false]]></issql>
      <xlocation><![CDATA[-1]]></xlocation>
      <ylocation><![CDATA[-1]]></ylocation>
      <xScroll><![CDATA[0]]></xScroll>
      <yScroll><![CDATA[0]]></yScroll>
      </table>
      """;

   private static final String OPERAND = """
      <XBinaryCondition isnot="false" clause="" containsNull="false">
      <expression1>
      <expression type="Field">
      <![CDATA[a.k]]>
      </expression>
      </expression1>
      <expression2>
      <expression type="Value">
      <![CDATA[1]]>
      </expression>
      </expression2>
      <op><![CDATA[=]]></op>
      </XBinaryCondition>
      """;

   // select a.x from a where (a.k = 1) is not true, written by the parser before the
   // refusal (#77735), without its sql string (same as XUnaryConditionTruthTestXmlTest)
   private static final String SAVED_IS_NOT_TRUE = """
      <uniform_sql parse="true">
      """ + TABLE_A + """
      <column>
      <![CDATA[a.x]]>
      <alias><![CDATA[]]></alias>
      <type><![CDATA[]]></type>
      <table><![CDATA[]]></table>
      <description><![CDATA[]]></description>
      <isExp><![CDATA[false]]></isExp>
      </column>
      <where>
      <XSet relation="and" isnot="false" group="false" clause="where">
      <XSet relation="and" isnot="false" group="false" clause="">
      <XSet relation="and" isnot="false" group="false" clause="">
      <XSet relation="is not" isnot="false" group="false" clause="where">
      """ + OPERAND + """
      <XUnaryCondition isnot="false" clause= "">
      <expression1>
      <expression type="Expression">
      <![CDATA[true]]>
      </expression>
      </expression1>
      <op><![CDATA[]]></op>
      </XUnaryCondition>
      </XSet>
      </XSet>
      <XSet relation="or" isnot="false" group="false" clause="">
      </XSet>
      </XSet>
      </XSet>
      </where>
      <sortby></sortby>
      <groupby></groupby>
      <having>
      </having>
      </uniform_sql>
      """;

   private static final String SQL_STRING =
      "<sqlstring parseResult=\"0\"><![CDATA[" + TEXT + "]]></sqlstring>\n</uniform_sql>";

   private RuntimeQueryService rqs;
   private SecurityEngine security;
   private XRepository repository;
   private QueryManagerService qms;
   private RuntimeQueryService.RuntimeXQuery runtimeQuery;
   private final Principal principal = mock(Principal.class);
   private JDBCDataSource ds;

   @BeforeEach
   void setUp() throws Exception {
      // a known product name keeps SQLHelper.getSQLHelper() from looking it up through Config
      ds = mock(JDBCDataSource.class);
      when(ds.getFullName()).thenReturn("ds");
      when(ds.getRuntimeProductName()).thenReturn("hsql");
      rqs = mock(RuntimeQueryService.class);
      security = mock(SecurityEngine.class);
      repository = mock(XRepository.class);
      when(repository.getDataSourceFullNames()).thenReturn(new String[0]);
      when(security.checkPermission(any(), any(ResourceType.class), any(String.class),
                                    any(ResourceAction.class))).thenReturn(true);
      qms = new QueryManagerService(rqs, repository, mock(DataSourceService.class), security,
                                    mock(ColumnCache.class));
      when(rqs.createRuntimeQuery(any(), any(), any(), any())).thenAnswer(inv -> {
         JDBCQuery query = inv.<JDBCQuery>getArgument(1).clone();
         // a mock doesn't clone
         query.setDataSource(ds);
         runtimeQuery = new RuntimeQueryService.RuntimeXQuery(query, RID, "ds");
         return runtimeQuery;
      });
      when(rqs.getRuntimeQuery(RID)).thenAnswer(inv -> runtimeQuery);
   }

   // a table saved with its sql string: parse success with the truth test in the tree, lossy
   // by re-parse (the reported case)
   @Test
   void dialogOpensSavedTruthTestWithSqlString() throws Exception {
      AdvancedSQLQueryModel model = openDialog(SAVED_IS_NOT_TRUE.replace("</uniform_sql>",
                                                                        SQL_STRING));
      FreeFormSQLPaneModel sqlPane = model.getFreeFormSQLPaneModel();

      assertEquals(TEXT, sqlPane.getSqlString());
      assertSqlOnly(sqlPane);
      assertEquals(UniformSQL.PARSE_SUCCESS, sqlPane.getParseResult());
      assertTrue(model.getConditionPaneModel().getConditions().isEmpty());

      qms.updateQuery(RID, model, null, true);

      UniformSQL sql = runtimeSql();
      assertEquals(TEXT, sql.getSQLString());
      assertTrue(hasTruthTest(sql.getWhere()), String.valueOf(sql.getWhere()));
   }

   // a table saved without its sql string (a structure edit before 1.2.0 cleared it): shown
   // as sql only with the generated sql, and a save keeps the where tree
   @Test
   void dialogOpensSavedTruthTestWithoutSqlString() throws Exception {
      AdvancedSQLQueryModel model = openDialog(SAVED_IS_NOT_TRUE);
      FreeFormSQLPaneModel sqlPane = model.getFreeFormSQLPaneModel();

      assertEquals(normalize(sqlPane.getGeneratedSqlString()),
                   normalize(sqlPane.getSqlString()));
      assertTrue(normalize(sqlPane.getSqlString()).contains("(a.k = 1) is not true"),
                 sqlPane.getSqlString());
      assertSqlOnly(sqlPane);
      assertTrue(model.getConditionPaneModel().getConditions().isEmpty());

      // the save posts the empty condition pane back, it must not replace the where tree
      qms.updateQuery(RID, model, null, true);
      UniformSQL sql = runtimeSql();
      assertFalse(sql.hasSQLString());
      assertTrue(hasTruthTest(sql.getWhere()), String.valueOf(sql.getWhere()));
      assertTrue(normalize(sql.getSQLString()).contains("(a.k = 1) is not true"),
                 sql.getSQLString());

      // a tab-leave of the conditions tab, too
      qms.updateQuery(RID, model, QueryManagerService.DatabaseQueryTabs.CONDITIONS.getTab(),
                      false);
      assertTrue(hasTruthTest(runtimeSql().getWhere()));
   }

   // a truth test in a having tree
   @Test
   void editorLoadsSavedHavingTruthTest() throws Exception {
      UniformSQL sql = load(SAVED_IS_NOT_TRUE);
      sql.setHaving(sql.getWhere());
      sql.setWhere(null);
      install(sql);

      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, null);
      assertSqlOnly(model.getFreeFormSQLPaneModel());
      assertTrue(model.getGroupingPaneModel().getHavingConditions().getConditions().isEmpty());

      qms.updateQuery(RID, model, null, true);
      assertTrue(hasTruthTest(sql.getHaving()), String.valueOf(sql.getHaving()));
   }

   // an ON-clause truth test is kept in the where tree, around a join-shaped comparison that
   // getJoins() doesn't return (#77618), so the links pane doesn't see it
   @Test
   void editorLoadsSavedJoinTruthTest() throws Exception {
      String xml = SAVED_IS_NOT_TRUE
         .replace(TABLE_A, TABLE_A + TABLE_A.replace("[a]", "[b]"))
         .replace(OPERAND, OPERAND.replace("XBinaryCondition", "XJoin")
            .replace("[a.k]", "[a.id]").replace("\"Value\"", "\"Field\"")
            .replace("[1]", "[b.k]"));
      UniformSQL sql = load(xml);
      assertEquals(0, sql.getJoins().length);
      install(sql);

      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, null);
      assertSqlOnly(model.getFreeFormSQLPaneModel());
      assertTrue(normalize(model.getFreeFormSQLPaneModel().getSqlString())
                    .contains("(a.id = b.k) is not true"),
                 model.getFreeFormSQLPaneModel().getSqlString());
      assertEquals(2, model.getLinkPaneModel().getTables().size());

      qms.updateQuery(RID, model, null, true);
      assertTrue(hasTruthTest(sql.getWhere()), String.valueOf(sql.getWhere()));
   }

   // control: a saved tree the pane can show is still edited in the panes
   @Test
   void savedPlainTreeStillEditable() throws Exception {
      UniformSQL sql = load(SAVED_IS_NOT_TRUE.replaceAll(
         "(?s)<XSet relation=\"is not\".*?</XUnaryCondition>\\s*</XSet>", OPERAND));
      assertFalse(hasTruthTest(sql.getWhere()));
      install(sql);

      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, null);
      FreeFormSQLPaneModel sqlPane = model.getFreeFormSQLPaneModel();
      assertFalse(sqlPane.isHasSqlString());
      assertFalse(sqlPane.isLossy());
      assertEquals(1, model.getConditionPaneModel().getConditions().size());

      // an emptied condition pane is applied
      model.getConditionPaneModel().getConditions().clear();
      qms.updateQuery(RID, model, QueryManagerService.DatabaseQueryTabs.CONDITIONS.getTab(),
                      false);
      assertFalse(normalize(sql.getSQLString()).contains("a.k = 1"), sql.getSQLString());
   }

   private AdvancedSQLQueryModel openDialog(String xml) throws Exception {
      JDBCQuery query = new JDBCQuery();
      query.setName(TABLE);
      query.setDataSource(dataSource());
      query.setSQLDefinition(load(xml));

      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly assembly = new SQLBoundTableAssembly(ws, TABLE);
      ((SQLBoundTableAssemblyInfo) assembly.getTableInfo()).setQuery(query);
      assembly.setAdvancedEditing(true);
      ws.addAssembly(assembly);

      RuntimeWorksheet rws = mock(RuntimeWorksheet.class);
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      when(rws.getWorksheet()).thenReturn(ws);
      when(rws.getAssetQuerySandbox()).thenReturn(box);
      when(box.getVariableTable()).thenReturn(new VariableTable());

      SQLQueryDialogModel dialog = qms.getSqlQueryDialogModel(rws, TABLE, "ds", principal);
      assertTrue(dialog.isAdvancedEdit());
      assertNotNull(dialog.getAdvancedModel());
      return dialog.getAdvancedModel();
   }

   private void install(UniformSQL sql) {
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(dataSource());
      query.setSQLDefinition(sql);
      runtimeQuery = new RuntimeQueryService.RuntimeXQuery(query, RID, "ds");
   }

   private UniformSQL runtimeSql() {
      return (UniformSQL) runtimeQuery.getQuery().getSQLDefinition();
   }

   // what database-query.component isSqlOnly() reads to disable the structured tabs
   private static void assertSqlOnly(FreeFormSQLPaneModel sqlPane) {
      assertTrue(sqlPane.isHasSqlString());
      assertTrue(sqlPane.isLossy());
   }

   private JDBCDataSource dataSource() {
      return ds;
   }

   private static UniformSQL load(String xml) throws Exception {
      Element root = Tool.parseXML(new StringReader(xml)).getDocumentElement();
      UniformSQL sql = new UniformSQL();
      sql.parseXML(root);
      return sql;
   }

   private static String normalize(String text) {
      return text == null ? "" : text.replaceAll("\\s+", " ").trim();
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
