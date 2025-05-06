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
import { HttpClient } from "@angular/common/http";
import {
   ChangeDetectorRef,
   Component,
   EventEmitter,
   HostBinding,
   HostListener,
   Input,
   NgZone,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../shared/data/asset-type";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../shared/util/tool";
import { AssemblyAction } from "../../../common/action/assembly-action";
import { AssemblyActionGroup } from "../../../common/action/assembly-action-group";
import { AssetConstants } from "../../../common/data/asset-constants";
import { AssetEntryHelper } from "../../../common/data/asset-entry-helper";
import { DragEvent } from "../../../common/data/drag-event";
import { ComponentTool } from "../../../common/util/component-tool";
import { GuiTool } from "../../../common/util/gui-tool";
import { MessageCommand } from "../../../common/viewsheet-client/message-command";
import {
   ComposerContextProviderFactory,
   ContextProvider
} from "../../../vsobjects/context-provider.service";
import { AssetTreeComponent } from "../../../widget/asset-tree/asset-tree.component";
import { InputNameDialog } from "../../../widget/dialog/input-name-dialog/input-name-dialog.component";
import { DomService } from "../../../widget/dom-service/dom.service";
import { ActionsContextmenuComponent } from "../../../widget/fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../../../widget/fixed-dropdown/dropdown-options";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DragService } from "../../../widget/services/drag.service";
import { ModelService } from "../../../widget/services/model.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { OpenSheetEvent } from "../../data/open-sheet-event";
import { Sheet } from "../../data/sheet";
import { AddFolderEvent } from "./add-folder-event";
import { ChangeAssetEvent } from "./change-asset-event";
import { RemoveAssetEvent } from "./remove-asset-event";
import { RenameAssetEvent } from "./rename-asset-event";
import { TabularDataSourceTypeModel } from "../../../../../../shared/util/model/tabular-data-source-type-model";
import { CurrentUser } from "../../../portal/current-user";
import { ComposerRecentService } from "../composer-recent.service";
import { TreeView } from "../../../widget/tree/tree.component";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { OpenLibraryAssetEvent } from "../../data/open-libraryAsset-event";
import { ComposerTabModel } from "../composer-tab-model";

const ADD_FOLDER_URI = "../api/composer/asset-tree/add-folder";
const REMOVE_ASSET_URI = "../api/composer/asset-tree/remove-asset";
const REMOVE_ASSET_CHECK_OPEN_SHEETS_URI = "../api/composer/asset-tree/remove-asset/check-open-sheets";
const REMOVE_DASHBOARD_ASSET_URI_AGILE = "../api/dashboard/recyclebin/remove-asset";
const REMOVE_DATASET_ASSET_URI_AGILE = "../api/dataset/recyclebin/remove-asset";
const RENAME_ASSET_URI = "../api/composer/asset-tree/rename-asset";
const RENAME_ASSET_CHECK_OPEN_SHEETS_URI = "../api/composer/asset-tree/rename-asset/check-open-sheets";
const CHANGE_ASSET_URI = "../api/composer/asset-tree/change-asset";
const CURRENT_USER_URI: string = "../api/portal/get-current-user";

const ASSET_ENTRY_BANNED_CHARS_MESSAGE = "_#(js:composer.sheet.checkSpeChar)";
const START_WITH_CHART_DIGIT_MESSAGE = "_#(js:asset.tree.checkStart)";

@Component({
   selector: "asset-tree-pane",
   templateUrl: "asset-tree-pane.component.html",
   providers: [{
      provide: ContextProvider,
      useFactory: ComposerContextProviderFactory
   }]
})
export class AssetTreePane implements OnChanges, OnInit {
   @HostBinding("hidden")
   @Input() inactive: boolean;
   @Input() deployed: boolean;
   @Input() focusedSheet: Sheet;
   @Input() openedSheets: Sheet[];
   @Input() opendTabs: ComposerTabModel[];
   @Input() viewsheetPermission = true;
   @Output() onOpenLibraryAsset = new EventEmitter<OpenLibraryAssetEvent>();
   @Output() onOpenSheet = new EventEmitter<OpenSheetEvent>();
   @Output() onNewViewsheet: EventEmitter<AssetEntry> = new EventEmitter<AssetEntry>();
   @Output() onNewQuery: EventEmitter<AssetEntry> = new EventEmitter<AssetEntry>();
   @Output() onSheetClose: EventEmitter<Sheet> = new EventEmitter<Sheet>();
   @Output() onTabClose: EventEmitter<ComposerTabModel> = new EventEmitter<ComposerTabModel>();
   @Output() onNewScript: EventEmitter<void> = new EventEmitter<void>();
   @Output() onNewTableStyle: EventEmitter<void> = new EventEmitter<void>();
   @ViewChild(AssetTreeComponent) assetTree: AssetTreeComponent;
   selectedNodes: TreeNodeModel[] = [];
   tabularDataSourceTypes: TabularDataSourceTypeModel[];

   constructor(private changeDetector: ChangeDetectorRef,
               private zone: NgZone,
               private dropdownService: FixedDropdownService,
               private modalService: NgbModal,
               private modelService: ModelService,
               private dragService: DragService,
               private domService: DomService,
               private http: HttpClient,
               private contextProvider: ContextProvider,
               private composerRecentService: ComposerRecentService)
   {
   }

   ngOnChanges(changes: SimpleChanges) {
      if(this.inactive) {
         this.changeDetector.detach();
      }
      else {
         this.changeDetector.reattach();
      }
   }

   ngOnInit(): void {
      this.modelService.getModel("../api/composer/tabularDataSourceTypes")
         .subscribe((data: TabularDataSourceTypeModel[]) => {
            this.tabularDataSourceTypes = data;
         });
   }

   @HostListener("keyup.delete")
   deleteEntries() {
      if(!this.inactive) {
         if(this.selectedNodes.length > 0) {
            const entries: AssetEntry[] = this.selectedNodes.map((treeNode) => treeNode.data);

            for(let i = entries.length - 1; i >= 0; i--) {
               if(entries[i].scope === AssetConstants.QUERY_SCOPE) {
                  entries.splice(i, 1);
               }
            }

            if(entries.length === 0) {
               return;
            }

            this.deleteAssets(entries);
         }
      }
   }

   // Open a worksheet or viewsheet in the composer from tree node.
   openNodeSheet(node: TreeNodeModel): void {
      if(!this.deployed) {
         this.openSheetFromAsset([node.data]);
      }
   }

   // Open the entries in the composer if they are sheets
   openSheetFromAsset(entries: AssetEntry[], meta: boolean = false) {
      entries.forEach(entry => {
         if(entry.type === AssetType.WORKSHEET) {
            this.addRecentlyViewed(entry);
            this.onOpenSheet.emit({
               type: "worksheet",
               assetId: entry.identifier,
            });
         }
         else if(entry.type === AssetType.VIEWSHEET) {
            this.addRecentlyViewed(entry);
            this.onOpenSheet.emit({type: "viewsheet", assetId: entry.identifier, meta: meta});
         }
         else if(entry.type === AssetType.SCRIPT) {
            this.addRecentlyViewed(entry);
            this.onOpenLibraryAsset.emit({type: "script", assetId: entry.identifier});
         }
         else if(entry.type === AssetType.TABLE_STYLE) {
            this.addRecentlyViewed(entry);
            this.onOpenLibraryAsset.emit({type: "tableStyle", assetId: entry.identifier, styleId: entry.properties["styleID"]});
         }
      });

      this.assetTree.selectNodes([]);
   }

   addRecentlyViewed(entry: AssetEntry): void {
      this.composerRecentService.addRecentlyViewed(entry);
   }

   getRecentRootFun(): () => Observable<TreeNodeModel[]> {
      return () => {
         this.composerRecentService.removeNonExistItems();
         return this.composerRecentService.recentlyViewedChange().pipe(map((data: AssetEntry[]) => {
            let recents: TreeNodeModel[] = [];

            if(data) {
               data.forEach(entry => {
                  recents.push({
                     label: entry.alias ? entry.alias : this.getEntryName(entry),
                     data: entry,
                     dragName: entry.type.toString().toLowerCase(),
                     leaf: true,
                     treeView: TreeView.RECENT_VIEW
                  });
               });
            }

            return recents;
         }));
      };
   }

   private getEntryName(entry: AssetEntry): string {
      if(!entry || !entry.path) {
         return "";
      }

      let path = entry.path;
      let paths = path.split("/");

      return paths.length > 0 ? paths[paths.length - 1] : "";
   }

   hasMenuFunction(): any {
      return (node) => this.hasMenu(node);
   }

   hasMenu(node: TreeNodeModel): boolean {
      const actions = this.createActions([null, node, [node]]);
      return actions.some(group => group.visible);
   }

   openAssetTreeContextmenu(event: [MouseEvent, TreeNodeModel, TreeNodeModel[]]) {
      let options: DropdownOptions = {
         position: {x: event[0].clientX, y: event[0].clientY},
         contextmenu: true,
      };

      let contextmenu: ActionsContextmenuComponent =
         this.dropdownService.open(ActionsContextmenuComponent, options).componentInstance;
      contextmenu.sourceEvent = event[0];
      contextmenu.actions = this.createActions(event);
   }

   private createActions(event: [MouseEvent, TreeNodeModel, TreeNodeModel[]]): AssemblyActionGroup[] {
      let group = new AssemblyActionGroup([]);
      let groups = [group];
      let node = event[1];
      let entry: AssetEntry = node.data;
      let selectedNodes = event[2];
      let entries: AssetEntry[] = selectedNodes.map((treeNode) => treeNode.data);

      if(entry == null || AssetEntryHelper.isQuery(entry) ||
         (entry.scope === AssetConstants.REPORT_SCOPE && AssetEntryHelper.isRoot(entry)))
      {
         return groups;
      }

      if(AssetEntryHelper.isScript(entry)) {
         group.actions.push(this.createOpenScriptAction(entries));

         if(node.treeView != TreeView.RECENT_VIEW) {
            group.actions.push(this.createRenameScriptAction(node));
         }

         group.actions.push(this.createDeleteScriptAction(entries, node.treeView));
      }
      else if(AssetEntryHelper.isTableStyle(entry)) {
         group.actions.push(this.createOpenTableStyleAction(entries));

         if(node.treeView != TreeView.RECENT_VIEW) {
            group.actions.push(this.createRenameTableStyleAction(node));
         }

         group.actions.push(this.createDeleteTableStyleAction(entries, node.treeView));
      }
      else if(AssetEntryHelper.isRoot(entry) && entry.scope !== AssetConstants.QUERY_SCOPE &&
         !AssetEntryHelper.isScriptFolder(entry) && !AssetEntryHelper.isLibraryFolder(entry))
      {
         group.actions.push(this.createNewFolderAction(entry));
      }
      else if(AssetEntryHelper.isViewsheet(entry) || AssetEntryHelper.isVSSnapshot(entry)) {
         group.actions.push(this.createOpenViewsheetAction(entries));

         if(node.treeView != TreeView.RECENT_VIEW) {
            group.actions.push(this.createRenameViewsheetAction(node));
         }

         group.actions.push(this.createDeleteViewsheetAction(entries, node.treeView));
         group.actions.push(this.createOpenMetaViewsheetAction(entries));
      }
      else if(AssetEntryHelper.isDataSource(entry)) {
         const dstype: string = entry.properties["datasource.type"];
         const allowCreateAction = this.tabularDataSourceTypes.findIndex(type =>
            Tool.equalsIgnoreCase(type.name, dstype)) >= 0;

         if((dstype == "jdbc" || allowCreateAction) && entry.properties["sqlEnabled"] == "true") {
            group.actions.push(this.createNewQueryAction(entry));
         }
      }
      else if(AssetEntryHelper.isEditable(entry)) {
         if(AssetEntryHelper.isWorksheet(entry)) {
            group.actions.push(this.createOpenWorksheetAction(entries));

            if(node.treeView != TreeView.RECENT_VIEW) {
               group.actions.push(this.createRenameWorksheetAction(node));
            }

            group.actions.push(this.createDeleteWorksheetAction(entries, node.treeView));

            if(entry.scope !== AssetConstants.REPORT_SCOPE) {
               group.actions.push(this.createNewViewsheetAction(entry));
            }
         }
         else if(entry.type === AssetType.REPORT_WORKSHEET_FOLDER) {
            group.actions.push(this.createNewFolderAction(entry));
         }
         else if(entry.type === AssetType.TABLE_STYLE_FOLDER) {
            group.actions.push(this.createNewFolderAction(entry));

            if(entry.properties["folder"]) {
               group.actions.push(this.createRenameFolderAction(node));
               group.actions.push(this.createDeleteFolderAction(entries));
            }
            else {
               group.actions.push(this.createNewTableStyleAction(entry));
            }
         }
         else if(entry.type === AssetType.SCRIPT_FOLDER) {
            group.actions.push(this.createNewScriptAction(entry));
         }
         else if(AssetEntryHelper.isFolder(entry)) {
            group.actions.push(this.createNewFolderAction(entry));
            group.actions.push(this.createRenameFolderAction(node));
            group.actions.push(this.createDeleteFolderAction(entries));
         }
      }

      return groups;
   }

   private createNewFolderAction(parent: AssetEntry): AssemblyAction {
      return {
         id: () => "asset-tree new-folder",
         label: () => "_#(js:New Folder)",
         icon: () => "fa fa-folder",
         enabled: () => !this.containsAuditNodes([parent]),
         visible: () => true,
         action: () => this.createNewFolder(parent)
      };
   }

   private createNewQueryAction(entry: AssetEntry): AssemblyAction {
      return {
         id: () => "asset-tree new-query",
         label: () => "_#(js:New Query)",
         icon: () => "",
         enabled: () => true,
         visible: () => true,
         action: () => this.onNewQuery.emit(entry)
      };
   }

   private createOpenViewsheetAction(entries: AssetEntry[]): AssemblyAction {
      return {
         id: () => "asset-tree open-viewsheet",
         label: () => "_#(js:Open Sheet)",
         icon: () => "",
         enabled: () => !this.deployed,
         visible: () => true,
         action: () => this.openSheetFromAsset(entries)
      };
   }

   private createOpenMetaViewsheetAction(entries: AssetEntry[]): AssemblyAction {
      return {
         id: () => "asset-tree open-meta-viewsheet",
         label: () => "_#(js:Open in Metadata mode)",
         icon: () => "",
         enabled: () => !this.deployed,
         visible: () => true,
         action: () => this.openSheetFromAsset(entries, true)
      };
   }

   private createRenameViewsheetAction(node: TreeNodeModel): AssemblyAction {
      return {
         id: () => "asset-tree rename-viewsheet",
         label: () => "_#(js:Rename)",
         icon: () => "",
         enabled: () => true,
         visible: () => true,
         action: () => this.showRenameAssetDialog(node)
      };
   }

   private createDeleteViewsheetAction(entries: AssetEntry[], treeView: number): AssemblyAction {
      return {
         id: () => "asset-tree delete-viewsheet",
         label: () => "_#(js:Remove)",
         icon: () => "",
         enabled: () => true,
         visible: () => true,
         action: () => treeView == TreeView.RECENT_VIEW ? this.deleteRecentAssets(entries) : this.deleteAssets(entries)
      };
   }

   private createOpenWorksheetAction(entries: AssetEntry[]): AssemblyAction {
      return {
         id: () => "asset-tree open-worksheet",
         label: () => "_#(js:Open Sheet)",
         icon: () => "",
         enabled: () => !this.deployed,
         visible: () => true,
         action: () => this.openSheetFromAsset(entries)
      };
   }

   private createRenameWorksheetAction(node: TreeNodeModel): AssemblyAction {
      return {
         id: () => "asset-tree rename-worksheet",
         label: () => "_#(js:Rename)",
         icon: () => "",
         enabled: () => true,
         visible: () => true,
         action: () => this.showRenameAssetDialog(node)
      };
   }

   private createDeleteWorksheetAction(entries: AssetEntry[], treeView: number): AssemblyAction {
      return {
         id: () => "asset-tree delete-worksheet",
         label: () => "_#(js:Remove)",
         icon: () => "",
         enabled: () => true,
         visible: () => true,
         action: () => treeView == TreeView.RECENT_VIEW ? this.deleteRecentAssets(entries) : this.deleteAssets(entries)
      };
   }

   private createNewViewsheetAction(entry: AssetEntry): AssemblyAction {
      return {
         id: () => "asset-tree new-viewsheet",
         label: () => "_#(js:New Viewsheet)",
         icon: () => "",
         enabled: () => true,
         visible: () => this.viewsheetPermission,
         action: () => this.onNewViewsheet.emit(entry)
      };
   }

   private createRenameFolderAction(node: TreeNodeModel): AssemblyAction {
      return {
         id: () => "asset-tree rename-folder",
         label: () => "_#(js:Rename)",
         icon: () => "",
         enabled: () => !Tool.isAuditNode(node),
         visible: () => true,
         action: () => this.showRenameAssetDialog(node)
      };
   }

   private createDeleteFolderAction(entries: AssetEntry[]): AssemblyAction {
      return {
         id: () => "asset-tree remove-folder",
         label: () => "_#(js:Remove)",
         icon: () => "fa fa-times",
         enabled: () => !this.containsAuditNodes(entries),
         visible: () => true,
         action: () => this.deleteAssets(entries)
      };
   }

   private createNewTableStyleAction(node: TreeNodeModel): AssemblyAction {
      return {
         id: () => "asset-tree new-table-style",
         label: () => "_#(js:New Table Style)",
         icon: () => "",
         enabled: () => !Tool.isAuditNode(node),
         visible: () => true,
         action: () => this.onNewTableStyle.emit()
      };
   }

   private createNewScriptAction(node: TreeNodeModel): AssemblyAction {
      return {
         id: () => "asset-tree new-table-style",
         label: () => "_#(js:New Script Function)",
         icon: () => "",
         enabled: () => !Tool.isAuditNode(node),
         visible: () => true,
         action: () => this.onNewScript.emit()
      };
   }

   private containsAuditNodes(entries: AssetEntry[]): boolean {
      return entries.filter(entry => !!entry.path)
         .map(entry => entry.path.split("/")[0])
         .some(path => path == Tool.BUILT_IN_ADMIN_REPORTS);
   }

   private createNewFolder(parent: AssetEntry) {
      const dialog = ComponentTool.showDialog(this.modalService, InputNameDialog,
         (name: string) => {
            this.sendAddFolderEvent(parent, name);
         }, {backdrop: "static"});

      dialog.title = "_#(js:Create New Folder)";
      dialog.label = "_#(js:Folder Name)";
      dialog.helpLinkKey = parent.type == AssetType.TABLE_STYLE_FOLDER ?
         "CreateTableStyleFolder" : "CreateNewAssetFolder";
      dialog.validators = [
         FormValidators.required,
         FormValidators.assetEntryBannedCharacters,
         FormValidators.assetNameStartWithCharDigit
      ];
      dialog.validatorMessages = [
         {validatorName: "required", message: "_#(js:folder.required)"},
         {validatorName: "assetEntryBannedCharacters", message: ASSET_ENTRY_BANNED_CHARS_MESSAGE},
         {validatorName: "assetNameStartWithCharDigit", message: START_WITH_CHART_DIGIT_MESSAGE}
      ];
   }

   private sendAddFolderEvent(parent: AssetEntry, name: string) {
      let event = new AddFolderEvent();
      event.setName(name);
      event.setParent(parent);
      this.modelService.sendModel<MessageCommand>(ADD_FOLDER_URI, event)
         .subscribe((res) => {
            if(!!res.body) {
               this.showMessage(res.body.message);
            }
         });
   }

   private createOpenScriptAction(entries: AssetEntry[]): AssemblyAction {
      return {
         id: () => "asset-tree open-script",
         label: () => "_#(js:Open Script)",
         icon: () => "",
         enabled: () => !this.deployed,
         visible: () => true,
         action: () => this.openLibAssets(entries)
      };
   }

   private createOpenTableStyleAction(entries: AssetEntry[]): AssemblyAction {
      return {
         id: () => "asset-tree open-table-style",
         label: () => "_#(js:Open Table Style)",
         icon: () => "",
         enabled: () => !this.deployed,
         visible: () => true,
         action: () => this.openLibAssets(entries)
      };
   }

   private createRenameScriptAction(node: TreeNodeModel): AssemblyAction {
      return {
         id: () => "asset-tree rename-script",
         label: () => "_#(js:Rename)",
         icon: () => "",
         enabled: () => true,
         visible: () => true,
         action: () => this.showRenameAssetDialog(node)
      };
   }

   private createRenameTableStyleAction(node: TreeNodeModel): AssemblyAction {
      return {
         id: () => "asset-tree rename-table-style",
         label: () => "_#(js:Rename)",
         icon: () => "",
         enabled: () => true,
         visible: () => true,
         action: () => this.showRenameAssetDialog(node)
      };
   }

   private createDeleteScriptAction(entries: AssetEntry[], treeView: number): AssemblyAction {
      return {
         id: () => "asset-tree delete-script",
         label: () => "_#(js:Remove)",
         icon: () => "",
         enabled: () => true,
         visible: () => true,
         action: () => treeView == TreeView.RECENT_VIEW ? this.deleteRecentAssets(entries) : this.deleteAssets(entries)
      };
   }

   private createDeleteTableStyleAction(entries: AssetEntry[], treeView: number): AssemblyAction {
      return {
         id: () => "asset-tree delete-table-style",
         label: () => "_#(js:Remove)",
         icon: () => "",
         enabled: () => true,
         visible: () => true,
         action: () => treeView == TreeView.RECENT_VIEW ? this.deleteRecentAssets(entries) : this.deleteAssets(entries)
      };
   }

   private openLibAssets(entries: AssetEntry[]) {
      entries.forEach(entry => {
         if(AssetEntryHelper.isScript(entry) || AssetEntryHelper.isTableStyle(entry)) {
            this.addRecentlyViewed(entry);

            if(AssetEntryHelper.isScript(entry)) {
               this.onOpenLibraryAsset.emit({type: "script", assetId: entry.identifier});
            }
            else {
               this.onOpenLibraryAsset.emit({
                  type: "tableStyle",
                  assetId: entry.identifier,
                  styleId: entry.properties["styleID"]
               });
            }
         }
      });

      this.assetTree.selectNodes([]);
   }

   private dispatchRemoveAssetEvent(entry: AssetEntry, confirmed: boolean = false) {
      let event = new RemoveAssetEvent(entry, confirmed);

      this.modelService.sendModel<MessageCommand>(REMOVE_ASSET_URI, event)
         .subscribe((res) => {
            if(!!res.body) {
               let messageCommand = res.body;

               if(messageCommand.type !== "CONFIRM") {
                  this.showMessage(messageCommand.message);
               }
               else if(!confirmed) {
                  this.confirm(messageCommand.message, () => {
                     this.dispatchRemoveAssetEvent(entry, true);
                  });
               }
            }
         });
   }

   private moveAssetToRecyclingBin(entry: AssetEntry, confirmed: boolean = false): void {
      const dashboardUri: string = REMOVE_DASHBOARD_ASSET_URI_AGILE;
      const datasetUri: string = REMOVE_DATASET_ASSET_URI_AGILE;
      const event = new RemoveAssetEvent(entry, confirmed);
      let uri = entry.type == AssetType.WORKSHEET || entry.type == AssetType.FOLDER ? datasetUri : dashboardUri;

      this.modelService.sendModel<MessageCommand>(uri, event)
         .subscribe((res) => {
            if(!!res.body) {
               let messageCommand = res.body;

               if(messageCommand.type !== "CONFIRM") {
                  this.showMessage(messageCommand.message);
               }
               else if(!confirmed) {
                  if(!!messageCommand.message) {
                     this.confirm(messageCommand.message, () => {
                        this.moveAssetToRecyclingBin(entry, true);
                     });
                  }
                  else {
                     this.moveAssetToRecyclingBin(entry, true);
                  }
               }
            }
         });
   }

   private deleteRecentAssets(entries: AssetEntry[]) {
      this.composerRecentService.removeRecentlyViewed(entries);
   }

   private deleteAssets(entries: AssetEntry[]) {
      let hasFolder: boolean = false;
      let message: string;

      for(let entry of entries) {
         // if(this.isAssetOpened(entry)) {
         //    this.showMessage("_#(js:common.tree.deleteForbidden)");
         //    return;
         // }

         if(entry.folder) {
            hasFolder = true;
         }
      }

      if(hasFolder) {
         message = "_#(js:common.tree.removeFolder)";
      }
      else {
         message = "_#(js:common.tree.deleteSelected)";
      }

      this.confirm(message, () => {
         entries.forEach((entry) => {
            let openedtabs: ComposerTabModel[] = this.containsOpenedLibraryAssets(entry);

            if(openedtabs) {
               openedtabs.forEach(tab => this.onTabClose.emit(tab));
            }

            this.dispatchRemoveAssetEvent(entry);
         });
      });
   }

   private showRenameAssetDialog(node: TreeNodeModel) {
      const dialog = ComponentTool.showDialog(this.modalService, InputNameDialog,
         (name: string) => {
            let asset: AssetEntry = node.data;

            if(node.label == name) {
               return;
            }

            this.renameAsset(asset, name, node);
         }, {backdrop: "static"});

      dialog.title = "_#(js:Rename Asset)";
      dialog.label = "_#(js:Asset Name)";
      dialog.value = node.label;
      dialog.helpLinkKey = "RenameAsset";
      dialog.validators = [
         FormValidators.required,
         FormValidators.assetEntryBannedCharacters,
         FormValidators.assetNameStartWithCharDigit
      ];
      dialog.validatorMessages = [
         {validatorName: "required", message: "_#(js:asset.tree.nameValid)"},
         {validatorName: "assetEntryBannedCharacters", message: ASSET_ENTRY_BANNED_CHARS_MESSAGE},
         {validatorName: "assetNameStartWithCharDigit", message: START_WITH_CHART_DIGIT_MESSAGE}
      ];
   }

   private renameAsset(entry: AssetEntry, name: string, node: TreeNodeModel) {
      for(let tab of this.opendTabs) {
         if(tab.type == "script" && !tab.asset.id.includes("Script Function")) {
            const index = tab.asset.id.indexOf("^" + tab.asset.label + "^");
            tab.asset.id = tab.asset.id.slice(0, index+1) + "Script Function/" + tab.asset.id.slice(index+1);
         }
      }

      if(this.isAssetOpened(entry)) {
         if(entry.type == "SCRIPT") {
            this.showMessage("_#(js:repository.tree.renameForbidden)");
         }
         else {
            this.showMessage("_#(js:common.tree.renameForbidden)");
         }
      }
      else {
         const oldName = node.label;
         node.label = name;
         node.loading = true;
         this.assetTree.refreshView();
         this.dispatchRenameAssetEvent(entry, name, node, oldName);
      }
   }

   private isAssetOpened(entry: AssetEntry): boolean {
      if(this.opendTabs.length > 0) {
         for(let tab of this.opendTabs) {
            let parentNode =
               this.assetTree.findAssetTreeNodeParentFromIdentifier(this.assetTree.root, tab.asset.id);

            if(!parentNode) {
               continue;
            }

            let parentPath: string = parentNode.data.path;

            if(tab.asset.id === entry.identifier || parentPath.indexOf(entry.path) != -1) {
               return true;
            }
         }
      }

      return false;
   }

   private containsOpenedLibraryAssets(entry: AssetEntry): ComposerTabModel[] {
      let tabs: ComposerTabModel[] = [];

      if(this.opendTabs.length > 0) {
         for(let tab of this.opendTabs) {
            let parentNode =
               this.assetTree.findAssetTreeNodeParentFromIdentifier(this.assetTree.root, tab.asset.id);

            if(!parentNode) {
               continue;
            }

            let parentPath: string = parentNode.data.path;

            if(tab.asset.id === entry.identifier || parentPath.indexOf(entry.path) != -1) {
               tabs.push(tab);
            }
         }
      }

      return tabs;
   }


   private dispatchRenameAssetEvent(entry: AssetEntry, name: string, node: TreeNodeModel,
                                    oldName: string, confirmed: boolean = false)
   {
      let event = new RenameAssetEvent(entry, name, confirmed);
      this.modelService.sendModel<MessageCommand>(RENAME_ASSET_URI, event)
         .subscribe((res) => {
            this.assetTree.selectedNodes = [];

            if(!!res.body) {
               let messageCommand = res.body;

               if(messageCommand.type !== "CONFIRM") {
                  this.showMessage(messageCommand.message);
                  node.label = oldName;
                  node.loading = false;
                  this.assetTree.refreshView();
               }
               else if(!confirmed) {
                  this.confirm(messageCommand.message,
                     () => {
                        this.dispatchRenameAssetEvent(entry, name, node, oldName, true);
                     },
                     () => {
                        node.label = oldName;
                        node.loading = false;
                        this.assetTree.refreshView();
                     });
               }
            }
         });
   }

   private dispatchChangeAssetEvent(targetNode: TreeNodeModel, parent: AssetEntry,
                                    entries: AssetEntry[], confirmed: boolean = false)
   {
      let event = new ChangeAssetEvent(parent, entries, confirmed);

      for(const entry of entries) {
         if(entry.type === "TABLE_STYLE" && this.isAssetOpened(entry)) {
            ComponentTool.showConfirmDialog(this.modalService,
               "_#(js:Warning)", "_#(js:common.tree.changeFolderForbidden)",
               {"ok": "_#(js:OK)"}).then(() => false);
            targetNode.loading = false;
            targetNode.expanded = true;
            this.assetTree.virtualScrollTree.tree.expandNode(targetNode);
            return;
         }
      }

      this.modelService.sendModel<MessageCommand>(CHANGE_ASSET_URI, event)
         .subscribe((res) => {
            if(!!res.body) {
               let messageCommand = res.body;

               if(messageCommand.type !== "CONFIRM") {
                  this.showMessage(messageCommand.message);
                  targetNode.loading = false;
               }
               else if(!confirmed) {
                  this.confirm(messageCommand.message, () => {
                     this.dispatchChangeAssetEvent(targetNode, parent, entries, true);
                  }, () => {
                     targetNode.loading = false;
                  });
               }
            }
            else {
               targetNode.loading = false;
            }

            if(!targetNode.leaf) {
               targetNode.expanded = true;
               this.assetTree.virtualScrollTree.tree.expandNode(targetNode);
            }
         });
   }

   showMessage(message: string) {
      ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", message, {"ok": "_#(js:OK)"},
         {backdrop: false });
   }

   confirm(message: string, onOk: Function, onCancel: Function = null) {
      ComponentTool.showMessageDialog(this.modalService, "_#(js:Confirm)", message,
         {"ok": "_#(js:OK)", "cancel": "_#(js:Cancel)"}, {backdrop: false})
         .then((buttonClicked: string) => {
            if(buttonClicked === "ok") {
               onOk();
            }
            else if(buttonClicked === "cancel" && onCancel) {
               onCancel();
            }
         }, () => {
            if(onCancel != null) {
               onCancel();
            }
         });
   }

   nodeDrag(event: DragEvent): void {
      const textData = event.dataTransfer.getData("text");

      if(textData) {
         const jsonData = JSON.parse(textData);
         const entries = jsonData.worksheet ? jsonData.worksheet
            : jsonData.table ? jsonData.table
            : jsonData.physical_table ? jsonData.physical_table
            : jsonData.column ? jsonData.column
            : jsonData.physical_column ? jsonData.physical_column
            : jsonData.query ? jsonData.query
            : jsonData.viewsheet ? jsonData.viewsheet
            : jsonData.table_style ? jsonData.table_style
            : jsonData.script;

         if(entries) {
            const labels = entries.map((entry) => {
               const i = entry.path.lastIndexOf("/");
               return i < 0 ? entry.path : entry.path.substring(i + 1);
            });

            const elem = GuiTool.createDragImage(labels);
            GuiTool.setDragImage(event, elem, this.zone, this.domService);
         }
      }
   }

   isRejectFunction(): any {
      return (nodes) => this.isRejectNodes(nodes);
   }

   isRejectNodes(nodes: TreeNodeModel[]): boolean {
      let isColumn = false;

      for(let i = 0; i < nodes.length; i++) {
         if(nodes[i].dragName == "column") {
            isColumn = true;
            break;
         }
      }

      return isColumn;
   }

   /**
    * Node drop event handler.
    * @param node the node on which the drop happened
    */
   nodeDrop(event: any): void {
      let node: TreeNodeModel = event.node;
      let entries: AssetEntry[] = [];
      let parent: AssetEntry = <AssetEntry> node.data;

      if(!parent.folder) {
         let parentNode: TreeNodeModel = this.assetTree.getParentNode(node);

         if(parentNode) {
            parent = <AssetEntry> parentNode.data;
         }
      }

      if(parent.type != AssetType.FOLDER &&
         parent.type != AssetType.REPOSITORY_FOLDER &&
         parent.type != AssetType.REPORT_WORKSHEET_FOLDER &&
         parent.type != AssetType.TABLE_STYLE_FOLDER)
      {
         return;
      }

      let dragData = this.dragService.getDragData();

      for(let key of Object.keys(dragData)) {
         let dragEntries: AssetEntry[] = JSON.parse(dragData[key]);

         if(dragEntries && dragEntries.length > 0) {
            for(let entry of dragEntries) {
               if(Tool.isEquals(parent, entry)) {
                  return;
               }

               if(parent.type == AssetType.FOLDER &&
                  entry.type != AssetType.FOLDER &&
                  entry.type != AssetType.WORKSHEET)
               {
                  return;
               }

               if(parent.type == AssetType.REPOSITORY_FOLDER &&
                  entry.type != AssetType.REPOSITORY_FOLDER &&
                  entry.type != AssetType.VIEWSHEET &&
                  entry.type != AssetType.VIEWSHEET_SNAPSHOT ||
                  parent.type == AssetType.TABLE_STYLE_FOLDER &&
                  entry.type != AssetType.TABLE_STYLE_FOLDER &&
                  entry.type != AssetType.TABLE_STYLE
               )
               {
                  return;
               }

               // make sure the drag entries are from this tree
               if(this.assetTree.getNodeByData("data", entry)) {
                  entries.push(entry);
               }
            }
         }
      }

      if(entries.length > 0) {
         this.confirm("_#(js:em.reports.drag.confirm)", () => {
            if(node.children && !node.leaf) {
               node.expanded = true;
            }

            this.dispatchChangeAssetEvent(node, parent, entries);
         });
      }
   }
}
