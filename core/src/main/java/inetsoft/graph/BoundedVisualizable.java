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
package inetsoft.graph;

import inetsoft.graph.internal.DimensionD;

import java.awt.geom.*;

/**
 * A visualizable that is at fixed position and size.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class BoundedVisualizable extends Visualizable {
   /**
    * Gets the position of this component in the form of a point specifying the
    * visual's bottom-left corner. The position will be relative to the whole
    * chart's coordinate space.
    */
   public Point2D getPosition() {
      return new Point2D.Double(x, y);
   }

   /**
    * Set the position of this visualizable in the chart's coordinate space.
    */
   public void setPosition(Point2D pos) {
      this.x = (float) pos.getX();
      this.y = (float) pos.getY();
   }

   /**
    * Returns the size of this visual.
    */
   public Dimension2D getSize() {
      return new DimensionD(w, h);
   }

   /**
    * Set the size of this visual.
    */
   public void setSize(Dimension2D size) {
      this.w = (float) size.getWidth();
      this.h = (float) size.getHeight();
   }

   /**
    * Set the position and size.
    */
   public void setBounds(double x, double y, double width, double height) {
      this.x = (float) x;
      this.y = (float) y;
      this.w = (float) width;
      this.h = (float) height;
   }

   /**
    * Set the position and size.
    */
   public void setBounds(Rectangle2D bounds) {
      setBounds(bounds.getX(), bounds.getY(), bounds.getWidth(),
                bounds.getHeight());
   }

   /**
    * Gets the bounds of this visual in the form of a Rectangle2D object.
    * The bounds specify this visual's width, height, and location relative to
    * the whole graph.
    */
   @Override
   public Rectangle2D getBounds() {
      return new Rectangle2D.Double(x, y, w, h);
   }

   private float x, y, w, h;
}
