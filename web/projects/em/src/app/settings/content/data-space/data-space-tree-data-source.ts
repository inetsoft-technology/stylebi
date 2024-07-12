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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable, Subject } from "rxjs";
import { map } from "rxjs/operators";
import { Tool } from "../../../../../../shared/util/tool";
import { FlatTreeDataSource } from "../../../common/util/tree/flat-tree-data-source";
import { FlatTreeNode, TreeDataModel } from "../../../common/util/tree/flat-tree-model";
import { DataSpaceFileChange } from "./data-space-editor-page/data-space-editor-page.component";
import { DataSpaceTreeNode } from "./data-space-tree-node";

@Injectable()
export class DataSpaceTreeDataSource extends FlatTreeDataSource<FlatTreeNode<DataSpaceTreeNode>, DataSpaceTreeNode> {
   private nodeSelectedSubject = new Subject<FlatTreeNode<DataSpaceTreeNode>>();

   constructor(private http: HttpClient) {
      super();
      this.init();
   }

   public nodeSelected(): Observable<FlatTreeNode<DataSpaceTreeNode>> {
      return this.nodeSelectedSubject.asObservable();
   }

   public init() {
      // get initial tree model
      this.http.get<any>("../api/em/content/data-space/tree")
         .pipe(map((model) => this.transform(model, 0)))
         .subscribe((nodes) => {
            this.data = nodes;

            // expand the root node
            if(!!nodes && nodes.length > 0) {
               this.treeControl.expand(nodes[0]);
            }
         });
   }

   public refresh(): void {
      const expanded = this.treeControl.expansionModel.selected.slice();
      this.data = [];
      this.treeControl.collapseAll();

      this.http.get<any>("../api/em/content/data-space/tree")
         .pipe(map(model => this.transform(model, 0)))
         .subscribe(nodes => {
            this.data = nodes;

            if(!!nodes && nodes.length > 0) {
               this.treeControl.expand(nodes[0]);

               if(expanded.length === 0) {
                  const node = this.treeControl.dataNodes.find(n => n.data.path === "/");

                  if(!!node) {
                     this.treeControl.expand(node);
                  }
               }
               else {
                  this.treeControl.dataNodes.forEach(node => {
                     if(expanded.findIndex(n => n.data.path === node.data.path) !== -1) {
                        this.treeControl.expand(node);
                     }
                  });
               }
            }
         });
   }

   /**
    * Method for deleting a node in the data space tree
    * @param node The node that we want to delete; can be a file or folder
    */
   public deleteNode(node: DataSpaceTreeNode): void {
      let index = this.data.findIndex((indexData) => indexData.data.path === node.path);

      if(index === -1) {
         return;
      }

      const flatNode = this.data[index];
      let endIndex = index;

      for(let i = index + 1; i < this.data.length; i++) {
         if(this.data[i].level > flatNode.level) {
            endIndex = i;
         }
         else {
            break;
         }
      }

      this.data.splice(index, endIndex - index + 1);
      this.removeFromParent(node);
      this.dataChange.next(this.data);
   }

   /**
    * Method for removing a node from its parent on the data space tree after deletion
    * @param node  The node that is being removed from the parent
    */
   private removeFromParent(node: DataSpaceTreeNode): void {
      let index: number = node.path.lastIndexOf("/");
      let parentPath: string = index < 0 ? "/" : node.path.substring(0, index);
      let parent: FlatTreeNode<DataSpaceTreeNode> = this.data.find(flatNode => flatNode.data.path === parentPath);

      if(!!parent && !!parent.data.children) {
         parent.data.children = parent.data.children.filter((child: DataSpaceTreeNode) => child.path != node.path);
      }
   }

   protected getChildren(node: FlatTreeNode<DataSpaceTreeNode>): Observable<TreeDataModel<DataSpaceTreeNode>> {
      const params = new HttpParams().set("path", Tool.byteEncode(node.data.path));
      return this.http.get<any>("../api/em/content/data-space/tree", {params});
   }

   protected transform(model: TreeDataModel<DataSpaceTreeNode>,
                       level: number): FlatTreeNode<DataSpaceTreeNode>[] {
      return model.nodes.map((node) =>
         new FlatTreeNode(node.label, level, node.folder, node, false, true, null, this.getIcon));
   }

   private readonly getIcon = function(expanded: boolean) {
      if(this.data.folder) {
         return expanded ? "folder-open-icon" : "folder-icon";
      }
      else {
         return "card-text-outline-icon";
      }
   };

   public fetchAndSelectNode(path: string) {
      const index = path.lastIndexOf("/");
      const parentPath = index < 0 ? "/" : path.substring(0, index);
      const parentIndex = this.data.findIndex(node => node.data.path === parentPath);
      const parentNode = this.data[parentIndex];

      if(!!parentNode) {
         if(!!parentNode.data.children) {
            const params = new HttpParams().set("path", Tool.byteEncode(path));
            this.http.get<any>("../api/em/content/data-space/tree/node", {params}).subscribe((node) => {
               this.treeControl.expand(parentNode);
               // insert the node in the correct place in the tree
               parentNode.data.children.push(node);
               parentNode.data.children.sort(this.sortNodes);
               const transformedNode = this.transform({nodes: [node]}, parentNode.level + 1)[0];
               const nodeLevel = this.treeControl.getLevel(parentNode) + 1;
               let currIndex = parentIndex + parentNode.data.children.indexOf(node) + 1;

                  for(; currIndex < this.treeControl.dataNodes.length; currIndex++) {
                     let tempNode = this.treeControl.dataNodes[currIndex];

                     if(this.treeControl.getLevel(tempNode) <= nodeLevel) {
                        break;
                     }
                  }

               this.data.splice(currIndex, 0, transformedNode);
               this.dataChange.next(this.data);
               this.nodeSelectedSubject.next(transformedNode);
            });
         }
         else {
            let subscription = this.dataChange.subscribe(() => {
               let childNode = this.data.find((node) => node.data.path === path);

               if(!!childNode) {
                  this.nodeSelectedSubject.next(childNode);
                  subscription.unsubscribe();
               }
            });

            this.treeControl.toggle(parentNode);
         }
      }
   }

   private sortNodes(a: DataSpaceTreeNode, b: DataSpaceTreeNode): number {
      if(a.folder && !b.folder) {
         return -1;
      }
      else if(!a.folder && b.folder) {
         return 1;
      }
      else {
         return a.label.localeCompare(b.label);
      }
   }

   public updateAndSelectNode(change: DataSpaceFileChange) {
      const index = this.data.findIndex((n) => n.data.path === change.oldPath);
      const node = this.data[index];
      const nindex = this.data.findIndex((n) => n.data.path === change.newPath);
      const nnode = this.data[nindex];

      if(Tool.isEquals(node, nnode)) {
         return;
      }

      if(!!nnode) {
         this.deleteNode(nnode.data);
      }

      if(!!node) {
         if(node.expandable && node.data.path !== change.newPath) {
            // If folder name changes, need to update paths for files inside it
            this.deleteNode(node.data);
            node.data.path = change.newPath;
            node.data.label = change.newName;
            this.fetchAndSelectNode(node.data.path);
         }
         else {
            node.data.path = change.newPath;
            node.data.label = change.newName;
            const newNode = this.transform({nodes: [node.data]}, node.level)[0];
            this.data[index] = newNode;
            // this.data = this.data;
            this.nodeSelectedSubject.next(newNode);
         }
      }
   }
}
