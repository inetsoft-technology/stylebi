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
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.pool.PoolMetrics;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MR4 concurrency metamorphic relation of the worksheet script context pool (Testing #77123):
 * with the pool on, scripts run concurrently give what one thread gives with the pool off.
 * <ul>
 * <li>(a) one sandbox, 8 threads each building its own lens over it, each with its own read
 * pattern;</li>
 * <li>(b) 8 sandboxes in parallel;</li>
 * <li>(c) as (a) while a 9th thread puts and removes env variables and resets the env every
 * 50 ms.</li>
 * </ul>
 * Differences are classified as in {@link RelMetamorphicTest}. No claim may leak and no cell
 * may fail with a GraalJS multi-threaded access error. A repetition runs 8 lenses of 1200 rows
 * at once (about 10 s on a loaded machine), so the default run has 3 repetitions per variant;
 * -Drel.reps=N sets the count, -Drel.long=true runs 500.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class RelConcurrencyMetamorphicTest {
   @BeforeAll
   public static void setUp() throws Exception {
      Logger logger = (Logger) LoggerFactory.getLogger("inetsoft");
      savedLevel = logger.getLevel();
      logger.setLevel(Level.ERROR);

      // the synthetic set and every 20th corpus script
      cases = RelMetamorphicTest.cases(20).toList();
   }

   @AfterAll
   public static void summary() {
      ((Logger) LoggerFactory.getLogger("inetsoft")).setLevel(savedLevel);
      StringBuilder str = new StringBuilder("RelConcurrencyMetamorphicTest summary (reps=" +
                                            REPS + ", cases=" + cases.size() + ")\n");
      STATS.forEach((k, v) -> str.append("  ").append(k).append(" = ").append(v).append('\n'));
      DRIFTS.forEach(d -> str.append("  drift: ").append(d).append('\n'));
      System.out.println(str);
   }

   @Test
   public void oneSandbox() throws Exception {
      for(int rep = 0; rep < REPS; rep++) {
         repeat("a", rep, false, false);
      }
   }

   @Test
   public void manySandboxes() throws Exception {
      for(int rep = 0; rep < REPS; rep++) {
         repeat("b", rep, true, false);
      }
   }

   @Test
   public void oneSandboxWithEnvChurn() throws Exception {
      for(int rep = 0; rep < REPS; rep++) {
         repeat("c", rep, false, true);
      }
   }

   /**
    * One repetition: THREADS runs of different scripts, shapes and read patterns at once.
    */
   private void repeat(String variant, int rep, boolean ownBoxes, boolean churn)
      throws Exception
   {
      RelConfig cfg = RelConfig.on();
      List<AssetQuerySandbox> boxes = new ArrayList<>();
      long leaked = PoolMetrics.nodeLeakedClaims();
      long multi = RelPipeline.MULTI_THREADED.get();
      ExecutorService executor = Executors.newFixedThreadPool(THREADS + 1);
      AtomicBoolean done = new AtomicBoolean();

      try {
         for(int i = 0; i < (ownBoxes ? THREADS : 1); i++) {
            AssetQuerySandbox box = RelPipeline.sandbox(cfg);
            assertInstanceOf(WorksheetScriptEnv.class, box.getScriptEnv());
            boxes.add(box);
         }

         Future<?> churner = null;

         if(churn) {
            ScriptEnv env = boxes.get(0).getScriptEnv();
            churner = executor.submit(() -> {
               long last = System.currentTimeMillis();

               for(int i = 0; !done.get(); i++) {
                  env.put("x" + i, i);
                  env.remove("x" + (i - 1));

                  if(System.currentTimeMillis() - last >= 50) {
                     env.reset();
                     STATS.computeIfAbsent("c.resets", k -> new AtomicLong()).incrementAndGet();
                     last = System.currentTimeMillis();
                  }

                  Thread.onSpinWait();
               }

               return null;
            });
         }

         CyclicBarrier start = new CyclicBarrier(THREADS);
         List<Future<String>> runs = new ArrayList<>();

         for(int t = 0; t < THREADS; t++) {
            int index = (rep * THREADS + t) % cases.size();
            RelMetamorphicTest.Case c = cases.get(index);
            Shape shape = Shape.values()[(rep + t) % Shape.values().length];
            List<ReadPattern> patterns = ReadPattern.all(c.seed() + rep);
            ReadPattern read = patterns.get(t % patterns.size());
            AssetQuerySandbox box = boxes.get(ownBoxes ? t : 0);
            // the single-thread oracle, computed before the threads start
            ORACLES.computeIfAbsent(key(c, shape), k -> RelMetamorphicTest.run(
               c.script(), shape, RelConfig.off(), ReadPattern.SEQUENTIAL));

            runs.add(executor.submit(() -> {
               start.await(30, TimeUnit.SECONDS);
               List<String> actual;

               try {
                  actual = RelPipeline.run(c.script(), shape, box, read);
               }
               catch(Exception ex) {
                  actual = List.of("RUN-" + RelPipeline.error(ex));
               }

               return check(variant, c, shape, read, actual);
            }));
         }

         List<String> failures = new ArrayList<>();

         for(Future<String> run : runs) {
            String failure = run.get(5, TimeUnit.MINUTES);

            if(failure != null) {
               failures.add(failure);
            }
         }

         done.set(true);

         if(churner != null) {
            churner.get(30, TimeUnit.SECONDS);
         }

         assertEquals(List.of(), failures, variant + " rep " + rep);
      }
      finally {
         done.set(true);
         executor.shutdownNow();
         boxes.forEach(AssetQuerySandbox::dispose);
      }

      assertEquals(leaked, PoolMetrics.nodeLeakedClaims(), variant + " rep " + rep + " leaked");
      assertEquals(multi, RelPipeline.MULTI_THREADED.get(),
                   variant + " rep " + rep + " multi-threaded access");
   }

   /**
    * @return {@code null} if the run gives the oracle or a classified drift, else the failure.
    */
   private static String check(String variant, RelMetamorphicTest.Case c, Shape shape,
                               ReadPattern read, List<String> actual)
   {
      List<String> expected = RelMetamorphicTest.comparable(ORACLES.get(key(c, shape)),
                                                            c.script());
      actual = RelMetamorphicTest.comparable(actual, c.script());
      STATS.computeIfAbsent(variant + ".comparisons", k -> new AtomicLong()).incrementAndGet();
      STATS.computeIfAbsent(variant + ".kind." +
         RelMetamorphicTest.kind(shape, ORACLES.get(key(c, shape))), k -> new AtomicLong())
         .incrementAndGet();

      if(expected.equals(actual)) {
         return null;
      }

      String diff = RelMetamorphicTest.diff(expected, actual);
      RelMetamorphicTest.Drift drift = RelMetamorphicTest.drift(expected, actual, c.script());

      if(drift != RelMetamorphicTest.Drift.NONE) {
         STATS.computeIfAbsent(variant + ".drift." + drift, k -> new AtomicLong())
            .incrementAndGet();
         DRIFTS.add(variant + " " + drift + " " + c.label() + " " + shape + " " + read + ": " +
                    diff);
         return null;
      }

      STATS.computeIfAbsent(variant + ".unknown", k -> new AtomicLong()).incrementAndGet();
      return variant + " " + c.label() + " " + shape + " " + read + " differs: " + diff +
         "\nscript: " + c.script();
   }

   private static String key(RelMetamorphicTest.Case c, Shape shape) {
      return shape + "\u0000" + c.script();
   }

   private static final int THREADS = 8;
   private static final int REPS =
      Integer.getInteger("rel.reps", Boolean.getBoolean("rel.long") ? 500 : 3);
   private static final Map<String, List<String>> ORACLES = new ConcurrentHashMap<>();
   private static final Map<String, AtomicLong> STATS = new ConcurrentSkipListMap<>();
   private static final List<String> DRIFTS = Collections.synchronizedList(new ArrayList<>());
   private static List<RelMetamorphicTest.Case> cases;
   private static Level savedLevel;
}
