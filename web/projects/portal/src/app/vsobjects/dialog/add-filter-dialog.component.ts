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
import { Component, EventEmitter, Input, Output, ViewChild } from "@angular/core";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { GuiTool } from "../../common/util/gui-tool";
import { TreeComponent } from "../../widget/tree/tree.component";

@Component({
   selector: "add-filter-dialog",
   templateUrl: "add-filter-dialog.component.html",
})
export class AddFilterDialog {
   @Input() model: TreeNodeModel;
   @Output() onCommit = new EventEmitter<TreeNodeModel[]>();
   @Output() onCancel = new EventEmitter<string>();
   @ViewChild(TreeComponent) tree: TreeComponent;
   selectedNodes: TreeNodeModel[];

   ok(): void {
      this.onCommit.emit(this.selectedNodes);
   }

   close(): void {
      this.onCancel.emit("cancel");
   }

   selectNodes(nodes: TreeNodeModel[]): void {
      this.selectedNodes = [];

      for(let node of nodes) {
         if(node.type == "columnNode") {
            this.selectedNodes.push(node);
         }
      }
   }

   selectNode(node: TreeNodeModel): void {
      if(node.type == "columnNode") {
         this.selectedNodes = [node];
      }
   }

   public getCSSIcon(node: TreeNodeModel): string {
      return GuiTool.getTreeNodeIconClass(node, "");
   }
}
