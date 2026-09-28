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

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77123: looping getters that only the completion re-snapshot reaches (an object member,
 * an element of a nested kept array, a loop that calls Java) are stopped by the script timeout,
 * the kept view keeps its call-time copy with no guest value, and the slot and engine lock are
 * released, so the next exec runs on the same and on another thread.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WsLiveViewGetterTimeoutTest {
   @BeforeEach
   void shortTimeout() throws Exception {
      previous = SreeEnv.getProperty("script.execution.timeout");
      SreeEnv.setProperty("script.execution.timeout", "2");
      refresh();
   }

   @AfterEach
   void restore() throws Exception {
      SreeEnv.setProperty("script.execution.timeout", previous);
      refresh();
   }

   @Test
   void loopingGettersAreBoundedAndReleaseTheSlot() throws Exception {
      String[] scripts = {
         // object (LiveMap) member getter
         "var o = {a: 1}; taker.take(o); " +
            "Object.defineProperty(o, 'a', {enumerable: true, get: function() { while(true) {} }}); 'done'",
         // nested element of a kept array: only the deep re-snapshot reaches it
         "var a = [[1, 2]]; taker.takeList(a); " +
            "Object.defineProperty(a[0], 1, {get: function() { for(;;) {} }}); 'done'",
         // getter that loops inside a Java callback chain
         "var a = [1]; taker.takeList(a); var n = 0; " +
            "Object.defineProperty(a, 0, {get: function() { while(true) { n++; java.lang.Math.abs(n); } }}); 'x'",
      };
      WorksheetScriptEnv env = env();
      Taker taker = new Taker();
      env.put("taker", taker);
      ExecutorService executor = Executors.newSingleThreadExecutor();

      try {
         for(String js : scripts) {
            long start = System.nanoTime();
            Future<Object> f = executor.submit(() -> run(env, js));
            ExecutionException ex =
               assertThrows(ExecutionException.class, () -> f.get(40, TimeUnit.SECONDS), js);
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertNotNull(ex.getCause());
            assertTrue(ms < 15000, ms + " ms");
            WsLiveViewIsolationTest.assertNoGuest(taker.last,
               Collections.newSetFromMap(new IdentityHashMap<>()), js);
            // the slot/engine lock is released: a following exec works on the same thread and here
            assertEquals(3.0, executor.submit(() -> run(env, "1 + 2")).get(20, TimeUnit.SECONDS));
            assertEquals(3.0, run(env, "1 + 2"));
            assertNull(executor.submit(WsExecContext::currentFrame).get(5, TimeUnit.SECONDS));
            assertNull(executor.submit(WsExecContext::currentContext).get(5, TimeUnit.SECONDS));
         }
      }
      finally {
         executor.shutdownNow();
      }
   }

   private static void refresh() throws Exception {
      Field field = GraalJavaScriptEngine.class.getDeclaredField("TIMEOUT_PROP");
      field.setAccessible(true);
      ((SreeEnv.Value) field.get(null)).updateValue();
   }

   private String previous;
}
