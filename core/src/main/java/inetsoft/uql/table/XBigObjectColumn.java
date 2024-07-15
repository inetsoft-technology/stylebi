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
package inetsoft.uql.table;

import inetsoft.uql.schema.XSchema;
import inetsoft.util.FileSystemService;
import inetsoft.util.Tool;
import inetsoft.util.graphics.ImageWrapper;
import inetsoft.util.swap.*;

import java.awt.*;
import java.io.*;
import java.nio.channels.FileChannel;

import com.esotericsoftware.kryo.kryo5.io.Input;
import com.esotericsoftware.kryo.kryo5.io.Output;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * XBigObjectColumn, maintains the meta information and data of one big object
 * column.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public final class XBigObjectColumn extends XSwappable implements XTableColumn {
   /**
    * Get the table column creator.
    * @return the table column creator.
    */
   public static XTableColumnCreator getCreator() {
      return new XTableColumnCreator() {
         @Override
         public XTableColumn createColumn(char isize, char size) {
            return new XBigObjectColumn(isize, size);
         }

         @Override
         public Class getColType() {
            return null;
         }
      };
   }

   /**
    * Create an instance of <tt>XBigObjectColumn</tt>.
    * @param isize the specified inital size.
    * @param size the specified max size.
    */
   public XBigObjectColumn(char isize, char size) {
      this((char) 128, isize, size);
   }

   /**
    * Create an instance of <tt>XBigObjectColumn</tt>.
    * @param msize the preferred in-memory size.
    * @param isize the specified inital size.
    */
   public XBigObjectColumn(char msize, char isize, char size) {
      super();

      this.pos = 0;
      this.size = size;
      this.serial = UNKNOWN;
      this.arr = new Object[isize];
      this.parr = new int[isize];
      this.mlist = new XIntList(msize);

      DEBUG_LOG.debug("Create an object column: " + this);
   }

   /**
    * Get the table column creator.
    * @return the table column creator.
    */
   @Override
   public XTableColumnCreator getCreator0() {
      return getCreator();
   }

   /**
    * Get the data type.
    * @return the data type. Refer to <tt>XSchema</tt> for detail.
    */
   @Override
   public String getType() {
      return XSchema.UNKNOWN;
   }

   /**
    * Check if data is stored as primitive value.
    * @return <tt>true</tt> if data is stored as primitive value,
    * <tt>false</tt> otherwise.
    */
   @Override
   public boolean isPrimitive() {
      return false;
   }

   /**
    * Get the capacity of this table column.
    * @return the capacity of this table column.
    */
   @Override
   public int capacity() {
      return size;
   }

   /**
    * Ensure capacity.
    */
   private void ensureCapacity() {
      if(pos == arr.length) {
         int nsize = Math.min((int) (arr.length * 1.5), size);
         int[] oparr = parr;
         int[] nparr = new int[nsize];
         System.arraycopy(oparr, 0, nparr, 0, oparr.length);
         parr = nparr;

         Object[] oarr = arr;
         Object[] narr = new Object[nsize];
         System.arraycopy(oarr, 0, narr, 0, oarr.length);
         arr = narr;
      }
   }

   /**
    * Get the cached object count.
    * @return the chached object count.
    */
   public int getCacheCount() {
      return mlist.size;
   }

   /**
    * Add an object.
    * @param obj the specified object.
    * @return the preferred table column.
    */
   @Override
   public synchronized XTableColumn addObject(Object obj) {
      ensureCapacity();

      parr[pos] = -1;
      arr[pos++] = obj;

      if(serial == UNKNOWN && obj != null) {
         image = obj instanceof Image;
         serial = (image || obj instanceof Serializable ||
            obj instanceof Externalizable) ?
               TRUE : FALSE;
      }

      mlist.removeElement(pos - 1);

      if(mlist.size == mlist.arr.length) {
         mlist.remove(0);
      }

      mlist.add(pos - 1);

      return null;
   }

   /**
    * Get the object value in one row.
    * @param r the specified row index.
    * @return the object value in the specified row.
    */
   @Override
   public synchronized Object getObject(int r) {
      if(arr == null) {
         return null;
      }

      mlist.removeElement(r);

      if(mlist.size == mlist.arr.length) {
         mlist.remove(0);
      }

      mlist.add(r);

      // XBigObject doesn't support serialization so the array is empty from vso.
      if(r >= arr.length) {
         return null;
      }
      else if(arr[r] == Tool.NULL) {
         arr[r] = readObject(r);
         scount--;
      }

      return arr[r];
   }

   /**
    * Get the double value in one row.
    * @param r the specified row index.
    * @return the double value in the specified row.
    */
   @Override
   public double getDouble(int r) {
      return 0;
   }

   /**
    * Get the float value in one row.
    * @param r the specified row index.
    * @return the float value in the specified row.
    */
   @Override
   public float getFloat(int r) {
      return 0;
   }

   /**
    * Get the long value in one row.
    * @param r the specified row index.
    * @return the long value in the specified row.
    */
   @Override
   public long getLong(int r) {
      return 0;
   }

   /**
    * Get the int value in one row.
    * @param r the specified row index.
    * @return the int value in the specified row.
    */
   @Override
   public int getInt(int r) {
      return 0;
   }

   /**
    * Get the short value in one row.
    * @param r the specified row index.
    * @return the short value in the specified row.
    */
   @Override
   public short getShort(int r) {
      return 0;
   }

   /**
    * Get the byte value in one row.
    * @param r the specified row index.
    * @return the byte value in the specified row.
    */
   @Override
   public byte getByte(int r) {
      return 0;
   }

   /**
    * Get the boolean value in one row.
    * @param r the specified row index.
    * @return the boolean value in the specified row.
    */
   @Override
   public boolean getBoolean(int r) {
      return false;
   }

   /**
    * Check if the value in one row is null.
    * @param r the specified row index.
    * @return <tt>true</tt> if null, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isNull(int r) {
      return getObject(r) == null;
   }

   /**
    * Check if the table column is in valid state.
    * @return <tt>true</tt> if in valid state, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isValid() {
      return true;
   }

   /**
    * Check if is serializable.
    * @return <tt>true</tt> if serializable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isSerializable() {
      return false;
   }

   @Override
   public double getSwapPriority() {
      if(disposed || mlist == null || pos == mlist.size || !isSwappable() ||
         !completed)
      {
         return 0;
      }

      return pos - mlist.size - scount != 0 ? 1 : 10;
   }

   /**
    * Check if this table column is completed for swap.
    * @return <tt>true</tt< if completed, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isCompleted() {
      return completed;
   }

   /**
    * Complete this table column.
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
    * Check if the swappable is swappable.
    * @return <tt>true</tt> if swappable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isSwappable() {
      return serial != FALSE && !disposed;
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

      File file = getFile(prefix + ".tdat");
      FileOutputStream fout = null;

      try {
         fout = new FileOutputStream(file, true);
         ByteArrayOutputStream2 bout = new ByteArrayOutputStream2(4096);
         int len = (int) file.length();

         for(int i = 0; i < pos; i++) {
            if(disposed) {
               return false;
            }

            if(mlist.contains(i)) {
               continue;
            }

            if(parr[i] == -1) {
               parr[i] = len;
               Output oout = new Output(bout);
               XSwapUtil.getKryo().writeClassAndObject(
                  oout, image ? new ImageWrapper((Image) arr[i]) : arr[i]);
               oout.flush();

               int len0 = bout.size();
               fout.write(bout.toBytes(), 0, len0);
               len += len0;
               bout.reset();
            }

            scount++;
            arr[i] = Tool.NULL;
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to swap data", ex);
      }
      finally {
         if(fout != null) {
            try {
               fout.close();
            }
            catch(Exception ex) {
               // ignore it
            }

            fout = null;
         }
      }

      return true;
   }

   /**
    * Read an object at a row index.
    * @param r the specified row index.
    * @return the object at the row index.
    */
   private synchronized Object readObject(int r) {
      return readObject(r, parseWithKryo4);
   }

   /**
    * Read an object at a row index for old version case.
    * @param r the specified row index.
    * @return the object at the row index.
    */
   private synchronized Object readObject(int r, boolean oldVersion) {
      if(disposed) {
         return null;
      }

      int skip = parr[r];
      File file = getFile(prefix + ".tdat");
      FileInputStream fin = null;
      Object obj = null;

      try {
         fin = new FileInputStream(file);
         long s = fin.skip(skip);
         BufferedInputStream in = new BufferedInputStream(fin, 4096);
         InputStream kryoInput;

         if(oldVersion) {
            com.esotericsoftware.kryo.io.Input oin = new com.esotericsoftware.kryo.io.Input(in);
            kryoInput = oin;
            obj = XSwapUtil.getKryo4().readClassAndObject(oin);
         }
         else {
            Input oin = new Input(in);
            kryoInput = oin;
            obj = XSwapUtil.getKryo().readClassAndObject(oin);
         }

         if(image) {
            obj = ((ImageWrapper) obj).unwrap();
         }

         kryoInput.close();
      }
      catch(Exception e) {
         LOG.error("Failed to read object [" + r + "]", e);
      }
      finally {
         if(fin != null) {
            try {
               fin.close();
            }
            catch(Exception ex) {
               // ignore it
            }

            fin = null;
         }
      }

      return obj;
   }

   @Override
   public void swap(File file, FileChannel fc) throws Exception {
   }

   @Override
   public void invalidate() {
   }

   /**
    * Dispose the serializable.
    */
   @Override
   public synchronized void dispose() {
      if(disposed) {
         return;
      }

      disposed = true;
      arr = null;
      parr = null;

      if(mlist != null) {
         mlist.clear();
         mlist = null;
      }

      File file = getFile(prefix + ".tdat");

      if(file.exists()) {
         boolean removed = file.delete();

         if(!removed) {
            FileSystemService.getInstance().remove(file, 30000);
         }
      }
   }

   @Override
   public int length() {
      return pos;
   }

   @Override
   public void setSwapInfo(File file, long pos, int size, int len) {
      this.arr = null;
   }

   @Override
   public void removeObjectPool() {
   }

   @Override
   public int getInMemoryLength() {
      return arr == null ? 0 : arr.length;
   }

   public void setParseWithKryo4(boolean parseWithKryo4) {
      this.parseWithKryo4 = parseWithKryo4;
   }

   private Object[] arr; // object array
   private int[] parr; // pointer array
   private char size; // max size
   private char pos; // next position
   private byte serial; // serializable flag
   private boolean image; // image flag
   private XIntList mlist; // in-memory row indices
   private int scount; // swapped row count
   private boolean completed; // completed flag
   private boolean disposed; // disposed flag
   private boolean parseWithKryo4;

   private static final Logger LOG =
      LoggerFactory.getLogger(XBigObjectColumn.class);
   private static final Logger DEBUG_LOG =
      LoggerFactory.getLogger("inetsoft.swap_data");
}
