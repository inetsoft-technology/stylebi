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
import org.junit.jupiter.api.Timeout;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77431: SparseMatrix must be safe for concurrent get/set/clear/serialization. Every value
 * stored in these tests encodes its own cell, so any non-NULL value that isn't the cell's own is a
 * wrong-cell read.
 */
@Tag("core")
class SparseMatrixConcurrencyTest {
   /**
    * One bucket ({@code new SparseMatrix(3)}, columns that are multiples of 3), anchor cells set
    * before the readers see the matrix, and a writer inserting in descending key order so every
    * insert shifts the bucket. Readers must never get another cell's value, nor miss an anchor.
    */
   @Test
   @Timeout(60)
   void readersNeverSeeAnotherCellsValueWhileWriterShiftsBucket() throws Exception {
      final AtomicReference<SparseMatrix> current = new AtomicReference<>(newAnchoredMatrix());
      final AtomicLong reads = new AtomicLong();
      final AtomicLong wrongAnchor = new AtomicLong();
      final AtomicLong wrongNew = new AtomicLong();
      final AtomicLong missedAnchor = new AtomicLong();
      final ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
      final long deadline = System.nanoTime() + RUN_NANOS;

      Thread writer = new Thread(() -> {
         try {
            while(System.nanoTime() < deadline) {
               SparseMatrix m = current.get();

               for(int k = ANCHOR_FIRST - 1; k >= 0; k--) {
                  m.set(0, 3 * k, 3 * k);
               }

               current.set(newAnchoredMatrix());
            }
         }
         catch(Throwable ex) {
            errors.add(ex);
         }
      }, "sparse-writer");

      List<Thread> readers = new ArrayList<>();

      for(int t = 0; t < READERS; t++) {
         final long seed = t;
         readers.add(new Thread(() -> {
            SplittableRandom rnd = new SplittableRandom(seed);
            long n = 0;

            try {
               while(System.nanoTime() < deadline) {
                  SparseMatrix m = current.get();

                  for(int i = 0; i < 1000; i++, n++) {
                     int k = rnd.nextInt(ANCHOR_LAST + 1);
                     Object val = m.get(0, 3 * k);

                     if(k >= ANCHOR_FIRST) {
                        if(val == SparseMatrix.NULL) {
                           missedAnchor.incrementAndGet();
                        }
                        else if(!Integer.valueOf(3 * k).equals(val)) {
                           wrongAnchor.incrementAndGet();
                        }
                     }
                     else if(val != SparseMatrix.NULL && !Integer.valueOf(3 * k).equals(val)) {
                        wrongNew.incrementAndGet();
                     }
                  }
               }
            }
            catch(Throwable ex) {
               errors.add(ex);
            }
            finally {
               reads.addAndGet(n);
            }
         }, "sparse-reader-" + t));
      }

      readers.forEach(Thread::start);
      writer.start();
      joinAll(writer, readers);

      String summary = "reads=" + reads + " wrongAnchor=" + wrongAnchor + " wrongNew=" + wrongNew +
         " missedAnchor=" + missedAnchor + " exceptions=" + errors.size() + " first=" + errors.peek();
      assertTrue(errors.isEmpty(), summary);
      assertEquals(0, wrongAnchor.get(), summary);
      assertEquals(0, wrongNew.get(), summary);
      assertEquals(0, missedAnchor.get(), summary);
      assertTrue(reads.get() > 0, summary);
   }

   /**
    * Several threads write distinct cells into a fresh matrix at the same time, so they race to
    * install the bucket array and the buckets. No entry may be lost.
    */
   @Test
   @Timeout(60)
   void concurrentFirstWritersLoseNoEntries() throws Exception {
      final int writers = 4;
      final int cellsPerWriter = 32;
      final ExecutorService pool = Executors.newFixedThreadPool(writers);
      long lost = 0;
      long rounds = 0;

      try {
         final long deadline = System.nanoTime() + RUN_NANOS;

         while(System.nanoTime() < deadline) {
            // small size so the writers also race on the same buckets
            final SparseMatrix m = new SparseMatrix(5);
            final CyclicBarrier start = new CyclicBarrier(writers);
            List<Future<?>> futures = new ArrayList<>();

            for(int t = 0; t < writers; t++) {
               final int col = t;
               futures.add(pool.submit(() -> {
                  start.await();

                  for(int r = 0; r < cellsPerWriter; r++) {
                     m.set(r, col, cellValue(r, col));
                  }

                  return null;
               }));
            }

            for(Future<?> f : futures) {
               f.get(30, TimeUnit.SECONDS);
            }

            for(int t = 0; t < writers; t++) {
               for(int r = 0; r < cellsPerWriter; r++) {
                  if(!cellValue(r, t).equals(m.get(r, t))) {
                     lost++;
                  }
               }
            }

            rounds++;
         }
      }
      finally {
         pool.shutdownNow();
      }

      assertEquals(0, lost, "lost entries in " + rounds + " rounds");
   }

   /**
    * clear() racing get/set must not throw, and a reader must never get another cell's value.
    */
   @Test
   @Timeout(60)
   void clearRacingGetAndSetIsSafe() throws Exception {
      final SparseMatrix m = new SparseMatrix();
      final AtomicLong wrong = new AtomicLong();
      final ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
      final long deadline = System.nanoTime() + RUN_NANOS;

      Thread writer = new Thread(() -> {
         SplittableRandom rnd = new SplittableRandom(42);

         try {
            for(long n = 0; System.nanoTime() < deadline; n++) {
               if(n % 1000 == 999) {
                  m.clear();
               }
               else {
                  int r = rnd.nextInt(2000);
                  int c = rnd.nextInt(10);
                  m.set(r, c, cellValue(r, c));
               }
            }
         }
         catch(Throwable ex) {
            errors.add(ex);
         }
      }, "sparse-clear-writer");

      List<Thread> readers = new ArrayList<>();

      for(int t = 0; t < READERS; t++) {
         final long seed = t;
         readers.add(new Thread(() -> {
            SplittableRandom rnd = new SplittableRandom(seed);

            try {
               while(System.nanoTime() < deadline) {
                  int r = rnd.nextInt(2000);
                  int c = rnd.nextInt(10);
                  Object val = m.get(r, c);

                  if(val != SparseMatrix.NULL && !cellValue(r, c).equals(val)) {
                     wrong.incrementAndGet();
                  }
               }
            }
            catch(Throwable ex) {
               errors.add(ex);
            }
         }, "sparse-clear-reader-" + t));
      }

      readers.forEach(Thread::start);
      writer.start();
      joinAll(writer, readers);

      assertTrue(errors.isEmpty(), "exceptions: " + errors);
      assertEquals(0, wrong.get());
   }

   /**
    * Serializing a matrix while a writer inserts into it (as DistributedTableCacheStore does with
    * cached lens chains) must not throw and must give a consistent copy.
    */
   @Test
   @Timeout(60)
   void serializationDuringWritesGivesConsistentCopy() throws Exception {
      final AtomicReference<SparseMatrix> current = new AtomicReference<>(newAnchoredMatrix());
      final ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
      final long deadline = System.nanoTime() + RUN_NANOS;

      Thread writer = new Thread(() -> {
         try {
            while(System.nanoTime() < deadline) {
               SparseMatrix m = current.get();

               for(int k = ANCHOR_FIRST - 1; k >= 0; k--) {
                  m.set(0, 3 * k, 3 * k);
               }

               current.set(newAnchoredMatrix());
            }
         }
         catch(Throwable ex) {
            errors.add(ex);
         }
      }, "sparse-ser-writer");

      writer.start();
      long copies = 0;
      long bad = 0;

      try {
         while(System.nanoTime() < deadline) {
            SparseMatrix copy = roundTrip(current.get());
            copies++;

            for(int k = 0; k <= ANCHOR_LAST; k++) {
               Object val = copy.get(0, 3 * k);

               if(k >= ANCHOR_FIRST ? !Integer.valueOf(3 * k).equals(val) :
                  val != SparseMatrix.NULL && !Integer.valueOf(3 * k).equals(val))
               {
                  bad++;
               }
            }
         }
      }
      catch(Throwable ex) {
         errors.add(ex);
      }

      writer.join(30_000);
      assertFalse(writer.isAlive());
      assertTrue(errors.isEmpty(), "exceptions after " + copies + " copies: " + errors.peek());
      assertEquals(0, bad, "inconsistent cells in " + copies + " copies");
   }

   /**
    * The serialized form must stay compatible with entries written by earlier builds (e.g. lens
    * chains in the DistributedTableCacheStore), and a deserialized matrix must stay usable.
    */
   @Test
   void serializedFormIsCompatible() throws Exception {
      assertEquals(-598829746600398701L,
                   ObjectStreamClass.lookup(SparseMatrix.class).getSerialVersionUID());
      assertEquals(6431452410715692690L,
                   ObjectStreamClass.lookup(Class.forName("inetsoft.util.SparseMatrix$ObjectList"))
                      .getSerialVersionUID());

      SparseMatrix m = new SparseMatrix();

      for(int r = 0; r < 50; r++) {
         for(int c = 0; c < 5; c++) {
            m.set(r, c, cellValue(r, c));
         }
      }

      SparseMatrix copy = roundTrip(m);

      for(int r = 0; r < 50; r++) {
         for(int c = 0; c < 5; c++) {
            assertEquals(cellValue(r, c), copy.get(r, c));
         }
      }

      assertSame(SparseMatrix.NULL, copy.get(100, 0));
      copy.set(100, 0, "x");
      copy.set(0, 0, "y");
      assertEquals("x", copy.get(100, 0));
      assertEquals("y", copy.get(0, 0));
      assertFalse(copy.toString().isEmpty());

      copy.clear();
      assertSame(SparseMatrix.NULL, copy.get(0, 0));
      assertEquals("", copy.toString());
   }

   private static SparseMatrix newAnchoredMatrix() {
      SparseMatrix m = new SparseMatrix(3);

      for(int k = ANCHOR_FIRST; k <= ANCHOR_LAST; k++) {
         m.set(0, 3 * k, 3 * k);
      }

      return m;
   }

   private static String cellValue(int r, int c) {
      return r + "," + c;
   }

   private static SparseMatrix roundTrip(SparseMatrix m) throws Exception {
      ByteArrayOutputStream bout = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bout)) {
         out.writeObject(m);
      }

      try(ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bout.toByteArray())))
      {
         return (SparseMatrix) in.readObject();
      }
   }

   private static void joinAll(Thread writer, List<Thread> readers) throws InterruptedException {
      writer.join(30_000);
      assertFalse(writer.isAlive(), "writer did not finish");

      for(Thread reader : readers) {
         reader.join(30_000);
         assertFalse(reader.isAlive(), "reader did not finish");
      }
   }

   private static final long RUN_NANOS = TimeUnit.MILLISECONDS.toNanos(1500);
   private static final int READERS = 3;
   private static final int ANCHOR_FIRST = 600;
   private static final int ANCHOR_LAST = 1199;
}
