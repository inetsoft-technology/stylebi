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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.*;
import inetsoft.graph.GraphTool;
import inetsoft.graph.internal.GTool;
import inetsoft.util.ResourceCache;
import inetsoft.util.Tool;
import inetsoft.util.graphics.SVGSupport;
import inetsoft.util.graphics.SVGTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.net.URL;

/**
 * This class draws a SVG image as a shape.
 *
 * @author InetSoft Technology Corp
 * @version 10.3
 */
@TernClass(url = "#cshid=SVGShape")
public class SVGShape extends GShape {
   /**
    * Check mark icon.
    */
   @TernField
   public static final SVGShape CHECK = new SVGShape("images/check.svg");
   /**
    * Minus icon.
    */
   @TernField
   public static final SVGShape MINUS = new SVGShape("images/minus.svg");
   /**
    * Plus icon.
    */
   @TernField
   public static final SVGShape PLUS = new SVGShape("images/plus.svg");
   /**
    * Star icon.
    */
   @TernField
   public static final SVGShape STAR = new SVGShape("images/star.svg");
   /**
    * Sun icon.
    */
   @TernField
   public static final SVGShape SUN = new SVGShape("images/sun.svg");
   /**
    * X icon.
    */
   @TernField
   public static final SVGShape X = new SVGShape("images/x.svg");
   /**
    * A blank face.
    */
   @TernField
   public static final SVGShape FACE_BLANK = new SVGShape("images/face_blank.svg");
   /**
    * A face with no expression.
    */
   @TernField
   public static final SVGShape FACE_OK = new SVGShape("images/face_ok.svg");
   /**
    * A sad face.
    */
   @TernField
   public static final SVGShape FACE_SAD = new SVGShape("images/face_sad.svg");
   /**
    * A smiling face.
    */
   @TernField
   public static final SVGShape FACE_SMILE = new SVGShape("images/face_smile.svg");
   /**
    * A happy face.
    */
   @TernField
   public static final SVGShape FACE_HAPPY = new SVGShape("images/face_happy.svg");
   /**
    * Up arrow icon.
    */
   @TernField
   public static final SVGShape UP_ARROW = new SVGShape("images/up_arrow.svg");
   /**
    * Down arrow icon.
    */
   @TernField
   public static final SVGShape DOWN_ARROW = new SVGShape("images/down_arrow.svg");
   /**
    * Left arrow icon.
    */
   @TernField
   public static final SVGShape LEFT_ARROW = new SVGShape("images/left_arrow.svg");
   /**
    * Right arrow icon.
    */
   @TernField
   public static final SVGShape RIGHT_ARROW = new SVGShape("images/right_arrow.svg");
   /**
    * Male person icon.
    */
   @TernField
   public static final SVGShape MALE = new SVGShape("images/male.svg");
   /**
    * Female person icon.
    */
   @TernField
   public static final SVGShape FEMALE = new SVGShape("images/female.svg");
   /**
    * Warning icon.
    */
   @TernField
   public static final SVGShape WARNING = new SVGShape("images/warning.svg");

   /**
    * Create an empty image shape. Image must be set before it's used.
    */
   public SVGShape() {
   }

   /**
    * Create a image shape.
    *
    * @param icon the svg file resource path.
    */
   @TernConstructor
   public SVGShape(String icon) {
      setSVG(icon);
      setFill(true);
   }

   /**
    * Set the image for filling the shape.
    *
    * @param icon the svg file resource path.
    */
   @TernMethod
   public void setSVG(String icon) {
      this.resource = icon;
   }

   /**
    * Get the image for filling the shape.
    */
   @TernMethod
   public String getSVG() {
      return resource;
   }

   @Override
   @TernMethod
   public double getMinSize() {
      return 12;
   }

   @Override
   public Shape getShape(double x, double y, double w, double h) {
      if(w < 0) {
         x += w;
         w = -w;
      }

      if(h < 0) {
         y += h;
         h = -h;
      }

      return new Rectangle2D.Double(x, y, w, h);
   }

   @Override
   public void paint(Graphics2D g, Shape shape) {
      g = (Graphics2D) g.create();
      g.clip(shape);

      try {
         Rectangle2D bounds = shape.getBounds2D();
         double bw = bounds.getWidth();
         double bh = bounds.getHeight();
         Dimension bsize = new Dimension();
         bsize.setSize(bw, bh);
         Tuple tuple = new Tuple(resource, bsize);
         Image img = (Image)
            ResourceCache2.getResourceCache().get(tuple);

         if(g.getColor() != null) {
            img = GTool.changeHue(img, g.getColor());
         }

         GraphTool.drawImage(g, img, bounds.getX(), bounds.getY(),
                             bounds.getWidth(), bounds.getHeight());
      }
      catch(Exception ex) {
         LOG.warn("Failed to paint shape", ex);
      }

      g.dispose();
   }

   public Image getImage(Dimension size) {
      Tuple tuple = new Tuple(resource, size);

      try {
         return (Image) ResourceCache2.getResourceCache().get(tuple);
      }
      catch(Exception ex) {
         LOG.warn("Failed to get the image", ex);
      }

      return null;
   }

   @Override
   public String getLegendId() {
      return super.getLegendId() + resource;
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof SVGShape)) {
         return false;
      }

      return super.equals(obj) && Tool.equals(resource, ((SVGShape) obj).resource);
   }

   @Override
   public String toString() {
      return "SVGGshape" + System.identityHashCode(this) + "(" + resource + ")";
   }

   /**
    * Icon resource cache.
    */
   private static class ResourceCache2 extends ResourceCache {
      /**
       * Get the resource cache.
       */
      public static ResourceCache getResourceCache() {
         if(cache == null) {
            cache = new ResourceCache2();
         }

         return cache;
      }

      /**
       * Create a resource.
       */
      @Override
      protected Object create(Object key) throws Exception {
         Tuple tuple = (Tuple) key;
         String uri = tuple.resource;
         Dimension size = tuple.size;

         if(uri.indexOf(':') < 0) {
            URL url = SVGShape.class.getResource(uri);
            uri = url.toExternalForm();
         }

         SVGTransformer svg = SVGSupport.getInstance().createSVGTransformer(new URL(uri));
         Dimension isize = svg.getDefaultSize();
         svg.setSize(size);
         svg.setTransform(AffineTransform.getScaleInstance(
            ((double) size.width) / ((double) isize.width),
            ((double) size.height) / ((double) isize.height)));

         return svg.getImage();
      }
   }

   private class Tuple {
      public Tuple(String resource, Dimension size) {
         this.resource = resource;
         this.size = size;
      }

      public int hashCode() {
         return resource.hashCode();
      }

      public boolean equals(Object obj) {
         if(!(obj instanceof Tuple)) {
            return false;
         }

         Tuple tuple = (Tuple) obj;

         return Tool.equals(resource, tuple.resource) && Tool.equals(size, tuple.size);
      }

      public String resource;
      public Dimension size;
   }

   private static final Logger LOG = LoggerFactory.getLogger(SVGShape.class);
   private static ResourceCache2 cache;
   private static final long serialVersionUID = 1L;

   private String resource;
}
