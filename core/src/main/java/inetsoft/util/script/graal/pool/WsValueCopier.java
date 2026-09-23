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

import inetsoft.util.script.ScriptException;
import inetsoft.util.script.graal.ScriptValueConverter;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The worksheet script to host boundary of pooled contexts (bug #76960, spec §6.8, §14.4,
 * §14.11, §14.12). Everything here applies only to values of the pooled context executing on
 * this thread ({@link WsExecContext}); anywhere else every method is the identity, so viewsheet
 * and report scripts, and the pool-off path, keep main's behaviour.
 *
 * <p>A value from another context (e.g. a viewsheet object stored in a parameter) is foreign:
 * it stays a live reference, as on main, and is only counted. It is never copied through its
 * owner, which could make the owner's own exec fail (proto check 5).
 */
public final class WsValueCopier {
   private WsValueCopier() {
   }

   /**
    * ScriptValueConverter.toHost's fallthrough (spec §14.12 A1, A3): a plain object of the
    * executing pooled context becomes a {@link CopyMap} with main's element shapes. A function
    * or non-plain object passes through for transient uses (A2); a store or result of it is
    * rejected by {@link #checkStorable}.
    */
   public static Object detach(Value v) {
      if(!isOwnGuest(v)) {
         return v;
      }

      if(!isPlainObject(v)) {
         return v;
      }

      int[] depth = DEPTH.get();

      if(++depth[0] > MAX_DEPTH) {
         depth[0]--;
         throw new ScriptException(DEPTH_MESSAGE);
      }

      try {
         CopyMap copy = new CopyMap(false);

         for(String key : v.getMemberKeys()) {
            copy.put(key, ScriptValueConverter.toHost(v.getMember(key)));
         }

         return copy;
      }
      finally {
         depth[0]--;
      }
   }

   /**
    * Reject a converted value that host code would keep but that cannot be kept apart from
    * its context: a function or non-plain object of the executing pooled context, also inside
    * an array or map (spec §14.12 A2, A4). Returns the value unchanged otherwise.
    */
   public static Object checkStorable(Object host) {
      Context context = WsExecContext.currentContext();

      if(context != null) {
         check(host, context, 0);
      }

      return host;
   }

   /**
    * Mark a guest-bound value of another context while its owner is still known: once Graal
    * re-views it inside the slot it reports the slot's context (spec §14.11, lead decision (b)).
    * Used where a host value enters a pooled context outside an exec (init, put, replay).
    *
    * @param guest   a value about to be handed to the guest (the result of toGuest).
    * @param context the pooled context it is handed to.
    *
    * @return a {@link ForeignRef} for an object of another context, else {@code guest}.
    */
   static Object markForeign(Object guest, Context context) {
      if(context == null || !(guest instanceof Value v)) {
         return guest;
      }

      try {
         if(isForeignCandidate(v) && !isOwn(v, context)) {
            FOREIGN.incrementAndGet();
            return ForeignRef.of(v);
         }
      }
      catch(RuntimeException ex) {
         // e.g. a value of a closed context: hand it over as main does, and let it fail there
      }

      return guest;
   }

   /**
    * {@link #markForeign(Object, Context)} for each value of an init map.
    */
   static Map<String, Object> markForeign(Map<String, Object> vars, Context context) {
      if(vars == null || context == null) {
         return vars;
      }

      Map<String, Object> marked = null;

      for(Map.Entry<String, Object> e : vars.entrySet()) {
         Object value = e.getValue();
         Object guest = markForeign(value, context);

         if(guest != value && marked == null) {
            marked = new LinkedHashMap<>(vars);
         }

         if(marked != null) {
            marked.put(e.getKey(), guest);
         }
      }

      return marked == null ? vars : marked;
   }

   /**
    * ScriptValueConverter.toGuest's hook for a raw {@link Value}: inside a pooled exec a value
    * of another context is marked foreign; anywhere else (always with the pool off) it is
    * returned unchanged.
    */
   public static Object markForeign(Value v) {
      Context context = WsExecContext.currentContext();
      return context == null ? v : markForeign(v, context);
   }

   /**
    * ScriptValueConverter.toHost of a foreign reference, with main's shapes (spec §14.12 A1):
    * a foreign array becomes main's {@code Object[]}, walked at main's moment (toHost time), with
    * main's risk; a foreign object or function is its owner's live value, as main's raw Value.
    * Nothing is copied at the entry point (spec §14.11).
    */
   public static Object foreign(ForeignRef ref) {
      Value owner = ref.value();
      return owner.hasArrayElements() ? ScriptValueConverter.toHost(owner) : owner;
   }

   /**
    * A value that is marked foreign when it comes from another context: an object, array or
    * function. Primitives, dates, host objects and proxies pass as they are.
    */
   static boolean isForeignCandidate(Value v) {
      return !(v.isNull() || v.isHostObject() || v.isProxyObject() || v.isString() ||
               v.isNumber() || v.isBoolean() || v.isDate() || v.isTime() || v.isTimeZone() ||
               v.isDuration() || v.isInstant()) &&
         (v.hasMembers() || v.hasArrayElements() || v.canExecute());
   }

   static boolean isForeignRef(Value v) {
      return v.isProxyObject() && v.asProxyObject() instanceof ForeignRef;
   }

   /**
    * A foreign reference passed to a Java method: its owner's live value in the target form,
    * as main's re-viewed value gave (a live Map/List/function view).
    */
   static <T> T foreignAs(Value v, Class<T> type) {
      return ((ForeignRef) v.asProxyObject()).value().as(type);
   }

   /**
    * @return the number of values marked foreign on entry to a pooled context (spec §14.11
    *         metric); a value is counted once, where it is marked, not at each later crossing.
    */
   public static long foreignValueCount() {
      return FOREIGN.get();
   }

   // --- HostAccess mappings (P-interop, spec §14.4 as amended by §14.12) ---

   static boolean isDate(Value v) {
      return isOwnGuest(v) && v.isDate() && v.isInstant();
   }

   static boolean isFunction(Value v) {
      return isOwnGuest(v) && v.canExecute();
   }

   static boolean isArray(Value v) {
      return isOwnGuest(v) && !v.canExecute() && v.hasArrayElements();
   }

   static boolean isPlainObject(Value v) {
      return isOwnGuest(v) && !v.canExecute() && !v.hasArrayElements() && !v.isDate() &&
         v.hasMembers() && isObjectMeta(v);
   }

   static boolean isNonPlainObject(Value v) {
      return isOwnGuest(v) && !v.canExecute() && !v.hasArrayElements() && !v.isDate() &&
         !isPlainObject(v);
   }

   static Date toDate(Value v) {
      return new Date(v.asInstant().toEpochMilli());
   }

   static CopyMap copyMap(Value v) {
      CopyMap copy = new CopyMap(true);

      for(String key : v.getMemberKeys()) {
         copy.put(key, interopElement(v.getMember(key)));
      }

      return copy;
   }

   static CopyList copyList(Value v) {
      CopyList copy = new CopyList();

      for(long i = 0; i < v.getArraySize(); i++) {
         copy.add(interopElement(v.getArrayElement(i)));
      }

      return copy;
   }

   /**
    * One element of a Java-argument copy: nested objects and arrays are copied, functions and
    * non-plain objects rejected, and scalars get Graal's default boxing (Integer when the
    * value fits), as main's live views gave (audit (d)4).
    */
   static Object interopElement(Value v) {
      if(v == null || v.isNull()) {
         return null;
      }

      // a foreign reference nested in an own object or array: the owner's live view, as main
      // gave for the element, never the ForeignRef proxy itself
      if(isForeignRef(v)) {
         return foreignAs(v, Object.class);
      }

      if(isFunction(v) || isNonPlainObject(v)) {
         throw reject(v);
      }

      if(isDate(v)) {
         return toDate(v);
      }

      if(isArray(v) || isPlainObject(v)) {
         int[] depth = DEPTH.get();

         if(++depth[0] > MAX_DEPTH) {
            depth[0]--;
            throw new ScriptException(DEPTH_MESSAGE);
         }

         try {
            return isArray(v) ? copyList(v) : copyMap(v);
         }
         finally {
            depth[0]--;
         }
      }

      return v.as(Object.class);
   }

   static ScriptException reject(Value v) {
      return new ScriptException(v.canExecute() ? FUNCTION_MESSAGE
         : "A script object of type " + metaName(v) + " cannot be passed to a Java method " +
           "or stored by a worksheet script; use a plain object, array or value");
   }

   private static void check(Object host, Context context, int depth) {
      if(depth > MAX_DEPTH) {
         throw new ScriptException(DEPTH_MESSAGE);
      }

      if(host instanceof Value value) {
         if(isOwn(value, context) && !value.isNull()) {
            throw new ScriptException(value.canExecute() ? FUNCTION_MESSAGE
               : "A script object of type " + metaName(value) + " cannot be stored in a host " +
                 "object or returned by a worksheet script; use a plain object, array or value");
         }

         // a foreign owner value (unwrapped, and counted, by foreign(ForeignRef)) is stored
         // as the live reference, as on main (spec §14.11)
      }
      else if(host instanceof Object[] array) {
         for(Object element : array) {
            check(element, context, depth + 1);
         }
      }
      else if(host instanceof CopyMap map) {
         for(Object element : map.values()) {
            check(element, context, depth + 1);
         }
      }
   }

   private static boolean isOwnGuest(Value v) {
      if(v == null || v.isNull() || v.isHostObject() || v.isProxyObject() || v.isString() ||
         v.isNumber() || v.isBoolean())
      {
         return false;
      }

      Context context = WsExecContext.currentContext();
      return context != null && isOwn(v, context);
   }

   private static boolean isOwn(Value v, Context context) {
      try {
         return context.equals(v.getContext());
      }
      catch(RuntimeException ex) {
         return false;
      }
   }

   private static boolean isObjectMeta(Value v) {
      try {
         Value meta = v.getMetaObject();
         return meta == null || "Object".equals(meta.getMetaQualifiedName());
      }
      catch(RuntimeException ex) {
         return false;
      }
   }

   private static String metaName(Value v) {
      try {
         Value meta = v.getMetaObject();
         return meta == null ? "object" : meta.getMetaQualifiedName();
      }
      catch(RuntimeException ex) {
         return "object";
      }
   }

   static final String FUNCTION_MESSAGE =
      "A script function cannot be passed to a Java method, stored in a host object or " +
      "returned by a worksheet script";
   private static final String DEPTH_MESSAGE =
      "A script object is nested too deeply, or refers to itself, to be passed to Java";
   private static final int MAX_DEPTH = 64;
   private static final ThreadLocal<int[]> DEPTH = ThreadLocal.withInitial(() -> new int[1]);
   private static final AtomicLong FOREIGN = new AtomicLong();
}
