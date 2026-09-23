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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Counters of one pooled worksheet env's contexts (bug #76960, spec §4.1, §14.3), plus the
 * node-wide count of pooled contexts.
 */
public final class PoolMetrics {
   void slotCreated() {
      int now = size.incrementAndGet();
      highWater.accumulateAndGet(now, Math::max);
      creations.incrementAndGet();
      NODE_SLOTS.incrementAndGet();
   }

   void slotClosed() {
      size.decrementAndGet();
      NODE_SLOTS.decrementAndGet();
   }

   void evicted() {
      evictions.incrementAndGet();
   }

   void doomedClosed() {
      doomedCloses.incrementAndGet();
   }

   void cleaned() {
      cleans.incrementAndGet();
   }

   void executed() {
      execs.incrementAndGet();
   }

   public int getSize() {
      return size.get();
   }

   public int getHighWater() {
      return highWater.get();
   }

   public long getCreations() {
      return creations.get();
   }

   public long getEvictions() {
      return evictions.get();
   }

   public long getDoomedCloses() {
      return doomedCloses.get();
   }

   public long getCleans() {
      return cleans.get();
   }

   public long getExecs() {
      return execs.get();
   }

   /**
    * @return cleans per exec; with batch claims this stays far below 1 (spec §14.3).
    */
   public double cleansPerExec() {
      long n = execs.get();
      return n == 0 ? 0 : (double) cleans.get() / n;
   }

   /**
    * @return the pooled worksheet contexts open on this node.
    */
   public static int nodeSlots() {
      return NODE_SLOTS.get();
   }

   private static final AtomicInteger NODE_SLOTS = new AtomicInteger();
   private final AtomicInteger size = new AtomicInteger();
   private final AtomicInteger highWater = new AtomicInteger();
   private final AtomicLong creations = new AtomicLong();
   private final AtomicLong evictions = new AtomicLong();
   private final AtomicLong doomedCloses = new AtomicLong();
   private final AtomicLong cleans = new AtomicLong();
   private final AtomicLong execs = new AtomicLong();
}
