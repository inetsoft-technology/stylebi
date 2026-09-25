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

import inetsoft.report.TableLens;
import inetsoft.report.filter.ConditionGroup;
import inetsoft.report.lens.*;
import inetsoft.test.*;
import inetsoft.uql.Condition;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for bug #76935: a thread holding the script engine lock (a script
 * reading a worksheet table, or a condition filter) read a {@link CrossJoinTableLens}
 * whose sides are condition-filtered formula tables. The cross join loaded its sides in
 * two background threads and waited for them, while their condition filters waited for
 * the engine lock held by the waiting thread.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CrossJoinScriptLockTest {
   @Test
   void crossJoinReadUnderScriptLockDoesNotDeadlock() throws Exception {
      GraalJavaScriptEnv senv = new GraalJavaScriptEnv();
      senv.init();
      Lock engineLock = senv.getExecutionLock();
      assertNotNull(engineLock, "senv.init() should have created the engine lock");

      AssetQuerySandbox box = new AssetQuerySandbox(null);
      injectSenv(box, senv);

      ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
         Thread t = new Thread(r);
         t.setDaemon(true);
         return t;
      });

      try {
         Future<Integer> holder = pool.submit(() -> {
            engineLock.lock();
            JavaScriptEngine.pushHeldScriptLock(engineLock);

            try {
               CrossJoinTableLens join = new CrossJoinTableLens(
                  filteredFormula(senv, box, 3), filteredFormula(senv, box, 4));
               join.moreRows(TableLens.EOT);
               return join.getRowCount();
            }
            finally {
               JavaScriptEngine.popHeldScriptLock();
               engineLock.unlock();
            }
         });

         try {
            assertEquals(1 + 3 * 4, holder.get(10, TimeUnit.SECONDS));
         }
         catch(TimeoutException e) {
            fail("cross join waited for its loading threads while they waited for the " +
                    "script engine lock held by the reader -- bug #76935");
         }
      }
      finally {
         pool.shutdownNow();
      }
   }

   private static TableLens filteredFormula(GraalJavaScriptEnv senv, AssetQuerySandbox box,
                                            int rows)
   {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "col1", "col2" };

      for(int i = 1; i <= rows; i++) {
         data[i] = new Object[] { "r" + i, i };
      }

      TableLens formula = new FormulaTableLens(new DefaultTableLens(data),
         new String[] { "f1" }, new String[] { "field['col2'] + 1" }, senv, null);
      return PostProcessor.filter(formula, col2GreaterThanZero(), box);
   }

   private static ConditionGroup col2GreaterThanZero() {
      Condition condition = new Condition();
      condition.setOperation(Condition.GREATER_THAN);
      condition.addValue(0);
      condition.setType(XSchema.INTEGER);
      ConditionGroup group = new ConditionGroup();
      group.addCondition(1, condition, 0);
      return group;
   }

   private static void injectSenv(AssetQuerySandbox box, GraalJavaScriptEnv senv) throws Exception {
      Field field = AssetQuerySandbox.class.getDeclaredField("senv");
      field.setAccessible(true);
      field.set(box, senv);
   }
}
