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
import java.util.List;
import java.util.concurrent.*;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Refute amendment 1 of bug #77123: the end-of-exec re-snapshot of the live views runs inside
 * the exec's timeout guard, so a getter the script defined after the Java call that never
 * returns is interrupted like any runaway script, instead of hanging the exec for good.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WsLiveViewTimeoutTest {
   @BeforeEach
   void shortTimeout() throws Exception {
      previous = SreeEnv.getProperty("script.execution.timeout");
      SreeEnv.setProperty("script.execution.timeout", "2");
      refreshTimeout();
   }

   @AfterEach
   void restoreTimeout() throws Exception {
      SreeEnv.setProperty("script.execution.timeout", previous);
      refreshTimeout();
   }

   @Test
   void aLoopingGetterDefinedAfterTheCallIsStoppedByTheTimeout() throws Exception {
      WorksheetScriptEnv env = env();
      Taker taker = new Taker();
      env.put("taker", taker);
      ExecutorService executor = Executors.newSingleThreadExecutor();

      try {
         Future<Object> exec = executor.submit(() -> run(env,
            "var a = [1, 2]; taker.takeList(a); java.util.Collections.sort(a); " +
            "Object.defineProperty(a, 0, {get: function() { while(true) {} }}); 'done'"));
         long start = System.nanoTime();
         ExecutionException failure =
            assertThrows(ExecutionException.class, () -> exec.get(30, TimeUnit.SECONDS),
                         "the exec must fail visibly, not hang or return");
         assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(30));
         assertTrue(String.valueOf(failure.getCause().getMessage()).contains("interrupted"),
                    String.valueOf(failure.getCause()));
         // the kept view keeps its call-time copy and holds no guest value
         assertEquals(List.of(1, 2), List.copyOf((List<?>) taker.last));
         assertFalse(((LiveList) taker.last).attached());
      }
      finally {
         executor.shutdownNow();
      }
   }

   private static void refreshTimeout() throws Exception {
      Field field = GraalJavaScriptEngine.class.getDeclaredField("TIMEOUT_PROP");
      field.setAccessible(true);
      ((SreeEnv.Value) field.get(null)).updateValue();
   }

   private String previous;
}
