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
      XSwapper.cur = System.currentTimeMillis();
      this.iaccessed = XSwapper.cur;
      this.valid = true;

      if(getMonitor() != null) {
         isCountHM = getMonitor().isLevelQualified(XSwappableMonitor.HITS);
         isCountRW = getMonitor().isLevelQualified(XSwappableMonitor.READ);
      }
   }

   /**
    * Access the object fragment.
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

         XSwapper.getSwapper().waitForMemory();

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

      return getAgePriority(XSwapper.cur - iaccessed, alive);
   }

   /**
    * Get the size of this object fragment.
    */
   public int size() {
      return pos;
   }

   /**
    * set new size to this object fragment.
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
   private synchronized void validate0(boolean reset) {
      File file = getFile(prefix + "_0.tdat");

      RandomAccessFile fin = null;
      FileChannel channel = null;
      ByteBuffer buf = null;
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
            return;
         }

         while(validate(buf) != -1) {
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
      }
      catch(FileNotFoundException ex) {
         return;
      }
      catch(Exception ex) {
         LOG.error("Failed to read swap file: " + file, ex);
      }
      finally {
         valid = true;
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
         swapFileCount = 0;
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
         XSwapper.getSwapper().waitForMemory();
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

      if(file.exists()) {
         if(disposed) {
            return;
         }

         invalidate(null);
         return;
      }

      RandomAccessFile fout = null;
      FileChannel channel = null;
      ByteBuffer buf = null;
      int counter = 0;

      try {
         fout = new RandomAccessFile(file, "rw");
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
            channel = fout.getChannel();

            buf = ByteBuffer.allocate((int) len);
            lspos = spos;
         }

	 swapFileCount = counter + 1;
         XSwapUtil.flip(buf);
         buf = XSwapUtil.compressByteBuffer(buf);
         channel.write(buf);
         channel.close();
         channel = null;
         fout.close();

         if(isCountRW) {
            getMonitor().countWrite(file.length(), XSwappableMonitor.DATA);
         }

         fout = null;
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
      // disposed?
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
   public T get(int r) {
      if(r >= pos) {
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
         access();
         return get(r);
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
         access();
         return arr;
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

      if(!valid) {
         XSwapper.getSwapper().waitForMemory();

         synchronized(this) {
            if(!valid) {
               validate0(true);
            }
         }
      }
   }

   /**
    * Validate this table column from a byte buffer.
    * @param buf the specified byte buffer.
    * @return next position if any, <tt>-1</tt> otherwise.
    */
   private int validate(ByteBuffer buf) {
      try {
         if(buf.capacity() - buf.position() < HEADER_LENGTH) {
            return spos;
         }

         int len = XSwapUtil.readInt(buf);

         if(len == 0){
            return spos;
         }

         pos = XSwapUtil.readChar(buf);
         int count = XSwapUtil.readChar(buf);
         byte[] bytes = new byte[len];
         buf.get(bytes);
         ByteArrayInputStream in = new ByteArrayInputStream(bytes);

         Input kin = kryoClass != null ? new Input(in) : null;
         Kryo kryo = kryoClass != null ? XSwapUtil.getKryo() : null;
         ObjectInputStream oin = kryoClass == null ? new ObjectInputStream(in) : null;

         if(arr == null) {
            arr = new Object[pos];
         }

         if(arr == null) {
            arr = new Object[pos];
         }

         for(int i = 0; i < count; i++) {
            Object obj = kryo != null ? kryo.readObject(kin, kryoClass)
               : oin.readObject();
            arr[spos + i] = obj;
         }

         spos += count;

         if(spos == pos) {
            spos = 0;
            return -1;
         }
         else {
            return spos;
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to read swap buffer", ex);
      }

      return -1;
   }

   /**
    * Invalidate this table column to a byte buffer.
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
            }

            writeBlock(bout.size(), pos, (char) (pos - spos), bout.toBytes(),
                       buf);
         }
         catch(Exception ex) {
            LOG.error("Failed to write swap buffer", ex);
         }
      }

      pos = 0;
      spos = 0;
      arr = null;
      return -1;
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
            monitor = XSwapper.getMonitor();
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
   private boolean valid; // valid flag
   private boolean lastValid;
   private boolean completed; // completed flag
   private boolean disposed; // disposed flag
   private AtomicInteger holding = new AtomicInteger(0); // suspend swapping
   private char spos; // next serialization position
   private Class kryoClass;
   // @by jasons, CacheMonitorService is not serializable, so this needs to
   // be transient
   private transient XSwappableMonitor monitor;
   private transient boolean isCountHM;
   private transient boolean isCountRW;
}
