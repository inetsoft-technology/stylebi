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

         if(!single && housing == null) {
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

         if(!single && housing != null) {
            // the lights are cw wide, 3 apart, and start 3 down (or across) from the paint area;
            // the outline drawn around each adds a pixel to its far side
            int outline = border != 0 ? 1 : 0;

            if(horizontal) {
               paintHousing(g, x, y + 3, (cw + 3) * 3 - 3 + outline, cw + outline);
            }
            else {
               paintHousing(g, x + 3, y, cw + outline, (cw + 3) * 3 - 3 + outline);
            }
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
    * Draw the box around the lights in this color, with rounded corners, instead of the classic
    * square gray one. Null, the default, keeps the classic box. This is not a bean property, so
    * it does not show in the presenter dialog or get saved: the table sets it on a modern table.
    */
   public void useModernHousing(Color color) {
      this.housing = color;
   }

   /**
    * The color of the modern box around the lights, or null for the classic box.
    */
   public Color modernHousing() {
      return housing;
   }

   // The box is drawn around the lights with the same space on every side, so the space above
   // them equals the space below. Its 1px line is drawn on pixel centers, half a pixel in from
   // its outer edge, so it is a sharp line and not two blurred ones. The presenter is painted at
   // its preferred size, which is wider than a narrow column, so the cell clips the painted area;
   // the box is kept inside what is visible, so its right and bottom edges are not lost.
   private void paintHousing(Graphics g, int lightsX, int lightsY, int lightsW, int lightsH) {
      double left = lightsX - HOUSING_PAD;
      double top = lightsY - HOUSING_PAD;
      double right = lightsX + lightsW + HOUSING_PAD;
      double bottom = lightsY + lightsH + HOUSING_PAD;
      Rectangle visible = g.getClipBounds();

      if(visible != null) {
         left = Math.max(left, visible.x);
         top = Math.max(top, visible.y);
         right = Math.min(right, visible.x + visible.width);
         bottom = Math.min(bottom, visible.y + visible.height);
      }

      if(right - left < 2 || bottom - top < 2) {
         return;
      }

      Graphics2D g2 = (Graphics2D) g;
      Object aa = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setColor(housing);
      g2.draw(new java.awt.geom.RoundRectangle2D.Double(
         left + 0.5, top + 0.5, right - left - 1, bottom - top - 1, HOUSING_ARC, HOUSING_ARC));

      if(aa != null) {
         g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
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

   /** The default green, yellow and red. A color still equal to one is taken as not chosen. */
   public static final Color DEFAULT_GREEN = new Color(0, 235, 0);
   public static final Color DEFAULT_YELLOW = new Color(235, 235, 0);
   public static final Color DEFAULT_RED = new Color(235, 0, 0);

   private Color[] COLORS = {DEFAULT_GREEN, DEFAULT_YELLOW, DEFAULT_RED};

   // the corner diameter of the modern box, px
   private static final int HOUSING_ARC = 6;
   // the space between the lights and the box, px
   private static final int HOUSING_PAD = 2;

   private Color housing = null;
   private double low = 0, high = 100;
   private boolean highRed = true; // true to show hi as red, otherwise green
   private boolean horizontal = true;
   private boolean single = false;
   private Color bg = null;
   private Color borderColor = null;
   private int border = 1;
   private int psize = 15;
}
