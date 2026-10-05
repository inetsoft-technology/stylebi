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

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Testing #77123: when a pooled exec's timeout interrupt times out (the exec sits in a host
 * call that ignores interrupts for longer than ctx.interrupt's 2 s bound), Graal leaves the
 * thread's interrupt flag set. The next script on that thread then failed once with "Failed
 * to create a worksheet script context: Thread was interrupted." while the doomed context was
 * replaced, and any wait of the thread (a lens read, a lock, JDBC) could fail the same way.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("slow")
class WsTimeoutInterruptLeakTest {
   public static final class Spinner {
      /** A host call that ignores interrupts, so the timeout's interrupt cannot stop it. */
      public int spin(int ms) {
         long end = System.nanoTime() + ms * 1_000_000L;

         while(System.nanoTime() - end < 0) {
            Thread.onSpinWait();
         }

         return 1;
      }

      /** Interrupt the calling thread, as a cancel from elsewhere would. */
      public void cancel() {
         Thread.currentThread().interrupt();
      }
   }

   @BeforeEach
   void oneSecondTimeout() throws Exception {
      Thread.interrupted();
      previous = SreeEnv.getProperty("script.execution.timeout");
      SreeEnv.setProperty("script.execution.timeout", "1");
      refresh();
      env = env();
      env.put("sp", new Spinner());
   }

   @AfterEach
   void restore() throws Exception {
      Thread.interrupted();
      SreeEnv.setProperty("script.execution.timeout", previous);
      refresh();
   }

   @Test
   void theNextScriptOnTheThreadRunsAfterAnInterruptTimeout() throws Exception {
      // interrupt at ~1 s; ctx.interrupt(2 s) gives up at ~3 s; the host call returns at 6 s,
      // which leaves room for a late watchdog on a loaded box
      try {
         run(env, "sp.spin(6000); 5");
      }
      catch(Exception ignore) {
         // not what is tested
      }

      assertEquals(1, env.getMetrics().getDoomedCloses(), "the interrupt must have timed out: " + env.getMetrics());
      assertFalse(Thread.currentThread().isInterrupted(),
                  "the timed-out interrupt left the thread interrupted");
      assertEquals(2, ((Number) run(env, "1+1")).intValue());
      assertEquals(199990000.0, ((Number) run(
         env, "var s = 0; for(var k = 0; k < 20000; k++) s += k; s")).doubleValue());
   }

   @Test
   void aCancelBeforeTheInterruptTimeoutIsKept() throws Exception {
      try {
         run(env, "sp.cancel(); sp.spin(6000); 5");
      }
      catch(Exception ignore) {
         // not what is tested
      }

      assertEquals(1, env.getMetrics().getDoomedCloses(), "the interrupt must have timed out: " + env.getMetrics());
      assertTrue(Thread.currentThread().isInterrupted(), "the cancel's interrupt was lost");
   }

   private static void refresh() throws Exception {
      Field field = GraalJavaScriptEngine.class.getDeclaredField("TIMEOUT_PROP");
      field.setAccessible(true);
      ((SreeEnv.Value) field.get(null)).updateValue();
   }

   private String previous;
   private WorksheetScriptEnv env;
}
