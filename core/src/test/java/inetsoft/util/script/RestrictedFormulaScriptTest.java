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

import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.PreAssetQuery;
import inetsoft.report.filter.CalcFieldFormula;
import inetsoft.report.filter.Formula;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.test.SreeHome;
import inetsoft.uql.Condition;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.script.VpmScope;
import org.junit.jupiter.api.*;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;

import static org.junit.jupiter.api.Assertions.*;

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
