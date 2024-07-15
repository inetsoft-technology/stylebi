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

import inetsoft.mv.fs.BlockFile;
import inetsoft.mv.fs.internal.CacheBlockFile;
import inetsoft.mv.util.*;
import inetsoft.util.swap.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The base class for double and float measure column.
 *
 * @author InetSoft Technology
 * @version 12.0
 */
public abstract class MVDecimalColumn extends AbstractMeasureColumn {
   /**
    * Measure column for loading the data on demand.
    * @param newbuf true if the buffer is new (empty).
    */
   public MVDecimalColumn(SeekableInputStream channel, long fpos,
                          BlockFile file, int size, boolean newbuf)
   {
      super(channel, fpos, file, size);
      createFragments(size, !newbuf);
   }

   /**
    * Get the column value as a dimention.
    */
   @Override
   public final long getDimValue(int idx) {
      double val = getValue(idx);
      return Double.doubleToLongBits(val);
   }

   /**
    * Get the value at the specified index.
    */
   @Override
   public double getValue(int r) {
      int index = r >>> BLOCK_BITS;
      r = r & BLOCK_MASK;
      return fragments[index].getValue(r);
   }

   /**
    * Set the value at the specified index.
    */
   @Override
   public void setValue(int r, double value) {
      int index = r >>> BLOCK_BITS;
      r = r & BLOCK_MASK;
      fragments[index].setValue(r, value);

      if(r == BLOCK_SIZE - 1) {
         fragments[index].complete();
      }
   }

   /**
    * Read cache buffer.
    */
   protected synchronized ByteBuffer readBlock(int index) {
      ByteBuffer rowBuffer = null;

      try {
         SeekableInputStream channel = this.channel;

         if((channel == null || !channel.isOpen()) && file != null) {
            channel = file.openInputStream();
         }
         else if(channel != null && !channel.isOpen()) {
            channel = this.channel.reopen();
         }

         headerLock.lock();

         try {
            if(headBuf == null) {
               headBuf = readHeaderBuf(channel);
               XSwapUtil.flip(headBuf);
            }
         }
         finally {
            headerLock.unlock();
         }

         XSwapUtil.position(headBuf, 5 + 12 * index);
         long offset = headBuf.getLong();
         int len = headBuf.getInt();

         ByteBuffer buf = channel.map(offset + fpos, len);

         if(channel != this.channel) {
            channel.close();
         }

         rowBuffer = buf;

         if(isCompressed()) {
            rowBuffer = XSwapUtil.uncompressByteBuffer(buf);
         }

         channel.unmap(buf);
      }
      catch(Exception ex) {
         LOG.error("Failed to read data block", ex);
      }

      return rowBuffer;
   }

   /**
    * Write to channel.
    */
   @Override
   public ByteBuffer write(WritableByteChannel channel, ByteBuffer sbuf)
         throws IOException
   {
      ByteBuffer headBuf = createHeaderBuf();
      int totalLen = 0;
      long offset = getHeaderLength();
      ByteBuffer buf = acquireByteBuffer(sbuf);

      XSwapUtil.flip(headBuf);
      XSwapUtil.position(headBuf, 5);

      // write header
      for(int i = 0; fragments != null && i < fragments.length; i++) {
         ByteBuffer buf2 = fragments[i].copyToBuffer(buf);
         int sizeInBytes = buf2.remaining();

         // block offset
         headBuf.putLong(offset);
         // block length
         headBuf.putInt(sizeInBytes);
         totalLen += sizeInBytes;
         offset += sizeInBytes;
      }

      // to write total length
      XSwapUtil.position(headBuf, 0);
      headBuf.putInt(totalLen);
      XSwapUtil.position(headBuf, 0);

      // write header
      while(headBuf.hasRemaining()) {
         channel.write(headBuf);
      }

      this.headBuf = null;

      // write fragments
      for(int i = 0; fragments != null && i < fragments.length; i++) {
         ByteBuffer buf2 = fragments[i].copyToBuffer(buf);

         while(buf2.hasRemaining()) {
            channel.write(buf2);
         }
      }

      return buf;
   }

   /**
    * Get a buffer for a block.
    */
   private ByteBuffer acquireByteBuffer(ByteBuffer sbuf) {
      ByteBuffer buf = null;
      // block length
      int bLen = BLOCK_SIZE * bytesPer();

      if(sbuf == null || sbuf.capacity() < bLen) {
         buf = ByteBuffer.allocate(bLen);
      }
      else {
         buf = sbuf;
         XSwapUtil.clear(buf);
      }

      return buf;
   }

   protected ByteBuffer createHeaderBuf() {
      ByteBuffer headBuf = ByteBuffer.allocate(getHeaderLength());
      headBuf.putInt(-1); // mark position, total length
      headBuf.put((byte) (isCompressed() ? 1 : 0));

      for(int i = 0; fragments != null && i < fragments.length; i++) {
         headBuf.putLong(-1L); // mark not writen
         headBuf.putInt(-1); // mark not writen
      }

      return headBuf;
   }

   private ByteBuffer readHeaderBuf(SeekableChannel channel) throws IOException {
      ByteBuffer headBuf;
      long opos = channel.position();

      if(channel instanceof FileSeekableInputStream &&
         channel.size() < fpos + getHeaderLength())
      {
         headBuf = createHeaderBuf();
         FileChannel fileChannel = ((FileSeekableInputStream)channel).getFileChannel();
         fileChannel.position(fpos);
         XSwapUtil.flip(headBuf);

         while(headBuf.hasRemaining()) {
            fileChannel.write(headBuf);
         }
      }
      else {
         headBuf = ByteBuffer.allocate(getHeaderLength());

         synchronized(channel) {
            channel.position(fpos);

            if(channel instanceof SeekableInputStream) {
               ((SeekableInputStream) channel).readFully(headBuf);
            }
            else {
               while(headBuf.remaining() > 0) {
                  int len = channel.read(headBuf);

                  if(len < 0) {
                     break;
                  }
               }
            }
         }
      }

      channel.position(opos);
      return headBuf;
   }

   /**
    * Get header length.
    */
   @Override
   public int getHeaderLength() {
      int len = super.getHeaderLength();

      if(fragments != null) {
         len += 12 * fragments.length;
      }

      return len;
   }

   /**
    * Number of bytes per value.
    */
   @Override
   protected abstract int bytesPer();

   /**
    * Read from channel.
    */
   @Override
   protected void read0(SeekableInputStream channel) throws Exception {
      // do nothing
   }

   protected void createFragments(int size, boolean swappable) {
      int n = size >>> BLOCK_BITS;
      int remain = size & BLOCK_MASK;
      int add = remain == 0 ? 0 : 1;
      fragments = new Fragment[n + add];

      for(int i = 0; i < fragments.length; i++) {
         int fragmentSize = (i == n && add > 0) ? remain : BLOCK_SIZE;
         fragments[i] = createFragment(i, fragmentSize, !swappable);

         if(swappable) {
            fragments[i].complete();
         }
      }
   }

   /**
    * Set the number of rows (items) in the column.
    */
   @Override
   public void setRowCount(int size) {
      super.setRowCount(size);

      if(fragments == null) {
         return;
      }

      int remain = size & BLOCK_MASK;
      int add = remain == 0 ? 0 : 1;
      int nfragments = (size >>> BLOCK_BITS) + add;

      if(nfragments < fragments.length) {
         Fragment[] fragments2 = new Fragment[nfragments];
         System.arraycopy(fragments, 0, fragments2, 0, nfragments);
         fragments = fragments2;
      }

      if(remain > 0 && fragments[fragments.length - 1] != null) {
         fragments[fragments.length - 1].setSize(remain);
      }
   }

   /**
    * Create a column fragment.
    */
   protected abstract Fragment createFragment(int index, int size, boolean newbuf);

   protected abstract class Fragment extends XSwappable {
      public Fragment(int index, int size, boolean newbuf) {
         super();

         this.index = index;
         this.size = size;
         this.newbuf = newbuf;
         XSwapper.cur = System.currentTimeMillis();
         this.iaccessed = XSwapper.cur;
      }

      public abstract ByteBuffer copyToBuffer(ByteBuffer buf);

      public abstract double getValue(int index);

      public abstract void setValue(int index, double val);

      public int getSize() {
         return size;
      }

      public void setSize(int size) {
         this.size = size;
         resizeTo(size);
      }

      // resize buffer to new size
      protected abstract void resizeTo(int size);

      @Override
      public double getSwapPriority() {
         if(!isValid()) {
            return 0;
         }

         return getAgePriority(XSwapper.cur - iaccessed, alive * 2);
      }

      @Override
      public boolean isCompleted() {
         return true;
      }

      @Override
      public boolean isSwappable() {
         return true;
      }

      @Override
      public synchronized boolean swap() {
         if(newbuf && isValid()) {
            boolean needsInit = file == null;

            if(file == null) {
               file = new CacheBlockFile(prefix + "_" + index + EXTENSION);
               fpos = 0;
            }

            try(TransactionChannel channel = file.openWriteChannel()) {
               ByteBuffer buf = ByteBuffer.allocate(BLOCK_SIZE * bytesPer());
               ByteBuffer buf2 = copyToBuffer(buf);
               headerLock.lock();

               // update header, should synchronized it
               try {
                  ByteBuffer headBuf;

                  if(needsInit) {
                     headBuf = createHeaderBuf();
                     XSwapUtil.flip(headBuf);

                     while(headBuf.hasRemaining()) {
                        channel.write(headBuf);
                     }
                  }
                  else {
                     headBuf = readHeaderBuf(channel); // get header
                  }

                  XSwapUtil.flip(headBuf);
                  headBuf.getInt(); // totalL
                  headBuf.get(); // compressed tag

                  long offset = getHeaderLength();

                  // find next write position
                  for(int i = 0; i < fragments.length; i++) {
                     long offset1 = headBuf.getLong();
                     int len1 = headBuf.getInt();

                     if(offset1 == -1L || len1 == -1) {
                        continue;
                     }

                     offset = Math.max(offset1 + len1, offset);
                  }

                  XSwapUtil.position(headBuf, 0);
                  // totalLen (without header)
                  headBuf.putInt((int) (offset + buf2.remaining() - getHeaderLength()));
                  XSwapUtil.position(headBuf, 5 + 12 * index); // update header tag
                  headBuf.putLong(offset);
                  headBuf.putInt(buf2.remaining());
                  XSwapUtil.flip(headBuf);
                  MVDecimalColumn.this.headBuf = null;
                  // write header
                  channel.position(fpos);

                  while(headBuf.hasRemaining()) {
                     channel.write(headBuf);
                  }

                  channel.position(fpos + offset);
               }
               finally {
                  headerLock.unlock();
               }

               while(buf2.hasRemaining()) {
                  channel.write(buf2);
               }

               channel.commit();
               newbuf = false;
            }
            catch(Exception ex) {
               LOG.error("Failed to swap data to file: " + file, ex);
            }
         }

         return true;
      }

      public String toString() {
         return super.toString() + "[" + index + "," + size + "]";
      }

      protected long iaccessed;
      protected int index;
      protected int size;
      private boolean newbuf; // new buffer, need to be written
   }

   protected Fragment[] fragments;
   private final Lock headerLock = new ReentrantLock();
   private ByteBuffer headBuf;
   private static final Logger LOG =
      LoggerFactory.getLogger(MVDecimalColumn.class);
}
