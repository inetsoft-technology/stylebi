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

import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77123 (context-pool regression C1): with the pool on, a JS array or object passed to a
 * Java method is the script's own value while the passing exec runs on its thread, so Java
 * mutations (Collections sort/reverse/swap/fill, a JS comparator sort, out parameters) reach
 * the script as with the pool off; a Java object that keeps it reads a host copy from other
 * threads (G11) and the script's final state after the exec.
 */
@Tag("core")
class WsHostLiveArgumentTest {
   private static final String[][] CASES = {
      { "var a = [3, 1, 2]; java.util.Collections.sort(a); String(a)", "1,2,3" },
      { "var a = [1, 2, 3]; java.util.Collections.reverse(a); String(a)", "3,2,1" },
      { "var a = [1, 2, 3]; java.util.Collections.swap(a, 0, 2); String(a)", "3,2,1" },
      { "var a = [1, 2, 3]; java.util.Collections.fill(a, 0); String(a)", "0,0,0" },
      { "var a = [1, 2, 3, 4, 5]; java.util.Collections.shuffle(a); " +
           "a.length + '|' + a.slice().sort().join()", "5|1,2,3,4,5" },
      { "var a = [3, 1, 2]; java.util.Collections.sort(a, function(x, y) { return y - x; }); " +
           "String(a)", "3,2,1" },
      { "var a = [[3, 1, 2]]; java.util.Collections.sort(a[0]); String(a[0])", "1,2,3" },
      { "var a = [3, 1.5, 2]; java.util.Collections.sort(a); String(a)", "1.5,2,3" },
      { "var l = [1, 2]; taker.mutateList(l); String(l)", "1,2,99" },
      { "var m = {a: 1}; taker.mutateMap(m); JSON.stringify(m)", "{\"a\":1,\"z\":1}" },
      // identity survives a Java sort of objects with a JS comparator
      { "var o1 = {v: 2}, o2 = {v: 1}; var a = [o1, o2]; " +
           "java.util.Collections.sort(a, function(x, y) { return x.v - y.v; }); " +
           "(a[0] === o2) + '|' + a[0].v + '|' + (a[1] === o1)", "true|1|true" },
      // a view returned to the script and written there writes the array
      { "var a = [1]; var r = taker.takeList(a); r[1] = 5; String(a)", "1,5" },
      // a Java method that runs a nested exec of the same env, then mutates the list
      { "var a = [1]; nester.nestedThenAdd(a, '1 + 1'); String(a) + '|' + nester.nested",
        "1,42|2" },
      // a JS object written through a view held in a Java collection during the exec
      { "var o = {a: 1}; var l = new java.util.ArrayList(); l.add(o); l.get(0).a = 2; String(o.a)",
        "2" },
   };

   @Test
   void javaMutationsReachTheScriptAsWithThePoolOff() throws Exception {
      for(String[] c : CASES) {
         assertEquals(c[1], eval(env(), c[0]), "pool on: " + c[0]);
         GraalJavaScriptEnv plain = new GraalJavaScriptEnv();
         plain.init();
         assertEquals(c[1], eval(plain, c[0]), "pool off: " + c[0]);
      }
   }

   @Test
   void aKeptArrayIsTheFinalStateAfterTheExecOnAnyThread() throws Exception {
      WorksheetScriptEnv env = withTaker(env());
      run(env, "var a = [3, 1]; taker.takeList(a); a.push(7); 1");
      assertEquals("[3, 1, 7]", String.valueOf(taker.last));
      assertEquals(List.of(3, 1, 7), new ArrayList<>((List<?>) taker.last));
      ExecutorService executor = Executors.newSingleThreadExecutor();

      try {
         assertEquals("[3, 1, 7]", executor.submit(() -> String.valueOf(taker.last))
            .get(10, TimeUnit.SECONDS));
      }
      finally {
         executor.shutdownNow();
      }

      // after its exec it is a copy: a later exec passing another array does not touch it
      run(env, "var b = [8]; taker.mutateList(b); 1");
      assertEquals("[3, 1, 7]", String.valueOf(taker.last));
      env.retire();
      assertEquals("[3, 1, 7]", String.valueOf(taker.last), "readable after retire");
   }

   /**
    * G11, List and Map variants: another thread reads the kept values while the originating
    * context is busy and gets host copies (as first passed), with no Multi threaded access;
    * after the exec they are the script's final state.
    */
   @Test
   void keptValuesAreReadableWhileTheOriginatingContextIsBusy() throws Exception {
      WorksheetScriptEnv env = withTaker(env());
      Taker second = new Taker();
      env.put("second", second);
      Callback cb = new Callback(env);
      env.put("cb", cb);
      ExecutorService executor = Executors.newSingleThreadExecutor();

      try {
         Future<Object> busy = executor.submit(() -> run(env,
            "var a = [3, 1, 2]; taker.takeList(a); var o = {a: 1}; second.take(o); " +
               "java.util.Collections.sort(a); cb.block(); a.push(9); o.b = 2; 1"));
         assertTrue(cb.entered.await(10, TimeUnit.SECONDS));
         List<?> list = (List<?>) taker.last;
         Map<?, ?> map = (Map<?, ?>) second.last;
         assertEquals(3, list.size());
         assertEquals(List.of(3, 1, 2), new ArrayList<>(list), "the copy as first passed");
         assertEquals("[3, 1, 2]", list.toString());
         assertEquals(Map.of("a", 1), new HashMap<>(map));
         assertEquals(1, map.get("a"));
         cb.release.countDown();
         assertEquals(1.0, busy.get(10, TimeUnit.SECONDS));
         assertEquals(List.of(1, 2, 3, 9), new ArrayList<>(list));
         assertEquals(Map.of("a", 1, "b", 2), new HashMap<>(map));
      }
      finally {
         cb.release.countDown();
         executor.shutdownNow();
      }
   }

   /**
    * The pool-off path is main's: Java gets Graal's own live view, not a pool view or copy.
    */
   @Test
   void poolOffStillHandsJavaGraalsLiveView() throws Exception {
      GraalJavaScriptEnv plain = new GraalJavaScriptEnv();
      plain.init();
      Taker plainTaker = new Taker();
      plain.put("taker", plainTaker);
      assertEquals("1,2,3", run(plain, "var a = [3, 1, 2]; taker.takeList(a); " +
         "java.util.Collections.sort(a); String(a)"));
      assertFalse(plainTaker.last instanceof ProxyArray, String.valueOf(plainTaker.last));
      assertFalse(plainTaker.last.getClass().getName().startsWith("inetsoft."));
   }

   private WorksheetScriptEnv withTaker(WorksheetScriptEnv env) {
      env.put("taker", taker);
      return env;
   }

   private static String eval(ScriptEnv env, String js) throws Exception {
      env.put("taker", new Taker());
      env.put("nester", new Nester(env));
      return String.valueOf(run(env, js));
   }

   /**
    * A Java method that runs a nested script of its env while it holds the list, then
    * mutates it.
    */
   public static final class Nester {
      Nester(ScriptEnv env) {
         this.env = env;
      }

      public void nestedThenAdd(List<Object> list, String js) throws Exception {
         Object result = run(env, js);
         nested = result instanceof Number n ? String.valueOf(n.intValue()) : String.valueOf(result);
         list.add(42);
      }

      public String nested;
      private final ScriptEnv env;
   }

   private final Taker taker = new Taker();
}
