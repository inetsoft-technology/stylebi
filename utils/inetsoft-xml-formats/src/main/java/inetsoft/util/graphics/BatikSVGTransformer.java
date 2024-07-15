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
package inetsoft.util.graphics;

import org.apache.batik.anim.dom.SVGOMDocument;
import org.apache.batik.bridge.*;
import org.apache.batik.gvt.GraphicsNode;
import org.apache.batik.gvt.renderer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.svg.*;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * SVGTransformer can transform a svg document as a buffered image.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public class BatikSVGTransformer implements Cloneable, SVGTransformer {
   /**
    * Conversion ratio
    */
   public static final double RATIO = 0.8434375d;

   /**
    * Constructor.
    */
   public BatikSVGTransformer() {
      super();
   }

   /**
    * Constructor.
    */
   public BatikSVGTransformer(Document doc) {
      super();

      UserAgent agent = new UserAgentAdapter() {
         /**
          * Returns the default size of this user agent (400x400).
          */
         @Override
         public Dimension2D getViewportSize() {
            return new Dimension(200, 200);
         }
      };

      BridgeContext ctx = new BridgeContext(agent);
      SVGOMDocument svgDoc = (SVGOMDocument) doc;
      SVGSVGElement root = svgDoc.getRootElement();
      GVTBuilder builder = new GVTBuilder();
      this.gvtRoot = builder.build(ctx, svgDoc);
      SVGAnimatedRect viewBox = root.getViewBox();

      if(viewBox != null) {
         SVGRect rect = viewBox.getBaseVal();

         if(rect != null) {
            this.documentSize = new Dimension((int) rect.getWidth(), (int) rect.getHeight());
         }
      }
   }

   /**
    * Get the affine transform.
    * @return the affine transform.
    */
   @Override
   public AffineTransform getTransform() {
      return transform;
   }

   /**
    * Set the affine transform.
    * @return transform the specific affine transform.
    */
   @Override
   public void setTransform(AffineTransform transform) {
      this.transform = transform;
   }

   /**
    * Get the default svg size.
    * @return the default svg size.
    */
   @Override
   public Dimension getSize() {
      return size;
   }

   /**
    * Set the default svg size.
    * @return size the specific svg size.
    */
   @Override
   public void setSize(Dimension size) {
      this.size = size;
   }

   /**
    * Get the original size of the image.
    */
   @Override
   public Dimension getDefaultSize() {
      if(documentSize != null) {
         return documentSize;
      }
      else {
         Rectangle2D bounds = gvtRoot.getOutline().getBounds2D();
         Dimension size = new Dimension();
         size.setSize(bounds.getWidth(), bounds.getHeight());
         return size;
      }
   }

   /**
    * Get buffered image of the svg.
    * @return the buffered image.
    */
   @Override
   public BufferedImage getImage() throws Exception {
      //ImageRendererFactory rendFactory = new ConcreteImageRendererFactory();
      //ImageRenderer renderer = rendFactory.createStaticImageRenderer();

      RenderingHints hints = new RenderingHints(null);

      hints.put(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
      hints.put(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
      hints.put(RenderingHints.KEY_COLOR_RENDERING,
                RenderingHints.VALUE_COLOR_RENDER_QUALITY);
      hints.put(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);
      /*
      hints.put(RenderingHints.KEY_DITHERING,
                RenderingHints.VALUE_DITHER_ENABLE);
      hints.put(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      hints.put(RenderingHints.KEY_ALPHA_INTERPOLATION,
                RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
      */

      StaticRenderer renderer = new StaticRenderer(hints, transform);

      renderer.updateOffScreen(size.width, size.height);
      renderer.setTransform(transform);
      renderer.setTree(gvtRoot);

      Shape shape = new Rectangle2D.Float(0, 0, size.width, size.height);
      AffineTransform inverse = transform.createInverse();
      renderer.repaint(inverse.createTransformedShape(shape));
      return renderer.getOffScreen();
   }

   /**
    * Clone this object.
    * @return the cloned object.
    */
   @Override
   public Object clone() {
      try {
         return super.clone();
      }
      catch(Exception ex) {
         LOG.error("Failed to clone SVGTransformer", ex);
      }

      return null;
   }

   private GraphicsNode gvtRoot;
   private AffineTransform transform = new AffineTransform();
   private Dimension size = new Dimension(100, 100);
   private Dimension documentSize;

   private static final Logger LOG =
      LoggerFactory.getLogger(BatikSVGTransformer.class);
}
