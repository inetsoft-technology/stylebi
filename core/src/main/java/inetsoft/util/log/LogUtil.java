/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.log;

import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

public class LogUtil {
   public static void logPerformance(PerformanceLogEntry entry) {
      if(entry == null || Tool.isEmptyString(entry.getAssetName()) || entry.getStartTime() == -1 ||
         entry.getFinishTime() == -1 || entry.getFinishTime() <= entry.getStartTime())
      {
         return;
      }

      Thread thread = Thread.currentThread();
      Map<LogContext, String> excludedContextsMap = new HashMap<>();

      if(thread instanceof GroupedThread) {
         GroupedThread groupedThread = (GroupedThread) thread;

         for(LogContext context : EXCLUDED_LOG_CONTEXTS) {
            String value = groupedThread.getRecord(context);

            if(!Tool.isEmptyString(value)) {
               excludedContextsMap.put(context, value);
               groupedThread.removeRecords(context);
            }
         }
      }

      PERFORMANCE_LOG.info(Catalog.getCatalog().getString("asset.execution.performance.log",
         entry.getAssetName(), entry.getFinishTime() - entry.getStartTime()));

      if(thread instanceof GroupedThread && excludedContextsMap.size() != 0) {
         for(LogContext logContext : excludedContextsMap.keySet()) {
            ((GroupedThread) thread).addRecord(logContext, excludedContextsMap.get(logContext));
         }
      }
   }

   public static void saveStackTrace(String prefix, String suffix) {
      File file = null;
      Map<Thread, StackTraceElement[]> stack = Thread.getAllStackTraces();

      try(OutputStream out = new FileOutputStream(file = File.createTempFile(prefix, suffix))) {
         PrintWriter writer = new PrintWriter(new OutputStreamWriter(out));

         stack.entrySet().stream().forEach(entry -> {
               Thread thread = entry.getKey();
               writer.println("\nThread: " + thread + ": " + thread.getState());
               Arrays.stream(entry.getValue()).forEach(trace -> writer.println("  " + trace));
            });

         System.err.println("Thread dump saved in " + file + " at " + new Date());
      }
      catch(IOException ex) {
         System.err.println("Unable to dump stack trace " + ex);
      }
   }

   public static class PerformanceLogEntry {
      public PerformanceLogEntry(String assetName) {
         this.assetName = assetName;
      }

      public PerformanceLogEntry() {
      }

      public String getAssetName() {
         return assetName;
      }

      public void setAssetName(String assetName) {
         this.assetName = assetName;
      }

      public long getStartTime() {
         return startTime;
      }

      public void setStartTime(long startTime) {
         this.startTime = startTime;
      }

      public long getFinishTime() {
         return finishTime;
      }

      public void setFinishTime(long finishTime) {
         this.finishTime = finishTime;
      }

      private String assetName;
      private long startTime;
      private long finishTime;
   }

   public static final String PERFORMANCE_LOGGER_NAME = "inetsoft.performance";
   private static final Logger PERFORMANCE_LOG = LoggerFactory.getLogger(PERFORMANCE_LOGGER_NAME);
   private static final LogContext[] EXCLUDED_LOG_CONTEXTS = new LogContext[] {
      LogContext.ASSEMBLY, LogContext.TABLE
   };
}
