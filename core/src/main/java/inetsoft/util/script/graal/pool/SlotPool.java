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

import inetsoft.util.script.ScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * The contexts of one pooled worksheet env (bug #76960, spec §4.3-§4.5): a primary and any
 * number of pooled slots. No method ever waits for a context: a busy slot is skipped with
 * tryLock and a new one is created, without a cap (I6); retire and eviction close only slots
 * they can tryLock, and doom the rest, which their owners close at their outermost release.
 */
final class SlotPool {
   SlotPool(SlotSource source, PoolConfig config, PoolMetrics metrics) {
      this.source = source;
      this.config = config;
      this.metrics = metrics;
   }

   PoolConfig config() {
      return config;
   }

   /**
    * Take a context for a claim at depth 0 to 1, replayed up to the current variables.
    *
    * @return a slot locked by the calling thread.
    */
   Slot checkout() {
      while(true) {
         Slot slot = takePrimary();

         if(slot == null) {
            slot = takePooled();
         }

         if(slot == null) {
            slot = create();
            pooled.add(slot);
         }

         if(prepare(slot)) {
            return slot;
         }
      }
   }

   /**
    * Return a context at its claim's 1 to 0 release: clean it and keep it idle, or close it
    * if it is doomed, stale, or not reusable after the clean.
    */
   void release(Slot slot) {
      boolean keep = false;

      try {
         if(!slot.isDoomed() && slot.epoch() >= epoch.get()) {
            keep = slot.clean().reusable(config.cleanThreshold()) &&
               !slot.isDoomed() && slot.epoch() >= epoch.get();
         }
      }
      finally {
         if(keep) {
            runBeforeIdleHook();
            slot.release();
            closeIfRetired(slot);
         }
         else {
            if(slot.isDoomed()) {
               metrics.doomedClosed();
            }

            discard(slot);
         }
      }
   }

   /**
    * Give every later checkout a fresh epoch: close the idle contexts that can be taken
    * without waiting, and doom the rest, including the calling thread's own (N6).
    */
   void retire() {
      epoch.incrementAndGet();

      for(Slot slot : slots()) {
         if(slot.isHeldByCurrentThread()) {
            slot.doom();
         }
         else if(slot.tryAcquire()) {
            discard(slot);
         }
         else {
            slot.doom();
         }
      }
   }

   /**
    * Create the primary if there is none, without waiting for anything.
    */
   void ensurePrimary() {
      if(primary.get() != null) {
         return;
      }

      Slot created = create();

      if(!primary.compareAndSet(null, created)) {
         pooled.add(created);
      }

      runBeforeIdleHook();
      created.release();
      closeIfRetired(created);
   }

   Slot primary() {
      return primary.get();
   }

   List<Slot> slots() {
      List<Slot> list = new ArrayList<>();
      Slot first = primary.get();

      if(first != null) {
         list.add(first);
      }

      list.addAll(pooled);
      return list;
   }

   int size() {
      return metrics.getSize();
   }

   /**
    * Close pooled contexts idle for longer than idleMillis, and stale ones. Never the primary,
    * never a context it cannot tryLock.
    */
   void evictIdle(long now) {
      for(Slot slot : new ArrayList<>(pooled)) {
         if(!isEvictable(slot, now) || !slot.tryAcquire()) {
            continue;
         }

         if(isEvictable(slot, now)) {
            metrics.evicted();
            discard(slot);
         }
         else {
            slot.unlock();
         }
      }
   }

   private boolean isEvictable(Slot slot, long now) {
      return slot.epoch() < epoch.get() || now - slot.idleSince() >= config.idleMillis();
   }

   private Slot takePrimary() {
      Slot current = primary.get();

      if(current == null) {
         Slot created = create();

         if(!primary.compareAndSet(null, created)) {
            pooled.add(created);
         }

         return created;
      }

      return current.tryAcquire() ? current : null;
   }

   private Slot takePooled() {
      for(Slot slot : pooled) {
         if(slot.tryAcquire()) {
            return slot;
         }
      }

      return null;
   }

   private Slot create() {
      startEvictor();
      Slot slot;

      try {
         slot = source.create(epoch.get());
      }
      catch(Exception ex) {
         LOG.error("Failed to create a worksheet script context", ex);
         throw new ScriptException("Failed to create a worksheet script context: " +
                                   ex.getMessage());
      }

      checkAlarms();
      return slot;
   }

   /**
    * Bring a taken slot up to date (spec §4.3 steps 3-4). Caller holds its lock.
    *
    * @return false if the slot was stale or doomed and was closed instead.
    */
   private boolean prepare(Slot slot) {
      if(slot.isDoomed() || slot.epoch() < epoch.get()) {
         discard(slot);
         return false;
      }

      // Linearization: a checkout takes effect here, before any retire() that dooms the slot
      // after this check. Such a slot is still handed out on purpose (retire never waits for
      // or revokes a holder), and it is closed at its claim's 1 to 0 release, whose doomed or
      // stale check (or closeIfRetired) sees the doom or the newer epoch.

      try {
         EnvState.Snapshot state = source.state().snapshot();

         if(state.version() > slot.version()) {
            slot.replay(state.after(slot.version()), state.version());
         }

         slot.engine().setSQL(source.isSQL());
         return true;
      }
      catch(RuntimeException ex) {
         discard(slot);
         throw ex;
      }
   }

   /**
    * Close and drop a slot; a closed primary is replaced by the next checkout (N7). Caller
    * holds its lock, which this releases.
    */
   private void discard(Slot slot) {
      pooled.remove(slot);
      primary.compareAndSet(slot, null);
      slot.close();
      slot.unlock();
   }

   private void checkAlarms() {
      int size = size();

      if(size > config.warnSlotsPerSandbox() && sandboxWarned.compareAndSet(false, true)) {
         LOG.warn("A worksheet sandbox has {} script contexts (warn threshold {}); contexts " +
                  "are never capped", size, config.warnSlotsPerSandbox());
      }

      int node = PoolMetrics.nodeSlots();

      if(node > config.warnSlotsPerNode() && NODE_WARNED.compareAndSet(false, true)) {
         LOG.warn("This node has {} pooled worksheet script contexts (warn threshold {}); " +
                  "contexts are never capped", node, config.warnSlotsPerNode());
      }
   }

   private void startEvictor() {
      if(!evictorStarted.compareAndSet(false, true)) {
         return;
      }

      WeakReference<SlotPool> ref = new WeakReference<>(this);
      long period = Math.max(1000L, config.idleMillis() / 2);
      EVICTOR.scheduleWithFixedDelay(() -> {
         SlotPool pool = ref.get();

         if(pool == null) {
            // stops this schedule
            throw new CancellationException("worksheet script pool collected");
         }

         try {
            pool.evictIdle(System.currentTimeMillis());
         }
         catch(RuntimeException ex) {
            LOG.warn("Failed to evict idle worksheet script contexts", ex);
         }
      }, period, period, TimeUnit.MILLISECONDS);
   }

   /**
    * Close a slot that was doomed or went stale after its keeper's last check but before it
    * went idle. A retire() in that window could only doom it, since the keeper still held the
    * lock, and no owner is left to close it at a later release: a primary is never evicted and
    * would stay open, counted in the node's slots. Never waits: if the tryLock fails, the new
    * holder closes it at its own release or its checkout's prepare.
    */
   private void closeIfRetired(Slot slot) {
      if((slot.isDoomed() || slot.epoch() < epoch.get()) && slot.tryAcquire()) {
         if(slot.isDoomed()) {
            metrics.doomedClosed();
         }

         discard(slot);
      }
   }

   private void runBeforeIdleHook() {
      Runnable hook = beforeIdleHook;

      if(hook != null) {
         hook.run();
      }
   }

   /**
    * Test hook run right before a slot this pool keeps goes idle (release's keep path and
    * ensurePrimary), while the caller still holds its lock; null in production.
    */
   volatile Runnable beforeIdleHook;

   private static final ScheduledExecutorService EVICTOR =
      Executors.newSingleThreadScheduledExecutor(r -> {
         Thread thread = new Thread(r, "ws-script-pool-evictor");
         thread.setDaemon(true);
         return thread;
      });
   private static final AtomicBoolean NODE_WARNED = new AtomicBoolean();

   private final SlotSource source;
   private final PoolConfig config;
   private final PoolMetrics metrics;
   private final AtomicReference<Slot> primary = new AtomicReference<>();
   private final Set<Slot> pooled = ConcurrentHashMap.newKeySet();
   private final AtomicLong epoch = new AtomicLong();
   private final AtomicBoolean evictorStarted = new AtomicBoolean();
   private final AtomicBoolean sandboxWarned = new AtomicBoolean();

   private static final Logger LOG = LoggerFactory.getLogger(SlotPool.class);
}
