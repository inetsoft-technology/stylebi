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
import {
   TYPE_PHYSICAL_GRAPH_CONNECTION_COLOR,
   TYPE_PHYSICAL_GRAPH_WEAK_CONNECTION
} from "../../portal/data/model/datasources/database/physical-model/jsplumb-physical-graph.config";

export class JSPlumbUtil {
   /**
    * Make thumbnail connection style of weak join
    */
   static makeWeakJoinConnection(connection: any): void {
      connection.type += ` ${TYPE_PHYSICAL_GRAPH_WEAK_CONNECTION}`;
   }

   /**
    * Make thumbnail connection style of cycle join
    */
   static makeCycleJoinConnection(connection: any): void {
      connection.type += ` ${TYPE_PHYSICAL_GRAPH_CONNECTION_COLOR}`;
      connection["data"] = {
         color: "red"
      };
   }

   /**
    * Make thumbnail connection style of highlight join
    */
   static makeHighlightJoinConnection(connection: any): void {
      connection.type += ` ${TYPE_PHYSICAL_GRAPH_CONNECTION_COLOR}`;
      connection["data"] = {
         color: "#ed711c"
      };
   }
}