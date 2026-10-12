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
package inetsoft.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77562: ThreadPool sets and restores a pool thread's created/parent stack
 * traces for every task, while another thread may call getStackTrace() on that
 * pool thread. getStackTrace() must not size its result from one value of those
 * fields and copy another.
 */
@Tag("core")
class GroupedThreadStackTraceRaceTest {
   @Test
   void getStackTraceWhileTracesChangeReturnsOneWholeTrace() throws Exception {
      GroupedThread target = new GroupedThread("stack-trace-target", null);
      StackTraceElement[] shortTrace = trace(1);
      StackTraceElement[] longTrace = trace(80);
      AtomicBoolean stop = new AtomicBoolean();
      // replace the traces recorded by the constructor, which have another length
      target.setCreatedStackTrace(shortTrace);
      target.setParentStackTrace(shortTrace);

      // what ThreadPool does around each task: set the task's traces, then restore
      Thread toggler = new Thread(() -> {
         while(!stop.get()) {
            target.setCreatedStackTrace(longTrace);
            target.setParentStackTrace(longTrace);
            target.setCreatedStackTrace(shortTrace);
            target.setParentStackTrace(shortTrace);
         }
      }, "stack-trace-toggler");
      toggler.setDaemon(true);
      toggler.start();

      try {
         long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);

         for(int i = 0; i < 200_000 && System.nanoTime() < deadline; i++) {
            StackTraceElement[] result = target.getStackTrace();
            assertTrue(result.length == 2 || result.length == 81 || result.length == 160,
                       "unexpected stack trace length " + result.length);

            for(StackTraceElement element : result) {
               assertNotNull(element, "stack trace has a null element");
            }
         }
      }
      finally {
         stop.set(true);
         toggler.join(5000);
      }
   }

   private static StackTraceElement[] trace(int size) {
      StackTraceElement[] trace = new StackTraceElement[size];

      for(int i = 0; i < size; i++) {
         trace[i] = new StackTraceElement("Cls", "m" + i, "Cls.java", i);
      }

      return trace;
   }
}
