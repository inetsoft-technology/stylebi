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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static inetsoft.report.composition.execution.PooledBatchClaimTest.formula;
import static inetsoft.report.composition.execution.PooledBatchClaimTest.table;

/**
 * Cost of finding F1's fix (bug #77123) on a chained formula lens: FTL(FTL(FTL(base))) on one
 * pooled env, read sequentially. A pooled batch now loads its base before it takes its lens
 * lock and opens its span, so a formula base computes under its own claim instead of nesting
 * in the outer batch's; this prints the time and the context cleans per run, pool off for
 * reference. Measurement only, no assertion. Run with -Dctxpool.perf=true.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
@EnabledIfSystemProperty(named = "ctxpool.perf", matches = "true")
public class PooledChainPerfTest {
   @Test
   public void chainedFormulaLenses() throws Exception {
      for(int run = 1; run <= RUNS; run++) {
         GraalJavaScriptEnv plain = new GraalJavaScriptEnv();
         plain.init();
         PoolTestSupport.run(plain, "1");
         long off = time(plain);

         WorksheetScriptEnv pooled = PoolTestSupport.env();
         pooled.init();

         try(SlotClaim warm = pooled.claimSlot()) {
            PoolTestSupport.run(pooled, "1");
         }

         long cleans = pooled.getMetrics().getCleans();
         long execs = pooled.getMetrics().getExecs();
         long on = time(pooled);

         System.out.printf("CHAIN run %d: levels %d, rows %d, pool off %d ms, pool on %d ms, " +
                           "cleans %d, execs %d%n", run, LEVELS, ROWS, off, on,
                           pooled.getMetrics().getCleans() - cleans,
                           pooled.getMetrics().getExecs() - execs);
      }
   }

   private static long time(ScriptEnv env) {
      TableLens lens = table(ROWS);

      for(int i = 0; i < LEVELS; i++) {
         lens = formula(lens, env, "field['value'] + 1");
      }

      int col = lens.getColCount() - 1;
      long start = System.nanoTime();

      for(int r = 1; lens.moreRows(r); r++) {
         lens.getObject(r, col);
      }

      return (System.nanoTime() - start) / 1_000_000;
   }

   private static final int RUNS = 3;
   private static final int LEVELS = 3;
   private static final int ROWS = 100_000;
}
