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
import { BreakpointObserver } from "@angular/cdk/layout";
import { HttpErrorResponse } from "@angular/common/http";
import { Component, OnDestroy, OnInit } from "@angular/core";
import { MatDialog, MatDialogConfig } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { ActivatedRoute, Router } from "@angular/router";
import { BehaviorSubject, Observable, Subject } from "rxjs";
import { catchError, map, takeUntil } from "rxjs/operators";
import { RepositoryEntryType } from "../../../../../../../shared/data/repository-entry-type.enum";
import { RepositoryEditorModel } from "../../../../../../../shared/util/model/repository-editor-model";
import { Tool } from "../../../../../../../shared/util/tool";
import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";
import { ContextHelp } from "../../../../context-help";
import { PageHeaderService } from "../../../../page-header/page-header.service";
import { Searchable } from "../../../../searchable";
import { Secured } from "../../../../secured";
import { TopScrollService } from "../../../../top-scroll/top-scroll.service";
import { equalsIdentity } from "../../../security/users/identity-id";
import { ExportAssetDialogComponent } from "../import-export/export-asset-dialog/export-asset-dialog.component";
import { ImportAssetDialogComponent } from "../import-export/import-asset-dialog/import-asset-dialog.component";
import { MaterializeSheetDialogComponent } from "../materialize-sheet-dialog/materialize-sheet-dialog.component";
import { MoveAssetDialogComponent } from "../move-assets-dialog/move-asset-dialog.component";
import { RepositoryTreeDataSource } from "../repository-tree-data-source";
import { RepositoryFlatNode, RepositoryTreeNode } from "../repository-tree-node";
import { ContentRepositoryService } from "./content-repository.service";

export class TreeSelectionEvent {
   constructor(public selected: boolean, // true if node was selected before tree was refreshed
      public data: any) {
   }
}

const SMALL_WIDTH_BREAKPOINT = 720;

@Secured({
   route: "/settings/content/repository",
   label: "Repository"
})
@Searchable({
   route: "/settings/content/repository",
   title: "Repository",
   keywords: ["Import Asset", "Export Asset", "Repository",
      "Data Space", "Materialized View", "Drivers and Plugins"]
})
@ContextHelp({
   route: "/settings/content/repository",
   link: "EMSettingsContentRepository"
})
@Component({
   selector: "em-content-repository-page",
   templateUrl: "./content-repository-page.component.html",
   styleUrls: ["./content-repository-page.component.scss"],
   providers: [
      RepositoryTreeDataSource
   ]
})
export class ContentRepositoryPageComponent implements OnInit, OnDestroy {
   editorModel = new BehaviorSubject<RepositoryEditorModel>(null);
   editingNode: RepositoryFlatNode;
   unsavedChanges: boolean = false;
   wsMVEnabled: boolean = false;
   private _loading: boolean = false;
   private afterLoadingRefreshData: TreeSelectionEvent;

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

   //For small device use only
   private _editing = false;

   private destroy$ = new Subject<void>();

   constructor(public dataSource: RepositoryTreeDataSource,
      private pageTitle: PageHeaderService,
      private snackBar: MatSnackBar, private dialog: MatDialog,
      private router: Router, private route: ActivatedRoute,
      private service: ContentRepositoryService,
      private breakpointObserver: BreakpointObserver,
      private scrollService: TopScrollService) {
      this.changes.pipe(
         map(node => new TreeSelectionEvent(false, node))
      )
         .subscribe(event => {
            if(event && event.data) {
               if(!this._loading) {
                  this.refreshTree(event);
               }
               else {
                  this.afterLoadingRefreshData = event;
               }
            }
         });

      this.service.loadingChanges.subscribe((loading) => {
         this.loading = loading;
      });

      this.dataSource.repositoryChanged
         .pipe(takeUntil(this.destroy$))
         .subscribe(() => this.refreshTree());

      this.service.needRefreshAfterDelete()
         .pipe(takeUntil(this.destroy$))
         .subscribe(() => this.refreshTree());
   }

   get changes(): Observable<any> {
      return this.service.changes.pipe(
         catchError((warning: HttpErrorResponse) => {
            const message = warning.error ? warning.error.message : warning.message;
            console.error("Failed to get repository changes: ", warning);
            this.snackBar.open(message, null, { duration: Tool.SNACKBAR_DURATION });
            this.refreshTree();
            return this.changes;
         }),
         takeUntil(this.destroy$)
      );
   }

   get editing(): boolean {
      return this._editing;
   }

   set editing(value: boolean) {
      if(value !== this._editing) {
         this._editing = value;
         this.scrollService.scroll("up");
      }
   }

   ngOnInit() {
      this.pageTitle.title = "_#(js:Repository)";
      this.service.isWSMVEnabled().subscribe(enabled => this.wsMVEnabled = enabled);
      this.service.hasMVPermission().subscribe(allow => this.dataSource.hasMVPermission = allow);
   }

   ngOnDestroy() {
      this.destroy$.next();
      this.destroy$.unsubscribe();
      this.editorModel.unsubscribe();
      this.service.clearSelectedNodes();
   }

   public get selectedNodes(): Observable<RepositoryFlatNode[]> {
      return this.service.selectedNodeChanges;
   }

   refreshTree(event?: TreeSelectionEvent, force: boolean = false) {
      let owner = event && event.data ? event.data.owner : null;
      this.dataSource.refresh(owner).subscribe((newNodes) => {
         // refresh tree and re-select selected nodes that still match
         let selected = this.service.selectedNodes;
         this.service.clearSelectedNodes();

         if(force) {
            this.updateEditorModel();
         }

         if(event && event.data) {
            const data = event.data;
            let node: RepositoryFlatNode;

            if(data.reportSettings) {
               data.owner = data.reportSettings.owner;
            }

            if(event.selected) {
               node = newNodes.find(n => n.data.path == data.path && n.data.type == data.type && equalsIdentity(n.data.owner, data.owner));
            }
            else {
               const expanded: RepositoryFlatNode[] = this.dataSource.treeControl.expansionModel.selected;
               const parent = newNodes.find(n => n.data.children && n.data.children.some(c => c.type === data.type && c.path === data.path && equalsIdentity(c.owner, data.owner)));

               if(parent !== undefined) {
                  this.dataSource.treeControl.expand(parent);
                  expanded.push(parent);
               }

               const nodes: RepositoryFlatNode[] = this.dataSource.treeControl.dataNodes;
               node = nodes.find(n => n.data.type == data.type && n.data.path === data.path && equalsIdentity(n.data.owner, data.owner));
               this.dataSource.restoreExpandedState(newNodes, expanded);
            }

            if(!!node) {
               selected = [node];
            }
         }

         if(selected?.length > 0) {
            let find = false;

            while(selected.length > 0) {
               const node = selected.pop();
               const newNode = newNodes.find((n) => node.equals(n));

               if(newNode != null) {
                  find = true;
                  this.service.selectNode(newNode);
                  this.service.getEditor(newNode).subscribe(model => this.updateEditorModel(newNode, model));
               }
            }

            // if all old selected nodes are existed, should clear editor to show empty.
            if(!find) {
               this.updateEditorModel();
            }
         }
         else {
            this.updateEditorModel();
         }

         if(this.dataSource.searchQuery != null) {
            this.dataSource.restoreExpandedState(this.dataSource.data, []);
            this.dataSource.search(this.dataSource.searchQuery, true);
         }
      });
   }

   public selectNode(nodes: RepositoryFlatNode[]) {
      if(nodes.length != 1) {
         this.service.selectedNodes = nodes;
         this.loadFirstSelectedNodeChildren();
      }
      else if(this.unsavedChanges) {
         const ref = this.dialog.open(MessageDialog, {
            data: {
               title: "_#(js:em.settings.repositorySettingsChanged)",
               content: "_#(js:em.settings.repositorySettings.confirm)",
               type: MessageDialogType.CONFIRMATION
            }
         });

         ref.afterClosed().subscribe(val => {
            if(val) {
               this.singleNodeSelected(nodes);
            }
         });
      }
      else {
         this.singleNodeSelected(nodes);
      }
   }

   private singleNodeSelected(nodes: RepositoryFlatNode[]) {
      this.service.clearSelectedNodes();
      this.service.selectNode(nodes[0]);
      this.loadFirstSelectedNodeChildren();

      if(!this.isScreenSmall()) {
         this.editNode(nodes);
      }
   }

   private loadFirstSelectedNodeChildren(): void {
      let firstSelectNode = this.service.selectedNodes && this.service.selectedNodes.length > 0 ?
         this.service.selectedNodes[0] : null;

      if(firstSelectNode && !firstSelectNode.data.children) {
         let expand = this.dataSource.treeControl.isExpanded(firstSelectNode);
         this.dataSource.treeControl.expand(firstSelectNode);

         if(!expand) {
            this.dataSource.treeControl.collapse(firstSelectNode);
         }
      }
   }

   editNode(nodes?: RepositoryFlatNode[]) {
      this.updateEditorModel();

      if(!nodes) {
         nodes = this.service.selectedNodes;
      }

      this.service.getEditor(nodes[0])
         .subscribe(model => this.updateEditorModel(nodes[0], model));
      this.editing = true;
   }

   updateEditorModel(node: RepositoryFlatNode = null, model: RepositoryEditorModel = null) {
      if(node != null && !!this.service.selectedNodes && this.service.selectedNodes.length > 0 &&
         !Tool.isEquals(node, this.service.selectedNodes[0])) {
         return;
      }

      this.editingNode = node;
      this.unsavedChanges = node == null ? false : this.unsavedChanges;
      this.editorModel.next(model);
   }

   public filterNodes(filter: number): void {
      this.dataSource.filter(filter);
   }

   public addDashboard() {
      this.service.addDashboard(this.service.selectedNode);
   }

   public addFolder() {
      this.service.addFolder(this.service.selectedNode);
   }

   public deleteNodes() {
      if(!this.service.selectedNodes ||
         this.service.selectedNodes.some(node => node?.data.readOnly)) {
         return;
      }

      this.service.deleteSelectedNodes();
   }

   public importAsset() {
      this.dialog.open(ImportAssetDialogComponent, {
         role: "dialog",
         width: "800px",
         maxWidth: "100%",
         maxHeight: "100%",
         disableClose: true,
         data: {}
      }).afterClosed().subscribe(result => {
         if(result) {
            this.refreshTree();
         }
      });
   }

   public exportAsset() {
      this.dialog.open(ExportAssetDialogComponent, {
         role: "dialog",
         width: "750px",
         maxWidth: "100%",
         maxHeight: "100%",
         disableClose: true,
         data: { selectedNodes: this.service.selectedNodes }
      });
   }

   public editorChanged(editorModel: RepositoryEditorModel): void {
      this.editing = false;
      this.refreshTree(new TreeSelectionEvent(true, editorModel));
   }

   public mvChanged(editorModel: RepositoryEditorModel): void {
      this.refreshTree(new TreeSelectionEvent(true, editorModel), true);
   }

   public finalizeNewDataSource(editorModel: RepositoryEditorModel): void {
      this.refreshTree(new TreeSelectionEvent(false, editorModel));
   }

   moveNodes(target: RepositoryFlatNode, confirm: boolean = true) {
      if(this.service.selectedNodes.some((node) =>
         (node.data.type & RepositoryEntryType.WORKSHEET) != RepositoryEntryType.WORKSHEET &&
         (node.data.type & RepositoryEntryType.VIEWSHEET) != RepositoryEntryType.VIEWSHEET &&
         (node.data.type & RepositoryEntryType.WORKSHEET_FOLDER) != RepositoryEntryType.WORKSHEET_FOLDER &&
         (node.data.type & RepositoryEntryType.REPOSITORY_FOLDER) != RepositoryEntryType.REPOSITORY_FOLDER &&
         (node.data.type & RepositoryEntryType.DATA_SOURCE) != RepositoryEntryType.DATA_SOURCE &&
         (node.data.type & RepositoryEntryType.DATA_SOURCE_FOLDER) != RepositoryEntryType.DATA_SOURCE_FOLDER &&
         node.data.type != (RepositoryEntryType.LOGIC_MODEL | RepositoryEntryType.FOLDER) &&
         node.data.type != (RepositoryEntryType.PARTITION | RepositoryEntryType.FOLDER) &&
         node.data.type != RepositoryEntryType.QUERY &&
         !((node.data.type & RepositoryEntryType.FOLDER) === RepositoryEntryType.FOLDER && node.data.owner != null) ||
         this.isSameDirectory(node.data, target.data) ||
         target.data.path.startsWith(node.data.path) ||
         node.data.type == RepositoryEntryType.SCHEDULE_TASK_FOLDER)) {
         return;
      }

      if(target.data.path.startsWith(Tool.RECYCLE_BIN) ||
         target.data.label.startsWith("_#(js:Trashcan Repository)") ||
         target.data.type == RepositoryEntryType.SCHEDULE_TASK_FOLDER) {
         return;
      }

      if(confirm) {
         this.dialog.open(MessageDialog, <MatDialogConfig>{
            data: {
               title: "_#(js:Confirm)",
               content: "_#(js:em.reports.drag.confirm)",
               type: MessageDialogType.CONFIRMATION
            }
         }).afterClosed().subscribe((confirmed) => {
            if(confirmed) {
               this.service.moveSelectedNodes(target);
            }
         });
      }
      else {
         this.service.moveSelectedNodes(target);
      }
   }

   isSameDirectory(fromNode: RepositoryTreeNode, toFolder: RepositoryTreeNode): boolean {
      return !!toFolder.children && toFolder.children.indexOf(fromNode) >= 0;
   }

   isScreenSmall(): boolean {
      return this.breakpointObserver.isMatched(`(max-width: ${SMALL_WIDTH_BREAKPOINT}px)`);
   }

   openMaterializeDialog() {
      this.dialog.open(MaterializeSheetDialogComponent, <MatDialogConfig>{
         data: this.getAllVSAndWS(this.service.selectedNodes),
         width: "610px",
         maxWidth: "100vw",
         maxHeight: "100vh",
         disableClose: true
      }).afterClosed().subscribe(val => {
         if(val) {
            this.refreshTree(null, true);
         }
      });
   }

   private getAllVSAndWS(nodes: RepositoryFlatNode[]): RepositoryTreeNode[] {
      let data: any = [];

      nodes.forEach((node) => {
         if(this.service.isFolder(node.data.type)) {
            node.data.children.forEach(child => {
               if(this.service.isFolder(child.type)) {
                  data = data.concat(this.getChildVSAndWS(child.children));
               }
               else if(this.isVSOrWS(child.type)) {
                  data = data.concat(child);
               }
            });
         }
         else {
            if(this.isVSOrWS(node.data.type)) {
               data.push(node.data);
            }
         }
      });

      return data;
   }

   private getChildVSAndWS(nodes: RepositoryTreeNode[]): RepositoryTreeNode[] {
      if(!nodes) {
         return [];
      }

      let allChildren: RepositoryTreeNode[] = [];
      nodes.forEach(node => {
         if(this.service.isFolder(node.type)) {
            allChildren = allChildren.concat(this.getChildVSAndWS(node.children));
         }
         else if(this.isVSOrWS(node.type)) {
            allChildren.push(node);
         }
      });

      return allChildren;
   }

   private isVSOrWS(type: RepositoryEntryType): boolean {
      return (type & RepositoryEntryType.VIEWSHEET) == RepositoryEntryType.VIEWSHEET ||
         (type & RepositoryEntryType.WORKSHEET) == RepositoryEntryType.WORKSHEET;
   }

   openMoveAssetDialog() {
      const selectedNodes: RepositoryFlatNode[] = this.service.selectedNodes;

      if(Tool.isEmpty(selectedNodes)) {
         return;
      }

      const dialogRef = this.dialog.open(MoveAssetDialogComponent, {
         role: "dialog",
         width: "750px",
         maxWidth: "100%",
         maxHeight: "100%",
         disableClose: true
      });

      dialogRef.componentInstance.onlyForDatabase = this.containsDataBaseNodes();
      dialogRef.componentInstance.rootType = this.getRootType(selectedNodes[0]);

      dialogRef.afterClosed().subscribe((res) => {
         if(res) {
            this.moveNodes(res, false);
         }
      });
   }

   /**
    * @param cNode first current selected node
    */
   getRootType(cNode: RepositoryFlatNode): RepositoryEntryType {
      let idx = this.dataSource.searchQuery != null ? 1 : 0;
      const data: RepositoryFlatNode[] = this.dataSource.data?.filter(node => node.level == idx) || [];

      for(let node of data) {
         if(node == cNode || this.findRootNode(node.data, cNode.data)) {
            return node.data.type;
         }
      }

      return cNode.data?.type;
   }

   private findRootNode(root: RepositoryTreeNode, node: RepositoryTreeNode): boolean {
      if(!!!root || !!!node) {
         return false;
      }

      if(Tool.isEquals(root, node)) {
         return true;
      }

      if(root.children) {
         for(let child of root.children) {
            if(this.findRootNode(child, node)) {
               return true;
            }
         }
      }

      return false;
   }

   private containsDataBaseNodes(): boolean {
      return this.service.selectedNodes.some((node) => {
         return node?.data?.type == (RepositoryEntryType.LOGIC_MODEL | RepositoryEntryType.FOLDER) ||
            node?.data?.type == (RepositoryEntryType.PARTITION | RepositoryEntryType.FOLDER) ||
            node?.data?.type == RepositoryEntryType.QUERY;
      });
   }
}
