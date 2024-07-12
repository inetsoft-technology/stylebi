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
package inetsoft.report.internal;

import inetsoft.report.*;
import inetsoft.util.Encoder;
import inetsoft.util.Tool;

import java.awt.*;
import java.io.*;

/**
 * Paint shapes defined in PageLayout.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ShapePaintable extends BasePaintable {
   /**
    * Create an empty paintable. The shape must be set before it's used.
    */
   public ShapePaintable() {
      this(null);
   }

   /**
    * Create a paintable for a shape.
    */
   public ShapePaintable(PageLayout.Shape shape) {
      super(null);
      this.shape = shape;
   }

   /**
    * Get the shape in this paintable.
    */
   public PageLayout.Shape getShape() {
      return shape;
   }

   /**
    * Set the shape in this paintable.
    */
   public void setShape(PageLayout.Shape shape) {
      this.shape = shape;
   }

   /**
    * Set the printable area on a page.
    */
   public void setPrintableBounds(Rectangle box) {
      pageBox = box;
   }

   /**
    * Set the print offset of the shape.
    */
   public void setPrintOffset(Point off) {
      offset = off;
   }

   /**
    * Paint the element on to a page.
    */
   @Override
   public void paint(Graphics g) {
      if(shape == null) {
         return;
      }

      Shape oclip = g.getClip();
      Point trans = null;
      Rectangle vclip = getVirtualClip();
      boolean changed = false; // if clip or center changed

      // set clipping region
      if(vclip != null && pageBox != null) {
         Point adj = new Point();

         changed = true;
         trans = new Point(-vclip.x, -vclip.y);

         if(container instanceof FixedContainer) {
            trans.x += pageBox.x;
            trans.y += pageBox.y;
         }
         // from tabular sheet
         else if(container == null) {
            adj.x = pageBox.x;
            adj.y = pageBox.y;
         }

         if(offset != null) {
            trans.x += offset.x;
            trans.y += offset.y;
         }

         g.translate(adjX(trans.x, false), adjY(trans.y, false));

         int x1 = adjX(vclip.x + adj.x, false);
         int y1 = adjY(vclip.y + adj.y, false);
         int x2 = adjX(vclip.x + adj.x + vclip.width, true);
         int y2 = adjY(vclip.y + adj.y + vclip.height, true);

         g.clipRect(x1, y1, x2 - x1, y2 - y1);
      }
      // if vclip is not set, use pageBox as the container
      else if(pageBox != null) {
         changed = true;
         trans = new Point(pageBox.x, pageBox.y);

         if(offset != null) {
            trans.x += offset.x;
            trans.y += offset.y;
         }

         g.translate(adjX(trans.x, false), adjY(trans.y, false));
         g.clipRect(0, 0, adjX(pageBox.width, true),
		    adjY(pageBox.height, true));
      }

      // this avoids using DimGraphics in designer master layout mode
      if(g instanceof GraphicsWrapper) {
         g = ((GraphicsWrapper) g).getGraphics();
      }

      shape.paint(g);

      if(changed) {
         g.translate(-adjX(trans.x, false), -adjY(trans.y, false));
         g.setClip(oclip);
      }
   }

   /**
    * Return the bounds of this paintable area.
    * @return area bounds or null if element does not occupy an area.
    */
   @Override
   public Rectangle getBounds() {
      return getBounds(true);
   }

   /**
    * Return the bounds of this paintable area.
    * @param normalize true to transform the rectangle so width and height is
    * always positive.
    * @return area bounds or null if element does not occupy an area.
    */
   public Rectangle getBounds(boolean normalize) {
      if(shape == null) {
         return new Rectangle(0, 0, 0, 0);
      }

      Rectangle bounds = new Rectangle(shape.getBounds());
      Rectangle vclip = getVirtualClip();

      if(normalize) {
         // take care of negative width and height
         if(bounds.width < 0) {
            bounds.x += bounds.width;
            bounds.width = -bounds.width;
         }

         if(bounds.height < 0) {
            bounds.y += bounds.height;
            bounds.height = -bounds.height;
         }
      }

      if(vclip != null) {
         bounds.y -= vclip.y;
      }

      // should match the paint logic here
      if(pageBox != null &&
         ((container instanceof FixedContainer) || inContainer))
      {
         bounds.x += pageBox.x;
         bounds.y += pageBox.y;
      }

      // zero width/height is never treated interacting clip rect in jdk 1.4
      if(normalize) {
         bounds.width = Math.max(bounds.width, 1);
         bounds.height = Math.max(bounds.height, 1);
      }

      return bounds;
   }

   /**
    * Set the location of this paintable area. This is used internally
    * for small adjustments during printing.
    * @param loc new location for this paintable.
    */
   @Override
   public void setLocation(Point loc) {
      // @by larryl, the getLocation and setLocation is needed for section
      // keepTogether option in order to move the paintable on restoration
      if(pageBox != null) {
         pageBox = new Rectangle(loc.x, loc.y, pageBox.width, pageBox.height);
      }
   }

   /**
    * Get the location of this paintable.
    */
   @Override
   public Point getLocation() {
      return (pageBox != null) ? new Point(pageBox.x, pageBox.y) : new Point();
   }

   /**
    * Get the report element that this paintable area corresponds to.
    * @return report element.
    */
   @Override
   public ReportElement getElement() {
      return null;
   }

   /**
    * Set the container this shape is in. Shapes can be inside a page layout
    * or a tabular sheet. The container is only set if the shape is in a
    * fixed container (e.g. section band).
    */
   public void setContainer(FixedContainer container) {
      this.container = container;
   }

   /**
    * Get the container this shape belongs to.
    */
   public FixedContainer getContainer() {
      return container;
   }

   /**
    * Scale x value.
    */
   private int adjX(int x, boolean up) {
      double val = (x * shape.getXScale());

      return (int) (up ? Math.ceil(val) : val);
   }

   /**
    * Scale y value.
    */
   private int adjY(int y, boolean up) {
      double val = (y * shape.getYScale());

      return (int) (up ? Math.ceil(val) : val);
   }

   /**
    * Encode shape to string representation, which is standalone and small.
    */
   private String encodeShape(PageLayout.Shape shape) {
      try {
         byte[] arr = Tool.serialize(shape);
         return Encoder.encodeAsciiHex(arr);
      }
      catch(Exception ex) {
         return "";
      }
   }

   /**
    * Decode shape to string representation, which is standalone and small.
    */
   private PageLayout.Shape decodeShape(String shape) {
      try {
         byte[] bytes = Encoder.decodeAsciiHex(shape);
         return (PageLayout.Shape)
            new ObjectInputStream(new ByteArrayInputStream(bytes)).readObject();
      }
      catch(Exception ex) {
         return null;
      }
   }

   private void writeObject(ObjectOutputStream stream) throws IOException {
      stream.defaultWriteObject();
      stream.writeBoolean(container != null);
   }

   private void readObject(java.io.ObjectInputStream s)
      throws ClassNotFoundException, java.io.IOException
   {
      s.defaultReadObject();

      boolean fixed = s.readBoolean();

      if(fixed) {
         container = new FixedContainer(null);
      }
   }

   private Point offset; // bounds offset
   private PageLayout.Shape shape;
   private Rectangle pageBox; // printable bounds on a page
   private transient FixedContainer container = null; // container shape is in
   private boolean inContainer = false;
}
