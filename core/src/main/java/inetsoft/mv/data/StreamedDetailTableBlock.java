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
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.swap.XSwappableObjectList;
import org.roaringbitmap.IntIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;

/**
 * Streaming record for read once.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public final class StreamedDetailTableBlock extends SubTableBlock {
   /**
    * Create an instance of DetailTableBlock.
    */
   public StreamedDetailTableBlock(DefaultTableBlock block, boolean donly) {
      super();
      this.block = block;
      this.donly = donly;
   }

   /**
    * Set the number of rows to fetch (all rows).
    */
   public void setRange(int nrow, int maxrows) {
      this.nrow = Math.min(nrow, maxrows);
      iter = new RangeIterator(0, nrow);
   }

   /**
    * Set the subset of rows to fetch.
    */
   public void setRange(BitSet rows, int maxrows) {
      nrow = Math.min(rows.rowCount(), maxrows);
      iter = rows.intIterator();
   }

   /**
    * Initialize this table block with SubMVQuery.
    */
   @Override
   public void init(SubMVQuery query) {
      dcnt = query.groups.length;
      order = query.order;
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
      throw new RuntimeException("addRow() not supported in StreamedDetailTableBlock");
   }

   /**
    * Add a detail row (dimension only) to grouping.
    */
   @Override
   public void addDRow(MVRow row) {
      throw new RuntimeException("addDRow() not supported in StreamedDetailTableBlock");
   }

   /**
    * Complete this table block.
    */
   @Override
   public void complete() {
   }

   /**
    * Check if all rows have been loaded.
    */
   @Override
   public boolean isCompleted() {
      return true;
   }

   /**
    * Get the row count of this XTableBlock.
    */
   @Override
   public int getRowCount() {
      return nrow;
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
   public MVRow getRow(int r) throws IOException {
      if(r != curr) {
         throw new RuntimeException("getRow() can only be called consecutively.");
      }

      curr++;
      int baseR = iter.next();

      MVRow row = donly ? block.getDRow(baseR) : block.getRow(baseR);
      // As DefaultTableBlock.getRow() recycles the one Row object, need to
      // clone() it here as it may not be used immediately.  Which
      // occurs with both rdd.collect() and rdd.toLocalIterator(),
      // but not rdd.foreach().
      // @temp larryl, toLocalIterator() is used in MVRowRDDTableLens, consider
      // changing it to use stream server to avoid cloning row here
      return (MVRow) row.clone();
   }

   /**
    * Replace the rows in the block.
    */
   @Override
   public void setRows(XSwappableObjectList<MVRow> rows0) {
      throw new RuntimeException("setRows() not supported in StreamedDetailTableBlock");
   }

   /**
    * Read this transferable.
    */
   @Override
   public void read(XReadBuffer buf) throws IOException {
      throw new RuntimeException("read() not supported in StreamedDetailTableBlock");
   }

   /**
    * Write this transferable.
    */
   @Override
   public void write(XWriteBuffer buf) throws IOException {
      throw new RuntimeException("write() not supported in StreamedDetailTableBlock");
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return super.toString() + '<' + Arrays.asList(headers) + '>';
   }

   private static class RangeIterator implements IntIterator {
      public RangeIterator(int start, int end) {
         this.curr = start - 1;
         this.start = start;
         this.end = end - 1;
      }

      @Override
      public boolean hasNext() {
         return curr < end;
      }

      @Override
      public int next() {
         return ++curr;
      }

      @Override
      public IntIterator clone() {
         return new RangeIterator(start, end);
      }

      private int curr = -1;
      private int start;
      private int end;
   }

   private IntIterator iter;
   private int curr = 0;
   private int nrow;
   private DefaultTableBlock block;
   private boolean donly;
   private static final Logger LOG =
      LoggerFactory.getLogger(DetailTableBlock.class);
}
