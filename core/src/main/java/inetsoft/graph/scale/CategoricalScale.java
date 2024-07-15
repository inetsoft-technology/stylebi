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
package inetsoft.graph.scale;

import com.inetsoft.build.tern.*;
import inetsoft.graph.data.*;
import inetsoft.graph.element.GraphtDataSelector;
import inetsoft.graph.internal.*;
import inetsoft.util.CoreTool;
import it.unimi.dsi.fastutil.objects.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A categorical scale is used to map nominal values to their logical position.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
@TernClass(url = "#cshid=CategoricalScale")
public class CategoricalScale extends Scale {
   /**
    * Default constructor. The categorical values must be explicitly set.
    */
   public CategoricalScale() {
   }

   /**
    * Create a categorical scale for the specified fields.
    */
   @TernConstructor
   public CategoricalScale(String... fields) {
      super(fields);
   }

   /**
    * Initialize the scale to use the values in the chartLens.
    * @param data is chart data table.
    */
   @Override
   public void init(DataSet data) {
      String[] cols = getDataFields();
      int option = getScaleOption();

      // use fields if data fields are not defined
      if(cols == null || cols.length == 0) {
         cols = getFields();
      }

      if(cols.length > 0) {
         boolean nonull = (option & NO_NULL) != 0;
         // us the last col to initialize scale, for brush all_col is the last
         // col - [col, all_col]. At present, we need not introduce complexity
         init0(getSortedData(data, cols[cols.length - 1], nonull));
      }
   }

   /**
    * Get the sorted data as an array.
    * @param chart the specified chart lens.
    * @param col the specified column.
    */
   private Object[] getSortedData(DataSet chart, String col, boolean nonull) {
      Set uniqVals = new ObjectOpenHashSet();
      // use a list instead of set to hold the value since the comparator may be null
      // so the values may not be sorted later. this will keep the natural order of the
      // values in that case.
      List list = new ObjectArrayList();
      int sumrows = getCalcRows(chart);
      int rowCount = chart.getRowCount();
      boolean skipped = false;
      hasNull = false;

      // @by ChrisSpagnoli bug1431065201291 2015-5-12
      // AbstractDataSetFilter.getRowCount0() calls data.getRowCount(),
      // which adds getRowCountUnprojected().  So AbstractDataSetFilter.getRowCount()
      // can duplicate rowsProjectedForward.  This is not desired here.
      if(chart instanceof AbstractDataSetFilter &&
         ((AbstractDataSetFilter) chart).getDataSet().getRowCount() < rowCount)
      {
         rowCount = ((AbstractDataSetFilter) chart).getRowCountUnprojected();
      }
      // categorical scale (outer) should not be projected forward
      else if(chart instanceof AbstractDataSet && !projection) {
         rowCount = ((AbstractDataSet) chart).getRowCountUnprojected();
      }

      // if it's outer (facet), don't include the calculated rows (such as SumDataSet)
      // since the calculated rows should only be meaningful in inner charts. (41337)
      if(!projection) {
         rowCount -= sumrows;
      }

      boolean trimLeading = (getScaleOption() & NO_LEADING_NULL_VAR) != 0;
      boolean trimTrailing = (getScaleOption() & NO_TRAILING_NULL_VAR) != 0;
      boolean trimNullMeasure = trimLeading || trimTrailing;
      // value with any non-null measure values
      Set nonullMeasures = new HashSet();

      for(int i = 0; i < rowCount; i++) {
         if(!isAccepted(chart, i)) {
            continue;
         }

         Object val = getScaleValue(chart.getData(col, i));

         if(trimNullMeasure && !nonullMeasures.contains(val)) {
            boolean nullMeasures = isNullMeasures(chart, i);

            if(!nullMeasures) {
               nonullMeasures.add(val);
            }
         }

         if(val == null) {
            hasNull = true;

            if(nonull) {
               skipped = true;
               continue;
            }
         }

         if(!uniqVals.contains(val)) {
            list.add(val);
            uniqVals.add(val);
         }
      }

      // if there is only a null, skipping it may cause display problem
      if(skipped && list.size() == 0) {
         list.add(null);
      }

      Comparator comp = comparator;

      if(comp == null) {
         comp = chart.getComparator(col);
      }

      if(comp != null) {
         comp = DataSetComparator.getComparator(comp, chart);
         Collections.sort(list, sortedOn = new MixedComparator(comp));
      }

      // remove values of all null measure values, leave at least one value.
      if(trimLeading) {
         while(list.size() > 1 && !nonullMeasures.contains(list.get(0))) {
            list.remove(0);
         }
      }

      if(trimTrailing) {
         while(list.size() > 1 && !nonullMeasures.contains(list.get(list.size() - 1))) {
            list.remove(list.size() - 1);
         }
      }

      return list.toArray();
   }

   /**
    * Check if a data set is for a waterfall graph.
    */
   private int getCalcRows(DataSet data) {
      int n = 0;

      if(data instanceof DataSetFilter) {
         while(data instanceof DataSetFilter) {
            n = Math.max(n, ((AbstractDataSet) data).getCalcRowCount());
            data = ((DataSetFilter) data).getDataSet();
         }
      }

      return n;
   }

   /**
    * Override the setScaleOption method to provide the ability to remove null
    * for the notShowNull combobox.
    */
   @Override
   @TernMethod
   public void setScaleOption(int option) {
      boolean ononull = (getScaleOption() & NO_NULL) != 0;
      boolean nnonull = (option & NO_NULL) != 0;
      super.setScaleOption(option);

      if(ononull != nnonull && hasNull && values != null && values.size() > 0) {
         copyOnWrite(false);

         if(nnonull) {
            for(int i = values.size() - 1; i >= 0; i--) {
               if(values.get(i) == null) {
                  values.remove(i);
               }
            }
         }
         else if(!nnonull) {
            values.add(0, null);
         }

         if(values.size() <= 0) {
            values.add(null);
         }

         values.trim();
         values0 = null;
         ticks = null;
      }
   }

   /**
    * Initialize the scale with the supplied value. This values may be trimmed
    * if it exceed the maximum size.
    */
   public void init(Object... vals) {
      sortedOn = null;
      init0(vals);
   }

   private void init0(Object... vals) {
      // if the values are explictly set by setValues, don't initialize again.
      // this is consistent with user set min/max in linear/time scale.
      if(staticValues || values == null) {
         return;
      }

      copyOnWrite(true);
      values.clear();
      values0 = null;
      ticks = null;

      // get default max count
      String mstr = GTool.getProperty("graph.axislabel.maxcount",
                                      GDefaults.AXIS_LABEL_MAX_COUNT + "");
      int count = Math.min(vals.length, Integer.parseInt(mstr));

      for(int i = 0; i < count; i++) {
         values.add(vals[i]);
      }

      if(reversed) {
         Collections.reverse(values);

         if(sortedOn != null) {
            sortedOn = sortedOn.reversed();
         }
      }

      if(vals.length > count) {
         String[] fields = getFields();
         String msg = GTool.getString(
            "viewer.viewsheet.chart.axisLabelCountMax", Double.valueOf(count)) +
            ": " + (fields.length == 0 ? "[]" : fields[0]);
         CoreTool.addUserMessage(msg);
      }

      values.trim();
   }

   /**
    * Initialize the values by copying the values from the other scale. This function
    * assumes the two scales have identical options (e.g. reverse).
    */
   @TernMethod
   public void copyValues(CategoricalScale scale) {
      ticks = null;
      shared = true;
      this.values = scale.values;
      this.sortedOn = scale.sortedOn;
      this.values0 = scale.values0;
   }

   // if values is shared, make a copy
   // @param clear true if an empty list is needed.
   private synchronized void copyOnWrite(boolean clear) {
      if(shared) {
         shared = false;

         if(clear || this.values == null) {
            this.values = new ObjectArrayList();
         }
         else {
            this.values = this.values.clone();
         }
      }
      else if(clear && this.values != null && !this.values.isEmpty()) {
         this.values = new ObjectArrayList();
      }
   }

   /**
    * Map a value to a logical position using this scale.
    * @param val the value need to get the logical position.
    * @return double represent the logical position of this value.
    */
   @Override
   @TernMethod
   public double mapValue(Object val) {
      val = getScaleValue(val);
      int idx = -1;

      // optimization for large number of items
      if(values != null) {
         if(sortedOn != null && values.size() > 9) {
            idx = Collections.binarySearch(values, val, sortedOn);
         }
         else {
            idx = values.indexOf(val);
         }
      }

      if(idx < 0) {
         return Double.NaN;
      }

      // since weight is used in getMax(), the scaled value should be calculated
      // according to the weights too, e.g. parabox.
      if(weightedScale && weightmap != null) {
         if(ticks == null) {
            getTicks();
         }

         if(idx < ticks.length) {
            return ticks[idx];
         }
      }

      return idx + (!fill ? 0.5 : 0);
   }

   private Object getScaleValue(Object val) {
      return val instanceof ScaleValue ? ((ScaleValue) val).getScaleValue()
         : GDefaults.getValue(val);
   }

   /**
    * Get the minimum value on the scale.
    */
   @Override
   @TernMethod
   public double getMin() {
      return 0;
   }

   /**
    * Get the maximum value on the scale.
    */
   @Override
   @TernMethod
   public double getMax() {
      // optimization, avoid cloning ticks in getTicks
      if(ticks == null) {
         getTicks();
      }

      int end = ticks.length - 1;
      double last = (ticks.length > 0) ? ticks[end] : 0;
      double extra = end >= 0 ? getWeightAt(end) : 1;
      // add 1 to max if min == max.
      // add half of weight at end (0.5 for default weight of 1).
      return last + (!fill ? extra / 2 : (grid ? extra : (end == 0 ? 1 : 0)));
   }

   /**
    * Get the tick positions. The values of the ticks are logical coordinate
    * position same as the values returned by map().
    * @return double[] represent the logical position of each tick.
    */
   @Override
   @TernMethod
   public synchronized double[] getTicks() {
      // optimization, cache since getMax/getMin called many times
      if(ticks == null) {
         ticks = new double[values.size()];
         double x = 0;
         double pweight = 0;

         for(int i = 0; i < ticks.length; i++) {
            double weight = getWeightAt(i);

            if(fill) {
               ticks[i] = x;
               x += weight;
            }
            else {
               // not fill, tick at the mid point of the weight interval.
               ticks[i] = (i == 0) ? weight / 2 : ticks[i - 1] + pweight / 2 + weight / 2;
               pweight = weight;
            }
         }
      }

      return ticks.clone();
   }

   /**
    * Get the values at each tick.
    * @return Object[] represent values on each tick.
    */
   @Override
   @TernMethod
   public Object[] getValues() {
      if(values0 != null) {
         return values0.clone();
      }

      return values0 = values != null ? values.toArray() : new Object[0];
   }

   /**
    * Set the values on the scale. The values will be used as the categorical
    * values until the next setValues is called. The init() calls are ignored
    * if the values are explicitly set.
    */
   @TernMethod
   public void setValues(Object... vals) {
      this.values0 = vals.clone();
      this.staticValues = vals.length > 0;
      copyOnWrite(true);
      values.addAll(Arrays.asList(vals));
      values.trim();
      this.sortedOn = null;
   }

   /**
    * Add a value to the categorical list.
    */
   @TernMethod
   public void addValue(Object val) {
      copyOnWrite(false);
      values.add(val);
      values0 = null;
      this.sortedOn = null;
   }

   /**
    * Set whether the scale should fill or leave gaps at two sides.
    */
   @TernMethod
   public void setFill(boolean fill) {
      if(this.fill != fill) {
         this.fill = fill;
         ticks = null;
      }
   }

   /**
    * Check whether the scale should fill or leave gaps at two sides.
    */
   @TernMethod
   public boolean isFill() {
      return fill;
   }

   /**
    * Set if this scale is used as the outer grid of a outer. If true,
    * space is reserved on the right most tick to hold the last tick contents.
    * @hidden
    */
   @TernMethod
   public void setGrid(boolean grid) {
      this.grid = grid;
   }

   /**
    * Check if this scale is used as the outer grid of a outer.
    * @hidden
    */
   @TernMethod
   public boolean isGrid() {
      return grid;
   }

   /**
    * Set whether the scale should be reversed (from largest to smallest).
    */
   @TernMethod
   public void setReversed(boolean reversed) {
      this.reversed = reversed;
   }

   /**
    * Check whether the scale is reversed (from largest to smallest).
    */
   @TernMethod
   public boolean isReversed() {
      return reversed;
   }

   /**
    * Set whether this scale should included projected rows.
    */
   @TernMethod
   public void setProjection(boolean projection) {
      this.projection = projection;
   }

   /**
    * Check whether this scale should included projected rows.
    */
   @TernMethod
   public boolean isProjection() {
      return this.projection;
   }

   @Override
   public void setGraphDataSelector(GraphtDataSelector selector) {
      super.setGraphDataSelector(selector);
      values.clear();
      values0 = null;
   }

   /**
    * Set the weights of label. The size allocated to the item is proportional
    * to the weight.
    * @hidden
    */
   public void setWeight(Object val, double weight) {
      if(weight == 1) {
         if(weightmap != null) {
            weightmap.remove(val);

            if(weightmap.isEmpty()) {
               weightmap = null;
            }
         }
      }
      else {
         if(weightmap == null) {
            weightmap = new Object2DoubleOpenHashMap<>();
         }

         weightmap.put(val, weight);
      }

      ticks = null;
   }

   /**
    * Get weight of a label.
    * @hidden
    */
   public double getWeight(Object val) {
      if(weightmap == null) {
         return 1;
      }

      double weight = weightmap.getOrDefault(val, Double.MIN_VALUE);

      if(val != null && weight == Double.MIN_VALUE) {
         String str = val.toString();
         weight = weightmap.getOrDefault(str, Double.MIN_VALUE);

         if(weight == Double.MIN_VALUE) {
            weight = weightmap.getOrDefault(Boolean.valueOf(str), 1);
         }
      }

      return (weight == Double.MIN_VALUE) ? 1 : weight;
   }

   /**
    * Get weight for the value at the specified index.
    * @hidden
    */
   public double getWeightAt(int idx) {
      return values == null ? getWeight(new IndexKey(idx)) : getWeight(values.get(idx));
   }

   /**
    * Get the maximum weight assigned to this scale. The default is 1 weight is not assigned.
    */
   @TernMethod
   public double getMaxWeight() {
      double max = 1;

      if(weightmap != null) {
         for(double v : weightmap.values().toDoubleArray()) {
            max = Math.max(max, v);
         }
      }

      return max;
   }

   @Override
   public boolean isUniformInterval() {
      return weightmap == null;
   }

   /**
    * Categorical scale is divided into same number of units as the weighted
    * number of ticks plus the gap on each side.
    */
   @Override
   public int getUnitCount() {
      return (int) (getMax() - getMin());
   }

   @Override
   public void releaseValues(boolean labelVisible) {
      // remove values() since for deeply nested facet, there may be hundreds of thousands
      // of axis/scales. the majority of memory is held in values.

      // copy weight information to support getWeightAt()
      if(weightmap != null && !weightmap.isEmpty() && values != null) {
         for(int i = 0; i < values.size(); i++) {
            double weight = getWeight(values.get(i));

            if(weight != 1) {
               weightmap.put(new IndexKey(i), weight);
            }
         }

         weightmap.trim();
      }

      if(!labelVisible) {
         values = null;
      }
      else if(values != null) {
         values.trim();
      }

      values0 = null;
   }

   /**
    * Clone this object.
    */
   @Override
   public CategoricalScale clone() {
      CategoricalScale scale = (CategoricalScale) super.clone();
      scale.values = values != null ? values.clone() : null;

      if(weightmap != null) {
         scale.weightmap = weightmap.clone();
      }

      return scale;
   }

   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      try {
         CategoricalScale scale = (CategoricalScale) obj;
         return Arrays.equals(getValues(), scale.getValues());
      }
      catch(ClassCastException ex) {
         return false;
      }
   }

   /**
    * Check if map() returns weighted value.
    */
   @TernMethod
   public boolean isWeightedScale() {
      return weightedScale;
   }

   /**
    * Set whether map() should return weighted value.
    */
   @TernMethod
   public void setWeightedScale(boolean weightedScale) {
      this.weightedScale = weightedScale;
   }

   /**
    * Get the comparator used for sorting values.
    */
   public Comparator getComparator() {
      return comparator;
   }

   /**
    * Set the comparator used for sorting values.
    */
   public void setComparator(Comparator comparator) {
      this.comparator = comparator;
   }

   /**
    * This interface defines a value that can be mapped to a different value for the
    * scale. If an object implements this interface, the getScaleValue() is called to
    * get the value to be added to the scale, and to be used to map to an index in
    * map() call.
    * @hidden
    */
   public interface ScaleValue {
      Object getScaleValue();
   }

   // represent a index value in weightmap
   private static class IndexKey {
      public IndexKey(int index) {
         this.index = index;
      }

      public boolean equals(Object obj) {
         return obj instanceof IndexKey && ((IndexKey) obj).index == index;
      }

      public int hashCode() {
         return IndexKey.class.hashCode() + index;
      }

      private int index;
   }

   private transient Object[] values0; // cached values (same as values)
   // if value is explicitly set, don't initialize it from dataset
   private boolean staticValues = false;
   private ObjectArrayList values = new ObjectArrayList(); // current values
   private boolean fill = false;
   private boolean grid = false;
   private boolean reversed = false;
   private boolean projection = true;
   private Object2DoubleOpenHashMap<Object> weightmap;
   private transient double[] ticks; // cached ticks
   private Comparator sortedOn;
   private boolean hasNull = false;
   private transient boolean shared = false;
   private boolean weightedScale = true;
   private Comparator comparator;

   private static final long serialVersionUID = 1L;
   private static final Logger LOG = LoggerFactory.getLogger(CategoricalScale.class);
}
