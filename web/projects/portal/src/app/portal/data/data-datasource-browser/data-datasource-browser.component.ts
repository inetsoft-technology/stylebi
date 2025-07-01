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
import {HttpClient, HttpParams} from "@angular/common/http";
import {AfterViewInit, Component, ElementRef, NgZone, OnDestroy, OnInit, Renderer2, ViewChild} from "@angular/core";
import {ActivatedRoute, ParamMap, ResolveStart, Router} from "@angular/router";
import {NgbModal, NgbPopover} from "@ng-bootstrap/ng-bootstrap";
import {Observable, of, Subscription} from "rxjs";
import {catchError, debounceTime, distinctUntilChanged, map, switchMap} from "rxjs/operators";
import {AssetType} from "../../../../../../shared/data/asset-type";
import { DateTypeFormatter } from "../../../../../../shared/util/date-type-formatter";
import {FormValidators} from "../../../../../../shared/util/form-validators";
import {SortOptions} from "../../../../../../shared/util/sort/sort-options";
import {SortTypes} from "../../../../../../shared/util/sort/sort-types";
import {Tool} from "../../../../../../shared/util/tool";
import {AssetConstants} from "../../../common/data/asset-constants";
import {RepositoryClientService} from "../../../common/repository-client/repository-client.service";
import {OpenComposerService} from "../../../common/services/open-composer.service";
import {ComponentTool} from "../../../common/util/component-tool";
import {GuiTool} from "../../../common/util/gui-tool";
import {CommandProcessor, ViewsheetClientService} from "../../../common/viewsheet-client";
import {MessageCommand} from "../../../common/viewsheet-client/message-command";
import {WSObjectType} from "../../../composer/dialog/ws/new-worksheet-dialog.component";
import {CreateQueryEventCommand} from "../../../composer/gui/vs/event/create-query-event-command";
import {SetComposedDashboardCommand} from "../../../vsobjects/command/set-composed-dashboard-command";
import {InputNameDialog} from "../../../widget/dialog/input-name-dialog/input-name-dialog.component";
import {ExpandStringDirective} from "../../../widget/expand-string/expand-string.directive";
import {AddFolderRequest} from "../commands/add-folder-request";
import {CheckDuplicateRequest} from "../commands/check-duplicate-request";
import {CheckDuplicateResponse} from "../commands/check-duplicate-response";
import {SearchCommand} from "../commands/search-command";
import {AddFolderConfig} from "../data-folder-browser/data-folder-browser.component";
import {PortalDataType} from "../data-navigation-tree/portal-data-type";
import {DataNotificationsComponent} from "../data-notifications.component";
import {DataSourceConnectionStatusRequest} from "../model/data-source-connection-status-request";
import {DataSourceInfo} from "../model/data-source-info";
import {DataSourceStatus} from "../model/data-source-status";
import {SearchDataSourceResultsModel} from "../model/search-data-source-results-model";
import {DataSourceBrowserModel} from "./data-source-browser-model";
import {DatasourceBrowserService} from "./datasource-browser.service";
import {DragService} from "../../../widget/services/drag.service";
import {DomService} from "../../../widget/dom-service/dom.service";
import {AssetEntry} from "../../../../../../shared/data/asset-entry";
import {SelectedDataSourcesRequest} from "../commands/selected-datasources-request";
import {FeatureFlagsService} from "../../../../../../shared/feature-flags/feature-flags.service";
import {AssetUtil} from "../../../binding/util/asset-util";
import {MultiObjectSelectList} from "../../../common/util/multi-object-select-list";

const CREATE_QUERY_URI = "/events/composer/ws/query/create";
const DATASOURCES_URI: string = "../api/data/datasources";
const DATASOURCE_CHECK_DEPENDENCIES: string = "../api/data/datasources/checkOuterDependencies";
const DATASOURCE_BROWSER_URI: string = "../api/data/datasources/browser";
const DATASOURCE_FOLDER_ADD = "../api/data/datasources/browser/folder/add";
const DATASOURCE_FOLDER_CHECKDUPLICATE = "../api/data/datasources/browser/folder/checkDuplicate";
const DATASOURCE_FOLDER_CHECK_DEPENDENCIES = "../api/data/datasources/browser/folder/checkOuterDependencies";
const DATASOURCE_FOLDER_URI = "../api/data/datasources/browser/folder";
const DATASOURCE_MOVE_URI = "../api/data/datasources/move";
const DATASOURCE_SEARCH_URI: string = "../api/data/search/dataSources";
const DATASOURCES_LIST_URI: string = "../api/data/dataSources/list";
const DATASOURCE_STATUSES_URI  = DATASOURCES_URI + "/statuses";

@Component({
   selector: "p-datasource-browser",
   templateUrl: "data-datasource-browser.component.html",
   styleUrls: ["data-datasource-browser.component.scss"],
   providers: [ViewsheetClientService]
})
export class DataDatasourceBrowserComponent extends CommandProcessor implements AfterViewInit, OnInit, OnDestroy {
   @ViewChild("dataNotifications") dataNotifications: DataNotificationsComponent;
   @ViewChild("searchInput") searchInput: ElementRef;
   @ViewChild("newQueryPopover", {read: NgbPopover}) newQueryPopover: NgbPopover;
   datasources: DataSourceInfo[] = [];
   sortOptions: SortOptions = new SortOptions(["name"], SortTypes.ASCENDING);
   SortTypes = SortTypes;
   newDatasourceEnabled = false;
   newVpmEnabled = false;
   currentFolderPathString: string = "";
   currentFolderScope: string = "";
   currentSearchQuery: string = "";
   folders: DataSourceInfo[] = [];
   physicalTablePermission: boolean;
   searchVisible: boolean = false;
   searchQuery: string = "";
   searchAssets: DataSourceInfo[] = [];
   searchView: boolean = false;
   selectedItems: DataSourceInfo[] = [];
   selectedFile: DataSourceInfo = null;
   selectionOn: boolean = false;
   isdisableAction: boolean = true;
   updatingStatus = false;
   private composedDashboard = false;
   private requests = new Subscription();
   private attemptingConnectionStatus: string = "_#(js:data.datasources.attemptingToConnectToDataSource)";
   private failedConnectionStatus: string = "_#(js:data.datasources.problemRetrievingDataSourceStatus)";
   searchFunc: (text: Observable<string>) => Observable<any> = (text: Observable<string>) =>
      text.pipe(
         debounceTime(300),
         distinctUntilChanged(),
         switchMap((term) => this.httpClient.post(DATASOURCE_SEARCH_URI + "/names",
            new SearchCommand(term, this.currentFolderPathString || "/", +this.currentFolderScope))),
         catchError(() => of([])),
         map((data: SearchDataSourceResultsModel) => {
            return !!data && !!data.dataSourceNames ? data.dataSourceNames.slice(0, 8) : [];
         })
      );
   private subscriptions: Subscription = new Subscription();
   private multiObjectSelectList: MultiObjectSelectList<DataSourceInfo>;

   constructor(private httpClient: HttpClient,
               private modalService: NgbModal,
               public viewsheetClient: ViewsheetClientService,
               private datasourceService: DatasourceBrowserService,
               private route: ActivatedRoute,
               private renderer: Renderer2,
               private router: Router,
               private zone: NgZone,
               private openComposerService: OpenComposerService,
               private repositoryClient: RepositoryClientService,
               private dragService: DragService,
               private domService: DomService,
               private featureFlagsService: FeatureFlagsService)
   {
      super(viewsheetClient, zone, true);
      this.multiObjectSelectList = new MultiObjectSelectList<DataSourceInfo>();
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
         isDefaultCubesName: ExpandStringDirective.expandString(
            "_#(js:common.cube.defaulecubesname)", ["Cubes"]),
      };
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
    * Get the assets to display in the folder browser view.
    * @returns {WorksheetBrowserInfo[]}  the assets to display
    */
   get viewAssets(): DataSourceInfo[] {

      return this.searchView ? this.searchAssets : this.folders.concat(this.datasources);
   }

   get moveDisable(): boolean {
      return !this.selectedItems || this.selectedItems.length === 0 || this.isdisableAction
         || !this.isSelectionDeletable() || !this.isSelectionEditable();
   }

   ngOnInit(): void {
      this.subscriptions = this.route.queryParamMap
          .subscribe((params: ParamMap) => {
             this.searchView = params.has("query");

             if(this.searchView) {
                this.currentSearchQuery = params.get("query");
             }

             let path: string = params.get("path");
             path = !path || path === "/" ? "" : path;
             this.currentFolderScope = params.get("scope");
             this.refreshAllData(path);

             if(this.currentFolderPathString != path) {
                this.selectedItems = [];
             }
          });

      this.repositoryClient.connect();
      this.viewsheetClient.connect();
      this.subscriptions.add(this.repositoryClient.dataChanged
         .subscribe(() => this.refreshAllData(this.currentFolderPathString)));
      this.subscriptions.add(this.datasourceService.getPhysicalTablePermission()
         .subscribe(hasPermison => this.physicalTablePermission = hasPermison));

      this.subscriptions.add(this.router.events.subscribe(e => {
         if(e instanceof ResolveStart) {
            this.requests.unsubscribe();
            this.requests = new Subscription();
         }
      }));
   }

   ngAfterViewInit(): void {
      if(this.newQueryPopover) {
        this.newQueryPopover.open();
      }
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
      this.requests.unsubscribe();
   }

   clickItem(datasource: DataSourceInfo): void {
      if(this.isDataSourceFolder(datasource)) {

         if(datasource.type.name === AssetType.DATA_SOURCE_FOLDER) {
            this.selectedItems = [];
         }

         this.datasourceService.changeFolder(datasource.path);
         this.refreshAllData(datasource.path);
      }
   }

   processSetComposedDashboardCommand(command: SetComposedDashboardCommand): void {
      this.composedDashboard = true;
   }

   private disableAction(): void {
      this.httpClient.get<DataSourceInfo[]>(DATASOURCES_LIST_URI).subscribe(model => {
         for (let i = 0; i < model.length; i++) {
            if(this.isDataSourceFolder(model[i])) {
               this.isdisableAction = false;
            }
         }
      });

      this.isdisableAction = true;
   }

   /**
    * Refresh datasource types and datasources.
    * Send request to refresh list of data sources and the data source types available.
    */
   private refreshAllData(path: string): void {
      this.updateDataSources(path);

      if(this.searchView) {
         this.refreshSearchBrowser(path, this.currentSearchQuery);
      }
   }

   private updateDataSources(path: string) {
      this.httpClient.get<DataSourceBrowserModel>(DATASOURCE_BROWSER_URI, {
         params: !!path ? new HttpParams().set("path", path) : null
      })
         .subscribe(model => {
            this.newDatasourceEnabled = model.newDatasourceEnabled;
            this.newVpmEnabled = model.newVpmEnabled;
            this.datasources = this.sortDataSources(model.dataSourceList, this.sortOptions);
            this.multiObjectSelectList.setObjectsKeepSelection(this.datasources);
            this.updateSelectedItems(this.datasources);
            this.fetchDataSourceStatuses(this.datasources, false);
            this.currentFolderPathString = path;
         });
   }

   public loadDataSourceStatus() {
      // in selection mode, load only the selected data sources
      this.fetchDataSourceStatuses(
         this.selectionOn ? this.selectedItems : this.datasources, true);
   }

   private fetchDataSourceStatuses(datasources: DataSourceInfo[], updateStatus: boolean): void {
      const dsCopy = datasources.filter(ds => !this.isDataSourceFolder(ds));

      if(dsCopy.length == 0) {
         return;
      }

      if(updateStatus) {
         // don't allow multiple requests to update the status at the same time
         if(this.updatingStatus) {
            return;
         }

         dsCopy.forEach(ds => ds.statusMessage = this.attemptingConnectionStatus);
         this.updatingStatus = true;
      }

      const names = dsCopy.map(ds => ds.path);
      const request = <DataSourceConnectionStatusRequest>{
         paths: names,
         updateStatus: updateStatus,
         timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone
      };

      const sub = this.httpClient.post<DataSourceStatus[]>(DATASOURCE_STATUSES_URI, request)
         .subscribe(statuses => {
            for(let i = 0; i < dsCopy.length; i++) {
               if(!!statuses[i]) {
                  dsCopy[i].statusMessage = statuses[i].message;
                  dsCopy[i].connected = statuses[i].connected;
               }
            }
         }, () => {
            dsCopy.forEach(ds => {
               ds.statusMessage = this.failedConnectionStatus;
               ds.connected = false;
            });
         }, () => {
            if(updateStatus) {
               this.updatingStatus = false;
            }

            this.requests.remove(sub);
         });

      this.requests.add(sub);
   }

   /**
    * Send request to refresh a data source and its status.
    * @param datasource the data source to refresh
    */
   refreshDataSource(datasource: DataSourceInfo): void {
      this.httpClient.get(DATASOURCES_URI + "/refresh/"
            + Tool.encodeURIComponentExceptSlash(datasource.path))
         .subscribe(
            () => this.dataNotifications.notifications.success(
               "_#(js:data.datasources.refreshSuccess)"),
            () => this.dataNotifications.notifications.danger(
               "_#(js:data.datasources.refreshError)")
         );

      if(!this.isDataSourceFolder(datasource)) {
         this.fetchDataSourceStatuses([datasource], true);
      }
   }

   /**
    * Refresh contents of folder search browser with the given path.
    * @param path    the folder path to get browser content from
    * @param query   the query to search for
    */
   private refreshSearchBrowser(path: string, query: string) {
      this.httpClient.post(DATASOURCE_SEARCH_URI,
         new SearchCommand(query, path || "/", +this.currentFolderScope))
         .subscribe((data: SearchDataSourceResultsModel) => {
               this.datasources = data.dataSourceInfos;
               this.multiObjectSelectList.setObjectsKeepSelection(this.datasources);
               this.sortOptions = new SortOptions([], SortTypes.ASCENDING);
               this.fetchDataSourceStatuses(this.datasources, false);
            },
            () => {
               this.datasources = [];
               this.multiObjectSelectList.setObjectsKeepSelection(this.datasources);
               this.dataNotifications.notifications.danger("_#(js:data.datasets.searchError)");
            });
   }

   getParentPath(path: string): any {
      let arr: string[] = path.split("/");
      return arr.length == 1 ? "/" : arr[arr.length - 2];
   }

   getParentRouterLinkParams(path: string): any {
      let arr: string[] = path.split("/");
      let parentPath = arr.length == 1 ? "/" : arr[arr.length - 2];
      return {path: parentPath, scope: 0};
   }

   /**
    * Update current sort options and sort view.
    * @param key  the key to sort on
    */
   updateSortOptions(key: string): void {
      if(this.sortOptions.keys.includes(key)) {
         if(this.sortOptions.type === SortTypes.ASCENDING) {
            this.sortOptions.type = SortTypes.DESCENDING;
         }
         else {
            this.sortOptions.type = SortTypes.ASCENDING;
         }
      }
      else {
         this.sortOptions.keys = [key];
         this.sortOptions.type = SortTypes.ASCENDING;
      }

      this.datasources = this.sortDataSources(this.datasources, this.sortOptions);
      this.multiObjectSelectList.setObjectsKeepSelection(this.datasources);
   }

   private sortDataSources(datasources: DataSourceInfo[],
                           sortOptions: SortOptions): DataSourceInfo[]
   {
      const folders = Tool.sortObjects(
         datasources.filter(d => d.type.name == "DATA_SOURCE_FOLDER"), sortOptions);
      const datasets = Tool.sortObjects(
         datasources.filter(d => d.type.name != "DATA_SOURCE_FOLDER"), sortOptions);
      return folders.concat(datasets);
   }

   // end user-managed drivers is dangerous, disabling for now until we figure
   // out a safe way to do this
   // /**
   //  * Navigate to the manage drivers page.
   //  */
   // manageDrivers(): void {
   //    this.router.navigate(["/portal/tab/data/drivers"]);
   // }

   showListings(): void {
      let queryParams = {
         path: this.currentFolderPathString,
         scope: this.currentFolderScope,
      };

      this.router.navigate(["listing", this.currentFolderPathString],
          {relativeTo: this.route, queryParams: queryParams});
   }

   /**
    * Get the icon to show for the data source status.
    * @param datasource the data source to get the icon for
    * @returns {string} string containing css class for icon
    */
   getDatasourceStatusIcon(datasource: DataSourceInfo): string {
      if(datasource.statusMessage === this.attemptingConnectionStatus) {
         return "help-question-mark-icon text-warning";
      }
      else if(this.isDataSourceFolder(datasource) || !datasource.statusMessage) {
         return "";
      }
      else if(!datasource.connected) {
         return "alert-circle-icon text-danger";
      }
      else {
         return "submit-icon text-success";
      }
   }

   isDataSourceFolder(datasource: DataSourceInfo): boolean {
      return !!datasource && !!datasource.type
         && datasource.type.name === PortalDataType.DATA_SOURCE_FOLDER;
   }

   isDataSource(datasource: DataSourceInfo): boolean {
      return !!datasource && !!datasource.type
         && (datasource.type.name === PortalDataType.DATABASE ||
         datasource.type.name === PortalDataType.XMLA_SOURCE);
   }

   toggleSearch(event: any) {
      if(!this.searchVisible) {
         this.searchVisible = true;

         let collapseSearchListener: any = this.renderer.listen("document", "click",
            (targetEvent: any) => {
            if(event !== targetEvent && targetEvent.target != this.searchInput?.nativeElement) {
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

         if(!!this.searchQuery) {
            this.search();
         }
      }
   }

   search(query: string = null): void {
      if(!!query) {
         this.searchQuery = query;
      }

      if(!this.searchQuery) {
         return;
      }

      const queryParams: any = { query: this.searchQuery };

      if(this.currentFolderScope) {
         queryParams.scope = this.currentFolderScope;
      }

      if(this.currentFolderPathString) {
         queryParams.path = this.currentFolderPathString;
      }

      const extras = {
         queryParams: queryParams,
         relativeTo: this.route.parent
      };

      this.router.navigate(["datasources"], extras);
      this.searchQuery = null;
   }

   /**
    * Clear current search.
    */
   clearSearch(): void {
      this.searchQuery = null;
      this.searchVisible = false;
      const extras = {
         queryParams: !this.currentFolderPathString ? { scope: this.currentFolderScope } :
            { path: this.currentFolderPathString, scope: this.currentFolderScope },
         relativeTo: this.route.parent
      };
      this.router.navigate(["datasources"], extras);
   }

   /**
    * add folder
    */
   addFolder(): void {
      if(!this.newDatasourceEnabled) {
         return;
      }

      const config = this.addFolderConfig;

      const dialog = ComponentTool.showDialog(this.modalService, InputNameDialog,
         (result: string) => {
            const request = new AddFolderRequest(result.trim(), this.currentFolderPathString, null);
            this.httpClient.post(DATASOURCE_FOLDER_ADD, request)
               .subscribe(
                  () => {
                     this.dataNotifications.notifications.success(config.addFolderSuccess);
                     this.refreshAllData(this.currentFolderPathString);
                     this.datasourceService.refreshTree();
                  },
                  () => this.dataNotifications.notifications.danger(config.addFolderError));
         });

      dialog.title = config.newfolder;
      dialog.label = config.folderName;
      dialog.helpLinkKey = "DataSourceNewFolder";
      dialog.validators = [
         FormValidators.required,
         FormValidators.isValidDataSourceFolderName
      ];
      dialog.validatorMessages = [
         {validatorName: "required", message: config.folderNameRequired},
         {validatorName: "containsSpecialCharsForName", message: config.folderNameInvalid},
         {validatorName: "isDefaultCubesName", message: config.isDefaultCubesName}
      ];
      dialog.hasDuplicateCheck = (value: string) =>
         this.httpClient
            .post<CheckDuplicateResponse>(DATASOURCE_FOLDER_CHECKDUPLICATE,
               <CheckDuplicateRequest> {
                  path: this.currentFolderPathString,
                  newName: value,
                  type: AssetType.DATA_SOURCE_FOLDER,
                  scope: null})
            .pipe(map(response => response.duplicate));
   }

   /**
    * rename folder
    * @param datasource folder.
    */
   renameFolder(datasource: DataSourceInfo): void {
      this.datasourceService.renameDataSourceFolder(datasource, this.currentFolderPathString,
         () => {
            this.refreshAllData(this.currentFolderPathString);
            this.datasourceService.refreshTree();
         });
   }

   moveDataSource(datasource: DataSourceInfo): void {
      if(!(datasource.editable && datasource.deletable)) {
         return;
      }

      this.datasourceService.moveDataSource(datasource, this.currentFolderPathString,
         () => {
            this.refreshAllData(this.currentFolderPathString);
            this.datasourceService.refreshTree();
         },
         (error) => {
            this.dataNotifications.notifications.danger(error.error.message);
         });
   }

   moveSelected(): void {
      for(let i = 0; i < this.selectedItems.length; i++) {
         let ds = this.selectedItems[i];

         if(!(ds.editable && ds.deletable)) {
            return;
         }
      }

      this.datasourceService.moveSelected(this.selectedItems, this.currentFolderPathString, () => {
            this.refreshAllData(this.currentFolderPathString);
            this.datasourceService.refreshTree();
            this.selectedItems = [];
         },
         (error) => {
            this.dataNotifications.notifications.danger(error.error.message);
         });
   }

   /**
    * Get the icon to show for the data source.
    * @param datasource the data source to get the icon for
    * @returns {string} string containing css class for icon
    */
   getDatasourceIcon(datasource: DataSourceInfo): string {
      if(datasource.type.name === "DATABASE") {
         return "database-icon";
      }
      else if(datasource.type.name === "Text and Excel Files") {
         return "TXT-icon";
      }
      else if(datasource.type.name === PortalDataType.XMLA_SOURCE) {
         return "cube-icon";
      }
      else if(this.isDataSourceFolder(datasource)) {
         return "folder-icon";
      }
      else {
         return "tabular-data-icon";
      }
   }

   /**
    * Navigate to the edit data source page for the selected data source.
    * @param datasource the data source to edit
    */
   editDataSource(datasource: DataSourceInfo): void {
      if(!datasource || !datasource.editable) {
         return;
      }

      const queryParams: any = {
         path: this.currentFolderPathString,
         scope: AssetConstants.QUERY_SCOPE
      };

      if(datasource.type.name === PortalDataType.DATABASE) {
         this.router.navigate(["database", Tool.byteEncode(datasource.path)],
            { relativeTo: this.route, queryParams: queryParams });
      }
      else {
         if(datasource.type.name == PortalDataType.XMLA_SOURCE) {
            this.router.navigate(["datasource/xmla/edit", Tool.byteEncode(datasource.path)],
               { relativeTo: this.route, queryParams });
         }
         else {
            this.router.navigate(["datasource", Tool.byteEncode(datasource.path)],
               { relativeTo: this.route, queryParams });
         }
      }
   }

   /**
    * delete a datasource or folder
    * @param datasource datasource or folder
    * @param index
    */
   deleteItem(datasource: DataSourceInfo, index: number): void {
      if(!datasource.deletable) {
         return;
      }

      if(this.isDataSourceFolder(datasource)) {
         this.datasourceService.deleteDataSourceFolder(datasource,
            (type: string, message: string) => this.handleResponse(type, message));
      }
      else {
         this.datasourceService.deleteDataSourceByInfo(datasource,
             (type: string, message: string) => this.handleResponseDatasource(type, message, index));
      }
   }

   handleResponse(type: string, message: string): void {
      if(type == "success") {
         // refresh current folder data
         this.refreshAllData(this.currentFolderPathString);
         this.datasourceService.refreshTree();
         this.dataNotifications.notifications.success(message);
      }
      else if(type == "danger") {
         this.dataNotifications.notifications.danger(message);
      }
   }

   handleResponseDatasource(type: string, message: string, index: number): void{
      if(type == "success") {
         // refresh current folder data
         this.datasources.splice(index, 1);
         this.datasourceService.refreshTree();
         this.dataNotifications.notifications.success(message);
      }
      else if(type == "danger") {
         this.dataNotifications.notifications.danger(message);
      }
   }

   /**
    * Create Query.
    */
   createQuery(datasource: DataSourceInfo): void {
      if(!datasource.queryCreatable) {
         return;
      }

      const baseDataSource: string = datasource.path;
      const baseDataSourceType: number = this.getDataSourceObjectType(datasource);

      if(this.composedDashboard) {
         this.openComposer(baseDataSource, baseDataSourceType, true);
      }
      else {
         this.openComposerService.composerOpen.subscribe(open => {
            if(open) {
               let event = new CreateQueryEventCommand(baseDataSource, baseDataSourceType);
               this.viewsheetClient.sendEvent(CREATE_QUERY_URI, event);
            }
            else {
               this.openComposer(baseDataSource, baseDataSourceType);
            }
         });
      }
   }

   createPhysicalView(datasource: DataSourceInfo): void {
      if(this.isDataSourceFolder(datasource) || !datasource.editable) {
         return;
      }

      this.datasourceService.onCreateEvent.next({
         datasource
      });
   }

   createVPM(datasource: DataSourceInfo): void {
      if(this.isDataSourceFolder(datasource) || !datasource.editable) {
         return;
      }

      this.datasourceService.onCreateEvent.next({
         datasource,
         vpm: true
      });
   }

   private processCreateQueryEventCommand(command: CreateQueryEventCommand): void {
      this.openComposer(command.baseDataSource, command.baseDataSourceType);
   }

   private processMessageCommand(msg: MessageCommand): void {
      this.dataNotifications.notifications.info(msg.message);
   }

   private openComposer(baseDataSource: string, baseDataSourceType: number,
                        deployed: boolean = false): void
   {
      const url = "composer";
      const params = new HttpParams()
         .set("wsWizard", "true")
         .set("baseDataSource", baseDataSource)
         .set("baseDataSourceType", `${baseDataSourceType}`)
         .set("deployed", `${deployed}`);
      GuiTool.openBrowserTab(url, params);
   }

   getDataSourceObjectType(datasource: DataSourceInfo): number {
      switch(datasource.type.name) {
         case PortalDataType.DATA_SOURCE:
            return WSObjectType.TABULAR;
         case PortalDataType.DATABASE:
            return WSObjectType.DATABASE_QUERY;
         default:
            return -1;
      }
   }

   getAssemblyName(): string {
      return null;
   }

   dragAsset(event: DragEvent, asset: DataSourceInfo) {
      let selectedObjects = this.multiObjectSelectList.getSelectedObjects();
      let dragAssets = selectedObjects.includes(asset) ? selectedObjects : [ asset ];
      this.dragService.put("dragDataSources", JSON.stringify(dragAssets));

      const labels = dragAssets.map(info => this.getEntryLabel(info));
      const elem = GuiTool.createDragImage(labels, [], 1, true);
      (<HTMLElement> elem).style.display = "flex";
      (<HTMLElement> elem).style.flexDirection = "column";
      (<HTMLElement> elem).style.lineHeight = "0.5";
      (<HTMLElement> elem).style.alignItems = "left";
      GuiTool.setDragImage(event, elem, this.zone, this.domService);
   }

   private getEntryLabel(info: DataSourceInfo): string {
      let textLabel = info.name;
      let entryIconFn = this.getDatasourceIcon.bind(this);

      return `
      <div>
      <span>
        <span class="${entryIconFn(info)}">
        </span>
        <label>
        ${textLabel}
        </label>
      </span>
      </div>`;
   }

   dropAssets(event: DragEvent, datasource: DataSourceInfo) {
      if(datasource && !this.isDataSourceFolder(datasource)) {
         return;
      }

      event.stopPropagation();
      let dragData = this.dragService.getDragData();

      if(dragData["dragDataSources"]) {
         this.moveDataSources0(
            JSON.parse(dragData["dragDataSources"]), datasource.path);
      }
      else {
         this.dataTreeDragToPane(datasource, dragData);
      }
   }

   dataTreeDragToPane(target: DataSourceInfo, dragData: any) {
      let assets: AssetEntry[] = [];
      let parentPath = target?.path || this.currentFolderPathString;

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

               if(entry.type != AssetType.DATA_SOURCE_FOLDER && entry.type != AssetType.DATA_SOURCE)
               {
                  continue;
               }

               assets.push(entry);
            }
         }
      }

      if(assets.length <= 0) {
         return;
      }

      let infos = this.datasourceService.createDataSourceInfos(assets);
      this.moveDataSources0(infos, target?.path || this.currentFolderPathString);
   }

   private moveDataSources0(datasources: DataSourceInfo[], targetFolder: string) {
      datasources = datasources
         .filter(item => (this.isDataSourceFolder(item) || this.isDataSource(item)) &&
            targetFolder != AssetUtil.getParentPath(item.path) &&
            !targetFolder.startsWith(item.path));

      if(datasources.length > 0) {
         ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
            "_#(js:em.reports.drag.confirm)").then((buttonClicked) =>
         {
            if(buttonClicked === "ok") {
               this.datasourceService.moveDataSourcesToFolder(datasources, targetFolder,
                  () => this.refreshAllData(this.currentFolderPathString));
            }
         });
      }
   }

   isSelectedItem(datasource: DataSourceInfo): boolean {
      return this.multiObjectSelectList.isSelected(datasource);
   }

   updateAssetSelection(datasource: DataSourceInfo, event: MouseEvent) {
      this.multiObjectSelectList.selectWithEvent(datasource, event);
   }

   /**
    * Turn selection state on or off.
    */
   toggleSelectionState(): void {
      this.selectionOn = !this.selectionOn;
      this.selectedItems=[];
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
    * Update the selected state of an asset item.
    * @param item    the asset item being updated
    */
   updateSelection(item: DataSourceInfo): void {
      const index: number = this.selectedItems.indexOf(item);
      this.disableAction();

      if(index !== -1) {
         this.selectedItems.splice(index, 1);
      }
      else {
         this.selectedItems.push(item);
      }
   }

   deleteSelected(): void {
      const request = this.selectedItems.reduce<SelectedDataSourcesRequest>((previous, current) => {
           const item = { name: current.name, path: current.path };

           if(current.type.name === AssetType.DATA_SOURCE_FOLDER) {
              previous.folders.push(item);
           }
           else {
              previous.dataSources.push(item);
           }

           return previous;
        }, { dataSources: [], folders: [] });

      this.httpClient.post(DATASOURCE_CHECK_DEPENDENCIES+"/selected", request).subscribe((result: any) => {
         if(!!result) {
            ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", "_#(js:data.datasets.deleteItemsDependencyError)\n" + result.body,
                {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
                .then((btn) => {
                   if(btn == "yes") {
                      this.deleteSelected0(request);
                   }
                });
         }
         else {
            ComponentTool.showConfirmDialog(this.modalService, "_#(js:Delete)",
                "_#(js:data.datasets.confirmDeleteItems)")
                .then(buttonClicked => {
                   if (buttonClicked === "ok") {
                      this.deleteSelected0(request);
                   }
                });
         }

      });
   }

   deleteSelected0(request: SelectedDataSourcesRequest): void {
      this.httpClient.post(DATASOURCES_URI+"/deleteDataSources", request).subscribe();
      this.selectedItems=[];
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
      this.disableAction();
      this.selectedItems = [];

      if(checked) {
         this.selectedItems.push(...this.viewAssets);
      }
   }

   public getDateLabel(dateNumber: number, dateFormat): string {
      return DateTypeFormatter.getLocalTime(dateNumber,  dateFormat);
   }

   private updateSelectedItems(newDataSources: DataSourceInfo[]) {
      if(!this.selectedItems || this.selectedItems.length == 0) {
         return;
      }

      this.selectedItems = this.selectedItems
         .map(ds => newDataSources.find(newDs => newDs.path === ds.path))
         .filter(ds => !!ds);
   }
}
