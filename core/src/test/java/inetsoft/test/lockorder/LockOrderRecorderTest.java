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
               Thread.sleep(200);
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
      Future<?> blocked = executor.submit(() -> {
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
      Thread.sleep(100);
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
      Future<?> owner = executor.submit(() -> {
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
            // the owner is now BLOCKED on base; the sampler sees it
            Thread.sleep(100);
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
               Thread.sleep(200);
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
