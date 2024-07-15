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
import { jsPlumbLib } from "./jsplumb";

export type endpointType  = "join" | "concat";
export const JOIN: endpointType = "join";
export const CONCAT: endpointType = "concat";
export const TYPE_VALID = "valid";
export const TYPE_INVALID = "invalid";
export const TYPE_INDETERMINATE = "indeterminate";
export const TYPE_DIMMED = "dimmed";
export const TYPE_ASSEMBLY_CONNECTION = "assembly-connection";
export const TYPE_CONCATENATION_WARNING = "concatenation-warning";
export const TYPE_COLUMN_SOURCE = "column-source";

const _dropOptions: Object = {
   tolerance: "touch",
   hoverClass: "dropHover",
   activeClass: "dragActive",
};

const _tableEndpoint: Object = {
   endpoint: ["Dot", {radius: 8}],
   connector: ["Bezier", {curviness: 25}],
   hoverClass: "table-endpoint--hover",
   isTarget: true,
   isSource: true,
   dropOptions: _dropOptions,
   connectionType: TYPE_ASSEMBLY_CONNECTION
};

/** Endpoint configs for the table thumbnail endpoints. */
const top: Object = {
   anchor: "Top",
   cssClass: "table-endpoint table-endpoint-concat table-endpoint-top",
   type: TYPE_VALID,
   scope: CONCAT,
};
const bottom: Object = {
   anchor: "Bottom",
   cssClass: "table-endpoint table-endpoint-concat table-endpoint-bottom",
   type: TYPE_VALID,
   scope: CONCAT,
};
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
      top[property] = _tableEndpoint[property];
      bottom[property] = _tableEndpoint[property];
      left[property] = _tableEndpoint[property];
      right[property] = _tableEndpoint[property];
   }
}

export const TABLE_ENDPOINTS: Object[] = [top, bottom, left, right];

/** Instance configurations */
export function jspInitGraphMain(): JSPlumb.JSPlumbInstance {
   const jsPlumbInstance = jsPlumbLib.jsPlumb.getInstance();
   jsPlumbInstance.restoreDefaults();
   jsPlumbInstance.importDefaults({
      Anchor: ["Perimeter", {shape: "Rectangle"}],
      Connector: "StateMachine",
      Endpoint: "Blank",
      Endpoints: [["Dot", {
         radius: 5,
         cssClass: "ws-assembly-connector-endpoint",
         hoverClass: "ws-assembly-connector-endpoint--hover",
      }], "Blank"],
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
      },
      [TYPE_INDETERMINATE]: {
         cssClass: "table-endpoint-concat--indeterminate"
      },
      [TYPE_DIMMED]: {
         cssClass: "ws-assembly-graph-element--dimmed"
      },
      [TYPE_COLUMN_SOURCE]: {
         cssClass: "ws-assembly-graph-element--column-source"
      }
   });
   jsPlumbInstance.registerConnectionTypes({
      [TYPE_DIMMED]: {
         cssClass: "ws-assembly-graph-element--dimmed"
      },
      [TYPE_ASSEMBLY_CONNECTION]: {
         cssClass: "ws-assembly-connection",
         overlays: [["Arrow", {
            width: 10,
            length: 10,
            location: 1,
            id: "arrow",
            cssClass: "ws-assembly-connector-arrow"
         }]],
         paintStyle: {stroke: "inherit"}
      },
      [TYPE_CONCATENATION_WARNING]: {
         cssClass: "ws-assembly-connection--concatenation-warning",
         overlays: [["Arrow", {
            width: 10,
            length: 10,
            location: 1,
            id: "arrow--composition-warning",
            cssClass: "ws-assembly-connector-arrow--composition-warning"
         }]]
      },
      [TYPE_COLUMN_SOURCE]: {
         cssClass: "ws-assembly-graph-element--column-source"
      }
   });

   return jsPlumbInstance;
}