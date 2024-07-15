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
import inetsoft.report.internal.Common;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;

import java.awt.*;
import java.text.NumberFormat;
import java.util.Vector;

/**
 * This presenter draws a ruler (coordinate axis). A value can be plotted on
 * the ruler. Or a rule can be drawn by itself.
 *
 * @version 9.0, 3/10/2007
 * @author InetSoft Technology Corp
 */
public class RulerPresenter implements Presenter {
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
    * Get the minimum value.
    */
   public double getMinimum() {
      return min;
   }

   /**
    * Set the minimum value.
    */
   public void setMinimum(double min) {
      this.min = min;
   }

   /**
    * Get the increment value.
    */
   public double getIncrement() {
      return increment;
   }

   /**
    * Set the increment value.
    */
   public void setIncrement(double increment) {
      this.increment = increment;
   }

   /**
    * Get the minor increment value.
    */
   public double getMinorIncrement() {
      return minorIncrement;
   }

   /**
    * Set the minor increment value.
    */
   public void setMinorIncrement(double increment) {
      this.minorIncrement = increment;
   }

   /**
    * Get the format used to convert number to label.
    */
   public NumberFormat getFormat() {
      return format;
   }

   /**
    * Set the format used to convert number to label.
    */
   public void setFormat(NumberFormat format) {
      this.format = format;
   }

   /**
    * Get the format for the minimum label. Use the regular format if not set.
    */
   public NumberFormat getMinimumFormat() {
      return minformat;
   }

   /**
    * Set the format for the minimum label. Use the regular format if not set.
    */
   public void setMinimumFormat(NumberFormat format) {
      this.minformat = format;
   }

   /**
    * Get the format for the maximum label. Use the regular format if not set.
    */
   public NumberFormat getMaximumFormat() {
      return maxformat;
   }

   /**
    * Set the format for the maximum label. Use the regular format if not set.
    */
   public void setMaximumFormat(NumberFormat format) {
      this.maxformat = format;
   }

   /**
    * Paint an object at the specified location.
    * @param g0 graphical context.
    * @param v object value.
    * @param x x coordinate of the left edge of the paint area.
    * @param y y coordinate of the upper edge of the paint area.
    * @param w area width.
    * @param h area height.
    */
   @Override
   public void paint(Graphics g0, Object v, int x, int y, int w, int h) {
      Graphics g = g0.create(x, y, w, h);
      Vector lbounds = new Vector();
      Color oc = g.getColor();

      if(font != null) {
         g.setFont(font);
      }

      g.setColor(LINE_COLOR);
      g.drawLine(0, 0, w, 0);

      if(v instanceof String) {
         try {
            v = Double.valueOf((String) v);
         }
         catch(Exception ex) {
         }
      }

      if(v instanceof Number) {
         double n = ((Number) v).doubleValue();
         Font ofont = g.getFont();

         g.setColor(oc);
         g.setFont(ofont.deriveFont(Font.BOLD));

         Rectangle box = drawLabel(g, n, w, lbounds);
         g.setFont(ofont);

         if(box != null) {
            lbounds.add(box);
            drawTick(g, n, 4, w);
         }
      }

      // for Major ticks
      if(increment > 0) {
         for(double t = min; t <= max; t += increment) {
            g.setColor(oc);
            Rectangle box = drawLabel(g, t, w, lbounds);

            if(box != null) {
               lbounds.add(box);
            }

            drawTick(g, t, 3, w);
         }

         drawTick(g, max, 3, w);
      }

      // for minor ticks
      if(minorIncrement > 0) {
         for(double t = min; t <= max; t += minorIncrement) {
            drawTick(g, t, 2, w);
         }
      }

      g.dispose();
   }

   /**
    * Draw a tick.
    * @param len length of the tick line.
    */
   private void drawTick(Graphics g, double val, int len, int width) {
      int x = Math.min(getX(val, width), width - 1);
      g.setColor(LINE_COLOR);
      g.drawLine(x, 0, x, len);
   }

   /**
    * Draw a label. A label will be omitted if it overlaps another label.
    * @param width paint region width.
    */
   private Rectangle drawLabel(Graphics g, double val, int width,
                               Vector lbounds) {
      FontMetrics fm = g.getFontMetrics();
      String label = format(val);
      int lw = fm.stringWidth(label);
      int x = getX(val, width) - lw / 2;

      if(x < 0) {
         x = 0;
      }
      else if(x + lw > width) {
         x = width - lw;
      }

      Rectangle box = new Rectangle(x - 1, 0, lw + 2, fm.getHeight());

      // check if overlaps
      for(int i = 0; i < lbounds.size(); i++) {
         Rectangle box2 = (Rectangle) lbounds.get(i);

         if(box.intersects(box2)) {
            return null;
         }
      }

      Common.drawString(g, label, x, 3 + fm.getAscent());
      return box;
   }

   /**
    * Format a number.
    */
   private String format(double val) {
      if(val == min && minformat != null) {
         return minformat.format(val);
      }

      if(val == max && maxformat != null) {
         return maxformat.format(val);
      }

      return (format != null) ? format.format(val) :
         (val == (int) val) ? Integer.toString((int) val)
         : Tool.toString(val, 2);
   }

   /**
    * Get the x position of a value.
    */
   private int getX(double v, int w) {
      return (int) ((v - min) * w / (max - min));
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
      return true;
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
      return true;
   }

   /**
    * Check if this presenter should always fill the entire area of a cell.
    */
   @Override
   public boolean isFill() {
      return fillCell;
   }

   /**
    * Set the property to fill the entire area of the cell.
    * @param fill true to fill the cell area.
    */
   public boolean setFill(boolean fill) {
      return fillCell = fill;
   }

   /**
    * Set the font to use for this presenter. A table calls this function
    * before the cell is printed when a presenter is used.
    */
   @Override
   public void setFont(Font font) {
      this.font = font;
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
      return Catalog.getCatalog().getString("Ruler");
   }

   /**
    * Check if equals another object.
    *
    * @param obj the specified object
    * @return true if equals, false otherwise
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof RulerPresenter)) {
         return false;
      }

      RulerPresenter bar2 = (RulerPresenter) obj;

      return fillCell == bar2.fillCell && max == bar2.max &&
         min == bar2.min && increment == bar2.increment &&
         minorIncrement == bar2.minorIncrement &&
         Tool.equals(psize, bar2.psize);
   }

   /**
    * Get the presenter's hash code.
    *
    * @return hash code
    */
   public int hashCode() {
      int hash = 0;
      hash += psize == null ? 0 : psize.hashCode();
      hash += Math.abs(Math.round(max));
      hash += Math.abs(Math.round(min));
      hash += Math.abs(Math.round(increment));
      hash += Math.abs(Math.round(minorIncrement));
      hash += fillCell ? 0 : 999999;
      return hash;
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

   private Color bg = null;
   private static final Color LINE_COLOR = new Color(80, 80, 80);
   private Dimension psize = new Dimension(60, 12);
   private boolean fillCell = true;
   private Font font;
   private double max = 100;
   private double min = 0;
   private double increment = 25;
   private double minorIncrement = 0;
   private NumberFormat format;
   private NumberFormat minformat;
   private NumberFormat maxformat;
}