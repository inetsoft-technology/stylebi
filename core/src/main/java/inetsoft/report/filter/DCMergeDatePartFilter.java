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
package inetsoft.report.filter;

import inetsoft.report.*;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.AbstractTableLens;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.List;
import java.util.*;

public class DCMergeDatePartFilter extends AbstractTableLens implements TableFilter {

   public DCMergeDatePartFilter(TableLens table, List<XDimensionRef> dcExtraRefs,
                                XDimensionRef partRef, XDimensionRef dateGroupRef,
                                XDimensionRef weekOfYearAuxiliaryRef)
   {
      super();
      this.table = table;
      this.dcExtraRefs = dcExtraRefs;
      this.partRef = partRef;
      this.dateGroupRef = dateGroupRef;
      this.weekOfYearAuxiliaryRef = weekOfYearAuxiliaryRef;
      updateVisibleDcExtraRefs(dcExtraRefs);

      init();
   }

   /**
    * Collect the dates in the range which is not exist in the data result from database.
    */
   private void init() {
      if(table == null) {
         return;
      }

      refsIndex = new HashMap<>();

      for(XDimensionRef dcExtraRef : dcExtraRefs) {
         int index = Util.findColumn(table, dcExtraRef.getFullName());

         if(index < 0 && !isIngoreDcTemp(dcExtraRef)) {
            invalid = true;
            return;
         }
         else {
            refsIndex.put(dcExtraRef.getFullName(), index);
         }
      }

      int partRefIndex = Util.findColumn(table, partRef.getFullName());

      if(partRefIndex < 0) {
         invalid = true;
      }

      refsIndex.put(partRef.getFullName(), partRefIndex);

      if(dateGroupRef != null) {
         refsIndex.put(dateGroupRef.getFullName(),
            Util.findColumn(table, dateGroupRef.getFullName()));
      }

      if(weekOfYearAuxiliaryRef != null) {
         refsIndex.put(weekOfYearAuxiliaryRef.getFullName(),
            Util.findColumn(table, weekOfYearAuxiliaryRef.getFullName()));
      }
   }

   @Override
   public int getRowCount() {
      return table.getRowCount();
   }

   @Override
   public int getColCount() {
      return table.getColCount();
   }

   @Override
   public Object getObject(int r, int c) {
      Integer partRefIndex = partRef != null ? refsIndex.get(partRef.getFullName()) : null;

      if(table.getHeaderRowCount() <= r && !invalid && partRefIndex != null && partRefIndex >= 0 &&
         c == partRefIndex)
      {
         MergePartCell mergePartCell = new MergePartCell(table.getObject(r, c));

         for(XDimensionRef dcExtraRef : dcExtraRefs) {
            Object value = table.getObject(r, refsIndex.get(dcExtraRef.getFullName()));

            if(isIngoreDcTemp(dcExtraRef)) {
               mergePartCell.setOriginalRawDate(value);
               continue;
            }

            value = fixWeekOfYear(dcExtraRef, value, r);
            mergePartCell.addValue(value);
         }

         mergePartCell.addValue(table.getObject(r, partRefIndex));

         if(dateGroupRef != null) {
            Integer index = refsIndex.get(dateGroupRef.getFullName());

            if(index != null && index >= 0) {
               mergePartCell.setDateGroupValue(table.getObject(r, index));
            }
         }

         if(weekOfYearAuxiliaryRef != null) {
            Integer index = refsIndex.get(weekOfYearAuxiliaryRef.getFullName());

            if(index != null && index >= 0) {
               mergePartCell.setAuxiliaryQuarterOfYear(table.getObject(r, index));
            }
         }

         return mergePartCell;
      }
      else {
         return table.getObject(r, c);
      }
   }

   private Object fixWeekOfYear(XDimensionRef dcExtraRef, Object value, int r) {
      Integer auxiliaryRefIndex = weekOfYearAuxiliaryRef != null ?
         refsIndex.get(weekOfYearAuxiliaryRef.getFullName()) : null;
      Integer dateGroupIndex = dateGroupRef != null ? refsIndex.get(dateGroupRef.getFullName()) :
         null;

      if(dcExtraRef.getDateLevel() == DateRangeRef.WEEK_OF_YEAR_PART &&
         auxiliaryRefIndex != null && auxiliaryRefIndex >= 0 && dateGroupIndex != null &&
         dateGroupIndex >= 0)
      {
         Object quarterOfYear = table.getObject(r,
            refsIndex.get(weekOfYearAuxiliaryRef.getFullName()));
         Object dateValue = table.getObject(r, dateGroupIndex);

         if(value instanceof Number && ((Number) value).intValue() == 1 &&
            quarterOfYear instanceof Number && ((Number) quarterOfYear).intValue() == 4 &&
            dateValue instanceof Date)
         {
            Calendar calendar = new GregorianCalendar();
            calendar.setFirstDayOfWeek(Tool.getFirstDayOfWeek());
            calendar.setTime((Date) dateValue);

            return calendar.getActualMaximum(Calendar.WEEK_OF_YEAR) + 1;
         }
      }

      return value;
   }

   @Override
   public void setObject(int r, int c, Object v) {
      r = getBaseRowIndex(r);
      c = getBaseColIndex(c);

      table.setObject(r, c, v);
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
      return row;
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
      return table.moreRows(row);
   }

   @Override
   public String getColumnIdentifier(int col) {
      String identifier = super.getColumnIdentifier(col);
      col = getBaseColIndex(col);

      return identifier == null ? table.getColumnIdentifier(col) : identifier;
   }

   @Override
   public TableDataDescriptor getDescriptor() {
      return table.getDescriptor();
   }

   @Override
   public Dimension getSpan(int r, int c) {
      return table.getSpan(r, c);
   }

   private void updateVisibleDcExtraRefs(List<XDimensionRef> dcExtraRefs) {
      visibleDcExtraRefs = new ArrayList<>();

      for(int i = 0; dcExtraRefs != null && i < dcExtraRefs.size(); i++) {
         XDimensionRef ref = dcExtraRefs.get(i);

         if(!isIngoreDcTemp(ref)) {
            visibleDcExtraRefs.add(ref);
         }
      }
   }

   private boolean isIngoreDcTemp(XDimensionRef ref) {
      return ref instanceof VSDimensionRef && ((VSDimensionRef) ref).isIgnoreDcTemp();
   }


   public class MergePartCell implements DCMergeCell, Comparable, Cloneable {
      public MergePartCell(Object originalValue) {
         this.originalValue = originalValue;
      }

      private void setDateGroupValue(Object originalValue) {
         this.dateGroupValue = originalValue;
      }

      public Object getDateGroupValue() {
         return dateGroupValue;
      }

      @Override
      public Object getOriginalData() {
         return this.originalValue;
      }

      public MergePartCell getEquivalenceCell() {
         if(dateGroupRef == null || !(getDateGroupValue() instanceof Date)) {
            return null;
         }

         Calendar cal = CoreTool.calendar.get();
         cal.setTime((Date) getDateGroupValue());

         List<XDimensionRef> mergedRefs = getMergedRefs();

         if(mergedRefs == null || mergedRefs.size() == 0) {
            return null;
         }

         int minimalDaysInFirstWeek = cal.getMinimalDaysInFirstWeek();

         try {
            for(int i = 0; i < mergedRefs.size(); i++) {
               XDimensionRef mergedRef = mergedRefs.get(i);
               Object value = getValue(i);

               if(mergedRef.getFullName().startsWith("WeekOfYear(") && value instanceof Integer) {
                  cal.setMinimalDaysInFirstWeek(7);
                  int weekMonthOfYear = (Integer) value;
                  cal.set(Calendar.MONTH, weekMonthOfYear / 10 - 1);
                  cal.set(Calendar.WEEK_OF_MONTH, weekMonthOfYear % 10);
                  int equivalenceValue = (cal.get(Calendar.MONTH) + 1) * 10 + cal.get(Calendar.WEEK_OF_MONTH);

                  if(weekMonthOfYear != equivalenceValue) {
                     MergePartCell equivalenceCell = new MergePartCell(this.originalValue);
                     equivalenceCell.dateGroupValue = dateGroupValue;
                     equivalenceCell.quarterOfYear = quarterOfYear;
                     ArrayList<Object> newValues = new ArrayList<>(values);
                     equivalenceCell.values = newValues;
                     newValues.set(i, equivalenceValue);

                     return equivalenceCell;
                  }
               }
            }
         }
         finally {
            cal.setMinimalDaysInFirstWeek(minimalDaysInFirstWeek);
         }

         return null;
      }

      private void addValue(Object value) {
         if(values == null) {
            values = new ArrayList<>();
         }

         values.add(value);
      }

      /**
       * Get the value of the dimension in the merge cell.
       *
       * @param index value index;
       */
      public Object getValue(int index) {
         if(index >= 0 && index < values.size()) {
            return values.get(index);
         }

         return null;
      }

      /**
       * Get the merge cell part ref.
       */
      public XDimensionRef getPartRef() {
         return partRef;
      }

      /**
       * Get the all merged dimensions in the cell.
       */
      public List<XDimensionRef> getMergedRefs() {
         List<XDimensionRef> refs = new ArrayList<>(visibleDcExtraRefs);
         refs.add(partRef);

         return refs;
      }

      public int getAuxiliaryQuarterOfYear() {
         return quarterOfYear;
      }

      private void setAuxiliaryQuarterOfYear(Object value) {
         if(value instanceof Number) {
            quarterOfYear = ((Number) value).intValue();
         }
      }

      @Override
      public int hashCode() {
         return values == null ? 0 : values.hashCode();
      }

      @Override
      public boolean equals(Object obj) {
         return obj instanceof MergePartCell && Tool.equals(values, ((MergePartCell) obj).values);
      }

      @Override
      public MergePartCell clone() {
         try {
            MergePartCell cloneCell = (MergePartCell) super.clone();
            cloneCell.values = (List<Object>) Tool.clone(values);
            cloneCell.originalValue = Tool.clone(originalValue);
            cloneCell.dateGroupValue = Tool.clone(dateGroupValue);

            return cloneCell;
         }
         catch(Exception ignore) {
         }

         return null;
      }

      public MergePartCell copyCell(Object newDateGroupValue) {
         MergePartCell cloneCell = clone();
         cloneCell.values = (List<Object>) Tool.clone(values);
         cloneCell.originalValue = Tool.clone(originalValue);
         cloneCell.dateGroupValue = newDateGroupValue;

         return cloneCell;
      }

      @Override
      public String toString() {
         if(this.values == null) {
            return "";
         }

         StringBuffer sf = new StringBuffer();

         for(int i = 0; i < values.size(); i++) {
            sf.append(values.get(i));

            if(i < values.size() - 1) {
               sf.append("-");
            }
         }

         return sf.toString();
      }

      @Override
      public int compareTo(Object o) {
         if(!(o instanceof MergePartCell)) {
            return 1;
         }

         List<Object> v1Values = values == null ? new ArrayList<>() : values;
         List<Object> v2Values = ((MergePartCell) o).values == null ? new ArrayList<>() :
            ((MergePartCell) o).values;

         for(int i = 0; i < v1Values.size(); i++) {
            if(i >= v2Values.size()) {
               return 1;
            }

            int compare = Tool.compare(v1Values.get(i), v2Values.get(i));

            if(compare != 0) {
               return compare;
            }
         }

         return 0;
      }

      public Object getOriginalRawDate() {
         return originalRawDate;
      }

      public void setOriginalRawDate(Object originalRawDate) {
         this.originalRawDate = originalRawDate;
      }

      private List<Object> values;
      private Object originalValue;
      private Object dateGroupValue;
      private Object originalRawDate;
      private int quarterOfYear = -1;
   }

   private TableLens table;
   private List<XDimensionRef> dcExtraRefs;
   private List<XDimensionRef> visibleDcExtraRefs;
   private Map<String, Integer> refsIndex;
   private XDimensionRef partRef;
   private XDimensionRef dateGroupRef;
   private XDimensionRef weekOfYearAuxiliaryRef;
   private boolean invalid;
   private static final Logger LOG = LoggerFactory.getLogger(DCMergeDatePartFilter.class);
}
