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

import inetsoft.util.script.Calc;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

import static inetsoft.util.script.graal.pool.PoolTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Testing #77123 (G10 memory): the pooled contexts of every sandbox and org share the stateless
 * host objects (CALC, the function globals, StyleConstant/Chart), and a script's writes to them
 * reach neither another pooled context of the same sandbox nor a sandbox of another org.
 */
@Tag("core")
class SharedHostObjectsPoolTest {
   private static final String WRITES = """
      CALC.x = 1;
      CALC.sum.foo = 2;
      sum.bar = 3;
      StyleConstant.Y = 4;
      Chart.Z = 5;
      isNull.baz = 6;
      delete CALC.sum;
      CALC.sum = 42;
      1
      """;

   // the pool's host boundary refuses a script function stored in a host object, shared or not
   private static final String FUNCTION_WRITE = "CALC.sum = function() { return 42; }; 1";

   private static final String PROBE = """
      [typeof CALC.x, typeof CALC.sum.foo, typeof sum.bar, typeof StyleConstant.Y,
       typeof Chart.Z, typeof isNull.baz, typeof CALC.sum, CALC.sum([1, 2, 3]),
       StyleConstant.PORTRAIT].join(',')
      """;

   private static final String CLEAN = "undefined,undefined,undefined,undefined,undefined," +
      "undefined,function,6,1";

   private static WorksheetScriptEnv env(String org) {
      return new WorksheetScriptEnv(PoolConfig.defaults(), new InitSnapshot(org, Map.of()));
   }

   @Test
   void sandboxesOfTwoOrgsShareOneCalc() throws Exception {
      WorksheetScriptEnv orgA = env("orgA");
      WorksheetScriptEnv orgB = env("orgB");
      Object calc = run(orgA, "CALC");
      assertInstanceOf(Calc.class, calc);
      assertSame(calc, run(orgB, "CALC"));
      assertSame(run(orgA, "StyleConstant"), run(orgB, "StyleConstant"));
   }

   @Test
   void writesToSharedObjectsReachNoOtherContextSandboxOrOrg() throws Exception {
      WorksheetScriptEnv orgA = env("orgA");
      WorksheetScriptEnv orgA2 = env("orgA");
      WorksheetScriptEnv orgB = env("orgB");
      run(orgA, "1");
      run(orgA2, "1");
      run(orgB, "1");

      // the writes run on a second pooled context of orgA's sandbox (the first is held)
      whileHeldElsewhere(orgA, () -> {
         run(orgA, WRITES);
         assertThrows(Exception.class, () -> run(orgA, FUNCTION_WRITE));
         assertEquals(CLEAN, run(orgA, PROBE));
         assertEquals(2, orgA.getMetrics().getSize());
      });

      assertEquals(CLEAN, run(orgA, PROBE));
      assertEquals(CLEAN, run(orgA2, PROBE));
      assertEquals(CLEAN, run(orgB, PROBE));
   }

   /**
    * Multi-tenant: each org's library functions call the shared CALC functions, one org's
    * library shadows a CALC global and writes to CALC; the other org sees neither.
    */
   @Test
   void orgLibrariesUsingCalcStayPerOrg() throws Exception {
      WorksheetScriptEnv orgA = new WorksheetScriptEnv(PoolConfig.defaults(), new InitSnapshot(
         "orgA", Map.of(
            "orgTotal", "function orgTotal(a) { CALC.lastOrg = 'A'; return CALC.sum(a) + 1; }",
            "proper", "function proper(s) { return 'orgA:' + s; }")));
      WorksheetScriptEnv orgB = new WorksheetScriptEnv(PoolConfig.defaults(), new InitSnapshot(
         "orgB", Map.of(
            "orgTotal", "function orgTotal(a) { return CALC.max(a) * 10; }")));

      assertEquals(7.0, run(orgA, "orgTotal([1, 2, 3])"));
      assertEquals("orgA:ab", run(orgA, "proper('ab')"));
      assertEquals(30.0, run(orgB, "orgTotal([1, 2, 3])"));
      assertEquals("Ab", run(orgB, "proper('ab')"));
      assertEquals("Ab", run(orgB, "CALC.proper('ab')"));
      assertEquals("undefined", run(orgB, "typeof CALC.lastOrg"));

      // a later pooled context of each org still has its own library over the shared CALC
      whileHeldElsewhere(orgA, () -> assertEquals("orgA:ab", run(orgA, "proper('ab')")));
      whileHeldElsewhere(orgB, () -> assertEquals("Ab", run(orgB, "proper('ab')")));
      assertEquals("undefined", run(orgA, "typeof CALC.lastOrg"));
   }

   @Test
   void calcFunctionsOfTwoOrgsRunConcurrently() throws Exception {
      WorksheetScriptEnv orgA = env("orgA");
      WorksheetScriptEnv orgB = env("orgB");
      int threads = 8;
      ExecutorService executor = Executors.newFixedThreadPool(threads);
      List<Future<Integer>> futures = new ArrayList<>();

      try {
         for(int t = 0; t < threads; t++) {
            final int seed = t;
            final WorksheetScriptEnv env = (t % 2 == 0) ? orgA : orgB;

            futures.add(executor.submit(() -> {
               int ok = 0;

               for(int i = 0; i < 100; i++) {
                  int n = seed * 1000 + i;
                  Object value = run(env, "CALC.sum([" + n + ", 1, 2]) + abs(-" + n +
                     ") + StyleConstant.PORTRAIT + CALC.proper('ab').length");
                  assertEquals(2.0 * n + 3 + 1 + 2, ((Number) value).doubleValue());
                  ok++;
               }

               return ok;
            }));
         }

         for(Future<Integer> future : futures) {
            assertEquals(100, future.get(120, TimeUnit.SECONDS));
         }
      }
      finally {
         executor.shutdownNow();
      }
   }
}
