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

import org.graalvm.polyglot.Value;

import java.util.List;
import java.util.Map;

/**
 * A Java-argument view of a worksheet script's own array ({@link LiveList}) or plain object
 * ({@link LiveMap}) (bug #77123).
 * <p>
 * Invariants:
 * <ol>
 *   <li><b>Live</b> only on the owner thread, while the outermost exec of its slot that made
 *       it is open and that slot is the one executing: there it reads and writes the script's
 *       value itself, so Java sorts and out parameters reach the script, as with the pool
 *       off.</li>
 *   <li>Everywhere else it is its <b>copy</b>, which is today's copy semantics or fresher: it
 *       is re-made each time the script passes the value to Java again, and every write Java
 *       makes through a live view is applied to the copy as well (a nested view shares its
 *       copy with its parent's copy), so another thread never sees a state older than the
 *       latest pass plus Java's own writes. While the exec is open a write in copy mode, or a
 *       read of a copy that could not follow a live write, raises an explicit error instead
 *       of losing the write or reading stale data.</li>
 *   <li>When the exec completes normally the copy is replaced by one of the script's final
 *       state; on any other exit it is kept. Either way the guest value and the frame are
 *       dropped, so the view never outlives its exec bound to the context.</li>
 *   <li>The frame holds its views <b>weakly</b>: a view Java did not keep is collected and
 *       needs no end-of-exec copy, so the frame costs memory and time only for the views Java
 *       (or the script) still reaches.</li>
 * </ol>
 */
sealed interface LiveView permits LiveList, LiveMap {
   /**
    * Replace the copy with one of the guest value's current state. Owner thread only, inside
    * the completing exec's timeout guard; may run guest code (getters).
    */
   void resnapshot();

   /**
    * The script passed the guest value to Java again: re-make the copy from its current state
    * (as today's per-pass copy), which also rejects a function added since. Owner thread only,
    * while live.
    */
   void refresh();

   /**
    * Take {@code copied}, the parent copy's element this view was just read as, as the copy,
    * so a write through this view is seen through the parent's copy too. Owner thread only.
    */
   void rebind(Object copied);

   /**
    * @return the current copy.
    */
   Object currentCopy();

   /**
    * Forget the guest value and the frame: from now on the view is its copy only.
    */
   void drop();

   /**
    * @return the guest value while this view is live on the calling thread, else null.
    */
   Value liveGuest();

   /**
    * The view a Java method gets for an own guest array (spec §6.8, bug #77123): the frame's
    * view of it, its copy re-made, when there is one, else a new view over an eager copy
    * (which rejects nested functions and non-plain objects at the call, as before). With no
    * open frame (the exec is detaching its views) it is a plain copy.
    */
   static List<Object> list(Value v) {
      WsExecContext.Frame frame = WsExecContext.openFrame();
      LiveView known = frame == null ? null : frame.get(v);

      if(known instanceof LiveList list) {
         list.refresh();
         return list;
      }

      CopyList copy = WsValueCopier.copyList(v);

      if(frame == null) {
         return copy;
      }

      LiveList view = new LiveList(v, copy, frame);
      return frame.register(v, view) ? view : copy;
   }

   /**
    * {@link #list} for an own plain object.
    */
   static Map<String, Object> map(Value v) {
      WsExecContext.Frame frame = WsExecContext.openFrame();
      LiveView known = frame == null ? null : frame.get(v);

      if(known instanceof LiveMap map) {
         map.refresh();
         return map;
      }

      CopyMap copy = WsValueCopier.copyMap(v);

      if(frame == null) {
         return copy;
      }

      LiveMap view = new LiveMap(v, copy, frame);
      return frame.register(v, view) ? view : copy;
   }

   /**
    * An element read through a live view: a nested array or plain object is (the frame's)
    * view of it, anything else is converted as a copy element would be.
    *
    * @param copied the parent copy's element at the same place, or null: a nested view takes
    *               it as its copy (no second copy), so the two copies stay one.
    */
   static Object element(Value e, WsExecContext.Frame frame, Object copied) {
      if(e == null || e.isNull()) {
         return null;
      }

      // a primitive, as interopElement converts it, without its object checks
      if(e.isNumber() || e.isString() || e.isBoolean()) {
         return e.as(Object.class);
      }

      if(!(e.isHostObject() || e.isProxyObject())) {
         LiveView known = frame.get(e);

         if(known != null) {
            known.rebind(copied);
            return known;
         }

         if(WsValueCopier.isArray(e)) {
            CopyList copy = copied instanceof CopyList list ? list : WsValueCopier.copyList(e);
            LiveList view = new LiveList(e, copy, frame);
            return frame.register(e, view) ? view : copy;
         }

         if(WsValueCopier.isPlainObject(e)) {
            CopyMap copy = copied instanceof CopyMap map ? map : WsValueCopier.copyMap(e);
            LiveMap view = new LiveMap(e, copy, frame);
            return frame.register(e, view) ? view : copy;
         }
      }

      return WsValueCopier.interopElement(e);
   }

   /**
    * A value a live view writes into its guest value: a view that is live here is written as
    * the guest value it views, so identity survives, e.g. a Java sort of an array of objects.
    */
   static Object toGuest(Object x) {
      if(x instanceof LiveView view) {
         Value guest = view.liveGuest();
         return guest != null ? guest : x;
      }

      return x;
   }

   /**
    * {@link #toGuest(Object)} for a value a script writes through a view read back into it.
    */
   static Object toGuest(Value v) {
      if(v != null && v.isProxyObject()) {
         Object proxy = v.asProxyObject();

         if(proxy instanceof LiveView view) {
            Value guest = view.liveGuest();
            return guest != null ? guest : v;
         }
      }

      return v;
   }

   /**
    * The copy-side twin of a value Java writes through a live view: a view is its copy (so a
    * Java sort of objects moves the nested copies, as today), anything else is itself.
    */
   static Object toCopy(Object x) {
      return x instanceof LiveView view ? view.currentCopy() : x;
   }

   /**
    * {@link #toCopy(Object)} for a value a script writes through a view read back into it.
    */
   static Object toCopy(Value v) {
      if(v != null && v.isProxyObject() && v.asProxyObject() instanceof LiveView view) {
         return view.currentCopy();
      }

      return WsValueCopier.interopElement(v);
   }

   /**
    * The error for a write in copy mode, or a read of a copy that fell behind, while the exec
    * that made the view is still open: explicit, as the pool-off live view's, never silent.
    */
   static IllegalStateException busy() {
      return new IllegalStateException(
         "Multi threaded access: a worksheet script value passed to Java is in use by its " +
         "running script; change it only on the script's thread while the script runs, or " +
         "after the run ends");
   }
}
