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
package inetsoft.graph.data;

import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.TimeScale;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.*;

/**
 * This class fills in the missing gaps in the time series.
 *
 * @version 11.1, 3/5/2011
 * @author InetSoft Technology Corp
 */
public class TimeSeriesRow implements CalcRow {
   /**
    * Create a time series row. The time series column, time series type,
    * must be specified before it's used.
    */
   public TimeSeriesRow() {
   }

   /**
    * Create a calc row.
    * @param tcol the time series column.
    * @param dtype date column type, a constants defined in TimeScale.
    * @param missingval the measure value for filling missing time gaps.
    */
   public TimeSeriesRow(String tcol, int dtype, Object missingval) {
      this.tcol = tcol;
      this.dtype = dtype;
      this.missingval = missingval;
   }

   /**
    * Set measure checker.
    * @hidden
    */
   public void setMeasureChecker(MeasureChecker checker) {
      mchecker = checker;
   }

   /**
    * Set the time series column name.
    */
   public void setTimeColumn(String tcol) {
      this.tcol = tcol;
   }

   /**
    * Get the time series column name.
    */
   public String getTimeColumn() {
      return tcol;
   }

   /**
    * Set the time series type, one of the constants defined in TimeScale
    * (e.g. YEAR, MONTH).
    */
   public void setTimeType(int dtype) {
      this.dtype = dtype;
   }

   /**
    * Get the time series type.
    */
   public int getTimeType() {
      return dtype;
   }

   /**
    * Set the per measure missing time gap value. If the per measure value
    * is not set, use the global value.
    */
   public void setMissingValue(String measure, Object val) {
      missingmap.put(measure, val);
   }

   /**
    * Get the per measure missing time gap value.
    */
   public Object getMissingValue(String measure) {
      return missingmap.get(measure);
   }

   /**
    * Set the fields for grouping values into subsets for filling in time series.
    */
   public void setGroupFields(String... fields) {
      this.gfields = (fields == null) ? new String[0] : fields;
   }

   /**
    * Get the fields for grouping values into subsets for filling in time series.
    */
   public String[] getGroupFields() {
      return gfields;
   }

   /**
    * Set the outer dimension fields.
    */
   public void setOuterFields(String... fields) {
      this.outerfields = (fields == null) ? new String[0] : fields;
   }

   /**
    * Get the outer dimension fields.
    */
   public String[] getOuterFields() {
      return outerfields;
   }

   /**
    * Get the maximum number of rows to add to fill the gaps.
    */
   public int getMaxGaps() {
      return maxrows;
   }

   /**
    * Set the maximum number of rows to add to fill the gaps.
    */
   public void setMaxGaps(int max) {
      this.maxrows = max;
   }

   /**
    * Calculate the values for the dataset.
    * @return the rows to append to the dataset.
    */
   @Override
   public List<Object[]> calculate(DataSet data) {
      // value list of each line
      Map<Object,List<TVal>> valmap = new HashMap<>();
      // track the min/max of each subgraph
      Map<Object,TVal> maxmap = new HashMap<>();
      Map<Object,TVal> minmap = new HashMap<>();
      // valkey -> outerkey map
      Map keymap = new HashMap();
      TVal minAll = null;
      TVal maxAll = null;
      int rowCount = data.getRowCount();

      // should not fill projected rows. (53019)
      if(data instanceof AbstractDataSet) {
         rowCount = ((AbstractDataSet) data).getRowCountUnprojected();
      }

      for(int i = 0; i < rowCount; i++) {
         Object valkey = createKey(data, gfields, i);
         Object outerkey = createKey(data, outerfields, i);
         Object val = data.getData(tcol, i);
         List vals = valmap.get(valkey);

         if(vals == null) {
            valmap.put(valkey, vals = new ArrayList());
         }

         if(val != null) {

            if(!(val instanceof Date)) {
               continue;
            }

            TVal tval = new TVal(val, i);
            TVal min = minmap.get(outerkey);
            TVal max = maxmap.get(outerkey);

            keymap.put(valkey, outerkey);
            vals.add(tval);

            if(minAll == null || minAll.compareTo(tval) > 0) {
               minAll = tval;
            }

            if(maxAll == null || maxAll.compareTo(tval) < 0) {
               maxAll = tval;
            }

            if(min == null || min.compareTo(tval) > 0) {
               min = tval;
               minmap.put(outerkey, min);
            }

            if(max == null || max.compareTo(tval) < 0) {
               max = tval;
               maxmap.put(outerkey, max);
            }
         }
      }

      List<Object[]> rows = new ArrayList<>();

      for(Object valkey : valmap.keySet()) {
         List<TVal> vals = valmap.get(valkey);
         TVal min, max;

         if(global) {
            min = minAll;
            max = maxAll;
         }
         else {
            Object outerkey = keymap.get(valkey);
            min = minmap.get(outerkey);
            max = maxmap.get(outerkey);
         }

         if(!createRows(data, rows, vals, min, max)) {
            break;
         }
      }

      return rows;
   }

   /**
    * Create the rows for the values.
    * @return true to continue and false to abandon.
    */
   private boolean createRows(DataSet data, List<Object[]> rows,
                              List<TVal> vals, TVal min, TVal max)
   {
      if(vals.size() == 0) {
         return true;
      }

      Collections.sort(vals);

      if(missingval != null && min.compareTo(vals.get(0)) < 0) {
         Object prev = min.value;
         long part0 = getPart(prev, this.dtype);
         long part2 = getPart(vals.get(0).value, this.dtype);

         while(part0 < part2) {
            rows.add(createRow(data, vals.get(0).row, prev, min.value));
            prev = add(prev, 1);
            part0 = getPart(prev, this.dtype);
         }
      }

      TVal lastval = vals.get(vals.size() - 1);

      if(missingval != null && max.compareTo(lastval) > 0) {
         Object prev = add(lastval.value, 1);
         long part0 = getPart(lastval, this.dtype);
         long part2 = getPart(max.value, this.dtype);

         while(part0 <= part2) {
            rows.add(createRow(data, vals.get(0).row, prev, lastval.value));
            prev = add(prev, 1);
            part0 = getPart(prev, this.dtype);
         }
      }

      // part is a long representing date, e.g. monthpart = year*12+month
      long part = getPart(vals.get(0).value, this.dtype);

      for(int i = 1; i < vals.size(); i++) {
         Object prev = vals.get(i - 1).value;
         long part2 = getPart(vals.get(i).value, this.dtype);

         // shouldn't compare (part2 >= part + 1) since for week, the
         // next value for 201252 is 201301 instead of 201253
         while(true) {
            prev = add(prev, 1);
            part = getPart(prev, this.dtype);

            if(part2 <= part) {
               break;
            }

            rows.add(createRow(data, vals.get(i).row, prev, vals.get(i - 1).value));

            if(rows.size() > maxrows) {
               String msg = GTool.getString("viewer.viewsheet.chart.timeSeriesMax");
               //Tool.addUserMessage(msg);
               // showing a warning to end user is more distracting than
               // helpful. in any case, when the max is reached, the graph
               // is generally so over crowed that any truncation is
               // unlikely to be noticeable or significant
               LOG.warn(msg);
               return false;
            }
         }

         part = part2;
      }

      return true;
   }

   /**
    * Create a key for the group column.
    */
   private Object createKey(DataSet data, String[] fields, int r) {
      ArrayList key = new ArrayList();

      for(int i = 0; i < fields.length; i++) {
         key.add(data.getData(fields[i], r));
      }

      return key;
   }

   /**
    * Create a new row. Make a copy of the row at r and replace the date
    * column with dval and the measure with missingval.
    */
   private Object[] createRow(DataSet data, int r, Object dval, Object pdval) {
      Object[] row = new Object[data.getColCount()];
      List<Group> groups = new ArrayList<>();

      for(int i = 0; i < data.getColCount(); i++) {
         String header = data.getHeader(i);

         if(header.equals(tcol)) {
            row[i] = dval;
            // pdval is the previous date group value.
            // This is needed for searching for the previous value in a group
            // for running totals
            groups.add(new Group(header, pdval));
         }
         else if(data.isMeasure(header)) {
            // @by stephenwebster, Fix bug1394210292826
            // Added a way to determine if we are accessing a Running
            // Total column.  If so, we want to carry over the previous
            // rows value when the missingval is non null.
            // @by stephenwebster, Fix bug1402949727054
            // The original fix was too naive, we need to walk back through
            // the dataset passing the current group values for a unique match.
            if(isRunningTotal(header)) {
               // @by stephenwebster for backwards compatibility
               // make sure that non-bar graphs show a gap
               if(missingval == null) {
                  row[i] = null;
               }
               // else find previous value
               else {
                  row[i] = searchData(data, groups, header);
               }
            }
            else if(isFillTimeGap(header)) {
               Object val = missingval;
               // @by stephenwebster
               // for bar graphs always fill with null, zero doesn't make sense.
               // Note that missingval equal to 1 means it is a bar graph
               if(missingval != null && Tool.equals(missingval, 1)) {
                  val = null;
               }

               // for multi-style chart, should not fill in value of measure that is null
               // because it's from the other sub-dataset of the union. (52521)
               boolean valid = mchecker == null && data.getData(header, r) != null
                  || mchecker != null && mchecker.check(data, r, header);

               row[i] = valid ? (missingmap.containsKey(header) ? missingmap.get(header) : val)
                  : null;
            }
         }
         else {
            row[i] = data.getData(header, r);
            groups.add(new Group(header, row[i]));
         }
      }

      return row;
   }

   /*
    * Check should fill time Gap.
    */
   protected boolean isFillTimeGap(String dim) {
      // override in sub Class
      return true;
   }

   /*
    * Checks to see if this field is the Running Total
    * calculated aggregate option. See GraphGenerator.
    */
   protected boolean isRunningTotal(String dim) {
      // override in sub Class
      return false;
   }

   /**
    * Get the comparer to sort data at the specified column.
    * @param col the specified column.
    */
   @Override
   public Comparator getComparator(String col) {
      return null;
   }

   /**
    * Get the date sequence number that uniquely identifies a date in a time series.
    * For example, DAY would be YYYYMMdd, hour would be YYYYMMddHH.
    */
   private long getPart(Object obj, int dtype) {
      if(obj instanceof Date) {
         GregorianCalendar cal = new GregorianCalendar();
         cal.setTime((Date) obj);

         switch(dtype) {
         case TimeScale.SECOND:
            return getPart(obj, TimeScale.MINUTE) * 100 + cal.get(Calendar.SECOND);
         case TimeScale.MINUTE:
             return getPart(obj, TimeScale.HOUR) * 100 + cal.get(Calendar.MINUTE);
          case TimeScale.HOUR:
             return getPart(obj, TimeScale.DAY) * 100 + cal.get(Calendar.HOUR_OF_DAY);
          case TimeScale.DAY:
             return cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR);
          case TimeScale.WEEK:
             return cal.get(Calendar.YEAR) * 100L + cal.get(Calendar.WEEK_OF_YEAR);
          case TimeScale.MONTH:
             return cal.get(Calendar.YEAR) * 100L + cal.get(Calendar.MONTH);
          case TimeScale.QUARTER:
             return cal.get(Calendar.YEAR) * 100L + cal.get(Calendar.MONTH) / 3;
          case TimeScale.YEAR:
          default:
             return cal.get(TimeScale.YEAR);
          }
      }

      return 0;
   }

   /**
    * Add a number of intervals to the date.
    */
   private Object add(Object obj, long n) {
      if(obj instanceof Date) {
         GregorianCalendar cal = new GregorianCalendar();
         cal.setTime((Date) obj);

         if(dtype == TimeScale.QUARTER) {
            n *= 3;
         }

         cal.add(getInterval(dtype), (int) n);

         if(obj instanceof Timestamp) {
            return new Timestamp(cal.getTimeInMillis());
         }
         else if(obj instanceof java.sql.Date) {
            return new java.sql.Date(cal.getTimeInMillis());
         }
         else if(obj instanceof java.sql.Time) {
            return new java.sql.Time(cal.getTimeInMillis());
         }
         else {
            return cal.getTime();
         }
      }

      return obj;
   }

   private int getInterval(int dtype) {
      return dtype == TimeScale.QUARTER ? TimeScale.MONTH : dtype;
   }

   /**
    * Searches dataset for previous group value
    * which corresponds to the targetDim (e.g. running total)
    * @param data DataSet to be searched
    * @param groups A list of group objects to ensure a unique match
    * @param targetDim The header of the dimension whose value should be returned
    * @return The value of the targetDimension for the last group
    */
   private Object searchData(DataSet data, List<Group> groups, String targetDim) {
      for(int i = data.getRowCount(); i >= 0; i--) {
         boolean match = false;

         for(Group group : groups) {
            Object groupValue = data.getData(group.getName(), i);

            if(Tool.equals(group.getValue(), groupValue)) {
               match = true;
            }
            else {
               match = false;
               break;
            }
         }

         if(match) {
            return data.getData(targetDim, i);
         }
      }

      return null;
   }

   @Override
   public boolean isPreColumn() {
      return preCol;
   }

   /**
    * Set whether this the time series should be filled before cacl columns.
    */
   public void setPreColumn(boolean preCol) {
      this.preCol = preCol;
   }

   /**
    * Check if the date range for filling gap should be global across entire data set
    * instead of based on each group.
    */
   public boolean isGlobalRange() {
      return global;
   }

   /**
    * Check the global date range option.
    */
   public void setGlobalRange(boolean global) {
      this.global = global;
   }

   @Override
   public boolean isNoNull() {
      return missingval != null && !Objects.equals(missingval, 1);
   }

   /**
    * The value and row pair.
    */
   private static class TVal implements Comparable {
      public TVal(Object value, int row) {
         this.value = value;
         this.row = row;
      }

      @Override
      public int compareTo(Object val) {
         return ((Comparable) value).compareTo(((TVal) val).value);
      }

      public String toString() {
         return value + "";
      }

      private Object value;
      private int row;
   }

   /**
    * A group object representing the header name and its value
    */
   private static class Group {
      public Group(String name, Object value) {
         this.name = name;
         this.value = value;
      }

      public String getName() {
         return name;
      }

      public Object getValue() {
         return value;
      }

      @Override
      public String toString() {
         return "Group[" + name + ":" + value + "]";
      }

      private String name;
      private Object value;
   }

   /**
    * Internal checker, to check a measure column should shown null or
    * show missage time gap value.
    * @hidden
    */
   public static interface MeasureChecker {
      public boolean check(DataSet data, int r, String measure);
   }

   private String tcol; // time series column
   private int dtype; // date type (YEAR)
   private Object missingval = null; // missing time gap measure value
   private Map missingmap = new HashMap(); // measure -> missing value
   private String[] gfields = {};
   private String[] outerfields = {};
   private int maxrows = 2000;
   private MeasureChecker mchecker;
   private boolean global = true;
   private boolean preCol = false;

   private static final Logger LOG =
      LoggerFactory.getLogger(TimeSeriesRow.class);
}
