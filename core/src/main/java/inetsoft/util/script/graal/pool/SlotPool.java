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
import inetsoft.util.stall.StallWatchdog;
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
      PoolMetrics.setNodeSlotWarnThreshold(config.warnSlotsPerNode());
      WsPoolStallProbe.register();
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
      // Terminates: a taken slot fails prepare() only if it is stale or doomed, and each
      // stale idle slot is discarded once. A freshly created slot fails only if a retire()
      // (env reset/drop) lands between create() stamping its epoch and prepare(), so every
      // extra pass needs another retire. Never waits, so no backoff.
      while(true) {
         // a formula table whose objects live on a home takes it first (B1 residual part 2)
         SlotTenant hint = homes.isEmpty() ? null : SlotClaim.homeHint();
         Slot slot = hint == null ? null : takeHome(hint);

         if(slot == null) {
            slot = takePrimary(hint);
         }

         if(slot == null) {
            slot = takePooled(hint);
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
               (!PoolParanoia.enabled() || PoolParanoia.accept(slot)) &&
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
      if(!homes.isEmpty()) {
         expireHomes(now);
      }

      for(Slot slot : new ArrayList<>(pooled)) {
         // a home whose tenants could not hand off yet is kept for the next tick
         if(!isEvictable(slot, now) || homes.contains(slot) || !slot.tryAcquire()) {
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

   private Slot takePrimary(SlotTenant hint) {
      Slot current = primary.get();

      if(current == null) {
         Slot created = create();

         if(!primary.compareAndSet(null, created)) {
            pooled.add(created);
         }

         return created;
      }

      return take(current, hint) ? current : null;
   }

   private Slot takePooled(SlotTenant hint) {
      for(Slot slot : pooled) {
         if(take(slot, hint)) {
            return slot;
         }
      }

      return null;
   }

   // ---- homes of formula tables' objects (Testing #77123, B1 residual part 2) -------------
   //
   // A formula table whose owned vars hold arrays or objects keeps them live on the context of
   // its last batch, its home. While the home is idle, a claim of another thread or table:
   // - skips it if it is one of the pool's exclusive homes (at most maxHomes per sandbox and
   //   maxHomesPerNode per node);
   // - otherwise takes it over after every tenant handed off (saved its values as a tree under
   //   its own table lock, taken without waiting), or skips it if one could not.
   // The evictor hands off a home idle for idleMillis (and then evicts it as usual); closing a
   // home hands off first, and a tenant that cannot loses its values (a warning on read).

   /**
    * Take the idle home of {@code hint}, never waiting.
    */
   private Slot takeHome(SlotTenant hint) {
      for(Slot slot : homes) {
         if(slot.hasTenant(hint) && isLive(slot) && slot.tryAcquire()) {
            if(slot.hasTenant(hint)) {
               return slot;
            }

            slot.unlock();
         }
      }

      return null;
   }

   /**
    * Take {@code slot} for a claim that prefers the home of {@code hint} (or none), never
    * waiting: a home of other tenants only if it is not exclusive and all of them hand off.
    */
   private boolean take(Slot slot, SlotTenant hint) {
      if(homes.isEmpty() || !homes.contains(slot)) {
         return slot.tryAcquire();
      }

      if(slot.exclusiveHome != null && (hint == null || !slot.hasTenant(hint)) ||
         !slot.tryAcquire())
      {
         return false;
      }

      // under the slot's lock no batch can enroll on it: this is the authoritative check
      if(hint != null && slot.hasTenant(hint)) {
         return true;
      }

      boolean exclusive;

      synchronized(homesLock) {
         purge(slot);
         exclusive = slot.exclusiveHome != null;
      }

      if(!exclusive && (!slot.hasTenants() || handOffAll(slot)) && !homes.contains(slot)) {
         metrics.tookOver();
         return true;
      }

      slot.unlock();
      return false;
   }

   private boolean isLive(Slot slot) {
      return primary.get() == slot || pooled.contains(slot);
   }

   /**
    * Record that {@code tenant}'s objects live on {@code slot}, which the caller holds.
    */
   void enroll(Slot slot, SlotTenant tenant) {
      synchronized(homesLock) {
         slot.addTenant(tenant);
         homes.add(slot);

         if(slot.exclusiveHome == null && exclusiveHomes < config.maxHomes() &&
            PoolMetrics.nodeHomes() < config.maxHomesPerNode())
         {
            slot.exclusiveHome = PoolMetrics.homeGranted(slot);
            exclusiveHomes++;
         }
      }
   }

   /**
    * {@code tenant}'s objects no longer live on {@code slot}. Never waits.
    */
   void leave(Slot slot, SlotTenant tenant) {
      synchronized(homesLock) {
         slot.removeTenant(tenant);
         purge(slot);
      }
   }

   // a slot without tenants (left, or collected) is no home; caller holds homesLock
   private void purge(Slot slot) {
      if(!slot.hasTenants()) {
         homes.remove(slot);

         if(slot.exclusiveHome != null) {
            slot.exclusiveHome.clean();
            slot.exclusiveHome = null;
            exclusiveHomes--;
         }
      }
   }

   /**
    * Hand off every tenant of {@code slot}, which the calling thread holds.
    *
    * @return whether no tenant is left on it.
    */
   private boolean handOffAll(Slot slot) {
      OwnedValueCodec codec = slot.isClosed() ? null : OwnedValueCodec.of(slot);
      boolean all = true;

      for(SlotTenant tenant : slot.tenants()) {
         boolean done;

         try {
            done = codec == null || tenant.handOff(codec);
         }
         catch(RuntimeException ex) {
            LOG.debug("Failed to hand off the objects of a formula table", ex);
            done = false;
         }

         if(done) {
            leave(slot, tenant);
         }
         else {
            all = false;
         }
      }

      return all;
   }

   /**
    * Hand off the homes idle for longer than idleMillis (amendment A5), or stale ones; a home
    * whose tenant cannot take its lock now is kept for the next tick.
    */
   private void expireHomes(long now) {
      for(Slot slot : new ArrayList<>(homes)) {
         synchronized(homesLock) {
            purge(slot);
         }

         if(!homes.contains(slot) || !isEvictable(slot, now) || !slot.tryAcquire()) {
            continue;
         }

         try {
            handOffAll(slot);
         }
         finally {
            slot.unlock();
         }
      }
   }

   /**
    * Give back a home a pull took (never waiting): close it if it was retired meanwhile.
    */
   void returnPulled(Slot slot) {
      slot.unlock();
      closeIfRetired(slot);
   }

   /**
    * Test hook: hand off every idle home now, as a take-over or expiry would.
    *
    * @return the homes handed off.
    */
   int handOffIdleHomes() {
      int n = 0;

      for(Slot slot : new ArrayList<>(homes)) {
         if(slot.tryAcquire()) {
            try {
               if(handOffAll(slot)) {
                  n++;
               }
            }
            finally {
               slot.unlock();
            }
         }
      }

      return n;
   }

   /**
    * @return the exclusive homes of this pool.
    */
   int exclusiveHomes() {
      synchronized(homesLock) {
         return exclusiveHomes;
      }
   }

   /**
    * @return the homes of this pool, exclusive or not.
    */
   int homes() {
      return homes.size();
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

      slot.setConfig(config);
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
         if(slot.isDoomed()) {
            metrics.doomedClosed();
         }

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
      if(homes.contains(slot)) {
         try {
            // a tenant that cannot hand off now loses its objects (a warning on read)
            handOffAll(slot);
         }
         catch(RuntimeException ex) {
            LOG.debug("Failed to hand off the objects of a closed home", ex);
         }
         finally {
            synchronized(homesLock) {
               slot.clearTenants();
               purge(slot);
            }
         }
      }

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

      if(node > config.warnSlotsPerNode()) {
         // the watchdog's probe reports the node above the threshold (bug #76967)
         StallWatchdog.wake();
      }

      if(node > config.warnSlotsPerNode() && NODE_WARNED.compareAndSet(false, true)) {
         LOG.warn("This node has {} pooled worksheet script contexts (warn threshold {}); " +
                  "contexts are never capped", node, config.warnSlotsPerNode());
      }
   }

   private void startEvictor() {
      if(!evictorStarted.compareAndSet(false, true)) {
         return;
      }

      if(NODE_LOG_STARTED.compareAndSet(false, true)) {
         long minutes = PoolMetrics.LOG_PERIOD_MINUTES;
         EVICTOR.scheduleWithFixedDelay(() -> {
            try {
               PoolMetrics.logNodeSummary();
            }
            catch(RuntimeException ex) {
               LOG.debug("Failed to log the worksheet script pool metrics", ex);
            }
         }, minutes, minutes, TimeUnit.MINUTES);
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
   void closeIfRetired(Slot slot) {
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
   // the node-wide metrics log (PoolMetrics.logNodeSummary) starts with the first pool
   private static final AtomicBoolean NODE_LOG_STARTED = new AtomicBoolean();

   private final SlotSource source;
   private final PoolConfig config;
   private final PoolMetrics metrics;
   private final AtomicReference<Slot> primary = new AtomicReference<>();
   private final Set<Slot> pooled = ConcurrentHashMap.newKeySet();
   private final AtomicLong epoch = new AtomicLong();
   private final AtomicBoolean evictorStarted = new AtomicBoolean();
   private final AtomicBoolean sandboxWarned = new AtomicBoolean();
   // the slots formula tables' objects live on (homes), and the exclusive ones among them
   private final Set<Slot> homes = ConcurrentHashMap.newKeySet();
   private final Object homesLock = new Object();
   private int exclusiveHomes; // guarded by homesLock

   private static final Logger LOG = LoggerFactory.getLogger(SlotPool.class);
}
