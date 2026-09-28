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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77123 (review I1/I2, independent tester coverage): live views of a pooled script's
 * values used by Java on other threads. Maps and nested values follow Java's own writes;
 * object identity survives a Java sort read by a parallel stream; every Java write API used
 * off the script's thread during the run fails explicitly (never silently lost); a copy that
 * fell behind fails explicitly; map entry/key/value views and a lazy stream taken live are
 * readable on another thread during and after the run.
 */
@Tag("core")
class WsLiveViewOffThreadApiTest {
   public final class Helper {
      @SuppressWarnings("unchecked")
      public String mapWritesThenWorker(Map<String, Object> m) throws Exception {
         List<Object> xs = (List<Object>) m.get("xs");
         Collections.sort(xs, Comparator.comparingDouble(x -> ((Number) x).doubleValue()));
         xs.add(100);
         m.put("n", 42);
         m.remove("gone");
         ((Map<String, Object>) m.get("inner")).put("deep", "D");
         return executor.submit(() -> String.valueOf(new TreeMap<>(m))).get(10, TimeUnit.SECONDS) +
            "#" + executor.submit(() -> xs.parallelStream().map(String::valueOf)
               .collect(Collectors.joining(","))).get(10, TimeUnit.SECONDS);
      }

      @SuppressWarnings("unchecked")
      public String sortObjectsThenParallel(List<Object> l) {
         l.sort(Comparator.comparingDouble(
            o -> ((Number) ((Map<String, Object>) o).get("k")).doubleValue()));
         return l.parallelStream().map(o -> String.valueOf(((Map<String, Object>) o).get("k")))
            .collect(Collectors.joining(","));
      }

      public void keep(List<Object> l) {
         kept = l;
      }

      public String addToKeptThenWorker(Object x) throws Exception {
         kept.add(x);
         return executor.submit(() -> {
            try {
               return String.valueOf(kept);
            }
            catch(IllegalStateException ex) {
               return "ISE " + ex.getMessage();
            }
         }).get(10, TimeUnit.SECONDS);
      }

      public String writesElsewhere(List<Object> l, Map<String, Object> m) throws Exception {
         List<Supplier<Object>> ops = List.of(
            () -> l.set(0, 9), () -> l.add(9), () -> { l.add(0, 9); return 0; },
            () -> l.remove(0), () -> { l.clear(); return 0; }, () -> l.addAll(List.of(1, 2)),
            () -> { l.sort(null); return 0; }, () -> { Collections.reverse(l); return 0; },
            () -> { l.replaceAll(x -> x); return 0; }, () -> l.removeIf(x -> true),
            () -> { Iterator<Object> it = l.iterator(); it.next(); it.remove(); return 0; },
            () -> { ListIterator<Object> it = l.listIterator(); it.next(); it.set(7); return 0; },
            () -> { l.subList(0, 1).clear(); return 0; },
            () -> m.put("z", 1), () -> m.remove("a"), () -> { m.clear(); return 0; },
            () -> { m.putAll(Map.of("q", 1)); return 0; }, () -> m.computeIfAbsent("w", k -> 1),
            () -> m.merge("a", 1, (a, b) -> b), () -> m.putIfAbsent("p", 1),
            () -> { m.entrySet().iterator().next().setValue(3); return 0; },
            () -> m.keySet().remove("a"), () -> m.values().remove(1),
            () -> { m.entrySet().removeIf(e -> true); return 0; },
            () -> { m.replaceAll((k, v) -> v); return 0; });
         StringBuilder sb = new StringBuilder();

         for(int i = 0; i < ops.size(); i++) {
            Supplier<Object> op = ops.get(i);
            String r = executor.submit(() -> {
               try {
                  op.get();
                  return "SILENT";
               }
               catch(IllegalStateException ex) {
                  return ex.getMessage().startsWith("Multi threaded access") ? "busy" : "ISE";
               }
               catch(UnsupportedOperationException ex) {
                  return "readonly";
               }
            }).get(10, TimeUnit.SECONDS);
            sb.append(i).append('=').append(r).append(' ');
         }

         return sb.toString().trim();
      }

      public String keepMapViews(Map<String, Object> m) throws Exception {
         entries = m.entrySet();
         stream = m.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue());
         keys = m.keySet();
         values = m.values();
         return executor.submit(() -> describe(false)).get(10, TimeUnit.SECONDS);
      }

      String describe(boolean withStream) {
         return entries.size() + ":" + new ArrayList<>(entries) + ":" + new ArrayList<>(keys) +
            ":" + new ArrayList<>(values) + ":" +
            entries.stream().map(Map.Entry::getKey).collect(Collectors.joining(",")) +
            (withStream ? ":" + stream.collect(Collectors.joining(",")) : "");
      }

      volatile List<Object> kept;
      volatile Set<Map.Entry<String, Object>> entries;
      volatile Stream<String> stream;
      volatile Set<String> keys;
      volatile Collection<Object> values;
   }

   /**
    * I1 for a map: Java sorts a nested array, adds to it, puts, removes and writes a nested
    * object; a worker thread then reads the map and a parallel stream reads the nested array:
    * both see every Java write, and so does the script.
    */
   @Test
   void workersSeeJavaWritesThroughAMapAndItsNestedValues() throws Exception {
      assertEquals("{inner={a=1, deep=D}, n=42, xs=[1, 2, 3, 100]}#1,2,3,100|" +
                      "{\"xs\":[1,2,3,100],\"inner\":{\"a\":1,\"deep\":\"D\"},\"n\":42}",
                   run(env, "var o = {xs: [3, 1, 2], gone: 1, inner: {a: 1}}; " +
                      "var r = h.mapWritesThenWorker(o); r + '|' + JSON.stringify(o)"));
   }

   /**
    * I1 with objects: a Java sort moves the script's objects (identity kept) and the parallel
    * stream that follows, partly on pool workers, reads them in the sorted order.
    */
   @Test
   void aParallelStreamReadsObjectsInTheOrderAJavaSortLeft() throws Exception {
      assertEquals("true|true", run(env,
         "var a = []; for (var i = 999; i >= 0; i--) a.push({k: i}); " +
         "var s = h.sortObjectsThenParallel(a); var ok = true; " +
         "for (var i = 0; i < 1000; i++) ok = ok && a[i].k == i; " +
         "(s == a.map(function(x) { return x.k; }).join()) + '|' + ok"));
   }

   /**
    * The script grew a kept array after passing it, then Java adds through the kept view: the
    * copy cannot follow, so a worker's read fails explicitly instead of returning stale data;
    * the owner's write reached the script.
    */
   @Test
   void aCopyThatFellBehindFailsExplicitlyOnAWorker() throws Exception {
      Object r = run(env, "var a = [{v: 0}, 2]; h.keep(a); a.push(3); " +
         "h.addToKeptThenWorker(4) + '|' + a.length");
      assertTrue(String.valueOf(r).startsWith("ISE Multi threaded access"), String.valueOf(r));
      assertTrue(String.valueOf(r).endsWith("|4"), String.valueOf(r));
   }

   /**
    * Every List/Map write API (direct, iterator, sub-list, entry, key/value view, default
    * methods) used on another thread during the run fails explicitly, and the script's values
    * are unchanged.
    */
   @Test
   void everyWriteApiOffTheScriptThreadFailsExplicitly() throws Exception {
      Object r = run(env, "var a = [1, 2, 3]; var o = {a: 1, b: 2}; " +
         "var r = h.writesElsewhere(a, o); r + '|' + String(a) + '|' + JSON.stringify(o)");
      String s = String.valueOf(r);
      assertFalse(s.contains("SILENT") || s.contains("ISE"), s);
      assertEquals(25, s.substring(0, s.indexOf('|')).split(" ").length, s);
      assertTrue(s.endsWith("|1,2,3|{\"a\":1,\"b\":2}"), s);
   }

   /**
    * I2: entrySet/keySet/values and a lazy entry stream taken while the map is live are read
    * on a worker during the run (the copy as last passed) and on a worker after it (the final
    * state), without touching the guest value.
    */
   @Test
   void mapViewsTakenLiveAreReadableOnWorkersDuringAndAfterTheRun() throws Exception {
      assertEquals("2:[a=1, b=2]:[a, b]:[1, 2]:a,b",
                   run(env, "var o = {a: 1, b: 2}; var r = h.keepMapViews(o); o.c = 3; r"));
      assertEquals("3:[a=1, b=2, c=3]:[a, b, c]:[1, 2, 3]:a,b,c:a=1,b=2,c=3",
                   executor.submit(() -> helper.describe(true)).get(10, TimeUnit.SECONDS));
   }

   @AfterEach
   void shutdown() {
      executor.shutdownNow();
   }

   private final ExecutorService executor = Executors.newFixedThreadPool(2);
   private final Helper helper = new Helper();
   private final WorksheetScriptEnv env = env();

   {
      env.put("h", helper);
   }
}
