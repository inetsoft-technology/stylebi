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
import { HttpClient, HttpErrorResponse, HttpParams } from "@angular/common/http";
import {
   Component,
   ElementRef,
   NgZone,
   OnDestroy,
   OnInit,
   Renderer2,
   ViewChild
} from "@angular/core";
import { Validators } from "@angular/forms";
import { ActivatedRoute, ParamMap, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { from, Observable, of, Subscription, throwError } from "rxjs";
import {
   catchError,
   debounceTime,
   distinctUntilChanged,
   filter,
   finalize,
   map,
   switchMap
} from "rxjs/operators";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { RepositoryEntryType } from "../../../../../../shared/data/repository-entry-type.enum";
import { DateTypeFormatter } from "../../../../../../shared/util/date-type-formatter";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { SortOptions } from "../../../../../../shared/util/sort/sort-options";
import { SortTypes } from "../../../../../../shared/util/sort/sort-types";
import { Tool } from "../../../../../../shared/util/tool";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import {
   RepositoryClientService
} from "../../../common/repository-client/repository-client.service";
import { ComponentTool } from "../../../common/util/component-tool";
import { CommandProcessor, ViewsheetClientService } from "../../../common/viewsheet-client";
import { MessageCommand } from "../../../common/viewsheet-client/message-command";
import { InputNameDialog } from "../../../widget/dialog/input-name-dialog/input-name-dialog.component";
import { AnalyzeMVDialog } from "../../dialog/analyze-mv/analyze-mv-dialog.component";
import { AddFolderRequest } from "../commands/add-folder-request";
import { AssetDependenciesResponse } from "../commands/asset-dependencies-response";
import { CheckDuplicateRequest } from "../commands/check-duplicate-request";
import { CheckDuplicateResponse } from "../commands/check-duplicate-response";
import { CheckRemovablesRequest } from "../commands/check-removables-request";
import { CheckRemovablesResponse } from "../commands/check-removables-response";
import { MoveCommand } from "../commands/move-command";
import { SearchCommand } from "../commands/search-command";
import { DataNotificationsComponent } from "../data-notifications.component";
import { MVTreeModel } from "../model/mv-tree-model";
import { SearchResultsModel } from "../model/search-results-model";
import { WorksheetBrowserInfo } from "../model/worksheet-browser-info";
import {
   FAKE_ROOT_PATH,
   MoveAssetDialogComponent
} from "../move-asset-dialog/move-asset-dialog.component";
import { DataBrowserService } from "./data-browser.service";
import { PortalDataBrowserModel } from "./portal-data-browser-model";
import { AssetItem } from "../model/datasources/database/asset-item";
import { DragService } from "../../../widget/services/drag.service";
import { GuiTool } from "../../../common/util/gui-tool";
import { DomService } from "../../../widget/dom-service/dom.service";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import {
   FeatureFlagsService,
   FeatureFlagValue
} from "../../../../../../shared/feature-flags/feature-flags.service";
import { AssetUtil } from "../../../binding/util/asset-util";
import {
   DataSourcesTreeActionsService
} from "../data-navigation-tree/data-sources-tree-actions.service";
import { AssetConstants } from "../../../common/data/asset-constants";

const FOLDER_URI: string = "../api/data/folders";
const DATA_URI: string = "../api/data/datasets";
const DATA_SEARCH_URI: string = "../api/data/search/datasets";

export interface MoveSelectedConfig {
   readonly assetType: string;
   readonly moveFolderPermissionErrorInBulk: string;
   readonly moveFoldersError: string;
   readonly moveFoldersURI: string;
   readonly moveAssetsURI: string;
   readonly moveAssetTypePermissionErrorInBulk: string;
   readonly moveAssetTypeError: string;
   readonly moveItemsSuccess: string;
}

export interface AddFolderConfig {
   readonly addFolderSuccess: string;
   readonly addFolderError: string;
   readonly newfolder: string;
   readonly folderName: string;
   readonly folderNameRequired: string;
   readonly folderNameInvalid: string;
   readonly duplicateFolderName: string;
   readonly isDefaultCubesName?: string;
}

export interface DeleteDataSetResponse {
   successful: boolean;
   corrupt: boolean;
}

@Component({
   selector: "data-folder-browser",
   templateUrl: "data-folder-browser.component.html",
   styleUrls: ["data-folder-browser.component.scss"],
   providers: [ViewsheetClientService]
})
export class DataFolderBrowserComponent extends CommandProcessor implements OnInit, OnDestroy {
   datasets: WorksheetBrowserInfo[] = [];
   @ViewChild("searchInput") searchInput: ElementRef;
   @ViewChild("dataNotifications") dataNotifications: DataNotificationsComponent;
   folders: WorksheetBrowserInfo[] = [];
   currentFolderPath: WorksheetBrowserInfo[] = [];
   currentFolderPathString: string = "";
   currentFolderPathScope: string = "";
   selectedFile: AssetItem = null;
   selectedItems: WorksheetBrowserInfo[] = [];
   sortOptions: SortOptions = new SortOptions(["name"], SortTypes.ASCENDING);
   worksheetAccess: boolean;
   selectionOn: boolean = false;
   searchView: boolean = false;
   searchVisible: boolean = false;
   searchQuery: string = null;
   searchDestination: string = null;
   searchAssets: WorksheetBrowserInfo[] = [];
   searchSortOptions: SortOptions = new SortOptions([], SortTypes.ASCENDING);
   // the search query that search has already been executed on
   currentSearchQuery: string = "";
   unauthorizedAccess: boolean = false;
   private routeParamSubscription: Subscription = Subscription.EMPTY;
   private subscriptions = new Subscription();

   // ngbTypeAhead function
   searchFunc: (text: Observable<string>) => Observable<any[]> = (text: Observable<string>) =>
      text.pipe(
         debounceTime(300),
         distinctUntilChanged(),
         switchMap(term => this.httpClient.post(DATA_SEARCH_URI + "/assetNames",
            new SearchCommand(term, this.currentFolderPathString || "/", +this.currentFolderPathScope))),
         // show empty search suggestions on error
         catchError(() => of([])),
         map((data: SearchResultsModel) => {
            return !!data && !!data.assetNames ? data.assetNames.slice(0, 8) : [];
         })
      );

   constructor(private dataBrowserService: DataBrowserService,
               private clientService: ViewsheetClientService,
               private repositoryClient: RepositoryClientService,
               private httpClient: HttpClient,
               private modalService: NgbModal,
               private renderer: Renderer2,
               private route: ActivatedRoute,
               private router: Router,
               private zone: NgZone,
               private dragService: DragService,
               private domService: DomService,
               private dataSourcesTreeActionsService: DataSourcesTreeActionsService)
   {
      super(clientService, zone, true);
   }

   ngOnInit(): void {
      // subscribe to route parameters and refresh browser content based on current path
      this.routeParamSubscription = this.route.queryParamMap
         .subscribe((params: ParamMap) => {
            this.searchView = params.has("query");

            if(this.searchView) {
               this.currentSearchQuery = params.get("query");
            }

            let path: string = params.get("path");
            const scope: string = params.get("scope");
            path = !path || path === "/" ? "" : path;
            this.currentFolderPathString = path;
            this.currentFolderPathScope = scope;

            this.searchDestination = path;

            if(scope == AssetEntryHelper.USER_SCOPE + "") {
               const privWS = "_#(js:User Worksheet)";
               this.searchDestination = path.length <= 1 ? privWS : privWS + "/" + path;
            }

            this.refreshBrowserContent(path);
         });

      this.subscriptions.add(this.dataSourcesTreeActionsService.showWSFolderDetailsSubject()
         .subscribe(data => {
            this.showWSDetailsByDataSourcesTree(data);
         }));

      this.repositoryClient.connect();
      this.repositoryClient.dataChanged
         .subscribe(() => this.refreshBrowserContent(this.currentFolderPathString));

      this.clientService.connect();
   }

   ngOnDestroy(): void {
      this.routeParamSubscription.unsubscribe();
      this.subscriptions.unsubscribe();
   }

   getAssemblyName(): string {
      return null;
   }

   private processMessageCommand(command: MessageCommand): void {
      if(command.message && command.type == "INFO") {
         this.dataNotifications.notifications.info(command.message);
      }
      else {
         this.processMessageCommand0(command, this.modalService, this.clientService);
      }
   }

   get addFolderConfig(): AddFolderConfig {
      return {
         addFolderSuccess: "_#(js:data.datasets.addFolderSuccess)",
         addFolderError: "_#(js:data.datasets.addFolderError)",
         newfolder: "_#(js:New Folder)",
         folderName: "_#(js:Folder Name)",
         folderNameRequired: "_#(js:data.datasets.folderNameRequired)",
         folderNameInvalid: "_#(js:data.datasets.folderNameInvalid)",
         duplicateFolderName: "_#(js:data.datasets.duplicateFolderName)",
      };
   }

   get moveSelectedConfig(): MoveSelectedConfig {
      return {
         assetType: AssetType.WORKSHEET,
         moveFolderPermissionErrorInBulk: "_#(js:data.datasets.moveFolderPermissionErrorInBulk)",
         moveFoldersError: "_#(js:data.datasets.moveFolderErrorInBulk)",
         moveFoldersURI: FOLDER_URI + "/moveFolders",
         moveAssetsURI: DATA_URI + "/moveDatasets",
         moveAssetTypePermissionErrorInBulk: "_#(js:data.datasets.moveDataSetPermissionErrorInBulk)",
         moveAssetTypeError: "_#(js:data.datasets.moveDataSetsError)",
         moveItemsSuccess: "_#(js:data.datasets.moveItemsSuccess)",
      };
   }

   /**
    * Refresh contents of folder browser with the given path.
    * @param path    the folder path to get browser content from
    * @param subPath the folder sub path to select file
    */
   protected refreshFolderBrowser(path: string, subPath?: string): void {
      let uri = "../api/portal/data/browser";

      if(!!path) {
         uri += ("/" + Tool.encodeURIComponentExceptSlash(path));
      }

      const params = this.currentFolderPathScope ?
         new HttpParams().set("scope", this.currentFolderPathScope) :
         new HttpParams();
      this.httpClient.get<PortalDataBrowserModel>(uri, { params: params }).pipe(
         catchError((error: HttpErrorResponse) => this.handleBrowserRefreshError(error)),
         finalize(() => {
            if(!this.searchView) {
               // make sure selected items are in sync
               this.updateSelectedItems();
            }
         }))
         .subscribe((data: PortalDataBrowserModel) => {
               this.unauthorizedAccess = false;
               this.folders = <WorksheetBrowserInfo[]> data.folders;
               this.currentFolderPath = data.currentFolder;
               this.datasets = <WorksheetBrowserInfo[]> data.files;
               this.worksheetAccess = data.worksheetAccess;

               if(!this.searchView) {
                  this.sortView();
               }

               if(!!subPath) {
                  const asset: WorksheetBrowserInfo = this.findAsset(subPath);
                  this.selectFile(asset);
               }
            }
         );
   }

   private handleBrowserRefreshError(error: HttpErrorResponse): Observable<never> {
      this.datasets = [];
      this.folders = [];
      this.currentFolderPath = [];

      if(error.status === 403) {
         this.unauthorizedAccess = true;
      }
      else {
         this.unauthorizedAccess = false;
         this.dataNotifications.notifications.danger("_#(js:data.datasets.getDataSetsError)");
      }

      return throwError(error);
   }

   /**
    * Refresh contents of folder search browser with the given path.
    * @param path    the folder path to get browser content from
    * @param query   the query to search for
    */
   protected refreshSearchBrowser(path: string, query: string, scope: number): void {
      this.httpClient.post<SearchResultsModel>(DATA_SEARCH_URI, new SearchCommand(query, path || "/", scope))
         .subscribe(
            data => {
               // search assets are returned in order of relevance, display them as they were returned
               this.searchAssets = data.assets;
               this.searchSortOptions = new SortOptions([], SortTypes.ASCENDING);
            },
            () => {
               this.searchAssets = [];
               this.dataNotifications.notifications.danger("_#(js:data.datasets.searchError)");
            },
            () => {
               // make sure selected items are in sync
               this.updateSelectedItems();
            }
         );
   }

   /**
    * Get tooltip string for the toggle selection button.
    * @returns {string} the tooltip string
    */
   get toggleSelectTooltip(): string {
      if(this.selectionOn) {
         return "_#(js:data.datasets.selectOff)";
      }
      else {
         return "_#(js:data.datasets.selectOn)";
      }
   }

   /**
    * Sort the contents of the view based on current sort options;
    */
   sortView(): void {
      if(this.searchView) {
         const folders = Tool.sortObjects(
            this.searchAssets.filter(asset => asset.type === AssetType.FOLDER), this.searchSortOptions);
         const datasets = Tool.sortObjects(
            this.searchAssets.filter(asset => asset.type !== AssetType.FOLDER), this.searchSortOptions);
         this.searchAssets = [].concat(folders, datasets);
      }
      else {
         this.folders = Tool.sortObjects(this.folders, this.sortOptions);
         this.datasets = Tool.sortObjects(this.datasets, this.sortOptions);
      }
   }

   get newWorksheetDisabled(): boolean {
      return !this.isFolderEditable || !this.worksheetAccess;
   }

   /**
    * Open the composer to new dataset page.
    */
   newWorksheet(): void {
      if(this.newWorksheetDisabled) {
         return;
      }

      const folderId: string = this.currentFolderPath.length
         ? this.currentFolderPath[this.currentFolderPath.length - 1].id
         : "1^1^__NULL__^" + this.currentFolderPathString;
      this.dataBrowserService.newWorksheet(folderId);
   }

   /**
    * Check if the user has permission on all items in the current selection
    * @returns {boolean} true if the deletion toolbar icon should be enabled
    */
   isSelectionDeletable(): boolean {
      return !this.selectedItems.some(item => (!item.deletable));
   }

   /**
    * Check if the user has edit permission on all items in the current selection
    * @returns {boolean} true has edit permission on all items in the current selection
    */
   isSelectionEditable(): boolean {
      return !this.selectedItems.some(item => (!item.editable));
   }

   /**
    * disable status of move icon.
    */
   get moveDisable(): boolean {
      return !this.selectedItems || this.selectedItems.length === 0
         || !this.isSelectionDeletable() || !this.isSelectionEditable();
   }

   /**
    * Attempt to delete the currently selected items.
    */
   deleteSelected(): void {
      if(this.selectedItems.length === 0) {
         return;
      }

      const request = this.selectedItems.reduce<CheckRemovablesRequest>((previous, current) => {
         const item = { path: current.path, scope: current.scope };

         if(current.type === AssetType.FOLDER) {
            previous.folders.push(item);
         }
         else if(current.type === AssetType.WORKSHEET) {
            previous.datasets.push(item);
         }

         return previous;
      }, { datasets: [], folders: [] });

      this.httpClient.post<CheckRemovablesResponse>("../api/data/removeableStatuses", request).subscribe(
         response => {
            const {folderDependencies, datasetDependencies} = response;
            const allDependencies: string[] = [].concat(folderDependencies, datasetDependencies);
            let prompt: string = "_#(js:data.datasets.confirmDeleteItems)";

            if(folderDependencies.length > 0 && datasetDependencies.length > 0) {
               prompt = "_#(js:data.datasets.deleteItemsDependencyError)";
            }
            else if(folderDependencies.length > 0) {
               prompt = "_#(js:data.datasets.deleteFoldersDependencyError)";
            }
            else if(datasetDependencies.length > 0) {
               prompt = "_#(js:data.datasets.deleteDataSetsDependencyError)";
            }

            if(allDependencies.length > 0) {
               prompt += "\n\n_#(js:Dependent Assets):\n";
               prompt += allDependencies.join("\n");
            }

            ComponentTool.showConfirmDialog(this.modalService, "_#(js:data.datasets.delete)",
               prompt)
               .then((buttonClicked) => {
                  if(buttonClicked === "ok") {
                     this.httpClient.post("../api/data/removeAll", this.selectedItems)
                        .subscribe(
                           () => {
                              this.dataNotifications.notifications.success(
                                 "_#(js:data.datasets.deleteItemsSuccess)");
                           },
                           () => {
                              this.dataNotifications.notifications.danger(
                                 "_#(js:data.datasets.deleteItemsError)");
                           },
                           () => {
                              // refresh folder contents, even if failure some items may be deleted
                              this.refreshBrowserContent(this.currentFolderPathString);
                           }
                        );
                  }
               });
         },
         () => {
            this.dataNotifications.notifications.danger(
               "_#(js:data.datasets.deleteItemsError)");
         }
      );
   }

   /**
    * Open the asset. If asset is a folder, open browser to folder path.
    * @param asset   the asset to open
    */
   openFolder(folder: WorksheetBrowserInfo): void {
      if(folder.type === AssetType.FOLDER) {
         const extras = {
            queryParams: { path: folder.path, scope: folder.scope },
            relativeTo: this.route.parent
         };
         this.router.navigate(["folder"], extras);
         this.dataBrowserService.changeFolder(folder.path, folder.scope);
      }
   }

   editWorksheet(worksheet: WorksheetBrowserInfo): void {
      this.dataBrowserService.openWorksheet(worksheet.id, this.clientService);
   }

   /**
    * Open rename dialog for the asset.
    * @param asset   the asset to rename
    */
   renameAsset(asset: WorksheetBrowserInfo): void {
      this.dataBrowserService.renameAsset(asset,
         (type: string, message: string) => this.handleResponse(type, message),
         (error) => this.handleEditAssetError(error));
   }

   handleResponse(type: string, message: string): void {
      if(type == "success") {
         this.dataNotifications.notifications.success(message);
         this.refreshBrowserContent(this.currentFolderPathString);
      }
      else if(type == "danger") {
         this.dataNotifications.notifications.danger(message);
      }
   }

   private handleEditAssetError(error: HttpErrorResponse): Observable<never> {
      this.dataNotifications.notifications.danger("_#(js:data.datasets.editFailed)");
      return throwError(error);
   }

   /**
    * Open move datasets asset dialog.
    * @param asset   the asset to move
    */
   moveAsset(asset: WorksheetBrowserInfo): void {
      const isFolder: boolean = asset.type === AssetType.FOLDER;
      let grandparentFolder: string;
      let parentPath: string;

      if(this.searchView) {
         // search view assets have their parent path attached to them since
         // it may not be current folder
         parentPath = (<WorksheetBrowserInfo> asset).parentPath;
         const privWS = "_#(js:User Worksheet)";

         if(parentPath.indexOf(privWS) == 0 && asset.scope == AssetEntryHelper.USER_SCOPE) {
            parentPath = parentPath == privWS ? "/" : parentPath.substring(privWS.length);
         }

         // if the parent is root, use fake root path, else get the folder directly above parent
         // folder paths directly under root don't have a leading "/", use "/" explicitly
         grandparentFolder = parentPath === "/" ? FAKE_ROOT_PATH :
            parentPath.indexOf("/") !== -1 ?
               parentPath.substring(0, parentPath.lastIndexOf("/")) : "/";
      }
      else {
         parentPath = this.currentFolderPathString || "/";
         const pathLength: number = this.currentFolderPath.length;
         grandparentFolder = pathLength <= 1 ?
            FAKE_ROOT_PATH : this.currentFolderPath[pathLength - 2].path;
      }

      const dialog = ComponentTool.showDialog(this.modalService, MoveAssetDialogComponent,
         (result: [string, number]) => {
         const movePath = result[0];
         const moveScope = result[1];

         if(movePath !== asset.path && movePath !== parentPath || moveScope !== asset.scope) {
               const uri = (isFolder ? FOLDER_URI : DATA_URI) + "/move";
               const assetPath: string = asset.path !== "/" ? asset.path : "";

               let httpParams = new HttpParams();

               if(Tool.isNumber(asset.scope)) {
                  httpParams = httpParams.set("assetScope", "" + asset.scope);
               }

               if(Tool.isNumber(moveScope)) {
                  httpParams = httpParams.set("targetScope", "" + moveScope);
               }

               const params = {
                  params: httpParams
               };
               this.httpClient.post(uri, new MoveCommand(movePath, assetPath, asset.name, asset.id,
                  asset.modifiedDate), params)
                  .subscribe(
                     () => {
                        this.dataNotifications.notifications.success(isFolder ?
                           "_#(js:data.datasets.moveFolderSuccess)" :
                           "_#(js:data.datasets.moveWorksheetSuccess)");
                        this.refreshBrowserContent(this.currentFolderPathString);
                     },
                     (error: HttpErrorResponse) => {
                        if(error.status === 403) {
                           this.dataNotifications.notifications
                              .danger("_#(js:data.datasets.moveTargetPermissionError)");
                        }
                        else {
                           this.dataNotifications.notifications.danger(isFolder ?
                              "_#(js:data.datasets.moveFolderError)" :
                              "_#(js:data.datasets.moveDataSetError)");
                        }
                     }
                  );
            }
         }, {size: "lg", backdrop: "static"});
      dialog.originalPaths = [asset.path];
      dialog.items = [asset];
      dialog.parentPath = parentPath;
      dialog.grandparentFolder = grandparentFolder;
      dialog.parentScope = asset.scope;
   }

   /**
    * Check if asset is deletable and confirm delete of asset.
    * @param asset   the asset to delete
    */
   deleteAsset(asset: WorksheetBrowserInfo): void {
      this.dataBrowserService.deleteAsset(asset,
         (type: string, message: string) => this.handleResponse(type, message),
         (error: HttpErrorResponse, isFolder: boolean) => this.handleDeleteError(error, isFolder));
   }

   private handleDeleteError(error: HttpErrorResponse, isFolder: boolean): Observable<never> {
      if(error.status === 450) {
         this.dataNotifications.notifications.danger(error.error);
      }
      else {
         this.dataNotifications.notifications.danger(isFolder ?
            "_#(js:data.datasets.deleteFolderError)" :
            "_#(js:data.datasets.deleteDataSetError)");
      }

      return throwError(error);
   }

   /**
    * Get the assets to display in the folder browser view.
    * @returns {WorksheetBrowserInfo[]}  the assets to display
    */
   get viewAssets(): WorksheetBrowserInfo[] {
      // return this.searchView ? this.searchAssets : this.folders.concat(this.datasets);
      return this.searchView ? this.searchAssets : this.folders.concat(this.datasets);
   }

   /**
    * Refresh the contents of the browser.
    * @param path    the path to refresh on
    */
   protected refreshBrowserContent(path: string): void {
      this.refreshFolderBrowser(path);

      if(this.searchView) {
         this.refreshSearchBrowser(path, this.currentSearchQuery, +this.currentFolderPathScope);
      }
   }

   /**
    * Turn selection state on or off.
    */
   toggleSelectionState(): void {
      this.selectionOn = !this.selectionOn;
   }

   /**
    * Check if the user can edit the current opened folder.
    */
   get isFolderEditable(): boolean {
      const length = this.currentFolderPath.length;
      return length == 0 || this.currentFolderPath[length - 1].editable;
   }

   /**
    * Toggle search input visibility, called by clicking on search icon.
    * @param event   the event toggling search
    */
   toggleSearch(event: any): void {
      if(!this.searchVisible) {
         this.searchVisible = true;
         let collapseSearchListener: any = this.renderer.listen("document", "click",
            (clickEvent: any) => {
               if(clickEvent !== event && clickEvent.target !== this.searchInput.nativeElement) {
                  this.searchVisible = false;
                  collapseSearchListener();
               }
            });

         // since searchInput is hidden at time of toggle, need to set timeout so it is focused correctly
         setTimeout(() => {
            this.searchInput.nativeElement.focus();
         });
      }
      else {
         this.searchVisible = false;

         // if there is a search query then execute a new search
         if(!!this.searchQuery) {
            this.search();
         }
      }
   }

   /**
    * Execute a new search with given query or current searchQuery.
    */
   search(query: string = null): void {
      if(query) {
         // query is given if user selects a search suggestion from dropdown,
         // use selected item as query
         this.searchQuery = query;
      }

      if(!this.searchQuery) {
         return;
      }

      const queryParams: any = { query: this.searchQuery };

      if(this.currentFolderPathScope) {
         queryParams.scope = this.currentFolderPathScope;
      }

      if(this.currentFolderPathString) {
         queryParams.path = this.currentFolderPathString;
      }

      const extras = {
         queryParams: queryParams,
         relativeTo: this.route.parent
      };

      this.router.navigate(["folder"], extras);
      this.searchQuery = null;
   }

   /**
    * Find the asset info by target path.
    */
   findAsset(path: string): WorksheetBrowserInfo {
      if(!path) {
         return null;
      }

      return this.viewAssets.find((asset, index, arr) => {
         return asset.path === path;
      });
   }

   /**
    * Clear current search.
    */
   clearSearch(): void {
      this.searchQuery = null;
      this.searchView = false;
      const extras = {
         queryParams: !this.currentFolderPathString ? { scope: this.currentFolderPathScope } :
            { path: this.currentFolderPathString, scope: this.currentFolderPathScope },
         relativeTo: this.route.parent
      };
      this.router.navigate(["folder"], extras);
   }

   /**
    * Handle dashboard/dataset selection.
    * @param file  the newly selected dashboard/dataset
    */
   selectFile(file: WorksheetBrowserInfo): void {
      let selectedItem: AssetItem = this.convertToAssetItem(file);

      if(Tool.isEquals(this.selectedFile, selectedItem)) {
         this.selectedFile = null;
      }
      else {
         this.selectedFile = selectedItem;
      }
   }

   private convertToAssetItem(file: WorksheetBrowserInfo): AssetItem {
      if(file != null) {
         return {
            type: file.type,
            id: file.id,
            path: file.path,
            urlPath: "",
            name: file.name,
            createdBy: file.createdBy,
            description: file.description,
            createdDate: file.createdDate,
            editable: file.editable,
            deletable: file.deletable,
            createdDateLabel:  DateTypeFormatter.getLocalTime(file.createdDate, file.dateFormat),
            modifiedDateLabel: DateTypeFormatter.getLocalTime(file.modifiedDate, file.dateFormat)
         };
      }

      return null;
   }

   /**
    * Check if select all should be checked.
    * @returns {boolean}   true if there is at least one item and all items are selected.
    */
   get selectAllChecked(): boolean {
      return this.selectedItems.length > 0 &&
         this.viewAssets.every(item => this.selectedItems.indexOf(item) !== -1);
   }

   /**
    * Change state of select all.
    * @param checked the new state of select all
    */
   selectAllChanged(checked: boolean): void {
      this.selectedItems = [];

      if(checked) {
         this.selectedItems.push(...this.viewAssets);
      }
   }

   /**
    * Update the selected items with the new asset items to make sure they are in sync.
    */
   protected updateSelectedItems(): void {
      let newSelectedItems: WorksheetBrowserInfo[] = [];

      for(let item of this.selectedItems) {
         // check if new assets contains this path and type
         let newItem: WorksheetBrowserInfo = this.viewAssets
            .find(asset => asset.path === item.path && asset.type === item.type);

         if(newItem) {
            newSelectedItems.push(newItem);
         }
      }

      this.selectedItems = newSelectedItems;

      if(!!this.selectedFile && !this.searchView) {
         let info = this.viewAssets.find(asset =>
            asset.path === this.selectedFile.path && asset.type === this.selectedFile.type);
         this.selectedFile = this.convertToAssetItem(info);
      }
   }

   /**
    * Open the input name dialog and create a new folder with specified name.
    */
   addFolder(): void {
      if(!this.isFolderEditable) {
         return;
      }

      const config = this.addFolderConfig;

      const dialog = ComponentTool.showDialog(this.modalService, InputNameDialog,
         (result: string) => {
            const request = new AddFolderRequest(result, this.currentFolderPathString,
               +this.currentFolderPathScope);
            this.httpClient.post(FOLDER_URI, request)
               .subscribe(
                  () => {
                     this.dataNotifications.notifications.success(config.addFolderSuccess);
                     this.refreshFolderBrowser(this.currentFolderPathString);
                  },
                  () => this.dataNotifications.notifications.danger(config.addFolderError));
         });

      dialog.title = config.newfolder;
      dialog.label = config.folderName;
      dialog.helpLinkKey = "DataSourceNewFolder";
      dialog.validators = [
         Validators.required,
         FormValidators.invalidAssetItemName,
         FormValidators.assetNameStartWithCharDigit,
      ];
      dialog.validatorMessages = [
         {validatorName: "required", message: config.folderNameRequired},
         {validatorName: "invalidAssetItemName", message: config.folderNameInvalid},
         {
            validatorName: "assetNameStartWithCharDigit",
            message: "_#(js:asset.tree.checkStart)"
         }
      ];
      dialog.hasDuplicateCheck = (value: string) =>
         this.httpClient
            .post<CheckDuplicateResponse>(DATA_URI + "/isDuplicate",
               <CheckDuplicateRequest> {
                  path: this.currentFolderPathString + "/",
                  newName: value,
                  type: AssetType.FOLDER,
                  scope: this.currentFolderPathScope ?  +this.currentFolderPathScope : null})
            .pipe(map(response => response.duplicate));
   }

   /**
    * Filters assets and returns only the assets with paths closest to the root.
    * For example, given assets with paths {"/A", "/A/B", "/A/B/C", "/X/Y", "/X/Y/Z"}, the returned
    * assets would be {"/A", "/X/Y"}.
    */
   protected getRootAssets(assets: WorksheetBrowserInfo[]): WorksheetBrowserInfo[] {
      const selectedRootAssets: WorksheetBrowserInfo[] = [];

      if(assets.length === 0) {
         return selectedRootAssets;
      }

      const assetPaths = assets.map(a => {
         return {asset: a, pathTokens: a.path.split("/")};
      });

      const folders = assetPaths.filter(ap => ap.asset.type === AssetType.FOLDER);

      assetPaths.forEach(ap => {
         const parent = folders.find(folder =>
            folder.pathTokens.length < ap.pathTokens.length &&
            folder.pathTokens.every((t, i) => t === ap.pathTokens[i])
         );

         if(parent == null) {
            selectedRootAssets.push(ap.asset);
         }
      });

      return selectedRootAssets;
   }

   /**
    * Open the move asset dialog.
    */
   moveSelected(): void {
      this.moveAssets(this.selectedItems);
   }

   moveAssets(assets: WorksheetBrowserInfo[], target?: WorksheetBrowserInfo, assetScope?: number) {
      const selectedRootAssets = this.getRootAssets(assets);

      if(selectedRootAssets.length === 0) {
         return;
      }

      const config = this.moveSelectedConfig;
      const pathLength: number = this.currentFolderPath.length;
      const grandparentFolder: string = pathLength === 1 ?
         FAKE_ROOT_PATH : this.currentFolderPath[pathLength - 2].path;

      const parentPath = !this.currentFolderPathString ? "/" : this.currentFolderPathString;
      const movedFolders = selectedRootAssets.filter(item => item.type === AssetType.FOLDER);
      const movedAssets = selectedRootAssets.filter(item => item.type === config.assetType);
      const moveAction = (result: [string, number]) => {
         const movePath = result[0];
         const moveScope = result[1];
         let promise: Promise<any> = Promise.resolve(null);
         const newPath: string = movePath === "/" ? "" : movePath;

         if(movedFolders.length > 0) {
            let httpParams = new HttpParams();

            if(Tool.isNumber(moveScope)) {
               httpParams = httpParams.set("targetScope", "" + moveScope);
            }

            if(Tool.isNumber(assetScope)) {
               httpParams = httpParams.set("assetScope", "" + assetScope);
            }
            else if(Tool.isNumber(this.currentFolderPathScope)) {
               httpParams = httpParams.set("assetScope", "" + this.currentFolderPathScope);
            }

            const params = {
               params: httpParams
            };
            const folderMoveCommands: MoveCommand[] = movedFolders.map(
               (f) => new MoveCommand(newPath, f.path, f.name, f.id, f.modifiedDate));

            promise = promise.then(() => {
               return this.httpClient.post(config.moveFoldersURI, folderMoveCommands, params)
                  .toPromise()
                  .then((res) => {
                     if((<any> res)?.message) {
                        return ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", (<any> res)?.message,
                           {"ok": "_#(js:OK)"}, {backdrop: false });
                     }
                     else {
                        return Promise.resolve(<WorksheetBrowserInfo[]> res);
                     }
                  })
                  .catch((err: HttpErrorResponse) => {
                     if(err.status === 403) {
                        this.dataNotifications.notifications.danger(
                           config.moveFolderPermissionErrorInBulk);
                     }
                     else {
                        this.dataNotifications.notifications.danger(config.moveFoldersError);
                     }

                     return Promise.reject(err);
                  });
            });
         }

         if(movedAssets.length > 0) {
            promise = promise.then(() => {
               let httpParams = new HttpParams();

               if(Tool.isNumber(moveScope)) {
                  httpParams = httpParams.set("targetScope", "" + moveScope);
               }

               if(Tool.isNumber(this.currentFolderPathScope)) {
                  httpParams = httpParams.set("assetScope", "" + this.currentFolderPathScope);
               }

               const params = {
                  params: httpParams
               };
               const assetMoveCommands: MoveCommand[] = movedAssets.map(
                  (a) => new MoveCommand(newPath, a.path, a.name, a.id, a.modifiedDate));

               return this.httpClient.post(config.moveAssetsURI, assetMoveCommands, params)
                  .toPromise()
                  .then((res) => {
                     if((<any> res)?.message) {
                        return ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", (<any> res)?.message,
                           {"ok": "_#(js:OK)"}, {backdrop: false });
                     }
                     else {
                        return Promise.resolve(<WorksheetBrowserInfo[]> res);
                     }
                  })
                  .catch((err: HttpErrorResponse) => {
                     if(err.status === 403) {
                        this.dataNotifications.notifications
                           .danger(config.moveAssetTypePermissionErrorInBulk);
                     }
                     else {
                        this.dataNotifications.notifications.danger(config.moveAssetTypeError);
                     }

                     return Promise.reject(err);
                  });
            })
               .catch((err: any) => {
                  return Promise.reject(err);
               });
         }

         promise.then(() => {
            // move was successful

            this.refreshBrowserContent(this.currentFolderPathString);
            this.dataNotifications.notifications.success(config.moveItemsSuccess);
         })
            .catch(() => {
               // move error
               this.refreshBrowserContent(this.currentFolderPathString);
            });
      };

      if(!target) {
         const dialog = ComponentTool.showDialog(this.modalService, MoveAssetDialogComponent,
            (result: [string, number]) => {
               moveAction(result);
            }, {size: "lg", backdrop: "static"});
         dialog.multi = true;
         dialog.originalPaths = movedFolders.map(item => item.path);
         dialog.items = selectedRootAssets;
         dialog.parentPath = parentPath;
         dialog.grandparentFolder = grandparentFolder;
         dialog.parentScope = +this.currentFolderPathScope;
      }
      else {
         let targetParameters = <[string, number]>[target.path, target.scope];
         moveAction(targetParameters);
      }
   }

   materializeAsset(worksheet: WorksheetBrowserInfo): void {
      let dialog: AnalyzeMVDialog = ComponentTool.showDialog(this.modalService, AnalyzeMVDialog,
         () => {
            this.refreshBrowserContent(this.currentFolderPathString);
            this.dataBrowserService.changeMV();
         },
         {backdrop: "static", windowClass: "analyze-mv-dialog"},
         () => this.dataBrowserService.changeMV());
      dialog.selectedNodes = [new MVTreeModel(worksheet.path, worksheet.id,
         RepositoryEntryType.WORKSHEET, worksheet.scope === AssetEntryHelper.USER_SCOPE)];
   }

   selectChanged(params: any) {
      this.dataBrowserService.changeFolder(params.path, params.scope);
   }

   dragAssets(data: {event: any, data: WorksheetBrowserInfo[]}): void {
      this.dragService.put("dragWorksheets", JSON.stringify(data.data));
      const labels = data.data.map(info => this.getEntryLabel(info));
      const elem = GuiTool.createDragImage(labels, [], 1, true);
      (<HTMLElement> elem).style.display = "flex";
      (<HTMLElement> elem).style.flexDirection = "column";
      (<HTMLElement> elem).style.lineHeight = "0.5";
      (<HTMLElement> elem).style.alignItems = "left";
      GuiTool.setDragImage(data.event, elem, this.zone, this.domService);
   }

   private getEntryLabel(info: WorksheetBrowserInfo): string {
      let textLabel = info.name;
      let entryIconFn = this.getEntryIcon.bind(this);

      return `
      <div>
      <span>
        <span class="${entryIconFn(info.type)}">
        </span>
        <label>
        ${textLabel}
        </label>
      </span>
      </div>`;
   }

   private getEntryIcon(classType: string): string {
      if(classType === AssetType.FOLDER) {
         return "folder-icon";
      }
      else if(classType === AssetType.WORKSHEET) {
         return "worksheet-icon";
      }

      return null;
   }

   assetsDroped(target: WorksheetBrowserInfo) {
      if(target == null) {
         let targetName = this.currentFolderPath?.length > 0 ?
            this.currentFolderPath[this.currentFolderPath.length - 1] : "";

         // fake info just use the path and scope for move.
         target = <WorksheetBrowserInfo> {
            name: targetName,
            path: this.currentFolderPathString,
            type: AssetType.FOLDER,
            scope: Number.parseInt(this.currentFolderPathScope, 10),
            description: "",
            id: "",
            createdBy: "",
            createdDate: -1,
            createdDateLabel: "",
            modifiedDate: -1,
            modifiedDateLabel: "",
            editable: false,
            deletable: false,
            materialized: false,
            canMaterialize: false,
            parentPath: "",
            parentFolderCount: -1,
            hasSubFolder: -1,
            workSheetType: -1
         };
      }

      if(!target || target.type != AssetType.FOLDER) {
         return;
      }

      let dragData = this.dragService.getDragData();

      if(dragData["dragWorksheets"]) {
         this.moveAssets0(JSON.parse(dragData["dragWorksheets"]), target);
      }
      else {
         // drag form data tree.
         this.dataTreeDragToPane(target, dragData);
      }
   }

   dataTreeDragToPane(target: WorksheetBrowserInfo, dragData: any) {
      let assets: AssetEntry[] = [];
      let parentPath = target.path;

      for(let key of Object.keys(dragData)) {
         let dragEntries: AssetEntry[] = JSON.parse(dragData[key]);

         if(dragEntries && dragEntries.length > 0) {
            for(let entry of dragEntries) {
               if(Tool.isEquals(parentPath, entry.path)) {
                  continue;
               }

               const entryPath = entry.path.endsWith("/") ? entry.path : entry.path + "/";

               if(parentPath.startsWith(entryPath)) {
                  continue;
               }

               if(entry.type != AssetType.FOLDER && entry.type != AssetType.WORKSHEET) {
                  continue;
               }

               assets.push(entry);
            }
         }
      }

      let assetsInfo = assets.map(asset => this.createInfoByAssetEntry(asset));
      this.moveAssets0(assetsInfo, target);
      this.dataBrowserService.changeFolder(this.currentFolderPathString, Number.parseInt(this.currentFolderPathScope, 10));
   }

   private moveAssets0(assets: WorksheetBrowserInfo[], target: WorksheetBrowserInfo) {
      assets = assets
         .filter(item => (item.type === AssetType.WORKSHEET || item.type === AssetType.FOLDER) &&
            (target.path != AssetUtil.getParentPath(item.path) &&
            !target.path.startsWith(item.path) || item.scope != target.scope));

      if(assets.length > 0) {
         ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
            "_#(js:em.reports.drag.confirm)").then((buttonClicked) =>
         {
            if(buttonClicked === "ok") {
               let scopeMap = new Map<number, WorksheetBrowserInfo[]>();

               for(let asset of assets) {
                  let scopeBrowserInfos = scopeMap.get(asset.scope);

                  if(!scopeBrowserInfos) {
                     scopeBrowserInfos = [];
                     scopeMap.set(asset.scope, scopeBrowserInfos);
                  }

                  scopeBrowserInfos.push(asset);
               }

               for(let key of scopeMap.keys()) {
                  this.moveAssets(assets, target, key);
               }
            }
         });
      }
   }

   /**
    * Create WorksheetBrowserInfo by AssentEntry for the move entries.
    * just type name path scope id is reliable, other properties is fake.
    *
    * @param asset
    * @private
    */
   private createInfoByAssetEntry(asset: AssetEntry): WorksheetBrowserInfo {
      return <WorksheetBrowserInfo> {
         name: AssetEntryHelper.getEntryName(asset),
         path: asset.path,
         type: asset.type,
         scope: asset.scope,
         description: asset.description,
         id: asset.identifier,
         createdBy: "",
         createdDate: 0,
         createdDateLabel: "",
         modifiedDate: 0,
         modifiedDateLabel: "",
         editable: true,
         deletable: true,
         materialized: true,
         canMaterialize: true,
         parentPath: "",
         parentFolderCount: 0,
         hasSubFolder: 0,
         workSheetType: 0
      };
   }

   showWSDetailsByDataSourcesTree(data: any) {
      this.currentFolderPathScope = data.scope;
      let path = data.path;

      if(!path || path === "/" || !path.includes("/")) {
         if(path === "/" && data.scope === AssetConstants.USER_SCOPE + "") {
            this.currentFolderPathScope = AssetConstants.GLOBAL_SCOPE + "";
         }

         path = "";
      }
      else {
         path = path.substring(0, path.lastIndexOf("/"));
      }

      this.refreshFolderBrowser(path, data.path);
   }
}
