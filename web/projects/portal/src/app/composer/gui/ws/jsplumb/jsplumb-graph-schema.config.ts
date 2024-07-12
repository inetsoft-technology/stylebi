/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import {
   TYPE_PHYSICAL_GRAPH_CONNECTION_COLOR,
   TYPE_PHYSICAL_GRAPH_WEAK_CONNECTION
} from "../../../../portal/data/model/datasources/database/physical-model/jsplumb-physical-graph.config";
import { jsPlumbLib } from "./jsplumb";

export const TYPE_COLUMN = "column";
export const PHYSICAL_VIEW_TYPE_COLUMN = "physical-view-column";
export const TYPE_COLUMN_INTERACTION_TARGET = "column-interaction-target";
export const TYPE_CROSS = "cross";

export const DEFAULT_ANCHOR = ["Continuous", {faces: ["left", "right"]}];
export const LEFT_INVALID_ANCHOR = ["Continuous", {faces: ["right"]}];

/** Instance configurations */
export function jspInitGraphSchema(): JSPlumb.JSPlumbInstance {
   const jsPlumbInstance = jsPlumbLib.jsPlumb.getInstance();
   jsPlumbInstance.restoreDefaults();
   jsPlumbInstance.importDefaults({
      Anchor: DEFAULT_ANCHOR,
      Connector: "StateMachine",
      Endpoint: "Blank",
      MaxConnections: -1,
      PaintStyle: {stroke: "fill", strokeWidth: 3}
   });

   jsPlumbInstance.registerConnectionTypes({
      [TYPE_CROSS]: {
         anchor: ["Bottom"],
         cssClass: "schema-connector-cross-join",
         paintStyle: {stroke: "inherit", strokeWidth: 1, "stroke-dasharray": "inherit"}
      },
      [TYPE_COLUMN]: {
         anchor: ["Left", "Right"],
         cssClass: "schema-connector-inner-join",
         overlays: [
            ["Arrow", {foldback: 0.8, width: 10, length: 5, location: 1, id: "arrow"}],
         ]
      },
      [PHYSICAL_VIEW_TYPE_COLUMN]: {
         anchor: ["Left", "Right"],
         cssClass: "schema-connector-inner-join",
         overlays: [
            ["Arrow", {foldback: 0.8, width: 10, length: 5, location: 1, id: "arrow"}],
         ],
         paintStyle: {stroke: "inherit"}
      },
      [TYPE_PHYSICAL_GRAPH_CONNECTION_COLOR]: {
         cssClass: "physical-graph-connection-color",
         overlays: [["Arrow", {
            foldback: 0.8,
            width: 10,
            length: 5,
            location: 1,
            id: "arrow--join-color",
            cssClass: "physical-graph-connection-arrow-color"
         }]],
         paintStyle: {stroke: "${color}"}
      },
      [TYPE_COLUMN_INTERACTION_TARGET]: {
         anchor: ["Left", "Right"],
         cssClass: "schema-connector-inner-join--interaction-target",
         overlays: [
            ["Arrow", {foldback: 0.8, width: 12, length: 5, location: 1, id: "arrow"}],
         ],
         paintStyle: {stroke: "inherit", strokeWidth: 9}
      },
      [TYPE_PHYSICAL_GRAPH_WEAK_CONNECTION]: {
         paintStyle: {
            dashstyle: "4 2",
            stroke: "inherit",
            strokeWidth: 1
         }
      }
   });
   jsPlumbInstance.bind("beforeDrop", function(info: any) {
      if(info.sourceId === info.targetId) { // source and target IDs are same
         // console.log("source and target IDs are the same - self connections not allowed.")
         return false;
      }
      else {
         return true;
      }
   });

   return jsPlumbInstance;
}
