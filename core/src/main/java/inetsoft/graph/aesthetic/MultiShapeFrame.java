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

import com.inetsoft.build.tern.TernMethod;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.Scale;
import inetsoft.util.CoreTool;

/**
 * This is the base class for shape frame the represent multiple variables.
 *
 * @version 10.1
 * @author InetSoft Technology
 */
public abstract class MultiShapeFrame extends ShapeFrame implements MultiFieldFrame {
   /**
    * Set the column associated with this frame.
    */
   @Override
   @TernMethod
   public void setField(String field) {
      if(fields != null && fields.length > 0) {
         fields[0] = field;
      }
      else {
         fields = new String[] {field};
      }
   }

   /**
    * Get the column associated with this frame.
    */
   @Override
   @TernMethod
   public String getField() {
      return (fields != null && fields.length > 0) ? fields[0] : null;
   }

   /**
    * Set the fields for the stems.
    */
   @TernMethod
   public void setFields(String... fields) {
      this.fields = fields;
   }

   /**
    * Get the fields for getting the stems.
    */
   @TernMethod
   public String[] getFields() {
      return fields;
   }

   /**
    * Set the scales for the stem fields.
    */
   @TernMethod
   public void setScales(Scale... scales) {
      this.scales = scales;
   }

   /**
    * Get the scales for the stem fields.
    */
   @TernMethod
   public Scale[] getScales() {
      return scales;
   }

   /**
    * Get the shape for the specified cell.
    * @param data the specified dataset.
    * @param col the name of the specified column.
    * @param row the specified row index.
    */
   @Override
   public GShape getShape(DataSet data, String col, int row) {
      if(scales == null) {
         return null;
      }

      double[] values = new double[scales.length];

      for(int i = 0; i < values.length; i++) {
         values[i] = scales[i].map(data.getData(fields[i], row));
         values[i] = (values[i] - scales[i].getMin()) /
            (scales[i].getMax() - scales[i].getMin());
      }

      return getShape(values);
   }

   /**
    * Get the shape for the specified value.
    */
   @Override
   public GShape getShape(Object val) {
      if(scales == null) {
         return null;
      }

      return getShape(getLegendTuple(val));
   }

   /**
    * Get the tuple for legend.
    * @param val the legend item field name.
    */
   protected double[] getLegendTuple(Object val) {
      double[] values = new double[fields.length];

      for(int i = 0; i < fields.length; i++) {
         values[i] = fields[i].equals(val) ? 1 : 0;
      }

      return values;
   }

   /**
    * Get a shape for the tuple. The values in the tuple have been scaled.
    */
   protected abstract GShape getShape(double... values);

   /**
    * Get the values mapped by this frame.
    */
   @Override
   @TernMethod
   public Object[] getValues() {
      return fields;
   }

   /**
    * Initialize the legend frame with values.
    */
   @Override
   public void init(DataSet data) {
      super.init(data);

      if(fields != null) {
         if(scales == null) {
            Scale scale = null;

            scales = new Scale[fields.length];

            if(isSharedScale()) {
               scale = Scale.createScale(data, fields);
            }

            // we share the same scale across the fields by default so values on
            // one shape is more comparable
            for(int i = 0; i < fields.length; i++) {
               if(!isSharedScale()) {
                  scale = Scale.createScale(data, fields[i]);
               }

               scales[i] = scale;
            }
         }

         for(int i = 0; i < scales.length; i++) {
            if(!isInitialized(scales[i])) {
	       scales[i].setScaleOption(getScaleOption());
               scales[i].init(data);
            }
         }
      }
   }

   /**
    * Check if all fields should share one scale.
    * @return true to share a scale for all fields, false to create a separate
    * scale for each field.
    */
   protected boolean isSharedScale() {
      return true;
   }

   /**
    * Check if this frame has been initialized and is ready to be used.
    */
   @Override
   public boolean isValid() {
      if(scales == null) {
         return false;
      }

      for(int i = 0; i < scales.length; i++) {
         if(!isInitialized(scales[i])) {
            return false;
         }
      }

      return true;
   }

   /**
    * Get the title to show on the legend.
    */
   @Override
   @TernMethod
   public String getTitle() {
      String title = GTool.getString(GTool.getFrameType(getClass()));
      return (fields == null) ? title : CoreTool.arrayToString(fields);
   }

   /**
    * Compare two arrays.
    */
   boolean equalsArray(double[] vs1, double[] vs2) {
      if(vs1.length != vs2.length) {
         return false;
      }

      for(int i = 0; i < vs1.length; i++) {
         if(vs1[i] != vs2[i]) {
            return false;
         }
      }

      return true;
   }

   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      MultiShapeFrame frame = (MultiShapeFrame) obj;

      return CoreTool.equals(fields, frame.fields);
   }

   private String[] fields;
   private Scale[] scales;
}
