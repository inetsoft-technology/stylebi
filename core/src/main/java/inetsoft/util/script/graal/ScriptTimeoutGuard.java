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

import org.graalvm.polyglot.Context;
import java.time.Duration;
import java.util.concurrent.*;

/**
 * Interrupts a long-running Context evaluation. Replaces TimeoutContext.
 * Usage:
 *   try(var ignored = guard.guard(ctx, duration)) { ctx.eval(...); }
 * If duration is zero or negative, no timeout is scheduled.
 */
public class ScriptTimeoutGuard {
   /** AutoCloseable variant that does not throw a checked exception on close(). */
   @FunctionalInterface
   public interface Guard extends AutoCloseable {
      @Override void close();
   }

   private static final ScheduledExecutorService SCHED =
      Executors.newScheduledThreadPool(1, r -> {
         Thread t = new Thread(r, "script-timeout-guard");
         t.setDaemon(true);
         return t;
      });

   // Separate cached pool for the blocking ctx.interrupt() calls so that
   // concurrent timeouts never queue behind each other on the scheduler thread.
   private static final ExecutorService INTERRUPT_POOL =
      Executors.newCachedThreadPool(r -> {
         Thread t = new Thread(r, "script-timeout-interrupt");
         t.setDaemon(true);
         return t;
      });

   /** Returns a Guard that cancels the watchdog when the eval finishes. */
   public Guard guard(Context ctx, Duration timeout) {
      if(timeout == null || timeout.isZero() || timeout.isNegative()) {
         return () -> { };
      }

      ScheduledFuture<?> f = SCHED.schedule(() ->
         INTERRUPT_POOL.submit(() -> {
            try {
               ctx.interrupt(Duration.ofSeconds(2));
            }
            catch(Exception ignore) {
               // context may already be closed
            }
         }),
         timeout.toMillis(), TimeUnit.MILLISECONDS);

      return () -> f.cancel(false);
   }
}
