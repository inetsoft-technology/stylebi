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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77123 (C1, a documented limitation): a Java argument is a host copy, so a Java change
 * to it is not seen by the script. A change made on the script's thread while the script run
 * that passed it executes is counted (PoolMetrics.nodeCopyMutations, once per copy) and
 * warned about once per script; the copy's construction, the script's own writes through a
 * copy handed back to it, and changes made elsewhere or after the run are not.
 */
@Tag("core")
class WsCopyMutationWarningTest {
   public static final class H {
      @SuppressWarnings("unchecked")
      public void sortFirst(List<Object> l) {
         ((List<Object>) l.get(0)).sort(null);
      }

      // reads only: none of these is a write to the copy
      @SuppressWarnings("unchecked")
      public String readAll(List<Object> l, Map<String, Object> m) {
         StringBuilder sb = new StringBuilder();
         sb.append(l.size()).append(l.get(0)).append(l.contains(2)).append(l.indexOf(2))
            .append(l.isEmpty()).append(l.subList(0, 1)).append(new ArrayList<>(l))
            .append(List.copyOf(l)).append(l.stream().mapToDouble(x -> ((Number) x).doubleValue()).sum())
            .append(Collections.max(l, Comparator.comparingDouble(x -> ((Number) x).doubleValue())))
            .append(l.hashCode()).append(l.equals(List.of(3, 1, 2))).append(l);

         for(Iterator<Object> it = l.listIterator(); it.hasNext(); ) {
            sb.append(it.next());
         }

         l.forEach(sb::append);
         sb.append(m.get("a")).append(m.containsKey("b")).append(m.getOrDefault("q", 0))
            .append(m.keySet()).append(m.values()).append(new TreeMap<>(m)).append(m.size());
         m.forEach((k, v) -> sb.append(k));

         for(Map.Entry<String, Object> e : m.entrySet()) {
            sb.append(e.getKey()).append(e.getValue());
         }

         return sb.toString();
      }

      public String addElsewhere(List<Object> l) throws Exception {
         ExecutorService ex = Executors.newSingleThreadExecutor();

         try {
            return String.valueOf(ex.submit(() -> l.add(7)).get(10, TimeUnit.SECONDS));
         }
         finally {
            ex.shutdownNow();
         }
      }
   }

   @BeforeEach
   void setUp() {
      logger = (Logger) LoggerFactory.getLogger(WsExecContext.class);
      oldLevel = logger.getLevel();
      logger.setLevel(Level.WARN);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
      env = env();
      taker = new Taker();
      env.put("taker", taker);
      env.put("h", new H());
      tag = "/* " + UUID.randomUUID() + " */ ";
   }

   @AfterEach
   void tearDown() {
      logger.detachAppender(appender);
      logger.setLevel(oldLevel);
   }

   @Test
   void aJavaSortOfAScriptArrayIsCountedAndWarnedOncePerScript() throws Exception {
      String js = tag + "var a = [3, 1, 2]; java.util.Collections.sort(a); String(a)";
      long before = PoolMetrics.nodeCopyMutations();
      // the limitation itself: the script's array keeps its order
      assertEquals("3,1,2", run(env, js));
      assertEquals(before + 1, PoolMetrics.nodeCopyMutations());
      assertEquals(1, warnings(), () -> String.valueOf(messages()));
      String message = appender.list.get(0).getFormattedMessage();
      assertTrue(message.startsWith(WsExecContext.COPY_MUTATION_MESSAGE), message);
      assertTrue(message.contains("Collections.sort(a)"), message);

      // the same script again: counted, not warned again
      assertEquals("3,1,2", run(env, js));
      assertEquals(before + 2, PoolMetrics.nodeCopyMutations());
      assertEquals(1, warnings(), () -> String.valueOf(messages()));

      // another script warns once
      run(env, tag + "var l = [1]; taker.mutateList(l); var m = {}; taker.mutateMap(m); " +
         "h.sortFirst([[2, 1]]); l.length + ',' + typeof m.z");
      assertEquals(before + 5, PoolMetrics.nodeCopyMutations(), "one per copy changed");
      assertEquals(2, warnings(), () -> String.valueOf(messages()));
      assertTrue(PoolMetrics.nodeSummary().contains("copyMutations="), PoolMetrics.nodeSummary());
   }

   @Test
   void onlyJavaChangesOnTheScriptThreadDuringTheRunAreCounted() throws Exception {
      long before = PoolMetrics.nodeCopyMutations();
      // passing and reading, the script writing into a copy handed back to it, and a change
      // on another thread during the run
      Object r = run(env, tag + "var a = [[1, 2], {b: 1}]; taker.takeList(a); " +
         "var l = taker.takeList([1]); l[0] = 2; l[1] = {c: 3}; " +
         "var m = taker.take({a: 1}); m.x = [1]; delete m.a; " +
         "h.addElsewhere([1, 2]) + ',' + l.length");
      assertEquals("true,2", r);
      assertEquals(before, PoolMetrics.nodeCopyMutations());

      // a change after the run, by the Java object that kept the copy
      @SuppressWarnings("unchecked") Map<String, Object> kept = (Map<String, Object>) taker.last;
      kept.put("late", 1);
      run(env, tag + "1");
      kept.put("later", 2);
      assertEquals(before, PoolMetrics.nodeCopyMutations());
      assertEquals(0, warnings(), () -> String.valueOf(messages()));
   }

   /**
    * The warning is once per script, not per exec or per call: a script run once per row (one
    * compiled script, many execs) and a script that changes many copies in one exec each
    * warn once, while every changed copy is counted.
    */
   @Test
   void aScriptRunPerRowOrChangingManyCopiesWarnsOnceAndCountsEachCopy() throws Exception {
      long before = PoolMetrics.nodeCopyMutations();
      Object perRow = env.compile(tag + "var a = [3, 1, 2]; java.util.Collections.sort(a); a[0]");

      for(int row = 0; row < 500; row++) {
         assertEquals(3.0, env.exec(perRow, null, null, null));
      }

      assertEquals(before + 500, PoolMetrics.nodeCopyMutations());
      assertEquals(1, warnings(), () -> String.valueOf(messages()));

      assertEquals(3.0, run(env, tag + "var r = 0; for(var i = 0; i < 100; i++) { " +
         "var a = [3, 1, 2]; java.util.Collections.reverse(a); r = a[0]; } r"));
      assertEquals(before + 600, PoolMetrics.nodeCopyMutations());
      assertEquals(2, warnings(), () -> String.valueOf(messages()));
   }

   @Test
   void javaReadsOfACopyAreNeverCounted() throws Exception {
      long before = PoolMetrics.nodeCopyMutations();
      Object r = run(env, tag + "h.readAll([3, 1, 2], {a: 1, b: [1, 2], c: {d: 1}})");
      assertTrue(String.valueOf(r).startsWith("33true2false[3][3, 1, 2]"), String.valueOf(r));
      assertEquals(before, PoolMetrics.nodeCopyMutations());
      assertEquals(0, warnings(), () -> String.valueOf(messages()));
   }

   /**
    * Pool off is main's path: Java gets Graal's own live value, so the sort reaches the
    * script, and nothing is counted or warned.
    */
   @Test
   void poolOffJavaChangesReachTheScriptAndAreNotCounted() throws Exception {
      GraalJavaScriptEnv plain = new GraalJavaScriptEnv();
      plain.init();
      Taker plainTaker = new Taker();
      plain.put("taker", plainTaker);
      long before = PoolMetrics.nodeCopyMutations();
      assertEquals("1,2,3", run(plain, tag + "var a = [3, 1, 2]; taker.takeList(a); " +
         "java.util.Collections.sort(a); String(a)"));
      assertFalse(plainTaker.last instanceof CopyList, String.valueOf(plainTaker.last));
      assertFalse(plainTaker.last.getClass().getName().startsWith("inetsoft."));
      assertEquals(before, PoolMetrics.nodeCopyMutations());
      assertEquals(0, warnings(), () -> String.valueOf(messages()));
   }

   private long warnings() {
      return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).count();
   }

   private List<String> messages() {
      List<String> list = new ArrayList<>();

      for(ILoggingEvent e : appender.list) {
         list.add(e.getLevel() + " " + e.getFormattedMessage());
      }

      return list;
   }

   private Logger logger;
   private Level oldLevel;
   private ListAppender<ILoggingEvent> appender;
   private WorksheetScriptEnv env;
   private Taker taker;
   private String tag;
}
