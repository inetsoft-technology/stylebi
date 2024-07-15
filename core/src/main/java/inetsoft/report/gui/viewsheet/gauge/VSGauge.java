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

import inetsoft.report.StyleFont;
import inetsoft.report.gui.viewsheet.VSFaceUtil;
import inetsoft.report.gui.viewsheet.VSImageable;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import inetsoft.uql.viewsheet.internal.GaugeVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSAssemblyInfo;
import inetsoft.util.*;
import inetsoft.util.css.CSSConstants;
import inetsoft.util.graphics.SVGSupport;
import inetsoft.util.graphics.SVGTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * VSGauge component for viewsheet.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class VSGauge extends VSImageable implements Cloneable {
   /**
    * The id number's face mask.
    */
   public static final int FACE_MASK = 0x00FF;
   /**
    * The face id's rotation mask;
    */
   public static final int ROTATION_MASK = 0xF000;
   /**
    * The face id's shape mask.
    */
   public static final int SHAPE_MASK = 0x0F00;

   /**
    * The rotation's factor, you can use the rotation multiply with the factor.
    * Clockwise rotation unit is 360/16 = 22.5, if you want to roate the image
    * 45 degree, you will be set the rotation in ID to be ((int)(45 / 22.5) <<4)
    */
   public static final double ROTATION_FACTOR = 22.5f;

   /**
    * Shape style: full.
    */
   public static final int SHAPE_FULL = 1;
   /**
    * Shape style: 1/2.
    */
   public static final int SHAPE_HALF = 2;
   /**
    * Shape style: 1/4.
    */
   public static final int SHAPE_QUARTER = 4;
   /**
    * Shape style: 1/8.
    */
   public static int SHAPE_EIGHTH = 8;

   /**
    * The default font of the VSGauge.
    */
   public static final Font DEFAULT_FONT =
      new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, Font.PLAIN, 10);

   /**
    * Use the id to create VSGauge.
    * @param id The VSGauge's id
    * @return if the ID does not exist, the method will return null.
    */
   public static synchronized VSGauge getGauge(int id) {
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
      VSGauge gauge = (VSGauge) fmap.get(ID);

      if(gauge == null) {
         return null;
      }

      return (VSGauge) gauge.clone();
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

      Map ids = new HashMap();

      // @by larryl, backward compatibility, hide old IDs, and only support
      // them in runtime. New ids are > 10000. Remove later (12.1, 2015).
      for(Object id : fmap.keySet()) {
         int idval = Integer.parseInt(id.toString());

         if(idval > 10000) {
            ids.put(id, id);
         }
      }

      return VSFaceUtil.getPrefixIDs(ids);
   }

   /**
    * Reset the VSGauge's configuration.
    */
   public static synchronized void reset() {
      fmap = null;
   }

   /**
    * Constructor.
    */
   protected VSGauge() {
      super(null);
   }

   /**
    * Constructor.
    */
   protected VSGauge(int faceID) {
      this();
      this.faceID = faceID;
   }

   /**
    * Set the Info.
    */
   @Override
   public void setAssemblyInfo(VSAssemblyInfo info) {
      super.setAssemblyInfo(info);

      VSCompositeFormat fmt = info.getFormat();

      labelFmt = (VSCompositeFormat) fmt.clone();
      valueFmt = (VSCompositeFormat) fmt.clone();

      labelFmt.getCSSFormat().setCSSType(CSSConstants.GAUGE_TICK_LABEL);
      valueFmt.getCSSFormat().setCSSType(CSSConstants.GAUGE_VALUE_LABEL);
   }

   /**
    * Get the face number.
    */
   public int getFaceNumber() {
      return faceID & FACE_MASK;
   }

   /**
    * Get the rotation of the VSObject.
    */
   public double getRotation() {
      // faceID is hexadecimal
      int decimalFaceID = Integer.parseInt(Integer.toString(faceID), 16);
      double degree = ROTATION_FACTOR * ((decimalFaceID & ROTATION_MASK) >> 12);
      return -degreeToRadian(degree);
   }

   /**
    * Get the shape style, the style is the constant.
    */
   public int getShapeStyle() {
      return faceID & SHAPE_MASK;
   }

   /**
    * Get the face ID.
    */
   public int getFaceID() {
      return faceID;
   }

   /**
    * Adjust scale and margin according to new size.
    */
   protected void adjust(boolean export) {
      double dwidth = defaultSize.width;
      double dheight = defaultSize.height;

      Dimension size = getPixelSize();

      if(size == null) {
         return;
      }

      int width = size.width;
      int height = size.height;

      if(export) {
         Insets padding = getGaugeAssemblyInfo().getPadding();
         width = size.width - padding.left - padding.right;
         height = size.height - padding.top - padding.bottom;
      }

      double xscale = width / dwidth;
      double yscale = height / dheight;
      double nscale = Math.min(xscale, yscale);

      setScale(nscale);
   }

   /**
    * Set the scale.
    */
   protected void setScale(double scale) {
      this.scale = scale;
      center = new Point2D.Double(center.getX() * scale, center.getY() * scale);
      majorTickRadius *= scale;
      minorTickRadius *= scale;
      rangeRadius *= scale;
      // adjust the radius of the tick's value
      valueRadius *= scale;
      contentSize.width = (int) (defaultSize.width * scale);
      contentSize.height = (int) (defaultSize.height * scale);

      rangeWidth *= scale;
      majorTickLength *= scale;
      majorTickLineWidth *= scale;
      minorTickLength *= scale;
      minorTickLineWidth *= scale;
   }

   /**
    * Check if the ranges should be draw with a round head.
    */
   protected boolean isRoundRanges() {
      return roundRanges;
   }

   /**
    * Check if the gauge need to fill ranges, the default is true.
    */
   protected boolean isFillRanges() {
      return fillRanges;
   }

   /**
    * Set whether the gauge need fill ranges.
    */
   protected void setFillRanges(boolean n) {
      fillRanges = n;
   }

   /**
    * Set if the ranges should be draw with a round head.
    */
   protected void setRoundRanges(boolean r) {
      roundRanges = r;
   }

   /**
    * Initialize the guage map.
    */
   private static void initMap() throws Exception  {
      fmap = new HashMap();
      Document document = Tool.parseXML(
         VSGauge.class.getResourceAsStream("gauge.xml"), "UTF-8");
      Element gaugeNode = Tool.getFirstElement(document);
      NodeList list = Tool.getChildNodesByTagName(gaugeNode, "face");

      for(int i = 0; i < list.getLength(); i++) {
         Element node = Tool.getNthChildNode(gaugeNode, i);
         String classname = node.getAttribute("classname");

         Class cls = Class.forName(classname);
         VSGauge gauge = (VSGauge) cls.newInstance();
         gauge.parseXML(node);
         fmap.put(Integer.toString(gauge.getFaceID()), gauge);
      }
   }

   /**
    * Get default format.
    */
   public static VSCompositeFormat getDefaultFormat(int faceId) {
      VSCompositeFormat fmt = new VSCompositeFormat();
      VSGauge gauge = getGauge(faceId);
      Color valueColor = gauge.getValueColor();
      StyleFont valueFont = gauge.getValueFont();
      fmt.getDefaultFormat().setForegroundValue(String.valueOf(valueColor.getRGB()));
      fmt.getDefaultFormat().setFontValue(valueFont);

      return fmt;
   }

   /**
    * Method to parse an xml segment.
    */
   @Override
   protected void parseXML(Element node) throws Exception {
      if(node.getNodeName().equals("face")) {
         // parse face tag
         int id = Integer.parseInt(Tool.getAttribute(node, "id"));
         faceID = id;

         String str;

         if((str = Tool.getAttribute(node, "center.x")) != null) {
            double x = Double.parseDouble(Tool.getAttribute(node, "center.x"));
            double y = Double.parseDouble(Tool.getAttribute(node, "center.y"));
            center = new Point2D.Double(x, y);
            int width = Integer.parseInt(Tool.getAttribute(node, "width"));
            int height = Integer.parseInt(Tool.getAttribute(node, "height"));
            defaultSize = new Dimension(width, height);
            contentSize = new Dimension(width, height);
            double iangle = Double.parseDouble(Tool.getAttribute(node, "angle"));
            angle = degreeToRadian(iangle);
         }

         // parse image tag
         Element imageNode = Tool.getChildNodeByTagName(node, "image");

         if(imageNode != null) {
            Element panelNode = Tool.getChildNodeByTagName(imageNode, "panel");
            panelPath = Tool.getValue(panelNode);
            needleCirclePath = Tool.getChildValueByTagName(imageNode, "needleCircle");
            needlePath = Tool.getChildValueByTagName(imageNode, "needle");
         }

         // parse range tag
         Element rangeNode = Tool.getChildNodeByTagName(node, "range");

         if(rangeNode != null) {
            rangeRadius = Double.parseDouble(Tool.getAttribute(rangeNode, "rangeRadius"));
            rangeWidth = Double.parseDouble(Tool.getAttribute(rangeNode, "rangeWidth"));
            String sRoundRanges = Tool.getAttribute(rangeNode, "sRoundRanges");

            if(sRoundRanges != null) {
               roundRanges = Boolean.valueOf(sRoundRanges).booleanValue();
            }

            String sFillRanges = Tool.getAttribute(rangeNode, "fillRanges");

            if(sFillRanges != null) {
               fillRanges = Boolean.valueOf(sFillRanges).booleanValue();
            }
         }

         // parse majorTick tag
         Element btNode = Tool.getChildNodeByTagName(node, "majorTick");

         if(btNode != null) {
            majorTickRadius = Double.parseDouble(Tool.getAttribute(btNode, "radius"));
            majorTickColor = parseColor(Tool.getAttribute(btNode, "color"));
            majorTickVisible = !"false".equals(Tool.getAttribute(btNode, "visible"));

            if(majorTickColor == null) {
               majorTickColor = Color.BLACK;
            }

            majorTickLineWidth = Integer.parseInt(Tool.getAttribute(btNode, "lineWidth"));
            majorTickLength = Integer.parseInt(Tool.getAttribute(btNode, "length"));
         }

         // parse minorTick tag
         Element ltNode = Tool.getChildNodeByTagName(node, "minorTick");

         if(ltNode != null) {
            minorTickRadius = Double.parseDouble(Tool.getAttribute(ltNode, "radius"));
            minorTickLength = Double.parseDouble(Tool.getAttribute(ltNode, "length"));
            minorTickColor = parseColor(Tool.getAttribute(ltNode, "color"));
            minorTickVisible = !"false".equals(Tool.getAttribute(ltNode, "visible"));

            if(minorTickColor == null) {
               minorTickColor = Color.BLACK;
            }

            minorTickLineWidth = Integer.parseInt(Tool.getAttribute(ltNode, "lineWidth"));
         }
      }

      super.parseXML(node);
   }

   /**
    * Get the gauge assembly information.
    */
   protected GaugeVSAssemblyInfo getGaugeAssemblyInfo() {
      return (GaugeVSAssemblyInfo) getAssemblyInfo();
   }

   /**
    * Get the current value.
    */
   protected double getValue() {
      return getGaugeAssemblyInfo().getDoubleValue();
   }

   /**
    * Clone the object.
    */
   @Override
   public Object clone() {
      try {
         VSGauge gauge = (VSGauge) super.clone();
         gauge.contentSize = contentSize == null ?
            null : (Dimension) contentSize.clone();
         return gauge;
      }
      catch(Exception ex) {
         LOG.error(ex.getMessage(), ex);
         return null;
      }
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

      try {
         SVGTransformer transformer = (SVGTransformer) getResourceCache().get(uri);

         synchronized(transformer) {
            transformer.setSize(contentSize);
            transformer.setTransform(transform);
            return transformer.getImage();
         }
      }
      catch(Exception ex) {
         throw new RuntimeException("Unable to find or load image: " + uri, ex);
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
    * Turn the degree to the radian.
    */
   protected static double degreeToRadian(double angle) {
      int factor = Math.abs((int) angle / 360);

      if(angle < 0) {
         angle = 360 + (angle + factor * 360);
      }
      else {
         angle = angle - factor * 360;
      }

      return (double) angle / 180D * Math.PI;
   }

   /**
    * Turn the radian to the degree.
    */
   protected static double radianToDegree(double radian) {
      return radian / Math.PI * 180;
   }

   /**
    * Get the start point of the gauge.
    */
   @Override
   protected Point getPosition() {
      Insets inset = getGaugeAssemblyInfo().getPadding();
      double x = inset.left;
      double y = inset.top;

      if(contentSize != null) {
         Dimension size = getSize();
         Dimension csize = contentSize;
         int width = size.width - inset.left - inset.right;
         int height = size.height - inset.top - inset.bottom;
         x += width > csize.width ? (width - csize.width) / 2 : 0;
         y += height > csize.height ? (height - csize.height) / 2 : 0;
      }

      return new Point((int) x, (int) y);
   }

   /**
    * Gauge resource cache.
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
         super("gauge", 15, 604800000L); // one week timeout
      }

      /**
       * Create a resource.
       */
      @Override
      protected Object create(Object key) throws Exception {
         String uri = (String) key;
         URL url = VSGauge.class.getResource(uri);
         return SVGSupport.getInstance().createSVGTransformer(url);
      }
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
    * Set the name (id) of the gauge assembly.
    */
   public static void setGaugeName(String vid) {
      vsobjId.set(vid);
   }

   public static void setGaugeClass(String vclass) {
      vsClass.set(vclass);
   }

   /**
    * Create the gauge that content in svg.
    */
   public abstract Graphics2D getContentSvg(boolean isShadow);

   protected static final ThreadLocal<String> vsobjId = new ThreadLocal<>();
   protected static final ThreadLocal<String> vsClass = new ThreadLocal<>();

   private static Map fmap = null;

   protected Point2D center;
   protected double scale = 1;
   protected Dimension contentSize;

   protected String needlePath;
   protected String panelPath;
   protected String needleCirclePath;

   protected double majorTickRadius;
   protected double majorTickLength;
   protected Color majorTickColor;
   protected int majorTickLineWidth;
   protected boolean majorTickVisible = true;

   protected double minorTickRadius;
   protected double minorTickLength;
   protected Color minorTickColor;
   protected int minorTickLineWidth;
   protected boolean minorTickVisible = true;

   protected double rangeRadius;
   protected double angle = 0;
   protected double rangeWidth;

   protected VSCompositeFormat valueFmt;
   protected VSCompositeFormat labelFmt;

   private boolean roundRanges = false;
   private boolean fillRanges = true;
   private int faceID;
   private boolean isDrawbg = true;

   private static final Logger LOG =
      LoggerFactory.getLogger(VSGauge.class);
}
