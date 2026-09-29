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
package inetsoft.report.composition.execution.reliability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.test.*;
import inetsoft.util.script.graal.pool.PoolMetrics;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Serial timing probe of the harness shapes, pool on and off (Testing #77123): one thread, a
 * fresh sandbox per run, 3 timed runs per cell after a warm-up. Prints wall ms (min and
 * median), context cleans and executions of the pooled env, and base.moreRows() calls per
 * run. Only with -Drel.perf=true; -Drel.perf.script picks the script (default a computing
 * one). Used for the F1 A/B (FormulaTableLens with and without #5857).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class RelFtlPerfProbe {
   @Test
   public void probe() throws Exception {
      Assumptions.assumeTrue(Boolean.getBoolean("rel.perf"));
      Logger logger = (Logger) LoggerFactory.getLogger("inetsoft");
      Level level = logger.getLevel();
      logger.setLevel(Level.ERROR);
      String script = System.getProperty("rel.perf.script", "field['value'] / 3");
      int reps = Integer.getInteger("rel.perf.reps", 3);
      Shape[] shapes = { Shape.FTL, Shape.FTL_UNDER_CF2, Shape.CONDITION };
      List<ReadPattern> reads = List.of(ReadPattern.SEQUENTIAL, ReadPattern.PAGED_100,
                                        ReadPattern.random(77123));
      StringBuilder out = new StringBuilder("RelFtlPerfProbe script=" + script + " reps=" +
                                            reps + "\n");
      RelPipeline.COUNT_BASE.set(true);

      try {
         // warm-up: every cell once, untimed
         for(Shape shape : shapes) {
            for(boolean pool : new boolean[] { true, false }) {
               for(ReadPattern read : reads) {
                  run(script, shape, pool, read);
               }
            }
         }

         out.append("shape|pool|read|min ms|median ms|cleans|execs|base.moreRows|" +
                    "min cpu ms|median cpu ms\n");

         for(Shape shape : shapes) {
            for(boolean pool : new boolean[] { true, false }) {
               for(ReadPattern read : reads) {
                  long[] ms = new long[reps];
                  long[] cpu = new long[reps];
                  long[] last = null;

                  for(int i = 0; i < reps; i++) {
                     long[] m = run(script, shape, pool, read);
                     ms[i] = m[0];
                     cpu[i] = m[4];
                     last = m;
                  }

                  Arrays.sort(ms);
                  Arrays.sort(cpu);
                  out.append(shape).append('|').append(pool ? "on" : "off").append('|')
                     .append(read).append('|').append(ms[0]).append('|').append(ms[reps / 2])
                     .append('|').append(last[1]).append('|').append(last[2]).append('|')
                     .append(last[3]).append('|').append(cpu[0]).append('|')
                     .append(cpu[reps / 2]).append('\n');
               }
            }
         }
      }
      finally {
         RelPipeline.COUNT_BASE.remove();
         RelPipeline.LAST_BASE.remove();
         logger.setLevel(level);
      }

      System.out.println(out);
   }

   /**
    * @return wall ms, cleans, execs, base moreRows calls and this thread's CPU ms (less
    * sensitive to other load than wall time) of one run on a fresh sandbox.
    */
   private static long[] run(String script, Shape shape, boolean pool, ReadPattern read)
      throws Exception
   {
      AssetQuerySandbox box = RelPipeline.sandbox(pool ? RelConfig.on() : RelConfig.off());

      try {
         PoolMetrics metrics = box.getScriptEnv() instanceof WorksheetScriptEnv env
            ? env.getMetrics() : null;
         long cleans = metrics == null ? 0 : metrics.getCleans();
         long execs = metrics == null ? 0 : metrics.getExecs();
         ThreadMXBean threads = ManagementFactory.getThreadMXBean();
         long cpu = threads.getCurrentThreadCpuTime();
         long start = System.nanoTime();
         List<String> cells = RelPipeline.run(script, shape, box, read);
         long ms = (System.nanoTime() - start) / 1_000_000;
         cpu = (threads.getCurrentThreadCpuTime() - cpu) / 1_000_000;
         assertEquals(shape == Shape.CONDITION ? RelPipeline.CONDITIONS * (RelPipeline.ROWS + 1)
                         : shape == Shape.FTL ? RelPipeline.ROWS : RelPipeline.ROWS - 1,
                      cells.size(), shape + " cells");
         RelPipeline.CountingTable base = RelPipeline.LAST_BASE.get();
         return new long[] {
            ms,
            metrics == null ? 0 : metrics.getCleans() - cleans,
            metrics == null ? 0 : metrics.getExecs() - execs,
            base == null ? -1 : base.moreRows.get(),
            cpu
         };
      }
      finally {
         box.dispose();
      }
   }
}
