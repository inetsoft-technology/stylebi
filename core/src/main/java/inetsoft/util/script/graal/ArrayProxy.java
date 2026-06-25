/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.util.script.graal;

import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyObject;

/**
 * Bridges a {@link ScriptArrayScope} to GraalJS. Exposes the scope as a JS
 * array (via {@link ProxyArray}) while still allowing named-member access
 * (via {@link ProxyObject}) so {@code arr.length} and other named properties
 * continue to resolve. Indexed access delegates to
 * {@link ScriptArrayScope#getArrayElement(long)}; named access delegates to the
 * {@link ScriptScope} string-member methods. The array view is read-only.
 */
public class ArrayProxy implements ProxyArray, ProxyObject {
   private final ScriptArrayScope scope;

   public ArrayProxy(ScriptArrayScope scope) {
      this.scope = scope;
   }

   public ScriptArrayScope getScope() {
      return scope;
   }

   // --- ProxyArray ---

   @Override
   public long getSize() {
      return scope.getArraySize();
   }

   @Override
   public Object get(long index) {
      return ScriptValueConverter.toGuest(scope.getArrayElement(index));
   }

   @Override
   public void set(long index, Value value) {
      scope.setArrayElement(index, ScriptValueConverter.toHost(value));
   }

   // --- ProxyObject ---

   @Override
   public Object getMember(String key) {
      // Negative indices (e.g. field[-1] for the previous row) arrive as the
      // string member key "-1": JS arrays have no negative indices, and GraalJS
      // only dispatches readArrayElement for 0 <= i < size, so it falls back to
      // readMember. Rhino called get(int) for any index, including negative, so
      // route negative integer keys to getArrayElement to preserve that. (#75423)
      long idx = negativeIndex(key);

      if(idx != NOT_INDEX) {
         return ScriptValueConverter.toGuest(scope.getArrayElement(idx));
      }

      return ScriptValueConverter.toGuest(scope.getMember(key));
   }

   @Override
   public void putMember(String key, Value value) {
      scope.putMember(key, ScriptValueConverter.toHost(value));
   }

   @Override
   public boolean hasMember(String key) {
      // report negative integer keys present so GraalJS dispatches the read to
      // getMember (which routes to getArrayElement). (#75423)
      return negativeIndex(key) != NOT_INDEX || scope.hasMember(key);
   }

   private static final long NOT_INDEX = Long.MIN_VALUE;

   /** Parse a negative integer member key, or {@link #NOT_INDEX} if not one. */
   private static long negativeIndex(String key) {
      if(key == null || key.length() < 2 || key.charAt(0) != '-') {
         return NOT_INDEX;
      }

      try {
         long v = Long.parseLong(key);
         return v < 0 ? v : NOT_INDEX;
      }
      catch(NumberFormatException ex) {
         return NOT_INDEX;
      }
   }

   @Override
   public Object getMemberKeys() {
      Object[] keys = scope.getMemberKeys();
      String[] str = new String[keys == null ? 0 : keys.length];

      for(int i = 0; i < str.length; i++) {
         str[i] = String.valueOf(keys[i]);
      }

      return str;
   }

   @Override
   public boolean removeMember(String key) {
      return scope.removeMember(key);
   }
}
