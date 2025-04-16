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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { PhysicalModelTableTreeComponent } from "../physical-model-table-tree.component";
import { TreeNodeModel } from "../../../../../../../widget/tree/tree-node-model";
import { PhysicalModelTreeNodeModel } from "../../../../../model/datasources/database/physical-model/physical-model-tree-node-model";

@Component({
   selector: "physical-model-table-tree-node",
   templateUrl: "physical-model-table-tree-node.component.html",
   styleUrls: ["physical-model-table-tree-node.component.scss"]
})
export class PhysicalModelTableTreeNodeComponent {
   @Input() tree: PhysicalModelTableTreeComponent;
   @Input() node: TreeNodeModel;
   @Input() searchStr: string = "";
   @Input() showOnlySelectedTables: boolean = false;
   @Input() disabled = false;
   @Input() showRoot: boolean = true;
   @Input() indentLevel: number = 0;
   @Output() onNodeContextMenu: EventEmitter<{node: TreeNodeModel, event: MouseEvent}> =
      new EventEmitter<{node: TreeNodeModel, event: MouseEvent}>();
   readonly INDENT_SIZE: number = 15;

   /**
    * Check if this node has children or is not a leaf.
    * @returns {boolean}   if the node has children or is not a leaf
    */
   hasChildren(): boolean {
      return this.node &&
         (this.node.leaf === false || (this.node.children && this.node.children.length > 0));
   }

   /**
    * Toggle this node.
    */
   toggleNode(): void {
      if(!this.hasChildren() || this.disabled) {
         return;
      }

      this.node.expanded = !this.node.expanded;

      if(this.node.expanded) {
         this.tree.expandNode(this.node);
      }
   }

   /**
    * Get the toggle icon css to display.
    * @returns {string} the toggle icon css classes
    */
   getToggleIcon(): string {
      if(this.node.expanded) {
         return "caret-down-icon";
      }
      else {
         return "caret-right-icon";
      }
   }

   isTableVisible(node: TreeNodeModel): boolean {
      return !this.showOnlySelectedTables || !node.leaf ||
         (<PhysicalModelTreeNodeModel> node.data).selected;
   }

   /**
    * Check if this node is currently selected.
    * @returns {boolean}   true if selected
    */
   isSelected(): boolean {
      if(this.tree.selectedNodes != null && this.tree.selectedNodes.length > 0) {
         return this.tree.selectedNodes.includes(this.node);
      }
      else if(this.tree.selectedNode != null && this.tree.selectedNode.length > 0) {
         return this.tree.selectedNode.includes(this.node);
      }

      return false;
   }

   /**
    * Select this node.
    */
   selectNode(event: MouseEvent): void {
      if(!this.node.leaf || this.disabled) {
         return;
      }

      if(event.ctrlKey || event.shiftKey) {
         this.tree.selectedNode.push(this.node)
      }
      else {
         this.tree.selectedNode = [this.node];
      }

      this.tree.selectNode0(this.tree.selectedNode);
   }

   /**
    * Node's checkbox was toggled.
    */
   checkboxToggledNode(): void {
      this.tree.checkboxToggledNode(this.node);
   }

   /**
    * If no search string then all children should be kept
    * @returns {boolean}   true if should keep all children
    */
   get keepAllChildren(): boolean {
      return this.searchStr == null || this.searchStr.length == 0;
   }
}