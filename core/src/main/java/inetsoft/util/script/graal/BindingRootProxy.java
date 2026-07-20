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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Emulates Rhino's scope chain for unqualified name resolution.
 * Bound as the global "__scope__"; scripts are wrapped in
 * with(__scope__){ ... } so GraalJS calls hasMember/getMember at lookup
 * time. Lookup order: engine/global scope (and its parent chain), then the
 * current FormulaContext execution scope (supplied lazily, resolved live).
 */
public class BindingRootProxy implements ProxyObject {
   private ScriptScope global;
   private final Supplier<ScriptScope> execScopeSupplier;
   private final Predicate<String> classFilter;
   private final Context context;
   private LegacyJavaShim.ImportScope imports;
   private ScriptScope builtinScope;

   public BindingRootProxy(ScriptScope global, Supplier<ScriptScope> execScopeSupplier) {
      this(global, execScopeSupplier, null, null);
   }

   public BindingRootProxy(ScriptScope global, Supplier<ScriptScope> execScopeSupplier,
                           Predicate<String> classFilter, Context context)
   {
      this.global = global;
      this.execScopeSupplier = execScopeSupplier;
      this.classFilter = classFilter;
      this.context = context;
   }

   /**
    * Case-insensitive last-resort scope for unqualified names (the CALC function
    * scope). Consulted only after the normal chain, exec scope and legacy imports
    * fail, and only for a name that has no exact global binding — so JS builtins
    * (e.g. Date, whose lowercase 'date' is a CALC function) and the case-sensitive
    * lowercase CALC copies always win. Rhino resolved these case-insensitively via
    * the Calc global-scope prototype; GraalJS global bindings are case-sensitive,
    * dropping PascalCase names like NthMostFrequent/Sum. (#75685)
    */
   public void setBuiltinScope(ScriptScope builtinScope) {
      this.builtinScope = builtinScope;
   }

   /**
    * Lazily-created per-exec import state for the legacy compatibility shim.
    * Populated by {@code importClass}/{@code importPackage}; consulted as a
    * last resort during unqualified name resolution. Never leaks across execs
    * because a fresh {@code BindingRootProxy} is created per execution.
    */
   LegacyJavaShim.ImportScope imports() {
      if(imports == null) {
         imports = new LegacyJavaShim.ImportScope();
      }

      return imports;
   }

   /**
    * Reuse support: this proxy is bound once as {@code __scope__} and its root
    * scope is swapped per exec (rather than allocating a new proxy and
    * re-binding __scope__ on every one of hundreds of thousands of per-row
    * script evaluations). Swaps return the previous value so the caller can
    * restore it in a finally — preserving correctness under reentrant exec
    * (a script that triggers another script execution on the same thread).
    * Single-threaded per engine (guarded by the engine lock). (#75423)
    */
   public ScriptScope swapGlobal(ScriptScope newGlobal) {
      ScriptScope prev = global;
      global = newGlobal;
      // A reused root scope can be mutated in place between execs while keeping
      // the same identity -- CalcCellScope/CrosstabCellScope.setCell(row,col)
      // changes which group/dimension names it exposes -- which the root-identity
      // check in inChain() cannot see. Binding the chain-membership cache to a
      // single exec keeps it correct: within one exec the chain's membership is
      // immutable apart from putMember (which also clears). (#75676)
      invalidateChainCache();
      return prev;
   }

   /** Swap the per-exec import state (reset to null for a fresh exec). */
   public LegacyJavaShim.ImportScope swapImports(LegacyJavaShim.ImportScope newImports) {
      LegacyJavaShim.ImportScope prev = imports;
      imports = newImports;
      return prev;
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
      if(inChain(name)) {
         for(ScriptScope s = global; s != null; s = s.getParentScope()) {
            if(s.hasMember(name)) {
               return s.getMember(name);
            }
         }
      }

      ScriptScope exec = execScopeSupplier.get();

      if(exec != null && exec.hasMember(name)) {
         return exec.getMember(name);
      }

      // last resort: unqualified names brought in by importClass/importPackage
      // (legacy compatibility shim). Declared scope names always win; real JS
      // globals/builtins are never shadowed (enforced in resolveImport).
      if(imports != null) {
         Object imp = LegacyJavaShim.resolveImport(imports, name, classFilter, context);

         if(imp != null) {
            return imp;
         }
      }

      // A real js global binding resolves natively via with(...) fall-through, so
      // report it absent here -- before the terminal Calc probe, so the ~98%
      // "global function, not in chain" case skips that probe. Equivalent to the
      // old `builtin != null && !hasGlobalBinding` guard, which likewise refused
      // to return a builtin that had an exact global binding. (#75676)
      if(isGlobalBinding(name)) {
         return NOT_FOUND;
      }

      // case-insensitive CALC functions (Rhino's global-scope prototype). Only
      // when the name has no exact global binding, so JS builtins (Date) and the
      // lowercase CALC copies are never shadowed. This is only reached after the
      // full chain (global/exec/imports) already missed — the terminal miss path
      // — so a single case-insensitive lookup here (null means absent) matches
      // Rhino's Calc-as-prototype cost and adds no repeated work on hits. (#75685)
      if(builtinScope != null) {
         Object builtin = builtinScope.getMember(name);

         if(builtin != null) {
            return builtin;
         }
      }

      return NOT_FOUND;
   }

   /** Whether {@code name} is an exact (case-sensitive) member of the JS global scope. */
   private boolean hasGlobalBinding(String name) {
      return context != null && context.getBindings("js").hasMember(name);
   }

   // Cache of the exact-name global-binding test. Only a 'true' answer is cached,
   // and permanently: a global, once installed, stays for the engine's lifetime.
   // A 'false' answer is deliberately NOT cached -- the GraalJS Context (and its
   // globalThis) is reused across same-org renders on a pooled thread (see
   // FormulaEvaluator), so a later formula's top-level var/function can turn a
   // previously-absent name into a real global; a cached 'false' would then keep
   // shadowing it via the builtin/CALC scope. Recomputing 'false' is cheap and
   // rare -- profiling shows ~98% of probes reaching here are real globals (i.e.
   // cached 'true'). Single-threaded per engine (engine lock). (#75676)
   private final Set<String> globalBindingCache = new HashSet<>();

   private boolean isGlobalBinding(String name) {
      if(globalBindingCache.contains(name)) {
         return true;
      }

      boolean present = hasGlobalBinding(name);

      if(present) {
         globalBindingCache.add(name);
      }

      return present;
   }

   // Per-chain-root cache of "is name provided by the scope chain?" The calc table
   // swaps in a fresh root scope per evaluation, so the cache is keyed on the root
   // identity and dropped when the root changes; within one root the same names
   // are probed repeatedly. Uses the real hasMember walk, so it stays correct for
   // scopes with dynamic (non-enumerated) members. Cleared on putMember, which can
   // add a name to a chain scope. (#75676)
   private ScriptScope chainCacheRoot;
   private final Map<String, Boolean> chainCache = new HashMap<>();

   private void invalidateChainCache() {
      chainCache.clear();
      chainCacheRoot = null;
   }

   private boolean inChain(String name) {
      if(chainCacheRoot != global) {
         chainCache.clear();
         chainCacheRoot = global;
      }

      Boolean cached = chainCache.get(name);

      if(cached != null) {
         return cached;
      }

      boolean found = false;

      for(ScriptScope s = global; s != null; s = s.getParentScope()) {
         if(s.hasMember(name)) {
            found = true;
            break;
         }
      }

      chainCache.put(name, found);
      return found;
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
      return ScriptValueConverter.toGuest(resolve(key));
   }
   @Override public boolean hasMember(String key) { return resolves(key); }
   @Override public Object getMemberKeys() { return enumerate().toArray(new String[0]); }
   @Override public void putMember(String key, Value value) {
      // a write can create a new name on a chain scope, so the "is name in chain?"
      // answer for the current root may change; drop the cache. (#75676)
      invalidateChainCache();
      Object host = ScriptValueConverter.toHost(value);

      // Rhino scope-chain write semantics: an unqualified assignment to a name
      // that already exists in the chain writes to the scope that OWNS it
      // (global scope, its parent chain, then the exec scope). Only a name that
      // exists nowhere is created on the root scope. Writing unconditionally to
      // `global` would shadow a parent/exec-scope variable, so a read from the
      // owning scope (Java side or another script) would see a stale value.
      for(ScriptScope s = global; s != null; s = s.getParentScope()) {
         if(s.hasMember(key)) {
            s.putMember(key, host);
            return;
         }
      }

      ScriptScope exec = execScopeSupplier.get();

      if(exec != null && exec.hasMember(key)) {
         exec.putMember(key, host);
         return;
      }

      global.putMember(key, host);
   }
}
