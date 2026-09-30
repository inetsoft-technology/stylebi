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

import inetsoft.util.script.ScriptSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * One thread's claim on one pooled worksheet env (bug #76960, spec §4.1 SlotClaim, §5.3):
 * {slot, depth}. Acquired and released only in try-with-resources or try/finally, and only
 * on its own thread. A nested acquire reuses the claim and its slot; only the outermost
 * release (1 to 0) cleans and returns the slot. A lazy claim takes a slot only when a script
 * first needs one, so a span around scriptless work costs no context.
 *
 * <p>While a query build is open on the thread ({@link #openBuild()}, G10 piece Q), a claim
 * first opened in it is also held by the build until the build ends, so the scripts of one
 * query build share one context and one clean.
 */
public final class SlotClaim implements ScriptSpan {
   private SlotClaim(SlotPool pool) {
      this.pool = pool;
      this.owner = Thread.currentThread();
   }

   /**
    * Open or re-enter this thread's claim on {@code pool}.
    *
    * @param lazy {@code true} to check a slot out only on the first {@link #slot()} call.
    */
   static SlotClaim acquire(SlotPool pool, boolean lazy) {
      Map<SlotPool, SlotClaim> claims = CLAIMS.get();

      if(claims == null) {
         if(!everClaimed) {
            everClaimed = true;
         }

         claims = new IdentityHashMap<>();
         CLAIMS.set(claims);
      }

      SlotClaim claim = claims.get(pool);

      if(claim == null) {
         claim = new SlotClaim(pool);
         claims.put(pool, claim);
         Build build = BUILD.get();

         if(build != null) {
            // the query build open on this thread holds the claim until it ends (G10 piece Q)
            claim.depth++;
            claim.build = true;
            build.claims.add(claim);
         }
      }

      claim.depth++;

      // a script, batch or span at the build's top level: on main, its own claim and clean
      if(claim.build && claim.depth == 2) {
         claim.units++;
      }

      if(!lazy) {
         try {
            claim.slot();
         }
         catch(RuntimeException | Error ex) {
            claim.close();
            throw ex;
         }
      }

      return claim;
   }

   /**
    * @return this thread's open claim on {@code pool}, or {@code null}.
    */
   static SlotClaim current(SlotPool pool) {
      Map<SlotPool, SlotClaim> claims = CLAIMS.get();
      return claims == null ? null : claims.get(pool);
   }

   /**
    * @return the claimed slot, checked out now if this claim has none yet (never waits).
    */
   Slot slot() {
      if(slot == null) {
         slot = pool.checkout();
         slot.metrics().checkedOut();
      }

      return slot;
   }

   /**
    * @param state the env's variables.
    * @param sql   the env's SQL mode.
    *
    * @return the slot for one script call (an exec, a function check, a key listing) at this
    * claim's current depth, checked out now if this claim has none yet (never waits). A query
    * build claim (G10 piece Q) that already holds a slot first:
    * <ul>
    *    <li>leaves it for a fresh one if an interrupt could not stop an earlier script on it
    *    and no span of the build is open, i.e. the call is at the build's top level
    *    (amendment 1). Inside a span (a formula table batch, a condition filter), the span's
    *    scripts go on on it, as they do on a batch claim, and the build's release closes it;
    *    </li>
    *    <li>brings it up to the env's variables, which another thread may have set after the
    *    build's first script (amendment 2). It does so at every script call of the build,
    *    inside a span too: a formula table batch or condition filter run in a build can see
    *    a variable another thread set between two of its rows, as with the pool off, where
    *    a batch outside a build sees the variables of its checkout for the whole batch.
    *    Owner only, never waits; one version compare when nothing changed.</li>
    * </ul>
    * A retire of the env during the build dooms the slot but does not swap it: the build ends
    * on the context it started on, as a batch claim does, and its release closes it
    * (amendment 4).
    */
   Slot scriptSlot(EnvState state, boolean sql) {
      Slot held = slot;

      if(held == null || !build) {
         return slot();
      }

      // depth 2: the build's own hold and this call's
      if(held.isInterruptLost() && depth == 2) {
         slot = null;
         held.metrics().swapped();
         allowBuildDeletes(held);
         units = 1;
         pool.release(held);
         return slot();
      }

      held.resync(state.snapshot(), sql);
      return held;
   }

   /**
    * Open a query build on the calling thread, or re-enter the one open (G10 piece Q): every
    * pooled worksheet script claim first opened on this thread until the outermost build
    * ends is held until then, so all the scripts of the build (its formula columns, condition
    * values, compiles, calc fields) run on one context, which is cleaned once, at the end.
    * Nothing is claimed here: a claim still takes its context only when its first script
    * needs one, so a build that runs no script costs no context. Never waits. Close it on the
    * same thread in a try-with-resources.
    */
   public static Build openBuild() {
      Build build = BUILD.get();

      if(build == null) {
         if(!everClaimed) {
            everClaimed = true;
         }

         build = new Build();
         BUILD.set(build);
      }

      build.depth++;
      return build;
   }

   /**
    * A query build open on one thread (G10 piece Q); see {@link #openBuild()}.
    */
   public static final class Build implements AutoCloseable {
      private Build() {
         this.owner = Thread.currentThread();
      }

      /**
       * End this level of the build; the outermost close releases the claims the build held,
       * each cleaning and returning its context. A RuntimeException of a release is logged;
       * an Error is rethrown once every claim was released.
       */
      @Override
      public void close() {
         if(Thread.currentThread() != owner) {
            throw new IllegalStateException(
               "A worksheet query build must be closed by the thread that opened it");
         }

         if(depth <= 0 || BUILD.get() != this) {
            LOG.warn("Unbalanced end of a worksheet query build");
            return;
         }

         if(--depth > 0) {
            return;
         }

         BUILD.remove();
         Throwable error = null;

         // every claim is released, even if one release throws an Error: a claim left
         // locked would hold its context until the thread's releaseLeaked
         for(SlotClaim claim : claims) {
            try {
               claim.endBuild();
            }
            catch(Throwable ex) {
               if(error == null) {
                  error = ex;
               }
               else {
                  error.addSuppressed(ex);
               }
            }
         }

         claims.clear();

         if(error instanceof Error err) {
            throw err;
         }
         else if(error != null) {
            // endBuild logs a RuntimeException and never throws one
            throw new IllegalStateException(error);
         }
      }

      private final Thread owner;
      private final List<SlotClaim> claims = new ArrayList<>();
      private int depth;
   }

   // the build's hold on this claim ends
   private void endBuild() {
      if(!build) {
         return;
      }

      build = false;

      if(depth == 1 && slot != null) {
         allowBuildDeletes(slot);
      }

      try {
         close();
      }
      catch(RuntimeException ex) {
         // runs in the build's close: never mask the build's own throw or skip other claims
         LOG.warn("Failed to release the worksheet script claim of a query build", ex);
      }
   }

   /*
    * A script, batch or span of a query build ended (this claim's 2 to 1 release) and the
    * build's slot is now the home of a formula table's script objects (G10 piece Q, round 2):
    * give the slot back now, cleaned and idle as the home, exactly where it is released
    * without a build (the end of the batch, or of the span or script the batch ran in). A
    * batch of the table on another thread (an aggregate's or distinct's on-demand worker,
    * another assembly sharing the cached lens) then pulls the objects from it instead of
    * reading them as undefined: A1 as without a build, not widened to the whole build. The
    * build keeps this claim; its next script checks out a context again, the table's next
    * batch its home if still idle. Only builds that run batches of tables with object vars
    * pay this, one clean per such unit, as without a build. hasTenants is a leaf monitor.
    */
   private void giveBackHome() {
      Slot held = slot;
      slot = null;
      held.metrics().buildYielded();
      allowBuildDeletes(held);
      units = 0;
      pool.release(held);
   }

   /**
    * Let the clean at the release of a build claim delete up to {@link
    * PoolConfig#MAX_FOREIGN_DELETES} implicit globals per top-level script of the build
    * (amendment 3, round 2): without a build, each of them had its own claim and clean
    * under that cap, so a build whose scripts leave a few globals each would otherwise
    * close its context at every build end. Bounded by {@link #MAX_BUILD_DELETES}, far under
    * the clean's time guard; a single script above the cap still closes the context.
    */
   private void allowBuildDeletes(Slot held) {
      long max = (long) PoolConfig.MAX_FOREIGN_DELETES * Math.max(1, units);
      held.allowDeletes((int) Math.min(MAX_BUILD_DELETES, max));
   }

   /**
    * The most implicit globals the clean of a build claim deletes before it closes the
    * context instead: about 50 ms at 48 us a delete (spec §14.1).
    */
   static final int MAX_BUILD_DELETES = 1024;

   /**
    * @return the claimed slot, or {@code null} if none was checked out yet.
    */
   Slot peekSlot() {
      return slot;
   }

   int depth() {
      return depth;
   }

   SlotPool pool() {
      return pool;
   }

   /**
    * Make this thread's next checkouts prefer a home of {@code tenant} (Testing #77123, B1
    * residual part 2): set by a formula table around its batch, so a lazy claim that takes its
    * slot in that batch takes the context the table's objects live on.
    *
    * @return the previous preference, which {@link #restoreHomeHint} restores.
    */
   static Object setHomeHint(SlotTenant tenant) {
      Object previous = HOME_HINT.get();

      if(tenant != previous) {
         if(tenant == null) {
            HOME_HINT.remove();
         }
         else {
            HOME_HINT.set(tenant);
         }
      }

      return previous;
   }

   static void restoreHomeHint(Object previous) {
      if(previous instanceof SlotTenant tenant) {
         HOME_HINT.set(tenant);
      }
      else {
         HOME_HINT.remove();
      }
   }

   /**
    * @return the tenant whose home this thread's checkout prefers, or {@code null}.
    */
   static SlotTenant homeHint() {
      return HOME_HINT.get() instanceof SlotTenant tenant ? tenant : null;
   }

   @Override
   public int batchRows() {
      return pool.config().batchRows();
   }

   @Override
   public int maxBatchRows() {
      return pool.config().maxBatchRows();
   }

   @Override
   public void close() {
      if(Thread.currentThread() != owner) {
         throw new IllegalStateException(
            "A worksheet script claim must be closed by the thread that opened it");
      }

      if(depth <= 0) {
         LOG.warn("Unbalanced release of a worksheet script claim");
         return;
      }

      if(--depth > 0) {
         // back at the build's own hold, on a formula table's home
         if(depth == 1 && build && slot != null && slot.hasTenants()) {
            giveBackHome();
         }

         return;
      }

      Map<SlotPool, SlotClaim> claims = CLAIMS.get();

      if(claims != null) {
         claims.remove(pool);

         if(claims.isEmpty()) {
            CLAIMS.remove();
         }
      }

      Slot held = slot;
      slot = null;

      if(held != null) {
         pool.release(held);
      }
   }

   /**
    * @return the number of claims open on the calling thread.
    */
   public static int openClaims() {
      Map<SlotPool, SlotClaim> claims = CLAIMS.get();
      return claims == null ? 0 : claims.size();
   }

   /**
    * Release every claim left open on the calling thread (N5). A claim is balanced by
    * construction; one left open by a bug would keep its context locked forever, and a later
    * task on a reused worker thread would re-enter it and never clean it. Called when a
    * GroupedThread ends and after every ThreadPool task. Never throws.
    */
   public static void releaseLeaked(String where) {
      // pool off: no claim was ever opened in this JVM, so skip even the ThreadLocal.get(),
      // which would create an entry on this thread
      if(!everClaimed) {
         return;
      }

      Map<SlotPool, SlotClaim> claims = CLAIMS.get();

      Build build = BUILD.get();

      if(build != null) {
         LOG.warn("A worksheet query build was left open at the end of {}; ending it", where);
         BUILD.remove();
         build.depth = 0;
         build.claims.clear();
      }

      if(claims == null) {
         return;
      }

      CLAIMS.remove();

      for(SlotClaim claim : new ArrayList<>(claims.values())) {
         LOG.warn("A worksheet script claim was left open at the end of {} (depth {}); " +
                  "releasing it", where, claim.depth);
         PoolMetrics.leakedClaim();
         claim.depth = 0;
         claim.build = false;
         Slot held = claim.slot;
         claim.slot = null;

         if(held != null) {
            try {
               claim.pool.release(held);
            }
            catch(RuntimeException ex) {
               // runs in a thread's finally: never mask its own throw or skip other claims
               LOG.warn("Failed to release a leaked worksheet script claim", ex);
            }
         }
      }
   }

   private static final ThreadLocal<Map<SlotPool, SlotClaim>> CLAIMS = new ThreadLocal<>();
   private static final ThreadLocal<Object> HOME_HINT = new ThreadLocal<>();
   private static final ThreadLocal<Build> BUILD = new ThreadLocal<>();
   // set on the first claim in this JVM, by the claiming thread before its CLAIMS entry
   private static volatile boolean everClaimed;

   private final SlotPool pool;
   private final Thread owner;
   private int depth;
   private Slot slot;
   // set while a query build holds this claim (G10 piece Q), which then counts one depth
   private boolean build;
   // the build's top-level scripts, batches and spans on this claim (amendment 3)
   private int units;

   private static final Logger LOG = LoggerFactory.getLogger(SlotClaim.class);
}
