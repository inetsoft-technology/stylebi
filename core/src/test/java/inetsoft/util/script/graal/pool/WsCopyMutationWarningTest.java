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
