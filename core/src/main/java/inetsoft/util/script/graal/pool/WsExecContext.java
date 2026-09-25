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

import org.graalvm.polyglot.Context;

import java.util.function.Supplier;

/**
 * The pooled worksheet context executing on this thread, if any (bug #76960). Set around each
 * exec of a pooled context; restored on exit, so a nested exec of another engine is correct.
 */
public final class WsExecContext {
   private WsExecContext() {
   }

   static Slot enter(Slot slot) {
      if(!everEntered) {
         everEntered = true;
      }

      Slot previous = CURRENT.get();
      CURRENT.set(slot);
      return previous;
   }

   static void exit(Slot previous) {
      if(previous == null) {
         CURRENT.remove();
      }
      else {
         CURRENT.set(previous);
      }
   }

   /**
    * Clear the mark for the exec of an engine that is not pooled, nested in a pooled exec on
    * this thread. With no mark set (always, with the pool off) this changes nothing.
    *
    * @return the token {@link #resume} restores.
    */
   public static Object suspend() {
      // pool off: nothing ever entered, so skip even the ThreadLocal.get(), which would
      // create an entry on every script thread
      if(!everEntered) {
         return null;
      }

      Slot previous = CURRENT.get();

      if(previous != null) {
         CURRENT.remove();
      }

      return previous;
   }

   /**
    * Restore the mark {@link #suspend} cleared.
    */
   public static void resume(Object token) {
      if(token instanceof Slot slot) {
         CURRENT.set(slot);
      }
   }

   /**
    * @return the Context of the pooled context executing on this thread, or {@code null}.
    */
   static Context currentContext() {
      if(!everEntered) {
         return null;
      }

      Slot slot = CURRENT.get();
      return slot == null ? null : slot.engine().context();
   }

   /**
    * Per-context state of a host object (spec §6.4), for the pooled context executing on this
    * thread; only that context's owner ever uses it.
    *
    * @return the attachment, or {@code null} when no pooled context is executing here.
    */
   public static <T> T currentSlotAttachment(Object key, Supplier<T> factory) {
      if(!everEntered) {
         return null;
      }

      Slot slot = CURRENT.get();
      return slot == null ? null : slot.attachment(key, factory);
   }

   private static final ThreadLocal<Slot> CURRENT = new ThreadLocal<>();
   // set on the first pooled exec in this JVM, by its thread before its CURRENT entry
   private static volatile boolean everEntered;
}
