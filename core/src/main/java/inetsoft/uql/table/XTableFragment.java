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
package inetsoft.uql.table;

import inetsoft.util.FileSystemService;
import inetsoft.util.Tool;
import inetsoft.util.swap.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;

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
      XSwapper.cur = System.currentTimeMillis();
      this.iaccessed = XSwapper.cur;
      this.valid = valid;
   }

   @Override
   public double getSwapPriority() {
      if(disposed || !completed || !valid || !isSwappable()) {
         return 0;
      }

      return getAgePriority(XSwapper.cur - iaccessed, alive);
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
      if(getSwapPriority() == 0) {
         return false;
      }

      valid = false;
      swapColumns();
      return true;
   }

   /**
    * Swap the columns.
    */
   private void swapColumns() {
      File file = getSwapFile();
      RandomAccessFile fout = null;
      FileChannel channel = null;
      boolean swapped = true;
      files.clear();

      try {
         ByteBuffer footer = null;
         files.add(file);

         if(!file.exists() || isOldVersionSnapWrapFile()) {
            if(isOldVersionSnapWrapFile() && file.exists()) {
               try(FileWriter fileWriter = new FileWriter(file)){
                  fileWriter.write("");
                  fout = new RandomAccessFile(file, "rw");
                  channel = fout.getChannel();
                  swapped = false;
                  footer = ByteBuffer.allocate(columns.length * 16);
               }
               catch(Exception ignore) {
               }
            }
            else {
               fout = new RandomAccessFile(file, "rw");
               channel = fout.getChannel();
               swapped = false;
               footer = ByteBuffer.allocate(columns.length * 16);
            }
         }

         for(XTableColumn column : columns) {
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

               column.swap(file, channel);
               long npos = channel.position();

               footer.asLongBuffer().put(opos);
               XSwapUtil.position(footer, footer.position() + 8);
               footer.asIntBuffer().put((int) (npos - opos));
               XSwapUtil.position(footer, footer.position() + 4);
               footer.asIntBuffer().put(column.length());
               XSwapUtil.position(footer, footer.position() + 4);
            }
            else {
               column.invalidate();
            }
         }

         if(footer != null) {
            XSwapUtil.flip(footer);
            channel.write(footer);
         }

         setParseWithKryo4(false);
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

         channel.position(file.length() - columns.length * 16);
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
      iaccessed = XSwapper.cur;
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

   public boolean isOldVersionSnapWrapFile() {
      return parseWithKryo4;
   }

   public void setParseWithKryo4(boolean val) {
      this.parseWithKryo4 = val;
      XTableColumn[] cols = getColumns();

      if(cols == null) {
         return;
      }

      for(XTableColumn col : cols) {
         if(col instanceof XObjectColumn) {
            ((XObjectColumn) col).setParseWithKryo4(val);
         }
         else if(col instanceof XBigObjectColumn) {
            ((XBigObjectColumn) col).setParseWithKryo4(val);
         }
      }
   }

   public boolean isParseWithKryo4() {
      return parseWithKryo4;
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
   private boolean parseWithKryo4;
   private String snappath; // snapshot path
   private static final Logger LOG =
      LoggerFactory.getLogger(XTableFragment.class);
}
