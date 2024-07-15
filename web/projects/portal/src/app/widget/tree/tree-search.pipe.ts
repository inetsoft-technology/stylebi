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
import { Pipe, PipeTransform } from "@angular/core";
import { TreeNodeModel } from "./tree-node-model";
import { SearchComparator } from "./search-comparator";

@Pipe({
  name: "search"
})
export class TreeSearchPipe implements PipeTransform {
   transform(nodes: TreeNodeModel[], input: string, onlySearchLeaf: boolean = false,
             searchEndNode?: (node: TreeNodeModel) => boolean): TreeNodeModel[]
   {
      if(input && input.trim()) {
         let searchSort = new SearchComparator(input);

         return nodes.filter((v: any) =>
            this.contains(v, input.trim().toLocaleLowerCase(), onlySearchLeaf, searchEndNode))
            .sort((a, b) => searchSort.searchSort(a, b));
      }

      return nodes;
   }

   contains(node: TreeNodeModel, str: string, onlySearchLeaf: boolean,
            searchEndNode?: (node: TreeNodeModel) => boolean)
   {
      let isSearchEnd = searchEndNode && searchEndNode(node);

      // not yet loaded
      if(!node.leaf && !node.children && !isSearchEnd) {
         return true;
      }

      if((!onlySearchLeaf || node.leaf) && TreeSearchPipe.nodeMatch(node, str)) {
         return true;
      }

      if(!isSearchEnd && node.children && node.children.length > 0) {
         return node.children.some(x => this.contains(x, str, onlySearchLeaf));
      }

      return false;
   }

   public static nodeMatch(node: TreeNodeModel, str: string): boolean {
      return node?.label?.toLowerCase().indexOf(str) >= 0;
   }
}
