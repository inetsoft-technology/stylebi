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
import { jsPlumbLib } from "../../../../../../composer/gui/ws/jsplumb/jsplumb";

export type endpointType  = "join";
export const JOIN: endpointType = "join";
export const TYPE_VALID = "valid";
export const TYPE_INVALID = "invalid";
export const TYPE_PHYSICAL_GRAPH_CONNECTION = "physical-graph-connection";
export const TYPE_PHYSICAL_GRAPH_REVERSE_ARROW = "physical-graph-connection-reverse-arrow";
export const TYPE_PHYSICAL_GRAPH_CONNECTION_COLOR = "physical-graph-connection-color";
export const TYPE_PHYSICAL_GRAPH_WEAK_CONNECTION = "physical-graph-weak-connection";

const _dropOptions: Object = {
   tolerance: "touch",
   hoverClass: "dropHover",
   activeClass: "dragActive",
};

const _tableEndpoint: Object = {
   connector: ["Bezier", {curviness: 25}],
   endpoint: ["Dot", {radius: 8}],
   hoverClass: "table-endpoint--hover",
   isTarget: true,
   isSource: true,
   dropOptions: _dropOptions,
   connectionType: TYPE_PHYSICAL_GRAPH_CONNECTION
};

/** Endpoint configs for the table thumbnail endpoints. */
const left: Object = {
   anchor: "Left",
   cssClass: "table-endpoint table-endpoint-join table-endpoint-left",
   scope: JOIN,
};
const right: Object = {
   anchor: "Right",
   cssClass: "table-endpoint table-endpoint-join table-endpoint-right",
   scope: JOIN,
};

for(let property in _tableEndpoint) {
   if(_tableEndpoint.hasOwnProperty(property)) {
      left[property] = _tableEndpoint[property];
      right[property] = _tableEndpoint[property];
   }
}

export const PHYSICAL_ENDPOINTS: Object[] = [left, right];

/** Instance configurations */
export function jspInitGraphMain(): JSPlumb.JSPlumbInstance {
   const jsPlumbInstance = jsPlumbLib.jsPlumb.getInstance();
   jsPlumbInstance.restoreDefaults();
   jsPlumbInstance.importDefaults({
      Anchor: ["Perimeter", {shape: "Rectangle"}],
      Connector: "StateMachine",
      Endpoint: "Blank",
      EndpointStyle: {fill: "inherit"},
      LogEnabled: true,
      MaxConnections: -1,
      ReattachConnections: true
   });

   jsPlumbInstance.bind("beforeDrop", function(info: any) {
      // console.log("source and target ID's are the same - self connections not allowed.")
      return info.sourceId !== info.targetId;
   });
   jsPlumbInstance.registerEndpointTypes({
      [TYPE_VALID]: {
         cssClass: "table-endpoint-concat--valid"
      },
      [TYPE_INVALID]: {
         cssClass: "table-endpoint-concat--invalid"
      }
   });
   jsPlumbInstance.registerConnectionTypes({
      [TYPE_PHYSICAL_GRAPH_CONNECTION]: {
         cssClass: "physical-graph-connection",
         overlays: [["Arrow", {
            width: 10,
            length: 10,
            location: 1,
            id: "arrow",
            cssClass: "physical-graph-connector-arrow"
         }]],
         paintStyle: {stroke: "inherit", strokeWidth: 1},
         hoverPaintStyle: {
            stroke: "#ed711c",
            strokeWidth: 2
         }
      },
      [TYPE_PHYSICAL_GRAPH_REVERSE_ARROW]: {
         overlays: [["Arrow", {
            id: "arrow--reverse",
            width: 10,
            length: 10,
            location: 0,
            direction: -1
         }]]
      },
      [TYPE_PHYSICAL_GRAPH_CONNECTION_COLOR]: {
         cssClass: "physical-graph-connection-color",
         overlays: [["Arrow", {
            width: 10,
            length: 10,
            location: 1,
            id: "arrow--join-color",
            cssClass: "physical-graph-connection-arrow-color"
         }]],
         paintStyle: {stroke: "${color}"}
      },
      [TYPE_PHYSICAL_GRAPH_WEAK_CONNECTION]: {
         paintStyle: {
            dashstyle: "4 2",
            stroke: "inherit",
            strokeWidth: 1
         }
      }
   });

   return jsPlumbInstance;
}