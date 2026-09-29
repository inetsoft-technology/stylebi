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
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.util.*;
import java.util.function.Supplier;

/**
 * The context-free form of a script object that a formula table's var holds (Testing #77123,
 * B1 residual). It is taken at the end of a pooled batch, on the slot the batch claimed, and
 * rebuilt in the context of a later batch that runs on another slot. Only a Date is kept: its
 * time value (NaN for an Invalid Date). Any other script object is {@link Lost}, and reads as
 * undefined on another context.
 *
 * <p><b>No script code runs in a snapshot</b>, so it may run after the batch's timeout guard
 * fired. The rule the code below keeps: a value is first tested with {@code isDate() &&
 * isInstant()} (the Date internal-slot test, false for every Proxy, so no trap can run), and
 * only a Date is then read with {@code getMemberKeys()}, which on a Proxy runs its
 * {@code ownKeys} trap. Every other value is only classified by interop messages that never
 * dispatch to script code ({@code getMetaObject}, {@code getMetaSimpleName},
 * {@code canExecute}, {@code hasArrayElements}, {@code equals}/{@code hashCode} identity).
 * A future member or element read of another kind must first rule out a Proxy
 * ({@code getMetaObject()} is the constant {@code Proxy}); {@code hasMembers()} is no guard.
 *
 * <p>A rebuild calls the Date constructor of the executing context captured when that
 * context was created, so a script that replaced the global {@code Date} does not change it.
 */
public final class OwnedValueCodec {
   private OwnedValueCodec(Slot slot) {
      this.context = slot.engine().context();
      this.dateConstructor = slot.engine().dateConstructor();
   }

   /**
    * @return the codec of the slot {@code span} claimed, or {@code null} if the span is not a
    *         pooled claim (pool off) or no slot was checked out under it (no script ran).
    */
   public static OwnedValueCodec forSpan(ScriptSpan span) {
      if(span instanceof SlotClaim claim) {
         return of(claim.peekSlot());
      }

      return null;
   }

   /**
    * @return the codec of the pooled context executing on this thread, or {@code null}.
    */
   public static OwnedValueCodec current() {
      return of(WsExecContext.currentSlot());
   }

   private static OwnedValueCodec of(Slot slot) {
      return slot != null && !slot.isClosed() && slot.engine().dateConstructor() != null
         ? new OwnedValueCodec(slot) : null;
   }

   public Context context() {
      return context;
   }

   /**
    * The snapshot of {@code v}, a value of {@link #context()}; aliases share one node through
    * {@code ids}. Never throws a RuntimeException: a value that cannot be read is
    * {@link Lost}.
    */
   public Object snapshot(Value v, Map<Value, Object> ids) {
      Object node;

      try {
         Supplier<? extends Throwable> fault = readFault;

         if(fault != null) {
            Throwable ex = fault.get();

            if(ex instanceof Error error) {
               throw error;
            }

            throw (RuntimeException) ex;
         }

         // interop identity (no script code), inside the catch like every read
         Object seen = ids.get(v);

         if(seen != null) {
            return seen;
         }

         // the ordering rule (class comment): isDate() && isInstant() before any member read
         if(v.isDate() && v.isInstant()) {
            double time = v.asInstant().toEpochMilli();
            Value meta = v.getMetaObject();
            String dropped = null;

            if(meta == null || !meta.equals(dateConstructor)) {
               String cls = meta == null ? null : meta.getMetaSimpleName();
               dropped = cls == null || cls.isEmpty() ? "a subclass of Date" : "the class " + cls;
            }

            // a Date, so no Proxy: own enumerable string keys, no getter runs
            Set<String> keys = v.getMemberKeys();

            if(!keys.isEmpty()) {
               String props = "the properties " + String.join(", ", keys);
               dropped = dropped == null ? props : dropped + " and " + props;
            }

            node = new DateNode(time, dropped);
         }
         // an Invalid Date is no interop date, but its constructor is the intrinsic Date
         else if(dateConstructor.equals(v.getMetaObject())) {
            node = new DateNode(Double.NaN, null);
         }
         else {
            node = new Lost(kind(v));
         }

         ids.put(v, node);
      }
      catch(RuntimeException ex) {
         node = UNREADABLE;
      }

      return node;
   }

   /**
    * Rebuild {@code node} in {@link #context()}, which executes on this thread; aliases
    * share one value through {@code built}.
    *
    * @return the value, or {@code null} if the node is not rebuilt (a {@link Lost} value).
    */
   public Value rebuild(Object node, IdentityHashMap<Object, Value> built) {
      Value seen = built.get(node);

      if(seen != null) {
         return seen;
      }

      if(!(node instanceof DateNode d)) {
         return null;
      }

      Value v = dateConstructor.newInstance(d.time);
      built.put(node, v);
      return v;
   }

   // what a value that is not kept is, for the warning: "holds <kind> created on another..."
   private static String kind(Value v) {
      try {
         if(v.canExecute()) {
            return "a function";
         }

         if(v.hasArrayElements()) {
            return "an array";
         }

         Value meta = v.getMetaObject();
         String name = meta == null ? null : meta.getMetaSimpleName();

         if(name == null || name.isEmpty()) {
            return "a script object";
         }

         if("Object".equals(name)) {
            return "an object";
         }

         return ("AEIOU".indexOf(name.charAt(0)) >= 0 ? "an " : "a ") + name + " object";
      }
      catch(RuntimeException ex) {
         return "a script object";
      }
   }

   /** A Date: its time value (NaN for an Invalid Date) and what a rebuild drops, if any. */
   public static final class DateNode {
      DateNode(double time, String dropped) {
         this.time = time;
         this.dropped = dropped;
      }

      /**
       * @return what a rebuild drops ("the properties a, b", "a subclass of Date"), or
       *         {@code null}.
       */
      public String dropped() {
         return dropped;
      }

      private final double time;
      private final String dropped;
   }

   /** A value that is not kept across contexts, e.g. an array. */
   public static final class Lost {
      Lost(String kind) {
         this.kind = kind;
      }

      /** @return what the value was, with its article ("an array", "a function"). */
      public String kind() {
         return kind;
      }

      private final String kind;
   }

   /** The node of a value whose snapshot failed: it is lost, never kept from an older batch. */
   public static final Lost UNREADABLE = new Lost("a value that could not be read");

   // tests only (PoolTestSupport.failOwnedValueReads): every snapshot read throws this
   static volatile Supplier<? extends Throwable> readFault;

   private final Context context;
   private final Value dateConstructor;
}
