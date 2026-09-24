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
import inetsoft.report.filter.DefaultTableFilter;
import inetsoft.report.lens.DefaultTableLens;
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
 * Regression test for bug #76935: a {@link FormulaTableLens} shared by two table chains,
 * one read through a condition filter (which takes the script engine lock before reading
 * its base) and one read directly (e.g. by a sort in a summary worker), deadlocked when
 * the lens took its own lock before the engine lock. The direct reader held the lens lock
 * and waited for the engine lock in exec(), while the condition filter held the engine
 * lock and waited for the lens lock.
 *
 * <p>The base table forces that interleaving: the direct reader pauses inside the lens's
 * row computation until the condition filter thread has taken the engine lock and reached
 * the lens.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class FormulaTableLensLockOrderTest {
   @Test
   void sharedFormulaLensDoesNotDeadlockWithConditionFilter() throws Exception {
      GraalJavaScriptEnv senv = new GraalJavaScriptEnv();
      senv.init();
      assertNotNull(senv.getExecutionLock(), "senv.init() should have created the engine lock");

      AssetQuerySandbox box = new AssetQuerySandbox(null);
      injectSenv(box, senv);

      CountDownLatch directInLens = new CountDownLatch(1);
      CountDownLatch filterAtLens = new CountDownLatch(1);
      Thread[] directThread = new Thread[1];
      Thread[] filterThread = new Thread[1];

      TableLens base = new DefaultTableFilter(createTable()) {
         @Override
         public boolean moreRows(int row) {
            Thread current = Thread.currentThread();

            if(current == filterThread[0]) {
               // the condition filter holds the engine lock here and is about to take
               // the formula lens's lock
               filterAtLens.countDown();
            }
            else if(current == directThread[0] && row == PAUSE_ROW) {
               // only reached from the formula lens's row loop, with its lock held
               directInLens.countDown();

               try {
                  filterAtLens.await(1, TimeUnit.SECONDS);
               }
               catch(InterruptedException e) {
                  Thread.currentThread().interrupt();
               }
            }

            return super.moreRows(row);
         }
      };

      FormulaTableLens formula = new FormulaTableLens(base, new String[] { "f1" },
         new String[] { "field['col2'] + 1" }, senv, null);
      TableLens filtered = PostProcessor.filter(formula, col2GreaterThanZero(), box);

      ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
         Thread t = new Thread(r);
         t.setDaemon(true);
         return t;
      });

      try {
         Future<Boolean> direct = pool.submit(() -> {
            directThread[0] = Thread.currentThread();
            return formula.moreRows(1);
         });

         assertTrue(directInLens.await(10, TimeUnit.SECONDS),
            "direct reader never reached the formula lens's row computation");

         Future<Boolean> condition = pool.submit(() -> {
            filterThread[0] = Thread.currentThread();
            return filtered.moreRows(TableLens.EOT);
         });

         try {
            assertTrue(direct.get(10, TimeUnit.SECONDS));
            condition.get(10, TimeUnit.SECONDS);
         }
         catch(TimeoutException e) {
            fail("formula lens and condition filter deadlocked on the lens lock and the " +
                    "script engine lock -- bug #76935");
         }

         assertEquals(ROWS, filtered.getRowCount() - 1,
            "every row satisfies the condition, none should be filtered out");
         assertEquals(2, ((Number) formula.getObject(1, 2)).intValue());
      }
      finally {
         pool.shutdownNow();
      }
   }

   /**
    * The formula lens takes the engine lock before its own, so it must not wait for its
    * base while holding it: the base may be an async lens (e.g. a cross join) whose
    * worker needs the engine lock, for a condition filter or formula below it.
    */
   @Test
   void formulaLensDoesNotWaitForBaseWorkerUnderEngineLock() throws Exception {
      GraalJavaScriptEnv senv = new GraalJavaScriptEnv();
      senv.init();
      Lock engineLock = senv.getExecutionLock();
      assertNotNull(engineLock, "senv.init() should have created the engine lock");

      ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
         Thread t = new Thread(r);
         t.setDaemon(true);
         return t;
      });

      try {
         // the first data row is available right away, the rest only after a worker
         // that needs the engine lock has run
         Future<?>[] worker = new Future<?>[1];
         TableLens base = new DefaultTableFilter(createTable()) {
            @Override
            public boolean moreRows(int row) {
               if(row > 1) {
                  synchronized(worker) {
                     if(worker[0] == null) {
                        worker[0] = pool.submit(() -> {
                           engineLock.lock();
                           engineLock.unlock();
                        });
                     }
                  }

                  try {
                     worker[0].get();
                  }
                  catch(Exception e) {
                     throw new RuntimeException(e);
                  }
               }

               return super.moreRows(row);
            }
         };

         FormulaTableLens formula = new FormulaTableLens(base, new String[] { "f1" },
            new String[] { "field['col2'] + 1" }, senv, null);
         Future<Boolean> reader = pool.submit(() -> formula.moreRows(1));

         try {
            assertTrue(reader.get(10, TimeUnit.SECONDS));
         }
         catch(TimeoutException e) {
            fail("formula lens waited for its base's worker while holding the script " +
                    "engine lock the worker needs -- bug #76935");
         }

         assertEquals(2, ((Number) formula.getObject(1, 2)).intValue());
      }
      finally {
         pool.shutdownNow();
      }
   }

   private static DefaultTableLens createTable() {
      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] { "col1", "col2" };

      for(int i = 1; i <= ROWS; i++) {
         data[i] = new Object[] { "r" + i, i };
      }

      return new DefaultTableLens(data);
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

   private static final int ROWS = 20;
   // a row the formula lens's loop reads while computing row 1, but not moreRows(1) itself
   private static final int PAUSE_ROW = 3;
}
