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

import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spec §14.14 (bug #76960): the v4 clean helper, with its layout fast path, gives the same
 * outcome as the v0 helper it replaced over a hostile corpus, on warm slots where the fast
 * path is taken, over several claim cycles; and after each reusable clean an independent
 * verifier (its own captured builtins and baseline) finds the global at its baseline, with
 * only declared leftovers set to undefined.
 *
 * <p>Two fresh slots per case: {@code a} is cleaned by its own (v4) helper; {@code b} by the
 * v0 source below, installed on it right after it was built, so both helpers start from the
 * same baseline.
 */
@Tag("core")
class CleanParityTest {
   @AfterEach
   void closeSlots() {
      for(Slot slot : new Slot[] { a, b }) {
         if(slot != null && !slot.isClosed()) {
            slot.close();
            slot.unlock();
         }
      }
   }

   static Stream<String> corpus() {
      return Stream.of(
         "x = 1",
         "CALC = 0",
         "delete globalThis.libFn",
         "delete globalThis.libFn; libFn = 5",
         // delete and re-add of the same value moves the key to the end of ownKeys order
         "var keep = globalThis.libFn; delete globalThis.libFn; globalThis.libFn = keep",
         "Object.defineProperty(globalThis, 'libFn', {get(){ return 1; }, configurable: true})",
         "Object.defineProperty(globalThis, 'isNull', {enumerable: false})",
         "Object.defineProperty(globalThis, 'isNull', {writable: false})",
         "globalThis[7] = 1",
         "globalThis[Symbol.for('s')] = 1; globalThis[Symbol('t')] = 2",
         "Object.prototype.value = 1; Object.prototype.writable = true; " +
            "Object.defineProperty(globalThis, 'isNull', {get(){ return 1; }, configurable: true})",
         "Object.prototype.get = function(){ return 1; }; libFn = 2",
         "Infinity = 5; NaN = 1",
         "zero = -0",
         "Object.defineProperty(globalThis, 'nc', {value: 1, writable: true, enumerable: true, " +
            "configurable: false})",
         "try { Object.defineProperty(globalThis, 'nc2', {value: 1, writable: true, " +
            "configurable: false}); } catch(e) {} nc2 = 7",
         "for(var i = 0; i < 40; i++) { globalThis['f' + i] = i; }",
         "Math = 1; JSON = 2",
         "var v = 5; function declared() {}",
         "eval('y = 1'); Object.assign(globalThis, {z: 1}); Function('w = 1')()");
   }

   @ParameterizedTest
   @MethodSource("corpus")
   void v4MatchesV0OnWarmSlots(String script) throws Exception {
      a = newSlot();
      b = newSlot();
      Value v0 = b.engine().context().eval(Source.newBuilder("js", V0, "<ws-clean-v0>")
                                               .buildLiteral());
      // a host var, expected by both helpers, for the -0 over 0 case
      a.applyOwn("zero", 0);
      b.applyOwn("zero", 0);
      v0.getMember("expect").execute("zero");
      Value checkA = verifier(a);
      Value checkB = verifier(b);

      // warm: the first clean builds v4's layout, so the cycles below take its fast path
      assertEquals(result(v0.getMember("clean").execute()), a.clean(), "warm-up clean");

      for(int cycle = 0; cycle < 3; cycle++) {
         run(a, script);
         run(b, script);
         CleanHelper.Result v4 = a.clean();
         CleanHelper.Result ref = result(v0.getMember("clean").execute());
         assertEquals(ref, v4, "cycle " + cycle + " of: " + script);

         if(!v4.reusable(256)) {
            break;
         }

         assertEquals("", checkA.execute().asString(), "v4 baseline, cycle " + cycle);
         assertEquals("", checkB.execute().asString(), "v0 baseline, cycle " + cycle);
      }
   }

   private static Slot newSlot() throws Exception {
      return Slot.create(new InitSnapshot("org0", Map.of("libFn", "function libFn(){ return 1; }")),
                         new EnvState().snapshot(), 0L, false,
                         Collections.synchronizedMap(new WeakHashMap<>()), new PoolMetrics());
   }

   private static void run(Slot slot, String script) throws Exception {
      WsEngine engine = slot.engine();
      engine.exec(engine.compile("try { " + script + " } catch(e) {} 1"), null, null);
   }

   private static CleanHelper.Result result(Value r) {
      return new CleanHelper.Result(r.getMember("leftovers").asInt(),
                                    r.getMember("failed").asBoolean(),
                                    r.getMember("restored").asInt(),
                                    r.getMember("removed").asInt(),
                                    r.getMember("tooMany").asBoolean());
   }

   /**
    * An independent baseline check, with its own captured builtins: every baseline key has its
    * baseline descriptor, and every other key is a non-configurable leftover whose value is
    * undefined. Returns '' when the global is at its baseline.
    */
   private static Value verifier(Slot slot) {
      return slot.engine().context().eval(Source.newBuilder("js", VERIFIER, "<ws-verify>")
                                              .buildLiteral());
   }

   private Slot a;
   private Slot b;

   private static final String VERIFIER = """
      (function () {
         'use strict';
         const G = globalThis;
         const ownKeys = Reflect.ownKeys;
         const gopd = Reflect.getOwnPropertyDescriptor;
         const is = Object.is;
         const hasOwn = Object.hasOwn;
         const create = Object.create;
         const str = String;
         const base = create(null);
         const keys = create(null);
         const descs = create(null);
         const initial = ownKeys(G);
         const n = initial.length;

         for(let i = 0; i < n; i++) {
            const k = initial[i];
            base[k] = true;
            keys[i] = k;
            descs[i] = gopd(G, k);
         }

         function same(e, d) {
            if(e.enumerable !== d.enumerable || e.configurable !== d.configurable) return false;
            if(hasOwn(e, 'value')) {
               return hasOwn(d, 'value') && e.writable === d.writable && is(e.value, d.value);
            }
            return !hasOwn(d, 'value') && is(e.get, d.get) && is(e.set, d.set);
         }

         return function check() {
            let bad = '';

            for(let i = 0; i < n; i++) {
               const d = gopd(G, keys[i]);
               if(d === undefined) bad += 'missing ' + str(keys[i]) + '; ';
               else if(!same(descs[i], d)) bad += 'changed ' + str(keys[i]) + '; ';
            }

            const now = ownKeys(G);
            for(let i = 0; i < now.length; i++) {
               const k = now[i];
               if(base[k] !== true) {
                  const d = gopd(G, k);
                  if(!(d !== undefined && !d.configurable && hasOwn(d, 'value') &&
                       d.value === undefined))
                  {
                     bad += 'foreign ' + str(k) + '; ';
                  }
               }
            }

            return bad;
         };
      })()
      """;

   /**
    * The clean helper before spec §14.14 (v0), kept as the parity reference.
    */
   private static final String V0 = """
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
