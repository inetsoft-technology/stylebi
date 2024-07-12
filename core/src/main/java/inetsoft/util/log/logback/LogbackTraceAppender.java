/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util.log.logback;

import ch.qos.logback.core.UnsynchronizedAppenderBase;
import inetsoft.sree.SreeEnv;

import java.io.*;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Map;

/**
 * Appender for Logback that fires log events back to the application.
 */
public class LogbackTraceAppender<E> extends UnsynchronizedAppenderBase<E> {
   @Override
   protected void append(E eventObject) {
      Throwable thrown = LogbackUtil.getThrowable(eventObject);

      if(stackTrace != null && thrown instanceof NullPointerException) {
         saveStackTrace();
      }
   }

   public static void captureStackTraces(String msg, StackRecord rmStack0) {
      stackTrace = Thread.getAllStackTraces();
      message = msg;
      rmStack = rmStack0;
   }

   private void saveStackTrace() {
      // assembly not found, dump debugging info
      if("true".equals(SreeEnv.getProperty("debug.viewsheet"))) {
         long now = System.currentTimeMillis();

         // don't dump more than once per 10m
         if(now > lastDump + 60000 * 10) {
            try {
               File file = File.createTempFile("viewsheetGetAssemblyNull", ".txt");
               lastDump = now;
               System.err.println("Thread dump saved in: " + file);

               try(OutputStream out = new FileOutputStream(file)) {
                  PrintWriter writer = new PrintWriter(new OutputStreamWriter(out));
                  writer.println("Message: " + message);

                  if(rmStack != null) {
                     writer.println("\nAssembly removed at: " + new Timestamp(rmStack.ts));
                     Arrays.stream(rmStack.stack).forEach(trace -> writer.println("  " + trace));
                     writer.println("\n");
                  }
                  else {
                     writer.println("\nAssembly was never removed.\n");
                  }

                  stackTrace.entrySet().stream().forEach(entry -> {
                     Thread thread = entry.getKey();
                     writer.println("\nThread: " + thread + ": " + thread.getState());
                     Arrays.stream(entry.getValue()).forEach(trace -> writer.println("  " + trace));
                  });
               }

               message = null;
               rmStack = null;
               stackTrace = null;
            }
            catch(IOException ex) {
               // ignore
            }
         }
      }
   }

   public static class StackRecord {
      public StackRecord() {
         ts = System.currentTimeMillis();
         stack = Thread.currentThread().getStackTrace();
      }

      private StackTraceElement[] stack;
      private long ts;
   }

   private static Map<Thread, StackTraceElement[]> stackTrace;
   private static String message;
   private static StackRecord rmStack;
   private long lastDump;
}
