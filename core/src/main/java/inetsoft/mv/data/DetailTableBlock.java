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
package inetsoft.mv.data;

import inetsoft.mv.comm.XReadBuffer;
import inetsoft.mv.comm.XWriteBuffer;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.swap.XSwapUtil;
import inetsoft.util.swap.XSwappableObjectList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * DetailTableBlock, the grouped XTableBlock as query result.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public final class DetailTableBlock extends SubTableBlock {
   /**
    * Create an instance of DetailTableBlock.
    */
   public DetailTableBlock() {
      super();
   }

   /**
    * Initialize this table block with SubMVQuery.
    */
   @Override
   public void init(SubMVQuery query) {
      blockIndex = query.getBlockIndex();
      dcnt = query.groups.length;
      headers = new String[dcnt];

      for(int i = 0; i < headers.length; i++) {
         headers[i] = VSUtil.getAttribute(query.groups[i].getDataRef());
      }
   }

   /**
    * Add a detail row to grouping.
    */
   @Override
   public void addRow(MVRow row) {
      MVRow orow = row;
      row = (MVRow) row.clone();
      orow.setGroups(new long[orow.getGroups().length]);

      rows.add(row);

      if(rows.size() % 20 == 0) {
         synchronized(rowsLock) {
            rowsLock.notifyAll();
         }
      }
   }

   /**
    * Add a detail row (dimension only) to grouping.
    */
   @Override
   public void addDRow(MVRow row) {
      addRow(row);
   }

   /**
    * Complete this table block.
    */
   @Override
   public void complete() {
      rows.complete();

      synchronized(rowsLock) {
         rowsLock.notifyAll();
      }
   }

   /**
    * Check if all rows have been loaded.
    */
   @Override
   public boolean isCompleted() {
      return rows.isCompleted();
   }

   /**
    * Get the row count of this XTableBlock.
    */
   @Override
   public int getRowCount() {
      return rows.size();
   }

   /**
    * Get the measure count of this XTableBlock.
    */
   @Override
   public int getMeasureCount() {
      return 0;
   }

   /**
    * Get the row at the specified column.
    */
   @Override
   public MVRow getRow(int r) {
      return rows.get(r);
   }

   /**
    * Replace the rows in the block.
    */
   @Override
   public void setRows(XSwappableObjectList<MVRow> rows0) {
      this.rows.dispose();
      this.rows = rows0;

      synchronized(rowsLock) {
         rowsLock.notifyAll();
      }
   }

   /**
    * Read this transferable.
    */
   @Override
   public void read(XReadBuffer buf) throws IOException {
      blockIndex = buf.readInt();
      int rcnt = buf.readInt();
      dcnt = buf.readInt();
      int cnt = dcnt;
      headers = new String[cnt];

      for(int i = 0; i < cnt; i++) {
         headers[i] = buf.readString();
      }

      rows = new XSwappableObjectList<>(MVRow.class);
      int dlen = dcnt << 3;
      ByteBuffer tbuf = null;
      long[] dmatrix = new long[BLOCK_SIZE * dcnt];

      for(int i = 0; i < rcnt; i += BLOCK_SIZE) {
         int block = Math.min(rcnt - i, BLOCK_SIZE);
         int length = block * dlen;
         // @by jasons, direct byte buffers are only efficient when used with
         //             an nio channel
         ByteBuffer dbuf = ByteBuffer.allocate(length);

         tbuf = readBytes(buf, tbuf, dbuf, length);
         XSwapUtil.flip(dbuf);
         dbuf.asLongBuffer().get(dmatrix, 0, block * dcnt);

         // add created row to rows
         for(int j = 0; j < block; j++) {
            long[] darr = new long[dcnt];
            System.arraycopy(dmatrix, j * dcnt, darr, 0, dcnt);
            rows.add(new MVRow(darr, null));
         }
      }
   }

   /**
    * Write this transferable.
    */
   @Override
   public void write(XWriteBuffer buf) throws IOException {
      int rcnt = rows.size();
      buf.writeInt(blockIndex);
      buf.writeInt(rcnt);
      buf.writeInt(dcnt);

      for(String header : headers) {
         buf.writeString(header);
      }

      int written = 0;
      int dlen = dcnt << 3;
      long[] dmatrix = new long[BLOCK_SIZE * dcnt];

      while(written < rcnt) {
         int block = Math.min(BLOCK_SIZE, rcnt - written);
         ByteBuffer dbuf = ByteBuffer.allocate(block * dlen);
         int end = written + block;
         int count = 0;

         for(int i = written; i < end; i++) {
            System.arraycopy(rows.get(i).getGroups(), 0, dmatrix, count, dcnt);
            count += dcnt;
         }

         dbuf.asLongBuffer().put(dmatrix, 0, count);
         int pos = count << 3;
         XSwapUtil.position(dbuf, pos);

         written += block;
         XSwapUtil.flip(dbuf);
         buf.write(dbuf);
         // XWriteBuffer) keeps a reference to it and uses it later
      }
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return super.toString() + '<' + Arrays.asList(headers) + '>';
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(DetailTableBlock.class);
   private XSwappableObjectList<MVRow> rows = new XSwappableObjectList<>(MVRow.class);
}
