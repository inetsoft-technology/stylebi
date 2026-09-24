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
package inetsoft.util.health;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A DOWN health check writes a status dump when it goes DOWN, then at most once per interval
 * while it stays DOWN, not on every poll (bug #76967).
 */
@Tag("core")
public class StatusDumpLimiterTest {
   @Test
   public void dumpsOnTheTransitionThenOncePerInterval() {
      StatusDumpLimiter limiter = new StatusDumpLimiter(now::get, 600000);

      assertFalse(limiter.shouldDump(false), "UP never dumps");
      assertTrue(limiter.shouldDump(true), "UP to DOWN dumps");

      for(int i = 0; i < 9; i++) {
         advance(60000);
         assertFalse(limiter.shouldDump(true), "no dump on every poll while DOWN");
      }

      advance(60000);
      assertTrue(limiter.shouldDump(true), "once per interval while it stays DOWN");
      advance(60000);
      assertFalse(limiter.shouldDump(true));
   }

   @Test
   public void laterEpisodeDumpsAtOnce() {
      StatusDumpLimiter limiter = new StatusDumpLimiter(now::get, 600000);

      assertTrue(limiter.shouldDump(true));
      advance(60000);
      assertFalse(limiter.shouldDump(false));
      advance(600000);
      assertTrue(limiter.shouldDump(true), "a later DOWN episode dumps on its first poll");
      advance(60000);
      assertFalse(limiter.shouldDump(true));
   }

   @Test
   public void flappingDoesNotDumpOnEveryTransition() {
      StatusDumpLimiter limiter = new StatusDumpLimiter(now::get, 600000);

      assertTrue(limiter.shouldDump(true));

      for(int i = 0; i < 5; i++) {
         advance(30000);
         assertFalse(limiter.shouldDump(false));
         advance(30000);
         assertFalse(limiter.shouldDump(true), "a flapping DOWN is rate-limited too");
      }
   }

   @Test
   public void stallOnlyDownIsRateLimited() {
      StatusDumpLimiter limiter = new StatusDumpLimiter(now::get, 600000);
      HealthStatus status = status(true, false);

      assertTrue(limiter.shouldDump(status));

      for(int i = 0; i < 9; i++) {
         advance(60000);
         assertFalse(limiter.shouldDump(status), "a lock stall alone is rate-limited");
      }
   }

   @Test
   public void nonStallDownDumpsOnEveryPoll() {
      StatusDumpLimiter limiter = new StatusDumpLimiter(now::get, 600000);

      for(int i = 0; i < 10; i++) {
         assertTrue(limiter.shouldDump(status(false, true)),
                    "any other DOWN cause dumps on every poll, as before bug #76967");
         assertTrue(limiter.shouldDump(status(true, true)),
                    "a lock stall with another DOWN cause dumps on every poll");
         advance(60000);
      }
   }

   @Test
   public void upNeverDumps() {
      StatusDumpLimiter limiter = new StatusDumpLimiter(now::get, 600000);

      assertFalse(limiter.shouldDump(status(false, false)));
   }

   /**
    * A health status that is DOWN for an unreleased lock stall and/or out of memory.
    */
   private static HealthStatus status(boolean stalled, boolean outOfMemory) {
      return new HealthStatus(
         new CacheSwapStatus(),
         new DeadlockStatus(0, new DeadlockedThread[0], stalled ? "stall not released: X" : null),
         new OutOfMemoryStatus(outOfMemory, outOfMemory ? "now" : null),
         new ReportFailureStatus(),
         new SchedulerStatus(true, false, false, 0, 0, null, 0, 0),
         new SecurityProviderStatus(0, new SecurityProviderState[0]),
         new FileSystemStatus(0, new FileSystemState[0]));
   }

   private void advance(long millis) {
      now.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
   }

   private final AtomicLong now = new AtomicLong(1_000_000_000L);
}
