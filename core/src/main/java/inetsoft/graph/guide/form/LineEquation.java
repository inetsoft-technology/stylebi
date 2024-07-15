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
package inetsoft.graph.guide.form;

import java.awt.geom.Point2D;
import java.io.Serializable;

/**
 * A line equation defines how a line (or curve) is drawn from a given set of
 * points. This is used to fit a trend line to a set of data points.
 *
 * @hidden
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public interface LineEquation extends Serializable {
   /**
    * Calculate the points on the line.
    * @param points the points to fit the line.
    * @return the points on the equation.
    */
   public Point2D[] calculate(Point2D... points);

   public void setXmax(double xmax);
}
