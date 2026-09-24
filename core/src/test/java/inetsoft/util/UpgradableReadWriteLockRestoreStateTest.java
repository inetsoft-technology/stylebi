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

import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #76986: the per-thread lock-state stack and the physical ReentrantReadWriteLock holds of
 * the restoring thread after {@link UpgradableReadWriteLock#restoreLocks()} of upgrade stacks,
 * on success and on a bounded failure. Thread A is a single-thread executor, so its per-thread
 * lock state stays on one thread while the test thread drives the peers.
 */
@Tag("core")
class UpgradableReadWriteLockRestoreStateTest {
   static final long BOUND = 300;
   static final long HANG = 3000;
   static final int READ = 0, WRITE = 1, NB_READ = 2, NB_WRITE = 3, SK_READ = 4, SK_WRITE = 5;

   ExecutorService a;
   final ThreadLocal<Boolean> nb = ThreadLocal.withInitial(() -> false);
   UpgradableReadWriteLock lock;
   final List<Thread> peers = new ArrayList<>();

   @BeforeEach
   void setUp() {
      a = Executors.newSingleThreadExecutor(r -> {
         Thread t = new Thread(r, "A");
         t.setDaemon(true);
         return t;
      });
      lock = new UpgradableReadWriteLock(BOUND, () -> nb.get());
   }

   @AfterEach
   void tearDown() throws Exception {
      a.shutdownNow();

      for(Thread t : peers) {
         t.join(8000);
         assertFalse(t.isAlive(), "peer " + t.getName() + " stuck");
      }

      assertLockFree();
   }

   // ---------------- scenarios ----------------

   /** S2: reader holds, writer queues while A is inside the bounded wait. */
   @Test
   void s2WriterQueuesDuringBoundedWait() throws Exception {
      onA(() -> { lock.lockRead(); lock.lockWrite(); lock.unlockAll(); });
      // released by the test, not when A returns, so the writer queues behind the reader even
      // if A's bound expires first (the expected state is the same either way)
      CountDownLatch release = new CountDownLatch(1);

      try {
         peerHoldRawRead(release);
         Thread at = aThread();
         Future<Object> f = restoreAsync(new CountDownLatch(1));
         awaitInBoundedWait(at);
         peerQueueRawWrite(release);
         awaitQueuedWriter();
         Object r = await(f, at);
         assertInstanceOf(IllegalStateException.class, r);
         State s = state();
         assertEquals(List.of(READ, SK_WRITE), s.stack);
         assertEquals(1, s.readHolds, "read barged back past the queued writer");
         assertFalse(s.writeHeld);
         assertEquals(1, s.skipped);
         onA(() -> { lock.unlockWrite(); lock.unlockRead(); });
         assertClean();
      }
      finally {
         release.countDown();
      }
   }

   /** Writer already holds the lock when the [READ, WRITE] restore starts. */
   @Test
   void writerHoldsAtRestoreStart() throws Exception {
      onA(() -> { lock.lockRead(); lock.lockWrite(); lock.unlockAll(); });
      CountDownLatch aDone = new CountDownLatch(1);
      peerHoldRawWrite(aDone);
      Thread at = aThread();
      Object r = await(restoreAsync(aDone), at);
      assertInstanceOf(IllegalStateException.class, r);
      State s = state();
      assertEquals(List.of(SK_READ, SK_WRITE), s.stack);
      assertEquals(0, s.readHolds);
      assertFalse(s.writeHeld);
      assertEquals(2, s.skipped);
      onA(() -> { lock.unlockWrite(); lock.unlockRead(); });
      assertThrows(ExecutionException.class, () -> a.submit(() -> lock.unlockRead()).get());
      assertClean();
   }

   /** NB_READ between READ and WRITE: happy path, writer-holds and S2. */
   @Test
   void nbReadBetweenReadAndWrite() throws Exception {
      Runnable save = () -> {
         lock.lockRead();
         nb.set(true);
         lock.lockRead();
         nb.set(false);
         lock.lockWrite();
         lock.unlockAll();
      };

      // happy
      onA(save);
      assertNull(awaitRestore(new CountDownLatch(1)));
      State s = state();
      assertEquals(List.of(READ, NB_READ, WRITE), s.stack);
      assertTrue(s.writeHeld);
      assertEquals(0, s.readHolds);
      assertEquals(0, s.skipped);
      onA(() -> lock.unlockWrite());
      s = state();
      assertFalse(s.writeHeld);
      assertEquals(2, s.readHolds);
      onA(() -> { lock.unlockRead(); lock.unlockRead(); });
      assertClean();
      assertLockFree();

      // writer holds
      onA(save);
      CountDownLatch done1 = new CountDownLatch(1);
      peerHoldRawWrite(done1);
      assertInstanceOf(IllegalStateException.class, awaitRestore(done1));
      s = state();
      assertEquals(List.of(SK_READ, SK_READ, SK_WRITE), s.stack);
      assertEquals(0, s.readHolds);
      assertEquals(3, s.skipped);
      onA(() -> { lock.unlockWrite(); lock.unlockRead(); lock.unlockRead(); });
      assertClean();
      joinPeers();
      assertLockFree();

      // S2, peers released by the test (see s2WriterQueuesDuringBoundedWait)
      onA(save);
      CountDownLatch release = new CountDownLatch(1);

      try {
         peerHoldRawRead(release);
         Thread at = aThread();
         Future<Object> f = restoreAsync(new CountDownLatch(1));
         awaitInBoundedWait(at);
         peerQueueRawWrite(release);
         awaitQueuedWriter();
         assertInstanceOf(IllegalStateException.class, await(f, at));
         s = state();
         assertEquals(List.of(READ, NB_READ, SK_WRITE), s.stack);
         assertEquals(2, s.readHolds);
         onA(() -> { lock.unlockWrite(); lock.unlockRead(); lock.unlockRead(); });
         assertClean();
      }
      finally {
         release.countDown();
      }
   }

   /** Nested [READ, READ, WRITE] happy path, downgrade, then two unlockReads. */
   @Test
   void nestedReadReadWriteHappyPath() throws Exception {
      onA(() -> { lock.lockRead(); lock.lockRead(); lock.lockWrite(); lock.unlockAll(); });
      State s = state();
      assertEquals(List.of(), s.stack);
      assertEquals(0, s.readHolds);
      assertNull(awaitRestore(new CountDownLatch(1)));
      s = state();
      assertEquals(List.of(READ, READ, WRITE), s.stack);
      assertTrue(s.writeHeld);
      assertEquals(0, s.readHolds);
      assertEquals(0, s.skipped);
      onA(() -> lock.unlockWrite());
      s = state();
      assertFalse(s.writeHeld);
      assertEquals(2, s.readHolds);
      onA(() -> lock.unlockRead());
      assertEquals(1, state().readHolds);
      onA(() -> lock.unlockRead());
      assertClean();
   }

   /**
    * Writer grabs the lock around the moment the bounded write fails (the SKIPPED_READ
    * rewrite window). The race is randomized: a reader releases near the bound while a
    * writer barges with untimed tryLock(). Every outcome must keep stack and physical
    * holds consistent and end with nothing held. Which outcomes occur depends on scheduling,
    * so they are only reported; the SKIPPED_READ rewrite is covered deterministically by
    * {@link #writerHoldsAtRestoreStart()} and
    * UpgradableReadWriteLockTest.rewoundReadsAreSkippedWhenWriterTakesLockDuringWait().
    */
   @Test
   void writerBargesAroundBoundExpiry() throws Exception {
      Map<String, Integer> outcomes = new TreeMap<>();
      Random rnd = new Random(76986);

      for(int iter = 0; iter < 30; iter++) {
         // [READ, WRITE] restores its read unheld; [NB_READ, WRITE] restores the read held,
         // so the bounded write rewinds a physical read
         Runnable save = iter % 2 == 0
            ? () -> { lock.lockRead(); lock.lockWrite(); lock.unlockAll(); }
            : () -> { nb.set(true); lock.lockRead(); nb.set(false); lock.lockWrite();
                      lock.unlockAll(); };
         onA(save);
         CountDownLatch aDone = new CountDownLatch(1);
         CountDownLatch rHolds = new CountDownLatch(1);
         long releaseAt = BOUND - 20 + rnd.nextInt(40);
         ReentrantReadWriteLock raw = rawLock();
         AtomicBoolean wGot = new AtomicBoolean();

         Thread r = peer("R" + iter, () -> {
            raw.readLock().lock();
            rHolds.countDown();

            try {
               Thread.sleep(releaseAt);
            }
            catch(InterruptedException ignore) {
            }
            finally {
               raw.readLock().unlock();
            }
         });
         assertTrue(rHolds.await(5, TimeUnit.SECONDS));
         Thread at = aThread();
         long t0 = System.nanoTime();
         Future<Object> f = restoreAsync(aDone);
         Thread w = peer("W" + iter, () -> {
            long end = System.currentTimeMillis() + BOUND + 500;

            while(System.currentTimeMillis() < end && aDone.getCount() > 0) {
               if(raw.writeLock().tryLock()) {
                  wGot.set(true);

                  try {
                     aDone.await(6, TimeUnit.SECONDS);
                  }
                  catch(InterruptedException ignore) {
                  }
                  finally {
                     raw.writeLock().unlock();
                  }

                  return;
               }

               Thread.onSpinWait();
            }
         });
         Object res = await(f, at);
         long ms = (System.nanoTime() - t0) / 1_000_000;
         assertTrue(ms < BOUND + 1000, "iter " + iter + " took " + ms + "ms");
         State s = state();
         int reads = (int) s.stack.stream().filter(o -> o == READ || o == NB_READ).count();
         boolean writes = s.stack.contains(WRITE) || s.stack.contains(NB_WRITE);
         // reads below a held write are rewound (0 physical), as lockWrite() leaves them
         assertEquals(writes ? 0 : reads, s.readHolds, "iter " + iter + " stack " + s.stack);
         assertEquals(writes, s.writeHeld, "iter " + iter + " stack " + s.stack);
         assertEquals(2, s.stack.size());
         String key = res == null ? "restored" + s.stack :
            reads == 0 ? "failed-skipped" : "failed-reads-retaken";
         outcomes.merge(key + (wGot.get() ? "/wGot" : ""), 1, Integer::sum);
         onA(() -> { lock.unlockWrite(); lock.unlockRead(); });
         assertClean();
         aDone.countDown();
         r.join(5000);
         w.join(8000);
         assertFalse(r.isAlive() || w.isAlive());
         assertLockFree();
      }

      System.out.println("bug #76986 barge outcomes: " + outcomes);
   }

   // ---------------- helpers ----------------

   Object await(Future<Object> f, Thread at) throws Exception {
      try {
         return f.get(HANG, TimeUnit.MILLISECONDS);
      }
      catch(TimeoutException ex) {
         fail("restoreLocks() still parked " + HANG + "ms after " + BOUND + "ms bound at " +
              Arrays.toString(at.getStackTrace()));
         return null;
      }
   }

   Object awaitRestore(CountDownLatch done) throws Exception {
      Thread at = aThread();
      return await(restoreAsync(done), at);
   }

   Future<Object> restoreAsync(CountDownLatch done) {
      return a.submit(() -> {
         try {
            lock.restoreLocks();
            return null;
         }
         catch(RuntimeException ex) {
            return ex;
         }
         finally {
            done.countDown();
         }
      });
   }

   void onA(Runnable r) throws Exception {
      a.submit(r).get(HANG, TimeUnit.MILLISECONDS);
   }

   Thread aThread() throws Exception {
      return a.submit(Thread::currentThread).get();
   }

   void awaitInBoundedWait(Thread at) throws InterruptedException {
      long end = System.currentTimeMillis() + 5000;

      while(System.currentTimeMillis() < end) {
         if(at.getState() == Thread.State.TIMED_WAITING && Arrays.stream(at.getStackTrace())
            .anyMatch(fr -> fr.getMethodName().equals("lockWriteBounded")))
         {
            return;
         }

         Thread.sleep(2);
      }

      fail("A never reached lockWriteBounded's timed wait");
   }

   void awaitQueuedWriter() throws InterruptedException {
      long end = System.currentTimeMillis() + 5000;

      while(System.currentTimeMillis() < end) {
         if(peers.stream().anyMatch(t -> t.getName().startsWith("Wq") &&
            t.getState() == Thread.State.WAITING))
         {
            return;
         }

         Thread.sleep(2);
      }

      fail("writer never queued");
   }

   void peerHoldRawRead(CountDownLatch until) throws InterruptedException {
      CountDownLatch holds = new CountDownLatch(1);
      peer("Rh", () -> {
         rawLock().readLock().lock();
         holds.countDown();

         try {
            until.await(6, TimeUnit.SECONDS);
         }
         catch(InterruptedException ignore) {
         }
         finally {
            rawLock().readLock().unlock();
         }
      });
      assertTrue(holds.await(5, TimeUnit.SECONDS));
   }

   void peerHoldRawWrite(CountDownLatch until) throws InterruptedException {
      CountDownLatch holds = new CountDownLatch(1);
      peer("Wh", () -> {
         rawLock().writeLock().lock();
         holds.countDown();

         try {
            until.await(6, TimeUnit.SECONDS);
         }
         catch(InterruptedException ignore) {
         }
         finally {
            rawLock().writeLock().unlock();
         }
      });
      assertTrue(holds.await(5, TimeUnit.SECONDS));
   }

   void peerQueueRawWrite(CountDownLatch until) {
      peer("Wq", () -> {
         rawLock().writeLock().lock();

         try {
            until.await(6, TimeUnit.SECONDS);
         }
         catch(InterruptedException ignore) {
         }
         finally {
            rawLock().writeLock().unlock();
         }
      });
   }

   Thread peer(String name, Runnable r) {
      Thread t = new Thread(r, name);
      t.setDaemon(true);
      peers.add(t);
      t.start();
      return t;
   }

   void joinPeers() throws InterruptedException {
      for(Thread t : peers) {
         t.join(8000);
         assertFalse(t.isAlive());
      }

      peers.clear();
   }

   record State(List<Integer> stack, int readHolds, boolean writeHeld, long skipped,
                boolean oldLocksEmpty) {}

   @SuppressWarnings("unchecked")
   State state() throws Exception {
      return a.submit(() -> {
         Field f = UpgradableReadWriteLock.class.getDeclaredField("thisLockState");
         f.setAccessible(true);
         Map<Object, Stack<Integer>> m = ((ThreadLocal<Map<Object, Stack<Integer>>>) f.get(null)).get();
         Stack<Integer> st = m.get(lock);
         Field o = UpgradableReadWriteLock.class.getDeclaredField("thisOldLocks");
         o.setAccessible(true);
         Deque<?> old = ((ThreadLocal<Deque<?>>) o.get(lock)).get();
         ReentrantReadWriteLock raw = rawLock();
         return new State(st == null ? List.of() : new ArrayList<>(st), raw.getReadHoldCount(),
                          raw.isWriteLockedByCurrentThread(), lock.getSkippedCount(),
                          old.isEmpty());
      }).get();
   }

   void assertClean() throws Exception {
      State s = state();
      assertEquals(List.of(), s.stack, "stack not empty");
      assertEquals(0, s.readHolds, "physical read leaked");
      assertFalse(s.writeHeld, "physical write leaked");
      assertTrue(s.oldLocksEmpty, "saved olocks leaked");
   }

   ReentrantReadWriteLock rawLock() {
      try {
         Field f = UpgradableReadWriteLock.class.getDeclaredField("thisLock");
         f.setAccessible(true);
         return (ReentrantReadWriteLock) f.get(lock);
      }
      catch(ReflectiveOperationException ex) {
         throw new AssertionError(ex);
      }
   }

   void assertLockFree() throws InterruptedException {
      ReentrantReadWriteLock raw = rawLock();
      AtomicBoolean ok = new AtomicBoolean();
      Thread t = new Thread(() -> {
         try {
            if(raw.writeLock().tryLock(3, TimeUnit.SECONDS)) {
               ok.set(true);
               raw.writeLock().unlock();
            }
         }
         catch(InterruptedException ignore) {
         }
      });
      t.start();
      t.join(5000);
      assertTrue(ok.get(), "lock not free (readers=" + raw.getReadLockCount() +
                 ", writeLocked=" + raw.isWriteLocked() + ")");
   }
}
