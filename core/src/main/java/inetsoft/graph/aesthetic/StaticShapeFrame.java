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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.*;
import inetsoft.graph.data.DataSet;
import inetsoft.util.CoreTool;

/**
 * Static shape frame defines a static shape for visual objects.
 * If a column is bound to this frame, and the value of the column is a GShape,
 * the value is used as the shape for the row instead of the static shape.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=StaticShapeFrame")
public class StaticShapeFrame extends ShapeFrame {
   /**
    * Default constructor.
    */
   public StaticShapeFrame() {
   }

   /**
    * Create a static shape frame with the specified shape.
    */
   @TernConstructor
   public StaticShapeFrame(GShape shape) {
      setShape(shape);
   }

   /**
    * Create a shape frame.
    * @param field field to get value to map to shapes.
    */
   public StaticShapeFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Get the shape of this static shape frame.
    */
   @TernMethod
   public GShape getShape() {
      return this.shape;
   }

   /**
    * Set the shape of this static shape frame.
    */
   @TernMethod
   public void setShape(GShape shape) {
      this.shape = shape;
   }

   /**
    * Get the shape for negative values.
    */
   @TernMethod
   public GShape getNegativeShape() {
      return negshape;
   }

   /**
    * Set the shape for negative values. If this shape is not set, the regular
    * shape is used for all values.
    */
   @TernMethod
   public void setNegativeShape(GShape negshape) {
      this.negshape = negshape;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      return CoreTool.equals(((StaticShapeFrame) obj).shape, shape) &&
         CoreTool.equals(((StaticShapeFrame) obj).negshape, negshape);
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
    * Get the shape for the value.
    */
   @Override
   @TernMethod
   public GShape getShape(Object val) {
      if(negshape != null && val instanceof Number) {
         if(((Number) val).doubleValue() < 0) {
            return negshape;
         }
      }
      else {
         String field = getField();

         if(field != null) {
            if(val instanceof GShape) {
               return (GShape) val;
            }
            else if(val instanceof String) {
               try {
                  return (GShape) GShape.class.getField((String) val).get(null);
               }
               catch(Exception ex) {
                  // ignore
               }
            }
         }
      }

      return shape;
   }

   /**
    * Get the values mapped by this frame.
    */
   @Override
   @TernMethod
   public Object[] getValues() {
      return null;
   }

   /**
    * Get the title to show on the legend.
    */
   @Override
   @TernMethod
   public String getTitle() {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Static frame never shows legend.
    * @return false
    */
   @Override
   @TernMethod
   public boolean isVisible() {
      return false;
   }

   @Override
   @TernMethod
   public String getUniqueId() {
      return shape != null ? shape.getLegendId() : super.getUniqueId();
   }

   @Override
   public String toString() {
      return super.toString() + "[" + shape + "]";
   }

   private GShape shape = GShape.FILLED_CIRCLE;
   private GShape negshape = null;
   private static final long serialVersionUID = 1L;
}
