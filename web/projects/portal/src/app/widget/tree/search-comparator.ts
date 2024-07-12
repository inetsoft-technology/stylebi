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
import { TreeNodeModel } from "./tree-node-model";
import { SecurityTreeNode } from "../../../../../em/src/app/settings/security/security-tree-view/security-tree-node";
import { RepositoryTreeNode } from "../../../../../em/src/app/settings/content/repository/repository-tree-node";

export class SearchComparator {
   constructor(search: string) {
      this.searchStr = !!search ? search.toLowerCase() : search;
   }

   public searchSort(a: TreeNodeModel | SecurityTreeNode | RepositoryTreeNode,
                     b: TreeNodeModel | SecurityTreeNode | RepositoryTreeNode): number
   {
      let ad = this.getMatchDegree(a);
      let bd = this.getMatchDegree(b);

      return bd - ad;
   }

   public searchSortStr(a: string, b: string): number {
      let ad = this.getDegree(a);
      let bd = this.getDegree(b);
      return bd - ad;
   }

   private getMatchDegree(node: TreeNodeModel | SecurityTreeNode | RepositoryTreeNode) {
      var degree: number;
      if(node instanceof  SecurityTreeNode) {
          degree = this.getDegree(node.identityID.name.toLowerCase());
      }
      else {
          degree = this.getDegree(node.label.toLowerCase());
      }
      if(node.children == null) {
         return degree;
      }

      for(let child of node.children) {
         degree = Math.max(degree, this.getMatchDegree(child));
      }

      return degree;
   }

   private getDegree(name: string) {
      name = !!name ? name.toLowerCase() : name;

      if(name == this.searchStr) {
         return 5;
      }

      if(name.indexOf(" ") != -1) {
         let names = name.split(" ");

         for(let i = 0; i < names.length; i++) {
            if(names[i] == this.searchStr) {
               return name.startsWith(this.searchStr) && i == 0 ? 4 : 3;
            }
         }
      }

      if(name.startsWith(this.searchStr)) {
         return 2;
      }

      if(name.includes(this.searchStr)) {
         return 1;
      }

      return 0;
   }

   private searchStr: string = null;
}
