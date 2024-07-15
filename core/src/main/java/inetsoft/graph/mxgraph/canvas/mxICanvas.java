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
package inetsoft.graph.mxgraph.canvas;

import inetsoft.graph.mxgraph.util.mxPoint;
import inetsoft.graph.mxgraph.view.mxCellState;

/**
 * Defines the requirements for a canvas that paints the vertices and edges of
 * a graph.
 */
public interface mxICanvas {
   /**
    * Sets the translation for the following drawing requests.
    */
   void setTranslate(double x, double y);

   /**
    * Returns the current translation.
    *
    * @return Returns the current translation.
    */
   mxPoint getTranslate();

   /**
    * Returns the scale.
    */
   double getScale();

   /**
    * Sets the scale for the following drawing requests.
    */
   void setScale(double scale);

   /**
    * Draws the given cell.
    *
    * @param state State of the cell to be painted.
    *
    * @return Object that represents the cell.
    */
   Object drawCell(mxCellState state);

   /**
    * Draws the given label.
    *
    * @param text  String that represents the label.
    * @param state State of the cell whose label is to be painted.
    * @param html  Specifies if the label contains HTML markup.
    *
    * @return Object that represents the label.
    */
   Object drawLabel(String text, mxCellState state, boolean html);

}
