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

   // Bug #77004: cancel(false) on a ScheduledThreadPoolExecutor without
   // remove-on-cancel leaves the cancelled ScheduledFutureTask in the delay
   // queue until its original deadline, so a long script.execution.timeout
   // (600s default, 10000s code default) lets the queue -- and the heap it
   // holds -- grow unbounded across script execs.
   @Test void cancelledWatchdogsDoNotAccumulateInQueue() {
      ScriptTimeoutGuard guard = new ScriptTimeoutGuard();
      Duration longTimeout = Duration.ofSeconds(600);

      try(Context ctx = Context.newBuilder("js").build()) {
         for(int i = 0; i < 10_000; i++) {
            try(var ignored = guard.guard(ctx, longTimeout)) {
               ctx.eval("js", "1+1");
            }
         }
      }

      assertTrue(ScriptTimeoutGuard.pendingWatchdogs() <= 10,
         "expected cancelled watchdogs to be removed from the scheduler queue, but found " +
         ScriptTimeoutGuard.pendingWatchdogs());
   }
}
