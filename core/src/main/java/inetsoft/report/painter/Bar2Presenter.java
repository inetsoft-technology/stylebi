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
package inetsoft.report.painter;

import inetsoft.report.Presenter;
import inetsoft.util.Catalog;

import java.awt.*;

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
public class Bar2Presenter implements Presenter {
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

      return max == bar2.max;
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

   private Color color = null, neg = new Color(128, 0, 0);
   private double max = 100;
   private Dimension psize = new Dimension(80, 12);
   private Color bg = null;
}
