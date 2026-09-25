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
import inetsoft.report.filter.SumFormula;
import inetsoft.report.filter.SummaryFilter;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.DistinctTableLens;
import inetsoft.report.lens.FormulaTableLens;
import inetsoft.report.script.formula.AssetQueryScope;
import inetsoft.test.*;
import inetsoft.uql.Condition;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.SubQueryValue;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.UpgradableReadWriteLock;
import inetsoft.util.script.JavaScriptEngine;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.SlotClaim;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Pool-on equivalents (bug #76960, spec §14.9, gate 1) of the suite cases that hold a raw
 * engine lock or build a raw env: with pooled contexts a thread inside a script holds a claim,
 * and nobody ever waits for it. They assert completion, G8 and G4 instead of lock identity.
 * They always run pooled, whatever -Dlockcycle.pool says.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class PoolModeCycleTest {
   @BeforeEach
   public void setUp() {
      harness = new LockCycleHarness();
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();
   }

   /**
    * ScriptThreadGuardCycleTest.filterLockHolderVsSandboxWriter, pooled (spec §14.3): H holds
    * a batch claim outside exec, as a filter does across its rows, so it is not a script
    * thread and really blocks reading S while W holds S for writing; W, holding S, then runs
    * a script. Pool off, H holds E and W waits for E: the known cycle. Pooled, W's checkout
    * skips H's busy context and takes another one without waiting, so W finishes and
    * releases S, and H reads it.
    */
   @Test
   public void scriptHolderVsSandboxWriter() throws Exception {
      UpgradableReadWriteLock s = new UpgradableReadWriteLock(JavaScriptEngine::isScriptThread);
      WorksheetScriptEnv env = PoolTestSupport.env();
      CountDownLatch holderIn = new CountDownLatch(1);
      CountDownLatch writerHasS = new CountDownLatch(1);

      try {
         Future<Void> holder = submit(() -> {
            try(SlotClaim claim = env.claimSlot()) {
               // the premise: a claim held outside exec leaves H a non-script thread, so the
               // S read below takes the blocking path
               assertFalse(JavaScriptEngine.isScriptThread(), "H must not be a script thread");
               holderIn.countDown();
               assertTrue(writerHasS.await(KNOWN_CAP, TimeUnit.SECONDS));
               s.lockRead();
               s.unlockRead();
            }

            return null;
         });
         Future<Object> writer = submit(() -> {
            assertTrue(holderIn.await(KNOWN_CAP, TimeUnit.SECONDS));
            s.lockWrite();

            try {
               writerHasS.countDown();
               // needs a context while H holds its claimed one
               return PoolTestSupport.run(env, "1 + 1");
            }
            finally {
               s.unlockWrite();
            }
         });

         assertEquals(2.0, harness.await(writer, ACTIVE_CAP, "W, the S writer running a script"));
         harness.await(holder, ACTIVE_CAP, "H, the claim holder reading S");
      }
      finally {
         env.retire();
      }
   }

   /**
    * SubQueryConditionCycleTest.formulaSubTableUnderPlainBase, pooled: a script thread holds
    * a claimed context while a populator drains a filter whose sub-query table is a formula
    * lens on the same env.
    */
   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   public void formulaSubTableUnderPlainBase(boolean distinct) throws Exception {
      WorksheetScriptEnv senv = PoolTestSupport.env();

      try {
         formulaSubTableUnderPlainBase(distinct, senv);
      }
      finally {
         senv.retire();
      }
   }

   private void formulaSubTableUnderPlainBase(boolean distinct, WorksheetScriptEnv senv)
      throws Exception
   {
      AssetQuerySandbox box = new AssetQuerySandbox(null);
      setField(box, "senv", senv);
      setField(box, "scriptPoolMode", true);

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

      Future<Boolean> script = submit(() -> {
         try(SlotClaim claim = senv.claimSlot()) {
            Future<Boolean> populator = submit(() -> filtered.moreRows(1));
            assertTrue(harness.await(populator, ACTIVE_CAP, "populator"));
            return filtered.moreRows(1);
         }
      });

      assertTrue(harness.await(script, ACTIVE_CAP, "script thread holding a claimed context"));
   }

   /**
    * BoxResetCycleTest / ConditionFilterBoxResetLockTest, pooled: the sandbox is reset or
    * disposed after the filter was built. The env stays pooled (G8), the filter takes no lock,
    * and a script thread holding a claimed context and a populator both finish (G4).
    */
   @ParameterizedTest
   @EnumSource(After.class)
   public void boxResetPooled(After after) throws Exception {
      AssetQuerySandbox box = PoolTestSupport.poolBox(true);
      ScriptEnv env = box.getScriptEnv();
      assertTrue(env instanceof WorksheetScriptEnv);
      assertNull(env.getExecutionLock());

      Object[][] data = new Object[ROWS + 1][];
      data[0] = new Object[] {"a", "b"};

      for(int i = 1; i <= ROWS; i++) {
         data[i] = new Object[] {"k" + i, i};
      }

      TableLens formula = new FormulaTableLens(new DefaultTableLens(data), new String[] {"f"},
                                               new String[] {"1"}, env, null);
      Condition cond = new Condition();
      cond.setOperation(Condition.GREATER_THAN);
      cond.addValue(-1);
      cond.setType(XSchema.INTEGER);
      ConditionGroup group = new ConditionGroup();
      group.addCondition(2, cond, 0);
      TableLens filter = harness.track(PostProcessor.filter(formula, group, box));

      switch(after) {
      case RESET:
         box.reset();
         break;
      case RESET_NEW_ENV:
         box.reset();
         ScriptEnv env2 = box.getScriptEnv();

         try {
            assertTrue(env2 instanceof WorksheetScriptEnv, "G8: the recreated env is pooled too");
            assertNotSame(env, env2);
         }
         finally {
            if(env2 instanceof WorksheetScriptEnv pooled2) {
               pooled2.retire();
            }
         }

         break;
      case DISPOSE:
         box.dispose();
         break;
      default:
         throw new IllegalArgumentException(after.name());
      }

      Future<Boolean> script = submit(() -> {
         try(SlotClaim claim = ((WorksheetScriptEnv) env).claimSlot()) {
            Future<Boolean> populator = submit(() -> filter.moreRows(ROWS));
            assertTrue(harness.await(populator, ACTIVE_CAP, "populator"));
            return filter.moreRows(ROWS);
         }
      });

      assertTrue(harness.await(script, ACTIVE_CAP, "script thread holding a claimed context"));
      assertFalse(filter.moreRows(Integer.MAX_VALUE));
      assertEquals(ROWS + 1, filter.getRowCount());
   }

   /**
    * GuestReaderCycleTest.assetQueryScopeUnderEngineLock, with truly concurrent contexts: four
    * threads run scripts on their own pooled contexts over one AssetQueryScope (spec §6.1).
    */
   @Test
   public void assetQueryScopeUnderConcurrentContexts() throws Exception {
      WorksheetScriptEnv env = PoolTestSupport.env();

      try {
         AssetQuerySandbox box = mock(AssetQuerySandbox.class);
         doReturn(new Worksheet()).when(box).getWorksheet();
         AssetQueryScope scope = new AssetQueryScope(box);
         Field field = AssetQueryScope.class.getDeclaredField("tablemap");
         field.setAccessible(true);
         int tables = ((Map<?, ?>) field.get(scope)).size();
         CountDownLatch go = new CountDownLatch(1);
         List<Future<Void>> threads = new ArrayList<>();

         for(int t = 0; t < 4; t++) {
            int id = t;
            threads.add(submit(() -> {
               go.await();

               for(int i = 0; i < 500; i++) {
                  Object script = env.compile("typeof id_" + id + "_" + i + "; m_" + id + "_" +
                                              i + " = " + i + ";");
                  env.exec(script, scope, null, null);
               }

               return null;
            }));
         }

         go.countDown();

         for(Future<Void> thread : threads) {
            harness.await(thread, ACTIVE_CAP, "script thread");
         }

         assertEquals(tables + 2 * 4 * 500, ((Map<?, ?>) field.get(scope)).size(),
                      "lost tablemap entries");
      }
      finally {
         env.retire();
      }
   }

   /**
    * CrossSandboxCycleTest's runSummary cases, pooled (they are pinned to pool off because
    * they gate on the first filter holder processing the summary inline under a held script
    * lock, which never happens with the pool on). Two condition filters, of sandbox 1 and of
    * sandbox 2 or both of sandbox 1, read one SummaryFilter over sandbox 1's filtered formula
    * table. The first touches it first; the second arrives while the summary is still being
    * processed. Both finish and get the rows of a lock-free control pipeline, and a later
    * reader gets the same rows without the summary reading its base again.
    */
   @ParameterizedTest
   @EnumSource(SummaryShape.class)
   public void summaryReadConcurrently(SummaryShape shape) throws Exception {
      WorksheetScriptEnv env1 = PoolTestSupport.env();
      WorksheetScriptEnv env2 = shape.crossBox ? PoolTestSupport.env() : env1;
      WorksheetScriptEnv controlEnv = PoolTestSupport.env();

      try {
         AssetQuerySandbox box1 = pooledBox(env1);
         AssetQuerySandbox box2 = shape.crossBox ? pooledBox(env2) : box1;

         TableLens controlSummary = harness.track(summaryOver(cf2(formula(
            new SlowTable(SUMMARY_ROWS, Slow.EVERYWHERE), controlEnv), null)));
         List<List<Object>> expected = harness.await(
            submit(() -> drain(cf2(controlSummary, null))), ACTIVE_CAP, "control filter");
         assertTrue(expected.size() > 2, "control pipeline is empty");

         HookTable base = new HookTable(SUMMARY_ROWS);
         TableLens summary = harness.track(summaryOver(cf2(formula(base, env1), box1)));
         TableLens own = harness.track(cf2(summary, box1));
         TableLens other = harness.track(cf2(summary, box2));
         TableLens first = shape.ownFirst ? own : other;
         TableLens second = shape.ownFirst ? other : own;

         Started<List<List<Object>>> h = harness.start(noClaimLeft(() -> drain(first)));
         assertTrue(base.entered.await(ACTIVE_CAP, TimeUnit.SECONDS),
                    "the summary never started processing its base");
         Started<List<List<Object>>> t3 = harness.start(noClaimLeft(() -> drain(second)));
         awaitParked(t3, KNOWN_CAP);
         base.release.countDown();

         assertEquals(expected, harness.await(t3.future, ACTIVE_CAP, "the second filter holder"));
         assertEquals(expected, harness.await(h.future, ACTIVE_CAP, "the first filter holder"));
         // processed once: a later reader gets the same rows without another pass over the base
         int passes = base.passes.get();
         assertEquals(expected, drain(cf2(summary, box2)), "a later reader sees other rows");
         assertEquals(passes, base.passes.get(), "the summary processed its base again");
      }
      finally {
         env1.retire();
         env2.retire();
         controlEnv.retire();
      }
   }

   /**
    * Submit {@code task} to the harness, and check on its harness thread that it left no
    * worksheet script claim open.
    */
   private <T> Future<T> submit(Callable<T> task) {
      return harness.submit(noClaimLeft(task));
   }

   private static <T> Callable<T> noClaimLeft(Callable<T> task) {
      return () -> {
         T result = task.call();
         assertEquals(0, SlotClaim.openClaims(), "a claim was left open on a harness thread");
         return result;
      };
   }

   private static AssetQuerySandbox pooledBox(WorksheetScriptEnv env) {
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      stubScriptLock(box, env);
      return box;
   }

   private static FormulaTableLens formula(TableLens base, ScriptEnv env) {
      return new FormulaTableLens(base, new String[] {"f1"}, new String[] {"1"}, env, null);
   }

   private static TableLens summaryOver(TableLens base) {
      return new SummaryFilter(base, new int[] {0}, new int[] {1}, new SumFormula(), null);
   }

   /**
    * A slow table that, at its first data read (on whatever thread processes the summary),
    * signals {@link #entered} and waits for {@link #release}, and counts reads of its first
    * data row's value cell.
    */
   private static final class HookTable extends SlowTable {
      HookTable(int rows) {
         super(rows, Slow.EVERYWHERE);
      }

      @Override
      public Object getObject(int r, int c) {
         if(r == 1 && c == 1) {
            passes.incrementAndGet();

            if(entered.getCount() > 0) {
               entered.countDown();

               try {
                  release.await(3 * KNOWN_CAP, TimeUnit.SECONDS);
               }
               catch(InterruptedException ex) {
                  Thread.currentThread().interrupt();
               }
            }
         }

         return super.getObject(r, c);
      }

      final CountDownLatch entered = new CountDownLatch(1);
      final CountDownLatch release = new CountDownLatch(1);
      final java.util.concurrent.atomic.AtomicInteger passes =
         new java.util.concurrent.atomic.AtomicInteger();
   }

   /**
    * The three runSummary shapes of CrossSandboxCycleTest.
    */
   public enum SummaryShape {
      /** summaryFirstTouchedByOtherSandbox */
      OTHER_SANDBOX_FIRST(false, true),
      /** summaryFirstTouchedByOwnSandbox */
      OWN_SANDBOX_FIRST(true, true),
      /** summaryReadBySameSandbox */
      SAME_SANDBOX(true, false);

      SummaryShape(boolean ownFirst, boolean crossBox) {
         this.ownFirst = ownFirst;
         this.crossBox = crossBox;
      }

      final boolean ownFirst;
      final boolean crossBox;
   }

   private static void setField(AssetQuerySandbox box, String name, Object value)
      throws Exception
   {
      Field field = AssetQuerySandbox.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(box, value);
   }

   public enum After {
      RESET, RESET_NEW_ENV, DISPOSE
   }

   private static final int ROWS = 2000;
   private static final int SUMMARY_ROWS = 300;
   private LockCycleHarness harness;
}
