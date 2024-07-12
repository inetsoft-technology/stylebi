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
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { SearchComparator } from "../../widget/tree/search-comparator";
import { Tool } from "../../../../../shared/util/tool";

const USE_VIRTUAL_SCROLL_NODE_COUNT: number = 500;

export namespace TreeTool {
   export const TREE_NODE_HEIGHT: number = 32;

   export function expandAllNodes(node: TreeNodeModel) {
      if(node.leaf) {
         return;
      }

      node.expanded = true;

      if(node.children) {
         node.children.forEach(child => expandAllNodes(child));
      }
   }

   /**
    * Get a flattened list of the tree node models
    */
   export function getFlattenedNodes(node: TreeNodeModel, searchComparator?: SearchComparator): TreeNodeModel[]
   {
      if(!node) {
         return [];
      }

      let flattenedNodes: TreeNodeModel[] = [];
      let childrenNode = node.children;

      if(childrenNode) {
         if(searchComparator) {
            childrenNode = [...childrenNode].sort((a, b) => searchComparator.searchSort(a, b));
         }

         for(let child of childrenNode) {
            if(child) {
               child.parent = node;
            }

            const children = getChildrenInOrder(child, searchComparator);
            flattenedNodes = flattenedNodes.concat(children);
         }
      }

      return flattenedNodes;
   }

   // eslint-disable-next-line no-inner-declarations
   function getChildrenInOrder(node: TreeNodeModel, searchComparator?: SearchComparator): TreeNodeModel[] {
      if(node.children != null) {
         let childNodes = [node];

         if(node.expanded) {
            let nodeChildren = [...node.children];

            if(searchComparator) {
               nodeChildren.sort((a, b) => searchComparator.searchSort(a, b));
            }

            for(let child of nodeChildren){
               if(child) {
                  child.parent = node;
               }

               const children = getChildrenInOrder(child, searchComparator);
               childNodes = childNodes.concat(children);
            }
         }

         return childNodes;
      }

      return [node];
   }

   export function needUseVirtualScroll(root: TreeNodeModel): boolean {
      return nodeCountMoreThanLimit(root, USE_VIRTUAL_SCROLL_NODE_COUNT, { count: 0 });
   }

   // eslint-disable-next-line no-inner-declarations
   function nodeCountMoreThanLimit(node: TreeNodeModel, limitCount: number,
                                   currentCount: StatisticalCountModel): boolean
   {
      currentCount.count++;

      if(currentCount.count > limitCount) {
         return true;
      }

      if(node && node.children) {
        return node.children
           .some(child => nodeCountMoreThanLimit(child, limitCount, currentCount));
      }

      return false;
   }

   interface StatisticalCountModel {
      count: number;
   }
}
