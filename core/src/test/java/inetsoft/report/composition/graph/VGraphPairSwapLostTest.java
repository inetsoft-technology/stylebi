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
package inetsoft.report.composition.graph;

import inetsoft.report.script.viewsheet.ViewsheetScope;
import inetsoft.test.*;
import inetsoft.util.swap.SwapFileReadException;
import inetsoft.util.swap.SwapLostTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78099: the chart render path's own script execution must not swallow a lock stall or
 * a lost swap file as an ordinary script failure. {@link VGraphPair}'s private
 * {@code executeScript} is a third swallow point on the path a chart's own assigned script
 * takes during normal rendering (reached twice per render, once with {@code ignoreError=true}
 * for the primary graph and once with {@code ignoreError=false} for the expanded one) and had
 * no permanent regression coverage prior to this test.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VGraphPairSwapLostTest {
   @Test
   void executeScriptRethrowsLostSwapFileWhenIgnoreErrorIsTrue() throws Exception {
      SwapFileReadException lost = SwapLostTestSupport.swapLost();
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(anyString(), anyString())).thenThrow(lost);

      RuntimeException thrown = assertThrows(RuntimeException.class,
         () -> invokeExecuteScript(scope, true),
         "executeScript() should rethrow a lost swap file even when ignoreError is true " +
         "(the primary egraph pass)");
      SwapLostTestSupport.assertSwapOf(lost, thrown);
   }

   @Test
   void executeScriptRethrowsLostSwapFileWhenIgnoreErrorIsFalse() throws Exception {
      SwapFileReadException lost = SwapLostTestSupport.swapLost();
      ViewsheetScope scope = mock(ViewsheetScope.class);
      when(scope.execute(anyString(), anyString())).thenThrow(lost);

      RuntimeException thrown = assertThrows(RuntimeException.class,
         () -> invokeExecuteScript(scope, false),
         "executeScript() should rethrow a lost swap file even when ignoreError is false " +
         "(the expanded egraph2 pass)");
      SwapLostTestSupport.assertSwapOf(lost, thrown);
   }

   /**
    * Call the private {@code VGraphPair.executeScript(ViewsheetScope, String, String, boolean)}
    * via reflection: the method only touches its own parameters, so no real
    * {@code ViewsheetSandbox}/viewsheet setup is needed to exercise it directly.
    */
   private static void invokeExecuteScript(ViewsheetScope scope, boolean ignoreError)
      throws Throwable
   {
      VGraphPair pair = new VGraphPair();
      Method method = VGraphPair.class.getDeclaredMethod(
         "executeScript", ViewsheetScope.class, String.class, String.class, boolean.class);
      method.setAccessible(true);

      try {
         method.invoke(pair, scope, "script", "chart1", ignoreError);
      }
      catch(InvocationTargetException ex) {
         throw ex.getCause();
      }
   }
}
