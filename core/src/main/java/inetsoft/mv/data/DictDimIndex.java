/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.mv.data;

import inetsoft.mv.util.SeekableInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * Dictionary encoded index. The directionary encoding is ideal for dimension
 * with high cardinality.
 * <br>
 * This implementation makes the following assumptions:
 *  1. addKey is called on consecutive rows with increasing order starting from 0
 *  2. The keys are in sorted order as in the real value. In another word, if
 *     key2 > key1, then value2 > value1. This is true since the dim dictionary
 *     is sorted.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public class DictDimIndex extends XDimIndex {
   /**
    * Create an instance of XDimIndex.
    * @param bits number of bits used for each key.
    */
   public DictDimIndex(int bits) {
      super();

      if(bits <= 32 && bits > 21) {
         dimbuf = new IntIntBuf(this);
      }
      else if(bits <= 16 && bits > 12) {
         dimbuf = new ShortIntBuf(this);
      }
      else if(bits <= 8 && bits > 6) {
         dimbuf = new ByteIntBuf(this);
      }
      else {
         dimbuf = new PackedIntBuf(bits, this);
      }
   }
  
   /**
    * Associate a row with an index key.
    */
   @Override
   public void addKey(int key, int row) {
      // we assume the keys are added in increasing order from 0
      dimbuf.addKey(key, row);
   }

   /**
    * Sort keys.
    */
   @Override
   public void complete() {
      dimbuf.complete();
      super.complete();
   }

   /**
    * Get the number of bits per value.
    */
   public int getBits() {
      return dimbuf.getBits();
   }

   /**
    * Get the bit set for the specified operation and values.
    */
   @Override
   public BitSet getRows(String op, long[] val, boolean not, boolean cnull) {
      if("IN".equals(op)) {
         return getRows(val, not, cnull);
      }

      return super.getRows(op, val, not, cnull);
   }

   /**
    * Get the bit set at the specified index.
    */
   @Override
   public BitSet getRows(long idx, boolean cnull) {
      return getRows(new long[] {idx}, false, cnull);
   }

   /**
    * Get rows matching one of the value ('in' condition).
    * @param vals values to match.
    * @param not negate the condition.
    */
   protected BitSet getRows(long[] vals, boolean not, boolean cnull) {
      BitSet vmask = new BitSet();
      int min = Integer.MAX_VALUE;

      // loop to find the min value in vals
      for(int i = 0; i < vals.length; i++) {
         min = min <= vals[i] ? min : ((int) vals[i]);
      }

      // each bit is a matching value
      for(int i = 0; i < vals.length; i++) {
         vmask.set((int) vals[i] - min);

         // if the value contains null for comparison (e.g. is null), don't
         // ignore null in the comparison later
         if(cnull && vals[i] == 0) {
            cnull = false;
         }
      }

      return dimbuf.getRows(vmask, min, not, cnull);
   }

   /**
    * Get the bit set for the specified range.
    */
   @Override
   public BitSet getRows(long from, boolean fincluded, long to,
                         boolean tincluded, boolean cnull)
   {
      to = to == Integer.MIN_VALUE ? Integer.MAX_VALUE : to;

      if(from == Integer.MIN_VALUE) {
         from = cnull ? 1 : 0;
      }

      return dimbuf.getRows(from, fincluded, to, tincluded, cnull);
   }

   /**
    * Get a bit set for all the rows in the index.
    */
   @Override
   public BitSet getAllRows(boolean cnull) {
      BitSet bitset = new BitSet();
      bitset.set(cnull ? 1 : 0, dimbuf.getSize());
      return bitset;
   }

   /**
    * Read from channel.
    */
   @Override
   protected void read0(SeekableInputStream channel) throws Exception {
      dimbuf.read(channel);
   }

   /**
    * Write to channel.
    */
   @Override
   public ByteBuffer write(WritableByteChannel channel, ByteBuffer sbuf)
      throws IOException
   {
      return dimbuf.write(channel, sbuf);
   }

   /**
    * Get the data length of this dimension index.
    */
   @Override
   public int getLength() {
      return dimbuf.getLength();
   }

   /**
    * Get header length.
    */
   @Override
   public int getHeaderLength() {
      return dimbuf.getHeaderLength();
   }

   /**
    * Called after this index is swapped to file. Clear the in-memory data now.
    */
   @Override
   protected void swapped() {
      dimbuf.invalidate();
   }

   protected IntBuf dimbuf; // dim values

   private static final Logger LOG = LoggerFactory.getLogger(DictDimIndex.class);
}
