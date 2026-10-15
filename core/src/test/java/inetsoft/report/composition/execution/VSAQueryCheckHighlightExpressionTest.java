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
package inetsoft.report.composition.execution;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.script.FormulaContext;
import inetsoft.util.script.ScriptException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78567: {@link VSAQuery#checkHighlightExpression} must evaluate a highlight expression the
 * way {@code ConditionGroup.getExpressionVal} does at render time. Runs the real method against a
 * real {@link AssetQuerySandbox}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSAQueryCheckHighlightExpressionTest {
   private static ViewsheetSandbox sandbox(VariableTable vars) {
      Viewsheet vs = new Viewsheet();
      AssetQuerySandbox box = new AssetQuerySandbox(new Worksheet(), null, vars);
      box.setActive(true);
      ViewsheetSandbox vbox = mock(ViewsheetSandbox.class);
      when(vbox.getConditionAssetQuerySandbox(vs)).thenReturn(box);
      when(vbox.getViewsheet()).thenReturn(vs);
      return vbox;
   }

   @Test
   void syntaxErrorIsRejectedAtCompile() {
      assertThrows(ScriptException.class,
                   () -> VSAQuery.checkHighlightExpression("1 +* 2", false,
                                                           sandbox(new VariableTable())));
   }

   @Test
   void goodExpressionReturnsNull() throws Exception {
      assertNull(VSAQuery.checkHighlightExpression("10 + 1", false, sandbox(new VariableTable())));
   }

   @Test
   void aThrowingExpressionReturnsItsMessage() throws Exception {
      String error = VSAQuery.checkHighlightExpression("undefinedFn(1)", false,
                                                       sandbox(new VariableTable()));

      assertNotNull(error);
      assertTrue(error.contains("undefinedFn"), error);
   }

   /** The renderer does not run the script until the variable has a value, so neither do we. */
   @Test
   void aSqlVariableReferenceIsNotRunWhileTheVariableIsUnset() throws Exception {
      assertNull(VSAQuery.checkHighlightExpression("undefinedFn($(limit))", true,
                                                   sandbox(new VariableTable())));
   }

   @Test
   void aSqlVariableReferenceIsRewrittenAndRunOnceTheVariableIsSet() throws Exception {
      VariableTable vars = new VariableTable();
      vars.put("limit", 5);

      assertNull(VSAQuery.checkHighlightExpression("$(limit) + 1", true, sandbox(vars)));
      // a rewrite to parameter.limit that then fails proves the script really ran
      String error = VSAQuery.checkHighlightExpression("undefinedFn($(limit))", true, sandbox(vars));
      assertNotNull(error);
      assertTrue(error.contains("undefinedFn"), error);
   }

   @Test
   void aJsParameterReferenceIsNotRunWhileTheVariableIsUnset() throws Exception {
      assertNull(VSAQuery.checkHighlightExpression("undefinedFn(parameter.limit)", false,
                                                   sandbox(new VariableTable())));
   }

   @Test
   void theRestrictedFlagIsRestoredAfterTheRun() throws Exception {
      boolean before = FormulaContext.isRestricted();

      VSAQuery.checkHighlightExpression("undefinedFn(1)", false, sandbox(new VariableTable()));

      assertEquals(before, FormulaContext.isRestricted());
   }

   /**
    * With javascript.java.com_org on, only the restricted mode refuses a com/org class lookup (the
    * probe in RestrictedScriptSurfaceTest), so a non-null result proves the check runs restricted
    * like the renderer does (bug #77396).
    */
   @Test
   void theExpressionRunsRestricted() throws Exception {
      String old = SreeEnv.getProperty("javascript.java.com_org");
      SreeEnv.setProperty("javascript.java.com_org", "true");

      try {
         FormulaContext.setRestricted(false);
         assertNotNull(VSAQuery.checkHighlightExpression(
            "Java.type('org.apache.commons.lang3.StringUtils')", false,
            sandbox(new VariableTable())));
         assertFalse(FormulaContext.isRestricted());
      }
      finally {
         SreeEnv.setProperty("javascript.java.com_org", old);
      }
   }
}
