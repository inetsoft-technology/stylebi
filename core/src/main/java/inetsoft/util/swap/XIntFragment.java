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
      XSwapper s = getSwapper();
      s.cur = System.currentTimeMillis();
      this.iaccessed = s.cur;
      this.valid = true;
      this.monitor = s.getMonitor();

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
      iaccessed = getSwapper().cur;

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

         getSwapper().waitForMemory();

         synchronized(this) {
            if(!valid) {
               validate0(false);
            }
         }
      }
   }

   @Override
   public double getSwapPriority() {
      if(disposed || !completed || !valid || !isSwappable() || holding.get() > 0) {
         return 0;
      }

      return getAgePriority(getSwapper().cur - iaccessed, alive);
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

      if(disposed) {
         valid = true;
         return;
      }

      // swap0() failed before the data was dropped, so the data in memory is still the only
      // good copy. remove the partial swap file to have the data written again on the next swap.
      // rewriteRequired is the actual correctness guarantee here, independent of whether the
      // delete below succeeds: deleteFile() can fall back to a delayed/queued delete that fires
      // after a later swap recreates this same filename (the sub-problem 2/3 race), so swap0()
      // must not depend on it succeeding.
      if(arr != null) {
         valid = true;
         rewriteRequired = true;
         deleteFile(file);
         return;
      }

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
            valid = true;
            return;
         }

         validate(buf);
         // a full, successful read-back proves the on-disk file is actually correct, so
         // any earlier failed-write stub it may have been is no longer a concern
         rewriteRequired = false;
         valid = true;
      }
      catch(Exception ex) {
         // reaching here means arr was already null (the arr != null fast path above returns
         // before this point), so there is no in-memory copy left to fall back on: returning 0
         // for every row would silently map the rows to the header row. keep the fragment
         // invalid so a later access tries the file again (recovers from a transient failure,
         // e.g. EACCES/EMFILE) and the swapper never writes the empty state back over the swap
         // file; fail loudly instead of silently substituting wrong data
         pos = 0;
         LOG.error("Failed to read swap file: " + file, ex);
         throw new SwapFileReadException(file, ex);
      }
      finally {
         buf = null;

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
         deleteFile(file);
      }
   }

   /**
    * Delete a swap file.
    */
   private static void deleteFile(File file) {
      if(file.exists() && !file.delete()) {
         FileSystemService.getInstance().remove(file, 30000);
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
         // reuse-without-rewrite only applies to a file that's actually a durable copy of the
         // current array; a stub left by a previous failed write (rewriteRequired) must always
         // be (re)written, even if it still physically exists - see validate0()
         if(!file.exists() || rewriteRequired) {
            fout = new RandomAccessFile(file, "rw");
            fout.setLength(0);
            channel = fout.getChannel();
            getSwapper().waitForMemory();
            buf = ByteBuffer.allocate((int) len);
         }

         if(disposed) {
            return;
         }

         serialize(buf);

         if(isCountRW && buf != null) {
            monitor.countWrite(buf.position(), XSwappableMonitor.DATA);
         }

         // already swapped?
         if(buf != null) {
            XSwapUtil.flip(buf);
            buf = XSwapUtil.compressByteBuffer(buf);

            if(testBeforeWrite != null) {
               testBeforeWrite.run();
            }

            channel.write(buf);
         }

         // only drop the in-memory array once the write above has actually
         // succeeded; if channel.write() threw, control never reaches here and
         // arr/pos are preserved so the data isn't silently lost
         arr = null;
         pos = 0;
         rewriteRequired = false;

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
      if(disposed) {
         return;
      }

      // a completed fragment may have been swapped out (arr == null) or swapped and read
      // back (the next swap reuses the old swap file), so a value added now would be lost
      if(completed) {
         throw new IllegalStateException(
            "Cannot add a value to a completed swappable fragment: " + prefix);
      }

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
      // read fully into locals first and only commit pos/arr together, once reading has
      // completed without throwing - a mid-read failure must leave the fields exactly as
      // they were (the still-intact array a failed swap0() write may have preserved), not
      // a new pos paired with the old, differently-sized arr
      char newPos = XSwapUtil.readChar(buf);
      int[] newArr = new int[newPos];

      for(int i = 0; i < newPos; i++) {
         newArr[i] = XSwapUtil.readInt(buf);
      }

      pos = newPos;
      arr = newArr;
      return -1;
   }

   /**
    * Serialize this fragment into a byte buffer. Does not touch arr/pos; the
    * caller is responsible for dropping them only after the buffer has been
    * durably written.
    * @param buf the specified byte buffer.
    * @return next position if any, <tt>-1</tt> otherwise.
    */
   private int serialize(ByteBuffer buf) {
      if(buf != null) {
         XSwapUtil.writeChar(buf, pos);

         for(int i = 0; i < pos; i++) {
            XSwapUtil.writeInt(buf, arr[i]);
         }
      }

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
   // true when the swap file on disk is a stub left by a failed write, not a durable copy
   // of arr; forces the next swap0() to rewrite it even though it still exists
   private boolean rewriteRequired;
   // test-only hook: when set, invoked immediately before the real durable write, so tests can
   // force a write failure deterministically without relying on platform-specific file locking
   // or permission semantics (which differ between Windows and Linux/CI). No-op in production.
   transient Runnable testBeforeWrite;
   private AtomicInteger holding = new AtomicInteger(0); // suspend swapping
   private transient XSwappableMonitor monitor;
   private transient boolean isCountHM;
   private transient boolean isCountRW;
}
