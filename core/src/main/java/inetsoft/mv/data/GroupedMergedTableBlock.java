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

import inetsoft.util.swap.XSwappableObjectList;

import java.io.IOException;
import java.util.Comparator;

/**
 * MergedTableBlock, the grouped XTableBlock as query result on server side.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public final class GroupedMergedTableBlock extends MergedTableBlock {
   /**
    * Constructor.
    */
   public GroupedMergedTableBlock(MVQuery query, SubTableBlock block) {
      super(query, block);

      rows = new XSwappableObjectList<>(MVRow2.class);
      comparator = new MVRow2.RowComparator(query.order);
   }

   /**
    * Add a grouped table block.
    */
   @Override
   public void add(SubTableBlock table) throws IOException {
      int mcnt2 = table.getMeasureCount();
      double[] arr = new double[mcnt2];
      Object[] arr2 = new Object[mcnt2];
      int rcnt2 = table.getRowCount();

      if(rcnt2 == 0) {
         return;
      }

      XSwappableObjectList<MVRow2> nrows = new XSwappableObjectList<>(MVRow2.class);
      int r1 = 0, r2 = 0;

      for(int i = 0; r1 < rcnt || r2 < rcnt2; i++) {
         if((i & 0xff) == 0 && query.isCancelled()) {
            return;
         }

         if(r1 >= rcnt) {
            nrows.add(resetRow(table.getRow(r2), infos, mcnt, arr, arr2, mcnt2));
            r2++;
            continue;
         }
         else if(r2 >= rcnt2) {
            nrows.add(rows.get(r1));
            r1++;
            continue;
         }

         MVRow2 row1 = rows.get(r1);
         MVRow2 row2 = (MVRow2) table.getRow(r2);
         int rc = comparator.compare(row1, row2);

         // add aggregate from row2 to row1
         if(rc == 0) {
            if(dimAggregate) {
               row1.add(row2.aggregates2);
            }
            else {
               row1.add(row2.getDouble(arr, mcnt2));
            }

            nrows.add(row1);
            // since rows are distinct in both table, both rows should
            // be consumed
            r1++;
            r2++;
         }
         // add row2
         else if(rc > 0) {
            nrows.add(resetRow(row2, infos, mcnt, arr, arr2, mcnt2));
            r2++;
         }
         // add row1
         else {
            nrows.add(row1);
            r1++;
         }
      }

      nrows.complete();
      rows.dispose();
      rows = nrows;
      rcnt = rows.size();
   }

   /**
    * Called when add() is finished.
    */
   @Override
   public void complete() {
      if(rows != null) {
         rows.complete();
      }
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

   private XSwappableObjectList<MVRow2> rows;
   private Comparator<MVRow> comparator;
   private int rcnt;
}
