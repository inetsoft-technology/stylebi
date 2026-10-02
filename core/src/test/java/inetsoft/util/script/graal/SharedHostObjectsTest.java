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

import inetsoft.util.script.Calc;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testing #77123 (G10 memory): the stateless host globals (CALC and its functions, the
 * ScriptFunction globals, the Chart / StyleConstant constant scopes) are one instance per JVM,
 * shared by every engine's Context, and a script's writes to them stay ignored, so nothing a
 * script does to them is visible to another engine.
 */
@Tag("core")
class SharedHostObjectsTest {
   private GraalJavaScriptEngine a;
   private GraalJavaScriptEngine b;

   @BeforeEach
   void setup() throws Exception {
      a = new GraalJavaScriptEngine();
      a.init(new HashMap<>());
      b = new GraalJavaScriptEngine();
      b.init(new HashMap<>());
   }

   @AfterEach
   void teardown() {
      a.close();
      b.close();
   }

   private static Object eval(GraalJavaScriptEngine engine, String src) throws Exception {
      return engine.exec(engine.compile(src), null, null);
   }

   @Test
   void hostObjectsAreSharedAcrossEngines() throws Exception {
      Object calcA = eval(a, "CALC");
      assertInstanceOf(Calc.class, calcA);
      assertSame(calcA, eval(b, "CALC"));
      assertSame(eval(a, "StyleConstant"), eval(b, "StyleConstant"));
      assertSame(eval(a, "Chart"), eval(b, "Chart"));
      // a JavaScriptEngine function, a FormulaFunctions function and a CALC function copy
      assertSame(eval(a, "isNull"), eval(b, "isNull"));
      assertSame(eval(a, "dateAdd"), eval(b, "dateAdd"));
      assertSame(eval(a, "abs"), eval(b, "abs"));
      assertSame(eval(a, "CALC.abs"), eval(b, "abs"));
   }

   @Test
   void scriptWritesToSharedObjectsAreIgnoredAndInvisibleToOtherEngines() throws Exception {
      eval(a, """
         CALC.x = 1;
         CALC.sum.foo = 2;
         sum.bar = 3;
         StyleConstant.Y = 4;
         Chart.Z = 5;
         isNull.baz = 6;
         StyleConstant.PORTRAIT = 99;
         delete CALC.sum;
         delete StyleConstant.PORTRAIT;
         CALC.sum = function() { return 42; };
         1
         """);

      for(GraalJavaScriptEngine engine : List.of(a, b)) {
         assertEquals("undefined", eval(engine, "typeof CALC.x"));
         assertEquals("undefined", eval(engine, "typeof CALC.sum.foo"));
         assertEquals("undefined", eval(engine, "typeof sum.bar"));
         assertEquals("undefined", eval(engine, "typeof StyleConstant.Y"));
         assertEquals("undefined", eval(engine, "typeof Chart.Z"));
         assertEquals("undefined", eval(engine, "typeof isNull.baz"));
         assertEquals(1.0, eval(engine, "StyleConstant.PORTRAIT"));
         assertEquals("function", eval(engine, "typeof CALC.sum"));
         assertEquals(6.0, eval(engine, "CALC.sum([1, 2, 3])"));
         assertEquals(Boolean.TRUE, eval(engine, "isNull(null)"));
      }
   }

   @Test
   void definePropertyAndSetPrototypeOnSharedObjectsThrow() throws Exception {
      assertEquals("TypeError", eval(a, """
         (function() {
            try { Object.defineProperty(CALC, 'q', { value: 1 }); return 'none'; }
            catch(e) { return e.name; }
         })()
         """));
      assertEquals("TypeError", eval(a, """
         (function() {
            try { Object.setPrototypeOf(StyleConstant, { q: 1 }); return 'none'; }
            catch(e) { return e.name; }
         })()
         """));
      assertEquals("undefined", eval(b, "typeof CALC.q"));
      assertEquals("undefined", eval(b, "typeof StyleConstant.q"));
   }

   @Test
   void globalRebindingStaysPerContext() throws Exception {
      // the global binding is per Context: rebinding a shared function's name in one engine
      // leaves the other engine's binding (and the shared object) alone
      eval(a, "isNull = function() { return 'mine'; }; abs = 7; CALC = 8; 1");
      assertEquals("mine", eval(a, "isNull(1)"));
      assertEquals(Boolean.FALSE, eval(b, "isNull(1)"));
      assertEquals(5.0, eval(b, "abs(-5)"));
      assertEquals(5.0, eval(b, "CALC.abs(-5)"));
   }

   @Test
   void sharedObjectsServeConcurrentEngines() throws Exception {
      int threads = 8;
      ExecutorService executor = Executors.newFixedThreadPool(threads);
      List<Future<Integer>> futures = new ArrayList<>();

      try {
         for(int t = 0; t < threads; t++) {
            final int seed = t;
            futures.add(executor.submit(() -> {
               GraalJavaScriptEngine engine = new GraalJavaScriptEngine();

               try {
                  engine.init(new HashMap<>());
                  Object script = engine.compile(
                     "CALC.sum([n, 1, 2]) + abs(-n) + StyleConstant.PORTRAIT + " +
                     "(isNull(null) ? 1 : 0) + CALC.proper('ab').length");
                  int ok = 0;

                  for(int i = 0; i < 200; i++) {
                     double n = seed * 1000 + i;
                     engine.put("n", n);
                     Object value = engine.exec(script, null, null);
                     assertEquals(n + 3 + n + 1 + 1 + 2, ((Number) value).doubleValue());
                     ok++;
                  }

                  return ok;
               }
               finally {
                  engine.close();
               }
            }));
         }

         for(Future<Integer> future : futures) {
            assertEquals(200, future.get(120, TimeUnit.SECONDS));
         }
      }
      finally {
         executor.shutdownNow();
      }
   }
}
