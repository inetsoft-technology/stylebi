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
package inetsoft.report.lens;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.script.TableRowScope;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.script.ScriptSpan;
import inetsoft.util.script.graal.pool.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static inetsoft.report.lens.FormulaTableLensVarTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Arrays and plain objects in a formula table's vars across pooled script contexts (Testing
 * #77123, B1 residual part 2). With the pool off a var keeps one object for the whole table;
 * with the pool on the table's objects stay live on one context, its home, which is kept for
 * the table while it is idle and which its next batch prefers, so nothing is copied in the
 * common case. At a hand-off (another context's batch pulls them from the idle home, or the
 * pool expires, closes or takes over the home) they are saved as one tree by a cloner that
 * runs no user code and rebuilt on the other context: aliases, cycles, nested Dates and
 * property attributes are kept. What it cannot keep (a function, a class instance, a Proxy,
 * an accessor) loses its own var, and any var sharing an object with it (A3), each with one
 * warning naming what it held.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PooledLensObjectVarTest {
   static final String ARRAY = "var a = a || []; a.push(field['id']); a.length";
   static final String OBJECT =
      "var c = c || {n: 0}; c['k' + field['id']] = 1; c.n++; c.n";
   // an alias var of a nested array, and a Date inside the object
   static final String NESTED = "var o = o || {list: [], d: new Date(0)}; var l = l || o.list; " +
      "l.push(1); o.d.setTime(o.d.getTime() + 1000); " +
      "(o.list === l && o.d.getTime() == l.length * 1000 ? 1 : -1) * o.list.length";
   static final String CYCLE = "var o = o || (function() { var x = {n: 0}; x.self = x; " +
      "return x; })(); o.self.n++; (o.self === o ? 1 : -1) * o.n";
   static final String CACHE10 =
      "var c = c || {}; c['k' + (field['id'] % 10)] = field['id']; var n = (n || 0) + 1; n";
   static final String GROW = "var a = a || []; a.push(field['id']); a.length";

   @BeforeEach
   void capture() {
      logger = (Logger) LoggerFactory.getLogger(TableRowScope.class);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void retire() throws Exception {
      logger.detachAppender(appender);
      PoolTestSupport.failOwnedValueReads(null);

      for(String p : new String[] { MAX_HOMES, MAX_HOMES_PER_NODE, HAND_OFF_MILLIS,
                                    HAND_OFF_ENTRIES })
      {
         SreeEnv.remove(p);
      }

      for(WorksheetScriptEnv env : envs) {
         env.retire();
      }

      envs.clear();
   }

   static Stream<Arguments> shapes() {
      List<Arguments> args = new ArrayList<>();

      for(String how : new String[] { "held", "busy", "handoff", "takeover" }) {
         for(String[] f : new String[][] { { "array", ARRAY }, { "object", OBJECT },
                                           { "nested", NESTED }, { "cycle", CYCLE } })
         {
            args.add(Arguments.of(how, f[0], f[1]));
         }
      }

      return args.stream();
   }

   /**
    * An array accumulator, an object cache, a nested object with an alias var and a Date, and
    * a cycle count every row as with the pool off, while another thread holds a context (held)
    * or runs a script (busy), and when the objects are handed off between reads (handoff: the
    * pool saves them as a tree; takeover: no exclusive home, so the other thread's claim takes
    * the home over and the table continues on another context). On main 885 of 1200 rows are
    * wrong.
    */
   @ParameterizedTest(name = "{0} {1}")
   @MethodSource("shapes")
   void anArrayOrObjectVarIsKeptAcrossPooledContexts(String how, String what, String formula)
      throws Exception
   {
      double[] v = crossSlot(how, formula);
      assertAll(v, what + " " + how);
      assertTrue(warnings().isEmpty(), () -> "no warning: " + warnings());

      if(how.equals("handoff") || how.equals("takeover")) {
         assertTrue(PoolTestSupport.metric(lastEnv, "HandOffs") >= 1, "a hand-off ran");
         assertTrue(PoolTestSupport.metric(lastEnv, "Rebuilds") >= 1, "a rebuild ran");
      }
   }

   /**
    * While another thread holds a context, a resident table stays on its home: no tree
    * snapshot, no rebuild, and the holder got another context.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "held", "busy" })
   void aResidentLensStaysOnItsHomeContext(String how) throws Exception {
      double[] v = crossSlot(how, NESTED);
      assertAll(v, how);
      assertEquals(0, PoolTestSupport.metric(lastEnv, "HandOffs"), "no tree snapshot");
      assertEquals(0, PoolTestSupport.metric(lastEnv, "Rebuilds"), "no rebuild");
      assertEquals(0, PoolTestSupport.metric(lastEnv, "CrossReads"), "no read of a foreign object");
      assertTrue(lastEnv.getMetrics().getCreations() >= 2, "the other thread got its own context");
   }

   /**
    * A stateful closure and a class instance are kept exactly while the table stays on its
    * home, which it does while another thread holds a context.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "closure", "class" })
   void aStatefulClosureAndAClassInstanceAreKeptOnTheirHome(String what) throws Exception {
      String f = what.equals("closure")
         ? "var f = f || (function() { var n = 0; return function() { return ++n; }; })(); f()"
         : "var k = k || new (class { constructor() { this.n = 0; } " +
           "inc() { return ++this.n; } })(); k.inc()";
      double[] v = crossSlot("held", f);
      assertAll(v, what);
      assertTrue(warnings().isEmpty(), () -> "no warning: " + warnings());
   }

   /**
    * A1: batches nested in an outer claim (a condition filter's span around each read) take
    * no tree snapshot per page: only the table's first batch, nested before the table is
    * known to be resident, saves its objects as one tree (Testing #77123, cond-home); every
    * later batch runs on a claim of its own. Exact rows, and no slower than without the span.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "grow", "cache10" })
   void nestedBatchesTakeOneSnapshotOnly(String what) throws Exception {
      String f = what.equals("grow") ? GROW : CACHE10;
      int rows = 5000;
      long plain = pagedRead(f, rows, false);
      long spanned = pagedRead(f, rows, true);
      assertEquals(1, PoolTestSupport.metric(lastEnv, "HandOffs"), "one tree, not one per page");
      System.out.println("B1OBJ nested " + what + ": plain " + plain + " ms, spanned " +
                         spanned + " ms");
      assertTrue(spanned <= plain * 3 + 2000,
                 "spanned " + spanned + " ms vs plain " + plain + " ms");
   }

   /**
    * A2: two tables read under one outer span (Testing #77123, cond-home): the first batch of
    * each saves its objects as one tree, as the span outlives it, and each later batch takes a
    * context of its own, so each table has its own home; then they are read separately: both
    * exact, one tree per table.
    */
   @Test
   void twoLensesReadUnderOneSpanHaveAHomeEach() throws Exception {
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      TableLens t1 = make(box, base(ROWS), ARRAY, "T1");
      TableLens t2 = make(box, base(ROWS), CYCLE, "T2");
      double[] v1 = new double[ROWS + 1];
      double[] v2 = new double[ROWS + 1];

      try(ScriptSpan span = w.openSpan()) {
         read(t1, v1, 1, 100);
         read(t2, v2, 1, 100);
      }

      assertEquals(2, PoolTestSupport.homes(w), "a home for each table");

      for(int s = 101; s <= ROWS; s += 100) {
         read(t1, v1, s, s + 99);
         read(t2, v2, s, s + 99);
      }

      assertAll(v1, "t1");
      assertAll(v2, "t2");
      assertEquals(2, PoolTestSupport.metric(w, "HandOffs"), "one tree per table");
   }

   /**
    * A3: at a hand-off a function or an Intl formatter is lost with one warning naming it,
    * and the plain accumulator next to it is kept.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "function", "intl" })
   void aValueThatCannotBeKeptLosesOnlyItsOwnVar(String what) throws Exception {
      String f = what.equals("function")
         ? "var t = t || {sum: 0}; var f = f || function(x) { return x; }; " +
           "t.sum = f(t.sum + 1); t.sum"
         : "var t = t || {sum: 0}; var fmt = fmt || new Intl.NumberFormat('en-US'); t.sum++; " +
           "fmt.format(1) == '1' ? t.sum : -1";
      double[] v = crossSlot("handoff", f);
      assertAll(v, what);
      List<String> warns = warningTexts();
      assertEquals(1, warns.size(), () -> "one warning: " + warns);
      String var = what.equals("function") ? "\"f\" holds a function"
         : "\"fmt\" holds an Intl.NumberFormat object";
      assertTrue(warns.get(0).contains(var), warns.get(0));
      assertFalse(warns.get(0).contains("\"t\" holds"), warns.get(0));
      // a function hides references (its closure): the kept t, an object, is named as a copy;
      // an Intl formatter hides none, so nothing is named
      assertEquals(what.equals("function") ? List.of("t") : List.of(),
                   PooledLensHiddenAliasTest.copiesIn(warns.get(0)), warns.get(0));
   }

   /**
    * A4: six tables with an object var in one sandbox, read in turn: at most maxHomes + 1
    * contexts (other homes are taken over after a hand-off), and every row is exact; with
    * maxHomes 1 and with the default (4).
    */
   @ParameterizedTest(name = "maxHomes {0}")
   @ValueSource(ints = { 1, 4 })
   void manyResidentLensesUseAtMostTheCapPlusOneContexts(int cap) throws Exception {
      if(cap != 4) {
         SreeEnv.setProperty(MAX_HOMES, String.valueOf(cap));
      }

      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      List<TableLens> lenses = new ArrayList<>();
      List<double[]> values = new ArrayList<>();

      for(int i = 0; i < 6; i++) {
         lenses.add(make(box, base(ROWS), i % 2 == 0 ? OBJECT : ARRAY, "T" + i));
         values.add(new double[ROWS + 1]);
      }

      for(int s = 1; s <= ROWS; s += 100) {
         for(int i = 0; i < 6; i++) {
            read(lenses.get(i), values.get(i), s, s + 99);
         }
      }

      for(int i = 0; i < 6; i++) {
         assertAll(values.get(i), "T" + i);
      }

      assertTrue(w.getMetrics().getHighWater() <= cap + 1,
                 "contexts: " + w.getMetrics().getHighWater());
      assertTrue(PoolTestSupport.exclusiveHomes(w) <= cap);
   }

   /**
    * A4: one table read in pages, with unrelated scripts between the pages: at most two
    * contexts, the home and one for the other scripts.
    */
   @Test
   void otherScriptsBetweenPagesUseAtMostOneMoreContext() throws Exception {
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      TableLens t = make(box, base(ROWS), OBJECT, "T");
      double[] v = new double[ROWS + 1];

      for(int s = 1; s <= ROWS; s += 60) {
         read(t, v, s, Math.min(ROWS, s + 59));
         PoolTestSupport.run(w, "var q = 1; q");
      }

      assertAll(v, "paged");
      assertTrue(w.getMetrics().getHighWater() <= 2,
                 "contexts: " + w.getMetrics().getHighWater());
   }

   /**
    * A4: the node cap: past maxHomesPerNode a home is soft, so another claim takes it over
    * (after a hand-off) instead of creating a context; every row stays exact.
    */
   @Test
   void theNodeCapMakesFurtherHomesSoft() throws Exception {
      int before = PoolTestSupport.nodeHomes();
      SreeEnv.setProperty(MAX_HOMES_PER_NODE, String.valueOf(Math.max(0, before) + 2));
      List<TableLens> lenses = new ArrayList<>();
      List<double[]> values = new ArrayList<>();
      List<WorksheetScriptEnv> ws = new ArrayList<>();

      for(int i = 0; i < 5; i++) {
         AssetQuerySandbox box = box();
         ws.add((WorksheetScriptEnv) box.getScriptEnv());
         lenses.add(make(box, base(ROWS), OBJECT, "T" + i));
         values.add(new double[ROWS + 1]);
         read(lenses.get(i), values.get(i), 1, 100);
      }

      assertTrue(PoolTestSupport.nodeHomes() - before <= 2,
                 "node homes " + PoolTestSupport.nodeHomes() + " (before " + before + ")");

      for(int i = 0; i < 5; i++) {
         WorksheetScriptEnv w = ws.get(i);
         int n = i;
         PoolTestSupport.whileHeldElsewhere(w, () -> read(lenses.get(n), values.get(n), 101,
                                                           ROWS));
         assertAll(values.get(i), "T" + i);
      }
   }

   /**
    * A5: a home idle for idleMillis is handed off and closed by the evictor, even though the
    * table was never completed; the next page is exact, rebuilt from the tree.
    */
   @Test
   void anExpiredHomeIsHandedOffAndClosed() throws Exception {
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      TableLens t = make(box, base(ROWS), NESTED, "T");
      double[] v = new double[ROWS + 1];
      // the home is a pooled context: the primary is held while the first page runs
      PoolTestSupport.whileHeldElsewhere(w, () -> read(t, v, 1, 200));
      assertEquals(2, w.getMetrics().getSize());
      assertEquals(1, PoolTestSupport.homes(w));

      PoolTestSupport.evictIdle(w, Long.MAX_VALUE);

      assertEquals(0, PoolTestSupport.homes(w), "the home is released");
      assertEquals(1, w.getMetrics().getSize(), "the expired home is closed");
      assertEquals(1, PoolTestSupport.metric(w, "HandOffs"));
      read(t, v, 201, ROWS);
      assertAll(v, "after expiry");
      assertEquals(1, PoolTestSupport.metric(w, "Rebuilds"));
   }

   /**
    * A home's claim is closed by a retire (env reset) while the table is idle: its objects are
    * handed off first, so the table continues exactly.
    */
   @Test
   void aRetireHandsOffTheHome() throws Exception {
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      TableLens t = make(box, base(ROWS), CYCLE, "T");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 300);
      w.retire();
      read(t, v, 301, ROWS);
      assertAll(v, "after retire");
      assertTrue(warnings().isEmpty(), () -> "no warning: " + warnings());
   }

   /**
    * A batch that runs inside another claim takes a context of its own, the idle home, even
    * though the outer claim skipped it and holds another context (Testing #77123,
    * cond-home): no pull is needed.
    */
   @Test
   void aLensInsideAnotherClaimTakesItsIdleHome() throws Exception {
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      TableLens t = make(box, base(ROWS), NESTED, "T");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 200);

      // an eager claim skips the exclusive home and takes a second context
      try(SlotClaim claim = w.claimSlot()) {
         read(t, v, 201, 600);
      }

      read(t, v, 601, ROWS);
      assertAll(v, "home taken");
      assertEquals(0, PoolTestSupport.metric(w, "Pulls"), "no pull");
      assertTrue(warnings().isEmpty(), () -> "no warning: " + warnings());
   }

   /**
    * A1 (Testing #77123, cond-home): while an outer claim or span of this thread is still
    * open (a condition filter's population, another table's batch), another thread reads
    * the table. The table's first batch, on the outer claim, saved its objects as one tree;
    * its later batches took a context of their own, given back at each batch end, so the
    * other thread's batch takes the idle home: every row exact, no warning. On main the outer
    * claim held the home and the other thread lost the objects (row 201 restarted at 1, with
    * one warning).
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "eager claim", "lazy span", "lazy span with a script" })
   void aReadWhileAnOuterClaimIsOpenTakesTheGivenBackHome(String outer) throws Exception {
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      TableLens t = make(box, base(ROWS), ARRAY, "T");
      double[] v = new double[ROWS + 1];

      try(ScriptSpan span = outer.equals("eager claim") ? w.claimSlot() : w.openSpan()) {
         if(outer.equals("lazy span with a script")) {
            // the span takes its context before the table's first batch, as a condition
            // filter's JavaScript value does
            assertEquals(2, ((Number) w.exec(w.compile("1 + 1"), null, null, null)).intValue());
         }

         read(t, v, 1, 200);
         ExecutorService ex = Executors.newSingleThreadExecutor();

         try {
            ex.submit(() -> {
               read(t, v, 201, 400);
               return null;
            }).get(60, TimeUnit.SECONDS);
         }
         finally {
            ex.shutdownNow();
         }

         read(t, v, 401, 600);
      }

      for(int r = 1; r <= 600; r++) {
         assertEquals(r, v[r], "row " + r);
      }

      assertTrue(warnings().isEmpty(), () -> "no warning: " + warnings());
      assertEquals(1, PoolTestSupport.metric(w, "HandOffs"), "one tree, at the first batch");
   }

   /**
    * Round 3 (Testing #77123, cond-home review finding 2): one thread, one outer span that ran
    * a script and covers the whole read (a condition filter's population), and a var holding
    * an object over the hand-off budget from row 1 (the entry cap, or the time bound). The
    * table's first batch, nested in the outer span, cannot save that object as a tree, so its
    * context stays the home and the table's later batches share the span, as before cond-home:
    * every row exact, no warning. On 22b01b3a8 the first batch saved it and lost it to the
    * budget: 989 of 1000 rows restarted, with one warning.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "entries", "time" })
   void anObjectOverTheBudgetStaysOnItsOuterSpan(String bound) throws Exception {
      SreeEnv.setProperty(bound.equals("entries") ? HAND_OFF_ENTRIES : HAND_OFF_MILLIS,
                          bound.equals("entries") ? "1000" : "1");
      int keys = bound.equals("entries") ? 3000 : 50000;
      int rows = 1000;
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      TableLens t = make(box, base(rows), "var m = m || (function() { var x = {cnt: 0}; " +
         "for(var i = 0; i < " + keys + "; i++) x['k' + i] = i; return x; })(); " +
         "m.cnt++; m.cnt", "T");
      double[] v = new double[rows + 1];

      try(ScriptSpan all = w.openSpan()) {
         assertEquals(2, ((Number) w.exec(w.compile("1 + 1"), null, null, null)).intValue());

         for(int s = 1; s <= rows; s += 100) {
            try(ScriptSpan page = w.openSpan()) {
               assertEquals(2, ((Number) w.exec(w.compile("1 + 1"), null, null, null))
                  .intValue());
               read(t, v, s, s + 99);
            }
         }
      }

      assertAll(v, "over the " + bound + " budget");
      assertTrue(warnings().isEmpty(), () -> "no warning: " + warningTexts());
   }

   /**
    * As {@link #anObjectOverTheBudgetStaysOnItsOuterSpan} for the marking budget (post-merge
    * review M1): a var holding a function next to a large array is lost for its function, and
    * marking its graph for objects shared with the small var m runs past the marking budget
    * (four entry caps), which loses m to the budget. That keeps the table's objects live on
    * the outer span's context: every row exact, the function callable, no warning. Past the
    * budget as a long array (refused before its keys are listed) or as many small objects.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "array", "objects" })
   void aVarPastTheMarkingBudgetStaysOnItsOuterSpan(String shape) throws Exception {
      SreeEnv.setProperty(HAND_OFF_ENTRIES, "100");
      int rows = 1000;
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      TableLens t = make(box, base(rows), "var b = b || {f: function(x) { return x; }, " +
         "a: (function() { var x = []; for(var i = 0; i < " +
         (shape.equals("array") ? "500; i++) x[i] = i; " : "150; i++) x[i] = {v: i, w: i}; ") +
         "return x; })()}; var m = m || {cnt: 0}; m.cnt++; b.f(m.cnt)", "T");
      double[] v = new double[rows + 1];

      try(ScriptSpan all = w.openSpan()) {
         assertEquals(2, ((Number) w.exec(w.compile("1 + 1"), null, null, null)).intValue());

         for(int s = 1; s <= rows; s += 100) {
            try(ScriptSpan page = w.openSpan()) {
               assertEquals(2, ((Number) w.exec(w.compile("1 + 1"), null, null, null))
                  .intValue());
               read(t, v, s, s + 99);
            }
         }
      }

      assertAll(v, "past the marking budget (" + shape + ")");
      assertTrue(warnings().isEmpty(), () -> "no warning: " + warningTexts());
   }

   /**
    * A6: a hand-off of a million-entry array stops at the entry cap: bounded, the var is lost
    * with one warning, and a second reader waits no longer than the hand-off. The hand-off
    * and the reader race for the idle home: if the reader takes it first, nothing is handed
    * off (the array is kept, no warning), and the home is handed off once the reader is done.
    */
   @Test
   void aHandOffOverTheBudgetIsLostAndBounded() {
      assertTimeoutPreemptively(Duration.ofSeconds(120), () -> {
         AssetQuerySandbox box = box();
         WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
         // only the first row builds the big array: after its loss the formula starts a new one
         TableLens t = make(box, base(ROWS), "var a = a || (field['id'] == 1 ? " +
            "(function() { var x = []; for(var i = 0; i < 1000000; i++) x[i] = i; " +
            "return x; })() : []); a.push(0); a.length > 1000000 ? 1 : 2", "T");
         double[] v = new double[ROWS + 1];
         read(t, v, 1, 20);
         ExecutorService ex = Executors.newSingleThreadExecutor();
         long budget = 5000; // the default hand-off time bound
         int count;

         try {
            long t0 = System.nanoTime();
            Future<Integer> handOff = ex.submit(() -> PoolTestSupport.handOffIdleHomes(w));
            Thread.sleep(20);
            long r0 = System.nanoTime();
            read(t, v, 21, 40);
            long reader = (System.nanoTime() - r0) / 1_000_000;
            count = handOff.get(60, TimeUnit.SECONDS);
            long handOffMs = (System.nanoTime() - t0) / 1_000_000;
            System.out.println("B1OBJ 1M hand-off " + handOffMs + " ms (" + count +
                               " homes), reader " + reader + " ms");
            assertTrue(handOffMs <= budget + 1000 + 2000, "hand-off " + handOffMs + " ms");
            assertTrue(reader <= budget + 1000 + 5000, "reader waited " + reader + " ms");
         }
         finally {
            ex.shutdownNow();
         }

         for(int r = 1; r <= 20; r++) {
            assertEquals(1.0, v[r], "row " + r);
         }

         // the last row read after the hand-off
         int last = 40;

         if(count == 0) {
            // the reader took the home first: nothing was handed off, the array is kept
            for(int r = 21; r <= 40; r++) {
               assertEquals(1.0, v[r], "nothing was lost: row " + r);
            }

            assertTrue(warningTexts().isEmpty(), () -> "no warning: " + warningTexts());
            long t1 = System.nanoTime();
            assertEquals(1, PoolTestSupport.handOffIdleHomes(w), "the idle home is handed off");
            long handOffMs = (System.nanoTime() - t1) / 1_000_000;
            assertTrue(handOffMs <= budget + 1000 + 2000, "hand-off " + handOffMs + " ms");
            // past the rows computed ahead of the hand-off (batches double)
            last = 200;
            read(t, v, 41, last);
         }
         else {
            assertEquals(1, count, "one home");
         }

         assertEquals(2.0, v[last], "the lost array was started again");
         List<String> warns = warningTexts();
         assertEquals(1, warns.size(), () -> "one warning: " + warns);
         assertTrue(warns.get(0).contains("\"a\" holds a value with more than"), warns.get(0));
      });
   }

   /**
    * A6: the time bound of a hand-off (here 1 ms against a large cache) loses the value with
    * one warning; the table continues.
    */
   @Test
   void aHandOffPastItsTimeBoundIsLost() throws Exception {
      SreeEnv.setProperty(HAND_OFF_MILLIS, "1");
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      TableLens t = make(box, base(ROWS), "var c = c || (function() { var o = {}; " +
         "for(var i = 0; i < 50000; i++) o['k' + i] = {n: i}; return o; })(); " +
         "var k = (k || 0) + 1; k", "T");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 20);
      PoolTestSupport.handOffIdleHomes(w);
      read(t, v, 21, 40);
      assertAll(v, 40, "k is a number, kept");
      List<String> warns = warningTexts();
      assertEquals(1, warns.size(), () -> "one warning: " + warns);
      assertTrue(warns.get(0).contains("\"c\" holds a value that took longer than"),
                 warns.get(0));
   }

   /**
    * A7: rebuilding a Date's own property writes it with the captured defineProperty, so a
    * setter a script put on Date.prototype never runs, and the property is kept exactly.
    */
   @Test
   void aRebuildDoesNotRunAPrototypeSetter() throws Exception {
      String f = "Object.defineProperty(Date.prototype, 'tag', { configurable: true, " +
         "set: function(v) { probe.hit(); }, get: function() { probe.hit(); return 'proto'; } }); " +
         "var o = o || { d: Object.defineProperty(new Date(0), 'tag', { value: 'kept', " +
         "writable: true, enumerable: true, configurable: true }) }; " +
         "o.d.setTime(o.d.getTime() + 1000); " +
         "(Object.getOwnPropertyDescriptor(o.d, 'tag').value === 'kept' ? 1 : -1) * " +
         "o.d.getTime() / 1000";
      double[] v = crossSlot("takeover", f);
      assertAll(v, "tag");
      assertEquals(0, probe.hits(), "no setter or getter ran");
      assertTrue(warnings().isEmpty(), () -> "no warning: " + warnings());
   }

   /**
    * A8: a Date var and an object holding it are one tree: after a hand-off o.d is still d.
    */
   @Test
   void aDateVarAliasedByAnObjectVarStaysOneObject() throws Exception {
      String f = "var d = d || new Date(0); var o = o || {d: d}; d.setTime(d.getTime() + 1000); " +
         "(o.d === d ? 1 : -1) * d.getTime() / 1000";
      double[] v = crossSlot("takeover", f);
      assertAll(v, "alias");
      assertTrue(warnings().isEmpty(), () -> "no warning: " + warnings());
   }

   /**
    * A tree keeps -0, NaN, Infinity, undefined, a bigint, holes, frozen / sealed /
    * non-enumerable properties, a null prototype, a Date with an own property inside an
    * object, key order, a cycle and an alias across two vars.
    */
   @Test
   void aTreeRoundTripIsExact() throws Exception {
      String sig = "(function(o) { var d = Object.getOwnPropertyDescriptor(o, 'ne'); return [" +
         "Object.is(o.z, -0), o.n !== o.n, o.i === Infinity, 'u' in o && o.u === undefined, " +
         "typeof o.b === 'bigint' && o.b === 12345678901234567890n, " +
         "!(1 in o.h) && o.h.length === 3 && o.h[2] === 3, Object.isFrozen(o.f), " +
         "Object.isFrozen(o.fa), Object.isSealed(o.s) && !Object.isFrozen(o.s), " +
         "!d.enumerable && d.value === 7, Object.getPrototypeOf(o.np) === null && o.np.k === 1, " +
         "o.dt instanceof Date && o.dt.getTime() === 5 && o.dt.tag === 'x', " +
         "Object.keys(o).join('|'), o.a1 === o.a2, o.self === o, o.sp.length === 4294967295" +
         "].join(','); })";
      String f = "var sig0 = sig0 || ''; var o = o || (function() { var inner = {q: 1}; " +
         "var x = {z: -0, n: NaN, i: Infinity, u: undefined, b: 12345678901234567890n, " +
         "h: [1,,3], f: Object.freeze({a: 1}), fa: Object.freeze([1, 2]), " +
         "s: Object.seal({a: 1}), np: Object.assign(Object.create(null), {k: 1}), " +
         "dt: Object.assign(new Date(5), {tag: 'x'}), a1: inner, a2: inner, " +
         "sp: (function() { var a = []; a[4294967294] = 1; return a; })()}; " +
         "Object.defineProperty(x, 'ne', {value: 7, enumerable: false, writable: true, " +
         "configurable: true}); x.self = x; return x; })(); var p = p || o.a1; " +
         "var s = " + sig + "(o); sig0 = sig0 || s; s === sig0 && p === o.a1 ? field['id'] : -1";
      double[] v = crossSlot("takeover", f);
      assertAll(v, "round trip");
      assertTrue(warnings().isEmpty(), () -> "no warning: " + warnings());
   }

   static Stream<Arguments> hostile() {
      return Stream.of(
         // shape, what the warning names ("" = kept exactly)
         Arguments.of("({n: 0, get g() { probe.hit(); return 1; }})",
                      "an object with a getter or setter"),
         Arguments.of("(function() { var a = [1, 2, 3]; Object.defineProperty(a, 1, " +
                      "{get: function() { probe.hit(); return 9; }, enumerable: true}); " +
                      "return a; })()", "an object with a getter or setter"),
         Arguments.of("new Proxy({a: 1}, {get: function() { while(true) {} }, " +
                      "ownKeys: function() { while(true) {} }, " +
                      "getOwnPropertyDescriptor: function() { while(true) {} }, " +
                      "getPrototypeOf: function() { while(true) {} }, " +
                      "has: function() { while(true) {} }})", "a Proxy object"),
         Arguments.of("({list: new Proxy([1], {get: function() { while(true) {} }, " +
                      "ownKeys: function() { while(true) {} }})})", "a Proxy object"),
         Arguments.of("Object.setPrototypeOf({a: 1}, {b: 2})", "an object of a class"),
         Arguments.of("({[Symbol('s')]: 1, a: 2})", "an object with a symbol key"),
         Arguments.of("new Int8Array(3)", "a typed array"),
         Arguments.of("new Map([[1, 2]])", "a Map"),
         Arguments.of("({s: new Set([1])})", "a Set"),
         Arguments.of("({f: function() { return 1; }})", "a function"),
         Arguments.of("new (class K { constructor() { this.n = 1; } })()",
                      "an object of a class"),
         // kept exactly: a hole with an Array.prototype getter behind it, a frozen object
         // (the getter stays on Array.prototype of the context that made the array)
         Arguments.of("(Object.defineProperty(Array.prototype, 1, {get: " +
                      "function() { probe.hit(); return 'p'; }, configurable: true}), [0,,2])",
                      ""),
         Arguments.of("Object.freeze({a: 1, b: [1, 2]})", ""));
   }

   /**
    * Hostile shapes at a hand-off: no user code runs (no getter, setter or trap; the traps
    * loop forever), nothing hangs, and each loss is one warning naming the kind; what the
    * cloner can keep is kept exactly.
    */
   @ParameterizedTest(name = "{1} {0}")
   @MethodSource("hostile")
   void hostileShapesRunNoUserCodeAndEachLossIsWarned(String shape, String kind) {
      String f = "var made = made || 0; var o = o || (made++, " + shape + "); made";
      double[][] v = new double[1][];
      assertTimeoutPreemptively(Duration.ofSeconds(90), () -> {
         v[0] = crossSlot("takeover", f);
      });
      assertEquals(0, probe.hits(), "no user code ran");
      List<String> warns = warningTexts();

      if(kind.isEmpty()) {
         assertEquals(1.0, v[0][ROWS], "kept: made once");
         assertTrue(warns.isEmpty(), () -> "no warning: " + warns);
      }
      else {
         assertTrue(v[0][ROWS] > 1.0, "lost at the hand-off: made again");
         assertEquals(1, warns.size(), () -> "one warning: " + warns);
         assertTrue(warns.get(0).contains("\"o\" holds " + kind), warns.get(0));
      }
   }

   /**
    * A snapshot that fails loses the value with a warning, never an older tree, and the
    * batch's claim is closed.
    */
   @Test
   void aHandOffFailureLosesTheValueWithAWarning() throws Exception {
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      TableLens t = make(box, base(ROWS), ARRAY, "T");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 200);
      PoolTestSupport.failOwnedValueReads(() -> new IllegalStateException("probe"));
      PoolTestSupport.handOffIdleHomes(w);
      PoolTestSupport.failOwnedValueReads(null);
      read(t, v, 201, 800);
      assertEquals(0, SlotClaim.openClaims());

      for(int r = 1; r <= 200; r++) {
         assertEquals(r, v[r], "row " + r);
      }

      // the rows computed ahead before the hand-off kept counting
      int first = 200;

      while(first < 800 && v[first + 1] == first + 1) {
         first++;
      }

      assertTrue(first < 800, "the lost array restarts");
      assertEquals(1.0, v[first + 1], "restarts, never an older tree");
      List<String> warns = warningTexts();
      assertEquals(1, warns.size(), () -> "one warning: " + warns);
      assertTrue(warns.get(0).contains("could not be read"), warns.get(0));
   }

   /** Completion and dispose release the home: another claim takes the primary again. */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "complete", "dispose" })
   void completionAndDisposeReleaseTheHome(String how) throws Exception {
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      FormulaTableLens t = (FormulaTableLens) make(box, base(ROWS), OBJECT, "T");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 200);
      assertEquals(1, PoolTestSupport.homes(w));

      if(how.equals("complete")) {
         assertFalse(t.moreRows(TableLens.EOT));
      }
      else {
         t.dispose();
      }

      assertEquals(0, PoolTestSupport.homes(w), "no home left");
      PoolTestSupport.run(w, "1");
      assertEquals(1, w.getMetrics().getSize(), "the other script ran on the primary");
   }

   /** A primitive or no-var formula never makes a home, nor a hand-off. */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "var acc = (acc || 0) + field['value']; acc",
                            "field['value'] + field['id'] - 1",
                            "var d = d || new Date(0); d.setTime(d.getTime() + 1000); " +
                            "d.getTime() / 1000" })
   void primitivesAndDatesMakeNoHome(String f) throws Exception {
      double[] v = crossSlot("held", f);
      assertAll(v, f);
      assertEquals(0, PoolTestSupport.homes(lastEnv), "no home");
      assertEquals(0, PoolTestSupport.metric(lastEnv, "HandOffs"), "no hand-off");
   }

   /** With the pool off nothing changes: every shape is kept, nothing warns. */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "array", "object", "nested", "cycle" })
   void poolOffIsUnchanged(String what) throws Exception {
      String f = switch(what) {
         case "array" -> ARRAY;
         case "object" -> OBJECT;
         case "nested" -> NESTED;
         default -> CYCLE;
      };
      AssetQuerySandbox box = PoolTestSupport.poolBox(false);
      TableLens t = make(box, base(ROWS), f, "T");
      double[] v = new double[ROWS + 1];

      for(int s = 1; s <= ROWS; s += 100) {
         read(t, v, s, Math.min(ROWS, s + 99));
      }

      assertAll(v, what);
      assertTrue(warnings().isEmpty(), () -> "no warning: " + warnings());
   }

   /**
    * Review finding 4 (Testing #77123, cond-home): a mutual reference on one thread. A
    * formula of A, in a batch of A on a claim of its own, reads B, whose batch runs on a
    * claim of its own and reads rows of A ahead: a batch of A nested in B's. That nested
    * batch runs on A's own claim again, where A's objects live, so it keeps them: the rows
    * match the pool off (which computes the rows of A in progress twice, as off the pool the
    * nested batch also does), with no warning. Without the re-entry the nested batch ran on
    * B's context and lost A's array (HOME_BUSY).
    */
   @Test
   void aBatchOfATableNestedInAnotherTablesBatchKeepsItsObjects() throws Exception {
      List<List<Double>> off = mutual(PoolTestSupport.poolBox(false));
      assertTrue(warnings().isEmpty(), () -> "no warning off the pool: " + warnings());
      List<List<Double>> on = mutual(box());
      assertEquals(off, on, "pool on vs off");
      assertTrue(warnings().isEmpty(), () -> "no warning: " + warnings());
   }

   /**
    * The loss path that remains (Testing #77123, cond-home review finding 5): the pool itself
    * can hold an idle home for a moment (its evictor's expiry hand-off, a take-over attempt)
    * while a batch of the table on another context needs the objects. They are then lost,
    * loudly and never stale: one warning per var, and the var starts over. Here a test thread
    * holds the home the way the pool would.
    */
   @Test
   void aHomeHeldByThePoolAtAPullLosesTheObjectsWithOneWarning() throws Exception {
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      PoolTestSupport.Hook hook = new PoolTestSupport.Hook();
      w.put("hook", hook);
      Object[] home = new Object[1];
      // the context of the batch that computes row 200 is the home once that batch ends
      hook.task = () -> home[0] = PoolTestSupport.currentSlot(w);
      TableLens t = make(box, base(ROWS), "var a = a || []; a.push(1); " +
         "if(field['id'] == 200) hook.fire(); a.length", "T");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 200);
      assertAll(v, 200, "before");
      assertNotNull(home[0]);
      Runnable release = PoolTestSupport.holdElsewhere(home[0]);

      try {
         read(t, v, 201, ROWS);
      }
      finally {
         release.run();
      }

      // the rows of that batch are exact; the next batch's a starts over, never stale
      int restart = 0;

      for(int r = 201; r <= ROWS && restart == 0; r++) {
         if(v[r] != r) {
            restart = r;
         }
      }

      assertTrue(restart > 200, "no loss");
      assertAll(v, restart - 1, "before the loss");

      for(int r = restart; r <= ROWS; r++) {
         assertEquals(r - restart + 1, v[r], "a starts over, never stale, row " + r);
      }

      List<String> warns = warningTexts();
      assertEquals(1, warns.size(), () -> "one warning: " + warns);
      assertTrue(warns.get(0).contains("\"a\" holds an array or object that stays on a " +
                                       "script context"), warns::toString);
   }

   // A fires B once at id 300, B fires A once at id 50; the rows of A and of B
   private static List<List<Double>> mutual(AssetQuerySandbox box) {
      PoolTestSupport.Hook toA = new PoolTestSupport.Hook();
      PoolTestSupport.Hook toB = new PoolTestSupport.Hook();
      box.getScriptEnv().put("toA", toA);
      box.getScriptEnv().put("toB", toB);
      TableLens a = make(box, base(ROWS), "var a = a || []; a.push(1); " +
         "if(field['id'] == 300) toB.fire(); a.length", "A");
      TableLens b = make(box, base(ROWS), "var q = q || []; q.push(1); " +
         "if(field['id'] == 50) toA.fire(); q.length", "B");
      toB.task = () -> b.moreRows(60);
      toA.task = () -> a.moreRows(320);
      List<Double> bv = new ArrayList<>();

      // B is resident before the batch that reads A
      for(int r = 1; r <= 20; r++) {
         assertTrue(b.moreRows(r));
         bv.add(num(b.getObject(r, 2)));
      }

      List<Double> av = new ArrayList<>();

      for(int r = 1; a.moreRows(r); r++) {
         av.add(num(a.getObject(r, 2)));
      }

      for(int r = 21; b.moreRows(r); r++) {
         bv.add(num(b.getObject(r, 2)));
      }

      return List.of(av, bv);
   }

   // --- helpers ---

   /**
    * Rows 1..200, then 201..600 under {@code how}, then 601.. back: held (another thread
    * holds a claimed context), busy (another thread runs a script), handoff (the pool hands
    * off the idle homes between the reads), takeover (no exclusive home: the other thread's
    * claim takes the home over after a hand-off).
    */
   private double[] crossSlot(String how, String formula) throws Exception {
      if(how.equals("takeover")) {
         SreeEnv.setProperty(MAX_HOMES, "0");
      }

      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      w.put("probe", probe);
      TableLens t = make(box, base(ROWS), formula, "T");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 200);

      switch(how) {
      case "held", "takeover" -> PoolTestSupport.whileHeldElsewhere(w, () -> read(t, v, 201, 600));
      case "handoff" -> {
         PoolTestSupport.handOffIdleHomes(w);
         read(t, v, 201, 600);
         PoolTestSupport.handOffIdleHomes(w);
      }
      default -> {
         PoolTestSupport.Callback cb = new PoolTestSupport.Callback(w);
         w.put("cb", cb);
         ExecutorService ex = Executors.newSingleThreadExecutor();

         try {
            Future<?> busy = ex.submit(() -> PoolTestSupport.run(w, "cb.block(); 1"));
            assertTrue(cb.entered.await(10, TimeUnit.SECONDS));

            try {
               read(t, v, 201, 600);
            }
            finally {
               cb.release.countDown();
               busy.get(10, TimeUnit.SECONDS);
            }
         }
         finally {
            ex.shutdownNow();
         }
      }
      }

      read(t, v, 601, ROWS);
      return v;
   }

   // a paged read of rows (100-row pages), optionally each page inside an outer lazy span
   private long pagedRead(String f, int rows, boolean spanned) throws Exception {
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      TableLens t = make(box, base(rows), f, "T");
      double[] v = new double[rows + 1];
      long t0 = System.nanoTime();

      for(int s = 1; s <= rows; s += 100) {
         if(spanned) {
            try(ScriptSpan span = w.openSpan()) {
               read(t, v, s, s + 99);
            }
         }
         else {
            read(t, v, s, s + 99);
         }
      }

      long ms = (System.nanoTime() - t0) / 1_000_000;

      for(int r = 1; r <= rows; r++) {
         assertEquals(r, v[r], "row " + r);
      }

      return ms;
   }

   private static void read(TableLens t, double[] v, int from, int to) {
      for(int r = from; r <= to; r++) {
         assertTrue(t.moreRows(r), "row " + r);
         v[r] = num(t.getObject(r, 2));
      }
   }

   private static void assertAll(double[] v, String what) {
      assertAll(v, v.length - 1, what);
   }

   private static void assertAll(double[] v, int to, String what) {
      List<String> bad = new ArrayList<>();

      for(int r = 1; r <= to; r++) {
         if(v[r] != r) {
            bad.add(r + "=" + v[r]);
         }
      }

      assertTrue(bad.isEmpty(), () -> what + ": " + bad.size() + " wrong rows, first " +
         bad.subList(0, Math.min(5, bad.size())));
   }

   private AssetQuerySandbox box() throws Exception {
      AssetQuerySandbox box = PoolTestSupport.poolBox(true);
      lastEnv = (WorksheetScriptEnv) box.getScriptEnv();
      envs.add(lastEnv);
      return box;
   }

   private List<ILoggingEvent> warnings() {
      return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
   }

   private List<String> warningTexts() {
      return warnings().stream().map(ILoggingEvent::getFormattedMessage).toList();
   }

   static final String MAX_HOMES = "script.ws.contextPool.maxHomes";
   static final String MAX_HOMES_PER_NODE = "script.ws.contextPool.maxHomesPerNode";
   static final String HAND_OFF_MILLIS = "script.ws.contextPool.handOffMillis";
   static final String HAND_OFF_ENTRIES = "script.ws.contextPool.handOffEntries";
   private static final int ROWS = 1200;
   private final List<WorksheetScriptEnv> envs = new ArrayList<>();
   private final PoolTestSupport.Probe probe = new PoolTestSupport.Probe();
   private WorksheetScriptEnv lastEnv;
   private Logger logger;
   private ListAppender<ILoggingEvent> appender;
}
