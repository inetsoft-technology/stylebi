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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.util.*;
import inetsoft.util.swap.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.locks.Lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * XTableFragment, the swappable table fragment.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public final class XTableFragment extends XSwappable {
   /**
    * Create an instance of <tt>XTableFragment</tt>.
    * @param columns the specified columns.
    */
   public XTableFragment(XTableColumn[] columns) {
      this(columns, true);
   }

   /**
    * Create an instance of <tt>XTableFragment</tt>.
    * @param columns the specified columns.
    * @param valid true if the table is valid.
    */
   public XTableFragment(XTableColumn[] columns, boolean valid) {
      super();

      this.files = new ArrayList<>();
      this.columns = columns;
      XSwapper s = getSwapper();
      s.cur = System.currentTimeMillis();
      this.iaccessed = s.cur;
      this.valid = valid;
   }

   @Override
   public double getSwapPriority() {
      if(disposed || !completed || !valid || lost || !isSwappable()) {
         return 0;
      }

      return getAgePriority(getSwapper().cur - iaccessed, alive);
   }

   /**
    * Complete this table fragment.
    */
   @Override
   public void complete() {
      if(disposed || completed) {
         return;
      }

      for(XTableColumn column : columns) {
         column.complete();
      }

      completed = true;
      super.complete();
   }

   /**
    * Check if this table fragment is completed for swap.
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
      // local reference to avoid synchronization
      XTableColumn[] columns = this.columns;

      if(disposed) {
         return false;
      }

      for(XTableColumn column : columns) {
         if(column.isSerializable()) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if the table fragment is valid.
    * @return <tt>true</tt> if valid, <tt>false</tt> otherwise.
    */
   @Override
   public final boolean isValid() {
      return valid;
   }

   /**
    * Check if the table fragment is disposed.
    * @return <tt>true</tt> if disposed, <tt>false</tt> otherwise.
    */
   public boolean isDisposed() {
      return disposed;
   }

   /**
    * Swap the swappable.
    * @return <tt>true</tt> if swapped, <tt>false</tt> rejected.
    */
   @Override
   public synchronized boolean swap() {
      return swap(false);
   }

   /**
    * Swap the swappable.
    * @return <tt>true</tt> if swapped, <tt>false</tt> rejected.
    */
   public synchronized boolean swap(boolean force) {
      // a fragment is not swapped again until it is read. a failed write keeps all its data in
      // memory without a file, so a forced swap (snapshot save/export) must retry the write, or
      // the table is saved without its data (bug #77963)
      boolean retry = force && rewriteRequired && !disposed && completed && isSwappable();

      if((!retry && getSwapPriority() == 0) || isSwapFileLost()) {
         return false;
      }

      valid = false;
      swapColumns();

      if(force) {
         Cluster cluster = Cluster.getInstance();
         // optimization, lock the map and bulk add files to cleaner instead
         // of doing so individually.
         Lock lock = cluster.getLock(XSwapper.SWAP_FILE_MAP_LOCK);
         lock.lock();

         try {
            // if there are any swap files then add them to the cleaner so that
            // the files can be removed once they are no longer referenced
            File[] swapFiles = getSwapFiles();

            if(swapFiles.length > 0) {
               Cleaner.add(new XSwapper.XSwappableReference(this, swapFiles));
            }
         }
         finally {
            lock.unlock();
         }
      }

      return true;
   }

   /**
    * Check if the swap file of this fragment is lost. It is lost when it is missing while some
    * of the swapped columns are not in memory, so they can't be written to a new file. Writing
    * it anyway would create an empty file, or one that holds nulls in place of the lost data.
    * The fragment is not swapped again once the file is lost, and reading the columns that are
    * not in memory throws SwapFileReadException (bug #77895).
    */
   private boolean isSwapFileLost() {
      if(!lost && !getSwapFile().exists()) {
         for(XTableColumn column : columns) {
            if(column.isSerializable() && !column.isValid()) {
               lost = true;
               LOG.error("Swap file is missing, the table fragment is not swapped out any " +
                         "more: " + getSwapFile());
               break;
            }
         }
      }

      return lost;
   }

   /**
    * Swap the columns. The columns are written to the swap file once, and a later swap reuses
    * the file. A column is invalidated only after the whole file, including the footer, is
    * written, and only when its data is in the file. A failed or incomplete write deletes the
    * file and keeps every column in memory, so the file is rewritten by the next swap instead
    * of being reused (bug #77948).
    */
   private void swapColumns() {
      File file = getSwapFile();
      RandomAccessFile fout = null;
      FileChannel channel = null;
      boolean swapped = true;
      boolean done = false;
      files.clear();

      try {
         ByteBuffer footer = null;
         boolean[] written = new boolean[columns.length];

         if(!file.exists() || rewriteRequired) {
            // cleared when the file is complete, so a failure to open the file is retried too
            rewriteRequired = true;
            fout = new RandomAccessFile(file, "rw");
            // the file exists now (it may have just been created), so a failure from here on
            // must delete it in the finally block instead of leaving a stub to be reused
            swapped = false;
            fout.setLength(0);
            channel = fout.getChannel();
            footer = ByteBuffer.allocate(columns.length * 16);
         }

         for(int i = 0; i < columns.length; i++) {
            XTableColumn column = columns[i];

            if(disposed) {
               return;
            }

            if(!column.isSerializable()) {
               if(footer != null) {
                  XSwapUtil.position(footer, footer.position() + 16);
               }
               continue;
            }

            if(!swapped) {
               long opos = channel.position();

               if(testBeforeWrite != null) {
                  testBeforeWrite.run();
               }

               column.swap(file, channel);
               long npos = channel.position();
               written[i] = npos > opos;

               footer.asLongBuffer().put(opos);
               XSwapUtil.position(footer, footer.position() + 8);
               footer.asIntBuffer().put((int) (npos - opos));
               XSwapUtil.position(footer, footer.position() + 4);
               footer.asIntBuffer().put(column.length());
               XSwapUtil.position(footer, footer.position() + 4);
            }
            else {
               written[i] = true;
            }
         }

         if(footer != null) {
            XSwapUtil.flip(footer);

            if(testBeforeWrite != null) {
               testBeforeWrite.run();
            }

            channel.write(footer);
         }

         // the file is complete, drop the data of the columns that are in it. a column whose
         // data could not be written (e.g. a value Kryo can't serialize) stays in memory
         for(int i = 0; i < columns.length; i++) {
            if(written[i] && columns[i].isSerializable() && columns[i].hasSwapData()) {
               columns[i].invalidate();
            }
         }

         files.add(file);
         rewriteRequired = false;
         done = true;
      }
      catch(Exception ex) {
         LOG.error("Failed to write XTableFragment swap file: " + file, ex);
      }
      finally {
         try {
            if(channel != null) {
               channel.close();
            }

            if(fout != null) {
               fout.close();
            }
         }
         catch(Exception ex) {
            // ignore it
         }

         // an incomplete file must not be reused by a later swap or listed for a snapshot.
         // no column was invalidated, so the data is all in memory
         if(!done && !swapped) {
            rewriteRequired = true;

            if(!file.delete() && file.exists()) {
               LOG.warn("Failed to delete incomplete XTableFragment swap file: " + file);
            }
         }
      }
   }

   @Override
   public synchronized void dispose() {
      if(disposed) {
         return;
      }

      disposed = true;

      if(columns != null) {
         for(XTableColumn column : columns) {
            column.dispose();
         }

         columns = null;
      }

      if(files != null) {
         // for snapshot table, don't delete the files since the files should be
         // persistent and are deleted by SnapshotEmbeddedTableAssembly
         files.clear();
         files = null;
      }
   }

   /**
    * Get swapped files.
    * @return the swapped files.
    */
   public List<File> getFiles() {
      return files;
   }

   /**
    * Set explicit path, if path exists, use this path to validate data.
    */
   public void setSnapshotPath(String path) {
      this.path = path;
      this.snappath = path;

      FileChannel channel = null;
      RandomAccessFile fout = null;

      try {
         Arrays.stream(columns).forEach(c -> c.setSwapLog("Loading " + path));
         File file = getSwapFile();

         if(!file.exists()) {
            Arrays.stream(columns).forEach(c -> c.setSwapLog("File missing: " + file));

            if(LOG.isDebugEnabled()) {
               LOG.warn("Snapshot file missing: " + file);
            }
            return;
         }

         files.add(file);
         fout = new RandomAccessFile(file, "rw");
         channel = fout.getChannel();

         channel.position(file.length() - columns.length * 16L);
         ByteBuffer footer = ByteBuffer.allocate(columns.length * 16);
         channel.read(footer);
         XSwapUtil.flip(footer);

         for(XTableColumn column : columns) {
            long pos = footer.asLongBuffer().get();
            XSwapUtil.position(footer, footer.position() + 8);
            int size = footer.asIntBuffer().get();
            XSwapUtil.position(footer, footer.position() + 4);
            int len = footer.asIntBuffer().get();
            XSwapUtil.position(footer, footer.position() + 4);

            column.setSwapInfo(file, pos, size, len);
         }
      }
      catch(Throwable ex) {
         LOG.error("Failed to read swap file header: " + path, ex);
         Arrays.stream(columns).forEach(c -> c.setSwapLog("Error: " + ex));
      }
      finally {
         if(channel != null) {
            try {
               channel.close();
            }
            catch(Exception ex2) {
               // ignore
            }
         }

         if(fout != null) {
            try {
               fout.close();
            }
            catch(Exception ex2) {
               // ignore
            }
         }
      }
   }

   /**
    * Get the swap/data file of this table fragment.
    */
   public File getSwapFile() {
      return getFileByPostfix("_s.tdat");
   }

   /**
    * Get the swap file path if this is for snapshot table.
    */
   public String getSnapshotPath() {
      return snappath;
   }

   /**
    * Get the file by postfix.
    * @return the file by postfix.
    */
   protected File getFileByPostfix(String postfix) {
      if(prefix != null) {
         prefix = prefix.replace(postfix, "");
      }

      if(path != null) {
         path = path.replace(postfix, "");
      }

      return path == null ? getFile(prefix + postfix) :
         FileSystemService.getInstance().getFile(Tool.convertUserFileName(path + postfix));
   }

   /**
    * Get prefix.
    */
   public String getPrefix() {
      return prefix;
   }

   /**
    * Called when the columns are used.
    */
   private void access() {
      iaccessed = getSwapper().cur;
      valid = true; // column access (isNull, get...) will swap in data
   }

   public final boolean isNull(int ridx, int c) {
      access();
      return columns[c].isNull(ridx);
   }

   public final Object getObject(int ridx, int c) {
      access();
      return columns[c].getObject(ridx);
   }

   public final double getDouble(int ridx, int c) {
      access();
      return columns[c].getDouble(ridx);
   }

   public final float getFloat(int ridx, int c) {
      access();
      return columns[c].getFloat(ridx);
   }

   public final long getLong(int ridx, int c) {
      access();
      return columns[c].getLong(ridx);
   }

   public final short getShort(int ridx, int c) {
      access();
      return columns[c].getShort(ridx);
   }

   public final byte getByte(int ridx, int c) {
      access();
      return columns[c].getByte(ridx);
   }

   public final int getInt(int ridx, int c) {
      access();
      return columns[c].getInt(ridx);
   }

   public final boolean getBoolean(int ridx, int c) {
      access();
      return columns[c].getBoolean(ridx);
   }

   public final boolean isPrimitive(int c) {
      access();
      return columns[c].isPrimitive();
   }

   public final synchronized XTableColumnCreator addObject(int col, Object val) {
      if(disposed) {
         return null;
      }

      XTableColumn column = columns[col].addObject(val);

      if(column != null) {
         XTableColumn t = columns[col];
         columns[col] = column;
         t.dispose();
         return column.getCreator0();
      }

      return null;
   }

   public final XTableColumn[] getColumns() {
      return columns;
   }

   public boolean isDataPathFileExist() {
      return files != null && files.stream().anyMatch(file -> file.exists());
   }

   /**
    * Remove internal cache.
    */
   public void removeObjectPool() {
      for(XTableColumn column : columns) {
         column.removeObjectPool();
      }
   }

   @Override
   public File[] getSwapFiles() {
      FileSystemService fileSystemService = FileSystemService.getInstance();
      List<File> swapFiles = new ArrayList<>();
      List<File> files = this.files == null ? new ArrayList<>() : new ArrayList<>(this.files);

      for(File file : files) {
         if(fileSystemService.isCacheFile(file)) {
            swapFiles.add(file);
         }
      }

      return swapFiles.toArray(new File[0]);
   }

   private XTableColumn[] columns; // table column
   private String path;
   private long iaccessed; // last accessed timestamp
   private boolean valid; // valid flag
   private List<File> files; // cache files
   private boolean completed; // completed flag
   private boolean disposed; // disposed flag
   private volatile boolean lost; // swap file lost, see isSwapFileLost()
   // the swap file is incomplete (a write failed), it must be rewritten and not reused
   private boolean rewriteRequired;
   private String snappath; // snapshot path
   // test-only hook, run before each column and the footer is written to the swap file
   transient Runnable testBeforeWrite;
   private static final Logger LOG =
      LoggerFactory.getLogger(XTableFragment.class);
}
