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
import inetsoft.report.filter.ConditionGroup;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.report.composition.execution.PostProcessor;
import inetsoft.test.*;
import inetsoft.uql.Condition;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.script.ScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
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
 * The #76918 cycle after the sandbox that built a lens chain was reset or disposed (bug
 * #76961). Ported from PR #5548's {@code ConditionFilterBoxResetLockTest}. A cached chain
 * {@code ConditionFilter2(FormulaTableLens)} outlives a {@code reset()} (bookmark switch) or
 * {@code dispose()} (viewsheet close) of its sandbox; the formula keeps running on the env it
 * was built with, while the filter looks up the sandbox's env when it is read, so it takes no
 * lock, or the lock of a newer env. Cycle: the populator holds the filter's monitor and waits
 * for the formula env's lock E in {@code exec}; the script thread holds E and waits for the
 * filter's monitor.
 *
 * <p>Unlike the rest of the suite this uses the real sandbox methods (a Mockito
 * {@code CALLS_REAL_METHODS} instance), because the case is about what the sandbox returns
 * after a reset.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class BoxResetCycleTest {
   @BeforeEach
   public void setUp() {
      harness = new LockCycleHarness();
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();
   }

   /**
    * The sandbox is left alone: the filter orders against the formula env's lock (#76918).
    */
   @Test
   public void noReset() throws Exception {
      race(setup(), ACTIVE_CAP);
   }

   /**
    * Bug #76961: the sandbox is reset or disposed after the filter was built, and optionally
    * gets a new env. Fixed by PR #5548: the filter locks the env it was built with.
    */
   @ParameterizedTest
   @EnumSource(After.class)
   public void boxChangedAfterBuild(After after) throws Exception {
      Setup setup = setup();

      switch(after) {
      case RESET:
         setup.box.reset();
         break;
      case RESET_NEW_ENV:
         setup.box.reset();
         ScriptEnv env2 = setup.box.getScriptEnv();
         env2.compile("1");
         assertNotSame(setup.env.getExecutionLock(), env2.getExecutionLock());
         break;
      case DISPOSE:
         setup.box.dispose();
         break;
      default:
         throw new IllegalArgumentException(after.name());
      }

      race(setup, ACTIVE_CAP);
   }

   private static Setup setup() throws Exception {
      Setup setup = new Setup();
      setup.box = Mockito.mock(AssetQuerySandbox.class, Mockito.CALLS_REAL_METHODS);
      Field field = AssetQuerySandbox.class.getDeclaredField("lock");
      field.setAccessible(true);
      field.set(setup.box, new Object());

      // pinned to pool off (bug #76960, spec §14.9): this case asserts the filter takes the
      // captured env's lock; PoolModeCycleTest.boxResetPooled is its pool-on equivalent.
      // The assertion documents intent: the mock skips field initializers, so the box's
      // pool mode is always off here and it cannot fail.
      assertFalse(setup.box.isScriptPoolMode());

      // AssetQuery builds the formula lens with box.getScriptEnv() before the filter
      setup.env = setup.box.getScriptEnv();
      setup.env.compile("1");

      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] {"a", "b"};

      for(int i = 1; i <= ROWS; i++) {
         data[i] = new Object[] {"k" + i, i};
      }

      TableLens formula = new FormulaTableLens(new DefaultTableLens(data), new String[] {"f"},
                                               new String[] {"1"}, setup.env, null);
      Condition condition = new Condition();
      condition.setOperation(Condition.GREATER_THAN);
      condition.addValue(-1);
      condition.setType(XSchema.INTEGER);
      ConditionGroup group = new ConditionGroup();
      // on the formula column
      group.addCondition(2, condition, 0);
      setup.filter = PostProcessor.filter(formula, group, setup.box);
      return setup;
   }

   /**
    * The #76918 shape: the script thread holds the formula env's lock, waits until the
    * populator is blocked on it, then reads the filter. Both must complete.
    */
   private void race(Setup setup, long cap) throws Exception {
      Lock lock = setup.env.getExecutionLock();
      assertNotNull(lock);
      AtomicReference<Thread> populatorThread = new AtomicReference<>();
      AtomicReference<Future<Boolean>> populator = new AtomicReference<>();

      Future<Boolean> script = harness.submit(() -> {
         lock.lock();

         try {
            populator.set(harness.submit(() -> {
               populatorThread.set(Thread.currentThread());
               return setup.filter.moreRows(ROWS);
            }));

            while(populatorThread.get() == null) {
               Thread.sleep(5);
            }

            // with the fix the populator waits for the lock outside the filter's monitor;
            // without it, it waits inside the monitor, running the formula
            awaitWaitingOnLock(populatorThread.get(), cap);
            return setup.filter.moreRows(ROWS);
         }
         finally {
            lock.unlock();
         }
      });

      assertTrue(harness.await(script, cap, "script thread holding the formula env's lock"));
      assertTrue(harness.await(populator.get(), cap, "populator"));
      assertFalse(setup.filter.moreRows(Integer.MAX_VALUE));
      assertEquals(ROWS + 1, setup.filter.getRowCount());
   }

   public enum After {
      RESET, RESET_NEW_ENV, DISPOSE
   }

   private static final class Setup {
      AssetQuerySandbox box;
      ScriptEnv env;
      TableLens filter;
   }

   private static final int ROWS = 2000;
   private LockCycleHarness harness;
}
