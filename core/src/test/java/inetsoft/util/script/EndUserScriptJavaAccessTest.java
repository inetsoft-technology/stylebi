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
package inetsoft.util.script;

import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.filter.*;
import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.report.script.viewsheet.ViewsheetScopeTest;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.DynamicParameterValue;
import inetsoft.sree.RepletRequest;
import inetsoft.sree.schedule.ScheduleParameterScope;
import inetsoft.sree.security.DestinationUserNameProviderPrincipal;
import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.script.VpmScope;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.composer.model.vs.DynamicValueModel;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77396: calculated fields, worksheet expression columns, viewsheet scripts and
 * schedule parameters are written by end users, so they must not reach com.* / org.*
 * classes (for example a reflection utility that reads hidden getters off
 * parameter.__principal__), by Java.type or by the package form. The scripts run
 * through the production calc field, worksheet query and viewsheet scope paths.
 * Basic java.lang / java.math classes stay usable, dangerous java.lang classes stay
 * denied, and an admin VPM script keeps com/org.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "/inetsoft/report/script/viewsheet/ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
class EndUserScriptJavaAccessTest {
   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   @BeforeEach
   void setUp() {
      FormulaContext.setRestricted(false);
      principal = new DestinationUserNameProviderPrincipal(
         new ClientInfo(new IdentityID("alice", "orgA"), "10.0.0.1"),
         new IdentityID[] { new IdentityID("Everyone", "orgA") },
         new String[] { "readers" }, "orgA", SECURE_ID, "Alice A");
   }

   @AfterEach
   void tearDown() {
      FormulaContext.setRestricted(false);
   }

   @ParameterizedTest
   @ValueSource(strings = { TYPE_FORM, PACKAGE_FORM, SECURE_ID_PROBE })
   void calcFieldCannotReachComOrgClass(String script) {
      assertEquals(BLOCKED, createCalcField(probe(script)).getResult());
      assertFalse(FormulaContext.isRestricted());
   }

   @ParameterizedTest
   @ValueSource(strings = { TYPE_FORM, PACKAGE_FORM, SECURE_ID_PROBE })
   void expressionColumnCannotReachComOrgClass(String script) throws Exception {
      TableLens lens = runExpressionColumn(probe(script));

      assertEquals(BLOCKED, lens.getObject(1, 2));
      assertEquals(BLOCKED, lens.getObject(2, 2));
      assertFalse(FormulaContext.isRestricted());
   }

   @ParameterizedTest
   @ValueSource(strings = { TYPE_FORM, PACKAGE_FORM })
   void viewsheetScriptCannotReachComOrgClass(String script) throws Exception {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox().orElseThrow();
      ViewsheetScope scope = new ViewsheetScope(sandbox, false);

      assertEquals(BLOCKED, scope.execute(
         probe(script), scope.getVSAScriptable(ViewsheetScope.VIEWSHEET_SCRIPTABLE), false));
      assertFalse(FormulaContext.isRestricted());
   }

   @ParameterizedTest
   @ValueSource(strings = { TYPE_FORM, PACKAGE_FORM })
   void scheduleParameterCannotReachComOrgClass(String script) {
      DynamicParameterValue param = new DynamicParameterValue(
         "=" + probe(script), DynamicValueModel.EXPRESSION, "string");
      ScheduleParameterScope scope = new ScheduleParameterScope();
      scope.getScriptEnv().addTopLevelParentScope(scope);

      assertEquals(BLOCKED, RepletRequest.executeParameter(param, scope));
      assertFalse(FormulaContext.isRestricted());
   }

   // basic java.lang / java.math classes stay usable in a restricted calc field
   @ParameterizedTest
   @ValueSource(strings = {
      "Java.type('java.lang.Integer').parseInt('5')",
      "java.lang.Integer.parseInt('5')",
      "Java.type('java.lang.Math').round(1.5) + 3",
      "java.lang.Math.round(1.5) + 3",
      "new (Java.type('java.math.BigDecimal'))('1.5').doubleValue() + 3.5",
      "new java.math.BigDecimal('1.5').doubleValue() + 3.5"
   })
   void calcFieldKeepsBasicJavaClasses(String script) {
      assertEquals(5.0, createCalcField(script).getResult());
   }

   // the dangerous java.lang classes stay denied in a restricted calc field
   @ParameterizedTest
   @ValueSource(strings = {
      "java.lang.System", "java.lang.Runtime", "java.lang.Class", "java.lang.ClassLoader",
      "java.lang.Thread", "java.lang.ProcessBuilder", "java.lang.reflect.Method",
      "java.lang.invoke.MethodHandles"
   })
   void calcFieldCannotReachDangerousJavaClass(String className) {
      assertEquals(BLOCKED, createCalcField(probe("Java.type('" + className + "')")).getResult());
      assertEquals(BLOCKED, createCalcField(probe(className)).getResult());
   }

   @Test
   void calcFieldStillEvaluatesNormalScript() {
      CalcFieldFormula formula = createCalcField(
         "var d = new Date(2020, 0, 15); " +
         "total * 2 + Math.max(1, 3) + CALC.abs(-1) + d.getMonth() + parameter.p1");

      // 6 * 2 + 3 + 1 + 0 + 4
      assertEquals(20.0, formula.getResult());
      assertFalse(FormulaContext.isRestricted());
   }

   @Test
   void expressionColumnStillEvaluatesNormalScript() throws Exception {
      TableLens lens = runExpressionColumn(
         "field['name'].toUpperCase() + '-' + (field['qty'] * 2) + '-' + " +
         "CALC.round(Math.PI, 2) + '-' + parameter.p1 + '-' + new Date(2020, 0, 15).getDate()");

      assertEquals("A-6-3.14-4-15", lens.getObject(1, 2));
      assertEquals("B-10-3.14-4-15", lens.getObject(2, 2));
      assertFalse(FormulaContext.isRestricted());
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "Java.type('org.apache.commons.lang3.StringUtils').length('abcd')",
      "org.apache.commons.lang3.StringUtils.length('abcd')"
   })
   void vpmScriptKeepsComOrgClass(String script) throws Exception {
      assertEquals(4, ((Number) VpmScope.execute(script, new VpmScope())).intValue());
   }

   // returns BLOCKED when the script fails, otherwise what it returned
   private static String probe(String script) {
      return "(function() { try { return 'found: ' + (" + script + "); } " +
         "catch(e) { return '" + BLOCKED + "'; } })()";
   }

   // calc field evaluated in the asset query scope, as aggregate calc fields are
   private CalcFieldFormula createCalcField(String expression) {
      AssetQuerySandbox box = createSandbox(new Worksheet());
      CalcFieldFormula formula = new CalcFieldFormula(
         expression, new String[] { "total" }, new Formula[] { new SumFormula() },
         new int[1], box.getScriptEnv(), box.getScope());
      formula.addValue(6.0);

      return formula;
   }

   // expression column on an embedded worksheet table, run by the asset query
   private TableLens runExpressionColumn(String expression) throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "T1");
      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.INTEGER },
         new Object[][] { { "name", "qty" }, { "a", 3 }, { "b", 5 } }));
      ws.addAssembly(table);

      ExpressionRef exp = new ExpressionRef(null, "exp");
      exp.setExpression(expression);
      ColumnSelection columns = table.getColumnSelection();
      columns.addAttribute(new ColumnRef(exp));
      table.setColumnSelection(columns);

      AssetQuerySandbox box = createSandbox(ws);
      TableLens lens = box.getTableLens("T1", AssetQuerySandbox.RUNTIME_MODE);
      lens.moreRows(Integer.MAX_VALUE);

      return lens;
   }

   private AssetQuerySandbox createSandbox(Worksheet ws) {
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      box.getVariableTable().put("__principal__", principal);
      box.getVariableTable().put("p1", 4);

      return box;
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ViewsheetScopeTest.ASSET_ID);
      event.setViewer(true);
      return event;
   }

   private static final String BLOCKED = "blocked";
   private static final String TYPE_FORM =
      "Java.type('org.apache.commons.lang3.StringUtils').length('abc')";
   private static final String PACKAGE_FORM =
      "org.apache.commons.lang3.StringUtils.length('abc')";
   private static final String SECURE_ID_PROBE =
      "Java.type('org.apache.commons.lang3.reflect.MethodUtils')" +
      ".invokeMethod(parameter.__principal__, 'getSecureID')";
   private static final long SECURE_ID = 987654321L;
   private DestinationUserNameProviderPrincipal principal;
}
