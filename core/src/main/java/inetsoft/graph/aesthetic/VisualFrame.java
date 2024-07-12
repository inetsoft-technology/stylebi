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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.TernMethod;
import inetsoft.graph.LegendSpec;
import inetsoft.graph.TextSpec;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.element.GraphtDataSelector;
import inetsoft.graph.scale.*;
import inetsoft.graph.visual.ElementVO;
import inetsoft.uql.asset.NamedRangeRef;
import inetsoft.util.CoreTool;
import inetsoft.util.DefaultComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.*;

/**
 * This class defines the common API for all aesthetic frames.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public abstract class VisualFrame implements Cloneable, Serializable {
   /**
    * Constructor.
    */
   public VisualFrame() {
      super();
   }

   /**
    * Get the title to show on the legend.
    */
   @TernMethod
   public String getTitle() {
      String title = legendSpec.getTitle();

      if(title == null) {
         title = field;
      }

      if(title == null) {
         title = getFrameType();
      }

      return title;
   }

   /**
    * Get the frame type, e.g. Color, Shape.
    */
   @TernMethod
   public String getFrameType() {
      Class cls = getClass();

      while(!cls.getSuperclass().equals(VisualFrame.class)) {
         cls = cls.getSuperclass();
      }

      String name = cls.getName();

      name = name.substring(name.lastIndexOf('.') + 1);

      if(name.endsWith("Frame")) {
         name = name.substring(0, name.length() - 5);
      }

      return name;
   }

   /**
    * Get the values mapped by this frame.
    */
   @TernMethod
   public Object[] getValues() {
      Object[] values = (scale != null) ? scale.getValues() : new Object[0];

      // displaying too many values causes some legend items look almost
      // identical (e.g. shape)
      if(values.length > 6 && scale instanceof LinearScale) {
         values = reduceValues(values);
      }

      return values;
   }

   /**
    * Merge the values to reduce the number of values on the list.
    */
   private Object[] reduceValues(Object[] values) {
      // odd number
      if((values.length % 2) == 1) {
         Object[] arr = new Object[values.length / 2 + 1];

         for(int i = 0; i < arr.length; i++) {
            arr[i] = values[i * 2];
         }

         return arr;
      }
      // even number
      else {
         Object[] arr = new Object[(values.length - 2) / 2 + 2];

         for(int i = 0; i < arr.length; i++) {
            if(i == 0) {
               arr[i] = values[i];
            }
            else if(i == arr.length - 1) {
               arr[i] = values[values.length - 1];
            }
            // take the average of the two
            else {
               int n1 = i * 2 - 1;
               int n2 = i * 2;

               double v = (((Number) values[n1]).doubleValue() +
                           ((Number) values[n2]).doubleValue()) / 2;

               if(v == (int) v) {
                  arr[i] = (int) v;
               }
               else {
                  arr[i] = v;
               }
            }
         }

         return arr;
      }
   }

   /**
    * Set the column associated with this frame.
    */
   @TernMethod
   public void setField(String field) {
      this.field = field;
   }

   /**
    * Get the column associated with this frame.
    */
   @TernMethod
   public String getField() {
      return field;
   }

   /**
    * If field is not bound directly, this is the column the visual effect is applied to.
    * By default it's the same as getField().
    */
   @TernMethod
   public String getVisualField() {
      return getField();
   }

   /**
    * Check if the field's visual is controlled by this frame. By default it matches
    * the field with the visual field.
    */
   @TernMethod
   public boolean isApplicable(String field) {
      return field != null && field.equals(getVisualField());
   }

   /**
    * Set the scale for mapping the value from a dataset to the frame.
    */
   @TernMethod
   public void setScale(Scale scale) {
      this.scale = scale;

      if(scale != null && selector != null) {
         scale.setGraphDataSelector(selector);
      }
   }

   /**
    * Get the scale for mapping the value from a dataset to the frame.
    */
   @TernMethod
   public Scale getScale() {
      return scale;
   }

   /**
    * Initialize the legend frame with values from the dataset.
    */
   public void init(DataSet data) {
      if(field != null && scale == null) {
         createScale(data);
      }

      // init if not already setup
      if(scale != null && !isValid()) {
         scale.setScaleOption(getScaleOption());
         scale.init(data);
      }
   }

   /**
    * Get the legend specification.
    */
   @TernMethod
   public LegendSpec getLegendSpec() {
      if(legendSpec0 == null) {
         legendSpec0 = createLegendSpec();
      }

      return legendSpec0;
   }

   /**
    * Set the legend attributes.
    */
   @TernMethod
   public void setLegendSpec(LegendSpec legendSpec) {
      this.legendSpec = legendSpec;
      legendSpec0 = null;
   }

   /**
    * Merge format from scale to text spec.
    */
   private LegendSpec createLegendSpec() {
      if(scale == null) {
         return legendSpec;
      }

      LegendSpec legend = legendSpec;
      TextSpec spec = legend.getTextSpec();
      TextSpec spec2 = scale.getAxisSpec().getTextSpec();

      if(spec.getFormat() == null && spec2.getFormat() != null) {
         legend = (LegendSpec) legend.clone();
         spec.setFormat(spec2.getFormat());
      }

      return legend;
   }

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

      if(scale != null) {
         scale.setScaleOption(option);
      }
   }

   /**
    * Get the labels of the values to show on the legend. The default
    * implementation will just convert values to labels.
    */
   @TernMethod
   public Object[] getLabels() {
      Object[] vals = getValues();

      if(vals == null) {
         return new Object[0];
      }

      return mapLabels(vals);
   }

   /**
    * Map the labels using text frame.
    */
   private Object[] mapLabels(Object[] vals) {
      if(vals == null) {
         return null;
      }

      Object[] labels = vals.clone();
      TextFrame textFrame = legendSpec.getTextFrame();

      for(int i = 0; i < labels.length; i++) {
         // no need to format the label, VLabel will do it, if here format the
         // label, will duplicate
         labels[i] = vals[i];

         if(textFrame != null) {
            Object olabel = labels[i];
            labels[i] = textFrame.getText(olabel);

            // when script explicitly set mapping, it could be set on formatted string.
            // if the original (e.g. date) is not mapped, try the formatted string and
            // see if it's mapped (45614).
            if(labels[i] != null && labels[i] == olabel &&
               legendSpec.getTextSpec().getFormat() != null && !(olabel instanceof String))
            {
               String nlabel = null;

               try {
                  nlabel = legendSpec.getTextSpec().getFormat().format(labels[i]);
               }
               catch(Exception ex) {
                  nlabel = labels[i].toString();
               }

               Object mapped = textFrame.getText(nlabel);

               if(mapped != nlabel) {
                  labels[i] = mapped;
               }
            }
         }
      }

      return labels;
   }

   /**
    * Check if the frame should be shown as a legend. The default
    * implementation will just check whether there are multiple labels.
    */
   @TernMethod
   public boolean isVisible() {
      try {
         return legendSpec.isVisible() &&
            // if a column is explicitly associated with this frame, we should
            // display the legend regardless of the number of items. otherwise
            // the user won't be able to tell what value (maybe a single value)
            // the color/shape/size represents
            (isMultiItem(null) || getField() != null);
      }
      catch(Exception ex) {
         LOG.warn("Failed to determine if frame is visible", ex);
      }

      return true;
   }

   /**
    * Check if this frame has been initialized and is ready to be used.
    */
   @TernMethod
   public boolean isValid() {
      return scale != null && isInitialized(scale);
   }

   /**
    * Set the frame that is used for displaying the legend for this frame
    * in the case that this frame is merged with the legend frame.
    */
   @TernMethod
   public void setLegendFrame(VisualFrame frame) {
      this.legendFrame = frame;
   }

   /**
    * Get the frame that is used to display the legend for this frame.
    */
   @TernMethod
   public VisualFrame getLegendFrame() {
      return legendFrame;
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
    * Check if a scale has been initialized.
    */
   boolean isInitialized(Scale scale) {
      return scale.getMin() != scale.getMax() && scale.getValues().length > 0;
   }

   /**
    * Check if there are multiple items on the frame.
    * @param getter if this method is passed it, it's used to get the legend
    * value for each item, and only different values are counted as multiple.
    */
   boolean isMultiItem(Method getter) throws Exception {
      Object[] values = getValues();

      if(values == null || values.length <= 1) {
         return false;
      }

      if(getter == null) {
         return true;
      }

      Object obj = null;

      for(int i = 0; i < values.length; i++) {
         Object v = getter.invoke(this, values[i]);

         if(!getLegendSpec().isVisible(v)) {
            continue;
         }

         if(i == 0) {
            obj = v;
         }
         else if(v != null && !v.equals(obj) || obj != null && !obj.equals(v)) {
            return true;
         }
      }

      return false;
   }

   /**
    * Get an unique id to identify a visual frame.
    */
   @TernMethod
   public String getUniqueId() {
      // different order gets different color/shape, so order shouldn't be ignored
      return getShareId(false);
   }

   /**
    * Get id that uniquely identify the column/value bound to this frame.
    */
   @TernMethod
   public String getShareId() {
      return getShareId(true);
   }

   /**
    * @param sort true to sort values in id.
    */
   private String getShareId(boolean sort) {
      Object[] vals = getValues();

      if(vals != null && sort) {
         vals = vals.clone();
         Arrays.sort(vals, new DefaultComparator(true)); // sort in case of different orders
      }

      // ignore different between DataGroup()/ColorGroup(). (57069)
      return NamedRangeRef.getBaseName(getField()) + ":" + CoreTool.arrayToString(vals);
   }

   /**
    * Check if content equals. This is used to detect whether two frames are completely identical,
    * such as to detect any change has been made by script.
    * @hidden
    */
   public boolean equalsContent(Object obj) {
      // most case, it is same as equals
      return equals(obj) && Objects.equals(legendSpec, ((VisualFrame) obj).legendSpec) &&
         Objects.equals(scale, ((VisualFrame) obj).scale);
   }

   /**
    * Get the selected used to select which rows are used for initializing text frame.
    */
   public GraphtDataSelector getGraphDataSelector() {
      return selector;
   }

   /**
    * Set the selected used to select which rows are used for initializing text frame.
    */
   public void setGraphDataSelector(GraphtDataSelector selector) {
      this.selector = selector;

      if(scale != null && selector != null) {
         scale.setGraphDataSelector(selector);
      }
   }

   /**
    * Check if equals another object. The default implementation will just
    * test whether class is equal.
    * Check if two visual frames will produce same legend.
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof VisualFrame)) {
         return false;
      }

      return getEqClass(obj).equals(getEqClass(this)) &&
         // ignore difference between field and __all__field. (58104)
         Objects.equals(ElementVO.getBaseName(getField()),
                        ElementVO.getBaseName(((VisualFrame) obj).getField()));
   }

   /**
    * Get the hashcode of the frame.
    */
   public int hashCode() {
      return getEqClass(this).hashCode();
   }

   /**
    * Get the class for comparing whether two frames are equivalent.
    */
   private Class getEqClass(Object obj) {
      Package pkg = VisualFrame.class.getPackage();

      for(Class clz = obj.getClass(); clz != null; clz = clz.getSuperclass()) {
         if(pkg.equals(clz.getPackage())) {
            return clz;
         }
      }

      return obj.getClass();
   }

   /**
    * Create a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         VisualFrame frame = (VisualFrame) super.clone();
         frame.legendSpec = legendSpec.clone();
         frame.legendSpec0 = null;
         return frame;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone visual frame", ex);
         return null;
      }
   }

   /**
    * Get all column headers.
    */
   static String[] getAllHeaders(DataSet data) {
      String[] headers = new String[data.getColCount()];

      for(int i = 0; i< headers.length; i++) {
         headers[i] = data.getHeader(i);
      }

      return headers;
   }

   @Override
   public String toString() {
      String cls = getClass().toString();
      return cls.substring(cls.lastIndexOf('.') + 1) + "@" + super.hashCode();
   }

   // Try to format value using the text format
   Object formatValue(Object val) {
      Object formatted = val;

      if(val != null && getLegendSpec().getTextSpec().getFormat() != null) {
         try {
            formatted = getLegendSpec().getTextSpec().getFormat().format(val);
         }
         catch(Exception ex) {
            // ignore
         }
      }

      return formatted;
   }

   void createScale(DataSet data) {
      Scale scale = null;

      if(this instanceof CategoricalFrame) {
         CategoricalScale scale0 = new CategoricalScale(getField());
         scale0.setComparator(comparator);
         scale = scale0;
      }
      else {
         scale = Scale.createScale(data, getField());
      }

      scale.setScaleOption(getScaleOption());

      if(scale instanceof CategoricalScale) {
         ((CategoricalScale) scale).setFill(true);
         ((CategoricalScale) scale).setProjection(false);
      }

      setScale(scale);
      scale.init(data);
   }

   private String field = null;
   private Scale scale = null;
   private LegendSpec legendSpec = new LegendSpec();
   private transient LegendSpec legendSpec0 = null; // cached
   private int scaleOption = Scale.TICKS;
   private VisualFrame legendFrame;
   private Comparator comparator;
   private GraphtDataSelector selector;

   private static final Logger LOG = LoggerFactory.getLogger(VisualFrame.class);
}
