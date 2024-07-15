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
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Input,
   NgZone,
   OnDestroy,
   OnInit,
   Output,
   Renderer2,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { Router } from "@angular/router";
import { NgbModal, NgbModalOptions, NgbModalRef } from "@ng-bootstrap/ng-bootstrap";
import { Subscription } from "rxjs";
import { AssetEntry, createAssetEntry } from "../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../shared/data/asset-type";
import { Tool } from "../../../../../shared/util/tool";
import { RefreshBindingTreeEvent } from "../../binding/event/refresh-binding-tree-event";
import { AssetEntryHelper } from "../../common/data/asset-entry-helper";
import { DataRef } from "../../common/data/data-ref";
import { Notification } from "../../common/data/notification";
import { Point } from "../../common/data/point";
import { VSObjectFormatInfoModel } from "../../common/data/vs-object-format-info-model";
import { UIContextService } from "../../common/services/ui-context.service";
import { ComponentTool } from "../../common/util/component-tool";
import { GuiTool } from "../../common/util/gui-tool";
import { LocalStorage } from "../../common/util/local-storage.util";
import { ViewsheetClientService } from "../../common/viewsheet-client";
import { NewViewsheetDialog } from "../../vs-wizard/gui/new-viewsheet-dialog.component";
import { CloseWizardModel } from "../../vs-wizard/model/close-wizard-model";
import { NewViewsheetDialogModel } from "../../vs-wizard/model/new-viewsheet-dialog-model";
import { VsWizardEditModes } from "../../vs-wizard/model/vs-wizard-edit-modes";
import { VsWizardModel, WizardOriginalInfo } from "../../vs-wizard/model/vs-wizard-model";
import { SetPrincipalCommand } from "../../vsobjects/command/set-principal-command";
import { FormatVSObjectEvent } from "../../vsobjects/event/format-vs-object-event";
import { OpenViewsheetEvent } from "../../vsobjects/event/open-viewsheet-event";
import { VSRefreshEvent } from "../../vsobjects/event/vs-refresh-event";
import { GuideBounds } from "../../vsobjects/model/layout/guide-bounds";
import { VSObjectModel } from "../../vsobjects/model/vs-object-model";
import { VSTabModel } from "../../vsobjects/model/vs-tab-model";
import { ShowHyperlinkService } from "../../vsobjects/show-hyperlink.service";
import { VSUtil } from "../../vsobjects/util/vs-util";
import { loadingScriptTreeModel, ScriptPaneTreeModel } from "../../widget/dialog/script-pane/script-pane-tree-model";
import { NotificationsComponent } from "../../widget/notifications/notifications.component";
import { PresenterPropertyDialogModel } from "../../widget/presenter/data/presenter-property-dialog-model";
import { ModelService } from "../../widget/services/model.service";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { VSScaleService } from "../../widget/services/scale/vs-scale.service";
import { SplitPane } from "../../widget/split-pane/split-pane.component";
import { OpenComposerAssetCommand } from "../command/open-composer-asset-command";
import { OpenSheetEvent } from "../data/open-sheet-event";
import { Sheet, SheetType } from "../data/sheet";
import { AssetRepositoryPaneModel } from "../data/vs/asset-respository-pane-model";
import { SaveViewsheetDialogModel } from "../data/vs/save-viewsheet-dialog-model";
import { Viewsheet } from "../data/vs/viewsheet";
import { ViewsheetPropertyDialogModel } from "../data/vs/viewsheet-property-dialog-model";
import { VSLayoutModel } from "../data/vs/vs-layout-model";
import { VSLayoutObjectModel } from "../data/vs/vs-layout-object-model";
import { SaveWorksheetDialogModel } from "../data/ws/save-worksheet-dialog-model";
import { SaveWSConfirmationModel } from "../data/ws/save-ws-confirmation-model";
import { Worksheet } from "../data/ws/worksheet";
import { ClipboardService } from "./clipboard.service";
import { ComponentsPane } from "./components-pane/components-pane.component";
import { ComposerClientService } from "./composer-client.service";
import { ResizeHandlerService } from "./resize-handler.service";
import { ComposerToolbarComponent } from "./toolbar/composer-toolbar.component";
import { ComposerObjectService } from "./vs/composer-object.service";
import { CloseSheetEvent } from "./vs/event/close-sheet-event";
import { SaveSheetEvent } from "./ws/socket/save-sheet-event";
import { WSObjectType } from "../dialog/ws/new-worksheet-dialog.component";
import { MessageCommand } from "../../common/viewsheet-client/message-command";
import { RefreshVsAssemblyEvent } from "../../vsobjects/event/refresh-vs-assembly-event";
import { AssemblyChangedCommand } from "../../vs-wizard/model/command/assembly-changed-command";
import { AssetConstants } from "../../common/data/asset-constants";
import { AssetTreeService } from "../../widget/asset-tree/asset-tree.service";
import { CheckBaseWsChangedEvent } from "../../vsobjects/event/check-base-ws-changed-event";
import { VSFormatsPane } from "../../vsobjects/format/vs-formats-pane.component";
import { ComposerRecentService } from "./composer-recent.service";
import { Dimension } from "../../common/data/dimension";
import {
   GettingStartedService,
   GettingStartedStep,
   StepIndex
} from "../../widget/dialog/getting-started-dialog/service/getting-started.service";
import { LibraryAsset } from "../data/library-asset";
import { TableStyleModel } from "../data/tablestyle/table-style-model";
import { loadingScriptTreePaneModel, ScriptTreePaneModel } from "../data/script/script-tree-pane-model";
import { ScriptModel } from "../data/script/script";
import { OpenLibraryAssetEvent } from "../data/open-libraryAsset-event";
import { ComposerTabModel } from "./composer-tab-model";
import { SaveTableStyleDialogModel } from "../data/tablestyle/save-table-style-dialog-model";
import { SpecificationModel } from "../data/tablestyle/specification-model";
import { CSSTableStyleModel } from "../data/tablestyle/css/css-table-style-model";
import { ScriptService } from "./script/script.service";
import { TableStyleUtil } from "../../common/util/table-style-util";
import { SaveScriptDialogModel } from "../data/script/save-script-dialog-model";
import { SaveLibraryDialogModelValidator } from "../data/tablestyle/save-library-dialog-model-validator";
import { ScriptPropertyDialogComponent } from "../dialog/script/script-property-dialog.component";
import { StylePaneComponent } from "./tablestyle/editor/style-pane.component";
import { ScriptEditPaneComponent } from "./script/editor/script-edit-pane.component";
import { SaveScriptDialogValidator } from "../data/script/SaveScriptDialogValidator";

export enum SidebarTab {
   ASSET_TREE,
   TOOLBOX,
   SCRIPT,
   COMPONENTS,
   FORMAT,
   WORKSHEET_COMPOSITE_TABLE_SIDEBAR,
   REGIONS
}

/** Worksheet URIs */
const CHECK_PRIMARY_ASSEMBLY_URI = "../api/composer/worksheet/check-primary-assembly/";
const CLOSE_WORKSHEET_SOCKET_URI = "/events/ws/close";
const SAVE_AND_CLOSE_WORKSHEET_SOCKET_URI = "/events/composer/worksheet/save-and-close";
const SAVE_WORKSHEET_DIALOG_AND_CLOSE_SOCKET_URI = "/events/composer/ws/dialog/save-worksheet-dialog-model/save-and-close";
const SAVE_WORKSHEET_DIALOG_SOCKET_URI = "/events/composer/ws/dialog/save-worksheet-dialog-model/";
const EDIT_WORKSHEET_JOINS_SOCKET_URI = "/events/composer/ws/join/open-join/";
const CANCEL_WORKSHEET_JOINS_SOCKET_URI = "/events/composer/ws/join/cancel-ws-join/";
const SAVE_WORKSHEET_SOCKET_URI = "/events/composer/worksheet/save";
const CHECK_WORKSHEET_CYCLE_URI = "../api/composer/worksheet/checkCycle";

/** Viewsheet URIs */
const CLOSE_VIEWSHEET_SOCKET_URI = "/events/composer/viewsheet/close";
const SAVE_VIEWSHEET_SOCKET_URI = "/events/composer/viewsheet/save";
const VIEWSHEET_CHECK_BASE_WS_UPDATE_URL = "/events/composer/viewsheet/check-base-ws-expired";
const SAVE_AND_CLOSE_VIEWSHEET_SOCKET_URI = "/events/composer/viewsheet/save-and-close";
const SAVE_VIEWSHEET_DIALOG_REST_URI = "../api/composer/vs/save-viewsheet-dialog-model/";
const SAVE_VIEWSHEET_DIALOG_SOCKET_URI = "/events/composer/vs/save-viewsheet-dialog-model";
const SAVE_VIEWSHEET_DIALOG_AND_CLOSE_SOCKET_URI = "/events/composer/vs/save-viewsheet-dialog-model/save-and-close";
const UPDATE_FORMAT_URI = "/events/composer/viewsheet/format";
const VIEWSHEET_PROPERTY_URI = "composer/vs/viewsheet-property-dialog-model";
const COMPOSER_WIZARD_STATUS_URI: string = "../api/composer/wizard/status";
const VIEWSHEET_CHECK_DEPEND_CHANGED_URI = "../api/composer/viewsheet/checkDependChanged";
const WORKSHEET_CHECK_DEPEND_CHANGED_URI = "../api/composer/worksheet/checkDependChanged";
const VIEWSHEET_RECYCLE_AUTO_SAVE_FILE = "../api/composer/viewsheet/recycleAutoSave";

/** TableStyleModel URIs */
const URI_NEW_TABLESTYLE = "../api/composer/table-style/new";
const URI_OPEN_TABLESTYLE = "../api/composer/table-style/open";
const URI_GET_TABLESTYLE_SPECMODEL = "../api/composer/table-style/spec-model";
const URI_SAVE_TABLESTYLE = "../api/composer/table-style/save";
const URI_SAVE_AS_TABLESTYLE = "../api/composer/table-style/save-as";
const URI_GET_CSS_TABLESTYLE_FORMAT = "../api/composer/table-style/css-format";

/** Script URIs */
const URI_SAVE_SCRIPT = "../api/composer/save/script";
const URI_SAVE_AS_SCRIPT = "../api/composer/save/as/script";
const SAVE_SCRIPT_DIALOG_VALIDATION_URI = "../api/composer/script/save-script-dialog/";
const URI_NEW_SCRIPT = "../api/script/new";
const URI_OPEN_SCRIPT = "../api/script/open";

let sheetCounter = 1;

const CONFIRM_MESSAGE = {
   title: "_#(js:Confirm)",
   options: { "yes": "_#(js:Yes)", "no": "_#(js:No)" },
   optionsOnlyOk: { "ok": "_#(js:OK)" },
   optionsWithCancel: { "yes": "_#(js:Yes)", "no": "_#(js:No)", "cancel": "_#(js:Cancel)" }
};

/**
 * The main pane of the worksheet composer.
 * Its purpose is to control the layout of its children panes.
 */
@Component({
   selector: "composer-main",
   templateUrl: "composer-main.component.html",
   styleUrls: ["composer-main.component.scss", "tab-selector/tab-selector-shared.scss"],
   providers: [
      ComposerClientService,
      {
         provide: ScaleService,
         useClass: VSScaleService
      }
   ]
})
export class ComposerMainComponent implements OnInit, OnDestroy, AfterViewInit {
   @Input() initialSheet: string;
   @Input() baseWS: string; // used from test drive, create a vs with baseWS (id) on open
   @Input() deployed: boolean = false;
   @Input() runtimeId: string;
   @Input() wsWizard: boolean = false;
   @Input() styleWizard: boolean = false;
   @Input() scriptWizard: boolean = false;
   @Input() baseDataSource: string;
   @Input() baseDataSourceType: number;
   @Input() vsWizard: boolean = false;
   @Input() saveToFolderId: string;
   @Input() closeOnComplete: boolean = false;
   @Input() setPrincipalCommand: SetPrincipalCommand;
   @Output() closed = new EventEmitter<string>();
   @ViewChild(SplitPane) splitPane: SplitPane;
   @ViewChild("toolPane") toolPane: ElementRef;
   @ViewChild("editorPane") editorPane: ElementRef;
   @ViewChild("toolbar", { static: true }) toolbar: ComposerToolbarComponent;
   @ViewChild("componentsPane") componentsPane: ComponentsPane;
   @ViewChild("saveViewsheetDialog") saveViewsheetDialog: TemplateRef<any>;
   @ViewChild("saveWorksheetDialog") saveWorksheetDialog: TemplateRef<any>;
   @ViewChild("saveTableStyleDialog") saveTableStyleDialog: TemplateRef<any>;
   @ViewChild("viewsheetPropertyDialog") viewsheetPropertyDialog: TemplateRef<any>;
   @ViewChild("notifications") notifications: NotificationsComponent;
   @ViewChild("tabContent") tabContentEle: ElementRef;
   @ViewChild("vsFormatsPane") vsFormatsPane: VSFormatsPane;
   @ViewChild("saveScriptDialog") saveScriptDialog: TemplateRef<any>;
   @ViewChild("editCustomPatternDialog") editCustomPatternDialog: TemplateRef<any>;
   @ViewChild("stylePaneComponent") stylePaneComponent: StylePaneComponent;
   @ViewChild("scriptEditPaneComponent") scriptEditPaneComponent: ScriptEditPaneComponent;

   readonly INIT_SPLIT_PANE_SIZE = 25;
   viewsheetPropertyModel: ViewsheetPropertyDialogModel;
   scriptTreePaneModel: ScriptTreePaneModel;
   scriptTreeModel: ScriptPaneTreeModel;
   openToScript: boolean = false;
   SidebarTab = SidebarTab;
   selectedTab: SidebarTab = SidebarTab.ASSET_TREE;
   toolboxDisabled: boolean = true;
   scriptDisabled: boolean = true;
   regionsDisabled: boolean = true;
   navTabHidden: boolean = true;
   componentsPaneDisabled: boolean = true;
   formatPaneDisabled: boolean = true;
   layoutMode: boolean = false;
   previewMode: boolean = false;
   printLayout: boolean = false;
   sheets: Sheet[] = [];
   _focusedSheet: Sheet;
   touchDevice: boolean = false;
   lastClick: Point = new Point();
   bindingPaneModel = {
      runtimeId: null,
      objectType: null,
      absoluteName: null,
      oldAbsoluteName: null,
      wizardOriginalInfo: null
   };
   principal: String;
   securityEnabled: boolean;
   viewsheetPermission: boolean;
   worksheetPermission: boolean;
   scriptPermission: boolean;
   tableStylePermission: boolean;
   grayedOutFields: DataRef[];
   splitPaneCollapsed: boolean = false;
   splitPaneSize: number = this.INIT_SPLIT_PANE_SIZE;
   scriptFontSize: number = 14;
   viewChecked: boolean = false;
   originalText: string;
   vsScroll: Point = new Point();
   designSaved: boolean = false;
   queryParameters: Map<string, string[]>;
   snapToGrid: boolean = LocalStorage.getItem("snap-to-grid") != "false";
   snapToObjects: boolean = LocalStorage.getItem("snap-to-objects") == "true";
   defaultFolder: AssetEntry;
   showAutoSaved: boolean = false;
   spec: SpecificationModel;
   saveViewsheetModel: SaveViewsheetDialogModel;
   saveTableStyleModel: SaveTableStyleDialogModel;
   saveScriptModel: SaveScriptDialogModel;
   private keydownListener: () => void;
   private confirmExpiredDisplayed = false;
   private subscriptions = new Subscription();
   private lastWS: string = null; // last saved ws
   wizardEditMode = false;
   wizardModel: VsWizardModel;
   importDialogOpen = false;
   tabContentEleToChild: ElementRef;
   openedTabs: ComposerTabModel[] = [];
   private _focusedTab: ComposerTabModel;
   private propertyDialogModal: NgbModalRef;

   constructor(private composerObjectService: ComposerObjectService,
      private resizeHandlerService: ResizeHandlerService,
      private clipboardService: ClipboardService,
      private modalService: NgbModal,
      private modelService: ModelService,
      private renderer: Renderer2,
      private hyperLinkService: ShowHyperlinkService,
      private assetTreeService: AssetTreeService,
      private uiContextService: UIContextService,
      private composerClient: ComposerClientService,
      private composerRecentService: ComposerRecentService,
      private changeDetectorRef: ChangeDetectorRef,
      private http: HttpClient,
      private zone: NgZone,
      private gettingStartedService: GettingStartedService,
      private router: Router,
      private scriptService: ScriptService) {
      GuiTool.isTouchDevice().then((value: boolean) => {
         this.touchDevice = value;
      });
      this.modelService.errorHandler = (error: any) => {
         if(error.error === "expiredSheet") {
            const sheet = this.sheets.find((s) => s.runtimeId === error.id);

            if(sheet) {
               if(!this.confirmExpiredDisplayed) {
                  this.confirmExpiredDisplayed = true;
                  const msg = "_#(js:common.expiredSheets)"
                     + ComponentTool.MESSAGEDIALOG_MESSAGE_CONNECTION
                     + sheet.type + " " + sheet.label;
                  const options: NgbModalOptions = { backdrop: "static" };
                  ComponentTool.showConfirmDialog(modalService, "_#(js:Expired)", msg,
                     CONFIRM_MESSAGE.options, options)
                     .then((answer) => {
                        if(answer === "yes") {
                           this.onSheetReload(sheet, true);
                        }
                        else {
                           this.onSheetClosed(sheet, true);
                        }

                        this.confirmExpiredDisplayed = false;
                     });
               }
            }

            return true;
         }

         return false;
      };

      this.subscriptions.add(this.hyperLinkService.showLinkSheetSubject.subscribe((linkModel) => {
         this.queryParameters = linkModel.queryParameters;
         this.showLinkVSInTab(linkModel.id);
      }));
   }

   ngOnInit(): void {
      if(this.setPrincipalCommand) {
         this.principal = this.setPrincipalCommand.principal;
         this.securityEnabled = this.setPrincipalCommand.securityEnabled;
         this.worksheetPermission = this.setPrincipalCommand.worksheetPermission;
         this.viewsheetPermission = this.setPrincipalCommand.viewsheetPermission;
         this.scriptPermission = this.setPrincipalCommand.scriptPermission;
         this.tableStylePermission = this.setPrincipalCommand.tableStylePermission;

         if(!this.vsWizard && this.setPrincipalCommand.autoSaveFiles != null &&
            !this.wsWizard && this.worksheetPermission &&
            this.setPrincipalCommand.autoSaveFiles.length > 0) {
            this.fixAutoSaveFiles(this.setPrincipalCommand.autoSaveFiles);
         }
      }

      this.setKeydownListener();

      const bc = new BroadcastChannel("composer");
      bc.onmessage = (evt) => this.handleMessageEvent(evt);

      // if we passed in a sheet assetId as a query parameter, open it here
      if(this.initialSheet) {
         const asset: AssetEntry = createAssetEntry(this.initialSheet);

         if(asset.type == AssetType.VIEWSHEET || asset.type == AssetType.WORKSHEET) {
            this.composerRecentService.addRecentlyViewed(asset);
         }

         if(asset.type == AssetType.VIEWSHEET) {
            this.openViewsheet(this.initialSheet, false, this.runtimeId);
         }
         else if(asset.type == AssetType.WORKSHEET) {
            this.openWorksheet(this.initialSheet,
               this.gettingStartedService.isEditWs());
         }
      }
      else if(this.wsWizard && this.worksheetPermission) {
         const gettingStarted = this.gettingStartedService.isEditWs();

         if(gettingStarted && this.gettingStartedService.isStartFromScratch()) {
            this.openNewWorksheet(gettingStarted);
         }
         else {
            this.openNewWsWithWizard(gettingStarted);
         }
      }
      else if(this.vsWizard && this.viewsheetPermission) {
         if(this.gettingStartedService.isCreateDashboard()) {
            this.gettingStartedCreateDashboard(this.baseWS);
         }
         else {
            this.newViewsheet();
         }
      }
      else if(this.scriptWizard && this.scriptPermission) {
         this.openNewScriptAsset();
      }
      else if(this.styleWizard && this.tableStylePermission) {
         this.onNewTableStyle();
      }

      if(!this.deployed) {
         this.initComposerClient();
      }

      this.listenGettingStartedEvent();

      window.addEventListener("storage", (event) => {
         if(event.key.endsWith("composer_recently_viewed_" + this.principal)) {
            this.composerRecentService.updateRecentlyViewed();
         }
      });
   }

   // open wizard if requested from portal
   private handleMessageEvent(event: MessageEvent): void {
      this.zone.run(() => {
         if(event.data == "vsWizard" && this.viewsheetPermission) {
            this.newViewsheet();
         }
         else if(event.data == "wsWizard" && this.worksheetPermission) {
            this.openNewWsWithWizard();
         }
      });
   }

   ngAfterViewInit(): void {
      setTimeout(() => {
         this.tabContentEleToChild = this.tabContentEle;
      });
   }

   get focusedSheet(): Sheet {
      return this._focusedSheet;
   }

   set focusedSheet(sheet: Sheet) {
      this.updateFocusedSheet(sheet, true);
   }

   get focusedTab(): ComposerTabModel {
      return this._focusedTab;
   }

   set focusedTab(tab: ComposerTabModel) {
      this._focusedTab = tab;
      this.updateFocusedSheet(this.isSheet(tab?.asset) ? <Sheet>tab.asset : null, false);
   }

   private updateFocusedSheet(sheet: Sheet, updateFocusedSheet: boolean) {
      let ftype = this._focusedSheet?.type;
      this._focusedSheet = sheet;
      let type = sheet?.type;

      if(ftype != type) {
         this.assetTreeService.loadAssetTreeSubject.next(type);

         if(type == "viewsheet") {
            const viewsheet: Viewsheet = sheet as Viewsheet;

            if(viewsheet.socketConnection) {
               viewsheet.socketConnection.sendEvent(VIEWSHEET_CHECK_BASE_WS_UPDATE_URL,
                  new CheckBaseWsChangedEvent());
            }
         }
      }

      if(updateFocusedSheet) {
         this.updateFocusedTab(sheet);
      }
   }

   fixAutoSaveFiles(autoSaveFiles: string[]) {
      if(this.showAutoSaved) {
         return;
      }

      this.showAutoSaved = true;
      const message = "_#(js:common.restoreUnsaveAssets)";

      ComponentTool.showMessageDialog(this.modalService, CONFIRM_MESSAGE.title, message,
         CONFIRM_MESSAGE.optionsWithCancel)
         .then((buttonClicked) => {
            switch(buttonClicked) {
               case "yes":
                  this.openAutoSaveFiles(autoSaveFiles);
                  break;
               case "no":
                  this.recycleAutoSaveFiles(autoSaveFiles);
                  break;
               default:
                  break;
            }
         });
   }

   openAutoSaveFiles(autoSaveFiles: string[]) {
      for(let i = 0; i < autoSaveFiles.length; i++) {
         this.openAutoSaveAsset(autoSaveFiles[i]);
      }
   }

   recycleAutoSaveFiles(autoSaveFiles: String[]) {
      this.modelService.getModel(VIEWSHEET_RECYCLE_AUTO_SAVE_FILE).subscribe();
   }

   ngOnDestroy() {
      if(!!this.subscriptions) {
         this.subscriptions.unsubscribe();
         this.subscriptions = null;
      }

      if(this.composerClient) {
         this.composerClient.disconnect();
      }

      this.removeKeydownListener();
   }

   get focusedViewsheet(): Viewsheet {
      if(this.focusedSheet && this.focusedSheet.type === "viewsheet") {
         return this.focusedSheet as Viewsheet;
      }

      return null;
   }

   get focusedSheetPreview(): boolean {
      return !!this.focusedSheet && (<Viewsheet>this.focusedSheet).preview;
   }

   public onFocusedSheetChanged(sheet: Sheet, updateSidebar: boolean = false,
      initPreview: boolean = false): void {
      if(updateSidebar || !this.focusedSheet || !sheet ||
         sheet.runtimeId != this.focusedSheet.runtimeId) {
         this.updateSidebar(sheet);
      }

      if(this.focusedTab) {
         this.focusedTab.asset.isFocused = false;

         if(this.focusedSheet) {
            this.uiContextService.sheetHide(this.focusedSheet.runtimeId);
         }
      }

      const needUpdateLayout = sheet && sheet.type === "viewsheet" && (<Viewsheet>sheet).preview;
      this.focusedSheet = needUpdateLayout ?
         this.getUpdatedLayoutPreviewSheet(<Viewsheet>sheet) : sheet;

      if(this.focusedSheet) {
         this.focusedSheet.isFocused = true;

         // preview sheet initially has same runtime id as parent. Don't show slide outs
         // using this runtime id
         if(!initPreview) {
            this.uiContextService.sheetShow(this.focusedSheet.runtimeId);
         }
      }
   }

   public onFocusedLibraryAssetChanged(asset: LibraryAsset, updateSidebar: boolean = false): void {
      if(updateSidebar || !this.focusedTab || !asset || this.isSheet(this.focusedTab.asset) ||
         this.focusedTab.asset.id != asset.id) {
         this.updatelibrarySidebar(asset);
      }

      if(this.focusedTab) {
         this.focusedTab.asset.isFocused = false;

         if(this.isSheet(this.focusedTab.asset)) {
            this.uiContextService.sheetHide(this.focusedSheet.runtimeId);
         }
      }

      if(asset) {
         let tabIndex = this.openedTabs
            .findIndex(tab => !this.isSheet(tab.asset) && tab.asset.id == asset.id);
         this.focusedTab = this.openedTabs[tabIndex];
      }
      else {
         this.focusedTab = null;
      }

      if(this.focusedTab) {
         this.focusedTab.asset.isFocused = true;

         if(this.focusedTab.type == "script") {
            this.regionsDisabled = true;
         }
         else if(this.focusedTab.type == "tableStyle") {
            this.scriptDisabled = true;
         }
      }
   }

   private updateFocusedTab(asset: Sheet | LibraryAsset): void {
      this._focusedTab = this.openedTabs.find(tab => tab.asset == asset);
   }

   public onSheetUpdated(sheet: Sheet): void {
      const preview: boolean = sheet && sheet.type === "viewsheet" && (<Viewsheet>sheet).preview;
      const layout: boolean = sheet && sheet.type === "viewsheet" &&
         !!(<Viewsheet>sheet).currentLayout;
      const printLayout: boolean = layout && (<Viewsheet>sheet).currentLayout.printLayout;

      if(this.isActiveSheet(sheet) && (this.focusedSheet != sheet || this.previewMode != preview ||
         this.layoutMode != layout || this.printLayout != printLayout)) {
         this.focusedSheet = sheet;
         this.updateSidebar(sheet);
      }
   }

   private updateSidebar(sheet: Sheet) {
      if(this.deployed) {
         this.componentsPaneDisabled = true;
         this.toolboxDisabled = false;
         this.formatPaneDisabled = false;
         this.scriptDisabled = true;
         this.regionsDisabled = true;
         this.selectedTab = SidebarTab.ASSET_TREE;
      }
      else if(sheet) {
         this.previewMode = sheet.type === "viewsheet" && (<Viewsheet>sheet).preview;
         this.layoutMode = sheet.type === "viewsheet" && !!(<Viewsheet>sheet).currentLayout;
         this.printLayout = this.layoutMode && (<Viewsheet>sheet).currentLayout.printLayout;
         let linkView = sheet.type === "viewsheet" && (<Viewsheet>sheet).linkview;

         if(this.previewMode || linkView) {
            this.componentsPaneDisabled = true;
            this.toolboxDisabled = true;
            this.formatPaneDisabled = true;
            this.selectedTab = SidebarTab.ASSET_TREE;
         }
         else if(this.layoutMode) {
            this.toolboxDisabled = true;
            this.componentsPaneDisabled = false;
            this.formatPaneDisabled = !this.printLayout;
            this.selectedTab = SidebarTab.COMPONENTS;
         }
         else {
            const isWorksheet = sheet.type === "worksheet";
            this.componentsPaneDisabled = true;
            this.formatPaneDisabled = isWorksheet;
            this.toolboxDisabled = isWorksheet;

            if(isWorksheet) {
               const worksheet = sheet as Worksheet;

               if(worksheet.isCompositeView()) {
                  this.selectedTab = SidebarTab.WORKSHEET_COMPOSITE_TABLE_SIDEBAR;
               }
               else {
                  this.selectedTab = SidebarTab.ASSET_TREE;
               }
            }
            else {
               this.selectedTab = SidebarTab.TOOLBOX;
            }
         }
      }
      else {
         this.selectedTab = SidebarTab.ASSET_TREE;
         this.componentsPaneDisabled = true;
         this.toolboxDisabled = true;
         this.formatPaneDisabled = true;
      }

      this.navTabHidden = this.toolboxDisabled && this.componentsPaneDisabled &&
         this.formatPaneDisabled;
   }

   private updatelibrarySidebar(library: LibraryAsset) {
      if(library && library.type === "script") {
         this.componentsPaneDisabled = true;
         this.toolboxDisabled = true;
         this.scriptDisabled = false;
         this.formatPaneDisabled = true;
         this.selectedTab = SidebarTab.SCRIPT;
      }
      else if(library && library.type === "tableStyle") {
         this.componentsPaneDisabled = true;
         this.toolboxDisabled = true;
         this.regionsDisabled = false;
         this.formatPaneDisabled = true;
         this.selectedTab = SidebarTab.REGIONS;
      }
      else {
         this.selectedTab = SidebarTab.ASSET_TREE;
         this.componentsPaneDisabled = true;
         this.toolboxDisabled = true;
         this.formatPaneDisabled = true;
      }

      this.navTabHidden = this.toolboxDisabled && this.componentsPaneDisabled &&
         this.formatPaneDisabled && this.scriptDisabled && this.regionsDisabled;
   }

   private getUpdatedLayoutPreviewSheet(vs: Viewsheet): Sheet {
      for(let parentSheet of this.sheets) {
         if(parentSheet && parentSheet.type === "viewsheet" && !(<Viewsheet>parentSheet).preview &&
            vs.parentSheet && parentSheet.runtimeId === vs.parentSheet.runtimeId) {
            let parentVS: Viewsheet = (<Viewsheet>parentSheet);
            vs.currentLayout = parentVS.currentLayout;
            vs.label = "_#(js:Preview) " + parentVS.label;

            if(parentVS.currentLayout && !parentVS.currentLayout.printLayout) {
               vs.label += " (" + parentVS.currentLayout.name + ")";
            }

            break;
         }
      }

      return vs;
   }

   public onSplitDragEnd(event: any): void {
      this.splitPaneSize = this.splitPane.getSizes()[0];

      if(this.splitPaneSize > 1) {
         this.splitPaneCollapsed = false;
      }
      else {
         this.splitPaneCollapsed = true;
         this.splitPaneSize = this.INIT_SPLIT_PANE_SIZE;
      }

      this.resizeHandlerService.onVerticalResizeEnd();
   }

   get showPaste(): boolean {
      return !this.clipboardService.clipboardEmpty;
   }

   onTabClick(tab: SidebarTab): void {
      this.selectedTab = tab;
   }

   isActive(tab: ComposerTabModel): boolean {
      // new preview sheet has same runtimeId as parent sheet until it creates a new vs on
      // the server. This leads to a moment where preview and parent sheet are both visible.
      return this.isSameTab(tab, this.focusedTab);
   }

   isActiveSheet(sheet: Sheet): boolean {
      // new preview sheet has same runtimeId as parent sheet until it creates a new vs on
      // the server. This leads to a moment where preview and parent sheet are both visible.
      return this.isSameSheet(this.focusedSheet, sheet);
   }

   private isSameTab(tab: ComposerTabModel, other: ComposerTabModel) {
      if(!!tab != !!other) {
         return false;
      }

      if(tab.type != other.type) {
         return false;
      }

      if(this.isSheet(tab.asset)) {
         return this.isSameSheet(<Sheet>tab.asset, <Sheet>other.asset);
      }
      else {
         return tab.asset.id == other.asset.id;
      }
   }

   private isSameSheet(sheet: Sheet, otherSheet: Sheet): boolean {
      return sheet.runtimeId === otherSheet.runtimeId &&
         (sheet.type !== "viewsheet" || (otherSheet.type === "viewsheet" &&
            (<Viewsheet>sheet).preview === (<Viewsheet>otherSheet).preview));
   }

   isPrintLayout(): boolean {
      return this.focusedSheet instanceof Viewsheet
         ? this.focusedSheet.currentLayout &&
         this.focusedSheet.currentLayout.name === "_#(js:Print Layout)"
         : false;
   }

   trackByFn(index: number, tab: ComposerTabModel) {
      return tab?.asset?.type == "viewsheet" || tab?.asset?.type == "worksheet" ?
         (<Sheet>tab.asset).localId : (<LibraryAsset>tab.asset).id;
   }

   closePreview(index: number, hasClosedOnServer: boolean = false): void {
      if(this.sheets && index >= 0 && index <= this.sheets.length) {
         this.uiContextService.sheetClose(this.sheets[index].runtimeId);

         if(!!this.sheets[index]) {
            this.sheets[index].closedOnServer = hasClosedOnServer;
         }

         this.onSheetClosed(this.sheets[index]);
      }
   }

   copySheet(sheet: Sheet): void {
      this.clipboardService.addToClipboard(sheet, false);
   }

   cutSheet(sheet: Sheet): void {
      this.clipboardService.addToClipboard(sheet, true);
   }

   checkRemovedAssembly(assemblyName: string): void {
      this.clipboardService.checkRemovedAssembly(assemblyName);
   }

   checkRenamedAssembly(nameChange: string[]): void {
      this.clipboardService.checkRenamedAssembly(nameChange[0], nameChange[1]);
   }

   onTabSelected(tab: ComposerTabModel): void {
      if(this.isSheet(tab.asset)) {
         this.scriptDisabled = true;
         this.regionsDisabled = true;
         this.onSheetSelected(<Sheet>tab.asset);
      }
      else {
         this.onFocusedLibraryAssetChanged(<LibraryAsset>tab.asset, true);
      }
   }

   onSheetSelected(sheet: Sheet): void {
      if(sheet && sheet.type === "viewsheet") {
         const viewsheet: Viewsheet = sheet as Viewsheet;

         if(!viewsheet.preview && !viewsheet.linkview) {
            //Bug #16023 make sure to refresh toolbox data source tree before
            // changing tab if it is not a preview tab
            const evt = new RefreshBindingTreeEvent(null);
            viewsheet.socketConnection.sendEvent("/events/vs/bindingtree/gettreemodel", evt);

            // refresh if there are any shared filters
            const refreshEvent = new VSRefreshEvent();
            refreshEvent.setCheckShareFilter(true);
            viewsheet.socketConnection.sendEvent("/events/vs/refresh", refreshEvent);
         }
      }

      this.navigateToExistingSheet(sheet);
   }

   onTabClosed(tab: ComposerTabModel, forceClose: boolean = false): void {
      if(this.isSheet(tab.asset)) {
         this.onSheetClosed(<Sheet>tab.asset, forceClose);
      }
      else {
         this.onLibClosed(<LibraryAsset>tab.asset);
      }
   }

   private onLibClosed(library: LibraryAsset) {
      const message = "_#(js:common.saveUnsavedChanges)";

      if(!library.isModified) {
         this.closeLibTab(library);
      }
      else {
         ComponentTool.showMessageDialog(this.modalService, CONFIRM_MESSAGE.title, message,
            CONFIRM_MESSAGE.optionsWithCancel)
            .then((buttonClicked) => {
               switch(buttonClicked) {
                  case "yes":
                     this.saveAndCloseLib(library);
                     break;
                  case "no":
                     this.closeLibTab(library);
                     break;
                  default:
                     break;
               }
            });
      }
   }

   onSheetClosed(sheet: Sheet, forceClose: boolean = false): void {
      if(sheet.type === "worksheet" && (sheet as Worksheet).closeProhibited) {
         ComponentTool.showMessageDialog(this.modalService, "Warn",
            "_#(js:viewer.worksheet.closeWhileImporting)",
            { "ok": "OK" }, { backdrop: false })
            .then(() => { }, () => { });
         return;
      }

      if(sheet.isModified() && !forceClose) {
         this.showCloseSheetConfirmMessage(sheet);
      }
      else {
         this.closeSheet(sheet);

         if(sheet.type == "worksheet" && sheet.gettingStarted) {
            this.gettingStartedService.continue(StepIndex.CREATE_DASHBOARD);
         }
         else if(sheet.type == "viewsheet" && sheet.gettingStarted) {
            if(sheet.label.startsWith("Untitled-")) {
               this.gettingStartedService.continue(StepIndex.CREATE_DASHBOARD);
            }
         }
         // return to portal
         else if(this.sheets.length == 0) {
            if(this.closeOnComplete) {
               this.closed.emit(sheet.id);
            }
         }
      }
   }

   onLibraryClosed(library: LibraryAsset): void {

   }

   onToggleSnapToGrid(): void {
      this.snapToGrid = !this.snapToGrid;
      LocalStorage.setItem("snap-to-grid", this.snapToGrid + "");
   }

   onToggleSnapToObjects(): void {
      this.snapToObjects = !this.snapToObjects;
      LocalStorage.setItem("snap-to-objects", this.snapToObjects + "");
   }

   openViewsheetOptionDialog(viewsheet: Viewsheet, size: Dimension = null, script: boolean = false): void {
      this.getScriptPane(viewsheet);
      const modelUri: string = "../api/" + VIEWSHEET_PROPERTY_URI + "/" +
         Tool.byteEncode(viewsheet.runtimeId);
      this.modelService.getModel<ViewsheetPropertyDialogModel>(modelUri).subscribe((data) => {
         this.viewsheetPropertyModel = data;
         this.viewsheetPropertyModel.id = viewsheet.runtimeId;
         this.openToScript = script;

         if(this.propertyDialogModal) {
            this.propertyDialogModal.close();
         }

         this.propertyDialogModal = this.modalService.open(this.viewsheetPropertyDialog,
            { backdrop: "static", windowClass: "property-dialog-window" });
         this.propertyDialogModal.result.then(
            (result: ViewsheetPropertyDialogModel) => {
               if(!result) {
                  return;
               }

               this.viewsheetPropertyModel = null;
               this.scriptTreeModel = null;
               result.preview = viewsheet.preview;

               if(viewsheet.preview && size != null) {
                  result.width = size.width;
                  result.height = size.height;
               }

               const eventUri: string = "/events/" + VIEWSHEET_PROPERTY_URI;
               viewsheet.socketConnection.sendEvent(eventUri, result);
               viewsheet.label = result.vsOptionsPane.alias ?
                  result.vsOptionsPane.alias : viewsheet.label;
            },
            () => {
               this.viewsheetPropertyModel = null;
               this.scriptTreeModel = null;
            }
         );
      });
   }

   openScriptOptions() {
      if(this.focusedTab?.type == "script" && this.focusedTab?.asset) {
         let libraryAsset = <LibraryAsset>this.focusedTab?.asset;
         let dialog = ComponentTool.showDialog(this.modalService,
            ScriptPropertyDialogComponent, (val: { comment: string; size: number }) => {
               if(libraryAsset.comment != val.comment) {
                  libraryAsset.comment = val.comment;
                  libraryAsset.isModified = true;
               }

               this.scriptFontSize = val.size;
               this.viewChecked = true;
            });

         dialog.comment = (<LibraryAsset>this.focusedTab?.asset).comment;
         dialog.fontSize = this.scriptFontSize;
      }
   }

   getScriptPane(viewsheet: Viewsheet): void {
      this.scriptTreeModel = loadingScriptTreeModel;
      const scriptUri: string = "../api/vsscriptable/scriptTree";
      const params = new HttpParams().set("vsId", viewsheet.runtimeId)
         .set("isVSOption", "true");
      this.modelService.getModel(scriptUri, params).subscribe(data => this.scriptTreeModel = data);
   }

   getScriptTreePane(): void {
      this.scriptTreePaneModel = loadingScriptTreePaneModel;
      const scriptUri: string = "../api/script/scriptTree";
      this.modelService.getModel(scriptUri).subscribe(data => this.scriptTreePaneModel = data);
   }
   private showCloseSheetConfirmMessage(sheet: Sheet): void {
      const message = "_#(js:common.saveUnsavedChanges)";

      if(sheet.annotationChanged) {
         ComponentTool.showAnnotationChangedDialog(this.modalService).then((value) => {
            if(value) {
               this.closeSheet(sheet);
            }
         });
      }
      else {
         ComponentTool.showMessageDialog(this.modalService, CONFIRM_MESSAGE.title, message,
            CONFIRM_MESSAGE.optionsWithCancel)
            .then((buttonClicked) => {
               switch(buttonClicked) {
                  case "yes":
                     this.saveAndClose(sheet);
                     break;
                  case "no":
                     this.closeSheet(sheet, true);

                     if(sheet.type == "worksheet" && sheet.gettingStarted) {
                        this.gettingStartedService.continue(
                           this.gettingStartedService.getWorksheetId() ? StepIndex.CREATE_DASHBOARD : null);
                     }

                     break;
                  default:
                     break;
               }
            });
      }
   }

   public dependencyChange(sheet: Viewsheet, wizard: boolean): void {
      if(wizard) {
         this.saveOnly(sheet);
         return;
      }

      const message = "_#(js:common.saveChangedDependency)";

      ComponentTool.showMessageDialog(this.modalService, CONFIRM_MESSAGE.title, message,
         CONFIRM_MESSAGE.optionsWithCancel)
         .then((buttonClicked) => {
            switch(buttonClicked) {
               case "yes":
                  this.saveOnly(sheet);
                  break;
               default:
                  break;
            }
         });
   }

   private saveOnly(sheet: Sheet): void {
      if(sheet.type === "viewsheet") {
         if(sheet.newSheet) {
            this.saveViewsheetAs(<Viewsheet>sheet, false);
         }
         else {
            this.saveViewsheet(<Viewsheet>sheet, false);
         }
      }
   }

   private saveAndClose(sheet: Sheet): void {
      if(sheet.type === "viewsheet") {
         if(sheet.newSheet) {
            this.saveViewsheetAs(<Viewsheet>sheet, true);
         }
         else {
            this.saveViewsheet(<Viewsheet>sheet, true);
         }
      }
      else if(sheet.type === "worksheet") {
         if(sheet.newSheet) {
            this.saveWorksheetAs(sheet as Worksheet, true);
         }
         else {
            this.saveWorksheet(sheet as Worksheet, true);
         }
      }
   }

   private saveAndCloseLib(library: LibraryAsset): void {
      if(library.newAsset) {
         if(library.type === "tableStyle") {
            this.saveTableStyleAs(<TableStyleModel>library, true);
         }
         else if(library.type === "script") {
            this.saveScriptAs(<ScriptModel>library, true);
         }
      }
      else {
         if(library.type === "tableStyle") {
            this.saveTableStyle(<TableStyleModel>library);
            this.closeLibTab(library);
         }
         else if(library.type === "script") {
            this.saveScript(<ScriptModel>library, true);
         }
      }
   }

   private closeLibTab(library: LibraryAsset) {
      let tabIndex = this.openedTabs.findIndex(t => {
         if(this.isSheet(t.asset)) {
            return false;
         }

         return t.asset.id == library.id;
      });

      if(tabIndex >= 0) {
         this.openedTabs.splice(tabIndex, 1);
      }

      if(this.openedTabs.length == 0) {
         this.focusedTab = null;
         this.scriptDisabled = true;
         this.regionsDisabled = true;
      }

      let focusedId = this.openedTabs.findIndex(t => {
         return t.asset.id == this.focusedTab.asset.id;
      });

      if(focusedId < 0) {
         if(tabIndex < this.openedTabs.length) {
            this.focusedTab = this.openedTabs[tabIndex];
         }
         else {
            this.focusedTab = this.openedTabs[this.openedTabs.length - 1];
         }
      }

      if(!this.focusedTab) {
         this.onFocusedSheetChanged(null);
         this.onFocusedLibraryAssetChanged(null);
      }
      else if(this.isSheet(this.focusedTab.asset)) {
         this.regionsDisabled = true;
         this.scriptDisabled = true;
         this.onFocusedSheetChanged(this.focusedSheet, true);
      }
      else {
         this.onFocusedLibraryAssetChanged(<LibraryAsset>this.focusedTab.asset, true);
      }
   }

   private closeSheet(sheet: Sheet, deleteAutosave: boolean = false) {
      const index: number = this.getIndexOfSheet(sheet);
      const tabIndex: number = this.getIndexOfTab(sheet);

      if(index >= 0) {
         let focusedSheetIndex: number;
         let focusedTabIndex: number;
         this.sheets.splice(index, 1);
         this.openedTabs.splice(tabIndex, 1);

         if(sheet instanceof Viewsheet && (<Viewsheet>sheet).preview) {
            focusedSheetIndex = this.getIndexOfSheet((<Viewsheet>sheet).parentSheet);
         }

         if(focusedSheetIndex != undefined && focusedSheetIndex !== -1) {
            // shouldn't need to refresh vs after preview since the viewsheet
            // has not changed. it could cause parameters to be re-prompted (#22303)
            // this.refreshSelectViewsheet((<Viewsheet>(<Viewsheet> sheet).parentSheet));
            // select parent sheet of previewed vs
         }
         else if(this.focusedSheet && this.focusedSheet.localId === sheet.localId ||
            (this.focusedTab && (this.focusedTab.type == "tableStyle" || this.focusedTab.type == "script"))) {
            if(this.openedTabs.length > 0) {
               if(tabIndex >= this.openedTabs.length) {
                  focusedTabIndex = this.openedTabs.length - 1;
               }
               else {
                  focusedTabIndex = tabIndex;
               }
            }
            else {
               focusedTabIndex = undefined;
               focusedSheetIndex = undefined;
            }
         }
         else {
            focusedSheetIndex = this.getIndexOfSheet(this.focusedSheet);
         }

         if(focusedSheetIndex >= 0) {
            this.navigateToExisting(focusedSheetIndex);
            this.onSheetSelected(this.focusedSheet);
         }
         else if(focusedTabIndex >= 0) {
            this.navigateToExistingTab(this.openedTabs[focusedTabIndex]);
         }
         else {
            this.onFocusedSheetChanged(null);
         }

         if(sheet.closeOnServer && !sheet.closedOnServer) {
            ComposerMainComponent.closeSheetOnServer(sheet, deleteAutosave);
         }
      }

      this.clipboardService.sheetClosed(sheet.runtimeId);
      this.uiContextService.sheetClose(sheet.runtimeId);
   }

   onSheetReload(sheet: Sheet, forceClose: boolean = false) {
      this.onSheetClosed(sheet, forceClose);
      this.openSheet({ type: sheet.type, assetId: sheet.id });
   }

   // When get rename finish info, should confirm to user if want to sync data and reload. If yes,
   // save viewsheet/ sync data/reload viewsheet. Else do nothing.
   onSaveViewsheet(sheet: Viewsheet) {
      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Dependencies Changed)",
         "_#(js:Update asset dependencies)" + "_*" + sheet.label, CONFIRM_MESSAGE.options)
         .then((result) => {
            if(result === "yes") {
               this.saveViewsheet0(sheet, false, true);
            }
         })
         .catch(() => { });
   }

   onTransformFinished(sheet: Sheet) {
      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Dependencies Changed)",
         "_#(js:composer.sheet.transformFinished)" + "_*" + sheet.label, CONFIRM_MESSAGE.options)
         .then((result) => {
            if(result === "yes") {
               this.onSheetReload(sheet, true);
            }
         })
         .catch(() => { });
   }

   onSaveWorksheet(event: { worksheet: Worksheet, close: boolean, updateDep: boolean }) {
      if(event.updateDep) {
         ComponentTool.showConfirmDialog(this.modalService, "_#(js:Dependencies Changed)",
            "_#(js:Update asset dependencies)" + "_*" + event.worksheet.label, CONFIRM_MESSAGE.options)
            .then((result) => {
               if(result === "yes") {
                  this.saveWorksheet0(event.worksheet, false, true);
               }
            })
            .catch(() => { });
      }
      else {
         if(event.worksheet.newSheet) {
            this.saveWorksheetAs(event.worksheet, event.close);
         }
         else {
            this.saveWorksheet(event.worksheet, event.close);
         }
      }
   }

   setGrayedOutFields(refs: DataRef[]) {
      this.grayedOutFields = refs;
   }

   copyAssembly(model: VSObjectModel): void {
      this.clipboardService.addToClipboard(this.focusedSheet, false);
   }

   cutAssembly(model: VSObjectModel): void {
      this.clipboardService.addToClipboard(this.focusedSheet, true);
   }

   pasteObjects(sheet: Sheet, pos?: Point): void {
      let left = this.lastClick.x + this.vsScroll.x;
      let top = this.lastClick.y + this.vsScroll.y;

      if(this.snapToGrid) {
         if(pos) {
            pos = new Point(Math.round(pos.x / 20) * 20, Math.round(pos.y / 20) * 20);
         }
         else {
            left = Math.round(left / 20) * 20;
            top = Math.round(top / 20) * 20;
         }
      }

      this.clipboardService.pasteObjects(sheet, pos ? pos : new Point(left, top));
   }

   pasteWithCutFinish(sourceId: string, cutAssemblies: string[]): void {
      let sheet = this.sheets.find(sh => sh.runtimeId == sourceId);

      if(!sheet) {
         return;
      }

      this.clipboardService.pasteWithCutFinish(sheet, cutAssemblies);
   }

   /**
    * Remove focused assemblies on the viewsheet.
    * @param model   the object that triggered the remove action
    */
   removeAssembly(model: VSObjectModel): void {
      let vs: Viewsheet = <Viewsheet>this.focusedSheet;
      let objectNames: string[] = [];
      let toStay: string[] = [];

      vs.currentFocusedAssemblies.forEach(assembly => {
         if(assembly.container) {
            let container: VSObjectModel = vs.vsObjects.find(
               (vso) => vso.absoluteName === assembly.container);

            if(container) {
               // remove entire group if a grouped element is selected
               if(container.objectType === "VSGroupContainer") {
                  objectNames.push(container.absoluteName);
               }
               // same logic as explained in composer-object.service.ts handleKeyEvent(e)
               else if(container.objectType === "VSTab") {
                  const tab = <VSTabModel>container;

                  if(vs.isAssemblyFocused(tab.absoluteName)) {
                     let allChildrenSelected: boolean = true;

                     for(let name of tab.childrenNames) {
                        if(!vs.isAssemblyFocused(name)) {
                           allChildrenSelected = false;
                           break;
                        }
                     }

                     if(allChildrenSelected || tab.selected === assembly.absoluteName) {
                        objectNames.push(assembly.absoluteName);
                        toStay.push(tab.absoluteName);
                     }
                  }
                  else {
                     objectNames.push(assembly.absoluteName);
                  }
               }
               // if selection container child is selected and remove action originates from
               // a child of this container, only remove the child that initialized the action
               // and keep all other children/container. Logic is consistent with 12.2 functionality
               else if(container.objectType === "VSSelectionContainer") {
                  if(model.container === container.absoluteName) {
                     objectNames.push(model.absoluteName);
                     toStay.push(container.absoluteName);
                  }
                  else {
                     // remove entire container if remove action did not start from a child
                     // of this container
                     objectNames.push(container.absoluteName);
                  }
               }
            }
         }
         else {
            objectNames.push(assembly.absoluteName);
         }
      });

      objectNames = Tool.uniq(objectNames);
      toStay = Tool.uniq(toStay);

      toStay.forEach((name) => {
         let selectedIndex: number = objectNames.indexOf(name);

         if(selectedIndex != -1) {
            objectNames.splice(selectedIndex, 1);
         }
      });

      vs.clearFocusedAssemblies();
      this.composerObjectService.removeObjects(vs, objectNames);
   }

   bringAssemblyToFront(model: VSObjectModel): void {
      this.composerObjectService
         .sendToFarthestIndex(<Viewsheet>this.focusedSheet, model, true);
   }

   bringAssemblyForward(model: VSObjectModel): void {
      this.composerObjectService
         .shiftLayerIndex(<Viewsheet>this.focusedSheet, model, true);
   }

   sendAssemblyToBack(model: VSObjectModel): void {
      this.composerObjectService
         .sendToFarthestIndex(<Viewsheet>this.focusedSheet, model, false);
   }

   sendAssemblyBackward(model: VSObjectModel): void {
      this.composerObjectService
         .shiftLayerIndex(<Viewsheet>this.focusedSheet, model, false);
   }

   onLayoutObjectChange() {
      if(this.componentsPane) {
         this.componentsPane.updateRoots();
      }
   }

   openNewWorksheet(gettingStarted?: boolean): void {
      const ws: Worksheet = new Worksheet();

      ws.localId = sheetCounter++;
      ws.newSheet = true;
      ws.gettingStarted = gettingStarted;
      const index = this.sheets.push(ws) - 1;
      this.openedTabs.push(new ComposerTabModel(ws.type, ws));
      this.navigateToExisting(index);
   }

   onNewTableStyle(): void {
      this.http.get<TableStyleModel>(URI_NEW_TABLESTYLE).subscribe((tableStyle) => {
         tableStyle.isModified = false;
         tableStyle.type = "tableStyle";
         tableStyle.newAsset = true;
         tableStyle.selectedRegion = TableStyleUtil.BODY;
         tableStyle.styleFormat.origianlIndex = 0;
         TableStyleUtil.addUndoList(tableStyle);
         this.scriptDisabled = true;
         this.toolboxDisabled = true;
         this.openedTabs.push(new ComposerTabModel(tableStyle.type, tableStyle));
         this.onFocusedLibraryAssetChanged(tableStyle);
         TableStyleUtil.initRegionsTree(tableStyle);
         TableStyleUtil.selectRegionTree(tableStyle);
      });
   }

   getCustomPatternsTree(tableStyle: TableStyleModel, id: number) {
      return this.http.post<SpecificationModel[]>(URI_GET_TABLESTYLE_SPECMODEL, tableStyle)
         .subscribe((specs => {
            tableStyle.selectedRegion = id.toString();
            tableStyle.styleFormat.specList = specs;
            TableStyleUtil.updateCustomLabel(tableStyle);
            TableStyleUtil.initRegionsTree(tableStyle);
            TableStyleUtil.addUndoList(this.asTableStyle(this.focusedTab));
            this.updateTableStylePreview();
         }));
   }

   updateTableStyle() {
      TableStyleUtil.addUndoList(this.asTableStyle(this.focusedTab));
      this.updateTableStylePreview(false);
   }

   updateTableStylePreview(refreshTree: boolean = true) {
      let style: TableStyleModel = this.asTableStyle(this.focusedTab);
      let styleFormat = style.styleFormat;
      const params: HttpParams = new HttpParams().set("styleId", style.styleId);

      if(refreshTree) {
         style.regionsTreeRoot.children[1] = TableStyleUtil.createCustomNode(style);

         if(!TableStyleUtil.isDefaultRegion(style.selectedRegion) &&
            !styleFormat.specList[parseInt(style.selectedRegion, 10)]) {
            style.selectedRegion = TableStyleUtil.BODY;
         }

         TableStyleUtil.selectRegionTree(style);
      }

      this.http.post<CSSTableStyleModel>(URI_GET_CSS_TABLESTYLE_FORMAT, style.styleFormat, { params })
         .subscribe((model) => {
            this.asTableStyle(this.focusedTab).cssStyleFormat = model;
            this.changeDetectorRef.detectChanges();
         });
   }

   openNewScriptAsset() {
      this.http.get<ScriptModel>(URI_NEW_SCRIPT).subscribe((script) => {
         script.type = "script";
         script.newAsset = true;
         script.isModified = false;
         this.regionsDisabled = true;
         this.openedTabs.push(new ComposerTabModel(script.type, script));
         this.onFocusedLibraryAssetChanged(script);
      });

      this.getScriptTreePane();
   }

   openNewWsWithWizard(gettingStarted?: boolean): void {
      this.openNewWorksheet(gettingStarted);

      if(!!this.focusedSheetAsWorksheet) {
         this.focusedSheetAsWorksheet.callBackFun = () => {
            if(gettingStarted && this.gettingStartedService.isProcessing() &&
               (this.gettingStartedService.isUploadFile() || this.gettingStartedService.isCreateQuery())) {
               this.createQueryOrUploadTable();
            }
            else {
               this.toolbar.openWorksheetWizard(this.wsWizard, !this.saveToFolderId,
                  this.baseDataSource, this.baseDataSourceType);
            }
         };
      }
   }

   private createQueryOrUploadTable(): void {
      const newQuery = this.baseDataSourceType == WSObjectType.TABULAR ||
         this.baseDataSourceType == WSObjectType.DATABASE_QUERY;

      if(newQuery) {
         this.toolbar.openWorksheetWizard(this.wsWizard, !this.saveToFolderId,
            this.baseDataSource, this.baseDataSourceType,
            (model) => {
               if(!model?.mashUpData) {
                  this.toolbar.save(true);
               }
            },
            () => this.closeTestDriveWorksheet(this.toolbar.sheet));
      }
      else {
         this.newUploadTable();
      }
   }

   openNewQuery(entry: AssetEntry) {
      if(entry.properties["datasource.type"] == "jdbc") {
         this.toolbar.createObject({
            objectType: WSObjectType.DATABASE_QUERY,
            dataSource: entry.properties["source"]
         }, false);
      }
      else {
         this.toolbar.createObject({
            objectType: WSObjectType.TABULAR,
            tabularType: {
               name: entry.properties["datasource.type"],
               label: entry.properties["prefix"],
               dataSource: entry.properties["source"],
               exists: true
            }
         }, false);
      }
   }

   // open upload dialog when ready
   private newUploadTable() {
      if(this.focusedSheet && this.focusedSheet.runtimeId) {
         setTimeout(() => {
            this.toolbar.newUploadTable(true, (model) => {
               if(!model?.mashUpData) {
                  this.toolbar.save(true);
               }
            },
               () => this.closeTestDriveWorksheet(this.toolbar.sheet));
         });
      }
      else {
         setTimeout(() => this.newUploadTable(), 100);
      }
   }

   openAutoSaveAsset(autoSaveFile: string): void {
      let attr = autoSaveFile.split("^");
      let isWS = attr[1] == "WORKSHEET";

      if(isWS) {
         const ws: Worksheet = new Worksheet();
         ws.localId = sheetCounter++;
         ws.newSheet = true;
         ws.label = "";
         ws.autoSaveFile = autoSaveFile;

         // The id of auto save file is scope + type + user + name
         if(attr.length > 3) {
            ws.id = attr[0] + "^2^" + attr[2] + "^" + attr[3];
         }

         const index = this.sheets.push(ws) - 1;
         this.openedTabs.push(new ComposerTabModel(ws.type, ws));
         this.navigateToExisting(index);
      }
      else {
         const vs: Viewsheet = new Viewsheet();
         vs.localId = sheetCounter++;
         vs.newSheet = true;
         vs.autoSaveFile = autoSaveFile;
         vs.label = "";

         // The id of auto save file is scope + type + user + name
         if(attr.length > 3) {
            vs.id = attr[0] + "^128^" + attr[2] + "^" + attr[3];
         }

         const index: number = this.sheets.push(vs) - 1;
         this.openedTabs.push(new ComposerTabModel(vs.type, vs));
         this.navigateToExisting(index);
      }
   }

   openNewViewsheet(baseEntry: AssetEntry, gettingStarted?: boolean): void {
      if(baseEntry && baseEntry.folder && baseEntry.type === AssetType.FOLDER) {
         baseEntry = null;
      }

      const vs: Viewsheet = new Viewsheet();
      vs.localId = sheetCounter++;
      vs.newSheet = true;
      vs.label = "";
      vs.id = "";
      vs.baseEntry = baseEntry;
      vs.gettingStarted = gettingStarted;

      const index: number = this.sheets.push(vs) - 1;
      this.openedTabs.push(new ComposerTabModel(vs.type, vs));
      this.navigateToExisting(index);
   }

   public openWorksheet(assetId: string, gettingStarted?: boolean) {
      this.openSheet({ type: "worksheet", assetId}, false,
         null, false, gettingStarted);
   }

   public openViewsheet(assetId: string, embedded: boolean = false,
      runtimeId: string = null, newSheet = false, gettingStarted?: boolean) {
      this.openSheet({ type: "viewsheet", assetId }, embedded, runtimeId, newSheet, gettingStarted);
   }

   public openSheet(event: OpenSheetEvent, embedded: boolean = false,
      runtimeId: string = null, newSheet = false, gettingStarted?: boolean): void {
      const type = event.type;
      const id = event.assetId;
      let index = this.sheets.findIndex((sheet) =>
         sheet.id === id && sheet.runtimeId.indexOf(AssetConstants.PREVIEW_VIEWSHEET) == -1);
      this.scriptDisabled = true;

      if(this.focusedSheet && this.focusedSheet.id === id && index >= 0) {
         return;
      }

      if(index < 0) {
         switch(type) {
            case "worksheet":
               const ws = new Worksheet();
               ws.id = id;
               ws.localId = sheetCounter++;
               ws.newSheet = newSheet;
               ws.gettingStarted = gettingStarted;

               index = this.sheets.push(ws) - 1;
               this.openedTabs.push(new ComposerTabModel(ws.type, ws));

               if(gettingStarted && this.gettingStartedService.isEditWs() &&
                  !this.gettingStartedService.isCustomizeData()) {
                  ws.callBackFun = () => {
                     if(!ws.gettingStarted || !this.gettingStartedService.isEditWs()) {
                        return;
                     }

                     if(!this.gettingStartedService.isCustomizeData() &&
                        !this.gettingStartedService.isStartFromScratch() &&
                        (!this.gettingStartedService.isCreateQuery() || !ws.singleQuery)) {
                        this.createQueryOrUploadTable();
                     }
                  };
               }
               break;
            case "viewsheet":
               const vs = new Viewsheet();
               vs.localId = sheetCounter++;
               vs.label = "";
               vs.id = id;
               vs.newSheet = newSheet;
               vs.runtimeId = runtimeId;
               vs.meta = event.meta;
               vs.closeOnServer = !runtimeId;
               // if opening an embedded vs from parent, need runtime id of the
               // parent(current focused vs)
               vs.embeddedId = embedded && this.focusedSheet ? this.focusedSheet.runtimeId : null;
               vs.gettingStarted = gettingStarted;
               index = this.sheets.push(vs) - 1;
               this.regionsDisabled = true;
               this.openedTabs.push(new ComposerTabModel(vs.type, vs));
               break;
            default:
               // should not happen
               console.error(`invalid type: ${type}`);
         }
      }

      this.navigateToExisting(index);
   }

   public openLibraryAsset(event: OpenLibraryAssetEvent): void {
      const type = event.type;
      const id: string = event.assetId;
      let params: HttpParams = new HttpParams().set("id", id);

      if(type == "tableStyle") {
         params = params.set("styleId", event.styleId);
      }

      let index = this.openedTabs.findIndex((tab) =>
         tab.type === type && tab.asset.id === id);

      if(this.focusedTab && event.assetId == this.focusedTab.asset.id
         && this.focusedTab.type === type && index >= 0) {
         return;
      }

      if(index < 0) {
         switch(type) {
            case "script":
               this.http.get<ScriptModel>(URI_OPEN_SCRIPT, { params }).subscribe((script) => {
                  script.isModified = false;
                  this.openedTabs.push(new ComposerTabModel(script.type, script));
                  script.newAsset = false;
                  this.getScriptTreePane();
                  this.onFocusedLibraryAssetChanged(script);
               });
               break;
            case "tableStyle":
               this.http.get<TableStyleModel>(URI_OPEN_TABLESTYLE, { params }).subscribe((tableStyle) => {
                  tableStyle.isModified = false;
                  tableStyle.type = event.type;
                  tableStyle.newAsset = false;
                  tableStyle.selectedRegion = TableStyleUtil.BODY;
                  tableStyle.styleFormat.origianlIndex = 0;
                  TableStyleUtil.addUndoList(tableStyle);
                  this.openedTabs.push(new ComposerTabModel(event.type, tableStyle));
                  this.onFocusedLibraryAssetChanged(tableStyle);
                  TableStyleUtil.initRegionsTree(tableStyle);
                  TableStyleUtil.selectRegionTree(tableStyle);
               });
               break;
         }
      }
      else {
         this.navigateToExistingTab(this.openedTabs[index]);
      }
   }

   saveWorksheet(sheet: Worksheet, close: boolean = false) {
      this.saveWorksheet0(sheet, close, false);
   }

   saveWorksheet0(worksheet: Worksheet, close: boolean = false, updateDep: boolean = false): void {
      let savePromise = this.checkCycle(worksheet);

      savePromise = savePromise.then(res => {
         if(res) {
            return this.confirmSaveWorksheetWithoutPrimaryAssembly(worksheet);
         }

         return false;
      });

      savePromise.then((confirmed) => {
         if(confirmed) {
            this.modelService.sendModel<SaveWSConfirmationModel>(CHECK_PRIMARY_ASSEMBLY_URI + Tool.byteEncode(worksheet.runtimeId),
               new SaveSheetEvent(false, close, false))
               .subscribe((res) => {
                  let promise = Promise.resolve(true);
                  let event: SaveSheetEvent = new SaveSheetEvent(false, close, false);
                  let saveConfirmation: SaveWSConfirmationModel = null;

                  if(res.body) {
                     saveConfirmation = res.body;
                  }

                  if(saveConfirmation && saveConfirmation.required) {
                     promise = promise.then(() => {
                        return ComponentTool.showConfirmDialog(this.modalService,
                           CONFIRM_MESSAGE.title,
                           `${saveConfirmation.confirmationMsg}.
                            _#(js:designer.composition.worksheetEngine.goOnAnyway)`,
                           CONFIRM_MESSAGE.options)
                           .then((buttonClicked) => {
                              event.forceSave = true;
                              return buttonClicked === "yes";
                           });
                     });
                  }

                  promise.then((proceed) => {
                     if(proceed) {
                        event.updateDepend = updateDep;

                        if(close) {
                           worksheet.socketConnection.sendEvent(
                              SAVE_AND_CLOSE_WORKSHEET_SOCKET_URI, event);
                        }
                        else {
                           worksheet.socketConnection.sendEvent(SAVE_WORKSHEET_SOCKET_URI, event);
                        }
                     }
                  });
               });
         }
      });
   }

   saveTableStyle(library: TableStyleModel) {
      let index = library.currentIndex;
      const entry = createAssetEntry(library.id);
      const params: HttpParams = new HttpParams()
         .set("styleName", library.styleName)
         .set("identifier", entry.identifier)

      this.http.get<SaveLibraryDialogModelValidator>("../api/composer/table-style/check-save-permission", { params })
         .subscribe((validator) => {
            if(!!validator.permissionDenied) {
               if(validator.permissionDenied.length > 0) {
                  ComponentTool.showMessageDialog(
                     this.modalService, "Error", validator.permissionDenied)
                     .then(() => { });
               }
            }
            else {
               this.http.post(URI_SAVE_TABLESTYLE, library).subscribe();
               this.stylePaneComponent.showNotifications();
               library.undoRedoList[index].origianlIndex = index;
               library.undoRedoList[0].origianlIndex = index != 0 ? -1 : 0;
               library.isModified = false;
            }
         });
   }

   saveTableStyleAs(library: TableStyleModel, isClose: boolean = false) {
      let index = library.currentIndex;
      this.saveTableStyleModel = new SaveTableStyleDialogModel(library.newAsset ? null : library.label);
      const entry = createAssetEntry(library.id);
      this.defaultFolder = entry ? AssetEntryHelper.getParent(entry) : null;

      this.modalService.open(this.saveTableStyleDialog, { backdrop: "static" }).result
         .then((result: SaveTableStyleDialogModel) => {
            for(let tab of this.openedTabs) {
               let parent = AssetEntryHelper.getParent(createAssetEntry(tab.asset.id));

               if(tab.type == "tableStyle" && result.name == tab.asset.label &&
                  parent?.path == createAssetEntry(result.identifier).path) {
                  ComponentTool.showConfirmDialog(this.modalService,
                     "_#(js:Warning)", "_#(js:common.overwriteForbidden)",
                     CONFIRM_MESSAGE.optionsOnlyOk).then(() => false);
                  return;
               }
            }

            const request = {
               tableStyleModel: library,
               saveModel: result
            };

            this.http.post<string>(URI_SAVE_AS_TABLESTYLE, request).subscribe((styleId) => {
               this.focusedTab.asset.isModified = false;
               this.focusedTab.asset.label = result.name;
               this.focusedTab.asset.id = TableStyleUtil.styleIdentifier(result.folder, result.name);
               library.newAsset = false;
               library.styleId = styleId;
               library.undoRedoList[index].origianlIndex = index;
               library.undoRedoList[0].origianlIndex = index != 0 ? -1 : 0;
               setTimeout(() => { this.stylePaneComponent.showNotifications(); }, 0);
            });

            if(isClose) {
               this.closeLibTab(library);
            }
         });
   }

   onOpenCustomEdit(event: any) {
      this.spec = event.model;
      let tableStyle = this.asTableStyle(this.focusedTab);

      this.modalService.open(this.editCustomPatternDialog, { backdrop: "static" }).result
         .then((result) => {
            if(event.new) {
               this.asTableStyle(this.focusedTab).styleFormat.specList.push(event.model);
            }

            this.getCustomPatternsTree(tableStyle, this.spec.id);
            this.focusedTab.asset.isModified = true;
         },
            () => {
               this.focusedTab.asset.isModified = false;
            });
   }


   saveScript(script: ScriptModel, close: boolean = false) {
      const entry = createAssetEntry(script.id);

      const saveScriptModel = {
         name: script.label,
         identifier: entry.identifier
      };

      this.http.post<SaveScriptDialogValidator>(SAVE_SCRIPT_DIALOG_VALIDATION_URI,
         saveScriptModel).subscribe((validator) => {

            if(!!validator.permissionDenied) {
               if(validator.permissionDenied.length > 0) {
                  ComponentTool.showMessageDialog(
                     this.modalService, "Error", validator.permissionDenied)
                     .then(() => {
                     });
               }
            }
            else {
               this.http.post(URI_SAVE_SCRIPT, script).subscribe((data) => {
                  if(data != "" && data.toString().length > 0) {
                     ComponentTool.showMessageDialog(
                        this.modalService, "_#(js:Error)", data.toString());
                  }
                  else {
                     this.focusedTab.asset.isModified = false;
                     this.scriptEditPaneComponent.showNotifications();

                     if(close) {
                        this.closeLibTab(script);
                     }
                  }
               });
            }
         });

      this.originalText = script.text;
   }

   saveScriptAs(script: ScriptModel, close: boolean = false) {
      this.saveScriptModel = new SaveScriptDialogModel(script.label);
      const entry = createAssetEntry(script.id);
      this.defaultFolder = entry ? AssetEntryHelper.getParent(entry) : null;

      this.modalService.open(this.saveScriptDialog, { backdrop: "static" }).result
         .then((result) => {
            for(let tab of this.openedTabs) {
               if(tab.type == "script" && result.name == tab.asset.label && result.name != this.focusedTab.asset.label) {
                  ComponentTool.showConfirmDialog(this.modalService,
                     "_#(js:Warning)", "_#(js:common.overwriteForbidden)",
                     CONFIRM_MESSAGE.optionsOnlyOk).then(() => false);
                  return;
               }
            }

            const scriptRequestModel = {
               scriptModel: script,
               saveModel: result,
            };

            if(script.newAsset) {
               script.newAsset = false;
            }

            this.http.post(URI_SAVE_AS_SCRIPT, scriptRequestModel).subscribe((scriptId) => {
               script.isModified = false;
               this.focusedTab.asset.id = scriptId.toString();
               this.focusedTab.asset.label = result.name;
               setTimeout(() => { this.scriptEditPaneComponent.showNotifications(); }, 0);
            });

            if(close) {
               this.closeLibTab(script);
            }
         });

   }

   saveViewsheet(sheet: Viewsheet, close: boolean = false) {
      this.saveViewsheet0(sheet, close, false);
   }

   saveViewsheet0(sheet: Viewsheet, close: boolean = false, updateDep: boolean = false): void {
      let event: SaveSheetEvent = new SaveSheetEvent(false, close);
      event.updateDepend = updateDep;

      if(close) {
         sheet.socketConnection.sendEvent(SAVE_AND_CLOSE_VIEWSHEET_SOCKET_URI, event);
      }
      else {
         sheet.socketConnection.sendEvent(SAVE_VIEWSHEET_SOCKET_URI, event);
      }

      sheet.onSave();
   }

   saveWorksheetAs(sheet: Worksheet, close: boolean = false): void {
      let para = new HttpParams().set("rid", sheet.runtimeId);

      this.modelService.getModel(WORKSHEET_CHECK_DEPEND_CHANGED_URI, para).subscribe(data => {
         if(data) {
            ComponentTool.showConfirmDialog(this.modalService, "_#(js:Dependencies Changed)",
               "_#(js:Update dependencies)", CONFIRM_MESSAGE.options)
               .then((result) => {
                  this.saveWorksheetAs0(sheet, close, result === "yes");
               });
         }
         else {
            this.saveWorksheetAs0(sheet, close, false);
         }
      });
   }

   saveWorksheetAs0(worksheet: Worksheet, close: boolean = false, updateDep?: boolean): void {
      const primaryAssemblyPromise = this.wsWizard ? Promise.resolve(true) :
         this.confirmSaveWorksheetWithoutPrimaryAssembly(worksheet);

      primaryAssemblyPromise.then((canSave) => {
         if(canSave) {
            const entry = createAssetEntry(worksheet.id);
            this.defaultFolder = AssetEntryHelper.getParent(entry);

            this.modalService
               .open(this.saveWorksheetDialog, { backdrop: "static" }).result
               .then((result) => {
                  result.updateDep = updateDep;
                  this.processSaveWorksheet(worksheet, close, result);
               },
                  () => { this.closeTestDriveWorksheet(worksheet); });
         }
         else {
            this.closeTestDriveWorksheet(worksheet);
         }
      });
   }

   private processSaveWorksheet(worksheet: Worksheet, close: boolean,
      dialogModel: SaveWorksheetDialogModel) {
      // wait for import to complete before closing composer
      // otherwise the ws saved will not be correct
      if(this.focusedSheet && this.focusedSheet.loading) {
         this.focusedSheet.loadingSubject.subscribe((value) => {
            if(!value) {
               this.processSaveWorksheet(worksheet, close, dialogModel);
            }
         });
         return;
      }

      this.finishSave(worksheet, close || this.closeOnComplete, dialogModel);
   }

   private finishSave(worksheet: Worksheet, close: boolean, dialogModel: SaveWorksheetDialogModel) {
      if(close) {
         worksheet.socketConnection.sendEvent(
            SAVE_WORKSHEET_DIALOG_AND_CLOSE_SOCKET_URI, dialogModel);
      }
      else {
         worksheet.socketConnection.sendEvent(
            SAVE_WORKSHEET_DIALOG_SOCKET_URI, dialogModel);
      }

      this.lastWS = this.createWorksheetIdentifier(dialogModel.assetRepositoryPaneModel);
   }

   private closeTestDriveWorksheet(worksheet: Sheet) {
      let gettingStartedEdit = worksheet.type == "worksheet" && (<Worksheet>worksheet).gettingStarted &&
         this.gettingStartedService.isEditWs();

      if(gettingStartedEdit) {
         this.closeSheet(worksheet);

         if(this.sheets.length == 0) {
            this.closed.emit(null);
         }

         if(gettingStartedEdit) {
            this.gettingStartedService.continue();
         }
      }
   }

   toggleImportDialog(opened: boolean) {
      this.importDialogOpen = opened;
   }

   saveViewsheetAs(sheet: Viewsheet, close: boolean = null) {
      let para = new HttpParams().set("rid", sheet.runtimeId);

      this.modelService.getModel(VIEWSHEET_CHECK_DEPEND_CHANGED_URI, para).subscribe(data => {
         if(data) {
            ComponentTool.showConfirmDialog(this.modalService, "_#(js:Dependencies Changed)",
               "_#(js:Update dependencies)", CONFIRM_MESSAGE.options)
               .then((result) => {
                  this.saveViewsheetAs0(sheet, close, result === "yes");
               });
         }
         else {
            this.saveViewsheetAs0(sheet, close, false);
         }
      });
   }

   saveViewsheetAs0(sheet: Viewsheet, close: boolean = null, updataDep: boolean): void {
      const modelUri: string = SAVE_VIEWSHEET_DIALOG_REST_URI + Tool.byteEncode(sheet.runtimeId);

      if(close == null) {
         close = this.closeOnComplete ||
            sheet.gettingStarted && this.gettingStartedService.isCreateDashboard();
      }

      const entry = createAssetEntry(sheet.id);
      this.defaultFolder = entry ? AssetEntryHelper.getParent(entry) : null;

      sheet.onSave();
      this.modelService.getModel(modelUri).toPromise().then(
         (data: any) => {
            this.saveViewsheetModel = <SaveViewsheetDialogModel>data;
            this.modalService.open(this.saveViewsheetDialog, { backdrop: "static" }).result.then(
               (result: SaveViewsheetDialogModel) => {
                  result.updateDepend = updataDep;

                  if(sheet.gettingStarted) {
                     this.gettingStartedService.finish();
                  }

                  if(close) {
                     sheet.socketConnection.sendEvent(
                        SAVE_VIEWSHEET_DIALOG_AND_CLOSE_SOCKET_URI, result);
                  }
                  else {
                     sheet.socketConnection.sendEvent(
                        SAVE_VIEWSHEET_DIALOG_SOCKET_URI, result);
                  }

                  this.designSaved = true;
               }).
               catch(() => { });
         },
         (error: any) => {
            //TODO handle error
            console.error("Failed to load save viewsheet model: ", error);
         }
      );
   }

   /**
    * show hyperlink vs in vs-tab
    * @param {string} id vs assetId
    */
   private showLinkVSInTab(id: string): void {
      const vs = new Viewsheet();
      vs.localId = sheetCounter++;
      vs.label = this.getLinkVSLabel(id);
      vs.id = id;
      vs.closeOnServer = true;
      vs.linkview = true;
      vs.parentSheet = this.focusedSheet;
      let index = this.sheets.push(vs) - 1;
      this.openedTabs.push(new ComposerTabModel(vs.type, vs));

      this.navigateToExisting(index);
   }

   private getLinkVSLabel(id: string): string {
      let pathWithoutOrgId = id.substring(0, id.lastIndexOf("^"));
      return pathWithoutOrgId.substring(pathWithoutOrgId.lastIndexOf("^") + 1)
   }

   previewViewsheet(sheet: Sheet): void {
      if(sheet && sheet.type === "viewsheet") {
         const vs = new Viewsheet();
         vs.localId = sheetCounter++;
         vs.label = "_#(js:Preview) " + sheet.label;
         vs.id = sheet.id;
         vs.runtimeId = sheet.runtimeId;
         vs.preview = true;
         vs.currentLayout = (<Viewsheet>sheet).currentLayout;
         vs.parentSheet = sheet;

         if(vs.currentLayout) {
            vs.label += " (" + vs.currentLayout.name + ")";
         }

         const index: number = this.sheets.push(vs) - 1;
         this.openedTabs.push(new ComposerTabModel(vs.type, vs));
         this.navigateToExisting(index, true);
         this.changeDetectorRef.detectChanges();
      }
   }

   public processNotification(notification: Notification) {
      switch(notification.type) {
         case "success":
            this.notifications.success(notification.message);
            break;
         case "info":
            this.notifications.info(notification.message);
            break;
         case "warning":
            this.notifications.warning(notification.message);
            break;
         case "danger":
            this.notifications.danger(notification.message);
            break;
         default:
            this.notifications.warning(notification.message);
      }
   }

   updateSheet(event: [Sheet, Sheet]): void {
      const original: Sheet = event[0];
      const updated: Sheet = event[1];
      const index = this.sheets.findIndex((sheet) => original === sheet);
      let tabIndex = this.openedTabs.findIndex(tab => tab.asset == original);

      if(tabIndex >= 0) {
         this.openedTabs[tabIndex] = new ComposerTabModel(updated.type, updated);
      }

      if(index >= 0) {
         this.sheets[index] = updated;

         if(this.focusedSheet === original) {
            this.onFocusedSheetChanged(updated, true);
         }
      }
   }

   worksheetCompositionChanged(worksheet: Worksheet) {
      this.updateSidebar(worksheet);
   }

   editJoin(event: Worksheet) {
      event.socketConnection.sendEvent(EDIT_WORKSHEET_JOINS_SOCKET_URI);
   }

   worksheetCancel(event: [Worksheet, number]) {
      event[0].socketConnection.sendEvent(CANCEL_WORKSHEET_JOINS_SOCKET_URI);
   }

   updateFormat(model: VSObjectFormatInfoModel): void {
      const layout: boolean = !!this.layout;
      const focusedObjects: VSObjectModel[] = layout ? this.layoutFormatObjects :
         this.focusedSheet.currentFocusedAssemblies;
      let event: FormatVSObjectEvent;
      const origFormat: VSObjectFormatInfoModel = this.layout ? this.layout.origFormat
         : (<Viewsheet>this.focusedSheet).origFormat;

      if(focusedObjects.length < 1) {
         event = new FormatVSObjectEvent();
         event.charts = [];
      }
      else {
         event = VSUtil.prepareFormatEvent(focusedObjects);
      }

      event.format = model;
      event.origFormat = origFormat;
      event.reset = !model;
      event.layout = layout;
      event.layoutRegion = layout ? this.layout.currentPrintSection : 0;

      layout ? this.layout.socketConnection.sendEvent(UPDATE_FORMAT_URI, event) :
         this.focusedSheet.socketConnection.sendEvent(UPDATE_FORMAT_URI, event);
   }

   getVariables(name: string, type: string): string[] {
      if(type == "VSChart") {
         return VSUtil.getVariableList((<Viewsheet>this.focusedSheet).vsObjects, name, true);
      }
      else {
         return VSUtil.getVariableList((<Viewsheet>this.focusedSheet).vsObjects, name);
      }
   }

   clearFocusedObjects(): void {
      if(this.focusedSheet) {
         this.focusedSheet.clearFocusedAssemblies();
      }
   }

   private getIndexOfSheet(sheet: Sheet): number {
      return this.sheets.findIndex((s) => s.runtimeId === sheet.runtimeId);
   }

   private getIndexOfTab(asset: Sheet | LibraryAsset): number {
      return this.openedTabs.findIndex((s) => {
         if(this.isSheet(asset) && this.isSheet(s.asset)) {
            return (<Sheet>asset).runtimeId == (<Sheet>s.asset).runtimeId;
         }

         if(!this.isSheet(asset) && !this.isSheet(s.asset)) {
            return (<LibraryAsset>asset).id == (<LibraryAsset>s.asset).id;
         }

         return false;
      });
   }

   private isSheet(asset: Sheet | LibraryAsset): boolean {
      return asset?.type == "viewsheet" || asset?.type == "worksheet";
   }

   private navigateToExisting(index: number, initPreview: boolean = false) {
      if(index >= 0 && index < this.sheets.length) {
         if(!this.importDialogOpen) {
            this.onFocusedSheetChanged(this.sheets[index], true, initPreview);
         }
      }
   }

   private navigateToExistingSheet(sheet: Sheet): void {
      for(let i = 0; i < this.sheets.length; i++) {
         if(this.sheets[i].runtimeId === sheet.runtimeId) {
            sheet = this.sheets[i];
         }
      }

      this.onFocusedSheetChanged(sheet);
   }

   private navigateToExistingTab(tab: ComposerTabModel): void {
      if(this.isSheet(tab.asset)) {
         this.onSheetSelected(<Sheet>tab.asset);
      }
      else {
         this.onFocusedLibraryAssetChanged(<LibraryAsset>tab.asset, true);
      }
   }

   private static closeSheetOnServer(sheet: Sheet, deleteAutosave: boolean) {
      if(!sheet.runtimeId || !sheet.socketConnection) {
         return;
      }

      if(sheet.type === "viewsheet") {
         sheet.socketConnection.sendEvent(CLOSE_VIEWSHEET_SOCKET_URI, new CloseSheetEvent(deleteAutosave));
      }
      else if(sheet.type === "worksheet") {
         sheet.socketConnection.sendEvent(CLOSE_WORKSHEET_SOCKET_URI);
      }
   }

   /**
    * Used for selecting a default folder to save to in the save (ws) dialog.
    * @returns {AssetEntry}
    */
   get defaultSaveToFolder(): AssetEntry {
      return this.saveToFolderId ? createAssetEntry(this.saveToFolderId) : this.defaultFolder;
   }

   get isFormatPainterMode(): boolean {
      return this.focusedSheet && this.focusedSheet.type === "viewsheet"
         && (<Viewsheet>this.focusedSheet).formatPainterMode;
   }

   get focusedSheetAsWorksheet(): Worksheet {
      if(this.focusedSheet && this.focusedSheet.type === "worksheet") {
         return <Worksheet>this.focusedSheet;
      }

      return null;
   }

   get layout(): VSLayoutModel {
      return this.focusedSheet && this.focusedSheet.type === "viewsheet" ?
         (<Viewsheet>this.focusedSheet).currentLayout : null;
   }

   get layoutName(): string {
      let layout: VSLayoutModel = this.layout;

      return layout ? layout.name : null;
   }

   get layoutGuide(): GuideBounds {
      let layout: VSLayoutModel = this.layout;

      return layout && !layout.printLayout ? layout.guideType : GuideBounds.GUIDES_NONE;
   }

   get layoutFormatObjects(): VSObjectModel[] {
      const editableObjects: VSLayoutObjectModel[] =
         this.layout.focusedObjects.filter(obj => obj.editable);
      // format enabled if only editable objects are selected on layout
      return editableObjects.length == this.layout.focusedObjects.length ?
         editableObjects.map(obj => obj.objectModel) : [];
   }

   updatePresenterProperties(uri: string, model: PresenterPropertyDialogModel): void {
      !!this.layout ? this.layout.socketConnection.sendEvent(uri, model) :
         this.focusedViewsheet.socketConnection.sendEvent(uri, model);
   }

   closeBindingPane(refresh: boolean = true): void {
      this.focusedViewsheet.bindingEditMode = false;
      let oldBindingPaneModel = this.bindingPaneModel;

      this.bindingPaneModel = {
         runtimeId: null,
         objectType: null,
         absoluteName: null,
         oldAbsoluteName: null,
         wizardOriginalInfo: null
      };

      if(refresh) {
         const evt = new RefreshBindingTreeEvent(null);

         // if edited by vs wizard, then refresh will be done when process AssemblyChangedCommand,
         // because assembly name maybe changed, should use the latest name to do refresh.
         if(!!oldBindingPaneModel.wizardOriginalInfo) {
            this.focusedViewsheet.socketConnection.sendEvent("/events/vs/bindingtree/gettreemodel",
               evt);

            return;
         }

         // if do rename in binding pane, should remove the old one from viewsheet pane after binding pane was closed.
         if(!!oldBindingPaneModel.oldAbsoluteName) {
            this.focusedViewsheet.removeAssembly(oldBindingPaneModel.oldAbsoluteName, true);
         }

         // fix Bug #44937, just refresh change assembly and dependencies.
         this.refreshAssembly(this.focusedViewsheet, oldBindingPaneModel.absoluteName);
         this.focusedViewsheet.socketConnection.sendEvent("/events/vs/bindingtree/gettreemodel", evt);
      }

      this.setKeydownListener();
   }

   refreshChangedAssembly(command: AssemblyChangedCommand): void {
      if(!this.focusedViewsheet || !command) {
         return;
      }

      this.refreshAssembly(this.focusedViewsheet, command.name);

      if(!!command.oname) {
         let objs = this.focusedViewsheet.vsObjects;
         let nobjs: VSObjectModel[] = [];

         for(let i = 0; i < objs.length; i++) {
            if(objs[i].absoluteName != command.oname) {
               nobjs[nobjs.length] = objs[i];
            }
         }

         this.focusedViewsheet.vsObjects = nobjs;
      }
   }

   changeBindingAssemblyName(newName: string): void {
      if(!!this.bindingPaneModel) {
         this.bindingPaneModel.oldAbsoluteName = this.bindingPaneModel.absoluteName;
         this.bindingPaneModel.absoluteName = newName;
      }
   }

   protected openEditPane(model: VSObjectModel, runtimeId?: string,
      oinfo?: WizardOriginalInfo, useOriginal: boolean = false): void {
      this.focusedViewsheet.bindingEditMode = true;
      this.bindingPaneModel = <any>{
         runtimeId: runtimeId ? runtimeId : this.focusedSheet.runtimeId,
         objectType: useOriginal && oinfo ? oinfo.objectType : model.objectType,
         absoluteName: useOriginal && oinfo ? oinfo.absoluteName : model.absoluteName,
         wizardOriginalInfo: oinfo
      };
      this.removeKeydownListener();
   }

   refreshSelectViewsheet(vs: Viewsheet, manualRefresh: boolean = true,
      disableParameterSheet: boolean = false) {
      if(vs && vs.socketConnection) {
         let mobile: boolean = GuiTool.isMobileDevice();
         let event: OpenViewsheetEvent = new OpenViewsheetEvent(
            vs.id, 0, 0, mobile, window.navigator.userAgent);
         event.viewer = false;
         event.runtimeViewsheetId = vs.runtimeId;
         event.manualRefresh = manualRefresh;
         event.disableParameterSheet = disableParameterSheet;
         vs.socketConnection.sendEvent("/events/open", event);
      }
   }

   private refreshAssembly(vs: Viewsheet, assemblyName: string) {
      if(!vs || !assemblyName) {
         return;
      }

      let refreshVsAssemblyEvent: RefreshVsAssemblyEvent = {
         vsRuntimeId: vs.runtimeId,
         assemblyName: assemblyName
      };

      vs.socketConnection.sendEvent("/events/vs/refresh/assembly", refreshVsAssemblyEvent);
   }

   private isModalOpen(): boolean {
      return document.body.classList.contains("modal-open");
   }

   private setKeydownListener() {
      if(this.keydownListener == null) {
         this.zone.runOutsideAngular(() => {
            this.keydownListener = this.renderer.listen(
               "document", "keydown", (e) => this.onKeydown(e));
         });
      }
   }

   private removeKeydownListener() {
      if(!!this.keydownListener) {
         this.keydownListener();
         this.keydownListener = null;
      }
   }

   private onKeydown(event: KeyboardEvent) {
      // ctrl-s
      if(event.keyCode === 83 && (event.ctrlKey || event.metaKey)) {
         // Only register key presses if sheet is active, key is not held down, modal is not open
         if(this.focusedSheet == null && this.focusedTab == null || event.repeat || this.isModalOpen()) {
            // don't prevent default or key won't work (e.g. asset tree search)
            return;
         }

         event.preventDefault();

         this.zone.run(() => {
            // avoid potential race condition where this.focusedSheet changes
            // between 'if' and saveViewsheetAs
            const focusedSheet: Sheet = this.focusedSheet;

            if(this.focusedTab) {
               if(this.focusedTab.type === "script" || this.focusedTab.type === "tableStyle") {
                  const focusedLib: LibraryAsset = <LibraryAsset>this.focusedTab.asset;

                  if(focusedLib.type === "tableStyle") {
                     focusedLib.newAsset ? this.saveTableStyleAs(<TableStyleModel>focusedLib) :
                        this.saveTableStyle(<TableStyleModel>focusedLib);
                  }
                  else if(focusedLib.type === "script") {
                     focusedLib.newAsset ? this.saveScriptAs(<ScriptModel>focusedLib) :
                        this.saveScript(<ScriptModel>focusedLib);
                  }
               }
            }

            if(focusedSheet) {
               if(focusedSheet.type === "viewsheet" && focusedSheet.newSheet) {
                  this.saveViewsheetAs(<Viewsheet>focusedSheet, false);
               }
               else if(focusedSheet.type === "viewsheet" && !focusedSheet.newSheet) {
                  this.saveViewsheet(<Viewsheet>focusedSheet, false);
               }
               else if(focusedSheet.type === "worksheet" && focusedSheet.newSheet) {
                  this.saveWorksheetAs(focusedSheet as Worksheet, false);
               }
               else {
                  this.saveWorksheet(focusedSheet as Worksheet, false);
               }
            }
         });
      }

      // ctrl-shift-t
      if(event.keyCode === 84 && event.ctrlKey && event.shiftKey) {
         // Only register key presses if sheet is active, sheet is a viewsheet and not
         // preview, key is not held down, modal is not open
         if(this.focusedViewsheet == null || this.focusedViewsheet.preview ||
            event.repeat || this.isModalOpen()) {
            // don't prevent default or key won't work (e.g. asset tree search)
            return;
         }

         event.preventDefault();

         this.zone.run(() => {
            this.previewViewsheet(this.focusedSheet);
         });
      }

      if(Tool.isEventTargetTextEditor(event)) {
         return;
      }

      if(event.ctrlKey || event.metaKey) {

         // ctrl-z
         if(event.keyCode == 90 && this.undoEnabled()) {
            if(this.focusedTab.type == "tableStyle") {
               let tableStyle = this.asTableStyle(this.focusedTab);
               tableStyle.currentIndex--;
               tableStyle.styleFormat = Tool.clone(tableStyle.undoRedoList[tableStyle.currentIndex]);
               tableStyle.isModified = tableStyle.styleFormat.origianlIndex != tableStyle.currentIndex;
               this.zone.run(() => this.updateTableStylePreview());
            }
            else if(this.layoutShowing) {
               const uri: string = `/events/composer/vs/layouts/undo/${this.layoutRuntimeId}`;
               this.focusedSheet.socketConnection.sendEvent(uri);
            }
            else {
               this.focusedSheet.socketConnection.sendEvent("/events/undo");
            }
         }
         // ctrl-y
         else if(event.keyCode == 89 && this.redoEnabled()) {
            if(this.focusedTab.type == "tableStyle") {
               let tableStyle = this.asTableStyle(this.focusedTab);
               tableStyle.currentIndex++;
               tableStyle.styleFormat = Tool.clone(tableStyle.undoRedoList[tableStyle.currentIndex]);
               tableStyle.isModified = tableStyle.styleFormat.origianlIndex != tableStyle.currentIndex;
               this.zone.run(() => this.updateTableStylePreview());
            }
            else if(this.layoutShowing) {
               const uri: string = `/events/composer/vs/layouts/redo/${this.layoutRuntimeId}`;
               this.focusedSheet.socketConnection.sendEvent(uri);
            }
            else {
               this.focusedSheet.socketConnection.sendEvent("/events/redo");
            }
         }
      }

      //ctrl-c, fix IE 11 cannot Listen the "document: copy" event
      if(event.keyCode == 67 && event.ctrlKey &&
         !Tool.isEventTargetTextEditor(event) && !!this.focusedSheet) {
         this.copySheet(this.focusedSheet);
      }

      //ctrl-x, fix IE 11 cannot Listen the "document: cut" event
      if(event.keyCode == 88 && event.ctrlKey &&
         !Tool.isEventTargetTextEditor(event) && !!this.focusedSheet) {
         this.cutSheet(this.focusedSheet);
      }
   }

   private get layoutShowing(): boolean {
      return this.focusedViewsheet && this.focusedViewsheet.type === "viewsheet" && this.focusedViewsheet.currentLayout != null;
   }

   private get layoutRuntimeId(): string {
      return this.focusedViewsheet.currentLayout.socketConnection.runtimeId;
   }

   private undoEnabled(): boolean {
      if(this.focusedTab.type == "tableStyle") {
         let tableStyle = <TableStyleModel>this.focusedTab.asset;

         return tableStyle.undoRedoList != null && tableStyle.undoRedoList.length > 0 &&
            tableStyle.currentIndex > 0;
      }

      return !this.wizardEditMode && (this.layoutShowing ? this.focusedViewsheet.layoutPoint > 0 :
         (this.focusedSheet.current > 0) && !this.focusedSheet.loading);
   }

   private redoEnabled(): boolean {
      if(this.focusedTab.type == "tableStyle") {
         let tableStyle = <TableStyleModel>this.focusedTab.asset;

         return tableStyle.undoRedoList != null && tableStyle.undoRedoList.length > 0 &&
            tableStyle.currentIndex < tableStyle.undoRedoList.length - 1;
      }

      return !this.wizardEditMode && (this.layoutShowing ?
         this.focusedViewsheet.layoutPoint < this.focusedViewsheet.layoutPoints - 1 :
         (this.focusedSheet.current < this.focusedSheet.points - 1 && !this.focusedSheet.loading));
   }

   /**
    * Checks whether the given worksheet has a primary assembly and asks the user for confirmation
    * if it does not.
    *
    * @param worksheet the worksheet to check
    * @returns a promise resolving to true if the worksheet is ok to save, false otherwise
    */
   private confirmSaveWorksheetWithoutPrimaryAssembly(worksheet: Worksheet): Promise<boolean> {
      const hasPrimaryAssembly = !!worksheet.assemblies().find((a) => a.primary);

      if(!hasPrimaryAssembly) {
         const message = "_#(js:common.worksheetNoPrimary)";
         return ComponentTool.showConfirmDialog(this.modalService, CONFIRM_MESSAGE.title, message,
            CONFIRM_MESSAGE.options)
            .then((buttonClicked) => buttonClicked === "yes");
      }
      else {
         return Promise.resolve<boolean>(true);
      }
   }

   private checkCycle(worksheet: Worksheet): Promise<boolean> {
      let para = new HttpParams().set("rid", worksheet.runtimeId);

      return this.modelService.getModel<boolean>(CHECK_WORKSHEET_CYCLE_URI, para).toPromise()
         .then(res => {
            if(res) {
               const message = "_#(js:common.worksheetCycleMessage)";
               return ComponentTool.showConfirmDialog(this.modalService, CONFIRM_MESSAGE.title, message,
                  CONFIRM_MESSAGE.options)
                  .then((buttonClicked) => buttonClicked === "yes");
            }
            else {
               return Promise.resolve<boolean>(true);
            }
         });
   }

   @HostListener("window:beforeunload", ["$event"])
   beforeunloadHandler(event) {
      // this message may or may not be shown depending on the browser
      let confirmMsg = "_#(js:unsave.changes.message)";

      for(let sheet of this.sheets) {
         if(sheet.isModified()) {
            event.returnValue = confirmMsg;
            return confirmMsg;
         }
      }

      for(let sheet of this.sheets) {
         ComposerMainComponent.closeSheetOnServer(sheet, true);
      }

      return null;
   }

   public toggleSplitPane(): void {
      if(this.splitPaneCollapsed) {
         this.splitPane.setSizes([this.splitPaneSize, 100 - this.splitPaneSize]);
         this.splitPaneCollapsed = false;
      }
      else {
         this.splitPane.collapse(0);
         this.splitPaneCollapsed = true;
      }

      this.resizeHandlerService.onVerticalResizeEnd();
   }

   private initComposerClient(): void {
      this.composerClient.connect();
      this.composerClient.editAsset.subscribe((command: OpenComposerAssetCommand) => {
         this.zone.run(() => {
            if(command.viewsheet) {
               const sheet = this.sheets.find((s) =>
                  s.id === command.assetId && s.runtimeId.indexOf(AssetConstants.PREVIEW_VIEWSHEET) == -1);

               if(!!sheet) {
                  const viewsheet: Viewsheet = sheet as Viewsheet;

                  // refresh toolbox data source tree before
                  const evt = new RefreshBindingTreeEvent(null);
                  viewsheet.socketConnection.sendEvent("/events/vs/bindingtree/gettreemodel", evt);

                  // refresh if there are any shared filters
                  const refreshEvent = new VSRefreshEvent();
                  refreshEvent.setCheckShareFilter(true);
                  viewsheet.socketConnection.sendEvent("/events/vs/refresh", refreshEvent);
               }

               this.composerRecentService.addRecentlyViewed(createAssetEntry(command.assetId));
               this.openViewsheet(command.assetId);
            }
            else if(command.wsWizard) {
               this.saveToFolderId = command.folderId;
               this.wsWizard = command.wsWizard;
               this.baseDataSource = command.baseDataSource;
               this.baseDataSourceType = command.baseDataSourceType;
               this.openNewWsWithWizard();
            }
            else {
               this.composerRecentService.addRecentlyViewed(createAssetEntry(command.assetId));
               this.openWorksheet(command.assetId);
            }
         });
      });
   }

   private listenGettingStartedEvent() {
      this.subscriptions.add(
         this.gettingStartedService.editSheet.subscribe(event => {
            this.saveToFolderId = event.folder;

            if(event.op == GettingStartedStep.CREATE_DASHBOARD) {
               this.gettingStartedCreateDashboard(event.baseWs );
            }
            else if(event.newSheet) {
               if(event.op == GettingStartedStep.START_FROM_SCRATCH ||
                  !event.baseDataSource && event.op != GettingStartedStep.UPLOAD) {
                  this.openNewWorksheet(true);
               }
               else {
                  this.wsWizard = true;
                  this.baseDataSource = event.baseDataSource;
                  this.baseDataSourceType = event.baseDataSourceType;
                  this.openNewWsWithWizard(true);
               }
            }
            else {
               this.composerRecentService.addRecentlyViewed(createAssetEntry(event.sheetId));

               if(event.op == GettingStartedStep.START_FROM_SCRATCH) {
                  this.wsWizard = false;
                  this.baseDataSource = null;
                  this.baseDataSourceType = null;
               }
               else if(event.op == GettingStartedStep.UPLOAD) {
                  this.baseDataSource = null;
                  this.baseDataSourceType = null;
               }
               else {
                  this.wsWizard = true;
                  this.baseDataSource = event.baseDataSource;
                  this.baseDataSourceType = event.baseDataSourceType;
               }

               this.openWorksheet(event.sheetId, true);
            }
         })
      );
   }

   private gettingStartedCreateDashboard(baseEntry: string) {
      if(!baseEntry) {
         this.vsWizard = true;
         this.newViewsheet(true);
      }
      else {
         let entry: AssetEntry = createAssetEntry(baseEntry);

         this.wizardModel = {
            entry: entry,
            componentWizardEnable: !!entry,
            editMode: VsWizardEditModes.WIZARD_DASHBOARD,
            temporarySheet: true,
            gettingStarted: true,
            oinfo: {
               runtimeId: null,
               editMode: VsWizardEditModes.WIZARD_DASHBOARD
            }
         };
         this.wizardEditMode = true;
      }
   }

   closeComposer(result: any): void {
      if(this.gettingStartedService.isProcessing()) {
         this.gettingStartedService.continue();
      }
      else if(this.closeOnComplete) {
         const pane = result.assetRepositoryPaneModel;
         const identifier = pane ? this.createWorksheetIdentifier(pane) : null;
         this.closed.emit(identifier);
      }
   }

   private createWorksheetIdentifier(assetPaneModel: AssetRepositoryPaneModel): string {
      const name: string = assetPaneModel.name;
      const parentEntry: AssetEntry = assetPaneModel.parentEntry;
      let identifier: string = assetPaneModel.selectedEntry;

      if(!identifier && parentEntry && name) {
         const sep = parentEntry.path.endsWith("/") ? "" : "/";
         const ppath = parentEntry.path == "/" ? "" : parentEntry.path;

         identifier = parentEntry.scope + "^" + 2 + "^" +
            (parentEntry.user || "__NULL__") + "^" + ppath + sep + name;
      }

      return identifier;
   }

   getParentSocketConnection(viewsheet: Viewsheet): ViewsheetClientService {
      return !!viewsheet ? viewsheet.parentSheet.socketConnection : null;
   }

   newViewsheet(gettingStarted?: boolean): void {
      let dialog = ComponentTool.showDialog(this.modalService, NewViewsheetDialog,
         (model: NewViewsheetDialogModel) => {
            if(!model.openWizard) {
               this.scriptDisabled = true;
               this.regionsDisabled = true;
               // open vs pane
               this.openNewViewsheet(model.baseEntry, gettingStarted);

               if(this.vsWizard && this.setPrincipalCommand.autoSaveFiles != null &&
                  this.setPrincipalCommand.autoSaveFiles.length > 0) {
                  this.fixAutoSaveFiles(this.setPrincipalCommand.autoSaveFiles);
               }
            }
            else {
               // open vs wizard
               this.wizardModel = {
                  entry: model.baseEntry,
                  componentWizardEnable: !!model.baseEntry,
                  editMode: VsWizardEditModes.WIZARD_DASHBOARD,
                  temporarySheet: true,
                  gettingStarted: gettingStarted,
                  oinfo: {
                     runtimeId: null,
                     editMode: VsWizardEditModes.WIZARD_DASHBOARD
                  }
               };
               this.wizardEditMode = true;
            }
         }, {}, () => {
            // close composer if getting started and canceled
            if(gettingStarted) {
               this.closeComposer(null);
            }
         });
   }

   closeWizardPane(evt?: CloseWizardModel): void {
      this.wizardEditMode = false;

      // cancel to create vs by wizard.
      if(!evt) {
         if(this.gettingStartedService.isCreateDashboard()) {
            this.gettingStartedService.continue();
         }

         return;
      }

      // new vs by wizard and finish to open the vs.
      if(evt.save && evt.model.editMode == VsWizardEditModes.WIZARD_DASHBOARD) {
         this.openViewsheet(evt.model.assetId, false, evt.model.runtimeId, true, evt?.model?.gettingStarted);

         // should close the viewsheet on server when close this viewsheet.
         this.sheets.forEach(sheet => {
            if(sheet.id == evt.model.assetId && sheet.runtimeId == evt.model.runtimeId) {
               sheet.closeOnServer = true;
            }
         });
      }
      // 1. open chart wizard in viewsheet pane and finish to go back to vs pane
      // 2. cancel back to viewsheet pane.
      else if(evt.model.editMode == VsWizardEditModes.VIEWSHEET_PANE ||
         !evt.save && evt.model.oinfo.editMode == VsWizardEditModes.VIEWSHEET_PANE) {
         if(this.focusedViewsheet.bindingEditMode) {
            this.closeBindingPane(false);
         }

         if(evt.save) {
            this.refreshSelectViewsheet(this.focusedViewsheet, false, true);
         }
      }
      // 1. cancel and go back to full editor.
      // 2. when chart wizard, click go to full editor in object wizard.
      else if(!evt.save && evt.model.oinfo.editMode == VsWizardEditModes.FULL_EDITOR ||
         evt.model.editMode == VsWizardEditModes.FULL_EDITOR) {
         this.openEditPane(evt.model.objectModel, evt.model.runtimeId, evt.model.oinfo, true);
      }
   }

   /**
    * when create a viewsheet through the wizard,
    * click go to full editor in object wizard
    * @param evt
    */
   goToFullEditor(evt: CloseWizardModel) {
      this.wizardEditMode = false;
      this.wizardModel.hiddenNewBlock = evt.model.hiddenNewBlock;

      //new vs by wizard and open full editor.
      if(!this.focusedViewsheet || this.focusedViewsheet.id != evt.model.assetId) {
         let vs: Viewsheet = new Viewsheet();
         vs.newSheet = evt.model.oinfo.editMode == VsWizardEditModes.WIZARD_DASHBOARD;
         vs.runtimeId = evt.model.runtimeId;
         vs.id = evt.model.assetId;
         vs.linkUri = evt.model.linkUri;
         vs.baseEntry = evt.model.entry;
         evt.model.oinfo.originalFocused = this.focusedSheet;
         this.focusedSheet = vs;
      }

      this.openEditPane(evt.model.objectModel, evt.model.runtimeId, evt.model.oinfo);
   }

   goToEditor(objectModel: VSObjectModel): void {
      this.openEditPane(objectModel);
   }

   goToWizardPane(model: VsWizardModel) {
      if(!!model) {
         model.temporarySheet = this.focusedViewsheet.newSheet;
         model.assetId = this.focusedViewsheet.id;
         model.hiddenNewBlock = this.wizardModel && this.wizardModel.hiddenNewBlock;
         this.wizardModel = model;
         this.wizardEditMode = true;
      }
   }

   switchBindingToWizard(model: VsWizardModel) {
      if(!model) {
         return;
      }

      // close binding pane before go back to wizard.
      if(model.oinfo.editMode == VsWizardEditModes.WIZARD_DASHBOARD) {
         this.closeBindingPane(false);
      }

      model.oldAbsoluteName = this.bindingPaneModel.oldAbsoluteName;

      this.goToWizardPane(model);

      if(!!model.oinfo.originalFocused) {
         this.focusedSheet = model.oinfo.originalFocused;
      }
   }

   fullViewVisible(): boolean {
      const vs = this.focusedViewsheet;
      return (vs == null || !vs.bindingEditMode) && !this.wizardEditMode;
   }

   processPreviewMessageCommand(command: MessageCommand, sheet: Viewsheet): void {
      const parentSheet = sheet.parentSheet;

      if(parentSheet?.type === "viewsheet") {
         (<Viewsheet>parentSheet).sendMessageCommand(command);
      }
   }

   openScript(vs: Viewsheet) {
      this.openViewsheetOptionDialog(vs, null, true);
   }

   isIframe(): boolean {
      return GuiTool.isIFrame();
   }

   openFormatPane(model: VSObjectModel): void {
      this.selectedTab = SidebarTab.FORMAT;
   }

   asViewsheet(tab: ComposerTabModel): Viewsheet {
      return tab.asset as Viewsheet;
   }

   asWorksheet(tab: ComposerTabModel): Worksheet {
      return tab.asset as Worksheet;
   }

   asScript(tab: ComposerTabModel): ScriptModel {
      return tab.asset as ScriptModel;
   }

   asTableStyle(tab: ComposerTabModel): TableStyleModel {
      return tab.asset as TableStyleModel;
   }

   openVSOnPortal(vsId: string) {
      if(this.gettingStartedService.finished) {
         this.gettingStartedService.openVsOnPortal(vsId);
      }
   }

   onSaveWorksheetFinish(ws: Worksheet) {
      if((this.gettingStartedService.isUploadFile() || this.gettingStartedService.isCreateQuery() ||
         this.gettingStartedService.isStartFromScratch()) && this.worksheetPermission) {
         this.gettingStartedService.setWorksheetId(ws.id);
      }
   }
}
