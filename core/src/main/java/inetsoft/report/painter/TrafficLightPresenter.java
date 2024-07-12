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

import inetsoft.graph.internal.GTool;
import inetsoft.report.Presenter;
import inetsoft.util.Catalog;

import java.awt.*;

/**
 * The TrafficLightPresenter display an integer number as one of the
 * traffic light color: red, yellow, or green. A color is chosen according
 * to the low-high range. By default the high range is shown as the red light.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class TrafficLightPresenter implements Presenter {
   /**
    * Set the minimum value for the high value range.
    */
   public void setHigh(double high) {
      this.high = high;
   }

   /**
    * Get the minimum value for the high value range.
    */
   public double getHigh() {
      return high;
   }

   /**
    * Set the mximum value for the low value range.
    */
   public void setLow(double low) {
      this.low = low;
   }

   /**
    * Get the maximum value for the low value range.
    */
   public double getLow() {
      return low;
   }

   /**
    * Set whether to show the lights horizontally.
    */
   public void setHorizontal(boolean h) {
      this.horizontal = h;
   }

   /**
    * Check whether to show the lights horizontally.
    */
   public boolean isHorizontal() {
      return horizontal;
   }

   /**
    * Set how colors are matched to the low-mid-high ranges. If true, the high
    * value range is shown in red. Otherwise it's shown in green.
    */
   public void setHighRed(boolean red) {
      this.highRed = red;
   }

   /**
    * Check the color mapping to value ranges.
    */
   public boolean isHighRed() {
      return highRed;
   }

   /**
    * Set whether to show only a single light.
    */
   public void setSingleLight(boolean single) {
      this.single = single;
   }

   /**
    * Check whether to show only a single light.
    */
   public boolean isSingleLight() {
      return single;
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

         g.clipRect(x, y, w, h);
         double n = ((Number) v).doubleValue();
         int cidx = 0;

         if(n <= low) {
            cidx = highRed ? 0 : 2;
         }
         else if(n <= high) {
            cidx = 1;
         }
         else {
            cidx = highRed ? 2 : 0;
         }

         if(!single) {
            g.setColor(Color.gray);
            g.drawRect(x + 1, y + 1, w - 3, h - 3);
         }

         int cw = single ? Math.min(h - 6, w - 6) :
            (horizontal ? Math.min(h - 6, (w - 9) / 3)
             : Math.min(w - 6, (h - 9) / 3));

         if(single) {
            x += Math.max(3, (w - cw) / 2);
            y += Math.max(3, (h - cw) / 2);
         }
         else if(horizontal) {
            x += Math.max(3, (w - (cw + 3) * 3) / 2);
         }
         else {
            y += Math.max(3, (h - (cw + 3) * 3) / 2);
         }

         if(single) {
            g.setColor(COLORS[cidx]);
            g.fillOval(x, y, cw, cw);

            if(border != 0) {
               Stroke stroke = ((Graphics2D) g).getStroke();

               g.setColor((borderColor != null) ? borderColor : Color.lightGray);
               ((Graphics2D) g).setStroke(GTool.getStroke(border));
               g.drawOval(x, y, cw, cw);
               ((Graphics2D) g).setStroke(stroke);
            }
         }
         else {
            for(int i = 0; i < 3; i++) {
               int x2, y2;
               g.setColor((i == cidx) ? COLORS[i] : Color.lightGray);

               if(horizontal) {
                  g.fillOval(x2 = x + i * (cw + 3), y2 = y + 3, cw, cw);
               }
               else {
                  g.fillOval(x2 = x + 3, y2 = y + i * (cw + 3), cw, cw);
               }

               if(border != 0) {
                  Stroke stroke = ((Graphics2D) g).getStroke();

                  g.setColor((borderColor != null) ? borderColor : Color.lightGray);
                  ((Graphics2D) g).setStroke(GTool.getStroke(border));
                  g.drawOval(x2, y2, cw, cw);
                  ((Graphics2D) g).setStroke(stroke);
               }
            }
         }

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
      if(single) {
         return new Dimension(psize, psize);
      }

      return horizontal ? new Dimension(psize * 4, psize)
	 : new Dimension(psize, psize * 4);
   }

   /**
    * Set the diameter of the circle.
    */
   public void setSize(int size) {
      this.psize = size;
   }

   /**
    * Get the diameter of the circle.
    */
   public int getSize() {
      return this.psize;
   }

   /**
    * Check if this presenter should always fill the entire area of a cell.
    */
   @Override
   public boolean isFill() {
      return false;
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
    * Set the font to use for this presenter. A table calls this function
    * before the cell is printed when a presenter is used.
    */
   @Override
   public void setFont(Font font) {
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
      return Catalog.getCatalog().getString("Traffic Light");
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
    * Set the border color.
    */
   public void setBorderColor(Color bgColor) {
      this.borderColor = bgColor;
   }

   /**
    * Get the border color.
    */
   public Color getBorderColor() {
      return borderColor;
   }

   /**
    * Set the border size.
    */
   public void setBorder(int w) {
      this.border = w;
   }

   /**
    * Get the border size.
    */
   public int getBorder() {
      return border;
   }

   /**
    * Set the first (green) color.
    */
   public Color getColor1() {
      return COLORS[0];
   }

   /**
    * Get the first (green) color.
    */
   public void setColor1(Color clr) {
      COLORS[0] = clr;
   }

   /**
    * Set the second (yellow) color.
    */
   public Color getColor2() {
      return COLORS[1];
   }

   /**
    * Get the second (yellow) color.
    */
   public void setColor2(Color clr) {
      COLORS[1] = clr;
   }

   /**
    * Get the third (red) color.
    */
   public Color getColor3() {
      return COLORS[2];
   }

   /**
    * Set the third (red) color.
    */
   public void setColor3(Color clr) {
      COLORS[2] = clr;
   }

   private Color[] COLORS = {
      new Color(0, 235, 0), new Color(235, 235, 0), new Color(235, 0, 0)};

   private double low = 0, high = 100;
   private boolean highRed = true; // true to show hi as red, otherwise green
   private boolean horizontal = true;
   private boolean single = false;
   private Color bg = null;
   private Color borderColor = null;
   private int border = 1;
   private int psize = 15;
}
