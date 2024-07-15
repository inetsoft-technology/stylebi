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
import { TreeNodeModel } from "../../../../../../../../widget/tree/tree-node-model";
import { Tool } from "../../../../../../../../../../../shared/util/tool";
import { AttributeModel } from "../../../../../../model/datasources/database/physical-model/logical-model/attribute-model";

@Component({
   selector: "physical-table-tree",
   templateUrl: "physical-table-tree.component.html",
   styleUrls: ["physical-table-tree.component.scss"]
})
export class PhysicalTableTreeComponent {
   @Input() root: TreeNodeModel;
   @Output() nodesSelected: EventEmitter<TreeNodeModel[]> = new EventEmitter<TreeNodeModel[]>();
   selectedNodes: TreeNodeModel[] = [];
   tree: PhysicalTableTreeComponent = this;

   /**
    * When the entity changes, remove old locked nodes from selected list.
    */
   public removeLockedNodes(): void {
      const parents: TreeNodeModel[] = [];

      this.selectedNodes = this.selectedNodes.filter((node: TreeNodeModel) => {
         if(node.disabled && node.leaf) {
            this.addNodeToArray(this.getParentNode(node), parents);
         }

         return !node.disabled;
      });

      parents.forEach((parent: TreeNodeModel) => {
         this.updateParent(parent);
      });
   }

   /**
    * Select the given node.
    * @param node
    * @param evt
    */
   public selectNode(node: TreeNodeModel, evt: MouseEvent): void {
      const index: number = this.indexOf(node, this.selectedNodes);

      if(evt.shiftKey) {
         this.addShiftNodes(node);
      }
      else {
         if(index >= 0) {
            this.selectedNodes.splice(index, 1);
            this.deselectAllChildren(node);
            this.deselectParent(node);
         }
         else {
            this.selectedNodes.push(node);
            this.selectAllChildren(node);
            this.selectParent(node);
         }
      }

      this.nodesSelected.emit(this.selectedNodes);
   }

   /**
    * Select node and expand to it.
    * @param node
    */
   public selectAndExpandToNode(node: TreeNodeModel) {
      const parent: TreeNodeModel = this.getParentNode(node);

      if(parent) {
         const current: TreeNodeModel = parent.children.find((child: TreeNodeModel) => {
            return this.nodeEquals(child, node);
         });

         if(current) {
            parent.expanded = true;
            this.selectedNodes.push(node);
            this.selectParent(node);
         }
      }
   }

   /**
    * When use shift key, find node from last node to current select node.
    * @param node
    */
   private addShiftNodes(node: TreeNodeModel) {
      let shiftObj: any = {start: false, end: false, order: true, shiftNodes: []};

      if(this.selectedNodes == null || this.selectedNodes.length == 0) {
         this.addNodeToArray(node, this.selectedNodes);
         this.selectAllChildren(node);
         return;
      }

      let lastNode = this.selectedNodes[this.selectedNodes.length - 1];

      if(lastNode == node) {
         return;
      }

      this.findNodes(this.root, shiftObj, lastNode, node);
      this.addSelectedNodes(shiftObj);
   }

   /**
    * Find the nodes in the range for shift selecting.
    * @param findNode
    * @param shiftObj
    * @param lastNode
    * @param node
    */
   private findNodes(findNode: TreeNodeModel, shiftObj: any, lastNode: TreeNodeModel,
             node: TreeNodeModel)
   {
      if(shiftObj.start && !shiftObj.end) {
         this.addNodeToArray(findNode, shiftObj.shiftNodes);
      }

      let nodes: TreeNodeModel[];

      if(findNode.expanded) {
         nodes = findNode.children;
      }

      // Find first node, start is true, find the second node, end is true.
      // add node when start is true and end is false.
      if(findNode == lastNode || findNode == node) {
         // If select node from top to bottom, order is true, else false.
         if(!shiftObj.start) {
            shiftObj.order = findNode == lastNode;
         }

         shiftObj.end = shiftObj.start;
         shiftObj.start = true;
         this.addNodeToArray(findNode, shiftObj.shiftNodes);
      }

      if(nodes == null) {
         return;
      }

      for(let i = 0; i < nodes.length; i++) {
         this.findNodes(nodes[i], shiftObj, lastNode, node);
      }
   }

   /**
    * Add the nodes, and remove dupplicates.
    * @param shiftObj
    */
   private addSelectedNodes(shiftObj: any) {
      let nodes = shiftObj.shiftNodes;

      if(!shiftObj.order) {
         for(let i = (nodes.length - 1); i >= 0; i--) {
            this.addNodeToArray(nodes[i], this.selectedNodes);
            this.selectAllChildren(nodes[i]);
         }
      }
      else {
         for(let j = 0; j < nodes.length; j++) {
            this.addNodeToArray(nodes[j], this.selectedNodes);
            this.selectAllChildren(nodes[j]);
         }
      }
   }

   /**
    * Add the node to the array and check that it is unique.
    * @param node
    * @param arr
    */
   private addNodeToArray(node: TreeNodeModel, arr: TreeNodeModel[]) {
      let found = false;

      for(let i = 0; i < arr.length; i++) {
         if(this.nodeEquals(arr[i], node)) {
            found = true;
         }
      }

      if(!found) {
         arr.push(node);
      }
   }

   /**
    * Deselect all the children of the node if it has any.
    * @param node
    */
   private deselectAllChildren(node: TreeNodeModel): void {
      if(node.children && node.children.length) {
         node.children.forEach((child: TreeNodeModel) => {
            const index: number = this.indexOf(child, this.selectedNodes);

            if(index >= -1) {
               this.selectedNodes.splice(index, 1);
            }
         });
      }
   }

   /**
    * Select all the child nodes, if the node has any.
    * @param node
    */
   private selectAllChildren(node: TreeNodeModel): void {
      if(node.children && node.children.length) {
         node.expanded = true;
         node.children.forEach((child: TreeNodeModel) => {
            this.addNodeToArray(child, this.selectedNodes);
         });
      }
   }

   /**
    * Deselect the parent node if it has one and if it has any deselected children.
    * @param node
    */
   private deselectParent(node: TreeNodeModel): void {
      if(node.leaf) {
         const parent: TreeNodeModel = this.getParentNode(node);
         let index: number = this.indexOf(parent, this.selectedNodes);
         parent.disabled = false;

         if(index >= 0) {
            this.selectedNodes.splice(index, 1);
         }
      }
   }

   /**
    * Select parent if there is one and if all the children are selected.
    * @param node
    */
   private selectParent(node: TreeNodeModel): void {
      if(node.leaf) {
         const parent: TreeNodeModel = this.getParentNode(node);
         let index: number = this.indexOf(parent, this.selectedNodes);

         if(index == -1) {
            let locked: boolean = true;
            const allChildrenSelected: boolean = parent.children.every((child: TreeNodeModel) => {
               const selected: TreeNodeModel = this.getSelectedNode(child);
               locked = locked && !!selected && !!selected.disabled;

               return !!selected;
            });

            if(allChildrenSelected) {
               if(locked) {
                  parent.disabled = true;
               }

               this.selectedNodes.push(parent);
            }
         }
      }
   }

   /**
    * Select parent if there is one and if all the children are selected.
    * @param node
    */
   private updateParent(parent: TreeNodeModel): void {
      let locked = true;

      const allChildrenSelected: boolean = parent.children.every((child: TreeNodeModel) => {
         const index: number = this.indexOf(child, this.selectedNodes);

         if(index >= 0) {
            const selected: TreeNodeModel = this.selectedNodes[index];
            locked = locked && selected.disabled;
            return true;
         }

         return false;
      });

      if(allChildrenSelected) {
         if(locked) {
            parent = Tool.clone(parent);
            parent.disabled = true;
         }

         this.selectedNodes.push(parent);
      }
      else {
         const index: number = this.indexOf(parent, this.selectedNodes);

         if(index >= 0) {
            this.selectedNodes.slice(index, 1);
         }
      }
   }

   /**
    * Check equivalency between nodes.
    * @param node1
    * @param node2
    * @returns {boolean}
    */
   private nodeEquals(node1: any, node2: any): boolean {
      if(!node1 || !node2) {
         return Tool.isEquals(node1, node2);
      }
      else if(!node1.leaf && node2.leaf || node1.leaf && !node2.leaf) {
         return false;
      }
      else if(node1.leaf && node2.leaf) {
         const data1: AttributeModel = node1.data as AttributeModel;
         const data2: AttributeModel = node2.data as AttributeModel;

         return Tool.isEquals(data1.qualifiedName, data2.qualifiedName) &&
            Tool.isEquals(data1.table, data2.table);
      }

      return Tool.isEquals(node1, node2);
   }

   /**
    * Get the selected node in the list.
    * @param node
    * @returns {any}
    */
   public getSelectedNode(node: TreeNodeModel): TreeNodeModel {
      let index: number = this.indexOf(node, this.selectedNodes);

      if(index >= 0) {
         return this.selectedNodes[index];
      }

      return null;
   }

   /**
    * Find index of a tree node using the comparitor.
    * @param node
    * @param arr
    * @returns {number}
    */
   public indexOf(node: TreeNodeModel, arr: TreeNodeModel[]): number {
      let index: number = -1;

      for(let i = 0; i < arr.length; i++) {
         if(this.nodeEquals(arr[i], node)) {
            index = i;
            break;
         }
      }

      return index;
   }

   /**
    * Get parent node by using the table field. (This tree will only ever have 2 levels)
    * @param childNode
    * @returns {any}
    */
   private getParentNode(childNode: TreeNodeModel): TreeNodeModel {
      return this.root.children.find((child: TreeNodeModel) => {
         return child.label == childNode.data.table;
      });
   }
}
