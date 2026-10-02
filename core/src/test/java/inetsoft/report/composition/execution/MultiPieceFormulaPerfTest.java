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
package inetsoft.report.composition.execution;

import inetsoft.report.TableLens;
import inetsoft.test.*;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.GraalJavaScriptEnv;
import inetsoft.util.script.graal.pool.PoolTestSupport;
import inetsoft.util.script.graal.pool.SlotClaim;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static inetsoft.report.composition.execution.PooledBatchClaimTest.formula;
import static inetsoft.report.composition.execution.PooledBatchClaimTest.table;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bug #77249: a formula that the #75688 completion split cuts into pieces (a statement
 * followed by a top-level {@code if}) runs within a small factor of a single-piece formula
 * over a formula table, pool off and on. Before the fix every piece was a direct eval that
 * GraalJS re-parsed on every row (20-100x the single-piece time). Run with
 * -Dctxpool.perf=true.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
@EnabledIfSystemProperty(named = "ctxpool.perf", matches = "true")
public class MultiPieceFormulaPerfTest {
   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   public void multiPieceFormulasRunNearTheSinglePieceTime(boolean pool) throws Exception {
      long single = best(SINGLE, pool);

      for(String f : MULTI) {
         long multi = best(f, pool);
         System.out.printf("#77249 pool %s: %d ms vs single-piece %d ms (%.2fx): %s%n",
                           pool ? "on" : "off", multi, single, (double) multi / single, f);
         assertTrue(multi <= single * 3 + 200,
                    f + ": " + multi + " ms vs single-piece " + single + " ms");
      }
   }

   // the minimum of RUNS runs, each on a fresh env and table after one warm-up exec
   private static long best(String expr, boolean pool) throws Exception {
      long best = Long.MAX_VALUE;

      for(int run = 0; run < RUNS; run++) {
         ScriptEnv env;

         if(pool) {
            WorksheetScriptEnv pooled = PoolTestSupport.env();
            pooled.init();

            try(SlotClaim warm = pooled.claimSlot()) {
               PoolTestSupport.run(pooled, "1");
            }

            env = pooled;
         }
         else {
            GraalJavaScriptEnv plain = new GraalJavaScriptEnv();
            plain.init();
            PoolTestSupport.run(plain, "1");
            env = plain;
         }

         TableLens lens = formula(table(ROWS), env, expr);
         long start = System.nanoTime();

         for(int r = 1; lens.moreRows(r); r++) {
            lens.getObject(r, 3);
         }

         long ms = (System.nanoTime() - start) / 1_000_000;
         System.out.printf("#77249 pool %s run %d: %d ms: %s%n", pool ? "on" : "off", run,
                           ms, expr);
         best = Math.min(best, ms);
      }

      return best;
   }

   // the formulas of the issue
   private static final String[] MULTI = {
      "var v = field['value']; if(v > 100) { 0 } else { v + 1 }",
      "var acc = (acc || 0) + field['value']; if(acc < 0) { acc = 0; } acc",
      "if(field['id'] > 50000) { -1 } if(field['id'] % 2 == 0) { field['id'] }"
   };
   private static final String SINGLE = "var v = field['value']; v > 100 ? 0 : v + 1";
   private static final int ROWS = 100_000;
   private static final int RUNS = 3;
}
