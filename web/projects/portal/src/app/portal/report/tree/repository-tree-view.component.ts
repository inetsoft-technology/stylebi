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
import {
   AfterViewInit,
   Component,
   EventEmitter,
   Input,
   OnDestroy,
   OnInit,
   Output,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { createAssetEntry } from "../../../../../../shared/data/asset-entry";
import { RepositoryEntry } from "../../../../../../shared/data/repository-entry";
import { RepositoryEntryType } from "../../../../../../shared/data/repository-entry-type.enum";
import { ComponentTool } from "../../../common/util/component-tool";
import { GuiTool } from "../../../common/util/gui-tool";
import { LocalStorage } from "../../../common/util/local-storage.util";
import { PageTabService, TabInfoModel } from "../../../viewer/services/page-tab.service";
import { RepositoryTreeComponent } from "../../../widget/repository-tree/repository-tree.component";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { ReportTabModel } from "../report-tab-model";
import { Tool } from "../../../../../../shared/util/tool";

const SEARCH_URI = "../api/portal/tree/search";
const GET_PORTAL_TREE_FOLDER = "../api/portal/tree";

@Component({
   selector: "p-repository-tree-view",
   templateUrl: "./repository-tree-view.component.html",
   styleUrls: ["./repository-tree-view.component.scss"]
})
export class RepositoryTreeViewComponent implements OnInit, AfterViewInit, OnDestroy {
   @Input() model: ReportTabModel;
   @Input() openedEntrys: RepositoryEntry[] = [];
   @Output() entryOpened = new EventEmitter<RepositoryEntry>();
   @Output() entryDeleted = new EventEmitter<RepositoryEntry>();
   @Output() editViewsheet = new EventEmitter<RepositoryEntry>();
   @Input() isMobile: boolean = false;

   @Input()
   set rootNode(node: TreeNodeModel) {
      const doSelectEntry = !this.rootNode && !!node && !!this._selectedEntry;
      this._rootNode = node;
      this.loading = node == null;

      if(doSelectEntry) {
         this.selectEntry(this._selectedEntry);
      }
   }

   get rootNode(): TreeNodeModel {
      return this._rootNode;
   }

   @Input()
   set selectedEntry(entry: RepositoryEntry) {
      this._selectedEntry = entry;
      this.selectEntry(entry);
   }

   get selectedEntry(): RepositoryEntry {
      return this._selectedEntry;
   }

   @ViewChild(RepositoryTreeComponent) repositoryTree: RepositoryTreeComponent;

   selectedNode: TreeNodeModel;
   searchString: string;
   searchMode = false;
   searchRootNode: TreeNodeModel;
   favoritesMode = false;
   favoritesRootNode: TreeNodeModel;
   loading = true;

   private _selectedEntry: RepositoryEntry;
   private _rootNode: TreeNodeModel;
   private subscriptions = new Subscription();

   constructor(private http: HttpClient, private modal: NgbModal,
               private pageTabService: PageTabService)
   {
      this.subscriptions.add(this.pageTabService.onRefreshPage.subscribe((tab: TabInfoModel) => {
         let entry = createAssetEntry(tab.id);

         if(!!entry.user && !entry.path.startsWith(Tool.MY_REPORTS)) {
            entry.path = Tool.MY_REPORTS + "/" + entry.path;
         }

         if(entry && this.rootNode) {
            this.repositoryTree.selectAndExpandToPath(entry.path, this.rootNode);
         }
      }));
   }

   ngOnInit(): void {
      if(LocalStorage.getItem("favorites-tree-mode") == "true") {
         this.favoritesMode = true;
         this.refreshTree();
      }
   }

   ngAfterViewInit(): void {
      if(this._selectedEntry) {
         this.selectEntry(this._selectedEntry);
      }
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
   }

   get currentRootNode(): TreeNodeModel {
      return this.searchMode ? this.searchRootNode :
         this.favoritesMode ? this.favoritesRootNode : this.rootNode;
   }

   nodeSelected(node: TreeNodeModel) {
      this._selectedEntry = node.data;
      this.entryOpened.emit(this._selectedEntry);
   }

   refreshTree(): void {
      if(this.favoritesMode) {
         this.showFavoritesTree();
      }
      else if(this.searchMode) {
         this.search();
      }
      else {
         this.repositoryTree.refreshTree();
      }
   }

   public updateRootNode(node: TreeNodeModel) {
      if(this.favoritesMode) {
         this.favoritesRootNode = node;
      }
      else {
         this._rootNode = node;
      }
   }

   clickFavoritesBtn(): void {
      this.searchMode = false;
      this.favoritesMode = !this.favoritesMode;
      setTimeout(() => this.refreshTree());
      LocalStorage.setItem("favorites-tree-mode", String(this.favoritesMode));
   }

   showFavoritesTree(): void {
      let params = new HttpParams()
         .set("path", "/")
         .set("checkDetailType", "false")
         .set("isReport", "true")
         .set("isFavoritesTree", "true");

      this.http.get<TreeNodeModel>(GET_PORTAL_TREE_FOLDER, { params }).subscribe(
         (favoritesNode) => {
            this.favoritesRootNode = favoritesNode;
            this.favoritesRootNode.expanded = true;
         },
         (error) => ComponentTool.showHttpError("Failed to show Favorites", error, this.modal)
      );
   }

   resetSearchMode(): void {
      this.searchString = null;
      this.searchMode = false;
      //wait a tick so that the viewchild updates and the correct tree is refreshed
      setTimeout(() => this.refreshTree());
   }

   search(): void {
      if(!!this.searchString && this.searchString.trim()) {
         let _params = new HttpParams().set("searchString", this.searchString)
                                       .set("favoritesMode", this.favoritesMode + "");

         this.http.get<TreeNodeModel>(SEARCH_URI, { params: _params }).subscribe(
            (node) => {
               this.searchRootNode = node;
               this.searchMode = true;
            },
            (error) => ComponentTool.showHttpError("Failed to search assets", error, this.modal)
         );
      }
   }

   searchStringChanged() {
      if(!this.searchString) {
         this.resetSearchMode();
      }
   }

   private selectEntry(entry: RepositoryEntry): void {
      if(this.repositoryTree && this.rootNode) {
         if(entry) {
            this.selectedNode = GuiTool.findNode(this.rootNode, (node) => {
               if(node.data && node.data.type === entry.type) {
                  const nodeEntry = node.data;

                  if(nodeEntry.type === RepositoryEntryType.VIEWSHEET &&
                          nodeEntry.entry && entry.entry)
                  {
                     return nodeEntry.entry.identifier === entry.entry.identifier;
                  }

               }
               return false;
            });

            // selected node wasn't found so check along the path.
            // it may not have been loaded yet.
            if(this.selectedNode == null) {
               this.repositoryTree.selectAndExpandToPath(entry.path, this.rootNode);
            }
         }
         else {
            this.selectedNode = null;
            this.repositoryTree.deselectAllNodes();
         }
      }
   }
}
