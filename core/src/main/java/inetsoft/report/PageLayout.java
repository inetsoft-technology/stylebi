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
package inetsoft.report;

import inetsoft.graph.internal.GTool;
import inetsoft.report.internal.*;
import inetsoft.sree.SreeEnv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.geom.Point2D;
import java.io.Serializable;

/**
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class PageLayout implements Serializable, Cloneable {
   /**
    * Create an empty page layout.
    */
   public PageLayout() {
   }

   /**
    * Get the shapes to draw on this page.
    */
   public PageLayout.Shape[] getShapes() {
      return shapes;
   }

   /**
    * Set the shapes to draw on this page. Shape classes are defined in
    * this class, e.g., PageLayout.Line.
    */
   public void setShapes(PageLayout.Shape[] shapes) {
      this.shapes = shapes;
   }

   /**
    * Paint the shapes on a page.
    */
   public void print(StylePage pg) {
      if(shapes == null) {
         return;
      }

      for(int i = 0; i < shapes.length; i++) {
         pg.addPaintable(new ShapePaintable(shapes[i]));
      }
   }

   /**
    * Check if this page layout is defined.
    */
   public boolean isEmpty() {
      return shapes.length == 0;
   }

   @Override
   public Object clone() {
      PageLayout obj = new PageLayout();

      if(shapes != null) {
         PageLayout.Shape[] arr = shapes.clone();

	      for(int i = 0; i < arr.length; i++) {
            arr[i] = (PageLayout.Shape) arr[i].clone();
         }

         obj.setShapes(arr);
      }

      return obj;
   }

   /**
    * This class defines a shape on a page.
    */
   public abstract static class Shape implements Cloneable, Serializable {
      /**
       * Set the color of this shape.
       */
      public void setColor(Color color) {
         this.color = color;
      }

      /**
       * Get the color of this shape.
       */
      public Color getColor() {
         return color;
      }

      /**
       * Set the line style of this shape.
       */
      public void setStyle(int style) {
         this.style = style;
      }

      /**
       * Get the line style of this shape.
       */
      public int getStyle() {
         return style;
      }

      /**
       * Scale the shape.
       * @param xs x scale 0 to 1.
       * @param ys y scale 0 to 1.
       */
      public void setScale(double xs, double ys) {
         this.xs = xs;
         this.ys = ys;
      }

      /**
       * Get the X direction scaling factor.
       */
      public double getXScale() {
         return xs;
      }

      /**
       * Get the Y direction scaling factor.
       */
      public double getYScale() {
         return ys;
      }

      /**
       * Get the bounding box of this paintable.
       */
      public abstract java.awt.Rectangle getBounds();

      /**
       * Move the shape by the specified x/y amount.
       */
      public abstract void move(double x, double y);

      /**
       * Check if the point falls inside the shape.
       */
      public abstract boolean contains(int x, int y);

      /**
       * Check if a rectangle is completely contained in the shape.
       */
      public boolean contains(java.awt.Rectangle rect) {
         return contains(rect.x, rect.y) &&
            contains(rect.x + rect.width - 1, rect.y) &&
            contains(rect.x, rect.y + rect.height - 1) &&
            contains(rect.x + rect.width - 1, rect.y + rect.height - 1);
      }

      /**
       * Paint this shape.
       */
      public abstract void paint(Graphics g);

      /**
       * Clone a shape.
       */
      @Override
      public Object clone() {
         try {
            return super.clone();
         }
         catch(Exception ex) {
            LOG.error("Failed to clone layout", ex);
         }

         return null;
      }

      /**
       * Copy the attributes of the shape into this shape.
       */
      public void copy(Shape shape) {
         color = shape.color;
         style = shape.style;
         xs = shape.xs;
         ys = shape.ys;
      }

		/**
		 * Set zindex for the shape.
		 * Just for the shape converted by a vs shape which applied the
		 * vs printlayout to make sure the converted shape can be paintabled
		 * with same hierarchy as source shape in the target vs.
		 */
		public void setZIndex(int zindex) {
			this.zindex = zindex;
		}

		/**
		 * Return the zindex of the shape.
		 */
		public int getZIndex() {
			return zindex;
		}

		private int zindex; // just for previewing vs printlayout.
      private Color color = Color.black; // shape color
      private int style = StyleConstants.THIN_LINE; // line style
      double xs = 1, ys = 1;
   }

   /**
    * A line shape.
    */
   public static class Line extends PageLayout.Shape {
      public static final int START = 0;
      public static final int END = 1;
      public static final int ALL = 2;

      public Line() {
      }

      /**
       * Create a line shape. The x and y coordinates are specified as
       * points relative to the left-upper cornor of the page.
       * All position and length are specified as points (1/72 inch).
       */
      public Line(double x1, double y1, double x2, double y2) {
         this.x1 = x1;
         this.y1 = y1;
         this.x2 = x2;
         this.y2 = y2;
      }

      /**
       * Set the starting point x position.
       */
      public void setX1(double x) {
         this.x1 = x;
      }

      /**
       * Get the starting point x position.
       */
      public double getX1() {
         return x1;
      }

      /**
       * Set the starting point y position.
       */
      public void setY1(double y) {
         this.y1 = y;
      }

      /**
       * Get the starting point y position.
       */
      public double getY1() {
         return y1;
      }

      /**
       * Set the ending point x position.
       */
      public void setX2(double x) {
         this.x2 = x;
      }

      /**
       * Get the ending point x position.
       */
      public double getX2() {
         return x2;
      }

      /**
       * Set the ending point y position.
       */
      public void setY2(double y) {
         this.y2 = y;
      }

      /**
       * Get the ending point y position.
       */
      public double getY2() {
         return y2;
      }

      /**
       * Paint this shape.
       */
      @Override
      public void paint(Graphics g) {
         Color oc = g.getColor();

         g.setColor(getColor());
         Common.drawLine(g, (float) (getX1() * xs), (float) (getY1() * ys),
            (float) (getX2() * xs), (float) (getY2() * ys), getStyle());

         if(isArrow) {
            Graphics2D g2 = (Graphics2D) g.create();
            Point2D p1 = new Point2D.Double(x1, y1);
            Point2D p2 = new Point2D.Double(x2, y2);

            g2.setStroke(GTool.getStroke(getStyle()));

            if(location == START) {
               GTool.drawArrow(g2, p2, p1, 7);
            }
            else if(location == END) {
               GTool.drawArrow(g2, p1, p2, 7);
            }
            else if(location == ALL) {
               GTool.drawArrow(g2, p2, p1, 7);
               GTool.drawArrow(g2, p1, p2, 7);
            }

            g2.dispose();
         }

         g.setColor(oc);
      }

      /**
       * Get the bounding box of this paintable.
       */
      @Override
      public java.awt.Rectangle getBounds() {
         return new java.awt.Rectangle((int) x1, (int) y1,
            (int) (x2 - x1), (int) (y2 - y1));
      }

      /**
       * Move the shape by the specified x/y amount.
       */
      @Override
      public void move(double x, double y) {
         x1 += x;
         y1 += y;
         x2 += x;
         y2 += y;
      }

      /*
       * Set the arrow of this line.
       */
      public void setArrow(boolean isArrow) {
         this.isArrow = isArrow;
      }

      /*
       * Check the arrow of this line.
       */
      public boolean isArrow() {
         return isArrow;
      }

      /*
       * Set the arrow's location of this line.
       */
      public void setArrowLocation(int location) {
         this.location = location;
      }

      /*
       * Get the arrow's location of this line.
       */
      public int getArrowLocation() {
         return location;
      }

      /**
       * Check if the point falls inside the shape.
       */
      @Override
      public boolean contains(int x, int y) {
         double X1 = x1, X2 = x2, Y1 = y1, Y2 = y2;

         X2 -= X1;
         Y2 -= Y1;
         x -= X1;
         y -= Y1;
         double dotprod = x * X2 + y * Y2;
         double projlenSq = dotprod * dotprod / (X2 * X2 + Y2 * Y2);

         return x * x + y * y - projlenSq < 3;
      }

      /**
       * Copy the attributes of the shape into this shape.
       */
      @Override
      public void copy(Shape shape) {
         super.copy(shape);

         Line line = (Line) shape;

         x1 = line.x1;
         x2 = line.x2;
         y1 = line.y1;
         y2 = line.y2;
      }

      private double x1, x2, y1, y2;
      private boolean isArrow;
      private int location;
   }

   /**
    * A rectangle shape.
    */
   public static class Rectangle extends PageLayout.Shape {
      public Rectangle() {
      }

      /**
       * Create a rectangle shape. The x and y coordinates are specified as
       * points relative to the left-upper cornor of the page. The width
       * and height are specified as points.
       * All position and length are specified as points (1/72 inch).
       */
      public Rectangle(double x, double y, double width, double height) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
      }

      /**
       * Set the left edge x position.
       */
      public void setX(double x) {
         this.x = x;
      }

      /**
       * Get the left edge x position.
       */
      public double getX() {
         return x;
      }

      /**
       * Set the top edge y position.
       */
      public void setY(double y) {
         this.y = y;
      }

      /**
       * Get the top edge y position.
       */
      public double getY() {
         return y;
      }

      /**
       * Set the width.
       */
      public void setWidth(double width) {
         this.width = width;
      }

      /**
       * Get the width.
       */
      public double getWidth() {
         return width;
      }

      /**
       * Set the height.
       */
      public void setHeight(double height) {
         this.height = height;
      }

      /**
       * Get the height.
       */
      public double getHeight() {
         return height;
      }

      /**
       * Set the fill color of this area.
       */
      public void setFillColor(Paint fill) {
         this.fill = fill;
      }

      /**
       * Get the fill color of this area.
       */
      public Paint getFillColor() {
         return fill;
      }

      /**
       * Paint this shape.
       */
      @Override
      public void paint(Graphics g) {
         Color oc = g.getColor();

         if(fill != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate((int) (getX() + xs), (int) (getY() * ys));
            g2.setPaint(fill);
            g2.fillRect(0, 0, (int) (getWidth() * xs), (int) (getHeight() * ys));
            g2.dispose();
         }

         g.setColor(getColor());
         Common.drawRect(g, (float) (getX() * xs), (float) (getY() * ys),
            (float) (getWidth() * xs), (float) (getHeight() * ys), getStyle());
         g.setColor(oc);
      }

      /**
       * Get the bounding box of this paintable.
       */
      @Override
      public java.awt.Rectangle getBounds() {
         return new java.awt.Rectangle((int) x, (int) y, (int) width,
            (int) height);
      }

      /**
       * Move the shape by the specified x/y amount.
       */
      @Override
      public void move(double x, double y) {
         this.x += x;
         this.y += y;
      }

      /**
       * Check if the point falls inside the shape.
       */
      @Override
      public boolean contains(int x, int y) {
         return getBounds().contains(x, y);
      }

      /**
       * Copy the attributes of the shape into this shape.
       */
      @Override
      public void copy(Shape shape) {
         super.copy(shape);

         Rectangle rect = (Rectangle) shape;

         x = rect.x;
         y = rect.y;
         width = rect.width;
         height = rect.height;
         fill = rect.fill;
      }

      private double x, y, width, height;
      private Paint fill = null;
   }

   /**
    * An information shape.
    */
   public static class InfoShape extends PageLayout.Shape {
      public InfoShape() {
         super();
      }

      /**
       * Create an info shape.
       */
      public InfoShape(String info, StylePage page) {
         this.info = info;
         calcBounds(page);
      }

      /**
       * Get the left edge x position.
       */
      public double getX() {
         return bounds == null ? -1 : bounds.x;
      }

      /**
       * Get the top edge y position.
       */
      public double getY() {
         return bounds == null ? -1 : bounds.y;
      }

      /**
       * Get the width.
       */
      public double getWidth() {
         return bounds == null ? -1 : bounds.width;
      }

      /**
       * Get the height.
       */
      public double getHeight() {
         return bounds == null ? -1 : bounds.height;
      }

      /**
      * Calculate bounds.
      */
      private void calcBounds(StylePage page) {
         FontMetrics fm = Common.getFontMetrics(font);
         int height = (int) Common.getHeight(font);
         int width = (int) Common.stringWidth(info, font, fm);
         Dimension size = page.getPageDimension();
         int delta = 3;
         String onheader = SreeEnv.getProperty("warning.position.onheader");
         int ystart = size.height - height - 15;

         if("true".equals(onheader)) {
            ystart = 50;
         }

         OUTER:
         for(int xi = size.width - width - 15; xi >= 10; xi -= delta) {
            for(int yi = ystart;
               yi >= 10 && yi >= ystart - 35; yi -= delta)
            {
               // athough bounds caculated here can make sure
               // warnings won't overlap in report, however when
               // exported to rtf they still overlap a little, so here
               // temporarily enlarge the width to caculate correct x and y
               int adjust = 10;
               java.awt.Rectangle bounds =
                  new java.awt.Rectangle(xi, yi, width + adjust, height);
               if(!isOverlapped(bounds, page)) {
                  bounds.width -= adjust;
                  this.bounds = bounds;
                  break OUTER;
               }
            }
         }
      }

      /**
       * Check if is overlapped with the others.
       */
      private boolean isOverlapped(java.awt.Rectangle bounds, StylePage page) {
         int count = page.getPaintableCount();

         // do not check too many paintables
         for(int i = count - 1; i >= 0 && i >= count - 1000; i--) {
            Paintable pt = page.getPaintable(i);

            if(pt instanceof ShapePaintable &&
               !(((ShapePaintable) pt).getShape() instanceof InfoShape))
            {
               continue;
            }

            java.awt.Rectangle bounds2 = pt.getBounds();

            if(bounds.intersects(bounds2)) {
               return true;
            }
         }

         return false;
      }

      /**
       * Paint this shape.
       */
      @Override
      public void paint(Graphics g) {
         if(bounds == null) {
            return;
         }

         Color ocolor = g.getColor();
         Font ofont = g.getFont();
         g.setColor(Color.red);
         g.setFont(font);
         FontMetrics fm = Common.getFontMetrics(font);
         Common.drawString(g, info, (float) (xs * bounds.x),
                           (float) (ys * bounds.y + Common.getAscent(fm)));
         g.setColor(ocolor);
         g.setFont(ofont);
      }

      /**
       * Get the bounding box of this paintable.
       */
      @Override
      public java.awt.Rectangle getBounds() {
         return bounds;
      }

      /**
       * Set the bounds of current paintable.
       */
      public void setBounds(java.awt.Rectangle bounds) {
         this.bounds = bounds;
      }

      /**
       * Move the shape by the specified x/y amount.
       */
      @Override
      public void move(double x, double y) {
         if(bounds == null) {
            return;
         }

         bounds.x += x;
         bounds.y += y;
      }

      /**
       * Check if the point falls inside the shape.
       */
      @Override
      public boolean contains(int x, int y) {
         if(bounds == null) {
            return false;
         }

         return bounds.contains(x, y);
      }

      /**
       * Copy the attributes of the shape into this shape.
       */
      @Override
      public void copy(Shape shape) {
         super.copy(shape);
         InfoShape ishape = (InfoShape) shape;
         info = ishape.info;
         bounds = ishape.bounds;
      }

      /**
       * Get warning info.
       */
      public String getInfo() {
         return this.info;
      }

      /**
       * Set warning infomation.
       */
      public void setInfo(String info) {
         this.info = info;
      }

      /**
       * Get font.
       */
      public static Font getFont() {
         return font;
      }

      /**
       * Get color of warning message.
       *
       */
      public static Color getWarningColor() {
         return warningColor;
      }

      private static final Font font = new StyleFont(StyleFont.DEFAULT_FONT_FAMILY, 0, 10);
      private static final Color warningColor = Color.RED;
      private String info;
      private java.awt.Rectangle bounds;
   }

   /**
    * An information text.
    */
   public static class InfoText extends TextElementDef {
      public InfoText() {
         super();
      }

      /**
       * Create an info shape.
       */
      public InfoText(String info, StylePage page) {
         this();
         delegate = new InfoShape(info, page);
      }

      /**
       * Get the left edge x position.
       */
      public double getX() {
         return delegate.getX();
      }

      /**
       * Get the top edge y position.
       */
      public double getY() {
         return delegate.getY();
      }

      /**
       * Get the width.
       */
      public double getWidth() {
         return delegate.getWidth();
      }

      /**
       * Get the height.
       */
      public double getHeight() {
         return delegate.getHeight();
      }

      /**
       * Get the bounding box of this paintable.
       */
      public java.awt.Rectangle getBounds() {
         return delegate.getBounds();
      }

      /**
       * Set the bounds of current paintable.
       */
      public void setBounds(java.awt.Rectangle bounds) {
         delegate.setBounds(bounds);
      }

      /**
       * Move the shape by the specified x/y amount.
       */
      public void move(double x, double y) {
         delegate.move(x, y);
      }

      /**
       * Check if the point falls inside the shape.
       */
      public boolean contains(int x, int y) {
         return delegate.contains(x, y);
      }

      /**
       * Get warning info.
       */
      public String getInfo() {
         return delegate.getInfo();
      }

      /**
       * Set warning infomation.
       */
      public void setInfo(String info) {
         delegate.setInfo(info);
      }

      /**
       * Get font.
       */
      @Override
      public Font getFont() {
         return InfoShape.getFont();
      }

      /**
       * Get foreground color.
       */
      @Override
      public Color getForeground() {
         return InfoShape.getWarningColor();
      }

      public TextPaintable getPaintable() {
         return new TextPaintable(getInfo(), (float) getX(), (float) getY(),
                                  (float) getWidth(), this, false);
      }

      private InfoShape delegate = new InfoShape();
   }

   /**
    * An oval or circle shape.
    */
   public static class Oval extends PageLayout.Shape {
      public Oval() {
      }

      /**
       * Create a oval shape. The x and y coordinates are specified as
       * points relative to the left-upper cornor of the page. The width
       * and height are specified as points.
       * All position and length are specified as points (1/72 inch).
       */
      public Oval(double x, double y, double width, double height) {
         this.x = x;
         this.y = y;
         this.width = width;
         this.height = height;
      }

      /**
       * Set the left edge x position.
       */
      public void setX(double x) {
         this.x = x;
      }

      /**
       * Get the left edge x position.
       */
      public double getX() {
         return x;
      }

      /**
       * Set the top edge y position.
       */
      public void setY(double y) {
         this.y = y;
      }

      /**
       * Get the top edge y position.
       */
      public double getY() {
         return y;
      }

      /**
       * Set the width.
       */
      public void setWidth(double width) {
         this.width = width;
      }

      /**
       * Get the width.
       */
      public double getWidth() {
         return width;
      }

      /**
       * Set the height.
       */
      public void setHeight(double height) {
         this.height = height;
      }

      /**
       * Get the height.
       */
      public double getHeight() {
         return height;
      }

      /**
       * Set the fill color of this area.
       */
      public void setFillColor(Paint fill) {
         this.fill = fill;
      }

      /**
       * Get the fill color of this area.
       */
      public Paint getFillColor() {
         return fill;
      }

      /**
       * Paint this shape.
       */
      @Override
      public void paint(Graphics g) {
         Color oc = g.getColor();

         if(fill != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.translate((int) (getX() + xs), (int) (getY() * ys));
            g2.setPaint(fill);
            g2.fillOval(0, 0, (int) (getWidth() * xs), (int) (getHeight() * ys));
            g2.dispose();
         }

         g.setColor(getColor());
         g.drawOval((int) (getX() * xs), (int) (getY() * ys),
            (int) (getWidth() * xs), (int) (getHeight() * ys));
         g.setColor(oc);
      }

      /**
       * Get the bounding box of this paintable.
       */
      @Override
      public java.awt.Rectangle getBounds() {
         return new java.awt.Rectangle((int) x, (int) y, (int) width,
            (int) height);
      }

      /**
       * Move the shape by the specified x/y amount.
       */
      @Override
      public void move(double x, double y) {
         this.x += x;
         this.y += y;
      }

      /**
       * Check if the point falls inside the shape.
       */
      @Override
      public boolean contains(int x, int y) {
         double ellw = getWidth();

         if(ellw <= 0.0) {
            return false;
         }

         double normx = (x - getX()) / ellw - 0.5;
         double ellh = getHeight();

         if(ellh <= 0.0) {
            return false;
         }

         double normy = (y - getY()) / ellh - 0.5;

         return (normx * normx + normy * normy) < 0.25;
      }

      /**
       * Copy the attributes of the shape into this shape.
       */
      @Override
      public void copy(Shape shape) {
         super.copy(shape);

         Oval oval = (Oval) shape;

         x = oval.x;
         y = oval.y;
         width = oval.width;
         height = oval.height;
         fill = oval.fill;
      }

      private double x, y, width, height;
      private Paint fill = null;
   }

   private PageLayout.Shape[] shapes = {};

   private static final Logger LOG = LoggerFactory.getLogger(PageLayout.class);
}
