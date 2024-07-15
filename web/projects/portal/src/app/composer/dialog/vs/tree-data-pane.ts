/*
 * inetsoft-web - StyleBI is a business intelligence web application.
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
import { Input, Directive } from "@angular/core";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { VSUtil } from "../../../vsobjects/util/vs-util";
import { DataTreeValidatorService } from "../../../vsobjects/dialog/data-tree-validator.service";

@Directive()
export class TreeDataPane {
   @Input() runtimeId: string;
   constructor(protected treeValidator: DataTreeValidatorService) {
   }

   expandNode(node: TreeNodeModel) {
      this.treeValidator.validateTreeNode(node, this.runtimeId);
   }

   selectedNode(node: TreeNodeModel) {
      this.treeValidator.validateTreeNode(node, this.runtimeId);
   }

   private getParentCompareType(node: TreeNodeModel) {
      // Worksheet or Table that is direct descendant of root
      if(node && (node.type == "worksheet" || node.type == "table" ||
         node.type == "folder" && node.data.path == ("/" + "_#(js:Components)")))
      {
         return "table";
      }
      else { // Cubes or Logic Model
         return "folder";
      }
   }

   protected getParentTable(column: TreeNodeModel, tree: TreeComponent): string {
      let parentNode: TreeNodeModel = tree.getParentNode(column);
      // direct parent of selected node has to have type handled differently depending on
      // whether or not it's a descendant of a worksheet or a logic model
      let parentType: string = this.getParentCompareType(
         tree.getTopAncestor("data", column.data, true));

      while(parentNode && parentType != parentNode.type) {
         parentNode = tree.getParentNode(parentNode);
      }

      const path: string = parentNode && parentNode.data && parentNode.data.path
         ? parentNode.data.path : null;

      if(path) {
         return VSUtil.getTableName(path.substring(path.lastIndexOf("/") + 1));
      }

      return parentNode ? VSUtil.getTableName(parentNode.label) : null;
   }
}
