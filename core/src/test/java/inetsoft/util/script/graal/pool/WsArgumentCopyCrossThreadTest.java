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

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.stream.Collectors;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77123 (PR #5809 round 2, F2 and F3): a Java argument is one host copy, made at the
 * call. Java's writes to it are never lost or replaced, and every thread, the owner included,
 * reads the same copy, nested values too, whatever the script does to its own value
 * afterwards. The script does not see Java's writes (C1, a documented limitation).
 */
@Tag("core")
class WsArgumentCopyCrossThreadTest {
   public final class H {
      public Object keep(Object v) {
         kept.add(v);
         return v;
      }

      @SuppressWarnings("unchecked")
      public void keepNested(List<Object> l) {
         keptList = l;
         keptNested = (Map<String, Object>) l.get(0);
      }

      public int touch(List<Object> l) {
         return l.size();
      }

      public String writeNestedThenWorker() throws Exception {
         keptNested.put("v", 5);
         return ex.submit(() -> String.valueOf(keptList)).get(10, TimeUnit.SECONDS) +
            "|owner:" + keptList;
      }

      @SuppressWarnings("unchecked")
      public String writeFirstThenWorker() throws Exception {
         ((Map<String, Object>) keptList.get(0)).put("v", 99);
         return ex.submit(() -> String.valueOf(keptList)).get(10, TimeUnit.SECONDS) +
            "|owner:" + keptList;
      }

      @SuppressWarnings("unchecked")
      public String sortThenParallel(List<Object> l) throws Exception {
         l.sort(Comparator.comparingDouble(
            o -> ((Number) ((Map<String, Object>) o).get("k")).doubleValue()));
         return ex.submit(() -> l.parallelStream()
            .map(o -> String.valueOf(((Map<String, Object>) o).get("k")))
            .collect(Collectors.joining(","))).get(10, TimeUnit.SECONDS);
      }

      public String writeElsewhere(List<Object> l, Map<String, Object> m) throws Exception {
         return ex.submit(() -> {
            l.add(9);
            Collections.reverse(l);
            m.put("z", 1);
            m.remove("a");
            return l + "|" + new TreeMap<>(m);
         }).get(10, TimeUnit.SECONDS);
      }

      final List<Object> kept = Collections.synchronizedList(new ArrayList<>());
      volatile List<Object> keptList;
      volatile Map<String, Object> keptNested;
   }

   /**
    * F2: a worker writing to kept copies while the owner's exec runs and ends. On #5802/r1
    * the end-of-exec re-snapshot replaced the copies, silently losing millions of accepted
    * writes; now no write is ever lost.
    */
   @Test
   void writesMadeWhileTheExecRunsAndEndsAreNeverLost() throws Exception {
      int views = 300;
      int cap = 500;
      AtomicBoolean go = new AtomicBoolean(true);
      ConcurrentLinkedQueue<long[]> accepted = new ConcurrentLinkedQueue<>();
      AtomicIntegerArray perList = new AtomicIntegerArray(views);
      CountDownLatch started = new CountDownLatch(1);
      Future<?> writer = ex.submit(() -> {
         started.countDown();
         long seq = 0;

         while(go.get()) {
            List<Object> snapshot;

            synchronized(h.kept) {
               snapshot = new ArrayList<>(h.kept);
            }

            for(int i = 0; i < snapshot.size(); i++) {
               if(perList.get(i) >= cap) {
                  continue;
               }

               @SuppressWarnings("unchecked") List<Object> l = (List<Object>) snapshot.get(i);
               long val = -(++seq);
               l.add(val);
               accepted.add(new long[] { i, val });
               perList.incrementAndGet(i);
            }

            Thread.onSpinWait();
         }
      });
      started.await();
      run(env, "for (var i = 0; i < " + views + "; i++) { var a = []; " +
         "for (var j = 0; j < 2000; j++) a.push(j); h.keep(a); } 1");
      // let the writer fill every list, also the one made last, then stop it
      while(perList.get(views - 1) < cap && !writer.isDone()) {
         Thread.onSpinWait();
      }

      go.set(false);
      writer.get(30, TimeUnit.SECONDS);
      int lost = 0;

      for(long[] a : accepted) {
         if(!((List<?>) h.kept.get((int) a[0])).contains(a[1])) {
            lost++;
         }
      }

      assertEquals(views, h.kept.size());
      assertEquals(0, lost, "accepted writes lost, of " + accepted.size());

      for(int i = 0; i < views; i++) {
         List<?> l = (List<?>) h.kept.get(i);
         assertEquals(cap, l.stream().filter(x -> x instanceof Long).count(), "list " + i);
         assertEquals(2000, l.size() - cap, "list " + i);
      }
   }

   /**
    * F3: Java keeps an element of a list across calls, the script passes the list again, and
    * Java writes the element: every thread sees the write (on #5802/r1 the worker read the
    * old value while the owner saw the new one).
    */
   @Test
   void aKeptNestedValueStaysOneWithItsListAcrossARepass() throws Exception {
      Object r = run(env, "var a = [{v: 1}, {v: 2}]; h.keepNested(a); h.touch(a); " +
         "h.writeNestedThenWorker() + '|script:' + a[0].v");
      assertEquals("[{v=5}, {v=2}]|owner:[{v=5}, {v=2}]|script:1", r);
   }

   /**
    * Review M1 of r1: after the script shifts its array, a Java write to the kept list's
    * first element changes that element, on every thread (r1 changed the wrong element's copy
    * for other threads).
    */
   @Test
   void aScriptShiftAfterTheCallNeverMovesAJavaWriteToAnotherElement() throws Exception {
      Object r = run(env, "var a = [{n: 'A', v: 1}, {n: 'B', v: 2}]; h.keepNested(a); " +
         "a.unshift({n: 'X', v: 0}); h.writeFirstThenWorker() + '|script:' + JSON.stringify(a)");
      assertEquals("[{n=A, v=99}, {n=B, v=2}]|owner:[{n=A, v=99}, {n=B, v=2}]|script:" +
         "[{\"n\":\"X\",\"v\":0},{\"n\":\"A\",\"v\":1},{\"n\":\"B\",\"v\":2}]", r);
   }

   /**
    * A Java sort of objects is seen by the parallel stream that follows, on other threads.
    */
   @Test
   void aJavaSortIsSeenByAParallelStreamOnAnotherThread() throws Exception {
      Object r = run(env, "var a = []; for (var i = 999; i >= 0; i--) a.push({k: i}); " +
         "var s = h.sortThenParallel(a); s == Array.from({length: 1000}, function(x, i) { " +
         "return i; }).join() ? 'sorted|' + a[0].k : 'unsorted: ' + s");
      // sorted for Java; the script's array keeps its order (C1)
      assertEquals("sorted|999", r);
   }

   /**
    * Another thread may change the copy during the run, as with any host collection; the
    * script's value is unchanged.
    */
   @Test
   void writesOnAnotherThreadDuringTheRunChangeOnlyTheCopy() throws Exception {
      Object r = run(env, "var a = [1, 2, 3]; var o = {a: 1, b: 2}; var r = h.writeElsewhere(a, o); " +
         "r + '|' + String(a) + '|' + JSON.stringify(o)");
      assertEquals("[9, 3, 2, 1]|{b=2, z=1}|1,2,3|{\"a\":1,\"b\":2}", r);
   }

   @AfterEach
   void down() {
      ex.shutdownNow();
   }

   private final ExecutorService ex = Executors.newFixedThreadPool(4);
   private final H h = new H();
   private final WorksheetScriptEnv env = env();

   {
      env.put("h", h);
   }
}
