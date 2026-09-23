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
    * Install the helper on a context whose globals are its baseline. Caller holds the lock.
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
         const is = Object.is;
         const isExt = Object.isExtensible;
         const hasOwn = Object.hasOwn;
         const create = Object.create;
         const MAX_DELETES = %MAX_DELETES%;
         const CARRIER = '__inetsoft_clean__';

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

         const expected = create(null);
         const known = create(null);
         const expKeys = create(null);
         let nexp = 0;

         function remember(k) {
            if(known[k] !== true) { known[k] = true; expKeys[nexp++] = k; }
         }

         function clean() {
            let leftovers = 0, failed = false, restored = 0, removed = 0, tooMany = false;
            try { if(!isExt(G)) failed = true; } catch(e) { failed = true; }

            const keys = ownKeys(G);
            const n = keys.length;
            const foreign = create(null);
            let nforeign = 0;

            for(let i = 0; i < n; i++) {
               const k = keys[i];
               try {
                  const d = gopd(G, k);
                  if(d === undefined) continue;
                  const e = expected[k];

                  if(e !== undefined) {
                     if(!same(e, d)) {
                        if(defProp(G, k, e)) restored++; else failed = true;
                     }
                     continue;
                  }

                  if(d.configurable) {
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
               if(e === undefined) continue;
               try {
                  if(!hasOwn(G, k)) {
                     if(defProp(G, k, e)) restored++; else failed = true;
                  }
               }
               catch(ex) {
                  failed = true;
               }
            }

            const r = create(null);
            r.leftovers = leftovers; r.failed = failed; r.restored = restored;
            r.removed = removed; r.tooMany = tooMany;
            return r;
         }

         function expect(k) {
            const d = gopd(G, k);
            if(d === undefined) { expected[k] = undefined; return; }
            remember(k);
            expected[k] = copyDesc(d);
         }

         function forget(k) {
            expected[k] = undefined;
         }

         const handles = create(null);
         handles.clean = clean;
         handles.expect = expect;
         handles.forget = forget;

         const cd = create(null);
         cd.value = handles; cd.writable = false; cd.enumerable = false; cd.configurable = false;
         defProp(G, CARRIER, cd);

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
