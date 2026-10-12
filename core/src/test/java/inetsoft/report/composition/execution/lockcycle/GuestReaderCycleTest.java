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
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Gate;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.SlowTable;
import inetsoft.report.composition.execution.lockcycle.LockCycleHarness.Started;
import inetsoft.report.filter.AbstractConditionFilter;
import inetsoft.report.filter.DefaultTableFilter;
import inetsoft.report.filter.SortFilter;
import inetsoft.report.lens.*;
import inetsoft.report.script.formula.AssetQueryScope;
import inetsoft.test.*;
import inetsoft.uql.asset.Worksheet;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import static inetsoft.report.composition.execution.lockcycle.LockCycleHarness.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

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
@Tag("slow")
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
      Gate gate = harness.gate();
      TableLens cf = harness.track(cf2(calcField(s, new SlowTable(ROWS, Slow.EVERYWHERE, gate)), s.box));
      // the populator parks at its first base read, inside the filter; the guest then reads
      // ahead of it
      Started<List<List<Object>>> populator = harness.startGated(gate, () -> drain(cf));
      assertTrue(gate.awaitEntered(ACTIVE_CAP), "the populator never read the base");
      Started<Integer> guest = harness.start(() -> s.asGuest(() -> {
         cf.moreRows(ROWS - 1);
         cf.moreRows(TableLens.EOT);
         return cf.getRowCount();
      }));
      releaseAfter(gate, guest, ACTIVE_CAP);

      assertEquals(expected, (int) harness.await(guest.future, ACTIVE_CAP, "guest reading ahead"));
      assertEquals(expected, harness.await(populator.future, ACTIVE_CAP, "populator").size());
   }

   /**
    * Design refutation UNION_CF2: a non-distinct union of two calc-field formula lenses
    * under a filter, read by a guest formula. A populates the filter (filter monitor →
    * {@code UnionTableLens} monitor → E in {@code exec}); B's current-row read goes
    * filter → {@code Union.getObject} → {@code Union.moreRows} → Union monitor while B holds
    * E. A single run hangs only sometimes on main (1 of 7 runs here, 1 of 3 in the
    * refuter's), so this is a stress case: it repeats the shape {@code UNION_ITERATIONS}
    * times, each on a fresh pipeline and sandbox, within {@code UNION_TOTAL_CAP} seconds.
    * The redesign must make every iteration complete.
    */
   @Test
   @Tag("known-deadlock")
   @EnabledIfSystemProperty(named = "lockcycle.known", matches = "true")
   public void unionOfFormulasUnderGuest() throws Exception {
      long deadline = System.currentTimeMillis() + UNION_TOTAL_CAP * 1000;

      for(int i = 0; i < UNION_ITERATIONS; i++) {
         long left = (deadline - System.currentTimeMillis()) / 1000;
         assertTrue(left > 0, "stress case exceeded its total cap after " + i + " iterations");
         runPopulatorAndGuest(s -> cf2(union(calcField(s, new SlowTable(UNION_ROWS, Slow.EVERYWHERE)),
                                             calcField(s, new SlowTable(UNION_ROWS, Slow.EVERYWHERE))), s.box),
                              Math.min(ACTIVE_CAP, left), "iteration " + (i + 1) + ": ");
      }
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
    * Regression test for bug #76972, over a formula table. Not a lock cycle: a reader of data
    * rows races with {@code invalidate()} and re-population of the same condition filter.
    * {@code getBaseRowIndex} used to read a {@code rowmap} snapshot after {@code moreRows} had
    * released both the engine lock and the filter's monitor; once its bounded retries were
    * exhausted with the row still unmapped, it indexed past the map's count and
    * {@code XIntFragment.getSafely} silently returned 0, which maps to the header row -- a
    * wrong value with no signal. {@code getBaseRowIndex} now falls back to one snapshot taken
    * under the filter's own monitor and either returns the mapped row or throws
    * {@code IndexOutOfBoundsException}, so every read here must return the right value or
    * throw -- never a silently wrong one.
    */
   @Test
   public void mappedRowReadsDuringInvalidate() throws Exception {
      Sandbox s = harness.sandbox();
      assertEquals(Collections.emptyMap(),
                   invalidateRace(cf2(s.formula(new SlowTable(INV_ROWS, Slow.NONE)), s.box), ACTIVE_CAP));
   }

   /**
    * Regression test for bug #76972, over a formula-free base -- a wrong result rather than a
    * hang. {@code getBaseRowIndex} reads {@code rowmap} after {@code moreRows} returns, while
    * {@code invalidate()} concurrently swaps in a new list holding only the header entries.
    * This filter takes no engine lock, so nothing slows the two threads down and the race used
    * to fire on every run: once retries were exhausted with the row still unmapped, reading
    * past the new map's count silently came back as 0, the header value ({@code "value"}),
    * instead of the real row. {@code getBaseRowIndex} now falls back to a snapshot taken under
    * the filter's own monitor and either returns the mapped row or throws
    * {@code IndexOutOfBoundsException}, so every read here must return the right value or
    * throw.
    */
   @Test
   public void mappedRowReadsDuringInvalidateWithoutLock() throws Exception {
      Sandbox s = harness.sandbox();
      assertEquals(Collections.emptyMap(),
                   invalidateRace(cf2(new SlowTable(INV_ROWS, Slow.NONE), s.box), ACTIVE_CAP));
   }

   /**
    * Invalidate and re-drain {@code cf} on one thread while another reads random data rows of
    * column 1 for 3 s.
    *
    * @return the wrong values and exceptions the reader saw, by kind.
    */
   private Map<String, Integer> invalidateRace(TableLens cf, long cap) throws Exception {
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
         int reads = 0;

         try {
            while(System.currentTimeMillis() < end) {
               int r = 1 + random.nextInt(INV_ROWS);

               try {
                  Object value = cf.getObject(r, 1);
                  reads++;

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

         assertTrue(reads > 0, "the reader never read a row during the invalidate storm");
         return bad;
      });

      Map<String, Integer> bad = harness.await(reader, cap, "reader");
      assertTrue(harness.await(invalidator, cap, "invalidator") > 0);
      return bad;
   }

   /**
    * Design refutation AQS_RACE: four threads run scripts through the real
    * {@code GraalJavaScriptEngine.exec} with one real {@link AssetQueryScope} as the scope.
    * Each script looks up an unknown name and assigns another; the engine resolves both
    * through {@code AssetQueryScope.hasMember}, which caches every name in the plain-map
    * {@code tablemap} (the assignment itself stays in the engine). The scope is not
    * thread-safe; without the engine lock the refuter's
    * probe lost entries. Here the lock comes from {@code exec} itself, not from the test, so a
    * redesign that lets scripts of one sandbox run concurrently on this scope fails this case.
    */
   @Test
   public void assetQueryScopeUnderEngineLock() throws Exception {
      Sandbox s = harness.sandbox();
      AssetQuerySandbox box = mock(AssetQuerySandbox.class);
      doReturn(new Worksheet()).when(box).getWorksheet();
      AssetQueryScope scope = new AssetQueryScope(box);
      int tables = mapField(scope, "tablemap").size();
      CountDownLatch go = new CountDownLatch(1);
      List<Future<Void>> threads = new ArrayList<>();

      for(int t = 0; t < AQS_THREADS; t++) {
         int id = t;
         threads.add(harness.submit(() -> {
            go.await();

            for(int i = 0; i < AQS_OPS; i++) {
               // a script that looks up an unknown name and assigns another, run by the real
               // engine, which takes the engine lock itself
               Object script = s.engine.compile("typeof id_" + id + "_" + i + "; m_" + id + "_" + i + " = " + i + ";");
               s.engine.exec(script, scope, null);
            }

            return null;
         }));
      }

      go.countDown();

      for(Future<Void> thread : threads) {
         harness.await(thread, ACTIVE_CAP, "script thread");
      }

      // each script looks up both of its names, so each caches two entries
      assertEquals(tables + 2 * AQS_THREADS * AQS_OPS, mapField(scope, "tablemap").size(),
                   "lost tablemap entries");
   }

   private static Map<?, ?> mapField(AssetQueryScope scope, String name) throws Exception {
      Field field = AssetQueryScope.class.getDeclaredField(name);
      field.setAccessible(true);
      return (Map<?, ?>) field.get(scope);
   }

   /**
    * Bug #76960 A2 with a guest holder: a script holding E reads a hash join whose inputs are
    * filtered formula tables, built by an unlocked thread. It waits in
    * {@code XSwappableTable.moreRows} for the JoinThreads. A guest cannot lend. The join's
    * builder has computed both inputs, so the JoinThreads only re-read mapped rows and must
    * not wait for E in the input filters (bug #77273). For inputs past the pre-drain see
    * {@link #guestReadsFilteredHashJoinPastPreDrain}.
    */
   @Test
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

      assertEquals(expected, sorted(harness.await(guest, ACTIVE_CAP, "guest reading the join")));
   }

   /**
    * Bug #77273: an unlocked thread builds a hash join over filtered formula inputs larger
    * than the 10000-row {@code JoinTable} pre-drain, and a guest drains it. The JoinThreads
    * must compute the formula rows past the pre-drain, which needs E, while the guest holds E
    * and waits for them in {@code JoinTable.moreRows}. Those rows are slow on the workers, so
    * the workers are still scanning when the guest arrives.
    */
   @Test
   public void guestReadsFilteredHashJoinPastPreDrain() throws Exception {
      harness.forceHashJoin();
      // joined on the unique id column
      runGuestReadsJoin(s -> hashJoin(cf2(calcField(s, bigTable()), s.box),
                                      cf2(calcField(s, bigTable()), s.box), 2));
   }

   /**
    * Bug #77273, the reporter's frame: as {@link #guestReadsFilteredHashJoinPastPreDrain}
    * over bare formula lenses, so the JoinThreads wait for E in
    * {@code FormulaTableLens.lockForRow} rather than in a condition filter.
    */
   @Test
   public void guestReadsFormulaHashJoinPastPreDrain() throws Exception {
      harness.forceHashJoin();
      runGuestReadsJoin(s -> hashJoin(calcField(s, bigTable()), calcField(s, bigTable()), 2));
   }

   /**
    * Bug #77273 with a merge join: an unlocked thread builds a {@code MergeJoinTable} over
    * filtered formula inputs within the pre-drain, and a guest drains it. The JoinThread's
    * sorts read the inputs on the worker.
    */
   @Test
   public void guestReadsFilteredMergeJoin() throws Exception {
      runGuestReadsJoin(s -> mergeJoin(s.filteredFormula(new SlowTable(MERGE_ROWS, Slow.WORKERS)),
                                       s.filteredFormula(new SlowTable(MERGE_ROWS, Slow.WORKERS)), 1));
   }

   /**
    * Bug #77273 with a merge join past the pre-drain: the JoinThread's sorts populate the
    * filtered formula inputs past row 10000 on the worker, which needs E.
    */
   @Test
   public void guestReadsFilteredMergeJoinPastPreDrain() throws Exception {
      runGuestReadsJoin(s -> mergeJoin(cf2(calcField(s, bigTable()), s.box),
                                       cf2(calcField(s, bigTable()), s.box), 2));
   }

   /**
    * A guest reads a hash join over bare formula lenses within the pre-drain, built by an
    * unlocked thread. The builder's pre-drain computes every formula row, and a worker
    * reading a computed formula lens takes no engine lock (#77215), so no worker waits for
    * the guest.
    */
   @Test
   public void guestReadsFormulaHashJoin() throws Exception {
      harness.forceHashJoin();
      runGuestReadsJoin(s -> hashJoin(s.formula(new SlowTable(JOIN_ROWS, Slow.WORKERS)),
                                      s.formula(new SlowTable(JOIN_ROWS, Slow.WORKERS)), 1));
   }

   /**
    * A reader that finds its row already mapped in a filtered formula table races an
    * {@code invalidate()} of the formula lens below: once the row map is reset, populating
    * it computes formula rows, which must not start without E (#76918). The reader X calls
    * {@code moreRows} of a mapped row while a guest holds E; the test thread holds the
    * filter's monitor, so X parks on E, or at the monitor if it skips E for the mapped row.
    * The test thread then invalidates the formula lens (and with it the filter) and lets X
    * go; the guest then reads the filter. If X populated the new map holding the filter's
    * monitor without E, it waits for E there while the guest waits for the monitor.
    */
   @Test
   public void mappedRowReaderRacingInvalidate() throws Exception {
      Sandbox s = harness.sandbox();
      FormulaTableLens formula = calcField(s, new SlowTable(INV_ROWS, Slow.NONE));
      TableLens cf = cf2(formula, s.box);
      drain(cf);
      Object expected = cf.getObject(READ_ROW, 1);

      CountDownLatch held = new CountDownLatch(1);
      CountDownLatch go = new CountDownLatch(1);
      Future<Object> guest = harness.submit(() -> s.asGuest(() -> {
         held.countDown();
         assertTrue(go.await(3 * KNOWN_CAP, TimeUnit.SECONDS), "the guest was never let go");
         return cf.getObject(READ_ROW, 1);
      }));
      assertTrue(held.await(ACTIVE_CAP, TimeUnit.SECONDS), "the guest never took the lock");
      Started<Boolean> reader;

      try {
         synchronized(cf) {
            reader = harness.start(() -> cf.moreRows(READ_ROW));
            awaitParked(reader, ACTIVE_CAP);
            assertFalse(reader.future.isDone() && !reader.future.get(),
                        "the mapped row was not found");
            formula.invalidate();
            ((AbstractConditionFilter) cf).invalidate();
         }

         // X either still waits for E, or populates the new row map and waits for E in the
         // formula lens, or has returned without populating
         if(!reader.future.isDone()) {
            assertTrue(awaitWaitingOnLock(reader.thread, ACTIVE_CAP),
                       "the reader is not waiting for the engine lock");
         }
      }
      finally {
         go.countDown();
      }

      assertEquals(expected, harness.await(guest, ACTIVE_CAP, "the guest reading the filter"));
      assertTrue(harness.await(reader.future, ACTIVE_CAP, "the mapped-row reader"));
      assertFalse(s.lock.isLocked());
   }

   /**
    * Bug #77273, the end-of-table probe: a join worker's last {@code moreRows}, past the end
    * of a completed filtered formula table, runs no script, so it is answered without E while
    * a guest holds E. Once the base has grown and the formula lens (and with it the filter)
    * is invalidated, the same question must go through E and find the new rows: an end
    * answered from the old map would stop a reader with too few rows. With the pool on there
    * is no E: the reset map is answered from the new rows without waiting for the guest.
    */
   @Test
   public void pastCompletedMapReaderAfterInvalidate() throws Exception {
      Sandbox s = harness.sandbox();
      ResizableTable base = new ResizableTable(2 * INV_ROWS, INV_ROWS);
      FormulaTableLens formula = calcField(s, base);
      TableLens cf = cf2(formula, s.box);
      int end = drain(cf).size();

      CountDownLatch held = new CountDownLatch(1);
      CountDownLatch go = new CountDownLatch(1);
      Future<Object> guest = harness.submit(() -> s.asGuest(() -> {
         held.countDown();
         assertTrue(go.await(3 * KNOWN_CAP, TimeUnit.SECONDS), "the guest was never let go");
         return null;
      }));
      assertTrue(held.await(ACTIVE_CAP, TimeUnit.SECONDS), "the guest never took the lock");
      Started<Boolean> reader;

      try {
         assertFalse(harness.await(harness.submit(() -> cf.moreRows(end)), ACTIVE_CAP,
                                   "probing past the completed map while the guest holds the lock"));

         base.setVisibleRows(2 * INV_ROWS);
         formula.invalidate();
         ((AbstractConditionFilter) cf).invalidate();
         reader = harness.start(() -> cf.moreRows(end));

         if(POOL) {
            // pool mode has no sandbox-wide engine lock: the reset map is repopulated on
            // another context while the guest holds its claim, and must see the grown base
            // (no stale end answered from the old completed map)
            assertTrue(harness.await(reader.future, ACTIVE_CAP, "the reader of the reset map"),
                       "the reset map was answered from the old end");
            assertFalse(guest.isDone(), "the guest let go before the reader finished");
         }
         else {
            awaitParked(reader, ACTIVE_CAP);
            assertFalse(reader.future.isDone(),
                        "the reset map was answered without the engine lock");
            assertTrue(awaitWaitingOnLock(reader.thread, ACTIVE_CAP),
                       "the reader of the reset map is not waiting for the engine lock");
         }
      }
      finally {
         go.countDown();
      }

      harness.await(guest, ACTIVE_CAP, "the guest");
      assertTrue(harness.await(reader.future, ACTIVE_CAP, "the reader of the reset map"));
      assertEquals(2 * INV_ROWS + 1, harness.await(harness.submit(() -> drain(cf).size()),
                                                   ACTIVE_CAP, "draining the reset map"));
      assertFalse(s.lock.isLocked());
   }

   /**
    * Same bug, other site (#77273 sweep, not fixed): an input of a built join is invalidated.
    * {@code AbstractConditionFilter.invalidate()} fires its change event under the filter's
    * monitor, the {@code JoinTableLens} listening rebuilds its delegate there, and the
    * {@code JoinTable} constructor pre-drains the reset input, which takes E after the monitor
    * (#76918's A side). A guest reading that input holds E and waits for the monitor. The
    * 10000-row pre-drain before #77273 did the same.
    */
   @Test
   @Tag("known-deadlock")
   @EnabledIfSystemProperty(named = "lockcycle.known", matches = "true")
   public void joinInputInvalidatedUnderGuest() throws Exception {
      harness.forceHashJoin();
      Sandbox s = harness.sandbox();
      TableLens left = s.filteredFormula(new SlowTable(JOIN_ROWS, Slow.NONE));
      TableLens right = s.filteredFormula(new SlowTable(JOIN_ROWS, Slow.NONE));
      TableLens join = harness.await(harness.submit(() -> hashJoin(left, right, 1)), ACTIVE_CAP, "build");
      int rows = harness.await(harness.submit(() -> drain(join).size()), ACTIVE_CAP, "draining the join");
      Object expected = left.getObject(READ_ROW, 1);

      CountDownLatch held = new CountDownLatch(1);
      CountDownLatch go = new CountDownLatch(1);
      Future<Object> guest = harness.submit(() -> s.asGuest(() -> {
         held.countDown();
         assertTrue(go.await(3 * KNOWN_CAP, TimeUnit.SECONDS), "the guest was never let go");
         return left.getObject(READ_ROW, 1);
      }));
      assertTrue(held.await(ACTIVE_CAP, TimeUnit.SECONDS), "the guest never took the lock");
      Started<Object> invalidator;

      try {
         invalidator = harness.start(() -> {
            ((AbstractConditionFilter) left).invalidate();
            return null;
         });
         awaitParked(invalidator, KNOWN_CAP);

         if(!invalidator.future.isDone()) {
            awaitWaitingOnLock(invalidator.thread, KNOWN_CAP);
         }
      }
      finally {
         go.countDown();
      }

      assertEquals(expected, harness.await(guest, KNOWN_CAP, "the guest reading the join input"));
      harness.await(invalidator.future, KNOWN_CAP, "the invalidator");
      assertEquals(rows, harness.await(harness.submit(() -> drain(join).size()), KNOWN_CAP,
                                       "draining the rebuilt join"));
   }

   /**
    * Bug #77273 with a cross join: an unlocked {@code getRowCount()} starts a
    * {@code CrossJoinTableLens}'s loading threads over filtered formula inputs, then a guest
    * drains it. {@code setTables} computes both inputs to the end on the building thread, so
    * the loaders only re-read mapped rows and must not wait for E, which the guest holds while
    * it waits for them. Each loader {@code moreRows} costs 150 ms, so the loaders are still
    * reading when the guest arrives.
    */
   @Test
   public void guestReadsCrossJoinStartedUnlocked() throws Exception {
      Function<Sandbox, TableLens> build = s -> harness.track(new CrossJoinTableLens(
         new SlowWorkerMoreRows(s.filteredFormula(new SlowTable(CROSS_ROWS, Slow.WORKERS))),
         new SlowWorkerMoreRows(s.filteredFormula(new SlowTable(CROSS_ROWS, Slow.WORKERS)))));
      Sandbox control = harness.control();
      List<String> expected = sorted(harness.await(
         harness.submit(() -> drain(build.apply(control))), ACTIVE_CAP, "control cross join"));

      Sandbox s = harness.sandbox();
      TableLens cross = harness.await(harness.submit(() -> {
         TableLens lens = build.apply(s);
         lens.getRowCount();
         return lens;
      }), ACTIVE_CAP, "unlocked getRowCount()");
      Future<List<List<Object>>> guest = harness.submit(() -> s.asGuest(() -> drain(cross)));

      assertEquals(expected, sorted(harness.await(guest, ACTIVE_CAP, "guest reading the cross join")));
   }

   /**
    * Widens a race: each {@code moreRows} on a lens worker costs 150 ms.
    */
   private static final class SlowWorkerMoreRows extends DefaultTableFilter {
      SlowWorkerMoreRows(TableLens table) {
         super(table);
      }

      @Override
      public boolean moreRows(int row) {
         if(!isHarnessThread()) {
            try {
               Thread.sleep(150);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }

         return super.moreRows(row);
      }
   }

   /**
    * A table of {@code rows} rows of which only the first {@code visible} exist until
    * {@link #setVisibleRows}: a base whose data grows.
    */
   private static final class ResizableTable extends SlowTable {
      ResizableTable(int rows, int visible) {
         super(rows, Slow.NONE);
         this.visible = visible;
      }

      void setVisibleRows(int rows) {
         visible = rows;
      }

      @Override
      public boolean moreRows(int row) {
         return row <= visible;
      }

      @Override
      public int getRowCount() {
         return visible + 1;
      }

      private volatile int visible;
   }

   /**
    * Build a join with {@code build} on a control sandbox for the expected rows, then on an
    * unlocked harness thread of a locking sandbox, and drain it as a guest of that sandbox.
    */
   private void runGuestReadsJoin(Function<Sandbox, TableLens> build) throws Exception {
      Sandbox control = harness.control();
      List<String> expected = sorted(harness.await(
         harness.submit(() -> drain(build.apply(control))), ACTIVE_CAP, "control join"));
      assertTrue(expected.size() > 2, "the control join is empty");

      Sandbox s = harness.sandbox();
      TableLens join = harness.await(harness.submit(() -> build.apply(s)), ACTIVE_CAP, "build");
      Future<List<List<Object>>> guest = harness.submit(() -> s.asGuest(() -> drain(join)));

      // the order of joined rows depends on the join workers' timing
      assertEquals(expected, sorted(harness.await(guest, ACTIVE_CAP, "guest reading the join")));
      assertFalse(s.lock.isLocked());
   }

   private TableLens hashJoin(TableLens left, TableLens right, int col) {
      return harness.track(new JoinTableLens(left, right, new int[] {col}, new int[] {col},
                                             JoinTableLens.INNER_JOIN, true));
   }

   /**
    * {@code MergeJoinTable} is package-private and {@code JoinTableLens} picks it only under
    * memory pressure, so build it directly.
    */
   private TableLens mergeJoin(TableLens left, TableLens right, int col) {
      try {
         Class<?> cls = Class.forName("inetsoft.report.lens.MergeJoinTable");
         Constructor<?> ctor = cls.getDeclaredConstructor(
            TableLens.class, TableLens.class, int[].class, int[].class, int.class, boolean.class,
            int.class);
         ctor.setAccessible(true);
         return harness.track((TableLens) ctor.newInstance(
            left, right, new int[] {col}, new int[] {col}, JoinTableLens.INNER_JOIN, true,
            Integer.MAX_VALUE));
      }
      catch(ReflectiveOperationException ex) {
         throw new IllegalStateException(ex);
      }
   }

   /**
    * A base past the {@code JoinTable} pre-drain, slow on the workers past it.
    */
   private static TableLens bigTable() {
      return new SlowTable(BIG_ROWS, Slow.WORKERS_PAST_PREDRAIN);
   }

   /**
    * Bug #76960 B (R2) with a guest: T1 sorts a shared {@code SortFilter} over a filtered
    * formula table, holding the sort monitor; the guest holds E and waits for the sort
    * monitor. The gate parks T1 during the sort's own population of the filter, which T1
    * does holding E, so the guest takes E only after that; T1 then re-reads the filter's
    * mapped rows under the sort monitor, which must not wait for E (bug #77273).
    *
    * <p>Not covered, and still open: a guest that takes E before T1 populates the filter, so
    * that T1 needs E for a real population while holding the sort monitor (a monitor-first
    * inversion; #77273 sweep).
    */
   @Test
   public void guestReadsSharedSort() throws Exception {
      Gate gate = harness.gate();
      Function<Sandbox, TableLens> build = s -> harness.track(new SortFilter(
         s.filteredFormula(new SlowTable(ROWS, Slow.EVERYWHERE, gate)), new int[] {1}));
      Sandbox control = harness.control();
      List<List<Object>> expected = harness.await(
         harness.submit(() -> drain(build.apply(control))), ACTIVE_CAP, "control sort");

      Sandbox s = harness.sandbox();
      TableLens sort = build.apply(s);
      // T1 parks at its first base read, inside the sort's monitor
      Started<List<List<Object>>> t1 = harness.startGated(gate, () -> drain(sort));
      assertTrue(gate.awaitEntered(ACTIVE_CAP), "T1 never started sorting");
      Started<List<List<Object>>> guest = harness.start(() -> s.asGuest(() -> drain(sort)));
      releaseAfter(gate, guest, ACTIVE_CAP);

      assertEquals(expected, harness.await(guest.future, ACTIVE_CAP, "guest reading the sort"));
      assertEquals(expected, harness.await(t1.future, ACTIVE_CAP, "T1, the unlocked sorter"));
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
      runPopulatorAndGuest(build, cap, "");
   }

   private void runPopulatorAndGuest(Function<Sandbox, TableLens> build, long cap, String label)
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

      assertEquals(expectedOuter, harness.await(b, cap, label + "B, the guest formula reader"));
      assertEquals(expectedCf, harness.await(a, cap, label + "A, the filter populator"));
      assertFalse(s.lock.isLocked());
   }

   private static FormulaTableLens calcField(Sandbox s, TableLens base) {
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
   private static final int BIG_ROWS = 12000;
   // the merge join's sort reads the join column many times
   private static final int MERGE_ROWS = 40;
   private static final int READ_ROW = 5;
   private static final int CROSS_ROWS = 60;
   private static final int AQS_THREADS = 4;
   private static final int AQS_OPS = 500;
   private static final int UNION_ROWS = 150;
   private static final int UNION_ITERATIONS = 20;
   private static final long UNION_TOTAL_CAP = 300;
   private LockCycleHarness harness;
}
