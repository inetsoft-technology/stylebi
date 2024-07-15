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
package inetsoft.mv.data;

import inetsoft.mv.util.SeekableInputStream;
import inetsoft.util.swap.XSwapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * Storage for an array of integers.
 *
 * @author InetSoft Technology
 * @version 12.2
 */
public class PackedIntBuf extends IntBuf {
   /**
    * Create an int array.
    * @param bits number of bits used for each key.
    */
   public PackedIntBuf(int bits, XDimIndex parent) {
      this.bits = bits;
      nkeys = 0;
      dimbuf = new long[256];
      this.dimIdx = parent;
      initMask();
   }

   /**
    * Initialize the mask.
    */
   private void initMask() {
      per = 64 / bits;
      mask = 0;
      perInverse = 1.0 / per;

      if(per == 0) {
         LOG.warn("Materialized view dimension column cardinality overflow: " +
                     bits);
      }

      for(int i = 0; i < bits; i++) {
         mask = (mask << 1) | 1;
      }

      for(int i = 0; i < bitsLookup.length; i++) {
         bitsLookup[i] = i * bits;
      }
   }

   @Override
   protected void changeSize(int nkeys) {
      int len = nkeys / per + 1;
      long[] dimbuf2 = new long[len];
      System.arraycopy(dimbuf, 0, dimbuf2, 0, Math.min(dimbuf.length, len));
      dimbuf = dimbuf2;
   }

   /**
    * Set the value at the specified index.
    */
   @Override
   public void setValue(int idx, int value) {
      int idx0 = idx / per;
      int subidx = idx % per;
      dimbuf[idx0] |= ((long) value & mask) << (subidx * bits);
   }

   /**
    * Get the value at the specified index.
    */
   @Override
   public int getValue(int idx) {
      long[] dimbuf = accessDimBuf();

      // optimization, multiplication is faster than division/mod
      //int idx0 = idx / per;
      //int subidx = idx % per;
      int idx0 = (int) (idx * perInverse);
      int subidx = idx - idx0 * per;

      // optimization, use lookup take to avoid multiplication
      //return (int) ((dimbuf[idx0] >>> (subidx * bits)) & mask);
      return (int) ((dimbuf[idx0] >>> bitsLookup[subidx]) & mask);
   }

   /**
    * Get the number of bits per value.
    */
   @Override
   public int getBits() {
      return bits;
   }

   /**
    * Associate a row with an index key.
    */
   @Override
   public void addKey(int key, int row) {
      // we assume the keys are added in increasing order from 0
      int idx = nkeys / per;
      int subidx = nkeys % per;

      if(idx >= dimbuf.length) {
         long[] buf2 = new long[dimbuf.length + Math.min(4092, dimbuf.length * 2)];
         System.arraycopy(dimbuf, 0, buf2, 0, dimbuf.length);
         dimbuf = buf2;
      }

      dimbuf[idx] |= ((long) key & mask) << (subidx * bits);
      nkeys++;
   }

   /**
    * Get rows matching one of the value ('in' condition) in vmask.
    * @param vmask values to match.
    * @param not negate the condition.
    */
   @Override
   public BitSet getRows(BitSet vmask, int min, boolean not, boolean cnull) {
      long[] dimbuf = accessDimBuf();
      BitSet rows = new BitSet();

      for(int i = 0, r = 0; i < dimbuf.length; i++) {
         long dimidx = dimbuf[i];

         for(int j = 0; j < per && r < nkeys; j++, r++) {
            long val = dimidx & mask;
            boolean match = vmask.get((int) (val - min));

            if(!not && match || not && !match) {
               if(not && cnull && val == 0) {
                  // if the value is null, the comparison should be false
                  // this is consistent with sql
               }
               else {
                  rows.add(r);
               }
            }

            dimidx = dimidx >>> bits;
         }
      }

      rows.complete();

      return rows;
   }

   /**
    * Get the bit set for the specified range.
    */
   @Override
   public BitSet getRows(long from, boolean fincluded, long to,
                         boolean tincluded, boolean cnull)
   {
      long[] dimbuf = accessDimBuf();
      BitSet rows = new BitSet();

      for(int i = 0, r = 0; i < dimbuf.length; i++) {
         long dimidx = dimbuf[i];

         for(int j = 0; j < per && r < nkeys; j++, r++) {
            long val = dimidx & mask;

            if((fincluded && val >= from || !fincluded && val > from) &&
               (tincluded && val <= to || !tincluded && val < to))
            {
               rows.add(r);
            }

            dimidx = dimidx >>> bits;
         }
      }

      rows.complete();

      return rows;
   }

   @Override
   public int capacity() {
      long[] dimbuf = this.dimbuf;
      return dimbuf != null ? dimbuf.length : size;
   }

   @Override
   protected void allocateBuffer(int size) {
      dimbuf = new long[size];
   }

   @Override
   protected void copyFromBuffer(ByteBuffer buf) {
      buf.asLongBuffer().get(dimbuf);
   }

   @Override
   protected void copyToBuffer(ByteBuffer buf) {
      buf.asLongBuffer().put(accessDimBuf());
   }

   /**
    * Read from channel.
    */
   @Override
   public void read(SeekableInputStream channel) throws Exception {
      ByteBuffer buf = ByteBuffer.allocate(getHeaderLength());
      channel.readFully(buf);
      XSwapUtil.flip(buf);

      int len = buf.getInt();
      buf.get(); // skip compressed
      nkeys = buf.getInt();
      bits = buf.getInt();
      initMask();

      int size = buf.getInt();
      dimbuf = new long[size];

      buf = channel.map(channel.position(), len);
      ByteBuffer buf2 = XSwapUtil.uncompressByteBuffer(buf);

      buf2.asLongBuffer().get(dimbuf);
      channel.unmap(buf);
   }

   /**
    * Write to channel.
    */
   @Override
   public ByteBuffer write(WritableByteChannel channel, ByteBuffer sbuf)
      throws IOException
   {
      accessDimBuf();
      int len = dimbuf.length * 8;
      ByteBuffer buf = null;

      if(sbuf == null || sbuf.capacity() < len) {
         buf = ByteBuffer.allocate(len);
      }
      else {
         buf = sbuf;
         XSwapUtil.clear(buf);
      }

      buf.asLongBuffer().put(dimbuf);
      XSwapUtil.position(buf, buf.position() + len);
      XSwapUtil.flip(buf);
      ByteBuffer buf2 = XSwapUtil.compressByteBuffer(buf);

      ByteBuffer headBuf = ByteBuffer.allocate(getHeaderLength());
      headBuf.putInt(buf2.remaining());
      headBuf.put((byte) 1); // compressed
      headBuf.putInt(nkeys);
      headBuf.putInt(bits);
      headBuf.putInt(dimbuf.length);
      XSwapUtil.flip(headBuf);

      while(headBuf.hasRemaining()) {
         channel.write(headBuf);
      }

      while(buf2.hasRemaining()) {
         channel.write(buf2);
      }

      return buf;
   }

   @Override
   public int getHeaderLength() {
      return 17;
   }

   /**
    * Get the data length of this dimension index.
    */
   @Override
   public int getLength() {
      return (int) Math.ceil(nkeys / (double) per) * 8 + getHeaderLength();
   }

   /**
    * Clear out the memory and use XDimIndex to restore later when necessary.
    */
   @Override
   public synchronized void invalidate() {
      long[] dimbuf = this.dimbuf;

      if(dimbuf != null) {
         size = dimbuf.length;
      }

      this.dimbuf = null;
   }

   @Override
   public void validate() {
      accessDimBuf();
   }

   /**
    * Get the buffer. Swap in if necessary.
    */
   private long[] accessDimBuf() {
      if(dimIdx != null) {
         dimIdx.touch();
      }

      long[] dimbuf = this.dimbuf;

      if(dimbuf != null) {
         return dimbuf;
      }

      synchronized(this) {
         dimbuf = this.dimbuf;

         if(dimbuf != null) {
            return dimbuf;
         }

         dimIdx.access();
         dimbuf = this.dimbuf;
      }

      return dimbuf;
   }

   private long[] dimbuf; // buffer for the dim values
   private int bits; // number of bits for each key
   private long mask; // bit mask with number of bits
   private int per; // number of keys per item
   // optimization structure
   private double perInverse = 1; // 1 / per
   private int[] bitsLookup = new int[64]; // bits * n
   private transient XDimIndex dimIdx;
   private transient int size;

   private static final Logger LOG = LoggerFactory.getLogger(PackedIntBuf.class);
}
