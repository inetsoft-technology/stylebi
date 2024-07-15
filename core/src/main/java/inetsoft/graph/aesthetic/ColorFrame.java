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

import com.inetsoft.build.tern.TernMethod;
import inetsoft.graph.data.DataSet;
import inetsoft.util.css.CSSDictionary;
import inetsoft.util.css.CSSParameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.lang.reflect.Method;
import java.util.List;

/**
 * This class defines the common API for all color frames.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public abstract class ColorFrame extends VisualFrame {
   /**
    * Get the color for the specified cell.
    * @param data the specified dataset.
    * @param col the specified column name.
    * @param row the specified row index.
    */
   public abstract Color getColor(DataSet data, String col, int row);

   /**
    * Get the color for the specified value.
    */
   public abstract Color getColor(Object val);

   /**
    * Set the brightness. The return color is adjusted by multiplying the
    * brightness value. A value of one (1) doesn't change the color.
    */
   @TernMethod
   public void setBrightness(double bright) {
      this.bright = bright;
   }

   /**
    * Get the brightness adjustment factor.
    */
   @TernMethod
   public double getBrightness() {
      return bright;
   }

   /**
    * Set whether to convert color to grayscale.
    */
   @TernMethod
   public void setGrayscale(boolean grayscale) {
      this.grayscale = grayscale;
   }

   /**
    * Check whether to convert color to grayscale.
    */
   @TernMethod
   public boolean isGrayscale() {
      return grayscale;
   }

   /**
    * Process the return color to apply any adjustments. This method should be
    * called by the implementation of getColor() before returning a color value.
    */
   protected Color process(Color c, double bright) {
      if(c == null && grayscale) {
         c = StaticColorFrame.DEFAULT_COLOR;
      }

      if(c == null || bright == 1 && !grayscale) {
         return c;
      }

      int a = c.getAlpha();
      int r = c.getRed();
      int g = c.getGreen();
      int b = c.getBlue();

      r = (int) (r * bright);
      g = (int) (g * bright);
      b = (int) (b * bright);

      if(grayscale) {
         double y = 0.3 * r + 0.59 * g + 0.11 * b;
         return new Color((int) y, (int) y, (int) y, a);
      }

      return new Color(r, g, b, a);
   }

   @Override
   boolean isMultiItem(Method getter) throws Exception {
      try {
         Class[] params = {Object.class};
         return super.isMultiItem(getClass().getMethod("getColor", params));
      }
      catch(Exception ex) {
         LOG.warn("Failed to determine if frame is a multi-item color frame", ex);
      }

      return true;
   }

   /**
    * @hidden
    */
   public List<CSSParameter> getParentParams() {
      return parentParams;
   }

   /**
    * @hidden
    */
   public void setParentParams(List<CSSParameter> parentParams) {
      this.parentParams = parentParams;
      updateCSSColors();
   }

   /**
    * @hidden
    */
   protected void updateCSSColors() {
      // override if the color frame allows css colors
   }

   /**
    * @hidden
    */
   public void setCSSDictionary(CSSDictionary cssDictionary) {
      this.cssDictionary = cssDictionary;
      this.isReport = true;
   }

   /**
    * Get css dictionary for this color frame
    * @hidden
    */
   public CSSDictionary getCSSDictionary() {
      if(cssDictionary == null && isReport) {
         cssDictionary = CSSDictionary.getDictionary(null);
      }
      else if(cssDictionary == null) {
         cssDictionary = CSSDictionary.getDictionary();
      }

      return cssDictionary;
   }

   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      ColorFrame frame = (ColorFrame) obj;
      // @by larryl, ignore brightness since we set the brightness lower for
      // line/point/..., if we check brightness, same color frames bound to
      // line and bar would be treated as different, resulting in two legends.
      // return bright == frame.bright && grayscale == frame.grayscale;
      return grayscale == frame.grayscale;
   }

   private double bright = 1;
   private boolean grayscale = false;
   protected List<CSSParameter> parentParams;
   private transient CSSDictionary cssDictionary;
   private boolean isReport;

   private static final Logger LOG = LoggerFactory.getLogger(ColorFrame.class);
}
