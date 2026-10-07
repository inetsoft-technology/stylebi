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

import inetsoft.mv.MVTool;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.swap.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Date;

/**
 * Base class for table columns.
 *
 * @version 12.2
 * @author InetSoft Technology Corp
 */
public abstract class AbstractTableColumn implements XTableColumn, XSerializable {
   protected AbstractTableColumn() {
      monitor = XSwapper.getSwapper().getMonitor();
      swapInfo = "Created at " + new Date();

      if(monitor != null) {
         isCountRW = monitor.isLevelQualified(XSwappableMonitor.READ);
      }
   }

   /**
    * Copy column data to a buffer.
    */
   public abstract ByteBuffer copyToBuffer();

   /**
    * Copy data data from a buffer to internal array.
    */
   public abstract void copyFromBuffer(ByteBuffer buf);

   /**
    * Called to write data to a swap file. The data is kept in memory, the caller invalidates
    * the column once the whole file is written (bug #77948).
    */
   @Override
   public synchronized void swap(File file, FileChannel fc) throws Exception {
      // no swap data until the write below has succeeded
      this.swapsize = 0;
      this.swapfile = file;
      this.swappos = fc.position();
      ByteBuffer buf0 = copyToBuffer();

      if(buf0 != null) {
         ByteBuffer buf = XSwapUtil.compressByteBuffer(buf0);
         int size = buf.remaining();

         if(isCountRW) {
            monitor.countWrite(size, XSwappableMonitor.DATA);
         }

         fc.write(buf);
         swapsize = size;
         ByteBufferPool.releaseByteBuffer(buf0);
      }
   }

   /**
    * Check if the data of this column is in its swap file. It is not when the column could
    * not be copied to a buffer (copyToBuffer() returned null) or the write failed, and the
    * column must then stay in memory (bug #77948).
    */
   @Override
   public synchronized boolean hasSwapData() {
      return swapsize > 0;
   }

   /**
    * Load swap file to memory.
    */
   protected synchronized void load() {
      if(swapsize == 0) {
         LOG.warn("Trying to load a column that has not been swapped: " + swapfile);
         return;
      }

      MappedByteBuffer buf0 = null;
      RandomAccessFile fin = null;
      FileChannel channel = null;
      long uncompressedSize = -1;

      try {
         fin = new RandomAccessFile(swapfile, "r");
         channel = fin.getChannel();

         if(isCountRW) {
            monitor.countRead(swapsize, XSwappableMonitor.DATA);
         }

         channel.position(swappos);

         buf0 = channel.map(FileChannel.MapMode.READ_ONLY, swappos, swapsize);

         ByteBuffer buf = XSwapUtil.uncompressByteBuffer(buf0);
         uncompressedSize = buf.remaining();
         copyFromBuffer(buf);
      }
      catch(Exception ex) {
         if(!swapfile.exists()) {
            Tool.addUserMessage(Catalog.getCatalog().getString("common.worksheet.swap.missing"));
            LOG.error("Failed to read swap file: " + swapInfo + " missing", ex);
         }
         else {
            LOG.error("Failed to read swap file: " + swapInfo + " size: " + swapfile.length() +
               " uncompressed: " + uncompressedSize + " ts: " + new Date(swapfile.lastModified()),
                      ex);
         }

         // the data array is left null so a later access tries the file again. fail loudly
         // instead of returning, which would end in a plain NullPointerException in the
         // getter, or in silent nulls for an XObjectColumn (bug #77895)
         throw new SwapFileReadException(swapfile, ex);
      }
      finally {
         if(buf0 != null) {
            MVTool.unmap(buf0);
         }

         try {
            if(channel != null) {
               channel.close();
            }

            if(fin != null) {
               fin.close();
            }
         }
         catch(Exception ex) {
            // ignore it
         }
      }
   }

   /**
    * Get the length (number of items) in this column.
    */
   @Override
   public int length() {
      return pos;
   }

   @Override
   public void setSwapInfo(File file, long swappos, int swapsize, int len) {
      this.swapfile = file;
      this.swappos = swappos;
      this.swapsize = swapsize;
      this.pos = (char) len;
      swapInfo = "swapfile: " + file + ", swappos: " + swappos + ", swapsize: " + swapsize +
         " pos: " + len;

      invalidate();
   }

   @Override
   public void removeObjectPool() {
   }

   @Override
   public String getSwapLog() {
      return swapInfo;
   }

   @Override
   public void setSwapLog(String info) {
      this.swapInfo = info;
   }

   protected char pos; // next position in array (length of current values)
   private File swapfile;
   private long swappos;
   private int swapsize;
   private transient boolean isCountRW;
   private transient XSwappableMonitor monitor;
   private String swapInfo; // for debugging

   private static final Logger LOG = LoggerFactory.getLogger(AbstractTableColumn.class);
}
