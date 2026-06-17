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
import org.graalvm.polyglot.proxy.ProxyObject;
import java.util.*;
import java.util.function.Supplier;

/**
 * Emulates Rhino's scope chain for unqualified name resolution.
 * Bound as the global "__scope__"; scripts are wrapped in
 * with(__scope__){ ... } so GraalJS calls hasMember/getMember at lookup
 * time. Lookup order: engine/global scope (and its parent chain), then the
 * current FormulaContext execution scope (supplied lazily, resolved live).
 */
public class BindingRootProxy implements ProxyObject {
   private final ScriptScope global;
   private final Supplier<ScriptScope> execScopeSupplier;

   public BindingRootProxy(ScriptScope global, Supplier<ScriptScope> execScopeSupplier) {
      this.global = global;
      this.execScopeSupplier = execScopeSupplier;
   }

   /** Sentinel that distinguishes "present with null value" from "absent". */
   private static final Object NOT_FOUND = new Object();

   /**
    * Walk the full scope chain once and return the value for {@code name},
    * or {@link #NOT_FOUND} if it is not defined anywhere in the chain.
    * Preserves the distinction between a member set to {@code null} and a
    * member that is simply absent.
    */
   private Object findInChain(String name) {
      for(ScriptScope s = global; s != null; s = s.getParentScope()) {
         if(s.hasMember(name)) {
            return s.getMember(name);
         }
      }

      ScriptScope exec = execScopeSupplier.get();

      if(exec != null && exec.hasMember(name)) {
         return exec.getMember(name);
      }

      return NOT_FOUND;
   }

   /** Resolve a name through the full chain; returns null if not found. */
   public Object resolve(String name) {
      Object result = findInChain(name);
      return result == NOT_FOUND ? null : result;
   }

   private boolean resolves(String name) {
      return findInChain(name) != NOT_FOUND;
   }

   private Set<String> enumerate() {
      Set<String> keys = new LinkedHashSet<>();

      for(ScriptScope s = global; s != null; s = s.getParentScope()) {
         for(Object k : nullSafe(s.getMemberKeys())) {
            keys.add(String.valueOf(k));
         }
      }

      ScriptScope exec = execScopeSupplier.get();

      if(exec != null) {
         for(Object k : nullSafe(exec.getMemberKeys())) {
            keys.add(String.valueOf(k));
         }
      }

      return keys;
   }

   private static Object[] nullSafe(Object[] a) {
      return a == null ? new Object[0] : a;
   }

   @Override public Object getMember(String key) {
      Object val = resolve(key);
      return val instanceof ScriptScope ? new ScopeProxy((ScriptScope) val) : val;
   }
   @Override public boolean hasMember(String key) { return resolves(key); }
   @Override public Object getMemberKeys() { return enumerate().toArray(new String[0]); }
   @Override public void putMember(String key, Value value) {
      global.putMember(key, ScriptValueConverter.toHost(value));
   }
}
