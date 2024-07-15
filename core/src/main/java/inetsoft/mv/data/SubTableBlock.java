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
package inetsoft.mv.data;

import inetsoft.mv.MVDef;
import inetsoft.mv.comm.XReadBuffer;
import inetsoft.mv.comm.XTransferable;
import inetsoft.util.swap.XSwapUtil;
import inetsoft.util.swap.XSwappableObjectList;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * SubTableBlock, for processing individual rows.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public abstract class SubTableBlock implements XTableBlock, XTransferable {
   /**
    * Initialize this table block with SubMVQuery.
    */
   public abstract void init(SubMVQuery query);

   /**
    * Process a single row.
    */
   public abstract void addRow(MVRow row);

   /**
    * Process a single row with dimension only (no aggregate).
    */
   public abstract void addDRow(MVRow row);

   /**
    * Replace the rows in the block.
    */
   public abstract void setRows(XSwappableObjectList<MVRow> rows);

   /**
    * Called when all rows have been processed.
    */
   public abstract void complete();

   /**
    * Check if all rows have been loaded.
    */
   public abstract boolean isCompleted();

   /**
    * Wait for the row.
    */
   public boolean moreRows(int row) {
      synchronized(rowsLock) {
         while(row >= getRowCount() && !isCompleted()) {
            try {
               rowsLock.wait(10000);
            }
            catch(Exception ex) {
               // ignore it
            }
         }
      }

      return row < getRowCount();
   }

   /**
    * Get the block index of the query.
    */
   public int getBlockIndex() {
      return blockIndex;
   }

   /**
    * Set the block index of the query.
    */
   public void setBlockIndex(int blockIndex) {
      this.blockIndex = blockIndex;
   }

   /**
    * Get the col count of this XTableBlock.
    */
   @Override
   public int getColCount() {
      return headers.length;
   }

   /**
    * Get the dimension count of this XTableBlock.
    */
   @Override
   public int getDimCount() {
      return dcnt;
   }

   /**
    * Get the Header at the specified column.
    */
   @Override
   public String getHeader(int c) {
      return headers[c];
   }

   /**
    * Get all column headers.
    */
   public String[] getHeaders() {
      return headers;
   }

   /**
    * Get the index of the specified column header.
    */
   @Override
   public int indexOfHeader(String header) {
      return indexOfCol(headers, header);
   }

   /**
    * Get the index of the specified column.
    */
   protected int indexOfCol(String[] cols, String col) {
      return MVDef.indexOfHeader(col, cols, 0);
   }

   /**
    * Read bytes from buf or tbuf into dbuf. Return the tbuf if there are
    * bytes in the buffer that's not consumed.
    */
   protected ByteBuffer readBytes(XReadBuffer buf, ByteBuffer tbuf,
                                  ByteBuffer dbuf, int length)
         throws IOException
   {
      int clength = 0;

      while(clength < length) {
         tbuf = tbuf != null ? tbuf : buf.read(null);
         int pos = tbuf.position();
         int limit = tbuf.limit();
         int available = limit - pos;

         if(available == 0) {
            // may be a direct byte buffer
            tbuf = null;
            continue;
         }

         // temp buffer is not enough
         if(available < length - clength) {
            dbuf.put(tbuf);
            clength += available;
            // may be a direct byte buffer
            tbuf = null;
         }
         // temp buffer is enough
         else {
            byte[] arr = new byte[length - clength];
            tbuf.get(arr);
            dbuf.put(arr);
            XSwapUtil.position(tbuf, pos + (length - clength));
            clength = length;
         }
      }

      return tbuf;
   }

   protected static final int BLOCK_SIZE = 0x400;
   int dcnt;
   String[] headers = {};
   boolean[] order;
   protected int blockIndex = -1;
   protected Object rowsLock = new String("lock");
}
