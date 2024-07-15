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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * Storage for an array of integers.
 *
 * @author InetSoft Technology
 * @version 12.2
 */
public class ByteIntBuf extends IntBuf {
   /**
    * Create an int array.
    */
   public ByteIntBuf(XDimIndex parent) {
      nkeys = 0;
      dimbuf = new byte[256];
      this.dimIdx = parent;
   }

   @Override
   protected void changeSize(int nkeys) {
      byte[] dimbuf2 = new byte[nkeys];
      System.arraycopy(dimbuf, 0, dimbuf2, 0, Math.min(dimbuf.length, nkeys));
      dimbuf = dimbuf2;
   }

   /**
    * Set the value at the specified index.
    */
   @Override
   public void setValue(int idx, int value) {
      dimbuf[idx] = (byte) value;
   }

   /**
    * Get the value at the specified index.
    */
   @Override
   public int getValue(int idx) {
      byte[] dimbuf = accessDimBuf();
      return dimbuf[idx];
   }

   /**
    * Get the number of bits per value.
    */
   @Override
   public int getBits() {
      return 8;
   }

   /**
    * Get rows matching one of the value ('in' condition) in vmask.
    * @param not negate the condition.
    */
   @Override
   public BitSet getRows(BitSet vmask, int min, boolean not, boolean cnull) {
      byte[] dimbuf = accessDimBuf();
      BitSet rows = new BitSet();

      for(int r = 0; r < dimbuf.length; r++) {
         byte val = dimbuf[r];
         boolean match = vmask.get(val - min);

         if(!not && match || not && !match) {
            if(not && cnull && val == 0) {
               // if the value is null, the comparison should be false
               // this is consistent with sql
            }
            else {
               rows.add(r);
            }
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
      byte[] dimbuf = accessDimBuf();
      BitSet rows = new BitSet();

      for(int r = 0; r < dimbuf.length; r++) {
         byte val = dimbuf[r];

         if((fincluded && val >= from || !fincluded && val > from) &&
            (tincluded && val <= to || !tincluded && val < to))
         {
            rows.add(r);
         }
      }

      rows.complete();

      return rows;
   }

   @Override
   protected void allocateBuffer(int size) {
      dimbuf = new byte[size];
   }

   @Override
   protected void copyFromBuffer(ByteBuffer buf) {
      buf.get(dimbuf);
   }

   @Override
   protected void copyToBuffer(ByteBuffer buf) {
      buf.put(accessDimBuf());
   }

   @Override
   public int getHeaderLength() {
      return 13;
   }

   /**
    * Get the size of the internal buffer.
    */
   @Override
   public int capacity() {
      byte[] dimbuf = this.dimbuf;
      return dimbuf != null ? dimbuf.length : size;
   }

   /**
    * Clear out the memory and use XDimIndex to restore later when necessary.
    */
   @Override
   public synchronized void invalidate() {
      byte[] dimbuf = this.dimbuf;

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
   private byte[] accessDimBuf() {
      if(dimIdx != null) {
         dimIdx.touch();
      }

      byte[] dimbuf = this.dimbuf;

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

   private byte[] dimbuf; // buffer for the dim values
   private int nkeys; // number of keys stored here
   private transient XDimIndex dimIdx;
   private transient int size;

   private static final Logger LOG = LoggerFactory.getLogger(ByteIntBuf.class);
}
