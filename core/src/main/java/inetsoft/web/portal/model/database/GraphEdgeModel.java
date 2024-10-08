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

import inetsoft.web.portal.model.database.graph.NodeConnectionInfo;

import java.util.List;

public class GraphEdgeModel {

   public List<NodeConnectionInfo> getInput() {
      return input;
   }

   public void setInput(List<NodeConnectionInfo> input) {
      this.input = input;
   }

   public List<NodeConnectionInfo> getOutput() {
      return output;
   }

   public void setOutput(List<NodeConnectionInfo> output) {
      this.output = output;
   }

   private List<NodeConnectionInfo> input;
   private List<NodeConnectionInfo> output;
}
