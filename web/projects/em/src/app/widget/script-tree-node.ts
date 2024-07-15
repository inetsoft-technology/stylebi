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
import { FlatTreeNode, TreeDataNode } from "../common/util/tree/flat-tree-model";
import { SearchComparator } from "../../../../portal/src/app/widget/tree/search-comparator";

export class ScriptTreeNode implements TreeDataNode<ScriptTreeNode> {
   constructor(children: ScriptTreeNode[], label: string, data: any, icon: string, leaf: boolean) {
      this.children = children;
      this.label = label;
      this.icon = icon;
      this.leaf = leaf;
      this.data = data;
   }

   public filter(filterString: string): ScriptTreeNode {
      // non-strict for number/string comparison
      if((this.label == filterString ||
         filterString && this.label.toLowerCase().includes(filterString.toLowerCase())))
      {
         return new ScriptTreeNode(this.children, this.label, this.data, this.icon, this.leaf);
      }

      if(this.children != null) {
         const children = this.children
            .map((child) => child.filter(filterString))
            .filter((child) => child != null)
            .sort((a, b) => new SearchComparator(filterString).searchSort(a, b));

         if(children.length > 0) {
            const node = new ScriptTreeNode(this.children, this.label, this.data, this.icon, this.leaf);
            // node.toggle(true);
            return node;
         }
      }

      return null;
   }

   children: ScriptTreeNode[];
   label: string;
   data: any;
   icon: string;
   leaf: boolean;
}

export class ScriptTreeFlatNode extends FlatTreeNode<ScriptTreeNode> {
   /**
    * Checks whether a pair of nodes should be treated equally for tracking expansion state
    */
   public equals(node: this): boolean {
      return node != null &&
         this.data.label === node.data.label &&
         this.level === node.level;
   }
}
