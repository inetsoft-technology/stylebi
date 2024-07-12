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
import { Component, Input } from "@angular/core";
import { PhysicalTableTreeComponent } from "../physical-table-tree.component";
import { TreeNodeModel } from "../../../../../../../../../widget/tree/tree-node-model";

@Component({
   selector: "physical-table-tree-node",
   templateUrl: "physical-table-tree-node.component.html",
   styleUrls: ["physical-table-tree-node.component.scss"]
})
export class PhysicalTableTreeNodeComponent {
   @Input() tree: PhysicalTableTreeComponent;
   @Input() node: TreeNodeModel;
   @Input() showRoot: boolean = true;
   @Input() indentLevel: number = 0;
   readonly INDENT_SIZE: number = 30;

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
      event.stopPropagation();
      event.preventDefault();
      this.node.expanded = !this.node.expanded;
   }

   /**
    * Get the toggle icon css to display.
    * @returns {string} the toggle icon css classes
    */
   getToggleIcon(): string {
      return this.node.expanded ? "caret-down-icon" : "caret-right-icon";
   }

   /**
    * Check if this node is currently selected. If disabled, it means its locked so it has
    * to be selected.
    * @returns {boolean}   true if selected
    */
   isSelected(): boolean {
      return this.tree.indexOf(this.node, this.tree.selectedNodes) >= 0;
   }

   /**
    * Check if this node is disabled/locked. Only happens when it is selected.
    * The node in the selected array maybe be different from current, check that one.
    * @returns {boolean}
    */
   isDisabled(): boolean {
      const selected: TreeNodeModel = this.tree.getSelectedNode(this.node);
      return selected == null ? false : selected.disabled;
   }

   /**
    * Select this node.
    */
   selectNode(event: MouseEvent): void {
      if(!this.isDisabled()) {
         this.tree.selectNode(this.node, event);
      }
   }
}
