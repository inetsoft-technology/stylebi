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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.*;
import inetsoft.graph.GraphConstants;
import inetsoft.graph.GraphTool;
import inetsoft.graph.internal.GTool;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.*;
import java.io.*;
import java.util.Objects;

/**
 * The GShape class is the base class for all shape aesthetics.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class GShape implements Cloneable, Serializable {
   /**
    * A circle shape.
    */
   @TernField
   public static final GShape CIRCLE = new Circle();

   /**
    * A triangle shape.
    */
   @TernField
   public static final GShape TRIANGLE = new Triangle();

   /**
    * A square shape.
    */
   @TernField
   public static final GShape SQUARE = new Square();

   /**
    * A cross shape.
    */
   @TernField
   public static final GShape CROSS = new Cross();

   /**
    * A star shape.
    */
   @TernField
   public static final GShape STAR = new Star();

   /**
    * A diamond shape.
    */
   @TernField
   public static final GShape DIAMOND = new Diamond();

   /**
    * An x shape.
    */
   @TernField
   public static final GShape XSHAPE = new XShape();

   /**
    * A filled circle shape.
    */
   @TernField
   public static final GShape FILLED_CIRCLE = CIRCLE.create(false, true);

   /**
    * A filled triangle shape.
    */
   @TernField
   public static final GShape FILLED_TRIANGLE = TRIANGLE.create(false, true);

   /**
    * A filled square shape.
    */
   @TernField
   public static final GShape FILLED_SQUARE = SQUARE.create(false, true);

   /**
    * A filled diamond shape.
    */
   @TernField
   public static final GShape FILLED_DIAMOND = DIAMOND.create(false, true);

   /**
    * A V shape.
    */
   @TernField
   public static final GShape VSHAPE = new VShape();

   /**
    * A half moon shape.
    */
   @TernField
   public static final GShape LSHAPE = new LShape();

   /**
    * A left arrow shape.
    */
   @TernField
   public static final GShape ARROW = new Arrow();

   /**
    * A filled left arrow shape.
    */
   @TernField
   public static final GShape FILLED_ARROW = ARROW.create(false, true);

   /**
    * A vertical stick with two ends. Useful as interval shape.
    */
   @TernField
   public static final GShape STICK = new Stick();

   /**
    * A bar with an upward arrow. useful as interval shape.
    */
   @TernField
   public static final GShape ARROWBAR = new ArrowBar();
   /**
    * A filled bar with an upward arrow. useful as interval shape.
    */
   @TernField
   public static final GShape FILLED_ARROWBAR = ARROWBAR.create(false, true);

   /**
    * A vertical line.
    */
   @TernField
   public static final GShape LINE = new Line();

   /**
    * A horizontal line.
    */
   @TernField
   public static final GShape HYPHEN = new Hyphen();

   /**
    * An empty shape. This shape has not visual drawing and takes up no space.
    * It can be used to plot text as point.
    */
   @TernField
   public static final GShape NIL = new Nil();

   /**
    * Constructor.
    */
   protected GShape() {
      super();
   }

   /**
    * Get the center point of the shape. A shape may define a center point that
    * is different from the center of the bounding box.
    */
   public Point2D getCenter(Shape shape) {
      Rectangle2D box = shape.getBounds2D();

      return new Point2D.Double(box.getX() + box.getWidth() / 2,
                                box.getY() + box.getHeight() / 2);
   }

   /**
    * Check if the outer border is drawn.
    */
   @TernMethod
   public boolean isOutline() {
      return outline || !fill;
   }

   /**
    * Set if the outer border is drawn. If fill is set to false, the outline is
    * always drawn regardless of this setting.
    */
   protected void setOutline(boolean outline) {
      this.outline = outline;
   }

   /**
    * Check if the shape should be filled.
    */
   @TernMethod
   public boolean isFill() {
      return fill;
   }

   /**
    * Set whether this shape should be filled.
    */
   protected void setFill(boolean fill) {
      this.fill = fill;
   }

   /**
    * Create a variation of the shape.
    * @param outline true to force an outline even if filled.
    * @param fill fill the shape instead of drawing the outline.
    */
   @TernMethod
   public GShape create(boolean outline, boolean fill) {
      GShape shape = clone();
      shape.setOutline(outline);
      shape.setFill(fill);

      return shape;
   }

   /**
    * Set the outline color. This color is used for the outline if both fill
    * and outline are drawn. The fill uses the default (graphics) color, and
    * the outline use the outline color, or a darker color of the fill color.
    */
   @TernMethod
   public void setLineColor(Color color) {
      this.linecolor = color;
   }

   /**
    * Get the outline color.
    */
   @TernMethod
   public Color getLineColor() {
      return linecolor;
   }

   /**
    * Set the line style used to draw outline. Default to a single pixel line.
    * @param lineStyle line style defined in GraphConstants.
    */
   @TernMethod
   public void setLineStyle(int lineStyle) {
      this.lineStyle = lineStyle;
   }

   /**
    * Get the line style used to draw outline. Default to a single pixel line.
    */
   @TernMethod
   public int getLineStyle() {
      return lineStyle;
   }

   /**
    * Set the fill color. This color is used for the fill of the shape if
    * fill is set to true. If not set, it uses the default (graphics) color.
    */
   @TernMethod
   public void setFillColor(Color color) {
      this.fillcolor = color;
   }

   /**
    * Get the fill color.
    */
   @TernMethod
   public Color getFillColor() {
      return this.fillcolor;
   }

   /**
    * Check if the shape should be painted with anti-aliasing on.
    */
   protected boolean isAntiAlias() {
      return false;
   }

   public Rectangle2D getBounds(Point2D pt, double radius) {
      // @by larryl, since we draw the border of the shape, a half of the border
      // link would be outside of the radius
      double r = radius + 1;

      // use the max bounds instead of the shape bounds. Irregular shape
      // could have different width/height, and after rotation, the
      // getElementBounds would be incorrect
      return new Rectangle2D.Double(pt.getX() - r, pt.getY() - r, r * 2, r * 2);
   }

   /*
    * Draw a shape at the specified bounds.
    * @param x the lower-left corner x position.
    * @param y the lower-left corner y position.
    * @param size the size determines the shape's width and height.
    */
   public void paint(Graphics2D g, double x, double y, double size) {
      paint(g, x - size, y - size, size * 2, size * 2);
   }

   /*
    * Draw a shape at the specified bounds.
    * @param x the lower-left corner x position.
    * @param y the lower-left corner y position.
    * @param w the width of the shape in points.
    * @param h the height of the shape in points.
    * @param outline true to draw the outline of the shape even if it's filled.
    */
   public void paint(Graphics2D g, double x, double y, double w, double h) {
      paint(g, getShape(x, y, w, h));
   }

   /**
    * Paint a shape. The shape could be from GShape.getShape() and
    * possibly transformed.
    */
   public void paint(Graphics2D g, Shape shape) {
      if(shape == null) {
         return;
      }

      GTool.setRenderingHint(g, isAntiAlias());
      Color clr = g.getColor();

      if(fill) {
         if(fillcolor != null) {
            g.setColor(fillcolor);
         }

         g.fill(shape);
      }

      if(!fill || outline) {
         // if line color is set, should always use it regardless of fill. otherwise
         // a bunch of points may have some with line color and others not. (61744)
         if(linecolor != null) {
            g.setColor(linecolor);
         }
         else if(fill) {
            if(fillcolor == null) {
               g.setColor(clr.darker());
            }
            else {
               g.setColor(clr);
            }
         }

         Stroke stroke = g.getStroke();
         g.setStroke(GTool.getStroke(lineStyle));
         g.draw(shape);
         g.setStroke(stroke);
      }

      if(fill) {
         g.setColor(clr);
      }
   }

   /**
    * Get the minimum size (width and height) this shape needs.
    */
   @TernMethod
   public double getMinSize() {
      return 4;
   }

   /**
    * Get an id to that be used to check if two shapes are the same for legend purpose.
    */
   @TernMethod
   public String getLegendId() {
      return getClass().getName();
   }

   /**
    * Clone this shape object.
    */
   @Override
   public GShape clone() {
      try {
         return (GShape) super.clone();
      }
      catch(Exception ex) {
         // impossible
         return this;
      }
   }

   @Override
   public String toString() {
      return getClass().getSimpleName() + "@" + System.identityHashCode(this);
   }

   /*
    * Get the shape object for this shape.
    * @param x the lower-left corner x position.
    * @param y the lower-left corner y position.
    * @param width the width of the shape in points.
    * @param height the height of the shape in points.
    * @return a Shape.
    */
   public abstract Shape getShape(double x, double y, double w, double h);

   /*
    * Circle shape.
    */
   private static class Circle extends GShape {
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

         return new Ellipse2D.Double(x, y, w, h);
      }

      @Override
      protected boolean isAntiAlias() {
         return true;
      }

      private static final long serialVersionUID = 1L;
   }

   /*
    * Cross shape.
    */
   private static class Cross extends GShape {
      @Override
      public Shape getShape(double x, double y, double w, double h) {
         GeneralPath path = new GeneralPath();

         path.moveTo((float) x, (float) (y + h / 2));
         path.lineTo((float) (x + w), (float) (y + h / 2));
         path.moveTo((float) (x + w / 2), (float) y);
         path.lineTo((float) (x + w / 2), (float) (y + h));

         return path;
      }

      private static final long serialVersionUID = 564596937370310072L;
   }

   /*
    * Triangle shape.
    */
   private static class Triangle extends GShape {
      @Override
      public Shape getShape(double x, double y, double w, double h) {
         GeneralPath path = new GeneralPath();

         path.moveTo((float) (x + w / 2), (float) (y + h));
         path.lineTo((float) x, (float) y);
         path.lineTo((float) (x + w), (float) y);
         path.closePath();

         return path;
      }

      @Override
      protected boolean isAntiAlias() {
         return true;
      }

      private static final long serialVersionUID = -7980720868742050600L;
   }

   /*
    * Square shape.
    */
   private static class Square extends GShape {
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

      private static final long serialVersionUID = 7758399316167853952L;
   }

   /*
    * Star shape.
    */
   private static class Star extends GShape {
      @Override
      public Shape getShape(double x, double y, double w, double h) {
         GeneralPath path = new GeneralPath();

         path.moveTo((float) x, (float) y);
         path.lineTo((float) (x + w), (float) (y + h));
         path.moveTo((float) (x + w / 2), (float) y);
         path.lineTo((float) (x + w / 2), (float) (y + h));
         path.moveTo((float) (x + w), (float) y);
         path.lineTo((float) x, (float) (y + h));
         path.moveTo((float) (x + w), (float) (y + h / 2));
         path.lineTo((float) x, (float) (y + h /2));

         return path;
      }

      @Override
      public void paint(Graphics2D g, Shape shape) {
         g = (Graphics2D) g.create();

         Rectangle2D bounds = shape.getBounds2D();
         g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

         if(getLineColor() != null) {
            g.setColor(getLineColor());
         }

         // @by larryl, drawing Line2D with antialias works better in jdk 1.6
         g.draw(new Line2D.Double(bounds.getMinX(), bounds.getCenterY(),
                                  bounds.getMaxX(), bounds.getCenterY()));
         g.draw(new Line2D.Double(bounds.getCenterX(), bounds.getMinY(),
                                  bounds.getCenterX(), bounds.getMaxY()));
         g.draw(new Line2D.Double(bounds.getMinX(), bounds.getMinY(),
                                  bounds.getMaxX(), bounds.getMaxY()));
         g.draw(new Line2D.Double(bounds.getMinX(), bounds.getMaxY(),
                                  bounds.getMaxX(), bounds.getMinY()));

         g.dispose();
      }

      private static final long serialVersionUID = -7040329592076913076L;
   }

   /*
    * Diamond shape.
    */
   private static class Diamond extends GShape {
      @Override
      public Shape getShape(double x, double y, double w, double h) {
         GeneralPath path = new GeneralPath();

         path.moveTo((float) (x + w / 2), (float) y);
         path.lineTo((float) (x + w), (float) (y + h / 2));
         path.lineTo((float) (x + w / 2), (float) (y + h));
         path.lineTo((float) x, (float) (y + h / 2));
         path.lineTo((float) (x + w / 2), (float) y);

         return path;
      }

      @Override
      protected boolean isAntiAlias() {
         return true;
      }

      private static final long serialVersionUID = 6848173164698296051L;
   }

   /*
    * XShape shape.
    */
   private static class XShape extends GShape {
      @Override
      public Shape getShape(double x, double y, double w, double h) {
         GeneralPath path = new GeneralPath();
         path.moveTo((float) x, (float) y);
         path.lineTo((float) (x + w), (float) (y + h));
         path.moveTo((float) (x + w), (float) y);
         path.lineTo((float) x, (float) (y + h));

         return path;
      }

      private static final long serialVersionUID = 7864467157809805951L;
   }

   /*
    * V shape.
    */
   private static class VShape extends GShape {
      @Override
      public Shape getShape(double x, double y, double w, double h) {
         GeneralPath path = new GeneralPath();
         path.moveTo((float) x, (float) (y + h));
         path.lineTo((float) (x + w / 2), (float) y);
         path.lineTo((float) (x + w), (float) (y + h));

         return path;
      }

      @Override
      protected boolean isAntiAlias() {
         return true;
      }

      private static final long serialVersionUID = -299802262354289679L;
   }

   /*
    * L shape.
    */
   private static class LShape extends GShape {
      @Override
      public Shape getShape(double x, double y, double w, double h) {
         GeneralPath path = new GeneralPath();
         path.moveTo((float) x, (float) (y + h));
         path.lineTo((float) x, (float) y);
         path.lineTo((float) (x + w), (float) y);

         return path;
      }

      private static final long serialVersionUID = -2261237120990768357L;
   }

   /*
    * Left arrow shape.
    */
   private static class Arrow extends GShape {
      @Override
      public Shape getShape(double x, double y, double w, double h) {
         GeneralPath path = new GeneralPath();
         path.moveTo((float) (x + w), (float) (y + h));
         path.lineTo((float) x, (float) (y + h / 2));
         path.lineTo((float) (x + w), (float) y);

         if(isFill()) {
            path.closePath();
         }

         return path;
      }

      @Override
      protected boolean isAntiAlias() {
         return true;
      }

      private static final long serialVersionUID = -6595430321957478639L;
   }

   /*
    * Vertical stick shape.
    */
   private static class Stick extends GShape {
      @Override
      public Shape getShape(double x, double y, double w, double h) {
         GeneralPath path = new GeneralPath();
         float cx = (float) (x + w / 2);

         path.moveTo(cx, (float) y);
         path.lineTo(cx, (float) (y + h));
         path.moveTo((float) x, (float) y);
         path.lineTo((float) (x + w), (float) y);
         path.moveTo((float) x, (float) (y + h));
         path.lineTo((float) (x + w), (float) (y + h));

         return path;
      }

      private static final long serialVersionUID = 4410876043576640062L;
   }

   /*
    * Bar with an upward arrow.
    */
   private static class ArrowBar extends GShape {
      @Override
      public Shape getShape(double x, double y, double w, double h) {
         GeneralPath path = new GeneralPath();
         float cx = (float) (x + w / 2);
         float left = (float) (x + w / 3.5);
         float right = (float) (x + w - w / 3.5);
         float head = (float) (y + h - w / 3.5);

         path.moveTo(cx, (float) (y + h)); // top of arrow
         path.lineTo((float) x, head);
         path.lineTo(left, head);
         path.lineTo(left, (float) y);
         path.lineTo(right, (float) y);
         path.lineTo(right, head);
         path.lineTo((float) (x + w), head);
         path.closePath();

         return path;
      }

      @Override
      protected boolean isAntiAlias() {
         return true;
      }

      private static final long serialVersionUID = 7183732650836485466L;
   }

   /*
    * Vertical line shape.
    */
   private static class Line extends GShape {
      @Override
      public Shape getShape(double x, double y, double w, double h) {
         GeneralPath path = new GeneralPath();
         float cx = (float) (x + w / 2);

         path.moveTo(cx, (float) y);
         path.lineTo(cx, (float) (y + h));

         return path;
      }

      private static final long serialVersionUID = -255337667807699769L;
   }

   /*
    * Horizontal line shape.
    */
   private static class Hyphen extends GShape {
      @Override
      public Shape getShape(double x, double y, double w, double h) {
         GeneralPath path = new GeneralPath();
         float cy = (float) (y + h / 2);

         path.moveTo((float) x, cy);
         path.lineTo((float) (x + w), cy);

         return path;
      }

      private static final long serialVersionUID = 2081009156313664605L;
   }

   /*
    * An empty shape.
    */
   private static class Nil extends GShape {
      @Override
      public Shape getShape(double x, double y, double w, double h) {
         return null;
      }

      private static final long serialVersionUID = -3120260697543232756L;
   }

   /**
    * Image fill shape.
    */
   @TernClass(url = "#cshid=ImageShape")
   public static class ImageShape extends GShape {
     /**
       * Alignment of the image relative to data point position.
       */
      public enum Alignment { CENTER, TOP, RIGHT };

      /**
       * Create an empty image shape. Image must be set before it's used.
       */
      public ImageShape() {
         // make sure alpha is set in graphics so transparency works
         setFill(true);
      }

      /**
       * Create an image shape.
       * @param path image file or resource path. It could be a storage path
       *             (e.g. portal/shapes/us.jpg).
       */
      public ImageShape(String path) {
         this();
         this.path = path;
      }

      /**
       * Create an image shape.
       * @param path image file or resource path.
       * @param asIs true if the image should be drawn as-is
       *                 without applying shape size and color.
       */
      @TernConstructor
      public ImageShape(String path, boolean asIs) {
         this();
         this.path = path;
         applyColor = applySize = !asIs;
      }

      /**
       * Create a image shape.
       */
      public ImageShape(Image image) {
         this();
         setImage(image);
      }

      /**
       * Get an id that uniquel identifies this image shape.
       */
      @TernMethod
      public String getImageId() {
         return path != null ? path : (image != null ? image.toString() : toString());
      }

      /**
       * Set the image for filling the shape.
       */
      @TernMethod
      public void setImage(Image image) {
         this.image = image;
         CoreTool.waitForImage(image);

         if(image != null) {
            iw = image.getWidth(null);
            ih = image.getHeight(null);
         }
      }

      /**
       * Get the image for filling the shape.
       */
      @TernMethod
      public Image getImage() {
         if(image == null && path != null) {
            try(InputStream input = getInputStream(path)) {
               ByteArrayOutputStream baos = new ByteArrayOutputStream();
               int cnt;
               byte[] buf = new byte[4096];

               if(input == null) {
                  LOG.error("Image file not found: " + path);
                  return null;
               }

               while((cnt = input.read(buf)) >= 0) {
                  baos.write(buf, 0, cnt);
               }

               setImage(CoreTool.getToolkit().createImage(baos.toByteArray()));
            }
            catch(Exception ex) {
               throw new RuntimeException(ex);
            }
         }

         return image;
      }

      private InputStream getInputStream(String path) throws IOException {
         File file = FileSystemService.getInstance().getFile(path);

         if(file.exists()) {
            return new FileInputStream(path);
         }

         InputStream input = GShape.class.getResourceAsStream(path);

         if(input != null) {
            return input;
         }

         return DataSpace.getDataSpace().getInputStream(null, path);
      }

      /**
       * Set whether to tile or resize the image.
       */
      @TernMethod
      public void setTile(boolean tile) {
         this.tile = tile;
      }

      /**
       * Check whether to tile or resize the image.
       */
      @TernMethod
      public boolean isTile() {
         return tile;
      }

      /**
       * Set if the graphics color should be applied on image.
       * The default is false.
       */
      @TernMethod
      public void setApplyColor(boolean color) {
         this.applyColor = color;
      }

      /**
       * Check if the graphics color should be applied on image.
       */
      @TernMethod
      public boolean isApplyColor() {
         return applyColor;
      }

      /**
       * Set whether image should be drawn in original size or fit into the size of the shape.
       */
      @TernMethod
      public void setApplySize(boolean applySize) {
         this.applySize = applySize;
      }

      /**
       * Check if image should be drawn in original size or fit into the size of the shape.
       */
      @TernMethod
      public boolean isApplySize() {
         return applySize;
      }

      /**
       * Set the color to ignore when applying color to image.
       */
      @TernMethod
      public void setIgnoredColor(Color color) {
         this.ignored = color;
      }

      /**
       * Get the color to ignore when applying color to image.
       */
      @TernMethod
      public Color getIgnoredColor() {
         return ignored;
      }

      /**
       * Set the image alignment relative to data point position. Default to CENTER.
       */
      @TernMethod
      public void setAlignment(Alignment alignment) {
         this.alignment = alignment;
      }

      /**
       * Get the image alignment relative to data point position.
       */
      @TernMethod
      public Alignment getAlignment() {
         return alignment;
      }

      @Override
      @TernMethod
      public double getMinSize() {
         getImage(); // make sure it's loaded
         return Math.min(10, Math.max(iw, ih));
      }

      @Override
      public Rectangle2D getBounds(Point2D pt, double radius) {
         return (Rectangle2D) getShape(pt.getX() - radius, pt.getY() - radius, radius * 2, radius * 2);
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

         if(tile) {
            return new Rectangle2D.Double(x, y, w, h);
         }

         Rectangle2D bounds = null;
         // center of shape, also the data point position.
         final double cx = x + w / 2;
         final double cy = y + h / 2;

         if(applySize) {
            double ow = w, oh = h;

            // keep aspect ratio
            if(iw != ih) {
               if(iw > ih) {
                  h = ih * w / iw;
                  y += (oh - h) / 2;
               }
               else {
                  w = iw * h / ih;
                  x += (ow - w) / 2;
               }
            }

            bounds = new Rectangle2D.Double(x, y, w, h);
         }
         else {
            bounds = new Rectangle2D.Double(cx - iw / 2.0, cy - ih / 2.0, iw, ih);
         }

         if(alignment == Alignment.TOP) {
            bounds.setRect(bounds.getX(), cy - bounds.getHeight(),
                           bounds.getWidth(), bounds.getHeight());
         }
         else if(alignment == Alignment.RIGHT) {
            bounds.setRect(cx - bounds.getWidth(), bounds.getY(),
                           bounds.getWidth(), bounds.getHeight());
         }

         return bounds;
      }

      @Override
      public void paint(Graphics2D g, Shape shape) {
         Image currentImage = getImage();

         if(currentImage == null) {
            return;
         }

         g = (Graphics2D) g.create();
         g.clip(shape);

         Rectangle2D bounds = shape.getBounds2D();
         boolean isTiled = this.tile;

         // only change image color if explicitly set and not ignored
         if(applyColor &&
            (ignored == null ||
             // ignore transparency since it may be set for subtle effects
             (g.getColor().getRGB() & 0xFFFFFF) != (ignored.getRGB() & 0xFFFFFF)))
         {
            currentImage = GTool.changeHue(currentImage, g.getColor());
         }

         // this is for legend which is better to draw a complete image than a
         // clip of the image. This condition is an educated guess and we may
         // need to make this explicit if there are reasonable cases that this
         // messes up.
         if(bounds.getWidth() < iw && bounds.getHeight() < ih && iw <= 32 && ih <= 32) {
            isTiled = false;
         }

         if(isTiled) {
            for(double y = bounds.getY(); y < bounds.getY() + bounds.getHeight(); y += ih) {
               for(double x = bounds.getX(); x < bounds.getX() + bounds.getWidth(); x += iw) {
                  GraphTool.drawImage(g, currentImage, x, y, iw, ih);
               }
            }
         }
         else {
            GraphTool.drawImage(g, currentImage, bounds.getX(), bounds.getY(),
                                bounds.getWidth(), bounds.getHeight());
         }

         g.dispose();
      }

      @Override
      public String getLegendId() {
         return "ImageShape:" + (path != null ? path : (image + ""));
      }

      @Override
      public int hashCode() {
         return Objects.hash(image, path, tile, applyColor, applySize, ignored, alignment);
      }

      @Override
      public String toString() {
         return "GShape.ImageShape" + System.identityHashCode(this) + "(" + getImageId() + ")";
      }

      @Override
      public boolean equals(Object obj) {
         if(!super.equals(obj)) {
            return false;
         }

         ImageShape shape = (ImageShape) obj;

         return Objects.equals(path, shape.path) && tile == shape.tile &&
            applyColor == shape.applyColor && applySize == shape.applySize &&
            Objects.equals(ignored, shape.ignored) && alignment == shape.alignment;
      }

      private Image image;
      private String path;
      private int iw;
      private int ih;
      private boolean tile = false;
      private boolean applyColor = false;
      private boolean applySize = true;
      private Color ignored = null;
      private Alignment alignment = Alignment.CENTER;
      private static final long serialVersionUID = -2388866808474085960L;
   }

   /**
    * Check if equals another objects.
    */
   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof GShape)) {
         return false;
      }

      GShape shape = (GShape) obj;

      if(!getClass().getName().equals(shape.getClass().getName())) {
         return false;
      }

      return fill == shape.fill;
   }

   @Override
   public int hashCode() {
      return getClass().hashCode();
   }

   private boolean fill = false;
   private boolean outline = false;
   private Color linecolor;
   private Color fillcolor;
   private int lineStyle = GraphConstants.THIN_LINE;
   private static final Logger LOG = LoggerFactory.getLogger(GShape.class);
}
