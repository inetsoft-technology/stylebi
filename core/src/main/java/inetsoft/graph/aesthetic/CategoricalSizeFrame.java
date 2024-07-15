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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.Scale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * This class defines a size frame for categorical values.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=CategoricalSizeFrame")
public class CategoricalSizeFrame extends SizeFrame implements CategoricalFrame {
   /**
    * Create a size frame for categorical values.
    */
   public CategoricalSizeFrame() {
   }

   /**
    * Create a size frame.
    * @param field field to get value to map to sizes.
    */
   @TernConstructor
   public CategoricalSizeFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Initialize the categorical size frame with categorical values.
    */
   public void init(Object... vals) {
      CategoricalScale scale = new CategoricalScale();

      scale.init(vals);
      setScale(scale);
   }

   /**
    * Initialize the categorical size frame with categorical values and sizes.
    * The value and size array must have identical length. Each value in the
    * value array is assigned a size from the size array at the same position.
    */
   public void init(Object[] vals, double[] sarr) {
      CategoricalScale scale = new CategoricalScale();
      scale.init(vals);
      setScale(scale);

      for(int i = 0; i < vals.length; i++) {
         cmap.put(GTool.toString(vals[i]), sarr[i]);
      }
   }

   /**
    * Initialize the categorical size frame with categorical values from the dimension column.
    */
   @Override
   public void init(DataSet data) {
      if(getField() == null) {
         return;
      }

      createScale(data);
   }

   /**
    * Get the size for the chart object.
    * @param data the specified dataset.
    * @param col the name of the specified column.
    * @param row the specified row index.
    */
   @Override
   public double getSize(DataSet data, String col, int row) {
      Object val = (getField() != null) ? data.getData(getField(), row) : col;
      return getSize(val);
   }

   /**
    * Set the size for a value.
    */
   @TernMethod
   public void setSize(Object val, double size) {
      if(!Double.isNaN(size)) {
         cmap.put(GTool.toString(val), size);
      }
      else {
         cmap.remove(GTool.toString(val));
      }
   }

   /**
    * Check if the value is assigned a static aesthetic value.
    */
   @Override
   @TernMethod
   public boolean isStatic(Object val) {
      return cmap.get(GTool.toString(val)) != null;
   }

   @Override
   @TernMethod
   public Set<Object> getStaticValues() {
      return cmap.keySet();
   }

   @Override
   @TernMethod
   public void clearStatic() {
      cmap.clear();
   }

   /**
    * Get a size for the specified value.
    */
   @Override
   @TernMethod
   public double getSize(Object val) {
      if(cmap.size() > 0) {
         Double size = cmap.get(GTool.toString(val));

         if(size != null) {
            return size;
         }
      }

      Scale scale = getScale();

      if(scale == null) {
         return getSmallest();
      }

      double idx = scale.map(val);

      if(Double.isNaN(idx)) {
         idx = scale.map(GTool.toString(val));
      }

      if(Double.isNaN(idx)) {
         return getSmallest();
      }

      // only one value, use the max size
      if(scale.getValues().length == 1) {
         return getLargest();
      }

      double smax = Math.max(scale.getMax(), 1);
      return getSmallest() + (getLargest() - getSmallest()) * idx / smax;
   }

   @Override
   @TernMethod
   public String getUniqueId() {
      return super.getUniqueId() + new TreeMap(cmap);
   }

   /**
    * Create a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         CategoricalSizeFrame frame = (CategoricalSizeFrame) super.clone();
         frame.cmap = new HashMap<>(cmap);
         return frame;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone size frame", ex);
         return null;
      }
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      return cmap.equals(((CategoricalSizeFrame) obj).cmap);
   }

   private Map<Object, Double> cmap = new HashMap<>();
   private static final long serialVersionUID = 1L;
   private static final Logger LOG = LoggerFactory.getLogger(CategoricalSizeFrame.class);
}
