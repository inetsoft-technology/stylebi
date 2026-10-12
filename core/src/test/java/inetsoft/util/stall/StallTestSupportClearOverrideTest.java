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
package inetsoft.util.stall;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77562: a stall dump still being written into a test's {@code @TempDir} when the test
 * ends keeps the file open, and JUnit cannot delete the directory on Windows.
 * {@link StallTestSupport#clearOverride()} must clear the override and then wait for a dump
 * that holds the global dumper's lock, in that order.
 */
@Tag("core")
class StallTestSupportClearOverrideTest {
   @TempDir
   File dumpDir;

   @AfterEach
   void tearDown() {
      StallPolicy.setOverride(null);
   }

   @Test
   void clearOverrideClearsFirstThenWaitsForADumpInFlight() throws Exception {
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 1000, 200, dumpDir,
                                              StallPolicy.DEFAULT_MAX_DUMPS, false));
      Thread tearDownThread = Thread.currentThread();
      CountDownLatch held = new CountDownLatch(1);
      AtomicBoolean waited = new AtomicBoolean();
      AtomicBoolean clearedBeforeWait = new AtomicBoolean();
      AtomicBoolean released = new AtomicBoolean();

      // stands for a dump in flight: StallDumper.dump() writes the file holding this lock
      Thread dumper = new Thread(() -> {
         synchronized(StallDumper.global()) {
            held.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);

            while(tearDownThread.getState() != Thread.State.BLOCKED &&
               System.nanoTime() < deadline)
            {
               Thread.onSpinWait();
            }

            waited.set(tearDownThread.getState() == Thread.State.BLOCKED);
            clearedBeforeWait.set(override() == null);
            released.set(true);
         }
      }, "stall-dump-in-flight");
      dumper.setDaemon(true);
      dumper.start();
      assertTrue(held.await(5, TimeUnit.SECONDS));

      StallTestSupport.clearOverride();

      assertTrue(released.get(), "clearOverride returns only after the dump releases the lock");
      assertTrue(waited.get(), "clearOverride waits on the global dumper's lock");
      assertTrue(clearedBeforeWait.get(),
                 "the override is cleared before waiting, so no later dump uses the old dir");
      assertNull(override());
      dumper.join(5000);
   }

   private static Object override() {
      try {
         Field field = StallPolicy.class.getDeclaredField("override");
         field.setAccessible(true);
         return field.get(null);
      }
      catch(ReflectiveOperationException ex) {
         throw new AssertionError(ex);
      }
   }
}
