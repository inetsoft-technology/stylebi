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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.IntegrationTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.Viewsheet;
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
 * Bug #78260: {@link VSAQuery#checkConditionExpression} is what set_condition uses to tell a
 * syntax error (refused) from a runtime failure (warned) from a good expression. Runs the real
 * method against a real {@link AssetQuerySandbox}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, IntegrationTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VSAQueryCheckConditionExpressionTest {
   private static ViewsheetSandbox sandbox() {
      AssetQuerySandbox box = new AssetQuerySandbox(new Worksheet(), null, new VariableTable());
      box.setActive(true);
      ViewsheetSandbox vbox = mock(ViewsheetSandbox.class);
      when(vbox.getAssetQuerySandbox()).thenReturn(box);
      when(vbox.getViewsheet()).thenReturn(new Viewsheet());
      return vbox;
   }

   /** Compilation must fail eagerly, otherwise a syntax error would only surface as a warning. */
   @Test
   void syntaxErrorIsRejectedAtCompile() {
      assertThrows(ScriptException.class,
                   () -> VSAQuery.checkConditionExpression("1 +* 2", sandbox()));
   }

   @Test
   void runtimeFailureIsReturnedNotThrown() throws Exception {
      String error = VSAQuery.checkConditionExpression("undefinedFn(1)", sandbox());

      assertNotNull(error);
      assertTrue(error.contains("undefinedFn"), error);
   }

   @Test
   void goodExpressionReturnsNull() throws Exception {
      assertNull(VSAQuery.checkConditionExpression("10 + 1", sandbox()));
   }

   @Test
   void unboundParameterIsNotAFalseWarning() throws Exception {
      assertNull(VSAQuery.checkConditionExpression("parameter.foo + 1", sandbox()));
   }

   /** A SyntaxError thrown while running is a runtime error, possibly transient: warn only. */
   @Test
   void runtimeSyntaxErrorsAreWarnedNotRejected() throws Exception {
      String json = VSAQuery.checkConditionExpression("JSON.parse(\"\")", sandbox());
      String regexp = VSAQuery.checkConditionExpression("new RegExp(\"(\")", sandbox());

      assertNotNull(json);
      assertNotNull(regexp);
   }
}
