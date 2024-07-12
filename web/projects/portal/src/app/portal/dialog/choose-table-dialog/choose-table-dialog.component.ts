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
import { Component, OnInit, Input, Output, EventEmitter, ViewChild } from "@angular/core";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { ConditionTypes } from "../../data/model/datasources/database/vpm/condition/condition-types.enum";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { HttpClient, HttpParams } from "@angular/common/http";
import { LocalStorage } from "../../../common/util/local-storage.util";
import { StringWrapper } from "../../data/model/datasources/database/string-wrapper";
import { DatabaseTreeNodeModel } from "../../data/model/datasources/database/physical-model/database-tree-node-model";
import { Observable } from "rxjs";
import { DatabaseTreeNodeType } from "../../data/model/datasources/database/database-tree-node-type";

const VPM_URI: string = "../api/data/vpm/";
const PHYSICAL_TREE_NODES_URI: string = "../api/data/physicalmodel/tree/nodes";
const PHYSICAL_TREE_ALL_NODES_URI: string = "../api/data/physicalmodel/tree/allNodes/";
const PHYSICAL_TABLE_PATH_URI: string = VPM_URI + "physicalModel/tablePath/";

@Component({
   selector: "choose-table-dialog",
   templateUrl: "choose-table-dialog.component.html",
   styleUrls: ["choose-table-dialog.component.scss"]
})
export class ChooseTableDialog implements OnInit {
   @Input() databaseName: string;
   @Input() conditionType: ConditionTypes;
   @Input() tableName: string;
   @Output() onCommit: EventEmitter<string> = new EventEmitter<string>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("tree") tree: TreeComponent;
   rootNode: TreeNodeModel = {
      children: []
   };
   ConditionTypes = ConditionTypes;
   loadingTree: boolean = false;
   private loadedFullTree: boolean = false;

   constructor(private httpClient: HttpClient) {
   }

   ngOnInit(): void {
      this.initRoot();
   }

   get title(): string {
      return this.conditionType == ConditionTypes.PHYSICAL_MODEL ?
         "_#(js:data.vpm.choosePhysicalModel)" : "_#(js:data.vpm.chooseTable)";
   }

   searchStart(start: boolean): void {
      if(start && !this.loadedFullTree) {
         this.initRoot(true);
      }
   }

   /**
    * Send request to get the top level nodes to show for the selected condition.
    */
   private initRoot(loadFullTree?: boolean): void {
      this.loadingTree = true;

      if(this.conditionType == ConditionTypes.TABLE) {
         this.openFolder(null, loadFullTree)
            .subscribe(
               data => {
                  this.loadingTree = false;
                  this.rootNode.children = data;

                  if(loadFullTree) {
                     this.loadedFullTree = true;

                     // after load full tree for search should expand all tree.
                     setTimeout(() => {
                        if(this.tree) {
                           this.tree.expandAll(this.rootNode);
                        }
                     }, 0);
                  }

                  if(!this.tableNameNull) {
                     this.initSelectedTable();
                  }
                  else {
                     LocalStorage.setItem("choose-table-tree-scroll-position", "0");
                  }
               },
               err => {
                  this.loadingTree = false;
               }
            );
      }
      else {
         let params: HttpParams = new HttpParams().set("database", this.databaseName);
         this.httpClient.get<TreeNodeModel[]>(VPM_URI + "physicalModels/nodes",
            { params: params})
            .subscribe(
               data => {
                  this.loadingTree = false;
                  this.rootNode.children = data;

                  if(!this.tableNameNull) {
                     const selectedChild: TreeNodeModel = data.find(node =>
                                                                    node.data == this.tableName);

                     if(!!selectedChild) {
                        this.tree.exclusiveSelectNode(selectedChild);
                     }
                  }
               },
               err => {
                  this.loadingTree = false;
               }
            );
      }
   }

   /**
    * Expand to and select the current table on init.
    */
   private initSelectedTable(): void {
      this.httpClient.post(PHYSICAL_TABLE_PATH_URI + this.databaseName,
                           new StringWrapper(this.tableName),
                           { responseType: "text" })
         .subscribe(
            data => {
               if(data != null) {
                  this.selectAndExpandToPath(data.replace(new RegExp('"', "g"), ""));
               }
            },
            err => {}
         );
   }

   /**
    * Search for and open folders on the way to find the node with the given path.
    * @param path the path to find
    * @param node the node being searched
    */
   private selectAndExpandToPath(path: string, node: TreeNodeModel = this.rootNode) {
      for(let child of node.children) {
         if((<DatabaseTreeNodeModel> child.data).path === path) {
            this.tree.selectAndExpandToNode(child);
         }
         else if(path.indexOf((<DatabaseTreeNodeModel> child.data).path + "/") == 0) {
            if(child.children.length > 0) {
               this.selectAndExpandToPath(path, child);
            }
            else {
               this.openFolder(child)
                  .subscribe(
                     data => {
                        child.children = data;
                        this.selectAndExpandToPath(path, child);
                     });
            }

            break;
         }
      }
   }

   /**
    * Check if the current table name is null.
    * @returns {boolean}   true if null
    */
   get tableNameNull(): boolean {
      return this.tableName == null || this.tableName.length == 0;
   }

   /**
    * Called when user selects node on tree. Navigate router to the selected nodes path.
    * @param nodes   the selected nodes on tree
    */
   selectNode(nodes: TreeNodeModel[]): void {
      if(nodes && nodes.length > 0) {
         const node: TreeNodeModel = nodes[0];

         if(this.conditionType == ConditionTypes.TABLE) {
            this.tableName = (<DatabaseTreeNodeModel> node.data).qualifiedName;
         }
         else {
            this.tableName = <string> node.data;
         }
      }
   }

   /**
    * Called when user expands folder on tree. Open folder and attach contents as children of node.
    * @param node    the expanded tree node
    */
   expandNode(node: TreeNodeModel): void {
      if(node == this.rootNode) {
         // tree calls expand on rootNode on init, ignore
         return;
      }

      this.loadingTree = true;

      this.openFolder(node)
         .subscribe(
            data => {
               this.loadingTree = false;
               node.children = data;
            },
            err => {
               this.loadingTree = false;
            }
         );
   }

   get databaseParr(): string {
      return this.databaseName.replace(/\//g, AssetEntryHelper.PATH_ARRAY_SEPARATOR);
   }

   /**
    * Send request to open a folder and get its children nodes.
    * @param node
    * @returns {Observable<Object>}
    */
   private openFolder(node: TreeNodeModel = null, loadFullTree?: boolean): Observable<TreeNodeModel[]> {
      if(loadFullTree) {
         let param: HttpParams = new HttpParams().set("database", this.databaseName);

         return this.httpClient.get<TreeNodeModel[]>(PHYSICAL_TREE_ALL_NODES_URI,
            { params: param });
      }
      else {
         const data = <DatabaseTreeNodeModel> node?.data;
         let path: string;
         let parr: string;

         if(!!data) {
            path = data.path;
            parr = data.parr;
         }
         else {
            path = this.databaseName;
            parr = this.databaseParr;
         }

         let params: HttpParams = new HttpParams()
            .set("parentPath", path);

         if(!!parr) {
            params = params.set("parr", parr);
         }

         return this.httpClient.get<TreeNodeModel[]>(PHYSICAL_TREE_NODES_URI, {params: params});
      }

   }

   getTreeNodeIcon(node: TreeNodeModel): string {
      if(!node) {
         return "folder-icon";
      }

      if(node.type === DatabaseTreeNodeType.FOLDER) {
         return "folder-icon";
      }
      else if(node.type === DatabaseTreeNodeType.PHYSICAL_VIEW) {
         return "partition-icon";
      }
      else if(node.type === DatabaseTreeNodeType.TABLE ||
         node.type === DatabaseTreeNodeType.ALIAS_TABLE)
      {
         return "data-table-icon";
      }

      return "folder-icon";
   }

   /**
    * Emit the updated permission on ok.
    */
   ok(): void {
      this.onCommit.emit(this.tableName);
   }

   /**
    * Cancel the dialog without changes.
    */
   cancel(): void {
      this.onCancel.emit("cancel");
   }
}
