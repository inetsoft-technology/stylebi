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
import { HttpParams } from "@angular/common/http";
import { Component, EventEmitter, Input, Output, ViewChild } from "@angular/core";
import { IdentityType } from "../../../../../shared/data/identity-type";
import { ModelService } from "../services/model.service";
import { TreeNodeModel } from "../tree/tree-node-model";
import { TreeComponent } from "../tree/tree.component";
import { SearchComparator } from "../tree/search-comparator";

const EXPAND_IDENTITY_NODE_URI = "../api/vs/expand-identity-node";

@Component({
  selector: "identity-tree",
  templateUrl: "identity-tree.component.html"
})
export class IdentityTreeComponent {
   @ViewChild("tree") tree: TreeComponent;
   @Input() root: TreeNodeModel;
   @Input() showRoot: boolean = false;
   @Input() searchMode: boolean = false;
   @Input() searchText: string = "";
   @Input() multiSelect: boolean = true;
   @Input() isBurst: boolean = false;
   @Input() selectedNodes: TreeNodeModel[] = [];
   @Output() nodesSelected: EventEmitter<TreeNodeModel[]> =
      new EventEmitter<TreeNodeModel[]>();

   constructor(private modelService: ModelService) {
   }

   iconFunction(node: TreeNodeModel): string {
      if(node.type == String(IdentityType.USER) || node.type == String(IdentityType.USERS)) {
         return "account-icon";
      }
      else if(node.type == String(IdentityType.GROUP) || node.type == String(IdentityType.GROUPS)) {
         return "user-group-icon";
      }

      return null;
   }

   nodeExpanded(node: TreeNodeModel): void {
      if(!node.children || node.children.length == 0) {
         let params = new HttpParams()
            .set("type", node.type)
            .set("searchMode", String(this.searchMode))
            .set("isBurst", String(this.isBurst))
            .set("searchString", this.searchText);

         if(node.label) {
            params = params.set("name", node.label);
         }

         this.modelService.getModel(EXPAND_IDENTITY_NODE_URI, params)
            .subscribe(
               (data: TreeNodeModel[]) => {
                  node.children = data;
                  let comparator = new SearchComparator(this.searchText);
                  node.children.sort((a, b) => comparator.searchSort(a, b));
               },
               (err) => {
                  // TODO handle error
                  console.error("Failed to expand identity node: ", err);
               }
            );
      }
   }

   _nodeSelected(nodes: TreeNodeModel[]): void {
      this.nodesSelected.emit(nodes);
   }
}
