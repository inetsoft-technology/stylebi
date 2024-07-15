/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.mv.data;

import inetsoft.util.swap.XSwappableObjectList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * This is a variation of GroupedMergedTableBlock that only handles grouping without
 * aggregation. For grouping only, we are essentially getting a distinct table, without
 * regard for aggregation of aggreggation. As an optimization, the table blocks are
 * merged into one evenly instead of building up a single large list. This avoids
 * repeatedly loading and traversing a large list, which is both cpu and memmory intensive.
 *
 * @author InetSoft Technology
 * @since  13.5
 */
public final class GroupOnlyMergedTableBlock extends MergedTableBlock {
   public GroupOnlyMergedTableBlock(MVQuery query, SubTableBlock block) {
      super(query, block);

      rows = new XSwappableObjectList<>(MVRow2.class);
      comparator = new MVRow2.RowComparator(query.order);
   }

   /**
    * Add a grouped table block.
    */
   @Override
   public void add(SubTableBlock table) throws IOException {
      if(table.getRowCount() > 0) {
         blocks.add(new TableRowList(table));
      }
   }

   /**
    * Called when add() is finished.
    */
   @Override
   public void complete() {
      try {
         while(blocks.size() > 1) {
            RowList list1 = blocks.remove(0);
            RowList list2 = blocks.remove(0);

            blocks.add(merge(list1, list2));
         }

         if(blocks.size() == 1) {
            rows = blocks.get(0).getRows();
         }
         else {
            rows = new XSwappableObjectList<>(MVRow2.class);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to merge rows: " + ex, ex);
      }

      if(rows != null) {
         rows.complete();
         rcnt = rows.size();
      }
   }

   private RowList merge(RowList rows, RowList table) throws IOException {
      XSwappableObjectList<MVRow2> nrows = new XSwappableObjectList<>(MVRow2.class);

      int rcnt2 = table.size();
      int rcnt = rows.size();
      int r1 = 0, r2 = 0;

      for(int i = 0; r1 < rcnt || r2 < rcnt2; i++) {
         if((i & 0xff) == 0 && query.isCancelled()) {
            return new XSwappableRowList(nrows);
         }

         if(r1 >= rcnt) {
            nrows.add(table.get(r2));
            r2++;
            continue;
         }
         else if(r2 >= rcnt2) {
            nrows.add(rows.get(r1));
            r1++;
            continue;
         }

         MVRow2 row1 = rows.get(r1);
         MVRow2 row2 = table.get(r2);
         int rc = comparator.compare(row1, row2);

         if(rc == 0) {
            nrows.add(row1);
            // since rows are distinct in both table, both rows should
            // be consumed
            r1++;
            r2++;
         }
         // add row2
         else if(rc > 0) {
            nrows.add(row2);
            r2++;
         }
         // add row1
         else {
            nrows.add(row1);
            r1++;
         }
      }

      nrows.complete();
      return new XSwappableRowList(nrows);
   }

   /**
    * Get the row at the specified column.
    */
   @Override
   public MVRow getRow(int r) {
      return rows.get(r);
   }

   /**
    * Get the row count of this XTableBlock.
    */
   @Override
   public int getRowCount() {
      return rcnt;
   }

   private interface RowList {
      int size();
      MVRow2 get(int r) throws IOException;

      XSwappableObjectList<MVRow2> getRows() throws IOException;
   }

   private static class XSwappableRowList implements RowList {
      public XSwappableRowList(XSwappableObjectList<MVRow2> rows) {
         this.rows = rows;
      }

      public int size() {
         return rows.size();
      }

      public MVRow2 get(int r) {
         return rows.get(r);
      }

      public XSwappableObjectList<MVRow2> getRows() {
         return rows;
      }

      private XSwappableObjectList<MVRow2> rows;
   }

   private class TableRowList implements RowList {
      public TableRowList(SubTableBlock table) {
         this.table = table;
      }

      public int size() {
         return table.getRowCount();
      }

      public MVRow2 get(int r) throws IOException {
         return (MVRow2) table.getRow(r);
      }

      public XSwappableObjectList<MVRow2> getRows() throws IOException {
         XSwappableObjectList<MVRow2> rows = new XSwappableObjectList<>(MVRow2.class);
         return merge(new XSwappableRowList(rows), this).getRows();
      }

      private SubTableBlock table;
   }

   private XSwappableObjectList<MVRow2> rows;
   private Comparator<MVRow> comparator;
   private List<RowList> blocks = new ArrayList<>();
   private int rcnt;
   private static final Logger LOG = LoggerFactory.getLogger(DictionaryPalette.class);
}
