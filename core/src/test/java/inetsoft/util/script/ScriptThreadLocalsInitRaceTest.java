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
package inetsoft.util.script;

import inetsoft.util.ConfigurationContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for bug #76938: {@link JavaScriptEngine} keeps its per-thread script
 * state in an object stored in the {@link ConfigurationContext}. When two threads made
 * the first access at the same time, each created and stored its own instance, and a
 * condition filter's held-lock record could land in the instance that lost. The holder
 * then no longer counted as holding the script engine lock, which reopened the
 * deadlock. Races a record against a concurrent first read from a fresh state many
 * times; no record may get lost.
 */
@Tag("core")
public class ScriptThreadLocalsInitRaceTest {
   @Test
   public void heldLockRecordSurvivesConcurrentFirstAccess() throws Exception {
      ConfigurationContext context = ConfigurationContext.getContext();
      Object saved = context.get(THREAD_LOCALS);
      LendableReentrantLock lock = new LendableReentrantLock();
      ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
         Thread thread = new Thread(r);
         thread.setDaemon(true);
         return thread;
      });
      AtomicInteger lost = new AtomicInteger();
      int iterations = 0;
      long deadline = System.currentTimeMillis() + 20000;

      try {
         for(; iterations < ITERATIONS && System.currentTimeMillis() < deadline; iterations++) {
            context.remove(THREAD_LOCALS);
            CyclicBarrier start = new CyclicBarrier(2);

            Future<?> holder = pool.submit(() -> {
               start.await(5, TimeUnit.SECONDS);
               JavaScriptEngine.pushHeldScriptLock(lock);

               try {
                  if(!JavaScriptEngine.holdsScriptLock()) {
                     lost.incrementAndGet();
                  }
               }
               finally {
                  JavaScriptEngine.popHeldScriptLock();
               }

               return null;
            });

            Future<?> reader = pool.submit(() -> {
               start.await(5, TimeUnit.SECONDS);
               return JavaScriptEngine.holdsScriptLock();
            });

            holder.get(5, TimeUnit.SECONDS);
            reader.get(5, TimeUnit.SECONDS);
         }
      }
      finally {
         pool.shutdownNow();
         context.remove(THREAD_LOCALS);

         if(saved != null) {
            context.put(THREAD_LOCALS, saved);
         }
      }

      assertTrue(iterations >= 1000, "too few iterations ran: " + iterations);
      assertEquals(0, lost.get(),
         "held-lock record lost in " + lost.get() + " of " + iterations + " races");
   }

   private static final int ITERATIONS = 20000;
   private static final String THREAD_LOCALS = JavaScriptEngine.class.getName() + ".threadLocals";
}
