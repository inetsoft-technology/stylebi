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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Observable, Subject } from "rxjs";
import { map } from "rxjs/operators";
import { Tool } from "../../../../../../shared/util/tool";
import { FlatTreeDataSource } from "../../../common/util/tree/flat-tree-data-source";
import { FlatTreeNode, TreeDataModel } from "../../../common/util/tree/flat-tree-model";
import { DataSpaceTreeNode } from "../../content/data-space/data-space-tree-node";

export class ShapesTreeDataSource extends FlatTreeDataSource<FlatTreeNode<DataSpaceTreeNode>, DataSpaceTreeNode> {
   private _data = new Subject<void>();
   private orgId: string;
   constructor(private http: HttpClient, orgId: string) {
      super();
      this.orgId = orgId;
      this.init(orgId);
   }

   public init(orgId: string) {
      let rootPath = !!orgId ? "portal/" + orgId + "/shapes" : "portal/shapes";
      // get initial tree model
      const params = new HttpParams()
         .set("path", Tool.byteEncode(rootPath))
         .set("init", true);
      this.http.get<any>("../api/em/content/data-space/shapes/tree", {params})
         .pipe(map((model) => this.transform(model, 0)))
         .subscribe((nodes) => {
            this.data = nodes;

            // expand the root node
            if(!!nodes && nodes.length > 0) {
               this.treeControl.expand(nodes[0]);
            }

            this._data.next()
         });
   }


   public refresh() {
      this.init(this.orgId);
   }

   get dataSubject(): Observable<void> {
      return this._data.asObservable();
   }

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
      return this.http.get<any>("../api/em/content/data-space/shapes/tree", {params})
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

}
