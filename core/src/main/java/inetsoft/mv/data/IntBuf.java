/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.mv.data;

import inetsoft.mv.util.SeekableInputStream;
import inetsoft.util.swap.XSwapUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * Storage for an array of integers.
 *
 * @author InetSoft Technology
 * @version 12.2
 */
public abstract class IntBuf {
   /**
    * Set the value at the specified index.
    */
   public abstract void setValue(int idx, int value);

   /**
    * Get the value at the specified index.
    */
   public abstract int getValue(int idx);

   /**
    * Get the number of bits per value.
    */
   public abstract int getBits();

   /**
    * Get rows matching one of the value ('in' condition) in vmask.
    * @param not negate the condition.
    */
   public abstract BitSet getRows(BitSet vmask, int min, boolean not, boolean cnull);

   /**
    * Get the bit set for the specified range.
    */
   public abstract BitSet getRows(long from, boolean fincluded, long to,
                                  boolean tincluded, boolean cnull);

   /**
    * Get the size of the buffer header.
    */
   public abstract int getHeaderLength();

   /**
    * Get the size of the internal buffer.
    */
   public abstract int capacity();

   /**
    * Clear out the memory and use XDimIndex to restore later when necessary.
    */
   public abstract void invalidate();

   /**
    * Make sure the buffer is loaded in memory.
    */
   public abstract void validate();

   /**
    * Change the internal buffer size.
    */
   protected abstract void changeSize(int nkeys);

   /**
    * Allocate internal buffer of specified size.
    */
   protected abstract void allocateBuffer(int size);

   /**
    * Copy data from byte buffer to internal buffer.
    */
   protected abstract void copyFromBuffer(ByteBuffer buf);

   /**
    * Copy data to byte buffer from internal buffer.
    */
   protected abstract void copyToBuffer(ByteBuffer buf);

   /**
    * Set the number of items in the array.
    * @param create true to create the array.
    */
   public void setSize(int nkeys, boolean create) {
      this.nkeys = nkeys;

      if(create) {
         changeSize(nkeys);
      }
      else {
         invalidate(); // dimbuf = null
      }
   }

   /**
    * Get the number of items in the array.
    */
   public int getSize() {
      return nkeys;
   }

   /**
    * Called when all keys have been added.
    */
   public void complete() {
      // shrink
      changeSize(nkeys);
   }

   /**
    * Associate a row with an index key.
    */
   public void addKey(int key, int idx) {
      int capacity = capacity();

      if(idx >= capacity) {
         changeSize(capacity + Math.min(4092, capacity * 2));
      }

      setValue(idx, key);
      nkeys = Math.max(idx + 1, nkeys);
   }

   /**
    * Read from channel.
    */
   public void read(SeekableInputStream channel) throws Exception {
      ByteBuffer buf = ByteBuffer.allocate(getHeaderLength());
      channel.readFully(buf);
      XSwapUtil.flip(buf);

      int len = buf.getInt();
      buf.get(); // skip compressed
      nkeys = buf.getInt();

      int size = buf.getInt();
      allocateBuffer(size);

      buf = channel.map(channel.position(), len);
      ByteBuffer buf2 = XSwapUtil.uncompressByteBuffer(buf);

      copyFromBuffer(buf2);
      channel.unmap(buf);
   }

   /**
    * Write to channel.
    */
   public ByteBuffer write(WritableByteChannel channel, ByteBuffer sbuf)
      throws IOException
   {
      validate(); // force dimbuf to be loaded or capacity() == 0 (incremental)

      int len = (int) Math.ceil(capacity() * ((double) getBits()) / 8.0);
      ByteBuffer buf;

      if(sbuf == null || sbuf.capacity() < len) {
         buf = ByteBuffer.allocate(len);
      }
      else {
         buf = sbuf;
         XSwapUtil.clear(buf);
      }

      int opos = buf.position();
      copyToBuffer(buf);
      XSwapUtil.position(buf, opos + len);
      XSwapUtil.flip(buf);
      ByteBuffer buf2 = XSwapUtil.compressByteBuffer(buf);

      ByteBuffer headBuf = ByteBuffer.allocate(getHeaderLength());
      headBuf.putInt(buf2.remaining());
      headBuf.put((byte) 1); // compressed
      headBuf.putInt(nkeys);
      headBuf.putInt(capacity());
      XSwapUtil.flip(headBuf);

      while(headBuf.hasRemaining()) {
         channel.write(headBuf);
      }

      while(buf2.hasRemaining()) {
         channel.write(buf2);
      }

      return buf;
   }

   /**
    * Get the data length of this dimension index.
    */
   public int getLength() {
      return nkeys * getBits() / 8 + getHeaderLength();
   }

   protected int nkeys; // number of keys stored here
}
