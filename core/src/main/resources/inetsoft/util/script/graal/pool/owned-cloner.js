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

// The structured cloner of lens-owned formula vars (Testing #77123, B1 residual part 2). It is
// evaluated once on each pooled worksheet context, when the context is created and before any
// library or user script runs, so every name it captures below is the context's intrinsic.
//
// It never runs user code:
// - snap reads only own property descriptors (Reflect.getOwnPropertyDescriptor / ownKeys),
//   refuses accessors, symbols, functions, Proxies (classified by the host without a trap)
//   and objects whose prototype is not a plain intrinsic one;
// - every write to an object it creates goes through the captured Reflect.defineProperty, or,
//   when Array.prototype and Object.prototype are clean (no index key, no accessor but the
//   intrinsic __proto__, the usual chain), a plain index write to an array of its own;
// - it reads its own JSON output with the captured JSON.parse (no reviver).
//
// host(batch): a string with one digit per object: 0 = ordinary, 1 = Proxy, 2 = host object
// (kept by reference), 3 = a Date (interop date), 4 = other foreign value, 5 = an object whose
// meta object is Date (an Invalid Date, or an object that inherits Date.prototype). keep(o) stores a host
// object and returns its index; kept(i) returns it. fail(i, kind) reports a root that is lost;
// dropped(i, what) a Date in root i that is kept as a plain Date from its time value only.
(function(host, keep, kept, fail, dropped) {
   'use strict';
   const R = Reflect, gOPD = R.getOwnPropertyDescriptor, ownKeys = R.ownKeys,
      getProto = R.getPrototypeOf, setProto = R.setPrototypeOf, isExt = R.isExtensible,
      defProp = R.defineProperty, preventExt = R.preventExtensions, isArray = Array.isArray,
      hasOwn = Object.hasOwn, create = Object.create, freeze = Object.freeze,
      seal = Object.seal,
      OP = Object.prototype, AP = Array.prototype, DP = Date.prototype, Dt = Date,
      now = Date.now, call = Function.prototype.call, jstr = JSON.stringify,
      jparse = JSON.parse, getTime = call.bind(DP.getTime), M = Map,
      mget = call.bind(M.prototype.get), mset = call.bind(M.prototype.set),
      mdel = call.bind(M.prototype.delete), Big = BigInt, strFn = String,
      charCode = call.bind(String.prototype.charCodeAt), join = call.bind(AP.join),
      SYM = 'symbol', STOP = freeze(create(null));
   const PROTO_KEY = '__proto__';

   // the prototypes of builtin classes, to name a value that is not kept
   const NAMED = new M();

   function name(ctor, what) {
      if(typeof ctor === 'function' && ctor.prototype) {
         mset(NAMED, ctor.prototype, what);
      }
   }

   name(Map, 'a Map'); name(Set, 'a Set'); name(WeakMap, 'a WeakMap');
   name(WeakSet, 'a WeakSet'); name(RegExp, 'a RegExp'); name(Promise, 'a Promise');
   name(Error, 'an Error'); name(TypeError, 'an Error'); name(RangeError, 'an Error');
   name(SyntaxError, 'an Error'); name(ReferenceError, 'an Error');
   name(EvalError, 'an Error'); name(URIError, 'an Error');
   name(Number, 'a Number object'); name(String, 'a String object');
   name(Boolean, 'a Boolean object'); name(ArrayBuffer, 'an ArrayBuffer');
   name(DataView, 'a DataView');
   name(Int8Array, 'a typed array'); name(Uint8Array, 'a typed array');
   name(Uint8ClampedArray, 'a typed array'); name(Int16Array, 'a typed array');
   name(Uint16Array, 'a typed array'); name(Int32Array, 'a typed array');
   name(Uint32Array, 'a typed array'); name(Float32Array, 'a typed array');
   name(Float64Array, 'a typed array');
   name(typeof BigInt64Array === 'function' ? BigInt64Array : null, 'a typed array');
   name(typeof BigUint64Array === 'function' ? BigUint64Array : null, 'a typed array');

   if(typeof Intl === 'object' && Intl !== null) {
      const intl = ['NumberFormat', 'DateTimeFormat', 'Collator', 'PluralRules',
                    'RelativeTimeFormat', 'ListFormat', 'Locale', 'Segmenter', 'DisplayNames'];

      for(let i = 0; i < intl.length; i++) {
         name(Intl[intl[i]], 'an Intl.' + intl[i] + ' object');
      }
   }

   function className(p) {
      const n = mget(NAMED, p);
      return n !== undefined ? n : 'an object of a class';
   }

   // plain writes to a fresh array are safe only if no index key or accessor (but the
   // intrinsic __proto__) is on the chain Array.prototype -> Object.prototype -> null
   function protoClean() {
      if(getProto(AP) !== OP || getProto(OP) !== null) {
         return false;
      }

      const ps = [AP, OP];

      for(let j = 0; j < 2; j++) {
         const ks = ownKeys(ps[j]);

         for(let i = 0; i < ks.length; i++) {
            const k = ks[i];

            if(typeof k === SYM) {
               continue;
            }

            if(k !== '' && (k >>> 0) + '' === k) {
               return false;
            }

            const d = gOPD(ps[j], k);

            if(!hasOwn(d, 'value') && !(ps[j] === OP && k === PROTO_KEY)) {
               return false;
            }
         }
      }

      return true;
   }

   function putter(clean) {
      return clean ? function(a, i, v) { a[i] = v; }
         : function(a, i, v) {
            defProp(a, i, { __proto__: null, value: v, writable: true, enumerable: true,
                            configurable: true });
         };
   }

   // roots (a host array of values) -> one JSON-like string. A root that cannot be kept is
   // reported with fail(i, kind) and encoded as undefined; the other roots are kept, with
   // aliases and cycles among them.
   function snap(roots, maxEntries, maxMillis) {
      const put = putter(protoClean());
      const deadline = now() + maxMillis;
      const ids = new M();
      const parts = [];
      const pending = [];
      const enc = [];
      let pl = 0, pn = 0, done = 0, entries = 0, steps = 0, why = '', hard = false, root = 0;

      function stop(k) {
         why = k;
         throw STOP;
      }

      // past the budget: never recovered below the root
      function stopHard(k) {
         hard = true;
         stop(k);
      }

      function tick(n) {
         entries += n;
         steps += n;

         if(entries > maxEntries) {
            stopHard('a value with more than ' + maxEntries + ' entries');
         }

         if(steps >= 2048) {
            steps = 0;

            if(now() > deadline) {
               stopHard('a value that took longer than ' + maxMillis + ' ms to save');
            }
         }
      }

      function out(s) {
         put(parts, pl, s);
         pl++;
      }

      function ref(o) {
         const id = mget(ids, o);

         if(id !== undefined) {
            return id;
         }

         const nid = pn;
         mset(ids, o, nid);
         put(pending, nid, o);
         pn = nid + 1;
         return nid;
      }

      function val(v) {
         const t = typeof v;

         if(t === 'number') {
            if(v !== v) return '[-2]';
            if(v === Infinity) return '[-3]';
            if(v === -Infinity) return '[-4]';
            if(v === 0 && 1 / v < 0) return '[-5]';
            return '' + v;
         }

         if(t === 'string') return jstr(v);
         if(t === 'boolean') return v ? 'true' : 'false';
         if(v === null) return 'null';
         if(t === 'undefined') return '[-1]';
         if(t === 'bigint') return '[-7,' + jstr(strFn(v)) + ']';
         if(t === SYM) stop('a symbol');
         if(t === 'function') stop('a function');
         return '[' + ref(v) + ']';
      }

      function props(o, keys, isArr) {
         const len = keys.length;

         for(let i = 0; i < len; i++) {
            const k = keys[i];

            if(typeof k === SYM) {
               stop('an object with a symbol key');
            }

            if(isArr && k === 'length') {
               continue;
            }

            const d = gOPD(o, k);

            if(!hasOwn(d, 'value')) {
               stop('an object with a getter or setter');
            }

            const v = val(d.value);
            const attrs = (d.writable ? 1 : 0) | (d.enumerable ? 2 : 0) |
               (d.configurable ? 4 : 0);
            out(',' + jstr(k) + ',' + (attrs === 7 ? v : '[-8,' + attrs + ',' + v + ']'));
            tick(1);
         }
      }

      function keysOf(o) {
         const keys = ownKeys(o);

         if(keys.length > maxEntries) {
            stopHard('a value with more than ' + maxEntries + ' entries');
         }

         return keys;
      }

      // the string keys of o, for a warning: "a, b, c and 17 more"
      function names(keys) {
         const list = [];
         let n = 0, more = 0;

         for(let i = 0; i < keys.length; i++) {
            if(typeof keys[i] !== SYM) {
               if(n < 8) {
                  put(list, n++, keys[i]);
               }
               else {
                  more++;
               }
            }
         }

         return join(list, ', ') + (more ? ' and ' + more + ' more' : '');
      }

      // a Date: type 3 (Date.prototype) or 6 (null prototype). One of a subclass, or with an
      // own property that cannot be kept (a function, an accessor...), is kept as a plain
      // Date from its time value, as the batch-end Date snapshot does, and reported
      function dateNode(o, p, ext, t) {
         const head = '[' + (p === null ? 6 : 3) + ',' + ext + ',' + (t !== t ? '"NaN"' : t);
         const keys = keysOf(o);
         const pl0 = pl, pn0 = pn, entries0 = entries;
         out(head);

         if(p !== DP && p !== null) {
            dropped(root, keys.length ? 'of a subclass and with the properties ' + names(keys)
               : 'of a subclass');
         }
         else {
            try {
               props(o, keys, false);
            }
            catch(e) {
               if(e !== STOP || hard) {
                  throw e;
               }

               // roll the Date's properties back: the objects they found are new
               for(let k = pn0; k < pn; k++) {
                  mdel(ids, pending[k]);
               }

               pn = pn0;
               pl = pl0;
               entries = entries0;
               out(head);
               dropped(root, 'with the properties ' + names(keys));
            }
         }

         out(']');
      }

      function node(o, h) {
         const p = getProto(o);
         const ext = isExt(o) ? 0 : 1;

         if(h === 3) {
            dateNode(o, p, ext, getTime(o));
            return;
         }

         if(h === 5 && p !== DP && p !== null) {
            // an Invalid Date of a subclass, or an object that inherits Date.prototype
            let t;

            try {
               t = getTime(o);
            }
            catch(e) {
               stop('an object that inherits Date.prototype but is no Date');
            }

            dateNode(o, p, ext, t);
            return;
         }

         if(isArray(o)) {
            if(p !== AP) {
               stop('an array of a subclass');
            }

            const ld = gOPD(o, 'length');
            out('[1,' + ext + ',' + ld.value + ',' + (ld.writable ? 1 : 0));
            props(o, keysOf(o), true);
            out(']');
            return;
         }

         if(p === DP || p === null) {
            // an Invalid Date is no interop date: only the Date internal slot tells it from
            // an object that inherits Date.prototype (or a null-prototype object)
            let t;
            let isDate = true;

            try {
               t = getTime(o);
            }
            catch(e) {
               isDate = false;
            }

            if(isDate) {
               dateNode(o, p, ext, t);
               return;
            }

            if(p === DP) {
               stop('an object that inherits Date.prototype but is no Date');
            }
         }

         if(p === OP || p === null) {
            out('[' + (p === null ? 4 : 0) + ',' + ext);
            props(o, keysOf(o), false);
            out(']');
            return;
         }

         stop(className(p));
      }

      function drain() {
         while(done < pn) {
            const end = pn;
            const batch = [];

            for(let i = done; i < end; i++) {
               put(batch, i - done, pending[i]);
            }

            const kinds = host(batch);

            for(let i = done; i < end; i++) {
               const h = charCode(kinds, i - done) - 48;
               out(i ? ',' : '');

               if(h === 1) {
                  stop('a Proxy object');
               }
               else if(h === 2) {
                  out('[5,' + keep(pending[i]) + ']');
               }
               else if(h === 4) {
                  stop('an object of another script engine');
               }
               else {
                  node(pending[i], h);
               }

               done = i + 1;
            }
         }
      }

      out('{"n":[');
      const rl = roots.length;

      for(let i = 0; i < rl; i++) {
         const pn0 = pn, pl0 = pl, entries0 = entries;
         root = i;

         try {
            put(enc, i, val(roots[i]));
            drain();
         }
         catch(e) {
            if(e !== STOP) {
               throw e;
            }

            // roll this root back: its new objects, its output
            for(let k = pn0; k < pn; k++) {
               mdel(ids, pending[k]);
            }

            pn = pn0;
            done = pn0;
            pl = pl0;
            entries = entries0;
            hard = false;
            put(enc, i, '[-1]');
            fail(i, why);

            // out of time: the roots after it are lost too
            if(now() > deadline) {
               for(let j = i + 1; j < rl; j++) {
                  put(enc, j, '[-1]');
                  fail(j, why);
               }

               break;
            }
         }
      }

      out('],"r":[');

      for(let i = 0; i < rl; i++) {
         out((i ? ',' : '') + enc[i]);
      }

      out(']}');
      // drop what a rollback left past the end
      defProp(parts, 'length', { __proto__: null, value: pl });
      return join(parts, '');
   }

   // the string of snap -> an array of the root values, built in this context
   function build(str, maxMillis) {
      const put = putter(protoClean());
      const deadline = now() + maxMillis;
      const t = jparse(str);
      const nodes = t.n, roots = t.r, nl = nodes.length;
      const made = [];
      let steps = 0;

      for(let i = 0; i < nl; i++) {
         const nd = nodes[i];
         const ty = nd[0];
         let o;

         if(ty === 5) {
            o = kept(nd[1]);
         }
         else if(ty === 1) {
            o = [];
         }
         else if(ty === 4) {
            o = create(null);
         }
         else if(ty === 3 || ty === 6) {
            o = new Dt(nd[2] === 'NaN' ? NaN : nd[2]);

            if(ty === 6) {
               setProto(o, null);
            }
         }
         else {
            o = {};
         }

         put(made, i, o);
      }

      function dec(v) {
         if(typeof v !== 'object' || v === null) return v;

         const a = v[0];

         if(a >= 0) return made[a];
         if(a === -2) return NaN;
         if(a === -3) return Infinity;
         if(a === -4) return -Infinity;
         if(a === -5) return -0;
         if(a === -7) return Big(v[1]);
         return undefined;
      }

      for(let i = 0; i < nl; i++) {
         const nd = nodes[i];
         const ty = nd[0];

         if(ty === 5) {
            continue;
         }

         const o = made[i];
         const start = ty === 1 ? 4 : ty === 3 || ty === 6 ? 3 : 2;
         const len = nd.length;
         // whether every property is non-configurable (sealed) and non-writable (frozen)
         let sealed = true, frozen = true;

         for(let j = start; j < len; j += 2) {
            let v = nd[j + 1];
            let attrs = 7;

            if(typeof v === 'object' && v !== null && v[0] === -8) {
               attrs = v[1];
               v = v[2];
            }

            sealed = sealed && (attrs & 4) === 0;
            frozen = frozen && (attrs & 5) === 0;

            defProp(o, nd[j], { __proto__: null, value: dec(v), writable: (attrs & 1) !== 0,
                                enumerable: (attrs & 2) !== 0,
                                configurable: (attrs & 4) !== 0 });

            if(++steps >= 2048) {
               steps = 0;

               if(now() > deadline) {
                  throw STOP;
               }
            }
         }

         if(ty === 1) {
            defProp(o, 'length', { __proto__: null, value: nd[2], writable: nd[3] === 1 });
            frozen = frozen && nd[3] !== 1;
         }

         // a non-extensible object: freeze or seal it when its properties say so (an array
         // frozen property by property is not reported frozen by this engine)
         if(nd[1] === 1) {
            if(frozen) {
               freeze(o);
            }
            else if(sealed) {
               seal(o);
            }
            else {
               preventExt(o);
            }
         }
      }

      const res = [];

      for(let i = 0; i < roots.length; i++) {
         put(res, i, dec(roots[i]));
      }

      return res;
   }

   return freeze({ __proto__: null, snap: snap, build: build, proxy: new Proxy({}, {}) });
})
