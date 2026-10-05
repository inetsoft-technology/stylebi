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

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77123 (PR #5809 round 2, F1 and I3): a Java argument is copied at the call, inside the
 * exec's timeout guard, and no script code runs after the guarded eval. So a looping getter
 * the script defines after the call never runs (#5802's end-of-exec re-snapshot ran it, and a
 * timeout interrupt that surfaced as a host exception there was swallowed, which let the next
 * kept value's getter run with no timeout at all). A looping getter the call-time copy reaches,
 * and a script loop writing into a copy handed back to it, are stopped by the timeout, and the
 * slot is released either way.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("slow")
class WsArgumentCopyTimeoutTest {
   public static final class Ctr {
      public void hit(double n) {
         last.set((long) n);
      }

      public void keep(List<?> l) {
         k1 = l;
      }

      public void keep2(List<?> l) {
         k2 = l;
      }

      public volatile Object k1;
      public volatile Object k2;
      public final AtomicLong last = new AtomicLong();
   }

   @BeforeEach
   void shortTimeout() throws Exception {
      previous = SreeEnv.getProperty("script.execution.timeout");
      SreeEnv.setProperty("script.execution.timeout", "2");
      refresh();
      env = env();
      taker = new Taker();
      ctr = new Ctr();
      env.put("taker", taker);
      env.put("ctr", ctr);
      executor = Executors.newSingleThreadExecutor(r -> {
         Thread t = new Thread(r);
         t.setDaemon(true);
         return t;
      });
   }

   @AfterEach
   void restore() throws Exception {
      executor.shutdownNow();
      SreeEnv.setProperty("script.execution.timeout", previous);
      refresh();
   }

   /**
    * F1: the tester's scripts, including three kept values whose getter loops through a host
    * callback (java.lang.Math via a ProxyObject, then a host method). On #5802 these hung or
    * returned depending on where the interrupt landed; now the getters never run.
    */
   @Test
   void gettersDefinedAfterTheCallNeverRun() throws Exception {
      String loop = "while(true) { n++; java.lang.Math.abs(n); ctr.hit(n); }";
      String[] scripts = {
         "var o = {a: 1}; taker.take(o); " +
            "Object.defineProperty(o, 'a', {enumerable: true, get: function() { while(true) {} }}); 'x'",
         "var a = [[1, 2]]; taker.takeList(a); " +
            "Object.defineProperty(a[0], 1, {get: function() { for(;;) {} }}); 'x'",
         "var a = [1]; taker.takeList(a); var n = 0; " +
            "Object.defineProperty(a, 0, {get: function() { while(true) { n++; java.lang.Math.abs(n); } }}); 'x'",
         "var a = [1], b = [2], c = [3]; taker.takeList(a); ctr.keep(b); ctr.keep2(c); var n = 0; " +
            "var g = {get: function() { " + loop + " }}; " +
            "Object.defineProperty(a, 0, g); Object.defineProperty(b, 0, g); " +
            "Object.defineProperty(c, 0, g); 'x'",
      };

      for(int rep = 0; rep < 5; rep++) {
         for(String js : scripts) {
            ctr.last.set(0);
            long start = System.nanoTime();
            Object result = executor.submit(() -> run(env, js)).get(30, TimeUnit.SECONDS);
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertEquals("x", result, js);
            // well under the 2 s timeout: nothing ran after the script body
            assertTrue(ms < 1500, ms + " ms: " + js);
            assertEquals(0, ctr.last.get(), "a getter ran: " + js);
            assertTrue(taker.last instanceof CopyList || taker.last instanceof CopyMap,
                       String.valueOf(taker.last));
            assertSlotReleased();
         }
      }

      // the kept values are the call-time copies
      assertEquals(List.of(1), List.copyOf((List<?>) taker.last));
      assertEquals(List.of(2), List.copyOf((List<?>) ctr.k1));
      assertEquals(List.of(3), List.copyOf((List<?>) ctr.k2));
   }

   /**
    * A looping getter that the call-time copy reaches runs inside the guard and is stopped,
    * however the interrupt surfaces (in guest code, or inside a host callback of the getter),
    * also when the script catches the call's error and calls again.
    */
   @Test
   void aLoopingGetterReachedByTheCopyIsStoppedByTheTimeout() throws Exception {
      String loop = "while(true) { n++; java.lang.Math.abs(n); ctr.hit(n); }";
      String[] scripts = {
         "var a = [1]; Object.defineProperty(a, 0, {get: function() { while(true) {} }}); " +
            "taker.takeList(a); 'x'",
         "var n = 0, a = [1]; Object.defineProperty(a, 0, {get: function() { " + loop + " }}); " +
            "taker.takeList(a); 'x'",
         "var n = 0, o = {}; Object.defineProperty(o, 'a', {enumerable: true, get: function() { " +
            loop + " }}); taker.take(o); 'x'",
         "var n = 0, a = [1], b = [2], c = [3], g = {get: function() { " + loop + " }}; " +
            "Object.defineProperty(a, 0, g); Object.defineProperty(b, 0, g); " +
            "Object.defineProperty(c, 0, g); taker.takeList(a); ctr.keep(b); ctr.keep2(c); 'x'",
         "var n = 0, caught = 0, a = [1]; Object.defineProperty(a, 0, {get: function() { " +
            loop + " }}); for(;;) { try { taker.takeList(a); } catch(e) { caught++; } }",
      };

      for(int rep = 0; rep < 2; rep++) {
         for(String js : scripts) {
            assertStoppedByTheTimeout(js);
         }
      }
   }

   /**
    * I3: a script loop that writes new objects into a copy handed back to it spends its time in
    * the copy's conversion (host code reading guest members); the interrupt is never swallowed
    * there, so the exec fails within its timeout.
    */
   @Test
   void aScriptWriteLoopIntoACopyIsStoppedByTheTimeout() throws Exception {
      String[] scripts = {
         "var l = taker.takeList([0]); while(true) { l[0] = {a: 1}; }",
         "var l = taker.takeList([0]); var i = 0; while(true) { l[i++ % 8] = {a: [1, {b: 2}]}; }",
         "var m = taker.take({}); while(true) { m.x = {a: 1, b: [1, 2]}; }",
      };

      for(int rep = 0; rep < 2; rep++) {
         for(String js : scripts) {
            assertStoppedByTheTimeout(js);
         }
      }
   }

   private void assertStoppedByTheTimeout(String js) throws Exception {
      long start = System.nanoTime();
      Future<Object> f = executor.submit(() -> run(env, js));
      ExecutionException ex =
         assertThrows(ExecutionException.class, () -> f.get(30, TimeUnit.SECONDS), js);
      long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      assertNotNull(ex.getCause(), js);
      assertTrue(ms < 15000, ms + " ms: " + js);
      assertSlotReleased();
   }

   // the slot and the engine lock are released: an exec works on the same thread and here
   private void assertSlotReleased() throws Exception {
      assertEquals(3.0, executor.submit(() -> run(env, "1 + 2")).get(20, TimeUnit.SECONDS));
      assertEquals(3.0, run(env, "1 + 2"));
      assertNull(executor.submit(WsExecContext::currentContext).get(5, TimeUnit.SECONDS));
      assertNull(executor.submit(WsExecContext::currentOrigin).get(5, TimeUnit.SECONDS));
   }

   private static void refresh() throws Exception {
      Field field = GraalJavaScriptEngine.class.getDeclaredField("TIMEOUT_PROP");
      field.setAccessible(true);
      ((SreeEnv.Value) field.get(null)).updateValue();
   }

   private String previous;
   private WorksheetScriptEnv env;
   private Taker taker;
   private Ctr ctr;
   private ExecutorService executor;
}
