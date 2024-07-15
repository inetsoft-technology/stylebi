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

import inetsoft.report.TableLens;
import inetsoft.report.lens.PagedTableLens;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;

/**
 * MergedTableBlock, the grouped XTableBlock as query result on server side.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
public final class DetailMergedTableBlock extends MergedTableBlock {
   /**
    * Constructor.
    */
   public DetailMergedTableBlock(MVQuery query, SubTableBlock block,
                                 int ccnt, int dcnt, int mcnt, int[] dictIdxes,
                                 Class[] types, boolean[] intcols, boolean[] tscols) 
   {
      super(query, block);

      this.ccnt = ccnt;
      this.dcnt = dcnt;
      this.mcnt = mcnt;
      this.dictIdxes = dictIdxes;
      this.types = types;
      this.intcols = intcols;
      this.tscols = tscols;
      
      tbl = new PagedTableLens() {};
      // bigdecimal is stored as double mv column. map the types to avoid class exception
      // when double is added to a XBDDoubleColumn
      Class[] types2 = Arrays.stream(types)
         .map(type -> type == BigDecimal.class ? Double.class : type)
         .toArray(Class[]::new);
      tbl.setTypes(types2);
      tbl.addRow(headers);
   }

   /**
    * Add a grouped table block.
    */
   @Override
   public void add(SubTableBlock table) throws IOException {
      int cnt = table.getRowCount();
      int mcnt2 = table.getMeasureCount();
      double[] arr = new double[mcnt2];
      Object[] arr2 = new Object[mcnt2];
      int maxrows = query.getMaxRows();

      if(maxrows > 0) {
         int existing = tbl.getRowCount();
         
         if(existing < 0) {
            existing = -existing - 1;
         }
         
         cnt = Math.min(cnt, maxrows - existing + 1);
      }

      for(int i = 0; i < cnt; i++) {
         if((i & 0xFF) == 0 && query.isCancelled()) {
            tbl.complete();
            return;
         }
            
         MVRow2 row = (MVRow2) resetRow(table.getRow(i), infos, mcnt, arr,
					arr2, mcnt2);

         tbl.addRow(row.getObjects(ccnt, dcnt, mcnt, dictIdxes, types, intcols, tscols));
      }
   }

   /**
    * Called when add() is finished.
    */
   @Override
   public void complete() {
      tbl.complete();
   }

   /**
    * Get the row at the specified column.
    */
   @Override
   public MVRow getRow(int r) {
      throw new RuntimeException("getRow is not supported in DetailmergedTableBlock.");
   }

   /**
    * Get the row count of this XTableBlock.
    */
   @Override
   public int getRowCount() {
      return tbl.getRowCount();
   }

   /**
    * Return the contained table lens if there is one.
    */
   @Override
   public TableLens getTableLens() {
      return tbl;
   }

   private PagedTableLens tbl;
   private int dcnt;
   private int mcnt;
   private int ccnt;
   private Class[] types;
   private boolean[] intcols;
   private boolean[] tscols;
   private int[] dictIdxes;
}
