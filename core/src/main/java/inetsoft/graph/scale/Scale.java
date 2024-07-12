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
package inetsoft.graph.scale;

import com.inetsoft.build.tern.TernMethod;
import inetsoft.graph.AxisSpec;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphtDataSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;

/**
 * A scale defines the measurement of a dimension. A scale is used to map a
 * value to its position in the logical coordinate space. The actual position
 * of the rendering for the corresponding graph element is determined by the
 * coordinates and their transformations.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class Scale implements Cloneable, Serializable {
   /**
    * Init option to include ticks in scale range.
    */
   public static final int TICKS = 1;
   /**
    * Init option to include zero in scale range.
    */
   public static final int ZERO = 2;
   /**
    * Init option to ignore null values.
    */
   public static final int NO_NULL = 4;
   /**
    * Init option to reserve a small gap at min and max.
    */
   public static final int GAPS = 8;
   /**
    * Ignore leading values with all null measure values. The var fields must be set by setVars().
    * Leading items are trimmed if all var values are null.
    */
   public static final int NO_LEADING_NULL_VAR = 16;
   /**
    * Ignore trailing values with all null measure values. The var fields must be set by setVars().
    * Trailing items are trimmed if all var values are null.
    */
   public static final int NO_TRAILING_NULL_VAR = 32;
   /**
    * Init option to use the actual data range without any change.
    */
   public static final int RAW = 0;

   /**
    * This is a special value that will be mapped to the min of the scale.
    */
   public static final Object MIN_VALUE = new MinValue();
   /**
    * This is a special value that will be mapped to the max of the scale.
    */
   public static final Object MAX_VALUE = new MaxValue();
   /**
    * This is a special value that will be mapped to the mid point of the scale.
    */
   public static final Object MID_VALUE = new MidValue();
   /**
    * This is a special value that will be mapped to the min or zero (if zero
    * is greater than min).
    */
   public static final Object ZERO_VALUE = new ZeroValue();

   /**
    * Get the default scale for a column.
    */
   public static Scale createScale(DataSet data, String... cols) {
      Class cls = data.getType(cols[0]);

      if(cls == null) {
         return new CategoricalScale(cols);
      }
      else if(Number.class.isAssignableFrom(cls)) {
         return new LinearScale(cols);
      }
      else if(Date.class.isAssignableFrom(cls)) {
         return new TimeScale(cols);
      }

      return new CategoricalScale(cols);
   }

   /**
    * Create an empty scale.
    */
   protected Scale() {
      super();
   }

   /**
    * Create a scale for the specified columns.
    */
   protected Scale(String... flds) {
      setFields(flds);
      setDataFields(flds);
   }

   /**
    * Map a value to a logical position using this scale. The subclasses should
    * implement the mapValue() method to map a value on the scale to the
    * logical position.
    * @return double represent the logical position of this value.
    */
   @TernMethod
   public final double map(Object val) {
      if(val instanceof Value) {
         return ((Value) val).getValue(this);
      }

      return mapValue(val);
   }

   /**
    * Map a value to a logical position using this scale.
    * @return double represent the logical position of this value.
    */
   public abstract double mapValue(Object val);

   /**
    * Initialize the scale to use the values in the chartLens.
    * @param data is chart data table.
    */
   public abstract void init(DataSet data);

   /**
    * Get the scale initialization option, e.g. Scale.TICKS | Scale.ZERO.
    */
   @TernMethod
   public int getScaleOption() {
      return scaleOption;
   }

   /**
    * Set the scale initialization option, e.g. Scale.TICKS | Scale.ZERO.
    */
   @TernMethod
   public void setScaleOption(int option) {
      scaleOption = option;
   }

   /**
    * Get the min value on the scale.
    * @return the min value of the scale in logical coordinate.
    */
   public abstract double getMin();

   /**
    * Get the max value on the scale.
    * @return the max value of the scale in logical coordinate.
    */
   public abstract double getMax();

   /**
    * Get the tick positions. The values of the ticks are logical coordinate
    * position same as the values returned by map().
    * @return double[] represent the logical position of each tick.
    */
   public abstract double[] getTicks();

   /**
    * Get the values at each tick.
    * @return Object[] represent values on each tick.
    */
   public abstract Object[] getValues();

   /**
    * Get the columns this scale is used to measure.
    */
   @TernMethod
   public String[] getFields() {
      return fields;
   }

   /**
    * Set the columns this scale is used to measure.
    */
   @TernMethod
   public void setFields(String... fields) {
      this.fields = fields;
   }

   /**
    * Get the columns this scale is initialized from.
    */
   @TernMethod
   public String[] getDataFields() {
      return dfields;
   }

   /**
    * Set the columns this scale is initialized from. This may contain a
    * subset of columns of the fields this scale is used to measure.
    */
   @TernMethod
   public void setDataFields(String... fields) {
      this.dfields = fields;
   }

   /**
    * Get the vars (in graph elements) used when trimming leading/trailing null var values.
    */
   @TernMethod
   public String[] getVars() {
      return mfields;
   }

   /**
    * Set the vars defined in graph elements, which are used to trimming leading/trailing
    * null var values.
    */
   @TernMethod
   public void setVars(String... fields) {
      this.mfields = fields;
   }

   // check if all measures are null
   boolean isNullMeasures(DataSet dset, int row) {
      return mfields != null && mfields.length > 0 &&
         Arrays.stream(mfields).map(field -> dset.getData(field, row))
         .allMatch(v -> v == null);
   }

   /**
    * Set the attribute for creating the axis for this scale.
    */
   @TernMethod
   public void setAxisSpec(AxisSpec axisSpec) {
      this.axisSpec = axisSpec;
   }

   /**
    * Get the associated axis attributes.
    */
   @TernMethod
   public AxisSpec getAxisSpec() {
      return axisSpec;
   }

   /**
    * If the original dataset is transformed, and this scale is applied to a
    * different column, this may be set to contain the name of the original
    * field. This field will be used to get aesthetic attributes instead of
    * the var in element binding.
    * @hidden
    */
   @TernMethod
   public void setMeasure(String ofield) {
      this.origField = ofield;
   }

   /**
    * Get the original field name.
    * @hidden
    */
   @TernMethod
   public String getMeasure() {
      return origField;
   }

   /**
    * Get the selector for selecting data to be plotted.
    */
   public GraphtDataSelector getGraphDataSelector() {
      return selector;
   }

   /**
    * Set the selector for selecting data to be plotted.
    */
   public void setGraphDataSelector(GraphtDataSelector selector) {
      this.selector = selector;
   }

   /**
    * Check if a row should be included on the scale.
    */
   protected boolean isAccepted(DataSet data, int row) {
      return selector == null || selector.accept(data, row, null);
   }

   /**
    * Add two mapped values to get the total value.
    * @param v1 the specified mapped value a.
    * @param v2 the specified mapped value b.
    * @return the new total value.
    */
   @TernMethod
   public double add(double v1, double v2) {
      if(Double.isNaN(v1)) {
         v1 = 0;
      }

      if(Double.isNaN(v2)) {
         v2 = 0;
      }

      return v1 + v2;
   }

   /**
    * Get the number of units if the space is divided into partitions. This is
    * used to calculate the size of shape (e.g. bar) for each data point.
    */
   @TernMethod
   public int getUnitCount() {
      return 1;
   }

   /**
    * Free up storage. The scale will not be used for actual scaling of values after this call.
    * Information used for rendering (e.g. weight) needs to be maintained.
    */
   public void releaseValues(boolean labelVisible) {
   }

   /**
    * Check if ticks are evenly spaced.
    */
   public boolean isUniformInterval() {
      return true;
   }

   @Override
   public Scale clone() {
      try {
         Scale scale = (Scale) super.clone();

         if(fields != null) {
            scale.fields = fields.clone();
         }

         if(dfields != null) {
            scale.dfields = dfields.clone();
         }

         if(mfields != null) {
            scale.mfields = mfields.clone();
         }

         if(axisSpec != null) {
            scale.axisSpec = axisSpec.clone();
         }

         return scale;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone scale", ex);
      }

      return null;
   }

   /**
    * Check if equals another object.
    */
   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof Scale)) {
         return false;
      }

      Scale scale = (Scale) obj;

      if(!getClass().equals(scale.getClass())) {
         return false;
      }

      if(fields.length != scale.fields.length) {
         return false;
      }

      if(dfields.length != scale.dfields.length) {
         return false;
      }

      for(int i = 0; i < fields.length; i++) {
         if(!fields[i].equals(scale.fields[i])) {
            return false;
         }
      }

      for(int i = 0; i < dfields.length; i++) {
         if(!dfields[i].equals(scale.dfields[i])) {
            return false;
         }
      }

      return true;
   }

   public boolean equalsContents(Object obj) {
      return equals(obj) && axisSpec.equals(((Scale) obj).getAxisSpec());
   }

   /**
    * get the original hash code.
    * @hidden
    */
   public int addr() {
      return super.hashCode();
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return getClass().getName() + "@" + System.identityHashCode(this) +
         '['+ Arrays.asList(fields) + ']';
   }

   /**
    * Get the hash code value.
    */
   public int hashCode() {
      int hash = 0;

      for(int i = 0; i < fields.length; i++) {
         if(fields[i] != null) {
            hash += fields[i].hashCode();
         }
      }

      return hash;
   }

   /**
    * This class calculate a dynamic scaled value.
    */
   public abstract static class Value implements Serializable {
      public abstract double getValue(Scale scale);
   }

   private static class MinValue extends Value {
      @Override
      public double getValue(Scale scale) {
         return scale.getMin();
      }
   }

   private static class MaxValue extends Value {
      @Override
      public double getValue(Scale scale) {
         return scale.getMax();
      }
   }

   private static class MidValue extends Value {
      public double getValue(Scale scale) {
         return (scale.getMax() + scale.getMin()) / 2;
      }
   }

   private static class ZeroValue extends Value {
      @Override
      public double getValue(Scale scale) {
         double min = scale.getMin();
         return (min > 0) ? min : 0;
      }
   }

   private String[] fields = {}; // measured fields
   private String[] dfields = {}; // data (init) fields
   private String[] mfields = {}; // measure fields (y)
   private AxisSpec axisSpec = new AxisSpec();
   private int scaleOption = Scale.TICKS | Scale.ZERO;
   private String origField;
   private GraphtDataSelector selector;

   private static final long serialVersionUID = 1L;
   private static final Logger LOG = LoggerFactory.getLogger(Scale.class);
}
