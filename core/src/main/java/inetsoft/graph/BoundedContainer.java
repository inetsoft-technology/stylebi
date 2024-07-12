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
package inetsoft.graph;

import inetsoft.graph.internal.DimensionD;

import java.awt.geom.*;

/**
 * A visual container that is at fixed position and size.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class BoundedContainer extends VContainer {
   /**
    * Gets the position of this component in the form of a point specifying the
    * visual's bottom-left corner. The position will be relative to the whole
    * chart's coordinate space.
    */
   public Point2D getPosition() {
      return pos;
   }

   /**
    * Set the position of the container in the chart's coordinate space.
    */
   public void setPosition(Point2D pos) {
      this.pos = pos;
   }

   /**
    * Returns the size of this container.
    */
   public Dimension2D getSize() {
      return size;
   }

   /**
    * Set the size of this container.
    */
   public void setSize(Dimension2D size) {
      this.size = size;
   }

   /**
    * Moves and resizes this visual. The new location of the bottom-left
    * corner is specified by x and y, and the new size is specified by width
    * and height.
    * @param x the new x of this visual.
    * @param y the new y of this visual.
    * @param width the new width of this visual.
    * @param height the new height of this visual.
    */
   public void setBounds(double x, double y, double width, double height) {
      pos = new Point2D.Double(x, y);
      size = new DimensionD(width, height);
   }

   /**
    * Gets the bounds of this visual in the form of a Rectangle2D object.
    * The bounds specify this visual's width, height, and location relative to
    * the whole graph.
    */
   @Override
   public Rectangle2D getBounds() {
      return new Rectangle2D.Double(pos.getX(), pos.getY(),
                                    size.getWidth(), size.getHeight());
   }

   private Point2D pos = new Point2D.Double(0, 0); // the position
   private Dimension2D size = new DimensionD(0, 0); // the size
}
