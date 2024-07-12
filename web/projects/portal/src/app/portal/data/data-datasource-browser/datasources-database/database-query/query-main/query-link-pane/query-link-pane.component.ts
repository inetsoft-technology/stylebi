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
import { HttpClient, HttpHeaders, HttpParams } from "@angular/common/http";
import { Component, Input, OnInit, ViewChild } from "@angular/core";
import { Observable } from "rxjs";
import { GuiTool } from "../../../../../../../common/util/gui-tool";
import { TreeNodeModel } from "../../../../../../../widget/tree/tree-node-model";
import {
   QueryLinkPaneModel
} from "../../../../../model/datasources/database/query/query-link-pane-model";
import { QueryTableModel } from "../../../../../model/datasources/database/query/query-table-model";
import {
   QueryLinkGraphPaneComponent
} from "./query-link-graph-pane/query-link-graph-pane.component";
import { DataQueryModelService } from "../../data-query-model.service";

const DATA_SOURCE_TREE_URI = "../api/data/datasource/query/data-source-tree";

@Component({
   selector: "query-link-pane",
   templateUrl: "./query-link-pane.component.html",
   styleUrls: [
      "./query-link-pane.component.scss",
      "../../../database-physical-model/database-model-pane.scss"
   ]
})
export class QueryLinkPaneComponent implements OnInit {
   @Input() queryName: string;
   @Input() runtimeID: string;
   @Input() linkModel: QueryLinkPaneModel;
   @Input() databaseName: string;
   @Input() dataSourceTreeRoot: TreeNodeModel;
   @ViewChild("graphPane") graphPane: QueryLinkGraphPaneComponent;
   readonly headers: HttpHeaders;
   editingTable: QueryTableModel;
   selectedNodes: TreeNodeModel[] = [];
   selectedGraphNodePath: string;

   constructor(private http: HttpClient, public queryModelService: DataQueryModelService) {
      this.headers = new HttpHeaders({
         "Content-Type": "application/json"
      });
   }

   ngOnInit() {
      this.initDataSourceTree();
   }

   initDataSourceTree(): void {
      if(!this.dataSourceTreeRoot) {
         this.getDataSourceTree(null).subscribe(data => {
            this.dataSourceTreeRoot = data;
         });
      }
   }

   getDataSourceTree(entry: any): Observable<TreeNodeModel> {
      const params = new HttpParams()
         .set("dataSource", this.databaseName)
         .set("columnLevel", false);
      const options = {headers: this.headers, params: params};

      return this.http.post<TreeNodeModel>(DATA_SOURCE_TREE_URI, entry, options);
   }

   nodeExpanded(node: TreeNodeModel): void {
      if(node.data == null) {
         return;
      }

      this.getDataSourceTree(node.data).subscribe((data) =>
         node.children = data.children);
   }

   nodeClicked(node: TreeNodeModel): void {
      this.selectNode(node);
   }

   iconFunction(node: TreeNodeModel): string {
      return GuiTool.getTreeNodeIconClass(node, "");
   }

   selectGraphNode(node: TreeNodeModel): void {
      this.selectedGraphNodePath = node?.data?.path;
   }

   selectNode(node: TreeNodeModel): void {
      this.selectGraphNode(node);
      this.changeEditingTable(node);
   }

   changeEditingTable(node: TreeNodeModel) {
      this.changeEditingTableByName(node?.data.qualifiedName);
   }

   changeEditingTableByName(qualifiedName: string) {
      if(!!qualifiedName) {
         this.editingTable = this.linkModel.tables
            .find(table => qualifiedName === table.qualifiedName);
      }
      else {
         this.editingTable = null;
      }
   }

   onNodeSelected(nodePath: string): void {
      if(this.dataSourceTreeRoot) {
         this.selectedNodes = [];
         this.findSelectedNode(this.dataSourceTreeRoot.children, nodePath);
      }
   }

   findSelectedNode(nodes: TreeNodeModel[], selectedNodePath: string): void {
      if(!!nodes && nodes.length > 0) {
         for(let i = 0; i < nodes.length; i++) {
            const node = nodes[i];
            const currentNodePath = node.data?.path;

            if(node.leaf && currentNodePath == selectedNodePath) {
               this.selectedNodes = [nodes[i]];
            }
            else if(selectedNodePath.indexOf(currentNodePath + "/") == 0 && !node.leaf) {
               node.expanded = true;

               if(node.children.length == 0) {
                  this.getDataSourceTree(node.data).subscribe(data => {
                     node.children = data.children;
                     this.findSelectedNode(node.children, selectedNodePath);

                     if(this.selectedNodes.length > 0) {
                        return;
                     }
                  });
               }
               else {
                  this.findSelectedNode(node.children, selectedNodePath);

                  if(this.selectedNodes.length > 0) {
                     return;
                  }
               }
            }

            if(this.selectedNodes.length > 0) {
               return;
            }
         }
      }

      return null;
   }

   public isJoinEditView(): boolean {
      if(!!this.graphPane) {
         return this.graphPane.isJoinEditView();
      }

      return false;
   }
}
