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
    *
    * @return the new slot, locked by the calling thread.
    */
   static Slot create(InitSnapshot snapshot, EnvState.Snapshot state, long epoch, boolean sql,
                      Map<Object, Integer> errorCounts, PoolMetrics metrics) throws Exception
   {
      WsEngine engine = new WsEngine(snapshot, errorCounts);

      try {
         engine.setSQL(sql);
         engine.init(state.vars());
         Slot slot = new Slot(engine, epoch, CleanHelper.install(engine.context()), metrics);
         slot.version = state.version();
         engine.bind(slot);

         if(!slot.lock.tryLock()) {
            throw new IllegalStateException("A new worksheet script context is locked");
         }

         metrics.slotCreated();
         return slot;
      }
      catch(Throwable ex) {
         closeQuietly(engine);
         throw ex;
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
    * Set a variable on this context now, and expect it (spec N2, §14.2). Owner only.
    */
   void applyOwn(String name, Object value) {
      engine.context().getBindings("js").putMember(
         name, WsValueCopier.markForeign(ScriptValueConverter.toGuest(value), engine.context()));
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
    */
   CleanHelper.Result clean() {
      metrics.cleaned();

      try(ScriptTimeoutGuard.Guard guard = engine.guard(CleanHelper.TIMEOUT)) {
         CleanHelper.Result result = cleaner.run();

         if(result.removed() > 0) {
            engine.globalsCleaned();
         }

         return result;
      }
      catch(RuntimeException ex) {
         LOG.debug("Failed to clean a worksheet script context", ex);
         return CleanHelper.Result.FAILED;
      }
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

   boolean isClosed() {
      return closed;
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

      metrics.slotClosed();
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
   private long version; // owner only
   private volatile boolean doomed;
   private volatile boolean closed;
   private volatile long idleSince = System.currentTimeMillis();

   private static final Logger LOG = LoggerFactory.getLogger(Slot.class);
}
