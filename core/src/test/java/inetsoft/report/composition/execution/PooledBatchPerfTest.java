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
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static inetsoft.report.composition.execution.PoolOffConditionFilterLockingTest.allRows;
import static inetsoft.report.composition.execution.PooledBatchClaimTest.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Gate G6 (bug #76960, spec §10.8, §14.3, §14.8): over 1M rows, sequential moreRows and
 * getObject loops over a formula lens and over a condition filter on it run within 10% of
 * pool off, with at most rows/batchRows + 1 cleans. Run with -Dctxpool.perf=true.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
@EnabledIfSystemProperty(named = "ctxpool.perf", matches = "true")
public class PooledBatchPerfTest {
   @ParameterizedTest
   @EnumSource(Shape.class)
   public void poolOnWithinTenPercentOfPoolOff(Shape shape) throws Exception {
      long off = Long.MAX_VALUE;
      long on = Long.MAX_VALUE;
      WorksheetScriptEnv pooled = null;
      long cleans = 0;

      // symmetric (spec §14.14): both envs, and the pooled env's first slot with its first
      // clean, are built before the timed region; the minimum of 5 runs per shape
      for(int run = 0; run < 5; run++) {
         GraalJavaScriptEnv plain = new GraalJavaScriptEnv();
         plain.init();
         off = Math.min(off, time(shape, plain, false));
         pooled = PoolTestSupport.env();
         pooled.init();

         try(SlotClaim warm = pooled.claimSlot()) {
            PoolTestSupport.run(pooled, "1");
         }

         long before = pooled.getMetrics().getCleans();
         on = Math.min(on, time(shape, pooled, true));
         cleans = pooled.getMetrics().getCleans() - before;
      }

      System.out.printf("G6 %s: pool off %d ms, pool on %d ms, ratio %.3f, cleans %d, " +
                        "cleansPerExec %.6f%n", shape, off, on, (double) on / off, cleans,
                        pooled.getMetrics().cleansPerExec());
      assertTrue(on <= off * 1.10, shape + ": pool on " + on + " ms vs off " + off + " ms");
      assertTrue(cleans <= PERF_ROWS / pooled.getConfig().batchRows() + 1, "cleans " + cleans);
   }

   private static long time(Shape shape, ScriptEnv env, boolean pool) {
      TableLens lens = formula(table(PERF_ROWS), env, "field['value'] + 1");

      if(shape == Shape.CF2_MORE_ROWS || shape == Shape.CF2_GET_OBJECT) {
         AssetQuerySandbox box = mock(AssetQuerySandbox.class);
         when(box.peekScriptEnv()).thenReturn(env);
         when(box.isScriptPoolMode()).thenReturn(pool);
         lens = PostProcessor.filter(lens, allRows(), box);
      }

      boolean read = shape == Shape.FTL_GET_OBJECT || shape == Shape.CF2_GET_OBJECT;
      long start = System.nanoTime();

      for(int r = 1; lens.moreRows(r); r++) {
         if(read) {
            lens.getObject(r, 3);
         }
      }

      return (System.nanoTime() - start) / 1_000_000;
   }

   public enum Shape {
      FTL_MORE_ROWS, FTL_GET_OBJECT, CF2_MORE_ROWS, CF2_GET_OBJECT
   }

   private static final int PERF_ROWS = 1_000_000;
}
