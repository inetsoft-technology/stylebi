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
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.test.*;
import inetsoft.uql.Condition;
import inetsoft.uql.schema.XSchema;
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
 * Regression test for bug #76935: {@code PostProcessor.ConditionFilter2.moreRows()} (bug
 * #76918's fix) should only take the sandbox's GraalJS engine execution lock when its own
 * base table's {@link inetsoft.report.TableFilter} chain can actually reach a
 * {@link FormulaTableLens} formula column -- not unconditionally whenever the sandbox
 * happens to already have a script engine (which serialized every condition-filtered query
 * in a viewsheet's sandbox behind whatever unrelated script happened to be executing,
 * turning parallel per-tile query time into a sum instead of a max).
 *
 * <p>This wires up one real {@link AssetQuerySandbox} sharing a single
 * {@link GraalJavaScriptEnv} (and therefore a single engine execution lock) between two
 * {@code ConditionFilter2} instances built through the real
 * {@link PostProcessor#filter(TableLens, ConditionGroup, AssetQuerySandbox)} factory: one
 * filtering a plain (formula-free) table, one filtering a {@link FormulaTableLens}-backed
 * table. A separate thread holds the engine lock -- mirroring a concurrently-executing
 * script elsewhere in the same sandbox -- while both filters' {@code moreRows()} are
 * exercised, so the test directly observes the behavior difference the fix claims rather
 * than just asserting on the private {@code needsScriptLock} field.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ConditionFilterFormulaScopingTest {
   @Test
   void formulaFreeFilterDoesNotBlockOnUnrelatedScriptLock() throws Exception {
      GraalJavaScriptEnv senv = new GraalJavaScriptEnv();
      // force the underlying GraalJavaScriptEngine (and its lock) to exist up front,
      // deterministically, without needing to actually run/exec a script.
      senv.init();
      Lock engineLock = senv.getExecutionLock();
      assertNotNull(engineLock, "senv.init() should have created the engine and its lock");

      AssetQuerySandbox box = new AssetQuerySandbox(null);
      injectSenv(box, senv);

      TableLens plainBase = XTableUtil.getDefaultTableLens();
      TableLens formulaBase = new FormulaTableLens(XTableUtil.getDefaultTableLens(),
         new String[] { "f1" }, new String[] { "1" }, senv, null);

      TableLens plainFiltered = PostProcessor.filter(plainBase, col2GreaterThanZero(), box);
      TableLens formulaFiltered = PostProcessor.filter(formulaBase, col2GreaterThanZero(), box);

      CountDownLatch scriptHasLock = new CountDownLatch(1);
      CountDownLatch releaseScript = new CountDownLatch(1);

      ExecutorService pool = Executors.newFixedThreadPool(3, r -> {
         Thread t = new Thread(r);
         t.setDaemon(true);
         return t;
      });

      try {
         // mirrors a *different*, concurrently-executing script elsewhere in the same
         // viewsheet's sandbox, holding the engine lock for the duration of its own exec().
         Future<?> scriptThread = pool.submit(() -> {
            engineLock.lock();

            try {
               scriptHasLock.countDown();
               assertTrue(releaseScript.await(10, TimeUnit.SECONDS),
                  "test never released the simulated script's hold on the engine lock");
            }
            finally {
               engineLock.unlock();
            }

            return null;
         });

         assertTrue(scriptHasLock.await(10, TimeUnit.SECONDS),
            "simulated script thread never acquired the engine lock");

         // formula-free filter: must complete promptly without queuing behind the
         // unrelated script's engine lock -- this is the #76935 regression itself.
         Future<Boolean> plainThread = pool.submit(() -> plainFiltered.moreRows(0));
         assertTrue(plainThread.get(2, TimeUnit.SECONDS),
            "formula-free ConditionFilter2.moreRows() blocked on an unrelated script's " +
            "engine lock -- #76935 regression is present");

         // formula-containing filter: must still serialize behind the engine lock --
         // #76918's original deadlock-safety property must survive this narrowing.
         Future<Boolean> formulaThread = pool.submit(() -> formulaFiltered.moreRows(0));

         assertThrows(TimeoutException.class, () -> formulaThread.get(300, TimeUnit.MILLISECONDS),
            "FormulaTableLens-backed ConditionFilter2.moreRows() did not wait for the " +
            "engine lock -- narrowing the lock scope would reopen #76918");

         releaseScript.countDown();
         scriptThread.get(5, TimeUnit.SECONDS);
         assertTrue(formulaThread.get(5, TimeUnit.SECONDS),
            "FormulaTableLens-backed filter never completed after the engine lock was released");
      }
      finally {
         pool.shutdownNow();
      }
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
