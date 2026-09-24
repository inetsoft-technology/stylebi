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

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Rate-limits the status dump of a health check that is DOWN (bug #76967). The dump is a zip
 * of dashboards, queries, users, threads, logs and metrics written to external storage, and a
 * DOWN check (a JVM deadlock or an unreleased lock stall) is polled every minute or so: it is
 * written on the first DOWN poll, then at most once per interval while the check stays DOWN
 * or flaps, instead of on every poll. The callers rate-limit only a DOWN caused by an
 * unreleased lock stall alone; every other DOWN cause is dumped on every poll, as before.
 */
public final class StatusDumpLimiter {
   /**
    * A limiter of one dump per {@link #DEFAULT_INTERVAL_MILLIS}.
    */
   public StatusDumpLimiter() {
      this(System::nanoTime, DEFAULT_INTERVAL_MILLIS);
   }

   /**
    * @param nanoClock      the clock.
    * @param intervalMillis the minimum time between two dumps.
    */
   public StatusDumpLimiter(LongSupplier nanoClock, long intervalMillis) {
      this.nanoClock = nanoClock;
      this.intervalNanos = TimeUnit.MILLISECONDS.toNanos(intervalMillis);
   }

   /**
    * Record a health poll, and check if the status should be dumped now.
    *
    * @param down whether the health check is DOWN.
    *
    * @return {@code true} if it is DOWN and no status was dumped within the interval.
    */
   public synchronized boolean shouldDump(boolean down) {
      if(!down) {
         return false;
      }

      long now = nanoClock.getAsLong();

      if(dumped && now - lastDumpNanos < intervalNanos) {
         return false;
      }

      dumped = true;
      lastDumpNanos = now;
      return true;
   }

   /**
    * Record a health poll of {@code status}, and check if the status should be dumped now. Only
    * a DOWN caused by an unreleased lock stall alone is rate-limited; any other DOWN is dumped
    * on every poll, as before bug #76967.
    */
   public boolean shouldDump(HealthStatus status) {
      if(!status.isDown()) {
         return false;
      }

      return !status.isDownOnlyByLockStall() || shouldDump(true);
   }

   public static final long DEFAULT_INTERVAL_MILLIS = 600000L;

   private final LongSupplier nanoClock;
   private final long intervalNanos;
   private boolean dumped;
   private long lastDumpNanos;
}
