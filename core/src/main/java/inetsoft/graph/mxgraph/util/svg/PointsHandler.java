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
package inetsoft.graph.mxgraph.util.svg;

/**
 * This interface must be implemented and then registred as the
 * handler of a <code>PointsParser</code> instance in order to be
 * notified of parsing events.
 *
 * @author <a href="mailto:stephane@hillion.org">Stephane Hillion</a>
 */
public interface PointsHandler {

   /**
    * Invoked when the points attribute starts.
    *
    * @throws ParseException if an error occured while processing the
    * points
    */
   void startPoints() throws ParseException;

   /**
    * Invoked when a point has been parsed.
    *
    * @param x the x coordinate of the point
    * @param y the y coordinate of the point
    *
    * @throws ParseException if an error occured while processing the
    * points
    */
   void point(float x, float y) throws ParseException;

   /**
    * Invoked when the points attribute ends.
    *
    * @throws ParseException if an error occured while processing the
    * points
    */
   void endPoints() throws ParseException;
}
