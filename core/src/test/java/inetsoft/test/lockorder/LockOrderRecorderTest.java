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
package inetsoft.test.lockorder;

import inetsoft.util.script.LendableReentrantLock;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The lock-order recorder finds planted inversions without any thread hanging: the two
 * orders run one after the other, never concurrently (bug #77123, reliability plan task 5).
 */
@Tag("core")
class LockOrderRecorderTest {
   @BeforeAll
   static void install() {
      // the recorder is one per JVM: never reset a run the extension is recording
      Assumptions.assumeTrue(System.getProperty(LockOrderExtension.PROPERTY) == null,
                             "a -Dlockorder.record run is in progress");
      recorder = LockOrderRecorder.install();
   }

   @BeforeEach
   void start() {
      recorder.reset();
      recorder.start();
   }

   @AfterEach
   void stop() {
      recorder.stop();
      executor.shutdownNow();
      assertEquals(0, recorder.errors(), "the recorder swallowed errors");
   }

   @AfterAll
   static void uninstall() {
      // runs even when install() was skipped: never remove the recorder of a recorded run
      if(recorder == null) {
         return;
      }

      // the surefire JVM is shared by every later test class
      LockOrderRecorder.uninstall();
      recorder = null;
      // and nothing is left for the later test classes: no sampler, no hook calls
      assertEquals(0, samplerThreads(), "a sampler thread is left");
      AtomicLong reached = new AtomicLong();
      inetsoft.test.lockorder.boot.LockHook.sink = (lock, kind) -> reached.incrementAndGet();

      try {
         ReentrantLock plain = new ReentrantLock();
         plain.lock();
         plain.unlock();
      }
      finally {
         inetsoft.test.lockorder.boot.LockHook.sink = null;
      }

      assertEquals(0, reached.get(), "ReentrantLock still calls the hook after the class");
      System.err.println("LOCKORDER uninstalled: samplerThreads=0 hookCalls=0");
      assertFalse(LockOrderRecorder.isInstalled());
   }

   /**
    * uninstall() restores the lock classes and stops the sampler: a lock op no longer reaches
    * the hook, and no sampler thread is left. The recorder is installed again afterwards for the
    * other tests of this class.
    */
   @Test
   void uninstallRemovesTheInstrumentationAndTheSampler() throws Exception {
      assertTrue(samplerThreads() > 0, "the sampler runs while installed");
      LockOrderRecorder.uninstall();

      try {
         assertNull(inetsoft.test.lockorder.boot.LockHook.sink);
         assertEquals(0, samplerThreads(), "the sampler thread is gone");
         AtomicLong reached = new AtomicLong();
         inetsoft.test.lockorder.boot.LockHook.sink = (lock, kind) -> reached.incrementAndGet();

         try {
            ReentrantLock plain = new ReentrantLock();
            plain.lock();
            assertTrue(plain.tryLock());
            assertTrue(plain.tryLock(1, TimeUnit.SECONDS));
            plain.unlock();
            plain.unlock();
            plain.unlock();
            LendableReentrantLock lendable = new LendableReentrantLock();
            lendable.lock();
            lendable.unlock();
         }
         finally {
            inetsoft.test.lockorder.boot.LockHook.sink = null;
         }

         assertEquals(0, reached.get(), "an uninstalled lock class still calls the hook");
      }
      finally {
         recorder = LockOrderRecorder.install();
         recorder.reset();
         recorder.start();
      }
   }

   private static long samplerThreads() {
      return Thread.getAllStackTraces().keySet().stream()
         .filter(t -> t.getName().equals("lockorder-sampler") && t.isAlive()).count();
   }

   /**
    * Wait (bounded, 30 s) until {@code thread} is BLOCKED, then until the sampler has read the
    * holders at least twice more, so a sampled edge is recorded however slow the machine is.
    */
   private static void awaitBlockedAndSampled(AtomicReference<Thread> thread) throws Exception {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);

      while(thread.get() == null || thread.get().getState() != Thread.State.BLOCKED) {
         assertTrue(System.nanoTime() < deadline, "the thread never blocked");
         Thread.sleep(1);
      }

      awaitSamples(deadline);
   }

   private static void awaitSamples(long deadline) throws Exception {
      long start = recorder.samples();

      while(recorder.samples() < start + 2) {
         assertTrue(System.nanoTime() < deadline, "the sampler did not run");
         Thread.sleep(1);
      }
   }

   @Test
   void theHookIsABootstrapClass() throws Exception {
      Class<?> hook = Class.forName("inetsoft.test.lockorder.boot.LockHook", false, null);
      assertNull(hook.getClassLoader());
      assertSame(hook, inetsoft.test.lockorder.boot.LockHook.class,
                 "application classes resolve the bootstrap copy");
   }

   @Test
   void findsAPlantedReentrantLockInversion() throws Exception {
      ReentrantLock a = new SiteA().lock;
      ReentrantLock b = new SiteB().lock;
      runInOrder(a, b);
      runInOrder(b, a);

      List<LockOrderRecorder.Cycle> cycles = recorder.findCycles();
      System.err.println(recorder.report("planted-rl"));
      assertTrue(hasCycle(cycles, SiteA.class, SiteB.class), String.valueOf(cycles));
      assertEquals(2, cycles.stream().mapToInt(c -> c.edges().size()).sum());
   }

   @Test
   void findsAPlantedEngineLockMonitorInversion() throws Exception {
      LendableReentrantLock engine = new SiteC().lock;
      Object lens = new LensMonitor();

      // monitor -> engine lock, seen at the acquisition
      executor.submit(() -> {
         synchronized(lens) {
            engine.lock();
            engine.unlock();
         }
      }).get(10, TimeUnit.SECONDS);

      // engine lock -> monitor, seen by the sampler while the monitor is held
      executor.submit(() -> {
         engine.lock();

         try {
            synchronized(lens) {
               awaitSamples(System.nanoTime() + TimeUnit.SECONDS.toNanos(30));
            }
         }
         finally {
            engine.unlock();
         }

         return null;
      }).get(10, TimeUnit.SECONDS);

      List<LockOrderRecorder.Cycle> cycles = recorder.findCycles();
      System.err.println(recorder.report("planted-lrl-monitor"));
      assertTrue(cycles.stream().anyMatch(c ->
         c.involvesScriptLock() && c.nodes().stream().anyMatch(n -> n.contains("LensMonitor"))),
                 String.valueOf(cycles));
   }

   @Test
   void aThreadBlockedOnAMonitorGivesTheEdge() throws Exception {
      ReentrantLock a = new SiteA().lock;
      Object lens = new LensMonitor();
      CountDownLatch owned = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      Future<?> owner = executor.submit(() -> {
         synchronized(lens) {
            owned.countDown();
            release.await(10, TimeUnit.SECONDS);
         }

         return null;
      });
      assertTrue(owned.await(10, TimeUnit.SECONDS));
      AtomicReference<Thread> blockedThread = new AtomicReference<>();
      Future<?> blocked = executor.submit(() -> {
         blockedThread.set(Thread.currentThread());
         a.lock();

         try {
            synchronized(lens) {
               return null;
            }
         }
         finally {
            a.unlock();
         }
      });
      awaitBlockedAndSampled(blockedThread);
      release.countDown();
      owner.get(10, TimeUnit.SECONDS);
      blocked.get(10, TimeUnit.SECONDS);

      assertTrue(recorder.edges().stream().anyMatch(e ->
         e.from().contains("SiteA") && e.to().contains("LensMonitor")),
                 recorder.report("blocked"));
   }

   @Test
   void tryLockEdgesNeverCloseACycle() throws Exception {
      ReentrantLock a = new SiteA().lock;
      ReentrantLock b = new SiteB().lock;
      runInOrder(a, b);

      executor.submit(() -> {
         b.lock();

         try {
            assertTrue(a.tryLock());
            a.unlock();
         }
         finally {
            b.unlock();
         }

         return null;
      }).get(10, TimeUnit.SECONDS);

      assertFalse(hasCycle(recorder.findCycles(), SiteA.class, SiteB.class),
                  "a tryLock cannot deadlock");
      assertTrue(hasCycle(recorder.findCycles(EnumSet.allOf(LockOrderRecorder.Kind.class)),
                          SiteA.class, SiteB.class));
   }

   /**
    * The F1 shape, bounded: the lock owner blocks on a monitor while a monitor holder waits
    * for the lock with a timed tryLock that fails. The failed timed attempt is an edge.
    */
   @Test
   void aFailedTimedAttemptUnderAMonitorClosesACycle() throws Exception {
      ReentrantLock lens = new SiteA().lock;
      Object base = new LensMonitor();
      CountDownLatch owned = new CountDownLatch(1);
      CountDownLatch monitorHeld = new CountDownLatch(1);
      AtomicReference<Thread> ownerThread = new AtomicReference<>();
      Future<?> owner = executor.submit(() -> {
         ownerThread.set(Thread.currentThread());
         lens.lock();

         try {
            owned.countDown();
            assertTrue(monitorHeld.await(10, TimeUnit.SECONDS));

            synchronized(base) {
               return null;
            }
         }
         finally {
            lens.unlock();
         }
      });
      assertTrue(owned.await(10, TimeUnit.SECONDS));
      Future<Boolean> reader = executor.submit(() -> {
         synchronized(base) {
            monitorHeld.countDown();
            // the owner blocks on base; wait until the sampler has seen it
            awaitBlockedAndSampled(ownerThread);
            return lens.tryLock(200, TimeUnit.MILLISECONDS);
         }
      });
      assertFalse(reader.get(10, TimeUnit.SECONDS));
      owner.get(10, TimeUnit.SECONDS);

      assertTrue(recorder.edges().stream().anyMatch(e ->
         e.kind() == LockOrderRecorder.Kind.TIMED && e.to().contains("SiteA")),
                 recorder.report("timed"));
      assertTrue(recorder.findCycles().stream().anyMatch(c ->
         c.nodes().stream().anyMatch(n -> n.contains("SiteA")) &&
         c.nodes().stream().anyMatch(n -> n.contains("LensMonitor"))), recorder.report("timed"));
   }

   @Test
   void reentryIsNoEdgeButTwoInstancesOfOneSiteAreASelfLoop() throws Exception {
      ReentrantLock a = new SiteA().lock;
      executor.submit(() -> {
         a.lock();
         a.lock();
         a.unlock();
         a.unlock();
      }).get(10, TimeUnit.SECONDS);
      assertTrue(recorder.findCycles().isEmpty(), recorder.report("reentry"));

      ReentrantLock other = new SiteA().lock;
      runInOrder(a, other);
      List<LockOrderRecorder.Cycle> cycles = recorder.findCycles();
      assertEquals(1, cycles.size(), String.valueOf(cycles));
      assertEquals(1, cycles.get(0).nodes().size());
   }

   @Test
   void aLockOnlyItsCreatorUsedYetGivesAPrivateEdgeThatClosesNoCycle() throws Exception {
      Object lens = new LensMonitor();
      // created and first taken under the monitor on one thread, as an engine locks itself
      // in its own init before anyone else can see it
      LendableReentrantLock engine = executor.submit(() -> {
         LendableReentrantLock created = new SiteC().lock;

         synchronized(lens) {
            created.lock();
            created.unlock();
         }

         return created;
      }).get(10, TimeUnit.SECONDS);

      executor.submit(() -> {
         engine.lock();

         try {
            synchronized(lens) {
               awaitSamples(System.nanoTime() + TimeUnit.SECONDS.toNanos(30));
            }
         }
         finally {
            engine.unlock();
         }

         return null;
      }).get(10, TimeUnit.SECONDS);

      assertTrue(recorder.edges().stream().anyMatch(e ->
         e.kind() == LockOrderRecorder.Kind.PRIVATE && e.to().contains("SiteC")),
                 recorder.report("private"));
      assertTrue(recorder.findCycles().isEmpty(), recorder.report("private"));
   }

   @Test
   void nothingIsRecordedWhileStopped() throws Exception {
      recorder.stop();
      ReentrantLock a = new SiteA().lock;
      ReentrantLock b = new SiteB().lock;
      recorder.start();
      // allocated while stopped: not named, so not recorded
      runInOrder(a, b);
      runInOrder(b, a);
      assertTrue(recorder.findCycles().isEmpty());
      assertTrue(recorder.ignoredEvents() > 0);
   }

   private void runInOrder(java.util.concurrent.locks.Lock first,
                           java.util.concurrent.locks.Lock second) throws Exception
   {
      executor.submit(() -> {
         first.lock();

         try {
            second.lock();
            second.unlock();
         }
         finally {
            first.unlock();
         }
      }).get(10, TimeUnit.SECONDS);
   }

   private static boolean hasCycle(List<LockOrderRecorder.Cycle> cycles, Class<?> x, Class<?> y) {
      return cycles.stream().anyMatch(c ->
         c.nodes().stream().anyMatch(n -> n.endsWith(x.getName())) &&
         c.nodes().stream().anyMatch(n -> n.endsWith(y.getName())));
   }

   static final class SiteA {
      final ReentrantLock lock = new ReentrantLock();
   }

   static final class SiteB {
      final ReentrantLock lock = new ReentrantLock();
   }

   static final class SiteC {
      final LendableReentrantLock lock = new LendableReentrantLock();
   }

   static final class LensMonitor {
   }

   private static LockOrderRecorder recorder;
   private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
      Thread thread = new Thread(r, "lockorder-test");
      thread.setDaemon(true);
      return thread;
   });
}
