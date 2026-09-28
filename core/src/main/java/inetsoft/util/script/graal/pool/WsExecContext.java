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
import org.graalvm.polyglot.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The pooled worksheet context executing on this thread, if any (bug #76960). Set around each
 * exec of a pooled context; restored on exit, so a nested exec of another engine is correct.
 */
public final class WsExecContext {
   private WsExecContext() {
   }

   /**
    * @param script the script the exec runs, named by the copy-mutation warning (bug #77123).
    */
   static Slot enter(Slot slot, Object script) {
      if(!everEntered) {
         everEntered = true;
      }

      Slot previous = CURRENT.get();

      // the outermost exec of this slot on this thread opens the origin its argument copies
      // record; set before the mark, so a failed allocation leaves neither (bug #77123)
      if(previous != slot) {
         ORIGIN.set(new Origin(script, ORIGIN.get()));
      }

      CURRENT.set(slot);
      return previous;
   }

   static void exit(Slot previous) {
      try {
         // the exec that opened an origin closes it (enter opened one iff previous != slot)
         if(CURRENT.get() != previous) {
            Origin origin = ORIGIN.get();

            if(origin != null) {
               Origin outer = origin.close();

               if(outer == null) {
                  ORIGIN.remove();
               }
               else {
                  ORIGIN.set(outer);
               }
            }
         }
      }
      finally {
         if(previous == null) {
            CURRENT.remove();
         }
         else {
            CURRENT.set(previous);
         }
      }
   }

   /**
    * @return the origin of the outermost exec of the pooled context executing on this thread,
    *         recorded by the host copies of its Java arguments, or {@code null}.
    */
   static Origin currentOrigin() {
      return everEntered && CURRENT.get() != null ? ORIGIN.get() : null;
   }

   /**
    * Java changed a host copy of a script value passed to it (bug #77123, C1): if it did so on
    * the script's thread while the exec that passed it runs, the script would have seen the
    * change with the pool off but does not see it here, so count it and warn once per script.
    *
    * @return whether the copy need not report again: it was counted, or its exec ended.
    */
   static boolean copyMutated(Origin origin) {
      if(!origin.open) {
         return true;
      }

      if(origin.thread != Thread.currentThread()) {
         return false;
      }

      PoolMetrics.copyMutated();
      Object script = origin.script;
      String text = script instanceof Source source ? String.valueOf(source.getCharacters())
         : String.valueOf(script);

      // once per script text; past the cap only the counter records further scripts
      if(WARNED.size() < MAX_WARNED && WARNED.add(text.hashCode())) {
         if(LOG.isWarnEnabled()) {
            LOG.warn(COPY_MUTATION_MESSAGE + " Script: {}",
                     text.length() > 200 ? text.substring(0, 200) + "..." : text);
         }
      }

      return true;
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
   public static Context currentContext() {
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

   /**
    * The outermost exec of one slot on one thread, as the host copies of its Java arguments
    * record it (bug #77123). It holds nothing once closed, so a copy Java keeps pins no
    * script, thread or outer exec.
    */
   static final class Origin {
      Origin(Object script, Origin outer) {
         this.script = script;
         this.outer = outer;
      }

      private Origin close() {
         Origin o = outer;
         open = false;
         outer = null;
         script = null;
         thread = null;
         return o;
      }

      private volatile boolean open = true;
      private volatile Thread thread = Thread.currentThread();
      private volatile Object script;
      private Origin outer;
   }

   static final String COPY_MUTATION_MESSAGE =
      "A Java method changed a worksheet script array or object passed to it (for example " +
      "java.util.Collections.sort or an out parameter) while the script ran. With " +
      "script.ws.contextPool=true, Java gets a copy, so the script does not see the change. " +
      "Return the changed value from the Java method and assign it in the script instead. " +
      "This is logged once per script.";

   private static final ThreadLocal<Slot> CURRENT = new ThreadLocal<>();
   private static final ThreadLocal<Origin> ORIGIN = new ThreadLocal<>();
   private static final Logger LOG = LoggerFactory.getLogger(WsExecContext.class);
   // hashes of the scripts warned about, bounded
   private static final Set<Integer> WARNED = ConcurrentHashMap.newKeySet();
   static final int MAX_WARNED = 512;
   // set on the first pooled exec in this JVM, by its thread before its CURRENT entry
   private static volatile boolean everEntered;
}
