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
import org.graalvm.polyglot.PolyglotException;
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
 * isInstant()} (the Date internal-slot test, false for every Proxy, so no trap can run), or,
 * for a value whose meta object is the intrinsic Date (an Invalid Date, or an object that only
 * inherits {@code Date.prototype}), with the intrinsic {@code Date.prototype.getTime} captured
 * when the context was created (a builtin that reads the internal slot and throws a TypeError
 * for any other value); only a Date is then read with {@code getMemberKeys()}, which on a
 * Proxy runs its {@code ownKeys} trap. Every other value is only classified by interop messages that never
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
      this.getTime = slot.engine().dateGetTime();
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
      return slot != null && !slot.isClosed() && slot.engine().dateConstructor() != null &&
         slot.engine().dateGetTime() != null ? new OwnedValueCodec(slot) : null;
   }

   /**
    * @return the context of the slot {@code span} claimed, or {@code null} if the span is not
    *         a pooled claim or no slot was checked out under it. Values of this context that
    *         {@link #forSpan} cannot save (a closed slot) are to be lost, never left with an
    *         older snapshot.
    */
   public static Context claimedContext(ScriptSpan span) {
      Slot slot = span instanceof SlotClaim claim ? claim.peekSlot() : null;
      return slot == null ? null : slot.engine().context();
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
            node = dateNode(v, v.asInstant().toEpochMilli());
         }
         else {
            // an Invalid Date is no interop date. Its meta object is the intrinsic Date, but
            // so is that of any object that only inherits Date.prototype
            // (Object.create(Date.prototype), an ES5 "subclass"): only the Date internal
            // slot, read by the intrinsic getTime, tells them apart
            boolean dateMeta = dateConstructor.equals(v.getMetaObject());
            Double time = dateMeta ? timeValue(v) : null;
            node = time != null ? dateNode(v, time) : dateMeta ? NOT_A_DATE : new Lost(kind(v));
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

   /**
    * The node of {@code v}, a value with the Date internal slot (so no Proxy: reading its
    * member keys runs no trap, and own enumerable string keys run no getter).
    */
   private DateNode dateNode(Value v, double time) {
      Value meta = v.getMetaObject();
      String dropped = null;

      if(meta == null || !meta.equals(dateConstructor)) {
         String cls = meta == null ? null : meta.getMetaSimpleName();
         dropped = cls == null || cls.isEmpty() ? "of a subclass" : "of a subclass (" + cls + ")";
      }

      Set<String> keys = v.getMemberKeys();

      if(!keys.isEmpty()) {
         String props = "with the properties " + list(keys);
         dropped = dropped == null ? props : dropped + " and " + props;
      }

      return new DateNode(time, dropped);
   }

   /**
    * @return the time value of {@code v} (NaN for an Invalid Date) if it has the Date
    *         internal slot, else {@code null}. The intrinsic getTime reads only that slot and
    *         runs no script code. An interrupt or cancel propagates (the value is unreadable).
    */
   private Double timeValue(Value v) {
      try {
         return getTime.execute(v).asDouble();
      }
      catch(PolyglotException ex) {
         if(ex.isGuestException() && !ex.isCancelled() && !ex.isInterrupted() &&
            !ex.isResourceExhausted())
         {
            return null; // the TypeError of a value that is not a Date
         }

         throw ex;
      }
   }

   // at most MAX_KEYS names, "a, b, c and 17 more"
   private static String list(Set<String> keys) {
      if(keys.size() <= MAX_KEYS) {
         return String.join(", ", keys);
      }

      List<String> first = new ArrayList<>(keys).subList(0, MAX_KEYS);
      return String.join(", ", first) + " and " + (keys.size() - MAX_KEYS) + " more";
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

         return ("AEIOaeio".indexOf(name.charAt(0)) >= 0 ? "an " : "a ") + name + " object";
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
       * @return what a rebuild drops ("with the properties a, b", "of a subclass (Foo)"),
       *         or {@code null}.
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

   /** The node of an object that inherits Date.prototype without being a Date. */
   public static final Lost NOT_A_DATE =
      new Lost("an object that inherits Date.prototype but is no Date");

   /** The node of a value whose snapshot failed: it is lost, never kept from an older batch. */
   public static final Lost UNREADABLE = new Lost("a value that could not be read");

   // tests only (PoolTestSupport.failOwnedValueReads): every snapshot read throws this
   static volatile Supplier<? extends Throwable> readFault;

   private final Context context;
   private final Value dateConstructor;
   private final Value getTime;
   private static final int MAX_KEYS = 8;
}
