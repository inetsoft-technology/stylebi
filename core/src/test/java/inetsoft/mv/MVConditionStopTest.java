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

import inetsoft.mv.data.*;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.util.script.ScriptException;
import inetsoft.util.script.graal.ScriptStopTestSupport;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78331: a script condition expression on a materialized view that the script timeout
 * stops. {@code MVConditionListHandler.executeScript} rebuilt the failure as a new script
 * exception without the stop flag, and {@code MVJob.init} then replaced whatever it caught by
 * an exception carrying only the message, so {@code ViewsheetSandbox.getData} did not see a
 * stop and cached NULL for the table. Both now keep the stop; other failures are unchanged.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  PluginsTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class MVConditionStopTest {
   private static final String BUSY =
      "var t0 = Date.now(); while(Date.now() - t0 < 1500) {} 5";

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void executeScriptKeepsTheStopOfATimedOutExpression(boolean pool) throws Exception {
      String old = ScriptStopTestSupport.setTimeout("1");

      try {
         ScriptException ex = assertThrows(ScriptException.class, () -> execute(pool, BUSY));
         assertTrue(ScriptTimeoutGuard.isStop(ex), "stop lost: " + ex);
      }
      finally {
         ScriptStopTestSupport.setTimeout(old);
      }
   }

   @ParameterizedTest
   @ValueSource(booleans = { true, false })
   void executeScriptKeepsTheMessageOfAnOrdinaryError(boolean pool) throws Exception {
      ScriptException ex = assertThrows(ScriptException.class,
                                        () -> execute(pool, "throw new Error('boom')"));
      assertFalse(ScriptTimeoutGuard.isStop(ex));
      assertTrue(ex.getMessage().startsWith("MV Script error: "), ex.getMessage());
      assertTrue(ex.getMessage().contains("boom"), ex.getMessage());
   }

   @Test
   void jobInitKeepsAStopFromTheConditionBuilder() {
      // the MVAssetQuery wrap of what the job constructor throws
      Exception ex = assertThrows(Exception.class, () -> newJob(ScriptStopTestSupport.stopped()));
      assertTrue(ScriptTimeoutGuard.isStop(new MVExecutionException(ex)), "stop lost: " + ex);
   }

   @Test
   void jobInitStillWrapsAnOrdinaryFailure() {
      Exception ex = assertThrows(Exception.class,
                                  () -> newJob(new ScriptException("MV Script error: boom")));
      assertInstanceOf(MVExecutionException.class, ex);
      assertEquals("MV Script error: boom", ex.getMessage());
      assertFalse(ScriptTimeoutGuard.isStop(new MVExecutionException(ex)));
   }

   private static Object execute(boolean pool, String expression) throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "T");
      ws.addAssembly(table);
      MVConditionListHandler handler =
         new MVConditionListHandler(table, new MV(), new VariableTable(), new MVDef());
      AssetQuerySandbox box = PoolTestSupport.poolBox(pool);
      Field field = MVConditionListHandler.class.getDeclaredField("box");
      field.setAccessible(true);
      field.set(handler, box);

      Condition cond = new Condition();
      cond.setOperation(XCondition.GREATER_THAN);
      ExpressionValue ev = new ExpressionValue();
      ev.setType(ExpressionValue.JAVASCRIPT);
      ev.setExpression(expression);
      cond.addValue(ev);
      return handler.executeScript(cond, ev, 0, null);
   }

   /** Creates a job whose condition builder fails with {@code failure}. */
   private static void newJob(RuntimeException failure) throws Exception {
      try(MockedStatic<MVStorage> storage = Mockito.mockStatic(MVStorage.class);
          MockedConstruction<MVQueryBuilder> builder =
             Mockito.mockConstruction(MVQueryBuilder.class, (mock, ctx) -> {
                Mockito.when(mock.getQuery()).thenThrow(failure);
             }))
      {
         storage.when(() -> MVStorage.getFile(Mockito.anyString())).thenReturn("f");
         storage.when(MVStorage::getInstance).thenReturn(Mockito.mock(MVStorage.class));
         new MVJob(Mockito.mock(TableAssembly.class), "mv", new VariableTable(), null);
      }
   }
}
