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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.time.Duration;

/**
 * The guest-side clean helper of a pooled worksheet context (bug #76960, spec §4.2 step 3,
 * §4.4, §14.1). Installed once per context after init and replay; the host keeps its functions
 * as Value handles and never looks them up by name. It captures every builtin it uses, keeps
 * its expected descriptors in closure-private null-prototype objects, works on property
 * descriptors only (it never runs a getter or setter), and handles each key in its own
 * try/catch.
 *
 * <p>Every clean still reads and compares every own key's descriptor. After a clean that left
 * the global at its baseline, the helper keeps the key layout; while the next clean finds all
 * layout keys in order, it classifies keys by position instead of by dictionary lookup and
 * skips the pass that re-adds deleted baseline keys (spec §14.14, perf-g6 "v4"). The loops
 * avoid {@code continue} and calls, which are costly in the interpreter-only GraalJS runtime.
 */
final class CleanHelper {
   /**
    * The bound of one clean; a timeout counts as a failure.
    */
   static final Duration TIMEOUT = Duration.ofSeconds(2);

   /**
    * The outcome of one clean.
    *
    * @param leftovers non-configurable writable foreign globals (declared var/function), set
    *                  to undefined; they stay declared on the global.
    * @param failed    the global could not be brought back to its baseline.
    * @param restored  baseline keys put back.
    * @param removed   configurable foreign keys deleted.
    * @param tooMany   more configurable foreign keys than {@link PoolConfig#MAX_FOREIGN_DELETES};
    *                  none were deleted.
    */
   record Result(int leftovers, boolean failed, int restored, int removed, boolean tooMany) {
      static final Result FAILED = new Result(0, true, 0, 0, false);

      /**
       * @return whether the context may serve another claim.
       */
      boolean reusable(int cleanThreshold) {
         return !failed && !tooMany && leftovers <= cleanThreshold;
      }
   }

   private CleanHelper(Value clean, Value expect, Value forget) {
      this.clean = clean;
      this.expect = expect;
      this.forget = forget;
   }

   /**
    * Install the helper on a context whose globals are its baseline. The context is not yet
    * reachable by any other thread (its slot is unpublished), or the caller holds its lock.
    * The helper's functions are returned to the host as this eval's result and kept only as
    * Value handles; nothing on the guest global refers to them.
    */
   static CleanHelper install(Context context) {
      Value handles = context.eval(
         Source.newBuilder("js", SOURCE, "<ws-clean>").buildLiteral());
      return new CleanHelper(handles.getMember("clean"), handles.getMember("expect"),
                             handles.getMember("forget"));
   }

   Result run() {
      Value r = clean.execute();
      return new Result(r.getMember("leftovers").asInt(), r.getMember("failed").asBoolean(),
                        r.getMember("restored").asInt(), r.getMember("removed").asInt(),
                        r.getMember("tooMany").asBoolean());
   }

   /**
    * Record the current descriptor of {@code name} as expected, after the host set it.
    */
   void expect(String name) {
      expect.execute(name);
   }

   /**
    * Stop expecting {@code name}, after the host removed it.
    */
   void forget(String name) {
      forget.execute(name);
   }

   private final Value clean;
   private final Value expect;
   private final Value forget;

   private static final String SOURCE = """
      (function () {
         'use strict';
         const G = globalThis;
         const ownKeys = Reflect.ownKeys;
         const gopd = Reflect.getOwnPropertyDescriptor;
         const defProp = Reflect.defineProperty;
         const delProp = Reflect.deleteProperty;
         const setProto = Reflect.setPrototypeOf;
         const is = Object.is;
         const isExt = Object.isExtensible;
         const hasOwn = Object.hasOwn;
         const create = Object.create;
         // %Object.prototype% is an immutable-prototype exotic object whose binding on Object is
         // non-writable, so this is the prototype of every descriptor gopd returns, for good
         const OP = Object.prototype;
         const MAX_DELETES = %MAX_DELETES%;

         function copyDesc(d) {
            const o = create(null);
            if(hasOwn(d, 'value')) { o.value = d.value; o.writable = d.writable; }
            else { o.get = d.get; o.set = d.set; }
            o.enumerable = d.enumerable;
            o.configurable = d.configurable;
            return o;
         }

         function same(e, d) {
            if(e.enumerable !== d.enumerable || e.configurable !== d.configurable) return false;
            if(hasOwn(e, 'value')) {
               return hasOwn(d, 'value') && e.writable === d.writable && is(e.value, d.value);
            }
            return !hasOwn(d, 'value') && is(e.get, d.get) && is(e.set, d.set);
         }

         function valueOnly(v) {
            const o = create(null);
            o.value = v;
            return o;
         }

         // a real array with no prototype: index writes past its length consult no setter
         function arr() {
            const a = [];
            setProto(a, null);
            return a;
         }

         const expected = create(null);
         const known = create(null);
         const expKeys = create(null);
         let nexp = 0;

         // The layout: the global's own keys, in ownKeys order, right after the last clean that
         // left it at its baseline; per position the expected descriptor split into flat fields
         // (lx: 0 accessor, 1 data; LEFT for a non-configurable leftover). Valid only while the
         // expected set is unchanged; expect/forget drop it.
         const LEFT = 2;
         let ln = -1;
         let lk = arr(), le = arr(), lx = arr(), lv = arr(), ls = arr(), lw = arr(), len_ = arr(),
             lc = arr();

         function remember(k) {
            if(known[k] !== true) { known[k] = true; expKeys[nexp++] = k; }
         }

         // descriptors read by plain property access are exact only while %Object.prototype%
         // has none of the descriptor field names
         function protoClean() {
            return !(hasOwn(OP, 'value') || hasOwn(OP, 'writable') || hasOwn(OP, 'get') ||
                     hasOwn(OP, 'set') || hasOwn(OP, 'enumerable') || hasOwn(OP, 'configurable'));
         }

         function rebuild() {
            const keys = ownKeys(G);
            const n = keys.length;
            lk = arr(); le = arr(); lx = arr(); lv = arr(); ls = arr(); lw = arr(); len_ = arr();
            lc = arr();
            for(let i = 0; i < n; i++) {
               const k = keys[i];
               const e = expected[k];
               lk[i] = k;
               le[i] = e;
               if(e === undefined) {
                  lx[i] = LEFT; lv[i] = undefined; ls[i] = undefined; lw[i] = false;
                  len_[i] = false; lc[i] = false;
               }
               else if(hasOwn(e, 'value')) {
                  lx[i] = 1; lv[i] = e.value; ls[i] = undefined; lw[i] = e.writable;
                  len_[i] = e.enumerable; lc[i] = e.configurable;
               }
               else {
                  lx[i] = 0; lv[i] = e.get; ls[i] = e.set; lw[i] = false;
                  len_[i] = e.enumerable; lc[i] = e.configurable;
               }
            }
            ln = n;
         }

         // Fast path: every layout key is still an own key, in layout order, so no expected key
         // is missing and every other key is foreign. Returns null (having changed nothing) when
         // that does not hold, and the slow path then runs.
         function fast(keys, n, isExtFailed) {
            let j = 0, nf = 0;
            for(let i = 0; i < n; i++) {
               if(j < ln && keys[i] === lk[j]) j++;
               else nf++;
            }
            if(j !== ln || !protoClean()) return null;
            if(nf === 0) return exact(keys, n, isExtFailed);

            let leftovers = 0, failed = isExtFailed, restored = 0, removed = 0, tooMany = false;
            let newLeft = false;
            const foreign = arr();
            let nforeign = 0;
            j = 0;

            for(let i = 0; i < n; i++) {
               const k = keys[i];
               const at = j;
               const mine = j < ln && k === lk[j];
               if(mine) j++;
               try {
                  const d = gopd(G, k);
                  if(d === undefined) {
                     // gone meanwhile
                  }
                  else if(mine) {
                     const x = lx[at];
                     if(x === LEFT) {
                        // a leftover: exactly the slow path's non-configurable foreign handling
                        if(d.configurable) { foreign[nforeign++] = k; }
                        else if(d.writable === true) {
                           if(d.value !== undefined && !defProp(G, k, valueOnly(undefined))) failed = true;
                           leftovers++;
                        }
                        else if(d.writable === false && d.value === undefined) { leftovers++; }
                        else { failed = true; }
                     }
                     else {
                        let ok;
                        if(x === 1) {
                           const v = d.value, e = lv[at];
                           // Object.is, inlined
                           ok = d.writable === lw[at] && d.enumerable === len_[at] &&
                              d.configurable === lc[at] &&
                              (v === e ? (v !== 0 || 1 / v === 1 / e) : (v !== v && e !== e));
                        }
                        else {
                           ok = d.writable === undefined && d.enumerable === len_[at] &&
                              d.configurable === lc[at] && d.get === lv[at] && d.set === ls[at];
                        }
                        if(!ok) {
                           if(defProp(G, k, le[at])) restored++; else failed = true;
                        }
                     }
                  }
                  else if(d.configurable) {
                     foreign[nforeign++] = k;
                  }
                  else if(d.writable === true) {
                     if(d.value !== undefined && !defProp(G, k, valueOnly(undefined))) failed = true;
                     leftovers++;
                     newLeft = true;
                  }
                  else if(d.writable === false && d.value === undefined) {
                     leftovers++;
                     newLeft = true;
                  }
                  else {
                     failed = true;
                  }
               }
               catch(ex) {
                  failed = true;
               }
            }

            if(nforeign > MAX_DELETES) {
               tooMany = true;
            }
            else {
               for(let i = 0; i < nforeign; i++) {
                  try { if(delProp(G, foreign[i])) removed++; else failed = true; }
                  catch(ex) { failed = true; }
               }
            }

            if(newLeft || tooMany || failed) ln = -1;
            return result(leftovers, failed, restored, removed, tooMany);
         }

         // The keys are exactly the layout's: only descriptors can differ. Leftovers are rare, so
         // a layout position that is not a matching expected descriptor goes to the general loop's
         // per-key handling in fix().
         function exact(keys, n, isExtFailed) {
            const st = create(null);
            st.leftovers = 0; st.failed = isExtFailed; st.restored = 0; st.left = false;
            for(let i = 0; i < n; i++) {
               try {
                  const d = gopd(G, keys[i]);
                  const x = lx[i];
                  let ok = false;
                  if(d === undefined) {
                     ok = true;
                  }
                  else if(x === 1) {
                     const v = d.value, e = lv[i];
                     ok = d.writable === lw[i] && d.enumerable === len_[i] &&
                        d.configurable === lc[i] &&
                        (v === e ? (v !== 0 || 1 / v === 1 / e) : (v !== v && e !== e));
                  }
                  else if(x === 0) {
                     ok = d.writable === undefined && d.enumerable === len_[i] &&
                        d.configurable === lc[i] && d.get === lv[i] && d.set === ls[i];
                  }
                  if(!ok) fix(keys[i], i, d, st);
               }
               catch(ex) {
                  st.failed = true;
               }
            }
            if(st.left || st.failed) ln = -1;
            return result(st.leftovers, st.failed, st.restored, 0, false);
         }

         function fix(k, i, d, st) {
            try {
               if(lx[i] === LEFT) {
                  if(d.configurable) { st.failed = true; } // cannot happen: non-configurable stays so
                  else if(d.writable === true) {
                     if(d.value !== undefined && !defProp(G, k, valueOnly(undefined))) st.failed = true;
                     st.leftovers++;
                  }
                  else if(d.writable === false && d.value === undefined) { st.leftovers++; }
                  else { st.failed = true; }
               }
               else if(defProp(G, k, le[i])) st.restored++;
               else st.failed = true;
            }
            catch(ex) {
               st.failed = true;
            }
         }

         function result(leftovers, failed, restored, removed, tooMany) {
            const r = create(null);
            r.leftovers = leftovers; r.failed = failed; r.restored = restored;
            r.removed = removed; r.tooMany = tooMany;
            return r;
         }

         function clean() {
            let extFailed = false;
            try { if(!isExt(G)) extFailed = true; } catch(e) { extFailed = true; }

            const keys = ownKeys(G);
            const n = keys.length;

            if(ln >= 0) {
               const r = fast(keys, n, extFailed);
               if(r !== null) return r;
            }

            const r = slow(keys, n, extFailed);
            if(!r.failed && !r.tooMany) rebuild(); else ln = -1;
            return r;
         }

         function slow(keys, n, extFailed) {
            let leftovers = 0, failed = extFailed, restored = 0, removed = 0, tooMany = false;
            const foreign = create(null);
            let nforeign = 0;

            for(let i = 0; i < n; i++) {
               const k = keys[i];
               try {
                  const d = gopd(G, k);
                  const e = d === undefined ? undefined : expected[k];

                  if(d === undefined) {
                     // gone meanwhile
                  }
                  else if(e !== undefined) {
                     if(!same(e, d)) {
                        if(defProp(G, k, e)) restored++; else failed = true;
                     }
                  }
                  else if(d.configurable) {
                     foreign[nforeign++] = k;
                  }
                  else if(hasOwn(d, 'value') && d.writable) {
                     if(d.value !== undefined && !defProp(G, k, valueOnly(undefined))) failed = true;
                     leftovers++;
                  }
                  else if(hasOwn(d, 'value') && d.value === undefined) {
                     leftovers++;
                  }
                  else {
                     failed = true;
                  }
               }
               catch(ex) {
                  failed = true;
               }
            }

            if(nforeign > MAX_DELETES) {
               tooMany = true;
            }
            else {
               for(let i = 0; i < nforeign; i++) {
                  try { if(delProp(G, foreign[i])) removed++; else failed = true; }
                  catch(ex) { failed = true; }
               }
            }

            for(let i = 0; i < nexp; i++) {
               const k = expKeys[i];
               const e = expected[k];
               if(e !== undefined) {
                  try {
                     if(!hasOwn(G, k)) {
                        if(defProp(G, k, e)) restored++; else failed = true;
                     }
                  }
                  catch(ex) {
                     failed = true;
                  }
               }
            }

            return result(leftovers, failed, restored, removed, tooMany);
         }

         function expect(k) {
            ln = -1;
            const d = gopd(G, k);
            if(d === undefined) { expected[k] = undefined; return; }
            remember(k);
            expected[k] = copyDesc(d);
         }

         function forget(k) {
            ln = -1;
            expected[k] = undefined;
         }

         // the handles go to the host only, as this eval's result; nothing on the global
         // refers to them, so guest code can never reach expect/forget/clean
         const handles = create(null);
         handles.clean = clean;
         handles.expect = expect;
         handles.forget = forget;

         const base = ownKeys(G);
         for(let i = 0; i < base.length; i++) {
            const k = base[i];
            expected[k] = copyDesc(gopd(G, k));
            remember(k);
         }
         return handles;
      })()
      """.replace("%MAX_DELETES%", String.valueOf(PoolConfig.MAX_FOREIGN_DELETES));
}
