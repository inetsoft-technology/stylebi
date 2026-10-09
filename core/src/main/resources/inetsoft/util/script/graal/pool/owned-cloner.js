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
// object and returns its index; kept(i) returns it. fail(i, kind, hides) reports a root that is
// lost, hides true if its graph holds a value whose references the cloner cannot list (see
// snap); dropped(i, what) a Date in root i that is kept as a plain Date from its time value only;
// copied(i), only if a lost root hides, a kept root that holds an array or object other than a
// Date without objects (one a lost root may have reached unseen: see snap).
//
// The snapshot format: {"n": [node...], "r": [value per root]}. A node is
// [0, ext, key, value...] a plain object; [4, ext, key, value...] one with a null prototype;
// [1, ext, length, lengthWritable, key, value...] an array; [3, ext, time, key, value...] a
// Date ([6, ...] with a null prototype; time "NaN" for an Invalid Date); [5, i] a host object
// kept by reference (kept(i)). ext is 1 for a non-extensible object. A value is a JSON number,
// string, boolean or null, [n] node n, [-1] undefined, [-2] NaN, [-3] Infinity, [-4] -Infinity,
// [-5] -0, [-7, "digits"] a bigint, or [-8, attrs, value] a property whose attributes are not
// all set (1 writable, 2 enumerable, 4 configurable).
(function(host, keep, kept, fail, dropped, copied) {
   'use strict';
   const R = Reflect, gOPD = R.getOwnPropertyDescriptor, ownKeys = R.ownKeys,
      getProto = R.getPrototypeOf, setProto = R.setPrototypeOf, isExt = R.isExtensible,
      defProp = R.defineProperty, preventExt = R.preventExtensions, isArray = Array.isArray,
      isView = ArrayBuffer.isView,
      hasOwn = Object.hasOwn, create = Object.create, freeze = Object.freeze,
      seal = Object.seal,
      OP = Object.prototype, AP = Array.prototype, DP = Date.prototype, Dt = Date,
      now = Date.now, call = Function.prototype.call, jstr = JSON.stringify,
      jparse = JSON.parse, getTime = call.bind(DP.getTime), M = Map,
      mget = call.bind(M.prototype.get), mset = call.bind(M.prototype.set),
      mdel = call.bind(M.prototype.delete), Big = BigInt, strFn = String,
      MP = M.prototype, SP = Set.prototype, mapEach = call.bind(MP.forEach),
      setEach = call.bind(SP.forEach),
      charCode = call.bind(String.prototype.charCodeAt), join = call.bind(AP.join),
      SYM = 'symbol', STOP = freeze(create(null));
   const PROTO_KEY = '__proto__';
   // the most objects one host call classifies while the lost roots are marked
   const MARK_SLICE = 2048;

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

   // what a root that shares an object with a lost root is lost for (amendment A3)
   const SHARED = 'an object that it shares with a variable whose value is not kept';
   // the builtins whose objects hold references that no own property lists
   const OPAQUE = new M();
   mset(OPAQUE, WeakMap.prototype, true); mset(OPAQUE, WeakSet.prototype, true);
   mset(OPAQUE, Promise.prototype, true);

   // whether an object with the prototype p may hold references that marking does not see
   function opaqueProto(p) {
      return p !== OP && p !== AP && p !== MP && p !== SP && p !== DP && p !== null &&
         (mget(NAMED, p) === undefined || mget(OPAQUE, p) === true);
   }

   const UNREAD = 'a value that could not be read';
   const INTERRUPTED = 'Thread was interrupted.';

   // an interrupt of the thread (a caller's cancel, or a timeout's), which Graal raises at a
   // guest safepoint as an error that a catch here sees, and whose flag it clears: never
   // taken for a value that could not be read, it stops the snapshot (Testing #77123). Only
   // Graal throws here (no script code runs), and it tells the interrupt by its message.
   // Graal's error is no script object (Reflect refuses it), so its message is an interop
   // read, which runs no script code; a script error's own message is read with the captured
   // getOwnPropertyDescriptor, which runs no getter. The reads here can be interrupted
   // themselves: that interrupt stops the snapshot too, and a read that fails otherwise
   // leaves e what it was (a value that could not be read)
   function keepInterrupt(e) {
      if(e === STOP || typeof e !== 'object' || e === null) {
         return;
      }

      let d;

      try {
         d = gOPD(e, 'message');
      }
      catch(x) {
         rethrowInterrupt(x);
         let m;

         try {
            m = e.message;
         }
         catch(y) {
            rethrowInterrupt(y);
            return;
         }

         if(m === INTERRUPTED) {
            throw e;
         }

         return;
      }

      if(d !== undefined && d.value === INTERRUPTED) {
         throw e;
      }
   }

   // throws x, an error of one of keepInterrupt's reads, when it is the thread's interrupt.
   // It tells it the same way, and a read of its own that fails is taken as no interrupt
   function rethrowInterrupt(x) {
      if(x === STOP || typeof x !== 'object' || x === null) {
         return;
      }

      let m;

      try {
         const d = gOPD(x, 'message');
         m = d === undefined ? undefined : d.value;
      }
      catch(z) {
         try {
            m = x.message;
         }
         catch(w) {
            return;
         }
      }

      if(m === INTERRUPTED) {
         throw x;
      }
   }

   // roots (a host array of values) -> one JSON-like string. A root that cannot be kept is
   // reported with fail(i, kind) and encoded as undefined, and so is every root that shares an
   // object with it (A3: a kept alias into a lost value would be stale once the lost var is
   // created again), each with its own kind. The other roots are kept, with aliases and cycles
   // among them. Past the time bound every root is lost: sharing can no longer be checked.
   //
   // It runs passes until one loses no new root; each lost root's object graph is marked
   // once, for every later pass (own data properties only, of objects and functions, so no
   // getter runs; a Proxy (a callable one too), a host object or a foreign object is marked
   // but not entered, as the host tells them apart without a trap: a lost var's init creates
   // a new host object, so a kept alias to the old one would split; a typed array or DataView
   // is marked but not entered, its elements are primitives; the entries of a Map or Set are
   // marked, with the intrinsic forEach). Marking counts against its own budget (maxMarks,
   // a multiple of the entry cap) and the time bound: past either every root is lost, as
   // sharing can no longer be checked.
   // An alias only through a closure, a getter, a WeakMap or WeakSet, a Map or Set of a
   // subclass or a typed array's named property is not seen. Such a lost root is reported
   // with hides: a function (its closure, a bound target), a Proxy, an accessor, or an object
   // of a class or of a builtin that holds hidden references (a WeakMap, a WeakSet, a
   // Promise) in its graph. The host then names the kept roots reported by copied(), which
   // may be stale copies of what it reached (Testing #77123, B1 residual): a root that holds
   // an array or object. A bigint, or a Date whose properties hold no object, is a value that
   // no closure can share, and is not reported (review L1).
   function snap(roots, maxEntries, maxMillis, maxMarks) {
      const put = putter(protoClean());
      const deadline = now() + maxMillis;
      // OwnedValueCodec.isBudgetKind matches the start of TIME, MARKS and the entry cap's kind
      const TIME = 'a value that took longer than ' + maxMillis + ' ms to save';
      const MARKS = 'a value that could not be checked for an object shared with a ' +
         'variable whose value is not kept, which has more than ' + maxMarks + ' entries';
      const rl = roots.length;
      // why each root is lost, or undefined while it is kept (no hole: never read through
      // the prototype)
      const lost = [];
      // whether each lost root's graph is marked (once for all passes)
      const marked = [];
      // whether each lost root's graph holds a value whose references are not listed
      const hides = [];
      // the objects of the lost roots' graphs (all passes) -> the lost root
      const tainted = new M();
      let nlost = 0, late = false, changed = false, marks = 0;

      for(let i = 0; i < rl; i++) {
         put(lost, i, undefined);
         put(marked, i, false);
         put(hides, i, false);
      }

      // the state of one pass
      let ids, parts, pending, enc, own;
      // the Dates of this pass -> whether their kept properties refer to an object
      let dates;
      // the object references encoded so far (all passes)
      let nrefs = 0;
      let pl = 0, pn = 0, done = 0, entries = 0, steps = 0, why = '', hard = false, root = 0;

      function lose(i, k) {
         if(lost[i] === undefined) {
            put(lost, i, k);
            nlost++;
            changed = true;
         }
      }

      function stop(k) {
         why = k;
         throw STOP;
      }

      // past the budget: never recovered below the root
      function stopHard(k) {
         hard = true;
         stop(k);
      }

      function checkTime() {
         if(now() > deadline) {
            late = true;
            stopHard(TIME);
         }
      }

      function tick(n) {
         entries += n;
         steps += n;

         if(entries > maxEntries) {
            stopHard('a value with more than ' + maxEntries + ' entries');
         }

         if(steps >= 2048) {
            steps = 0;
            checkTime();
         }
      }

      function out(s) {
         put(parts, pl, s);
         pl++;
      }

      function ref(o) {
         nrefs++;
         const id = mget(ids, o);

         if(id !== undefined) {
            return id;
         }

         if(mget(tainted, o) !== undefined) {
            stop(SHARED);
         }

         const nid = pn;
         mset(ids, o, nid);
         put(pending, nid, o);
         put(own, nid, root);
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
         const pl0 = pl, pn0 = pn, entries0 = entries, r0 = nrefs;
         out(head);
         mset(dates, o, false);

         if(p !== DP && p !== null) {
            dropped(root, keys.length ? 'of a subclass and with the properties ' + names(keys)
               : 'of a subclass');
         }
         else {
            try {
               props(o, keys, false);
               mset(dates, o, nrefs > r0);
            }
            catch(e) {
               // an object shared with a lost root loses the whole root, not the property
               if(e !== STOP || hard || why === SHARED) {
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
               keepInterrupt(e);
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
               keepInterrupt(e);
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
            // the first object of this batch: done moves on in the loop below
            const base = done;
            const batch = [];

            for(let i = base; i < end; i++) {
               put(batch, i - base, pending[i]);
            }

            const kinds = host(batch);

            for(let i = base; i < end; i++) {
               const h = charCode(kinds, i - base) - 48;
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

      // an object or a function (whose own data properties are marked too)
      function isObj(x) {
         return x !== null && (typeof x === 'object' || typeof x === 'function');
      }

      // n more entries walked to mark the lost roots, against the marking budget of the
      // hand-off
      function markTick(n) {
         marks += n;
         steps += n;

         if(marks > maxMarks) {
            stopHard(MARKS);
         }

         if(steps >= 2048) {
            steps = 0;
            checkTime();
         }
      }

      // mark the object graph of the lost root j: a kept root of this pass that holds one of
      // its objects is lost too, and a later root that reaches one is lost in ref()
      function mark(v, j) {
         if(!isObj(v)) {
            return;
         }

         let front = [v];
         let fl = 1;

         while(fl > 0) {
            const next = [];
            let nl = 0;
            // the kinds of the slice of front that starts at kb and has kl objects
            let kinds = '', kb = 0, kl = 0;

            for(let k = 0; k < fl; k++) {
               // the host classifies the front in slices, so the time bound is checked
               // between them
               if(k - kb >= kl) {
                  const end = fl - k > MARK_SLICE ? k + MARK_SLICE : fl;
                  const slice = [];

                  for(let q = k; q < end; q++) {
                     put(slice, q - k, front[q]);
                  }

                  kinds = host(slice);
                  kb = k;
                  kl = end - k;
                  checkTime();
               }

               const o = front[k];
               const h = charCode(kinds, k - kb) - 48;
               const owner = mget(tainted, o);

               if(owner !== undefined) {
                  // another lost root's graph: it may hide what this one reaches
                  if(hides[owner]) {
                     put(hides, j, true);
                  }

                  continue;
               }

               mset(tainted, o, j);
               const id = mget(ids, o);

               if(id !== undefined) {
                  lose(own[id], SHARED);
               }

               // a Proxy (its target and handler are not listed: it hides), a host object, a
               // value of another engine: marked, not entered; a typed array or DataView holds
               // only primitives: never list its elements
               if(h === 1) {
                  put(hides, j, true);
               }

               if(h === 1 || h === 2 || h === 4 || isView(o)) {
                  continue;
               }

               // no Proxy here: reading the prototype runs no trap
               const p = getProto(o);

               if(typeof o === 'function' || opaqueProto(p)) {
                  put(hides, j, true);
               }

               // an array too long for the cap is refused before its keys are listed
               if(isArray(o)) {
                  const len = gOPD(o, 'length').value;

                  if(marks + len > maxMarks) {
                     stopHard(MARKS);
                  }
               }

               const keys = ownKeys(o);

               for(let q = 0; q < keys.length; q++) {
                  const d = gOPD(o, keys[q]);

                  if(d !== undefined && hasOwn(d, 'value')) {
                     const x = d.value;

                     if(isObj(x) && mget(tainted, x) === undefined) {
                        put(next, nl++, x);
                     }
                  }
                  else if(d !== undefined) {
                     // a getter or setter: what its closure reaches is not listed
                     put(hides, j, true);
                  }

                  markTick(1);
               }

               // a Map's keys and values, a Set's values (the intrinsic forEach of a Map or Set
               // runs no user code; one of a subclass is not entered)
               if(p === MP || p === SP) {
                  (p === MP ? mapEach : setEach)(o, function(x, y) {
                     if(isObj(x) && mget(tainted, x) === undefined) {
                        put(next, nl++, x);
                     }

                     if(p === MP && isObj(y) && mget(tainted, y) === undefined) {
                        put(next, nl++, y);
                     }

                     markTick(1);
                  });
               }
            }

            front = next;
            fl = nl;
         }
      }

      // mark the lost root j once, while another root is kept. Anything that stops it loses
      // every root, as sharing cannot be checked (a time-out: at the end, as TIME)
      function markLost(j) {
         if(nlost >= rl || marked[j]) {
            return;
         }

         put(marked, j, true);

         try {
            mark(roots[j], j);
         }
         catch(e) {
            keepInterrupt(e);
            const k = e === STOP ? why : UNREAD;
            hard = false;

            if(!late) {
               for(let r = 0; r < rl; r++) {
                  lose(r, k);
               }
            }
         }
      }

      function encode(i) {
         const pn0 = pn, pl0 = pl, entries0 = entries;
         root = i;

         try {
            put(enc, i, val(roots[i]));
            drain();
         }
         catch(e) {
            keepInterrupt(e);
            // anything but a refusal (e.g. a value that could not be read) loses this root only
            const k = e === STOP ? why : UNREAD;

            // roll this root back: its new objects, its output
            for(let q = pn0; q < pn; q++) {
               mdel(ids, pending[q]);
            }

            pn = pn0;
            done = pn0;
            pl = pl0;
            entries = entries0;
            hard = false;
            put(enc, i, '[-1]');
            lose(i, k);

            if(now() > deadline) {
               late = true;
            }

            if(!late) {
               markLost(i);
            }
         }
      }

      function pass() {
         ids = new M();
         dates = new M();
         parts = [];
         pending = [];
         enc = [];
         own = [];
         pl = 0;
         pn = 0;
         done = 0;
         entries = 0;
         steps = 0;
         hard = false;
         changed = false;

         for(let j = 0; j < rl && !late; j++) {
            if(lost[j] !== undefined) {
               markLost(j);
            }
         }

         out('{"n":[');

         for(let i = 0; i < rl && !late; i++) {
            if(lost[i] === undefined) {
               encode(i);
            }
            else {
               put(enc, i, '[-1]');
            }
         }
      }

      do {
         pass();
      }
      while(changed && !late && nlost < rl);

      if(late || nlost >= rl) {
         // nothing is kept: no node
         pl = 0;
         out('{"n":[');

         for(let i = 0; i < rl; i++) {
            lose(i, TIME);
            put(enc, i, '[-1]');
         }
      }

      let hidden = false;

      for(let i = 0; i < rl; i++) {
         if(lost[i] !== undefined) {
            fail(i, lost[i], hides[i]);
            hidden = hidden || hides[i];
         }
      }

      // the kept roots that hold a copied array or object, for a lost root that hides
      for(let i = 0; i < rl && hidden; i++) {
         if(lost[i] === undefined && isObj(roots[i]) && mget(dates, roots[i]) !== false) {
            copied(i);
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
