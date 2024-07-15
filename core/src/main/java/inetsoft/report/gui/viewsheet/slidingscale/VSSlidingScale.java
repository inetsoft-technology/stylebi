/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.gui.viewsheet.slidingscale;

import inetsoft.report.StyleFont;
import inetsoft.report.gui.viewsheet.VSFaceUtil;
import inetsoft.report.gui.viewsheet.VSImageable;
import inetsoft.report.internal.Common;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.SlidingScaleVSAssemblyInfo;
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
 * VSSlidingScale component for view sheet.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class VSSlidingScale extends VSImageable implements Cloneable {
   /**
    * Get a slidingscale instance.
    * @param id The VSSlidingScale's id.
    * @return if the ID does not exist, the method will return null.
    */
   public static synchronized VSSlidingScale getSlidingScale(int id) {
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
      VSSlidingScale slidingScale = (VSSlidingScale) fmap.get(ID);

      if(slidingScale == null) {
         return null;
      }

      return (VSSlidingScale) slidingScale.clone();
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
    * Reset the SlidingScale's configuration.
    */
   public static synchronized void reset() {
      fmap = null;
   }

   /**
    * Constructor.
    */
   protected VSSlidingScale() {
      super(null);
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      try {
         VSSlidingScale sliding = (VSSlidingScale) super.clone();
         sliding.contentSize = contentSize == null ?
            null : (Dimension) contentSize.clone();
         return sliding;
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
    * Get the image of the slidingScale.
    */
   @Override
   public BufferedImage getContentImage() {
      adjust();

      BufferedImage image = new BufferedImage(contentSize.width,
         contentSize.height, BufferedImage.TYPE_4BYTE_ABGR);
      Image img = createPanelImage();
      Graphics2D g = (Graphics2D) img.getGraphics();
      setRenderingStrategy(g);

      SlidingScaleVSAssemblyInfo info = getInfo();

      if(info.getMax() > info.getMin()) {
         fillRanges(g);
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
    * Initialize the slidingscale map.
    */
   private static void initMap() throws Exception  {
      fmap = new HashMap();
      Document document = Tool.parseXML(
         VSSlidingScale.class.getResourceAsStream("slidingscale.xml"), "UTF-8");
      Element slidingScaleNode = Tool.getFirstElement(document);
      NodeList list = Tool.getChildNodesByTagName(slidingScaleNode, "face");

      for(int i = 0; i < list.getLength(); i++) {
         Element node = Tool.getNthChildNode(slidingScaleNode, i);
         String id = Tool.getAttribute(node, "id");
         VSSlidingScale slidingScale =
            (VSSlidingScale) VSSlidingScale.class.newInstance();
         slidingScale.parseXML(node);
         fmap.put(id, slidingScale);
      }
   }

   /**
    * Get default format.
    */
   public static VSCompositeFormat getDefaultFormat(int faceId) {
      VSCompositeFormat fmt = new VSCompositeFormat();
      VSSlidingScale slidingscale = getSlidingScale(faceId);
      Color valueColor = slidingscale.getValueColor();
      StyleFont valueFont = slidingscale.getValueFont();
      fmt.getDefaultFormat().setForegroundValue(
         String.valueOf(valueColor.getRGB()));
      fmt.getDefaultFormat().setFontValue(valueFont);

      return fmt;
   }

   /**
    * Method to parse an xml segment.
    * @param node the XML representation of this object.
    */
   @Override
   protected void parseXML(Element node) throws Exception {
      // parse face tag
      int id = Integer.parseInt(Tool.getAttribute(node, "id"));
      faceID = id;
      int width = Integer.parseInt(Tool.getAttribute(node, "width"));
      int height = Integer.parseInt(Tool.getAttribute(node, "height"));
      defaultSize = new Dimension((int) width, (int) height);
      contentSize = defaultSize;

      // parse image tag
      Element imageNode = Tool.getChildNodeByTagName(node, "image");
      panelPath = Tool.getChildValueByTagName(imageNode, "panel");
      needlePath = Tool.getChildValueByTagName(imageNode, "needle");

      // parse tickLabel tag
      Element tickLabelNode = Tool.getChildNodeByTagName(node, "tickLabel");
      tickLabelWidth = Double.parseDouble(
         Tool.getAttribute(tickLabelNode, "width"));
      tickLabelHeight = Double.parseDouble(
         Tool.getAttribute(tickLabelNode, "height"));
      double tickLabelX = Double.parseDouble(
         Tool.getAttribute(tickLabelNode, "x"));
      double tickLabelY = Double.parseDouble(
         Tool.getAttribute(tickLabelNode, "y"));
      labelPosition = new Point2D.Double(tickLabelX, tickLabelY);

      // parse line tag
      Element lineNode = Tool.getChildNodeByTagName(node, "line");
      double lineY = Double.parseDouble(
         Tool.getAttribute(lineNode, "y"));
      lineHeight = Double.parseDouble(
         Tool.getAttribute(lineNode, "height"));

      // parse majorTick tag
      Element btNode = Tool.getChildNodeByTagName(node, "majorTick");
      majorTickColor = parseColor(Tool.getAttribute(btNode,"color"));

      if(majorTickColor == null) {
         majorTickColor = Color.BLACK;
      }

      majorTickWidth = Integer.parseInt(Tool.getAttribute(btNode, "width"));
      majorTickLength = Integer.parseInt(Tool.getAttribute(btNode, "length"));
      tickMargin = Integer.parseInt(Tool.getAttribute(btNode, "margin"));
      linePosition = new Point2D.Double(tickLabelX + tickMargin, lineY);
      lineWidth = tickLabelWidth - 2 * tickMargin;

      // parse minorTick tag
      Element ltNode = Tool.getChildNodeByTagName(node, "minorTick");
      minorTickLength = Double.parseDouble(
         Tool.getAttribute(ltNode, "length"));
      minorTickWidth = Integer.parseInt(Tool.getAttribute(ltNode, "width"));
      minorTickColor = parseColor(Tool.getAttribute(ltNode, "color"));

      if(minorTickColor == null) {
         minorTickColor = Color.BLACK;
      }

      // parse value tag
      Element valueNode = Tool.getChildNodeByTagName(node, "value");
      distance = Integer.parseInt(Tool.getAttribute(valueNode, "distance"));

      super.parseXML(node);
   }

   /**
    * Get the image from the URI name.
    * @param uri the file's URI.
    * @param transform the affine transform of the image.
    * @return the image.
    */
   private BufferedImage getImageByURI(String uri, AffineTransform transform)
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
    * Get the current scale of the image.
    */
   private AffineTransform getScale() {
      return AffineTransform.getScaleInstance(scaleX, scaleY);
   }

   /**
    * Set the scale of the image.
    * @param scaleX the Width's scale.
    * @param scaleY the heigth's scale.
    */
   private void setScale(double scaleX, double scaleY) {
      this.scaleX = scaleX;
      this.scaleY = scaleY;

      linePosition = new Point2D.Double(linePosition.getX() * scaleX,
         linePosition.getY() * scaleY);
      lineWidth = lineWidth * scaleX;
      lineHeight = lineHeight * scaleY;
      labelPosition = new Point2D.Double(labelPosition.getX() * scaleX,
         labelPosition.getY() * scaleY);
      majorTickLength = majorTickLength  * scaleY;
      majorTickWidth = (int) (majorTickWidth * scaleX);
      minorTickLength = minorTickLength * scaleY;
      minorTickWidth = (int) (minorTickWidth * scaleX);
      tickLabelWidth = tickLabelWidth * scaleX;
      tickLabelHeight = tickLabelHeight * scaleY;
      contentSize = new Dimension((int) (defaultSize.width * scaleX),
         (int) (defaultSize.height * scaleY));
      tickMargin = (int) (tickMargin * scaleX);
      startPoint = new Point((int) (startPoint.getX() * scaleX),
         (int) (startPoint.getY() * scaleY));
      distance = (int) (distance * scaleY);
      valueMargin = (int) (valueMargin * scaleX);
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
    * Draw the slidingScale panel.
    * @param g the graphics.
    */
   private void drawPanel(Graphics2D g) {
      BufferedImage panelImage = null;

      try {
         panelImage = getImageByURI(panelPath, getScale());
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
      }

      g.drawImage(panelImage, (int) startPoint.getX(),
         (int) startPoint.getY(), null);
   }

   /**
    * Draw the slidingScale needle.
    * @param g the graphics.
    */
   private void drawNeedle(Graphics g) {
      BufferedImage needleImage = null;

      try {
         needleImage = getImageByURI(needlePath, getScale());
      }
      catch(Exception e) {
         LOG.error(e.getMessage(), e);
      }

      SlidingScaleVSAssemblyInfo info = getInfo();

      double currentValue = getValue();

      if(currentValue > info.getMax()) {
         currentValue = info.getMax();
      }
      else if(currentValue < info.getMin()) {
         currentValue = info.getMin();
      }

      int needleX = (int) (startPoint.getX() + tickMargin +
         (tickLabelWidth - tickMargin * 2) * (currentValue - info.getMin()) /
         (info.getMax() - info.getMin()));
      int needleY = (int) startPoint.getY() ;
      g.drawImage(needleImage, needleX, needleY, null);
   }

   /**
    * Fill the ranges
    * @param g the graphics.
    */
   private void fillRanges(Graphics2D g) {
      SlidingScaleVSAssemblyInfo info = getInfo();
      double[] ranges = info.getRanges();
      Color[] colors = info.getRangeColors();

      if(ranges == null || colors == null || ranges.length == 0 ||
         ranges.length > colors.length)
      {
         return;
      }

      double min = info.getMin();
      double max = info.getMax();
      double last = min;

      for(int i = 0; i < ranges.length; i++) {
         double range = ranges[i];
         range = range < min ? min : range;
         range = range > max ? max : range;

         if(range <= last) {
            continue;
         }

         if(colors[i] != null) {
            Color c1 = colors[i];
            Color c2 = (i < colors.length - 1) ? colors[i + 1] : c1.darker();
            double wrate = (range - last) / (max - min);
            double xrate = (last - min) / (max - min);
            int width = (int) (wrate * lineWidth) + 2;
            int x = (int) (linePosition.getX() + xrate * lineWidth);

            if(info.isRangeGradient()) {
               if(c2 == null) {
                  if(c1 != null) {
                     c2 = c1.darker();
                  }
                  else {
                     continue;
                  }
               }

               GradientPaint gra = new GradientPaint(x, 0, c1, x + width, 0,
                                                     c2);
               g.setPaint(gra);
            }
            else {
               g.setColor(colors[i]);
            }

            g.fillRect(x, (int) linePosition.getY() - 1, width,
                       (int) (lineHeight + 2));
         }

         last = range;
      }
   }

   /**
    * Draw the ticks and the values.
    * @param g the graphics.
    */
   private void drawTicksAndValues(Graphics2D g) {
      SlidingScaleVSAssemblyInfo info = getInfo();
      double majorInc = info.getMajorInc();
      double minorInc = info.getMinorInc();
      double dMinValue = info.getMin();
      double dMaxValue = info.getMax();

      double tickStartX = linePosition.getX();
      double tickEndX = linePosition.getX() + lineWidth;
      double majorTickStartY = labelPosition.getY() + (tickLabelHeight -
         majorTickLength) / 2;
      double minorTickStartY = labelPosition.getY() + (tickLabelHeight -
         minorTickLength) / 2;
      int majorTickCount = (int) Math.ceil(
         ((dMaxValue - dMinValue) / majorInc)) + 1;

      if(majorTickCount >= MAX_MAJOR_TICK_COUNT * 2) {
         LOG.info("Too many major ticks found in slinding scale: " +
            majorTickCount);
         int inc = (majorTickCount / MAX_MAJOR_TICK_COUNT);
         majorInc *= inc;
         minorInc *= inc;
         majorTickCount =
            (int) Math.ceil(((dMaxValue - dMinValue) / majorInc)) + 1;
      }

      int minorTickBetweenMajorTicks = (minorInc == 0) ? 0 :
         (int) Math.ceil(majorInc / minorInc) - 1;
      double minorDelta = (minorInc == 0) ? 0 :
         (tickLabelWidth - 2 * tickMargin) * minorInc / (dMaxValue - dMinValue);
      double majorDelta =
         (tickLabelWidth - 2 * tickMargin) * majorInc / (dMaxValue - dMinValue);

      String[] labels = new String[majorTickCount];
      double[] values = new double[majorTickCount];

      // calculate the values should be displayed
      for(int i = 0; i < majorTickCount; i++) {
         values[i] = dMinValue + i * majorInc;
         labels[i] = formatValue(values[i], false);
      }

      values[majorTickCount - 1] = dMaxValue;
      labels[majorTickCount - 1] = formatValue(dMaxValue, false);
      labels = abbreviate(labels);

      boolean[] flags = new boolean[labels.length];
      flags = resetFlags(flags, 0);
      Font font = adjustValuesFont(g, tickStartX, tickEndX, labels, flags);

      // calculate the display flags
      while(isValuesOverlapped(g, tickStartX, majorDelta, font, labels, flags))
      {
         flags = resetFlags(flags, getJump(flags) + 1);
      }

      checkMaxOverlapped(g, majorDelta, font, labels, flags);

      g.setFont(font);

      FontMetrics fm = g.getFontMetrics();
      float valueStartY = (float) (labelPosition.getY() + tickLabelHeight +
         distance + fm.getAscent());

      if(majorInc < minorInc || (dMaxValue - dMinValue) < majorInc) {
         drawTick(g, tickStartX , majorTickStartY, majorTickLength,
            majorTickWidth, majorTickColor);
         drawTick(g, tickEndX , majorTickStartY, majorTickLength,
            majorTickWidth, majorTickColor);
         Common.drawString(g, labels[0],
            (float) tickStartX - fm.stringWidth(labels[0]) / 2, valueStartY);
         Common.drawString(g, labels[labels.length - 1],
            (float) tickEndX - fm.stringWidth(labels[labels.length - 1]) / 2,
            valueStartY);

         return;
      }

      for(int i = 0; i < majorTickCount; i++) {
         // draw major tick
         drawTick(g, tickStartX , majorTickStartY, majorTickLength,
            majorTickWidth, majorTickColor);

         // draw value
         if(flags[i]) {
            float valueStartX =
               (float) tickStartX - fm.stringWidth(labels[i]) / 2;

            g.setColor(getForeground());
            Common.drawString(g, labels[i], valueStartX, valueStartY);
         }

         // draw minor ticks
         for(int j = 0; j < minorTickBetweenMajorTicks; j++) {
            double minorTickStartX = tickStartX + minorDelta * (j + 1);

            if(minorTickStartX < tickEndX) {
               drawTick(g, minorTickStartX, minorTickStartY,
               minorTickLength, minorTickWidth, minorTickColor);
            }
         }

         if(i != majorTickCount - 2) {
            tickStartX = tickStartX + majorDelta;
         }
         else {
            tickStartX = tickEndX;
         }
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
   private void drawTick(Graphics2D g, double x, double y,
                         double lineLength, double lineWidth, Color color) {
      g.setStroke(new BasicStroke((float) lineWidth, BasicStroke.CAP_ROUND,
         BasicStroke.JOIN_MITER));
      Point2D begin = new Point2D.Double(x, y);
      Point2D end = new Point2D.Double(x, y + lineLength);
      Line2D line = new Line2D.Double();

      g.setColor(color);
      line.setLine(begin, end);
      g.draw(line);
   }

   /**
    * Set the redering strategy. The default is just set anti allasing.
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
    * Get the VSSlidingScale's info.
    */
   private SlidingScaleVSAssemblyInfo getInfo() {
      return (SlidingScaleVSAssemblyInfo) getAssemblyInfo();
   }

   /**
    * Reset the flags.
    * @param flags the boolean array to be reset.
    * @param delta the delta of the array.
    */
   private boolean[] resetFlags(boolean[] flags, int delta) {
      for(int i = 0; i < flags.length; i++) {
         if(i % (delta + 1) == 0) {
            flags[i] = true;
         }
         else {
            flags[i] = false;
         }
      }

      return flags;
   }

   /**
    * Get the delta of the display flag.
    * @param flags the boolean array.
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
    * Get the value of this sliding scale.
    */
   public double getValue() {
      return getInfo().getDoubleValue();
   }

   /**
    * Calculate the length of string.
    * @param g the graphics.
    * @param contents the display string.
    * @param font the display font.
    * @param flags use null to calculate all the length of the string,
    * also define which string will be added.
    */
   private double calculateStringLength(Graphics2D g, String[] contents,
                                        Font font, boolean[] flags) {
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
    * Adjust the font so that the value string will not out of the image.
    * @param g the graphics.
    * @param startx the start of the x-axis.
    * @param endx the end of the x-axis.
    * @param values the string.
    * @param flags the array determine whether the value should be displayed.
    */
   private Font adjustValuesFont(Graphics2D g, double startx, double endx,
                                 String[] values, boolean[] flags)
   {
      Font result = getFont();
      double dis = endx - startx;

      while(calculateStringLength(g, values, result, flags) > dis) {
         if(result.getSize() <= MIN_FONT_SIZE) {
            break;
         }

         result = reduceSize(result);
      }

      int gap = 2;

      while(result.getSize() > MIN_FONT_SIZE) {
         // reduce gap so the font can be bigger
         if(result.getSize() < 10) {
            gap = 0;
         }

         int fontH = Common.getFontMetrics(result).getHeight();
         int bottom = (int) (labelPosition.getY() + tickLabelHeight +
                             distance + fontH);

         if(bottom >= getContentHeight() - gap) {
            if(distance > gap) {
               distance -= bottom - getContentHeight() + 3;

               if(distance < gap) {
                  distance = gap;
               }
            }
            else {
               result = reduceSize(result);
            }
         }
         else {
            break;
         }
      }

      return result;
   }

   /**
    * Check if the values overlap each other.
    * @param g the graphics.
    * @param begin the start of x-axis.
    * @param delta the delta between values.
    * @param font the font of the string.
    * @param contents the string.
    * @param flags the array determine whether the value should be displayed.
    */
   private boolean isValuesOverlapped(Graphics2D g, double begin,
      double delta, Font font, String[] contents, boolean[] flags)
   {
      int jump = getJump(flags) + 1;

      g.setFont(font);

      for(int i = 0; i < contents.length; i += jump) {
         if((i + jump) < contents.length) {
            double px = getStringEndX(g, begin + delta * i, contents[i]);
            double cx = getStringStartX(g, begin + delta * (i + jump),
                                        contents[i + jump]);

            if(px + valueMargin > cx) {
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
      if(contents.length < 2 || !flags[flags.length - 1]) {
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
      double cx = getStringStartX(g, tickLabelWidth - 2 * tickMargin,
         contents[maxIdx]);

      if(px + valueMargin > cx) {
         flags[prevIdx] = false;
      }
   }

   /**
    * Calculate the value string start x position.
    * @param g the graphics.
    * @param tickX the x-axis of tick.
    * @param value the string to be displayed.
    */
   private double getStringStartX(Graphics2D g, double tickX, String value) {
      FontMetrics fm = g.getFontMetrics();
      double width = fm.stringWidth(value);
      return tickX - width / 2;
   }

   /**
    * Calculate the value string end x position.
    * @param g the graphics.
    * @param tickX the x-axis of tick.
    * @param tickX the string to be displayed.
    */
   private double getStringEndX(Graphics2D g, double tickX, String value) {
      FontMetrics fm = g.getFontMetrics();
      double width = fm.stringWidth(value);
      return tickX + width / 2;
   }

   /**
    * Sliding scale resource cache.
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
         super("slidingscale", 15, 604800000L); // one week timeout
      }

      /**
       * Create a resource.
       */
      @Override
      protected Object create(Object key) throws Exception {
         String uri = (String) key;
         URL url = VSSlidingScale.class.getResource(uri);
         return SVGSupport.getInstance().createSVGTransformer(url);
      }
   }

   public static Map fmap = null;
   private final int MAX_FONT_SIZE = 18;
   private final int MIN_FONT_SIZE = 6;

   private int faceID;
   protected Dimension contentSize;
   private int tickMargin;
   private int distance;
   private double scaleX = 1;
   private double scaleY = 1;
   private Point2D startPoint = new Point2D.Double(0, 0);

   private String needlePath;
   private String panelPath;

   private Point2D linePosition;
   private double lineWidth;
   private double lineHeight;

   private double majorTickLength;
   private Color majorTickColor;
   private int majorTickWidth;

   private double minorTickLength;
   private Color minorTickColor;
   private int minorTickWidth;

   private Point2D labelPosition;
   private double tickLabelWidth;
   private double tickLabelHeight;

   private int valueMargin = 2;

   /**
    * Only for test.
    */
   public static class TestInfo2 extends SlidingScaleVSAssemblyInfo {
      @Override
      public double getMajorInc() {
         return 25000;
      }

      @Override
      public String getMajorIncValue() {
         return "25000";
      }

      @Override
      public double getMax() {
         return 100000;
      }

      @Override
      public String getMaxValue() {
         return "100000";
      }

      @Override
      public double getMin() {
         return 0;
      }

      @Override
      public double getMinorInc() {
         return 5000;
      }

      @Override
      public String getMinorIncValue() {
         return "5000";
      }

      @Override
      public String getMinValue() {
         return "0";
      }

      @Override
      public double getDoubleValue() {
         return -50000;
      }

      public double getRange() {
         return -50000;
      }

      public Color getRangeColor() {
         return Color.RED;
      }
   }

   private boolean isDrawbg = true;
   private static final Logger LOG =
      LoggerFactory.getLogger(VSSlidingScale.class);
}
