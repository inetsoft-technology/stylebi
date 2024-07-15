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
package inetsoft.report.internal;

import inetsoft.util.DataSerializable;
import inetsoft.util.XMLSerializable;

import java.awt.*;
import java.io.Serializable;

/**
 * The Region interface provides operations for objects that represent some
 * form of geometric shape region.
 *
 * @version 8.0, 9/22/2005
 * @author InetSoft Technology Corp
 */
public interface Region extends XMLSerializable, DataSerializable, Serializable {
   /**
    * Get region name.
    */
   String getName();

   /**
    * Get the class name.
    */
   String getClassName();

   /**
    * Gets the bounding box of this region.
    */
   Rectangle getBounds();

   /**
    * Create the corresponding {@link Shape}
    */
   Shape createShape();

   /**
    * Draw region border on the graphics.
    *
    * @param g the graphics.
    */
   void drawBorder(Graphics g);

   /**
    * Draw region border with the specified color on the graphics.
    *
    * @param g the graphics.
    * @param color the specified color.
    */
   void drawBorder(Graphics g, Color color);

   /**
    * Draw region border with the specified line on the graphics.
    *
    * @param g the graphics.
    * @param style the specified line style.
    */
   void drawBorder(Graphics g, int style);

   /**
    * Draw region border with the specified on the graphics.
    *
    * @param g the graphics.
    * @param color the specified color.
    * @param style the specified line style.
    */
   void drawBorder(Graphics g, Color color, int style);

   /**
    * Tests if a specified position is inside the boundary of the region.
    *
    * @param x the x coordinate of position.
    * @param y the x coordinate of position.
    */
   boolean contains(float x, float y);

   /**
    * Tests if a specified rectangle is intersects the boundary of the region.
    *
    * @param rectangle the specified rectangle.
    */
   boolean intersects(Rectangle rectangle);
}
