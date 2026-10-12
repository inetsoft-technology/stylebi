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
package inetsoft.report.lens;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.script.TableRowScope;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.script.ScriptSpan;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static inetsoft.report.lens.FormulaTableLensVarTest.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Hand-offs of lens-owned arrays and objects across pooled contexts, review round 2 of
 * Testing #77123 (B1 residual part 2):
 * <ul>
 *    <li>the cloner classifies each object of a batch by its own host digit, so a Date, a
 *    Proxy or a host object that is not first in its layer is saved (or lost) as what it is,
 *    and no trap runs;</li>
 *    <li>amendment A3 as written: a var holding an object that a lost var's graph also holds
 *    is lost too, each with its own warning, never kept as a stale alias;</li>
 *    <li>the two concurrent reads that lose values (a home held by another thread's batch)
 *    are loud and never stale;</li>
 *    <li>four resident tables of a sandbox each keep their own home (maxHomes 4), and an
 *    evictor pass hands off at most four homes.</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PooledLensObjectHandOffTest {
   // every trap counts a guest call: the cloner must never dispatch to one
   static final String TRAPS = "{get: function() { probe.hit(); }, " +
      "set: function() { probe.hit(); return true; }, " +
      "has: function() { probe.hit(); return false; }, " +
      "ownKeys: function() { probe.hit(); return []; }, " +
      "getOwnPropertyDescriptor: function() { probe.hit(); }, " +
      "getPrototypeOf: function() { probe.hit(); return null; }, " +
      "isExtensible: function() { probe.hit(); return true; }, " +
      "defineProperty: function() { probe.hit(); return true; }}";
   static final String ARRAY = "var a = a || []; a.push(field['id']); a.length";
   static final String OBJECT =
      "var c = c || {n: 0}; c['k' + field['id']] = 1; c.n++; c.n";

   @BeforeEach
   void capture() {
      logger = (Logger) LoggerFactory.getLogger(TableRowScope.class);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void retire() {
      logger.detachAppender(appender);
      SreeEnv.remove(MAX_HOMES);
      SreeEnv.remove(HAND_OFF_MILLIS);
      SreeEnv.remove(HAND_OFF_ENTRIES);

      for(WorksheetScriptEnv env : envs) {
         env.retire();
      }

      envs.clear();
   }

   static Stream<Arguments> classifier() {
      // name, shape of o, a step of o that is true while o is intact ("" for a lost shape)
      String[][] shapes = {
         { "dateThenObject", "{d: new Date(0), m: {k: 0}}",
           "(o.d.setTime(o.d.getTime() + 1000), o.m.k++, o.d.getTime() == o.m.k * 1000)" },
         { "dateThenArray", "[new Date(0), [0]]",
           "(o[0].setTime(o[0].getTime() + 1000), o[1][0]++, o[0].getTime() == o[1][0] * 1000)" },
         // a Date first in a deeper layer, an object after it
         { "nestedDateFirst", "{list: [new Date(0), {k: 0}], m: {}, d: new Date(0)}",
           "(o.list[0].setTime(o.list[0].getTime() + 1000), o.list[1].k++, " +
           "o.m['k' + o.list[1].k] = 1, o.d.setTime(o.d.getTime() + 1000), " +
           "o.list[0].getTime() == o.list[1].k * 1000 && o.d.getTime() == o.list[1].k * 1000 " +
           "&& Object.keys(o.m).length == o.list[1].k)" },
         { "objectThenDates", "{a: {x: 0}, d: new Date(0), b: [new Date(0), {y: 0}]}",
           "(o.a.x++, o.b[1].y++, o.d.setTime(o.d.getTime() + 1000), " +
           "o.b[0].setTime(o.b[0].getTime() + 1000), " +
           "o.d.getTime() == o.a.x * 1000 && o.b[0].getTime() == o.b[1].y * 1000)" },
         { "hostThenDate", "{h: probe, d: new Date(0), m: {k: 0}}",
           "(o.m.k++, o.d.setTime(o.d.getTime() + 1000), " +
           "o.h != null && o.d.getTime() == o.m.k * 1000)" },
         { "objectThenProxy", "{a: {}, p: new Proxy({}, " + TRAPS + ")}", "" },
         { "arrayThenProxy", "[{}, new Proxy([], " + TRAPS + ")]", "" },
         { "dateThenProxy", "{d: new Date(0), p: new Proxy({}, " + TRAPS + ")}", "" },
         { "hostThenProxy", "{h: probe, p: new Proxy({}, " + TRAPS + ")}", "" },
         { "proxyLastOfMany", "[{}, [], new Date(0), {q: 1}, new Proxy({}, " + TRAPS + ")]", "" },
      };
      List<Arguments> args = new ArrayList<>();

      for(String how : new String[] { "handoff", "takeover" }) {
         for(String[] s : shapes) {
            args.add(Arguments.of(how, s[0], s[1], s[2]));
         }
      }

      return args.stream();
   }

   /**
    * T1: a Date, a Proxy or a host object that is not the first object of its layer is
    * classified as itself: a Date after an object and an object after a Date are kept exactly;
    * a Proxy after a plain object, a Date or a host object is lost with one warning naming a
    * Proxy, runs no trap, and the plain accumulator t next to it is kept.
    */
   @ParameterizedTest(name = "{0} {1}")
   @MethodSource("classifier")
   void eachObjectOfABatchIsClassifiedAsItself(String how, String name, String shape,
                                               String step)
   {
      boolean kept = !step.isEmpty();
      // t counts every row; made counts how often o was created
      String f = "var t = t || {n: 0}; t.n++; var made = made || 0; " +
         "var o = o || (made++, " + shape + "); " +
         "(" + (kept ? step : "true") + " ? 1 : -1) * (t.n + 10000 * (made - 1))";
      double[][] v = new double[1][];
      assertTimeoutPreemptively(Duration.ofSeconds(90), () -> {
         v[0] = crossSlot(how, f);
      });
      assertEquals(0, probe.hits(), "no trap ran");
      assertTrue(PoolTestSupport.metric(lastEnv, "HandOffs") >= 1, "a hand-off ran");
      List<String> warns = warningTexts();

      for(int r = 1; r <= ROWS; r++) {
         assertTrue(v[0][r] > 0, name + " row " + r + ": " + v[0][r]);
         assertEquals(r, v[0][r] % 10000, name + ": t is kept, row " + r);
      }

      if(kept) {
         assertEquals(ROWS, v[0][ROWS], name + ": o is kept (made once)");
         assertTrue(warns.isEmpty(), () -> "no warning: " + warns);
      }
      else {
         assertTrue(v[0][ROWS] > 10000, name + ": o was lost at the hand-off, made again");
         assertEquals(1, warns.size(), () -> "one warning: " + warns);
         assertTrue(warns.get(0).contains("\"o\" holds a Proxy object"), warns.get(0));
      }
   }

   static IntStream seeds() {
      return IntStream.rangeClosed(1, 16);
   }

   /**
    * T1 fuzz: three vars of random nested objects and arrays, with Dates, host objects and at
    * most one value that is not kept per var (a Proxy, a function, a Map, a getter) at random
    * positions. At a hand-off each var is either exact (its counter, its Date and its shape)
    * or lost with one warning naming what it held (or, round 3, that it shares the probe, a
    * host object, with a lost var); no user code runs.
    */
   @ParameterizedTest(name = "seed {0}")
   @MethodSource("seeds")
   void randomPositionsAreEachExactOrLostWithTheRightWarning(int seed) {
      Random rnd = new Random(seed);
      int vars = 3;
      List<Shape> shapes = new ArrayList<>();
      StringBuilder f = new StringBuilder();

      for(int i = 0; i < vars; i++) {
         Shape s = shape(rnd, rnd.nextBoolean());
         shapes.add(s);
         f.append("var v").append(i).append(" = v").append(i).append(" || ").append(s.js)
            .append("; ");
      }

      StringBuilder mismatch = new StringBuilder("0");
      StringBuilder clocks = new StringBuilder("true");
      StringBuilder sigs = new StringBuilder("''");

      for(int i = 0; i < vars; i++) {
         Shape s = shapes.get(i);
         String c = "v" + i + s.counter;
         String d = "v" + i + s.clock;
         f.append(c).append(".c++; ").append(d).append(".setTime(").append(d)
            .append(".getTime() + 1000); ");
         mismatch.append(" + (").append(c).append(".c == field['id'] ? 0 : ").append(1 << i)
            .append(")");
         clocks.append(" && ").append(d).append(".getTime() == ").append(c).append(".c * 1000");

         // the shape of a var that holds nothing it cannot keep (reading one would run a
         // getter or a trap)
         if(s.lostKind == null) {
            sigs.append(" + '|' + sigOf(v").append(i).append(")");
         }
      }

      String sigOf = "(function() { var sigOf = function(x) { " +
         "if(x instanceof Date) return 'D'; " +
         "if(x !== null && typeof x === 'object' && typeof x.hits === 'function') return 'H'; " +
         "if(Array.isArray(x)) return 'A[' + x.map(sigOf).join(',') + ']'; " +
         "if(x !== null && typeof x === 'object') return 'O{' + Object.keys(x).map(" +
         "function(k) { return k + ':' + sigOf(x[k]); }).join(',') + '}'; " +
         "return typeof x; }; return " + sigs + "; })()";
      f.append("var s0 = s0 || ").append(sigOf).append("; ");
      f.append("(").append(mismatch).append(") + ((").append(clocks).append(") ? 0 : 8) + (")
         .append(sigOf).append(" === s0 ? 0 : 16)");
      String formula = f.toString();
      double[][] v = new double[1][];
      assertTimeoutPreemptively(Duration.ofSeconds(90), () -> {
         v[0] = crossSlot("takeover", formula);
      });
      assertEquals(0, probe.hits(), () -> "no user code ran: " + formula);
      // A3 (round 3, R1): a var that holds the probe, a host object, which a lost var holds
      // too is lost as well, as the lost var's init could create that object again
      // (a var lost for its own value that reaches the probe first may be reported as either)
      String shared = "an object that it shares with a variable whose value is not kept";
      String[] kinds = new String[vars];
      boolean[] either = new boolean[vars];
      int lostBits = 0;

      for(int i = 0; i < vars; i++) {
         Shape s = shapes.get(i);
         int me = i;
         boolean hostOfLost = s.host && IntStream.range(0, vars).anyMatch(
            j -> j != me && shapes.get(j).lostKind != null && shapes.get(j).host);
         kinds[i] = s.lostKind != null ? s.lostKind : hostOfLost ? shared : null;
         either[i] = s.lostKind != null && hostOfLost;

         if(kinds[i] != null) {
            lostBits |= 1 << i;
         }
      }

      for(int r = 1; r <= ROWS; r++) {
         int bits = (int) v[0][r];
         int row = r;
         assertEquals(0, bits & ~lostBits, () -> "row " + row + " (" + bits + "): " + formula);
      }

      assertEquals(lostBits, (int) v[0][ROWS], () -> "the lost vars restarted: " + formula);
      List<String> warns = warningTexts();
      // a lost var that hides references names each kept var, all objects here, as a copy
      // once; no other var is named
      boolean hiding = shapes.stream().anyMatch(s -> HIDING.contains(s.lostKind));
      List<String> named = warns.stream()
         .flatMap(w -> PooledLensHiddenAliasTest.copiesIn(w).stream()).toList();

      for(int i = 0; i < vars; i++) {
         String var = "\"v" + i + "\"";
         List<String> mine = warns.stream().filter(w -> w.contains(var + " holds")).toList();
         String kind = kinds[i];
         long copies = named.stream().filter(("v" + i)::equals).count();
         assertEquals(kind == null && hiding ? 1 : 0, copies,
                      () -> var + " named as a copy: " + warns + " " + formula);

         if(kind == null) {
            assertTrue(mine.isEmpty(), () -> var + " is kept: " + mine + " " + formula);
         }
         else {
            assertEquals(1, mine.size(), () -> var + ": " + mine + " " + formula);
            assertTrue(mine.get(0).contains(var + " holds " + kind) ||
                       either[i] && mine.get(0).contains(var + " holds " + shared),
                       () -> mine.get(0) + " " + formula);
         }
      }

      assertEquals(Integer.bitCount(lostBits), warns.size(), () -> warns + " " + formula);
   }

   /**
    * T1 at batch boundaries: thousands of objects in each layer of one var (Dates, objects and
    * arrays interleaved, each layer's objects saved in one host batch), and a second var of
    * 3000 objects whose last one is a Proxy: the first is kept exactly, the second lost with
    * one warning, and no trap runs.
    */
   @Test
   void manyObjectsPerBatchAreEachClassifiedAsThemselves() {
      String f = "var big = big || (function() { var a = []; for(var i = 0; i < 3000; i++) { " +
         "a.push(i % 3 == 0 ? new Date(i) : i % 3 == 1 ? {x: i, d: new Date(i)} : " +
         "[i, {y: i}]); } return a; })(); " +
         "var made = made || 0; var pr = pr || (function() { made++; var a = []; " +
         "for(var i = 0; i < 2999; i++) a.push(i % 2 ? {} : new Date(i)); " +
         "a.push(new Proxy({}, " + TRAPS + ")); return a; })(); " +
         "var k = (k || 0) + 1; " +
         "var ok = field['id'] % 50 != 0 || (function() { for(var i = 0; i < 3000; i++) { " +
         "var e = big[i]; if(i % 3 == 0 ? !(e instanceof Date && e.getTime() == i) : " +
         "i % 3 == 1 ? !(e.x == i && e.d instanceof Date && e.d.getTime() == i) : " +
         "!(Array.isArray(e) && e[0] == i && e[1].y == i)) return false; } return true; })(); " +
         "(ok ? 1 : -1) * (k + 10000 * (made - 1))";
      double[][] v = new double[1][];
      assertTimeoutPreemptively(Duration.ofSeconds(120), () -> {
         v[0] = crossSlot("handoff", f);
      });
      assertEquals(0, probe.hits(), "no trap ran");

      for(int r = 1; r <= ROWS; r++) {
         assertTrue(v[0][r] > 0, "big is intact, row " + r + ": " + v[0][r]);
         assertEquals(r, v[0][r] % 10000, "k, row " + r);
      }

      assertTrue(v[0][ROWS] > 10000, "pr was lost at the hand-off, made again");
      List<String> warns = warningTexts();
      assertEquals(1, warns.size(), () -> "one warning: " + warns);
      assertTrue(warns.get(0).contains("\"pr\" holds a Proxy object"), warns.get(0));
   }

   /**
    * T2 (amendment A3 as written): a var that holds an object which a lost var's graph also
    * holds is lost too, with its own warning, whichever of the two owns the object, so the
    * two never split into a stale alias; a plain var next to them is kept.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "intoLost", "hostIntoLost", "hostMadeIntoLost", "mapIntoLost",
                            "fnPropIntoLost", "lostInto", "rootAlias", "hostInArrayIntoLost" })
   void anAliasIntoALostVarIsLostToo(String what) throws Exception {
      // each: (x === y ? 1 : -1) * (t.n * 10000 + count), where x and y must stay one object
      String f = "var t = t || {n: 0}; t.n++; " + switch(what) {
         // a points into b, which holds a function
         case "intoLost" -> "var b = b || {y: {n: 0}, f: function(x) { return x; }}; " +
            "var a = a || {x: b.y}; b.y.n++; (a.x === b.y ? 1 : -1) * (t.n * 10000 + a.x.n)";
         // the same with a host object, which b's init creates again (round 3, R1)
         case "hostIntoLost" -> "var b = b || {y: new java.util.HashMap(), " +
            "f: function(x) { return x; }}; var a = a || {x: b.y}; " +
            "b.y.put('n', (b.y.containsKey('n') ? b.y.get('n') : 0) + 1); " +
            "(a.x === b.y ? 1 : -1) * (t.n * 10000 + a.x.get('n'))";
         // a host list made by a factory (round 3, tester's T2-H probe)
         case "hostMadeIntoLost" -> "var b = b || {h: factory.list(), " +
            "f: function(x) { return x; }}; var a = a || {h: b.h}; b.h.add(1); " +
            "(a.h === b.h ? 1 : -1) * (t.n * 10000 + a.h.size())";
         // an object in a lost function's own property (round 3, tester's fnProps probe)
         case "fnPropIntoLost" -> "var g = g || (function() { var h = function() {}; " +
            "h.s = {n: 0}; return h; })(); var q = q || g.s; g.s.n++; " +
            "(g.s === q ? 1 : -1) * (t.n * 10000 + q.n)";
         // the same through a Map's value (round 3, tester finding)
         case "mapIntoLost" -> "var b = b || {f: function(x) { return x; }, " +
            "m: new Map([['k', {n: 0}]])}; var a = a || {x: b.m.get('k')}; " +
            "b.m.get('k').n++; (a.x === b.m.get('k') ? 1 : -1) * (t.n * 10000 + a.x.n)";
         // the probe, a host object, as an array element of a and in b (array shape)
         case "hostInArrayIntoLost" -> "var b = b || {h: probe, " +
            "f: function(x) { return x; }}; var a = a || [probe, {n: 0}]; a[1].n++; " +
            "(a[0] === b.h ? 1 : -1) * (t.n * 10000 + a[1].n)";
         // b, which holds a function, points into a
         case "lostInto" -> "var a = a || {y: {n: 0}}; " +
            "var b = b || {y: a.y, f: function(x) { return x; }}; a.y.n++; " +
            "(a.y === b.y ? 1 : -1) * (t.n * 10000 + a.y.n)";
         // q is an object of p, which holds a function (review L4)
         default -> "var p = p || {s: {n: 0}, f: function(x) { return x; }}; " +
            "var q = q || p.s; q.n++; (p.s === q ? 1 : -1) * (t.n * 10000 + q.n)";
      };
      double[] v = crossSlot("handoff", f);
      // the count of the shared object: it goes on, or starts over at 1 (at each of the two
      // hand-offs), never from an older value
      int restart = 0;
      double prev = 0;

      for(int r = 1; r <= ROWS; r++) {
         assertTrue(v[r] > 0, what + ": the two vars split at row " + r + ": " + v[r]);
         assertEquals(r, Math.floor(v[r] / 10000), what + ": t is kept, row " + r);
         double n = v[r] % 10000;

         if(n != prev + 1) {
            assertEquals(1, n, what + ": the shared object starts over at row " + r);
            restart = restart == 0 ? r : restart;
         }

         prev = n;
      }

      assertTrue(restart > 200, what + ": the values were lost at the hand-off: " + restart);
      List<String> warns = warningTexts();
      assertEquals(2, warns.size(), () -> "one warning per lost var: " + warns);
      String owner = what.equals("rootAlias") ? "p" : what.equals("fnPropIntoLost") ? "g" : "b";
      String alias = what.equals("rootAlias") || what.equals("fnPropIntoLost") ? "q" : "a";
      assertTrue(warns.stream().anyMatch(w -> w.contains("\"" + owner + "\" holds a function")),
                 () -> "" + warns);
      assertTrue(warns.stream().anyMatch(w -> w.contains("\"" + alias + "\" holds an object " +
         "that it shares with a variable whose value is not kept")), () -> "" + warns);
      assertTrue(warns.stream().noneMatch(w -> w.contains("\"t\" holds")), () -> "" + warns);
      // t, the only kept var, holds an object the lost function may reach: named once
      assertEquals(List.of("t"), warns.stream()
         .flatMap(w -> PooledLensHiddenAliasTest.copiesIn(w).stream()).toList(),
                   () -> "" + warns);
   }

   /**
    * Round 3, R5: a chain (c shares only with b, which shares with the lost l, in an order
    * that needs a pass per link) and a cycle through the lost var are lost whole, each var
    * with its own warning, and re-created as one graph; t next to them is kept.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "chain", "cycle" })
   void aChainOrACycleThroughALostVarIsLostWhole(String what) throws Exception {
      String f = "var t = t || {n: 0}; t.n++; " + switch(what) {
         case "chain" -> "var c = c || {w: {n: 0}}; var b = b || {w: c.w, z: {}}; " +
            "var l = l || {y: b.z, f: function(x) { return x; }}; c.w.n++; " +
            "(c.w === b.w && b.z === l.y ? 1 : -1) * (t.n * 10000 + c.w.n)";
         default -> "var l = l || {y: {n: 0}, f: function(x) { return x; }}; " +
            "var a = a || {x: l.y}; if(!l.y.a) l.y.a = a; l.y.n++; " +
            "(a.x === l.y && l.y.a === a ? 1 : -1) * (t.n * 10000 + a.x.n)";
      };
      double[] v = crossSlot("handoff", f);
      int restart = 0;
      double prev = 0;

      for(int r = 1; r <= ROWS; r++) {
         assertTrue(v[r] > 0, what + ": the vars split at row " + r + ": " + v[r]);
         assertEquals(r, Math.floor(v[r] / 10000), what + ": t is kept, row " + r);
         double n = v[r] % 10000;

         if(n != prev + 1) {
            assertEquals(1, n, what + ": the shared object starts over at row " + r);
            restart = restart == 0 ? r : restart;
         }

         prev = n;
      }

      assertTrue(restart > 200, what + ": the values were lost at the hand-off: " + restart);
      List<String> warns = warningTexts();
      String[] shared = what.equals("chain") ? new String[] { "b", "c" } : new String[] { "a" };
      assertEquals(shared.length + 1, warns.size(), () -> "one warning per lost var: " + warns);
      assertTrue(warns.stream().anyMatch(w -> w.contains("\"l\" holds a function")),
                 () -> "" + warns);

      for(String s : shared) {
         assertTrue(warns.stream().anyMatch(w -> w.contains("\"" + s + "\" holds an object " +
            "that it shares with a variable whose value is not kept")), () -> s + ": " + warns);
      }

      assertTrue(warns.stream().noneMatch(w -> w.contains("\"t\" holds")), () -> "" + warns);
      // t, the only kept var, holds an object the lost function may reach: named once
      assertEquals(List.of("t"), warns.stream()
         .flatMap(w -> PooledLensHiddenAliasTest.copiesIn(w).stream()).toList(),
                   () -> "" + warns);
   }

   /**
    * Round 3: marking a lost var enters its functions' own data properties, but never a
    * callable Proxy (the host tells it apart without a trap): no trap runs, and a var that
    * shares nothing with it is kept exactly.
    */
   @Test
   void markingALostVarRunsNoTrapOfACallableProxy() throws Exception {
      String f = "var b = b || {f: function(x) { return x; }, " +
         "p: {g: new Proxy(function() {}, " + TRAPS + ")}}; b.f.s = b.f.s || {m: b.p}; " +
         "var a = a || {n: 0}; a.n++; a.n";
      double[] v = crossSlot("handoff", f);
      assertAll(v, "a is kept");
      assertEquals(0, probe.hits(), "no trap ran");
      List<String> warns = warningTexts();
      assertEquals(1, warns.size(), () -> "one warning: " + warns);
      assertTrue(warns.get(0).contains("\"b\" holds a function"), warns.get(0));
   }

   /**
    * Round 3, R5: past the time bound every object var of the table is lost, each with its
    * own warning, not only the one that was being saved; a number var is kept.
    */
   @Test
   void pastTheTimeBoundEveryObjectVarIsLost() throws Exception {
      SreeEnv.setProperty(HAND_OFF_MILLIS, "1");
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      TableLens t = make(box, base(ROWS), "var c = c || (function() { var o = {}; " +
         "for(var i = 0; i < 50000; i++) o['k' + i] = {n: i}; return o; })(); " +
         "var d = d || {n: 0}; d.n++; var k = (k || 0) + 1; k", "T");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 20);
      PoolTestSupport.handOffIdleHomes(w);
      read(t, v, 21, 40);

      for(int r = 1; r <= 40; r++) {
         assertEquals(r, v[r], "k is a number, kept: row " + r);
      }

      List<String> warns = warningTexts();
      assertEquals(2, warns.size(), () -> "one warning per object var: " + warns);

      for(String var : new String[] { "c", "d" }) {
         assertTrue(warns.stream().anyMatch(x -> x.contains("\"" + var +
            "\" holds a value that took longer than")), () -> var + ": " + warns);
      }
   }

   /**
    * Round 3, R2: a large typed array is lost by its class without its elements being listed
    * (a 10M-element buffer), fast, and an object var next to it is kept exactly.
    */
   @Test
   void aLargeTypedArrayIsLostFastAndItsNeighbourIsKept() {
      assertTimeoutPreemptively(Duration.ofSeconds(120), () -> {
         AssetQuerySandbox box = box();
         WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
         TableLens t = make(box, base(ROWS), "var big = big || new Float64Array(10000000); " +
            "big[0]++; var c = c || {n: 0}; c.n++; c.n", "T");
         double[] v = new double[ROWS + 1];
         read(t, v, 1, 20);
         long t0 = System.nanoTime();
         PoolTestSupport.handOffIdleHomes(w);
         long ms = (System.nanoTime() - t0) / 1_000_000;
         read(t, v, 21, 40);
         System.out.println("B1OBJ typed array hand-off " + ms + " ms");
         assertTrue(ms < 1500, "hand-off " + ms + " ms");

         for(int r = 1; r <= 40; r++) {
            assertEquals(r, v[r], "c is kept: row " + r);
         }

         List<String> warns = warningTexts();
         assertEquals(1, warns.size(), () -> "one warning: " + warns);
         assertTrue(warns.get(0).contains("\"big\" holds a typed array"), warns.get(0));
      });
   }

   /**
    * Round 3, R2: marking a lost var's objects counts against the marking budget (four entry
    * caps). Past it, sharing cannot be checked, so every object var is lost, each with its
    * own warning, and never read stale.
    */
   @Test
   void markingPastTheEntryCapLosesEveryObjectVar() throws Exception {
      SreeEnv.setProperty(HAND_OFF_ENTRIES, "100");
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      TableLens t = make(box, base(ROWS), "var b = b || {f: function(x) { return x; }, " +
         "a: (function() { var x = []; for(var i = 0; i < 500; i++) x[i] = i; return x; })()}; " +
         "var c = c || {n: 0}; c.n++; var k = (k || 0) + 1; k * 10000 + c.n", "T");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 20);
      PoolTestSupport.handOffIdleHomes(w);
      read(t, v, 21, ROWS);
      // rows past 20 may have run before the hand-off: c goes on, then starts over once
      int restart = 0;

      for(int r = 1; r <= ROWS; r++) {
         assertEquals(r, Math.floor(v[r] / 10000), "k is kept: row " + r);
         double n = v[r] % 10000;

         if(r > 1 && n != v[r - 1] % 10000 + 1) {
            assertEquals(1, n, "c starts over: row " + r);
            assertEquals(0, restart, "c starts over once: row " + r);
            restart = r;
         }
      }

      assertTrue(restart > 20, "c was lost at the hand-off: " + restart);

      List<String> warns = warningTexts();
      assertEquals(2, warns.size(), () -> "one warning per object var: " + warns);
      assertTrue(warns.stream().anyMatch(x -> x.contains("\"b\" holds a function")),
                 () -> "" + warns);
      assertTrue(warns.stream().anyMatch(x -> x.contains("\"c\" holds a value that could " +
         "not be checked")), () -> "" + warns);
      assertTrue(warns.stream().anyMatch(x -> x.contains("more than " +
         4 * 100 + " entries")), () -> "" + warns);
   }

   /**
    * Follow-up of round 3 (over-loss b): a lost var whose graph is over the entry cap but
    * within the marking budget (four entry caps) is marked, so an object var next to it that
    * shares nothing with it is kept exactly; the lost var has its own warning. Lost for its
    * size, or for a function next to a large array.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "overTheCap", "functionAndLargeArray" })
   void aNeighbourOfAVarOverTheEntryCapIsKept(String what) throws Exception {
      SreeEnv.setProperty(HAND_OFF_ENTRIES, "100");
      String big = "(function() { var x = []; for(var i = 0; i < 150; i++) x[i] = {v: i}; " +
         "return x; })()";
      String f = (what.equals("overTheCap") ? "var lk = lk || " + big + "; "
         : "var lk = lk || {f: function(x) { return x; }, a: " + big + "}; ") +
         "var c = c || {n: 0}; c.n++; c.n";
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      TableLens t = make(box, base(ROWS), f, "T");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 200);
      PoolTestSupport.handOffIdleHomes(w);
      read(t, v, 201, 600);
      PoolTestSupport.handOffIdleHomes(w);
      read(t, v, 601, ROWS);
      assertAll(v, "c is kept");
      List<String> warns = warningTexts();
      assertEquals(1, warns.size(), () -> "one warning: " + warns);
      assertTrue(warns.get(0).contains(what.equals("overTheCap")
         ? "\"lk\" holds a value with more than 100 entries" : "\"lk\" holds a function"),
         warns.get(0));
   }

   /**
    * Follow-up of round 3 (over-loss b), at the default bounds: a lookup var over the entry
    * cap (250k elements) is marked within the budget and the small var next to it is kept;
    * one past the budget (a million elements) loses both, refused before its keys are
    * listed. Each hand-off stays inside the time bound.
    */
   @ParameterizedTest(name = "{0}")
   @ValueSource(ints = { 250_000, 1_000_000 })
   void aLargeLookupVarIsMarkedWithinTheBudgetAndBounded(int size) {
      assertTimeoutPreemptively(Duration.ofSeconds(120), () -> {
         AssetQuerySandbox box = box();
         WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
         TableLens t = make(box, base(ROWS), "var lk = lk || (function() { var a = []; " +
            "for(var i = 0; i < " + size + "; i++) a[i] = i; return a; })(); " +
            "var s = s || {n: 0}; s.n++; var k = (k || 0) + 1; k * 10000 + s.n", "T");
         double[] v = new double[ROWS + 1];
         read(t, v, 1, 200);
         long t0 = System.nanoTime();
         PoolTestSupport.handOffIdleHomes(w);
         long ms = (System.nanoTime() - t0) / 1_000_000;
         read(t, v, 201, ROWS);
         System.out.println("B1OBJ lookup " + size + " hand-off " + ms + " ms");
         // the time bound (5000 ms) plus the guard's backstop, under load
         assertTrue(ms < 6000, "hand-off " + ms + " ms");
         int restart = 0;

         for(int r = 1; r <= ROWS; r++) {
            assertEquals(r, Math.floor(v[r] / 10000), "k is kept: row " + r);
            double n = v[r] % 10000;

            if(r > 1 && n != v[r - 1] % 10000 + 1) {
               assertEquals(1, n, "s starts over: row " + r);
               restart = restart == 0 ? r : restart;
            }
         }

         List<String> warns = warningTexts();

         // the marking budget: four entry caps
         if(size < 4 * PoolConfig.DEFAULT_HAND_OFF_ENTRIES) {
            assertEquals(0, restart, "s is kept");
            assertEquals(1, warns.size(), () -> "one warning: " + warns);
         }
         else {
            assertTrue(restart > 200, "s was lost at the hand-off: " + restart);
            assertEquals(2, warns.size(), () -> "one warning per object var: " + warns);
            assertTrue(warns.stream().anyMatch(x -> x.contains("\"s\" holds a value that " +
               "could not be checked")), () -> "" + warns);
         }

         assertTrue(warns.stream().anyMatch(x -> x.contains("\"lk\" holds a value with " +
            "more than")), () -> "" + warns);
      });
   }

   /**
    * M2 / T3: two tables whose batches ran nested in one claim. On main they shared one
    * home, and while one table's batch held it on another thread, a read of the other table
    * lost each of its vars with one warning. Each batch of a table with object vars now takes
    * a context of its own (Testing #77123, cond-home), so each table has its own home: the
    * read of the second table while the first one's batch is busy is exact too, no warning.
    */
   @Test
   void aReadOfATableWhileAnotherTableOfTheSameSpanIsBusyIsExact() throws Exception {
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      // past what the first span computes ahead (batches of 10, 20, 40, 80 rows)
      Gate gate = new Gate(400);
      w.put("gate", gate);
      TableLens t1 = make(box, base(ROWS), "gate.pass(field['id']); " + ARRAY, "T1");
      TableLens t2 = make(box, base(ROWS), "var q = q || [0]; q[0]++; " + OBJECT +
         " * 10000 + q[0]", "T2");
      double[] v1 = new double[ROWS + 1];
      double[] v2 = new double[ROWS + 1];

      try(ScriptSpan span = w.openSpan()) {
         read(t1, v1, 1, 100);
         read(t2, v2, 1, 100);
      }

      assertEquals(2, PoolTestSupport.homes(w), "a home for each table");
      ExecutorService ex = Executors.newSingleThreadExecutor();

      try {
         Future<?> other = ex.submit(() -> {
            read(t1, v1, 101, 600);
            return null;
         });
         assertTrue(gate.entered.await(30, TimeUnit.SECONDS), "t1's batch holds the home");

         try {
            read(t2, v2, 101, 300);
         }
         finally {
            gate.release.countDown();
         }

         other.get(60, TimeUnit.SECONDS);
      }
      finally {
         ex.shutdownNow();
      }

      read(t1, v1, 601, ROWS);
      read(t2, v2, 301, ROWS);

      for(int r = 1; r <= ROWS; r++) {
         assertEquals(r, v1[r], "t1 row " + r);
      }

      for(int r = 1; r <= ROWS; r++) {
         assertEquals(r * 10000.0 + r, v2[r], "t2 row " + r);
      }

      assertTrue(warningTexts().isEmpty(), () -> "no warning: " + warningTexts());
   }

   /**
    * M2: four resident tables of one sandbox read in turn each keep their own exclusive home
    * (maxHomes defaults to 4): no hand-off, one context per table, every row exact.
    */
   @Test
   void fourResidentTablesOfASandboxKeepTheirOwnHomes() throws Exception {
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      List<TableLens> lenses = new ArrayList<>();
      List<double[]> values = new ArrayList<>();

      for(int i = 0; i < 4; i++) {
         lenses.add(make(box, base(ROWS), i % 2 == 0 ? OBJECT : ARRAY, "T" + i));
         values.add(new double[ROWS + 1]);
      }

      for(int s = 1; s <= ROWS; s += 100) {
         for(int i = 0; i < 4; i++) {
            read(lenses.get(i), values.get(i), s, s + 99);
         }
      }

      for(int i = 0; i < 4; i++) {
         assertAll(values.get(i), "T" + i);
      }

      assertEquals(0, PoolTestSupport.metric(w, "HandOffs"), "no hand-off");
      assertEquals(4, PoolTestSupport.exclusiveHomes(w), "four exclusive homes");
      assertTrue(w.getMetrics().getHighWater() <= 4,
                 "one context per table: " + w.getMetrics().getHighWater());
   }

   /**
    * Review L3: one evictor pass hands off at most four idle homes of a pool (the evictor
    * thread is shared by the node); the next pass hands off the rest, and every table
    * continues exactly.
    */
   @Test
   void anEvictorPassHandsOffAtMostFourHomes() throws Exception {
      SreeEnv.setProperty(MAX_HOMES, "8");
      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      List<TableLens> lenses = new ArrayList<>();
      List<double[]> values = new ArrayList<>();

      for(int i = 0; i < 6; i++) {
         lenses.add(make(box, base(ROWS), i % 2 == 0 ? OBJECT : ARRAY, "T" + i));
         values.add(new double[ROWS + 1]);
         read(lenses.get(i), values.get(i), 1, 100);
      }

      assertEquals(6, PoolTestSupport.homes(w), "one home per table");
      PoolTestSupport.evictIdle(w, Long.MAX_VALUE);
      assertEquals(4, PoolTestSupport.metric(w, "HandOffs"), "four hand-offs in one pass");
      assertEquals(2, PoolTestSupport.homes(w), "two homes left for the next pass");
      PoolTestSupport.evictIdle(w, Long.MAX_VALUE);
      assertEquals(6, PoolTestSupport.metric(w, "HandOffs"), "the rest in the next pass");
      assertEquals(0, PoolTestSupport.homes(w));

      for(int i = 0; i < 6; i++) {
         read(lenses.get(i), values.get(i), 101, ROWS);
         assertAll(values.get(i), "T" + i);
      }

      assertTrue(warnings().isEmpty(), () -> "no warning: " + warnings());
   }

   // --- random shapes ---

   /** A var's literal, the paths to its counter object and its Date, what it cannot keep. */
   // host: whether a probe (a host object shared by every var that holds it) is in it
   record Shape(String js, String counter, String clock, String lostKind, boolean host) {
   }

   private static final class Node {
      Node(boolean array) {
         this.array = array;
      }

      final boolean array;
      final List<Object> items = new ArrayList<>(); // Node or a literal
   }

   private static final String COUNTER = "\u0001counter";
   private static final String CLOCK = "\u0001clock";
   private static final String[] FILLERS = {
      "{x: 1}", "[1, 2]", "new Date(5)", "7", "'s'", "{}", "[]", "null", "{y: {z: [1]}}",
      "probe", "[new Date(9), {w: 2}]" };
   private static final String[][] REFUSALS = {
      { "new Proxy({}, " + TRAPS + ")", "a Proxy object" },
      { "new Proxy([], " + TRAPS + ")", "a Proxy object" },
      { "function() { return 1; }", "a function" },
      { "new Map([[1, 2]])", "a Map" },
      { "{get g() { probe.hit(); return 1; }}", "an object with a getter or setter" } };
   // the refusals whose lost var hides references: it names the kept vars as copies
   private static final Set<String> HIDING =
      Set.of("a Proxy object", "a function", "an object with a getter or setter");

   // a container with nested containers, fillers, the counter, the Date and at most one
   // value that is not kept, each at a random position
   private static Shape shape(Random rnd, boolean refuse) {
      Node root = new Node(rnd.nextBoolean());
      List<Node> all = new ArrayList<>(List.of(root));
      int containers = rnd.nextInt(4);
      boolean host = false;

      for(int i = 0; i < containers; i++) {
         Node n = new Node(rnd.nextBoolean());
         insert(all.get(rnd.nextInt(all.size())), n, rnd);
         all.add(n);
      }

      for(Node n : all) {
         for(int k = rnd.nextInt(4); k > 0; k--) {
            String filler = FILLERS[rnd.nextInt(FILLERS.length)];
            host |= filler.equals("probe");
            insert(n, filler, rnd);
         }
      }

      insert(all.get(rnd.nextInt(all.size())), COUNTER, rnd);
      insert(all.get(rnd.nextInt(all.size())), CLOCK, rnd);
      String kind = null;

      if(refuse) {
         String[] r = REFUSALS[rnd.nextInt(REFUSALS.length)];
         insert(all.get(rnd.nextInt(all.size())), r[0], rnd);
         kind = r[1];
      }

      String[] paths = new String[2];
      String js = render(root, "", paths);
      return new Shape(js, paths[0], paths[1], kind, host);
   }

   private static void insert(Node n, Object item, Random rnd) {
      n.items.add(rnd.nextInt(n.items.size() + 1), item);
   }

   private static String render(Node n, String path, String[] paths) {
      StringBuilder buf = new StringBuilder(n.array ? "[" : "{");

      for(int i = 0; i < n.items.size(); i++) {
         Object item = n.items.get(i);
         String key = n.array ? "[" + i + "]" : "['k" + i + "']";
         buf.append(i > 0 ? ", " : "").append(n.array ? "" : "k" + i + ": ");

         if(item instanceof Node child) {
            buf.append(render(child, path + key, paths));
         }
         else if(item == COUNTER) {
            buf.append("{c: 0}");
            paths[0] = path + key;
         }
         else if(item == CLOCK) {
            buf.append("new Date(0)");
            paths[1] = path + key;
         }
         else {
            buf.append(item);
         }
      }

      return buf.append(n.array ? "]" : "}").toString();
   }

   // --- helpers ---

   /** Makes a host list, as a script's own Java helper would. */
   public static final class Factory {
      public List<Integer> list() {
         return new ArrayList<>();
      }
   }

   /** Blocks the formula of one row once, on the first thread that computes it. */
   public static final class Gate {
      Gate(int row) {
         this.row = row;
      }

      public void pass(Object id) throws InterruptedException {
         if(id instanceof Number n && n.intValue() == row && entered.getCount() > 0) {
            entered.countDown();
            release.await(30, TimeUnit.SECONDS);
         }
      }

      final CountDownLatch entered = new CountDownLatch(1);
      final CountDownLatch release = new CountDownLatch(1);
      private final int row;
   }

   /**
    * Rows 1..200, then 201..600 under {@code how}, then 601.. back: handoff (the pool hands
    * off the idle homes between the reads), takeover (no exclusive home: another thread's
    * claim takes the home over after a hand-off).
    */
   private double[] crossSlot(String how, String formula) throws Exception {
      if(how.equals("takeover")) {
         SreeEnv.setProperty(MAX_HOMES, "0");
      }

      AssetQuerySandbox box = box();
      WorksheetScriptEnv w = (WorksheetScriptEnv) box.getScriptEnv();
      w.put("probe", probe);
      w.put("factory", new Factory());
      TableLens t = make(box, base(ROWS), formula, "T");
      double[] v = new double[ROWS + 1];
      read(t, v, 1, 200);

      if(how.equals("takeover")) {
         PoolTestSupport.whileHeldElsewhere(w, () -> read(t, v, 201, 600));
      }
      else {
         PoolTestSupport.handOffIdleHomes(w);
         read(t, v, 201, 600);
         PoolTestSupport.handOffIdleHomes(w);
      }

      read(t, v, 601, ROWS);
      return v;
   }

   private static void read(TableLens t, double[] v, int from, int to) {
      for(int r = from; r <= to; r++) {
         assertTrue(t.moreRows(r), "row " + r);
         v[r] = num(t.getObject(r, 2));
      }
   }

   private static void assertAll(double[] v, String what) {
      List<String> bad = new ArrayList<>();

      for(int r = 1; r < v.length; r++) {
         if(v[r] != r) {
            bad.add(r + "=" + v[r]);
         }
      }

      assertTrue(bad.isEmpty(), () -> what + ": " + bad.size() + " wrong rows, first " +
         bad.subList(0, Math.min(5, bad.size())));
   }

   private AssetQuerySandbox box() throws Exception {
      AssetQuerySandbox box = PoolTestSupport.poolBox(true);
      lastEnv = (WorksheetScriptEnv) box.getScriptEnv();
      envs.add(lastEnv);
      return box;
   }

   private List<ILoggingEvent> warnings() {
      return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
   }

   private List<String> warningTexts() {
      return warnings().stream().map(ILoggingEvent::getFormattedMessage).toList();
   }

   private static final String MAX_HOMES = "script.ws.contextPool.maxHomes";
   private static final String HAND_OFF_MILLIS = "script.ws.contextPool.handOffMillis";
   private static final String HAND_OFF_ENTRIES = "script.ws.contextPool.handOffEntries";
   private static final int ROWS = 1200;
   private final List<WorksheetScriptEnv> envs = new ArrayList<>();
   private final PoolTestSupport.Probe probe = new PoolTestSupport.Probe();
   private WorksheetScriptEnv lastEnv;
   private Logger logger;
   private ListAppender<ILoggingEvent> appender;
}
