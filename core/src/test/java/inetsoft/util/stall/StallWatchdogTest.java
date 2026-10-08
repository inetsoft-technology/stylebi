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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static inetsoft.util.stall.StallTestSupport.assertStallOf;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the watchdog scan with an injected clock (bug #76967): a stall that is still
 * registered one scan after its limit is "unreleased" (health DOWN), one resolved by its
 * timeout only dumps.
 */
@Tag("slow")
public class StallWatchdogTest {
   @BeforeEach
   public void setUp() {
      StallTestSupport.resetGlobalStallState();
      policy = new StallPolicy(StallPolicy.Mode.FAIL, 1000, 500, dumpDir,
                               StallPolicy.DEFAULT_MAX_DUMPS, true);
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
   public void alertModeStallIsNeverUnreleasedWhileFailModeIs() {
      policy = new StallPolicy(StallPolicy.Mode.ALERT, 1000, 500, dumpDir,
                               StallPolicy.DEFAULT_MAX_DUMPS, false);
      // never checked by its waiter, so it becomes overdue
      WaitRecord alert = runOnOtherThread(() -> registry.open("alert.site", () -> 0, NONE));
      // tripped by its waiter and still registered: an alert wait goes on by design
      WaitRecord alertTripped = registry.open("alert.tripped", () -> 0, NONE);
      advance(1500);
      alertTripped.checkStall();
      assertTrue(alertTripped.isTripped());

      // the mode changes mid-episode: each record keeps the mode it was opened with
      policy = new StallPolicy(StallPolicy.Mode.FAIL, 1000, 500, dumpDir,
                               StallPolicy.DEFAULT_MAX_DUMPS, true);
      WaitRecord fail = runOnOtherThread(() -> registry.open("fail.site", () -> 0, NONE));
      assertEquals(StallPolicy.Mode.ALERT, alert.getMode());
      assertEquals(StallPolicy.Mode.FAIL, fail.getMode());
      advance(1500);

      for(int i = 0; i < 4; i++) {
         watchdog.scan();
         advance(500);
      }

      String reason = watchdog.getUnreleasedStall();
      assertNotNull(reason, "an overdue fail-mode wait is unreleased");
      assertTrue(reason.contains("fail.site"), reason);
      assertFalse(reason.contains("alert.site"), "an overdue alert wait is never unreleased");
      assertFalse(reason.contains("alert.tripped"),
                  "a tripped alert wait that persists is never unreleased");
      assertNotNull(alertTripped.getDumpPath(), "an alert stall is still dumped");
      assertTrue(alert.isWatchdogReported(), "an alert stall is still logged");

      fail.close();
      watchdog.scan();
      assertNull(watchdog.getUnreleasedStall(), "alert waits alone never turn health DOWN");
      alert.close();
      alertTripped.close();
   }

   @Test
   public void offModeWaitIsNeverUnreleased() {
      policy = new StallPolicy(StallPolicy.Mode.OFF, 1000, 500, dumpDir,
                               StallPolicy.DEFAULT_MAX_DUMPS, false);
      WaitRecord record = registry.open("off.site", () -> 0, NONE);
      assertSame(WaitRecord.NOOP, record);
      assertEquals(StallPolicy.Mode.OFF, record.getMode());
      advance(3000);
      watchdog.scan();
      advance(500);
      watchdog.scan();
      assertNull(watchdog.getUnreleasedStall());
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
      policy = new StallPolicy(StallPolicy.Mode.OFF, 1000, 500, dumpDir,
                               StallPolicy.DEFAULT_MAX_DUMPS, false);
      deadlocked = new long[] { 1, 2 };
      watchdog.scan();
      assertNull(watchdog.getUnreleasedStall());
      assertEquals(0, dumper.getDumpCount());
   }

   @Test
   public void beginStartsTheGlobalWatchdog() {
      StallWatchdog.resetForTest();
      assertTrue(findWatchdogThread().isEmpty(), "reset stops the watchdog thread");
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 300000, 30000, dumpDir,
                                              StallPolicy.DEFAULT_MAX_DUMPS, false));

      try(WaitRecord ignored = WaitRegistry.begin("site", () -> 0)) {
         Thread thread = findWatchdogThread().orElse(null);
         assertNotNull(thread, "the first wait starts a daemon watchdog");
         assertSame(StallWatchdog.class.getClassLoader(), thread.getContextClassLoader());
      }
      finally {
         StallTestSupport.clearOverride();
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
      StallPolicy.setOverride(new StallPolicy(StallPolicy.Mode.FAIL, 300000, 30000, dumpDir,
                                              StallPolicy.DEFAULT_MAX_DUMPS, false));

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
         StallTestSupport.clearOverride();
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
      dumper.dump(StallDumper.Kind.DEADLOCK, "an earlier deadlock");
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

   /**
    * A JVM deadlock never resolves, so its dump must not use up the window of the stalled
    * waits: a stall that trips inside it still gets its own dump, from the watchdog and from
    * the waiter.
    */
   @Test
   public void stallAfterADeadlockDumpGetsItsOwnDump() {
      deadlocked = new long[] { 3, 4 };
      watchdog.scan();
      assertEquals(1, dumper.getDumpCount(StallDumper.Kind.DEADLOCK));
      String deadlockDump = dumper.getLastDump(StallDumper.Kind.DEADLOCK).path();

      WaitRecord record = runOnOtherThread(() -> registry.open("scanned", () -> 0, NONE));
      advance(1500);
      watchdog.scan();
      assertNotNull(record.getDumpPath(), "the watchdog dumps the stall");
      assertNotEquals(deadlockDump, record.getDumpPath());
      assertEquals(1, dumper.getDumpCount(StallDumper.Kind.DEADLOCK),
                   "the deadlock is dumped once");
      record.close();
   }

   @Test
   public void waiterAfterADeadlockDumpGetsItsOwnDump() {
      deadlocked = new long[] { 3, 4 };
      watchdog.scan();
      String deadlockDump = dumper.getLastDump(StallDumper.Kind.DEADLOCK).path();

      WaitRecord waiter = registry.open("waiter", () -> 0, NONE);
      advance(1500);

      LockStallException stall = assertThrows(LockStallException.class, waiter::checkStall);
      assertNotNull(stall.getDumpPath(), "the waiter gets a dump of its stall");
      assertNotEquals(deadlockDump, stall.getDumpPath());
      assertEquals(1, dumper.getDumpCount(StallDumper.Kind.WAIT));
      waiter.close();
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
      dumper.dump(StallDumper.Kind.PROBE, "an earlier signal");
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

   /**
    * A probe finding (e.g. an interrupt timeout) has its own dump window: its dump must not
    * use up the window of a real stall that follows, from the watchdog or from the waiter.
    */
   @Test
   public void stallAfterAProbeDumpGetsItsOwnDump() {
      watchdog.add(() -> List.of(new StallProbe.Finding("k", "signal", true)));
      watchdog.scan();
      assertEquals(1, dumper.getDumpCount(StallDumper.Kind.PROBE));
      assertEquals(0, dumper.getDumpCount(StallDumper.Kind.WAIT));

      advance(1000);
      WaitRecord watched = runOnOtherThread(() -> registry.open("watched", () -> 0, NONE));
      advance(1100);
      watchdog.scan();
      assertEquals(1, dumper.getDumpCount(StallDumper.Kind.WAIT),
                   "the watchdog dumps the stall inside the probe's window");
      assertNotNull(watched.getDumpPath());

      advance(61000);
      WaitRecord waiter = registry.open("waiter", () -> 0, NONE);
      advance(1100);
      LockStallException stall = assertThrows(LockStallException.class, waiter::checkStall);
      assertNotNull(stall.getDumpPath(), "a probe dump never takes the waiter's window");
      waiter.close();
      watched.close();
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

   /**
    * The health check drops the watchdog's JVM deadlock finding from the real scan output when
    * it shows the deadlocked threads itself: only that part, the unreleased wait is kept.
    */
   @Test
   public void jvmDeadlockPartOfARealScanIsRemovedAlone() {
      WaitRecord record = registry.open("stuck.site", () -> 0, NONE);
      deadlocked = new long[] { 7, 3 };
      advance(1500);
      watchdog.scan();
      advance(500);
      watchdog.scan();

      String reason = watchdog.getUnreleasedStall();
      assertNotNull(reason);
      assertTrue(reason.contains("stuck.site"), reason);
      assertTrue(reason.endsWith("; JVM deadlock of 2 threads"), reason);

      String rest = StallWatchdog.withoutJvmDeadlock(reason);
      assertEquals(reason.substring(0, reason.length() - "; JVM deadlock of 2 threads".length()),
                   rest);
      assertTrue(rest.startsWith("stall not released: stuck.site"), rest);
      assertFalse(rest.contains("JVM deadlock"), rest);

      record.close();
      watchdog.scan();
      assertEquals("JVM deadlock of 2 threads", watchdog.getUnreleasedStall());
      assertNull(StallWatchdog.withoutJvmDeadlock(watchdog.getUnreleasedStall()),
                 "nothing is left once the deadlock is removed");
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
      assertStallOf(ex, assertThrows(LockStallException.class, record::checkStall),
                 "a later check rethrows the failure (a copy) instead of returning");
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
      policy = new StallPolicy(StallPolicy.Mode.OFF, 1000, 500, dumpDir,
                               StallPolicy.DEFAULT_MAX_DUMPS, false);
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
    * Bug #77152: an alert-mode wait for a thread that is BLOCKED on a monitor the waiter holds
    * is a wait-for cycle no timeout releases. It is unreleased only once the waiter is stalled
    * and the same cycle is found on two scans.
    */
   @Test
   public void alertModeWaitForCycleIsUnreleasedWhenStalledOnTwoScans() throws Exception {
      policy = new StallPolicy(StallPolicy.Mode.ALERT, 1000, 500, dumpDir,
                               StallPolicy.DEFAULT_MAX_DUMPS, false);
      Object monitor = new Object();
      AtomicReference<Thread> t2 = new AtomicReference<>();
      CountDownLatch done = new CountDownLatch(1);
      WaitRecord t1 =
         waitHolding("cycle-t1", monitor, () -> new Thread[] { t2.get() }, done, done);
      t2.set(blockedOn("cycle-t2", monitor));

      watchdog.scan();
      watchdog.scan();
      assertNull(watchdog.getUnreleasedStall(), "a cycle of a wait not stalled yet is none");

      advance(1500);
      watchdog.scan();
      assertNull(watchdog.getUnreleasedStall(), "one sighting is not enough");
      watchdog.scan();
      String reason = watchdog.getUnreleasedStall();
      assertNotNull(reason, "a stalled alert-mode wait-for cycle is unreleased");
      assertTrue(reason.startsWith("wait-for cycle of threads "), reason);
      assertTrue(reason.contains("\"cycle-t1\"(" + t1.getThread().threadId() +
                                    ") in cycle-t1.site"), reason);
      assertTrue(reason.contains("\"cycle-t2\"(" + t2.get().threadId() + ")"), reason);

      done.countDown();
      t2.get().join(5000);
      watchdog.scan();
      assertNull(watchdog.getUnreleasedStall(), "released once the cycle is broken");
   }

   /**
    * Bug #77152: a pure cycle of registered waits, no monitor involved, is found too, and a
    * report-only fail-mode wait (a loan reclaim), which never gives up, is a member.
    */
   @Test
   public void registeredOnlyCycleOfReportOnlyWaitsIsUnreleased() throws Exception {
      AtomicReference<Thread> a = new AtomicReference<>();
      AtomicReference<Thread> b = new AtomicReference<>();
      WaitRecord recordA =
         waitHolding("pure-a", new Object(), () -> new Thread[] { b.get() }, release, release);
      WaitRecord recordB =
         waitHolding("pure-b", new Object(), () -> new Thread[] { a.get() }, release, release);
      a.set(recordA.getThread());
      b.set(recordB.getThread());
      recordA.setReportOnly("reclaim");
      recordB.setReportOnly("reclaim");
      assertEquals(StallPolicy.Mode.FAIL, recordA.getMode());

      advance(1100);
      watchdog.scan();
      watchdog.scan();
      String reason = watchdog.getUnreleasedStall();
      assertNotNull(reason);
      assertTrue(reason.contains("wait-for cycle"), reason);
      assertTrue(reason.contains("\"pure-a\"") && reason.contains("\"pure-b\""), reason);
   }

   /**
    * Bug #77152: a fail-mode wait is released by its own timeout, so a cycle through it is
    * never reported as a wait-for cycle, only as the stall of that wait once the timeout
    * evidently did not release it (overdue, on two scans).
    */
   @Test
   public void failModeWaitForCycleIsLeftToItsTimeout() throws Exception {
      Object monitor = new Object();
      AtomicReference<Thread> t2 = new AtomicReference<>();
      waitHolding("fail-t1", monitor, () -> new Thread[] { t2.get() }, release, release);
      t2.set(blockedOn("fail-t2", monitor));

      // stalled, but not overdue yet: its waiter would still fail it
      advance(1100);

      for(int i = 0; i < 3; i++) {
         watchdog.scan();
         assertNull(watchdog.getUnreleasedStall(), "a fail-mode cycle waits for its timeout");
      }

      advance(500);
      watchdog.scan();
      watchdog.scan();
      String reason = watchdog.getUnreleasedStall();
      assertNotNull(reason, "the timeout did not release it");
      assertTrue(reason.contains("stall not released: fail-t1.site"), reason);
      assertFalse(reason.contains("wait-for cycle"), reason);
   }

   /**
    * Bug #77152: a chain is no cycle. T1 waits for T2, which is BLOCKED on a monitor of an
    * unrelated T3.
    */
   @Test
   public void alertModeChainWithoutACycleIsNotUnreleased() throws Exception {
      policy = new StallPolicy(StallPolicy.Mode.ALERT, 1000, 500, dumpDir,
                               StallPolicy.DEFAULT_MAX_DUMPS, false);
      Object t3Monitor = new Object();
      AtomicReference<Thread> t2 = new AtomicReference<>();
      holding("chain-t3", t3Monitor);
      waitHolding("chain-t1", new Object(), () -> new Thread[] { t2.get() }, release, release);
      t2.set(blockedOn("chain-t2", t3Monitor));
      advance(1500);

      for(int i = 0; i < 3; i++) {
         watchdog.scan();
         advance(500);
      }

      assertNull(watchdog.getUnreleasedStall());
   }

   /**
    * Bug #77152: a waiter of a lent lock waits for the lender or the borrower, either of which
    * lets it on. While the borrower is running the waiter gets credit and is no cycle member,
    * although the lender is BLOCKED on a monitor the waiter holds, the edge of a cycle.
    */
   @Test
   public void lentLockWaiterWithAHealthyBorrowerIsNotUnreleased() throws Exception {
      policy = new StallPolicy(StallPolicy.Mode.ALERT, 1000, 500, dumpDir,
                               StallPolicy.DEFAULT_MAX_DUMPS, false);
      Object monitor = new Object();
      AtomicReference<Thread> lender = new AtomicReference<>();
      AtomicReference<Thread> borrower = new AtomicReference<>();
      AtomicLong samples = new AtomicLong();
      CountDownLatch opened = new CountDownLatch(1);
      CountDownLatch borrowerDone = new CountDownLatch(1);
      AtomicBoolean busy = new AtomicBoolean(true);
      start("lent-waiter", () -> {
         synchronized(monitor) {
            try(WaitRecord record = registry.open(
               "lent.site", () -> 0, () -> new Thread[] { lender.get(), borrower.get() }))
            {
               opened.countDown();

               while(release.getCount() > 0) {
                  record.checkStall();
                  samples.incrementAndGet();
                  Thread.sleep(1);
               }
            }
         }
      });
      assertTrue(opened.await(5, TimeUnit.SECONDS));
      borrower.set(start("lent-borrower", () -> {
         while(busy.get()) {
            Thread.onSpinWait();
         }

         borrowerDone.await(30, TimeUnit.SECONDS);
      }));
      lender.set(blockedOn("lent-lender", monitor));

      for(int i = 0; i < 8; i++) {
         advance(300);
         awaitSample(samples);
         watchdog.scan();
      }

      assertNull(watchdog.getUnreleasedStall(), "the running borrower lets the waiter on");

      // the control: once the borrower is stuck too, the same shape is a cycle
      busy.set(false);
      StallTestSupport.awaitTrue(() -> borrower.get().getState() == Thread.State.TIMED_WAITING, 5,
                                 "the borrower never parked");
      awaitSample(samples);
      advance(1500);
      awaitSample(samples);
      watchdog.scan();
      watchdog.scan();
      String reason = watchdog.getUnreleasedStall();
      assertNotNull(reason);
      assertTrue(reason.contains("wait-for cycle") && reason.contains("\"lent-lender\""),
                 reason);
      borrowerDone.countDown();
   }

   /**
    * Bug #77152: a cycle seen on one scan only, such as a lens worker momentarily BLOCKED on
    * the monitor its consumer re-enters on a slice, never turns health DOWN, even with the
    * consumer's wait stalled.
    */
   @Test
   public void cycleSeenOnOneScanOnlyIsNotUnreleased() throws Exception {
      policy = new StallPolicy(StallPolicy.Mode.ALERT, 1000, 500, dumpDir,
                               StallPolicy.DEFAULT_MAX_DUMPS, false);
      Object monitor = new Object();
      AtomicReference<Thread> t2 = new AtomicReference<>();
      CountDownLatch leave = new CountDownLatch(1);
      waitHolding("brief-t1", monitor, () -> new Thread[] { t2.get() }, leave, release);
      t2.set(blockedOn("brief-t2", monitor));
      advance(1500);
      watchdog.scan();
      assertNull(watchdog.getUnreleasedStall(), "one sighting is not enough");

      // the waiter lets go of the monitor but keeps waiting: the cycle is gone
      leave.countDown();
      t2.get().join(5000);
      assertFalse(t2.get().isAlive());

      for(int i = 0; i < 3; i++) {
         watchdog.scan();
         assertNull(watchdog.getUnreleasedStall());
      }
   }

   /**
    * Feature #77123: with the default rule, a fail-mode wait that saw no progress for its
    * limit is no proof of a lock cycle, e.g. the lock owner polls a slow data source with
    * sleeps (TIMED_WAITING, so no credit). It is reported, never failed and never turns
    * health DOWN, however long it waits, while no cycle is confirmed.
    */
   @Test
   public void unconfirmedFailModeStallIsReportedNotFailed() throws Exception {
      policy = confirmedOnly();
      Thread owner = start("sleeping-owner", () -> {
         while(release.getCount() > 0) {
            Thread.sleep(5);
         }
      });
      StallTestSupport.awaitTrue(() -> owner.getState() == Thread.State.TIMED_WAITING, 5,
                                 "the owner never slept");
      WaitRecord record = registry.open("slow.site", () -> 0, () -> new Thread[] { owner });
      assertEquals(StallPolicy.Mode.FAIL, record.getMode());
      assertFalse(record.isFailOnTimeout());

      advance(1500);
      record.checkStall();
      assertTrue(record.isTripped(), "the stall is reported");
      assertNotNull(record.getDumpPath(), "and dumped");
      assertFalse(record.isFailed());

      for(int i = 0; i < 6; i++) {
         advance(500);
         record.checkStall();
         watchdog.scan();
         assertNull(watchdog.getUnreleasedStall(), "an unconfirmed stall is never DOWN");
      }

      assertFalse(record.isCycleConfirmed());
      assertFalse(record.isFailed());
      assertEquals(1, dumper.getDumpCount(), "one dump per episode");
      record.close();
   }

   /**
    * Feature #77123: with the default rule, a fail-mode wait of a wait-for cycle (the waiter
    * holds a monitor its blocker is BLOCKED on) is confirmed once the cycle is found on two
    * scans, and fails on its next check. The failure breaks the cycle, which is never DOWN.
    */
   @Test
   public void confirmedCycleFailsTheFailModeWait() throws Exception {
      policy = confirmedOnly();
      Object monitor = new Object();

      synchronized(monitor) {
         AtomicReference<Thread> t2 = new AtomicReference<>();
         WaitRecord record =
            registry.open("cycle.site", () -> 0, () -> new Thread[] { t2.get() });
         t2.set(blockedOn("cycle-t2", monitor));

         advance(1500);
         record.checkStall();
         assertTrue(record.isTripped());
         assertFalse(record.isFailed(), "not confirmed yet");

         watchdog.scan();
         assertFalse(record.isCycleConfirmed(), "one sighting is not enough");
         record.checkStall();
         watchdog.scan();
         assertTrue(record.isCycleConfirmed(), "found on two scans");
         assertNull(watchdog.getUnreleasedStall(), "its failure releases it");

         LockStallException ex = assertThrows(LockStallException.class, record::checkStall);
         assertEquals("cycle.site", ex.getSite());
         assertTrue(record.isFailed());
         assertStallOf(ex, assertThrows(LockStallException.class, record::checkStall),
                    "a failed wait rethrows the stall (a copy)");
         record.close();
      }

      watchdog.scan();
      assertNull(watchdog.getUnreleasedStall());
   }

   /**
    * Feature #77123: a thread parked for a lock another thread owns (here the read lock of a
    * ReentrantReadWriteLock whose write lock the waiter holds, as for the sandbox lock) waits
    * for that owner, so the cycle through it is confirmed and fails the fail-mode wait.
    */
   @Test
   public void cycleThroughAnOwnedLockFailsTheFailModeWait() throws Exception {
      policy = confirmedOnly();
      ReentrantReadWriteLock rw = new ReentrantReadWriteLock();
      rw.writeLock().lock();

      try {
         Thread reader = start("rw-reader", () -> {
            rw.readLock().lock();
            rw.readLock().unlock();
         });
         StallTestSupport.awaitTrue(() -> reader.getState() == Thread.State.WAITING, 5,
                                    "the reader never parked for the read lock");
         WaitRecord record = registry.open("rw.site", () -> 0, () -> new Thread[] { reader });
         advance(1500);
         record.checkStall();
         watchdog.scan();
         watchdog.scan();
         assertTrue(record.isCycleConfirmed(), "the owned-lock edge closes the cycle");
         assertThrows(LockStallException.class, record::checkStall);
         record.close();
      }
      finally {
         rw.writeLock().unlock();
      }
   }

   /**
    * Feature #77123: a confirmed cycle whose fail-mode waiter never reaches its check (it
    * cannot fail) is unreleased once it was confirmed for two wait slices.
    */
   @Test
   public void confirmedCycleNotBrokenByItsFailureIsUnreleased() throws Exception {
      policy = confirmedOnly();
      Object monitor = new Object();
      AtomicReference<Thread> t2 = new AtomicReference<>();
      WaitRecord t1 =
         waitHolding("stuck-t1", monitor, () -> new Thread[] { t2.get() }, release, release);
      t2.set(blockedOn("stuck-t2", monitor));

      advance(1500);
      watchdog.scan();
      watchdog.scan();
      assertTrue(t1.isCycleConfirmed());
      assertNull(watchdog.getUnreleasedStall(), "the waiter still gets its chance to fail");

      // the slice is noProgressMillis / 4 = 250 ms
      advance(500);
      watchdog.scan();
      String reason = watchdog.getUnreleasedStall();
      assertNotNull(reason, "the waiter did not fail and unwind");
      assertTrue(reason.contains("wait-for cycle") && reason.contains("stuck-t1.site"), reason);
   }

   /**
    * Feature #77123: a fail-mode wait for a thread of a JVM deadlock can never progress, so it
    * is confirmed (on two scans) and fails, although it is no member of a cycle itself.
    */
   @Test
   public void waitForAJvmDeadlockFailsTheFailModeWait() throws Exception {
      policy = confirmedOnly();
      Object monitor = new Object();
      holding("deadlocked", monitor);
      Thread stuck = blockedOn("deadlocked-peer", monitor);
      WaitRecord record = registry.open("behind.site", () -> 0, () -> new Thread[] { stuck });
      advance(1500);
      record.checkStall();
      assertFalse(record.isFailed());

      deadlocked = new long[] { stuck.threadId() };
      watchdog.scan();
      assertFalse(record.isCycleConfirmed(), "one sighting is not enough");
      watchdog.scan();
      assertTrue(record.isCycleConfirmed());
      assertThrows(LockStallException.class, record::checkStall);
      record.close();
   }

   /**
    * Feature #77123: progress ends a confirmed episode, so a wait that got going again is not
    * failed by an old confirmation.
    */
   @Test
   public void progressEndsTheConfirmation() throws Exception {
      policy = confirmedOnly();
      Object monitor = new Object();
      AtomicLong rows = new AtomicLong();

      synchronized(monitor) {
         AtomicReference<Thread> t2 = new AtomicReference<>();
         WaitRecord record =
            registry.open("resumed.site", rows::get, () -> new Thread[] { t2.get() });
         t2.set(blockedOn("resumed-t2", monitor));
         advance(1500);
         record.checkStall();
         watchdog.scan();
         watchdog.scan();
         assertTrue(record.isCycleConfirmed());

         rows.incrementAndGet();
         record.checkStall();
         assertFalse(record.isCycleConfirmed(), "progress ends the episode");
         assertFalse(record.isFailed());
         record.close();
      }
   }

   /**
    * Feature #77123 (review P2): a cycle confirmed on two scans dissolves before the waiter
    * checks, and the wait now waits for a live thread that sleeps (so it gets no credit). The
    * waiter checks the graph again before it fails, finds no cycle, and goes on waiting.
    */
   @Test
   public void dissolvedCycleIsNotFailedAtTheWaitersCheck() throws Exception {
      policy = confirmedOnly();
      Object monitor = new Object();
      AtomicReference<Thread> target = new AtomicReference<>();

      synchronized(monitor) {
         WaitRecord record =
            registry.open("dissolved.site", () -> 0, () -> new Thread[] { target.get() });
         target.set(blockedOn("dissolved-t2", monitor));
         advance(1500);
         record.checkStall();
         watchdog.scan();
         watchdog.scan();
         assertTrue(record.isCycleConfirmed(), "the cycle was confirmed");

         // the cycle dissolves: the wait is now for a sleeping owner, no cycle
         target.set(sleeper("dissolved-sleeper"));
         assertDoesNotThrow(record::checkStall, "a dissolved cycle must not fail the wait");
         assertFalse(record.isCycleConfirmed(), "the confirmation is revoked");
         assertFalse(record.isFailed());
         record.close();
      }
   }

   /**
    * Feature #77123: a scan that no longer finds the confirmed cycle revokes the confirmation,
    * so it does not wait for the waiter's own check to notice.
    */
   @Test
   public void scanRevokesTheConfirmationOfADissolvedCycle() throws Exception {
      policy = confirmedOnly();
      Object monitor = new Object();
      AtomicReference<Thread> target = new AtomicReference<>();

      synchronized(monitor) {
         WaitRecord record =
            registry.open("revoked.site", () -> 0, () -> new Thread[] { target.get() });
         target.set(blockedOn("revoked-t2", monitor));
         advance(1500);
         watchdog.scan();
         watchdog.scan();
         assertTrue(record.isCycleConfirmed());

         target.set(sleeper("revoked-sleeper"));
         watchdog.scan();
         assertFalse(record.isCycleConfirmed(), "the scan revokes a dissolved cycle");
         assertDoesNotThrow(record::checkStall);
         assertNull(watchdog.getUnreleasedStall());
         record.close();
      }
   }

   /**
    * Feature #77123: a 2-cycle of fail-mode waits has one victim, the youngest wait. Only it
    * is confirmed and fails; the other one is not failed, and once the victim unwinds nothing
    * is left to confirm.
    */
   @Test
   public void twoCycleFailsOnlyTheYoungestWait() {
      policy = confirmedOnly();
      AtomicReference<Thread> ta = new AtomicReference<>();
      AtomicReference<Thread> tb = new AtomicReference<>();
      WaitRecord a = runOnOtherThread(
         () -> registry.open("two-a.site", () -> 0, () -> new Thread[] { tb.get() }));
      ta.set(a.getThread());
      advance(10);
      WaitRecord b = runOnOtherThread(
         () -> registry.open("two-b.site", () -> 0, () -> new Thread[] { ta.get() }));
      tb.set(b.getThread());

      advance(1500);
      watchdog.scan();
      watchdog.scan();
      assertTrue(b.isCycleConfirmed(), "the youngest wait is the victim");
      assertFalse(a.isCycleConfirmed(), "a single victim per cycle");

      assertDoesNotThrow(a::checkStall);
      assertThrows(LockStallException.class, b::checkStall);
      assertFalse(a.isFailed());
      b.close();

      watchdog.scan();
      watchdog.scan();
      assertDoesNotThrow(a::checkStall, "the cycle is broken, the survivor is not failed");
      assertFalse(a.isCycleConfirmed());
      assertNull(watchdog.getUnreleasedStall());
      a.close();
   }

   /**
    * Feature #77123: a 3-cycle of fail-mode waits opened at the same instant has exactly one
    * victim, the lowest thread id. The victim stays the one failure while it is still
    * registered, and its two peers are never failed.
    */
   @Test
   public void threeCycleFailsExactlyOneMember() {
      policy = confirmedOnly();
      List<AtomicReference<Thread>> threads = List.of(
         new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>());
      List<WaitRecord> records = new ArrayList<>();

      for(int i = 0; i < 3; i++) {
         AtomicReference<Thread> next = threads.get((i + 1) % 3);
         WaitRecord record = runOnOtherThread(
            () -> registry.open("three.site", () -> 0, () -> new Thread[] { next.get() }));
         threads.get(i).set(record.getThread());
         records.add(record);
      }

      advance(1500);
      watchdog.scan();
      watchdog.scan();

      WaitRecord expected = records.stream()
         .min(Comparator.comparingLong(r -> r.getThread().threadId())).orElseThrow();
      int failures = 0;

      for(WaitRecord record : records) {
         try {
            record.checkStall();
         }
         catch(LockStallException ex) {
            failures++;
            assertSame(expected, record, "the lowest thread id is the victim on a tie");
         }
      }

      assertEquals(1, failures, "exactly one member of the cycle fails");

      // the victim has not unwound yet: it stays the victim, no second failure
      watchdog.scan();
      watchdog.scan();

      for(WaitRecord record : records) {
         if(record != expected) {
            assertDoesNotThrow(record::checkStall);
            assertFalse(record.isCycleConfirmed());
         }
      }

      records.forEach(WaitRecord::close);
   }

   /**
    * Feature #77123 (review r2, liveness): a victim whose thread never reaches its check, such
    * as one parked without a timeout inside its registered wait, is passed over once it was
    * confirmed for more than two wait slices. The next member becomes the victim and fails;
    * the passed-over one, if merely slow, is never failed too; health stays DOWN meanwhile.
    */
   @Test
   public void victimThatCannotCheckHandsTheRoleOn() {
      policy = confirmedOnly();
      AtomicReference<Thread> ta = new AtomicReference<>();
      AtomicReference<Thread> tb = new AtomicReference<>();
      WaitRecord a = runOnOtherThread(
         () -> registry.open("pass-a.site", () -> 0, () -> new Thread[] { tb.get() }));
      ta.set(a.getThread());
      advance(10);
      WaitRecord b = runOnOtherThread(
         () -> registry.open("pass-b.site", () -> 0, () -> new Thread[] { ta.get() }));
      tb.set(b.getThread());

      advance(1500);
      watchdog.scan();
      watchdog.scan();
      assertTrue(b.isCycleConfirmed(), "the youngest wait is the first victim");
      assertFalse(a.isCycleConfirmed());

      // the slice is noProgressMillis / 4 = 250 ms: two slices are not over yet
      advance(400);
      watchdog.scan();
      assertTrue(b.isCycleConfirmed(), "the victim keeps its chance for two slices");
      assertFalse(a.isCycleConfirmed());

      advance(200);
      watchdog.scan();
      assertFalse(b.isCycleConfirmed(), "the victim that did not fail is passed over");
      assertTrue(a.isCycleConfirmed(), "the next member is the victim");
      String reason = watchdog.getUnreleasedStall();
      assertNotNull(reason, "the hand-off does not make the cycle released");
      assertTrue(reason.contains("wait-for cycle"), reason);

      // the passed-over victim was only slow: it checks now and goes on
      assertDoesNotThrow(b::checkStall);
      assertFalse(b.isFailed());
      assertThrows(LockStallException.class, a::checkStall);

      // the failed victim is still registered: it stays the victim, no second failure
      for(int i = 0; i < 3; i++) {
         advance(600);
         watchdog.scan();
         assertFalse(b.isCycleConfirmed(), "a single failure per cycle");
         assertDoesNotThrow(b::checkStall);
      }

      assertFalse(b.isFailed());
      a.close();
      b.close();
   }

   /**
    * Feature #77123 (review r2, liveness): when no victim ever checks, the role goes round the
    * cycle in the victim order, and only one member is ever confirmed at a time.
    */
   @Test
   public void victimRoleGoesRoundOneAtATime() {
      policy = confirmedOnly();
      List<AtomicReference<Thread>> threads = List.of(
         new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>());
      List<WaitRecord> records = new ArrayList<>();

      for(int i = 0; i < 3; i++) {
         AtomicReference<Thread> next = threads.get((i + 1) % 3);
         WaitRecord record = runOnOtherThread(
            () -> registry.open("round.site", () -> 0, () -> new Thread[] { next.get() }));
         threads.get(i).set(record.getThread());
         records.add(record);
         advance(10);
      }

      advance(1500);
      watchdog.scan();
      watchdog.scan();
      // the youngest first: opened last
      List<WaitRecord> expected = List.of(records.get(2), records.get(1), records.get(0),
                                          records.get(2), records.get(1));

      for(WaitRecord victim : expected) {
         for(WaitRecord record : records) {
            assertEquals(record == victim, record.isCycleConfirmed(),
                         "one confirmed victim at a time, in the victim order");
         }

         advance(600);
         watchdog.scan();
      }

      records.forEach(record -> assertFalse(record.isFailed()));
      records.forEach(WaitRecord::close);
   }

   /**
    * Feature #77123 (review P3): a thread in a timed tryLock for a lock the waiter owns ends
    * its wait by itself, so it is no permanent edge and no cycle through it is confirmed.
    */
   @Test
   public void timedTryLockIsNoPermanentEdge() throws Exception {
      policy = confirmedOnly();
      ReentrantLock lock = new ReentrantLock();
      lock.lock();

      try {
         Thread tryer = start("timed-tryer", () -> {
            if(lock.tryLock(60, TimeUnit.SECONDS)) {
               lock.unlock();
            }
         });
         StallTestSupport.awaitTrue(() -> tryer.getState() == Thread.State.TIMED_WAITING, 5,
                                    "the tryer never parked for the lock");
         WaitRecord record =
            registry.open("timed.site", () -> 0, () -> new Thread[] { tryer });
         advance(1500);
         record.checkStall();

         for(int i = 0; i < 4; i++) {
            watchdog.scan();
         }

         assertFalse(record.isCycleConfirmed(), "a timed park is no permanent edge");
         assertDoesNotThrow(record::checkStall);
         assertFalse(record.isFailed());
         record.close();
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * The rule of {@code stall.watchdog.failOnTimeout=true}, and of fail before Feature #77123:
    * the timeout alone fails the wait, no cycle needed.
    */
   @Test
   public void failOnTimeoutFailsWithoutACycle() {
      policy = new StallPolicy(StallPolicy.Mode.FAIL, 1000, 500, dumpDir, 20, true);
      WaitRecord record = registry.open("timeout.site", () -> 0, NONE);
      advance(1500);
      assertThrows(LockStallException.class, record::checkStall);
      record.close();
   }

   /**
    * The policy of the default rule (Feature #77123) with this test's limits.
    */
   private StallPolicy confirmedOnly() {
      return new StallPolicy(StallPolicy.Mode.FAIL, 1000, 500, dumpDir, 20, false);
   }

   /**
    * Start a thread that registers a wait for {@code blockers} and holds {@code monitor} until
    * {@code leave}, then keeps waiting (the record open) until {@code done}. The wait never
    * checks its stall itself, so it gets no credit.
    */
   private WaitRecord waitHolding(String name, Object monitor, Supplier<Thread[]> blockers,
                                  CountDownLatch leave, CountDownLatch done)
      throws InterruptedException
   {
      AtomicReference<WaitRecord> record = new AtomicReference<>();
      CountDownLatch opened = new CountDownLatch(1);
      start(name, () -> {
         try(WaitRecord wait = registry.open(name + ".site", () -> 0, blockers)) {
            record.set(wait);

            synchronized(monitor) {
               opened.countDown();
               leave.await(30, TimeUnit.SECONDS);
            }

            done.await(30, TimeUnit.SECONDS);
         }
      });
      assertTrue(opened.await(5, TimeUnit.SECONDS), name + " never opened its wait");
      return record.get();
   }

   /**
    * Start a live thread that sleeps until the test ends ({@code TIMED_WAITING}, no credit),
    * and wait until it sleeps.
    */
   private Thread sleeper(String name) throws InterruptedException {
      Thread thread = start(name, () -> release.await(30, TimeUnit.SECONDS));
      StallTestSupport.awaitTrue(() -> thread.getState() == Thread.State.TIMED_WAITING, 5,
                                 name + " never slept");
      return thread;
   }

   /**
    * Start a thread that holds {@code monitor} until the test ends.
    */
   private void holding(String name, Object monitor) throws InterruptedException {
      CountDownLatch held = new CountDownLatch(1);
      start(name, () -> {
         synchronized(monitor) {
            held.countDown();
            release.await(30, TimeUnit.SECONDS);
         }
      });
      assertTrue(held.await(5, TimeUnit.SECONDS), name + " never took the monitor");
   }

   /**
    * Start a thread that enters {@code monitor}, and wait until it is BLOCKED on it.
    */
   private static Thread blockedOn(String name, Object monitor) throws InterruptedException {
      Thread thread = start(name, () -> {
         synchronized(monitor) {
            // only enters it
            Thread.onSpinWait();
         }
      });
      StallTestSupport.awaitTrue(() -> thread.getState() == Thread.State.BLOCKED, 5,
                                 name + " never blocked on the monitor");
      return thread;
   }

   private static Thread start(String name, Interruptible body) {
      Thread thread = new Thread(() -> {
         try {
            body.run();
         }
         catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
         }
      }, name);
      thread.setDaemon(true);
      thread.start();
      return thread;
   }

   /**
    * Wait until the waiter sampled its wait again, entirely at the current clock.
    */
   private static void awaitSample(AtomicLong samples) throws InterruptedException {
      long before = samples.get();
      StallTestSupport.awaitTrue(() -> samples.get() > before + 1, 5,
                                 "the waiter never sampled its wait");
   }

   @FunctionalInterface
   private interface Interruptible {
      void run() throws InterruptedException;
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
