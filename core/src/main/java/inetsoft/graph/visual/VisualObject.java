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
package inetsoft.graph.visual;

import inetsoft.graph.Visualizable;
import inetsoft.graph.coord.Coordinate;

import java.awt.geom.Rectangle2D;

/**
 * A visual object is an object that has a visual representation on a graphic
 * output.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public abstract class VisualObject extends Visualizable implements PlotObject {
   /**
    * The gap between visual object and its text label.
    */
   public static final int TEXT_GAP = 2;

   /**
    * Transform the logic coordinate to chart coordinate.
    * @param coord the visual object's coordinate.
    */
   public abstract void transform(Coordinate coord);

   /**
    * Get the bounding box of the visual object in the graphics output.
    * @return the bounding box of the visual object in the graphics output.
    */
   @Override
   public abstract Rectangle2D getBounds();

   /**
    * Get the size that is unscaled by screen transformation.
    * @param pos the position (side) in the shape, e.g. GraphConstants.TOP.
    */
   @Override
   public double getUnscaledSize(int pos, Coordinate coord) {
      return 0;
   }

   /**
    * Check if this object should be kept inside plot area.
    */
   @Override
   public boolean isInPlot() {
      return true;
   }
}
