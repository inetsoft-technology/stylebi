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
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77468: FixedSizeSparseMatrix must never return another cell's value to a concurrent
 * reader. Every value stored in these tests encodes its own cell, so any non-NULL value that isn't
 * the cell's own is a wrong-cell read.
 */
@Tag("core")
class FixedSizeSparseMatrixConcurrencyTest {
   /**
    * Cells (0,0) and (0,3) share slot 0 of {@code new FixedSizeSparseMatrix(3)}. A writer keeps
    * replacing one with the other; readers must only get NULL or the cell's own value.
    */
   @Test
   @Timeout(60)
   void readersNeverSeeAnotherCellsValueInCollidingSlot() throws Exception {
      final FixedSizeSparseMatrix m = new FixedSizeSparseMatrix(3);
      final AtomicLong reads = new AtomicLong();
      final AtomicLong wrong = new AtomicLong();
      final ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
      final long deadline = System.nanoTime() + RUN_NANOS;

      Thread writer = new Thread(() -> {
         try {
            while(System.nanoTime() < deadline) {
               for(int i = 0; i < 1000; i++) {
                  m.set(0, 0, cellValue(0, 0));
                  m.set(0, 3, cellValue(0, 3));
               }
            }
         }
         catch(Throwable ex) {
            errors.add(ex);
         }
      }, "fixed-sparse-writer");

      List<Thread> readers = new ArrayList<>();

      for(int t = 0; t < READERS; t++) {
         final int col = t % 2 == 0 ? 0 : 3;
         final String own = cellValue(0, col);
         readers.add(new Thread(() -> {
            long n = 0;

            try {
               while(System.nanoTime() < deadline) {
                  for(int i = 0; i < 1000; i++, n++) {
                     Object val = m.get(0, col);

                     if(val != FixedSizeSparseMatrix.NULL && !own.equals(val)) {
                        wrong.incrementAndGet();
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
         }, "fixed-sparse-reader-" + t));
      }

      readers.forEach(Thread::start);
      writer.start();
      joinAll(List.of(writer), readers);

      String summary = "reads=" + reads + " wrong=" + wrong + " exceptions=" + errors.size() +
         " first=" + errors.peek();
      assertTrue(errors.isEmpty(), summary);
      assertEquals(0, wrong.get(), summary);
      assertTrue(reads.get() > 0, summary);
   }

   /**
    * The FormatTableLens/VSDataSet pattern: a default-size matrix caching a table with more cells
    * than slots, filled lock-free on a miss by several threads scanning the table.
    */
   @Test
   @Timeout(60)
   void readPathFillOverLargeTableNeverReturnsAnotherCellsValue() throws Exception {
      final FixedSizeSparseMatrix m = new FixedSizeSparseMatrix();
      final AtomicLong reads = new AtomicLong();
      final AtomicLong wrong = new AtomicLong();
      final ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
      final long deadline = System.nanoTime() + RUN_NANOS;
      List<Thread> scanners = new ArrayList<>();

      for(int t = 0; t < SCANNERS; t++) {
         final long seed = t;
         scanners.add(new Thread(() -> {
            SplittableRandom rnd = new SplittableRandom(seed);
            long n = 0;

            try {
               while(System.nanoTime() < deadline) {
                  int start = rnd.nextInt(TABLE_ROWS);

                  for(int i = 0; i < TABLE_ROWS; i++) {
                     int r = (start + i) % TABLE_ROWS;

                     for(int c = 0; c < TABLE_COLS; c++, n++) {
                        Object val = m.get(r, c);

                        if(val == FixedSizeSparseMatrix.NULL) {
                           m.set(r, c, cellValue(r, c));
                        }
                        else if(!cellValue(r, c).equals(val)) {
                           wrong.incrementAndGet();
                        }
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
         }, "fixed-sparse-scanner-" + t));
      }

      scanners.forEach(Thread::start);
      joinAll(List.of(), scanners);

      String summary = "reads=" + reads + " wrong=" + wrong + " exceptions=" + errors.size() +
         " first=" + errors.peek();
      assertTrue(errors.isEmpty(), summary);
      assertEquals(0, wrong.get(), summary);
      assertTrue(reads.get() > 0, summary);
   }

   /**
    * A cached null value is returned as null, not as NULL.
    */
   @Test
   void cachedNullIsDistinctFromMissing() {
      FixedSizeSparseMatrix m = new FixedSizeSparseMatrix();
      assertSame(FixedSizeSparseMatrix.NULL, m.get(1, 2));

      m.set(1, 2, null);
      assertNull(m.get(1, 2));
      assertSame(FixedSizeSparseMatrix.NULL, m.get(2, 1));

      m.clear();
      assertSame(FixedSizeSparseMatrix.NULL, m.get(1, 2));
   }

   /**
    * The serialized form must stay compatible with earlier builds (highlight/hyperlink lenses keep
    * these matrices in non-transient fields), even while a writer is filling the matrix. Only the
    * size is serialized, so a copy starts empty and stays usable.
    */
   @Test
   @Timeout(60)
   void serializationDuringWritesIsCompatible() throws Exception {
      assertEquals(7199357927496490854L,
                   ObjectStreamClass.lookup(FixedSizeSparseMatrix.class).getSerialVersionUID());

      final FixedSizeSparseMatrix m = new FixedSizeSparseMatrix(7);
      final ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
      final long deadline = System.nanoTime() + RUN_NANOS;

      Thread writer = new Thread(() -> {
         try {
            for(int n = 0; System.nanoTime() < deadline; n++) {
               int r = n % 2000;
               m.set(r, 0, cellValue(r, 0));
            }
         }
         catch(Throwable ex) {
            errors.add(ex);
         }
      }, "fixed-sparse-ser-writer");

      writer.start();
      long copies = 0;

      try {
         while(System.nanoTime() < deadline) {
            FixedSizeSparseMatrix copy = roundTrip(m);
            copies++;

            for(int r = 0; r < 14; r++) {
               assertSame(FixedSizeSparseMatrix.NULL, copy.get(r, 0));
            }

            // size 7 is retained, so cells 7 apart collide and replace each other
            copy.set(0, 0, "a");
            assertEquals("a", copy.get(0, 0));
            copy.set(0, 7, "b");
            assertEquals("b", copy.get(0, 7));
            assertSame(FixedSizeSparseMatrix.NULL, copy.get(0, 0));
         }
      }
      catch(Throwable ex) {
         errors.add(ex);
      }

      joinAll(List.of(writer), List.of());
      assertTrue(errors.isEmpty(), "exceptions after " + copies + " copies: " + errors.peek());
      assertTrue(copies > 0);
   }

   private static String cellValue(int r, int c) {
      return r + "," + c;
   }

   private static FixedSizeSparseMatrix roundTrip(FixedSizeSparseMatrix m) throws Exception {
      ByteArrayOutputStream bout = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bout)) {
         out.writeObject(m);
      }

      try(ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bout.toByteArray())))
      {
         return (FixedSizeSparseMatrix) in.readObject();
      }
   }

   private static void joinAll(List<Thread> writers, List<Thread> readers)
      throws InterruptedException
   {
      for(Thread writer : writers) {
         writer.join(30_000);
         assertFalse(writer.isAlive(), "writer did not finish");
      }

      for(Thread reader : readers) {
         reader.join(30_000);
         assertFalse(reader.isAlive(), "reader did not finish");
      }
   }

   private static final long RUN_NANOS = TimeUnit.MILLISECONDS.toNanos(1500);
   private static final int READERS = 3;
   private static final int SCANNERS = 4;
   private static final int TABLE_ROWS = 1000;
   private static final int TABLE_COLS = 10;
}
