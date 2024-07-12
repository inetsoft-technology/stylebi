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
import { FlatTreeDataSource } from "../common/util/tree/flat-tree-data-source";
import { Observable, of } from "rxjs";
import { TreeDataModel } from "../common/util/tree/flat-tree-model";
import { ScriptTreeFlatNode, ScriptTreeNode } from "./script-tree-node";
import { Tool } from "../../../../shared/util/tool";
import { SearchComparator } from "../../../../portal/src/app/widget/tree/search-comparator";

export class ScriptTreeDataSource extends FlatTreeDataSource<ScriptTreeFlatNode, ScriptTreeNode> {
   searchQuery: string;
   private previousState: ScriptTreeFlatNode[];
   private expandedState: ScriptTreeFlatNode[];

   constructor() {
      super();
   }

   public search(query: string): void {
      this.searchQuery = query;

      if(!query) {
         this.clearSearch();
         return;
      }

      if(!this.previousState) {
         this.expandedState = Tool.clone(this.treeControl.expansionModel.selected);
         this.treeControl.collapseAll();
         this.previousState = Tool.clone(this.data);
      }

      this.treeControl.expandAll();
      let nodes = this.data.slice();
      const stack = nodes.filter(n => n.level === 0).map(n => n.data);
      const flattened: ScriptTreeNode[] = [];

      while(stack.length > 0) {
         const node = stack.pop();
         flattened.push(node);

         if(node.children && node.children.length) {
            node.children.forEach(n => stack.push(n));
         }
      }

      const matches = flattened
         .filter(n => n.label && n.label.toLowerCase().includes(query.toLowerCase()));
      nodes.forEach(node => node.visible = true);

      // only need to recursively search from search results node
      nodes.forEach(rootNode => {
            this.sortSearchNodes(rootNode.data.children, query);
            this.queryNode(rootNode, matches);
         });

      // this.data = this.data;
   }

   public clearSearch(): void {
      this.searchQuery = null;

      if(!!this.previousState) {
         this.data = this.previousState;
         this.previousState = null;
         this.restoreExpandedState(this.data, this.expandedState);
      }
   }

   private addSearchResultNode() {
      let resultNode: ScriptTreeNode = new ScriptTreeNode([], "_#(js:Search Results)", null, null, false);

      // add all root node to SearchResultNode
      for(let treeNode of this.data) {
         if(treeNode.level != 0) {
            continue;
         }

         resultNode.children.push(treeNode.data);
      }

      this.data = [
         new ScriptTreeFlatNode(
            resultNode.label, 0, true, resultNode, false, true, null, this.getIcon)
      ];
   }

   private isSearchResultNode(node: ScriptTreeFlatNode): boolean {
      return !!this.previousState && node.level == 0;
   }

   public restoreExpandedState(nodes: ScriptTreeFlatNode[], selected: ScriptTreeFlatNode[]): void {
      this.treeControl.collapseAll();
      this.data = nodes;

      // Starting from the leftmost node expand the comparable node in the new list
      if(!!selected) {
         selected.sort((a, b) => a.level - b.level)
            .forEach((oldNode) => {
               const newNode = nodes.find((node) => oldNode.equals(node));

               if(newNode != null) {
                  this.treeControl.expand(newNode);
               }
            });
      }
   }

   private sortSearchNodes(nodes: ScriptTreeNode[], query: string): void {
      if(!nodes || nodes.length == 1) {
         return;
      }

      nodes.sort((a, b) => new SearchComparator(query).searchSort(a, b));

      nodes.forEach((node) => {
         if(!!node.children) {
            this.sortSearchNodes(node.children, query);
         }
      });
   }

   private queryNode(node: ScriptTreeFlatNode, matches: ScriptTreeNode[]): boolean {
      if(node == null) {
         return false;
      }

      let visible: boolean;

      if(this.nodeMatches(node.data, matches)) {
         visible = true;
      }

      if(node.expandable && node.data.children && node.data.children.length) {
         if((node.level == 1)) {
            this.treeControl.expand(node);
         }

         for(let i = 0; i < node.data.children.length; i++) {
            const child = node.data.children[i];
            const found = this.data.find(flatNode => flatNode.data === child);

            if(found) {
               this.treeControl.expand(node);
            }

            if(this.queryNode(found, matches)) {
               visible = true;
            }
         }
      }

      return node.visible = visible;
   }

   private nodeMatches(node: ScriptTreeNode, matches: ScriptTreeNode[]): boolean {
      return matches.some(n => n === node);
   }

   private readonly getIcon = function(expanded: boolean): string {
      return this.data.leaf ? "worksheet-icon" : "folder-icon";
   };

   protected getChildren(node: ScriptTreeFlatNode): Observable<TreeDataModel<ScriptTreeNode>> {
      return of({
         nodes: node.data.children
      });
   }

   transform(model: TreeDataModel<ScriptTreeNode>, level: number): ScriptTreeFlatNode[] {
      if(model.nodes && model.nodes.length > 0) {
         return model.nodes.map((scriptTreeModel: ScriptTreeNode) => {
            if(scriptTreeModel) {
               return new ScriptTreeFlatNode(scriptTreeModel.label, level, !scriptTreeModel.leaf , scriptTreeModel, false, true, null, this.getIcon);
            }

            return null;
         });
      }

      return [];
   }
}
