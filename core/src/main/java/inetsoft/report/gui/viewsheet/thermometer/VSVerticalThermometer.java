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
 * VSThermometer, a thermometer style VSObject.
 *
 * @version 8.5, 2006-7-3
 * @author InetSoft Technology Corp
 */
public class VSVerticalThermometer extends VSThermometer {
   /**
    * Parse an xml segment.
    */
   @Override
   protected void parseXML(Element node) throws Exception {
      super.parseXML(node);

      Element mtNode = Tool.getChildNodeByTagName(node, "majorTick");
      centerX = Double.parseDouble(Tool.getAttribute(mtNode, "centerX"));
      majorTickStartX = centerX - majorTickLength / 2;
      ticksHeight = Double.parseDouble(
         Tool.getAttribute(mtNode, "height"));
      startYAdjustment = Double.parseDouble(
         Tool.getAttribute(mtNode, "startYAdjustment"));

      Element vNode = Tool.getChildNodeByTagName(node, "value");
      valueStartX = Double.parseDouble(Tool.getAttribute(vNode, "startX"));

      Element recNode = Tool.getChildNodeByTagName(node, "valueRec");
      recWidth = Double.parseDouble(Tool.getAttribute(recNode, "width"));
      recStartX = centerX - recWidth / 2;

      recLeftColor = parseColor(Tool.getAttribute(recNode, "leftColor"));
      recRightColor = parseColor(Tool.getAttribute(recNode, "rightColor"));
      recStartY = Double.parseDouble(Tool.getAttribute(recNode, "startY"));
      majorTickStartY = recStartY - startYAdjustment;
      majorTickEndY = majorTickStartY  - ticksHeight;

      Element rangeNode = Tool.getChildNodeByTagName(node, "range");
      rangeStartX = Double.parseDouble(Tool.getAttribute(rangeNode, "startX"));
      rangeWidth = Double.parseDouble(Tool.getAttribute(rangeNode, "width"));
   }

   /**
    * Fill the ranges
    * @param g the graphics.
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
      int rangeHeight = (int) ticksHeight;
      int startx = (int) rangeStartX;
      int starty = (int) majorTickStartY;

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
         double hrate = (range - (i > 0 ? ranges[i - 1] : min)) / (max - min);
         double yrate = (range - min) / (max - min);
         int height = (int) (hrate * rangeHeight);
         int y = (int) (majorTickStartY - yrate * rangeHeight);

         // get the y-axis value of next range
         double nextRange = i > 0 ? ranges[i - 1] : min;
         nextRange = nextRange < min ? min : nextRange;
         nextRange = nextRange > max ? max : nextRange;
         double nextYrate = (nextRange - min) / (max - min);
         int nextY = (int) (majorTickStartY - nextYrate * rangeHeight);

         if(info.isRangeGradient()) {
            if(c2 == null) {
               if(c1 != null) {
                  c2 = c1.darker();
               }
               else {
                  continue;
               }
            }

            GradientPaint gra = new GradientPaint(0, y, c2, 0, y + height, c1);
            g.setPaint(gra);
         }
         else {
            g.setColor(c1);
         }

         // define the trapezia area of the range
         Point p1 = new Point(startx, y);
         Point p2 = new Point(startx - (int) (rangeWidth * (yrate + 0.5)), y);
         Point p3 = new Point(startx - (int) (rangeWidth * (nextYrate + 0.5)), nextY);
         Point p4 = new Point(startx, nextY);
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
      double realHeight = rate * (majorTickStartY - majorTickEndY);
      double adjust = startYAdjustment;
      realHeight += adjust;
      double starty = majorTickStartY + adjust;
      double endy = starty - realHeight;
      double startx = recStartX;
      double endx = recStartX + recWidth;
      int width = (int) recWidth;
      Point2D colorStart = new Point2D.Double(startx, endy);
      Point2D colorEnd = new Point2D.Double(endx, endy);
      GradientPaint paint = new GradientPaint(colorStart,
         recLeftColor, colorEnd, recRightColor);
      g.setPaint(paint);
      g.fillRect((int) startx, (int) endy, width, (int) realHeight);
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

      if(majorTickCount < 0 || minorTickBetweenMajorTicks < 0) {
         return;
      }

      double[] values = new double[majorTickCount];
      String[] labels = new String[majorTickCount];

      // calculate the values should be displayed
      for(int i = 0; i < majorTickCount - 1; i++) {
         values[i] = dMinValue + majorInc * i;
         labels[i] = formatValue(dMinValue + majorInc * i, false);
      }

      labels[majorTickCount - 1] = formatValue(dMaxValue, false);
      values[majorTickCount - 1] = dMaxValue;
      labels = abbreviate(labels);

      // draw the ticks and the values
      int startx = (int) (majorTickStartX);
      double stringStartX = valueStartX;
      double starty = majorTickStartY;
      double endy = majorTickEndY;
      double majorDelta = (starty - endy) * majorInc / (dMaxValue - dMinValue);
      double minorDelta = (minorInc == 0) ? 0 :
         (starty - endy) * minorInc / (dMaxValue - dMinValue);

      boolean[] flags = new boolean[values.length];
      flags = resetFlags(flags, 0);
      Font font = adjustValuesFont(g, majorDelta, labels);
      FontMetrics fm = g.getFontMetrics();
      double fontHeight = fm.getHeight() - fm.getDescent();
      g.setFont(font);
      g.setColor(majorTickColor);
      double begin = starty;
      double btxDelta = (majorTickLength - minorTickLength) / 2;
      double valueMaxLength = getValueEndX() - valueStartX;

      labels = getDisplayedValues(valueMaxLength, labels, fm);

      // calculate the display flags
      while(isValuesOverlapped(fontHeight, majorDelta, flags)) {
         int jump = getJump(flags);
         flags = resetFlags(flags, jump + 1);

         if(jump >= flags.length - 1) {
            break;
         }
      }

      checkMaxOverlapped(g, fontHeight, flags, values);

      if(majorInc < minorInc || (dMaxValue - dMinValue) < majorInc) {
         drawTick(g, new Point2D.Double(startx, starty),
            new Point2D.Double(startx + majorTickLength, starty),
            majorTickWidth, majorTickColor);
         drawTick(g, new Point2D.Double(startx, endy),
            new Point2D.Double(startx + majorTickLength, endy),
            majorTickWidth, majorTickColor);
         Common.drawString(g, labels[0], (float) stringStartX,
               (float) (starty + fontHeight / 2));
         Common.drawString(g, labels[labels.length - 1], (float) stringStartX,
               (float) (endy + fontHeight / 2));

         return;
      }

      for(int i = 0; i < majorTickCount; i++) {
         Point2D start = new Point2D.Double(startx, begin);
         Point2D end = new Point2D.Double(startx + majorTickLength, begin);

         // draw major ticks
         drawTick(g, start, end, majorTickWidth, majorTickColor);
         g.setColor(getForeground());

         // draw value
         if(flags[i]) {
            Common.drawString(g, labels[i], (float) stringStartX,
               (float) (begin + fontHeight / 2));
         }

         // draw minor ticks
         double majorTickValue = dMinValue + majorInc * i;

         for(int j = 0; j < minorTickBetweenMajorTicks; j++) {
            if((majorTickValue + minorInc * (j + 1)) >= dMaxValue) {
               break;
            }

            double y = begin - minorDelta * (j + 1);
            Point2D mstart = new Point2D.Double(startx + btxDelta, y);
            Point2D mend = new Point2D.Double(
               startx + btxDelta + minorTickLength, y);
            drawTick(g, mstart, mend, minorTickWidth, minorTickColor);
         }

         if(i < majorTickCount - 2) {
            begin -= majorDelta;
         }
         else {
            begin = endy;
         }
      }
   }

   /**
    * Get displayed Values.
    */
   private String[] getDisplayedValues(double maxLength, String[] values,
                                       FontMetrics fm) {
      double stringWidth;
      String dotsString = "..";
      double dotsStringWidth = fm.stringWidth(dotsString);

      for(int i = 0; i < values.length; i++) {
         stringWidth = fm.stringWidth(values[i]);

         if(stringWidth < maxLength) {
            continue;
         }

         while(stringWidth > maxLength - dotsStringWidth) {
            int endIndex = values[i].length() - 2;

            if(endIndex < 0) {
               endIndex = 0;
               break;
            }

            values[i] = values[i].substring(0, endIndex);
            stringWidth = fm.stringWidth(values[i]);
         }

         values[i] = values[i] + dotsString;
      }

      return values;
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
    * Check if the values overlap each other.
    */
   private boolean isValuesOverlapped(double fontHeigth, double delta,
                                      boolean[] flags) {
      int jump = getJump(flags) + 1;
      return fontHeigth + gap > delta * jump;
   }

   /**
    * Check if the max is overlapped.
    */
   private void checkMaxOverlapped(Graphics2D g, double fontHeight,
                                   boolean[] flags, double[] values) {
      if(flags.length < 2 || !flags[flags.length - 1]) {
         return;
      }

      int valueBeforeMaxIndex = flags.length - 2;

      for(; !flags[valueBeforeMaxIndex]; valueBeforeMaxIndex--) {
         if(valueBeforeMaxIndex == 0) {
            return;
         }
      }

      ThermometerVSAssemblyInfo info = getThermometerAssemblyInfo();
      double min = info.getMin();
      double max = info.getMax();
      double valueBeforeMax = values[valueBeforeMaxIndex];
      double height = ticksHeight * (max - valueBeforeMax) / (max - min);

      if(height < fontHeight + gap) {
         flags[valueBeforeMaxIndex] = false;
      }
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
    * Adjust the font so that the value string will not out of the image.
    */
   protected Font adjustValuesFont(Graphics2D g, double heightDelta,
                                   String[] values) {
      Font font = getFont();
      g.setFont(font);
      double x = valueStartX;
      double dis = contentSize.getWidth() - x;
      String s = values[getMaxStringLength(values, g)];
      Font result = font;
      FontMetrics fm = g.getFontMetrics();

      while(fm.stringWidth(s) > dis ||
            fm.getHeight() - fm.getDescent() >= heightDelta) {
         if(result.getSize() <= MIN_FONT_SIZE) {
            break;
         }

         result = reduceSize(result);
         g.setFont(result);
         fm = g.getFontMetrics();
      }

      return result;
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
      valueStartX = valueStartX * scaleX;
      centerX = centerX * scaleX;
      recStartX = recStartX * scaleX;
      recWidth = recWidth * scaleX;
      majorTickStartX = majorTickStartX * scaleX;
      majorTickStartY = majorTickStartY * scaleY;
      majorTickEndY = majorTickEndY * scaleY;
      startYAdjustment = startYAdjustment * scaleY;
      recStartY = recStartY * scaleY;
      minorTickWidth = minorTickWidth * scaleY;
      majorTickWidth = majorTickWidth * scaleY;
      minorTickLength = minorTickLength * scaleX;
      majorTickLength = majorTickLength * scaleX;
      rangeStartX = rangeStartX * scaleX;
      rangeWidth = rangeWidth * scaleX;
      ticksHeight = ticksHeight * scaleY;
      gap = gap * scaleY;
   }

   /**
    * Get value end x.
    */
   protected double getValueEndX() {
      return contentSize.getWidth() -
         MARGIN_WIDTH * contentSize.getWidth() / DEFAULT_WIDTH ;
   }

   protected final int DEFAULT_HEIGHT = 200;
   protected final int DEFAULT_WIDTH = 80;
   protected final int MARGIN_WIDTH = 3;

   protected double valueStartX;
   protected double centerX;
   protected double startYAdjustment;
   protected double ticksHeight;
   protected double majorTickEndY;
   protected double majorTickStartY;
   protected double majorTickStartX;
   protected Color recLeftColor;
   protected Color recRightColor;
   protected double recStartX;
   protected double recWidth;
   protected double recStartY;
   protected double rangeStartX;
   protected double rangeWidth;
   protected double gap = 2;

   private static final Logger LOG =
      LoggerFactory.getLogger(VSVerticalThermometer.class);
}
