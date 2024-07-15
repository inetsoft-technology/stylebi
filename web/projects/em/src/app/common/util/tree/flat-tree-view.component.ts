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
import { DataSource } from "@angular/cdk/collections";
import { FlatTreeControl } from "@angular/cdk/tree";
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { FlatSecurityTreeNode } from "../../../settings/security/security-tree-view/security-tree-node";
import { FlatTreeNode, FlatTreeNodeMenuItem } from "./flat-tree-model";

export interface FlatTreeSelectNodeEvent {
   node: FlatTreeNode;
   event: MouseEvent;
}

export interface FlatTreeContextMenuEvent {
   node: FlatTreeNode;
   menu: FlatTreeNodeMenuItem;
}

@Component({
   selector: "em-flat-tree-view",
   templateUrl: "./flat-tree-view.component.html",
   styleUrls: ["./flat-tree-view.component.scss"]
})
export class FlatTreeViewComponent {
   @Input() dataSource: DataSource<FlatTreeNode>;
   @Input() treeControl: FlatTreeControl<FlatTreeNode>;
   @Input() selectedNodes: FlatTreeNode[];
   @Input() menuIconSelectNode: boolean = true;
   @Input() menuIconFloatRight: boolean = false;
   @Input() wrap: boolean = true;
   @Output() nodeSelected = new EventEmitter<FlatTreeSelectNodeEvent>();
   @Output() onDrop = new EventEmitter<FlatTreeNode>();
   @Output() dblClicked = new EventEmitter<FlatTreeNode>();
   @Output() onContextMenu = new EventEmitter<FlatTreeContextMenuEvent>();
   @Output() onShowContextMenu = new EventEmitter<FlatTreeNode>();
   readonly nodePadding: number = 40;
   public hasChild = (nodeData: FlatTreeNode) => nodeData.expandable;

   public isSelected(node: FlatTreeNode): boolean {
      return this.selectedNodes && this.selectedNodes.indexOf(node) > -1;
   }

   public getIcon(node: FlatTreeNode, hasChild: boolean = false): string {
      const expanded = hasChild && this.treeControl.isExpanded(node);
      let icon = node.getIcon(expanded);

      if(icon == null && hasChild) {
         icon = expanded ? "downward-icon" : "forward-icon";
      }

      return icon;
   }

   selectNode(node: FlatTreeNode<any>, event: MouseEvent) {
      this.nodeSelected.emit(<FlatTreeSelectNodeEvent>{node: node, event: event});
   }

   clickMenu(event: MouseEvent, node: FlatTreeNode) {
      if(!this.menuIconSelectNode) {
         event.stopPropagation();
         this.onShowContextMenu.emit(node);
      }
   }

   onDragStart(node: FlatTreeNode<any>, event: DragEvent) {
      event.dataTransfer.setData("text", node.label);
      this.selectNode(node, event);
   }

   /**
    * Returns first node with a higher level than the current node, this node is the parent
    * of the param node
    * @param {FlatTreeNode<any>} node
    * @returns {FlatTreeNode<any>}
    */
   getParent(node: FlatTreeNode<any>): FlatTreeNode<any> {
      const currentLevel = this.treeControl.getLevel(node);

      if(currentLevel < 1) {
         return node;
      }

      const startIdx = this.treeControl.dataNodes.indexOf(node) - 1;

      for (let idx = startIdx; idx >= 0; idx--) {
         const curr = this.treeControl.dataNodes[idx];

         if(this.treeControl.getLevel(curr) < currentLevel) {
            return curr;
         }
      }

      return node;
   }

   dropIntoParent(node: FlatTreeNode<any>): void {
      let parent = this.getParent(node);
      this.onDrop.emit(parent);
   }

   onDblclick(node: FlatTreeNode<any>): void {
      this.treeControl.toggle(node);
      this.dblClicked.emit(node);
   }

   trackByFn(index, node: FlatSecurityTreeNode) {
      return index;
   }

   contextMenuClicked(node: FlatTreeNode<any>, menu: FlatTreeNodeMenuItem): void {
      this.onContextMenu.emit({node, menu});
   }

   toggle(event: MouseEvent, node: FlatTreeNode): void {
      this.treeControl.toggle(node);
      event.stopPropagation();
   }
}
