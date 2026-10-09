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
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import inetsoft.util.script.graal.pool.*;
import inetsoft.util.stall.StallWatchdog;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Soak of the worksheet script context pool (Testing #77123, Task 9: R4 bounded resources, R6
 * sustained performance). THREADS workers run a mixed pool-on workload for
 * {@code -Drel.soak.minutes} (60) minutes: mostly short-lived sandboxes (build, run 1-3
 * pipeline shapes of random scripts with random read patterns, sometimes reset the env
 * between them, dispose), some runs on 2 long-lived sandboxes shared by all workers and reset
 * now and then by another thread, a fifth of the short-lived sandboxes with a non-default
 * batch configuration. Scripts are the synthetic set, every {@code -Drel.soak.step}-th (3)
 * corpus script, and 1% ({@code -Drel.soak.timeoutPercent}) of the time a script whose row 600 loops until the 2 s script
 * timeout interrupts it. Every result is compared with the pool-off sequential oracle of its
 * (script, shape), all computed before the soak starts, and differences are classified as in
 * {@link RelMetamorphicTest}.
 *
 * <p>Every {@code -Drel.soak.sampleSeconds} (30) s the workers are paused until none runs
 * (so no run holds lens data), the heap is collected, and one CSV row is written: open claims,
 * node slots and counters, leaked claims, interrupt timeouts, heap and non-heap after GC,
 * live threads, paranoia violations, the window's runs and mean run time, stall / pool log
 * warnings, system CPU load. Pass: heap-after-GC slope over the last 2/3 < 1 MB / 10 min,
 * node-slot slope < 1 / 10 min, thread slope < 1 / 10 min, no open claim at any pause, no
 * leaked claim, no unclassified difference, no stall warning, no multi-threaded access, no
 * paranoia violation (run with {@code -Dscript.ws.contextPool.paranoid=true} for the paranoid
 * soak). Skipped unless {@code -Drel.long=true}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class RelSoakTest {
   @Test
   public void soak() throws Exception {
      Assumptions.assumeTrue(Boolean.getBoolean("rel.long"), "the soak needs -Drel.long=true");
      String tag = (PoolParanoia.enabled() ? "paranoid-" : "") +
         new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date());
      Path dir = Paths.get(System.getProperty("rel.soak.dir", "target/rel-soak"));
      Files.createDirectories(dir);
      Path csv = dir.resolve("soak-" + tag + ".csv");
      Map<String, Level> levels = quiet();
      OwnedVarWarnings.install();
      Counter appender = new Counter();
      appender.start();
      root().addAppender(appender);
      String timeout = SreeEnv.getProperty("script.execution.timeout");
      SreeEnv.setProperty("script.execution.timeout", "2");
      refreshTimeout();
      ExecutorService workers = Executors.newFixedThreadPool(THREADS, r -> {
         Thread thread = new Thread(r, "rel-soak-" + THREAD_IDS.incrementAndGet());
         thread.setDaemon(true);
         return thread;
      });
      AssetQuerySandbox[] longLived = new AssetQuerySandbox[LONG_LIVED];

      try(PrintWriter out = new PrintWriter(Files.newBufferedWriter(csv))) {
         List<RelMetamorphicTest.Case> cases = cases();
         long warm = System.currentTimeMillis();
         warmOracles(cases, workers);
         String warmup = "warmup: " + cases.size() + " cases, " + ORACLES.size() +
            " oracles in " + (System.currentTimeMillis() - warm) / 1000 + " s";
         System.out.println(warmup);

         for(int i = 0; i < LONG_LIVED; i++) {
            longLived[i] = RelPipeline.sandbox(RelConfig.on());
         }

         long leaked0 = PoolMetrics.nodeLeakedClaims();
         long violations0 = PoolParanoia.violations();
         long inconclusive0 = PoolParanoia.inconclusive();
         long interrupts0 = PoolMetrics.nodeInterruptTimeouts();
         long multi0 = RelPipeline.MULTI_THREADED.get();
         appender.clear();
         out.println(HEADER);
         long start = System.currentTimeMillis();
         long end = start + MINUTES * 60_000L;
         AtomicBoolean done = new AtomicBoolean();
         List<Future<?>> futures = new ArrayList<>();

         for(int t = 0; t < THREADS; t++) {
            long seed = SEED + t;
            futures.add(workers.submit(() -> work(new Random(seed), cases, longLived, done)));
         }

         Thread resetter = new Thread(() -> resets(longLived, done), "rel-soak-reset");
         resetter.setDaemon(true);
         resetter.start();
         List<double[]> samples = new ArrayList<>();
         long lastRuns = 0;
         long lastMillis = 0;

         while(System.currentTimeMillis() < end) {
            Thread.sleep(SAMPLE_SECONDS * 1000L);
            workerFailure(futures);
            long quiesce = pause();

            try {
               int open = SlotClaim.openClaims();
               System.gc();
               Thread.sleep(200);
               System.gc();
               double heap = mb(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
               double nonHeap =
                  mb(ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed());
               long runs = count("runs");
               long millis = count("runMillis");
               double[] row = {
                  (System.currentTimeMillis() - start) / 60_000.0, quiesce, open,
                  PoolMetrics.nodeSlots(), PoolMetrics.nodeMaxSandboxSlots(),
                  PoolMetrics.nodeCreations(), PoolMetrics.nodeEvictions(),
                  PoolMetrics.nodeDoomedCloses(), PoolMetrics.nodeCleans(),
                  PoolMetrics.nodeExecs(), PoolMetrics.nodeLeakedClaims() - leaked0,
                  PoolMetrics.nodeInterruptTimeouts() - interrupts0,
                  PoolMetrics.nodeCopyMutations(), heap, nonHeap,
                  ManagementFactory.getThreadMXBean().getThreadCount(),
                  PoolParanoia.violations() - violations0, runs, count("comparisons"),
                  count("mismatches"), count("drifts"), runs - lastRuns,
                  runs == lastRuns ? 0 : (double) (millis - lastMillis) / (runs - lastRuns),
                  appender.stalls.get(), appender.poolWarns.get(), appender.poolErrors.get(),
                  RelPipeline.MULTI_THREADED.get() - multi0, cpuLoad()
               };
               lastRuns = runs;
               lastMillis = millis;
               samples.add(row);
               out.println(csvRow(row));
               out.flush();
               // an interrupted paranoid check is reported, never gated (S1)
               System.out.println("soak sample " + csvRow(row) + " | paranoiaInconclusive=" +
                                  (PoolParanoia.inconclusive() - inconclusive0) + " | " +
                                  PoolMetrics.nodeSummary());
            }
            finally {
               paused = false;
            }
         }

         done.set(true);

         for(Future<?> future : futures) {
            future.get(10, TimeUnit.MINUTES);
         }

         resetter.join(60_000);
         String report = report(warmup, samples, appender);
         System.out.println(report);
         Files.writeString(dir.resolve("soak-" + tag + "-summary.txt"), report);
         check(samples, appender);
      }
      finally {
         paused = false;
         workers.shutdownNow();

         for(AssetQuerySandbox box : longLived) {
            if(box != null) {
               box.dispose();
            }
         }

         root().detachAppender(appender);
         OwnedVarWarnings.uninstall();
         levels.forEach((name, level) ->
            ((Logger) LoggerFactory.getLogger(name)).setLevel(level));
         SreeEnv.setProperty("script.execution.timeout", timeout);
         refreshTimeout();
      }
   }

   /**
    * The soak's scripts: the synthetic set and every STEP-th corpus script, then the timeout
    * scripts (picked 1% of the time).
    */
   static List<RelMetamorphicTest.Case> cases() {
      List<RelMetamorphicTest.Case> cases =
         new ArrayList<>(RelMetamorphicTest.cases(STEP).toList());

      for(String[] s : TIMEOUTS) {
         cases.add(new RelMetamorphicTest.Case("timeout:" + s[0], s[1],
            RelCorpus.classify(s[1]), SEED + cases.size(), cases.size()));
      }

      return cases;
   }

   /**
    * Compute the pool-off oracle of every (script, shape) before the soak, so the oracle cache
    * does not grow during the measured part. Each oracle is computed twice in parallel runs;
    * when the two differ (the pool off itself is not repeatable, meta's O1), a third run on
    * this thread alone decides, and the instability is counted and listed apart.
    */
   private static void warmOracles(List<RelMetamorphicTest.Case> cases,
                                   ExecutorService workers) throws Exception
   {
      Map<String, List<String>> second = new ConcurrentHashMap<>();
      Map<String, RelMetamorphicTest.Case> byKey = new LinkedHashMap<>();
      List<Future<?>> futures = new ArrayList<>();

      for(Map<String, List<String>> into : List.of(ORACLES, second)) {
         for(RelMetamorphicTest.Case c : cases) {
            for(Shape shape : Shape.values()) {
               byKey.putIfAbsent(key(c.script(), shape), c);
               futures.add(workers.submit(() -> into.put(key(c.script(), shape),
                  run(c.script(), shape, RelConfig.off(), ReadPattern.SEQUENTIAL))));
            }
         }
      }

      for(Future<?> future : futures) {
         future.get();
      }

      byKey.forEach((key, c) -> {
         Shape shape = Shape.valueOf(key.substring(0, key.indexOf('\u0000')));
         List<String> a = RelMetamorphicTest.comparable(ORACLES.get(key), c.script());
         List<String> b = RelMetamorphicTest.comparable(second.get(key), c.script());

         if(!a.equals(b)) {
            List<String> alone = run(c.script(), shape, RelConfig.off(), ReadPattern.SEQUENTIAL);
            List<String> third = RelMetamorphicTest.comparable(alone, c.script());
            ORACLES.put(key, alone);
            inc("oracleUnstable");
            String what = "pool-off oracle unstable " + c.label() + " " + shape + ": run 1 vs 2 " +
               RelMetamorphicTest.diff(a, b) + "; run alone equals run " +
               (third.equals(a) ? "1" : third.equals(b) ? "2" : "neither") + "; script: " +
               c.script();
            example(UNSTABLE, what);
            System.out.println("SOAK " + what);
         }
      });
   }

   /**
    * One worker: run work items until done, holding off while the sampler pauses.
    */
   private void work(Random random, List<RelMetamorphicTest.Case> cases,
                     AssetQuerySandbox[] longLived, AtomicBoolean done)
   {
      List<RelMetamorphicTest.Case> timeouts = cases.stream()
         .filter(c -> c.label().startsWith("timeout:")).toList();
      List<RelMetamorphicTest.Case> normal = cases.stream()
         .filter(c -> !c.label().startsWith("timeout:")).toList();

      while(!done.get()) {
         enter();

         try {
            if(random.nextInt(100) < LONG_LIVED_PERCENT) {
               int i = random.nextInt(longLived.length);
               runOne(longLived[i], pick(random, normal, timeouts), "long", RelConfig.on());
               inc("items.long");
            }
            else {
               RelConfig cfg = random.nextInt(5) == 0 ? batchConfig(random) : RelConfig.on();
               AssetQuerySandbox box = RelPipeline.sandbox(cfg);

               try {
                  int n = 1 + random.nextInt(3);

                  for(int k = 0; k < n; k++) {
                     boolean reset = k > 0 && random.nextInt(100) < 15;

                     if(reset) {
                        box.getScriptEnv().reset();
                        inc("resets.short");
                     }

                     runOne(box, pick(random, normal, timeouts), reset ? "short-reset" : "short",
                            cfg);
                  }
               }
               finally {
                  box.dispose();
               }

               inc("items.short");
            }
         }
         catch(Throwable ex) {
            inc("workerErrors");
            example(FAILURES, "worker error " + ex);
         }
         finally {
            active.decrementAndGet();
         }
      }
   }

   /**
    * Reset one of the long-lived envs every 20-60 s while runs use it.
    */
   private void resets(AssetQuerySandbox[] longLived, AtomicBoolean done) {
      Random random = new Random(SEED - 1);

      while(!done.get()) {
         try {
            Thread.sleep(20_000 + random.nextInt(40_000));
         }
         catch(InterruptedException ex) {
            return;
         }

         if(!paused && !done.get()) {
            longLived[random.nextInt(longLived.length)].getScriptEnv().reset();
            inc("resets.long");
         }
      }
   }

   private static Work pick(Random random, List<RelMetamorphicTest.Case> normal,
                            List<RelMetamorphicTest.Case> timeouts)
   {
      RelMetamorphicTest.Case c = random.nextInt(100) < TIMEOUT_PERCENT
         ? timeouts.get(random.nextInt(timeouts.size()))
         : normal.get(random.nextInt(normal.size()));
      Shape shape = Shape.values()[random.nextInt(Shape.values().length)];
      List<ReadPattern> reads = ReadPattern.all(random.nextLong());
      ReadPattern read = shape.rowScripted()
         ? reads.get(random.nextInt(reads.size())) : ReadPattern.SEQUENTIAL;
      return new Work(c, shape, read);
   }

   private static RelConfig batchConfig(Random random) {
      int[] batches = { 0, 1, 7, 256 };
      int[] maxes = { 1, 256, 8192 };
      int batch = batches[random.nextInt(batches.length)];
      int max = Math.max(batch, maxes[random.nextInt(maxes.length)]);
      return RelConfig.on().with(PoolConfig.BATCH_ROWS, String.valueOf(batch))
         .with(PoolConfig.MAX_BATCH_ROWS, String.valueOf(max));
   }

   /**
    * Run one work item on a sandbox and compare it with its oracle.
    */
   private static void runOne(AssetQuerySandbox box, Work w, String where, RelConfig cfg) {
      long start = System.nanoTime();
      List<String> actual;
      Map<String, String> lost;

      // the loss warnings of the lens this worker reads
      try(OwnedVarWarnings.Recording recording = OwnedVarWarnings.record()) {
         try {
            actual = RelPipeline.run(w.c().script(), w.shape(), box, w.read());
         }
         catch(Exception ex) {
            actual = List.of("RUN-" + RelPipeline.error(ex));
         }

         lost = recording.lost();
      }

      add("runMillis", (System.nanoTime() - start) / 1_000_000);
      inc("runs");
      inc("runs." + w.shape());
      String script = w.c().script();
      List<String> oracle = ORACLES.get(key(script, w.shape()));
      inc("kind." + RelMetamorphicTest.kind(w.shape(), oracle));
      List<String> expected = RelMetamorphicTest.comparable(oracle, script);
      actual = RelMetamorphicTest.comparable(actual, script);
      inc("comparisons");

      if(!lost.isEmpty()) {
         inc("warned." + w.shape());
      }

      // a plain-data object var is kept: its loss is a finding, but for a home in use by
      // another thread (long-lived sandboxes are shared)
      String plainLoss = RelMetamorphicTest.plainLoss(script, lost, true);

      if(plainLoss != null) {
         inc("mismatches");
         String what = w.c().label() + " " + w.shape() + " " + w.read() + " " + where + " " +
            cfg + ": " + plainLoss;
         example(FAILURES, "mismatch " + what + "\nscript: " + script);
         System.out.println("SOAK MISMATCH " + what + "\nscript: " + script);
         return;
      }

      if(expected.equals(actual)) {
         return;
      }

      // a clock script's oracle holds only while the clock is where it was: a value derived from
      // now() (e.g. a day count against a timestamp with hours) moves as the soak runs, so its
      // oracle is computed again now and the comparison repeated against it
      if(RelMetamorphicTest.clock(script)) {
         List<String> fresh = run(script, w.shape(), RelConfig.off(), ReadPattern.SEQUENTIAL);

         if(RelMetamorphicTest.comparable(fresh, script).equals(actual)) {
            ORACLES.put(key(script, w.shape()), fresh);
            inc("clockOracleRefreshed");
            return;
         }

         expected = RelMetamorphicTest.comparable(fresh, script);
      }

      String diff = RelMetamorphicTest.diff(expected, actual);
      RelMetamorphicTest.Drift drift =
         RelMetamorphicTest.drift(expected, actual, script, w.shape(), lost);
      String what = w.c().label() + " " + w.shape() + " " + w.read() + " " + where + " " + cfg +
         ": " + diff;

      if(drift != RelMetamorphicTest.Drift.NONE) {
         inc("drifts");
         inc("drift." + drift + "." + where);
         example(DRIFTS, drift + " " + what);
         return;
      }

      inc("mismatches");
      // the same run again alone, on new sandboxes with the pool off and on, tells a pool
      // difference from a run that is not repeatable
      boolean off = RelMetamorphicTest.comparable(
         run(script, w.shape(), RelConfig.off(), w.read()), script).equals(expected);
      boolean on = RelMetamorphicTest.comparable(
         run(script, w.shape(), RelConfig.on(), w.read()), script).equals(expected);
      String rerun = "rerun alone: off " + (off ? "==" : "!=") + " oracle, on " +
         (on ? "==" : "!=") + " oracle";
      example(FAILURES, "mismatch " + what + " (" + rerun + ")\nscript: " + script);
      // a long run's findings are in its log as they happen
      System.out.println("SOAK MISMATCH " + what + "\n" + rerun + "\nscript: " + script +
                         "\nexpected: " + abbreviate(expected) + "\nactual: " +
                         abbreviate(actual));
   }

   private static String abbreviate(List<String> cells) {
      String str = String.valueOf(cells);
      return str.length() > 3000 ? str.substring(0, 3000) + "..." : str;
   }

   private void enter() {
      while(true) {
         while(paused) {
            try {
               Thread.sleep(10);
            }
            catch(InterruptedException ex) {
               Thread.currentThread().interrupt();
               return;
            }
         }

         active.incrementAndGet();

         if(!paused) {
            return;
         }

         active.decrementAndGet();
      }
   }

   /**
    * Pause the workers and wait until none runs an item.
    *
    * @return the milliseconds waited.
    */
   private long pause() throws InterruptedException {
      long start = System.currentTimeMillis();
      paused = true;

      while(active.get() > 0 && System.currentTimeMillis() - start < 180_000) {
         Thread.sleep(20);
      }

      if(active.get() > 0) {
         inc("pauseTimeouts");
         example(FAILURES, "a worker still ran 180 s after the pause");
      }

      return System.currentTimeMillis() - start;
   }

   private static void workerFailure(List<Future<?>> futures) throws Exception {
      for(Future<?> future : futures) {
         if(future.isDone()) {
            future.get();
            throw new IllegalStateException("a soak worker stopped early");
         }
      }
   }

   private static String report(String warmup, List<double[]> samples, Counter appender) {
      StringBuilder str = new StringBuilder("RelSoakTest summary: minutes=" + MINUTES +
         ", threads=" + THREADS + ", step=" + STEP + ", paranoid=" + PoolParanoia.enabled() +
         ", samples=" + samples.size() + "\n  " + warmup + "\n");
      STATS.forEach((k, v) -> str.append("  ").append(k).append(" = ").append(v).append('\n'));
      int from = tail(samples);
      str.append(String.format(
         "  slopes over samples %d..%d (per 10 min): heap %.3f MB (se %.3f), nonHeap %.3f MB, " +
         "slots %.3f, threads %.3f, windowMeanMs %.1f%n", from, samples.size() - 1,
         slope(samples, from, HEAP), se(samples, from, HEAP), slope(samples, from, NON_HEAP),
         slope(samples, from, SLOTS), slope(samples, from, THREADS_COL),
         slope(samples, from, MEAN_MS)));
      str.append(String.format("  ranges over the tail: heap %.1f..%.1f MB, slots %.0f..%.0f, " +
         "threads %.0f..%.0f, windowMeanMs %.0f..%.0f, cpu %.2f..%.2f%n",
         min(samples, from, HEAP), max(samples, from, HEAP), min(samples, from, SLOTS),
         max(samples, from, SLOTS), min(samples, from, THREADS_COL),
         max(samples, from, THREADS_COL), min(samples, from, MEAN_MS),
         max(samples, from, MEAN_MS), min(samples, from, CPU), max(samples, from, CPU)));
      str.append("  pool log WARN+ messages: ").append(appender.messages).append('\n');
      DRIFTS.forEach(d -> str.append("  drift: ").append(d).append('\n'));
      UNSTABLE.forEach(d -> str.append("  ").append(d).append('\n'));
      FAILURES.forEach(d -> str.append("  FAILURE: ").append(d).append('\n'));
      return str.toString();
   }

   private static void check(List<double[]> samples, Counter appender) {
      assertTrue(samples.size() >= 6, "too few samples: " + samples.size());
      int from = tail(samples);
      double[] last = samples.get(samples.size() - 1);
      assertEquals(List.of(), FAILURES);
      assertEquals(0, count("mismatches"), "unclassified differences");
      assertEquals(0, max(samples, 0, OPEN_CLAIMS), "open claims at a pause");
      assertEquals(0, last[LEAKED], "leaked claims");
      assertEquals(0, last[PARANOIA], "paranoia violations");
      assertEquals(0, last[MULTI], "multi-threaded access errors");
      assertEquals(0, appender.stalls.get(), "stall warnings: " + appender.messages);
      assertFalse(grows(samples, from, HEAP, 1.0), "heap after GC grows");
      assertFalse(grows(samples, from, SLOTS, 1.0), "node slots grow");
      assertFalse(grows(samples, from, THREADS_COL, 1.0), "threads grow");
   }

   /**
    * Whether a column grows by at least {@code limit} per 10 minutes over samples from..end.
    * The slope must exceed the limit by 2 standard errors: on a short tail a column that only
    * swings (node slots 8 -> 4 -> 8 while other builds saturate the CPU) can fit a slope above
    * the limit with no trend, while a real leak grows steadily, so its error is small. A slope
    * of 4 times the limit fails whatever its error.
    */
   static boolean grows(List<double[]> samples, int from, int col, double limit) {
      double slope = slope(samples, from, col);
      return slope >= 4 * limit || slope - 2 * se(samples, from, col) >= limit;
   }

   /** the first sample of the last 2/3 of the run */
   private static int tail(List<double[]> samples) {
      return samples.size() / 3;
   }

   /**
    * @return the least-squares slope of a column over samples from..end, per 10 minutes.
    */
   static double slope(List<double[]> samples, int from, int col) {
      double[] fit = fit(samples, from, col);
      return fit[0] * 10;
   }

   /**
    * @return the standard error of the slope, per 10 minutes.
    */
   static double se(List<double[]> samples, int from, int col) {
      return fit(samples, from, col)[1] * 10;
   }

   private static double[] fit(List<double[]> samples, int from, int col) {
      List<double[]> tail = samples.subList(from, samples.size());
      int n = tail.size();

      if(n < 3) {
         return new double[] { 0, 0 };
      }

      double mt = tail.stream().mapToDouble(s -> s[0]).average().orElse(0);
      double mv = tail.stream().mapToDouble(s -> s[col]).average().orElse(0);
      double stt = 0;
      double stv = 0;

      for(double[] s : tail) {
         stt += (s[0] - mt) * (s[0] - mt);
         stv += (s[0] - mt) * (s[col] - mv);
      }

      double b = stt == 0 ? 0 : stv / stt;
      double sse = 0;

      for(double[] s : tail) {
         double e = s[col] - mv - b * (s[0] - mt);
         sse += e * e;
      }

      return new double[] { b, stt == 0 ? 0 : Math.sqrt(sse / (n - 2) / stt) };
   }

   private static double min(List<double[]> samples, int from, int col) {
      return samples.subList(from, samples.size()).stream().mapToDouble(s -> s[col]).min()
         .orElse(0);
   }

   private static double max(List<double[]> samples, int from, int col) {
      return samples.subList(from, samples.size()).stream().mapToDouble(s -> s[col]).max()
         .orElse(0);
   }

   private static String csvRow(double[] row) {
      StringJoiner join = new StringJoiner(",");

      for(double v : row) {
         join.add(v == Math.rint(v) && Math.abs(v) < 1e15 ? String.valueOf((long) v)
                     : String.format(Locale.ROOT, "%.3f", v));
      }

      return join.toString();
   }

   private static double mb(long bytes) {
      return bytes / (1024.0 * 1024.0);
   }

   private static double cpuLoad() {
      return ManagementFactory.getOperatingSystemMXBean()
         instanceof com.sun.management.OperatingSystemMXBean os ? os.getCpuLoad() : -1;
   }

   private static List<String> run(String script, Shape shape, RelConfig cfg,
                                   ReadPattern read)
   {
      try {
         return RelPipeline.run(script, shape, cfg, read);
      }
      catch(Exception ex) {
         return List.of("RUN-" + RelPipeline.error(ex));
      }
   }

   private static String key(String script, Shape shape) {
      return shape + "\u0000" + script;
   }

   private static void inc(String key) {
      add(key, 1);
   }

   private static void add(String key, long n) {
      STATS.computeIfAbsent(key, k -> new AtomicLong()).addAndGet(n);
   }

   private static long count(String key) {
      AtomicLong value = STATS.get(key);
      return value == null ? 0 : value.get();
   }

   private static void example(List<String> list, String what) {
      synchronized(list) {
         if(list.size() < MAX_EXAMPLES) {
            list.add(what);
         }
      }
   }

   /**
    * Quiet the per-row script warnings; keep the pool's and the stall watchdog's warnings and
    * the pool's node summary for the counter.
    *
    * @return the previous levels.
    */
   private static Map<String, Level> quiet() {
      Map<String, Level> levels = new LinkedHashMap<>();
      Map<String, Level> set = new LinkedHashMap<>();
      set.put("inetsoft", Level.ERROR);
      set.put(StallWatchdog.class.getPackageName(), Level.WARN);
      set.put(PoolMetrics.class.getPackageName(), Level.WARN);
      set.put(PoolMetrics.class.getName(), Level.INFO);

      set.forEach((name, level) -> {
         Logger logger = (Logger) LoggerFactory.getLogger(name);
         levels.put(name, logger.getLevel());
         logger.setLevel(level);
      });

      return levels;
   }

   private static Logger root() {
      return (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
   }

   private static void refreshTimeout() throws Exception {
      Field field = GraalJavaScriptEngine.class.getDeclaredField("TIMEOUT_PROP");
      field.setAccessible(true);
      ((SreeEnv.Value) field.get(null)).updateValue();
   }

   /**
    * Counts the stall watchdog's warnings and the pool's warnings and errors.
    */
   private static final class Counter extends AppenderBase<ILoggingEvent> {
      @Override
      protected void append(ILoggingEvent event) {
         String name = event.getLoggerName();
         boolean warn = event.getLevel().isGreaterOrEqual(Level.WARN);

         if(!warn) {
            return;
         }

         if(name.startsWith(StallWatchdog.class.getPackageName())) {
            stalls.incrementAndGet();
         }
         else if(name.startsWith(PoolMetrics.class.getPackageName())) {
            (event.getLevel() == Level.ERROR ? poolErrors : poolWarns).incrementAndGet();
         }
         else {
            return;
         }

         String message = event.getFormattedMessage();
         message = message.length() > 160 ? message.substring(0, 160) : message;

         if(messages.size() < MAX_EXAMPLES) {
            messages.merge(name.substring(name.lastIndexOf('.') + 1) + ": " + message, 1,
                           Integer::sum);
         }
         else {
            messages.computeIfPresent(name.substring(name.lastIndexOf('.') + 1) + ": " + message,
                                      (k, v) -> v + 1);
         }
      }

      void clear() {
         stalls.set(0);
         poolWarns.set(0);
         poolErrors.set(0);
         messages.clear();
      }

      final AtomicLong stalls = new AtomicLong();
      final AtomicLong poolWarns = new AtomicLong();
      final AtomicLong poolErrors = new AtomicLong();
      final Map<String, Integer> messages = new ConcurrentSkipListMap<>();
   }

   record Work(RelMetamorphicTest.Case c, Shape shape, ReadPattern read) {
   }

   private static final String HEADER = "minutes,quiesceMs,openClaims,nodeSlots,maxSandboxSlots," +
      "creations,evictions,doomedCloses,cleans,execs,leakedClaims,interruptTimeouts," +
      "copyMutations,heapMb,nonHeapMb,threads,paranoiaViolations,runs,comparisons,mismatches," +
      "drifts,windowRuns,windowMeanMs,stallWarns,poolWarns,poolErrors,multiThreaded,cpuLoad";
   private static final int OPEN_CLAIMS = 2;
   private static final int SLOTS = 3;
   private static final int LEAKED = 10;
   private static final int HEAP = 13;
   private static final int NON_HEAP = 14;
   private static final int THREADS_COL = 15;
   private static final int PARANOIA = 16;
   private static final int MEAN_MS = 22;
   private static final int MULTI = 26;
   private static final int CPU = 27;

   /** name, body: row 600 (group 12 of a calc field) loops until the script timeout */
   static final String[][] TIMEOUTS = {
      { "loop600", "if(field['id'] == 600) { while(true) {} }\nfield['value']" },
      { "loop600var", "var t = field['id']; if(t == 600) { for(;;) { t++; } }\nt * 2" },
   };

   private static final int THREADS = Integer.getInteger("rel.soak.threads", 8);
   private static final int MINUTES = Integer.getInteger("rel.soak.minutes", 60);
   private static final int SAMPLE_SECONDS = Integer.getInteger("rel.soak.sampleSeconds", 30);
   private static final int STEP = Integer.getInteger("rel.soak.step", 3);
   private static final int LONG_LIVED = 2;
   private static final int LONG_LIVED_PERCENT = 30;
   /** percent of picks that are a timeout script (1 by default) */
   private static final int TIMEOUT_PERCENT = Integer.getInteger("rel.soak.timeoutPercent", 1);
   private static final int MAX_EXAMPLES = 40;
   private static final long SEED = Long.getLong("rel.seed", 77123L);
   private static final AtomicInteger THREAD_IDS = new AtomicInteger();
   private static final Map<String, List<String>> ORACLES = new ConcurrentHashMap<>();
   private static final Map<String, AtomicLong> STATS = new ConcurrentSkipListMap<>();
   private static final List<String> DRIFTS = new ArrayList<>();
   private static final List<String> FAILURES = new ArrayList<>();
   private static final List<String> UNSTABLE = new ArrayList<>();
   private final AtomicInteger active = new AtomicInteger();
   private volatile boolean paused;
}
