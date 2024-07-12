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

import com.inetsoft.build.tern.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.Scale;
import inetsoft.util.CoreTool;
import inetsoft.util.DefaultComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class defines a shape frame for categorical values.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=CategoricalShapeFrame")
public class CategoricalShapeFrame extends ShapeFrame implements CategoricalFrame {
   /**
    * Create a shape frame for categorical values.
    */
   public CategoricalShapeFrame() {
      shps = new GShape[] {GShape.CIRCLE, GShape.TRIANGLE, GShape.SQUARE,
         GShape.CROSS, GShape.STAR, GShape.DIAMOND, GShape.XSHAPE,
         GShape.FILLED_CIRCLE, GShape.FILLED_TRIANGLE, GShape.FILLED_SQUARE,
         GShape.FILLED_DIAMOND, GShape.CIRCLE, GShape.TRIANGLE, GShape.SQUARE,
         GShape.CROSS, GShape.STAR, GShape.DIAMOND, GShape.XSHAPE,
         GShape.FILLED_CIRCLE, GShape.FILLED_TRIANGLE, GShape.FILLED_SQUARE,
         GShape.FILLED_DIAMOND, GShape.CIRCLE, GShape.TRIANGLE, GShape.SQUARE,
         GShape.CROSS, GShape.STAR, GShape.DIAMOND, GShape.XSHAPE,
         GShape.FILLED_CIRCLE
      };
   }

   /**
    * Create a shape frame.
    * @param field field to get value to map to shapes.
    */
   @TernConstructor
   public CategoricalShapeFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Initialize the categorical shape frame with categorical values.
    */
   public void init(Object... vals) {
      CategoricalScale scale = new CategoricalScale();

      scale.init(vals);
      setScale(scale);
   }

   /**
    * Initialize the categorical shape frame with categorical values and shapes.
    * The value and shape array must have identical length. Each value in the
    * value array is assigned a shape from the shape array at the same position.
    */
   public void init(Object[] vals, GShape[] shps) {
      this.shps = shps;
      CategoricalScale scale = new CategoricalScale();
      scale.init(vals);
      setScale(scale);
   }

   /**
    * Initialize the categorical shape frame with categorical values from the
    * dimension column.
    */
   @Override
   public void init(DataSet data) {
      if(getField() == null) {
         init(getAllHeaders(data), shps);
         return;
      }

      createScale(data);
   }

   /**
    * Get the shape for the specified cell.
    * @param data the specified dataset.
    * @param col the name of the specified column.
    * @param row the specified row index.
    */
   @Override
   public GShape getShape(DataSet data, String col, int row) {
      Object val = (getField() != null) ? data.getData(getField(), row) : col;
      return getShape(val);
   }

   /**
    * Get a shape for the specified value.
    */
   @Override
   @TernMethod
   public GShape getShape(Object val) {
      if(cmap.size() > 0) {
         GShape shape = cmap.get(GTool.toString(val));

         if(shape != null) {
            return shape;
         }
      }

      Scale scale = getScale();

      if(scale == null) {
         return defaultShape;
      }

      double idx = scale.map(val);

      if(Double.isNaN(idx)) {
         idx = scale.map(GTool.toString(val));
      }

      return Double.isNaN(idx) ? defaultShape : getShape((int) idx);
   }

   /**
    * Set the shape to be used if the value is not found in the categorical values.
    */
   @TernMethod
   public void setDefaultShape(GShape shape) {
      this.defaultShape = shape;
   }

   /**
    * Get the shape to be used if the value is not found in the categorical values.
    */
   @TernMethod
   public GShape getDefaultShape() {
      return defaultShape;
   }

   /**
    * Set the shape for the specified value.
    */
   @TernMethod
   public void setShape(Object val, GShape shape) {
      if(shape != null) {
         cmap.put(GTool.toString(val), shape);
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
    * Get the shape at the specified index.
    */
   @TernMethod
   public GShape getShape(int index) {
      return shps.length == 0 ? null : shps[index % shps.length];
   }

   /**
    * Set the shape at the specified index.
    */
   @TernMethod
   public void setShape(int index, GShape shape) {
      if(shps != null) {
         shps[index % shps.length] = shape;
      }
   }

   /**
    * Get the shape count.
    */
   @TernMethod
   public int getShapeCount() {
      return shps.length;
   }

   /**
    * Get all shapes used by this frame.
    */
   public Stream<GShape> getAllShapes() {
      return Stream.concat(Arrays.stream(shps), cmap.values().stream());
   }

   @Override
   @TernMethod
   public String getUniqueId() {
      List<Object> keys = new ArrayList<>(cmap.keySet());
      keys.sort(new DefaultComparator());
      String fixedShapes = keys.stream().map(k -> k + ":" + cmap.get(k).getLegendId())
         .collect(Collectors.joining());
      return super.getUniqueId() + fixedShapes;
   }

   /**
    * Check if equals another object.
    */
   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      GShape[] shps2 = ((CategoricalShapeFrame) obj).shps;

      if(shps.length != shps2.length) {
         return false;
      }

      for(int i = 0; i < shps.length; i++) {
         if(!CoreTool.equals(shps[i], shps2[i])) {
            return false;
         }
      }

      return cmap.equals(((CategoricalShapeFrame) obj).cmap);
   }

   /**
    * Create a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         CategoricalShapeFrame frame = (CategoricalShapeFrame) super.clone();
         // shapes are mutable so should be deep cloned.
         frame.shps = (GShape[]) CoreTool.clone(shps);
         frame.cmap = new HashMap<>(cmap);

         return frame;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone shape frame", ex);
         return null;
      }
   }

   private GShape[] shps;
   private Map<Object, GShape> cmap = new HashMap<>();
   private GShape defaultShape = null;

   private static final long serialVersionUID = 1L;
   private static final Logger LOG = LoggerFactory.getLogger(CategoricalShapeFrame.class);
}
