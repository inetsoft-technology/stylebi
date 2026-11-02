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
package inetsoft.mv.data;

import inetsoft.mv.util.SeekableInputStream;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79401: the range cache of {@link BitDimIndex} must not hand out an entry whose result
 * has not been computed yet (concurrent identical range), and must not keep one when the
 * computation fails.
 *
 * <p>The index is re-opened over an in-memory channel so the first {@code getRows} swaps the
 * data in through {@code channel.position()}, where a latch holds the computation open (or
 * fails it) deterministically.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class BitDimIndexRangeCacheTest {
   @Test
   void serialIdenticalRangesControl() throws Exception {
      BitDimIndex idx = swappedIn(new Gate(image()));
      BitSet r1 = between(idx, 2, 4);
      BitSet r2 = between(idx, 2, 4);
      assertEquals(EXPECTED, r1.toString());
      assertEquals(EXPECTED, r2.toString());
   }

   @Test
   void concurrentIdenticalRangeWaitsForResult() throws Exception {
      Gate gate = new Gate(image());
      BitDimIndex idx = swappedIn(gate);
      gate.mode = 1;
      AtomicReference<BitSet> ares = new AtomicReference<>();
      Thread ta = new Thread(() -> ares.set(between(idx, 2, 4)), "range-A");
      ta.start();

      try {
         assertTrue(gate.parked.await(10, TimeUnit.SECONDS));

         // B runs while A is still computing the same range
         AtomicReference<BitSet> bres = new AtomicReference<>();
         AtomicReference<BitSet> braw = new AtomicReference<>();
         Thread tb = new Thread(() -> {
            bres.set(between(idx, 2, 4));
            braw.set(idx.getRows(2, true, 4, true, false));
         }, "range-B");
         tb.start();
         tb.join(1000);

         gate.release.countDown();
         tb.join(10000);
         ta.join(10000);

         assertNotNull(bres.get());
         assertNotNull(braw.get(), "raw range lookup must never return null");
         assertEquals(EXPECTED, ares.get().toString());
         assertEquals(EXPECTED, bres.get().toString());
         assertEquals(EXPECTED, braw.get().toString());
      }
      finally {
         gate.release.countDown();
         ta.join(10000);
      }
   }

   @Test
   void failedComputationLeavesNoCachedEntry() throws Exception {
      Gate gate = new Gate(image());
      BitDimIndex idx = swappedIn(gate);
      gate.mode = 2;

      assertThrows(Throwable.class, () -> between(idx, 2, 4));

      // the channel is healthy now: the identical range must be computed, not answered by a stub
      BitSet again = between(idx, 2, 4);
      BitSet raw = idx.getRows(2, true, 4, true, false);
      assertEquals(EXPECTED, again.toString());
      assertNotNull(raw);
      assertEquals(EXPECTED, raw.toString());
   }

   @Test
   void cacheStillServesRepeatedRangesAfterEviction() throws Exception {
      BitDimIndex idx = swappedIn(new Gate(image()));
      assertEquals(EXPECTED, between(idx, 2, 4).toString());

      for(int k = 0; k < 15; k++) {
         assertNotNull(idx.getRows(k % 10, true, k % 10, true, false));
      }

      assertEquals(EXPECTED, between(idx, 2, 4).toString());
      assertEquals(EXPECTED, between(idx, 2, 4).toString());
   }

   private static BitSet between(BitDimIndex idx, long a, long b) {
      return idx.getRows("BETWEEN", new long[] { a, b }, false, false);
   }

   // keys 0..9, key k owns rows {k, k+10}
   private static byte[] image() throws Exception {
      BitDimIndex src = new BitDimIndex();

      for(int k = 0; k < 10; k++) {
         src.addKey(k, k);
         src.addKey(k, k + 10);
      }

      src.complete();
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      src.write(Channels.newChannel(bos), null);
      return bos.toByteArray();
   }

   private static BitDimIndex swappedIn(Gate gate) throws Exception {
      BitDimIndex idx = new BitDimIndex();
      idx.init(gate, 0, null, false);
      return idx;
   }

   private static final String EXPECTED = rowsOf(2, 3, 4, 12, 13, 14);

   private static String rowsOf(int... rows) {
      BitSet set = new BitSet();

      for(int row : rows) {
         set.set(row);
      }

      return set.toString();
   }

   /**
    * In-memory channel whose {@code position(long)} parks once (mode 1) or throws on the next
    * two calls (mode 2, touch() and accessKeyRow() each swap in).
    */
   private static final class Gate implements SeekableInputStream {
      Gate(byte[] data) {
         this.data = data;
      }

      @Override
      public SeekableInputStream position(long p) throws IOException {
         int m = mode;

         if(m == 1) {
            mode = 0;
            parked.countDown();

            try {
               release.await();
            }
            catch(InterruptedException e) {
               throw new IOException(e);
            }
         }
         else if(m == 2) {
            if(++thrown >= 2) {
               mode = 0;
            }

            throw new IOException("injected swap-in read failure");
         }

         pos = (int) p;
         return this;
      }

      @Override
      public long position() {
         return pos;
      }

      @Override
      public long size() {
         return data.length;
      }

      @Override
      public int read(ByteBuffer b) {
         if(pos >= data.length) {
            return -1;
         }

         int n = Math.min(b.remaining(), data.length - pos);
         b.put(data, pos, n);
         pos += n;
         return n;
      }

      @Override
      public boolean isOpen() {
         return true;
      }

      @Override
      public void close() {
      }

      @Override
      public ByteBuffer map(long p, long size) {
         return ByteBuffer.wrap(data, (int) p, (int) size).slice();
      }

      @Override
      public void unmap(ByteBuffer b) {
      }

      @Override
      public SeekableInputStream reopen() {
         return this;
      }

      @Override
      public long getModificationTime() {
         return 0;
      }

      @Override
      public Object getFilePath() {
         return null;
      }

      private final byte[] data;
      private int pos;
      private int thrown;
      volatile int mode; // 0 pass, 1 park once, 2 throw twice
      final CountDownLatch parked = new CountDownLatch(1);
      final CountDownLatch release = new CountDownLatch(1);
   }
}
