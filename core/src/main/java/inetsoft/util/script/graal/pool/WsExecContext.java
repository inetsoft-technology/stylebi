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
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
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

      // the outermost exec of this slot on this thread opens the frame its live views are
      // bound to (bug #77123); pushed before the mark, so a failed push leaves no mark
      if(previous != slot) {
         FRAMES.get().push(new Frame(slot));
      }

      CURRENT.set(slot);
      return previous;
   }

   /**
    * The exec entered with {@code previous} completed normally, still inside its timeout
    * guard (bug #77123): if it is the outermost exec of {@code slot} on this thread, close its
    * frame and re-snapshot each live view from its guest value, so a Java object that kept one
    * sees the script's final state. The re-snapshot reads the guest (a getter may run), which
    * is why it runs here and not in {@link #exit}: a timeout still interrupts it. A view whose
    * re-snapshot fails keeps its call-time copy; an interrupt, cancel or Error is rethrown
    * after every view is dropped, so the exec fails visibly.
    */
   static void complete(Slot previous, Slot slot) {
      if(previous == slot || CURRENT.get() != slot) {
         return;
      }

      Frame frame = FRAMES.get().peek();

      if(frame == null || frame.slot != slot || !frame.open) {
         return;
      }

      // from here on every view of the frame is copy-only, and a view created by guest code
      // that runs during the re-snapshot is born detached (never registered)
      frame.open = false;
      Throwable fatal = null;

      for(LiveView view : frame.views()) {
         if(fatal != null) {
            drop(view);
            continue;
         }

         Throwable failure = null;

         try {
            view.resnapshot();
         }
         catch(Throwable ex) {
            failure = ex;
         }
         finally {
            drop(view);
         }

         if(failure != null) {
            if(isFatal(failure)) {
               fatal = failure;
            }
            else {
               LOG.debug("A Java-held copy of a worksheet script value keeps its call-time " +
                            "state: its final state could not be read", failure);
            }
         }
      }

      frame.clear();

      if(fatal instanceof RuntimeException ex) {
         throw ex;
      }

      if(fatal instanceof Error err) {
         throw err;
      }
   }

   static void exit(Slot previous) {
      try {
         Slot current = CURRENT.get();

         // the exec that opened a frame: drop every view still bound to it without running
         // guest code (an exec that ended by exception, timeout or cancel never completed),
         // so each keeps its call-time copy and no guest value outlives the exec (bug #77123)
         if(current != null && current != previous) {
            ArrayDeque<Frame> frames = FRAMES.get();
            Frame frame = frames.peek();

            if(frame != null && frame.slot == current) {
               frames.pop();
               frame.open = false;

               for(LiveView view : frame.views()) {
                  drop(view);
               }

               frame.clear();
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
    * @return the open frame of the pooled context executing on this thread, the frame new
    *         live views are bound to; {@code null} when there is none (no pooled exec, or its
    *         frame is being detached), so a view made now is a plain copy.
    */
   static Frame openFrame() {
      Slot slot = CURRENT.get();

      if(slot == null) {
         return null;
      }

      Frame frame = FRAMES.get().peek();
      return frame != null && frame.open && frame.slot == slot ? frame : null;
   }

   /**
    * @return whether {@code frame} is open and its slot is executing on this, its own, thread:
    *         the only place its live views touch their guest values.
    */
   static boolean isLive(Frame frame) {
      return frame.thread == Thread.currentThread() && frame.open && CURRENT.get() == frame.slot;
   }

   private static void drop(LiveView view) {
      try {
         view.drop();
      }
      catch(Throwable ex) {
         // a field write; never let it stop the other views from being dropped
      }
   }

   private static boolean isFatal(Throwable ex) {
      return ex instanceof Error || ex instanceof PolyglotException pe &&
         (pe.isCancelled() || pe.isInterrupted() || pe.isResourceExhausted() || pe.isExit());
   }

   /**
    * The live views of the outermost exec of one slot on one thread (bug #77123), one per
    * guest value (keyed by Value identity), so repeated passes or reads of the same array or
    * object reuse one view. Used by its thread only.
    */
   static final class Frame {
      Frame(Slot slot) {
         this.slot = slot;
      }

      LiveView get(Value guest) {
         return views == null ? null : views.get(guest);
      }

      <T extends LiveView> T register(Value guest, T view) {
         if(open) {
            if(views == null) {
               views = new HashMap<>();
            }

            views.put(guest, view);
         }

         return view;
      }

      int size() {
         return views == null ? 0 : views.size();
      }

      private Collection<LiveView> views() {
         return views == null ? List.of() : new ArrayList<>(views.values());
      }

      private void clear() {
         views = null;
      }

      final Slot slot;
      final Thread thread = Thread.currentThread();
      boolean open = true;
      private Map<Value, LiveView> views;
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
    * The innermost open frame of this thread, for tests.
    */
   static Frame currentFrame() {
      return FRAMES.get().peek();
   }

   private static final ThreadLocal<Slot> CURRENT = new ThreadLocal<>();
   private static final ThreadLocal<ArrayDeque<Frame>> FRAMES =
      ThreadLocal.withInitial(ArrayDeque::new);
   private static final Logger LOG = LoggerFactory.getLogger(WsExecContext.class);
   // set on the first pooled exec in this JVM, by its thread before its CURRENT entry
   private static volatile boolean everEntered;
}
