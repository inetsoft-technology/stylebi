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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77123 (context-pool C1), independent isolation checks of the live Java-argument views:
 * no guest value survives the exec on any path (including Java calls and nested execs made by
 * getters during the completion re-snapshot), host exceptions end the exec cleanly, S-T-S
 * nesting on one thread, views kept across execs and contexts, and a two-thread read hammer
 * across the completion swap (G11: no Multi threaded access, no torn bulk reads).
 */
@Tag("core")
class WsLiveViewIsolationTest {
   public static final class H {
      public Object keep(Object v) {
         kept.add(v);
         return v;
      }

      public Object keepList(List<?> v) {
         kept.add(v);
         return v;
      }

      public int ctx() {
         Context c = WsExecContext.currentContext();
         return c == null ? 0 : System.identityHashCode(c);
      }

      public String readLast() {
         Object last = kept.get(kept.size() - 1);
         return String.valueOf(last) + "|" + ((List<?>) last).size();
      }

      public void writeLast() {
         @SuppressWarnings("unchecked") List<Object> last = (List<Object>) kept.get(kept.size() - 1);
         last.add(55);
      }

      @SuppressWarnings("unchecked")
      public void add4(Object l) {
         ((List<Object>) l).add(4);
      }

      @SuppressWarnings("unchecked")
      public void putM(Object m) {
         ((Map<String, Object>) m).put("m", 2);
      }

      public void boomRuntime() {
         throw new IllegalStateException("java boom");
      }

      public void boomError() {
         throw new AssertionError("java error boom");
      }

      public Object nested(String js) throws Exception {
         return run(other, js);
      }

      public final List<Object> kept = new CopyOnWriteArrayList<>();
      volatile inetsoft.util.script.ScriptEnv other;
   }

   // ---- (b) leak: nothing Java keeps holds a guest Value after the exec ----

   @Test
   void noGuestValueSurvivesTheExecOnAnyPath() throws Exception {
      WorksheetScriptEnv env = env();
      WorksheetScriptEnv env2 = env();
      H h = new H();
      h.other = env2;
      env.put("h", h);
      env2.put("h", h);
      Callback cb = new Callback(env);
      env.put("cb", cb);
      Object result = run(env,
         "var b = [5, {q: [1]}]; var c = {z: {y: [2]}}; var a = [1, {v: 2}]; h.keep(a); h.keep(c);" +
         "Object.defineProperty(a, 0, {enumerable: true, get: function() {" +
         "  h.keep(b); h.keep(c.z);" +                          // same thread, during detach
         "  cb.nested('h.keep([7, {w: 8}]); 1');" +                      // nested exec same slot during detach
         "  h.nested('var t = [9, {u: 1}]; h.keep(t); t.push(3); 1');" +   // exec of another env during detach
         "  return 1; }});" +
         "h.keep(a[1]); h.keep(c.z.y); h.keepList(b)");
      assertNull(WsExecContext.currentFrame());
      assertNull(WsExecContext.currentContext());
      List<Object> all = new ArrayList<>(h.kept);
      all.add(result);
      assertTrue(all.size() >= 10, String.valueOf(all.size()));

      for(Object o : all) {
         assertNoGuest(o, Collections.newSetFromMap(new IdentityHashMap<>()), "kept " + o);
      }

      String before = all.toString();
      env.retire();
      env2.retire();
      // after the contexts are retired everything is still readable, from this and another thread
      assertEquals(before, all.toString());
      ExecutorService ex = Executors.newSingleThreadExecutor();

      try {
         assertEquals(before, ex.submit(all::toString).get(10, TimeUnit.SECONDS));
      }
      finally {
         ex.shutdownNow();
      }
   }

   static void assertNoGuest(Object o, Set<Object> seen, String where) throws Exception {
      if(o == null || !seen.add(o)) {
         return;
      }

      String cn = o.getClass().getName();
      assertFalse(o instanceof Value, "guest Value in " + where);
      assertFalse(cn.startsWith("com.oracle") || cn.startsWith("org.graalvm"),
                  "graal object " + cn + " in " + where);

      if(o instanceof LiveList || o instanceof LiveMap) {
         Field g = o.getClass().getDeclaredField("guest");
         g.setAccessible(true);
         assertNull(g.get(o), "view still attached in " + where);
         Field c = o.getClass().getDeclaredField("copy");
         c.setAccessible(true);
         assertNoGuest(c.get(o), seen, where + "/copy");
         return;
      }

      if(o instanceof Map<?, ?> m) {
         for(Map.Entry<?, ?> e : m.entrySet()) {
            assertNoGuest(e.getValue(), seen, where + "." + e.getKey());
         }
      }
      else if(o instanceof Collection<?> c) {
         for(Object e : c) {
            assertNoGuest(e, seen, where + "[]");
         }
      }
   }

   // ---- (c) exceptions at exit, nested execs, views across execs ----

   @Test
   void hostExceptionsEndTheExecCleanly() throws Exception {
      WorksheetScriptEnv env = env();
      H h = new H();
      env.put("h", h);

      for(String boom : new String[] { "h.boomRuntime()", "h.boomError()" }) {
         h.kept.clear();
         assertThrows(inetsoft.util.script.ScriptException.class, () -> run(env,
            "var a = [1, 2]; h.keep(a); a.push(3); " + boom));
         assertNull(WsExecContext.currentFrame());
         assertNull(WsExecContext.currentContext());
         assertEquals("[1, 2]", String.valueOf(h.kept.get(0)), "call-time copy on failure");
         assertNoGuest(h.kept.get(0), Collections.newSetFromMap(new IdentityHashMap<>()), boom);
         assertEquals(2.0, run(env, "1 + 1"), "the next exec works");
      }

      // an Error thrown by Java from a getter during the re-snapshot
      h.kept.clear();
      Throwable t = null;
      Object r = null;

      try {
         r = run(env, "var a = [1, 2]; var b = [4]; h.keep(b); h.keep(a); b.push(5); " +
            "Object.defineProperty(a, 0, {enumerable: true, get: function() { h.boomError(); }}); 1");
      }
      catch(Throwable ex) {
         t = ex;
      }

      // a Java Error from a getter is a guest exception there: that view keeps its call-time
      // copy, the others take their final state, the exec still completes
      assertNull(t, String.valueOf(t));
      assertEquals(1.0, r);
      assertEquals("[4, 5]", String.valueOf(h.kept.get(0)));
      assertEquals("[1, 2]", String.valueOf(h.kept.get(1)));
      assertNull(WsExecContext.currentFrame());
      assertNull(WsExecContext.currentContext());

      for(Object o : h.kept) {
         assertNoGuest(o, Collections.newSetFromMap(new IdentityHashMap<>()), "err");
      }

      assertEquals(2.0, run(env, "1 + 1"), "the next exec works");
   }

   @Test
   void anInnerExecOfTheSameSlotUnderAnotherContextWritesTheOuterView() throws Exception {
      WorksheetScriptEnv s = env();
      WorksheetScriptEnv t = env();
      H h = new H();
      Callback toT = new Callback(t);
      Callback toS = new Callback(s);
      s.put("h", h);
      t.put("h", h);
      s.put("toT", toT);
      t.put("toS", toS);
      // outer S passes a; T nested; inner S (new frame, previous = T) sorts the kept view via Java
      Object r = run(s,
         "var a = [3, 1, 2]; h.keepList(a); var o = {n: 1}; h.keep(o);" +
         "var inner = toT.nested(\"toS.nested('var l = h.kept.get(0); " +
         "java.util.Collections.sort(l); h.add4(l); h.putM(h.kept.get(1)); String(l)')\");" +
         "inner + '|' + String(a) + '|' + JSON.stringify(o)");
      assertEquals("1,2,3,4|1,2,3,4|{\"n\":1,\"m\":2}", r);
      assertNull(WsExecContext.currentFrame());
      assertNull(WsExecContext.currentContext());

      for(Object o : h.kept) {
         assertNoGuest(o, Collections.newSetFromMap(new IdentityHashMap<>()), "sts");
      }

      assertEquals("[1, 2, 3, 4]", String.valueOf(h.kept.get(0)));
   }

   @Test
   void aViewKeptByOneExecIsAHostCopyInLaterExecsAndOtherContexts() throws Exception {
      WorksheetScriptEnv env = env();
      WorksheetScriptEnv env2 = env();
      H h = new H();
      env.put("h", h);
      env2.put("h", h);
      int c1 = ((Number) run(env, "var a = [3, 1]; h.keepList(a); a.push(7); h.ctx()"))
         .intValue();
      assertTrue(c1 != 0, "exec 1 ran on a pooled context");
      Object view = h.kept.get(0);
      // exec 2, same thread, same env (slot may be the same): a copy of exec 1's final state
      Object r2 = run(env, "var n = h.ctx(); var s = h.readLast(); h.writeLast(); " +
         "var back = h.kept.get(0); back[0] = 100; n + '#' + s + '#' + back.length");
      assertTrue(String.valueOf(r2).endsWith("#[3, 1, 7]|3#4"), String.valueOf(r2));
      assertEquals("[100, 1, 7, 55]", String.valueOf(view), "a host copy, Java writes stick");
      // the script's own array of exec 1 is not affected (a in the global may be cleaned)
      // exec 2 on another env (other slot) on this thread and on another thread
      assertEquals("[100, 1, 7, 55]|4", run(env2, "h.readLast()"));
      ExecutorService ex = Executors.newSingleThreadExecutor();

      try {
         assertEquals("[100, 1, 7, 55]|4",
                      ex.submit(() -> run(env, "h.readLast()")).get(10, TimeUnit.SECONDS));
      }
      finally {
         ex.shutdownNow();
      }

      // while exec 1 of env still runs, Java hands the view to a nested exec of env2
      h.kept.clear();
      Callback to2 = new Callback(env2);
      env.put("to2", to2);
      Object r3 = run(env, "var a = [3, 1, 2]; h.keepList(a); a.push(4); " +
         "var inner = to2.nested('h.readLast()'); inner + '#' + a.length");
      assertEquals("[3, 1, 2]|3#4", r3, "another context sees the call-time copy, no error");
      assertEquals("[3, 1, 2, 4]", String.valueOf(h.kept.get(0)));
   }

   // ---- (d) G11 hammer ----

   @Test
   void twoReadersNeverSeeATornOrForeignReadAcrossTheCompletionSwap() throws Exception {
      WorksheetScriptEnv env = env();
      Taker taker = new Taker();
      Taker second = new Taker();
      env.put("taker", taker);
      env.put("second", second);
      Callback cb = new Callback(env);
      env.put("cb", cb);
      List<Integer> init = new ArrayList<>();
      List<Integer> fin = new ArrayList<>();
      Map<String, Integer> minit = new HashMap<>();
      Map<String, Integer> mfin = new HashMap<>();

      for(int i = 0; i < 50; i++) {
         init.add(i);
         fin.add(100 + i);
         minit.put("k" + i, i);
         mfin.put("k" + i, 100 + i);
      }

      ExecutorService owner = Executors.newSingleThreadExecutor();
      ExecutorService readers = Executors.newFixedThreadPool(2);
      ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
      AtomicInteger seenInit = new AtomicInteger();
      AtomicInteger seenFinal = new AtomicInteger();
      AtomicInteger mixedPerElement = new AtomicInteger();
      AtomicInteger total = new AtomicInteger();
      CountDownLatch ownerDone = new CountDownLatch(1);

      try {
         Future<Object> busy = owner.submit(() -> {
            try {
               return run(env,
                  "var a = []; var o = {}; for (var i = 0; i < 50; i++) { a.push(i); o['k' + i] = i; }" +
                  "taker.takeList(a); second.take(o); java.util.Collections.sort(a); cb.block();" +
                  "for (var i = 0; i < 50; i++) { a[i] = 100 + i; o['k' + i] = 100 + i; }" +
                  "var s = 0; for (var k = 0; k < 300000; k++) { s += k % 7; } 1");
            }
            finally {
               ownerDone.countDown();
            }
         });
         assertTrue(cb.entered.await(10, TimeUnit.SECONDS));
         List<?> list = (List<?>) taker.last;
         Map<?, ?> map = (Map<?, ?>) second.last;
         List<Future<?>> fs = new ArrayList<>();

         for(int r = 0; r < 2; r++) {
            fs.add(readers.submit(() -> {
               long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);

               // >= 10,000 reads each, and 2,000 more after the owner exec ended (the swap)
               int after = 0;

               for(int it = 0; (it < 10000 || after < 2000) && System.nanoTime() < deadline; it++) {
                  if(ownerDone.getCount() == 0) {
                     after++;
                  }

                  try {
                     List<Object> snap = new ArrayList<>(list);
                     boolean isInit = snap.equals(init);
                     assertTrue(isInit || snap.equals(fin), "torn list copy " + snap);
                     (isInit ? seenInit : seenFinal).incrementAndGet();
                     String s = list.toString();
                     assertTrue(s.equals(init.toString()) || s.equals(fin.toString()), s);
                     int sum = 0;

                     for(Object x : list) {
                        sum += (Integer) x;
                     }

                     assertTrue(sum == 1225 || sum == 1225 + 5000, "iter sum " + sum);
                     int ssum = list.stream().mapToInt(x -> (Integer) x).sum();
                     assertTrue(ssum == 1225 || ssum == 6225, "stream sum " + ssum);
                     Map<Object, Object> msnap = new HashMap<>(map);
                     assertTrue(msnap.equals(minit) || msnap.equals(mfin), "torn map " + msnap);
                     int esum = 0;

                     for(Map.Entry<?, ?> e : map.entrySet()) {
                        esum += (Integer) e.getValue();
                     }

                     assertTrue(esum == 1225 || esum == 6225, "entry sum " + esum);
                     // per-element reads are not a bulk read: they may mix (count, don't fail)
                     int n = list.size();
                     boolean lo = false, hi = false;

                     for(int i = 0; i < n; i++) {
                        int v = (Integer) list.get(i);
                        lo |= v < 100;
                        hi |= v >= 100;
                     }

                     if(lo && hi) {
                        mixedPerElement.incrementAndGet();
                     }

                     total.incrementAndGet();

                     if(it == 200) {
                        cb.release.countDown();
                     }
                  }
                  catch(Throwable ex) {
                     errors.add(ex);

                     if(errors.size() > 20) {
                        return null;
                     }
                  }
               }

               return null;
            }));
         }

         for(Future<?> f : fs) {
            f.get(180, TimeUnit.SECONDS);
         }

         assertEquals(1.0, busy.get(10, TimeUnit.SECONDS));
                  assertTrue(errors.isEmpty(), "errors: " + errors.peek());
         assertTrue(total.get() >= 20000);
         assertTrue(seenInit.get() > 0 && seenFinal.get() > 0, "straddled the swap");
         assertEquals(fin, new ArrayList<>(list));
         assertEquals(mfin, new HashMap<>(map));
      }
      finally {
         cb.release.countDown();
         owner.shutdownNow();
         readers.shutdownNow();
      }
   }
}
