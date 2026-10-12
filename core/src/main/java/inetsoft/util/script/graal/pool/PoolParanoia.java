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

import inetsoft.sree.SreeEnv;
import inetsoft.util.script.graal.ScriptTimeoutGuard;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The opt-in paranoid check of the worksheet context pool (bug #77123): when
 * {@value #PROPERTY} is true, every clean that would keep its context is followed by a
 * read-only pass that compares the global with the clean helper's baseline, and a context
 * that still differs is closed instead of reused. Off by default; when off, a release pays
 * three volatile reads and one {@code System.currentTimeMillis()}, plus, at most every 10 s,
 * one re-read of the property (a {@code Boolean.getBoolean} and a {@code SreeEnv} lookup).
 * The JVM system property of the same name also turns it on, since
 * SreeEnv does not see {@code -D} properties (e.g. a whole test suite run with
 * {@code -Dscript.ws.contextPool.paranoid=true}).
 */
public final class PoolParanoia {
   public static final String PROPERTY = "script.ws.contextPool.paranoid";

   private PoolParanoia() {
   }

   /**
    * @return whether the paranoid check is on; the property is re-read at most every 10 s.
    */
   public static boolean enabled() {
      Boolean forced = PoolParanoia.forced;

      if(forced != null) {
         return forced;
      }

      long now = System.currentTimeMillis();

      if(now >= nextRead) {
         enabled = read();
         nextRead = now + REREAD_MILLIS;
      }

      return enabled;
   }

   /**
    * @return the node-wide number of cleaned contexts the check found off their baseline.
    */
   public static long violations() {
      return VIOLATIONS.get();
   }

   /**
    * @return the node-wide number of checks that could not finish (the check's own timeout
    *         interrupted it), whose contexts were closed without a verdict.
    */
   public static long inconclusive() {
      return INCONCLUSIVE.get();
   }

   /**
    * Compare a cleaned context's global with its baseline, without changing it.
    * <p>
    * A caller's cancel is kept (bug #77568), as in {@link Slot#clean()}: the release runs
    * this check right after the clean has set the caller's interrupt flag again, and Graal
    * turns a set flag into "Thread was interrupted." at the check's first guest safepoint
    * poll, clearing the flag. The flag is cleared while the check runs and set again after;
    * a cancel that lands during the check stops it, is re-asserted, and makes the check
    * inconclusive, unless the check's own timeout interrupted it.
    *
    * @return the mismatching keys, empty if the global is at its baseline; a single
    *         {@link #INCONCLUSIVE_PREFIX} entry if the check was interrupted before it
    *         could tell.
    */
   static List<String> verify(Context context, CleanHelper cleaner) {
      boolean wasInterrupted = Thread.interrupted();

      try {
         return verify0(context, cleaner);
      }
      finally {
         // only ever set the flag here, so a cancel that landed during the check is kept
         if(wasInterrupted) {
            Thread.currentThread().interrupt();
         }
      }
   }

   private static List<String> verify0(Context context, CleanHelper cleaner) {
      VERIFIES.incrementAndGet();
      ScriptTimeoutGuard.Guard guard = GUARD.guard(context, verifyTimeout);
      List<String> keys;

      try(guard) {
         keys = cleaner.verify();
      }
      catch(RuntimeException ex) {
         ScriptTimeoutGuard.keepCancel(ex, guard);
         return List.of((inconclusive(ex) || guard.interruptTimedOut() ?
            INCONCLUSIVE_PREFIX : "<verify failed: ") + ex.getMessage() + ">");
      }

      return guard.interruptTimedOut() ? List.of(INCONCLUSIVE_PREFIX + "timed out>") : keys;
   }

   /**
    * Whether a verify failure only says the check was stopped (its timeout interrupted it,
    * or the context was cancelled), not that the global differs from its baseline.
    */
   static boolean inconclusive(Throwable ex) {
      return ex instanceof PolyglotException pe && (pe.isInterrupted() || pe.isCancelled());
   }

   /**
    * Check a slot whose clean succeeded. Caller holds its lock.
    *
    * @return whether the slot may be kept.
    */
   static boolean accept(Slot slot) {
      Consumer<Slot> hook = beforeVerifyHook;

      if(hook != null) {
         hook.accept(slot);
      }

      List<String> keys = verify(slot.engine().context(), slot.cleaner());

      if(keys.isEmpty()) {
         return true;
      }

      if(keys.size() == 1 && keys.get(0).startsWith(INCONCLUSIVE_PREFIX)) {
         long n = INCONCLUSIVE.incrementAndGet();

         if(n % 100 == 1) {
            LOG.warn("The check of a cleaned worksheet script context did not finish ({} so " +
                     "far); it is closed instead of reused: {}", n, keys);
         }

         return false;
      }

      long n = VIOLATIONS.incrementAndGet();

      if(n % 100 == 1) {
         LOG.error("A cleaned worksheet script context is not at its baseline ({} so far); " +
                   "it is closed instead of reused: {}", n, keys);
      }

      return false;
   }

   private static boolean read() {
      if(Boolean.getBoolean(PROPERTY)) {
         return true;
      }

      try {
         return "true".equalsIgnoreCase(SreeEnv.getProperty(PROPERTY, "false"));
      }
      catch(Exception ex) {
         return false;
      }
   }

   /**
    * Drop the cached property value, so the next {@link #enabled()} reads it. Tests only.
    */
   static void refresh() {
      nextRead = 0L;
   }

   /**
    * Test hook: when non-null, {@link #enabled()} returns it and never reads the property.
    * Null in production.
    */
   static volatile Boolean forced;
   /**
    * Test hook run on a cleaned slot right before the release's check, to plant a defect the
    * clean did not see; null in production.
    */
   static volatile Consumer<Slot> beforeVerifyHook;
   // test hook: the number of verify passes run
   static final AtomicLong VERIFIES = new AtomicLong();
   // test hook: the verify pass's own time bound
   static volatile Duration verifyTimeout = CleanHelper.TIMEOUT;
   static final String INCONCLUSIVE_PREFIX = "<verify inconclusive: ";

   private static final long REREAD_MILLIS = 10_000L;
   private static final AtomicLong VIOLATIONS = new AtomicLong();
   private static final AtomicLong INCONCLUSIVE = new AtomicLong();
   private static final ScriptTimeoutGuard GUARD = new ScriptTimeoutGuard();
   private static volatile boolean enabled;
   private static volatile long nextRead;

   private static final Logger LOG = LoggerFactory.getLogger(PoolParanoia.class);
}
