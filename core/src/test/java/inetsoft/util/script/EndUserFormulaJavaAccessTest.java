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
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.PreAssetQuery;
import inetsoft.report.filter.CalcFieldFormula;
import inetsoft.report.filter.Formula;
import inetsoft.report.filter.SumFormula;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.RepletRequest;
import inetsoft.sree.DynamicParameterValue;
import inetsoft.sree.schedule.ScheduleParameterScope;
import inetsoft.sree.security.DestinationUserNameProviderPrincipal;
import inetsoft.sree.security.IdentityID;
import inetsoft.test.SreeHome;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.ExpressionRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.script.VpmScope;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.admin.schedule.ScheduleTaskFormulaService;
import inetsoft.web.composer.model.vs.DynamicValueModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77396, a calc field or a worksheet expression column must not be able to
 * call com.* / org.* classes (for example a reflection utility that reads hidden
 * getters off the session principal). Scripts run through the production calc
 * field and worksheet query paths with a real parameter.__principal__.
 */
@SreeHome
class EndUserFormulaJavaAccessTest {
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

   @Test
   void calcFieldCannotCallOrgClass() {
      CalcFieldFormula formula = createCalcField(
         "org.apache.commons.lang3.StringUtils.length('abc') + total");

      assertThrows(ScriptException.class, formula::getResult);
      assertFalse(FormulaContext.isRestricted());
   }

   @Test
   void calcFieldCannotReadPrincipalSecureId() {
      CalcFieldFormula formula = createCalcField(
         "Number(org.apache.commons.lang3.reflect.MethodUtils.invokeMethod(" +
         "parameter.__principal__, 'getSecureID'))");

      assertThrows(ScriptException.class, formula::getResult);
   }

   @Test
   void calcFieldStillEvaluatesNormalScript() {
      CalcFieldFormula formula = createCalcField(
         "var d = new Date(2020, 0, 15); " +
         "total * 2 + Math.max(1, 3) + CALC.abs(-1) + d.getMonth() + " +
         "new java.util.ArrayList().size() + " +
         "parseFloat(new java.text.DecimalFormat('0.0').format(0.5)) + parameter.p1");

      // 6 * 2 + 3 + 1 + 0 + 0 + 0.5 + 4
      assertEquals(20.5, formula.getResult());
      assertFalse(FormulaContext.isRestricted());
   }

   @Test
   void expressionColumnCannotCallOrgClass() throws Exception {
      TableLens lens = runExpressionColumn(
         "org.apache.commons.lang3.StringUtils.isEmpty('') ? 'leak' : 'none'");

      assertNotEquals("leak", lens.getObject(1, 2));
      assertFalse(FormulaContext.isRestricted());
   }

   @Test
   void expressionColumnCannotReadPrincipalSecureId() throws Exception {
      TableLens lens = runExpressionColumn(
         "'' + org.apache.commons.lang3.reflect.MethodUtils.invokeMethod(" +
         "parameter.__principal__, 'getSecureID')");

      assertNotEquals(String.valueOf(SECURE_ID), lens.getObject(1, 2));
   }

   @Test
   void expressionColumnStillEvaluatesNormalScript() throws Exception {
      TableLens lens = runExpressionColumn(
         "field['name'].toUpperCase() + '-' + (field['qty'] * 2) + '-' + " +
         "CALC.round(Math.PI, 2) + '-' + parameter.p1");

      assertEquals("A-6-3.14-4", lens.getObject(1, 2));
      assertEquals("B-10-3.14-4", lens.getObject(2, 2));
      assertFalse(FormulaContext.isRestricted());
   }

   @Test
   void formulasCanUseJavaLangAndJavaMath() throws Exception {
      String exp = "java.lang.Integer.parseInt('10') + java.lang.Math.round(2.6) + " +
         "new java.math.BigDecimal('0.5').doubleValue()";

      // 10 + 3 + 0.5, plus the aggregate in the calc field
      assertEquals(19.5, createCalcField(exp + " + total").getResult());
      assertEquals(13.5, ((Number) runCondition(exp)).doubleValue());
      assertEquals("13.5", runExpressionColumn("'' + (" + exp + ")").getObject(1, 2));
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "java.lang.System.getProperty('user.dir')",
      "java.lang.Runtime.getRuntime().availableProcessors()",
      "java.lang.Class.forName('java.lang.String').getName()",
      "java.lang.ClassLoader.getSystemClassLoader().toString()",
      "java.lang.Thread.currentThread().getName()",
      "new java.lang.ProcessBuilder('true').toString()",
      "java.lang.reflect.Array.newInstance(java.lang.Integer, 1).length",
      "java.lang.invoke.MethodHandles.lookup().toString()"
   })
   void formulasCannotUseDangerousJavaLangClasses(String exp) throws Exception {
      String script = "'' + " + exp;

      assertThrows(ScriptException.class, () -> createCalcField(script).getResult());
      assertThrows(Exception.class, () -> runCondition(script));
      // a failed expression column cell shows the error marker
      assertEquals("XXX", runExpressionColumn(script).getObject(1, 2));
      assertFalse(FormulaContext.isRestricted());
   }

   @Test
   void scheduleParameterExpressionCannotCallOrgClass() {
      ScheduleParameterScope scope = new ScheduleParameterScope();
      scope.getScriptEnv().addTopLevelParentScope(scope);

      assertThrows(ScriptException.class, () -> RepletRequest.executeParameter(
         new DynamicParameterValue("=org.apache.commons.lang3.StringUtils.length('abc')",
                                   DynamicValueModel.EXPRESSION, XSchema.INTEGER), scope));
      assertEquals(13, RepletRequest.executeParameter(
         new DynamicParameterValue("=java.lang.Integer.parseInt('10') + 3",
                                   DynamicValueModel.EXPRESSION, XSchema.INTEGER), scope));
      assertFalse(FormulaContext.isRestricted());
   }

   @Test
   void scheduleParameterTestScriptCannotCallOrgClass() {
      ScheduleTaskFormulaService service = new ScheduleTaskFormulaService();

      assertNotNull(service.testScheduleParameterExpression(
         "org.apache.commons.lang3.StringUtils.length('abc')"));
      assertNull(service.testScheduleParameterExpression("java.lang.Math.round(2.6) + 1"));
      assertFalse(FormulaContext.isRestricted());
   }

   @Test
   void vpmScriptKeepsOrgClassAccess() throws Exception {
      VpmScope scope = new VpmScope();

      Object result = VpmScope.execute(
         "org.apache.commons.lang3.StringUtils.length('abcd')", scope);

      assertEquals(4, ((Number) result).intValue());
   }

   // calc field evaluated in the asset query scope, as aggregate calc fields are
   private CalcFieldFormula createCalcField(String expression) {
      AssetQuerySandbox box = createSandbox(new Worksheet());
      CalcFieldFormula formula = new CalcFieldFormula(
         expression, new String[] { "total" }, new Formula[] { new SumFormula() },
         new int[1], box.getScriptEnv(), box.getScope());
      formula.addValue(6);

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

   // condition value expression, as a worksheet condition runs it
   private Object runCondition(String expression) {
      Condition cond = new Condition();
      cond.setType(XSchema.DOUBLE);

      return PreAssetQuery.execScriptExpression(
         expression, cond, new VariableTable(), createSandbox(new Worksheet()));
   }

   private AssetQuerySandbox createSandbox(Worksheet ws) {
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      box.getVariableTable().put("__principal__", principal);
      box.getVariableTable().put("p1", 4);

      return box;
   }

   private static final long SECURE_ID = 987654321L;
   private DestinationUserNameProviderPrincipal principal;
}
