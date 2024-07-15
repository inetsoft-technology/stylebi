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
import { Component, HostListener, Input } from "@angular/core";
import { MatDialogRef } from "@angular/material/dialog";
import { RepositoryEntryType } from "../../../../../../../shared/data/repository-entry-type.enum";
import { FlatTreeSelectNodeEvent } from "../../../../common/util/tree/flat-tree-view.component";
import { RepositoryTreeDataSource } from "../repository-tree-data-source";
import { RepositoryFlatNode, RepositoryTreeNode } from "../repository-tree-node";

@Component({
   selector: "em-move-asset-dialog",
   templateUrl: "./move-asset-dialog.component.html",
   styleUrls: ["./move-asset-dialog.component.scss"],
   providers: [RepositoryTreeDataSource]
})
export class MoveAssetDialogComponent {
   @Input() onlyForDatabase: boolean = false;
   @Input() rootType: RepositoryEntryType;
   @Input() defaultSelectedPath: string;
   @Input() nodeFilter: (node: RepositoryFlatNode | RepositoryTreeNode) => boolean;
   selectedNodes: RepositoryFlatNode[] = [];
   loading = false;
   private inited: boolean = false;

   constructor(private dialogRef: MatDialogRef<MoveAssetDialogComponent>,
               public dataSource: RepositoryTreeDataSource)
   {
      this.dataSource.dataSubject.subscribe(() => {
         this.dataSource.data = this.filterDataSource();
      });
      this.dataSource.loading.subscribe((loading) => this.loading = loading);

      // handle lazy loaded nodes
      this.dataSource.nodeToggled.subscribe((node) => {
         if(node.data.owner != null &&
            node.data.type === (RepositoryEntryType.USER | RepositoryEntryType.FOLDER))
         {
            this.filterChildNodes(node.data.children);
            this.dataSource.data = this.dataSource.data.filter((data) => {
               return (data.data.type & RepositoryEntryType.FOLDER) == RepositoryEntryType.FOLDER;
            });
         }
      });
   }

   private filterDataSource(): RepositoryFlatNode[] {
      let dataSource: RepositoryFlatNode[] = this.dataSource.data.filter((data) => {

         return (!!!this.rootType || data.data.type == this.rootType)
            && data.data.type != RepositoryEntryType.TRASHCAN_FOLDER
            && data.data.type != RepositoryEntryType.RECYCLEBIN_FOLDER
            && data.data.type != RepositoryEntryType.LIBRARY_FOLDER
            && (data.data.type & RepositoryEntryType.DASHBOARD_FOLDER) != RepositoryEntryType.DASHBOARD_FOLDER
            && data.data.path != "Users' Dashboards"
            && data.data.path != "Schedule Tasks"
            && (data.data.type & RepositoryEntryType.FOLDER) == RepositoryEntryType.FOLDER;
      });

      for(let node of dataSource) {
         this.filterChildNodes(node.data.children);
      }

      return dataSource;
   }

   private filterChildNodes(nodes: RepositoryTreeNode[]) {
      for(let i = nodes.length - 1; i >= 0; i--) {
         if(!this.isNodeVisible(nodes[i])) {
            nodes.splice(i, 1);
         }
         else if(!!nodes[i].children && nodes[i].children.length > 0) {
            this.filterChildNodes(nodes[i].children);
         }
      }
   }

   private isNodeVisible(node: RepositoryTreeNode): boolean {
      if(this.onlyForDatabase) {
         return node?.type == RepositoryEntryType.DATA_SOURCE_FOLDER ||
            node?.type == (RepositoryEntryType.DATA_SOURCE | RepositoryEntryType.FOLDER) ||
            node?.type == RepositoryEntryType.DATA_MODEL_FOLDER ||
            node?.type == (RepositoryEntryType.QUERY | RepositoryEntryType.FOLDER);
      }

      return (node.type & RepositoryEntryType.FOLDER) == RepositoryEntryType.FOLDER &&
         node.type != (RepositoryEntryType.DATA_SOURCE | RepositoryEntryType.FOLDER) &&
         (!this.nodeFilter || this.nodeFilter(node));
   }

   public nodeSelected(evt: FlatTreeSelectNodeEvent): void {
      this.selectedNodes = [<RepositoryFlatNode>evt.node];
   }

   public finish(): void {
      this.dialogRef.close(this.selectedNodes[0]);
   }

   public cancel(): void {
      this.dialogRef.close();
   }

   @HostListener("window:keyup.enter", [])
   onEnter() {
      this.finish();
   }

   @HostListener("window:keyup.esc", [])
   onEsc() {
      this.cancel();
   }
}
