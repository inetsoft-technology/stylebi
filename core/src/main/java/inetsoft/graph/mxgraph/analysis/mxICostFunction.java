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
package inetsoft.graph.mxgraph.analysis;

import inetsoft.graph.mxgraph.view.mxCellState;

/**
 * The cost function takes a cell and returns it's cost as a double. Two typical
 * examples of cost functions are the euclidian length of edges or a constant
 * number for each edge. To use one of the built-in cost functions, use either
 * <code>new mxDistanceCostFunction(graph)</code> or
 * <code>new mxConstantCostFunction(1)</code>.
 */
public interface mxICostFunction {

   /**
    * Evaluates the cost of the given cell state.
    *
    * @param state The cell state to be evaluated
    *
    * @return Returns the cost to traverse the given cell state.
    */
   double getCost(mxCellState state);

}
