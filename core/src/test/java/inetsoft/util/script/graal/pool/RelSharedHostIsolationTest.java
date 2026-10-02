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
package inetsoft.util.script.graal.pool;

import inetsoft.report.StyleConstants;
import inetsoft.report.composition.region.ChartConstants;
import inetsoft.util.script.graal.*;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Testing #77123, round 2 (seat r2-share): the host objects #5885 shares JVM-wide (CALC, the
 * ScriptFunction globals, the Chart / StyleConstant constant scopes) must not carry any write of
 * one sandbox, user or org to another. Each write path runs in sandbox A, then the observable
 * state of every shared object is compared with the one of a context that never saw a write:
 * in A's later claims, in A's other pooled context, in another sandbox of the same org, in
 * another org's sandbox, and, pool off, in another engine.
 *
 * <p>The constant scopes hold immutable values only: an array or a {@code Size} constant is a
 * fresh copy per read, so a write to it changes only the script's copy, and a constant that is
 * any other object is not a member.
 */
@Tag("core")
class RelSharedHostIsolationTest {
   /** The shared objects, by the expression that reaches each one from a script. */
   private static final String[] TARGETS = {
      "CALC", "StyleConstant", "Chart", "CALC.sum", "CALC.proper", "sum", "abs", "isNull",
      "dateAdd", "formatNumber"
   };

   /**
    * Everything a script can observe of the shared objects: the shape of each target, every
    * primitive member of the constant scopes, the CALC member names and the results of the
    * shared functions.
    */
   private static final String PROBE = """
      (function() {
         var out = [];
         var targets = { CALC: CALC, StyleConstant: StyleConstant, Chart: Chart,
            'CALC.sum': CALC.sum, 'CALC.proper': CALC.proper, sum: sum, abs: abs,
            isNull: isNull, dateAdd: dateAdd, formatNumber: formatNumber };
         var names = ['x', 'foo', 'sum', 'PORTRAIT', 'prototype', 'q', 'toString'];

         for(var t in targets) {
            var o = targets[t];
            var s = t + ':' + typeof o;

            try { s += ',ext=' + Object.isExtensible(o); } catch(e) { s += ',ext!' + e.name; }
            try { s += ',frz=' + Object.isFrozen(o); } catch(e) { s += ',frz!' + e.name; }
            try { s += ',sld=' + Object.isSealed(o); } catch(e) { s += ',sld!' + e.name; }
            try { s += ',proto=' + typeof Object.getPrototypeOf(o); }
            catch(e) { s += ',proto!' + e.name; }
            try { s += ',keys=' + Object.keys(o).length; } catch(e) { s += ',keys!' + e.name; }

            for(var i = 0; i < names.length; i++) {
               try { s += ',' + names[i] + '=' + typeof o[names[i]]; }
               catch(e) { s += ',' + names[i] + '!' + e.name; }
            }

            out.push(s);
         }

         var scopes = [StyleConstant, Chart];

         for(var j = 0; j < scopes.length; j++) {
            var keys = Object.keys(scopes[j]);
            var vals = [];

            for(var k = 0; k < keys.length; k++) {
               var v = scopes[j][keys[k]];

               if(v == null || typeof v != 'object' && typeof v != 'function') {
                  vals.push(keys[k] + '=' + v);
               }
            }

            out.push(vals.join(';'));
         }

         out.push(Object.keys(CALC).join(','));
         out.push([CALC.sum([1, 2, 3]), sum([4, 5]), CALC.average([1, 2, 3]),
            CALC.proper('ab cd'), abs(-3), isNull(null), isNull(1),
            formatNumber(1234.5, '#,##0.00'), dateAdd('day', 1, new Date(0)).getTime(),
            CALC.max([3, 9, 1]), CALC.len('abc')].join('|'));
         return out.join('\\n');
      })()
      """;

   /**
    * One try per write path on each target: property put, nested put, delete, the Object and
    * Reflect meta operations, prototype change and the integrity levels.
    */
   private static final String WRITES = """
      (function() {
         var targets = [%s];
         var ops = [
            function(o) { o.x = 1; },
            function(o) { o.PORTRAIT = 99; },
            function(o) { o.sum = 42; },
            function(o) { o.toString = 'x'; },
            function(o) { o.prototype = { q: 1 }; },
            function(o) { o.prototype.q = 1; },
            function(o) { o.name = 'q'; },
            function(o) { o.length = 9; },
            function(o) { o.sum.foo = 2; },
            function(o) { o.sum.prototype = {}; },
            function(o) { delete o.sum; },
            function(o) { delete o.PORTRAIT; },
            function(o) { delete o.toString; },
            function(o) { Object.defineProperty(o, 'x', { value: 1 }); },
            function(o) { Object.defineProperty(o, 'PORTRAIT', { value: 9 }); },
            function(o) { Object.defineProperty(o, 'sum', { get: function() { return 1; } }); },
            function(o) { Object.defineProperties(o, { q: { value: 1 } }); },
            function(o) { Object.assign(o, { x: 1, PORTRAIT: 9 }); },
            function(o) { Object.setPrototypeOf(o, { q: 1 }); },
            function(o) { o.__proto__ = { q: 1 }; },
            function(o) { Object.preventExtensions(o); },
            function(o) { Object.seal(o); },
            function(o) { Object.freeze(o); },
            function(o) { Reflect.set(o, 'x', 1); },
            function(o) { Reflect.defineProperty(o, 'x', { value: 1 }); },
            function(o) { Reflect.deleteProperty(o, 'sum'); },
            function(o) { Reflect.deleteProperty(o, 'PORTRAIT'); },
            function(o) { Reflect.setPrototypeOf(o, { q: 1 }); },
            function(o) { Reflect.preventExtensions(o); },
            function(o) { Object.keys(o).push('x'); },
            function(o) { Object.keys(o).length = 0; },
            function(o) { Object.getOwnPropertyNames(o).length = 0; },
            function(o) { for(var k in o) { o[k] = 0; } },
            function(o) { var d = Object.getOwnPropertyDescriptor(o, 'PORTRAIT'); d.value = 9; }
         ];
         var threw = 0;

         for(var i = 0; i < targets.length; i++) {
            for(var j = 0; j < ops.length; j++) {
               try { ops[j](targets[i]); } catch(e) { threw++; }
            }
         }

         return threw;
      })()
      """.formatted(String.join(", ", TARGETS));

   /**
    * Patches of the built-in prototypes and globals that the shared objects' methods (the
    * host-to-guest conversion of their arguments and results) could consult. Per Context by
    * construction (B7); they must never reach another context.
    */
   private static final String PROTO_PATCHES = """
      Array.prototype.join = function() { return 'patched'; };
      Array.prototype.reduce = function() { return -1; };
      Array.prototype.map = function() { return []; };
      Array.prototype[Symbol.iterator] = function* () { yield 999; };
      Object.prototype.valueOf = function() { return 13; };
      Object.prototype.toString = function() { return 'patched'; };
      Object.prototype.x = 'patched';
      Object.prototype.PORTRAIT = 'patched';
      Number.prototype.valueOf = function() { return 7; };
      String.prototype.toString = function() { return 'patched'; };
      Function.prototype.call = function() { return 'patched'; };
      Function.prototype.apply = function() { return 'patched'; };
      Function.prototype.x = 'patched';
      Date.prototype.getTime = function() { return 5; };
      JSON.stringify = function() { return 'patched'; };
      Math.abs = function() { return -1; };
      // the shared functions run once over the patched prototypes
      [CALC.sum([1, 2, 3]), sum([4, 5]), CALC.proper('ab'), abs(-3), isNull(null),
       formatNumber(1.5, '0.0'), dateAdd('day', 1, new Date(0))].length
      """;

   /** The constant kinds the scopes hand out as a fresh copy per read (see the class doc). */
   private static boolean isCopiedValue(Object value) {
      return value instanceof int[] || value instanceof inetsoft.report.Size;
   }

   private static boolean isImmutableValue(Object value) {
      return value == null || value instanceof String || value instanceof Boolean ||
         value instanceof Character || value instanceof Integer || value instanceof Long ||
         value instanceof Double || value instanceof Float || value instanceof Short ||
         value instanceof Byte || value instanceof Enum;
   }

   private static String pristine;
   private final List<GraalJavaScriptEngine> engines = new ArrayList<>();

   @BeforeAll
   static void pristineProbe() throws Exception {
      // a context that never saw a write
      pristine = (String) run(env("fresh"), PROBE);
   }

   @AfterEach
   void closeEngines() {
      engines.forEach(GraalJavaScriptEngine::close);
      engines.clear();
   }

   private static WorksheetScriptEnv env(String org) {
      return new WorksheetScriptEnv(PoolConfig.defaults(), new InitSnapshot(org, Map.of()));
   }

   private GraalJavaScriptEngine engine() throws Exception {
      GraalJavaScriptEngine engine = new GraalJavaScriptEngine();
      engine.init(new HashMap<>());
      engines.add(engine);
      return engine;
   }

   private static Object eval(GraalJavaScriptEngine engine, String src) throws Exception {
      return engine.exec(engine.compile(src), null, null);
   }

   /** The paths that must leave no trace anywhere, not even in the writing context. */
   private void assertWritesInvisible(String writes) throws Exception {
      assertWritesInvisible(writes, false);
   }

   /**
    * @param inScope run the writes with an exec scope, so unqualified names go through the
    *                scope proxy (BindingRootProxy).
    */
   private void assertWritesInvisible(String writes, boolean inScope) throws Exception {
      // pool on: A has two pooled contexts; A2 is another sandbox of A's org, B another org
      WorksheetScriptEnv a = env("orgA");
      WorksheetScriptEnv a2 = env("orgA");
      WorksheetScriptEnv b = env("orgB");
      run(a2, "1");
      run(b, "1");

      whileHeldElsewhere(a, () -> {
         if(inScope) {
            a.exec(a.compile(writes), new MapScope(), null, null);
         }
         else {
            run(a, writes);
         }

         // the writing context's later claim, after its clean
         assertEquals(pristine, run(a, PROBE), "A's later claim");
      });

      assertEquals(2, a.getMetrics().getSize());
      assertEquals(pristine, run(a, PROBE), "A's other context");
      whileHeldElsewhere(a, () -> assertEquals(pristine, run(a, PROBE), "A's writing context"));
      assertEquals(pristine, run(a2, PROBE), "another sandbox of A's org");
      assertEquals(pristine, run(b, PROBE), "another org");

      // pool off: the same objects are shared by every engine
      GraalJavaScriptEngine ea = engine();
      GraalJavaScriptEngine eb = engine();
      ea.exec(ea.compile(writes), inScope ? new MapScope() : null, null);

      if(inScope) {
         // pool off, a name with a global binding is rebound on the writing engine's own
         // global (per Context, as SharedHostObjectsTest.globalRebindingStaysPerContext);
         // the shared object behind it is untouched
         assertEquals(6.0, ((Number) eval(ea, "CALC.sum([1, 2, 3])")).doubleValue());
         assertEquals("Ab", eval(ea, "CALC.proper('ab')"));
      }
      else {
         assertEquals(pristine, eval(ea, PROBE), "pool off, the writing engine");
      }

      assertEquals(pristine, eval(eb, PROBE), "pool off, another engine");
      assertEquals(pristine, run(b, PROBE), "another org after pool-off writes");
   }

   @Test
   void pristineProbeSeesEveryTarget() {
      for(String target : TARGETS) {
         assertTrue(pristine.contains(target + ":"), target);
      }

      // the shared objects are live in the probe
      assertTrue(pristine.contains("CALC:object"), pristine);
      assertTrue(pristine.contains("PORTRAIT=1"), pristine);
      assertTrue(pristine.contains("6|9|2|Ab Cd|3|true|false|1,234.50|86400000|9|3"), pristine);
   }

   /** Property put, nested put, delete, Object/Reflect meta operations and integrity levels. */
   @Test
   void everyMetaWritePathOnEverySharedObjectIsInvisible() throws Exception {
      // the paths are really exercised: most of them throw a TypeError, none may leak
      WorksheetScriptEnv probe = env("orgP");
      Object threw = run(probe, WRITES);
      assertTrue(((Number) threw).intValue() > 0, "some write paths throw: " + threw);
      assertWritesInvisible(WRITES);
   }

   /** A shared object stored in a Java collection and read back is still the guarded proxy. */
   @Test
   void sharedObjectsRoundTrippedThroughHostCollectionsStayGuarded() throws Exception {
      String writes = """
         var L = Java.type('java.util.ArrayList');
         var M = Java.type('java.util.HashMap');
         var l = new L();
         var m = new M();
         [CALC, StyleConstant, Chart, sum, CALC.sum].forEach(function(o) { l.add(o); });
         m.put('k', StyleConstant);
         var hidden = 0;

         for(var i = 0; i < l.size(); i++) {
            var o = l.get(i);
            // the scope implementations' own Java methods are not reachable from a script
            if(typeof o.putConstant == 'undefined' && typeof o.setParentScope == 'undefined' &&
               typeof o.getScope == 'undefined') hidden++;
            try { o.x = 1; } catch(e) {}
            try { delete o.sum; } catch(e) {}
            try { Object.defineProperty(o, 'q', { value: 1 }); } catch(e) {}
         }

         if(typeof m.get('k').putConstant == 'undefined') hidden++;
         if(l.get(0) !== CALC || m.get('k') !== StyleConstant) throw new Error('not the same');
         hidden
         """;
      assertEquals(6.0, ((Number) run(env("orgP"), writes)).doubleValue());
      assertWritesInvisible(writes);
   }

   /**
    * A shared object passed into a shared host function comes back as the guarded proxy, and
    * the host side cannot be driven to change it.
    */
   @Test
   void sharedObjectsPassedThroughSharedFunctionsStayGuarded() throws Exception {
      String writes = """
         var back = [CALC.iif(true, CALC, 0), CALC.iif(true, StyleConstant, 0),
            CALC.iif(false, 0, Chart)];
         back.forEach(function(o) {
            try { o.x = 1; } catch(e) {}
            try { Object.freeze(o); } catch(e) {}
            if(typeof o.putConstant != 'undefined') throw new Error('host method exposed');
         });
         typeof back[0].sum + String(isNull(StyleConstant)) + typeof split('a,b', ',')
         """;
      Object result = run(env("orgP"), writes);
      assertEquals("functionfalseobject", result);
      assertWritesInvisible(writes);
   }

   /** An unqualified write of a CALC builtin's name inside a scope chain shadows it locally. */
   @Test
   void unqualifiedBuiltinWritesThroughTheScopeProxyStayLocal() throws Exception {
      String writes = "Sum = 5; PROPER = 'x'; sum = 7; 1";
      WorksheetScriptEnv a = env("orgA");
      WorksheetScriptEnv b = env("orgB");
      MapScope scope = new MapScope();
      a.exec(a.compile(writes), scope, null, null);
      assertEquals(pristine, run(a, PROBE));
      assertEquals(pristine, run(b, PROBE));
      assertEquals(6.0, ((Number) b.exec(b.compile("Sum([1, 2, 3])"), new MapScope(), null, null))
         .doubleValue());
      assertWritesInvisible(writes, true);
   }

   /**
    * Built-in prototype patches the shared functions consult stay on the patching context
    * (B7): another context of the same sandbox, another sandbox, another org and another
    * pool-off engine see the pristine behaviour.
    */
   @Test
   void prototypePatchesConsultedBySharedFunctionsStayPerContext() throws Exception {
      WorksheetScriptEnv a = env("orgA");
      WorksheetScriptEnv a2 = env("orgA");
      WorksheetScriptEnv b = env("orgB");
      run(a, "1");
      run(a2, "1");
      run(b, "1");

      whileHeldElsewhere(a, () -> run(a, PROTO_PATCHES));

      // B7: a one-context sandbox keeps its own patches after the clean (observed: its probe
      // differs); they stay on that context, a new sandbox of the same org sees none of them
      WorksheetScriptEnv one = env("orgS");
      run(one, PROTO_PATCHES);
      assertEquals(1, one.getMetrics().getSize());
      assertEquals(pristine, run(env("orgS"), PROBE), "another sandbox of a patched one's org");

      assertEquals(pristine, run(a, PROBE), "A's other context");
      assertEquals(pristine, run(a2, PROBE), "another sandbox of A's org");
      assertEquals(pristine, run(b, PROBE), "another org");

      GraalJavaScriptEngine ea = engine();
      GraalJavaScriptEngine eb = engine();
      eval(ea, PROTO_PATCHES);
      assertEquals(pristine, eval(eb, PROBE), "pool off, another engine");
      assertEquals(pristine, run(b, PROBE), "another org after pool-off patches");
   }

   /** The shared objects never hand a script a live host collection of their own. */
   @Test
   void sharedObjectsHandOutNoLiveCollections() throws Exception {
      String writes = """
         var k1 = Object.keys(CALC); k1.length = 0; k1.push('x');
         var k2 = Object.getOwnPropertyNames(StyleConstant); k2.splice(0, k2.length);
         var k3 = Reflect.ownKeys(Chart); k3.reverse();
         var parts = split('a,b,c', ','); parts[0] = 'z';
         1
         """;
      assertWritesInvisible(writes);
   }

   /**
    * Writers and probers of two orgs, pool on and off, run at once; every probe sees the
    * pristine state.
    */
   @Test
   void concurrentWritesOfTwoSandboxesReachNoProbe() throws Exception {
      WorksheetScriptEnv a = env("orgA");
      WorksheetScriptEnv b = env("orgB");
      String attack = WRITES + ";" + PROTO_PATCHES;
      int rounds = 30;
      ExecutorService executor = Executors.newFixedThreadPool(8);
      List<Future<Integer>> futures = new ArrayList<>();

      try {
         for(int t = 0; t < 8; t++) {
            final WorksheetScriptEnv env = t % 2 == 0 ? a : b;
            final boolean writer = t % 4 < 2;
            final boolean poolOff = t >= 6;

            futures.add(executor.submit(() -> {
               GraalJavaScriptEngine engine = null;

               try {
                  if(poolOff) {
                     engine = new GraalJavaScriptEngine();
                     engine.init(new HashMap<>());
                  }

                  int probes = 0;

                  for(int i = 0; i < rounds; i++) {
                     if(writer) {
                        if(engine != null) {
                           eval(engine, WRITES);
                        }
                        else {
                           run(env, attack);
                        }
                     }
                     else {
                        // a prober's own engine never ran a write
                        Object seen = engine != null ? eval(engine, PROBE) : null;

                        if(seen != null) {
                           assertEquals(pristine, seen);
                           probes++;
                        }
                     }
                  }

                  return probes;
               }
               finally {
                  if(engine != null) {
                     engine.close();
                  }
               }
            }));
         }

         // pooled probes run on fresh sandboxes of both orgs while the writers run
         for(int i = 0; i < rounds; i++) {
            assertEquals(pristine, run(env(i % 2 == 0 ? "orgA" : "orgB"), PROBE), "round " + i);
         }

         int probes = 0;

         for(Future<Integer> future : futures) {
            probes += future.get(300, TimeUnit.SECONDS);
         }

         assertTrue(probes > 0);
      }
      finally {
         executor.shutdownNow();
      }

      assertEquals(pristine, run(env("orgC"), PROBE));
   }

   /**
    * Every member of the shared constant scopes is an immutable value or a fresh copy per read,
    * so a new mutable constant fails here.
    */
   @Test
   void everySharedConstantMemberIsImmutableOrACopyPerRead() throws Exception {
      WorksheetScriptEnv env = env("orgP");
      int copied = 0;

      for(String name : new String[] { "Chart", "StyleConstant" }) {
         ConstantScope scope = (ConstantScope) run(env, name);

         for(Object key : scope.getMemberKeys()) {
            Object value = scope.getMember((String) key);

            if(isCopiedValue(value)) {
               Object again = scope.getMember((String) key);
               assertNotSame(value, again, name + "." + key);
               assertTrue(value instanceof int[] ? Arrays.equals((int[]) value, (int[]) again)
                             : value.equals(again), name + "." + key);
               copied++;
            }
            else {
               assertTrue(isImmutableValue(value), name + "." + key + ": " + value.getClass());
            }
         }
      }

      // 3 arrays in each scope and 67 page sizes in StyleConstant
      assertEquals(73, copied);
   }

   @Test
   void constantArrayElementWritesAreInvisible() throws Exception {
      int[] saved = ChartConstants.TEXTURE_STYLES.clone();

      try {
         assertWritesInvisible(
            "try { Chart.TEXTURE_STYLES[0] = 77; } catch(e) {} " +
            "try { StyleConstant.TRENDLINE_TYPES[0] = 77; } catch(e) {} 1");
         assertEquals((double) saved[0],
            ((Number) run(env("orgB"), "Chart.TEXTURE_STYLES[0]")).doubleValue());
         assertArrayEquals(saved, ChartConstants.TEXTURE_STYLES);
      }
      finally {
         System.arraycopy(saved, 0, ChartConstants.TEXTURE_STYLES, 0, saved.length);
      }
   }

   @Test
   void constantSizeFieldWritesAreInvisible() throws Exception {
      float width = StyleConstants.PAPER_A4.width;

      try {
         run(env("orgA"), "try { StyleConstant.PAPER_A4.width = 1; } catch(e) {} 1");
         assertEquals(String.valueOf(width), String.valueOf(
            ((Number) run(env("orgB"), "StyleConstant.PAPER_A4.width")).floatValue()));
         assertEquals(width, StyleConstants.PAPER_A4.width);
      }
      finally {
         StyleConstants.PAPER_A4.width = width;
      }
   }

   private static final class MapScope implements ScriptScope {
      private final Map<String, Object> members = new LinkedHashMap<>();

      @Override
      public Object getMember(String name) {
         return members.get(name);
      }

      @Override
      public boolean hasMember(String name) {
         return members.containsKey(name);
      }

      @Override
      public void putMember(String name, Object value) {
         members.put(name, value);
      }

      @Override
      public Object[] getMemberKeys() {
         return members.keySet().toArray();
      }
   }
}
