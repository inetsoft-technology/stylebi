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
import { Component, ElementRef, NgZone, OnDestroy, OnInit, ViewChild } from "@angular/core";
import { ActivatedRoute, NavigationEnd, ParamMap, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable, of, Subscription } from "rxjs";
import { switchMap, tap } from "rxjs/operators";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { DatasourceDatabaseType } from "../../../../../../shared/util/model/datasource-database-type";
import { Tool } from "../../../../../../shared/util/tool";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { AssetClientService } from "../../../common/asset-client/asset-client.service";
import {
   RepositoryClientService
} from "../../../common/repository-client/repository-client.service";
import { ComponentTool } from "../../../common/util/component-tool";
import { GuiTool } from "../../../common/util/gui-tool";
import { CommandProcessor, ViewsheetClientService } from "../../../common/viewsheet-client";
import {
   ActionsContextmenuComponent
} from "../../../widget/fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../../../widget/fixed-dropdown/dropdown-options";
import { DebounceService } from "../../../widget/services/debounce.service";
import { DragService } from "../../../widget/services/drag.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { TreeComponent } from "../../../widget/tree/tree.component";
import { CheckDuplicateResponse } from "../commands/check-duplicate-response";
import { CheckItemsDuplicateCommand } from "../commands/check-items-duplicate-command";
import { CheckMoveDuplicateRequest } from "../commands/check-move-duplicate-request";
import { MoveCommand } from "../commands/move-command";
import {
   CreateEventInfo,
   DatasourceBrowserService
} from "../data-datasource-browser/datasource-browser.service";
import { DataBrowserService } from "../data-folder-browser/data-browser.service";
import { DataNotificationsComponent } from "../data-notifications.component";
import { DataSourceInfo } from "../model/data-source-info";
import { WorksheetBrowserInfo } from "../model/worksheet-browser-info";
import { PortalDataType } from "./portal-data-type";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { ValidatorFn, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { DatasourceTreeAction } from "../model/datasources/database/datasource-tree-action";
import { DataModelNameChangeService } from "../services/data-model-name-change.service";
import {
   DataModelBrowserService
} from "../data-datasource-browser/datasources-database/database-data-model-browser/data-model-browser.service";
import { DatabaseAsset } from "../model/datasources/database/database-asset";
import { WSObjectType } from "../../../composer/dialog/ws/new-worksheet-dialog.component";
import {
   SetComposedDashboardCommand
} from "../../../vsobjects/command/set-composed-dashboard-command";
import { CreateQueryEventCommand } from "../../../composer/gui/vs/event/create-query-event-command";
import { OpenComposerService } from "../../../common/services/open-composer.service";
import { MessageCommand } from "../../../common/viewsheet-client/message-command";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { DomService } from "../../../widget/dom-service/dom.service";
import {
   FeatureFlagsService,
   FeatureFlagValue
} from "../../../../../../shared/feature-flags/feature-flags.service";
import { DataSourcesTreeActionsService } from "./data-sources-tree-actions.service";
import { GettingStartedService } from "../../../widget/dialog/getting-started-dialog/service/getting-started.service";
import { AppInfoService } from "../../../../../../shared/util/app-info.service";

const DATA_FOLDERS_URI: string = "../api/data/folders/children/";
const DATA_DATASOURCES_URI: string = "../api/data/datasources/nodes";
const CHECK_DATASOURCE_DUPLICATE_URI = "../api/data/datasources/move/checkDuplicate";
const CHECK_FOLDER_DUPLICATE_URI: string = "../api/data/move/checkDuplicate";
const PHYSICAL_MODEL_CHECK_DUPLICATE_URI: string = "../api/data/logicalModel/checkDuplicate";
const LOGICAL_MODEL_CHECK_DUPLICATE_URI: string = "../api/data/logicalModel/checkDuplicate";
const VPM_CHECK_DUPLICATE_URI: string = "../api/data/vpm/checkDuplicate";
const FOLDER_URI: string = "../api/data/folders";
const DATA_URI: string = "../api/data/datasets";
const CREATE_QUERY_URI = "/events/composer/ws/query/create";

@Component({
   selector: "p-data-sources-tree-view",
   templateUrl: "data-sources-tree-view.component.html",
   styleUrls: ["data-sources-tree-view.component.scss"],
   providers: [ViewsheetClientService, AssetClientService]
})
export class DataSourcesTreeViewComponent extends CommandProcessor implements OnInit, OnDestroy {
   @ViewChild(TreeComponent) tree: TreeComponent;
   @ViewChild("dataNotifications") dataNotifications: DataNotificationsComponent;
   @ViewChild("treeContainer") treeContainer: ElementRef;
   rootNode: TreeNodeModel;
   private _oldRootNode: TreeNodeModel;
   private currentFolderPath: string = "";
   private subscriptions = new Subscription();
   selectedNodes: TreeNodeModel[];
   private initPath: string;
   private initScope: string;
   private searchView = false;
   private composedDashboard = false;
   scrollY = 0;
   PortalDataType = PortalDataType;
   loading: boolean = false;
   oldSelectedNode: TreeNodeModel;
   private enterprise: boolean;

   datasetHome: TreeNodeModel = {
      label: "_#(js:Data)",
      data: <WorksheetBrowserInfo> {
         name: "data_home",
         path: "/"
      },
      children: [],
      expanded: true,
      leaf: false,
      type: AssetType.FOLDER,
      cssClass: "action-color"
   };

   datasourceHome: TreeNodeModel = {
      label: "_#(js:Data Source)",
      data: <WorksheetBrowserInfo> {
         name: "datasource_home",
         path: "/"
      },
      children: [],
      expanded: false,
      leaf: false,
      type: AssetType.DATA_SOURCE,
      cssClass: "action-color"
   };
   ModelNameValidators: ValidatorFn[] = [
      Validators.required,
      FormValidators.notWhiteSpace,
      FormValidators.matchReservedModelName,
      FormValidators.invalidDataModelName
   ];

   validatorMessages = [
      {
         validatorName: "required",
         message: "_#(js:data.model.nameRequired)"
      },
      {
         validatorName: "notWhiteSpace",
         message: "_#(js:viewer.notWhiteSpace)"
      },
      {
         validatorName: "invalidDataModelName",
         message: "_#(js:data.model.nameInvalid)"
      },
      {
         validatorName: "matchReservedModelName",
         message: "_#(js:common.datasource.reservedWords)"
      }
   ];

   constructor(private dataFolderService: DataBrowserService,
               private datasourceService: DatasourceBrowserService,
               private dataModelBrowserService: DataModelBrowserService,
               private assetClientService: AssetClientService,
               private clientService: ViewsheetClientService,
               private debounceService: DebounceService,
               private dragService: DragService,
               protected modalService: NgbModal,
               private repositoryClient: RepositoryClientService,
               private dataModelNameChangeService: DataModelNameChangeService,
               private openComposerService: OpenComposerService,
               private dropdownService: FixedDropdownService,
               private httpClient: HttpClient,
               private route: ActivatedRoute,
               private router: Router,
               private zone: NgZone,
               private domService: DomService,
               private dataSourcesTreeActionsService: DataSourcesTreeActionsService,
               private gettingStartedService: GettingStartedService,
               private appInfoService: AppInfoService,
               private featureFlagService: FeatureFlagsService)
   {
      super(clientService, zone, true);
   }

   ngOnInit(): void {
      this.getDataNavigationTree();
      this.appInfoService.isEnterprise().subscribe(info => this.enterprise = info);

      this.repositoryClient.connect();
      this.subscriptions.add(this.repositoryClient.dataChanged.subscribe(() => {
         this.debounceService.debounce("refreshDataTree",
            () => this.zone.run(() => this.getDataNavigationTree()), 600, []);
      }));

      this.subscriptions.add(this.dataFolderService.mvChanged.subscribe(() => {
         this.debounceService.debounce("refreshDataTree",
            () => this.zone.run(() => this.getDataNavigationTree()), 600, []);
      }));

      this.subscriptions.add(this.dataFolderService.folderChanged.subscribe((folder) => {
         this.changeFolder(folder.path, null, folder.type);
      }));

      this.subscriptions.add(this.datasourceService.datasourceChanged.subscribe(() => {
         this.getDataNavigationTree();
      }));

      this.subscriptions.add(this.datasourceService.folderChanged.subscribe((folder) => {
         this.changeFolder(folder.path, null, folder.type);
      }));

      this.subscriptions.add(this.datasourceService.onCreateEvent.subscribe((event: CreateEventInfo) => {
         if(event.vpm) {
            this.dataModelBrowserService.addVPM(event.datasource.path);
         }
         else {
            // physical view
            this.dataModelBrowserService.addPhysicalView(event.datasource.path);
         }
      }));

      this.subscriptions.add(this.router.events.subscribe(event => {
         if(event instanceof NavigationEnd && event.url === "/portal/tab/data") {
            this.selectedNodes = this.rootNode.children.length > 0
               ? [this.rootNode.children[0]] : [];
            this.rootNode.children[0].expanded = true;
            this.selectNode(this.selectedNodes);
         }
      }));

      this.subscriptions.add(this.route.queryParamMap
         .subscribe((params: ParamMap) => {
            let searchAllNodes = false;

            if(this.router.url.startsWith("/portal/tab/data/datasources/datasource/listing")) {
               this.initPath = "/";
               this.initScope = "0";
            }
            else {
               this.initPath = params.get("path");
               this.initScope = params.get("scope");
               this.searchView = params.has("query");

               if(!this.initPath && !this.initScope && !!params.get("databaseName")) {
                  let folder = params.get("folderName");
                  this.initPath = params.get("databaseName") + "/" +
                     (!!folder && folder !== "/" ? folder : "Data Model");
                  this.initScope = "0";
                  searchAllNodes = true;
               }
               else if(!this.initPath && this.initScope == "0") {
                  this.initPath = "/"
               }
            }

            if(this.rootNode && this.initPath && this.initScope) {
               this.initSeletedNodes(this.initPath, this.initScope, searchAllNodes);
            }
         }));

      this.clientService.connect();
   }

   processSetComposedDashboardCommand(command: SetComposedDashboardCommand): void {
      this.composedDashboard = true;
   }

   private processMessageCommand(msg: MessageCommand): void {
      this.dataNotifications.notifications.info(msg.message);
   }

   private initSeletedNodes(path: string, scope: string, searchAllNodes: boolean = false) {
      let type = "SHARED_WORKSHEETS_FOLDER";

      if(scope == "4") {
         type = "PRIVATE_WORKSHEETS_FOLDER";
      }
      else if(scope == "0") {
         type = "DATA_SOURCE_ROOT_FOLDER";
      }
      else if(this.route.snapshot["_routerState"]) {
         const url: string = this.route.snapshot["_routerState"].url;
         const database = "/portal/tab/data/datasources/database";

         if(url.startsWith(database)) {
            type = "DATA_SOURCE_ROOT_FOLDER";
            path = decodeURIComponent(url.substring(database.length));
         }

         if(path && path.startsWith("/")) {
            path = path.substring(1);
         }
      }

      path = !path ? "/" : path;
      this.changeFolder(path, scope, type, searchAllNodes);
   }

   ngOnDestroy() {
      if(!!this.subscriptions && !this.subscriptions.closed) {
         this.subscriptions.unsubscribe();
         this.subscriptions = null;
      }
   }

   changeFolder(path: string, scope: string, folderType: string, searchAllNodes: boolean = false) {
      this.selectedNodes = [];

      if(folderType === PortalDataType.PRIVATE_WORKSHEETS_FOLDER) {
         let privateNode = this.getPrivateWorksheetNodeParent();
         this.rootNode.expanded = true;
         scope = !scope ? AssetEntryHelper.USER_SCOPE + "" : scope;
         this.changeDataSourcesTree(this.getNodePath(privateNode.type, path), scope,
            folderType, privateNode, searchAllNodes);
      }
      else {
         scope = !scope && folderType == PortalDataType.SHARED_WORKSHEETS_FOLDER ? AssetEntryHelper.GLOBAL_SCOPE + "" : scope;
         this.changeDataSourcesTree(path, scope, folderType, null, searchAllNodes);
      }
   }

   /**
    * Highlight the path node.
    */
   changeDataSourcesTree(path: string, scope: string, folderType: string, parent?: TreeNodeModel,
                         searchAllNodes: boolean = false): boolean
   {
      let isRoot = !parent;
      let found = false;
      parent = !isRoot ? parent : this.rootNode;
      let children = !!parent ? parent.children : [];

      for(let i = 0; i < children.length; i++) {
         if(isRoot && children[i].type != folderType) {
            continue;
         }

         if(children[i].data.path === path && children[i].data.scope == scope) {
            this.selectedNodes = [children[i]];
            children[i].expanded = true;
            found = true;
         }
         else if(children[i].type == PortalDataType.PRIVATE_WORKSHEETS_FOLDER
            && folderType != children[i].type)
         {
            continue;
         }
         else if(children[i].children.length > 0 &&
            ((path.startsWith(children[i].data.path) || searchAllNodes) ||
               children[i].type == PortalDataType.DATA_MODEL ||
               children[i].data.path == "/"))
         {
            found = this.changeDataSourcesTree(path, scope, folderType, children[i], searchAllNodes)
               || found;
         }
      }

      if(found) {
         parent.expanded = true;
      }

      return false;
   }

   getPrivateWorksheetNodeParent(): TreeNodeModel {
      let children = this.rootNode.children;

      for (let i = 0; i < children.length; i ++) {
         if(children[i].type == PortalDataType.SHARED_WORKSHEETS_FOLDER) {
            return children[i];
         }
      }

      return null;
   }

   getNodePath(type: string, path: string) {
      return type == PortalDataType.PRIVATE_WORKSHEETS_FOLDER && path === "User Worksheet"
         ? "/" : path.startsWith("User Worksheet/")
         ? Tool.replaceStr(path, "User Worksheet/", "") : path;
   }

   getCurrentRootNode(type: string): TreeNodeModel {
      let result: TreeNodeModel = this.rootNode;

      if(!!this.rootNode && !!type) {
         let child: TreeNodeModel;

         if((child = this.rootNode.children.find(n => n.type === type))) {
            result = child;
         }
      }

      return result;
   }

   /**
    * Get root node for dataset/datasources tree
    */
   getDataNavigationTree(type?: string): void {
      console.trace("=======getDataNavigationTree============")
      this._oldRootNode = Tool.clone(this.rootNode);
      this.loading = true;
      this.httpClient.get<TreeNodeModel>("../api/portal/data/tree")
         .subscribe(root => {
            this.rootNode = root;
            this.loading = false;
            let paneRoot = this.getCurrentRootNode(type);

            if(this.selectedNodes) {
               this.selectedNodes = this.updateSelectedNodes(this._oldRootNode, paneRoot);
               this.keepExpandedNodes(this._oldRootNode, this.rootNode);
            }
            else {
               this.initSeletedNodes(this.initPath, this.initScope);
               this.rootNode.children[0].expanded = true;
               this.keepExpandedNodes(this.rootNode, paneRoot);
            }

            if(this.selectedNodes && this.selectedNodes.length > 0 &&
               !this.isEditDataSource() && !this.searchView)
            {
               this.selectNode(this.selectedNodes);
            }
         });
   }

   /**
    * Whether datasource pane has been opened.
    * return true when edit datasource or database.
    */
   private isEditDataSource(): boolean {
      return this.router.url.indexOf("/portal/tab/data/datasources/") != -1
         && this.router.url.indexOf("/portal/tab/data/datasources/database/vpms/") == -1
         && this.router.url.indexOf("/portal/tab/data/datasources/databaseModels") == -1;
   }

   updateSelectedNodes(node: TreeNodeModel, root: TreeNodeModel): TreeNodeModel[] {
      const currentSelectedNodes = [];

      for(let child of node.children) {
         const contains = this.tree.isSelectedNode(child);
         let treeNode = GuiTool.findNode(root, (n) =>
            !!n.data && n.data.identifier === child.data.identifier && n.label === child.label);

         if(contains && treeNode) {
            currentSelectedNodes.push(treeNode);
         }

         currentSelectedNodes.push(...this.updateSelectedNodes(child, root));
      }

      return currentSelectedNodes;
   }

   keepExpandedNodes(node: TreeNodeModel, root: TreeNodeModel): void {
      if(!node || node.leaf || !node.children) {
         return;
      }

      for(let child of node.children) {
         if(child.expanded) {
            let treeNode = GuiTool.findNode(root, (n) =>
               !!n.data && n.data.path === child.data.path && n.label === child.label &&
               n.data.type === child.data.type && n.data.scope === child.data.scope);

            if(treeNode) {
               treeNode.expanded = true;
            }

            this.keepExpandedNodes(child, root);
         }
      }
   }

   /**
    * Called when user selects node on tree. Navigate router to the selected nodes path.
    * @param nodes   the selected nodes on tree
    */
   selectNode(nodes: TreeNodeModel[]): void {
      this.searchView = false;

      if(nodes && nodes.length > 0) {
         this.selectedNodes = nodes;

         if(nodes.length != 1) {
            return;
         }

         const node: TreeNodeModel = nodes[0];
         const nodeEntry = !!node ? <AssetEntry>node.data : null;

         if(!nodeEntry) {
            return;
         }

         if(this.gettingStartedService.isProcessing() && this.gettingStartedService.isEditWs()) {
            return;
         }

         if(node.type === PortalDataType.DATA_SOURCE_ROOT_FOLDER ||
            node.type === PortalDataType.DATA_SOURCE_FOLDER)
         {
            const path = nodeEntry.path;
            const scope = nodeEntry.scope + "";
            const extras = {
               queryParams: {
                  path: path,
                  scope: scope,
                  temp: new Date().getTime()
               }
            };

            this.router.navigate(["/portal/tab/data/datasources"], extras);
         }
         else if(node.type === PortalDataType.DATA_MODEL_FOLDER ||
            node.type === PortalDataType.DATA_MODEL)
         {
            let database = node.data.properties["databasePath"];
            let folder = node.type === PortalDataType.DATA_MODEL ? "" :
               node.data.properties["folder"];

            if(database) {
               let routePath = "/portal/tab/data/datasources/databaseModels";

               const extras = {
                  queryParams: {
                     databaseName: database,
                     folderName: !!folder ? folder : "",
                     temp: new Date().getTime()
                  }
               };

               this.router.navigate([routePath], extras);
            }
         }

         else if(nodeEntry.type === AssetType.FOLDER) {
            const path = nodeEntry.path;
            const scope = nodeEntry.scope + "";
            const extras = {
               queryParams: {
                  path: path,
                  scope: scope,
                  temp: new Date().getTime()
               }
            };
            this.router.navigate(["/portal/tab/data/folder"], extras);
         }
         else if(!!nodeEntry && nodeEntry.type === AssetType.WORKSHEET) {
            this.dataFolderService.openWorksheet(nodeEntry.identifier, this.clientService);
         }
         else if(node.type === PortalDataType.DATABASE) {
            this.router.navigate(["/portal/tab/data/datasources/database",
               Tool.byteEncode(nodeEntry.path)], {relativeTo: this.route});
         }
         else if(node.type === PortalDataType.DATA_SOURCE) {
            this.router.navigate(["/portal/tab/data/datasources/datasource",
               Tool.byteEncode(nodeEntry.path), ], {relativeTo: this.route});
         }
         else if(node.type === PortalDataType.XMLA_SOURCE) {
            this.router.navigate(["/portal/tab/data/datasources/datasource/xmla/edit",
               Tool.byteEncode(nodeEntry.path), ], {relativeTo: this.route});
         }
         else if(node.type === PortalDataType.PARTITION) {
            let splitName = this.splitModelName(node, true);

            if(!splitName.database || !splitName.name) {
               return;
            }

            this.router.navigate(["/portal/tab/data/datasources/database",
               splitName.database, "physicalModel",
               splitName.name], {relativeTo: this.route});
         }
         else if(node.type === PortalDataType.EXTENDED_PARTITION) {
            if(node.data && node.data.properties) {
               let database = node.data.properties["database"];
               let parent = node.data.properties["parent"];
               let name = node.data.properties["name"];

               if(!database || !parent || !name) {
                  return;
               }

               this.router.navigate(["/portal/tab/data/datasources/database",
                  Tool.byteEncode(database), "physicalModel", Tool.byteEncode(name),
                  { parent: Tool.byteEncode(parent) }],
                  {relativeTo: this.route});
            }
         }
         else if(node.type === PortalDataType.LOGIC_MODEL) {
            if(node.data && node.data.properties) {
               let physicalModel = node.data.properties["physicalModel"];
               let splitName = this.splitModelName(node, true);

               if(!physicalModel || !splitName.database || !splitName.name) {
                  return;
               }

               this.router.navigate(["datasources/database", splitName.database,
                  "physicalModel", Tool.byteEncode(physicalModel), "logicalModel", splitName.name],
                  {relativeTo: this.route});
            }
         }
         else if(node.type === PortalDataType.EXTENDED_LOGIC_MODEL) {
            if(node.data && node.data.properties) {
               let database = node.data.properties["database"];
               let physicalModel = node.data.properties["physicalModel"];
               let parent = node.data.properties["parent"];
               let name = node.data.properties["name"];

               if(!database || !physicalModel || !parent || !name) {
                  return;
               }

               this.router.navigate(["datasources/database", Tool.byteEncode(database),
                  "physicalModel", Tool.byteEncode(physicalModel), "logicalModel",
                  Tool.byteEncode(name), { parent: Tool.byteEncode(parent) }],
                  {relativeTo: this.route});
            }
         }
         else if(node.type === PortalDataType.VPM_FOLDER) {
            let path = nodeEntry.path;
            let idx = !path ? -1 : path.lastIndexOf("/");

            if(idx == -1 || idx >= path.length) {
               return;
            }

            const extras = {
               queryParams: {
                  temp: new Date().getTime()
               },
               relativeTo: this.route
            };

            this.router.navigate(["datasources/database/vpms",
               Tool.byteEncode(path.substring(0, idx))], extras);
         }
         else if(node.type === PortalDataType.VPM) {
            this.router.navigate(["datasources/database/vpm",
               Tool.byteEncode(nodeEntry.path)], {relativeTo: this.route});
         }
      }
   }

   /**
    * Called when user expands folder on tree. Open folder and attach contents as children of node.
    * @param node       the expanded tree node
    */
   expandNode(node: TreeNodeModel): Observable<TreeNodeModel[]> {
      if(node === this.rootNode) {
         // tree calls expand on rootNode on init, ignore
         return of([]);
      }

      // Only worksheets have folders, if node is not a folder than it is a data model node
      const request: Observable<TreeNodeModel[]> = node.type !== AssetType.FOLDER ?
         this.openDatasourcesFolder(node) : this.openFolder(node);

      return request.pipe(
         tap(data => {
            const selectedNode = this.tree.selectedNodes.length > 0 ? this.tree.selectedNodes[0] : null;

            node.children.forEach(oldChild => {
               if(oldChild.children.length > 0 || oldChild.expanded || oldChild === selectedNode) {
                  const newChild: TreeNodeModel = data.find(child =>
                     (<WorksheetBrowserInfo> child.data).name === (<WorksheetBrowserInfo> oldChild.data).name
                  );

                  if(!!newChild) {
                     newChild.expanded = oldChild.expanded;
                     newChild.children = oldChild.children;

                     if(oldChild === selectedNode) {
                        this.tree.exclusiveSelectNode(newChild);
                     }
                  }
               }
            });

            node.children = data;
         }));
   }

   onNodeDrag(event: any) {
      const textData = event.dataTransfer.getData("text");

      if(textData) {
         const jsonData = JSON.parse(textData);
         const entryLabelFn = this.getEntryLabel;
         const labels = jsonData.AssetEntry.map(entryLabelFn.bind(this));
         const elem = GuiTool.createDragImage(labels, jsonData.dragName, 1, true);
         (<HTMLElement> elem).style.display = "flex";
         (<HTMLElement> elem).style.flexDirection = "column";
         (<HTMLElement> elem).style.lineHeight = "0.5";
         (<HTMLElement> elem).style.alignItems = "left";
         GuiTool.setDragImage(event, elem, this.zone, this.domService);
      }
   }

   private getEntryLabel(asset: AssetEntry): string {
      let textLabel = AssetEntryHelper.getEntryName(asset);
      let entryIconFn = this.getAssetIcon.bind(this);

      return `
      <div>
      <span>
        <span class="${entryIconFn(asset.type)}">
        </span>
        <label>
        ${textLabel}
        </label>
      </span>
      </div>`;
   }

   onNodeDrop(event: any) {
      const targetEntry: AssetEntry = event.node.data;
      let dragData = this.dragService.getDragData();

      if(dragData["dragModelAssets"]) {
         this.moveDataModelAssetItems(event.node,
            <DatabaseAsset[]> JSON.parse(dragData["dragModelAssets"]));
         return;
      }
      else if(dragData["dragWorksheets"]) {
         this.moveDataFolderItems(targetEntry,
            <WorksheetBrowserInfo[]> JSON.parse(dragData["dragWorksheets"]));
         return;
      }
      else if(dragData["dragDataSources"]) {
         this.moveDatasourceInfos(targetEntry,
            <DataSourceInfo[]> JSON.parse(dragData["dragDataSources"]));
         return;
      }

      let moveEntries: AssetEntry[] = [];

      for(let key of Object.keys(dragData)) {
         let dragItems: any = JSON.parse(dragData[key]);

         if(!(dragItems instanceof Array)) {
            continue;
         }

         let dragEntries: AssetEntry[] = dragItems;

         for(let dragEntry of dragEntries) {
            let dragType = dragEntry.type != AssetType.DATA_SOURCE ?
               dragEntry.type : AssetType.DATA_SOURCE_FOLDER;
            const parent = this.getParentPath(dragEntry);

            if(targetEntry.type != dragType) {
               const message = dragType == AssetType.DATA_SOURCE_FOLDER ?
                  "_#(js:em.reports.drag.datasource.note)" : "_#(js:em.reports.drag.unworksheetfolder.note)";

               ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", message);
               return;
            }
            else if(targetEntry.scope != dragEntry.scope ||
               (targetEntry.path != parent && targetEntry.path.indexOf(dragEntry.path) == -1))
            {
               moveEntries.push(dragEntry);
            }
         }
      }

      if(moveEntries.length > 0) {
         if(targetEntry.type == AssetType.DATA_SOURCE_FOLDER) {
            this.moveDatasourceAssets(targetEntry, moveEntries);
         }
         else {
            this.moveDataAssets(targetEntry, moveEntries);
         }
      }
   }

   private moveDataModelAssetItems(targetNode: TreeNodeModel, moveItems: DatabaseAsset[]) {
      if(!targetNode || !moveItems || !targetNode.data.properties.databasePath) {
         return;
      }

      let targetEntry: AssetEntry = targetNode.data;

      if(targetNode.type == PortalDataType.DATA_MODEL ||
         targetNode.type == PortalDataType.DATA_MODEL_FOLDER)
      {
         let name;

         if(targetNode.type == PortalDataType.DATA_MODEL) {
            name = "/";
         }
         else {
            const parent = this.getParentPath(targetEntry);
            name = parent == "/" ? targetEntry.path :
               targetEntry.path.substr(parent.length + 1);
         }

         ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
            "_#(js:em.reports.drag.confirm)").then((buttonClicked) =>
         {
            if(buttonClicked === "ok") {
               this.dataModelBrowserService.moveModelsToTarget(moveItems, name,
                  () => this.dataModelBrowserService.emitChanged());
            }
         });
      }
      else {
         const message = "_#(js:em.reports.drag.datasource.note)";
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", message);
      }
   }



   private moveDataFolderItems(targetEntry: AssetEntry, dataFolders: WorksheetBrowserInfo[]) {
      if(!targetEntry || !dataFolders) {
         return;
      }

      if(targetEntry.type == AssetType.FOLDER) {
         this.checkDataFoldersDuplicate(dataFolders, targetEntry).subscribe(duplicate => {
            if(!duplicate) {
               const movedFolders = dataFolders.filter(item => item.type === AssetType.FOLDER &&
                  (item.scope != targetEntry.scope ||
                     targetEntry.path != this.getParentPath0(item.path) &&
                     !targetEntry.path.startsWith(item.path)));
               const movedAssets = dataFolders
                  .filter(item => item.type === AssetType.WORKSHEET &&
                     (item.scope != targetEntry.scope ||
                     targetEntry.path != this.getParentPath0(item.path) &&
                     !targetEntry.path.startsWith(item.path)));

               let promise: Promise<any> = Promise.resolve(null);

               if(movedFolders.length <= 0 && movedAssets.length <= 0) {
                  return;
               }

               ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
                  "_#(js:em.reports.drag.confirm)").then((buttonClicked) =>
               {
                  if(buttonClicked === "ok") {
                     if(movedFolders.length > 0) {
                        promise = this.moveDataSetsAndFolders(promise, movedFolders, targetEntry,
                           FOLDER_URI + "/moveFolders");
                     }

                     if(movedAssets.length > 0) {
                        promise = this.moveDataSetsAndFolders(promise, movedAssets, targetEntry,
                           DATA_URI + "/moveDatasets");
                     }

                     promise.then((res) => {
                        if(!!res?.message) {
                           this.showMessage(res?.message);
                        }

                        this.dataFolderService.changeFolder(targetEntry.path, targetEntry.scope);
                     });
                  }
               });
            }
            else {
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", "_#(js:common.duplicateName)");
               this.loading = false;
            }
         });
      }
      else {
         const message = "_#(js:em.reports.drag.unworksheetfolder.note)";
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", message);
      }
   }

   showMessage(message: string) {
      ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", message, {"ok": "_#(js:OK)"},
         {backdrop: false });
   }

   private moveDataSetsAndFolders(promise: Promise<any>, items: WorksheetBrowserInfo[],
                                  targetEntry: AssetEntry, url: string): Promise<any>
   {

      let scopeMap = new Map<number, WorksheetBrowserInfo[]>();
      items.forEach(item => {
         let scopeItems: WorksheetBrowserInfo[]  = scopeMap.get(item.scope);

         if(!scopeItems) {
            scopeItems = [];
            scopeMap.set(item.scope, scopeItems);
         }

         scopeItems.push(item);
      });

      scopeMap.forEach((scopeItems, scope) => {
         let httpParams = new HttpParams();

         if(Tool.isNumber(scope)) {
            httpParams = httpParams.set("assetScope", "" + scope);
         }

         if(Tool.isNumber(targetEntry.scope)) {
            httpParams = httpParams.set("targetScope", "" + targetEntry.scope);
         }

         const params = {
            params: httpParams
         };
         const newPath: string = targetEntry.path === "/" ? "" : targetEntry.path;
         const folderMoveCommands: MoveCommand[] = scopeItems.map(
            (f) => new MoveCommand(newPath, f.path, f.name, f.id, f.createdDate));
         promise = promise.then(() => {
            return this.httpClient.post(url, folderMoveCommands, params)
               .toPromise()
               .then((data: WorksheetBrowserInfo[]) => {
                  return Promise.resolve(data);
               });
         });
      });

      return promise;
   }

   private getParentPath(entry: AssetEntry): string {
      return this.getParentPath0(entry.path);
   }

   private getParentPath0(path: string): string {
      if(path === "/") {
         return null;
      }

      let index = path.lastIndexOf("/");
      return index >= 0 ? path.substring(0, index) : "/";
   }

   private checkDatasourceDuplicate0(targetEntry: AssetEntry, moveItems: DataSourceInfo[]): Observable<Object> {
      let checkMoveDuplicateRequest: CheckMoveDuplicateRequest = {
         items: moveItems
      };

      if(targetEntry.path !== "/") {
         checkMoveDuplicateRequest.path = targetEntry.path;
      }

      return this.httpClient.post(CHECK_DATASOURCE_DUPLICATE_URI, checkMoveDuplicateRequest);
   }

   private moveDatasourceAssets(targetEntry: AssetEntry, assets: AssetEntry[]) {
      if(assets?.length == 0) {
         return;
      }

      const moveItems: DataSourceInfo[] = this.datasourceService.createDataSourceInfos(assets);
      this.moveDatasourceInfos(targetEntry, moveItems);
   }

   private moveDatasourceInfos(targetEntry: AssetEntry, assets: DataSourceInfo[]) {
      if(assets?.length == 0) {
         return;
      }

      if(targetEntry.type == AssetType.DATA_SOURCE_FOLDER) {
         ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
            "_#(js:em.reports.drag.confirm)").then((buttonClicked) =>
         {
            if(buttonClicked === "ok") {
               this.checkDatasourceDuplicate0(targetEntry, assets).subscribe(
                  (res: CheckDuplicateResponse) => {
                     if(!res.duplicate) {
                        this.datasourceService.moveDataSourcesToFolder(assets, targetEntry.path,
                           () => {
                              this.loading = false;
                              this.datasourceService.refreshTree();
                           },
                           (error) => {
                              this.loading = false;
                              const message = error.error.message;

                              if(message == "_#(js:data.datasets.moveTargetPermissionError)") {
                                 ComponentTool.showMessageDialog(this.modalService, "Unauthorized", message);
                              }
                              else {
                                 this.dataNotifications.notifications.danger(error.error.message);
                              }
                           });
                     }
                     else {
                        ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", "_#(js:common.duplicateName)");
                        this.loading = false;
                     }
                  }
               );
            }
         });
      }
      else {
         const message = "_#(js:em.reports.drag.datasource.note)";
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", message);
      }
   }

   private moveDataAssets(targetEntry: AssetEntry, assets: AssetEntry[]) {
      const moveItems: WorksheetBrowserInfo[] = [];

      for(let asset of assets) {
         const parent = this.getParentPath(asset);
         const name = parent == "/" ? asset.path : asset.path.substr(parent.length + 1);

         moveItems.push(<WorksheetBrowserInfo> {
            canMaterialize: false,
            createdBy: asset.createdUsername,
            createdDate: 0,
            createdDateLabel: "",
            modifiedDate: 0,
            modifiedDateLabel: "",
            deletable: false,
            description: "",
            editable: false,
            hasSubFolder: 0,
            id: asset.identifier,
            materialized: false,
            name: name,
            parentPath: "",
            path: asset.path,
            scope: asset.scope,
            type: asset.type,
            workSheetType: 0
         });
      }

      this.moveDataFolderItems(targetEntry, moveItems);
   }

   private checkDataFoldersDuplicate(moveItems: WorksheetBrowserInfo[], targetEntry: AssetEntry): Observable<Object>
   {
      let httpParams = new HttpParams();

      if(Tool.isNumber(targetEntry.scope)) {
         httpParams = httpParams.set("targetScope", "" + targetEntry.scope);
      }

      return this.httpClient.post(CHECK_FOLDER_DUPLICATE_URI,
         new CheckItemsDuplicateCommand(moveItems, targetEntry.path), { params: httpParams });
   }

   /**
    * Set the current folder path and select current path in tree.
    * @param path       the current folder's path
    * @param selectNode if should select the node with the given path
    */
   private setCurrentFolderPath(path: string, selectNode: boolean = true): Observable<any> {
      this.currentFolderPath = path;

      if(!path || path === "/") {
         if(selectNode) {
            this.tree.selectedNodes = [this.datasetHome];
         }

         return this.expandNode(this.datasetHome);
      }
      else {
         return this.selectAndExpandToPath(path, selectNode);
      }
   }

   /**
    * Select and expand to given path on tree. Open folders while searching for node with given path.
    * @param path                the folder path
    * @param select              if should select the found node
    * @param node                the current node to search
    * @param breakOnNonExpanded  if function should break if a found node is not expanded
    */
   private selectAndExpandToPath(path: string, select: boolean,
                                 node: TreeNodeModel = this.datasetHome,
                                 breakOnNonExpanded: boolean = false): Observable<any> {
      let index: number = -1;

      // if root
      if(node.data.path === "/") {
         index = path.indexOf("/");
      }
      else {
         index = path.indexOf("/", node.data.path.length + 1);
      }

      const nextPath = index !== -1 ? path.substring(0, index) : path;

      for(let child of node.children) {
         if(child.data.path === nextPath) {
            if(breakOnNonExpanded && !child.expanded) {
               break;
            }

            if(nextPath === path) {
               if(select) {
                  this.tree.selectAndExpandToNode(child);
               }
               else {
                  this.tree.expandToNode(child);
               }

               if(child.expanded) {
                  return this.expandNode(child);
               }

               return of();
            }
            else {
               if(child.children.length > 0) {
                  return this.selectAndExpandToPath(path, select, child, breakOnNonExpanded);
               }
               else {
                  return this.openFolder(child).pipe(
                     tap(data => child.children = data),
                     switchMap(() => this.selectAndExpandToPath(path, select, child, breakOnNonExpanded))
                  );
               }
            }
         }
      }

      return of();
   }

   /**
    * Refresh an array of paths on the tree. Used when moving assets so the moved to folder is refreshed.
    * @param {string[]} paths the paths to refresh
    */
   private refreshPaths(paths: string[]): void {
      if(!!paths && paths.length > 0) {
         let nextPath: string = paths.shift();
         let obs: Observable<any>;

         if(!nextPath || nextPath === "/") {
            obs = this.expandNode(this.datasetHome);
         }
         else {
            obs = this.selectAndExpandToPath(nextPath, false, this.datasetHome, true);
         }

         obs.subscribe(() => this.refreshPaths(paths));
      }
   }

   /**
    * Get the contents of the folder at the given path.
    * @param node    the folder node to get children of
    * @returns {Observable<Object>} an observable with the contents of the given folder path.
    */
   private openFolder(node: TreeNodeModel = this.datasetHome): Observable<TreeNodeModel[]> {
      const folder: WorksheetBrowserInfo = <WorksheetBrowserInfo> node.data;
      const path: string = folder.path !== "/" ? folder.path : "";
      return this.httpClient.get<TreeNodeModel[]>(DATA_FOLDERS_URI
         + Tool.encodeURIComponentExceptSlash(path));
   }

   /**
    * Select and expand the current path to correct datasource node.
    * @param params     the current first child's params
    * @param selectNode if should select the node with the given path
    */
   private updateDatasourceNodes(params: ParamMap, selectNode: boolean = true) {
      if(!!params.get("databaseName")) {
         let dataModelPath: string[] = [params.get("databaseName")];

         this.selectAndExpandToDataModel(dataModelPath, selectNode);
      }
      else if(!!params.get("datasourceName")) {
         this.selectAndExpandToDataModel([params.get("datasourceName")], selectNode);
      }
      else {
         // no child params, on datasources home. Select and update home node.
         if(selectNode) {
            this.tree.selectedNodes = [this.datasourceHome];
         }

         this.expandNode(this.datasourceHome);
      }
   }

   /**
    * Select and expand to correct data model/folder on tree. Open folders while searching for
    * correct node.
    * @param dataModelPath    list of strings for the path of the wanted node, (e.g. a path to a
    *                         logical model would be ['databaseName','physicalModelName',
    *                         'logicalModelName'])
    * @param select           if should select the found node
    * @param node             the current node to search
    * @param expandedParent   if the parent node's children were refreshed
    */
   private selectAndExpandToDataModel(dataModelPath: string[], select: boolean,
                                      node: TreeNodeModel = this.datasourceHome,
                                      expandedParent: boolean = false): void {
      const nextPath: string = dataModelPath.shift();
      let found: boolean = expandedParent;

      for(let child of node.children) {
         if(child.data.name === nextPath) {
            found = true;

            if(dataModelPath.length === 0) {
               if(select) {
                  this.tree.selectAndExpandToNode(child);
               }
               else {
                  this.tree.expandToNode(child);
               }

               if(child.expanded) {
                  this.expandNode(child);
               }
            }
            else {
               if(child.children.length > 0) {
                  this.selectAndExpandToDataModel(dataModelPath, select, child);
               }
               else {
                  this.openDatasourcesFolder(child)
                     .subscribe(
                        data => {
                           child.children = data;
                           this.selectAndExpandToDataModel(dataModelPath, select, child, true);
                        });
               }
            }

            break;
         }
      }

      if(!found) {
         // next path did not exist, re-expand current node if it was not refreshed
         this.openDatasourcesFolder(node)
            .subscribe(
               data => {
                  node.children = data;
                  dataModelPath.unshift(nextPath);
                  this.selectAndExpandToDataModel(dataModelPath, select, node, true);
               }
            );
      }
   }

   /**
    * Send request to get children of a datasources node.
    * @param node    the node to get children of
    * @returns {any} an observable  with the children of the given datasources node
    */
   private openDatasourcesFolder(node: TreeNodeModel = this.datasourceHome): Observable<TreeNodeModel[]> {
      const nodeData = <WorksheetBrowserInfo> node.data;

      if(node === this.datasourceHome) {
         return this.httpClient.get<TreeNodeModel[]>(DATA_DATASOURCES_URI);
      }
      else {
         return of([]);
      }
   }

   /**
    * Dropdown was clicked on the tree. Show dropdown menu.
    * @param {[MouseEvent , TreeNodeModel]} event event containing the mouse coords and node clicked
    */
   contextMenu(event: [MouseEvent, TreeNodeModel, TreeNodeModel[]]): void {
      if(!this.hasMenuFunction(event[1])) {
         return;
      }

      let options: DropdownOptions = {
         position: {x: event[0].clientX, y: event[0].clientY},
         contextmenu: true
      };

      let contextmenu: ActionsContextmenuComponent =
         this.dropdownService.open(ActionsContextmenuComponent, options).componentInstance;
      contextmenu.sourceEvent = event[0];
      contextmenu.actions = this.createActions(event[1]);

      if(!!event[1] && event[1].type != PortalDataType.DATABASE) {
         this.selectNode([event[1]]);
      }
   }

   private createActions(node: TreeNodeModel): AssemblyActionGroup[] {
      let group = new AssemblyActionGroup();
      let groups: AssemblyActionGroup[] = [group];
      this.oldSelectedNode = this.selectedNodes != null && this.selectedNodes.length > 0 ?
         this.selectedNodes[0] : null;

      group.actions = [
         {
            id: () => "repository-tree new-folder",
            label: () => "_#(js:New Folder)",
            icon: () => "",
            enabled: () => this.canCreateChildren(node),
            visible: () => this.newFolderVisible(node),
            action: () => this.newFolder(node)
         },
         {
            id: () => "repository-tree new-database",
            label: () => "_#(js:data.datasources.newDataSource)",
            icon: () => "",
            visible: () => this.isDataSourceFolder(node),
            enabled: () => this.canNewDataSource(node),
            action: () => this.newDataAsset(node)
         },
         {
            id: () => "repository-tree new-worksheet",
            label: () => "_#(js:New Worksheet)",
            icon: () => "",
            visible: () => this.isDataWorksheetFolder(node),
            enabled: () => this.canNewWorksheet(node),
            action: () => this.newDataAsset(node)
         },
         {
            id: () => "rename foler",
            label: () => "_#(js:Rename)",
            icon: () => "",
            visible: () => this.renameVisible(node),
            enabled: () => this.canRename(node),
            action: () => this.renameFolder(node)
         },
         {
            id: () => "delete folder",
            label: () => "_#(js:Delete)",
            icon: () => "",
            visible: () => this.deleteVisible(node),
            enabled: () => this.canDelete(node),
            action: () => this.deleteFolder(node)
         },
         {
            id: () => "add logical model",
            label: () => "_#(js:New Logical Model)",
            icon: () => "",
            visible: () => node.type === PortalDataType.DATA_MODEL ||
               node.type === PortalDataType.DATA_MODEL_FOLDER ||
               node.type === PortalDataType.PARTITION,
            enabled: () => this.canCreateChildren(node) && this.canCreateLogicalModel(node),
            action: () => this.addDataModel(node, PortalDataType.LOGIC_MODEL)
         },
         {
            id: () => "add physical view",
            label: () => "_#(js:New Physical View)",
            icon: () => "",
            visible: () => node.type === PortalDataType.DATA_MODEL ||
               node.type === PortalDataType.DATA_MODEL_FOLDER ||
               node.type === PortalDataType.DATABASE,
            enabled: () => this.canCreateChildren(node),
            action: () => this.addDataModel(node, PortalDataType.PARTITION)
         },
         {
            id: () => "add query",
            label: () => "_#(js:New Query)",
            icon: () => "",
            visible: () => node.type === PortalDataType.DATABASE ||
               node.type === PortalDataType.DATA_SOURCE,
            enabled: () => this.canCreateChildren(node) && this.canCreateQuery(node),
            action: () => this.createQuery(node)
         },
         {
            id: () => "add database vpm",
            label: () => "_#(js:data.datasources.newVPM)",
            icon: () => "",
            visible: () => this.isVpmVisible(node),
            enabled: () => this.canCreateChildren(node),
            action: () => this.addDataModel(node, PortalDataType.VPM)
         },
         {
            id: () => "worksheet folder detail",
            label: () => "_#(js:Details)",
            icon: () => "",
            visible: () => this.detailVisible(node),
            enabled: () => true,
            action: () => this.dataSourcesTreeActionsService
               .showWSFolderDetails({path: node.data.path, scope: node.data.scope + ""})
         },
      ];

      return groups;
   }

   private isVpmVisible(node: TreeNodeModel): boolean {
      let identifier: string = node?.data?.identifier;

      if(!!identifier && identifier.endsWith("^SELF")) {
         return false;
      }

      if(!this.enterprise) {
         return false;
      }

      return node.type === PortalDataType.DATABASE || node.type === PortalDataType.VPM_FOLDER;
   }

   private actionCallback(node: TreeNodeModel, parent?: boolean): () => void {
      return () => {
         let selectedNode;
         let root;

         if(node.type === PortalDataType.SHARED_WORKSHEETS_FOLDER ||
            node.type === PortalDataType.PRIVATE_WORKSHEETS_FOLDER ||
            node.type === PortalDataType.FOLDER)
         {
            root = this.getCurrentRootNode(PortalDataType.SHARED_WORKSHEETS_FOLDER);
         }
         else if(node.type === PortalDataType.DATA_SOURCE_ROOT_FOLDER ||
            node.type === PortalDataType.DATA_SOURCE_FOLDER || node.type === PortalDataType.DATA_MODEL ||
            node.type === PortalDataType.DATA_MODEL_FOLDER || node.type === PortalDataType.DATA_SOURCE ||
            node.type === PortalDataType.DATABASE || node.type == PortalDataType.XMLA_SOURCE)
         {
            root = this.getCurrentRootNode(PortalDataType.DATA_SOURCE_ROOT_FOLDER);
         }

         if(!parent) {
            let currentNode = GuiTool.findNode(root, (n) =>
               !!n.data && n.data.path === node.data.path && n.label === node.label &&
               n.data.type === node.data.type && n.data.scope === node.data.scope);

            selectedNode = !!currentNode ? currentNode : root;
         }
         else {
            let parentPath;

            if(node.type === PortalDataType.DATA_MODEL_FOLDER) {
               parentPath = this.getParentPath0(node.data.path) + "/Data Model";
            }
            else {
               parentPath = this.getParentPath0(node.data.path);
            }

            let parentNode = GuiTool.findNode(root, (n) =>
               !!n.data && n.data.path === parentPath && n.data.scope === node.data.scope);
            selectedNode = !!parentNode ? parentNode : root;
         }

         this.selectNode([selectedNode]);
         this.datasourceService.refreshTree();
      };
   }

   getIconFunction(): (node: TreeNodeModel) => string {
      return (node: TreeNodeModel) => this.getAssetIcon(node.type, node.materialized);
   }

   getAssetIcon(type: string, materialized?: boolean): string {
      if(type === PortalDataType.DATABASE) {
         return "database-icon";
      }
      else if(type === PortalDataType.DATA_MODEL) {
         return "db-model-icon";
      }
      else if(type === PortalDataType.XMLA_SOURCE) {
         return "cube-icon";
      }
      else if(type === PortalDataType.DATA_SOURCE) {
         return "tabular-data-icon";
      }
      else if(type === PortalDataType.DATA_SOURCE_ROOT_FOLDER) {
         return "data-source-folder-icon";
      }
      else if(type === PortalDataType.FOLDER
         || type === PortalDataType.DATA_SOURCE_FOLDER
         || type == PortalDataType.SHARED_WORKSHEETS_FOLDER
         || type == PortalDataType.PRIVATE_WORKSHEETS_FOLDER
         || type == PortalDataType.VPM_FOLDER
         || type == PortalDataType.DATA_MODEL_FOLDER)
      {
         return "folder-icon";
      }
      else if(type == PortalDataType.PARTITION ||
              type == PortalDataType.EXTENDED_PARTITION)
      {
         return "partition-icon";
      }
      else if(type == PortalDataType.LOGIC_MODEL ||
              type == PortalDataType.EXTENDED_LOGIC_MODEL)
      {
         return "logical-model-icon";
      }
      else if(type == PortalDataType.VPM) {
         return "vpm-icon";
      }
      else if(materialized) {
         return "materialized-worksheet-icon";
      }
      else {
         return "worksheet-icon";
      }
   }

   onScroll(event: any) {
      this.scrollY = this.treeContainer.nativeElement.scrollTop;
   }

   hasMenuFunction(node: TreeNodeModel): boolean {
      if(node.type == PortalDataType.PARTITION ||
         node.type == PortalDataType.EXTENDED_PARTITION ||
         node.type == PortalDataType.LOGIC_MODEL ||
         node.type == PortalDataType.EXTENDED_LOGIC_MODEL ||
         node.type == PortalDataType.VPM)
      {
         return node.data.properties[DatasourceTreeAction.RENAME] === "true" ||
            node.data.properties[DatasourceTreeAction.DELETE] === "true" ||
            node.data.properties[DatasourceTreeAction.CREATE_CHILDREN] === "true";
      }

      return true;
   }

   private getDuplicateCheckUri(type: string): string {
      if(type == PortalDataType.PARTITION) {
         return PHYSICAL_MODEL_CHECK_DUPLICATE_URI;
      }
      else if(type == PortalDataType.LOGIC_MODEL) {
         return LOGICAL_MODEL_CHECK_DUPLICATE_URI;
      }
      else if(type == PortalDataType.VPM) {
         return VPM_CHECK_DUPLICATE_URI;
      }

      return null;
   }


   canCreateChildren(node: TreeNodeModel): boolean {
      if(node && node.data && node.data.properties) {
         return node.data.properties[DatasourceTreeAction.CREATE_CHILDREN] === "true";
      }

      return false;
   }

   private canCreateLogicalModel(node: TreeNodeModel): boolean {
      if(node.type === PortalDataType.DATA_MODEL || node.type === PortalDataType.DATA_MODEL_FOLDER) {
         let partitionCountStr = node?.data?.properties?.partitionCount;

         if(partitionCountStr) {
            try {
               return parseInt(partitionCountStr, 10) > 0;
            }
            catch(ignore) {
            }
         }
      }

      return false;
   }

   canRename(node: TreeNodeModel): boolean {
      if(node && node.data && node.data.properties) {
         return node.data.properties[DatasourceTreeAction.RENAME] === "true";
      }

      return false;
   }

   canDelete(node: TreeNodeModel): boolean {
      if(node && node.data && node.data.properties) {
         return node.data.properties[DatasourceTreeAction.DELETE] === "true";
      }

      return false;
   }

   canNewWorksheet(node: TreeNodeModel): boolean {
      if(node && node.data && node.data.properties) {
         return node.data.properties[DatasourceTreeAction.NEW_WORKSHEET] === "true";
      }

      return false;
   }

   canNewDataSource(node: TreeNodeModel): boolean {
      if(node && node.data && node.data.properties) {
         return node.data.properties[DatasourceTreeAction.NEW_DATASOURCE] === "true";
      }

      return false;
   }

   private deleteDataModelFolder(node: TreeNodeModel) {
      let split = this.splitModelName(node);
      this.dataModelBrowserService.deleteDataModelFolder(split.database, split.folder,
         this.actionCallback(node, true));
   }

   private canCreateQuery(node: TreeNodeModel): boolean {
      let type = !!node ? node.type : null;

      if(type !== this.PortalDataType.DATABASE && type != this.PortalDataType.DATA_SOURCE) {
         return false;
      }

      if(node.data.properties["queryCreatable"] == "false") {
         return false;
      }

      return true;
   }

   /**
    * Show dialog to input a name and then add a physical or logical model.
    */
   createQuery(node: TreeNodeModel): void {
      if(!this.canCreateQuery(node)) {
         return;
      }

      let baseDataSource = node.data.path;
      let baseDataSourceType: number = this.getDataSourceObjectType(node.type);

      if(this.composedDashboard) {
         this.openComposer(baseDataSource, baseDataSourceType, true);
      }
      else {
         this.openComposerService.composerOpen.subscribe(open => {
            if(open) {
               let event = new CreateQueryEventCommand(baseDataSource, baseDataSourceType);
               this.clientService.sendEvent(CREATE_QUERY_URI, event);
            }
            else {
               this.openComposer(baseDataSource, baseDataSourceType);
            }
         });
      }
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

   getDataSourceObjectType(type: string): number {
      switch(type) {
         case PortalDataType.DATA_SOURCE:
            return WSObjectType.TABULAR;
         case PortalDataType.DATABASE:
            return WSObjectType.DATABASE_QUERY;
         default:
            return -1;
      }
   }

   /**
    * Show dialog to input a name and then add a physical or logical model.
    */
   addDataModel(node: TreeNodeModel, createType: PortalDataType): void {
      if(!!node) {
         const isDatabase: boolean = node.type === this.PortalDataType.DATABASE;
         const isDataModel: boolean = node.type === this.PortalDataType.DATA_MODEL;
         const isDataModelFolder: boolean = node.type === this.PortalDataType.DATA_MODEL_FOLDER;
         const isPhysical: boolean = node.type === this.PortalDataType.PARTITION;
         const isVPMFOLDER: boolean = node.type === this.PortalDataType.VPM_FOLDER;
         let databasePath: string;
         let folder: string;
         let physicalView: string;

         if(isDatabase) {
            databasePath = node.data.path;
         }
         else if(isDataModel || isDataModelFolder) {
            databasePath = node.data.properties["databasePath"];
            folder = node.data.properties["folder"];
         }
         else if(isVPMFOLDER) {
            databasePath = node.data.properties["databasePath"];
         }
         else {
            let split = this.splitModelName(node);
            databasePath = split.database;
            folder = node.data.properties["folder"];

            if(node.type == PortalDataType.PARTITION) {
               physicalView = split.name;
            }
         }

         if((isDatabase || isVPMFOLDER) && createType == PortalDataType.VPM) {
            this.dataModelBrowserService.addVPM(databasePath);
         }
         else if(isDataModel || isDataModelFolder || isDatabase) {
            if(createType == PortalDataType.PARTITION) {
               this.dataModelBrowserService.addPhysicalView(databasePath, folder);
            }
            else if(createType == PortalDataType.LOGIC_MODEL) {
               this.dataModelBrowserService.addLogicalModel(databasePath, null, folder);
            }
         }
         else if(isPhysical) {
            this.dataModelBrowserService.addLogicalModel(databasePath, physicalView, folder);
         }
      }
   }

   private splitModelName(node: TreeNodeModel, encode = false): { database: string, folder: string, name: string } {
      let result = {
         database: "",
         folder: null,
         name: ""
      };

      if(!node || !node.data) {
         return result;
      }

      if(node.type == PortalDataType.DATABASE) {
         result.database = this.encodeString(node.data.path, encode);

         return result;
      }

      let path = node.data.path;
      let idx = !path ? -1 : path.lastIndexOf("/");

      if(idx == -1 || idx >= path.length) {
         return null;
      }

      result.database = this.encodeString(path.substring(0, idx), encode);
      result.name = this.encodeString(path.substring(idx + 1), encode);
      result.folder = node.data.properties["folder"];
      return result;
   }

   private encodeString(str: string, encode: boolean): string {
      return encode ? Tool.byteEncode(str) : str;
   }

   getAssemblyName(): string {
      return null;
   }

   newFolderVisible(node: TreeNodeModel): boolean {
      return node.type === PortalDataType.DATA_MODEL ||
         this.isDataSourceFolder(node) || this.isDataWorksheetFolder(node);
   }

   newFolder(node: TreeNodeModel) {
      if(node.type === PortalDataType.DATA_MODEL) {
         this.dataModelBrowserService.addDataModelFolder(node.data.properties["databasePath"],
            this.actionCallback(node));
      }
      else if(this.isDataSourceFolder(node)) {
         this.dataSourcesTreeActionsService.addDataSourceFolder(node.data.path,
            this.actionCallback(node));
      }
      else if(this.isDataWorksheetFolder(node)) {
         this.dataSourcesTreeActionsService.addDataWorksheetFolder(node.data.path,
            node.data.scope + "", this.actionCallback(node));
      }
   }

   newDataAsset(node: TreeNodeModel) {
      if(this.isDataSourceFolder(node)) {
         this.dataSourcesTreeActionsService.addDataSource(node.data.path, node.data.scope);
      }
      else if(this.isDataWorksheetFolder(node)) {
         this.dataSourcesTreeActionsService.addDataWorksheet(node.data.identifier);
      }
   }

   deleteFolder(node: TreeNodeModel): void {
      if(node.type === PortalDataType.DATA_MODEL_FOLDER) {
         this.deleteDataModelFolder(node);
      }
      else if(node.type === PortalDataType.DATA_SOURCE_FOLDER) {
         this.dataSourcesTreeActionsService.deleteDataSourceFolder(node,
            this.actionCallback(node, true));
      }
      else if(node.type === PortalDataType.FOLDER) {
         this.dataSourcesTreeActionsService.deleteWorksheetFolder(node,
            this.actionCallback(node, true));
      }
      else if(node.type === PortalDataType.DATA_SOURCE || node.type === PortalDataType.DATABASE ||
         node.type === PortalDataType.XMLA_SOURCE)
      {
         this.dataSourcesTreeActionsService.deleteDataSource(node,
             this.actionCallback(node, true));
      }
   }

   renameFolder(node: TreeNodeModel): void {
      if(node.type === PortalDataType.DATA_MODEL_FOLDER) {
         this.dataModelBrowserService.renameDataModelFolder(node.data.path, node.label,
            this.actionCallback(node, true), true, node);
      }
      else if(node.type === PortalDataType.DATA_SOURCE_FOLDER) {
         this.dataSourcesTreeActionsService.renameDataSourceFolder(node,
            this.actionCallback(node, true));
      }
      else if(node.type === PortalDataType.FOLDER) {
         this.dataSourcesTreeActionsService.renameWorksheetFolder(node,
            this.actionCallback(node, true));
      }
   }

   deleteVisible(node: TreeNodeModel): boolean {
      return node.type === PortalDataType.DATA_MODEL_FOLDER ||
         node.type === PortalDataType.DATA_SOURCE_FOLDER || node.type === PortalDataType.FOLDER ||
         node.type === PortalDataType.DATA_SOURCE || node.type === PortalDataType.DATABASE ||
         node.type == PortalDataType.XMLA_SOURCE;
   }

   detailVisible(node: TreeNodeModel): boolean {
      return !(node.type === PortalDataType.SHARED_WORKSHEETS_FOLDER) &&
         this.isDataWorksheetFolder(node);
   }

   renameVisible(node: TreeNodeModel): boolean {
      return node.type === PortalDataType.DATA_MODEL_FOLDER ||
         node.type === PortalDataType.DATA_SOURCE_FOLDER || node.type === PortalDataType.FOLDER;
   }

   isDataSourceFolder(node: TreeNodeModel): boolean {
      return node.type === PortalDataType.DATA_SOURCE_ROOT_FOLDER ||
         node.type === PortalDataType.DATA_SOURCE_FOLDER;
   }

   isDataWorksheetFolder(node: TreeNodeModel): boolean {
      return node.type === PortalDataType.SHARED_WORKSHEETS_FOLDER ||
         node.type === PortalDataType.PRIVATE_WORKSHEETS_FOLDER ||
         node.type === PortalDataType.FOLDER;
   }
}
