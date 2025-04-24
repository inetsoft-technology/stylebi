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
import { Injectable, NgZone, OnDestroy } from "@angular/core";
import { MatDialog, MatDialogConfig } from "@angular/material/dialog";
import { BehaviorSubject, observable, Observable, of, Subject } from "rxjs";
import { catchError, map, tap } from "rxjs/operators";
import { RepositoryEntryType, } from "../../../../../../shared/data/repository-entry-type.enum";
import { StompClientConnection } from "../../../../../../shared/stomp/stomp-client-connection";
import { StompClientService } from "../../../../../../shared/stomp/stomp-client.service";
import { Tool } from "../../../../../../shared/util/tool";
import { MessageDialog, MessageDialogType } from "../../../common/util/message-dialog";
import { FlatTreeDataSource } from "../../../common/util/tree/flat-tree-data-source";
import {
   FlatTreeNodeMenu,
   FlatTreeNodeMenuItem,
   TreeDataModel,
} from "../../../common/util/tree/flat-tree-model";
import { convertToKey, IdentityId } from "../../security/users/identity-id";
import { RepositoryFlatNode, RepositoryTreeNode } from "./repository-tree-node";
import { LicensedComponents } from "./repository-tree-view/licensed-components";
import { SearchComparator } from "../../../../../../portal/src/app/widget/tree/search-comparator";

export interface RepositoryTreeActionStates {
   newDashboardDisabled: boolean;
   newFolderDisabled: boolean;
   deleteDisabled: boolean;
   moveDisabled: boolean;
   exportDisabled: boolean;
   materializeDisabled: boolean;
}

@Injectable()
export class RepositoryTreeDataSource
   extends FlatTreeDataSource<RepositoryFlatNode, RepositoryTreeNode> implements OnDestroy {
   private currentFilter = RepositoryEntryType.ALL_FILTERS;
   // nodes that are visible before search is initiated
   private previousState: RepositoryFlatNode[];
   private expandedState: RepositoryFlatNode[];
   private _data = new Subject<void>();
   private _loading = new BehaviorSubject<boolean>(true);
   private connection: StompClientConnection;
   private connecting = false;
   private _changed = new Subject<void>();
   private lazyLoadChildrenFilter: (children: RepositoryTreeNode[]) => void;
   licensedComponents: LicensedComponents;
   smallDevice = false;
   wsMVEnabled = false;
   hasMVPermission = false;
   contextMenusEnabled = false;
   searchQuery: string = null;

   get dataSubject(): Observable<void> {
      return this._data.asObservable();
   }

   get loading(): Observable<boolean> {
      return this._loading.asObservable();
   }

   get repositoryChanged(): Observable<void> {
      return this._changed.asObservable();
   }

   private readonly getIcon = function (expanded: boolean) {
      if(this.data.icon) {
         return this.data.icon;
      }

      switch(this.data.type) {
         case RepositoryEntryType.VIEWSHEET:
            return "viewsheet-icon";
         case RepositoryEntryType.WORKSHEET:
            return "worksheet-icon";
         case RepositoryEntryType.TRASHCAN_FOLDER:
         case RepositoryEntryType.RECYCLEBIN_FOLDER:
         case RepositoryEntryType.TRASHCAN:
            return "trash-icon";
         case RepositoryEntryType.BEAN:
            return "report-bean-icon";
         case RepositoryEntryType.SCRIPT:
            return "javascript-icon";
         case RepositoryEntryType.META_TEMPLATE:
            return "report-meta-icon";
         case RepositoryEntryType.PARAMETER_SHEET:
            return "report-param-only-icon";
         case RepositoryEntryType.TABLE_STYLE:
            return "style-icon";
         case RepositoryEntryType.QUERY:
            return "db-table-icon";
         case RepositoryEntryType.LOGIC_MODEL:
            return "logical-model-icon";
         case RepositoryEntryType.PARTITION:
            return "partition-icon";
         case RepositoryEntryType.VPM:
            return "vpm-icon";
         case RepositoryEntryType.DASHBOARD:
            return "viewsheet-book-icon";
         case RepositoryEntryType.DATA_SOURCE:
            return "data-sources-icon";
         case RepositoryEntryType.DATA_SOURCE_FOLDER:
            if(this.data.path !== "/") {
               return expanded ? "folder-open-icon" : "folder-icon";
            }

            return "data-source-folder-icon";
         default:
            if((this.data.type & RepositoryEntryType.FOLDER) === RepositoryEntryType.FOLDER) {
               if(this.data.path.indexOf(Tool.RECYCLE_BIN) != -1) {
                  return "folder-icon";
               }

               return expanded ? "folder-open-icon" : "folder-icon";
            }

            return null;
      }
   };

   constructor(private http: HttpClient, private client: StompClientService,
               private zone: NgZone, private dialog: MatDialog)
   {
      super();
      this.connectSocket(true);
   }

   ngOnDestroy() {
      this._data.unsubscribe();
      this._loading.unsubscribe();
      this._changed.unsubscribe();
      this.disconnectSocket();
   }

   private connectSocket(init?: boolean): void {
      if(!this.connecting && !this.connection) {
         this.client.connect("../vs-events").subscribe(
            (connection) => {
               this.connecting = false;
               this.connection = connection;
               this.subscribeSocket();

               if(init) {
                  this.initTreeDate();
               }
            },
            (error: any) => {
               this.connecting = false;
               console.error("Failed to connect to server: ", error);
            }
         );
      }
   }

   private subscribeSocket(): void {
      this.connection.subscribe("/user/em-content-changed", () => {
         this.zone.run(() => this._changed.next());
      });
   }

   private disconnectSocket(): void {
      if(this.connection) {
         this.connection.disconnect();
         this.connection = null;
      }
   }

   private initTreeDate(): void {
      this.init().subscribe((nodes) => {
         this.data = nodes;

         if(!this._data.closed) {
            this._data.next();
         }

         if(!this._loading.closed) {
            this._loading.next(false);
         }
      });
   }

   /**
    * Get the initial tree data
    */
   public init(userToLoad: IdentityId = null): Observable<RepositoryFlatNode[]> {
      const expandedUsers: string[] = this.treeControl.expansionModel.selected
         .filter(node => this.isUserNode(node))
         .map(node => convertToKey(node.data.owner)) || [];

      if(userToLoad != null) {
         expandedUsers.push(convertToKey(userToLoad));
      }

      return this.http.post<TreeDataModel<RepositoryTreeNode>>("../api/em/content/repository/tree", expandedUsers)
         .pipe(
            catchError(error => {
               const orgInvalid = error.error.type === "InvalidOrgException";

               if(orgInvalid) {
                  this.dialog.open(MessageDialog, <MatDialogConfig>{
                     width: "350px",
                     data: {
                        title: "_#(js:Error)",
                        content: error.error.message,
                        type: MessageDialogType.ERROR
                     }
                  });
               }

               return of(null);
            }),
            map((model) => this.transform(model, 0)));
   }

   /**
    * Get a new tree and persist expanded state
    */
   public refresh(userToLoad: IdentityId = null): Observable<RepositoryFlatNode[]> {
      return this.init(userToLoad).pipe(
         tap(newNodes => this.restoreExpandedState(newNodes, this.treeControl.expansionModel.selected.slice()))
      );
   }

   public restoreExpandedState(nodes: RepositoryFlatNode[], selected: RepositoryFlatNode[]): void {
      this.treeControl.collapseAll();
      this.data = nodes;

      // Starting from the leftmost node expand the comparable node in the new list
      if(!!selected) {
         selected.sort((a, b) => a.level - b.level)
            .forEach((oldNode) => {
               const newNode = nodes.find((node) => oldNode.equals(node));

               if(newNode != null) {
                  this.treeControl.expand(newNode);
               }
            });
      }
   }

   public filter(filter: number): void {
      this.currentFilter = filter;
      const nodes = this.data;

      for(let i = 0; i < nodes.length; i++) {
         const node = nodes[i];
         node.visible = this.checkVisibility(node.data);

         // hide child nodes when the parent node is hidden
         if(!node.visible) {
            for(let j = i + 1; j < nodes.length; j++) {
               const childNode = nodes[j];

               if(childNode.level > node.level) {
                  childNode.visible = false;
                  i = j;
               }
               else {
                  break;
               }
            }
         }
      }

      this.dataChange.next(this.data);
   }

   public search(query: string, foceFreshPreState: boolean = false): void {
      this.searchQuery = query;

      if(!query) {
         this.clearSearch();
         return;
      }

      if(!this.previousState || foceFreshPreState) {
         this.expandedState = Tool.clone(this.treeControl.expansionModel.selected);
         this.treeControl.collapseAll();
         this.previousState = Tool.clone(this.data);

         // to add SearchResult node when fist time to search.
         this.addSearchResultNode();
      }

      const params = new HttpParams()
         .set("filter", Tool.byteEncode(this.searchQuery));
      this.http.get<TreeDataModel<RepositoryTreeNode>>("../api/em/content/repository/tree/search", { params })
         .subscribe(model => {
            let searchResultNode = this.data.slice().find(n => this.isSearchResultNode(n));

            if(searchResultNode) {
               this.treeControl.collapse(searchResultNode);
               searchResultNode.data.children = model.nodes;
               this.sortSearchNodes(searchResultNode.data.children, query);
               this.processSearchResults(searchResultNode);
            }

            this.treeControl.expandAll();
            this.dataChange.next(this.data);
         });
   }

   public setLazyLoadChildrenFilter(fun: (children: RepositoryTreeNode[]) => void) {
      this.lazyLoadChildrenFilter = fun;
   }

   private sortSearchNodes(nodes: RepositoryTreeNode[], query: string): void {
      if(!nodes || nodes.length == 1) {
         return;
      }

      nodes.sort((a, b) => new SearchComparator(query).searchSort(a, b));
      nodes.sort((a, b) =>
         (b.type & RepositoryEntryType.FOLDER) - (a.type & RepositoryEntryType.FOLDER));

      nodes.forEach((node) => {
         if(!!node.children) {
            this.sortSearchNodes(node.children, query);
         }
      });
   }

   private addSearchResultNode() {
      let resultNode = {
         label: "_#(js:Search Results)",
         path: "",
         owner: null,
         type: RepositoryEntryType.FOLDER,
         readOnly: true,
         builtIn: false,
         description: "_#(js:Search Results)",
         icon: null,
         visible: true,
         children: []
      };

      this.data = [
         new RepositoryFlatNode(
            resultNode.label, 0, true, resultNode, false, true, null, this.getIcon)
      ];
   }

   /**
    * For each node in a search result, set the visibility to true.
    *
    * @param node the root node of the search result.
    */
   private processSearchResults(node: RepositoryFlatNode): boolean {
      if(node == null) {
         return false;
      }

      let visible = this.isSearchResultNode(node);

      if(!visible) {
         visible = true;
      }

      if(node.expandable && node.data.children && node.data.children.length) {
         if((node.level == 1) || this.isSearchResultNode(node)) {
            this.treeControl.expand(node);
         }

         visible = this.processSearchResults0(node) || visible;
      }

      return node.visible = visible;
   }

   /**
    * Process the search result nodes.
    *
    * @param node the root node of the search result tree.
    */
   private processSearchResults0(node: RepositoryFlatNode): boolean {
      let childrenVisible = false;

      if(node.expandable && node.data.children && node.data.children.length) {
         this.treeControl.expand(node);

         for(let i = 0; i < node.data.children.length; i++) {
            const child = node.data.children[i];
            const found = this.data.find(flatNode => flatNode.data === child);

            if(found) {
               this.treeControl.expand(node);
            }

            if(this.processSearchResults(found)) {
               childrenVisible = true;
            }
         }
      }

      return childrenVisible;
   }

   private isSearchResultNode(node: RepositoryFlatNode): boolean {
      return !!this.previousState && node.level == 0;
   }

   public clearSearch(): void {
      this.searchQuery = null;
      if(!!this.previousState) {
         this.data = this.previousState;
         this.previousState = null;
         this.restoreExpandedState(this.data, this.expandedState);
      }
   }

   /**
    * @inheritDoc
    */
   protected getChildren(node: RepositoryFlatNode): Observable<TreeDataModel<RepositoryTreeNode>> {
      if(this.isUserNode(node)) {
         const params = new HttpParams()
            .set("path", Tool.byteEncode(node.data.path))
            .set("owner", Tool.byteEncode(convertToKey(node.data.owner)));
         return this.http.get<any>("../api/em/content/repository/tree", { params }).pipe(
            catchError(error => {
               const orgInvalid = error.error.type === "InvalidOrgException";

               const errContent: string = orgInvalid
                  ? error.error.message
                  : "_#(js:em.security.orgAdmin.identityPermissionDenied)";

               this.dialog.open(MessageDialog, <MatDialogConfig>{
                  width: "350px",
                  data: {
                     title: "_#(js:Error)",
                     content: errContent,
                     type: MessageDialogType.ERROR
                  }
               });

               return of(null);
            }),
            tap(((result: TreeDataModel<RepositoryTreeNode>) => {
               if(result && this.lazyLoadChildrenFilter) {
                  this.lazyLoadChildrenFilter(result.nodes);
               }
            }))
         );
      }

      return of({
         nodes: node.data.children || [],
         [Symbol.observable]: observable
      });
   }

   protected transform(model: TreeDataModel<RepositoryTreeNode>,
      level: number, parent?: RepositoryFlatNode): RepositoryFlatNode[] {
      return model.nodes.map((node) => {
         const expandable = (node.type & RepositoryEntryType.FOLDER) === RepositoryEntryType.FOLDER;
         let result = new RepositoryFlatNode(
            node.label, level, expandable, node, false, this.checkVisibility(node),
            this.getContextMenu(node, level), this.getIcon);

         if(parent) {
            result.parent = parent;
         }

         return result;
      });
   }

   private checkVisibility(node: RepositoryTreeNode): boolean {
      let nodeType: RepositoryEntryType;

      switch(node.type) {
         case RepositoryEntryType.VIEWSHEET:
            nodeType = RepositoryEntryType.VIEWSHEETS;
            break;
         case RepositoryEntryType.WORKSHEET:
         case RepositoryEntryType.WORKSHEET_FOLDER:
            nodeType = RepositoryEntryType.WORKSHEETS;
            break;
         case RepositoryEntryType.USER | RepositoryEntryType.FOLDER:
            nodeType = RepositoryEntryType.USER_FOLDERS;
            break;
         case RepositoryEntryType.USER | RepositoryEntryType.WORKSHEET_FOLDER:
            nodeType = RepositoryEntryType.WORKSHEETS + RepositoryEntryType.USER_FOLDERS;
            break;
         default:
            return node.visible;
      }

      return (nodeType & this.currentFilter) === nodeType && node.visible;
   }

   private isUserNode(node: RepositoryFlatNode): boolean {
      return node.data.owner != null &&
         node.data.type === (RepositoryEntryType.USER | RepositoryEntryType.FOLDER);
   }

   private getContextMenu(node: RepositoryTreeNode, level: number): () => FlatTreeNodeMenu | null {
      if(!this.contextMenusEnabled) {
         return null;
      }

      return () => this.createContextMenu(node, level);
   }

   private createContextMenu(node: RepositoryTreeNode, level: number): FlatTreeNodeMenu | null {
      const states = this.getActionStates(node, level);
      const items: FlatTreeNodeMenuItem[] = [];

      if(!states.newDashboardDisabled) {
         items.push({
            name: "new-dashboard",
            label: "_#(js:New Dashboard)",
            disabled: () => false
         });
      }

      if(!states.newFolderDisabled) {
         items.push({
            name: "new-folder",
            label: "_#(js:New Folder)",
            disabled: () => false
         });
      }

      if(!states.deleteDisabled) {
         items.push({
            name: "delete",
            label: "_#(js:Delete)",
            disabled: () => false
         });
      }

      if(!states.moveDisabled) {
         items.push({
            name: "move",
            label: "_#(js:Move)",
            disabled: () => false
         });
      }

      if(this.smallDevice) {
         items.push({
            name: "edit",
            label: "_#(js:Edit)",
            disabled: () => false
         });
      }

      if(!states.materializeDisabled) {
         items.push({
            name: "materialize",
            label: "_#(js:Materialize)",
            disabled: () => false
         });
      }

      if(!states.exportDisabled) {
         items.push({
            name: "export",
            label: "_#(js:Export Asset)",
            disabled: () => false
         });
      }

      return items.length > 0 ? { items } : null;
   }

   private isExtendedModel(node: RepositoryTreeNode): boolean {
      const type = node?.type;

      if((type & RepositoryEntryType.LOGIC_MODEL) != RepositoryEntryType.LOGIC_MODEL &&
         (type & RepositoryEntryType.PARTITION) != RepositoryEntryType.PARTITION) {
         return false;
      }

      let folderSpliter = "^__^";
      let path = node.path;
      let hasFolder = path.indexOf(folderSpliter) != -1;
      let paths = path.split("^");

      return hasFolder ? paths.length >= 5 : paths.length >= 3;
   }

   public getActionStates(node: RepositoryTreeNode, level: number): RepositoryTreeActionStates {
      const type = node.type;
      const path = node.path;
      const readOnly = node.readOnly;
      const builtIn =
         node.builtIn || (!!node.path && node.path.indexOf(Tool.BUILT_IN_ADMIN_REPORTS) === 0);

      const deleteDisabled = level === 0 || builtIn ||
         (type & RepositoryEntryType.USER_FOLDER) === RepositoryEntryType.USER_FOLDER ||
         (type & RepositoryEntryType.FOLDER) === RepositoryEntryType.FOLDER &&
         level === 1 &&
         ((type & RepositoryEntryType.BEAN) === RepositoryEntryType.BEAN ||
            (type & RepositoryEntryType.META_TEMPLATE) === RepositoryEntryType.META_TEMPLATE ||
            (type & RepositoryEntryType.PARAMETER_SHEET) === RepositoryEntryType.PARAMETER_SHEET ||
            (type & RepositoryEntryType.SCRIPT) === RepositoryEntryType.SCRIPT ||
            (type & RepositoryEntryType.TABLE_STYLE) === RepositoryEntryType.TABLE_STYLE) ||
         (type & RepositoryEntryType.RECYCLEBIN_FOLDER) === RepositoryEntryType.RECYCLEBIN_FOLDER ||
         (type & RepositoryEntryType.TRASHCAN_FOLDER) === RepositoryEntryType.TRASHCAN_FOLDER ||
         (type & RepositoryEntryType.CUBE) === RepositoryEntryType.CUBE ||
         (type & RepositoryEntryType.AUTO_SAVE_FOLDER) === RepositoryEntryType.AUTO_SAVE_FOLDER ||
         (type & RepositoryEntryType.VS_AUTO_SAVE_FOLDER) === RepositoryEntryType.VS_AUTO_SAVE_FOLDER ||
         (type & RepositoryEntryType.WS_AUTO_SAVE_FOLDER) === RepositoryEntryType.WS_AUTO_SAVE_FOLDER ||
         (type & RepositoryEntryType.SCHEDULE_TASK) === RepositoryEntryType.SCHEDULE_TASK ||
         node.label === "_#(js:Data Model)" || node.path == "/" ||
         node.readOnly;

      const moveDisabled = deleteDisabled ||
         (type & RepositoryEntryType.DASHBOARD) === RepositoryEntryType.DASHBOARD ||
         (type & RepositoryEntryType.BEAN) === RepositoryEntryType.BEAN ||
         (type & RepositoryEntryType.META_TEMPLATE) === RepositoryEntryType.META_TEMPLATE ||
         (type & RepositoryEntryType.PARAMETER_SHEET) === RepositoryEntryType.PARAMETER_SHEET ||
         (type & RepositoryEntryType.SCRIPT) === RepositoryEntryType.SCRIPT ||
         (type & RepositoryEntryType.TABLE_STYLE) === RepositoryEntryType.TABLE_STYLE ||
         (type & RepositoryEntryType.DATA_MODEL_FOLDER) === RepositoryEntryType.DATA_MODEL_FOLDER ||
         (type & RepositoryEntryType.VPM) === RepositoryEntryType.VPM ||
         (type & RepositoryEntryType.AUTO_SAVE_VS) === RepositoryEntryType.AUTO_SAVE_VS ||
         (type & RepositoryEntryType.AUTO_SAVE_WS) === RepositoryEntryType.AUTO_SAVE_WS ||
         (path && (path.indexOf(Tool.RECYCLE_BIN) == 0 || path.indexOf(Tool.MY_REPORTS_RECYCLE_BIN) == 0)) ||
         this.isExtendedModel(node) ||
         (path && path.indexOf(Tool.TRASHCAN_FOLDER) == 0 && type == RepositoryEntryType.TRASHCAN);

      // just additional source type is RepositoryEntryType.DATA_SOURCE. see ContentRepositoryTreeService
      let isAdditionalSource = type === RepositoryEntryType.DATA_SOURCE;

      const exportDisabled =
         (type & RepositoryEntryType.RECYCLEBIN_FOLDER) === RepositoryEntryType.RECYCLEBIN_FOLDER ||
         (type & RepositoryEntryType.TRASHCAN_FOLDER) === RepositoryEntryType.TRASHCAN_FOLDER ||
         (type & RepositoryEntryType.TRASHCAN) === RepositoryEntryType.TRASHCAN ||
         (type & RepositoryEntryType.CUBE) === RepositoryEntryType.CUBE ||
         (path && (path.indexOf(Tool.RECYCLE_BIN) == 0 || path.indexOf(Tool.MY_REPORTS_RECYCLE_BIN) == 0)) ||
         builtIn || isAdditionalSource;

      const newDashboardDisabled = readOnly ||
         (type & RepositoryEntryType.DASHBOARD_FOLDER) !== RepositoryEntryType.DASHBOARD_FOLDER ||
         (type & RepositoryEntryType.DATA_MODEL_FOLDER) === RepositoryEntryType.DATA_MODEL_FOLDER ||
         !this.licensedComponents?.dashboards || node.readOnly;
      const userDashboard =
         (type & RepositoryEntryType.USER_FOLDER) === RepositoryEntryType.USER_FOLDER &&
         !!path && path.indexOf("My Dashboards") === 0;
      let newFolderDisabled = true;

      const isRecycle = (type === RepositoryEntryType.RECYCLEBIN_FOLDER && path === "") ||
         (!!path && path.indexOf(Tool.RECYCLE_BIN) != -1);

      if((type & RepositoryEntryType.FOLDER) !== 0) {
         const isRootNode = level === 0;

         newFolderDisabled =
            (type & RepositoryEntryType.DATA_SOURCE) === RepositoryEntryType.DATA_SOURCE ||
            (type & RepositoryEntryType.LOGIC_MODEL) === RepositoryEntryType.LOGIC_MODEL ||
            (type & RepositoryEntryType.PARTITION) === RepositoryEntryType.PARTITION ||
            (type & RepositoryEntryType.TRASHCAN) === RepositoryEntryType.TRASHCAN ||
            (type & RepositoryEntryType.AUTO_SAVE_FOLDER) === RepositoryEntryType.AUTO_SAVE_FOLDER ||
            (type & RepositoryEntryType.VS_AUTO_SAVE_FOLDER) === RepositoryEntryType.VS_AUTO_SAVE_FOLDER ||
            (type & RepositoryEntryType.WS_AUTO_SAVE_FOLDER) === RepositoryEntryType.WS_AUTO_SAVE_FOLDER ||
            (isRootNode && (type & RepositoryEntryType.USER_FOLDER) === RepositoryEntryType.USER_FOLDER) ||
            (type & RepositoryEntryType.LIBRARY_FOLDER) === RepositoryEntryType.LIBRARY_FOLDER ||
            (type & RepositoryEntryType.BEAN) === RepositoryEntryType.BEAN ||
            (type & RepositoryEntryType.META_TEMPLATE) === RepositoryEntryType.META_TEMPLATE ||
            (type & RepositoryEntryType.PARAMETER_SHEET) === RepositoryEntryType.PARAMETER_SHEET ||
            (type & RepositoryEntryType.SCRIPT) === RepositoryEntryType.SCRIPT ||
            (type & RepositoryEntryType.TABLE_STYLE) === RepositoryEntryType.TABLE_STYLE ||
            (type & RepositoryEntryType.QUERY) == RepositoryEntryType.QUERY &&
            (type & RepositoryEntryType.FOLDER) == RepositoryEntryType.FOLDER ||
            (type & RepositoryEntryType.DATA_MODEL_FOLDER) === RepositoryEntryType.DATA_MODEL_FOLDER ||
            (type & RepositoryEntryType.SCHEDULE_TASK) === RepositoryEntryType.SCHEDULE_TASK ||
            isRecycle || !!path && path.indexOf(Tool.BUILT_IN_ADMIN_REPORTS) === 0 ||
            node.readOnly || !newDashboardDisabled;
      }

      let materializeDisabled = true;

      if(!!path && path.indexOf(Tool.RECYCLE_BIN) < 0 && path !== "My Dashboards"  &&
         path != "My Portal Dashboards" &&
         path != "Users' Dashboards" && path != "Built-in Admin Reports" &&
         path != "Schedule Tasks" && path != "My Schedule Tasks") {
         const isWs =
            (type & RepositoryEntryType.WORKSHEET_FOLDER) == RepositoryEntryType.WORKSHEET_FOLDER ||
            type == RepositoryEntryType.WORKSHEET;

         if(isWs) {
            materializeDisabled = !this.wsMVEnabled;
         }
         else {
            materializeDisabled = !this.hasMVPermission ||
               (type & RepositoryEntryType.USER_FOLDER) != RepositoryEntryType.USER_FOLDER &&
               type != RepositoryEntryType.VIEWSHEET &&
               type != RepositoryEntryType.REPOSITORY_FOLDER;
         }
      }

      return {
         newDashboardDisabled,
         newFolderDisabled,
         deleteDisabled,
         moveDisabled,
         exportDisabled,
         materializeDisabled
      };
   }
}
