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
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Sandbox;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Slow;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Gate;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.SlowTable;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Started;
import inetsoft.report.filter.SumFormula;
import inetsoft.report.filter.SummaryFilter;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * Lock cycles between two sandboxes' engine locks. A lens graph cached in
 * {@code AssetDataCache} is keyed without the sandbox, so a lens built by sandbox 1 (whose
 * formula and condition filters use lock L1) can be read under a condition filter of
 * sandbox 2 (which holds L2), e.g. by two sessions of the same user.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class CrossSandboxCycleTest {
   @BeforeEach
   public void setUp() {
      harness = new LockCycleHarness();
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();
   }

   /**
    * Bug #76960 B, R2-X. A {@code SummaryFilter} over sandbox 1's filtered formula table is
    * first touched by sandbox 2's filter holder. {@code holdsScriptLock()} is true for any
    * held lock, so #5531's inline path runs {@code process()} under the summary's monitor.
    * Cycle: H holds L2 and the summary monitor and waits for L1 in the inner filter; T3 holds
    * L1 (sandbox 1's filter over the same summary) and waits for the summary monitor in
    * {@code SummaryFilter.waitForRow}.
    */
   @Test
   @Tag("known-deadlock")
   @EnabledIfSystemProperty(named = "lockcycle.known", matches = "true")
   public void summaryFirstTouchedByOtherSandbox() throws Exception {
      assumeFalse(POOL, INLINE_UNDER_SCRIPT_LOCK);
      runSummary(false, true, KNOWN_CAP);
   }

   /**
    * Bug #76960 B, R2-X′. H is a script thread of another engine (e.g. the viewsheet engine,
    * holding L2 inside {@code exec}) and first-touches the cached summary; script threads
    * always process inline under the summary's monitor. Cycle: H holds L2 and the summary
    * monitor and waits for L1; B holds L1 (sandbox 1's filter) and waits for the monitor.
    */
   @Test
   @Tag("known-deadlock")
   @EnabledIfSystemProperty(named = "lockcycle.known", matches = "true")
   public void summaryFirstTouchedByOtherScriptThread() throws Exception {
      Sandbox s1 = harness.sandbox();
      Sandbox s2 = harness.sandbox();
      Summary summary = summary(s1);

      Started<List<List<Object>>> h =
         harness.startGated(summary.gate, () -> s2.asGuest(() -> drain(summary.summary)));
      assertTrue(summary.gate.awaitEntered(KNOWN_CAP), "H never processed the summary inline first");
      Started<List<List<Object>>> b = harness.start(() -> drain(cf2(summary.summary, s1.box)));
      releaseAfter(summary.gate, b, KNOWN_CAP);

      assertEquals(summary.expectedOuter, harness.await(b.future, KNOWN_CAP, "B, sandbox 1's filter holder"));
      assertEquals(summary.expectedLens, harness.await(h.future, KNOWN_CAP, "H, the other engine's script thread"));
   }

   /**
    * Bug #76964 (R3), reversed nesting with no monitor-first lens. A reads sandbox 1's filter
    * over sandbox 2's cached filtered formula table; B reads sandbox 2's filter over sandbox
    * 1's. Cycle: A holds L1 and waits for L2 in the inner filter; B holds L2 and waits for L1.
    * No per-sandbox lock order can close this one.
    */
   @Test
   @Tag("known-deadlock")
   @EnabledIfSystemProperty(named = "lockcycle.known", matches = "true")
   public void reversedNesting() throws Exception {
      Sandbox c1 = harness.control();
      List<List<Object>> expected = harness.await(harness.submit(
         () -> drain(cf2(c1.filteredFormula(new SlowTable(ROWS, Slow.EVERYWHERE)), null))),
         ACTIVE_CAP, "control pipeline");

      Sandbox s1 = harness.sandbox();
      Sandbox s2 = harness.sandbox();
      TableLens readIn1 = cf2(s2.filteredFormula(new SlowTable(ROWS, Slow.EVERYWHERE)), s1.box);
      TableLens readIn2 = cf2(s1.filteredFormula(new SlowTable(ROWS, Slow.EVERYWHERE)), s2.box);
      CountDownLatch go = new CountDownLatch(1);
      Future<List<List<Object>>> a = harness.submit(() -> {
         go.await();
         return drain(readIn1);
      });
      Future<List<List<Object>>> b = harness.submit(() -> {
         go.await();
         return drain(readIn2);
      });
      go.countDown();

      assertEquals(expected, harness.await(a, KNOWN_CAP, "A, sandbox 1 over sandbox 2"));
      assertEquals(expected, harness.await(b, KNOWN_CAP, "B, sandbox 2 over sandbox 1"));
   }

   /**
    * The R2-X shape with sandbox 1's own filter holder touching first. It holds L1, so
    * #5531's synchronous path runs {@code process()} inline on its thread, and the inner
    * filter re-enters L1; this completes on main.
    */
   @Test
   public void summaryFirstTouchedByOwnSandbox() throws Exception {
      assumeFalse(POOL, INLINE_UNDER_SCRIPT_LOCK);
      runSummary(true, true, ACTIVE_CAP);
   }

   /**
    * Bug #76938 (fixed by #5531): both filter holders of the summary are sandbox 1's.
    */
   @Test
   public void summaryReadBySameSandbox() throws Exception {
      assumeFalse(POOL, INLINE_UNDER_SCRIPT_LOCK);
      runSummary(true, false, ACTIVE_CAP);
   }

   /**
    * @param ownFirst  sandbox 1's filter holder touches the summary first.
    * @param crossBox  the other holder is sandbox 2's (otherwise also sandbox 1's).
    */
   private void runSummary(boolean ownFirst, boolean crossBox, long cap) throws Exception {
      Sandbox s1 = harness.sandbox();
      Sandbox s2 = crossBox ? harness.sandbox() : s1;
      Summary summary = summary(s1);
      TableLens own = harness.track(cf2(summary.summary, s1.box));
      TableLens other = harness.track(cf2(summary.summary, s2.box));
      TableLens first = ownFirst ? own : other;
      TableLens second = ownFirst ? other : own;

      // the first holder holds a lock, so it processes the summary inline on its own thread,
      // where it parks at the gate inside the summary's monitor until the second holder has
      // arrived
      Started<List<List<Object>>> h = harness.startGated(summary.gate, () -> drain(first));
      assertTrue(summary.gate.awaitEntered(cap),
                 "the first filter holder never processed the summary inline first");
      Started<List<List<Object>>> t3 = harness.start(() -> drain(second));
      releaseAfter(summary.gate, t3, cap);

      assertEquals(summary.expectedOuter, harness.await(t3.future, cap, "the second filter holder"));
      assertEquals(summary.expectedOuter, harness.await(h.future, cap, "the first filter holder"));
      assertFalse(s1.lock.isLocked());
      assertFalse(s2.lock.isLocked());
   }

   /**
    * A summary (sum of value by group) over sandbox 1's filtered formula table, and the rows
    * of the same pipeline built without a lock.
    */
   private Summary summary(Sandbox s1) throws Exception {
      Sandbox control = harness.control();
      TableLens controlLens = harness.track(summaryOver(control.filteredFormula(new SlowTable(ROWS, Slow.EVERYWHERE))));
      Summary summary = new Summary();
      summary.expectedLens = harness.await(harness.submit(() -> drain(controlLens)), ACTIVE_CAP, "control summary");
      summary.expectedOuter = harness.await(harness.submit(() -> drain(cf2(controlLens, null))),
                                            ACTIVE_CAP, "control filter");
      assertTrue(summary.expectedLens.size() > 2, "control pipeline is empty");
      summary.gate = harness.gate();
      summary.summary = harness.track(summaryOver(s1.filteredFormula(
         new SlowTable(ROWS, Slow.EVERYWHERE, summary.gate))));
      return summary;
   }

   private static TableLens summaryOver(TableLens base) {
      return new SummaryFilter(base, new int[] {0}, new int[] {1}, new SumFormula(), null);
   }

   private static final class Summary {
      TableLens summary;
      Gate gate;
      List<List<Object>> expectedLens;
      List<List<Object>> expectedOuter;
   }

   /**
    * Why the runSummary cases are pinned to pool off (bug #76960, spec §14.9): they gate on
    * the first filter holder processing the summary inline, which SummaryFilter does only on
    * a thread holding a script lock. With the pool on a condition filter holds no lock, so
    * the premise cannot occur; PoolModeCycleTest.summaryReadConcurrently is the pool-on
    * equivalent.
    */
   static final String INLINE_UNDER_SCRIPT_LOCK =
      "pool off only: premise is that the first holder processes the summary inline under " +
      "a held script lock, which pool mode never takes";

   private static final int ROWS = 300;
   private LockCycleHarness harness;
}
