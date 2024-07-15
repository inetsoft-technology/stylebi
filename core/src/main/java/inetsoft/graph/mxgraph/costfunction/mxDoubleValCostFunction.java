/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.mxgraph.costfunction;

import inetsoft.graph.mxgraph.view.mxCellState;
import inetsoft.graph.mxgraph.view.mxGraph;

/**
 * A cost function that assumes that edge value is of type "double" or "String" and returns that value. Default edge weight is 1.0 (if no double value can be retrieved)
 */
public class mxDoubleValCostFunction extends mxCostFunction {
   public double getCost(mxCellState state)
   {
      //assumed future parameters
      if(state == null || state.getView() == null || state.getView().getGraph() == null) {
         return 1.0;
      }

      mxGraph graph = state.getView().getGraph();
      Object cell = state.getCell();

      Double edgeWeight = null;

      if(graph.getModel().getValue(cell) == null || graph.getModel().getValue(cell) == "") {
         return 1.0;
      }
      else if(graph.getModel().getValue(cell) instanceof String) {
         edgeWeight = Double.parseDouble((String) graph.getModel().getValue(cell));
      }
      else {
         edgeWeight = (Double) graph.getModel().getValue(cell);
      }

      return edgeWeight;
   }

}
