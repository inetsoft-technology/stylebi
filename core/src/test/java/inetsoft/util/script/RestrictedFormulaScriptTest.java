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

import inetsoft.report.FormulaTable;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.PreAssetQuery;
import inetsoft.report.filter.CalcFieldFormula;
import inetsoft.report.filter.ConditionGroup;
import inetsoft.report.filter.Formula;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.lens.CalcTableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.sree.DynamicParameterValue;
import inetsoft.sree.RepletRequest;
import inetsoft.sree.schedule.ScheduleParameterScope;
import inetsoft.test.SreeHome;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.script.VpmScope;
import inetsoft.web.admin.schedule.ScheduleTaskFormulaService;
import inetsoft.web.composer.model.vs.DynamicValueModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77396, end-user formula scripts (calc fields, condition expressions and
 * expression columns) must run in restricted mode, so com.* and org.* classes
 * can't be reached from them. Admin scripts (VPM) stay unrestricted.
 */
@SreeHome
class RestrictedFormulaScriptTest {
   @BeforeEach
   void setUp() {
      FormulaContext.setRestricted(false);
   }

   @AfterEach
   void tearDown() {
      FormulaContext.setRestricted(false);
   }

   @Test
   void runRestrictedRestoresFalse() throws Exception {
      assertTrue(FormulaContext.runRestricted(FormulaContext::isRestricted));
      assertFalse(FormulaContext.isRestricted());
   }

   @Test
   void runRestrictedKeepsEnclosingRestriction() throws Exception {
      FormulaContext.setRestricted(true);
      boolean inner = FormulaContext.runRestricted(
         () -> FormulaContext.runRestricted(FormulaContext::isRestricted));

      assertTrue(inner);
      assertTrue(FormulaContext.isRestricted());
   }

   @Test
   void runRestrictedRestoresOnException() {
      assertThrows(IllegalStateException.class, () -> FormulaContext.runRestricted(() -> {
         throw new IllegalStateException("fail");
      }));
      assertFalse(FormulaContext.isRestricted());
   }

   @Test
   void unrestrictedScriptReachesOrgClass() throws Exception {
      // control: shows the class is reachable when not restricted, so the
      // restricted assertions below are meaningful
      ScriptEnv senv = ScriptEnvRepository.getScriptEnv();
      Scriptable scope = createScope(senv, new Probe());

      Object result = senv.exec(senv.compile(ORG_CLASS), scope, null, null);

      assertEquals(JAVA_CLASS, result);
   }

   @Test
   void calcFieldRunsRestricted() {
      Probe probe = new Probe();
      ScriptEnv senv = ScriptEnvRepository.getScriptEnv();
      Scriptable scope = createScope(senv, probe);
      CalcFieldFormula formula = new CalcFieldFormula(
         PROBE_SCRIPT, new String[] { "total" }, new Formula[] { new SumFormula() },
         new int[1], senv, scope);
      formula.addValue(1);

      assertEquals(PACKAGE_STUB, formula.getResult());
      assertEquals(Boolean.TRUE, probe.restricted);
      assertFalse(FormulaContext.isRestricted());
   }

   @Test
   void calcFieldKeepsEnclosingRestriction() throws Exception {
      Probe probe = new Probe();
      ScriptEnv senv = ScriptEnvRepository.getScriptEnv();
      Scriptable scope = createScope(senv, probe);
      CalcFieldFormula formula = new CalcFieldFormula(
         PROBE_SCRIPT, new String[] { "total" }, new Formula[] { new SumFormula() },
         new int[1], senv, scope);
      formula.addValue(1);
      FormulaContext.setRestricted(true);

      assertEquals(PACKAGE_STUB, formula.getResult());
      assertTrue(FormulaContext.isRestricted());
   }

   @Test
   void expressionColumnRunsRestricted() {
      Probe probe = new Probe();
      ScriptEnv senv = ScriptEnvRepository.getScriptEnv();
      Scriptable scope = createScope(senv, probe);
      FormulaTableLens lens = createFormulaTable(senv, scope);

      assertTrue(lens.isRestricted(0));
      assertTrue(lens.moreRows(1));
      assertEquals(PACKAGE_STUB, lens.getObject(1, 1));
      assertEquals(Boolean.TRUE, probe.restricted);
      assertFalse(FormulaContext.isRestricted());
   }

   @Test
   void expressionColumnKeepsEnclosingRestriction() {
      Probe probe = new Probe();
      ScriptEnv senv = ScriptEnvRepository.getScriptEnv();
      Scriptable scope = createScope(senv, probe);
      FormulaTableLens lens = createFormulaTable(senv, scope);
      // even a column explicitly marked unrestricted must not lift the
      // restriction of an enclosing script
      lens.setRestricted(0, false);
      FormulaContext.setRestricted(true);

      assertTrue(lens.moreRows(1));
      assertEquals(PACKAGE_STUB, lens.getObject(1, 1));
      assertEquals(Boolean.TRUE, probe.restricted);
      assertTrue(FormulaContext.isRestricted());
   }

   @Test
   void conditionExpressionRunsRestricted() {
      Probe probe = new Probe();
      AssetQuerySandbox box = new AssetQuerySandbox(new Worksheet());
      box.getScriptEnv().put("probe", probe);
      Condition cond = new Condition();
      cond.setType(XSchema.STRING);

      Object result = PreAssetQuery.execScriptExpression(
         PROBE_SCRIPT, cond, new VariableTable(), box);

      assertEquals(PACKAGE_STUB, result);
      assertEquals(Boolean.TRUE, probe.restricted);
      assertFalse(FormulaContext.isRestricted());
   }

   @Test
   void expressionColumnReferencedFromUnrestrictedColumnRunsRestricted() {
      Probe probe = new Probe();
      ScriptEnv senv = ScriptEnvRepository.getScriptEnv();
      Scriptable scope = createScope(senv, probe);
      DefaultTableLens table = new DefaultTableLens(new Object[][] {
         { "col1" },
         { "a" }
      });
      FormulaTableLens lens = new FormulaTableLens(
         table, new String[] { "f1", "f2" },
         new String[] { "field['f2']", PROBE_SCRIPT }, senv, scope);
      // f1 is unrestricted but pulls f2 through field[], f2 keeps its own restriction
      lens.setRestricted(0, false);

      assertTrue(lens.moreRows(1));
      assertEquals(PACKAGE_STUB, lens.getObject(1, 1));
      assertEquals(Boolean.TRUE, probe.restricted);
      assertFalse(FormulaContext.isRestricted());
   }

   @Test
   void conditionGroupRunsRestricted() {
      Probe probe = new Probe();
      AssetQuerySandbox box = new AssetQuerySandbox(new Worksheet());
      box.getScriptEnv().put("probe", probe);
      ExpressionValue eval = new ExpressionValue();
      eval.setType(ExpressionValue.JAVASCRIPT);
      eval.setExpression(PROBE_SCRIPT);
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue(eval);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(new ColumnRef(new AttributeRef("col1")), cond, 0));

      // highlights and named groups evaluate expression values when the group is built
      ConditionGroup group = new ConditionGroup(0, list, box);

      assertEquals(Boolean.TRUE, probe.restricted);
      assertTrue(group.evaluate(new Object[] { PACKAGE_STUB }));
      assertFalse(FormulaContext.isRestricted());
   }

   @Test
   void freehandCellFormulaRunsRestricted() {
      Probe probe = new Probe();
      ScriptEnv senv = ScriptEnvRepository.getScriptEnv();
      senv.put("probe", probe);
      FormulaTable elem = mock(FormulaTable.class);
      when(elem.getID()).thenReturn("FreehandTable1");
      when(elem.getScriptEnv()).thenReturn(senv);
      when(elem.getScriptTable()).thenReturn(new DefaultTableLens(new Object[][] { { "col1" } }));
      FreehandTable lens = new FreehandTable();
      lens.setElement(elem);

      assertEquals(PACKAGE_STUB, lens.evaluate(PROBE_SCRIPT));
      assertEquals(Boolean.TRUE, probe.restricted);
      assertFalse(FormulaContext.isRestricted());
   }

   @Test
   void scheduleParameterExpressionRunsRestricted() {
      DynamicParameterValue parameter = new DynamicParameterValue(
         "=" + ORG_CLASS, DynamicValueModel.EXPRESSION, XSchema.STRING);
      ScheduleParameterScope scope = new ScheduleParameterScope();
      scope.getScriptEnv().addTopLevelParentScope(scope);

      assertEquals(PACKAGE_STUB, RepletRequest.executeParameter(parameter, scope));
      assertFalse(FormulaContext.isRestricted());
   }

   @Test
   void scheduleParameterTestScriptRunsRestricted() {
      ScheduleTaskFormulaService service = new ScheduleTaskFormulaService();

      // succeeds only if the org class resolves to a package stub
      assertNull(service.testScheduleParameterExpression(
         "if(" + ORG_CLASS + " != '" + PACKAGE_STUB + "') throw 'unrestricted';"));
      assertFalse(FormulaContext.isRestricted());
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "java.lang.System", "java.lang.Runtime", "java.lang.Class", "java.lang.ClassLoader",
      "java.lang.Thread", "java.lang.Process", "java.lang.ProcessBuilder",
      "java.lang.reflect.Method", "java.lang.reflect.Field", "java.lang.reflect.Proxy",
      "java.lang.invoke.MethodHandles", "java.lang.invoke.MethodHandle",
      "java.lang.management.ManagementFactory", "java.lang.ref.WeakReference"
   })
   void shutterDeniesDangerousJavaLangClasses(String className) {
      assertFalse(new SecureClassShutter().visibleToScripts(className));
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "java.lang.System", "java.lang.Runtime", "java.lang.Class", "java.lang.ClassLoader",
      "java.lang.Thread", "java.lang.ProcessBuilder", "java.lang.reflect.Method",
      "java.lang.invoke.MethodHandles"
   })
   void restrictedScriptCannotResolveDangerousJavaLangClasses(String className)
      throws Exception
   {
      ScriptEnv senv = ScriptEnvRepository.getScriptEnv();
      Scriptable scope = createScope(senv, new Probe());
      Object script = senv.compile("String(" + className + ")");

      Object result = FormulaContext.runRestricted(() -> senv.exec(script, scope, null, null));

      assertFalse(String.valueOf(result).startsWith("[JavaClass"), String.valueOf(result));
   }

   @Test
   void restrictedScriptCannotCallSystem() {
      ScriptEnv senv = ScriptEnvRepository.getScriptEnv();
      Scriptable scope = createScope(senv, new Probe());

      assertThrows(Exception.class, () -> FormulaContext.runRestricted(
         () -> senv.exec(senv.compile("java.lang.System.getProperty('user.home')"),
                         scope, null, null)));
   }

   @Test
   void restrictedScriptCanUseJavaLangAndJavaMath() throws Exception {
      Probe probe = new Probe();
      ScriptEnv senv = ScriptEnvRepository.getScriptEnv();
      Scriptable scope = createScope(senv, probe);
      CalcFieldFormula formula = new CalcFieldFormula(
         "java.lang.Integer.parseInt('5') + java.lang.Math.round(2.4) + " +
         "new java.math.BigDecimal('1.5').doubleValue() + (probe.check('x') ? 1 : 0)",
         new String[] { "total" }, new Formula[] { new SumFormula() }, new int[1], senv, scope);
      formula.addValue(1);

      assertEquals(9.5, formula.getResult());
      assertEquals(Boolean.TRUE, probe.restricted);
   }

   @Test
   void vpmScriptStaysUnrestricted() throws Exception {
      Probe probe = new Probe();
      VpmScope scope = new VpmScope();
      scope.put("probe", scope, probe);

      Object result = VpmScope.execute(PROBE_SCRIPT, scope);

      assertEquals(JAVA_CLASS, result);
      assertEquals(Boolean.FALSE, probe.restricted);
      assertFalse(FormulaContext.isRestricted());
   }

   private static Scriptable createScope(ScriptEnv senv, Probe probe) {
      senv.put("probe", probe);
      NativeObject scope = new NativeObject();
      senv.addTopLevelParentScope(scope);
      return scope;
   }

   private static FormulaTableLens createFormulaTable(ScriptEnv senv, Scriptable scope) {
      DefaultTableLens table = new DefaultTableLens(new Object[][] {
         { "col1" },
         { "a" }
      });

      return new FormulaTableLens(
         table, new String[] { "f1" }, new String[] { PROBE_SCRIPT }, senv, scope);
   }

   /**
    * Exposes the freehand table cell evaluation.
    */
   private static final class FreehandTable extends CalcTableLens {
      FreehandTable() {
         super(1, 1);
      }

      Object evaluate(String formula) {
         return evaluate(0, 0, new CalcTableLens.Formula(formula));
      }
   }

   /**
    * Records whether the script calling it ran in restricted mode.
    */
   public static final class Probe {
      public String check(String value) {
         restricted = FormulaContext.isRestricted();
         return value;
      }

      private Boolean restricted;
   }

   private static final String ORG_CLASS = "String(org.apache.commons.lang3.StringUtils)";
   private static final String PROBE_SCRIPT = "probe.check(" + ORG_CLASS + ")";
   private static final String JAVA_CLASS = "[JavaClass org.apache.commons.lang3.StringUtils]";
   private static final String PACKAGE_STUB =
      "[ContextJavaPackage org.apache.commons.lang3.StringUtils]";
}
