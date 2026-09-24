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

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the progress / no-progress logic of registered waits with an injected clock, so no
 * test sleeps for the stall limit (bug #76967).
 */
@Tag("core")
public class WaitRegistryTest {
   @BeforeEach
   public void setUp() {
      StallTestSupport.resetGlobalStallState();
      policy = new StallPolicy(StallPolicy.Mode.FAIL, 1000, 500, dumpDir);
      dumper = new StallDumper(now::get, () -> dumpDir, 60000);
      registry = new WaitRegistry(now::get, () -> policy, dumper);
   }

   @AfterEach
   public void tearDown() {
      release.countDown();
   }

   @Test
   public void noProgressFailsAfterTheLimit() throws Exception {
      WaitRecord record = registry.open("site", () -> 7, NONE);
      advance(999);
      record.checkStall();
      advance(2);
      LockStallException ex = assertThrows(LockStallException.class, record::checkStall);

      assertEquals("site", ex.getSite());
      assertEquals(Thread.currentThread().getName(), ex.getThreadName());
      assertEquals(1001, ex.getStalledMillis());
      assertNotNull(ex.getDumpPath());
      assertTrue(Files.readString(new File(ex.getDumpPath()).toPath(), StandardCharsets.UTF_8)
                    .contains("site"));
      assertTrue(record.isFailed());
      record.close();
      assertTrue(registry.getActive().isEmpty());
   }

   @Test
   public void progressResetsTheClock() {
      AtomicLong rows = new AtomicLong();
      WaitRecord record = registry.open("site", rows::get, NONE);

      for(int i = 0; i < 5; i++) {
         advance(600);
         rows.incrementAndGet();
         record.checkStall();
      }

      assertFalse(record.isTripped());
      assertEquals(0, dumper.getDumpCount());
      record.close();
   }

   @Test
   public void alertModeFlagsOncePerEpisode() {
      policy = new StallPolicy(StallPolicy.Mode.ALERT, 1000, 500, dumpDir);
      AtomicLong rows = new AtomicLong();
      WaitRecord record = registry.open("site", rows::get, NONE);

      advance(1500);
      record.checkStall();
      assertTrue(record.isTripped());
      advance(100);
      record.checkStall();
      assertEquals(1, dumper.getDumpCount(), "one dump per stall episode");

      advance(10);
      rows.incrementAndGet();
      record.checkStall();
      assertFalse(record.isTripped(), "progress ends the episode");

      advance(61000);
      record.checkStall();
      assertTrue(record.isTripped());
      assertEquals(2, dumper.getDumpCount(), "a new episode dumps again");
      assertFalse(record.isFailed());
      record.close();
   }

   @Test
   public void offModeRegistersNothing() {
      policy = new StallPolicy(StallPolicy.Mode.OFF, 1000, 500, dumpDir);
      WaitRecord record = registry.open("site", () -> 0, NONE);

      assertSame(WaitRecord.NOOP, record);
      assertTrue(registry.getActive().isEmpty());
      assertEquals(0, registry.getBeginCount());
      advance(100000);
      record.checkStall();
      assertEquals(500, record.waitMillis(500));
      record.close();
   }

   @Test
   public void waitMillisIsSlicedByTheLimit() {
      WaitRecord record = registry.open("site", () -> 0, NONE);
      assertEquals(250, record.waitMillis(10000));
      assertEquals(50, record.waitMillis(50));
      record.close();

      policy = new StallPolicy(StallPolicy.Mode.FAIL, 300000, 30000, dumpDir);
      WaitRecord prod = registry.open("site", () -> 0, NONE);
      assertEquals(10000, prod.waitMillis(10000), "production timeouts are unchanged");
      prod.close();
   }

   @Test
   public void progressingRegisteredBlockerCreditsTheWaiter() throws Exception {
      AtomicLong blockerRows = new AtomicLong();
      Parked blocker = parked("blocker", blockerRows::get, NONE);
      WaitRecord waiter = registry.open("waiter", () -> 0, () -> new Thread[] { blocker.thread });

      for(int i = 0; i < 5; i++) {
         advance(600);
         blockerRows.incrementAndGet();
         blocker.record.checkStall();
         waiter.checkStall();
      }

      advance(1100);
      assertThrows(LockStallException.class, blocker.record::checkStall);
      assertThrows(LockStallException.class, waiter::checkStall,
                   "a stalled blocker gives no credit");
      waiter.close();
   }

   @Test
   public void runnableUnregisteredBlockerCreditsTheWaiter() throws Exception {
      AtomicReference<Boolean> spin = new AtomicReference<>(true);
      Thread spinner = daemon(() -> {
         while(spin.get()) {
            Thread.onSpinWait();
         }
      });
      spinner.start();
      WaitRecord waiter = registry.open("waiter", () -> 0, () -> new Thread[] { spinner });

      for(int i = 0; i < 5; i++) {
         advance(600);
         waiter.checkStall();
      }

      spin.set(false);
      spinner.join(5000);
      advance(1100);
      assertThrows(LockStallException.class, waiter::checkStall);
      waiter.close();
   }

   @Test
   public void timedWaitingUnregisteredBlockerGivesNoCredit() throws Exception {
      Thread parked = daemon(() -> {
         try {
            release.await(30, TimeUnit.SECONDS);
         }
         catch(InterruptedException ignore) {
         }
      });
      parked.start();
      awaitWaiting(parked);
      WaitRecord waiter = registry.open("waiter", () -> 0, () -> new Thread[] { parked });

      advance(1100);
      assertThrows(LockStallException.class, waiter::checkStall);
      waiter.close();
   }

   @Test
   public void runnableRegisteredBlockerCreditsItsTimestampNotNow() throws Exception {
      AtomicReference<Boolean> spin = new AtomicReference<>(true);
      CompletableFuture<WaitRecord> opened = new CompletableFuture<>();
      Thread spinner = daemon(() -> {
         WaitRecord own = registry.open("spinner", () -> 0, NONE);
         opened.complete(own);

         while(spin.get()) {
            Thread.onSpinWait();
         }
      });
      spinner.start();
      opened.get(5, TimeUnit.SECONDS);
      WaitRecord waiter = registry.open("waiter", () -> 0, () -> new Thread[] { spinner });

      try {
         advance(600);
         waiter.checkStall();
         advance(500);
         assertEquals(Thread.State.RUNNABLE, spinner.getState());
         assertThrows(LockStallException.class, waiter::checkStall,
                      "a registered blocker credits its own progress time, not now");
      }
      finally {
         spin.set(false);
         spinner.join(5000);
         waiter.close();
      }
   }

   @Test
   public void monitorBlockedUnregisteredBlockerGivesNoCredit() throws Exception {
      Object monitor = new Object();

      synchronized(monitor) {
         Thread blocked = daemon(() -> {
            synchronized(monitor) {
               monitor.notifyAll();
            }
         });
         blocked.start();
         awaitState(blocked, Thread.State.BLOCKED);
         WaitRecord waiter = registry.open("waiter", () -> 0, () -> new Thread[] { blocked });

         advance(1100);
         assertThrows(LockStallException.class, waiter::checkStall);
         waiter.close();
      }
   }

   @Test
   public void deadBlockerGivesNoCredit() throws Exception {
      Thread dead = daemon(() -> { });
      dead.start();
      dead.join(5000);
      assertEquals(Thread.State.TERMINATED, dead.getState());
      WaitRecord waiter = registry.open("waiter", () -> 0, () -> new Thread[] { dead, null });

      advance(1100);
      assertThrows(LockStallException.class, waiter::checkStall);
      waiter.close();
   }

   @Test
   public void onlyOpenCountsAsABegin() {
      assertEquals(0, registry.getBeginCount(), "a new registry has seen no wait");
      WaitRecord record = registry.open("site", () -> 0, NONE);

      for(int i = 0; i < 3; i++) {
         advance(100);
         record.checkStall();
         record.waitMillis(10000);
         registry.getActive();
      }

      record.close();
      assertEquals(1, registry.getBeginCount(), "checks, slices and close register nothing");
   }

   @Test
   public void cycleOfRegisteredWaitersIsDetected() throws Exception {
      AtomicReference<Thread> threadA = new AtomicReference<>();
      AtomicReference<Thread> threadB = new AtomicReference<>();
      Parked a = parked("A", () -> 0, () -> new Thread[] { threadB.get() });
      threadA.set(a.thread);
      Parked b = parked("B", () -> 0, () -> new Thread[] { threadA.get() });
      threadB.set(b.thread);
      boolean aFailed = false;
      boolean bFailed = false;

      for(int i = 0; i < 10 && !(aFailed && bFailed); i++) {
         advance(300);

         try {
            a.record.checkStall();
         }
         catch(LockStallException ex) {
            aFailed = true;
         }

         try {
            b.record.checkStall();
         }
         catch(LockStallException ex) {
            bFailed = true;
         }
      }

      assertTrue(aFailed && bFailed, "members of a cycle must not credit each other");
   }

   @Test
   public void nestedRecordsRestoreTheOuterOne() {
      WaitRecord outer = registry.open("outer", () -> 0, NONE);
      WaitRecord inner = registry.open("inner", () -> 0, NONE);

      assertEquals(1, registry.getActive().size());
      assertSame(inner, registry.getActive().get(0));
      inner.close();
      assertSame(outer, registry.getActive().get(0));
      outer.close();
      outer.close();
      assertTrue(registry.getActive().isEmpty());
      assertEquals(2, registry.getBeginCount());
   }

   @Test
   public void runnableBlockerOfARegisteredBlockerCreditsTransitively() throws Exception {
      AtomicReference<Boolean> spin = new AtomicReference<>(true);
      Thread spinner = daemon(() -> {
         while(spin.get()) {
            Thread.onSpinWait();
         }
      });
      spinner.start();
      Parked blocker = parked("B", () -> 0, () -> new Thread[] { spinner });
      WaitRecord waiter = registry.open("waiter", () -> 0, () -> new Thread[] { blocker.thread });

      try {
         for(int i = 0; i < 5; i++) {
            advance(600);
            blocker.record.checkStall();
            waiter.checkStall();
         }

         spin.set(false);
         spinner.join(5000);
         advance(1100);
         assertThrows(LockStallException.class, blocker.record::checkStall);
         assertThrows(LockStallException.class, waiter::checkStall,
                      "the credit ends with the running thread at the end of the chain");
      }
      finally {
         spin.set(false);
         waiter.close();
      }
   }

   @Test
   public void failedWaitRethrowsTheSameException() {
      AtomicLong rows = new AtomicLong();
      WaitRecord record = registry.open("site", rows::get, NONE);
      advance(1500);
      LockStallException first = assertThrows(LockStallException.class, record::checkStall);

      advance(10);
      rows.incrementAndGet();
      LockStallException again = assertThrows(LockStallException.class, record::checkStall,
                                              "a failed wait must not continue silently");
      assertSame(first, again);
      assertEquals(1, dumper.getDumpCount());
      record.close();
   }

   @Test
   public void unboundedWaitIsSliced() {
      WaitRecord record = registry.open("site", () -> 0, NONE);
      assertEquals(250, record.waitMillis(0), "0 means forever for wait/await");
      assertEquals(250, record.waitMillis(-1));
      record.close();
      assertEquals(0, WaitRecord.NOOP.waitMillis(0), "an unwatched wait is unchanged");
   }

   @Test
   public void outOfOrderCloseDoesNotRestoreAClosedRecord() {
      WaitRecord outer = registry.open("outer", () -> 0, NONE);
      WaitRecord inner = registry.open("inner", () -> 0, NONE);

      outer.close();
      assertSame(inner, registry.getActive().get(0));
      inner.close();
      assertTrue(registry.getActive().isEmpty(), "the closed outer wait is not registered again");
   }

   @Test
   public void nullBlockersAreNone() {
      WaitRecord record = registry.open("site", () -> 0, () -> null);
      advance(500);
      record.checkStall();
      advance(600);
      assertThrows(LockStallException.class, record::checkStall);
      record.close();
   }

   @Test
   public void creditOnlyWaitNeverTripsOrDumps() {
      WaitRecord record = registry.openCreditOnly("credit", () -> 0, NONE);
      assertTrue(record.isCreditOnly());
      assertEquals(1, registry.getBeginCount(), "a credit-only wait is a begin too");

      for(int i = 0; i < 5; i++) {
         advance(1500);
         record.checkStall();
      }

      assertFalse(record.isTripped());
      assertFalse(record.isFailed());
      assertNull(record.getDumpPath());
      assertEquals(0, dumper.getDumpCount());

      StallWatchdog watchdog = new StallWatchdog(registry, () -> null);
      watchdog.scan();
      advance(1500);
      watchdog.scan();
      assertNull(watchdog.getUnreleasedStall(), "a credit-only wait is never unreleased");
      assertEquals(0, dumper.getDumpCount(), "nor dumped by the watchdog");
      record.close();
      assertTrue(registry.getActive().isEmpty());
   }

   @Test
   public void creditOnlyWaitNeverThrowsFromItsSamples() {
      WaitRecord record = registry.openCreditOnly(
         "credit", () -> { throw new IllegalStateException("progress"); },
         () -> { throw new IllegalStateException("blockers"); });
      advance(1500);
      assertDoesNotThrow(record::checkStall);
      record.close();
   }

   @Test
   public void creditOnlyWaitPassesOnTheCreditOfARunnableProducer() throws Exception {
      AtomicReference<Boolean> spin = new AtomicReference<>(true);
      Thread producer = daemon(() -> {
         while(spin.get()) {
            Thread.onSpinWait();
         }
      });
      producer.start();
      CompletableFuture<WaitRecord> opened = new CompletableFuture<>();
      Thread holder = daemon(() -> {
         opened.complete(registry.openCreditOnly("holder", () -> 0,
                                                 () -> new Thread[] { producer }));

         try {
            release.await(30, TimeUnit.SECONDS);
         }
         catch(InterruptedException ignore) {
         }
      });
      holder.start();
      WaitRecord credit = opened.get(5, TimeUnit.SECONDS);
      awaitWaiting(holder);
      WaitRecord waiter = registry.open("waiter", () -> 0, () -> new Thread[] { holder });

      try {
         for(int i = 0; i < 6; i++) {
            advance(600);
            credit.checkStall();
            waiter.checkStall();
         }

         spin.set(false);
         producer.join(5000);
         advance(1100);
         credit.checkStall();
         assertThrows(LockStallException.class, waiter::checkStall,
                      "no credit once the producer stops: the stale time is passed on");
      }
      finally {
         spin.set(false);
         waiter.close();
      }
   }

   @Test
   public void cycleThroughACreditOnlyWaitTripsTheRegisteredMember() throws Exception {
      AtomicReference<Thread> threadA = new AtomicReference<>();
      AtomicReference<Thread> threadB = new AtomicReference<>();
      CompletableFuture<WaitRecord> opened = new CompletableFuture<>();
      Thread a = daemon(() -> {
         opened.complete(registry.openCreditOnly("A", () -> 0,
                                                 () -> new Thread[] { threadB.get() }));

         try {
            release.await(30, TimeUnit.SECONDS);
         }
         catch(InterruptedException ignore) {
         }
      });
      threadA.set(a);
      a.start();
      WaitRecord recordA = opened.get(5, TimeUnit.SECONDS);
      Parked b = parked("B", () -> 0, () -> new Thread[] { threadA.get() });
      threadB.set(b.thread);
      boolean bFailed = false;

      for(int i = 0; i < 10 && !bFailed; i++) {
         advance(300);
         recordA.checkStall();

         try {
            b.record.checkStall();
         }
         catch(LockStallException ex) {
            bFailed = true;
         }
      }

      assertTrue(bFailed, "a credit-only wait in a cycle must not keep it alive");
   }

   private Parked parked(String what, LongSupplier progress, Supplier<Thread[]> blockers)
      throws Exception
   {
      CompletableFuture<WaitRecord> opened = new CompletableFuture<>();
      Thread thread = daemon(() -> {
         opened.complete(registry.open(what, progress, blockers));

         try {
            release.await(30, TimeUnit.SECONDS);
         }
         catch(InterruptedException ignore) {
         }
      });
      thread.start();
      return new Parked(thread, opened.get(5, TimeUnit.SECONDS));
   }

   private static Thread daemon(Runnable runnable) {
      Thread thread = new Thread(runnable);
      thread.setDaemon(true);
      return thread;
   }

   private static void awaitWaiting(Thread thread) throws InterruptedException {
      long deadline = System.currentTimeMillis() + 5000;

      while(thread.getState() != Thread.State.WAITING &&
         thread.getState() != Thread.State.TIMED_WAITING)
      {
         assertTrue(System.currentTimeMillis() < deadline, "thread never parked");
         Thread.sleep(5);
      }
   }

   private static void awaitState(Thread thread, Thread.State state) throws InterruptedException {
      long deadline = System.currentTimeMillis() + 5000;

      while(thread.getState() != state) {
         assertTrue(System.currentTimeMillis() < deadline, "thread never reached " + state);
         Thread.sleep(5);
      }
   }

   private void advance(long millis) {
      now.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
   }

   private record Parked(Thread thread, WaitRecord record) {
   }

   private static final Supplier<Thread[]> NONE = () -> new Thread[0];

   @TempDir
   File dumpDir;
   private final AtomicLong now = new AtomicLong(1_000_000_000L);
   private final CountDownLatch release = new CountDownLatch(1);
   private volatile StallPolicy policy;
   private StallDumper dumper;
   private WaitRegistry registry;
}
