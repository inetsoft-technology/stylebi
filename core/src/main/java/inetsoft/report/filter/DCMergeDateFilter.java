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
package inetsoft.report.filter;

import inetsoft.report.TableFilter;
import inetsoft.report.TableLens;
import inetsoft.report.lens.AbstractTableLens;

import java.util.*;

public class DCMergeDateFilter extends AbstractTableLens implements TableFilter {
   public DCMergeDateFilter(TableLens table, int mergeCellCol, int dcRangeCol,
                            Map<GroupTuple, DCMergeDatesCell> dcMergeGroup, boolean chart)
   {
      super();
      this.table = table;
      this.dcMergeGroup = dcMergeGroup;
      this.mergeCellCol = mergeCellCol;
      this.dcRangeCol = dcRangeCol;
      this.chart = chart;

      init();
   }

   /**
    * Collect the dates in the range which is not exist in the data result from database.
    */
   private void init() {
      existingDates = new ArrayList<>();
      rowMapping = new HashMap<>();

      if(mergeCellCol == -1 || mergeCellCol >= table.getColCount()) {
         return;
      }

      table.moreRows(TableLens.EOT);

      for(int i = 0; i < table.getRowCount(); i++) {
         Object obj = table.getObject(i, mergeCellCol);

         if(obj instanceof Date) {
            existingDates.add(((Date) obj).getTime());
         }
      }

      int row = 0;

      for(int i = 0; i < table.getRowCount(); i++) {
         Object cell = getMergeCell(i);

         if(!(cell instanceof DCMergeDatesCell) ||
            (((DCMergeDatesCell) cell).getDates() != null &&
            ((DCMergeDatesCell) cell).getDates().size() > 0))
         {
            rowMapping.put(row, i);
            row++;
         }
      }
   }

   @Override
   public int getRowCount() {
      return rowMapping.size();
   }

   @Override
   public int getColCount() {
      return table.getColCount();
   }

   @Override
   public Object getObject(int r, int c) {
      r = getBaseRowIndex(r);

      if(r < table.getHeaderRowCount() || c != mergeCellCol || mergeCellCol == -1 ||
         dcRangeCol == -1 || dcMergeGroup == null)
      {
         return table.getObject(r, c);
      }

      return getMergeCell(r);
   }

   private Object getMergeCell(int r) {
      if(cache == null) {
         cache = new HashMap<>();
      }

      CacheKey cacheKey = new CacheKey(r, mergeCellCol);
      Object obj = cache.get(cacheKey);

      if(obj != null) {
         return obj;
      }

      Object rangeGroup = table.getObject(r, dcRangeCol);
      obj = table.getObject(r, mergeCellCol);
      GroupTuple tuple = new GroupTuple();

      if(rangeGroup instanceof Date) {
         rangeGroup = new Date(((Date) rangeGroup).getTime());
      }

      tuple.addValue(rangeGroup);

      Object obj0 = obj;

      // to match the tuple date.
      if(obj0 != null && obj instanceof Date) {
         obj0 = new Date(((Date) obj0).getTime());
      }

      tuple.addValue(obj0);
      DCMergeDatesCell result = dcMergeGroup.get(tuple);

      if(result == null) {
         return obj;
      }
      else {
         result = result.clone();
         result.setOriginalDate(obj);
         removeNotExistDates(result);
         result.setToStringAllLevel(!chart);
         cache.put(cacheKey, result);

         return result;
      }
   }

   /**
    * Remove the dates which not exist in the table to make sure label numbers
    * match the bar numbers.
    */
   private void removeNotExistDates(DCMergeDatesCell mergeDatesCell) {
      List<Date> dates = mergeDatesCell == null ? null : mergeDatesCell.getDates();

      if(dates == null) {
         return;
      }

      List<Date> ndates = new ArrayList<>();

      for(int i = 0; i < dates.size(); i++) {
         if(dates.get(i) != null && existingDates.contains(dates.get(i).getTime())) {
            ndates.add(dates.get(i));
         }
      }

      mergeDatesCell.setDates(ndates);
   }

   @Override
   public TableLens getTable() {
      return table;
   }

   @Override
   public void setTable(TableLens table) {
      this.table = table;
   }

   @Override
   public void invalidate() {
      init();
   }

   @Override
   public int getBaseRowIndex(int row) {
      Integer baseRow = rowMapping.get(row);

      return baseRow == null ? -1 : baseRow;
   }

   @Override
   public int getBaseColIndex(int col) {
      return col;
   }

   @Override
   public int getHeaderRowCount() {
      return table.getHeaderRowCount();
   }

   @Override
   public int getHeaderColCount() {
      return table.getHeaderColCount();
   }

   @Override
   public boolean moreRows(int row) {
      if(row >= getRowCount()) {
         return false;
      }

      if(rowMapping != null) {
         row = rowMapping.get(row) == null ? row : rowMapping.get(row);
      }

      return table.moreRows(row);
   }

   @Override
   public String getColumnIdentifier(int col) {
      String identifier = super.getColumnIdentifier(col);
      col = getBaseColIndex(col);

      return identifier == null ? table.getColumnIdentifier(col) : identifier;
   }

   private static class CacheKey {
      private int col;
      private int row;

      public CacheKey(int col, int row) {
         this.col = col;
         this.row = row;
      }

      @Override
      public boolean equals(Object obj) {
         return obj instanceof CacheKey && ((CacheKey) obj).row == row &&
            ((CacheKey) obj).col == col;
      }

      @Override
      public int hashCode() {
         return Integer.hashCode(row) * 1000 + Integer.hashCode(col);
      }
   }

   private TableLens table;
   private Map<GroupTuple, DCMergeDatesCell> dcMergeGroup;
   private Map<CacheKey, Object> cache;
   private int dcRangeCol = -1;
   private int mergeCellCol = -1;
   private List<Long> existingDates;
   private Map<Integer, Integer> rowMapping;
   private boolean chart;
}
