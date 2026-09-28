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
 * ({@link LiveMap}) (bug #77123). While the outermost exec that made it is open and only on
 * that exec's thread, it reads and writes the script's value itself, so Java-side sorts and
 * out parameters reach the script, as with the pool off. Everywhere else (another thread,
 * another context, after the exec) it is the host copy made when it was created; when the
 * exec completes normally the copy is replaced by one of the script's final state and the
 * guest value is dropped, so the view never outlives its exec bound to the context.
 */
sealed interface LiveView permits LiveList, LiveMap {
   /**
    * Replace the copy with one of the guest value's current state. Owner thread only, inside
    * the completing exec's timeout guard; may run guest code (getters).
    */
   void resnapshot();

   /**
    * Forget the guest value: from now on the view is its copy only.
    */
   void drop();

   /**
    * @return the guest value while this view is live on the calling thread, else null.
    */
   Value liveGuest();

   /**
    * The view a Java method gets for an own guest array (spec §6.8, bug #77123): the frame's
    * view of it when there is one, else a new view over an eager copy (which rejects nested
    * functions and non-plain objects at the call, as before). With no open frame (the exec is
    * detaching its views) it is a plain copy.
    */
   static List<Object> list(Value v) {
      WsExecContext.Frame frame = WsExecContext.openFrame();
      LiveView known = frame == null ? null : frame.get(v);

      if(known instanceof LiveList list) {
         return list;
      }

      CopyList copy = WsValueCopier.copyList(v);
      return frame == null ? copy : frame.register(v, new LiveList(v, copy, frame));
   }

   /**
    * {@link #list} for an own plain object.
    */
   static Map<String, Object> map(Value v) {
      WsExecContext.Frame frame = WsExecContext.openFrame();
      LiveView known = frame == null ? null : frame.get(v);

      if(known instanceof LiveMap map) {
         return map;
      }

      CopyMap copy = WsValueCopier.copyMap(v);
      return frame == null ? copy : frame.register(v, new LiveMap(v, copy, frame));
   }

   /**
    * An element read through a live view: a nested array or plain object is (the frame's)
    * view of it, anything else is converted as a copy element would be.
    */
   static Object element(Value e, WsExecContext.Frame frame) {
      if(e == null || e.isNull()) {
         return null;
      }

      if(!(e.isNumber() || e.isString() || e.isBoolean() || e.isHostObject() ||
         e.isProxyObject()))
      {
         LiveView known = frame.get(e);

         if(known != null) {
            return known;
         }

         if(WsValueCopier.isArray(e)) {
            return frame.register(e, new LiveList(e, WsValueCopier.copyList(e), frame));
         }

         if(WsValueCopier.isPlainObject(e)) {
            return frame.register(e, new LiveMap(e, WsValueCopier.copyMap(e), frame));
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
}
