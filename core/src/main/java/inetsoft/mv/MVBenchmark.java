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
package inetsoft.mv;

import inetsoft.report.io.viewsheet.excel.CSVUtil;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.sree.SreeEnv;
import inetsoft.util.*;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MVBenchmark {
   private MVBenchmark() {
      if(enabled) {
         executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "MV Benchmark");
            thread.setDaemon(true);
            return thread;
         });
      }
   }

   private static synchronized MVBenchmark getInstance() {
      if(instance == null) {
         instance = new MVBenchmark();
      }

      return instance;
   }

   public static boolean isEnabled() {
      return getInstance().enabled;
   }

   public static void startBenchmark() {
      getInstance().startBenchmarkInternal();
   }

   private void startBenchmarkInternal() {
      if(enabled) {
         this.currentBenchmark.remove();
         this.currentBenchmark.get();
      }
   }

   public static void stopBenchmark(Consumer<BenchmarkContext> context) {
      getInstance().stopBenchmarkInternal(context);
   }

   private void stopBenchmarkInternal(Consumer<BenchmarkContext> context) {
      if(enabled) {
         BenchmarkData benchmark = this.currentBenchmark.get();
         this.currentBenchmark.remove();
         context.accept(benchmark.context::put);
         executor.submit(() -> writeBenchmark(benchmark));
      }
   }

   public static void addMV(Consumer<BenchmarkFile> file) {
      getInstance().addMVInternal(file);
   }

   private void addMVInternal(Consumer<BenchmarkFile> file) {
      if(enabled) {
         BenchmarkMetadata data = new BenchmarkMetadata();
         file.accept((name, rows, columns, dimensions, measures) -> {
            data.name = name;
            data.rowCount = rows;
            data.columnCount = columns;
            data.dimensionCount = dimensions;
            data.measureCount = measures;
         });
         executor.submit(() -> writeFileInfo(data));
      }
   }

   private void writeBenchmark(BenchmarkData benchmark) {
      String home = ConfigurationContext.getContext().getHome();
      FileSystemService fs = FileSystemService.getInstance();
      String id = UUID.randomUUID().toString();

      DefaultTableLens table = new DefaultTableLens(new Object[][] {
         { "id", "mvType", "mvName", "groups", "aggregates", "conditions" },
         {
            id, benchmark.context.get("mvType"), benchmark.context.get("mvName"),
            benchmark.context.get("groups"), benchmark.context.get("aggregates"),
            benchmark.context.get("condition")
         }
      });
      File file = fs.getFile(home, "mv-benchmark-context.csv");
      Tool.lock(file.getAbsolutePath());

      try {
         if(!file.exists()) {
            Files.write(
               file.toPath(),
               Collections.singleton(
                  "\"id\",\"mvType\",\"mvName\",\"groups\",\"aggregates\",\"conditions\""));
         }

         try(OutputStream output = new FileOutputStream(file, true)) {
            CSVUtil.writeTableDataAssembly(table, output, ",", "\"", false);
         }
      }
      catch(IOException ignore) {
      }
      finally {
         Tool.unlock(file.getAbsolutePath());
      }

      table = new DefaultTableLens(new Object[][] {
         { "id", "task", "elapsedTime", "rowCount" }
      });

      int row = 1;

      for(Map.Entry<String, Long> e : benchmark.startTimes.entrySet()) {
         long start = e.getValue();
         long end = benchmark.endTimes.getOrDefault(e.getKey(), start);
         long count = benchmark.counts.getOrDefault(e.getKey(), 0L);
         table.addRow();
         table.setObject(row, 0, id);
         table.setObject(row, 1, e.getKey());
         table.setObject(row, 2, end - start);
         table.setObject(row, 3, count);
         ++row;
      }

      file = fs.getFile(home, "mv-benchmark.csv");
      Tool.lock(file.getAbsolutePath());

      try {
         if(!file.exists()) {
            Files.write(
               file.toPath(),
               Collections.singleton("\"id\",\"task\",\"elapsedTime\",\"rowCount\""));
         }

         try(OutputStream output = new FileOutputStream(file, true)) {
            CSVUtil.writeTableDataAssembly(table, output, ",", "\"", false);
         }
      }
      catch(IOException ignore) {
      }
      finally {
         Tool.unlock(file.getAbsolutePath());
      }
   }

   private void writeFileInfo(BenchmarkMetadata data) {
      String home = ConfigurationContext.getContext().getHome();
      FileSystemService fs = FileSystemService.getInstance();
      File file = fs.getFile(home, "mv-benchmark-files.csv");

      DefaultTableLens table = new DefaultTableLens(new Object[][] {
         { "mvName", "rowCount", "columnCount", "dimensionCount", "measureCount" },
         { data.name, data.rowCount, data.columnCount, data.dimensionCount, data.measureCount }
      });

      Tool.lock(file.getAbsolutePath());

      try {
         if(!file.exists()) {
            Files.write(
               file.toPath(),
               Collections.singleton(
                  "\"mvName\",\"rowCount\",\"columnCount\",\"dimensionCount\",\"measureCount\""));
         }

         try(OutputStream output = new FileOutputStream(file, true)) {
            CSVUtil.writeTableDataAssembly(table, output, ",", "\"", false);
         }
      }
      catch(IOException ignore) {
      }
      finally {
         Tool.unlock(file.getAbsolutePath());
      }
   }

   public static void startTimer(String name) {
      getInstance().startTimerInternal(name);
   }

   private void startTimerInternal(String name) {
      if(enabled) {
         this.currentBenchmark.get().startTimes.put(name, System.currentTimeMillis());
      }
   }

   public static void stopTimer(String name) {
      getInstance().stopTimerInternal(name, 0L);
   }

   public static void stopTimer(String name, long count) {
      getInstance().stopTimerInternal(name, count);
   }

   private void stopTimerInternal(String name, long count) {
      if(enabled) {
         BenchmarkData benchmark = this.currentBenchmark.get();
         benchmark.endTimes.put(name, System.currentTimeMillis());
         benchmark.counts.put(name, count);
      }
   }

   public static void time(String name, Supplier<Long> task) {
      getInstance().timeInternal(name, task);
   }

   private void timeInternal(String name, Supplier<Long> task) {
      if(enabled) {
         BenchmarkData benchmark = this.currentBenchmark.get();
         benchmark.startTimes.put(name, System.currentTimeMillis());
         long count = task.get();
         benchmark.endTimes.put(name, System.currentTimeMillis());
         benchmark.counts.put(name, count);

      }
   }

   private final boolean enabled = "true".equals(SreeEnv.getProperty("mv.benchmark"));
   private final ThreadLocal<BenchmarkData> currentBenchmark =
      ThreadLocal.withInitial(BenchmarkData::new);
   private ExecutorService executor;
   private static MVBenchmark instance = null;

   private static final class BenchmarkData {
      public BenchmarkData() {
         this.context = new HashMap<>();
         this.startTimes = new HashMap<>();
         this.endTimes = new HashMap<>();
         this.counts = new HashMap<>();
      }

      private final Map<String, String> context;
      private final Map<String, Long> startTimes;
      private final Map<String, Long> endTimes;
      private final Map<String, Long> counts;
   }

   private static final class BenchmarkMetadata {
      private String name;
      private int rowCount;
      private int columnCount;
      private int dimensionCount;
      private int measureCount;
   }

   @FunctionalInterface
   public interface BenchmarkContext {
      void put(String name, String value);
   }

   @FunctionalInterface
   public interface BenchmarkFile {
      void put(String mvName, int rowCount, int columnCount, int dimensionCount, int measureCount);
   }
}
