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
import java.util.concurrent.locks.Lock;

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

            giveBack(slot);
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
         runPlainTakeHook(slot);

         if(!slot.tryAcquire()) {
            return false;
         }

         // under the slot's lock no batch can enroll on it: this is the authoritative check
         if(!homes.contains(slot)) {
            return true;
         }

         // a batch made it its home between the check and the lock (Testing #77123, finding
         // G2): keeping it would hold the home for this whole claim, so the table's next batch
         // lost its objects. Give it back and take it as a home: the tenants' locks first
         giveBack(slot);
      }

      if(slot.exclusiveHome != null && (hint == null || !slot.hasTenant(hint))) {
         return false;
      }

      TenantLocks locks = new TenantLocks();

      try {
         // a take-over holds the tenants' locks before the slot (B1-R2-1); the claim's own
         // home needs none
         if(!(hint != null && slot.hasTenant(hint)) && !locks.take(slot) ||
            !slot.tryAcquire())
         {
            return false;
         }

         // under the slot's lock no batch can enroll on it: this is the authoritative check
         if(hint != null && slot.hasTenant(hint)) {
            return true;
         }

         runHandOffHook(slot);
         boolean exclusive;

         synchronized(homesLock) {
            purge(slot);
            exclusive = slot.exclusiveHome != null;
         }

         // a home whose tenants were only collected is no take-over
         boolean tenants = slot.hasTenants();

         // a tenant that enrolled before the slot was taken is locked now, or the home is
         // skipped before anything is handed off
         if(!exclusive && (!tenants || locks.take(slot) && handOffAll(slot)) &&
            !homes.contains(slot))
         {
            if(tenants) {
               metrics.tookOver();
            }

            return true;
         }

         // given back while the tenants' locks are still held: none of their batches saw it
         giveBack(slot);
         return false;
      }
      finally {
         locks.release();
      }
   }

   /**
    * The locks of an idle home's tenants, which a hand-off by the pool (a take-over, the
    * expiry) takes without waiting BEFORE the slot, and releases only after it gave the slot
    * back (Testing #77123, finding B1-R2-1). A tenant's batch holds its lock (the formula
    * table's lens lock) from before it takes a context until after it pulled its values from
    * its home, so while the pool holds a home for a hand-off no batch of a locked tenant runs:
    * none can miss its home because of the hand-off and then fail to pull from it, which lost
    * every object var of the home (a warning "... another thread is using"). This is the order
    * every batch takes them in (a table's lock, then its context), and every lock here is only
    * tryLocked, so the pool never waits and no cycle can form; a batch that wants its table's
    * lock meanwhile waits no longer than the hand-off, as it did when the hand-off took the
    * lock itself.
    */
   private static final class TenantLocks {
      /**
       * TryLock the lock of every tenant of {@code slot} not locked yet.
       *
       * @return false if another thread holds one: skip the home (nothing was handed off).
       */
      boolean take(Slot slot) {
         for(SlotTenant tenant : slot.tenants()) {
            Lock lock = tenant.handOffLock();

            if(lock == null || held.contains(lock)) {
               continue;
            }

            if(!lock.tryLock()) {
               return false;
            }

            held.add(lock);
         }

         return true;
      }

      void release() {
         for(int i = held.size() - 1; i >= 0; i--) {
            held.get(i).unlock();
         }

         held.clear();
      }

      private final List<Lock> held = new ArrayList<>();
   }

   /**
    * Give back a slot this pool took without a claim (a refused take, a hand-off), never
    * waiting: close it if a retire() doomed it meanwhile, which could only doom it while it
    * was held (N6).
    */
   private void giveBack(Slot slot) {
      slot.unlock();
      closeIfRetired(slot);
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

         if(slot.exclusiveHome == null && exclusiveHomes < config.maxHomes()) {
            // the node cap is granted atomically across the node's pools
            slot.exclusiveHome = PoolMetrics.tryGrantHome(slot, config.maxHomesPerNode());

            if(slot.exclusiveHome != null) {
               exclusiveHomes++;
            }
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
    * Hand off the homes idle for longer than idleMillis (amendment A5), or stale ones, at most
    * {@link #HAND_OFFS_PER_PASS} per pass: the evictor thread is shared by every pool of the
    * node, so the rest waits for the next tick. A home whose tenant cannot take its lock now
    * is kept for the next tick too.
    */
   private void expireHomes(long now) {
      int budget = HAND_OFFS_PER_PASS;

      for(Slot slot : new ArrayList<>(homes)) {
         synchronized(homesLock) {
            purge(slot);
         }

         if(budget <= 0 || !homes.contains(slot) || !isEvictable(slot, now)) {
            continue;
         }

         TenantLocks locks = new TenantLocks();

         try {
            // the tenants' locks before the slot, see TenantLocks
            if(!locks.take(slot) || !slot.tryAcquire()) {
               continue;
            }

            budget--;

            try {
               runHandOffHook(slot);

               if(locks.take(slot)) {
                  handOffAll(slot);
               }
            }
            finally {
               giveBack(slot);
            }
         }
         finally {
            locks.release();
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
         TenantLocks locks = new TenantLocks();

         try {
            // the tenants' locks before the slot, see TenantLocks
            if(locks.take(slot) && slot.tryAcquire()) {
               try {
                  runHandOffHook(slot);

                  if(locks.take(slot) && handOffAll(slot)) {
                     n++;
                  }
               }
               finally {
                  giveBack(slot);
               }
            }
         }
         finally {
            locks.release();
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
      catch(RuntimeException | Error ex) {
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

      try {
         slot.close();
      }
      finally {
         // Slot.close catches Exception only: an Error there must not leak the lock
         slot.unlock();
      }
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
            catch(RuntimeException | Error ex) {
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
         catch(RuntimeException | Error ex) {
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

   private void runHandOffHook(Slot slot) {
      java.util.function.Consumer<Slot> hook = handOffHook;

      if(hook != null) {
         hook.accept(slot);
      }
   }

   private void runPlainTakeHook(Slot slot) {
      java.util.function.Consumer<Slot> hook = plainTakeHook;

      if(hook != null) {
         hook.accept(slot);
      }
   }

   /**
    * Test hook run by a take-over, an expiry or {@link #handOffIdleHomes} once it holds an idle
    * home without a claim, before it hands off the tenants (Testing #77123, B1-R2-1); null in
    * production.
    */
   volatile java.util.function.Consumer<Slot> handOffHook;

   /**
    * Test hook run by a take of a slot that is no home, after that check and before it
    * takes the slot's lock (Testing #77123, finding G2); null in production.
    */
   volatile java.util.function.Consumer<Slot> plainTakeHook;

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
   // the most homes one pool's evictor pass hands off (Testing #77123, B1 residual part 2)
   static final int HAND_OFFS_PER_PASS = 4;
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
