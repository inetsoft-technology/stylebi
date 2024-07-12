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

import inetsoft.graph.data.DataSet;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.*;

/**
 * This class defines a color frame that uses the column values as encoded/integer RGB values.
 *
 * @version 13.2
 * @author InetSoft Technology
 */
public class ColorValueColorFrame extends ColorFrame implements CategoricalFrame {
   /**
    * Create a color frame for color column.
    */
   public ColorValueColorFrame() {
      super();
   }

   /**
    * Create a color frame for color column.
    */
   public ColorValueColorFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Get the color for the chart object.
    * @param data the specified dataset.
    * @param col the name of the specified column.
    * @param row the specified row index.
    */
   @Override
   public Color getColor(DataSet data, String col, int row) {
      Object val = null;

      if(getField() != null) {
         val = data.getData(getField(), row);
      }
      else {
         val = data.getData(col, row);
      }

      return getColor(val);
   }

   /**
    * Get the color for the specified value.
    */
   @Override
   public Color getColor(Object val) {
      Color color = defaultColor;

      if(val instanceof Number) {
         color = new Color(((Number) val).intValue());
      }
      else if(val != null) {
         try {
            color = Tool.getColorFromHexString(val.toString());
         }
         catch(Exception ex) {
            if(LOG.isDebugEnabled()) {
               LOG.debug("Invalid color found: " + val);
            }
         }
      }

      return process(color, getBrightness());
   }

   /**
    * Set the color to be used if the value is not found in the categorical values.
    */
   public void setDefaultColor(Color color) {
      this.defaultColor = color;
   }

   /**
    * Get the color to be used if the value is not found in the categorical values.
    */
   public Color getDefaultColor() {
      return defaultColor;
   }

   /**
    * Check if the value is assigned a static aesthetic value.
    */
   @Override
   public boolean isStatic(Object val) {
      return true;
   }

   @Override
   public Set<Object> getStaticValues() {
      return new HashSet<>();
   }

   @Override
   public void clearStatic() {
   }

   /**
    * Check if equals another object.
    */
   @Override
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      ColorValueColorFrame frame = (ColorValueColorFrame) obj;

      return Objects.equals(defaultColor, frame.defaultColor);
   }

   private Color defaultColor = null;
   private static final long serialVersionUID = 1L;
   private static final Logger LOG = LoggerFactory.getLogger(ColorValueColorFrame.class);
}
