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
import { FlatTreeControl } from "@angular/cdk/tree";
import { MatTreeFlattener } from "@angular/material/tree";
import { BehaviorSubject } from "rxjs";
import { take } from "rxjs/operators";
import { FlatSecurityTreeNode, SecurityTreeNode } from "./security-tree-node";

export class SecurityTreeFlattener extends MatTreeFlattener<SecurityTreeNode, FlatSecurityTreeNode> {
   flattenedDataChanged = new BehaviorSubject<FlatSecurityTreeNode[]>([]);

   constructor(public transformFunction: (node: SecurityTreeNode, level: number) => FlatSecurityTreeNode,
               public getLevel: (node: FlatSecurityTreeNode) => number,
               public isExpandable: (node: FlatSecurityTreeNode) => boolean,
               public treeControl: FlatTreeControl<FlatSecurityTreeNode>)
   {
      super(transformFunction, getLevel, isExpandable, null);

      this.getChildren = (node: SecurityTreeNode) => {
         node.toggle(this.selected(node));
         return node.getChildren();
      };
   }

   flattenNode(node: SecurityTreeNode, level: number,
               parentNode: FlatSecurityTreeNode,
               resultNodes: FlatSecurityTreeNode[]): FlatSecurityTreeNode[]
   {
      const flatNode = this.transformFunction(node, level);
      resultNodes.push(flatNode);

      const find = this.selected(node);
      this.deselect(node);

      if(find) {
         this.treeControl.expand(flatNode);
      }

      if (this.isExpandable(flatNode)) {
         const childrenNodes = this.getChildren(node);

         if(Array.isArray(childrenNodes)) {
            this.flattenChildren(childrenNodes, level, resultNodes, flatNode);
         }
         else {
            childrenNodes.pipe(take(1)).subscribe(children => {
               this.flattenChildren(children, level, resultNodes, flatNode);
            });
         }
      }

      return resultNodes;
   }

   flattenChildren(children: SecurityTreeNode[], level: number,
                   resultNodes: FlatSecurityTreeNode[],
                   parentNode: FlatSecurityTreeNode): void
   {
      children.forEach((child) => {
         this.flattenNode(child, level + 1, parentNode, resultNodes);
      });
   }

   /**
    * @Override override flattenNodes function in MatTreeFlattener
    * Flatten a list of node type SecurityTreeNode to flattened version of node
    * FlatSecurityTreeNode.
    * Please note that type SecurityTreeNode may be nested, and the length of
    * `structuredData` may be different from that of returned list `FlatSecurityTreeNode[]`.
    */
   flattenNodes(structuredData: SecurityTreeNode[]): FlatSecurityTreeNode[] {
      const resultNodes: FlatSecurityTreeNode[] = [];
      structuredData.forEach(node => this.flattenNode(
         node, 0, null, resultNodes));

      this.flattenedDataChanged.next(resultNodes);
      return resultNodes;
   }

   private selected(node: SecurityTreeNode): boolean {
      return this.treeControl ? this.treeControl.expansionModel.selected.some(
         (selectedNode: FlatSecurityTreeNode) =>
         selectedNode &&
         selectedNode.getData() === node) : false;
   }

   private deselect(node: SecurityTreeNode) {
      const nodes = this.treeControl.expansionModel.selected.filter(
         (selectedNode: FlatSecurityTreeNode) =>
            selectedNode &&
            selectedNode.getData() === node);
      this.treeControl.expansionModel.deselect(...nodes);
   }
}