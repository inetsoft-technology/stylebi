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
package inetsoft.report.painter;

import inetsoft.report.Presenter;
import inetsoft.util.Catalog;

import java.awt.*;
import java.util.function.DoubleSupplier;

/**
 * The Bar2Presenter presents a number as a horizontal bar. The Bar2Presenter
 * handles both positive number and negative number. The area is divided
 * into left and right portions. The negative number is painted as a
 * horizontal bar that grows from the center to the left. The positive
 * number is painted as a bar that grows from the center to the right.
 * <p>
 * This presenter can be used in a table cell to display numbers as
 * horizontal bars.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class Bar2Presenter implements AutoMaximumPresenter {
   /**
    * Create a default counter presenter.
    */
   public Bar2Presenter() {
   }

   /**
    * Create a counter presenter with the max value of the bar, and
    * the color of the bar.
    * @param max bar maximum value.
    * @param color bar color.
    * @param neg negative bar color.
    */
   public Bar2Presenter(double max, Color color, Color neg) {
      this.max = max;
      this.color = color;
      this.neg = neg;
   }

   /**
    * Get the color of the bar.
    */
   public Color getColor() {
      return color;
   }

   /**
    * Set the color of the bar.
    */
   public void setColor(Color color) {
      this.color = color;
   }

   /**
    * Get the color of the negative bar.
    */
   public Color getNegativeColor() {
      return neg;
   }

   /**
    * Set the color of the negative bar.
    */
   public void setNegativeColor(Color color) {
      this.neg = color;
   }

   /**
    * Get the maximum value.
    */
   public double getMaximum() {
      return max;
   }

   /**
    * Set the maximum value.
    */
   public void setMaximum(double max) {
      this.max = max;
   }

   /**
    * Check if the bar is scaled to the maximum of the column data instead of the
    * configured maximum.
    */
   @Override
   public boolean isAutoMaximum() {
      return autoMaximum;
   }

   /**
    * Set whether the bar is scaled to the maximum of the column data. When no positive
    * maximum is found in the data, the configured maximum is used.
    */
   public void setAutoMaximum(boolean autoMaximum) {
      this.autoMaximum = autoMaximum;
      this.autoMaximumSet = true;
   }

   /**
    * Scale to the column's data in the modern style, unless the author set Auto Maximum either
    * way. This is not a bean property, so it does not show in the presenter dialog or get saved.
    */
   public void useModernAutoMaximum() {
      if(!autoMaximumSet) {
         autoMaximum = true;
      }
   }

   @Override
   public boolean absoluteMaximum() {
      return true;
   }

   @Override
   public Presenter withMaximumResolver(DoubleSupplier resolver) {
      Bar2Presenter copy = new Bar2Presenter();
      copy.color = color;
      copy.neg = neg;
      copy.psize = psize == null ? null : new Dimension(psize);
      copy.max = max;
      copy.bg = bg;
      copy.autoMaximum = autoMaximum;
      copy.autoMaximumSet = autoMaximumSet;
      copy.maxResolver = resolver;
      return copy;
   }

   /**
    * Get the maximum used for drawing: the data maximum when auto maximum is on and one is
    * available, otherwise the configured maximum.
    */
   private double getDrawingMaximum() {
      if(autoMaximum && maxResolver != null) {
         double dataMax = maxResolver.getAsDouble();

         if(dataMax > 0 && !Double.isInfinite(dataMax)) {
            return dataMax;
         }
      }

      return max;
   }

   /**
    * Paint an object at the specified location.
    * @param g graphical context.
    * @param v object value.
    * @param x x coordinate of the left edge of the paint area.
    * @param y y coordinate of the upper edge of the paint area.
    * @param w area width.
    * @param h area height.
    */
   @Override
   public void paint(Graphics g, Object v, int x, int y, int w, int h) {
      if(v != null && v instanceof Number) {
         Shape clip = g.getClip();
         Color oc = g.getColor();

         g.clipRect(x, y, w, h);

         x += w / 2;
         double max = getDrawingMaximum();
         double n = ((Number) v).doubleValue();

         // @by galr added the ceil so small values in the presenter will
         // not disappear (0.5 could be rounded to 0 and thus an empty bar).
         if(n < 0) {
            int width = (int) Math.ceil(-n * w / (2 * max));

            g.setColor(neg);
            g.fillRect(x - width, y + 2, width, h - 4);
         }
         else {
            int width = (int) Math.ceil(n * w / (2 * max));

            if(color != null) {
               g.setColor(color);
            }

            g.fillRect(x, y + 2, width, h - 4);
         }

         g.setColor(oc);
         g.setClip(clip);
      }
   }

   /**
    * Calculate the preferred size of the object representation.
    * @param v object value.
    * @return preferred size.
    */
   @Override
   public Dimension getPreferredSize(Object v) {
      return psize;
   }

   /**
    * Change the preferred size of the presenter.
    */
   public void setPreferredSize(Dimension psize) {
      this.psize = new Dimension(psize);
   }

   /**
    * Check if the presenter can handle this type of objects.
    * @param type object type.
    * @return true if the presenter can handle this type.
    */
   @Override
   public boolean isPresenterOf(Class type) {
      return Number.class.isAssignableFrom(type);
   }

   /**
    * Check if the presenter can handle this particular object. Normally
    * a presenter handles a class of objects, which is checked by the
    * isPresenterOf(Class) method. If this presenter does not care about
    * the value in the object, it can just call the isPresenterOf() with
    * the class of the object, e.g.<pre>
    *   if(type == null) {
    *      return false;
    *   }
    *   return isPresenterOf(obj.getClass());
    * </pre>
    * @param obj object type.
    * @return true if the presenter can handle this type.
    */
   @Override
   public boolean isPresenterOf(Object obj) {
      return (obj == null) ? false : isPresenterOf(obj.getClass());
   }

   /**
    * Check if this presenter should always fill the entire area of a cell.
    */
   @Override
   public boolean isFill() {
      return false;
   }

   /**
    * Set the font to use for this presenter. A table calls this function
    * before the cell is printed when a presenter is used.
    */
   @Override
   public void setFont(Font font) {
   }

   /**
    * Check if equals another object.
    *
    * @param obj the specified object
    * @return true if equals, false otherwise
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof Bar2Presenter)) {
         return false;
      }

      Bar2Presenter bar2 = (Bar2Presenter) obj;

      if(psize == null) {
         if(bar2.psize != null) {
            return false;
         }
      }
      else if(!psize.equals(bar2.psize)) {
         return false;
      }

      if(color == null) {
         if(bar2.color != null) {
            return false;
         }
      }
      else if(!color.equals(bar2.color)) {
         return false;
      }

      if(neg == null) {
         if(bar2.neg != null) {
            return false;
         }
      }
      else if(!neg.equals(bar2.neg)) {
         return false;
      }

      return max == bar2.max && autoMaximum == bar2.autoMaximum;
   }

   /**
    * Get the presenter's hash code.
    *
    * @return hash code
    */
   public int hashCode() {
      int hash = 0;
      hash += color == null ? 0 : color.hashCode();
      hash += neg == null ? 0 : neg.hashCode();
      hash += psize == null ? 0 : psize.hashCode();
      hash += Math.abs(Math.round(max));
      hash += autoMaximum ? 31 : 0;
      return hash;
   }

   /**
    * Get the display name of this presenter.
    *
    * @return a user-friendly name for this presenter.
    *
    * @since 5.1
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("Horizontal Bar +/-");
   }

   /**
    * Determine if this Presenter requires raw (unformatted) data.
    *
    * @return <code>true</code>.
    */
   @Override
   public boolean isRawDataRequired() {
      return true;
   }

   /**
    * Set the background.
    */
   @Override
   public void setBackground(Color bg) {
      this.bg = bg;
   }

   /**
    * Get the background.
    */
   public Color getBackground() {
      return bg;
   }

   /**
    * The negative color a new presenter starts with.
    */
   public static final Color DEFAULT_NEGATIVE_COLOR = new Color(128, 0, 0);

   private Color color = null, neg = DEFAULT_NEGATIVE_COLOR;
   private double max = 100;
   private Dimension psize = new Dimension(80, 12);
   private Color bg = null;
   private boolean autoMaximum = false;
   // true once the author set Auto Maximum, on or off, so the modern default leaves it alone
   private boolean autoMaximumSet = false;
   // resolves the data maximum at paint time, set by the table lens on a copy of the
   // presenter; not a bean property, so it is never shown in the dialog or saved
   private transient DoubleSupplier maxResolver;
}
