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
package inetsoft.util.swap;

import inetsoft.util.FileSystemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * XIntFragment, the swappable int fragment.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public final class XIntFragment extends XSwappable {
   /**
    * Create an instance of <tt>XIntFragment</tt>.
    * @param isize the specified initial size.
    * @param size the specified max size.
    */
   public XIntFragment(char isize, char size) {
      this();
      this.size = size;
      this.pos = 0;
      this.arr = new int[isize];
   }

   private XIntFragment() {
      super();
      XSwapper.cur = System.currentTimeMillis();
      this.iaccessed = XSwapper.cur;
      this.valid = true;
      this.monitor = XSwapper.getMonitor();

      if(monitor != null) {
         isCountHM = monitor.isLevelQualified(XSwappableMonitor.HITS);
         isCountRW = monitor.isLevelQualified(XSwappableMonitor.READ);
      }
   }

   /**
    * Create a swappable int array.
    */
   public XIntFragment(int[] arr) {
      this();
      this.size = (char) arr.length;
      this.pos = this.size;
      this.arr = arr;
      complete();
   }

   /**
    * Access the int fragment.
    */
   public final void access() {
      iaccessed = XSwapper.cur;

      if(isCountHM) {
         if(valid && !lastValid) {
            monitor.countHits(XSwappableMonitor.DATA, 1);
            lastValid = true;
         }
         else if(!valid) {
            monitor.countMisses(XSwappableMonitor.DATA, 1);
            lastValid = false;
         }
      }

      if(!valid) {
         DEBUG_LOG.debug("Validate swapped data: %s", this);
         validate0(false);
      }
   }

   @Override
   public double getSwapPriority() {
      if(disposed || !completed || !valid || !isSwappable() || holding.get() > 0) {
         return 0;
      }

      return getAgePriority(XSwapper.cur - iaccessed, alive);
   }

   /**
    * Get the size of this int fragment.
    */
   public int size() {
      int pos = this.pos;

      if(pos == 0) {
         holding.incrementAndGet();

         try {
            access();
            pos = this.pos;
         }
         finally {
            holding.decrementAndGet();
         }
      }

      return pos;
   }

   /**
    * set new size to this int fragment.
    */
   public synchronized void size(char size) {
      if(size < 0 || size >= pos) {
         return;
      }

      if(disposed) {
         return;
      }

      if(!valid) {
         validate0(true);
      }

      completed = false;
      pos = size;
   }

   /**
    * Complete this int fragment.
    */
   @Override
   public void complete() {
      if(disposed || completed) {
         return;
      }

      completed = true;
      super.complete();
   }

   /**
    * Check if this int fragment is completed for swap.
    * @return <tt>true</tt< if completed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isCompleted() {
      return completed;
   }

   /**
    * Check if the swappable is swappable.
    * @return <tt>true</tt> if swappable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isSwappable() {
      return !disposed;
   }

   /**
    * Validate the int fragment internally.
    */
   private synchronized void validate0(boolean reset) {
      File file = getFile(prefix + ".tdat");

      RandomAccessFile fin = null;
      FileChannel channel = null;
      ByteBuffer buf = null;

      try {
         fin = new RandomAccessFile(file, "r");
         channel = fin.getChannel();
         buf = ByteBuffer.allocate((int) file.length());
         channel.read(buf);
         buf = XSwapUtil.uncompressByteBuffer(buf);

         if(isCountRW) {
            monitor.countRead(file.length(), XSwappableMonitor.DATA);
         }

         // @by yanie: after uncompress, the buffer is wrapped,
         // we don't need to flip, or else
         // the buf.limit() will be reset to 0 and will result in
         // BufferUnderflowException and the fragment cannot swap back
         //XSwapUtil.flip(buf)

         if(disposed) {
            return;
         }

         validate(buf);
         file = null;
      }
      catch(FileNotFoundException ex) {
         return;
      }
      catch(Exception ex) {
         LOG.error("Failed to read swap file: " + file, ex);
      }
      finally {
         buf = null;
         valid = true;

         try {
            if(channel != null) {
               channel.close();
               channel = null;
            }

            if(fin != null) {
               fin.close();
               fin = null;
            }
         }
         catch(Exception ex) {
            // ignore it
         }
      }

      if(reset) {
         file.delete();
      }
   }

   /**
    * Check if the int fragment is valid.
    * @return <tt>true</tt> if valid, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isValid() {
      return valid;
   }

   /**
    * Swap the swappable.
    * @return <tt>true</tt> if swapped, <tt>false</tt> rejected.
    */
   @Override
   public synchronized boolean swap() {
      if(getSwapPriority() == 0) {
         return false;
      }

      valid = false;
      swap0();
      return true;
   }

   /**
    * Swap the int fragment internally.
    */
   private void swap0() {
      long len = length();
      File file = getFile(prefix + ".tdat");
      RandomAccessFile fout = null;
      ByteBuffer buf = null;
      FileChannel channel = null;

      try {
         if(!file.exists()) {
            fout = new RandomAccessFile(file, "rw");
            channel = fout.getChannel();
            XSwapper.getSwapper().waitForMemory();
            buf = ByteBuffer.allocate((int) len);
         }

         if(disposed) {
            return;
         }

         invalidate(buf);

         if(isCountRW && buf != null) {
            monitor.countWrite(buf.position(), XSwappableMonitor.DATA);
         }

         // already swapped?
         if(buf != null) {
            XSwapUtil.flip(buf);
            buf = XSwapUtil.compressByteBuffer(buf);
            channel.write(buf);
         }

         file = null;
      }
      catch(Exception ex) {
         LOG.error("Failed to write swap file: " + file, ex);
      }
      finally {
         buf = null;

         try {
            if(channel != null) {
               channel.close();
               channel = null;
            }

            if(fout != null) {
               fout.close();
               fout = null;
            }
         }
         catch(Exception ex) {
            // ignore it
         }
      }
   }

   /**
    * Dispose the swappable.
    */
   @Override
   public synchronized void dispose() {
      if(disposed) {
         return;
      }

      disposed = true;
      arr = null;
      File file = getFile(prefix + ".tdat");

      if(file.exists()) {
         boolean result = file.delete();

         if(!result) {
            FileSystemService.getInstance().remove(file, 30000);
         }
      }
   }

   @Override
   protected void finalize() throws Throwable {
      dispose();
      super.finalize();
   }

   /**
    * Get the available data size of this fragment.
    * @return the available data size of this fragment.
    */
   public int available() {
      return pos;
   }

   /**
    * Get the capacity of this fragment.
    * @return the capacity of this fragment.
    */
   public int capacity() {
      return size;
   }

   /**
    * Add an int value.
    * @param val the specified int value.
    */
   public void add(int val) {
      // disposed?
      if(arr == null) {
         return;
      }

      if(pos == arr.length) {
         int nsize = Math.min((int) (arr.length * 1.5), size);
         int[] oarr = arr;
         int[] narr = new int[nsize];
         System.arraycopy(oarr, 0, narr, 0, oarr.length);
         arr = narr;
      }

      arr[pos++] = val;
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @return the int value in the specified row.
    */
   public int get(int r) {
      return arr == null ? 0 : arr[r];
   }

   /**
    * Get the value safely (ensuring the values are swapped in).
    */
   public int getSafely(int r) {
      holding.incrementAndGet();

      try {
         access();
         return arr == null ? 0 : arr[r];
      }
      finally {
	 holding.decrementAndGet();
      }
   }

   /**
    * Get the array used for holding the actual values.
    */
   public int[] getArray() {
      holding.incrementAndGet();

      try {
         access();
         return arr;
      }
      finally {
	 holding.decrementAndGet();
      }
   }

   /**
    * Get the byte length of this fragment.
    * @return the byte length of this fragment, <tt>-1</tt> unknown.
    */
   private long length() {
      return 4 * pos + 2;
   }

   /**
    * Validate this fragment from a byte buffer.
    * @param buf the specified byte buffer.
    * @return next position if any, <tt>-1</tt> otherwise.
    */
   private int validate(ByteBuffer buf) {
      pos = XSwapUtil.readChar(buf);
      int[] arr = new int[pos];

      for(int i = 0; i < pos; i++) {
         arr[i] = XSwapUtil.readInt(buf);
      }

      this.arr = arr;
      return -1;
   }

   /**
    * Invalidate this fragment to a byte buffer.
    * @param buf the specified byte buffer.
    * @return next position if any, <tt>-1</tt> otherwise.
    */
   private int invalidate(ByteBuffer buf) {
      if(buf != null) {
         XSwapUtil.writeChar(buf, pos);

         for(int i = 0; i < pos; i++) {
            XSwapUtil.writeInt(buf, arr[i]);
         }
      }

      arr = null;
      pos = 0;
      return -1;
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(XIntFragment.class);
   private static final Logger DEBUG_LOG =
      LoggerFactory.getLogger("inetsoft.swap_data");

   private long iaccessed;
   private int[] arr;
   private char size;
   private char pos;
   private boolean valid; // valid flag
   private boolean lastValid;
   private boolean completed; // completed flag
   private boolean disposed; // disposed flag
   private AtomicInteger holding = new AtomicInteger(0); // suspend swapping
   private transient XSwappableMonitor monitor;
   private transient boolean isCountHM;
   private transient boolean isCountRW;
}
