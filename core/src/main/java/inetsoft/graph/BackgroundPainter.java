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

import inetsoft.graph.coord.GeoCoord;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.Serializable;

/**
 * This interface defines the API for drawing dynamic background in a plot area.

 * @version 13.5
 * @author InetSoft Technology Corp
 */
public interface BackgroundPainter extends Cloneable, Serializable {
   /**
    * Get the image used for drawing background, including padding.
    */
   Image getImage(VGraph graph) throws IOException;

   /**
    * Paint the plot background for graph.
    */
   void paint(Graphics2D g, VGraph graph) throws IOException;

   /**
    * Get the amount of padding to add to the sides.
    * @return percentage (in fraction) of the width/height to add to each side, constrained
    * by the map API limit.
    */
   default double getPadding(GeoCoord coord) {
      // avoid padding that will cause lat/lon to be out of range. (54231)
      Rectangle2D bbox = coord.getBBox();
      double padding = Math.min(1 - bbox.getWidth() / 360, 1 - bbox.getHeight() / 180);
      return Math.min(0.5, Math.max(0, padding));
   }

   /**
    * Check and adjust latitude/longitude range if necessary.
    */
   default void prepareCoordinate(GeoCoord coord, int width, int height) {
   }
}
