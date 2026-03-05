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
import { Component, HostBinding, Input, OnInit } from "@angular/core";
import { TreeNodeModel } from "../../../../widget/tree/tree-node-model";

@Component({
   selector: "wiz-components-pane",
   templateUrl: "./wiz-components-pane.component.html",
   styleUrls: ["./wiz-components-pane.component.scss"]
})
export class WizComponentsPane implements OnInit {
   @HostBinding("hidden")
   @Input() inactive: boolean;

   visualizations: TreeNodeModel;
   filter: TreeNodeModel;
   output: TreeNodeModel;
   shape: TreeNodeModel;

   ngOnInit(): void {
      this.visualizations = {
         label: "_#(js:Visualizations)",
         icon: "folder-toolbox-icon",
         children: [
            {
               label: "_#(js:Visualization 1)",
               icon: "viewsheet-icon",
               leaf: true,
               dragName: "dragchart"
            },
            {
               label: "_#(js:Visualization 2)",
               icon: "viewsheet-icon",
               leaf: true,
               dragName: "dragcrosstab"
            },
            {
               label: "_#(js:Visualization 3)",
               icon: "viewsheet-icon",
               leaf: true,
               dragName: "dragtable"
            },
            {
               label: "_#(js:Visualization 4)",
               icon: "viewsheet-icon",
               leaf: true,
               dragName: "dragfreehandtable"
            },
            {
               label: "_#(js:Visualization 5)",
               icon: "viewsheet-icon",
               leaf: true,
               dragName: "draggauge"
            }
         ]
      };

      this.filter = {
         label: "_#(js:Filter)",
         icon: "condition-icon",
         children: [
            {
               label: "_#(js:Order Name)",
               leaf: true
            },
            {
               label: "_#(js:Order Number)",
               leaf: true
            },
            {
               label: "_#(js:ProductName)",
               leaf: true
            },
            {
               label: "_#(js:Product Id)",
               leaf: true
            }
         ]
      };

      this.output = {
         label: "_#(js:Output)",
         icon: "folder-toolbox-icon",
         children: [
            {
               label: "_#(js:Text)",
               icon: "text-box-icon",
               leaf: true,
               dragName: "dragtext"
            },
            {
               label: "_#(js:Image)",
               icon: "image-icon",
               leaf: true,
               dragName: "dragimage"
            }
         ]
      };

      this.shape = {
         label: "_#(js:Shape)",
         icon: "folder-toolbox-icon",
         children: [
            {
               label: "_#(js:Line)",
               icon: "line-icon",
               leaf: true,
               dragName: "dragline"
            },
            {
               label: "_#(js:Rectangle)",
               icon: "rectangle-icon",
               leaf: true,
               dragName: "dragrectangle"
            },
            {
               label: "_#(js:Oval)",
               icon: "oval-icon",
               leaf: true,
               dragName: "dragoval"
            }
         ]
      };
   }
}
