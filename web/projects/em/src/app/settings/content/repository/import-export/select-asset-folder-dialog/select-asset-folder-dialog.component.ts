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
import { Component, Input, OnDestroy } from "@angular/core";
import { RepositoryTreeDataSource } from "../../repository-tree-data-source";
import { RepositoryFlatNode, RepositoryTreeNode } from "../../repository-tree-node";
import { MatDialog, MatDialogRef } from "@angular/material/dialog";
import {
   FlatTreeContextMenuEvent,
   FlatTreeSelectNodeEvent
} from "../../../../../common/util/tree/flat-tree-view.component";
import { RepositoryEntryType } from "../../../../../../../../shared/data/repository-entry-type.enum";
import { SelectAssetFolderDataSource } from "./select-asset-folder-data-source.service";
import { ContentRepositoryService } from "../../content-repository-page/content-repository.service";
import { InputNameDialogComponent } from "../input-name-dialog/input-name-dialog.component";
import { catchError, map, takeUntil } from "rxjs/operators";
import { Observable, Subject } from "rxjs";
import { Tool } from "../../../../../../../../shared/util/tool";
import { TreeSelectionEvent } from "../../content-repository-page/content-repository-page.component";

@Component({
   selector: "em-select-asset-folder-dialog",
   templateUrl: "./select-asset-folder-dialog.component.html",
   styleUrls: ["./select-asset-folder-dialog.component.scss"],
   providers: [ SelectAssetFolderDataSource ]
})
export class SelectAssetFolderDialogComponent implements OnDestroy {
   @Input() defaultSelectedNode: RepositoryFlatNode;
   @Input() rootChildrenFilter: (node: RepositoryTreeNode) => boolean;
   @Input() rootNodesTypeFun: (dataSource: RepositoryTreeDataSource) => RepositoryEntryType[];
   selectedNodes: RepositoryFlatNode[] = [];
   private _loading = false;
   private inited: boolean = false;
   private destroy$ = new Subject<void>();
   private afterLoadingRefreshData: TreeSelectionEvent;

   constructor(private dialog: MatDialog, private dialogRef: MatDialogRef<SelectAssetFolderDialogComponent>,
               private repositoryService: ContentRepositoryService,
               public dataSource: SelectAssetFolderDataSource)
   {
      this.dataSource.contextMenusEnabled = true;
      this.dataSource.setLazyLoadChildrenFilter((nodes: RepositoryTreeNode[]) => this.filterChildNodes(nodes));
      this.dataSource.dataSubject.subscribe(() => {
         this.dataSource.data = this.filterDataSource();
         this.initTreeStatus();
      });
      this.dataSource.loading.subscribe((loading) => this.loading = loading);

      this.changes.pipe(
         map(node => new TreeSelectionEvent(false, node))
      )
      .subscribe(event => {
         if(event && event.data) {
            if(!this.loading) {
               this.refreshTree(event);
            }
            else {
               this.afterLoadingRefreshData = event;
            }
         }
      });


      this.dataSource.repositoryChanged.pipe(takeUntil(this.destroy$)).subscribe(() => this.refreshTree());
   }

   ngOnDestroy(): void {
      this.destroy$.next();
   }

   get loading(): boolean {
      return this._loading;
   }

   set loading(loading: boolean) {
      let oldValue = this._loading;
      this._loading = loading;

      if(oldValue && !this._loading && !!this.afterLoadingRefreshData) {
         this.refreshTree(this.afterLoadingRefreshData);
         this.afterLoadingRefreshData = null;
      }
   }

   get changes(): Observable<any> {
      return this.repositoryService.changes.pipe(
         catchError(() => {
            this.refreshTree();
            return this.changes;
         }),
         takeUntil(this.destroy$)
      );
   }

   refreshTree(event?: TreeSelectionEvent) {
      let owner = event && event.data ? event.data.owner : null;
      this.dataSource.refresh(owner).subscribe((newNodes) => {
         let oldExpands = this.dataSource.treeControl.expansionModel.selected;
         let selected = this.selectedNodes;
         this.selectedNodes = [];

         if(selected?.length > 0) {
            while(selected.length > 0) {
               const node = selected.pop();
               const newNode = newNodes.find((n) => node.equals(n));

               if(newNode != null) {
                  this.selectedNodes.push(newNode);
               }
            }
         }

         let expandNodes = [];

         if(oldExpands) {
            for(let select of oldExpands) {
               const newNode = newNodes.find((n) => select.equals(n));

               if(newNode != null) {
                  expandNodes.push(newNode);
               }
            }
         }

         let newSelectNodeData;

         if(!!event?.data) {
            const data = event.data;
            let parent;

            for(let n of newNodes) {
               newSelectNodeData = n.data.children &&
                  n.data.children.find(c => c.type === data.type && c.path === data.path && c.owner == data.owner);

               if(newSelectNodeData) {
                  parent = n;
                  break;
               }
            }

            if(!!parent) {
               expandNodes.push(parent);
            }
         }

         this.dataSource.data = this.filterDataSource();
         this.dataSource.restoreExpandedState(this.dataSource.data, expandNodes);

         if((!event?.data || !newSelectNodeData) && this.selectedNodes?.length == 1) {
            newSelectNodeData = this.selectedNodes[0]?.data;
         }

         if(newSelectNodeData) {
            let newSelectNode = this.dataSource.data.filter(c => !!c.data)
               .find(c => c.data.type === newSelectNodeData.type && c.data.path === newSelectNodeData.path &&
               c.data.owner == newSelectNodeData.owner);

            if(newSelectNode) {
               this.selectedNodes = [ newSelectNode ];
            }
         }
      });
   }

   private initTreeStatus(): void {
      setTimeout(() => {
         if(!this.inited && this.dataSource && this.defaultSelectedNode) {
            if(!this.defaultSelectedNode) {
               return;
            }

            let type = this.defaultSelectedNode.data.type;
            let user = this.defaultSelectedNode.data.owner;
            let rootType = -1;

            if(!!user) {
               rootType = RepositoryEntryType.USER_FOLDER;
            }
            else if(type == RepositoryEntryType.REPOSITORY_FOLDER) {
               rootType = RepositoryEntryType.REPOSITORY_FOLDER;
            }
            else if(type == RepositoryEntryType.WORKSHEET_FOLDER) {
               rootType = RepositoryEntryType.WORKSHEET_FOLDER;
            }

            if(rootType == -1) {
               return;
            }

            let defaultSelectedPath = this.defaultSelectedNode.data.path;
            let rootIndex = this.dataSource?.treeControl?.dataNodes.findIndex((node) => rootType == node.data.type);

            if(rootIndex < 0) {
               return;
            }

            if(rootType == RepositoryEntryType.USER_FOLDER) {
               this.dataSource?.treeControl.expand(this.dataSource?.treeControl?.dataNodes[rootIndex]);
               let userRootIndex = this.dataSource?.treeControl?.dataNodes.findIndex(node =>
                  node?.data?.path == Tool.MY_REPORTS && node?.data?.owner == user);

               if(userRootIndex >= 0) {
                  rootIndex = userRootIndex;
               }
            }

            let parts = ["/"].concat(defaultSelectedPath.split("/"));

            parts.forEach(part => {
               let folder = this.dataSource?.treeControl.dataNodes?.slice(rootIndex).find(node =>
                  node?.data?.path?.endsWith(part));

               if(folder && folder.expandable) {
                  if(folder?.data?.path == defaultSelectedPath) {
                     this.selectedNodes = [folder];
                  }
                  else {
                     this.dataSource?.treeControl.expand(folder);
                  }
               }
            });

            this.inited = true;
         }
      });
   }

   private filterDataSource(): RepositoryFlatNode[] {
      if(!this.rootNodesTypeFun) {
         return [];
      }

      let types: RepositoryEntryType[] = this.rootNodesTypeFun(this.dataSource);

      if(!types || types.length == 0) {
         return [];
      }

      let dataSource: RepositoryFlatNode[] = this.dataSource.data.filter((data) => {
         return types.indexOf(data?.data?.type) >= 0 && (!this.rootChildrenFilter || this.rootChildrenFilter(data.data));
      });

      for(let node of dataSource) {
         this.filterChildNodes(node.data.children);
      }

      return dataSource;
   }

   private filterChildNodes(nodes: RepositoryTreeNode[]) {
      if(!nodes) {
         return;
      }

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
      return !this.rootChildrenFilter || this.rootChildrenFilter(node);
   }

   public nodeSelected(evt: FlatTreeSelectNodeEvent): void {
      let selectNode: RepositoryFlatNode = <RepositoryFlatNode> evt.node;

      if(selectNode?.data.type == RepositoryEntryType.USER_FOLDER && selectNode?.data?.path == Tool.USERS_REPORTS) {
         return;
      }

      this.selectedNodes = [<RepositoryFlatNode>evt.node];
   }

   public finish(): void {
      this.dialogRef.close(this.selectedNodes[0]);
   }

   public cancel(): void {
      this.dialogRef.close();
   }

   onEnter() {
      this.finish();
   }

   onEsc() {
      this.cancel();
   }

   doContextMenu(event: FlatTreeContextMenuEvent) {
      if(event.menu.name == "new-folder" && event.node) {
         const dialogRef = this.dialog.open(InputNameDialogComponent, {
            role: "dialog",
            width: "500px",
            maxWidth: "100%",
            maxHeight: "100%",
            disableClose: true
         });

         dialogRef.componentInstance.title = "_#(js:Folder Name)";

         dialogRef.afterClosed().subscribe((res) => {
            if(res) {
               this.repositoryService.addFolder(<RepositoryTreeNode> event.node.data, res);
            }
         });
      }
   }
}
