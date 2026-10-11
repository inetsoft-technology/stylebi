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
package inetsoft.mv;

import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.util.script.graal.ScriptStopTestSupport;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78331: {@code MVAssetQuery} wraps every failure of the MV executor in an
 * {@code MVExecutionException}, which {@code ViewsheetSandbox.getData} answers with an MV
 * recreate when {@code mv.ondemand} is on (no cache decision, NULL cached afterwards). A stop
 * must come out as the stop itself, so getData's generic arm does not cache it, whatever the
 * on-demand setting; any other failure is still wrapped. This drives the real
 * {@code getPostBaseTableLens} with a mocked executor; {@code ViewsheetSandbox.getData}'s
 * handling of the two exception types is not driven here.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVAssetQueryStopTest {
   @Test
   void stopFromTheExecutorIsNotWrapped() {
      RuntimeException stop = ScriptStopTestSupport.stopped();
      RuntimeException ex = assertThrows(RuntimeException.class, () -> run(stop));
      assertFalse(ex instanceof MVExecutionException, "would trigger an on-demand recreate");
      assertTrue(ScriptTimeoutGuard.isStop(ex));
   }

   @Test
   void stopInTheCauseChainIsNotWrapped() {
      RuntimeException stop = ScriptStopTestSupport.stopped();
      RuntimeException ex = assertThrows(
         RuntimeException.class, () -> run(new RuntimeException("MV job failed", stop)));
      assertFalse(ex instanceof MVExecutionException);
      assertTrue(ScriptTimeoutGuard.isStop(ex));
   }

   @Test
   void ordinaryFailureIsStillWrapped() {
      RuntimeException failure = new IllegalStateException("broken mv");
      RuntimeException ex = assertThrows(RuntimeException.class, () -> run(failure));
      assertInstanceOf(MVExecutionException.class, ex);
      assertSame(failure, ex.getCause());
      assertFalse(ScriptTimeoutGuard.isStop(ex));
   }

   private static void run(RuntimeException failure) throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "T");
      ws.addAssembly(table);
      MVAssetQuery query = Mockito.mock(MVAssetQuery.class, Mockito.CALLS_REAL_METHODS);
      set(MVAssetQuery.class, query, "table", table);
      set(inetsoft.report.composition.execution.PreAssetQuery.class, query, "box",
          Mockito.mock(AssetQuerySandbox.class));
      MVExecutor executor = Mockito.mock(MVExecutor.class);
      Mockito.when(executor.getData()).thenThrow(failure);

      try(MockedStatic<MVTool> tool = Mockito.mockStatic(MVTool.class)) {
         tool.when(() -> MVTool.newMVExecutor(Mockito.any(), Mockito.any(), Mockito.any(),
                                              Mockito.any())).thenReturn(executor);
         query.getPostBaseTableLens(new VariableTable());
      }
   }

   private static void set(Class<?> type, Object target, String name, Object value)
      throws Exception
   {
      Field field = type.getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
   }
}
