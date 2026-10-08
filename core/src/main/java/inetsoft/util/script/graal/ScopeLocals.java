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
package inetsoft.util.script.graal;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bug #77866: the top-level var stores that script engines made for one scope (see
 * {@link GraalJavaScriptEngine}'s {@code localsFor}), held by the scope itself, so that a
 * store lives exactly as long as its scope. An engine that held the store of a scope in a
 * map weakly keyed by the scope kept the scope forever when a var of the store referred
 * back to it (a var holding the scope, or a member object that reaches it), since a
 * {@link WeakHashMap} value strongly reaches its key. A scope returns its holder from
 * {@link ScriptScope#getScopeLocals}; a scope made for one evaluation (a query view, a
 * condition scope, a calc table scope) should, since an engine outlives it.
 *
 * <p>A store is kept per engine, weakly keyed by the engine, as each engine has its own
 * Context. Several engines (pooled contexts) may run scripts on one scope on different
 * threads at the same time, so the holder is thread-safe. A copy of a scope must not
 * share its holder, or the copy would share the vars of the original.
 */
public final class ScopeLocals {
   /** The store {@code engine} made for the scope, or null. */
   synchronized Object get(GraalJavaScriptEngine engine) {
      return stores.get(engine);
   }

   synchronized void put(GraalJavaScriptEngine engine, Object store) {
      stores.put(engine, store);
   }

   /** Drop the store of {@code engine}, when its Context is closed or replaced. */
   synchronized void remove(GraalJavaScriptEngine engine) {
      stores.remove(engine);
   }

   private final Map<GraalJavaScriptEngine, Object> stores = new WeakHashMap<>(2);
}
