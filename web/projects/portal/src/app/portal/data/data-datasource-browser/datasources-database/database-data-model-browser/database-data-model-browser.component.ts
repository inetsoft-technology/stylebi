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
import { Component, NgZone, OnDestroy, OnInit, ViewChild } from "@angular/core";
import { ActivatedRoute, ParamMap, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { HttpClient, HttpParams } from "@angular/common/http";
import { NotificationsComponent } from "../../../../../widget/notifications/notifications.component";
import { SortOptions } from "../../../../../../../../shared/util/sort/sort-options";
import { Subscription } from "rxjs";
import { FolderChangeService } from "../../../services/folder-change.service";
import { Tool } from "../../../../../../../../shared/util/tool";
import { SortTypes } from "../../../../../../../../shared/util/sort/sort-types";
import { DatasourceBrowserService } from "../../datasource-browser.service";
import { PhysicalModelBrowserInfo } from "../../../model/datasources/database/physical-model/physical-model-browser-info";
import { LogicalModelBrowserInfo } from "../../../model/datasources/database/physical-model/logical-model/logical-model-browser-info";
import { DataModelNameChangeService } from "../../../services/data-model-name-change.service";
import { DataModelBrowserService } from "./data-model-browser.service";
import {
   ListColumn,
   RouteLinkEntry
} from "../../../asset-item-list-view/asset-item-list-view.component";
import { DatabaseAsset } from "../../../model/datasources/database/database-asset";
import { AssemblyActionGroup } from "../../../../../common/action/assembly-action-group";
import { ActionsContextmenuComponent } from "../../../../../widget/fixed-dropdown/actions-contextmenu.component";
import { FixedDropdownService } from "../../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DropdownOptions } from "../../../../../widget/fixed-dropdown/dropdown-options";
import { Point } from "../../../../../common/data/point";
import { AssetItem } from "../../../model/datasources/database/asset-item";
import { AssetListBrowseModel } from "../../../model/datasources/database/asset-list-browse-model";
import { SearchCommand } from "../../../commands/search-command";
import { DatabaseDataModelBrowserModel } from "../../../model/datasources/database/database-data-model-browser-model";
import { DragService } from "../../../../../widget/services/drag.service";
import { DomService } from "../../../../../widget/dom-service/dom.service";
import { GuiTool } from "../../../../../common/util/gui-tool";
import { AssetType } from "../../../../../../../../shared/data/asset-type";
import {
   FeatureFlagsService,
   FeatureFlagValue
} from "../../../../../../../../shared/feature-flags/feature-flags.service";
import { AssetEntry } from "../../../../../../../../shared/data/asset-entry";
import { ComponentTool } from "../../../../../common/util/component-tool";
import {DataModelBrowserModel} from "./data-model-browser-model";
import { AppInfoService } from "../../../../../../../../shared/util/app-info.service";

const LOGICAL_MODEL_ASSET: string = "logical_model";
const PHYSICAL_VIEW_ASSET: string = "physical_model";
const FOLDER: string = "data_model_folder";
const DATABASE_DATA_MODEL_URI: string = "../api/data/database/dataModel/browse";
const SEARCH_DATA_MODEL_URI: string = "../api/data/search/dataModel";
const GET_DATA_MODEL_URI: string = "../api/data/database/dataModel/folder/browser";

export enum ActionType {
   DELETE,
   RENAME
}

@Component({
   templateUrl: "./database-data-model-browser.component.html",
   styleUrls: ["./database-data-model-browser.component.scss"]
})
export class DatabaseDataModelBrowserComponent implements OnDestroy, OnInit {
   @ViewChild("notifications") notifications: NotificationsComponent;
   pageModel: DatabaseDataModelBrowserModel;
   models: DatabaseAsset[] = [];
   databaseName: string;
   folderName: string;
   sortOptions: SortOptions = {
      keys: ["name"],
      type: SortTypes.ASCENDING,
      caseSensitive: false
   };
   SortTypes = SortTypes;
   selectionOn: boolean = false;
   selectedItems: DatabaseAsset[] = [];
   showDetailsItem: AssetItem = null;
   searchVisible: boolean = false;
   searchQuery: string = "";
   searchView: boolean = false;
   currentFolderPathString: string = "";
   currentSearchQuery: string = "";
   isdisableAction: boolean = true;
   rootLabel: string = "_#(js:Data Model)";
   fetchChildren = (item) => this.getChildren(item);
   private enterprise: boolean;
   private subscriptions: Subscription = new Subscription();
   private columns: ListColumn[]  = [
      {
         label: "_#(js:Name)",
         widthPercentage: this.searchView ? 15 : 30,
         sortKey: "name",
         visible: true,
         value: (item): string => item.name
      },
      {
         label: "_#(js:Location)",
         widthPercentage: 15,
         sortKey: "location",
         visible: this.searchView,
         value: (item): string => this.getParentPath(item),
         routerLink: (item) => this.getRouterPath(item)
      },
      {
         label: "_#(js:Type)",
         widthPercentage: 15,
         sortKey: "type",
         visible: true,
         value: (item): string => this.getTypeLabel(item)
      },
      {
         label: "_#(js:data.datasources.basedView)",
         widthPercentage: 15,
         sortKey: "physicalModel",
         visible: true,
         value: (item): string => this.getBasedView(item)
      },
      {
         label: "_#(js:data.datasources.createdBy)",
         widthPercentage: 15,
         sortKey: "createdBy",
         visible: true,
         value: (item): string => item.createdBy
      },
      {
         label: "_#(js:data.datasources.creationDate)",
         widthPercentage: 15,
         sortKey: "createdDateLabel",
         visible: true,
         value: (item): string => item.createdDateLabel
      }
   ];

   constructor(private folderChangeService: FolderChangeService,
               private dropdownService: FixedDropdownService,
               private httpClient: HttpClient,
               private modalService: NgbModal,
               private route: ActivatedRoute,
               private router: Router,
               private datasourceService: DatasourceBrowserService,
               private dataModelNameChangeService: DataModelNameChangeService,
               private dataModelBrowserService: DataModelBrowserService,
               private zone: NgZone,
               private dragService: DragService,
               private appInfoService: AppInfoService,
               private domService: DomService)
   {
   }

   ngOnInit(): void {
      this.appInfoService.isEnterprise().subscribe(info => this.enterprise = info);
      // subscribe to route parameters and refresh models based on current params
      this.subscriptions.add(this.route.queryParamMap.subscribe((params: ParamMap) => {
         this.searchView = params.has("query");
         this.databaseName = Tool.byteDecode(params.get("databaseName"));
         this.folderName = Tool.byteDecode(params.get("folderName"));

         if(this.searchView) {
            this.currentSearchQuery = params.get("query");
         }

         this.refreshModels();
      }));

      this.subscriptions.add(
         this.dataModelBrowserService.changed().subscribe(() => {
            this.refreshModels();
         })
      );
   }

   get listModel(): AssetListBrowseModel {
      return this.pageModel ? this.pageModel.listModel : null;
   }

   getListColumns(): ListColumn[] {
      return this.columns;
   }

   ngOnDestroy(): void {
      if(this.subscriptions) {
         this.subscriptions.unsubscribe();
         this.subscriptions = null;
      }
   }

   getRouterPath(asset: AssetItem): RouteLinkEntry {
      return {
         path: "/portal/tab/data/datasources/databaseModels",
         queryParams: {
            databaseName: this.databaseName,
            folderName: this.getParentPath(asset)
         }
      };
   }

   /**
    * Turn selection state on or off.
    */
   toggleSelectionState(): void {
      this.selectionOn = !this.selectionOn;
   }

   search(query: string): void {
      if(this.databaseName) {
         this.routeToFolder(this.databaseName, this.currentFolderPathString, query);
      }
   }

   clearSearch() {
      this.searchQuery = null;
      this.searchVisible = false;
      this.routeToFolder(this.databaseName, this.currentFolderPathString, this.searchQuery);
   }

   routeToFolder(database: string, folder?: string, query?: string) {
      let routePath = "/portal/tab/data/datasources/databaseModels";

      const extras = {
         queryParams: {
            databaseName: database,
            folderName: folder,
            query: query,
            temp: new Date().getTime()
         }
      };

      this.router.navigate([routePath], extras);
   }

   getParentPath(item: AssetItem): string {
      if((this.isPhysicalView(item) || this.isLogicalModel(item)) &&
         this.isExtend(<DatabaseAsset> item))
      {
         return "";
      }

      let forderName = this.isPhysicalView(item) ? (<PhysicalModelBrowserInfo> item).folderName
         : this.isLogicalModel(item) ? (<LogicalModelBrowserInfo> item).folderName : null;
      return forderName == null ? "/" : forderName;
   }

   getTypeLabel(item: AssetItem): string {
      let extend: boolean = (this.isPhysicalView(item) || this.isLogicalModel(item)) &&
         this.isExtend(<DatabaseAsset> item);

      if(this.isPhysicalView(item)) {
         return extend ? "_#(js:Extended View)" : "_#(js:asset.type.XPARTITION)";
      }
      else if(this.isLogicalModel(item)) {
         return extend ? "_#(js:Extended Model)" : "_#(js:asset.type.XLOGICALMODEL)";
      }
      else if(this.isFolder(item)) {
         return "_#(js:Folder)";
      }

      return "";
   }

   getBasedView(item: AssetItem): string {
      if(!item || !this.isLogicalModel(item)) {
         return "";
      }

      let logicalModel = <LogicalModelBrowserInfo> item;

      if(this.isExtend(logicalModel)) {
         return logicalModel.physicalModel + "/" +
            (!!logicalModel.connection ? logicalModel.connection : "(Default Connection)");
      }

      return (<LogicalModelBrowserInfo>item).physicalModel;
   }

   /**
    * Refresh contents of folder search browser with the given path.
    * @param path    the folder path to get browser content from
    * @param query   the query to search for
    */
   private refreshSearchBrowser(path: string, query: string) {
      this.httpClient.post(SEARCH_DATA_MODEL_URI,
         new SearchCommand(query, path || "/", 0, this.databaseName))
         .subscribe((data: DatabaseDataModelBrowserModel) => {
               this.pageModel = data;
               this.models = this.listModel?.items ? <DatabaseAsset[]> this.listModel?.items : [];
            },
            () => {
               this.models = [];
               this.notifications.danger("_#(js:data.datasets.searchError)");
            });
   }

   /**
    * Update current sort options and sort view.
    * @param key  the key to sort on
    */
   updateSortOptions(key: string): void {
      if(this.sortOptions.keys.indexOf(key) != -1) {
         if(this.sortOptions.type == SortTypes.ASCENDING) {
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

      this.sortModels();
   }

   /**
    * Sort all item by sort options.
    */
   sortModels(): void {
      const folders = Tool.sortObjects(
         this.models.filter(d => this.isFolder(d)), this.sortOptions);
      const items = Tool.sortObjects(
         this.models.filter(d => !this.isFolder(d)), this.sortOptions);
      this.models = folders.concat(items);
   }

   /**
    * Navigate to the edit vpm page for the selected data model.
    * @param model   the data model to edit
    */
   editModel(item: DatabaseAsset): void {
      if(this.isPhysicalView(item)) {
         let physicalItem: PhysicalModelBrowserInfo = <PhysicalModelBrowserInfo> item;
         let parent = physicalItem.parentView;
         let folder = physicalItem.folderName;

         if(parent) {
            this.router.navigate(["/portal/tab/data/datasources/database",
                  Tool.byteEncode(physicalItem.databaseName), "physicalModel",
                  Tool.byteEncode(item.name), {parent: Tool.byteEncode(parent), folder: folder}],
               {relativeTo: this.route});
         }
         else {
            this.router.navigate(["/portal/tab/data/datasources/database",
               Tool.byteEncode(physicalItem.databaseName), "physicalModel",
               physicalItem.name, {folder: folder}],
               {relativeTo: this.route});
         }
      }
      else if(this.isLogicalModel(item)) {
         let logicalModelItem: LogicalModelBrowserInfo = <LogicalModelBrowserInfo> item;
         let parent = logicalModelItem.parentModel;
         let folder = !!logicalModelItem.folderName ? logicalModelItem.folderName : "";

         if(parent) {
            this.router.navigate(["/portal/tab/data/datasources/database",
                  Tool.byteEncode(item.databaseName),
                  "physicalModel", Tool.byteEncode(logicalModelItem.physicalModel), "logicalModel",
                  Tool.byteEncode(item.name), {parent: Tool.byteEncode(parent), folder: folder}],
                  {relativeTo: this.route});
         }
         else {
            this.router.navigate(["/portal/tab/data/datasources/database",
                  Tool.byteEncode(logicalModelItem.databaseName), "physicalModel",
                  Tool.byteEncode(logicalModelItem.physicalModel), "logicalModel",
                  logicalModelItem.name, {parent: Tool.byteEncode(parent), folder: folder}],
               {relativeTo: this.route});
         }
      }
   }

   changeSelectedItems(items: AssetItem[]): void {
      this.disableAction();
      this.selectedItems = items.map(item => <DatabaseAsset> item);
   }

   /**
    * Send request to refresh vpms.
    */
   protected refreshModels(folder?: string): void {
      this.updateModels(folder);

      if(this.searchView) {
         this.refreshSearchBrowser(this.currentFolderPathString, this.currentSearchQuery);
      }
   }

   private updateModels(folder?: string) {
      let params: HttpParams = new HttpParams()
         .set("database", this.databaseName);

      if(!!folder) {
         this.folderName = folder;
      }

      if(this.folderName) {
         params = params.set("folder", this.folderName);
      }

      this.httpClient.get<DatabaseDataModelBrowserModel>(DATABASE_DATA_MODEL_URI, { params: params })
         .subscribe(
            data => {
               this.pageModel = data;
               this.models = !!this.listModel?.items ? <DatabaseAsset[]> this.listModel.items : [];
               this.currentFolderPathString = this.folderName;
               this.sortModels();
            },
            err => {}
         );
   }

   getIcon(): (item: DatabaseAsset) => string {
      return (item) => {
         if(this.isPhysicalView(item)) {
            return "partition-icon";
         }
         else if(this.isLogicalModel(item)) {
            return "logical-model-icon";
         }
         else if(this.isFolder(item)) {
            return "folder-icon";
         }

         return "";
      };
   }

   dragSupportFun(): (item: DatabaseAsset) => boolean {
      return (item) => {
         return !this.isFolder(item);
      };
   }

   getChildren(item: AssetItem): AssetItem[] {
      if(this.isLogicalModel(item)) {
         return (<LogicalModelBrowserInfo> item).extendModels;
      }
      else if(this.isPhysicalView(item ))
      {
         return (<PhysicalModelBrowserInfo> item).extendViews;
      }

      return [];
   }

   clickItem(item: AssetItem): void {
      if(this.isFolder(item)) {
         this.routeToFolder(this.databaseName, item.name);
      }
      else if(item.editable) {
         this.editModel(<DatabaseAsset> item);
      }
   }

   isRoot(): boolean {
      return !this.folderName;
   }

   /**
    * Add a data model folder for data source.
    * @param node
    */
   addDataModelFolder() {
      this.dataModelBrowserService.addDataModelFolder(this.databaseName, () => {
         this.notifications.success("_#(js:data.datasets.addFolderSuccess)");
         this.refreshListAndTree();
      });
   }

   addPhysicalView(folder?: string) {
      folder = folder ? folder : this.folderName;
      this.dataModelBrowserService.addPhysicalView(this.databaseName, folder);
   }

   addLogicalModel0(model: DatabaseAsset) {
      let viewName = this.isPhysicalView(model) ? model.name : null;
      let folder = this.isFolder(model) ? model.name : null;
      this.addLogicalModel(viewName, folder);
   }

   addLogicalModel(physicalModel?: string, folder?: string) {
      folder = folder ? folder : this.folderName;
      this.dataModelBrowserService.addLogicalModel(this.databaseName, physicalModel, folder);
   }

   addExtendModel(item: DatabaseAsset): void {
      let isView = this.isPhysicalView(item);
      const databaseName: string = item.databaseName;
      const name: string = item.name;
      let physicalModel;

      if(!databaseName || !name) {
         return;
      }

      if(!isView) {
         physicalModel = (<LogicalModelBrowserInfo> item).physicalModel;
      }

      this.dataModelBrowserService.addExtendModel(databaseName, name, physicalModel,
         isView, (<any> item).folderName);
   }

   /**
    * Open dialog to rename the vpm.
    * @param item the model to rename
    */
   renameModel(item: DatabaseAsset): void {
      if(this.isPhysicalView(item)) {
         this.dataModelBrowserService.renamePhysicalView(item.name,
            (<PhysicalModelBrowserInfo> item).databaseName, item.description,
            (<PhysicalModelBrowserInfo> item).folderName,
            this.actionCallback(item, ActionType.RENAME, false));
      }
      else if(this.isLogicalModel(item)) {
         this.dataModelBrowserService.renameLogicalModel(item.name,
            (<LogicalModelBrowserInfo> item).databaseName, item.description,
            (<LogicalModelBrowserInfo> item).folderName,
            this.actionCallback(item, ActionType.RENAME, false));
      }
      else if(this.isFolder(item)) {
         this.dataModelBrowserService.renameDataModelFolder(this.databaseName, item.name,
            this.actionCallback(item, ActionType.RENAME), false);
      }
   }

   isPhysicalView(item: AssetItem): boolean {
      return item?.type === PHYSICAL_VIEW_ASSET;
   }

   isLogicalModel(item: AssetItem): boolean {
      return item?.type === LOGICAL_MODEL_ASSET;
   }

   isFolder(item: AssetItem): boolean {
      return item?.type === FOLDER;
   }

   /**
    * Confirm then send request to delete a vpm.
    * @param model   the data model to delete
    * @param index   the data model index in models array
    */
   deleteModel(item: DatabaseAsset, folder: string): void {
      if(this.isPhysicalView(item)) {
         this.dataModelBrowserService.deletePhysicalView(item.name, item.databaseName,
            (<PhysicalModelBrowserInfo> item).parentView,
            folder, this.actionCallback(item, ActionType.DELETE, false));
      }
      else if(this.isLogicalModel(item)) {
         this.dataModelBrowserService.deleteLogicalModel(item.name, item.databaseName,
            (<LogicalModelBrowserInfo> item).extendModels,
            (<LogicalModelBrowserInfo> item).parentModel,
            folder, this.actionCallback(item, ActionType.DELETE, false));
      }
      else if(this.isFolder(item)) {
         this.dataModelBrowserService.deleteDataModelFolder(this.databaseName, item.name,
            this.actionCallback(item, ActionType.DELETE));
      }
   }

   private getActionMessage(actionType: ActionType, item: AssetItem): string {
      switch(actionType) {
         case ActionType.DELETE:
            return this.getDeleteMessage(item);
         case ActionType.RENAME:
            return this.getRenameMessage(item);
         default:
            return null;
      }
   }

   private getDeleteMessage(item: AssetItem): string {
      if(this.isPhysicalView(item)) {
         return "_#(js:data.datasources.deletePhysicalViewSuccess)";
      }
      else if(this.isLogicalModel(item)) {
         return "_#(js:data.datasources.deleteLogicalModelSuccess)";
      }
      else if(this.isFolder(item)) {
         return "_#(js:data.datasets.deleteFolderSuccess)";
      }

      return null;
   }

   private getRenameMessage(item: AssetItem): string {
      if(this.isPhysicalView(item)) {
         return "_#(js:data.datasources.renamePhysicalViewSuccess)";
      }
      else if(this.isLogicalModel(item)) {
         return "_#(js:data.logicalmodel.renameLogicalModelSuccess)";
      }
      else if(this.isFolder(item)) {
         return "_#(js:data.datasources.renameFolderSuccess)";
      }

      return null;
   }

   private actionCallback(item: AssetItem,
                          actionType: ActionType,
                          refreshTree: boolean = true): () => void
   {
      return () => {
         let msg = this.getActionMessage(actionType, item);

         if(!!msg) {
            this.notifications.success(msg);
         }

         this.refreshListAndTree(refreshTree);
      };
   }

   moveModel(item: DatabaseAsset): void {
      this.dataModelBrowserService.moveModels([item], () => {
         this.refreshListAndTree(false);
      });
   }

   refreshListAndTree(refreshTree: boolean = true): void {
      this.refreshModels();

      if(refreshTree) {
         this.datasourceService.refreshTree();
      }
   }

   moveSelected(): void {
      this.dataModelBrowserService.moveModels(this.selectedItems,
         () => {
            this.selectedItems = [];
            this.refreshListAndTree(false);
         });
   }

   deleteSelected(): void {
      this.dataModelBrowserService.deleteModels(this.databaseName, this.selectedItems,
         () => {
            this.selectedItems = [];
            this.refreshListAndTree(false);
         });
   }

   get moveDisable(): boolean {
      return !this.selectedItems || this.selectedItems.length === 0 || this.isdisableAction ||
         this.selectedItems.some(item => this.isFolder(item) || !item.deletable ||
            !item.editable || this.isExtend(item) && !this.isBaseModelSelected(item));
   }

   /**
    * Whether the base of item is selected.
    * @param item
    */
   isBaseModelSelected(item: DatabaseAsset): boolean {
      return this.selectedItems?.some(selectedItem => {
         if(this.isPhysicalView(item) && this.isPhysicalView(selectedItem)) {
            return (<PhysicalModelBrowserInfo> item).parentView == selectedItem.name;
         }
         else if(this.isLogicalModel(item) && this.isLogicalModel(selectedItem)) {
            return (<LogicalModelBrowserInfo> item).parentModel == selectedItem.name;
         }

         return false;
      });
   }

   /**
    * Whether the item is extend model or extend view.
    * @param item
    */
   isExtend(item: DatabaseAsset): boolean {
      if(this.isLogicalModel(item) && !!(<LogicalModelBrowserInfo> item).parentModel ||
         this.isPhysicalView(item) && !!(<PhysicalModelBrowserInfo> item).parentView)
      {
         return true;
      }

      return false;
   }

   setShowDetailsItem(item: AssetItem): void {
      if(this.showDetailsItem == item) {
         this.showDetailsItem = null;
      }
      else {
         this.showDetailsItem = item;
      }
   }

   openContextmenu(event: [AssetItem, MouseEvent | TouchEvent]) {
      let options: DropdownOptions = {
         position : new Point(),
         contextmenu: true
      };

      if(event[1] instanceof MouseEvent) {
         options.position = {x: (<MouseEvent> event[1]).clientX + 1,
            y: (<MouseEvent> event[1]).clientY};
      }
      else if(event[1] instanceof TouchEvent) {
         options.position = {x: (<TouchEvent> event[1]).targetTouches[0].pageX,
            y: (<TouchEvent> event[1]).targetTouches[0].pageY};
      }

      let contextmenu: ActionsContextmenuComponent =
         this.dropdownService.open(ActionsContextmenuComponent, options).componentInstance;
      contextmenu.sourceEvent = event[1];
      contextmenu.actions = this.createActions(<DatabaseAsset> event[0]);
   }

   private createActions(model: DatabaseAsset): AssemblyActionGroup[] {
      let group = new AssemblyActionGroup();
      let groups: AssemblyActionGroup[] = [group];
      group.actions = [
         {
            id: () => "data model move",
            label: () => "_#(js:Move)",
            icon: () => "",
            enabled: () => true,
            visible: () => !this.isFolder(model) && model.deletable && model.editable &&
               !this.isExtend(model),
            action: () => this.moveModel(model)
         },
         {
            id: () => "data model rename",
            label: () => "_#(js:Rename)",
            icon: () => "",
            visible: () => model.deletable && model.editable && !this.isExtend(model),
            enabled: () => true,
            action: () => this.renameModel(model)
         },
         {
            id: () => "data model delete",
            label: () => " _#(js:Delete)",
            icon: () => "",
            visible: () => model.deletable,
            enabled: () => true,
            action: () => this.deleteModel(model, this.folderName)
         },
         {
            id: () => "data model add physical view",
            label: () => "_#(js:Add Physical View)",
            icon: () => "",
            visible: () => model.editable && this.isFolder(model),
            enabled: () => true,
            action: () => this.addPhysicalView(model.name)
         },
         {
            id: () => "data model add logical model",
            label: () => "_#(js:Add Logical Model)",
            icon: () => "",
            visible: () => model.editable &&
               (this.isPhysicalView(model) && !this.isExtend(model) || this.isFolder(model)),
            enabled: () => true,
            action: () => this.addLogicalModel0(model)
         },
         {
            id: () => "data model add extended view",
            label: () => " _#(js:Add Extended View)",
            icon: () => "",
            visible: () => this.isPhysicalView(model) && this.pageModel.dbEditable &&
               this.listModel.editable && !this.isExtend(model) && this.enterprise,
            enabled: () => true,
            action: () => this.addExtendModel(model)
         },
         {
            id: () => "data model add extended model",
            label: () => "_#(js:Add Extended Model)",
            icon: () => "",
            visible: () => this.isLogicalModel(model) && this.pageModel.dbEditable &&
               this.listModel.editable && model.editable && !this.isExtend(model) && this.enterprise,
            enabled: () => true,
            action: () => this.addExtendModel(model)
         },
         {
            id: () => "data model details",
            label: () => " _#(js:Details)",
            icon: () => "",
            visible: () => !this.isFolder(model) && !this.isExtend(model),
            enabled: () => true,
            action: () => this.setShowDetailsItem(model)
         }
      ];

      return groups;
   }

   dragAssetsItems(event: { event: any; data: AssetItem[] }) {
      if(event.data.length <= 0) {
         return;
      }

      this.dragService.put("dragModelAssets", JSON.stringify(event.data));
      const labels = event.data.map(info => this.getAssetLabel(info));
      const elem = GuiTool.createDragImage(labels, [], 1, true);
      (<HTMLElement> elem).style.display = "flex";
      (<HTMLElement> elem).style.flexDirection = "column";
      (<HTMLElement> elem).style.lineHeight = "0.5";
      (<HTMLElement> elem).style.alignItems = "left";
      GuiTool.setDragImage(event.event, elem, this.zone, this.domService);
   }

   dropAssetsItems(target: AssetItem) {
      if(!this.isFolder(target)) {
         return;
      }

      let dragData = this.dragService.getDragData();

      if(dragData["dragModelAssets"]) {
         this.moveModels0(<DatabaseAsset[]> JSON.parse(dragData["dragModelAssets"]), target.name);
      }
      else {
         this.dataTreeDragToPane(<DatabaseAsset> target, dragData);
      }
   }

   private moveModels0(moveItems: DatabaseAsset[], folderName: string) {
      moveItems = moveItems.filter(item => !this.isFolder(item));

      if(moveItems.length <= 0) {
         return;
      }

      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
         "_#(js:em.reports.drag.confirm)").then((buttonClicked) =>
      {
         if(buttonClicked === "ok") {
            this.dataModelBrowserService.moveModelsToTarget(moveItems, folderName, () => {
               this.refreshListAndTree(false);
            });
         }
      });
   }

   private disableAction(): void {
      let params = new HttpParams().set("database", this.databaseName);

      this.httpClient.get<DataModelBrowserModel>(GET_DATA_MODEL_URI, {params})
          .subscribe(model =>{
             if(model.dataModelList.length != 0) {
                this.isdisableAction = false;
             }
      });

      this.isdisableAction = true;
   }

   dataTreeDragToPane(target: DatabaseAsset, dragData: any) {
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

               if(entry.type != AssetType.LOGIC_MODEL && entry.type != AssetType.PARTITION)
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

      let items: DatabaseAsset[] = this.dataModelBrowserService.createDataBaseAssets(assets);
      this.moveModels0(items, target.name);
   }

   private getAssetLabel(info: AssetItem): string {
      let textLabel = info.name;
      let entryIconFn = this.getEntryIcon.bind(this);

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

   private getEntryIcon(asset: AssetItem): string {
      if(this.isFolder(asset)) {
         return "folder-icon";
      }
      else if(this.isLogicalModel(asset)) {
         return "logical-model-icon";
      }
      else if(this.isPhysicalView(asset)) {
         return "partition-icon";
      }

      return null;
   }
}
