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
package inetsoft.report.io.viewsheet.pdf;

import inetsoft.graph.internal.GTool;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;

/**
 * LinkArea stores the linkable area and the bounds, it is used for pdf export.
 */
public class LinkArea {
   /**
    * Create a link area.
    */
   public LinkArea(Shape shape, AffineTransform trans, double offsetx,
                   double offsety, float pageH)
   {
      this.shape = shape;
      this.trans = trans;
      this.offsetx = offsetx;
      this.offsety = offsety;
      this.pageH = pageH;
   }

   /**
    * Get the link area.
    */
   private Object getLinkArea() {
      if(shape instanceof Rectangle2D) {
         return getBounds();
      }

      Rectangle2D rect2d = shape.getBounds();
      rect2d = new Rectangle2D.Double(rect2d.getX(), rect2d.getY(),
         rect2d.getWidth() == 0 ? UNIT : rect2d.getWidth(),
         rect2d.getHeight() == 0 ? UNIT : rect2d.getHeight());
      ArrayList<Rectangle2D> list = new ArrayList<>();
      double x = rect2d.getX();
      double y = rect2d.getY();

      for(double i = x; i < x + rect2d.getWidth(); i += UNIT) {
         for(double j = y; j < y + rect2d.getHeight(); j+= UNIT) {
            Rectangle2D rect = new Rectangle2D.Double(i, j, UNIT, UNIT);

            if(shape.contains(rect)) {
               rect = GTool.transform(rect, trans);
               rect = rect.getBounds();
               rect = new Rectangle2D.Double(offsetx + rect.getX(),
                  offsety + rect.getY(), rect.getWidth(), rect.getHeight());
               list.add(rect);
            }
         }
      }

      return list;
   }

   /**
    * Get the bounds of the area.
    */
   public Rectangle2D getBounds() {
      Rectangle2D rect2d = shape.getBounds();
      rect2d = new Rectangle2D.Double(rect2d.getX(), rect2d.getY(),
         rect2d.getWidth() == 0 ? 1 : rect2d.getWidth(),
         rect2d.getHeight() == 0 ? 1 : rect2d.getHeight());
      rect2d = GTool.transform(rect2d, trans);
      Rectangle rect = rect2d.getBounds();

      return new Rectangle2D.Double(offsetx + rect.x, offsety + rect.y,
         rect.width, rect.height);
   }

   /**
    * Get the rectangle array.
    */
   public String getRectArray() {
      double[] coord = getLowerLeftUpperRightPoints(getBounds());
      return "/Rect [ " + coord[0] + " " + coord[1] + " " + coord[2] + " "
         + coord[3] + " ]";
   }

   /**
    * Get the lower left and the upper right points.
    */
   private double[] getLowerLeftUpperRightPoints(Rectangle2D rect) {
      double llx = rect.getX();
      double lly = pageH - rect.getY() - rect.getHeight();
      double urx = llx + rect.getWidth();
      double ury = lly + rect.getHeight();

      return new double[] {llx, lly, urx, ury};
   }

   private Shape shape;
   private AffineTransform trans;
   private double offsetx;
   private double offsety;
   private float pageH;
   private String whiteSpace = " ";
   private int UNIT = 1;
}
