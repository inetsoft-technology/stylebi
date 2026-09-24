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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
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

   @AfterEach
   public void tearDown() {
      release.countDown();
      StallWatchdog.resetForTest();
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
      // the reason is shown by the health check, which may be unauthenticated: the dump's
      // file name only, never its absolute path (the log has that)
      assertTrue(watchdog.getUnreleasedStall().contains(
                    "thread dump: " + new File(record.getDumpPath()).getName()),
                 watchdog.getUnreleasedStall());
      assertFalse(watchdog.getUnreleasedStall().contains(dumpDir.getAbsolutePath()),
                  watchdog.getUnreleasedStall());

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
      StallWatchdog.resetForTest();
      assertTrue(findWatchdogThread().isEmpty(), "reset stops the watchdog thread");
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 300000, 30000, dumpDir));

      try(WaitRecord ignored = WaitRegistry.begin("site", () -> 0)) {
         Thread thread = findWatchdogThread().orElse(null);
         assertNotNull(thread, "the first wait starts a daemon watchdog");
         assertSame(StallWatchdog.class.getClassLoader(), thread.getContextClassLoader());
      }
      finally {
         StallPolicy.setOverride(null);
      }
   }

   @Test
   public void failingThreadStartNeverReachesTheWaiter() {
      StallWatchdog.resetForTest();
      ThreadFactory factory = StallWatchdog.threadFactory;
      LongSupplier clock = StallWatchdog.startClock;
      StallWatchdog.startClock = now::get;
      StallWatchdog.threadFactory = r -> {
         throw new OutOfMemoryError("unable to create native thread");
      };
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 300000, 30000, dumpDir));

      try {
         try(WaitRecord record = WaitRegistry.begin("site", () -> 0)) {
            assertNotSame(WaitRecord.NOOP, record, "the wait is still registered");
         }

         assertTrue(findWatchdogThread().isEmpty());
         StallWatchdog.threadFactory = factory;
         advance(StallPolicy.DEFAULT_SCAN_MILLIS);
         StallWatchdog.ensureStarted();
         assertTrue(findWatchdogThread().isPresent(), "a later wait retries the start");
      }
      finally {
         StallWatchdog.threadFactory = factory;
         StallWatchdog.startClock = clock;
         StallPolicy.setOverride(null);
      }
   }

   @Test
   public void failedThreadStartIsRetriedOncePerScanInterval() {
      StallWatchdog.resetForTest();
      ThreadFactory factory = StallWatchdog.threadFactory;
      LongSupplier clock = StallWatchdog.startClock;
      AtomicInteger attempts = new AtomicInteger();
      StallWatchdog.startClock = now::get;
      StallWatchdog.threadFactory = r -> {
         attempts.incrementAndGet();
         throw new OutOfMemoryError("unable to create native thread");
      };

      try {
         StallWatchdog.ensureStarted();
         StallWatchdog.ensureStarted();
         assertEquals(1, attempts.get(), "a failed start is not retried by every wait");

         advance(StallPolicy.DEFAULT_SCAN_MILLIS - 1);
         StallWatchdog.ensureStarted();
         assertEquals(1, attempts.get());

         advance(1);
         StallWatchdog.ensureStarted();
         StallWatchdog.ensureStarted();
         assertEquals(2, attempts.get(), "retried once per scan interval");

         StallWatchdog.threadFactory = factory;
         advance(StallPolicy.DEFAULT_SCAN_MILLIS);
         StallWatchdog.ensureStarted();
         assertTrue(findWatchdogThread().isPresent(), "the start succeeds once it can");
      }
      finally {
         StallWatchdog.threadFactory = factory;
         StallWatchdog.startClock = clock;
      }
   }

   @Test
   public void watchdogThreadInheritsNoThreadLocals() throws Exception {
      InheritableThreadLocal<String> local = new InheritableThreadLocal<>();
      local.set("request state");
      AtomicReference<String> seen = new AtomicReference<>("not run");

      try {
         Thread thread = StallWatchdog.threadFactory.newThread(() -> seen.set(local.get()));
         assertEquals("Lock-Stall-Watchdog", thread.getName());
         assertTrue(thread.isDaemon());
         thread.start();
         thread.join(5000);
         assertNull(seen.get(), "the watchdog does not keep the first waiter's thread locals");
      }
      finally {
         local.remove();
      }
   }

   @Test
   public void waiterTrippingInsideTheDumpWindowGetsNoStaleDumpPath() {
      String earlier = dumper.dump("an earlier stall");
      WaitRecord record = registry.open("site", () -> 0, NONE);
      advance(1100);
      LockStallException ex = assertThrows(LockStallException.class, record::checkStall);
      assertNull(ex.getDumpPath(), "an unrelated dump is not the stall's dump");
      assertNull(record.getDumpPath());

      advance(60000);
      watchdog.scan();
      assertNotNull(record.getDumpPath(), "the watchdog attaches a fresh dump later");
      assertNotEquals(earlier, record.getDumpPath());
      assertEquals(2, dumper.getDumpCount());
      record.close();
   }

   @Test
   public void watchdogLoopSurvivesErrors() {
      AtomicInteger policyCalls = new AtomicInteger();
      AtomicInteger finderCalls = new AtomicInteger();
      WaitRegistry failing = new WaitRegistry(now::get, () -> {
         if(policyCalls.getAndIncrement() == 0) {
            throw new IllegalStateException("broken policy");
         }

         return policy;
      }, dumper);
      StallWatchdog dog = new StallWatchdog(failing, () -> {
         if(finderCalls.getAndIncrement() == 0) {
            throw new LinkageError("broken finder");
         }

         return new long[] { 1, 2 };
      });
      List<Long> sleeps = new ArrayList<>();

      dog.loop(millis -> {
         if(sleeps.size() == 3) {
            throw new InterruptedException();
         }

         sleeps.add(millis);
      });

      assertEquals(List.of(StallPolicy.DEFAULT_SCAN_MILLIS, 500L, 500L), sleeps,
                   "a failed policy read falls back to the default scan interval");
      assertTrue(dog.getUnreleasedStall().contains("JVM deadlock"),
                 "scans go on after an Error");
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

   @Test
   public void scanShorterThanTheSliceWaitsForTheWaitersTimeout() throws Exception {
      // limit 1000, slice 250, the watchdog scans every 100 ms. The waiter's progress time
      // comes from its blocker (10 ms), so its own check trips one slice late, at 1250 ms.
      AtomicLong blockerRows = new AtomicLong();
      Parked blocker = parked("blocker", blockerRows::get, NONE);
      WaitRecord waiter = registry.open("waiter", () -> 0, () -> new Thread[] { blocker.thread });
      assertEquals(TimeUnit.MILLISECONDS.toNanos(250), waiter.getSliceNanos());
      boolean tripped = false;

      for(int t = 10; t <= 1400; t += 10) {
         advance(10);

         if(t == 10) {
            blockerRows.incrementAndGet();
            blocker.record.checkStall();
         }

         if(t % 250 == 0) {
            if(t == 1250) {
               assertThrows(LockStallException.class, waiter::checkStall);
               tripped = true;
            }
            else {
               waiter.checkStall();
            }
         }

         if(t % 100 == 0) {
            watchdog.scan();

            if(!tripped) {
               assertNull(watchdog.getUnreleasedStall(),
                          "unreleased before the waiter's own timeout fired, at " + t + " ms");
            }
         }
      }

      assertTrue(tripped);
      assertNotNull(watchdog.getUnreleasedStall(), "tripped at 1250, still registered at 1400");
      assertTrue(watchdog.getUnreleasedStall().contains("waiter"));
      assertFalse(watchdog.getUnreleasedStall().contains("blocker"),
                  "the blocker is not overdue before 1510 ms");
      waiter.close();
   }

   @Test
   public void deadlockSeenInsideTheDumpWindowIsDumpedLater() {
      dumper.dump("an earlier stall");
      deadlocked = new long[] { 3, 4 };
      watchdog.scan();
      assertEquals(1, dumper.getDumpCount());
      assertTrue(watchdog.getUnreleasedStall().contains("JVM deadlock"));

      advance(61000);
      watchdog.scan();
      assertEquals(2, dumper.getDumpCount(), "the deadlock gets its own dump once it can");
      advance(61000);
      watchdog.scan();
      assertEquals(2, dumper.getDumpCount());
   }

   @Test
   public void stallInsideTheDumpWindowGetsItsOwnDump() {
      WaitRecord record = registry.open("site", () -> 0, NONE);
      String earlier = dumper.dump("an earlier stall");
      advance(1100);
      watchdog.scan();
      assertNull(record.getDumpPath(), "an unrelated dump is not attached to the stall");

      advance(60000);
      watchdog.scan();
      assertNotNull(record.getDumpPath());
      assertNotEquals(earlier, record.getDumpPath());
      assertEquals(2, dumper.getDumpCount());
      record.close();
   }

   @Test
   public void failingDeadlockCheckDoesNotFreezeTheFlag() {
      watchdog = new StallWatchdog(registry, () -> {
         throw new UnsupportedOperationException("no thread MXBean");
      });
      WaitRecord record = registry.open("stuck.site", () -> 0, NONE);
      advance(1500);
      watchdog.scan();
      advance(500);
      watchdog.scan();
      assertTrue(watchdog.getUnreleasedStall().contains("stuck.site"));

      record.close();
      watchdog.scan();
      assertNull(watchdog.getUnreleasedStall());
   }

   @Test
   public void failingDumpDoesNotFreezeTheFlag() {
      dumper = new StallDumper(now::get, () -> {
         throw new IllegalStateException("no dump dir");
      }, 60000);
      registry = new WaitRegistry(now::get, () -> policy, dumper);
      watchdog = new StallWatchdog(registry, () -> deadlocked);
      WaitRecord record = registry.open("stuck.site", () -> 0, NONE);
      advance(1500);
      watchdog.scan();
      advance(500);
      watchdog.scan();
      assertTrue(watchdog.getUnreleasedStall().contains("stuck.site"));

      record.close();
      watchdog.scan();
      assertNull(watchdog.getUnreleasedStall());
   }

   @Test
   public void interruptedWatchdogIsRestarted() throws Exception {
      StallWatchdog.ensureStarted();
      Thread first = findWatchdogThread().orElseThrow();
      first.interrupt();
      first.join(5000);
      assertFalse(first.isAlive());

      StallWatchdog.ensureStarted();
      Thread second = findWatchdogThread().orElse(null);
      assertNotNull(second, "the next wait restarts a watchdog thread that ended");
      assertNotSame(first, second);
   }

   @Test
   public void cycleMemberTrippingInsideTheWindowGetsTheCyclesDump() {
      WaitRecord a = registry.open("A", () -> 0, NONE);
      WaitRecord b = registry.open("B", () -> 0, NONE);
      advance(1100);
      LockStallException exA = assertThrows(LockStallException.class, a::checkStall);
      assertNotNull(exA.getDumpPath());
      advance(10);

      // fail mode: B throws and closes its record at once, so the watchdog never sees it
      LockStallException exB = assertThrows(LockStallException.class, b::checkStall);
      b.close();
      a.close();

      assertEquals(exA.getDumpPath(), exB.getDumpPath(),
                   "a dump taken during B's own stall is B's dump too");
      assertEquals(1, dumper.getDumpCount());
   }

   @Test
   public void rateLimitedProbeDumpIsRetriedWhileTheEpisodePersists() {
      dumper.dump("an earlier stall");
      watchdog.add(() -> List.of(new StallProbe.Finding("k", "signal", true)));
      watchdog.scan();
      assertEquals(1, dumper.getDumpCount(), "rate-limited: no dump yet");

      advance(61000);
      watchdog.scan();
      assertEquals(2, dumper.getDumpCount(), "the episode gets its dump once it can");

      advance(61000);
      watchdog.scan();
      assertEquals(2, dumper.getDumpCount(), "and only once");
   }

   @Test
   public void throwingProbeKeepsItsEpisode() {
      AtomicInteger calls = new AtomicInteger();
      watchdog.add(() -> {
         if(calls.incrementAndGet() == 2) {
            throw new IllegalStateException("flapping probe");
         }

         return List.of(new StallProbe.Finding("k", "signal", true));
      });
      watchdog.scan();
      assertEquals(1, dumper.getDumpCount());

      advance(61000);
      watchdog.scan();
      advance(61000);
      watchdog.scan();
      assertEquals("signal", watchdog.getProbeFindings());
      assertEquals(1, dumper.getDumpCount(), "a failed poll does not end the episode");
   }

   @Test
   public void failingDumpDirectoryStillFailsTheWaitWithAStallException() {
      dumper = new StallDumper(now::get, () -> {
         throw new IllegalStateException("no dump dir");
      }, 60000);
      registry = new WaitRegistry(now::get, () -> policy, dumper);
      WaitRecord record = registry.open("site", () -> 0, NONE);
      advance(1100);

      LockStallException ex = assertThrows(LockStallException.class, record::checkStall);
      assertNull(ex.getDumpPath());
      record.close();
   }

   @Test
   public void onlyADumpStartedStrictlyAfterTheLastProgressIsAttached() {
      // started at the instant of the last progress: not this stall's dump
      assertNotNull(dumper.dump("same instant"));
      WaitRecord same = registry.open("same", () -> 0, NONE);
      advance(1100);
      assertNull(assertThrows(LockStallException.class, same::checkStall).getDumpPath());
      same.close();

      // started one nanosecond after the last progress: this stall's dump
      advance(60000);
      WaitRecord after = registry.open("after", () -> 0, NONE);
      now.incrementAndGet();
      String dump = dumper.dump("one nanosecond later");
      assertNotNull(dump);
      advance(1100);
      assertEquals(dump, assertThrows(LockStallException.class, after::checkStall).getDumpPath());
      after.close();
   }

   @Test
   public void errorWhileDumpingDoesNotCutTheScanShort() {
      // every attempt fails with an Error, such as an OutOfMemoryError from dumping the threads
      dumper = new StallDumper(now::get, () -> {
         throw new InternalError("simulated while dumping");
      }, 0);
      registry = new WaitRegistry(now::get, () -> policy, dumper);
      watchdog = new StallWatchdog(registry, () -> deadlocked);
      WaitRecord first = registry.open("first.site", () -> 0, NONE);
      WaitRecord second = runOnOtherThread(() -> registry.open("second.site", () -> 0, NONE));
      advance(1500);
      assertDoesNotThrow(watchdog::scan);
      advance(500);
      assertDoesNotThrow(watchdog::scan);

      String unreleased = watchdog.getUnreleasedStall();
      assertNotNull(unreleased, "the error must not leave the flag of a partial scan");
      assertTrue(unreleased.contains("first.site"), unreleased);
      assertTrue(unreleased.contains("second.site"), unreleased);
      first.close();
      second.close();
   }

   @Test
   public void errorWhileDumpingStillFailsTheWaitWithAStallException() {
      dumper = new StallDumper(now::get, () -> {
         // an Error such as an OutOfMemoryError from Tool.dumpAllThreads (a real OOME would also
         // end the test JVM when it escapes, as JUnit rethrows it)
         throw new InternalError("simulated while dumping");
      }, 60000);
      registry = new WaitRegistry(now::get, () -> policy, dumper);
      WaitRecord record = registry.open("site", () -> 0, NONE);
      advance(1100);

      LockStallException ex = assertThrows(LockStallException.class, record::checkStall);
      assertNull(ex.getDumpPath());
      assertTrue(record.isFailed());
      assertSame(ex, assertThrows(LockStallException.class, record::checkStall),
                 "a later check rethrows the same failure instead of returning");
      record.close();
   }

   private static Optional<Thread> findWatchdogThread() {
      return Thread.getAllStackTraces().keySet().stream()
         .filter(t -> "Lock-Stall-Watchdog".equals(t.getName()) && t.isDaemon() && t.isAlive())
         .findFirst();
   }

   private Parked parked(String what, LongSupplier progress, Supplier<Thread[]> blockers)
      throws Exception
   {
      CompletableFuture<WaitRecord> opened = new CompletableFuture<>();
      Thread thread = new Thread(() -> {
         opened.complete(registry.open(what, progress, blockers));

         try {
            release.await(30, TimeUnit.SECONDS);
         }
         catch(InterruptedException ignore) {
         }
      });
      thread.setDaemon(true);
      thread.start();
      return new Parked(thread, opened.get(5, TimeUnit.SECONDS));
   }

   private record Parked(Thread thread, WaitRecord record) {
   }

   @Test
   public void probeFindingIsLoggedOncePerEpisodeAndNeverUnreleased() {
      List<StallProbe.Finding> current = new ArrayList<>();
      watchdog.add(() -> new ArrayList<>(current));
      current.add(new StallProbe.Finding("k", "signal k", true));

      watchdog.scan();
      watchdog.scan();
      assertEquals("signal k", watchdog.getProbeFindings());
      assertNull(watchdog.getUnreleasedStall(), "a probe never makes a stall unreleased");
      assertEquals(1, dumper.getDumpCount(), "one dump per episode");

      current.clear();
      watchdog.scan();
      assertNull(watchdog.getProbeFindings());

      advance(61000);
      current.add(new StallProbe.Finding("k", "signal k", true));
      watchdog.scan();
      assertEquals(2, dumper.getDumpCount(), "a new episode dumps again");
   }

   @Test
   public void probeWithoutDumpOnlyLogs() {
      watchdog.add(() -> List.of(new StallProbe.Finding("k", "no dump", false)));
      watchdog.scan();

      assertEquals("no dump", watchdog.getProbeFindings());
      assertEquals(0, dumper.getDumpCount());
   }

   @Test
   public void failingProbeDoesNotStopTheScan() {
      watchdog.add(() -> {
         throw new IllegalStateException("broken probe");
      });
      watchdog.add(() -> List.of(new StallProbe.Finding("ok", "still scanned", false)));
      watchdog.scan();

      assertEquals("still scanned", watchdog.getProbeFindings());
   }

   @Test
   public void offModeSkipsProbes() {
      policy = new StallPolicy(StallPolicy.Mode.OFF, 1000, 500, dumpDir);
      watchdog.add(() -> List.of(new StallProbe.Finding("k", "signal", true)));
      watchdog.scan();

      assertNull(watchdog.getProbeFindings());
      assertEquals(0, dumper.getDumpCount());
   }

   @Test
   public void wakeStartsTheGlobalWatchdog() {
      StallWatchdog.wake();
      boolean running = Thread.getAllStackTraces().keySet().stream()
         .anyMatch(t -> "Lock-Stall-Watchdog".equals(t.getName()) && t.isDaemon());
      assertTrue(running);
   }

   /**
    * Open a wait on another thread (the registry keeps one innermost wait per thread). Only
    * the watchdog reads it, and closing it from here just removes it.
    */
   private static WaitRecord runOnOtherThread(Supplier<WaitRecord> open) {
      try {
         return CompletableFuture.supplyAsync(open, r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            thread.start();
         }).get(5, TimeUnit.SECONDS);
      }
      catch(Exception ex) {
         throw new AssertionError(ex);
      }
   }

   private void advance(long millis) {
      now.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
   }

   private static final Supplier<Thread[]> NONE = () -> new Thread[0];

   @TempDir
   File dumpDir;
   private final AtomicLong now = new AtomicLong(1_000_000_000L);
   private final CountDownLatch release = new CountDownLatch(1);
   private volatile StallPolicy policy;
   private volatile long[] deadlocked;
   private StallDumper dumper;
   private WaitRegistry registry;
   private StallWatchdog watchdog;
}
