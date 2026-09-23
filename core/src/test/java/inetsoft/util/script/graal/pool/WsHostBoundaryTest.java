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
package inetsoft.util.script.graal.pool;

import inetsoft.util.script.ScriptException;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The E_ws host boundary (bug #76960, spec §6.8, §14.4, §14.11, §14.12 A1-A5; gates G11,
 * G12 rescoped): what a worksheet script hands to Java is a copy that is safe to read from any
 * thread, a value that cannot be kept apart from its context is rejected where it would be
 * kept, and the ScriptValueConverter shapes stay main's.
 */
@Tag("core")
class WsHostBoundaryTest {
   // --- P-interop: direct calls on Java objects (HostAccess mappings) ---

   @Test
   void plainObjectArgumentIsAProxyBackedCopyWithDefaultBoxing() throws Exception {
      WorksheetScriptEnv env = withTaker();
      run(env, "taker.take({a: 1, b: {c: 2}}); 1");
      assertTrue(taker.last instanceof CopyMap, String.valueOf(taker.last));
      Map<?, ?> map = (Map<?, ?>) taker.last;
      assertEquals(1, map.get("a"));
      assertTrue(map.get("b") instanceof CopyMap);
   }

   @Test
   void copiedObjectReadsBackLikeTheOriginal() throws Exception {
      WorksheetScriptEnv env = withTaker();
      assertEquals("{\"a\":1}|1|true",
                   run(env, "var o = taker.take({a: 1}); " +
                            "JSON.stringify(o) + '|' + Object.keys(o).length + '|' + (o.a === 1)"));
   }

   @Test
   void nullPrototypeObjectIsPlain() throws Exception {
      WorksheetScriptEnv env = withTaker();
      run(env, "var o = Object.create(null); o.a = 1; taker.take(o); 1");
      assertTrue(taker.last instanceof CopyMap, String.valueOf(taker.last));
      assertEquals(1, ((Map<?, ?>) taker.last).get("a"));
   }

   @Test
   void arrayArgumentIsAListCopy() throws Exception {
      WorksheetScriptEnv env = withTaker();
      run(env, "taker.takeList([1, [2, 3]]); 1");
      assertTrue(taker.last instanceof CopyList);
      assertEquals(2, ((List<?>) taker.last).size());
      assertTrue(((List<?>) taker.last).get(1) instanceof CopyList);
   }

   @Test
   void arrayToAMapParameterIsRejected() {
      WorksheetScriptEnv env = withTaker();
      assertThrows(ScriptException.class, () -> run(env, "taker.takeMap([1, 2]); 1"));
   }

   @Test
   void functionsAndObjectsHoldingFunctionsAreRejectedAsJavaArguments() {
      WorksheetScriptEnv env = withTaker();
      ScriptException fn = assertThrows(ScriptException.class,
                                        () -> run(env, "taker.take(function(){}); 1"));
      assertTrue(fn.getMessage().contains("function"), fn.getMessage());
      assertThrows(ScriptException.class, () -> run(env, "taker.take({f: function(){}}); 1"));
   }

   @Test
   void nonPlainObjectsAreRejectedAsJavaArguments() {
      WorksheetScriptEnv env = withTaker();

      for(String js : new String[] { "new Map()", "new Set()", "/x/", "Promise.resolve(1)",
                                     "new (class K {})()" })
      {
         assertThrows(ScriptException.class, () -> run(env, "taker.take(" + js + "); 1"), js);
      }
   }

   @Test
   void jsDateArgumentIsAJavaDate() throws Exception {
      WorksheetScriptEnv env = withTaker();
      run(env, "taker.take(new Date(0)); 1");
      assertEquals(new Date(0), taker.last);
   }

   @Test
   void outParametersAreNoLongerVisibleToTheScript() throws Exception {
      WorksheetScriptEnv env = withTaker();
      assertEquals(2.0, run(env, "var l = [1, 2]; taker.mutateList(l); l.length"));
      assertEquals("undefined", run(env, "var m = {a: 1}; taker.mutateMap(m); typeof m.z"));
      assertEquals(3.0, run(env,
         "var a = [3, 1, 2]; Java.type('java.util.Collections').sort(a); a[0]"));
   }

   // --- P-toHost: ScriptValueConverter (global functions, scope writes, results) ---

   @Test
   void transientGlobalFunctionsStillAcceptFunctionsAndNonPlainObjects() throws Exception {
      WorksheetScriptEnv env = env();
      assertEquals("false|false", run(env, "isNull(function(){}) + '|' + isNull(new Map())"));
   }

   @Test
   void toHostKeepsMainShapes() throws Exception {
      WorksheetScriptEnv env = env();
      Object[] array = (Object[]) run(env, "[7, 17, 44]");
      assertEquals(List.of(7.0, 17.0, 44.0), Arrays.asList(array));
      Object map = run(env, "({a: 1, b: [1, 2]})");
      assertTrue(map instanceof CopyMap, String.valueOf(map));
      assertEquals(1.0, ((Map<?, ?>) map).get("a"));
      assertTrue(((Map<?, ?>) map).get("b") instanceof Object[]);
   }

   @Test
   void unstorableExecResultsAreErrors() {
      WorksheetScriptEnv env = env();
      assertThrows(ScriptException.class, () -> run(env, "(function(){})"));
      assertThrows(ScriptException.class, () -> run(env, "new Map()"));
   }

   @Test
   void scopeWritesCopyPlainValuesAndRejectFunctions() throws Exception {
      WorksheetScriptEnv env = env();
      MapScope holder = new MapScope();
      env.put("holder", holder);
      run(env, "holder.x = {a: 1}; holder.t = [['h'], [1]]; 1");
      assertTrue(holder.values.get("x") instanceof CopyMap);
      Object[] table = (Object[]) holder.values.get("t");
      assertTrue(table[0] instanceof Object[], "Sheet.table = [[...]] keeps Object[] rows");
      assertThrows(ScriptException.class, () -> run(env, "holder.f = function(){}; 1"));
      assertThrows(ScriptException.class, () -> run(env, "holder.m = new Map(); 1"));
   }

   // --- G11: a copy is readable from another thread while its context is busy ---

   @Test
   void copyIsReadableWhileTheOriginatingContextIsBusy() throws Exception {
      WorksheetScriptEnv env = withTaker();
      Callback cb = new Callback(env);
      env.put("cb", cb);
      ExecutorService executor = Executors.newSingleThreadExecutor();

      try {
         Future<Object> busy = executor.submit(() -> run(env, "taker.take({a: 1}); cb.block(); 1"));
         assertTrue(cb.entered.await(10, TimeUnit.SECONDS));
         assertEquals(1, ((Map<?, ?>) taker.last).get("a"));
         cb.release.countDown();
         assertEquals(1.0, busy.get(10, TimeUnit.SECONDS));
      }
      finally {
         cb.release.countDown();
         executor.shutdownNow();
      }
   }

   /**
    * ROUTE_FUNC: a viewsheet/report env is unaffected and still hands Java a live view that
    * fails fast from another thread while its context is busy (main's behaviour).
    */
   @Test
   void plainEnvIsUnchanged() throws Exception {
      GraalJavaScriptEnv plain = new GraalJavaScriptEnv();
      plain.init();
      Taker plainTaker = new Taker();
      Callback cb = new Callback(plain);
      plain.put("taker", plainTaker);
      plain.put("cb", cb);
      ExecutorService executor = Executors.newSingleThreadExecutor();

      try {
         Future<Object> busy = executor.submit(() -> run(plain, "taker.take({a: 1}); cb.block(); 1"));
         assertTrue(cb.entered.await(10, TimeUnit.SECONDS));
         assertFalse(plainTaker.last instanceof CopyMap);
         assertThrows(IllegalStateException.class, () -> ((Map<?, ?>) plainTaker.last).get("a"));
         cb.release.countDown();
         busy.get(10, TimeUnit.SECONDS);
      }
      finally {
         cb.release.countDown();
         executor.shutdownNow();
      }
   }

   // --- G12 rescoped (§14.7, §14.11): a viewsheet value read by a worksheet script ---

   @Test
   void viewsheetValueIsALiveReferenceThatFailsFastWhileItsOwnerIsBusy() throws Exception {
      GraalJavaScriptEnv vs = new GraalJavaScriptEnv();
      vs.init();
      Object vsValue = run(vs, "({a: 1})");
      Callback vsCb = new Callback(vs);
      vs.put("cb", vsCb);

      WorksheetScriptEnv ws = withTaker();
      ws.put("vsval", vsValue);
      assertEquals(1.0, run(ws, "vsval.a"), "owner free: the live reference reads");

      long foreign = WsValueCopier.foreignValueCount();
      run(ws, "taker.take(vsval); 1");
      assertFalse(taker.last instanceof CopyMap, "a foreign value is never copied through its owner");
      assertTrue(WsValueCopier.foreignValueCount() > foreign, "foreign value not counted");

      ExecutorService executor = Executors.newCachedThreadPool();

      try {
         Future<Object> owner = executor.submit(() -> run(vs, "cb.block(); 1"));
         assertTrue(vsCb.entered.await(10, TimeUnit.SECONDS));
         long start = System.nanoTime();
         assertThrows(Exception.class, () -> run(ws, "vsval.a"));
         assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(2), "the read waited");
         vsCb.release.countDown();
         assertEquals(1.0, owner.get(10, TimeUnit.SECONDS), "the owner's exec was not broken");

         // reverse race: a worksheet reader walks the value while the owner executes; either
         // side may fail fast, neither may wait (residual shared with main, §11)
         Future<Integer> reader = executor.submit(() -> race(() -> run(ws,
            "var s = 0; for(var i = 0; i < 2000; i++) { s += vsval.a; } s")));
         Future<Integer> ownerLoop = executor.submit(() -> race(() -> run(vs, "1 + 1")));
         reader.get(20, TimeUnit.SECONDS);
         ownerLoop.get(20, TimeUnit.SECONDS);
      }
      finally {
         vsCb.release.countDown();
         executor.shutdownNow();
      }
   }

   private static int race(Callable<Object> task) {
      long end = System.currentTimeMillis() + 1000;
      int failures = 0;

      while(System.currentTimeMillis() < end) {
         try {
            task.call();
         }
         catch(Exception ex) {
            failures++;
         }
      }

      return failures;
   }

   private WorksheetScriptEnv withTaker() {
      WorksheetScriptEnv env = env();
      env.put("taker", taker);
      return env;
   }

   private final Taker taker = new Taker();
}
