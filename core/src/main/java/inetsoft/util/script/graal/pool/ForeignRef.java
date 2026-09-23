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

import inetsoft.util.script.graal.ScriptValueConverter;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;

import java.util.Set;

/**
 * A value of another context (e.g. a viewsheet object stored in a parameter) as a pooled
 * worksheet context sees it (bug #76960, spec §6.8, §14.11). Once Graal re-views a foreign value
 * inside the slot it reports the slot's context and cannot be told apart from an own object, so
 * the entry points wrap it while its owner is still known.
 *
 * <p>It is a live reference, never a copy: every read, write and call goes straight to the
 * owner's {@link Value}, so an access while the owner is busy fails fast, as on main. At the host
 * boundary it unwraps to that owner value, which is never walked or copied.
 */
public class ForeignRef implements ProxyObject {
   ForeignRef(Value value) {
      this.value = value;
   }

   /**
    * Wrap a value of another context, with the traits of the value.
    */
   static ForeignRef of(Value value) {
      if(value.canExecute()) {
         return new Function(value);
      }

      if(value.hasArrayElements()) {
         return new Array(value);
      }

      return new ForeignRef(value);
   }

   /**
    * @return the owner's live value.
    */
   public Value value() {
      return value;
   }

   @Override
   public Object getMember(String key) {
      Value member = value.getMember(key);

      // a method keeps its receiver, so obj.m() still runs with this === obj in the owner
      if(member != null && member.canExecute() && WsValueCopier.isForeignCandidate(member)) {
         return child(key, member, () -> new Method(value, key, member));
      }

      return child(key, member, () -> out(member));
   }

   /**
    * The reference handed out for a member or element: the same one again while the owner
    * still holds the same value there, so o.b === o.b holds as on main.
    */
   Object child(Object key, Value member, java.util.function.Supplier<Object> factory) {
      if(member == null || !WsValueCopier.isForeignCandidate(member)) {
         return factory.get();
      }

      synchronized(this) {
         if(children == null) {
            children = new java.util.HashMap<>();
         }

         Object cached = children.get(key);

         if(cached instanceof ForeignRef ref && ref.value.equals(member)) {
            return cached;
         }

         Object created = factory.get();
         children.put(key, created);
         return created;
      }
   }

   @Override
   public Object getMemberKeys() {
      Set<String> keys = value.getMemberKeys();
      return keys.toArray(new String[0]);
   }

   @Override
   public boolean hasMember(String key) {
      return value.hasMember(key);
   }

   @Override
   public void putMember(String key, Value v) {
      value.putMember(key, in(v));
   }

   @Override
   public boolean removeMember(String key) {
      return value.removeMember(key);
   }

   @Override
   public String toString() {
      return value.toString();
   }

   /**
    * An owner value handed back to the worksheet script: objects stay marked foreign.
    */
   static Object out(Value v) {
      if(v == null || v.isNull()) {
         return null;
      }

      if(v.isHostObject()) {
         return v.asHostObject();
      }

      if(v.isProxyObject()) {
         return v.asProxyObject();
      }

      if(WsValueCopier.isForeignCandidate(v)) {
         return of(v);
      }

      // a primitive or date: passed as the owner's value, as Graal does for main's re-view
      return v;
   }

   /**
    * A worksheet value written into the owner: a foreign reference unwraps to its owner value,
    * a primitive passes as is, and anything else is converted as a stored value (copied, or
    * rejected when it cannot be kept apart from the worksheet context, spec §14.12 A2).
    */
   static Object in(Value v) {
      if(v == null || v.isNull()) {
         return null;
      }

      if(v.isProxyObject() && v.asProxyObject() instanceof ForeignRef ref) {
         return ref.value;
      }

      if(v.isString() || v.isNumber() || v.isBoolean()) {
         return v;
      }

      return ScriptValueConverter.toHostStored(v);
   }

   /**
    * A call argument handed to the owner: transient, so it is passed live as on main, except
    * that a foreign reference unwraps to its owner value.
    */
   static Object arg(Value v) {
      if(v != null && v.isProxyObject() && v.asProxyObject() instanceof ForeignRef ref) {
         return ref.value;
      }

      return v;
   }

   static Object[] args(Value... arguments) {
      Object[] args = new Object[arguments.length];

      for(int i = 0; i < arguments.length; i++) {
         args[i] = arg(arguments[i]);
      }

      return args;
   }

   /**
    * A foreign array.
    */
   static final class Array extends ForeignRef implements ProxyArray {
      Array(Value value) {
         super(value);
      }

      @Override
      public Object get(long index) {
         Value element = value().getArrayElement(index);
         return child(index, element, () -> out(element));
      }

      @Override
      public void set(long index, Value v) {
         value().setArrayElement(index, in(v));
      }

      @Override
      public boolean remove(long index) {
         return value().removeArrayElement(index);
      }

      @Override
      public long getSize() {
         return value().getArraySize();
      }
   }

   /**
    * A foreign function.
    */
   static class Function extends ForeignRef implements ProxyExecutable {
      Function(Value value) {
         super(value);
      }

      @Override
      public Object execute(Value... arguments) {
         return out(value().execute(args(arguments)));
      }
   }

   /**
    * A foreign method read from its object: called on that object.
    */
   static final class Method extends Function {
      Method(Value receiver, String name, Value function) {
         super(function);
         this.receiver = receiver;
         this.name = name;
      }

      @Override
      public Object execute(Value... arguments) {
         return out(receiver.invokeMember(name, args(arguments)));
      }

      private final Value receiver;
      private final String name;
   }

   private final Value value;
   private java.util.Map<Object, Object> children;
}
