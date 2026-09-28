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

import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Live Java-argument views read or written from other threads while their exec runs (bug
 * #77123, review r1 I1/I2/M2/M6): Java code that changes a view and then reads it on a
 * parallel stream or an executor it waits on sees its own changes, never a stale or mixed
 * copy; a change made off the script's thread while the exec runs fails explicitly instead
 * of being lost; an entry set kept past the exec or read on another thread never touches
 * the guest value; a kept view serializes as its copy.
 */
@Tag("core")
class WsLiveViewCrossThreadTest {
   /**
    * Host helpers the scripts call.
    */
   public final class Helper {
      public double parallelSum(Object value) {
         return ((List<?>) value).parallelStream()
            .mapToDouble(x -> ((Number) x).doubleValue()).sum();
      }

      @SuppressWarnings("unchecked")
      public String sortThenCollect(Object value) {
         List<Object> list = (List<Object>) value;
         list.sort(Comparator.comparingDouble(x -> ((Number) x).doubleValue()));
         return list.parallelStream().map(x -> String.valueOf(((Number) x).intValue()))
            .collect(Collectors.joining(","));
      }

      @SuppressWarnings("unchecked")
      public String sortThenOnExecutor(Object value) throws Exception {
         List<Object> list = (List<Object>) value;
         Collections.sort(list, Comparator.comparingDouble(x -> ((Number) x).doubleValue()));
         Collections.reverse(list);
         return executor.submit(() -> list.stream()
            .map(x -> String.valueOf(((Number) x).intValue()))
            .collect(Collectors.joining(","))).get(10, TimeUnit.SECONDS);
      }

      @SuppressWarnings("unchecked")
      public String nestedWriteThenOnExecutor(Object value) throws Exception {
         List<Object> list = (List<Object>) value;
         ((Map<String, Object>) list.get(0)).put("v", 99);
         list.add(Map.of("v", 7));
         return executor.submit(() -> list.stream()
            .map(x -> String.valueOf(((Number) ((Map<?, ?>) x).get("v")).intValue()))
            .collect(Collectors.joining(","))).get(10, TimeUnit.SECONDS);
      }

      @SuppressWarnings("unchecked")
      public String writeOnExecutor(Object value) throws Exception {
         List<Object> list = (List<Object>) value;

         try {
            executor.submit(() -> list.set(0, 9)).get(10, TimeUnit.SECONDS);
            return "no error";
         }
         catch(ExecutionException ex) {
            return ex.getCause().getClass().getSimpleName() + ": " + ex.getCause().getMessage();
         }
      }

      @SuppressWarnings("unchecked")
      public String parallelWrite(Object value) {
         List<Object> list = (List<Object>) value;

         try {
            java.util.stream.IntStream.range(0, list.size()).parallel()
               .forEach(i -> list.set(i, i * 10));
            return "no error";
         }
         catch(RuntimeException ex) {
            return ex.getClass().getSimpleName() + ": " + ex.getMessage();
         }
      }

      public Object keep(Object value) {
         kept.add(value);
         return value;
      }

      @SuppressWarnings("unchecked")
      public String readAndWriteKept() {
         List<Object> list = (List<Object>) kept.get(0);
         String read = String.valueOf(list);

         try {
            list.add(2);
            return read + "|no error";
         }
         catch(IllegalStateException ex) {
            return read + "|" + ex.getMessage();
         }
      }

      public void keepEntries(Map<?, ?> map) {
         entries = map.entrySet();
         iterator = map.entrySet().iterator();
      }

      public String readEntriesElsewhere() throws Exception {
         return executor.submit(() -> String.valueOf(new ArrayList<>(entries)))
            .get(10, TimeUnit.SECONDS);
      }

      final List<Object> kept = new ArrayList<>();
      volatile Set<? extends Map.Entry<?, ?>> entries;
      volatile Iterator<? extends Map.Entry<?, ?>> iterator;
   }

   /**
    * I1, first scenario: the script grows the array and passes it again each time; a
    * parallel stream reads it on the owner thread (live) and on pool workers (copy).
    */
   @Test
   void aParallelStreamSeesEachPassOfAGrowingArray() throws Exception {
      WorksheetScriptEnv env = withHelper();
      assertEquals("ok", run(env,
         "var a = []; var bad = []; for (var i = 0; i < 300; i++) { a.push(i); " +
         "var s = h.parallelSum(a); if (s != i * (i + 1) / 2) bad.push(i + ':' + s); } " +
         "bad.length == 0 ? 'ok' : bad.slice(0, 5).join()"));
   }

   /**
    * I1, second scenario: Java sorts the view, then collects it on a parallel stream.
    */
   @Test
   void aJavaSortIsSeenByTheParallelStreamThatFollows() throws Exception {
      WorksheetScriptEnv env = withHelper();
      StringBuilder expected = new StringBuilder();

      for(int i = 0; i < 2000; i++) {
         expected.append(i == 0 ? "" : ",").append(i);
      }

      assertEquals(expected + "|true", run(env,
         "var a = []; for (var i = 1999; i >= 0; i--) a.push(i); " +
         "var r = h.sortThenCollect(a); r + '|' + (a[0] == 0 && a[1999] == 1999)"));
   }

   /**
    * I1 with an executor the Java method waits on, including a write through a nested view
    * and an add.
    */
   @Test
   void anExecutorTaskSeesTheJavaWritesMadeBeforeIt() throws Exception {
      WorksheetScriptEnv env = withHelper();
      assertEquals("5,4,3,2,1|5,4,3,2,1", run(env,
         "var a = [3, 1, 5, 2, 4]; var r = h.sortThenOnExecutor(a); r + '|' + String(a)"));
      assertEquals("99,2,7|99|3", run(env,
         "var a = [{v: 1}, {v: 2}]; var r = h.nestedWriteThenOnExecutor(a); " +
         "r + '|' + a[0].v + '|' + a.length"));
   }

   /**
    * A write off the script's thread while the exec runs would be lost: it fails explicitly,
    * as with the pool off, and the script's value is unchanged.
    */
   @Test
   void aWriteFromAnotherThreadDuringTheRunFailsExplicitly() throws Exception {
      WorksheetScriptEnv env = withHelper();
      Object r = run(env, "var a = [1, 2]; var r = h.writeOnExecutor(a); r + '|' + String(a)");
      assertTrue(String.valueOf(r).startsWith("IllegalStateException: Multi threaded access"),
                 String.valueOf(r));
      assertTrue(String.valueOf(r).endsWith("|1,2"), String.valueOf(r));

      // a parallel forEach that writes: the owner thread's writes go live, a worker's fail
      Object p = run(env, "var a = []; for (var i = 0; i < 2000; i++) a.push(i); " +
         "h.parallelWrite(a)");
      assertTrue(String.valueOf(p).startsWith("no error") ||
                 String.valueOf(p).contains("Multi threaded access"), String.valueOf(p));

      // once the run ended the kept value is a plain host copy: writes work
      run(env, "var b = [1]; h.keep(b); 1");
      @SuppressWarnings("unchecked") List<Object> kept = (List<Object>) helper.kept.get(0);
      executor.submit(() -> kept.add(5)).get(10, TimeUnit.SECONDS);
      assertEquals("[1, 5]", String.valueOf(kept));
   }

   /**
    * Review M6: on the owner thread inside a script of another (non-pooled) engine the view
    * is in copy mode: a read gets the copy, a write fails explicitly instead of being lost.
    */
   @Test
   void aWriteInsideANestedScriptOfAnotherEngineFailsExplicitly() throws Exception {
      WorksheetScriptEnv env = withHelper();
      GraalJavaScriptEnv plain = new GraalJavaScriptEnv();
      plain.init();
      plain.put("h", helper);
      env.put("plain", new Callback(plain));
      Object r = run(env, "var a = [1]; h.keep(a); var n = plain.nested('h.readAndWriteKept()'); " +
         "n + '|' + String(a)");
      assertTrue(String.valueOf(r).startsWith("[1]|Multi threaded access"), String.valueOf(r));
      assertTrue(String.valueOf(r).endsWith("|1"), String.valueOf(r));
      assertEquals("[1]", String.valueOf(helper.kept.get(0)));
   }

   /**
    * I2: an entry set (and an iterator) taken while the view is live, read on another thread
    * during the run and on any thread after it: no NullPointerException and no guest access.
    */
   @Test
   void aKeptEntrySetIsReadableElsewhereAndAfterTheRun() throws Exception {
      WorksheetScriptEnv env = withHelper();
      Object during = run(env, "var o = {a: 1}; h.keepEntries(o); o.b = 2; " +
         "var r = h.readEntriesElsewhere(); o.c = 3; r");
      assertEquals("[a=1]", during, "another thread reads the copy as last passed");
      assertEquals("[a=1, b=2, c=3]", String.valueOf(new ArrayList<>(helper.entries)),
                   "after the run: the final state");
      assertEquals("[a=1, b=2, c=3]", executor.submit(
         () -> String.valueOf(new ArrayList<>(helper.entries))).get(10, TimeUnit.SECONDS));
      List<String> fromIterator = new ArrayList<>();
      helper.iterator.forEachRemaining(e -> fromIterator.add(e.getKey() + "=" + e.getValue()));
      assertEquals(List.of("a=1"), fromIterator, "the live iterator's keys, the copy's values");
   }

   /**
    * Review M2: a kept view serializes as its copy (CopyList/CopyMap were Serializable).
    */
   @Test
   void aKeptViewSerializesAsItsCopy() throws Exception {
      WorksheetScriptEnv env = withHelper();
      run(env, "var a = [1, {x: [2]}]; h.keep(a); h.keep({k: 'v'}); a.push(3); 1");

      for(Object view : helper.kept) {
         ByteArrayOutputStream bytes = new ByteArrayOutputStream();

         try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(view);
         }

         try(ObjectInputStream in =
                new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
         {
            Object back = in.readObject();
            assertTrue(back instanceof CopyList || back instanceof CopyMap, back.getClass() + "");
            assertEquals(view, back);
         }
      }

      assertEquals("[1, {x=[2]}, 3]", String.valueOf(helper.kept.get(0)));
   }

   @AfterEach
   void shutdown() {
      executor.shutdownNow();
   }

   private WorksheetScriptEnv withHelper() {
      WorksheetScriptEnv env = env();
      env.put("h", helper);
      return env;
   }

   private final ExecutorService executor = Executors.newFixedThreadPool(2);
   private final Helper helper = new Helper();
}
