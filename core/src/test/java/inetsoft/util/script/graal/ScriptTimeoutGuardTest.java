/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.util.script.graal;

import org.graalvm.polyglot.*;
import org.junit.jupiter.api.*;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class ScriptTimeoutGuardTest {
   @Test void interruptsRunawayScript() {
      try(Context ctx = Context.newBuilder("js").build()) {
         ScriptTimeoutGuard guard = new ScriptTimeoutGuard();
         PolyglotException ex = assertThrows(PolyglotException.class, () -> {
            try(var ignored = guard.guard(ctx, Duration.ofMillis(300))) {
               ctx.eval("js", "while(true){}");
            }
         });
         assertTrue(ex.isInterrupted() || ex.isCancelled());
      }
   }

   @Test void zeroDurationMeansNoTimeout() {
      try(Context ctx = Context.newBuilder("js").build()) {
         ScriptTimeoutGuard guard = new ScriptTimeoutGuard();
         try(var ignored = guard.guard(ctx, Duration.ZERO)) {
            assertEquals(3, ctx.eval("js", "1+2").asInt());
         }
      }
   }

   /**
    * Spec §6.3 / G5a (bug #76960): a timeout that fired while exec A was still running, but
    * whose interrupt lands only after A finished, must not interrupt the next exec B on the
    * same Context.
    */
   @Test void lateInterruptDoesNotHitTheNextExec() {
      try(Context ctx = Context.newBuilder("js").build()) {
         ScriptTimeoutGuard guard = new ScriptTimeoutGuard();
         CountDownLatch bStarted = new CountDownLatch(1);
         // hold the claimed interrupt until B has started (or 1 s passed)
         ScriptTimeoutGuard.beforeInterruptHook = () -> {
            try {
               bStarted.await(1, TimeUnit.SECONDS);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         };

         try {
            try(var a = guard.guard(ctx, Duration.ofMillis(50))) {
               // A runs past its 50 ms timeout; the interrupt is claimed but held by the hook
               ctx.eval("js", "var t = Date.now(); while(Date.now() - t < 200) {} 1");
            }

            bStarted.countDown();
            // B: no guard of its own; it must not be hit by A's late interrupt
            assertEquals(42, ctx.eval("js",
               "var t2 = Date.now(); while(Date.now() - t2 < 1500) {} 42").asInt());
         }
         finally {
            ScriptTimeoutGuard.beforeInterruptHook = null;
         }
      }
   }

   @Test void interruptThatCannotStopTheExecIsReported() {
      ScriptTimeoutGuard.Guard none = new ScriptTimeoutGuard().guard(null, Duration.ZERO);
      assertFalse(none.interruptTimedOut());
   }
}
