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

import inetsoft.storage.BlobChannel;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.cachefs.CacheFS;
import inetsoft.util.swap.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.util.Date;

/**
 * Base class for table columns.
 *
 * @version 12.2
 * @author InetSoft Technology Corp
 */
public abstract class AbstractTableColumn implements XTableColumn, XSerializable {
   protected AbstractTableColumn() {
      monitor = XSwapper.getMonitor();
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
    * Called to swap data to a file.
    */
   @Override
   public synchronized void swap(Path file, SeekableByteChannel fc) throws Exception {
      this.swapfile = file;
      this.swappos = fc.position();
      ByteBuffer buf0 = copyToBuffer();

      if(buf0 != null) {
         ByteBuffer buf = XSwapUtil.compressByteBuffer(buf0);
         swapsize = buf.remaining();

         if(isCountRW) {
            monitor.countWrite(swapsize, XSwappableMonitor.DATA);
         }

         fc.write(buf);
         ByteBufferPool.releaseByteBuffer(buf0);
         invalidate();
      }
      else {
         swapsize = 0;
      }
   }

   /**
    * Load swap file to memory.
    */
   protected synchronized void load() {
      if(swapsize == 0) {
         LOG.warn("Trying to load a column that has not been swapped: " + swapfile);
         return;
      }

      ByteBuffer buf0 = null;
      SeekableByteChannel channel = null;
      long uncompressedSize = -1;

      try {
         channel = Files.newByteChannel(swapfile, StandardOpenOption.READ);

         if(isCountRW) {
            monitor.countRead(swapsize, XSwappableMonitor.DATA);
         }

         channel.position(swappos);

         buf0 = ((BlobChannel) channel).map(swappos, swapsize);

         ByteBuffer buf = XSwapUtil.uncompressByteBuffer(buf0);
         uncompressedSize = buf.remaining();
         copyFromBuffer(buf);
      }
      catch(Exception ex) {
         if(!Files.exists(swapfile)) {
            Tool.addUserMessage(Catalog.getCatalog().getString("common.worksheet.swap.missing"));

            if(LOG.isDebugEnabled()) {
               LOG.debug(
                  "Failed to read swap file: {} {}",
                  swapInfo, Files.exists(swapfile) ? "size: " + CacheFS.size(swapfile) : " missing",
                  ex);
            }
         }
         else {
            LOG.error(
               "Failed to read swap file: {} size: {} uncompressed: {} ts: {}",
               swapInfo, CacheFS.size(swapfile), uncompressedSize,
               new Date(CacheFS.lastModified(swapfile)), ex);
         }
      }
      finally {
         if(buf0 != null) {
            try {
               ((BlobChannel) channel).unmap(buf0);
            }
            catch(IOException ignore) {
            }
         }

         try {
            if(channel != null) {
               channel.close();
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
   public void setSwapInfo(Path file, long swappos, int swapsize, int len) {
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
   private Path swapfile;
   private long swappos;
   private int swapsize;
   private transient boolean isCountRW;
   private transient XSwappableMonitor monitor;
   private String swapInfo; // for debugging

   private static final Logger LOG = LoggerFactory.getLogger(AbstractTableColumn.class);
}
