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
import inetsoft.report.lens.DistinctTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.test.*;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.SubQueryValue;
import inetsoft.uql.erm.AttributeRef;
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
 * Regression test for bug #76965: since bug #76935, {@code PostProcessor.ConditionFilter2}
 * only takes the sandbox's script engine lock before its monitor when its base table
 * chain can reach script. A sub-query condition reads its sub table lazily from
 * {@code checkCondition()}, inside the filter's monitor ({@code SubQueryValue.getValues()}),
 * and that sub table can run script (a {@link FormulaTableLens}, or a
 * {@link DistinctTableLens} over one, since sub-query tables are made distinct). With a
 * plain base the filter skipped the lock, so a populating thread held the monitor while
 * waiting for the engine lock, and a script thread holding the engine lock blocked on the
 * monitor: bug #76918's deadlock again.
 *
 * <p>Uses a real {@link AssetQuerySandbox} with a real {@link GraalJavaScriptEnv}, the
 * real {@code ConditionFilter2} from {@link PostProcessor#filter}, and a real ONE_OF
 * {@link AssetCondition} with a {@link SubQueryValue}. All waits are bounded and the
 * threads are daemons, so a regression fails with a timeout instead of hanging the build.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class ConditionFilterSubQueryLockOrderingTest {
   @Test
   public void formulaSubTableUnderPlainBase() throws Exception {
      runInterleaving(false);
   }

   @Test
   public void distinctFormulaSubTableUnderPlainBase() throws Exception {
      runInterleaving(true);
   }

   /**
    * T2 is a script already holding the engine lock that then reads the filter. T1
    * populates the filter, which evaluates the sub-query condition and so runs the sub
    * table's formula. T1 starts while T2 holds the lock, so it must wait for the lock
    * before taking the filter's monitor.
    */
   private void runInterleaving(boolean distinct) throws Exception {
      GraalJavaScriptEnv senv = new GraalJavaScriptEnv();
      senv.init();
      Lock engineLock = senv.getExecutionLock();
      AssetQuerySandbox box = new AssetQuerySandbox(null);
      Field field = AssetQuerySandbox.class.getDeclaredField("senv");
      field.setAccessible(true);
      field.set(box, senv);

      TableLens plainBase = XTableUtil.getDefaultTableLens();
      TableLens subTable = new FormulaTableLens(XTableUtil.getDefaultTableLens(),
         new String[] { "f1" }, new String[] { "1" }, senv, null);

      if(distinct) {
         subTable = new DistinctTableLens(subTable);
      }

      SubQueryValue subQuery = new SubQueryValue();
      subQuery.setAttribute(new AttributeRef(null, "f1"));
      AssetCondition condition = new AssetCondition();
      condition.setOperation(XCondition.ONE_OF);
      condition.setType(XSchema.INTEGER);
      condition.addValue(subQuery);
      condition.init();
      condition.initSubTable(subTable);
      condition.initMainTable(plainBase, 1);

      ConditionGroup group = new ConditionGroup();
      group.addCondition(1, condition, 0);
      TableLens filtered = PostProcessor.filter(plainBase, group, box);

      CountDownLatch scriptHasLock = new CountDownLatch(1);
      ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
         Thread thread = new Thread(r);
         thread.setDaemon(true);
         return thread;
      });

      try {
         Future<Boolean> scriptThread = pool.submit(() -> {
            engineLock.lock();

            try {
               scriptHasLock.countDown();
               // let the populating thread reach the filter first
               Thread.sleep(500);
               return filtered.moreRows(1);
            }
            finally {
               engineLock.unlock();
            }
         });

         assertTrue(scriptHasLock.await(10, TimeUnit.SECONDS));
         Future<Boolean> populateThread = pool.submit(() -> filtered.moreRows(1));

         assertEquals(Boolean.TRUE, scriptThread.get(10, TimeUnit.SECONDS));
         assertEquals(Boolean.TRUE, populateThread.get(10, TimeUnit.SECONDS));
      }
      finally {
         pool.shutdownNow();
      }
   }
}
