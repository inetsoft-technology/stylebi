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
import inetsoft.util.Tool;

import java.awt.*;
import java.text.Format;
import java.util.function.Supplier;

/**
 * This presenter is an implementation of bullet graph. It is similar to a
 * gauge but has a simpler look-and-feel and smaller foot print.
 *
 * @version 9.0, 3/10/2007
 * @author InetSoft Technology Corp
 */
public class BulletGraphPresenter implements Presenter {

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
      this.scaleSet = true;
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
      this.scaleSet = true;
   }

   /**
    * Get the target value.
    */
   public double getTarget() {
      return target;
   }

   /**
    * Set the target value.
    */
   public void setTarget(double target) {
      this.target = target;
      this.scaleSet = true;
   }

   /**
    * Get the range 1 value.
    */
   public double getRange1() {
      return range1;
   }

   /**
    * Set the range 1 value. This defines the 1st range to be from minimum to
    * range1.
    */
   public void setRange1(double range1) {
      this.range1 = range1;
      this.scaleSet = true;
   }

   /**
    * Get the range 2 value.
    */
   public double getRange2() {
      return range2;
   }

   /**
    * Set the range 2 value. This defines the 2nd range to be from minimum to
    * range2.
    */
   public void setRange2(double range2) {
      this.range2 = range2;
      this.scaleSet = true;
   }

   /**
    * Get the range 3 value.
    */
   public double getRange3() {
      return range3;
   }

   /**
    * Set the range 3 value. This defines the 3rd range to be from minimum to
    * range3.
    */
   public void setRange3(double range3) {
      this.range3 = range3;
      this.scaleSet = true;
   }

   /**
    * Get the range 1 color.
    */
   public Color getColor1() {
      return color1;
   }

   /**
    * Set the range 1 color.
    */
   public void setColor1(Color color1) {
      this.color1 = color1;
   }

   /**
    * Get the range 2 color.
    */
   public Color getColor2() {
      return color2;
   }

   /**
    * Set the range 2 color.
    */
   public void setColor2(Color color2) {
      this.color2 = color2;
   }

   /**
    * Get the range 3 color.
    */
   public Color getColor3() {
      return color3;
   }

   /**
    * Set the range 3 color.
    */
   public void setColor3(Color color3) {
      this.color3 = color3;
   }

   /**
    * Get the range 4 color.
    */
   public Color getColor4() {
      return color4;
   }

   /**
    * Set the range 4 color.
    */
   public void setColor4(Color color4) {
      this.color4 = color4;
   }

   /**
    * Get the bar color.
    */
   public Color getBarColor() {
      return barColor;
   }

   /**
    * Set the bar color.
    */
   public void setBarColor(Color color) {
      this.barColor = color;
   }

   /**
    * Set the shadow value.
    */
   public boolean isShadow() {
      return shadow;
   }

   /**
    * Get the shadow value.
    */
   public void setShadow(boolean shadow) {
      this.shadow = shadow;
   }

   /**
    * Check if the scale is worked out from the column's data instead of the configured values.
    */
   public boolean isAutoScale() {
      return autoScale;
   }

   /**
    * Set whether the scale is worked out from the column's data. The minimum is the column's
    * smallest value or 0, whichever is lower, and the maximum its largest value or 0, whichever is
    * higher, so a bar always starts at zero for positive data. The ranges split that span in four
    * and the target is the column's average. When the column has no usable numbers, the
    * configured minimum, maximum, ranges and target are used.
    */
   public void setAutoScale(boolean autoScale) {
      this.autoScale = autoScale;
      this.scaleSet = true;
   }

   /**
    * Scale to the column's data in the modern style, unless the author chose the scale: set Auto
    * Scale either way, or set any of the minimum, maximum, ranges and target. This is not a bean
    * property, so it does not show in the presenter dialog or get saved.
    */
   public void useModernAutoScale() {
      if(!scaleSet) {
         autoScale = true;
      }
   }

   /**
    * Give a cell a copy that resolves the column's numbers at paint time, when auto scale is on.
    * This presenter is not changed, since a script presenter is shared by all cells of a column.
    * @param resolver returns the column's numbers, or null when there are none.
    */
   public Presenter withStatsResolver(Supplier<ColumnStats> resolver) {
      BulletGraphPresenter copy = copy();
      copy.statsResolver = resolver;
      return copy;
   }

   // every field, so no copy forgets one
   private BulletGraphPresenter copy() {
      BulletGraphPresenter copy = new BulletGraphPresenter();
      copy.psize = psize == null ? null : new Dimension(psize);
      copy.fillCell = fillCell;
      copy.max = max;
      copy.min = min;
      copy.target = target;
      copy.range1 = range1;
      copy.range2 = range2;
      copy.range3 = range3;
      copy.color1 = color1;
      copy.color2 = color2;
      copy.color3 = color3;
      copy.color4 = color4;
      copy.barColor = barColor;
      copy.targetColor = targetColor;
      copy.bg = bg;
      copy.labelVisible = labelVisible;
      copy.shadow = shadow;
      copy.fmt = fmt;
      copy.autoScale = autoScale;
      copy.scaleSet = scaleSet;
      copy.statsResolver = statsResolver;
      return copy;
   }

   /**
    * The presenter to paint with: a copy scaled to the column's data when auto scale is on and
    * the column has a usable span, otherwise this one.
    */
   private BulletGraphPresenter scaled() {
      if(!autoScale || statsResolver == null) {
         return this;
      }

      ColumnStats stats = statsResolver.get();

      if(stats == null || Double.isNaN(stats.min()) || Double.isNaN(stats.max())) {
         return this;
      }

      double lo = Math.min(0, stats.min());
      double hi = Math.max(0, stats.max());

      if(!(hi > lo) || Double.isInfinite(lo) || Double.isInfinite(hi)) {
         return this;
      }

      double span = hi - lo;
      BulletGraphPresenter copy = copy();
      copy.autoScale = false;
      copy.scaleSet = true;
      copy.statsResolver = null;
      copy.min = lo;
      copy.max = hi;
      copy.range1 = lo + span / 4;
      copy.range2 = lo + span / 2;
      copy.range3 = lo + span * 3 / 4;
      copy.target = Double.isNaN(stats.mean()) ? lo : Math.max(lo, Math.min(hi, stats.mean()));
      return copy;
   }

   /**
    * Paint an object at the specified location.
    * @param g graphical context.
    * @param v object value.
    * @param x0 x coordinate of the left edge of the paint area.
    * @param y0 y coordinate of the upper edge of the paint area.
    * @param w0 area width.
    * @param h0 area height.
    */
   @Override
   public void paint(Graphics g, Object v, int x0, int y0, int w0, int h0) {
      BulletGraphPresenter scaled = scaled();

      if(scaled != this) {
         scaled.paint(g, v, x0, y0, w0, h0);
         return;
      }

      if(v != null && v instanceof Number) {
         Shape clip = g.getClip();
         Color oc = g.getColor();
         double n = ((Number) v).doubleValue();

         g.clipRect(x0, y0, w0, h0);

         final int gap = 2;
         double[] ranges = {min, range1, range2, range3, max};
         Color[] colors = {color1, color2, color3, color4};

         normalizeRanges(ranges);
         normalizeColors(colors);

         int filly = y0 + gap;
         int fillh = h0 - gap * 2;
         int fillx = x0;
         int fillw = w0;

         FontMetrics fm = g.getFontMetrics();

         if(labelVisible) {
            int fontH = fm.getHeight();
            fillh -= fontH;

            double inc = findIncrement();
            int ticks = (int) Math.ceil((max - min) / inc) + 1;

            fillx += fm.stringWidth(formatValue(min)) / 2 + 1;
            fillw -= fm.stringWidth(formatValue(min)) / 2 +
               fm.stringWidth(formatValue(max)) / 2 + 4;

            double xinc = fillw / (ticks - 1.0);
            double tickx = fillx;
            int ticky = filly + fillh + fm.getAscent() + 2;
            double val = min;

            // if the labels will overlap, only draw min and max
            for(int i = 0; i < ticks; i++, val += inc) {
               String str = formatValue(val);

               if(fm.stringWidth(str) > xinc) {
                  ticks = 2;
                  inc = max - min;
                  xinc = fillw;
                  break;
               }
            }

            val = min;

            for(int i = 0; i < ticks; i++, tickx += xinc, val += inc) {
               String str = formatValue(val);
               int strW = fm.stringWidth(str);
               g.setColor(Color.LIGHT_GRAY);
               g.drawLine((int) tickx, filly + fillh, (int) tickx, filly + fillh + 2);
               g.setColor(oc);

               if(!this.isShadow()) {
                  g.drawString(str, (int) (tickx - strW / 2), ticky);
               }
            }
         }

         // fill range colors
         for(int i = 0; i < ranges.length - 1; i++) {
            double v1 = ranges[i];
            double v2 = ranges[i + 1];

            if(v2 < v1) {
               break;
            }

            int rx = fillx + (int) ((v1 - min) * fillw / (max - min));
            int rx2 = fillx + (int) ((v2 - min) * fillw / (max - min));
            int rw = rx2 - rx;
            g.setColor(colors[i]);
            g.fillRect(rx, filly, rw, fillh);
         }

         // draw bar
         int bw = (int) ((n - min) * fillw / (max - min));

         if(barColor != null) {
            g.setColor(barColor);
         }
         else {
            g.setColor(oc);
         }

         // a value above the minimum always shows, even when it is far smaller than the maximum
         if(n > min && bw < 1 && fillw > 0) {
            bw = 1;
         }

         g.fillRect(fillx, filly + (int) Math.ceil(fillh / 4.0), bw, (int) Math.floor(fillh / 2.0));

         // draw target
         // a target at the maximum is at the cell's edge, so keep it inside the cell
         int tx = Math.min((int) ((target - min) * fillw / (max - min)) + fillx, fillx + fillw - 1);
         g.setColor(targetColor != null ? targetColor : new Color(20, 20, 20));
         g.drawLine(tx, filly + gap, tx, filly + fillh - gap);

         g.setColor(oc);
         g.setClip(clip);
      }
   }

   /**
    * Draw the target line in this color, in place of the classic near-black one. Null, the
    * default, keeps that. This is not a bean property, so it does not show in the presenter
    * dialog or get saved: the table sets it on a modern table.
    */
   public void useModernTarget(Color color) {
      this.targetColor = color;
   }

   /**
    * The color of the modern target line, or null for the classic one.
    */
   public Color modernTarget() {
      return targetColor;
   }

   private String formatValue(Object val) {
      String str = Tool.toString(val);

      if(fmt != null) {
         try {
            str = fmt.format(val);
         }
         catch(Exception ex) {
            // ignore
         }
      }

      return str;
   }

   // find a nice increment
   private double findIncrement() {
      for(int ticks = 4; ticks > 2; ticks--) {
         double[] vs = GTool.getNiceNumbers(min, max, min, Double.NaN, ticks);

         if(vs[1] == max) {
            return vs[2];
         }
      }

      return (max - min) / 2;
   }

   /**
    * Make sure there is no gap in the ranges.
    */
   private void normalizeRanges(double[] ranges) {
      for(int i = 0; i < ranges.length - 1; i++) {
         if(Double.isNaN(ranges[i + 1]) || ranges[i + 1] < ranges[i]) {
            ranges[i + 1] = ranges[ranges.length - 1];
         }
      }
   }

   /**
    * Make sure colors are set.
    */
   private void normalizeColors(Color[] colors) {
      Color prev = new Color(100, 100, 120);

      for(int i = 0; i < colors.length; i++) {
         if(colors[i] == null) {
            colors[i] = brighter(prev);
         }

         prev = colors[i];
      }
   }

   /**
    * Return a brighter color.
    */
   private Color brighter(Color c) {
      int r = c.getRed();
      int g = c.getGreen();
      int b = c.getBlue();

      if(r == 0 && g == 0 && b == 0) {
         return Color.gray;
      }

      r = (int) Math.min(255, r * 1.25);
      g = (int) Math.min(255, g * 1.25);
      b = (int) Math.min(255, b * 1.25);

      return new Color(r, g, b);
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
   }

   /**
    * Get the display name of this presenter.
    * @return a user-friendly name for this presenter.
    */
   @Override
   public String getDisplayName() {
      return Catalog.getCatalog().getString("Bullet Graph");
   }

   /**
    * Check if equals another object.
    *
    * @param obj the specified object
    * @return true if equals, false otherwise
    */
   public boolean equals(Object obj) {
      if(!(obj instanceof BulletGraphPresenter)) {
         return false;
      }

      BulletGraphPresenter bar2 = (BulletGraphPresenter) obj;

      return fillCell == bar2.fillCell && autoScale == bar2.autoScale && max == bar2.max &&
         min == bar2.min && target == bar2.target &&
         range1 == bar2.range1 && range2 == bar2.range2 &&
         range3 == bar2.range3 && Tool.equals(psize, bar2.psize) &&
         Tool.equals(color1, bar2.color1) &&
         Tool.equals(color2, bar2.color2) &&
         Tool.equals(color3, bar2.color3) &&
         Tool.equals(color4, bar2.color4) &&
         Tool.equals(barColor, bar2.barColor);
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
      hash += Math.abs(Math.round(range1));
      hash += Math.abs(Math.round(range2));
      hash += Math.abs(Math.round(range3));
      hash += fillCell ? 0 : 999999;
      hash += autoScale ? 31 : 0;
      hash += color1 == null ? 0 : color1.hashCode();
      hash += color2 == null ? 0 : color2.hashCode();
      hash += color3 == null ? 0 : color3.hashCode();
      hash += color4 == null ? 0 : color4.hashCode();
      hash += barColor == null ? 0 : barColor.hashCode();
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

   /**
    * Set whether to drop ticks and labels.
    */
   public void setLabelVisible(boolean visible) {
      this.labelVisible = visible;
   }

   // hide from beans, used internally for now
   public boolean isLabelVisible() {
      return labelVisible;
   }

   public void setFormat(Format fmt) {
      this.fmt = fmt;
   }

   public Format getFormat() {
      return fmt;
   }

   private Dimension psize = new Dimension(80, 12);
   private boolean fillCell = true;
   private double max = 100;
   private double min = 0;
   private double target = 0;
   private double range1 = -1;
   private double range2 = -1;
   private double range3 = -1;
   private Color color1 = null; // min -> range1
   private Color color2 = null; // range1 -> range2
   private Color color3 = null; // range2 -> range3
   private Color color4 = null; // range3 -> max
   private Color barColor = null;
   private Color targetColor = null;
   private Color bg = null;
   private boolean labelVisible;
   private boolean shadow;
   private Format fmt;
   private boolean autoScale = false;
   // true once the author chose the scale, by setting Auto Scale either way or any of the minimum,
   // maximum, ranges and target, so the modern default leaves it alone
   private boolean scaleSet = false;
   // resolves the column's numbers at paint time, set by the table lens on a copy of the
   // presenter; not a bean property, so it is never shown in the dialog or saved
   private transient Supplier<ColumnStats> statsResolver;
}
