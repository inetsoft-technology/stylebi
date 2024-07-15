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
package inetsoft.web.portal.model.database;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.util.StringUtils;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GraphViewModel {

   public GraphViewModel() {
   }

   public GraphViewModel(List<GraphModel> graphs) {
      this.graphs = graphs;
   }

   public List<GraphModel> getGraphs() {
      return graphs;
   }

   public void setGraphs(List<GraphModel> graphs) {
      this.graphs = graphs;
   }

   public GraphModel findGraphModel(String tableName) {
      if(graphs == null || StringUtils.isEmpty(tableName)) {
         return null;
      }

      for(GraphModel graph : graphs) {
         if(tableName.equals(graph.getNode().getName())) {
            return graph;
         }
      }

      return null;
   }

   private List<GraphModel> graphs;
}
