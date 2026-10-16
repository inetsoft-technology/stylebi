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

import inetsoft.util.script.LendableReentrantLock;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import inetsoft.util.script.graal.ScriptValueConverter;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.Cleaner;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;

/**
 * One pooled worksheet script context (bug #76960, spec §4.1-§4.4): a {@link WsEngine}, the
 * epoch it was built in, the change-log version it has applied, and its clean helper. Only the
 * thread holding its lock (the owner of a claim, or the pool while it closes an idle slot)
 * touches its Context; the lock is only ever tryLocked or re-entered.
 */
final class Slot {
   private Slot(WsEngine engine, long epoch, CleanHelper cleaner, PoolMetrics metrics) {
      this.engine = engine;
      this.lock = engine.getExecutionLock();
      this.epoch = epoch;
      this.cleaner = cleaner;
      this.metrics = metrics;
   }

   /**
    * Build a context: init from the snapshot with the current variables, then install the
    * clean helper, whose baseline is the result. Runs under no monitor (spec §4.2 step 4).
    * <p>
    * A library function that fails to compile with a PolyglotException is logged and skipped,
    * as on the plain engine. Any other throw during init (only a JVM error or a broken Context
    * in practice) fails the creation on purpose: the engine is closed, no slot is counted and
    * the throw reaches the caller, so a half-initialised context is never pooled.
    * <p>
    * A caller's cancel is kept (Testing #77123): the interrupt flag set before the creation is
    * cleared while it runs, so Graal does not stop the creation on it, and set again after; a
    * cancel that lands during the creation fails it and is re-asserted.
    *
    * @return the new slot, locked by the calling thread.
    */
   static Slot create(InitSnapshot snapshot, EnvState.Snapshot state, long epoch, boolean sql,
                      Map<Object, Integer> errorCounts, PoolMetrics metrics) throws Exception
   {
      WsEngine engine = new WsEngine(snapshot, errorCounts);
      boolean cancelled = Thread.interrupted();

      try {
         engine.setSQL(sql);
         engine.init(state.vars());
         Slot slot = new Slot(engine, epoch, CleanHelper.install(engine.context()), metrics);
         slot.version = state.version();
         engine.bind(slot);

         if(!slot.lock.tryLock()) {
            throw new IllegalStateException("A new worksheet script context is locked");
         }

         slot.nodeCount = metrics.slotCreated(slot);
         return slot;
      }
      catch(Throwable ex) {
         closeQuietly(engine);
         ScriptTimeoutGuard.keepCancel(ex, null);
         throw ex;
      }
      finally {
         if(cancelled) {
            Thread.currentThread().interrupt();
         }
      }
   }

   /**
    * Try to take this idle slot, never waiting.
    */
   boolean tryAcquire() {
      if(closed || lock.isHeldByCurrentThread() || !lock.tryLock()) {
         return false;
      }

      // Invariant: a slot has at most one claim, its lock holder's. Pool mode never lends a
      // slot's lock; if it is lent anyway, the tryLock above succeeded only as the borrower
      // of the holder's claim, which must not become a second claim on the same slot.
      if(closed || lock.isLent()) {
         lock.unlock();
         return false;
      }

      return true;
   }

   void unlock() {
      lock.unlock();
   }

   /**
    * Return this slot as idle.
    */
   void release() {
      idleSince = System.currentTimeMillis();
      lock.unlock();
   }

   /**
    * Apply the change-log entries this slot has not seen. Owner only.
    */
   void replay(List<EnvState.Change> changes, long upTo) {
      for(EnvState.Change change : changes) {
         if(change.removed()) {
            removeOwn(change.name());
         }
         else {
            applyOwn(change.name(), change.value());
         }
      }

      version = upTo;
   }

   /**
    * Bring this slot, which a query build claim holds, up to the env's current variables
    * before one of the build's scripts (G10 piece Q, amendment 2): the claim took it at the
    * build's first script, so without this the build would not see a variable another thread
    * set after that, which a checkout per script and the pool off both do. Owner only; never
    * waits. A context the replay fails on is doomed, so its claim's release closes it.
    */
   void resync(EnvState.Snapshot state, boolean sql) {
      if(state.version() > version) {
         try {
            replay(state.after(version), state.version());
         }
         catch(RuntimeException ex) {
            doom();
            throw ex;
         }
      }

      engine.setSQL(sql);
   }

   /**
    * Let the next clean delete up to {@code max} implicit globals before it reports too many
    * (the release of a query build claim, G10 piece Q amendment 3); later cleans are back at
    * {@link PoolConfig#MAX_FOREIGN_DELETES}. Owner only.
    */
   void allowDeletes(int max) {
      maxDeletes = max;
   }

   /**
    * Set a variable on this context now, and expect it (spec N2, §14.2). Owner only.
    */
   void applyOwn(String name, Object value) {
      engine.context().getBindings("js").putMember(
         name, WsValueCopier.markForeign(ScriptValueConverter.toGuest(value), engine.context()));
      engine.hostGlobal(name);
      cleaner.expect(name);
   }

   /**
    * Remove a variable from this context now, and stop expecting it. Owner only. Deleting a
    * global also drops the name resolution cache, whose cached "is a global" answer would
    * otherwise keep hiding a same-named case-insensitive CALC function.
    */
   void removeOwn(String name) {
      engine.context().getBindings("js").removeMember(name);
      cleaner.forget(name);
      engine.globalsCleaned();
   }

   /**
    * Bring the context back to its baseline (spec §4.4). Owner only.
    * <p>
    * A caller's cancel is kept (Testing #77123): a cancel that landed during the batch after
    * its last guest safepoint would otherwise stop the clean's JS, which clears the flag. The
    * flag is cleared while the clean runs and set again after. A cancel that lands during the
    * clean is re-asserted only if it surfaces outside a JS {@code try}: the clean then fails
    * with it, and {@link ScriptTimeoutGuard#keepCancel} sets the flag again (unless the
    * clean's own timeout interrupted it). One that surfaces inside a per-key {@code catch} of
    * the clean script is swallowed there, after Graal cleared the flag: the clean reports
    * {@code failed}, so {@link SlotPool#release} discards the slot, and the thread's flag
    * stays clear. The query still stops at its own cancel flag.
    */
   CleanHelper.Result clean() {
      boolean cancelled = Thread.interrupted();

      try {
         return clean0();
      }
      finally {
         if(cancelled) {
            Thread.currentThread().interrupt();
         }
      }
   }

   private CleanHelper.Result clean0() {
      metrics.cleaned();
      ScriptTimeoutGuard.Guard guard;
      CleanHelper.Result result;
      int maxDeletes = this.maxDeletes;
      this.maxDeletes = PoolConfig.MAX_FOREIGN_DELETES;

      try {
         guard = engine.guard(cleanTimeout);
      }
      catch(RuntimeException ex) {
         LOG.debug("Failed to clean a worksheet script context", ex);
         return CleanHelper.Result.FAILED;
      }

      try(guard) {
         result = cleaner.run(maxDeletes);

         if(result.removed() > 0) {
            engine.globalsCleaned();
         }
      }
      catch(RuntimeException ex) {
         ScriptTimeoutGuard.keepCancel(ex, guard);
         LOG.debug("Failed to clean a worksheet script context", ex);
         return CleanHelper.Result.FAILED;
      }

      // an interrupt that could not stop the clean leaves the Context unknown (spec §6.3)
      return guard.interruptTimedOut() ? CleanHelper.Result.FAILED : result;
   }

   /**
    * Mark this slot to be closed at its owner's outermost release, never earlier.
    */
   void doom() {
      doomed = true;
   }

   boolean isDoomed() {
      return doomed;
   }

   /**
    * An interrupt could not stop an exec on this context, so a claimed interrupt may still
    * land on it: doom it, and let a query build claim leave it at its next script (G10 piece
    * Q, amendment 1).
    */
   void interruptLost() {
      interruptLost = true;
      doom();
   }

   boolean isInterruptLost() {
      return interruptLost;
   }

   boolean isClosed() {
      return closed;
   }

   /**
    * A pool thread is about to take this slot without a claim and without the locks of all
    * its tenants (a take-over, the expiry, a retire): a tenant's batch that misses its home
    * meanwhile waits a moment for it in its pull instead of losing its objects (Testing
    * #77123, finding G1). Begun BEFORE the slot's tryLock, so a puller whose tryLock failed on
    * the prober's hold sees it; ended once the prober holds every tenant's lock (no tenant's
    * batch runs then) or gave the slot back. A count: probes of several threads may overlap.
    */
   void beginProbe() {
      probes.incrementAndGet();
   }

   void endProbe() {
      probes.decrementAndGet();
   }

   /**
    * @return whether a pool thread probes this slot now, see {@link #beginProbe}.
    */
   boolean isProbed() {
      return probes.get() > 0;
   }

   long epoch() {
      return epoch;
   }

   long version() {
      return version;
   }

   long idleSince() {
      return idleSince;
   }

   boolean isHeldByCurrentThread() {
      return lock.isHeldByCurrentThread();
   }

   WsEngine engine() {
      return engine;
   }

   PoolMetrics metrics() {
      return metrics;
   }

   /**
    * @return the configuration of the pool this slot belongs to.
    */
   PoolConfig config() {
      return config;
   }

   void setConfig(PoolConfig config) {
      this.config = config;
   }

   /**
    * @return whether {@code tenant} lives on this slot (Testing #77123, B1 residual part 2).
    */
   boolean hasTenant(SlotTenant tenant) {
      synchronized(tenants) {
         return tenants.containsKey(tenant);
      }
   }

   /**
    * @return the tenants living on this slot, a copy.
    */
   List<SlotTenant> tenants() {
      synchronized(tenants) {
         return new ArrayList<>(tenants.keySet());
      }
   }

   boolean hasTenants() {
      synchronized(tenants) {
         return !tenants.isEmpty();
      }
   }

   void addTenant(SlotTenant tenant) {
      synchronized(tenants) {
         tenants.put(tenant, Boolean.TRUE);
      }
   }

   void removeTenant(SlotTenant tenant) {
      synchronized(tenants) {
         tenants.remove(tenant);
      }
   }

   void clearTenants() {
      synchronized(tenants) {
         tenants.clear();
      }
   }

   CleanHelper cleaner() {
      return cleaner;
   }

   /**
    * Per-slot state of host objects (e.g. a table's row window), only ever used by the owner.
    */
   @SuppressWarnings("unchecked")
   <T> T attachment(Object key, Supplier<T> factory) {
      return (T) attachments.computeIfAbsent(key, k -> factory.get());
   }

   /**
    * Close the context. The caller must hold the lock; the lock stays held. The slot is marked
    * closed even if its Context fails to close, so it is never handed out again.
    *
    * @throws IllegalStateException if the current thread does not hold the lock.
    */
   void close() {
      if(!lock.isHeldByCurrentThread()) {
         throw new IllegalStateException(
            "A worksheet script context may only be closed by its lock holder");
      }

      if(closed) {
         return;
      }

      closed = true;
      attachments.clear();

      try {
         engine.close();
      }
      catch(Exception ex) {
         // still retired: closed stays true, so the slot is never reused
         LOG.warn("A worksheet script context failed to close; the slot is retired and " +
                  "never reused, but its Context may not have been released", ex);
      }

      metrics.slotClosed(nodeCount);
   }

   private static void closeQuietly(WsEngine engine) {
      try {
         engine.close();
      }
      catch(Exception ex) {
         LOG.debug("Failed to close a worksheet script context", ex);
      }
   }

   private final WsEngine engine;
   private final LendableReentrantLock lock;
   private final long epoch;
   private final CleanHelper cleaner;
   private final PoolMetrics metrics;
   private final Map<Object, Object> attachments = new WeakHashMap<>();
   // the formula tables whose owned objects live on this context (B1 residual part 2), weakly
   private final Map<SlotTenant, Boolean> tenants = new WeakHashMap<>();
   // set while this is an exclusive home, which other claims skip while it is idle; it counts
   // toward the node's homes until revoked or collected. Guarded by the pool's homes lock
   Cleaner.Cleanable exclusiveHome;
   private volatile PoolConfig config = PoolConfig.defaults();
   // the clean's timeout; only tests shorten it
   Duration cleanTimeout = CleanHelper.TIMEOUT;
   private Cleaner.Cleanable nodeCount; // set at creation, before the slot is shared
   private long version; // owner only
   private int maxDeletes = PoolConfig.MAX_FOREIGN_DELETES; // owner only
   // the pool threads probing this slot, see beginProbe
   private final java.util.concurrent.atomic.AtomicInteger probes =
      new java.util.concurrent.atomic.AtomicInteger();
   private volatile boolean doomed;
   private volatile boolean interruptLost;
   private volatile boolean closed;
   private volatile long idleSince = System.currentTimeMillis();

   private static final Logger LOG = LoggerFactory.getLogger(Slot.class);
}
