/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.report.filter;

import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.sree.DynamicParameterValue;
import inetsoft.sree.RepletRequest;
import inetsoft.sree.schedule.ScheduleParameterScope;
import inetsoft.test.*;
import inetsoft.uql.asset.Worksheet;
import inetsoft.util.script.FormulaContext;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.web.admin.schedule.ScheduleTaskFormulaService;
import inetsoft.web.composer.model.vs.DynamicValueModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77396: the end-user script surfaces (calculated fields, expression columns,
 * schedule parameters) run restricted, so they cannot look up com.* / org.* classes, and they put back
 * the restricted flag the thread had when they finish.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RestrictedScriptSurfaceTest {
   // 1 when the script can look up a com/org class, 2 when it is refused
   private static final String PROBE =
      "(function() { try { Java.type('org.apache.commons.lang3.StringUtils'); return 1; }" +
      " catch(e) { return 2; } })()";

   private AssetQuerySandbox box;

   @BeforeEach
   void setUp() throws Exception {
      FormulaContext.setRestricted(false);
      box = new AssetQuerySandbox(new Worksheet());
   }

   @AfterEach
   void tearDown() {
      FormulaContext.setRestricted(false);
   }

   @Test
   void unrestrictedScriptKeepsComOrgByDefault() throws Exception {
      ScriptEnv senv = box.getScriptEnv();
      assertEquals(1.0, ((Number) senv.exec(senv.compile(PROBE), box.getScope(), null, null))
         .doubleValue());
   }

   @Test
   void calculatedFieldRunsRestricted() {
      assertEquals(2.0, calcFieldResult());
      assertFalse(FormulaContext.isRestricted());
   }

   @Test
   void calculatedFieldKeepsEnclosingRestrictedFlag() {
      FormulaContext.setRestricted(true);
      assertEquals(2.0, calcFieldResult());
      assertTrue(FormulaContext.isRestricted());
   }

   @Test
   void expressionColumnRunsRestricted() {
      assertEquals(2.0, expressionColumnResult());
      assertFalse(FormulaContext.isRestricted());
   }

   @Test
   void expressionColumnKeepsEnclosingRestrictedFlag() {
      FormulaContext.setRestricted(true);
      assertEquals(2.0, expressionColumnResult());
      assertTrue(FormulaContext.isRestricted());
   }

   @Test
   void scheduleParameterRunsRestricted() {
      DynamicParameterValue param =
         new DynamicParameterValue("=" + PROBE, DynamicValueModel.EXPRESSION, "double");
      ScheduleParameterScope scope = new ScheduleParameterScope();
      scope.getScriptEnv().addTopLevelParentScope(scope);

      assertEquals(2.0, ((Number) RepletRequest.executeParameter(param, scope)).doubleValue());
      assertFalse(FormulaContext.isRestricted());
   }

   @Test
   void scheduleParameterTestRunsRestricted() {
      ScheduleTaskFormulaService service = new ScheduleTaskFormulaService(null);

      // null is success, a message is a failure
      assertNull(service.testScheduleParameterExpression("Java.type('java.lang.Math').max(1, 2)"));
      assertNotNull(service.testScheduleParameterExpression(
         "Java.type('org.apache.commons.lang3.StringUtils')"));
      assertFalse(FormulaContext.isRestricted());
   }

   private double calcFieldResult() {
      CalcFieldFormula formula = new CalcFieldFormula(
         PROBE, new String[] { "SUM" }, new Formula[] { new SumFormula() }, new int[] { 0 },
         box.getScriptEnv(), box.getScope());
      formula.addValue(new Object[] { null, 5.0 });
      return ((Number) formula.getResult()).doubleValue();
   }

   private double expressionColumnResult() {
      DefaultTableLens tbl = new DefaultTableLens(new Object[][] { { "x" }, { 1 } });
      FormulaTableLens lens = new FormulaTableLens(
         tbl, new String[] { "f" }, new String[] { PROBE }, new GraalJavaScriptEnv(), null);
      assertTrue(lens.moreRows(1));
      return ((Number) lens.getObject(1, 1)).doubleValue();
   }
}
