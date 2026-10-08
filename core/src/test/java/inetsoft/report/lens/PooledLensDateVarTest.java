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
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
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

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static inetsoft.report.lens.FormulaTableLensVarTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A Date in a formula table's var across pooled batches that run on different script contexts
 * (Testing #77123, B1 residual): as with the pool off, the var keeps one Date for the whole
 * table - in-place changes, aliases, an Invalid Date - whichever context a batch runs on. At
 * the end of each batch the Date's time value is saved without running script code; a batch
 * on another context rebuilds it there from that time value. A table holding an array or
 * object (or a Proxy) keeps it on its home context (B1 residual part 2); the tests of such
 * tables run with no exclusive home, so another thread's claim takes the home over after a
 * hand-off and the table's next batch really runs on another context: an array or object is
 * kept there, a function reads as undefined, with one warning naming what it holds. A
 * Date-only table has no home and takes the batch-end Date path.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PooledLensDateVarTest {
   static final String DATE =
      "var d = d || new Date(0); d.setTime(d.getTime() + 1000); d.getTime() / 1000";
   static final String YEAR =
      "var d = d || new Date(2000, 0, 1); d.setFullYear(d.getFullYear() + 1); " +
      "(d instanceof Date ? 1 : -1) * (d.getFullYear() - 2000)";
   static final String ALIAS =
      "var a = a || new Date(0); var b = b || a; a.setTime(a.getTime() + 1000); " +
      "(a === b ? 1 : -1) * b.getTime() / 1000";
   // made counts the Dates the formula created: an Invalid Date is kept, not re-created
   static final String INVALID =
      "var made = made || 0; var d = d || (made++, new Date(NaN)); var k = (k || 0) + 1; " +
      "made == 1 && isNaN(d.getTime()) && d instanceof Date ? k : -1";
   static final String OWNPROP =
      "var d = d || (function() { var x = new Date(0); x.tag = 'kept'; return x; })(); " +
      "d.setTime(d.getTime() + 1000); d.getTime() / 1000";
   static final String SUBCLASS =
      "var d = d || new (class extends Date {})(0); d.setTime(d.getTime() + 1000); " +
      "d.getTime() / 1000";
   // an Invalid Date with an own property: rebuilt without it, with the warning
   static final String INVALIDPROP =
      "var d = d || (function() { var x = new Date(NaN); x.tag = 1; return x; })(); " +
      "var k = (k || 0) + 1; isNaN(d.valueOf()) && d instanceof Date ? k : -1";
   // saving a Date uses the intrinsic getTime, never the script's (it loops forever)
   static final String LOOPING_GETTIME = "Date.prototype.getTime = function() { while(true) {} }; ";
   // saving it must not call any of these: they loop forever
   static final String HOSTILE =
      "var d = d || (function() { var x = new Date(0); var loop = function() { while(true) {} }; " +
      "x.valueOf = loop; x.toString = loop; x.toJSON = loop; x[Symbol.toPrimitive] = loop; " +
      "Object.defineProperty(x, 'g', { get: loop, enumerable: true }); return x; })(); " +
      "Date.prototype.setTime.call(d, Date.prototype.getTime.call(d) + 1000); " +
      "Date.prototype.getTime.call(d) / 1000";
   // a Proxy is never a Date; saving it must not run a trap (they loop forever)
   static final String PROXY =
      "var made = made || 0; var p = p || (made++, new Proxy(new Date(0), { " +
      "ownKeys() { while(true) {} }, get() { while(true) {} }, " +
      "getPrototypeOf() { while(true) {} }, getOwnPropertyDescriptor() { while(true) {} }, " +
      "has() { while(true) {} } })); made";

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
      SreeEnv.remove(MAX_HOMES);

      for(WorksheetScriptEnv env : envs) {
         env.retire();
      }

      envs.clear();
   }

   static Stream<Arguments> dates() {
      List<Arguments> args = new ArrayList<>();

      for(String how : new String[] { "held", "busy" }) {
         for(String[] f : new String[][] { { "date", DATE }, { "year", YEAR },
                                           { "alias", ALIAS }, { "invalid", INVALID } })
         {
            args.add(Arguments.of(how, f[0], f[1]));
         }
      }

      return args.stream();
   }

   /**
    * A Date accumulator, a Date changed with setFullYear (still instanceof Date after a
    * rebuild), two vars holding one Date (still one object) and an Invalid Date (kept, not
    * created again) count every row, while some batches run on another context.
    */
   @ParameterizedTest(name = "{0} {1}")
   @MethodSource("dates")
   void aDateVarIsKeptAcrossPooledContexts(String how, String what, String formula)
      throws Exception
   {
      double[] v = crossSlot(how, formula);
      assertAll(v, what + " " + how);
      assertTrue(warnings().isEmpty(), () -> "no warning: " + warnings());
   }

   /**
    * A Date with its own properties, or of a subclass, is rebuilt on another context as a
    * plain Date from its time value, with one warning naming the var and what it drops.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "ownprop", "subclass", "invalidprop" })
   void aDateWithOwnPropertiesOrASubclassIsRebuiltFromItsTime(String what) throws Exception {
      double[] v = crossSlot("held", switch(what) {
         case "ownprop" -> OWNPROP;
         case "subclass" -> SUBCLASS;
         default -> INVALIDPROP;
      });
      assertAll(v, what);
      List<ILoggingEvent> warns = warnings();
      assertEquals(1, warns.size(), () -> "one warning: " + warns);
      String msg = warns.get(0).getFormattedMessage();
      assertTrue(msg.contains("\"d\" holds a Date " +
         (what.equals("subclass") ? "of a subclass" : "with the properties tag")), msg);
      assertTrue(msg.contains("rebuilds it as a plain Date from its time value"), msg);
   }

   /**
    * An object that only inherits Date.prototype (its meta object is the intrinsic Date, but
    * it has no Date internal slot) is no Date: on another context it reads as undefined with
    * one warning, as any other object, and is never rebuilt as an Invalid Date (review I1).
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "create", "setPrototypeOf", "es5" })
   void anObjectInheritingDatePrototypeIsNoDate(String what) {
      String make = switch(what) {
         case "create" -> "Object.create(Date.prototype)";
         case "setPrototypeOf" -> "Object.setPrototypeOf({}, Date.prototype)";
         default -> "(function() { function Stamp() {} " +
            "Stamp.prototype = Object.create(Date.prototype); return new Stamp(); })()";
      };
      // made counts the objects the formula created; -1 if the var holds a real Date
      String f = "var made = made || 0; var o = o || (made++, " + make + "); " +
         "Object.prototype.toString.call(o) == '[object Object]' && " +
         "Object.getPrototypeOf(o) !== null ? made : -1";
      double[][] v = new double[1][];
      // no Date: the table keeps the object on a home, which the other thread takes over
      noExclusiveHome();
      assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
         v[0] = crossSlot("held", f);
      });

      for(int r = 1; r <= ROWS; r++) {
         assertTrue(v[0][r] >= 1.0, "row " + r + " holds a real Date: " + v[0][r]);
      }

      assertEquals(1.0, v[0][200]);
      assertTrue(v[0][ROWS] > 1.0 && v[0][ROWS] < 100, "made " + v[0][ROWS]);
      List<ILoggingEvent> warns = warnings();
      assertEquals(1, warns.size(), () -> "one warning: " + warns);
      String msg = warns.get(0).getFormattedMessage();
      assertTrue(msg.contains("\"o\" holds an object that inherits Date.prototype but is no " +
                              "Date created on another"), msg);
   }

   /**
    * A Date and an Invalid Date are saved with the intrinsic getTime, not the one a script
    * put on Date.prototype (it loops forever): both are kept, and nothing hangs.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "date", "invalid" })
   void aReplacedGetTimeIsNotCalled(String what) {
      String f = LOOPING_GETTIME + (what.equals("date")
         ? "var d = d || new Date(0); d.setTime(d.valueOf() + 1000); d.valueOf() / 1000"
         : "var made = made || 0; var d = d || (made++, new Date(NaN)); var k = (k || 0) + 1; " +
           "made == 1 && isNaN(d.valueOf()) && d instanceof Date ? k : -1");
      assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
         double[] v = crossSlot("held", f);
         assertAll(v, what);
      });
      assertTrue(warnings().isEmpty(), () -> "no warning: " + warnings());
   }

   /**
    * Saving a Date runs no script code: a Date whose valueOf, toString, toJSON,
    * Symbol.toPrimitive and an enumerable getter loop forever is kept, and nothing hangs.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "held", "busy" })
   void theSnapshotRunsNoScriptCode(String how) {
      assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
         double[] v = crossSlot(how, HOSTILE);
         assertAll(v, "hostile " + how);
      });

      List<ILoggingEvent> warns = warnings();
      assertEquals(1, warns.size(), () -> "one warning: " + warns);
      String msg = warns.get(0).getFormattedMessage();
      assertTrue(msg.contains("valueOf") && msg.contains("toJSON") && msg.contains("g"), msg);
   }

   /**
    * A Proxy of a Date is not a Date (codec ordering rule: member keys are read only after
    * isDate and isInstant): its traps, which loop forever, never run; on another context it
    * reads as undefined, with one warning naming it.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "held", "busy" })
   void aProxyOfADateIsNotReadThroughItsTraps(String how) {
      double[][] v = new double[1][];
      // a Proxy is no Date: the table keeps it on a home, which the other thread takes over
      noExclusiveHome();
      assertTimeoutPreemptively(Duration.ofSeconds(60), () -> {
         v[0] = crossSlot(how, PROXY);
      });

      assertEquals(1.0, v[0][1]);
      // one Proxy per context the var moved to; never more than the batches
      assertTrue(v[0][ROWS] > 1.0 && v[0][ROWS] < 100, "made " + v[0][ROWS]);
      List<ILoggingEvent> warns = warnings();
      assertEquals(1, warns.size(), () -> "one warning: " + warns);
      String msg = warns.get(0).getFormattedMessage();
      assertTrue(msg.contains("\"p\" holds a Proxy object created on another"), msg);
   }

   /**
    * A batch that ends in a real script timeout keeps its Date: the next batch, on another
    * context, continues from it.
    */
   @Test
   void aBatchEndedByATimeoutKeepsItsDate() throws Exception {
      String previous = SreeEnv.getProperty("script.execution.timeout");
      SreeEnv.setProperty("script.execution.timeout", "2");
      refreshTimeout();

      try {
         assertTimeoutPreemptively(Duration.ofSeconds(90), () -> {
            AssetQuerySandbox box = box();
            WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
            TableLens t = make(box, base(ROWS), "var d = d || new Date(0); " +
               "d.setTime(d.getTime() + 1000); if(field['id'] == 300) { while(true) {} } " +
               "d.getTime() / 1000", "T");
            double[] v = new double[ROWS + 1];
            int failedAt = -1;

            for(int r = 1; r <= 400; r++) {
               try {
                  t.moreRows(r);
                  v[r] = num(t.getObject(r, 2));
               }
               catch(Throwable ex) {
                  failedAt = r;
                  break;
               }
            }

            assertTrue(failedAt > 0 && failedAt <= 300, "the timeout ended a batch: " + failedAt);
            final int from = failedAt;
            PoolTestSupport.whileHeldElsewhere(w, () -> {
               read(t, v, from, 299);
               // the timed-out row has no value: its read fails with the stop, every time,
               // without running into the timeout again (bug #77949)
               assertTrue(t.moreRows(300));

               for(int i = 0; i < 2; i++) {
                  long start = System.currentTimeMillis();
                  Throwable stop = assertThrows(Throwable.class, () -> t.getObject(300, 2));
                  assertTrue(ScriptTimeoutGuard.isStop(stop), "a stop: " + stop);
                  assertTrue(System.currentTimeMillis() - start < 1500, "not run again");
               }

               read(t, v, 301, 800);
            });

            assertEquals(299.0, v[299]);
            assertEquals(301.0, v[301], "the Date continues after the timed-out batch");
            assertEquals(800.0, v[800]);
         });
      }
      finally {
         SreeEnv.setProperty("script.execution.timeout", previous);
         refreshTimeout();
      }
   }

   /**
    * While an outer claim keeps this thread's context, the lens lock is free between
    * batches and another thread's batch runs on another context: every batch end saves the
    * Date, whatever the claim depth.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "date", "alias" })
   void anOuterClaimHeldWhileAnotherThreadReads(String what) throws Exception {
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      TableLens t = make(box, base(ROWS), what.equals("date") ? DATE : ALIAS, "T");
      double[] v = new double[ROWS + 1];

      try(SlotClaim outer = w.claimSlot()) {
         read(t, v, 1, 200);
         ExecutorService ex = Executors.newSingleThreadExecutor();

         try {
            ex.submit(() -> {
               read(t, v, 201, 600);
               return null;
            }).get(60, TimeUnit.SECONDS);
         }
         finally {
            ex.shutdownNow();
         }

         read(t, v, 601, ROWS);
      }

      assertAll(v, what);
   }

   /**
    * A Date read in the next batch on the same context is the same object (no rebuild): its
    * own property is kept and nothing warns.
    */
   @Test
   void aDateOnTheSameContextKeepsItsIdentity() throws Exception {
      TableLens t = make(box(), base(ROWS), "var d = d || (function() { var x = new Date(0); " +
         "x.n = 0; return x; })(); d.n++; d.setTime(d.getTime() + 1000); " +
         "d.n == d.getTime() / 1000 ? d.n : -1", "T");
      double[] v = new double[ROWS + 1];

      for(int s = 1; s <= ROWS; s += 100) {
         read(t, v, s, Math.min(ROWS, s + 99));
      }

      assertAll(v, "same context");
      assertTrue(warnings().isEmpty(), () -> "no warning: " + warnings());
   }

   /**
    * A value that cannot be read when the batch ends is lost, never kept from an older
    * batch: another context reads it as undefined, with one warning.
    */
   @Test
   void aSnapshotFailureLosesTheValueWithAWarning() throws Exception {
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      TableLens t = make(box, base(ROWS), DATE, "T");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 50);
      PoolTestSupport.failOwnedValueReads(() -> new IllegalStateException("probe"));
      read(t, v, 51, 200);
      PoolTestSupport.failOwnedValueReads(null);
      assertEquals(0, SlotClaim.openClaims(), "the batch's claim is closed");
      PoolTestSupport.whileHeldElsewhere(w, () -> read(t, v, 201, 600));

      for(int r = 1; r <= 200; r++) {
         assertEquals(r, v[r], "row " + r);
      }

      int first = 200;

      while(first < 600 && v[first + 1] == first + 1) {
         first++;
      }

      assertTrue(first < 600, "the other context restarts the Date");
      assertEquals(1.0, v[first + 1], "restarts, not an older snapshot");
      List<ILoggingEvent> warns = warnings();
      assertEquals(1, warns.size(), () -> "one warning: " + warns);
      assertTrue(warns.get(0).getFormattedMessage().contains("could not be read"),
                 warns.get(0).getFormattedMessage());
   }

   /**
    * An error the snapshot does not catch never skips closing the batch's claim or the lens
    * lock: the next read works on this and on another thread.
    */
   @Test
   void anErrorInTheSnapshotStillClosesTheSpan() throws Exception {
      TableLens t = make(box(), base(ROWS), DATE, "T");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 50);
      PoolTestSupport.failOwnedValueReads(() -> new AssertionError("probe error"));

      try {
         assertThrows(AssertionError.class, () -> t.moreRows(200));
      }
      finally {
         PoolTestSupport.failOwnedValueReads(null);
      }

      assertEquals(0, SlotClaim.openClaims(), "the batch's claim is closed");
      ExecutorService ex = Executors.newSingleThreadExecutor();

      try {
         assertTrue(ex.submit(() -> t.moreRows(300)).get(30, TimeUnit.SECONDS),
                    "the lens lock is released");
      }
      finally {
         ex.shutdownNow();
      }
   }

   /** With the pool off nothing changes: the Date is kept, nothing warns. */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "date", "alias", "ownprop" })
   void poolOffIsUnchanged(String what) throws Exception {
      String f = what.equals("date") ? DATE : what.equals("alias") ? ALIAS : OWNPROP;
      AssetQuerySandbox box = PoolTestSupport.poolBox(false);

      TableLens t = make(box, base(ROWS), f, "T");
      double[] v = new double[ROWS + 1];

      for(int s = 1; s <= ROWS; s += 100) {
         read(t, v, s, Math.min(ROWS, s + 99));
      }

      assertAll(v, what);
      assertTrue(warnings().isEmpty(), () -> "no warning: " + warnings());
      FormulaTableLens lens = (FormulaTableLens) t;
      assertFalse(lens.moreRows(TableLens.EOT));
   }

   /**
    * An array or object is kept on another context (B1 residual part 2), with no warning; a
    * function is not: it reads as undefined there, with one warning naming it, and the idiom
    * creates it again, so the count is kept.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "an array", "an object", "a function" })
   void anArrayOrObjectIsKeptAndAFunctionReadsAsUndefinedWithOneWarning(String kind)
      throws Exception
   {
      String f = switch(kind) {
      case "an array" -> "var a = a || []; a.push(1); a.length";
      case "an object" -> "var a = a || {n: 0}; a.n++; a.n";
      default -> "var a = a || function(x) { return x + 1; }; var k = a(k || 0); k";
      };
      noExclusiveHome();
      double[] v = crossSlot("held", f);
      assertAll(v, kind);
      List<ILoggingEvent> warns = warnings();

      if(kind.equals("a function")) {
         assertEquals(1, warns.size(), () -> "one warning: " + warns);
         String msg = warns.get(0).getFormattedMessage();
         assertTrue(msg.contains("\"a\" holds " + kind + " created on another"), msg);
         assertTrue(msg.contains("Keep a number, string, boolean, Date, or an array or " +
                                 "plain object"), msg);
      }
      else {
         assertTrue(warns.isEmpty(), () -> "no warning: " + warns);
      }
   }

   /** A completed table keeps no script object and no snapshot. */
   @Test
   void aCompletedTableDropsItsSnapshots() throws Exception {
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      FormulaTableLens t = (FormulaTableLens) make(box, base(ROWS), DATE, "T");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 200);
      PoolTestSupport.whileHeldElsewhere(w, () -> read(t, v, 201, 600));
      read(t, v, 601, ROWS);
      assertAll(v, "date");
      Object scope = ownedScope(t);
      assertFalse(((Map<?, ?>) field(scope, "snapshots")).isEmpty(), "a running table saves");

      assertFalse(t.moreRows(TableLens.EOT));
      assertTrue(((Map<?, ?>) field(scope, "snapshots")).isEmpty(), "completed: no snapshot");

      for(Object value : ((Map<?, ?>) field(scope, "valmap")).values()) {
         assertFalse(value instanceof org.graalvm.polyglot.Value, "holds " + value);
      }
   }

   // --- helpers ---

   /**
    * Rows 1..200 on this thread's context, 201..600 on another one (held: another thread
    * holds this thread's usual context; busy: the primary context runs a script on another
    * thread), 601.. back.
    */
   private double[] crossSlot(String how, String formula) throws Exception {
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      TableLens t = make(box, base(ROWS), formula, "T");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 200);

      if(how.equals("held")) {
         PoolTestSupport.whileHeldElsewhere(w, () -> read(t, v, 201, 600));
      }
      else {
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

      read(t, v, 601, ROWS);
      return v;
   }

   private static void read(TableLens t, double[] v, int from, int to) {
      for(int r = from; r <= to; r++) {
         assertTrue(t.moreRows(r), "row " + r);
         v[r] = num(t.getObject(r, 2));
      }
   }

   private static void assertAll(double[] v, String what) {
      List<String> bad = new ArrayList<>();

      for(int r = 1; r <= ROWS; r++) {
         if(v[r] != r) {
            bad.add(r + "=" + v[r]);
         }
      }

      assertTrue(bad.isEmpty(), () -> what + ": " + bad.size() + " wrong rows, first " +
         bad.subList(0, Math.min(5, bad.size())));
   }

   // no exclusive home for a table that has one (see the class comment); a Date-only table
   // never has a home
   private static void noExclusiveHome() {
      SreeEnv.setProperty(MAX_HOMES, "0");
   }

   private AssetQuerySandbox box() throws Exception {
      AssetQuerySandbox box = PoolTestSupport.poolBox(true);
      envs.add((WorksheetScriptEnv) box.getScriptEnv());
      return box;
   }

   private List<ILoggingEvent> warnings() {
      return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
   }

   private static void refreshTimeout() throws Exception {
      Field field = GraalJavaScriptEngine.class.getDeclaredField("TIMEOUT_PROP");
      field.setAccessible(true);
      ((SreeEnv.Value) field.get(null)).updateValue();
   }

   private static final String MAX_HOMES = "script.ws.contextPool.maxHomes";
   private static final int ROWS = 1200;
   private final List<WorksheetScriptEnv> envs = new ArrayList<>();
   private Logger logger;
   private ListAppender<ILoggingEvent> appender;
}
