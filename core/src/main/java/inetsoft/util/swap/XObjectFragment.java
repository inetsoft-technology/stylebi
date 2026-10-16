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

import com.esotericsoftware.kryo.kryo5.Kryo;
import com.esotericsoftware.kryo.kryo5.io.Input;
import com.esotericsoftware.kryo.kryo5.io.Output;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * XObjectFrament, the swappable object fragment.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public final class XObjectFragment<T> extends XSwappable {
   /**
    * Create an instance of <tt>XObjectFrament</tt>.
    * @param isize the specified initial size.
    * @param size the specified max size.
    */
   public XObjectFragment(char isize, char size, Class kryoClass) {
      super();

      this.kryoClass = kryoClass;
      this.size = size;
      this.pos = 0;
      this.arr = new Object[isize];
      XSwapper s = getSwapper();
      s.cur = System.currentTimeMillis();
      this.iaccessed = s.cur;
      this.valid = true;

      if(getMonitor() != null) {
         isCountHM = getMonitor().isLevelQualified(XSwappableMonitor.HITS);
         isCountRW = getMonitor().isLevelQualified(XSwappableMonitor.READ);
      }
   }

   /**
    * Access the object fragment.
    */
   public final Object[] access() {
      Object[] arr = this.arr;
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

      if(!valid || arr == null) {
         DEBUG_LOG.debug("Validate swapped data: %s", this);

         getSwapper().waitForMemory();

         synchronized(this) {
            if(!valid || arr == null) {
               arr = validate0(false);
            }
         }
      }

      return arr;
   }

   @Override
   public double getSwapPriority() {
      if(disposed || !completed || !valid || !isSwappable() || holding.get() > 0) {
         return 0;
      }

      return getAgePriority(getSwapper().cur - iaccessed, alive);
   }

   /**
    * Get the size of this object fragment.
    */
   public int size() {
      return pos;
   }

   /**
    * Complete this object fragment.
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
    * Check if this object fragment is completed for swap.
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
    * Validate the object fragment internally.
    */
   private synchronized Object[] validate0(boolean reset) {
      File file = getFile(prefix + "_0.tdat");

      if(disposed) {
         valid = true;
         return this.arr;
      }

      // the fragment was empty when swapped (no swap file was written), or swap0() failed
      // before the data was dropped. the data in memory is the only good copy either way,
      // so remove any partial swap file to have the data written again on the next swap.
      // rewriteRequired is the actual correctness guarantee here, independent of whether the
      // delete below succeeds, so swap0() must not depend on it succeeding.
      if(this.arr != null) {
         rewriteRequired = true;
         deleteSwapFiles();
         spos = 0;
         valid = true;
         return this.arr;
      }

      RandomAccessFile fin = null;
      FileChannel channel = null;
      ByteBuffer buf = null;
      ObjectArrayHolder holder =  null;
      int counter = 0;

      try {
         fin = new RandomAccessFile(file, "r");
         buf = ByteBuffer.allocate((int) file.length());
         channel = fin.getChannel();
         channel.read(buf);

         if(isCountRW) {
            getMonitor().countRead(file.length(), XSwappableMonitor.DATA);
         }

         XSwapUtil.flip(buf);
         buf = XSwapUtil.uncompressByteBuffer(buf);

         if(disposed) {
            valid = true;
            return this.arr;
         }

        holder = new ObjectArrayHolder();

         while(validate(buf, holder) != -1) {
            buf = null;
            channel.close();
            fin.close();

            counter++;
            file = getFile(prefix + '_' + counter + ".tdat");
            fin = new RandomAccessFile(file, "r");
            buf = ByteBuffer.allocate((int) file.length());
            channel = fin.getChannel();
            channel.read(buf);

            if(isCountRW) {
               getMonitor().countRead(file.length(), XSwappableMonitor.DATA);
            }

            XSwapUtil.flip(buf);
            buf = XSwapUtil.uncompressByteBuffer(buf);
         }

         file = null;

         // validate(buf, holder) now either completes every chunk (setting holder.complete)
         // or throws out to the catch below - it no longer swallows a partial read internally
         // and returns -1 as if complete - so holder.complete is always true by the time the
         // while loop above exits normally. Kept as defense-in-depth / parity with the
         // write-side rewriteRequired bookkeeping rather than assuming that invariant holds
         // forever; the else branch below is not expected to be reachable today.
         if(holder.complete) {
            this.arr = holder.arr;
            this.pos = holder.pos;
            // a full, successful read-back proves the on-disk file(s) are actually correct
            rewriteRequired = false;
         }
         else {
            // the on-disk file(s) didn't fully parse - force the next swap0() to actually
            // (re)write regardless of file.exists(), rather than treating them as already-valid.
            // this.arr is already null here (the arr != null fast path above returns before
            // this point), so there is nothing to protect by holding onto a partial holder.arr
            rewriteRequired = true;
         }

         valid = true;

         if(reset) {
            swapFileCount = 0;
         }

         return this.arr;
      }
      catch(Exception ex) {
         // reaching here means this.arr was already null (the arr != null fast path above
         // returns before this point), so there is no in-memory copy left, and a partial
         // read would hand back wrong data for the missing rows. keep the fragment invalid
         // so a later access tries the files again (recovers from a transient failure) and
         // the swapper never swaps the fragment out in this state; fail loudly instead of
         // silently substituting partial data
         spos = 0;

         // a timeout or cancel closed the channel, the swap file is not lost (bug #77916)
         if(SwapReadInterruptedException.isInterrupt(ex)) {
            LOG.debug("Read of swap file interrupted: " + file, ex);
            throw new SwapReadInterruptedException(file, ex);
         }

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
   }

   /**
    * Delete the swap files of this fragment.
    */
   private void deleteSwapFiles() {
      boolean failed = false;

      for(int i = 0; ; i++) {
         File file = getFile(prefix + "_" + i + ".tdat");

         if(!file.exists()) {
            break;
         }

         // a file that could not be deleted is not queued for a delayed removal, which deletes
         // by name and would remove the file the next swap writes with the same name (#77877)
         if(!(testDelete != null ? testDelete.test(file) : file.delete())) {
            failed = true;
         }
      }

      swapFileCount = 0;
      // a file that could not be deleted may still be there for the next swap to reuse,
      // so keep checking for it on the next change
      hasSwapFiles = failed;

      // the files left no longer hold the current array, have the next swap rewrite them
      if(failed) {
         rewriteRequired = true;
      }
   }

   /**
    * Check if the object fragment is valid.
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
   public boolean swap() {
      File file = getFile(prefix + "_0.tdat");

      if(length() != 0 && !file.exists()) {
         getSwapper().waitForMemory();
      }

      synchronized(this) {
         if(getSwapPriority() == 0) {
            return false;
         }

         valid = false;
         swap0();
         return true;
      }
   }

   /**
    * Swap the object fragment internally.
    */
   private void swap0() {
      long len = length();

      if(len == 0) {
         return;
      }

      File file = getFile(prefix + "_0.tdat");

      // reuse-without-rewrite only applies to a file that's actually a durable copy of the
      // current array; a stub left by a previous failed write (rewriteRequired) must always be
      // (re)written, even if it still physically exists - see validate0()
      if(file.exists() && !rewriteRequired) {
         if(disposed) {
            return;
         }

         hasSwapFiles = true;
         invalidate(null);
         clear();
         return;
      }

      RandomAccessFile fout = null;
      FileChannel channel = null;
      ByteBuffer buf = null;
      int counter = 0;

      try {
         // set before the first file is created, so a failed write leaving a partial file
         // is still known to change()
         hasSwapFiles = true;
         fout = new RandomAccessFile(file, "rw");
         fout.setLength(0);
         channel = fout.getChannel();
         buf = ByteBuffer.allocate((int) len);
         int spos = 0; // current save pos
         int lspos = 0; // last save pos
         int recordSize = 0; // per record size

         while((spos = invalidate(buf)) != -1) {
            // very large object? try again
            if(spos < 0) {
               len = Math.max(-spos + MIN_SIZE / 2, len);
               buf = ByteBuffer.allocate((int) len);
               continue;
            }

            XSwapUtil.flip(buf);
            buf = XSwapUtil.compressByteBuffer(buf);

            if(testBeforeWrite != null) {
               testBeforeWrite.run();
            }

            channel.write(buf);
            channel.close();
            channel = null;
            fout.close();

            if(isCountRW) {
               getMonitor().countWrite(file.length(), XSwappableMonitor.DATA);
            }

            fout = null;
            int nrecordSaved = spos - lspos;
	    int newRecordSize = (int) Math.min(file.length() / nrecordSaved, 1024);
            recordSize = nrecordSaved == 0 ? 0 : Math.max(newRecordSize, recordSize);
            long new_len = Math.min((pos - spos) * recordSize, 20*1024*1024);
            len = Math.max(len, new_len);

	    counter++;
            file = getFile(prefix + '_' + counter + ".tdat");
            fout = new RandomAccessFile(file, "rw");
            fout.setLength(0);
            channel = fout.getChannel();

            buf = ByteBuffer.allocate((int) len);
            lspos = spos;
         }

	 swapFileCount = counter + 1;
         XSwapUtil.flip(buf);
         buf = XSwapUtil.compressByteBuffer(buf);

         if(testBeforeWrite != null) {
            testBeforeWrite.run();
         }

         channel.write(buf);
         channel.close();
         channel = null;
         fout.close();

         if(isCountRW) {
            getMonitor().countWrite(file.length(), XSwappableMonitor.DATA);
         }

         fout = null;

         // only drop the in-memory array once the final write above has
         // actually succeeded; if channel.write() threw, control never
         // reaches here and arr/pos/spos are preserved so the data isn't
         // silently lost
         clear();

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
      swapFileCount = 0;
   }

   @Override
   protected void finalize() throws Throwable {
      dispose();
      super.finalize();
   }

   /**
    * Get the length or a file block.
    */
   private long length() {
      if(available() == 0) {
         return 0;
      }

      return Math.max(MIN_SIZE * 2, available() * 64);
   }

   /**
    * Get the available data size of this fragment (number of object).
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
    * Add an object value.
    * @param val the specified object value.
    */
   public void add(Object val) {
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
         Object[] oarr = arr;
         Object[] narr = new Object[nsize];
         System.arraycopy(oarr, 0, narr, 0, oarr.length);
         arr = narr;
      }

      arr[pos++] = val;
   }

   /**
    * Get the object value at one row.
    * @param r the specified row index.
    * @return the object value at the specified row.
    */
   private T get(Object[] arr, int r) {
      if(arr == null || r >= arr.length) {
         return null;
      }

      return (T) arr[r];
   }

   /**
    * Get the value safely (ensuring the values are swapped in).
    */
   public T getSafely(int r) {
      holding.incrementAndGet();

      try {
         Object[] arr = access();
         return get(arr, r);
      }
      finally {
	 holding.decrementAndGet();
      }
   }

   /**
    * Get the array used for holding the actual objects.
    */
   public Object[] getArray() {
      holding.incrementAndGet();

      try {
         return access();
      }
      finally {
         holding.decrementAndGet();
      }
   }

   /**
    * Set the object value at one row.
    * @param r the specified row index.
    * @param obj the object value at the specified row.
    */
   public void set(int r, Object obj) {
      arr[r] = obj;
   }

   /**
    * Mark the fragment to be changed. That's to say, if it's swapped,
    * we should not reuse the swap file.
    */
   public void change() {
      if(disposed) {
         return;
      }

      // bring the data back first, the swap files may hold the only copy
      access();

      synchronized(this) {
         // a fragment that was never swapped has no file to check, which keeps this
         // per-set() call free of file system access
         if(disposed || !valid || arr == null || !hasSwapFiles) {
            return;
         }

         // swap0() skips writing when the first swap file exists, so remove the files
         // to have the changed values written on the next swap
         deleteSwapFiles();
      }
   }

   /**
    * Validate this table column from a byte buffer.
    * @param buf the specified byte buffer.
    * @return next position if any, <tt>-1</tt> otherwise.
    */
   private int validate(ByteBuffer buf, ObjectArrayHolder holder) throws Exception {
      Kryo kryo = null;

      try {
         if(buf.capacity() - buf.position() < HEADER_LENGTH) {
            return spos;
         }

         int len = XSwapUtil.readInt(buf);

         if(len == 0){
            return spos;
         }

         holder.pos = XSwapUtil.readChar(buf);
         int count = XSwapUtil.readChar(buf);
         byte[] bytes = new byte[len];
         buf.get(bytes);
         ByteArrayInputStream in = new ByteArrayInputStream(bytes);

         Input kin = kryoClass != null ? new Input(in) : null;
         kryo = kryoClass != null ? XSwapUtil.getKryo() : null;
         ObjectInputStream oin = kryoClass == null ? new ObjectInputStream(in) : null;

         if(holder.arr == null) {
            holder.arr = new Object[holder.pos];
         }

         for(int i = 0; i < count; i++) {
            Object obj = kryo != null ? kryo.readObject(kin, kryoClass)
               : oin.readObject();
            holder.arr[spos + i] = obj;
         }

         spos += count;

         if(spos == holder.pos) {
            spos = 0;
            // the only point at which every object has genuinely been read back; distinct
            // from the swallowed-exception path below, which also returns -1 but must not be
            // mistaken by validate0() for a real, complete read
            holder.complete = true;
            return -1;
         }
         else {
            return spos;
         }
      }
      finally {
         XSwapUtil.releaseKryo(kryo);
      }
   }

   /**
    * Serialize (a chunk of) this table column into a byte buffer. Does not
    * touch pos/spos/arr; the caller is responsible for dropping them only
    * once the resulting buffer(s) have been durably written.
    * @param buf the specified byte buffer.
    * @return next position if any, <tt>-1</tt> otherwise.
    */
   private int invalidate(ByteBuffer buf) {
      if(pos == 0 || spos == pos) {
         return -1;
      }

      if(buf != null) {
         try {
            ByteArrayOutputStream2 bout = new ByteArrayOutputStream2();

            if(HEADER_LENGTH > buf.capacity() - buf.position()) {
               return spos;
            }

            Output kout = kryoClass != null ? new Output(bout) : null;
            Kryo kryo = kryoClass != null ? XSwapUtil.getKryo() : null;
            ObjectOutputStream oout = kryoClass == null
               ? new ObjectOutputStream(bout) : null;

            try {
               for(int i = spos; i < pos; i++) {
                  int len = bout.size();
                  Object obj = arr[i];

                  if(obj instanceof java.sql.Array) {
                     try {
                        obj = ((java.sql.Array) obj).getArray();
                     }
                     catch(Exception ex) {
                        // ignore it
                     }
                  }

                  try {
                     if(kryo != null) {
                        kryo.writeObject(kout, obj);
                        // @by stephenwebster, For Bug #16227
                        // flushing object after every write to avoid problems
                        // reading object back in, just calling close() was not
                        // sufficient.
                        kout.flush();
                     }
                     else {
                        oout.writeObject(obj);
                        oout.flush();
                     }
                  }
                  catch(Exception ex) {
                     LOG.error("Failed to serialize object: " +
                                  (obj != null ? obj.getClass() : null), ex);
                     throw ex;
                  }

                  if(bout.size() + HEADER_LENGTH > buf.capacity() - buf.position()) {
                     // very large object? return size hint
                     if(i == spos) {
                        return -bout.size() - HEADER_LENGTH;
                     }
                     else {
                        writeBlock(len, pos, (char) (i - spos), bout.toBytes(), buf);
                     }

                     spos = (char) i;
                     return spos;
                  }
               }
            }
            finally {
               if(kryoClass != null) {
                  kout.close();
               }
               else {
                  oout.close();
               }

               XSwapUtil.releaseKryo(kryo);
            }

            writeBlock(bout.size(), pos, (char) (pos - spos), bout.toBytes(),
                       buf);
         }
         catch(Exception ex) {
            LOG.error("Failed to write swap buffer", ex);
         }
      }

      return -1;
   }

   /**
    * Drop the in-memory array once its serialized form has been durably
    * written (or, for the already-swapped fast path, once there is nothing
    * new to write).
    */
   private void clear() {
      pos = 0;
      spos = 0;
      arr = null;
      rewriteRequired = false;
   }

   /**
    * Write a block.
    * @param len the specified length.
    * @param size the specified size.
    * @param count the specified count.
    * @param arr the specified byte array.
    * @param buf the specified byte buffer.
    */
   private static void writeBlock(int len, char size, char count, byte[] arr,
                                  ByteBuffer buf) {
      XSwapUtil.writeInt(buf, len);
      XSwapUtil.writeChar(buf, size);
      XSwapUtil.writeChar(buf, count);
      buf.put(arr, 0, len);
   }

   /**
    * Gets the swappable monitor for this fragment.
    *
    * @return the monitor.
    */
   private XSwappableMonitor getMonitor() {
      if(monitor != null) {
         return monitor;
      }

      synchronized(this) {
         // @by jasons, monitor is now transient, so we need to look it up on
         // demand so a deserialized version works.
         if(monitor == null) {
            monitor = getSwapper().getMonitor();
         }
      }

      return monitor;
   }

   @Override
   public File[] getSwapFiles() {
      List<File> swapFiles = new ArrayList<>();

      for(int i = 0; i < swapFileCount; i++) {
         swapFiles.add(getFile(prefix + "_" + i + ".tdat"));
      }

      return swapFiles.toArray(new File[0]);
   }

   public boolean isDisposed() {
      return disposed;
   }

   /**
    * Write the objects of a swapped fragment, not only the names of its swap files. The copy may
    * be read by another JVM (e.g. from the distributed table cache), which can't read the swap
    * files of this JVM (bug #78042).
    */
   private void writeObject(ObjectOutputStream out) throws IOException {
      holding.incrementAndGet();

      try {
         // read the objects back outside of the lock, access() waits for memory
         access();

         // swap() drops the array while holding the lock, so it can't run while the fields
         // are written
         synchronized(this) {
            if((!valid || arr == null) && !disposed) {
               validate0(false);
            }

            out.defaultWriteObject();
         }
      }
      finally {
         holding.decrementAndGet();
      }
   }

   /**
    * Read a copy, which gets its own swap files and is swapped by the swapper of this JVM.
    */
   private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
      in.defaultReadObject();

      // written by an older version, which only wrote the swap file names of a swapped fragment
      if(arr == null && !disposed) {
         throw new InvalidObjectException("Swapped fragment written without its values: " + prefix);
      }

      // the writer's prefix names the writer's swap files, which the copy must not read, reuse
      // for its own swap or delete when it is changed
      XSwapper s = getSwapper();
      prefix = s.getPrefix();
      holding = new AtomicInteger(0);
      valid = true;
      lastValid = false;
      rewriteRequired = false;
      hasSwapFiles = false;
      swapFileCount = 0;
      spos = 0;
      iaccessed = s.cur;

      if(getMonitor() != null) {
         isCountHM = getMonitor().isLevelQualified(XSwappableMonitor.HITS);
         isCountRW = getMonitor().isLevelQualified(XSwappableMonitor.READ);
      }

      if(completed && !disposed) {
         completed = false;
         complete();
      }
   }

   private static final long serialVersionUID = -8549751950069082289L;
   private static final int HEADER_LENGTH = 8;
   private static final long MIN_SIZE = 131072L;
   private static final Logger LOG =
      LoggerFactory.getLogger(XObjectFragment.class);
   private static final Logger DEBUG_LOG =
      LoggerFactory.getLogger("inetsoft.swap_data");

   private long iaccessed;
   private Object[] arr;
   private char size;
   private char pos;
   private int swapFileCount; // number of cache files
   // swap files of this fragment may exist, guarded by this fragment's monitor
   private boolean hasSwapFiles;
   private boolean valid; // valid flag
   private boolean lastValid;
   private boolean completed; // completed flag
   private boolean disposed; // disposed flag
   // true when the on-disk file(s) are a stub/incomplete reconstruction left by a failed write
   // or read, not a durable copy of arr; forces the next swap0() to rewrite regardless of
   // file.exists()
   private boolean rewriteRequired;
   // test-only hook: when set, invoked immediately before each real durable write, so tests can
   // force a write failure deterministically without relying on platform-specific file locking
   // or permission semantics (which differ between Windows and Linux/CI). No-op in production.
   transient Runnable testBeforeWrite;
   // test-only hook: when set, called instead of File.delete() for a swap file of a live
   // fragment, so tests can force a delete failure on every platform. No-op in production.
   transient Predicate<File> testDelete;
   private AtomicInteger holding = new AtomicInteger(0); // suspend swapping
   private char spos; // next serialization position
   private Class kryoClass;
   // @by jasons, CacheMonitorService is not serializable, so this needs to
   // be transient
   private transient XSwappableMonitor monitor;
   private transient boolean isCountHM;
   private transient boolean isCountRW;

   private static class ObjectArrayHolder {
      private Object[] arr;
      private char pos;
      // true only once every object across every chunk has actually been read back;
      // distinguishes a genuine finish from validate()'s swallowed-exception return(-1)
      private boolean complete;
   }
}
