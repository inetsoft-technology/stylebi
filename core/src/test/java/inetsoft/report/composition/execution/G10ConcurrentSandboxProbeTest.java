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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.mv.MVManager;
import inetsoft.report.TableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.script.ScriptEnv;
import inetsoft.util.script.graal.pool.PoolConfig;
import inetsoft.util.script.graal.pool.PoolMetrics;
import inetsoft.util.script.graal.pool.WorksheetScriptEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.management.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The G10 gate of the worksheet script context pool (Testing #77123): N live worksheet sandboxes
 * (real AssetQuerySandbox, embedded tables with two expression columns and a JavaScript post
 * condition, no data cache), each driven by R concurrent readers per round, one table per
 * reader. Prints slots per sandbox, node slots, heap after GC (base / peak / after idle
 * retire), throughput, WARN counts and thread-dump summaries. It asserts nothing: a run with
 * the pool on is compared with a run with it off ({@code -Dg10.pool=false}).
 *
 * <p>The gate (redefined 2026-10-05), from the median of 3 interleaved off/on repetitions of
 * {@code -Dg10=true -Dg10.rounds=5 -Dg10.idleMillis=10000}, steady rounds:
 * <ul>
 *    <li>throughput, pool on / pool off: at least 0.80 at 500 sandboxes x 2 readers, at least
 *    0.70 at 500 x 4 and 100 x 16;</li>
 *    <li>retained after idle retire: at most 50 MB above pool off at 500 sandboxes; at most
 *    0.25 MB per extra context ((peak - retired) / (slots - sandboxes)); at most 16 slots per
 *    sandbox. Peak during a burst is reported, not gated.</li>
 * </ul>
 * Baseline on main 0d4d83a9: ratios 0.88 / 0.77 / 0.80; +33 MB retained; 0.18-0.19 MB per
 * context. Skipped unless {@code -Dg10=true}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class, LibManagerTestConfiguration.class, PluginsTestConfiguration.class, G10ConcurrentSandboxProbeTest.TestConfig.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
@EnabledIfSystemProperty(named = "g10", matches = "true")
class G10ConcurrentSandboxProbeTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   @Test
   void probe() throws Exception {
      boolean pool = Boolean.parseBoolean(System.getProperty("g10.pool", "true"));
      int sandboxes = Integer.getInteger("g10.sandboxes", 500);
      int readers = Integer.getInteger("g10.readers", 16);
      int rows = Integer.getInteger("g10.rows", 300);
      int rounds = Integer.getInteger("g10.rounds", 3);
      int threads = Integer.getInteger("g10.threads", 160);
      long idle = Long.getLong("g10.idleMillis", 10000L);
      Path out = Paths.get(System.getProperty("g10.out", "target/g10"));
      Files.createDirectories(out);
      String tag = pool ? "on" : "off";

      if(pool) {
         // main: the pool is off by default, so turn it on explicitly
         SreeEnv.setProperty(PoolConfig.ENABLED, "true");
      }
      else {
         SreeEnv.setProperty(PoolConfig.ENABLED, "false");
      }

      SreeEnv.setProperty(PoolConfig.IDLE_MILLIS, Long.toString(idle));
      String timeout = System.getProperty("g10.timeout");

      if(timeout != null) {
         // "0" disables the per-exec timeout watchdog (isolates its global scheduler lock)
         SreeEnv.setProperty("script.execution.timeout", timeout);
      }

      p("G10 %s: script.execution.timeout=%s", tag, SreeEnv.getProperty("script.execution.timeout"));

      Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
      ListAppender<ILoggingEvent> warns = new ListAppender<>();
      warns.start();
      root.addAppender(warns);

      p("G10 %s: sandboxes=%d readers=%d rows=%d rounds=%d threads=%d idleMillis=%d " +
        "maxHeap=%dMB", tag, sandboxes, readers, rows, rounds, threads, idle,
        Runtime.getRuntime().maxMemory() >> 20);

      ExecutorService exec = Executors.newFixedThreadPool(threads, r -> {
         Thread t = new Thread(r, "g10-reader");
         t.setDaemon(true);
         return t;
      });

      // warm-up in the same mode: engine, classes, compile caches
      List<AssetQuerySandbox> warm = build(2, readers, rows, 90000, pool);
      runRound(warm, readers, rows, 0, exec, null);
      runRound(warm, readers, rows, 1, exec, null);
      retireAll(warm);
      warm.clear();
      long base = usedAfterGc();
      int baseNode = PoolMetrics.nodeSlots();
      p("G10 %s: base heap %d MB, node slots %d", tag, base >> 20, baseNode);

      List<AssetQuerySandbox> boxes = build(sandboxes, readers, rows, 0, pool);
      long built = usedAfterGc();
      p("G10 %s: built %d sandboxes, heap %d MB", tag, sandboxes, built >> 20);
      long[] roundMs = new long[rounds];
      long totalRows = 0;

      for(int round = 0; round < rounds; round++) {
         Path dump = round == rounds - 1 ? out.resolve("dump-" + tag + "-peak.txt") : null;
         long start = System.nanoTime();
         totalRows += runRound(boxes, readers, rows, round, exec, dump);
         roundMs[round] = (System.nanoTime() - start) / 1_000_000;
         p("G10 %s: round %d %d ms, node slots %d", tag, round, roundMs[round],
           PoolMetrics.nodeSlots());
      }

      long peak = usedAfterGc();
      p("G10 %s: peak heap %d MB (+%d MB over base)", tag, peak >> 20, (peak - base) >> 20);
      slotStats(tag + " peak", boxes);
      long steady = 0;

      for(int round = 1; round < rounds; round++) {
         steady += roundMs[round];
      }

      p("G10 %s: steady rounds 1..%d total %d ms, %.0f rows/s (all rounds %d rows)", tag,
        rounds - 1, steady, (double) rows * readers * sandboxes * (rounds - 1) * 1000 / steady,
        totalRows);

      // idle retire: sandboxes stay alive, pooled slots are evicted after idleMillis
      Thread.sleep(idle * 2 + 5000);
      long retired = usedAfterGc();
      p("G10 %s: after idle retire heap %d MB (+%d MB over base), node slots %d", tag,
        retired >> 20, (retired - base) >> 20, PoolMetrics.nodeSlots());
      slotStats(tag + " retired", boxes);
      exec.shutdown();
      exec.awaitTermination(60, TimeUnit.SECONDS);
      // let the executor's exiting workers finish before the dump
      Thread.sleep(3000);
      threadDump(out.resolve("dump-" + tag + "-retired.txt"), "retired");

      retireAll(boxes);
      boxes.clear();
      long end = usedAfterGc();
      p("G10 %s: after dispose heap %d MB, node slots %d", tag, end >> 20,
        PoolMetrics.nodeSlots());
      p("G10 %s: node summary %s", tag, PoolMetrics.nodeSummary());

      root.detachAppender(warns);
      Map<String, Integer> byMsg = new TreeMap<>();

      for(ILoggingEvent e : warns.list) {
         if(e.getLevel().isGreaterOrEqual(Level.WARN)) {
            String m = e.getLoggerName() + ": " + e.getFormattedMessage();
            byMsg.merge(m.length() > 160 ? m.substring(0, 160) : m, 1, Integer::sum);
         }
      }

      p("G10 %s: WARN+ events %d, distinct %d", tag,
        byMsg.values().stream().mapToInt(Integer::intValue).sum(), byMsg.size());
      byMsg.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(15)
         .forEach(e -> p("G10 %s:   %5d x %s", tag, e.getValue(), e.getKey()));
      SreeEnv.remove(PoolConfig.ENABLED);
      SreeEnv.remove(PoolConfig.IDLE_MILLIS);
   }

   private static List<AssetQuerySandbox> build(int n, int readers, int rows, int offset,
                                                boolean pool)
   {
      List<AssetQuerySandbox> boxes = new ArrayList<>(n);

      for(int s = 0; s < n; s++) {
         Worksheet ws = new Worksheet();

         for(int t = 0; t < readers; t++) {
            addTable(ws, "T" + t, rows, s + offset);
         }

         AssetQuerySandbox box = new AssetQuerySandbox(ws);

         if(box.isScriptPoolMode() != pool) {
            throw new AssertionError("pool mode " + box.isScriptPoolMode());
         }

         boxes.add(box);
      }

      return boxes;
   }

   private static void addTable(Worksheet ws, String name, int rows, int sandbox) {
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "value", "id", "grp" };

      for(int i = 1; i <= rows; i++) {
         data[i] = new Object[] { sandbox * 7 + i, i, i % 5 };
      }

      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.INTEGER, XSchema.INTEGER, XSchema.INTEGER }, data));
      // no content-keyed data cache: every sandbox and round computes its own rows
      table.setProperty("no_cache", "true");
      ws.addAssembly(table);
      ColumnSelection columns = table.getColumnSelection(false);
      columns.addAttribute(expr("out", "var acc = (acc || 0) + field['id']; acc"));
      columns.addAttribute(expr("x2", "Math.sqrt(field['value']) + field['grp'] * 2"));
      table.setColumnSelection(columns, false);

      // a JavaScript condition value that keeps every row
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.GREATER_THAN);
      cond.setType(XSchema.INTEGER);
      ExpressionValue value = new ExpressionValue();
      value.setExpression("Math.min(0, " + sandbox + ")");
      value.setType(ExpressionValue.JAVASCRIPT);
      cond.addValue(value);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(columns.getAttribute("id"), cond, 0));
      table.setPostConditionList(list);
   }

   private static ColumnRef expr(String name, String formula) {
      inetsoft.uql.erm.ExpressionRef exp = new inetsoft.uql.erm.ExpressionRef(null, name);
      exp.setExpression(formula);
      ColumnRef column = new ColumnRef(exp);
      column.setDataType(XSchema.DOUBLE);
      return column;
   }

   /**
    * One round: every sandbox's readers start together (a barrier per sandbox), each reads
    * its own table in full (reset first after round 0) and checks the accumulator.
    *
    * @return rows read.
    */
   private static long runRound(List<AssetQuerySandbox> boxes, int readers, int rows,
                                int round, ExecutorService exec, Path dump) throws Exception
   {
      List<Future<Integer>> futures = new ArrayList<>();
      AtomicInteger done = new AtomicInteger();
      AtomicInteger errors = new AtomicInteger();
      AtomicReference<Throwable> first = new AtomicReference<>();
      int total = boxes.size() * readers;

      for(AssetQuerySandbox box : boxes) {
         CyclicBarrier barrier = new CyclicBarrier(readers);

         for(int t = 0; t < readers; t++) {
            String name = "T" + t;
            futures.add(exec.submit(() -> {
               try {
                  try {
                     barrier.await(120, TimeUnit.SECONDS);
                  }
                  catch(BrokenBarrierException | TimeoutException ex) {
                     // read anyway
                  }

                  if(round > 0) {
                     box.resetTableLens(name);
                  }

                  TableLens lens = box.getTableLens(name, AssetQuerySandbox.RUNTIME_MODE);
                  lens.moreRows(TableLens.EOT);
                  int cout = col(lens, "out");
                  int cx = col(lens, "x2");
                  int n = 0;

                  for(int r = 1; lens.moreRows(r); r++) {
                     Object o = lens.getObject(r, cout);
                     lens.getObject(r, cx);
                     double expect = (double) r * (r + 1) / 2;

                     if(!(o instanceof Number) || ((Number) o).doubleValue() != expect) {
                        throw new AssertionError(name + " row " + r + " out " + o +
                                                    " expected " + expect);
                     }

                     n++;
                  }

                  if(n != rows) {
                     throw new AssertionError(name + " rows " + n);
                  }

                  return n;
               }
               catch(Throwable ex) {
                  errors.incrementAndGet();
                  first.compareAndSet(null, ex);
                  return 0;
               }
               finally {
                  done.incrementAndGet();
               }
            }));
         }
      }

      if(dump != null) {
         while(done.get() < total / 2) {
            Thread.sleep(20);
         }

         threadDump(dump, "peak");
      }

      long n = 0;

      for(Future<Integer> f : futures) {
         n += f.get(30, TimeUnit.MINUTES);
      }

      if(errors.get() > 0) {
         p("G10 round %d: %d reader errors, first: %s", round, errors.get(), first.get());
         first.get().printStackTrace(System.out);
      }

      return n;
   }

   private static void slotStats(String what, List<AssetQuerySandbox> boxes) {
      if(boxes.isEmpty() || !(boxes.get(0).peekScriptEnv() instanceof WorksheetScriptEnv)) {
         p("G10 %s: plain env (pool off), 1 context per sandbox", what);
         return;
      }

      int[] hw = new int[boxes.size()];
      int[] size = new int[boxes.size()];
      long creations = 0;
      long evictions = 0;
      long cleans = 0;
      long execs = 0;

      for(int i = 0; i < boxes.size(); i++) {
         PoolMetrics m = ((WorksheetScriptEnv) boxes.get(i).peekScriptEnv()).getMetrics();
         hw[i] = m.getHighWater();
         size[i] = m.getSize();
         creations += m.getCreations();
         evictions += m.getEvictions();
         cleans += m.getCleans();
         execs += m.getExecs();
      }

      p("G10 %s: highWater %s | size %s | over16 hw=%d size=%d | creations %d evictions %d " +
        "cleans %d execs %d | node slots %d nodeMaxSandboxSlots %d", what, dist(hw), dist(size),
        Arrays.stream(hw).filter(v -> v > 16).count(),
        Arrays.stream(size).filter(v -> v > 16).count(), creations, evictions, cleans, execs,
        PoolMetrics.nodeSlots(), PoolMetrics.nodeMaxSandboxSlots());
   }

   private static String dist(int[] v) {
      int[] s = v.clone();
      Arrays.sort(s);
      return String.format("min %d p50 %d p90 %d max %d sum %d", s[0], s[s.length / 2],
                           s[(int) (s.length * 0.9)], s[s.length - 1],
                           Arrays.stream(s).sum());
   }

   private static void threadDump(Path file, String what) throws IOException {
      ThreadMXBean mx = ManagementFactory.getThreadMXBean();
      long[] dead = mx.findDeadlockedThreads();
      ThreadInfo[] infos = mx.dumpAllThreads(true, true);
      Map<String, Integer> states = new TreeMap<>();
      int blockedInProduct = 0;

      try(PrintWriter w = new PrintWriter(Files.newBufferedWriter(file))) {
         for(ThreadInfo info : infos) {
            states.merge(info.getThreadName().replaceAll("[-#]?\\d+$", "") + ":" +
                            info.getThreadState(), 1, Integer::sum);
            w.println("\"" + info.getThreadName() + "\" " + info.getThreadState() +
                         (info.getLockName() != null ? " on " + info.getLockName() : "") +
                         (info.getLockOwnerName() != null ? " owned by " +
                            info.getLockOwnerName() : ""));

            for(StackTraceElement e : info.getStackTrace()) {
               w.println("    at " + e);
            }

            w.println();

            if(info.getThreadState() == Thread.State.BLOCKED) {
               blockedInProduct++;
            }
         }
      }

      p("G10 dump %s: threads %d, deadlocked %s, BLOCKED %d, states %s -> %s", what,
        infos.length, dead == null ? "none" : dead.length, blockedInProduct, states, file);
   }

   private static void retireAll(List<AssetQuerySandbox> boxes) {
      for(AssetQuerySandbox box : boxes) {
         ScriptEnv env = box.peekScriptEnv();

         if(env instanceof WorksheetScriptEnv pooled) {
            pooled.retire();
         }

         box.dispose();
      }
   }

   private static long usedAfterGc() throws InterruptedException {
      MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
      long used = Long.MAX_VALUE;

      for(int i = 0; i < 6; i++) {
         System.gc();
         Thread.sleep(300);
         used = Math.min(used, mem.getHeapMemoryUsage().getUsed());
      }

      return used;
   }

   private static int col(TableLens t, String name) {
      for(int c = 0; c < t.getColCount(); c++) {
         if(name.equals(String.valueOf(t.getObject(0, c)))) {
            return c;
         }
      }

      throw new AssertionError("no column " + name);
   }

   private static void p(String fmt, Object... args) {
      System.out.println(String.format(fmt, args));
      System.out.flush();
   }
}
