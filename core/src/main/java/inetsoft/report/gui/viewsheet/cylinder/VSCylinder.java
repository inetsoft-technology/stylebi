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
package inetsoft.report.gui.viewsheet.cylinder;

import inetsoft.report.StyleFont;
import inetsoft.report.gui.viewsheet.VSFaceUtil;
import inetsoft.report.gui.viewsheet.VSImageable;
import inetsoft.report.internal.Common;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.CylinderVSAssemblyInfo;
import inetsoft.util.*;
import inetsoft.util.graphics.SVGSupport;
import inetsoft.util.graphics.SVGTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * VSCylinder component for viewsheet.
 *
 * @version 8.5, 2006-6-29
 * @author InetSoft Technology Corp
 */
public class VSCylinder extends VSImageable implements Cloneable {
   /**
    * Get a cylinder instance.
    */
   public static synchronized VSCylinder getCylinder(int id) {
      if(fmap == null) {
         try {
            initMap();
         }
         catch(Exception e) {
            LOG.error(e.getMessage(), e);
         }
      }

      id += VSFaceUtil.getCurrentThemeID();

      String ID = Integer.toString(id);
      VSCylinder cylinder = (VSCylinder) fmap.get(ID);

      if(cylinder == null) {
         return null;
      }

      return (VSCylinder) cylinder.clone();
   }

   /**
    * Get all the available ids.
    * @return all the available ids.
    */
   public static synchronized String[] getIDs() {
      if(fmap == null) {
         try {
            initMap();
         }
         catch(Exception e) {
            LOG.error(e.getMessage(), e);
         }
      }

      return VSFaceUtil.getIDs(fmap);
   }

   /**
    * Get all the available prefix ids excluding the theme part.
    * @return all the available prefix ids.
    */
   public static synchronized String[] getPrefixIDs() {
      if(fmap == null) {
         try {
            initMap();
         }
         catch(Exception e) {
            LOG.error(e.getMessage(), e);
         }
      }

      return VSFaceUtil.getPrefixIDs(fmap);
   }

   /**
    * Reset the Cylinder's configuration.
    */
   public static synchronized void reset() {
      fmap = null;
   }

   /**
    * Constructor.
    */
   public VSCylinder() {
      super(null);
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      try {
         VSCylinder cylinder = (VSCylinder) super.clone();
         cylinder.contentSize = contentSize == null ?
            null : (Dimension) contentSize.clone();
         return cylinder;
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
         return null;
      }
   }

   /**
    * Adjust scale and margin according to new size.
    */
   protected void adjust() {
      Dimension size = getSize();

      double scaleX = (double) size.width / (double) defaultSize.width;
      double scaleY = (double) size.height / (double) defaultSize.height;

      setScale(scaleX, scaleY);
   }

   /**
    * Get the image of the cylinder.
    */
   @Override
   public BufferedImage getContentImage() {
      adjust();

      BufferedImage image = new BufferedImage(contentSize.width,
         contentSize.height, BufferedImage.TYPE_4BYTE_ABGR);
      Image img = createPanelImage();
      Graphics2D g = (Graphics2D) img.getGraphics();
      setRenderingStrategy(g);
      CylinderVSAssemblyInfo info = getCylinderAssemblyInfo();

      if(info.getMax() > info.getMin()) {
         drawCylinder(g);
         fillRanges(g);
         drawTicksAndValues(g);
         g.dispose();
      }

      Graphics2D g2 = (Graphics2D) image.getGraphics();

      if(isDrawbg()) {
         drawBackground(g2);
      }

      g2.drawImage(img,(int) startPoint.getX(), (int) startPoint.getY(), null);
      g2.dispose();

      return image;
   }

   /**
    * Set isDrawbg.
    */
   public void setDrawbg(boolean isDrawbg) {
      this.isDrawbg = isDrawbg;
   }

   /**
    * Get isDrawbg.
    */
   public boolean isDrawbg() {
      return isDrawbg;
   }

   /**
    * Initialize the guage map.
    */
   private static void initMap() throws Exception  {
      fmap = new HashMap();
      Document document = Tool.parseXML(
         VSCylinder.class.getResourceAsStream("cylinder.xml"), "UTF-8");
      Element cylinderNode = Tool.getFirstElement(document);
      NodeList list = Tool.getChildNodesByTagName(cylinderNode, "face");

      for(int i = 0; i < list.getLength(); i++) {
         Element node = Tool.getNthChildNode(cylinderNode, i);
         String id = Tool.getAttribute(node, "id");
         String classname = node.getAttribute("classname");
         VSCylinder cylinder;

         if(classname != null && !"".equals(classname)) {
            Class cls = Class.forName(classname);
            cylinder = (VSCylinder) cls.newInstance();
         }
         else {
            cylinder = (VSCylinder) VSCylinder.class.newInstance();
         }

         cylinder.parseXML(node);
         fmap.put(id, cylinder);
      }
   }

   /**
    * Get default format.
    */
   public static VSCompositeFormat getDefaultFormat(int faceId) {
      VSCompositeFormat fmt = new VSCompositeFormat();
      VSCylinder cylinder = getCylinder(faceId);
      Color valueColor = cylinder.getValueColor();
      StyleFont valueFont = cylinder.getValueFont();
      fmt.getDefaultFormat().setForegroundValue(
         String.valueOf(valueColor.getRGB()));
      fmt.getDefaultFormat().setFontValue(valueFont);

      return fmt;
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   protected void parseXML(Element node) throws Exception {
      faceID = Tool.getAttribute(node, "id");
      double dwidth = Double.parseDouble(Tool.getAttribute(node, "width"));
      double dheight = Double.parseDouble(Tool.getAttribute(node, "height"));
      defaultSize = new Dimension((int) dwidth, (int) dheight);

      // get all image
      Element imagenode = Tool.getChildNodeByTagName(node, "image");
      Element panelNode = Tool.getChildNodeByTagName(imagenode, "panel");
      panelPath = Tool.getValue(panelNode);
      cylinderHeight = Double.parseDouble(Tool.getAttribute(panelNode, "height"));

      Element canvasNode = Tool.getChildNodeByTagName(imagenode, "canvas");
      canvasPath = Tool.getValue(canvasNode);

      cylinderWidth = Double.parseDouble(Tool.getAttribute(panelNode, "width"));
      double x = Double.parseDouble(Tool.getAttribute(panelNode, "x"));
      double y = Double.parseDouble(Tool.getAttribute(panelNode, "y"));
      startPoint = new Point2D.Double(x, y);
      Element maskNode = Tool.getChildNodeByTagName(imagenode, "mask");
      valueMaskPath = Tool.getValue(maskNode);
      maskHeight =
         Double.parseDouble(Tool.getAttribute(maskNode, "maskHeight"));
      Element ellipseNode = Tool.getChildNodeByTagName(imagenode, "ellipse");
      valueEllipsePath = Tool.getValue(ellipseNode);
      ellipseHeight =
         Double.parseDouble(Tool.getAttribute(ellipseNode, "ellipseHeight"));

      if(faceID.charAt(faceID.length() - 1) == '5') {
         Element shadowNode = Tool.getChildNodeByTagName(imagenode, "shadow");
         shadowPath = Tool.getValue(shadowNode);
      }

      Element mtNode = Tool.getChildNodeByTagName(node, "majorTick");
      majorTickX = Double.parseDouble(Tool.getAttribute(mtNode, "startX"));
      majorTickStartY = Double.parseDouble(Tool.getAttribute(mtNode, "startY"));
      majorTickEndY = Double.parseDouble(Tool.getAttribute(mtNode, "endY"));
      majorTickWidth =
         Double.parseDouble(Tool.getAttribute(mtNode, "lineWidth"));
      majorTickLength = Double.parseDouble(Tool.getAttribute(mtNode, "length"));
      majorTickColor = parseColor(Tool.getAttribute(mtNode, "color"));

      Element miNode = Tool.getChildNodeByTagName(node, "minorTick");
      minorTickLength = Double.parseDouble(Tool.getAttribute(miNode, "length"));
      minorTickWidth =
         Double.parseDouble(Tool.getAttribute(miNode, "lineWidth"));
      minorTickColor = parseColor(Tool.getAttribute(miNode, "color"));

      Element rangeNode = Tool.getChildNodeByTagName(node, "range");
      rangeStartX = Double.parseDouble(Tool.getAttribute(rangeNode, "startX"));
      rangeWidth = Double.parseDouble(Tool.getAttribute(rangeNode, "width"));
      rangeColor = parseColor(Tool.getAttribute(rangeNode, "color"));

      Element vNode = Tool.getChildNodeByTagName(node, "value");
      valueStartX = Double.parseDouble(Tool.getAttribute(vNode, "startX"));

      super.parseXML(node);
   }

   /**
    * Get the image from the URI name.
    * @param uri the file's URI.
    * @param transform the affine transform of the image.
    * @return the image.
    */
   protected BufferedImage getImageByURI(String uri, AffineTransform transform)
      throws Exception
   {
      if(transform == null) {
         transform = AffineTransform.getScaleInstance(1, 1);
      }

      SVGTransformer transformer = (SVGTransformer) getResourceCache().get(uri);

      synchronized(transformer) {
         transformer.setSize(contentSize);
         transformer.setTransform(transform);
         return transformer.getImage();
      }
   }

   /**
    * Get the resource cache.
    * @return the resource cache.
    */
   public static ResourceCache getResourceCache() {
      return ResourceCache2.getResourceCache();
   }

   /**
    * Set the scale of the image.
    */
   protected void setScale(double scaleX, double scaleY) {
      this.scaleX = scaleX;
      this.scaleY = scaleY;

      cylinderHeight = cylinderHeight * scaleY;
      cylinderWidth = cylinderWidth * scaleX;
      bottomMarginWidth = bottomMarginWidth * scaleY;
      rightMarginWidth = rightMarginWidth  * scaleX;
      contentSize = new Dimension(
         (int) (defaultSize.width * scaleX),
         (int) (defaultSize.height * scaleY));

      majorTickX = majorTickX * scaleX;
      majorTickStartY = majorTickStartY * scaleY;
      majorTickEndY = majorTickEndY * scaleY;
      rangeStartX = rangeStartX * scaleX;
      startPoint = new Point2D.Double(startPoint.getX() * scaleX,
         startPoint.getY() * scaleY);
      rangeWidth = (double) (rangeWidth * (float) scaleX);
      valueStartX = valueStartX * scaleX;
      ellipseHeight = ellipseHeight * scaleY;
      majorTickLength = majorTickLength * scaleX;
      majorTickWidth = majorTickWidth * scaleX;
      minorTickLength = minorTickLength * scaleX;
      gap = gap * scaleY;
   }

   /**
    * Get the backgroud image with panel.
    */
   private BufferedImage createPanelImage() {
      BufferedImage image = null;

      try {
         AffineTransform transform =
            AffineTransform.getScaleInstance(scaleX, scaleY);
         image = getImageByURI(panelPath, transform);
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
      }

      return image;
   }

   /**
    * Draw the cylinder.
    */
   protected void drawCylinder(Graphics2D g) {
      drawCanvas(g);

      CylinderVSAssemblyInfo info = getCylinderAssemblyInfo();
      double value = getValue();

      if(value > info.getMax()) {
         value = info.getMax();
      }
      else if(value < info.getMin()) {
         value = info.getMin();
      }

      double rate = (value - info.getMin()) / (info.getMax() - info.getMin());
      double maskDrawingHeight = rate * cylinderHeight;
      double sx = scaleX;
      double sy = maskDrawingHeight / maskHeight;
      AffineTransform scaleT = AffineTransform.getScaleInstance(sx, sy);
      BufferedImage mask = null;
      BufferedImage ellipse = null;

      try {
         if(scaleT.getScaleY() != 0) {
            mask = getImageByURI(valueMaskPath, scaleT);
         }

         ellipse = getImageByURI(valueEllipsePath, getScale());
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
         return;
      }

      int y = (int) (cylinderHeight - maskDrawingHeight + (ellipseHeight / 2));

      // draw the mask
      if(mask != null) {
         g.drawImage(mask, 0, y, null);
      }

      // If water surface is higher, ellipse should be painted first, otherwise
      // ellipse will overlap the top of the panel. And If water surface is
      // lower, panel should be painted first, otherwise panel will overlap the
      // ellipse.
      if(rate >= 0.2) {
         // draw the ellipse and the panel
         g.drawImage(ellipse, 0, (int) (y - ellipseHeight / 2), null);
         drawPanel(g);
      }
      else {
         // draw the panel and the ellipse
         drawPanel(g);
         g.drawImage(ellipse, 0, (int) (y - ellipseHeight / 2), null);
      }
   }

   /**
    * Draw canvas.
    */
   protected void drawCanvas(Graphics2D g) {
      BufferedImage canvas = null;

      try {
         canvas = getImageByURI(canvasPath, getScale());
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
         return;
      }

      g.drawImage(canvas, 0, 0, null);
   }

   /**
    * Draw the cylinder itself.
    */
   protected void drawPanel(Graphics2D g) {
      BufferedImage panel = null;

      try {
         panel = getImageByURI(panelPath, getScale());
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
         return;
      }

      // draw the panel
      g.drawImage(panel, 0, 0, null);
   }

   /**
    * Draw the ticks and the values.
    */
   protected void drawTicksAndValues(Graphics2D g) {
      CylinderVSAssemblyInfo info = getCylinderAssemblyInfo();
      double majorInc = info.getMajorInc();
      double minorInc = info.getMinorInc();
      double dMinValue = info.getMin();
      double dMaxValue = info.getMax();
      int majorTickCount = (int) Math.ceil(
         ((dMaxValue - dMinValue) / majorInc)) + 1;

      if(majorTickCount >= MAX_MAJOR_TICK_COUNT * 2) {
         LOG.info("Too many major ticks found in cylinder: " +
            majorTickCount);
         int inc = (majorTickCount / MAX_MAJOR_TICK_COUNT);
         majorInc *= inc;
         minorInc *= inc;
         majorTickCount =
            (int) Math.ceil(((dMaxValue - dMinValue) / majorInc)) + 1;
      }

      int minorTickBetweenMajorTicks = (minorInc == 0) ? 0 :
         (int) Math.ceil(majorInc / minorInc) - 1;
      double[] values = new double[majorTickCount];
      String[] labels = new String[majorTickCount];

      // calculate the values should be displayed
      for(int i = 0; i < majorTickCount; i++) {
         double val = dMinValue + majorInc * i;
         values[i] = val;
         labels[i] = formatValue(val, false);
      }

      values[majorTickCount - 1] = dMaxValue;
      labels[majorTickCount - 1] = formatValue(dMaxValue, false);
      labels = abbreviate(labels);

      // draw the ticks and the values
      int startx = (int) majorTickX;
      double stringStartX = valueStartX;
      double starty = majorTickStartY;
      double endy = majorTickEndY;
      double majorDelta = (endy - starty) * majorInc / (dMaxValue - dMinValue);
      double minorDelta = (minorInc == 0) ? 0 :
         (endy - starty) * minorInc / (dMaxValue - dMinValue);
      boolean[] flags = new boolean[values.length];

      flags = resetFlags(flags, 0);
      Font font = adjustValuesFont(g, getValuesFont(),
         Math.abs(majorDelta + 2 * scaleY), labels);
      g.setFont(font);
      g.setColor(majorTickColor);

      Line2D line = new Line2D.Double();
      line.setLine(startx, starty, startx, endy);
      g.draw(line);

      double begin = endy;
      FontMetrics fm = g.getFontMetrics();
      double fontHeight = fm.getHeight() - fm.getDescent();
      double valueMaxLength = contentSize.getWidth() - stringStartX -
         rightMarginWidth;

      labels = getDisplayedValues(valueMaxLength, labels, fm);

      // calculate the display flags
      for(int i = 0; i < 100 && isValuesOverlapped(fontHeight, majorDelta, flags); i++) {
         flags = resetFlags(flags, getJump(flags) + 1);
      }

      checkMaxOverlapped(g, fontHeight, flags, values);

      if(majorInc < minorInc || (dMaxValue - dMinValue) < majorInc) {
         drawTick(g, startx, endy, majorTickLength, majorTickWidth,
            majorTickColor);
         drawTick(g, startx, starty, majorTickLength, majorTickWidth,
            majorTickColor);
         Common.drawString(g, labels[0], (float) stringStartX,
               (float) (endy + fontHeight / 2));
         Common.drawString(g, labels[labels.length - 1], (float) stringStartX,
               (float) (starty + fontHeight / 2));

         return;
      }

      for(int i = 0; i < majorTickCount; i++) {
         drawTick(g, startx, begin, majorTickLength, majorTickWidth,
            majorTickColor);
         g.setColor(getValuesColor());

         // draw values
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

            drawTick(g, startx, begin - minorDelta * (j + 1),
               minorTickLength, (int) minorTickWidth, minorTickColor);
         }

         if(i < majorTickCount - 2) {
            begin -= majorDelta;
         }
         else {
            begin = starty;
         }
      }
   }

   /**
    * Check if the values overlap each other.
    */
   private boolean isValuesOverlapped(double fontHeigth, double delta, boolean[] flags) {
      int jump = getJump(flags);
      jump++;


      if(fontHeigth + gap <= Math.abs(delta) * jump) {
         return false;
      }

      return true;
   }

   /**
    * Check if the max is overlapped.
    */
   private void checkMaxOverlapped(Graphics2D g, double fontHeight,
      boolean[] flags, double[] values)
   {
      if(flags.length < 2) {
         return;
      }

      int valueBeforeMaxIndex = flags.length - 2;

      for(; !flags[valueBeforeMaxIndex]; valueBeforeMaxIndex--) {
         if(valueBeforeMaxIndex == 0) {
            return;
         }
      }

      CylinderVSAssemblyInfo info = getCylinderAssemblyInfo();
      double min = info.getMin();
      double max = info.getMax();
      double valueBeforeMax = values[valueBeforeMaxIndex];
      double height = cylinderHeight * (max - valueBeforeMax) / (max - min);

      if(height < fontHeight + gap) {
         flags[valueBeforeMaxIndex] = false;
      }
   }

   /**
    * Reset the flags.
    */
   private boolean[] resetFlags(boolean[] flags, int jump) {
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
    * Get displayed Values.
    */
   private String[] getDisplayedValues(double maxLength, String[] values,
      FontMetrics fm)
   {
      double stringWidth;
      String dotsString = "..";
      double dotsStringWidth = fm.stringWidth(dotsString);

      for(int i = 0; i < values.length; i++) {
         stringWidth = fm.stringWidth(values[i]);

         if(stringWidth < maxLength) {
            continue;
         }

         while(stringWidth > maxLength - dotsStringWidth) {
            if(values[i].length() < 3) {
               break;
            }

            values[i] = values[i].substring(0, values[i].length() - 3);
            stringWidth = fm.stringWidth(values[i]);
         }

         values[i] = values[i] + dotsString;
      }

      return values;
   }

   /**
    * Fill the ranges
    * @param g the graphics.
    */
   private void fillRanges(Graphics2D g) {
      CylinderVSAssemblyInfo info = (CylinderVSAssemblyInfo) getAssemblyInfo();
      double[] ranges = info.getRanges();
      Color[] colors = info.getRangeColors();

      if(ranges == null || colors == null || ranges.length == 0 ||
         ranges.length > colors.length)
      {
         return;
      }

      double min = info.getMin();
      double max = info.getMax();
      int rangeHeight = (int) (majorTickEndY - majorTickStartY);
      int startx = (int) rangeStartX;
      int starty = (int) majorTickEndY;

      for(int i = ranges.length - 1; i >= 0; i--) {
         double range = ranges[i];
         range = range < min ? min : range;
         range = range > max ? max : range;
         Color c1 = colors[i];
         Color c2 = (i < colors.length - 1) ? colors[i + 1] : null;

         if(c2 == null && c1 != null) {
            c2 = c1.darker();
         }

         // set the range color same with previous one when the color is null,
         if(c1 == null) {
            if(i < ranges.length - 1 && c2 != null) {
               c1 = c2;
               colors[i] = c2;
            }
            else {
               continue;
            }
         }

         double hrate = (range - (i > 0 ? ranges[i - 1] : min)) / (max - min);
         double yrate = (range - min) / (max - min);
         int height = (int) (hrate * rangeHeight);
         int y = (int) (majorTickEndY - yrate * rangeHeight);

         // get the y-axis value of next range
         double nextRange = i > 0 ? ranges[i - 1] : min;
         nextRange = nextRange < min ? min : nextRange;
         nextRange = nextRange > max ? max : nextRange;
         double nextYrate = (nextRange - min) / (max - min);
         int nextY = (int) (majorTickEndY - nextYrate * rangeHeight);

         if(info.isRangeGradient()) {
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
         Polygon trapepzia = new Polygon();
         trapepzia.addPoint(p1.x, p1.y);
         trapepzia.addPoint(p2.x, p2.y);
         trapepzia.addPoint(p3.x, p3.y);
         trapepzia.addPoint(p4.x, p4.y);
         g.fill(trapepzia);
      }
   }

   /**
    * Draw one tick.
    * @param g The graphics object to draw.
    * @param x The begin point's x.
    * @param y The begin point's y.
    * @param lineLength The tick's length.
    * @param lineWidth The tick's line width.
    * @param color The tick's color.
    */
   protected void drawTick(Graphics2D g, double x, double y,
                           double lineLength, double lineWidth, Color color) {
      g.setStroke(new BasicStroke((float) lineWidth, BasicStroke.CAP_SQUARE,
         BasicStroke.JOIN_MITER));
      Point2D begin = new Point2D.Double(x + lineWidth, y);
      Point2D end = new Point2D.Double(x + lineLength, y);
      g.setColor(color);
      Line2D line = new Line2D.Double();
      line.setLine(begin, end);
      g.draw(line);
   }

   /**
    * Set the rendering strategy.
    */
   protected void setRenderingStrategy(Graphics2D g) {
      // set rendering hints
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
    * Adjust the font to avoid the ticks values out of the image.
    */
   protected Font adjustValuesFont(Graphics2D g, Font font, double heightDelta,
                                   String[] values) {
      g.setFont(font);

      double x = valueStartX;
      double dis = contentSize.getWidth() - x - rightMarginWidth;
      String s = values[getMaxStringLength(values, g)];
      Font result = font;
      FontMetrics fm = g.getFontMetrics();

      while(fm.stringWidth(s) > dis ||
         fm.getHeight() - fm.getDescent() >= heightDelta)
      {
         result = reduceSize(result);

         if(result.getSize() <= MINIMAL_FONT_SIZE) {
            break;
         }

         g.setFont(result);
         fm = g.getFontMetrics();
      }

      return result;
   }

   /**
    * Get the max string length.
    */
   protected int getMaxStringLength(String[] values, Graphics2D g) {
      double length = 0;
      int position = 0;
      FontMetrics fm = g.getFontMetrics();

      for(int i = 0; i < values.length; i++) {
         double tempLength = fm.stringWidth(values[i]);

         if(tempLength > length) {
            length = tempLength;
            position = i;
         }
      }

      return position;
   }

   /**
    * Get the values' font.
    */
   protected Font getValuesFont() {
      return getFont();
   }

   /**
    * Get the VSCylinder's info.
    */
   protected CylinderVSAssemblyInfo getCylinderAssemblyInfo() {
      return (CylinderVSAssemblyInfo) getAssemblyInfo();
   }

   /**
    * Get the current value.
    */
   protected double getValue() {
      return getCylinderAssemblyInfo().getDoubleValue();
   }

   /**
    * Get the values' font color.
    */
   protected Color getValuesColor() {
      return getForeground();
   }

   /**
    * Get the current scale of the image.
    */
   protected AffineTransform getScale() {
      return AffineTransform.getScaleInstance(scaleX, scaleY);
   }

   /**
    * Cylinder resource cache.
    */
   public static class ResourceCache2 extends PersistentResourceCache {
      /**
       * Get the resource cache.
       */
      public static ResourceCache getResourceCache() {
         return SingletonManager.getInstance(ResourceCache2.class);
      }

      /**
       * Create a resource cache.
       */
      public ResourceCache2() {
         super("cylinder", 15, 604800000L); // one week timeout
      }

      /**
       * Create a resource.
       */
      @Override
      protected Object create(Object key) throws Exception {
         String uri = (String) key;
         URL url = VSCylinder.class.getResource(uri);
         return SVGSupport.getInstance().createSVGTransformer(url);
      }
   }

   protected static final int THEME_MASK = 0xFF;
   protected static final int COLOR_MASK = 0xFF00;
   protected static final Font DEFAULT_FONT =
      new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, Font.PLAIN, 12);
   protected static final int MINIMAL_FONT_SIZE = 8;
   protected final int DEFAULT_HEIGHT = 200;

   protected double rangeStartX;
   protected double rangeWidth;
   protected Color rangeColor;
   protected Dimension contentSize = defaultSize;

   protected double minorTickWidth;
   protected Color minorTickColor;
   protected double minorTickLength;

   protected Color majorTickColor;
   protected double majorTickLength;
   protected double majorTickWidth;
   protected double majorTickEndY;
   protected double majorTickStartY;
   protected double majorTickX;

   protected String faceID;
   protected double scaleX;
   protected double scaleY;
   protected Point2D startPoint;
   protected double valueStartX;

   protected String panelPath;
   protected String valueMaskPath;
   protected String valueEllipsePath;
   protected String canvasPath;
   protected String shadowPath;

   protected double cylinderHeight;
   protected double cylinderWidth;
   protected double maskHeight;
   protected double ellipseHeight;
   protected double bottomMarginWidth = 3;
   protected double rightMarginWidth = 3;
   protected double gap = 2;

   private static Map fmap = null;
   private boolean isDrawbg = true;
   private static final Logger LOG =
      LoggerFactory.getLogger(VSCylinder.class);
}
