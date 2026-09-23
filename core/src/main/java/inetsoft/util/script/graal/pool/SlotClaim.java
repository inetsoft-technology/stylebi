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
      }

      claim.depth++;

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
      }

      return slot;
   }

   /**
    * @return the claimed slot, or {@code null} if none was checked out yet.
    */
   Slot peekSlot() {
      return slot;
   }

   int depth() {
      return depth;
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

      if(claims == null) {
         return;
      }

      CLAIMS.remove();

      for(SlotClaim claim : new ArrayList<>(claims.values())) {
         LOG.warn("A worksheet script claim was left open at the end of {} (depth {}); " +
                  "releasing it", where, claim.depth);
         claim.depth = 0;
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
   // set on the first claim in this JVM, by the claiming thread before its CLAIMS entry
   private static volatile boolean everClaimed;

   private final SlotPool pool;
   private final Thread owner;
   private int depth;
   private Slot slot;

   private static final Logger LOG = LoggerFactory.getLogger(SlotClaim.class);
}
