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
package inetsoft.report.composition.execution.lockcycle;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.PostProcessor;
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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76965: since #76935 a condition filter takes the engine lock before its monitor only
 * when its base chain can reach script, but a sub-query condition reads its sub table lazily
 * from {@code checkCondition()}, inside the filter's monitor ({@code SubQueryValue.getValues()}),
 * and that sub table can run script: a {@link FormulaTableLens}, or a
 * {@link DistinctTableLens} over one, since sub-query tables are made distinct. Cycle: the
 * populator holds the filter's monitor and waits for E in the sub table's formula; the
 * script thread holds E and waits for the filter's monitor (#76918 again).
 *
 * <p>Ported from {@code ConditionFilterSubQueryLockOrderingTest} of the withdrawn PR #5551,
 * with a real {@link AssetQuerySandbox} and {@link GraalJavaScriptEnv}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class SubQueryConditionCycleTest {
   @BeforeEach
   public void setUp() {
      harness = new LockCycleHarness();
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();
   }

   /**
    * @param distinct the sub table is {@code DistinctTableLens(FormulaTableLens)} rather than
    *                 the formula lens itself.
    */
   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   @Tag("known-deadlock")
   @EnabledIfSystemProperty(named = "lockcycle.known", matches = "true")
   public void formulaSubTableUnderPlainBase(boolean distinct) throws Exception {
      GraalJavaScriptEnv senv = new GraalJavaScriptEnv();
      senv.init();
      Lock lock = senv.getExecutionLock();
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
      TableLens filtered = harness.track(PostProcessor.filter(plainBase, group, box));
      AtomicReference<Thread> populatorThread = new AtomicReference<>();
      AtomicReference<Future<Boolean>> populator = new AtomicReference<>();

      // the script holds E, lets the populator reach E (inside the monitor without the
      // fix, before it with the fix; in the distinct case the populator instead waits for
      // the distinct worker, which waits for E), then reads the filter
      Future<Boolean> script = harness.submit(() -> {
         lock.lock();

         try {
            populator.set(harness.submit(() -> {
               populatorThread.set(Thread.currentThread());
               return filtered.moreRows(1);
            }));

            while(populatorThread.get() == null) {
               Thread.sleep(5);
            }

            awaitWaitingOnLock(populatorThread.get(), 2);
            return filtered.moreRows(1);
         }
         finally {
            lock.unlock();
         }
      });

      try {
         assertTrue(harness.await(script, KNOWN_CAP, "script thread holding the engine lock"));
         assertTrue(harness.await(populator.get(), KNOWN_CAP, "populator"));
      }
      finally {
         if(lock.tryLock()) {
            lock.unlock();
            senv.reset();
         }
      }
   }

   private LockCycleHarness harness;
}
