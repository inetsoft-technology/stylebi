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
package inetsoft.report.gui.viewsheet.thermometer;

import inetsoft.report.internal.Common;
import inetsoft.uql.viewsheet.internal.ThermometerVSAssemblyInfo;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.Point2D;

/**
 * The horizontal thermometer.
 *
 * @version 8.5, 2006-7-3
 * @author InetSoft Technology Corp
 */
public class VSHorizontalThermometer extends VSThermometer {

   /**
    * Parse an xml segment.
    */
   @Override
   protected void parseXML(Element node) throws Exception {
      super.parseXML(node);

      Element mtNode = Tool.getChildNodeByTagName(node, "majorTick");
      majorTickStartY = Double.parseDouble(
         Tool.getAttribute(mtNode, "startY"));
      ticksLength = Double.parseDouble(
         Tool.getAttribute(mtNode, "length"));
      startXAdjustment = Double.parseDouble(
         Tool.getAttribute(mtNode, "startXAdjustment"));

      Element vNode = Tool.getChildNodeByTagName(node, "value");
      valueStartY = Double.parseDouble(Tool.getAttribute(vNode, "startY"));

      Element recNode = Tool.getChildNodeByTagName(node, "valueRec");
      recWidth = Double.parseDouble(Tool.getAttribute(recNode, "width"));
      centerY = Double.parseDouble(Tool.getAttribute(recNode, "centerY"));

      recStartY = centerY - recWidth / 2;
      recTopColor = parseColor(Tool.getAttribute(recNode, "topColor"));
      recBottomColor = parseColor(Tool.getAttribute(recNode, "bottomColor"));
      recStartX = Double.parseDouble(Tool.getAttribute(recNode, "startX"));
      majorTickStartX = recStartX + startXAdjustment;
      majorTickEndX = majorTickStartX + ticksLength;

      Element rangeNode = Tool.getChildNodeByTagName(node, "range");
      rangeStartY = Double.parseDouble(Tool.getAttribute(rangeNode, "startY"));
      rangeWidth = Double.parseDouble(Tool.getAttribute(rangeNode, "width"));
   }

   /**
    * Fill the range.
    */
   @Override
   protected void fillRanges(Graphics2D g) {
      ThermometerVSAssemblyInfo info =
         (ThermometerVSAssemblyInfo) getAssemblyInfo();
      double[] ranges = info.getRanges();
      Color[] colors = info.getRangeColors();

      if(ranges == null || colors == null || ranges.length == 0 ||
         ranges.length > colors.length)
      {
         return;
      }

      double min = info.getMin();
      double max = info.getMax();
      int rangeHeight = (int) ticksLength;
      int startx = (int) majorTickStartX;
      int starty = (int) rangeStartY;

      for(int i = ranges.length - 1; i >= 0; i--) {
         double range = ranges[i];
         range = range < min ? min : range;
         range = range > max ? max : range;

         // set the range color same with previous one when the color is null
         if(colors[i] == null) {
            if(i < ranges.length - 1 && colors[i + 1] != null) {
               colors[i] = colors[i + 1];
            }
            else {
               continue;
            }
         }

         Color c1 = colors[i];
         Color c2 = (i < colors.length - 1) ? colors[i + 1] : c1.darker();
         double wrate = (range - (i > 0 ? ranges[i - 1] : min)) / (max - min);
         double xrate = (range - min) / (max - min);
         int width = (int) (wrate * rangeHeight);
         int x = (int) (majorTickStartX + xrate * rangeHeight);

         // get the x-axis value of next range
         double nextRange = i > 0 ? ranges[i - 1] : min;
         nextRange = nextRange < min ? min : nextRange;
         nextRange = nextRange > max ? max : nextRange;
         double nextXrate = (nextRange - min) / (max - min);
         int nextX = (int) (majorTickStartX + nextXrate * rangeHeight);

         if(info.isRangeGradient()) {
            if(c2 == null) {
               if(c1 != null) {
                  c2 = c1.darker();
               }
               else {
                  continue;
               }
            }

            GradientPaint gra = new GradientPaint(x, 0, c2, x - width, 0, c1);
            g.setPaint(gra);
         }
         else {
            g.setColor(c1);
         }

         // define the trapezia area of the range
         Point p1 = new Point(x, starty);
         Point p2 = new Point(x, starty - (int) (rangeWidth * (xrate + 0.5)));
         Point p3 = new Point(nextX, starty - (int) (rangeWidth * (nextXrate + 0.5)));
         Point p4 = new Point(nextX, starty);
         Polygon trapezia = new Polygon();
         trapezia.addPoint(p1.x, p1.y);
         trapezia.addPoint(p2.x, p2.y);
         trapezia.addPoint(p3.x, p3.y);
         trapezia.addPoint(p4.x, p4.y);
         g.fill(trapezia);
      }
   }

   /**
    * Draw the value rectangle.
    */
   @Override
   protected void drawValueRec(Graphics2D g) {
      ThermometerVSAssemblyInfo info = getThermometerAssemblyInfo();
      double dMinValue = info.getMin();
      double dMaxValue = info.getMax();
      double value = getValue();

      if(value > dMaxValue) {
         value = dMaxValue;
      }
      else if(value < dMinValue) {
         value = dMinValue;
      }

      double rate = (value - dMinValue) / (dMaxValue - dMinValue);
      int realWidth =
         (int) (rate * (majorTickEndX - majorTickStartX) + startXAdjustment);
      int y = (int) recStartY;
      int height = (int) recWidth;
      GradientPaint paint = new GradientPaint((float) majorTickStartX,
         (float) recStartY, recTopColor, (float) majorTickStartX,
         (float) (recStartY + height), recBottomColor);
      g.setPaint(paint);
      g.fillRect((int) recStartX, y, realWidth, height);
   }

   /**
    * Adjust the font so that the value string will not out of the image.
    */
   protected Font adjustValuesFont(Graphics2D g, double startx,
                                   double endx, String[] values, boolean[] flags)
   {
      Font font = getFont();
      g.setFont(font);
      Font result = font;
      double dis = endx - startx;

      while(calculateStringLength(g, values, result, flags) > dis) {
         if(result.getSize() <= MIN_FONT_SIZE) {
            break;
         }

         result = reduceSize(result);

         g.setFont(result);
      }

      // maximum space to fit height
      int maxFontH = (int) (getSize().height - valueStartY - 3);

      while(result.getSize() > MIN_FONT_SIZE) {
         if(g.getFontMetrics().getHeight() <= maxFontH) {
            break;
         }

         result = reduceSize(result);
      }

      return result;
    }

   /**
    * Draw the ticks and tick values.
    */
   @Override
   protected void drawTicksAndValues(Graphics2D g) {
      ThermometerVSAssemblyInfo info = getThermometerAssemblyInfo();
      double majorInc = info.getMajorInc();
      double minorInc = info.getMinorInc();
      double dMinValue = info.getMin();
      double dMaxValue = info.getMax();
      int majorTickCount = (int) Math.ceil(
         ((dMaxValue - dMinValue) / majorInc)) + 1;

      if(majorTickCount >= MAX_MAJOR_TICK_COUNT * 2) {
         LOG.info("Too many major ticks found in thermometer: " +
            majorTickCount);
         int inc = (majorTickCount / MAX_MAJOR_TICK_COUNT);
         majorInc *= inc;
         minorInc *= inc;
         majorTickCount =
            (int) Math.ceil(((dMaxValue - dMinValue) / majorInc)) + 1;
      }

      int minorTickBetweenMajorTicks = (minorInc == 0) ? 0 :
         (int) Math.ceil(majorInc / minorInc) - 1;
      String[] values = new String[majorTickCount];

      // calculate the values should be displayed.
      for(int i = 0; i < majorTickCount - 1; i++) {
         values[i] = formatValue(dMinValue + majorInc * i, false);
      }

      values[majorTickCount - 1] = formatValue(dMaxValue, false);
      values = abbreviate(values);

      // draw the ticks and the values
      boolean[] flags = new boolean[values.length];
      flags = resetFlags(flags, 0);

      Font font = adjustValuesFont(g, majorTickStartX, majorTickEndX,
                                   values, flags);
      g.setFont(font);

      FontMetrics fm = g.getFontMetrics();
      double fontHeight = fm.getHeight() - fm.getDescent();
      double startX = majorTickStartX;
      double endX = majorTickEndX;
      double majorDelta = (endX - startX) * majorInc / (dMaxValue - dMinValue);
      double minorDelta = (minorInc == 0) ? 0 :
         (endX - startX) * minorInc / (dMaxValue - dMinValue);
      double btyDelta = (majorTickLength - minorTickLength) / 2;
      double begin = startX;

      g.setColor(majorTickColor);

      // calculate the display flags
      while(isValuesOverlapped(g, majorTickStartX, majorDelta, values, flags)) {
         int jump = getJump(flags);
         flags = resetFlags(flags, jump + 1);

         if(jump >= flags.length - 1) {
            break;
         }
      }

      checkMaxOverlapped(g, majorDelta, font, values, flags);

      if(majorInc < minorInc || (dMaxValue - dMinValue) < majorInc) {
         drawTick(g, new Point2D.Double(startX, majorTickStartY),
            new Point2D.Double(startX, majorTickStartY + majorTickLength),
            majorTickWidth, majorTickColor);
         drawTick(g, new Point2D.Double(endX, majorTickStartY),
            new Point2D.Double(endX, majorTickStartY + majorTickLength),
            majorTickWidth, majorTickColor);

         float height = (float) (valueStartY + fm.getAscent());
         Common.drawString(g, values[0],
               (float) getStringStartX(g, startX, values[0]), height);
         Common.drawString(g, values[values.length - 1],
            (float) getStringStartX(g, endX, values[values.length - 1]),
            height);

         return;
      }

      for(int i = 0; i < majorTickCount; i++) {
         // draw major ticks
         Point2D start = new Point2D.Double(begin, majorTickStartY);
         Point2D end = new Point2D.Double(begin,
            majorTickStartY + majorTickLength);
         drawTick(g, start, end, majorTickWidth, majorTickColor);

         // draw Value
         if(flags[i]) {
            float height = (float) (valueStartY + fm.getAscent());
            g.setColor(getForeground());
            Common.drawString(g, values[i],
               (float) getStringStartX(g, begin, values[i]), height);
         }

         double majorTickValue = dMinValue + majorInc * i;

         // draw minor ticks
         for(int j = 0; j < minorTickBetweenMajorTicks; j++) {
            if((majorTickValue + minorInc * (j + 1)) >= dMaxValue) {
               break;
            }

            g.setColor(minorTickColor);
            double x = begin + minorDelta * (j + 1);
            Point2D mstart = new Point2D.Double(x, majorTickStartY + btyDelta);
            Point2D mend = new Point2D.Double(
               x, majorTickStartY + btyDelta + minorTickLength);
            drawTick(g, mstart, mend, minorTickWidth, minorTickColor);
         }

         if(i < majorTickCount - 2) {
            begin += majorDelta;
         }
         else {
            begin = endX;
         }
      }
   }

   /**
    * Check if the values overlap each other.
    */
   private boolean isValuesOverlapped(Graphics2D g, double begin,
      double delta, String[] contents, boolean[] flags)
   {
      Font font = getFont();
      g.setFont(font);
      int jump = getJump(flags);
      jump++;

      for(int i = 0; i < contents.length; i += jump) {
         if((i + jump) < contents.length) {
            double px = getStringEndX(g, begin + delta * i, contents[i]);
            double cx = getStringStartX(g, begin + delta * (i + jump),
               contents[i + jump]);

            if(px + 2 * scaleX > cx) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Check if the max is overlapped.
    * @param delta the delta between values.
    */
   private void checkMaxOverlapped(Graphics2D g, double delta, Font font,
                                   String[] contents, boolean[] flags)
   {
      if(contents.length < 2) {
         return;
      }

      int maxIdx = contents.length - 1;
      int prevIdx = contents.length - 2;

      for(; prevIdx >= 0 && !flags[prevIdx]; prevIdx--) {
      }

      if(prevIdx < 0) {
         return;
      }

      double px = getStringEndX(g, delta * prevIdx, contents[prevIdx]);
      double cx = getStringStartX(g, majorTickEndX - majorTickStartX,
         contents[maxIdx]);

      if(px + valueMargin > cx) {
         flags[prevIdx] = false;
      }
   }

   /**
    * Caculate the value string start x position.
    */
   private double getStringStartX(Graphics2D g, double tickX, String value) {
      FontMetrics fm = g.getFontMetrics();
      double width = fm.stringWidth(value);
      return tickX - width / 2;
   }

   /**
    * Caculate the value string end x position.
    */
   private double getStringEndX(Graphics2D g, double tickX, String value) {
      FontMetrics fm = g.getFontMetrics();
      double width = fm.stringWidth(value);
      return tickX + width / 2;
   }

   /**
    * Reset the flags.
    */
   private boolean[] resetFlags(boolean[] flags, int jump) {
      for(int i = 0; i < flags.length; i++) {
         flags[i] = i % (jump + 1) == 0;
      }

      return flags;
   }

   /**
    * Get the jump of the display flag.
    */
   private int getJump(boolean[] flags) {
      int jump = 0;
      boolean begin = false;

      for(int i = 0; i < flags.length; i++) {
         if(begin) {
            if(!flags[i]) {
               jump++;
            }
            else {
               break;
            }
         }
         else {
            if(flags[i]) {
               begin = true;
            }
         }
      }

      return jump;
   }

   /**
    * Calculate all contents display length.
    * @param g The graphics.
    * @param contents The display strings.
    * @param font The display font.
    * @param flags To calculate the length of all strings, use null,
    * or define which string will be calculated.
    */
   private double calculateStringLength(Graphics2D g, String[] contents,
      Font font, boolean[] flags)
   {
      g.setFont(font);
      FontMetrics fm = g.getFontMetrics();
      double contentsLength = 0;

      for(int i = 0; i < contents.length; i++) {
         if(flags == null || flags[i]) {
            contentsLength += fm.stringWidth(contents[i]);
         }
      }

      return contentsLength;
   }

  /**
    * Set the scale of the image.
    * @param scaleX the width's scale.
    * @param scaleY the heigth's scale.
    */
   @Override
   protected void setScale(double scaleX, double scaleY) {
      super.setScale(scaleX, scaleY);

      double minScale = Math.min(scaleX, scaleY);
      valueStartY = valueStartY * scaleY;
      centerY = centerY * scaleY;
      recStartY = recStartY * scaleY;
      recWidth = recWidth * scaleY;
      majorTickStartY = majorTickStartY * scaleY;
      majorTickStartX = majorTickStartX * scaleX;
      majorTickEndX = majorTickEndX * scaleX;
      startXAdjustment = startXAdjustment * scaleX;
      recStartX = recStartX * scaleX;
      rangeStartY = rangeStartY * scaleY;
      rangeWidth = rangeWidth * scaleY;
      ticksLength = ticksLength * scaleX;
      minorTickWidth = minorTickWidth * scaleX;
      majorTickWidth = majorTickWidth * scaleX;
      minorTickLength = minorTickLength * scaleY;
      majorTickLength = majorTickLength * scaleY;
      valueMargin = valueMargin * scaleX;
   }

   protected double valueStartY;
   protected double startXAdjustment;
   protected double centerY;
   protected double ticksLength;

   protected double rangeStartY;
   protected double rangeWidth;

   protected double majorTickEndX;
   protected double majorTickStartX;
   protected double majorTickStartY;

   protected Color recTopColor;
   protected Color recBottomColor;
   protected double recStartY;
   protected double recStartX;
   protected double recWidth;
   protected double valueMargin;

   private static final Logger LOG =
      LoggerFactory.getLogger(VSHorizontalThermometer.class);
}
