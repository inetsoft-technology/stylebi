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
package inetsoft.graph.internal;

import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.geo.GeoShape;

import java.awt.*;
import java.io.Serializable;

/**
 * This interface mark a shape that is created from positions on a coordinate.
 * It is supported by polygon element to get shapes tied to coordinate space.
 *
 * @version 10.1
 * @author InetSoft Technology Corp
 */
public interface PolyShape extends Serializable {
   /**
    * Get the shape on this coordinate.
    */
   Shape getShape(Coordinate coord);

   /**
    * Get the geo shape for this shape
    */
   GeoShape getShape();
}
