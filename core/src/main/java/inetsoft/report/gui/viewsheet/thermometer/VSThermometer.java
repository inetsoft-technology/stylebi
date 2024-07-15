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

import inetsoft.report.StyleFont;
import inetsoft.report.gui.viewsheet.VSFaceUtil;
import inetsoft.report.gui.viewsheet.VSImageable;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.ThermometerVSAssemblyInfo;
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
import java.util.*;

/**
 * The Thermometer VSObject.
 *
 * @version 8.5, 2006-7-5
 * @author InetSoft Technology Corp
 */
public abstract class VSThermometer extends VSImageable implements Cloneable {
   /**
    * Vertical thermometer.
    */
   public static final int STYLE_VERTICAL = 0x01;
   /**
    * Horizontal thermometer.
    */
   public static final int STYLE_HORIZONTAL = 0x02;

   /**
    * Reset the Thermometer's configuration.
    */
   public static synchronized void reset() {
      fmap = null;
   }

   /**
    * Get a sylinder instance.
    */
   public static synchronized VSThermometer getThermometer(int id) {
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
      VSThermometer thermometer = (VSThermometer) fmap.get(ID);

      if(thermometer == null) {
         return null;
      }

      return (VSThermometer) thermometer.clone();
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
    * Constructor.
    */
   protected VSThermometer() {
      super(null);
   }

   /**
    * Adjust scale and margin according to new size.
    */
   protected void adjust() {
      contentSize = getSize();

      double scaleX = (double) contentSize.width / (double) defaultSize.width;
      double scaleY = (double) contentSize.height / (double) defaultSize.height;

      setScale(scaleX, scaleY);
   }

   /**
    * Get the image of the thermemoter.
    */
   @Override
   public BufferedImage getContentImage() {
      adjust();

      BufferedImage image = new BufferedImage(contentSize.width,
         contentSize.height, BufferedImage.TYPE_4BYTE_ABGR);
      Image img = createPanelImage();
      Graphics2D g = (Graphics2D) img.getGraphics();
      setRenderingStrategy(g);
      ThermometerVSAssemblyInfo info = getThermometerAssemblyInfo();

      if(info.getMax() > info.getMin()) {
         drawValueRec(g);
         drawTicksAndValues(g);
         fillRanges(g);
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
    * Clone the object.
    */
   @Override
   public Object clone() {
      try {
         VSThermometer thermometer = (VSThermometer) super.clone();
         thermometer.contentSize = contentSize == null ?
            null : (Dimension) contentSize.clone();
         return thermometer;
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
         return null;
      }
   }

   /**
    * Initialize the guage map.
    */
   protected static void initMap() throws Exception  {
      fmap = new HashMap();
      Document document = Tool.parseXML(
         VSThermometer.class.getResourceAsStream("thermometer.xml"), "UTF-8");
      Element tNode = Tool.getFirstElement(document);
      NodeList list = Tool.getChildNodesByTagName(tNode, "face");

      for(int i = 0; i < list.getLength(); i++) {
         Element node = Tool.getNthChildNode(tNode, i);
         String id = Tool.getAttribute(node, "id");
         String cname = Tool.getAttribute(node, "classname");
         VSThermometer thermometer = null;

         Class c = Class.forName(cname);
         thermometer = (VSThermometer) c.newInstance();

         try {
            thermometer.parseXML(node);
         }
         catch(Exception e) {
            LOG.error("Parse " + id + " error!", e);
         }

         fmap.put(id, thermometer);
      }
   }

   /**
    * Get default format.
    */
   public static VSCompositeFormat getDefaultFormat(int faceId) {
      VSCompositeFormat fmt = new VSCompositeFormat();
      VSThermometer thermometer = getThermometer(faceId);
      Color valueColor = thermometer.getValueColor();
      StyleFont valueFont = thermometer.getValueFont();
      fmt.getDefaultFormat().setForegroundValue(
         String.valueOf(valueColor.getRGB()));
      fmt.getDefaultFormat().setFontValue(valueFont);

      return fmt;
   }

   /**
    * Fill the range.
    */
   protected abstract void fillRanges(Graphics2D g);

   /**
    * Draw the value rectangle.
    */
   protected abstract void drawValueRec(Graphics2D g);

   /**
    * Draw the ticks and tick values.
    */
   protected abstract void drawTicksAndValues(Graphics2D g);

   /**
    * Method to parse an xml segment.
    */
   @Override
   protected void parseXML(Element node) throws Exception {
      Element inode = Tool.getChildNodeByTagName(node, "image");
      Element pnode = Tool.getChildNodeByTagName(inode, "panel");
      double dwidth = Double.parseDouble(Tool.getAttribute(pnode, "width"));
      double dheight = Double.parseDouble(Tool.getAttribute(pnode, "height"));
      defaultSize = new Dimension((int) dwidth, (int) dheight);
      contentSize = defaultSize;
      panelPath = Tool.getValue(pnode);

      Element mtNode = Tool.getChildNodeByTagName(node, "majorTick");
      majorTickWidth =
         Double.parseDouble(Tool.getAttribute(mtNode, "lineWidth"));
      majorTickLength = Double.parseDouble(
         Tool.getAttribute(mtNode, "lineLength"));
      majorTickColor = parseColor(Tool.getAttribute(mtNode, "color"));

      Element miNode = Tool.getChildNodeByTagName(node, "minorTick");
      minorTickLength = Double.parseDouble(
         Tool.getAttribute(miNode, "lineLength"));

      minorTickWidth =
         Double.parseDouble(Tool.getAttribute(miNode, "lineWidth"));

      minorTickColor = parseColor(Tool.getAttribute(miNode, "color"));

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
    * Get the current value.
    */
   protected double getValue() {
      return getThermometerAssemblyInfo().getDoubleValue();
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
    * Draw one tick.
    *
    * @param g The graphics object to draw.
    * @param start The start point.
    * @param end The end point.
    * @param lineWidth The tick's line width.
    * @param color The tick's color.
    */
   protected void drawTick(Graphics2D g, Point2D start, Point2D end,
                           double lineWidth, Color color) {
      g.setStroke(new BasicStroke((float) lineWidth, BasicStroke.CAP_ROUND,
         BasicStroke.JOIN_MITER));
      g.setColor(color);
      Line2D line = new Line2D.Double();
      line.setLine(start, end);
      g.draw(line);
   }

   /**
    * Set the rendering stratergy.
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
    * Get the assembly information.
    */
   protected ThermometerVSAssemblyInfo getThermometerAssemblyInfo() {
      return (ThermometerVSAssemblyInfo) getAssemblyInfo();
   }

   /**
    * Get the current scale of the image.
    */
   private AffineTransform getScale() {
      return AffineTransform.getScaleInstance(this.scaleX, this.scaleY);
   }

   /**
    * Parse the color from the XML attribute.
    * @param c the color stored in the XML, it is should be formated as
    *          alpha, red, green, blue.
    * @return the color if successful.
    */
   @Override
   protected Color parseColor(String c) {
      StringTokenizer token = new StringTokenizer(c, ",");
      int alpha = 0;
      int red = 0;
      int blue = 0;
      int green = 0;

      try {
         alpha = Integer.parseInt(token.nextToken());
         red = Integer.parseInt(token.nextToken());
         green = Integer.parseInt(token.nextToken());
         blue = Integer.parseInt(token.nextToken());
         return new Color(red, green, blue, alpha);
      }
      catch(Exception e) {
         return null;
      }
   }

   /**
    * Parse the font from the XML attribute.
    * @param f, The font description, it is shoule be the format of the
    *           StyleFont's toString() method's result.
    * @return return the font, if not successful, it return the default font!
    */
   @Override
   protected StyleFont parseFont(String f, Font defaultFont) {
      Font font = StyleFont.decode(f);

      if(font == null) {
         font = defaultFont;
      }

      return new StyleFont(font);
   }

   /**
    * Set the scale of the image.
    * @param scaleX the width's scale.
    * @param scaleY the heigth's scale.
    */
   protected void setScale(double scaleX, double scaleY) {
      this.scaleX = scaleX;
      this.scaleY = scaleY;
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
         super("thermometer", 15, 604800000L); // one week timeout
      }

      /**
       * Create a resource.
       */
      @Override
      protected Object create(Object key) throws Exception {
         String uri = (String) key;
         URL url = VSThermometer.class.getResource(uri);
         return SVGSupport.getInstance().createSVGTransformer(url);
      }
   }

   protected static final int STYLE_MASK = 0xF;
   protected static final int THEME_MASK = 0xF0;
   protected static final int COLOR_MASK = 0xFF00;
   protected static final Font DEFAULT_FONT =
      new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, Font.PLAIN, 10);
   protected static final int MIN_FONT_SIZE = 8;
   protected static final int MAX_FONT_SIZE = 18;

   private static Map fmap = null;

   protected Dimension contentSize;
   protected double scaleX = 1;
   protected double scaleY = 1;
   protected String panelPath;

   protected Color minorTickColor;
   protected double minorTickWidth;
   protected double minorTickLength;

   protected Color majorTickColor;
   protected double majorTickLength;
   protected double majorTickWidth;

   private boolean isDrawbg = true;
   private static final Logger LOG =
      LoggerFactory.getLogger(VSThermometer.class);
}
