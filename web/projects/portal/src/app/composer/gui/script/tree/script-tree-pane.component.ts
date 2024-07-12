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
import {Component, HostBinding, Input, OnChanges, SimpleChanges} from "@angular/core";
import {ScriptTreePaneModel} from "../../../data/script/script-tree-pane-model";
import {TreeNodeModel} from "../../../../widget/tree/tree-node-model";
import {ScriptService} from "../script.service";
import {Tool} from "../../../../../../../shared/util/tool";

@Component({
   selector: "script-tree-pane",
   templateUrl: "script-tree-pane.component.html",
})
export class ScriptTreePane implements OnChanges {
   @HostBinding("hidden")
   @Input() inactive: boolean;
   @Input() scriptTreePaneModel: ScriptTreePaneModel;
   @Input() disabled: boolean = false;
   private _functionTree: TreeNodeModel;
   activeRoot: TreeNodeModel;

   @Input()
   set functionTree(value: TreeNodeModel) {
      this._functionTree = value;
      this._functionTree.expanded = false;
   }

   get functionTree(): TreeNodeModel {
      return this._functionTree;
   }

   constructor(private scriptService: ScriptService) {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.hasOwnProperty("functionTree")) {
         this.activeRoot = this.functionTree
      }
    }

   /**
    * Method for determining the css class of the tree.
    */
   getCSSIcon(node: any): string {
      let css: string = "";

      if(node.data && node.data.data == "New Aggregate") {
         css += "summary-icon";
      }
      else if(node.data && node.data.useragg == "true") {
         css += "summary-icon";
      }
      else if(node.data && node.data.useragg == "false") {
         css += "summary-icon";
      }
      else if(node.data && node.data.name == "LOGIC_MODEL") {
         css += "db-model-icon";
      }
      else if(node.data && (node.data.isTable == "true" || node.type == "entity" ||
         node.data.name == "TABLE" || node.data.name == "PHYSICAL_TABLE"))
      {
         css += "data-table-icon";
      }
      else if(node.data && (node.data.isField == "true" || node.data.type == "column") ||
         node.type === "field" || node.data && node.data.name == "COLUMN" ||
         node.data && node.data.name == "DATE_COLUMN")
      {
         css += "column-icon";
      }
      else if(node.children && node.children.length > 0) {
         if(node.expanded) {
            css += "folder-open-icon";
         }
         else {
            css += "folder-icon";
         }
      }

      return css;
   }

   itemClicked(node: TreeNodeModel, target: string): void {
      this.scriptService.setClickedNode(node, target);
   }

   searchStart(start: boolean): void {
      if(!start) {
         this.activeRoot = this.functionTree;
      }
      else {
         this.activeRoot = Tool.clone(this.functionTree);
      }
   }
}