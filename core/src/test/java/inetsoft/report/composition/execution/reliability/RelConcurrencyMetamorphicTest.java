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
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.PoolMetrics;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
@Tag("slow")
public class RelConcurrencyMetamorphicTest {
   @BeforeAll
   public static void setUp() throws Exception {
      Logger logger = (Logger) LoggerFactory.getLogger("inetsoft");
      savedLevel = logger.getLevel();
      logger.setLevel(Level.ERROR);
      OwnedVarWarnings.install();

      // the synthetic set and every 20th corpus script
      cases = RelMetamorphicTest.cases(20).toList();
   }

   @AfterAll
   public static void summary() {
      ((Logger) LoggerFactory.getLogger("inetsoft")).setLevel(savedLevel);
      OwnedVarWarnings.uninstall();
      StringBuilder str = new StringBuilder("RelConcurrencyMetamorphicTest summary (reps=" +
                                            REPS + ", cases=" + cases.size() + ")\n");
      STATS.forEach((k, v) -> str.append("  ").append(k).append(" = ").append(v).append('\n'));
      DRIFTS.forEach(d -> str.append("  drift: ").append(d).append('\n'));
      str.append("  unattributed owned-var warnings = ").append(OwnedVarWarnings.unattributed())
         .append('\n');
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
    * (d) as (a), every thread a formula lens whose vars hold objects (the synthetic plain-data
    * and lossy object vars), so more tables are resident on the sandbox than it has exclusive
    * homes (maxHomes 4): the rest take their homes over, handing the values off.
    */
   @Test
   public void objectVarsOnOneSandbox() throws Exception {
      for(int rep = 0; rep < REPS; rep++) {
         repeat("d", rep, false, false, RelConfig.on(), objectCases(), ROW_SHAPES);
      }
   }

   /**
    * (e) as (d) with no exclusive home (maxHomes 0): every home is taken over by the next
    * claim of another table.
    */
   @Test
   public void objectVarsWithoutExclusiveHomes() throws Exception {
      RelConfig cfg = RelConfig.on().with(PoolConfig.MAX_HOMES, "0");

      for(int rep = 0; rep < REPS; rep++) {
         repeat("e", rep, false, false, cfg, objectCases(), ROW_SHAPES);
      }
   }

   /**
    * (f) one sandbox, 8 threads, each reading 3 object-var formula lenses interleaved 7 rows
    * at a time with 1-row batches, with the default homes and with none: the lenses' batches
    * take over, pull from and rebuild each other's homes all the time. Each lens gives the
    * pool-off result, but for a lossy var that warned and restarted.
    */
   @ParameterizedTest(name = "maxHomes={0}")
   @ValueSource(strings = { "4", "0" })
   public void interleavedObjectVars(String maxHomes) throws Exception {
      interleaved("f" + maxHomes, maxHomes, false, REPS);
   }

   /**
    * (f) with no excuse for a plain-data var lost to a home in use by another thread
    * (finding B1-R2-1): every lens reads its own vars on one thread, yet another thread's
    * claim could hold its idle home for an instant (a failed take-over given back when the
    * lens's lock was busy), and a read then lost the lens's plain data (loud, restarted, never
    * stale). Fixed by #5965, which brings its own deterministic regression test; this pin is
    * now a probabilistic regression guard. Before the fix it hit about 1 in 5,000 interleaved
    * lens runs, so {@code -Drel.long=true} runs {@code -Drel.pinReps} (320) reps per maxHomes:
    * 2 x 320 x 24 = 15,360 lens runs, a hit with probability about 95% at that rate. A run
    * after #5965 merged passed all 15,360 with no plain-data loss.
    */
   @ParameterizedTest(name = "maxHomes={0}")
   @ValueSource(strings = { "4", "0" })
   public void interleavedPlainObjectVarsAreNeverLost(String maxHomes) throws Exception {
      Assumptions.assumeTrue(Boolean.getBoolean("rel.long"), "the pin needs -Drel.long=true");
      interleaved("strict-f" + maxHomes, maxHomes, true, Integer.getInteger("rel.pinReps", 320));
   }

   private void interleaved(String variant, String maxHomes, boolean strict, int reps)
      throws Exception
   {
      RelConfig cfg = RelConfig.on().with(PoolConfig.BATCH_ROWS, "1")
         .with(PoolConfig.MAX_BATCH_ROWS, "1").with(PoolConfig.MAX_HOMES, maxHomes);
      List<RelMetamorphicTest.Case> objects = objectCases();

      for(int rep = 0; rep < reps; rep++) {
         AssetQuerySandbox box = RelPipeline.sandbox(cfg);
         ExecutorService executor = Executors.newFixedThreadPool(THREADS);
         long leaked = PoolMetrics.nodeLeakedClaims();

         try {
            CyclicBarrier start = new CyclicBarrier(THREADS);
            List<Future<List<String>>> runs = new ArrayList<>();

            for(int t = 0; t < THREADS; t++) {
               // 3 different scripts per thread, whose var names are disjoint (objectCases),
               // so a warning names the var of one of them
               List<RelMetamorphicTest.Case> mine = new ArrayList<>();

               for(int k = 0; k < 3; k++) {
                  mine.add(objects.get((rep * THREADS + t * 3 + k) % objects.size()));
               }

               for(RelMetamorphicTest.Case c : mine) {
                  ORACLES.computeIfAbsent(key(c, Shape.FTL), k -> RelMetamorphicTest.run(
                     c.script(), Shape.FTL, RelConfig.off(), ReadPattern.SEQUENTIAL));
               }

               runs.add(executor.submit(() -> {
                  start.await(30, TimeUnit.SECONDS);
                  List<List<String>> cells;
                  Map<String, String> lost;

                  try(OwnedVarWarnings.Recording recording = OwnedVarWarnings.record()) {
                     cells = RelPipeline.interleaved(
                        mine.stream().map(RelMetamorphicTest.Case::script).toList(), box, 7);
                     lost = recording.lost();
                  }

                  List<String> failures = new ArrayList<>();

                  for(int k = 0; k < mine.size(); k++) {
                     RelMetamorphicTest.Case c = mine.get(k);
                     Set<String> vars = RelMetamorphicTest.varNames(c.script());
                     Map<String, String> own = new LinkedHashMap<>(lost);
                     own.keySet().retainAll(vars);
                     String failure = strict
                        ? RelMetamorphicTest.plainLoss(c.script(), own, false) : null;
                     failure = failure != null ? variant + " " + c.label() + " " + failure
                        : check(variant, c, Shape.FTL, ReadPattern.SEQUENTIAL, cells.get(k),
                                own);

                     if(failure != null) {
                        failures.add(failure);
                     }
                  }

                  return failures;
               }));
            }

            List<String> failures = new ArrayList<>();

            for(Future<List<String>> run : runs) {
               failures.addAll(run.get(5, TimeUnit.MINUTES));
            }

            PoolMetrics metrics = ((WorksheetScriptEnv) box.getScriptEnv()).getMetrics();
            add(variant + ".handOffs", metrics.getHandOffs());
            add(variant + ".takeOvers", metrics.getTakeOvers());
            add(variant + ".pulls", metrics.getPulls());
            add(variant + ".rebuilds", metrics.getRebuilds());
            assertEquals(List.of(), failures, variant + " rep " + rep);
         }
         finally {
            executor.shutdownNow();
            box.dispose();
         }

         assertEquals(leaked, PoolMetrics.nodeLeakedClaims(), variant + " rep " + rep + " leaked");
      }
   }

   /**
    * @return the synthetic object-var cases, whose var names are disjoint: the interleaved
    * variant attributes a thread's warnings to its lenses by var name.
    */
   private static List<RelMetamorphicTest.Case> objectCases() {
      List<RelMetamorphicTest.Case> list = cases.stream()
         .filter(c -> RelMetamorphicTest.PLAIN_OBJECT_VARS.contains(c.script()) ||
                      RelMetamorphicTest.LOSSY_OBJECT_VARS.contains(c.script()))
         .toList();
      Set<String> names = new HashSet<>();

      for(RelMetamorphicTest.Case c : list) {
         for(String name : RelMetamorphicTest.varNames(c.script())) {
            assertTrue(names.add(name), "var " + name + " of " + c.label() + " is not unique");
         }
      }

      return list;
   }

   private void repeat(String variant, int rep, boolean ownBoxes, boolean churn)
      throws Exception
   {
      repeat(variant, rep, ownBoxes, churn, RelConfig.on(), cases, Shape.values());
   }

   /**
    * One repetition: THREADS runs of different scripts, shapes and read patterns at once.
    */
   private void repeat(String variant, int rep, boolean ownBoxes, boolean churn,
                       RelConfig cfg, List<RelMetamorphicTest.Case> cases, Shape[] shapes)
      throws Exception
   {
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
            Shape shape = shapes[(rep + t) % shapes.length];
            List<ReadPattern> patterns = ReadPattern.all(c.seed() + rep);
            ReadPattern read = patterns.get(t % patterns.size());
            AssetQuerySandbox box = boxes.get(ownBoxes ? t : 0);
            // the single-thread oracle, computed before the threads start
            ORACLES.computeIfAbsent(key(c, shape), k -> RelMetamorphicTest.run(
               c.script(), shape, RelConfig.off(), ReadPattern.SEQUENTIAL));

            runs.add(executor.submit(() -> {
               start.await(30, TimeUnit.SECONDS);
               List<String> actual;
               Map<String, String> lost;

               // the loss warnings of the lens this thread reads
               try(OwnedVarWarnings.Recording recording = OwnedVarWarnings.record()) {
                  try {
                     actual = RelPipeline.run(c.script(), shape, box, read);
                  }
                  catch(Exception ex) {
                     actual = List.of("RUN-" + RelPipeline.error(ex));
                  }

                  lost = recording.lost();
               }

               return check(variant, c, shape, read, actual, lost);
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

         // how often the values of object vars moved between contexts
         for(AssetQuerySandbox box : boxes) {
            PoolMetrics metrics = ((WorksheetScriptEnv) box.getScriptEnv()).getMetrics();
            add(variant + ".handOffs", metrics.getHandOffs());
            add(variant + ".takeOvers", metrics.getTakeOvers());
            add(variant + ".pulls", metrics.getPulls());
            add(variant + ".rebuilds", metrics.getRebuilds());
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
                               ReadPattern read, List<String> actual, Map<String, String> lost)
   {
      List<String> expected = RelMetamorphicTest.comparable(ORACLES.get(key(c, shape)),
                                                            c.script());
      actual = RelMetamorphicTest.comparable(actual, c.script());
      STATS.computeIfAbsent(variant + ".comparisons", k -> new AtomicLong()).incrementAndGet();
      STATS.computeIfAbsent(variant + ".kind." +
         RelMetamorphicTest.kind(shape, ORACLES.get(key(c, shape))), k -> new AtomicLong())
         .incrementAndGet();

      if(!lost.isEmpty()) {
         STATS.computeIfAbsent(variant + ".warned." + shape, k -> new AtomicLong())
            .incrementAndGet();
         DRIFTS.add(variant + " warned " + c.label() + " " + shape + " " + read + ": " + lost);
      }

      // a plain-data object var is kept: its loss is a finding, but for a home in use by
      // another thread
      String plainLoss = RelMetamorphicTest.plainLoss(c.script(), lost, true);

      if(plainLoss != null) {
         STATS.computeIfAbsent(variant + ".unknown", k -> new AtomicLong()).incrementAndGet();
         return variant + " " + c.label() + " " + shape + " " + read + " " + plainLoss +
            "\nscript: " + c.script();
      }

      if(expected.equals(actual)) {
         return null;
      }

      String diff = RelMetamorphicTest.diff(expected, actual);
      RelMetamorphicTest.Drift drift =
         RelMetamorphicTest.drift(expected, actual, c.script(), shape, lost);

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

   private static void add(String key, long n) {
      STATS.computeIfAbsent(key, k -> new AtomicLong()).addAndGet(n);
   }

   private static String key(RelMetamorphicTest.Case c, Shape shape) {
      return shape + "\u0000" + c.script();
   }

   private static final int THREADS = 8;
   private static final Shape[] ROW_SHAPES = { Shape.FTL, Shape.FTL_UNDER_CF2 };
   private static final int REPS =
      Integer.getInteger("rel.reps", Boolean.getBoolean("rel.long") ? 500 : 3);
   private static final Map<String, List<String>> ORACLES = new ConcurrentHashMap<>();
   private static final Map<String, AtomicLong> STATS = new ConcurrentSkipListMap<>();
   private static final List<String> DRIFTS = Collections.synchronizedList(new ArrayList<>());
   private static List<RelMetamorphicTest.Case> cases;
   private static Level savedLevel;
}
