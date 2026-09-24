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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the watchdog scan with an injected clock (bug #76967): a stall that is still
 * registered one scan after its limit is "unreleased" (health DOWN), one resolved by its
 * timeout only dumps.
 */
@Tag("core")
public class StallWatchdogTest {
   @BeforeEach
   public void setUp() {
      policy = new StallPolicy(StallPolicy.Mode.FAIL, 1000, 500, dumpDir);
      dumper = new StallDumper(now::get, () -> dumpDir, 60000);
      registry = new WaitRegistry(now::get, () -> policy, dumper);
      watchdog = new StallWatchdog(registry, () -> deadlocked);
   }

   @Test
   public void stallStillRegisteredAfterAnotherScanIsUnreleased() {
      WaitRecord record = registry.open("stuck.site", () -> 0, NONE);
      advance(1500);
      watchdog.scan();

      assertNotNull(record.getDumpPath(), "the watchdog dumps a stall it finds");
      assertNull(watchdog.getUnreleasedStall(), "one scan is not enough to call it unreleased");

      advance(500);
      watchdog.scan();
      assertNotNull(watchdog.getUnreleasedStall());
      assertTrue(watchdog.getUnreleasedStall().contains("stuck.site"));
      assertEquals(1, dumper.getDumpCount());

      record.close();
      watchdog.scan();
      assertNull(watchdog.getUnreleasedStall(), "released once the thread lets go");
   }

   @Test
   public void stallResolvedByItsTimeoutIsNotUnreleased() {
      WaitRecord record = registry.open("site", () -> 0, NONE);
      advance(1500);
      assertThrows(LockStallException.class, record::checkStall);
      record.close();

      watchdog.scan();
      advance(500);
      watchdog.scan();
      assertNull(watchdog.getUnreleasedStall());
      assertEquals(1, dumper.getDumpCount());
   }

   @Test
   public void progressingWaitIsNotAStall() {
      AtomicLong rows = new AtomicLong();
      WaitRecord record = registry.open("site", rows::get, NONE);

      for(int i = 0; i < 10; i++) {
         advance(300);
         rows.incrementAndGet();
         record.checkStall();
         watchdog.scan();
      }

      assertNull(record.getDumpPath());
      assertNull(watchdog.getUnreleasedStall());
      assertEquals(0, dumper.getDumpCount());
      record.close();
   }

   @Test
   public void persistingAlertModeStallIsUnreleased() {
      policy = new StallPolicy(StallPolicy.Mode.ALERT, 1000, 500, dumpDir);
      WaitRecord record = registry.open("alert.site", () -> 0, NONE);
      advance(1500);
      record.checkStall();
      assertTrue(record.isTripped());

      watchdog.scan();
      advance(500);
      watchdog.scan();
      assertTrue(watchdog.getUnreleasedStall().contains("alert.site"));
      assertEquals(1, dumper.getDumpCount(), "the watchdog reuses the waiter's dump");
      record.close();
   }

   @Test
   public void jvmDeadlockIsReportedAndDumpedOnce() {
      deadlocked = new long[] { 7, 5 };
      watchdog.scan();
      advance(61000);
      watchdog.scan();

      assertTrue(watchdog.getUnreleasedStall().contains("JVM deadlock"));
      assertEquals(1, dumper.getDumpCount(), "the same deadlock is dumped once");

      deadlocked = null;
      watchdog.scan();
      assertNull(watchdog.getUnreleasedStall());
   }

   @Test
   public void offModeReportsNothing() {
      policy = new StallPolicy(StallPolicy.Mode.OFF, 1000, 500, dumpDir);
      deadlocked = new long[] { 1, 2 };
      watchdog.scan();
      assertNull(watchdog.getUnreleasedStall());
      assertEquals(0, dumper.getDumpCount());
   }

   @Test
   public void beginStartsTheGlobalWatchdog() {
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 300000, 30000, dumpDir));

      try(WaitRecord ignored = WaitRegistry.begin("site", () -> 0)) {
         boolean running = Thread.getAllStackTraces().keySet().stream()
            .anyMatch(t -> "Lock-Stall-Watchdog".equals(t.getName()) && t.isDaemon());
         assertTrue(running);
      }
      finally {
         StallPolicy.setOverride(null);
      }
   }

   @Test
   public void progressAfterTheWatchdogDumpEndsTheEpisode() {
      AtomicLong rows = new AtomicLong();
      WaitRecord record = registry.open("site", rows::get, NONE);
      advance(1100);
      watchdog.scan();
      String first = record.getDumpPath();
      assertNotNull(first);
      assertEquals(1, dumper.getDumpCount());

      rows.incrementAndGet();
      record.checkStall();
      assertNull(record.getDumpPath(), "progress before the trip ends the episode");

      advance(61000);
      LockStallException ex = assertThrows(LockStallException.class, record::checkStall);
      assertEquals(2, dumper.getDumpCount(), "the new episode dumps again");
      assertNotNull(ex.getDumpPath());
      assertNotEquals(first, ex.getDumpPath());
      record.close();
   }

   private void advance(long millis) {
      now.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
   }

   private static final Supplier<Thread[]> NONE = () -> new Thread[0];

   @TempDir
   File dumpDir;
   private final AtomicLong now = new AtomicLong(1_000_000_000L);
   private volatile StallPolicy policy;
   private volatile long[] deadlocked;
   private StallDumper dumper;
   private WaitRegistry registry;
   private StallWatchdog watchdog;
}
