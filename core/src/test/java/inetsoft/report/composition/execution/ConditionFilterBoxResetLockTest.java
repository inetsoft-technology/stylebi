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
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.FormulaTableLens;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for bug #76961: {@code PostProcessor$ConditionFilter2} took the script
 * lock of whatever env its sandbox held <i>when it was read</i>, while the formula lens
 * below it keeps running on the env it was built with. A cached lens chain survives a
 * {@code reset()} (bookmark switch) or {@code dispose()} (viewsheet close) of the sandbox
 * that built it, and after that the filter took no lock, or the lock of a newer env, so
 * the #76918 deadlock came back.
 *
 * <p>Uses the real {@code ConditionFilter2} (via {@link PostProcessor#filter}), a real
 * {@link FormulaTableLens}, a real GraalJS env and the real sandbox methods (a Mockito
 * {@code CALLS_REAL_METHODS} instance, so no worksheet is needed). Thread B is a script
 * thread: it holds the formula env's lock and reads the filter. Thread A populates the
 * filter from outside script, which runs the formula. Both are daemons with bounded joins,
 * so a regression fails the test instead of hanging the build.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class ConditionFilterBoxResetLockTest {
   /**
    * The sandbox is left alone: the harness itself must not deadlock.
    */
   @Test
   public void noResetCompletes() throws Exception {
      Setup setup = setup();
      race(setup);
   }

   /**
    * The sandbox is reset or disposed after the filter was built, and optionally gets a
    * new env: the filter must still order against the formula env's lock.
    */
   @ParameterizedTest
   @EnumSource(After.class)
   public void boxChangedAfterBuildCompletes(After after) throws Exception {
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

      race(setup);
   }

   private static Setup setup() throws Exception {
      Setup setup = new Setup();
      setup.box = Mockito.mock(AssetQuerySandbox.class, Mockito.CALLS_REAL_METHODS);
      Field field = AssetQuerySandbox.class.getDeclaredField("lock");
      field.setAccessible(true);
      field.set(setup.box, new Object());

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
    * The #76918 shape: B holds the formula env's lock, waits until A is blocked on it,
    * then reads the filter. Both must complete.
    */
   private static void race(Setup setup) throws Exception {
      Lock lock = setup.env.getExecutionLock();
      assertNotNull(lock);
      Thread populator = new Thread(() -> setup.filter.moreRows(ROWS), "populator");
      populator.setDaemon(true);
      Thread script = new Thread(() -> {
         lock.lock();

         try {
            populator.start();
            long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(TIMEOUT);

            while(System.currentTimeMillis() < deadline && !waitingOnLock(populator)) {
               Thread.sleep(5);
            }

            setup.filter.moreRows(ROWS);
         }
         catch(InterruptedException ignore) {
         }
         finally {
            lock.unlock();
         }
      }, "script");
      script.setDaemon(true);
      script.start();
      script.join(TimeUnit.SECONDS.toMillis(TIMEOUT));
      populator.join(TimeUnit.SECONDS.toMillis(TIMEOUT));

      assertFalse(script.isAlive() || populator.isAlive(), () ->
         "deadlock:\n" + stack(populator) + stack(script));
      assertFalse(setup.filter.moreRows(Integer.MAX_VALUE));
      assertEquals(ROWS + 1, setup.filter.getRowCount());
   }

   private static boolean waitingOnLock(Thread thread) {
      if(thread.getState() != Thread.State.WAITING) {
         return false;
      }

      for(StackTraceElement element : thread.getStackTrace()) {
         if(element.getClassName().endsWith("LendableReentrantLock") &&
            element.getMethodName().equals("lock"))
         {
            return true;
         }
      }

      return false;
   }

   private static String stack(Thread thread) {
      StringBuilder buf = new StringBuilder(thread.getName()).append(' ')
         .append(thread.getState()).append('\n');

      for(StackTraceElement element : thread.getStackTrace()) {
         buf.append("   at ").append(element).append('\n');
      }

      return buf.toString();
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
   private static final long TIMEOUT = 5;
}
