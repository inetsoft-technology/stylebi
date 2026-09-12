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

import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import java.util.Arrays;

/**
 * Bridges a {@link ScriptScope} to GraalJS. This is the only class that
 * references org.graalvm.polyglot.proxy. All host<->guest conversion is
 * delegated to {@link ScriptValueConverter}; GraalJS auto-wraps the host
 * values returned here according to the active HostAccess policy.
 */
public class ScopeProxy implements ProxyObject {
   private final ScriptScope scope;

   public ScopeProxy(ScriptScope scope) {
      this.scope = scope;
   }

   public ScriptScope getScope() {
      return scope;
   }

   @Override
   public Object getMember(String key) {
      // GraalJS's string-coercion (ToPrimitive) looks up a callable "toString"/
      // "valueOf" *member* on this proxy rather than calling the underlying
      // ScriptScope's Java toString(), so a scope object concatenated directly
      // into a string (e.g. an embedded viewsheet's "thisParameter") would
      // otherwise stringify as the generic "[object Object]".
      if("toString".equals(key) && !scope.hasMember(key)) {
         return (ProxyExecutable) args -> scope.toString();
      }

      return ScriptValueConverter.toGuest(scope.getMember(key));
   }

   @Override
   public void putMember(String key, org.graalvm.polyglot.Value value) {
      scope.putMember(key, ScriptValueConverter.toHost(value));
   }

   @Override
   public boolean hasMember(String key) {
      return scope.hasMember(key) || "toString".equals(key);
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
