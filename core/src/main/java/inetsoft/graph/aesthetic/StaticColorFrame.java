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
import inetsoft.util.Tool;
import inetsoft.util.css.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

/**
 * A color frame with a static color.
 * If a column is bound to this frame, and the value of the column is a number,
 * or color, the value is used as the color for the row instead of the
 * static color.
 *
 * @version 10.0
 * @author InetSoft Technology Corp.
 */
@TernClass(url = "#cshid=StaticColorFrame")
public class StaticColorFrame extends ColorFrame {
   /**
    * Default constructor.
    */
   public StaticColorFrame() {
      super();
   }

   /**
    * Create a static color frame with the specified color.
    */
   public StaticColorFrame(Color color) {
      setUserColor(color);
   }

   /**
    * Create a color frame. The specified column should contain color or number
    * as RGB value to be used as color for each row.
    * @param field field to get value to map to color.
    */
   @TernConstructor
   public StaticColorFrame(String field) {
      setField(field);
   }

   /**
    * Get the static color value of the static color frame.
    */
   @TernMethod
   public Color getColor() {
      return userColor != null ? userColor : cssColor != null ? cssColor : defaultColor;
   }

   /**
    * Set the static color value of the static color frame.
    */
   @TernMethod
   public void setColor(Color color) {
      setUserColor(color);
   }

   /**
    * Set the static color value of the static color frame.
    * @hidden
    */
   public void setDefaultColor(Color color) {
      this.defaultColor = color;
   }

   /**
    * Get the color for negative values.
    */
   @TernMethod
   public Color getNegativeColor() {
      return negcolor;
   }

   /**
    * Set the color for negative values. If this color is not set, the regular
    * color is used for all values.
    */
   @TernMethod
   public void setNegativeColor(Color negcolor) {
      this.negcolor = negcolor;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      StaticColorFrame frame2 = (StaticColorFrame) obj;
      return CoreTool.equals(defaultColor, frame2.defaultColor) &&
         CoreTool.equals(negcolor, frame2.negcolor) &&
         Tool.equals(cssColor, frame2.cssColor) &&
         Tool.equals(userColor, frame2.userColor);
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

   /**
    * Get the color for the specified cell.
    * @param data the specified dataset.
    * @param col the specified column name.
    * @param row the specified row index.
    */
   @Override
   public Color getColor(DataSet data, String col, int row) {
      Object val = (getField() != null) ? data.getData(getField(), row) : col;
      return getColor(val);
   }

   /**
    * Get the color for the specified value.
    */
   @Override
   @TernMethod
   public Color getColor(Object val) {
      Color clr = getColor();

      if(negcolor != null && val instanceof Number) {
         if(((Number) val).doubleValue() < 0) {
            clr = negcolor;
         }
      }
      else if(getField() != null) {
         if(val instanceof Number) {
            clr = new Color(((Number) val).intValue());
         }
         else if(val instanceof Color) {
            clr = (Color) val;
         }
      }

      return process(clr, getBrightness());
   }

   /**
    * @hidden
    */
   public Color getDefaultColor() {
      return defaultColor;
   }

   /**
    * @hidden
    */
   public Color getCssColor() {
      return cssColor;
   }

   /**
    * @hidden
    */
   public void setCssColor(Color cssColor) {
      this.cssColor = cssColor;
   }

   /**
    * @hidden
    */
   public Color getUserColor() {
      return userColor;
   }

   /**
    * @hidden
    */
   public void setUserColor(Color userColor) {
      this.userColor = userColor;
   }

   /**
    * @hidden
    */
   public int getIndex() {
      return index;
   }

   /**
    * @hidden
    */
   public void setIndex(int index) {
      this.index = index;
      updateCSSColors();
   }

   @Override
   protected void updateCSSColors() {
      if(parentParams != null) {
         CSSDictionary cssDictionary = getCSSDictionary();
         CSSParameter cssParam = new CSSParameter(CSSConstants.CHART_PALETTE,
                                                  null, null, new CSSAttr("index", index + 1 + ""));
         cssColor = cssDictionary.getForeground(CSSParameter.getAllCSSParams(parentParams, cssParam));
      }
      else {
         cssColor = null;
      }
   }

   @Override
   public boolean isApplicable(String field) {
      // apply brushing color. (57612)
      return true;
   }

   @Override
   @TernMethod
   public String getUniqueId() {
      return "StaticColorFrame:" + getColor();
   }

   /**
    * Create a copy of this object.
    */
   @Override
   public Object clone() {
      try {
         StaticColorFrame frame = (StaticColorFrame) super.clone();
         frame.defaultColor = defaultColor;
         frame.cssColor = cssColor;
         frame.userColor = userColor;
         frame.index = index;
         return frame;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone static color frame", ex);
         return null;
      }
   }

   @Override
   public String toString() {
      return super.toString() + "[" + userColor + "]";
   }

   static final Color DEFAULT_COLOR = CategoricalColorFrame.COLOR_PALETTE[0];

   private Color defaultColor = DEFAULT_COLOR;
   private Color cssColor;
   private Color userColor;
   private Color negcolor = null;
   private int index = -1;

   private static final long serialVersionUID = 1L;
   private static final Logger LOG =
      LoggerFactory.getLogger(StaticColorFrame.class);
}
