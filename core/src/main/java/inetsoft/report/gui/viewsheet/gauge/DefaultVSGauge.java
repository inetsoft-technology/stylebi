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
package inetsoft.report.gui.viewsheet.gauge;

import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;
import inetsoft.util.graphics.SVGSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URL;

/**
 * The VSGauge's default implementation.
 *
 * @version 8.5, 06/19/2006
 * @author InetSoft Technology Corp
 */
public class DefaultVSGauge extends VSGauge {
   /**
    * Create the gauge's image.
    */
   @Override
   public BufferedImage getContentImage() {
      adjust(true);

      BufferedImage image = new BufferedImage(contentSize.width,
         contentSize.height, BufferedImage.TYPE_4BYTE_ABGR);

      try {
         Image img = createPanelImage();
         Graphics2D g = (Graphics2D) img.getGraphics();
         setRenderingStratergy(g);
         GaugeVSAssemblyInfo info = getGaugeAssemblyInfo();

         if(info.getMax() > info.getMin() && info.getMajorInc() > 0) {
            fillRanges(g);
            fillHighlight(g);
            drawTicksAndValues(g);
            drawNeedle(g);
            g.dispose();
         }

         Graphics2D g2 = (Graphics2D) image.getGraphics();

         if(isDrawbg()) {
            drawBackground(g2);
         }

         g2.drawImage(img, 0, 0, null);
         g2.dispose();
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
      }

      return image;
   }

   @Override
   public Graphics2D getContentSvg(boolean isShadow) {
      Graphics2D g = null;
      adjust(false);

      try {
         GaugeVSAssemblyInfo info = getGaugeAssemblyInfo();
         Color bg = isDrawbg() ? getBackground() : null;
         g = SVGSupport.getInstance().getSVGGraphics(
            getPathUrl(panelPath), getPixelSize(), isShadow, bg, scale,
            info.getFormat().getRoundCorner());
         setRenderingStratergy(g);

         if(info.getMax() > info.getMin() && info.getMajorInc() > 0) {
            fillRanges(g);
            fillHighlight(g);
            drawTicksAndValues(g);
            // Populate the document root with the generated SVG content.
            SVGSupport svgSupport = SVGSupport.getInstance();
            Element root = svgSupport.getSVGRootElement(g);
            Document doc = svgSupport.getSVGDocument(g);
            Element svg = Tool.getChildNodeByTagName(doc, "svg");

            if(svg != null) {
               svg.appendChild(root);
            }

            drawSVGNeedle(g);
         }
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
      }

      return g;
   }

   @Override
   protected void paintComponent(Graphics2D g) {
      Graphics2D svg = getContentSvg(isShadow());

      // get root would include the <defs> (for gradient). it's not in the base document
      SVGSupport svgSupport = SVGSupport.getInstance();
      Element root = svgSupport.getSVGRootElement(svg);
      Document doc = svgSupport.getSVGDocument(svg);
      Element svgElement = Tool.getChildNodeByTagName(doc, "svg");

      if(svgElement != null) {
         svgElement.appendChild(root);
      }

      try {
         byte[] buf = svgSupport.transcodeSVGImage(doc);
         ByteArrayInputStream baos = new ByteArrayInputStream(buf);
         Image img = svgSupport.getSVGImage(baos);
         BufferedImage bufferedImage = (BufferedImage) img;
         Point startPoint = getPosition();

         if(svg != null) {
            g.drawImage(img, startPoint.x, startPoint.y,
               bufferedImage.getWidth(), bufferedImage.getHeight(), null);
         }
      } catch(Exception e) {
         LOG.debug("Draw the gauge image error", e);
      }
   }

   public void fillHighlight(Graphics2D g) {
      //do nothing
   }

   public void drawSVGNeedle(Graphics2D g2d) {
      double radian = calculateNeedleRadian();
      AffineTransform transform = AffineTransform.getScaleInstance(scale, scale);
      transform.rotate(radian, center.getX() / scale, center.getY() / scale);
      // merger needlePath svg file
      SVGSupport.getInstance().mergeSVGDocument(g2d, getPathUrl(needlePath), transform);
      transform = AffineTransform.getScaleInstance(scale, scale);
      // merger needleCirclePath svg file
      SVGSupport.getInstance().mergeSVGDocument(g2d, getPathUrl(needleCirclePath), transform);
   }

   public String getPathUrl(String relativePath) {
      URL url = VSGauge.class.getResource(relativePath);
      return getConvertURLString(url);
   }

   /**
    * Create panel image as the background.
    */
   protected BufferedImage createPanelImage() throws Exception {
      AffineTransform transform =
         AffineTransform.getScaleInstance(scale, scale);
      BufferedImage image = getImageByURI(panelPath, transform);

      return image;
   }

   /**
    * Draw all ranges.
    */
   protected void fillRanges(Graphics2D g2d) {
      if(!isFillRanges()) {
         return;
      }

      GaugeVSAssemblyInfo info = getGaugeAssemblyInfo();
      double[] ranges = info.getRanges();
      Color[] rangeColors = info.getRangeColors();
      fillRanges0(g2d, ranges, rangeColors, info.isRangeGradient());
   }

   protected void fillRanges0(Graphics2D g2d, double[] ranges, Color[] rangeColors, boolean gradient) {
      if(ranges.length == 0) {
         return;
      }

      try {
         GaugeVSAssemblyInfo info = getGaugeAssemblyInfo();
         double beginRadian = getValueDrawingBeginRadian();
         double radius = rangeRadius;
         double width = rangeWidth;
         double begin = beginRadian;
         double radianMaxRange = getRadianBetweenBeginAndEnd();
         double rangeBegin = info.getMin();
         double rangeEnd = 0.0;

         Loop:
         for(int i = 0; i < ranges.length; i++) {
            for(int k = i - 1; i != 0 && k >= 0; k--) {
               if(i != 0 && ranges[i] < ranges[k]) {
                  continue Loop;
               }
            }

            // not between min and max? ignore it
            if(ranges[i] <= info.getMin() || ranges[i] >= info.getMax() && i > 0
               && ranges[i - 1] >= info.getMax())
            {
               continue;
            }
            else if(ranges[i] > info.getMax() &&
               (i > 0 && ranges[i - 1] < info.getMax() || i == 0))
            {
               rangeEnd = info.getMax();
            }
            else {
               rangeEnd = !Double.isNaN(ranges[i]) ? ranges[i] : rangeEnd;
            }

            double rangeDelta = rangeEnd - rangeBegin;
            double delta = radianMaxRange *
               rangeDelta / (info.getMax() - info.getMin());
            Color c1 = null;
            Color c2 = null;

            if(i < rangeColors.length) {
               c1 = rangeColors[i];
               c2 = (i < rangeColors.length - 1) ? rangeColors[i + 1] : c1;
            }

            if(c2 == null && c1 != null) {
               c2 = c1.darker();
            }

            if(rangeBegin == info.getMin() && rangeEnd != info.getMax()) {
               // the left range
               fillRange(g2d, center, begin + getAngleAdjustment(),
                  begin - delta , c1, c2, radius, width,
                  isRoundRanges(), false, gradient);
            }
            else if(rangeEnd == info.getMax() && rangeBegin != info.getMin()) {
               // the right range
               fillRange(g2d, center, begin,
                  begin - delta - getAngleAdjustment(), c1, c2,
                  radius, width, false, isRoundRanges(), gradient);
            }
            else if(rangeEnd == info.getMax() && rangeBegin == info.getMin()) {
               // the range covers from min to max
               fillRange(g2d, center, begin + getAngleAdjustment(),
                  begin - delta - getAngleAdjustment(), c1, c2,
                  radius, width, isRoundRanges(), isRoundRanges(), gradient);
            }
            else {
               // the middle ranges
               fillRange(g2d, center, begin, begin - delta, c1, c2,
                  radius, width, false, false, gradient);
            }

            begin = begin - delta;
            rangeBegin = rangeEnd;
         }
      }
      catch(ArrayIndexOutOfBoundsException ae) {
         LOG.error("Fill ranges in gauge error, the range and " +
            "range color array do not match.", ae);
      }
   }

   /**
    * Draw all ticks line and the tick's value.
    */
   protected void drawTicksAndValues(Graphics2D g2d) {
      Color originColor = g2d.getColor();
      GaugeVSAssemblyInfo info = (GaugeVSAssemblyInfo) getAssemblyInfo();
      double majorInc = info.getMajorInc();
      double minorInc = info.getMinorInc();
      double dMinValue = info.getMin();
      double dMaxValue = info.getMax();
      // calculate the number of ticks, should consider the case when the
      // last tick is too close the the max. The round() would ignore the
      // last tick if it's less that 1/2 of the increment
      int majorTickCount = (int) Math.round((dMaxValue - dMinValue) / majorInc) + 1;

      if(majorTickCount >= MAX_MAJOR_TICK_COUNT * 2) {
         LOG.info("Too many major ticks found in gauge: " + majorTickCount);
         int inc = (majorTickCount / MAX_MAJOR_TICK_COUNT);
         majorInc *= inc;
         minorInc *= inc;
         majorTickCount = (int) Math.ceil((dMaxValue - dMinValue) / majorInc) + 1;
      }

      int minorTickBetweenMajorTicks = minorInc == 0 ? 0 : (int) Math.ceil(majorInc / minorInc) - 1;
      final int MAX_MINOR_TICKS = 20;

      // sanity check, make sure not too many ticks.
      if(minorTickBetweenMajorTicks > MAX_MINOR_TICKS) {
         minorInc = majorInc / MAX_MINOR_TICKS;
         minorTickBetweenMajorTicks = (int) Math.ceil(majorInc / minorInc) - 1;
      }

      double beginRadian = getValueDrawingBeginRadian();
      double radianMaxRange = getRadianBetweenBeginAndEnd();
      double majorTickRedianDelta = radianMaxRange * majorInc / (dMaxValue - dMinValue);
      double minorTickRedianDelta = (minorInc == 0) ? 0 :
         radianMaxRange * minorInc / (dMaxValue - dMinValue);
      String[] labels = new String[majorTickCount];
      double[] values = new double[majorTickCount];

      // calculate the values should be displayed
      for(int i = 0; i < majorTickCount - 1; i++) {
         values[i] = dMinValue + i * majorInc;
         labels[i] = formatValue(values[i], false);
      }

      labels[majorTickCount - 1] = formatValue(dMaxValue, false);
      values[majorTickCount - 1] = dMaxValue;
      labels = abbreviate(labels);

      // adjust the values font
      Font valueFont = adjustValueFont(g2d, labels);
      boolean[] drawValueFlags =
         calculateValuesDrawingFlags(g2d, labels, values, valueFont);
      g2d.setFont(valueFont);
      double descent = g2d.getFontMetrics().getDescent();
      FontMetrics fm = g2d.getFontMetrics();
      double fontHeight = fm.getHeight() - fm.getDescent();

      // calculate the actual radius of the tick's value
      if(valueRadius == 0) {
         valueRadius = rangeRadius - rangeWidth / 2 - fontHeight / 2;
      }

      double endRadian = beginRadian - radianMaxRange;

      if(majorInc < minorInc || dMaxValue - dMinValue < majorInc) {
         drawString(g2d, center, beginRadian, valueRadius, labels[0], 0);
         drawString(g2d, center, beginRadian - radianMaxRange, valueRadius,
                    labels[majorTickCount - 1], 0);
         return;
      }

      for(int i = 0; i < majorTickCount; i++) {
         double majorTickValue = dMinValue + majorInc * i;
         double radian = beginRadian - majorTickRedianDelta * i;

         if(i == majorTickCount - 1 && radian < endRadian) {
            radian = endRadian;
         }

         // draw major tick
         drawTick(g2d, center, radian, majorTickRadius, majorTickLength,
                  majorTickLineWidth, majorTickColor, true);

         // draw values
         drawValue(g2d, center, drawValueFlags, radian, labels[i], majorTickCount, i);

         // draw minor ticks
         for(int j = 0; j < minorTickBetweenMajorTicks; j++) {
            if((majorTickValue + minorInc * (j + 1)) >= dMaxValue) {
               break;
            }

            drawTick(g2d, center, radian - minorTickRedianDelta * (j + 1),
                     minorTickRadius, minorTickLength,
                     minorTickLineWidth, minorTickColor, false);
         }
      }

      g2d.setColor(originColor);
   }

   protected void drawValue(Graphics2D g2d, Point2D center, boolean[] drawValueFlags, double radian,
                            String label, int majorTickCount, int idx)
   {
      drawValue(g2d, center, drawValueFlags, radian, label, idx, true);
   }

   protected void drawValue(Graphics2D g2d, Point2D center, boolean[] drawValueFlags, double radian,
                            String label, int idx, boolean showValue)
   {
      try {
         if(valueVisible && showValue && (drawValueFlags == null || drawValueFlags[idx])) {
            // since the labels are drawn at the mid point of the
            // string at the tick position, a long label could get out of
            // bounds. The first label is normally shorter than the last
            // label so we shift it slight to the left (ccw) to reduce the
            // chance of label out of bounds
            double labelRadian = radian + Math.PI * 4 / 180;
            drawString(g2d, center, labelRadian, valueRadius, label, idx);
         }
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
      }
   }

   /**
    * Draw the needle of the gauge.
    * @param g2d the graphics of drawing.
    */
   protected void drawNeedle(Graphics2D g2d) {
      // draw needle
      BufferedImage needle = null;
      double radian = calculateNeedleRadian();
      AffineTransform transform = AffineTransform.getScaleInstance(scale, scale);
      transform.rotate(radian, center.getX() / scale, center.getY() / scale);

      try {
         needle = getNeedleImage(transform);
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
         return;
      }

      g2d.drawImage(needle, null, 0, 0);

      try {
         AffineTransform nonT = AffineTransform.getScaleInstance(scale,
            scale);
         g2d.drawImage(getNeedleCircleImage(nonT), 0, 0, null);
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
      }
   }

   /**
    * Caculate the needle's display radian.
    */
   protected double calculateNeedleRadian() {
      GaugeVSAssemblyInfo info = (GaugeVSAssemblyInfo) getAssemblyInfo();
      // we should use value regardless of whether it's bound or not because
      // the value could be set from script
      double value = getValue();

      if(value <= info.getMin()) {
         value = info.getMin();
      }

      if(value >= info.getMax()) {
         value = info.getMax();
      }

      double minRadian = getValueDrawingBeginRadian();
      double rate = (value - info.getMin()) / (info.getMax() - info.getMin());
      double needleRadian = getRadianBetweenBeginAndEnd() * rate;
      double offset = needleRadian - minRadian;

      return offset;
   }

   /**
    * Get the image of needle.
    * @param transform the transform to the needle.
    */
   protected BufferedImage getNeedleImage(AffineTransform transform)
      throws Exception
   {
      return getImageByURI(needlePath, transform);
   }

   /**
    * Get the needle circle image.
    * @param transform the transform maybe need to use.
    */
   protected BufferedImage getNeedleCircleImage(AffineTransform transform)
      throws Exception
   {
      return getImageByURI(needleCirclePath, transform);
   }

   /**
    * Calculate the beginning of the values, ticks and ranges drawing angle.
    */
   protected double getValueDrawingBeginRadian() {
      double begin = Math.PI / 2 + angle / 2 + getRotation();

      while(begin > Math.PI * 2) {
         begin -= Math.PI * 2;
      }

      return begin;
   }

   /**
    * Caculate the end of the values, ticks and ranges drawing angle.
    */
   protected double getValueDrawingEndRadian() {
      double end = getValueDrawingBeginRadian() - angle;
      return end;
   }

   /**
    * Fill one range.
    * @param g The graphics object to draw.
    * @param center The range's circle center point.
    * @param beginRadian The arc's begin radian.
    * @param endRadian   The arc's end radian.
    * @param radius      The range's outter edge radius.
    * @param rangeWidth  The range's width.
    * @param leftRound   If the range need to be round on left.
    * @param rightRound  If the range need to be round on right.
    */
   protected void fillRange(Graphics2D g, Point2D center, double beginRadian,
                            double endRadian, Color color1, Color color2,
                            double radius, double rangeWidth,
                            boolean leftRound, boolean rightRound,
                            boolean gradient)
   {
      if(color1 == null || color2 == null) {
         return;
      }

      boolean exchange = false;

      if(beginRadian < endRadian) {
         double temp = endRadian;
         endRadian = beginRadian;
         beginRadian = temp;
         exchange = true;
      }

      Color originColor = g.getColor();
      Arc2D arc = new Arc2D.Double();
      double beginDegree = radianToDegree(beginRadian);
      double endDegree = radianToDegree(endRadian);
      double delta = endDegree - beginDegree;

      arc.setArcByCenter(center.getX(), center.getY(), radius,
         beginDegree, delta, Arc2D.PIE);

      Arc2D mask = new Arc2D.Double();
      mask.setArcByCenter(center.getX(), center.getY(), radius - rangeWidth,
         beginDegree, delta, Arc2D.PIE);

      Area range = new Area();

      if(!exchange) {
         range.add(new Area(arc));
         range.subtract(new Area(mask));
      }

      if(leftRound) {
         // draw the left round of the range
         Point2D roundCenter = calculatePoint(center, radius - rangeWidth / 2,
            beginRadian);
         double roundRadius = rangeWidth / 2;
         Arc2D arcLeft = new Arc2D.Double();
         arcLeft.setArcByCenter(roundCenter.getX(), roundCenter.getY(),
            roundRadius, beginDegree, 180, Arc2D.PIE);
         range.add(new Area(arcLeft));
      }

      if(rightRound) {
         // draw the right round of the range
         Point2D roundCenter = calculatePoint(center, radius - rangeWidth / 2,
            endRadian);
         double roundRadius = rangeWidth / 2;
         Arc2D arcRight = new Arc2D.Double();
         arcRight.setArcByCenter(roundCenter.getX(), roundCenter.getY(),
            roundRadius, endDegree, -180, Arc2D.PIE);
         Area roundArea = new Area(arcRight);
         range.add(roundArea);
      }

      if(gradient) {
         Point2D s = calculatePoint(center, radius, beginRadian);
         Point2D e = calculatePoint(center, radius, endRadian);
         /* SVG GradientExtensionHandler only support LinearGradientPaint
         GradientPaint gra = new GradientPaint(
            (float) s.getX(), (float) s.getY(), color1,
            (float) e.getX(), (float) e.getY(), color2);
         */

         if(!s.equals(e)) {
            g.setPaint(new LinearGradientPaint(s, e, new float[] { 0, 1 },
                                               new Color[] { color1, color2 },
                                               MultipleGradientPaint.CycleMethod.NO_CYCLE));
         }
      }
      else {
         g.setColor(color1);
      }

      g.fill(range);
      g.setColor(originColor);
   }

   /**
    * Draw one tick.
    * @param g The graphics object to draw.
    * @param center The panel's center point.
    * @param radian The tick's radian relative to the center point.
    * @param radius The tick's outter point radius relative to the center.
    * @param lineLength The tick's length.
    * @param lineWidth The tick's line width.
    * @param color The tick's color.
    */
   protected void drawTick(Graphics2D g, Point2D center, double radian,
      double radius, double lineLength, double lineWidth, Color color, boolean major)
   {
      if(major && !majorTickVisible || !major && !minorTickVisible) {
         return;
      }

      Point2D begin = calculatePoint(center, radius, radian);
      Point2D end = calculatePoint(center, radius - lineLength, radian);
      g.setStroke(new BasicStroke((float) lineWidth, BasicStroke.CAP_ROUND,
         BasicStroke.JOIN_MITER));
      Color originColor = g.getColor();
      g.setColor(color);
      Line2D line = new Line2D.Double();
      line.setLine(begin, end);
      g.draw(line);
      g.setColor(originColor);
   }

   protected void drawString(Graphics2D g2d, Point2D center, double radian,
                             double radius, String content, int idx)
   {
      drawString(g2d, center, radian, radius, content);
   }

   /**
    * Draw a string on the panel.
    * @param g The graphics object to draw.
    * @param center The panel's center point.
    * @param radian The string's center point's radian relative to
    *                   the panel's center point.
    * @param radius The string's center point's radius relative to
    *                   the panel's center point.
    * @param content The string itself.
    */
   protected void drawString(Graphics2D g, Point2D center, double radian,
      double radius, String content)
   {
      Font font = labelFmt.getFont();
      Color color = labelFmt.getForeground();
      FontMetrics fm = g.getFontMetrics();
      double stringWidth = fm.stringWidth(content);
      double stringRadian = stringWidth / radius;

      radian = radian + stringRadian / 2;
      g.setColor(color);
      g.setFont(font);

      for(int i = 0; i < content.length() ; i++) {
         String str = content.substring(i, i + 1);
         double charWidth = fm.stringWidth(content.substring(0, i + 1)) -
            fm.stringWidth(content.substring(0, i));
         double charRadian = stringRadian * charWidth / stringWidth;
         Point2D tpos = getStringStartPoint(g, center, radian - charRadian / 2, radius, str, font);
         radian -= charRadian;

         Graphics2D g2 = (Graphics2D) g.create();
         g2.translate((float) tpos.getX(), (float) tpos.getY());
         g2.rotate(-(radian - Math.PI / 2));
         CoreTool.drawGlyph(g2, str, 0, 0);
         g2.dispose();
      }
   }

   /**
    * Calculate the string's start point, it base on the center, the font and the
    * affine transform of the string.
    * @param g The graphics object to draw.
    * @param center the panel's center point.
    * @param radian the string's center point's radian relative to
    *                   the panel's center point.
    * @param radius the string's center point's radius relative to
    *                   the panel's center point.
    * @param content the string itself.
    * @return The string drawing position.
    */
   protected Point2D getStringStartPoint(Graphics2D g, Point2D center,
      double radian, double radius, String content, Font font)
   {
      g.setFont(font);
      FontMetrics fm = g.getFontMetrics();
      double stringWidth = fm.stringWidth(content);
      Point2D stringCenter = calculatePoint(center, radius, radian);
      Point2D startPoint = calculatePoint(stringCenter, stringWidth / 2,
                                          Math.PI / 2 + radian);
      return startPoint;
   }

   /**
    * Calculate the position on the perimeter at the specified angle.
    * @param center The center point of the panel.
    * @param radius The point's distance to the center point.
    * @param radian The radial line's radian.
    */
   protected Point2D calculatePoint(Point2D center, double radius,
      double radian)
   {
      Point2D.Double point = new Point2D.Double();
      point.x = center.getX() + Math.cos(radian) * radius;
      point.y = center.getY() - Math.sin(radian) * radius;
      return point;
   }

   /**
    * Set the redering stratergy. The default is just set anti allasing.
    */
   protected void setRenderingStratergy(Graphics2D g) {
      // set rendering hints
      // commenting setRenderingStratergy will cause new bug
      RenderingHints hints = new RenderingHints(null);
      hints.put(RenderingHints.KEY_ANTIALIASING,
         RenderingHints.VALUE_ANTIALIAS_ON);
      hints.put(RenderingHints.KEY_TEXT_ANTIALIASING,
         RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      hints.put(RenderingHints.KEY_DITHERING,
         RenderingHints.VALUE_DITHER_ENABLE);
      g.setRenderingHints(hints);
   }

   /**
    * Caculate the radian between the begin and the end radian.
    */
   protected double getRadianBetweenBeginAndEnd() {
      double beginRadian = getValueDrawingBeginRadian();
      double endRadian = getValueDrawingEndRadian();
      double radianMaxRange = 0;

      if(endRadian < Math.PI) {
         radianMaxRange = beginRadian - endRadian;
      }
      else {
         radianMaxRange = beginRadian + (Math.PI * 2 - endRadian);
      }

      if(radianMaxRange < 0) {
         radianMaxRange = -radianMaxRange;
      }

      if(radianMaxRange > Math.PI * 2) {
         radianMaxRange = radianMaxRange - Math.PI * 2;
      }

      return radianMaxRange;
   }

   /**
    * Because the ticks and the values should be smaller than the ranges.
    * So the ranges' angle should be a minor wider than ticks.
    */
   protected double getAngleAdjustment() {
      // shorten the range at the round edge, so it looks same in length
      // when its middle part and two sides are very small
      //by yanie: bug1414745744312, if not roundrange, don't shorten
      return isRoundRanges() ?
         (-(rangeWidth) / (6 * (rangeRadius - rangeWidth / 2))) : 0;
   }

   /**
    * Adjust value font to make it possible to draw the value string in
    * the range.
    */
   protected Font adjustValueFont(Graphics2D g, String[] contents) {
      AffineTransform originTrans = g.getTransform();
      AffineTransform noneTrans = AffineTransform.getRotateInstance(0);
      g.setTransform(noneTrans);
      Font result = getFont();
      double rangeLen = getRangeLength();

      while(calcLabelsLength(g, contents, result, null) * 1.05 > rangeLen) {
         if(result.getSize() <= MIN_FONT_SIZE) {
            break;
         }

         result = reduceSize(result);
      }

      g.setTransform(originTrans);
      return result;
   }

   /**
    * Caculate value drawing flag. If the drawing flag is false, the value will
    * not be displayed.
    */
   protected boolean[] calculateValuesDrawingFlags(Graphics2D g,
      String[] contents, double[] values, Font font)
   {
      boolean[] drawValueFlags = new boolean[contents.length];
      double rangeLen = getRangeLength();
      double valuesLength = calcLabelsLength(g, contents, font, null);

      if(rangeLen > valuesLength) {
         for(int i = 0; i < drawValueFlags.length; i++) {
            drawValueFlags[i] = true;
         }
      }
      else {
         int jump = 0;
         int numTicks = contents.length - 1;

         do {
            // @by ankitmathur, Fix Bug #1192, to avoid infinite loop, we need
            // to make sure the jump increment is increased per loop. If not, the
            // following "while" condition is only going to be met once and
            // therefore the value flags will only be changed once.
            jump++;
            // make sure the skipped ticks are balanced (without different
            // size gaps)
            while(jump < numTicks && numTicks % (jump + 1) != 0) {
               jump++;
            }

            drawValueFlags = resetFlags(drawValueFlags, jump);
         }
         while(calcLabelsLength(g, contents, font, drawValueFlags) > rangeLen);
      }

      return drawValueFlags;
   }

   /**
    * Reset the flags.
    */
   private boolean[] resetFlags(boolean[] flags, int jump) {
      if(jump == 0) {
         return flags;
      }

      for(int i = 0; i < flags.length; i++) {
         if(i % (jump + 1) == 0) {
            flags[i] = true;
         }
         else {
            flags[i] = false;
         }
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
    * Caculate all contents display length.
    * @param g the graphics
    * @param contents the display strings
    * @param font     the display font
    * @param flags    If you want to calculate all strings length, you can use
    *                 null, or you can define which string will be added.
    */
   private double calcLabelsLength(Graphics2D g, String[] contents,
                                   Font font, boolean[] flags)
   {
      g.setFont(font);
      FontMetrics fm = g.getFontMetrics();
      double maxlabel = 0;
      int num = 0;

      for(int i = 0; i < contents.length; i++) {
         if(flags == null || flags[i]) {
            num++;
            maxlabel = Math.max(fm.stringWidth(contents[i]) -
                                // drawString calculated individual char width
                                // as stringWidth(c) - 1, so we minus the same
                                // amount here to match it
                                (contents[i].length() - 1), maxlabel);
         }
      }

      // since labels are drawn at center, this effectively gives it one extra
      // tick for the text
      return (num - 1) * maxlabel + STRING_GAP * (num - 2);
   }

   /**
    * Get the ranges circle length.
    */
   private double getRangeLength() {
      double length = 0;
      // should use the real value position radius if available
      double radius = (valueRadius > 0) ? valueRadius : rangeRadius;

      if(valueRadius <= 0 && isRoundRanges()) {
         radius += rangeWidth / 2;
      }

      length = radius * (getRadianBetweenBeginAndEnd() + getAngleAdjustment());

      return length;
   }

   private static int STRING_GAP = 1;
   private static int MIN_FONT_SIZE = 10;

   private static final Logger LOG =
      LoggerFactory.getLogger(DefaultVSGauge.class);
}
