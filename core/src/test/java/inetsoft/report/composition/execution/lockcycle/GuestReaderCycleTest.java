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
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.SlowTable;
import inetsoft.report.filter.AbstractConditionFilter;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.lens.*;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Cycles whose lock holder is a true guest: a thread inside {@code exec} holding the
 * engine lock E, as a calc-field formula reading the current row, or a worksheet/viewsheet
 * script reading a whole table, rather than a condition-filter holder. A guest cannot lend
 * its lock ({@code canLendScriptLocks} refuses inside {@code exec}), so #5531's lending does
 * not help it. Also the #76918 shapes with real calc-field formulas, and a race between a
 * reader and {@code invalidate()} that a lock-free read path would have to survive.
 *
 * <p>Formulas here read the base ({@code field['value'] + 1}), so computing them is slow
 * and runs while the formula lens holds its own lock.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class GuestReaderCycleTest {
   @BeforeEach
   public void setUp() {
      harness = new LockCycleHarness();
   }

   @AfterEach
   public void tearDown() throws Exception {
      harness.close();
   }

   /**
    * Bug #76918, the jstack shape with real classes: A populates a condition filter over a
    * calc-field formula lens (filter monitor → E in {@code exec}); B is a real guest, a
    * formula lens over the same filter whose formula reads the current row (E → filter
    * monitor). Fixed by #5506's engine-first order.
    */
   @Test
   public void calcFieldGuestReadsCurrentRow() throws Exception {
      runPopulatorAndGuest(s -> cf2(calcField(s, new SlowTable(ROWS, Slow.EVERYWHERE)), s.box),
                           ACTIVE_CAP);
   }

   /**
    * Bug #76918, forward read: a guest (a script reading another table assembly) reads rows
    * the populator has not reached yet, holding E. Fixed by #5506's engine-first order.
    */
   @Test
   public void guestReadsAheadOfPopulator() throws Exception {
      Sandbox control = harness.control();
      int expected = harness.await(harness.submit(() -> {
         TableLens cf = cf2(calcField(control, new SlowTable(ROWS, Slow.EVERYWHERE)), null);
         cf.moreRows(TableLens.EOT);
         return cf.getRowCount();
      }), ACTIVE_CAP, "control pipeline");

      Sandbox s = harness.sandbox();
      TableLens cf = harness.track(cf2(calcField(s, new SlowTable(ROWS, Slow.EVERYWHERE)), s.box));
      Future<List<List<Object>>> populator = harness.submit(() -> drain(cf));
      Thread.sleep(80);
      Future<Integer> guest = harness.submit(() -> s.asGuest(() -> {
         cf.moreRows(ROWS - 1);
         cf.moreRows(TableLens.EOT);
         return cf.getRowCount();
      }));

      assertEquals(expected, (int) harness.await(guest, ACTIVE_CAP, "guest reading ahead"));
      assertEquals(expected, harness.await(populator, ACTIVE_CAP, "populator").size());
   }

   /**
    * Design refutation UNION_CF2: a non-distinct union of two calc-field formula lenses
    * under a filter, read by a guest formula. A populates the filter (filter monitor →
    * {@code UnionTableLens} monitor → E in {@code exec}); B's current-row read goes
    * filter → {@code Union.getObject} → {@code Union.moreRows} → Union monitor while B holds
    * E. On main this hangs in some runs (a race), not in all.
    */
   @Test
   @Tag("known-deadlock")
   @EnabledIfSystemProperty(named = "lockcycle.known", matches = "true")
   public void unionOfFormulasUnderGuest() throws Exception {
      runPopulatorAndGuest(s -> cf2(union(calcField(s, new SlowTable(UNION_ROWS, Slow.EVERYWHERE)),
                                          calcField(s, new SlowTable(UNION_ROWS, Slow.EVERYWHERE))), s.box),
                           ACTIVE_CAP);
   }

   /**
    * Design refutation UNIONI_CF2: {@code FTL(CF2(Union(CF2(FTL), CF2(FTL))))}, a
    * concatenation of two conditioned sub-tables with expression columns, filtered again and
    * read by a guest formula of the current row. Completes on main (the inner filters take E
    * before the union's monitor is needed again); pinned for the redesign.
    */
   @Test
   public void unionOfFilteredFormulasUnderGuest() throws Exception {
      runPopulatorAndGuest(s -> cf2(union(cf2(calcField(s, new SlowTable(ROWS, Slow.EVERYWHERE)), s.box),
                                          cf2(calcField(s, new SlowTable(ROWS, Slow.EVERYWHERE)), s.box)),
                                    s.box),
                           ACTIVE_CAP);
   }

   /**
    * Design refutation INV_RACE: a reader of already-mapped rows racing with
    * {@code invalidate()} and re-population of the same condition filter. Every read must
    * return the right value and never throw. On main a read occasionally throws
    * {@code ArrayIndexOutOfBoundsException} (a wrong result, not a hang), so this is known.
    */
   @Test
   @Tag("known-deadlock")
   @EnabledIfSystemProperty(named = "lockcycle.known", matches = "true")
   public void mappedRowReadsDuringInvalidate() throws Exception {
      Sandbox s = harness.sandbox();
      TableLens cf = cf2(new SlowTable(INV_ROWS, Slow.NONE), s.box);
      drain(cf);
      AtomicBoolean stop = new AtomicBoolean();
      Future<Integer> invalidator = harness.submit(() -> {
         int n = 0;

         while(!stop.get()) {
            ((AbstractConditionFilter) cf).invalidate();
            drain(cf);
            n++;
         }

         return n;
      });
      Future<Map<String, Integer>> reader = harness.submit(() -> {
         Map<String, Integer> bad = new TreeMap<>();
         Random random = new Random(1);
         long end = System.currentTimeMillis() + 3000;

         try {
            while(System.currentTimeMillis() < end) {
               int r = 1 + random.nextInt(INV_ROWS);

               try {
                  Object value = cf.getObject(r, 1);

                  if(!Integer.valueOf(r % 30).equals(value)) {
                     bad.merge("wrong value " + value, 1, Integer::sum);
                  }
               }
               catch(Throwable ex) {
                  bad.merge(ex.getClass().getSimpleName(), 1, Integer::sum);
               }
            }
         }
         finally {
            stop.set(true);
         }

         return bad;
      });

      assertEquals(Collections.emptyMap(), harness.await(reader, KNOWN_CAP, "reader"));
      assertTrue(harness.await(invalidator, KNOWN_CAP, "invalidator") > 0);
   }

   /**
    * Bug #76960 A2 with a guest holder: a script holding E reads a hash join whose inputs are
    * filtered formula tables. It waits in {@code XSwappableTable.moreRows} for the
    * JoinThreads, which wait for E in the input filters. A guest cannot lend.
    */
   @Test
   @Tag("known-deadlock")
   @EnabledIfSystemProperty(named = "lockcycle.known", matches = "true")
   public void guestReadsHashJoin() throws Exception {
      harness.forceHashJoin();
      Function<Sandbox, TableLens> build = s -> harness.track(new JoinTableLens(
         s.filteredFormula(new SlowTable(JOIN_ROWS, Slow.WORKERS)),
         s.filteredFormula(new SlowTable(JOIN_ROWS, Slow.WORKERS)),
         new int[] {1}, new int[] {1}, JoinTableLens.INNER_JOIN, true));
      Sandbox control = harness.control();
      List<String> expected = sorted(harness.await(
         harness.submit(() -> drain(build.apply(control))), ACTIVE_CAP, "control join"));

      Sandbox s = harness.sandbox();
      TableLens join = harness.await(harness.submit(() -> build.apply(s)), ACTIVE_CAP, "build");
      Future<List<List<Object>>> guest = harness.submit(() -> s.asGuest(() -> drain(join)));

      assertEquals(expected, sorted(harness.await(guest, KNOWN_CAP, "guest reading the join")));
   }

   /**
    * Bug #76960 B (R2) with a guest: T1 sorts a shared {@code SortFilter} over a filtered
    * formula table, holding the sort monitor and waiting for E; the guest holds E and waits
    * for the sort monitor.
    */
   @Test
   @Tag("known-deadlock")
   @EnabledIfSystemProperty(named = "lockcycle.known", matches = "true")
   public void guestReadsSharedSort() throws Exception {
      Function<Sandbox, TableLens> build = s -> harness.track(new SortFilter(
         s.filteredFormula(new SlowTable(ROWS, Slow.EVERYWHERE)), new int[] {1}));
      Sandbox control = harness.control();
      List<List<Object>> expected = harness.await(
         harness.submit(() -> drain(build.apply(control))), ACTIVE_CAP, "control sort");

      Sandbox s = harness.sandbox();
      TableLens sort = build.apply(s);
      Future<List<List<Object>>> t1 = harness.submit(() -> drain(sort));
      Thread.sleep(60);
      Future<List<List<Object>>> guest = harness.submit(() -> s.asGuest(() -> drain(sort)));

      assertEquals(expected, harness.await(guest, KNOWN_CAP, "guest reading the sort"));
      assertEquals(expected, harness.await(t1, KNOWN_CAP, "T1, the unlocked sorter"));
   }

   /**
    * Bug #76964 (R3) with guests: a script of sandbox 1 (holding L1) reads sandbox 2's cached
    * filtered formula table (needs L2), while a script of sandbox 2 (holding L2) reads
    * sandbox 1's (needs L1).
    */
   @Test
   @Tag("known-deadlock")
   @EnabledIfSystemProperty(named = "lockcycle.known", matches = "true")
   public void guestsReadEachOthersCachedTable() throws Exception {
      Sandbox control = harness.control();
      List<List<Object>> expected = harness.await(harness.submit(
         () -> drain(control.filteredFormula(new SlowTable(ROWS, Slow.EVERYWHERE)))),
         ACTIVE_CAP, "control table");

      Sandbox s1 = harness.sandbox();
      Sandbox s2 = harness.sandbox();
      TableLens builtBy1 = s1.filteredFormula(new SlowTable(ROWS, Slow.EVERYWHERE));
      TableLens builtBy2 = s2.filteredFormula(new SlowTable(ROWS, Slow.EVERYWHERE));
      CountDownLatch bothInScript = new CountDownLatch(2);
      Future<List<List<Object>>> a = harness.submit(() -> s1.asGuest(() -> {
         bothInScript.countDown();
         bothInScript.await();
         return drain(builtBy2);
      }));
      Future<List<List<Object>>> b = harness.submit(() -> s2.asGuest(() -> {
         bothInScript.countDown();
         bothInScript.await();
         return drain(builtBy1);
      }));

      assertEquals(expected, harness.await(a, KNOWN_CAP, "sandbox 1's script"));
      assertEquals(expected, harness.await(b, KNOWN_CAP, "sandbox 2's script"));
   }

   /**
    * A drains {@code build}'s filter; B, at the same time, drains a formula lens over it
    * whose formula reads the current row ({@code field['value'] * 2 + field['f']}), so B
    * reads the filter inside {@code exec}. Both must see the rows of the same pipeline built
    * without a lock.
    */
   private void runPopulatorAndGuest(Function<Sandbox, TableLens> build, long cap)
      throws Exception
   {
      Sandbox control = harness.control();
      TableLens controlCf = harness.track(build.apply(control));
      List<List<Object>> expectedCf = harness.await(
         harness.submit(() -> drain(controlCf)), ACTIVE_CAP, "control filter");
      List<List<Object>> expectedOuter = harness.await(
         harness.submit(() -> drain(guestFormula(control, controlCf))), ACTIVE_CAP, "control guest");

      Sandbox s = harness.sandbox();
      TableLens cf = harness.track(build.apply(s));
      TableLens outer = guestFormula(s, cf);
      Future<List<List<Object>>> a = harness.submit(() -> drain(cf));
      Future<List<List<Object>>> b = harness.submit(() -> drain(outer));

      assertEquals(expectedOuter, harness.await(b, cap, "B, the guest formula reader"));
      assertEquals(expectedCf, harness.await(a, cap, "A, the filter populator"));
      assertFalse(s.lock.isLocked());
   }

   private static TableLens calcField(Sandbox s, TableLens base) {
      return s.formula(base, "f", "field['value'] + 1");
   }

   private static TableLens guestFormula(Sandbox s, TableLens table) {
      return s.formula(table, "g", "field['value'] * 2 + field['f']");
   }

   private static TableLens union(TableLens left, TableLens right) {
      UnionTableLens union = new UnionTableLens(left, right);
      union.setDistinct(false);
      return union;
   }

   private static List<String> sorted(List<List<Object>> rows) {
      return rows.stream().map(Object::toString).sorted().collect(Collectors.toList());
   }

   private static final int ROWS = 100;
   private static final int JOIN_ROWS = 120;
   private static final int INV_ROWS = 300;
   private static final int UNION_ROWS = 300;
   private LockCycleHarness harness;
}
