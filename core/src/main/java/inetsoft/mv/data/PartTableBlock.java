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

import inetsoft.mv.comm.XReadBuffer;
import inetsoft.mv.comm.XWriteBuffer;
import inetsoft.util.swap.XSwappableObjectList;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A wrapper to present part of a table block as a SubTableBlock.
 *
 * @author InetSoft Technology
 * @since  12.1
 */
public class PartTableBlock extends SubTableBlock {
   /**
    * Create a table block for a section of the table.
    * @param startRow the starting row index in the base table.
    * @param endRow the ending (exclusive) row index in the base table.
    */
   public PartTableBlock(SubTableBlock tbl, int startRow, int endRow) {
      this.tbl = tbl;
      this.startRow = startRow;
      this.endRow = Math.min(endRow, tbl.getRowCount());
   }
   
   /**
    * Get the row count of this XTableBlock.
    */
   @Override
   public int getRowCount() {
      return endRow - startRow;
   }

   @Override
   public boolean isCompleted() {
      return tbl.isCompleted();
   }

   /**
    * Get the row at the specified column.
    */
   @Override
   public MVRow getRow(int r) throws IOException {
      if(rows != null) {
         return rows.get(r);
      }
      
      return tbl.getRow(r + startRow);
   }

   /**
    * Initialize this table block with SubMVQuery.
    */
   @Override
   public void init(SubMVQuery query) {
      tbl.init(query);
   }

   /**
    * Process a single row.
    */
   @Override
   public void addRow(MVRow row) {
      tbl.addRow(row);
   }

   /**
    * Process a single row with dimension only (no aggregate).
    */
   @Override
   public void addDRow(MVRow row) {
      tbl.addDRow(row);
   }

   /**
    * Replace the rows in the block.
    */
   @Override
   public void setRows(XSwappableObjectList<MVRow> rows) {
      this.rows = rows;
   }

   /**
    * Called when all rows have been processed.
    */
   @Override
   public void complete() {
      tbl.complete();
   }

   /**
    * Get the block index of the query.
    */
   @Override
   public int getBlockIndex() {
      return tbl.getBlockIndex();
   }

   /**
    * Set the block index of the query.
    */
   @Override
   public void setBlockIndex(int blockIndex) {
      tbl.setBlockIndex(blockIndex);
   }

   /**
    * Get the col count of this XTableBlock.
    */
   @Override
   public int getColCount() {
      return tbl.getColCount();
   }

   /**
    * Get the measure count of this XTableBlock.
    */
   @Override
   public int getMeasureCount() {
      return tbl.getMeasureCount();
   }

   /**
    * Get the dimension count of this XTableBlock.
    */
   @Override
   public int getDimCount() {
      return tbl.getDimCount();
   }

   /**
    * Get the Header at the specified column.
    */
   @Override
   public String getHeader(int c) {
      return tbl.getHeader(c);
   }

   /**
    * Get the index of the specified column header.
    */
   @Override
   public int indexOfHeader(String header) {
      return tbl.indexOfHeader(header);
   }

   /**
    * Get the index of the specified column.
    */
   @Override
   protected int indexOfCol(String[] cols, String col) {
      return tbl.indexOfCol(cols, col);
   }

   /**
    * Read bytes from buf or tbuf into dbuf. Return the tbuf if there are
    * bytes in the buffer that's not consumed.
    */
   @Override
   protected ByteBuffer readBytes(XReadBuffer buf, ByteBuffer tbuf,
                                  ByteBuffer dbuf, int length) 
         throws IOException
   {
      throw new RuntimeException("readBytes is not supported by PartTableBlock:");
   }

   /**
    * Read this transferable.
    */
   @Override
   public void read(XReadBuffer buf) throws IOException {
      throw new RuntimeException("read is not supported by PartTableBlock:");
   }

   /**
    * Write this transferable.
    */
   @Override
   public void write(XWriteBuffer buf) throws IOException {
      throw new RuntimeException("write is not supported by PartTableBlock:");
   }

   private SubTableBlock tbl;
   private XSwappableObjectList<MVRow> rows;
   private int startRow;
   private int endRow;
}
