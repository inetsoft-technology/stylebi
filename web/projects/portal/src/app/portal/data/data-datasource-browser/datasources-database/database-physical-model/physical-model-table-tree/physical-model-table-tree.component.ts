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
import { EventEmitter, Input, Output, Component } from "@angular/core";
import { TreeNodeModel } from "../../../../../../widget/tree/tree-node-model";

@Component({
   selector: "physical-model-table-tree",
   templateUrl: "physical-model-table-tree.component.html"
})
export class PhysicalModelTableTreeComponent {
   treeRef: PhysicalModelTableTreeComponent = this;
   @Input() root: TreeNodeModel;
   @Input() searchStr: string = "";
   @Input() showOnlySelectedTables: boolean = false;
   @Input() disabled = false;
   @Output() nodeExpanded: EventEmitter<TreeNodeModel> = new EventEmitter<TreeNodeModel>();
   @Output() nodeSelected: EventEmitter<TreeNodeModel[]> = new EventEmitter<TreeNodeModel[]>();
   @Output() nodeCheckboxToggled: EventEmitter<TreeNodeModel> = new EventEmitter<TreeNodeModel>();
   @Output() onNodeContextMenu: EventEmitter<{node: TreeNodeModel, event: MouseEvent}> =
      new EventEmitter<{node: TreeNodeModel, event: MouseEvent}>();
   selectedNodes: TreeNodeModel[] = [];
   selectedNode: TreeNodeModel[] = [];

   /**
    * Node was expanded, emit node.
    * @param node the node expanded
    */
   expandNode(node: TreeNodeModel): void {
      this.nodeExpanded.emit(node);
   }

   /**
    * A node was selected, emit node.
    * @param node the node to edit
    */
   selectNode(node: TreeNodeModel): void {
      this.selectedNodes = this.selectedNodes == null ? [] : this.selectedNodes;
      this.selectedNodes.push(node);
   }

   selectNode0(node: TreeNodeModel[]) {
      this.nodeSelected.emit(node);
   }

   /**
    * A node checkbox was clicked, emit node.
    * @param node the node toggled
    */
   checkboxToggledNode(node: TreeNodeModel): void {
      this.nodeCheckboxToggled.emit(node);
   }
}