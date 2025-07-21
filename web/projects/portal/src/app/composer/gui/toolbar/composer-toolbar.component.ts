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
   AfterViewInit,
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Input,
   NgZone,
   OnDestroy,
   OnInit,
   Output,
   QueryList,
   Renderer2,
   TemplateRef,
   ViewChild,
   ViewChildren,
} from "@angular/core";
import { NgbDropdown, NgbModal, NgbTooltipConfig } from "@ng-bootstrap/ng-bootstrap";
import { Observable, of as observableOf, Subject, Subscription } from "rxjs";
import { takeUntil } from "rxjs/operators";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import {
   DatabaseDataSource,
   DatabaseDataSources
} from "../../../../../../shared/util/model/database-data-source";
import { TabularDataSourceTypeModel } from "../../../../../../shared/util/model/tabular-data-source-type-model";
import { Tool } from "../../../../../../shared/util/tool";
import { ChatService } from "../../../common/chat/chat.service";
import { Notification } from "../../../common/data/notification";
import { VariableInfo } from "../../../common/data/variable-info";
import { FullScreenService } from "../../../common/services/full-screen.service";
import { GuiTool } from "../../../common/util/gui-tool";
import { XConstants } from "../../../common/util/xconstants";
import { GuideBounds } from "../../../vsobjects/model/layout/guide-bounds";
import { ZoomOptions } from "../../../vsobjects/model/layout/zoom-options";
import { PrintLayoutSection } from "../../../vsobjects/model/layout/print-layout-section";
import { VSLineModel } from "../../../vsobjects/model/vs-line-model";
import { VSObjectModel } from "../../../vsobjects/model/vs-object-model";
import { VSViewsheetModel } from "../../../vsobjects/model/vs-viewsheet-model";
import { VSCompositeModel } from "../../../vsobjects/model/vs-composite-model";
import { VSSelectionBaseModel } from "../../../vsobjects/model/vs-selection-base-model";
import { ScriptPaneTreeModel } from "../../../widget/dialog/script-pane/script-pane-tree-model";
import { VariableInputDialogModel } from "../../../widget/dialog/variable-input-dialog/variable-input-dialog-model";
import { DropdownObserver } from "../../../widget/services/dropdown-observer.service";
import { ModelService } from "../../../widget/services/model.service";
import { ScaleService } from "../../../widget/services/scale/scale-service";
import { Sheet } from "../../data/sheet";
import { SelectDataSourceDialogModel } from "../../data/vs/select-data-source-dialog-model";
import { Viewsheet } from "../../data/vs/viewsheet";
import { VSLayoutModel } from "../../data/vs/vs-layout-model";
import { VSLayoutObjectModel } from "../../data/vs/vs-layout-object-model";
import { AbstractTableAssembly } from "../../data/ws/abstract-table-assembly";
import { Worksheet } from "../../data/ws/worksheet";
import { WSAssembly } from "../../data/ws/ws-assembly";
import { WorksheetTableOperator } from "../../data/ws/ws-table.operators";
import { WSObjectType } from "../../dialog/ws/new-worksheet-dialog.component";
import { ComposerToolbarService } from "../composer-toolbar.service";
import { EventQueueService } from "../vs/event-queue.service";
import { MoveResizeLayoutObjectsEvent } from "../vs/event/move-resize-layout-objects-event";
import { ResizeVSObjectEvent } from "../vs/objects/event/resize-vs-object-event";
import { WSCollectVariablesOverEvent } from "../ws/socket/ws-collect-variables/ws-collect-variables-over-event";
import { WSConcatenateEvent } from "../ws/socket/ws-concatenate-event";
import { WSCrossJoinEvent } from "../ws/socket/ws-join/ws-cross-join-event";
import { WSJoinTablesEvent } from "../ws/socket/ws-join/ws-join-tables-event";
import { WSMergeJoinEvent } from "../ws/socket/ws-join/ws-merge-join-event";
import { WSLayoutGraphEvent } from "../ws/socket/ws-layout-graph-event";
import { ToolbarActionGroup } from "../../../widget/toolbar/toolbar-action-group";
import { ToolbarAction } from "../../../widget/toolbar/toolbar-action";
import { ComponentTool } from "../../../common/util/component-tool";
import { LayoutUtil } from "../../../vsobjects/util/layout-util";
import { WsSqlQueryController } from "../ws/editor/ws-sql-query-controller";
import { ComposerTabModel } from "../composer-tab-model";
import { LibraryAsset } from "../../data/library-asset";
import { TableStyleModel } from "../../data/tablestyle/table-style-model";
import { TableStyleFormatModel } from "../../data/tablestyle/table-style-format-model";

declare const window;

enum LayoutAlignment {
   TOP,
   MIDDLE,
   BOTTOM,
   LEFT,
   CENTER,
   RIGHT,
   DIST_H,
   DIST_V,
   RESIZE_MIN_W,
   RESIZE_MAX_W,
   RESIZE_MIN_H,
   RESIZE_MAX_H
}

const WORKSHEET_PROPERTY_SOCKET = "/events/composer/ws/dialog/worksheet-property-dialog-model/";
const WORKSHEET_COLLECT_VARIABLES_URI = "/events/ws/dialog/variable-input-dialog";
const WORKSHEET_COLLECT_VARIABLES_RESTORE_URI = "/events/ws/dialog/variable-restore";
const WORKSHEET_ENTER_PARAMS_URI = "../api/composer/ws/dialog/variable-input-dialog-model/";
const WORKSHEET_VPM_PRINCIPAL_SOCKET = "/events/composer/ws/dialog/vpm-principal-dialog";
const WORKSHEET_LAYOUT_URI = "/events/composer/worksheet/layout-graph";
const WORKSHEET_CONCAT_TABLES_URI = "/events/composer/worksheet/concatenate-tables";
const WORKSHEET_NEW_CROSS_JOIN_URI = "/events/ws/dialog/inner-join-dialog/cross-join";
const WORKSHEET_NEW_MERGE_JOIN_URI = "/events/composer/worksheet/dialog/inner-join-dialog/merge-join";
const WORKSHEET_NEW_INNER_JOIN_URI = "/events/composer/worksheet/dialog/inner-join-dialog/inner-join";
const COMPOSER_WIZARD_STATUS_URI: string = "../api/composer/wizard/status";

@Component({
   selector: "composer-toolbar",
   templateUrl: "composer-toolbar.component.html",
   styleUrls: ["composer-toolbar.component.scss"],
   providers: [FullScreenService, NgbTooltipConfig]
})
export class ComposerToolbarComponent implements OnInit, AfterViewInit, OnDestroy {
   _sheet: Sheet;
   @Input() deployed: boolean = false;
   @Input() showPaste: boolean;
   @Input() snapToGrid: boolean = false;
   @Input() snapToObjects: boolean = false;
   @Input() worksheetPermission: boolean;
   @Input() viewsheetPermission: boolean;
   @Input() scriptPermission: boolean;
   @Input() vpmToolbarButtonsVisible: boolean = false;
   @Input() tableStylePermission: boolean;
   @Input() showHelpButton = false;
   @Input() wsWizard: boolean;
   @Input() focusedTab: ComposerTabModel;
   @Output() onCopy: EventEmitter<Sheet> = new EventEmitter<Sheet>();
   @Output() onCut: EventEmitter<Sheet> = new EventEmitter<Sheet>();
   @Output() onPaste: EventEmitter<Sheet> = new EventEmitter<Sheet>();
   @Output() onNewWorksheet: EventEmitter<any> = new EventEmitter<any>();
   @Output() onOpenViewsheetWizard: EventEmitter<any> = new EventEmitter<AssetEntry>(); // teamp
   @Output() onNotification = new EventEmitter<Notification>();
   @Output() onSaveWorksheet: EventEmitter<Sheet> = new EventEmitter<Sheet>();
   @Output() onSaveWorksheetAs: EventEmitter<Sheet> = new EventEmitter<Sheet>();
   @Output() onSaveAndCloseWorksheet: EventEmitter<Sheet> = new EventEmitter<Sheet>();
   @Output() onSaveAsAndCloseWorksheet: EventEmitter<Sheet> = new EventEmitter<Sheet>();
   @Output() onSaveViewsheet: EventEmitter<Sheet> = new EventEmitter<Sheet>();
   @Output() onSaveViewsheetAs: EventEmitter<Sheet> = new EventEmitter<Sheet>();
   @Output() onSaveTableStyle: EventEmitter<LibraryAsset> = new EventEmitter<LibraryAsset>();
   @Output() onSaveTableStyleAs: EventEmitter<LibraryAsset> = new EventEmitter<LibraryAsset>();
   @Output() onUpdateTableStylePreview = new EventEmitter();
   @Output() onSaveScript: EventEmitter<LibraryAsset> = new EventEmitter<LibraryAsset>();
   @Output() onSaveScriptAs: EventEmitter<LibraryAsset> = new EventEmitter<LibraryAsset>();
   @Output() onPreviewViewsheet: EventEmitter<Sheet> = new EventEmitter<Sheet>();
   @Output() onRefreshViewsheet: EventEmitter<Sheet> = new EventEmitter<Sheet>();
   @Output() onOpenViewsheetOptions: EventEmitter<Sheet> = new EventEmitter<Sheet>();
   @Output() onOpenImportDialog: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() onToggleSnapToGrid: EventEmitter<boolean> = new EventEmitter<boolean>();
   @Output() onToggleSnapToObjects = new EventEmitter<boolean>();
   @Output() onOpenScriptOptions = new EventEmitter();
   @Output() closed: EventEmitter<boolean> = new EventEmitter<boolean>();
   private scrolling: any;
   private tempStyles: {[style: string]: any} = {};
   public LayoutAlignment = LayoutAlignment;
   public GuideBounds = GuideBounds;
   public ZoomOptions = ZoomOptions;
   public PrintLayoutSection = PrintLayoutSection;
   @ViewChild("importCsvDialog") importCsvDialog: TemplateRef<any>;
   @ViewChild("sqlQueryDialog") sqlQueryDialog: TemplateRef<any>;
   @ViewChild("tabularQueryDialog") tabularQueryDialog: TemplateRef<any>;
   @ViewChild("queryNameDialog") queryNameDialog: TemplateRef<any>;
   @ViewChild("groupingDialog") groupingDialog: TemplateRef<any>;
   @ViewChild("selectDataSourceDialog") selectDataSourceDialog: TemplateRef<any>;
   @ViewChild("embeddedTableDialog") embeddedTableDialog: TemplateRef<any>;
   @ViewChild("worksheetPropertyDialog") worksheetPropertyDialog: TemplateRef<any>;
   @ViewChild("viewsheetPropertyDialog") viewsheetPropertyDialog: TemplateRef<any>;
   @ViewChild("variableAssemblyDialog") variableAssemblyDialog: TemplateRef<any>;
   @ViewChild("variableInputDialog") variableInputDialog: TemplateRef<any>;
   @ViewChild("vpmPrincipalDialog") vpmPrincipalDialog: TemplateRef<any>;
   @ViewChild("newWSDialog") newWSDialog: TemplateRef<any>;
   @ViewChildren(NgbDropdown) dropdowns: QueryList<NgbDropdown>;
   scriptTreeModel: ScriptPaneTreeModel;
   variableInputDialogModel: VariableInputDialogModel;
   tabularDataSourceTypes: TabularDataSourceTypeModel[];
   selectedDataSourceType: TabularDataSourceTypeModel;
   databaseDataSources: DatabaseDataSource[];
   selectedDatabaseDataSource: string;
   selectDataSourceModel: SelectDataSourceDialogModel;
   newTableName: string;
   editCollapsed: boolean = false;
   previewCollapsed: boolean = false;
   fullScreen: boolean = false;
   moreCollapsed: boolean = false;
   mergeMenuCollapsed: boolean = false;
   showWSWizardVisible: boolean = false;
   wsSqlQueryController: WsSqlQueryController;
   showMashUpData: boolean = false;
   private subscriptions = new Subscription();
   private dropdownSubject = new Subject<void>();

   @Input() set sheet(s: Sheet) {
      this._sheet = s;
      this.resizeListener();

      if(this.sheet) {
         this.wsSqlQueryController.runtimeId = this.sheet.runtimeId;
      }
   }

   get sheet(): Sheet {
      return this._sheet;
   }

   get crossJoinEnabled(): boolean {
      return this.composerToolbarService.crossJoinEnabled;
   }

   get tables(): AbstractTableAssembly[] {
      return (this.sheet && this.sheet.type === "worksheet") ?
         (<Worksheet> this.sheet).tables : null;
   }

   get focusedObjects(): any[] {
      return !!this.sheet ? this.sheet.currentFocusedAssemblies : null;
   }

   // objects for layout alignment, vsobject or layout object
   get alignObjects(): any[] {
      if(this.sheet == null) {
         return [];
      }

      if(this.sheet.type == "viewsheet" && this.currentLayout) {
         const layout: VSLayoutModel = this.currentLayout;

         if(layout.focusedObjects == null) {
            return [];
         }

         return layout.focusedObjects.filter(obj => obj.objectModel.objectType != "VSPageBreak");
      }

      return this.sheet.currentFocusedAssemblies || [];
   }

   get layoutDistributeEnabled(): boolean {
      const objects = this.alignObjects || [];
      return objects.reduce((total, object) =>
         total + (!object.container && object.locked !== true ? 1 : 0), 0) > 2;
   }

   get layoutAlignEnabled(): boolean {
      const objects = this.alignObjects || [];
      return objects.reduce((total, object) =>
         total + (!object.container && object.locked !== true ? 1 : 0), 0) >= 2;
   }

   get layoutResizeEnabled(): boolean {
      const objects = this.alignObjects || [];
      return objects.reduce((total, object) =>
         total + (!object.container && object.locked !== true ? 1 : 0), 0) >= 2;
   }

   constructor(private hostElement: ElementRef,
               private renderer: Renderer2,
               private modalService: NgbModal,
               private modelService: ModelService,
               private eventQueueService: EventQueueService,
               private fullScreenService: FullScreenService,
               private composerToolbarService: ComposerToolbarService,
               private http: HttpClient,
               protected zone: NgZone,
               private scaleService: ScaleService,
               private tooltipConfig: NgbTooltipConfig,
               private chatService: ChatService,
               private dropdownObserver: DropdownObserver)
   {
      tooltipConfig.container = "body";
      this.wsSqlQueryController = new WsSqlQueryController(this.http, this.modelService, this.modalService);
   }

   public ngOnInit(): void {
      this.loadDataSources();
      this.resizeListener();
      this.subscriptions.add(
         this.fullScreenService.fullScreenChange.subscribe(() => this.onFullScreenChange()));
   }

   loadDataSources() {
      this.modelService.getModel("../api/composer/tabularDataSourceTypes")
         .subscribe((data: TabularDataSourceTypeModel[]) => {
            this.tabularDataSourceTypes = data;
         });
      this.modelService.getModel<DatabaseDataSources>("../api/composer/databaseDataSources")
         .subscribe(data => this.databaseDataSources = data.dataSources);
   }

   ngAfterViewInit(): void {
      this.addDropdownListeners();
      this.subscriptions.add(this.dropdowns.changes.subscribe(() => {
         this.dropdownSubject.next();
         Promise.resolve(null).then(() => this.addDropdownListeners());
      }));
   }

   private addDropdownListeners() {
      this.dropdowns.forEach(dropdown => {
         dropdown.openChange
            .pipe(takeUntil(this.dropdownSubject))
            .subscribe(open => {
               if(open) {
                  this.dropdownObserver.onDropdownOpened();
               }
               else {
                  this.dropdownObserver.onDropdownClosed();
               }
            });
      });
   }

   ngOnDestroy(): void {
      this.subscriptions.unsubscribe();
      this.dropdownSubject.next();
      this.dropdownSubject.unsubscribe();

      this.dropdowns.forEach(dropdown => {
         if(dropdown.isOpen()) {
            this.dropdownObserver.onDropdownClosed();
         }
      });
   }

   newWorksheet(): void {
      this.onNewWorksheet.emit(true);
   }

   /**
    * Only called for Agile purposes.
    */
   openWorksheetWizard(wsWizard: boolean, showWSWizardVisible: boolean,
                       baseDataSource?: string, baseDataSourceType?: number,
                       successCallback?: (model?: any) => void, cancelCallback?: () => void): void
   {
      this.wsWizard = wsWizard;
      this.showWSWizardVisible = showWSWizardVisible;
      // Workaround for ExpressionChangedAfterItHasBeenCheckedError which happens when
      // opening a modal during a change detection cycle.
      setTimeout(() => {
         if(!!baseDataSource && baseDataSourceType >= 0) {
            if(baseDataSourceType == WSObjectType.TABULAR) {
               this.getTabularDataSourceTypes().subscribe(data => {
                  this.tabularDataSourceTypes = data;
                  const tabular = this.tabularDataSourceTypes.find(
                     ds => !!ds && ds.dataSource === baseDataSource);

                  if(!!tabular) {
                     this.createObject({
                        objectType: baseDataSourceType,
                        tabularType: tabular
                     }, true, successCallback, cancelCallback);
                  }
               });
            }
            else if(baseDataSourceType == WSObjectType.DATABASE_QUERY) {
               this.databaseDataSources.forEach(database => {
                  if(!!database && database.name == baseDataSource) {
                     this.createObject({
                        objectType: baseDataSourceType,
                        dataSource: database.name,
                        showMashUp: true
                     }, true, successCallback, cancelCallback);
                  }
               });
            }
         }
         else {
            this.modalService.open(this.newWSDialog, {backdrop: "static"})
               .result
               .then(
                  (selected: any) => {
                     selected.showMashUp = true;
                     this.createObject(selected, true);
                  },
                  () => {
                     this.closed.emit(false);
                  }
               );
         }
      }, 200);
   }

   public createObject(selected: any, save: boolean, successCallback?: (model?: any) => void,
                       cancelCallback?: () => void): void
   {
      const successCallbackFn: (model?: any) => void = (model?: any) => {
         if(!!successCallback) {
            successCallback(model);
         }
         else if(save) {
            if(model?.mashUpData) {
               return;
            }

            this.onSaveWorksheetAs.emit(this.sheet);
         }
      };
      const cancelCallbackFn: () => void = () => {
         if(!!cancelCallback) {
            cancelCallback();
         }
         else {
            this.closed.emit(false);
         }
      };

      switch(selected.objectType) {
         case WSObjectType.EMBEDDED_TABLE:
            this.newEmbeddedTable(successCallbackFn, cancelCallbackFn);
            break;
         case WSObjectType.UPLOAD_FILE:
            this.newUploadTable(selected.showMashUp, successCallbackFn, cancelCallbackFn);
            break;
         case WSObjectType.DATABASE_QUERY:
            this.newDatabaseQuery(selected.dataSource, selected.showMashUp, successCallbackFn, cancelCallbackFn);
            break;
         case WSObjectType.VARIABLE:
            this.newVariable(successCallbackFn, cancelCallbackFn);
            break;
         case WSObjectType.GROUPING:
            this.newGrouping(successCallbackFn, cancelCallbackFn);
            break;
         case WSObjectType.TABULAR:
            this.newTabularQuery(selected.tabularType, successCallbackFn, cancelCallbackFn);
            break;
         case WSObjectType.MASHUP:
            // Do nothing
            break;
      }
   }

   openViewsheetWizard(): void {
      this.onOpenViewsheetWizard.emit();
   }

   notify(notification: Notification): void {
      this.onNotification.emit(notification);
   }

   save(close?: boolean): void {
      // delay a little for pending actions to finish
      // for example, if adhoc filter is open, it will take 500ms to close it (adhoc-filter.service)
      setTimeout(() => this.save0(close), 600);
   }

   private getTabularDataSourceTypes(): Observable<TabularDataSourceTypeModel[]> {
      return this.modelService.getModel("../api/composer/tabularDataSourceTypes");
   }

   private save0(close?: boolean): void {
      if(this.focusedTab.type === "viewsheet") {
         if(this.sheet.newSheet) {
            this.onSaveViewsheetAs.emit(this.sheet);
         }
         else {
            this.onSaveViewsheet.emit(this.sheet);
            this.sheet.newSheet = false;
         }
      }
      else if(this.focusedTab.type === "worksheet") {
         if(this.sheet.newSheet) {
            if(close) {
               this.onSaveAsAndCloseWorksheet.emit(this.sheet);
            }
            else {
               this.onSaveWorksheetAs.emit(this.sheet);
            }
         }
         else {
            if(close) {
               this.onSaveAndCloseWorksheet.emit(this.sheet);
            }
            else {
               this.onSaveWorksheet.emit(this.sheet);
            }
         }
      }
      else if(this.focusedTab.type === "tableStyle") {
         let tableStyle: LibraryAsset = <LibraryAsset> this.focusedTab.asset;

         if(tableStyle.newAsset) {
            this.onSaveTableStyleAs.emit(tableStyle);
         }
         else {
            this.onSaveTableStyle.emit(tableStyle);
         }
      }
      else if(this.focusedTab.type === "script") {
         let script: LibraryAsset = <LibraryAsset> this.focusedTab.asset;

         if(script.newAsset) {
            this.onSaveScriptAs.emit(script);
         }
         else {
            this.onSaveScript.emit(script);
         }
      }
   }

   saveAs(): void {
      if(this.sheet && this.sheet.type === "viewsheet") {
         this.onSaveViewsheetAs.emit(this.sheet);
      }
      else if(this.sheet && this.sheet.type === "worksheet") {
         this.onSaveWorksheetAs.emit(this.sheet);
      }
      else if(this.focusedTab.type === "tableStyle") {
         let tableStyle: LibraryAsset = <LibraryAsset>this.focusedTab.asset;
         this.onSaveTableStyleAs.emit(tableStyle);
      }
      else if(this.focusedTab.type === "script") {
         let script: LibraryAsset = <LibraryAsset> this.focusedTab.asset;
         this.onSaveScriptAs.emit(script);
      }
   }

   options(): void {
      if(this.sheet?.type === "viewsheet") {
         this.onOpenViewsheetOptions.emit(this.sheet);
      }
      else if(this.sheet?.type === "worksheet") {
         this.modalService
            .open(this.worksheetPropertyDialog,
               {windowClass: "property-dialog-window", backdrop: "static"})
            .result
            .then((result) => {
               this.sheet.socketConnection.sendEvent(
                  WORKSHEET_PROPERTY_SOCKET + Tool.byteEncode(this.sheet.runtimeId), result);
            }, () => {});
      }
      else if(this.isScript) {
         this.onOpenScriptOptions.emit();
      }
   }

   preview(): void {
      if(this.sheet.type === "viewsheet") {
         if(this.isPrintLayout) {
            const url = "../export/viewsheet/" + Tool.byteEncode(this.sheet.runtimeId) +
               "?match=false&current=true&previewPrintLayout=true";
            GuiTool.openBrowserTab(url);
         }
         else {
            this.onPreviewViewsheet.emit(this.sheet);
         }
      }
      else if(this.sheet.type === "worksheet") {
      }
   }

   refresh() {
      this.onRefreshViewsheet.emit(this.sheet);
   }

   getTableStyleModel() {
      if(this.focusedTab && this.focusedTab.type == "tableStyle") {
         return <TableStyleModel> this.focusedTab.asset;
      }

      return null;
   }

   undo(): void {
      if(this.focusedTab.type == "tableStyle") {
         let tableStyle = this.getTableStyleModel();
         tableStyle.currentIndex--;
         tableStyle.styleFormat = Tool.clone(tableStyle.undoRedoList[tableStyle.currentIndex])
         tableStyle.isModified = tableStyle.styleFormat.origianlIndex != tableStyle.currentIndex;
         this.onUpdateTableStylePreview.emit();
      }
      else {
         if(this.layoutShowing) {
            const uri: string = `/events/composer/vs/layouts/undo/${this.layoutRuntimeId}`;
            this.sheet.socketConnection.sendEvent(uri);
         }
         else {
            this.sheet.socketConnection.sendEvent("/events/undo");
         }
      }
   }

   get undoEnabled(): boolean {
      if(this.getTableStyleModel() != null) {
         let tableStyle = <TableStyleModel> this.focusedTab.asset;

         return tableStyle.undoRedoList != null && tableStyle.undoRedoList.length > 0 &&
            tableStyle.currentIndex > 0;
      }
      else if(!this.sheet) {
         return false;
      }

      return this.layoutShowing ? (<Viewsheet> this.sheet).layoutPoint > 0 :
         (this.sheet.current > 0) && !this.sheet.loading;
   }

   redo(): void {
      if(this.focusedTab.type == "tableStyle") {
         let tableStyle = <TableStyleModel> this.focusedTab.asset;
         tableStyle.currentIndex++;
         tableStyle.isModified = true;
         tableStyle.styleFormat = Tool.clone(tableStyle.undoRedoList[tableStyle.currentIndex]);
         tableStyle.isModified = tableStyle.styleFormat.origianlIndex != tableStyle.currentIndex;
         this.onUpdateTableStylePreview.emit();
      }
      else if(this.layoutShowing) {
         const uri: string = `/events/composer/vs/layouts/redo/${this.layoutRuntimeId}`;
         this.sheet.socketConnection.sendEvent(uri);
      }
      else {
         this.sheet.socketConnection.sendEvent("/events/redo");
      }
   }

   get redoEnabled(): boolean {
      if(this.getTableStyleModel() != null) {
         let tableStyle = <TableStyleModel> this.focusedTab.asset;

         return tableStyle.undoRedoList != null && tableStyle.undoRedoList.length > 0 &&
            tableStyle.currentIndex < tableStyle.undoRedoList.length - 1;
      }
      else if(!this.sheet) {
         return false;
      }

      return this.layoutShowing ?
         (<Viewsheet> this.sheet).layoutPoint < (<Viewsheet> this.sheet).layoutPoints - 1 :
         (this.sheet.current < this.sheet.points - 1 && !this.sheet.loading);
   }

   private get currentLayout(): VSLayoutModel {
      return (<Viewsheet> this.sheet).currentLayout;
   }

   private get layoutRuntimeId(): string {
      return this.currentLayout.socketConnection.runtimeId;
   }

   cut(): void {
      this.onCut.emit(this.sheet);
   }

   copy(): void {
      this.onCopy.emit(this.sheet);
   }

   paste(): void {
      this.onPaste.emit(this.sheet);
   }

   snapToGridChanged(): void {
      this.onToggleSnapToGrid.emit(this.snapToGrid);
   }

   snapToObjectsChanged(): void {
      this.onToggleSnapToObjects.emit(this.snapToObjects);
   }

   enableFormatPainter(): void {
      (<Viewsheet> this.sheet).formatPainterMode = !(<Viewsheet> this.sheet).formatPainterMode;
      (<Viewsheet> this.sheet).painterFormat = null;
   }

   // get left/top width/height from vsobject or layout obj
   private getAlignObj(obj: any): {left: number, top: number, width: number, height: number} {
      if(obj.objectModel) {
         return new AlignLayoutDelegate(obj);
      }

      if(obj.objectType == "VSViewsheet") {
         return new AlignVSViewsheetDelegate(obj as VSViewsheetModel);
      }
      else if(obj.objectType == "VSLine") {
         return new AlignLineDelegate(obj as VSLineModel);
      }

      return new AlignObjectDelegate(obj);
   }

   // move/resize vsobject or layout object
   private fireMoveResize(objects: any[]): void {
      if(!this.layoutShowing) {
         for(let obj of objects) {
            let event: ResizeVSObjectEvent = new ResizeVSObjectEvent(
               obj.absoluteName, obj.objectFormat.left, obj.objectFormat.top,
               obj.objectFormat.width, obj.objectFormat.height);

            this.eventQueueService.addResizeEvent(this.sheet.socketConnection, event);
         }
      }
      else {
         let event: MoveResizeLayoutObjectsEvent = new MoveResizeLayoutObjectsEvent(
            this.currentLayout.name,
            objects.map(object => object.name),
            objects.map(object => object.left),
            objects.map(object => object.top),
            objects.map(object => object.width),
            objects.map(object => object.height)
         );
         event.region = this.currentLayout.currentPrintSection;

         this.currentLayout.socketConnection
            .sendEvent("/events/composer/vs/layouts/moveResizeObjects", event);
      }
   }

   layoutAlign(type: LayoutAlignment): void {
      const selectedObjects =
         this.alignObjects.filter((obj) => !obj.container && obj.locked !== true);

      const firstSelected: any = selectedObjects[0];
      const firstAlignObj = this.getAlignObj(firstSelected);
      const baseX: number = firstAlignObj.left;
      const baseY: number = firstAlignObj.top;
      const baseWidth: number = firstAlignObj.width;
      const baseHeight: number = firstAlignObj.height;

      for(let object of selectedObjects) {
         const selectedAlignObj = this.getAlignObj(object);
         const objWidth: number = selectedAlignObj.width;
         const objHeight: number = selectedAlignObj.height;

         let newX: number = null;
         let newY: number = null;

         switch(type) {
            case LayoutAlignment.TOP:
               newY = baseY;
               break;
            case LayoutAlignment.MIDDLE:
               newY = baseY + baseHeight / 2 - objHeight / 2;
               break;
            case LayoutAlignment.BOTTOM:
               newY = baseY + baseHeight - objHeight;
               break;
            case LayoutAlignment.LEFT:
               newX = baseX;
               break;
            case LayoutAlignment.CENTER:
               newX = baseX + baseWidth / 2 - objWidth / 2;
               break;
            case LayoutAlignment.RIGHT:
               newX = baseX + baseWidth - objWidth;
               break;
            default:
               console.log("Alignment Error");
         }

         if(newX != null) {
            newX = Math.max(newX, 0);
            selectedAlignObj.left = newX;
         }

         if(newY != null) {
            newY = Math.max(newY, 0);
            selectedAlignObj.top = newY;
         }
      }

      this.fireMoveResize(selectedObjects);
   }

   layoutDistribute(type: LayoutAlignment): void {
      let selectedObjects =
         this.alignObjects.filter((obj) => !obj.container && obj.locked !== true);

      switch(type) {
      case LayoutAlignment.DIST_H:
         selectedObjects = selectedObjects.sort((obj1, obj2) => {
            return this.getAlignObj(obj1).left - this.getAlignObj(obj2).left;
         });
         break;
      case LayoutAlignment.DIST_V:
         selectedObjects = selectedObjects.sort((obj1, obj2) => {
            return this.getAlignObj(obj1).top - this.getAlignObj(obj2).top;
         });
         break;
      }

      // Top left most corner left or top value depending on command
      let topLeft: number = Number.MAX_VALUE;

      // Bottom right most corner left or top value depending on command
      let bottomRight: number = 0;

      // Total height or width
      let totalSize: number = 0;

      for(let object of selectedObjects) {
         const selectedAlignObj = this.getAlignObj(object);
         const objX: number = selectedAlignObj.left;
         const objY: number = selectedAlignObj.top;
         const objWidth: number = selectedAlignObj.width;
         const objHeight: number = selectedAlignObj.height;

         switch(type) {
            case LayoutAlignment.DIST_H:
               topLeft = Math.min(topLeft, objX);
               bottomRight = Math.max(bottomRight, objX + objWidth);
               totalSize += objWidth;
               break;
            case LayoutAlignment.DIST_V:
               topLeft = Math.min(topLeft, objY);
               bottomRight = Math.max(bottomRight, objY + objHeight);
               totalSize += objHeight;
               break;
            default:
               console.log("Distribution type not found.");
         }
      }

      const spacing: number = (bottomRight - topLeft - totalSize)
         / (selectedObjects.length - 1);
      let currPos: number = topLeft;

      for(let object of selectedObjects) {
         const selectedAlignObj = this.getAlignObj(object);
         const objWidth: number = selectedAlignObj.width;
         const objHeight: number = selectedAlignObj.height;

         switch(type) {
         case LayoutAlignment.DIST_H:
            selectedAlignObj.left = currPos;
            currPos += objWidth + spacing;
            break;
         case LayoutAlignment.DIST_V:
            selectedAlignObj.top = currPos;
            currPos += objHeight + spacing;
            break;
         default:
            console.log("Distribution type not found.");
         }
      }

      this.fireMoveResize(selectedObjects);
   }

   layoutResize(type: LayoutAlignment): void {
      let selectedObjects: any[] = this.alignObjects.filter(
         (obj) => !obj.container && obj.locked !== true);

      let size: number = -1;

      for(let object of selectedObjects) {
         const selectedAlignObj = this.getAlignObj(object);
         let objWidth: number = selectedAlignObj.width;
         let objHeight: number = selectedAlignObj.height;

         switch(type) {
            case LayoutAlignment.RESIZE_MIN_W:
               size = size == -1 ? objWidth : Math.min(size, objWidth);
               break;
            case LayoutAlignment.RESIZE_MAX_W:
               size = size == -1 ? objWidth : Math.max(size, objWidth);
               break;
            case LayoutAlignment.RESIZE_MIN_H:
               size = size == -1 ? objHeight : Math.min(size, objHeight);
               break;
            case LayoutAlignment.RESIZE_MAX_H:
               size = size == -1 ? objHeight : Math.max(size, objHeight);
               break;
            default:
               console.log("Resizing type not found.");
         }
      }

      for(let object of selectedObjects) {
         switch(type) {
         case LayoutAlignment.RESIZE_MIN_W:
         case LayoutAlignment.RESIZE_MAX_W:
            this.getAlignObj(object).width = size;
            break;
         case LayoutAlignment.RESIZE_MIN_H:
         case LayoutAlignment.RESIZE_MAX_H:
            this.getAlignObj(object).height = size;
            break;
         default:
            console.log("Resizing type not found.");
         }
      }

      this.fireMoveResize(selectedObjects);
   }

   enterParameters(): void {
      this.modelService.getModel(WORKSHEET_ENTER_PARAMS_URI + Tool.byteEncode(this.sheet.runtimeId))
         .subscribe((varInfos: VariableInfo[]) => {
            this.variableInputDialogModel = {varInfos};

            if(this.variableInputDialogModel.varInfos.length > 0) {
               this.modalService.open(this.variableInputDialog, { backdrop: "static" }).result
                  .then((result: VariableInputDialogModel) => {
                     let event = new WSCollectVariablesOverEvent(result);
                     event.setInitial(!(<Worksheet>this.sheet).init);
                     this.sheet.socketConnection
                        .sendEvent(WORKSHEET_COLLECT_VARIABLES_URI, event);
                  }, () => {
                     this.sheet.socketConnection.sendEvent(WORKSHEET_COLLECT_VARIABLES_RESTORE_URI);
                  });
            }
            else {
               this.notify({
                  type: "info",
                  message: "_#(js:common.worksheet.noParameters)"
               });
            }
         });
   }

   private openVPMPrincipalDialog(): void {
      const worksheet = this.sheet as Worksheet;

      this.modalService.open(this.vpmPrincipalDialog, {backdrop: "static"}).result
         .then((model) => {
            if(model) {
               worksheet.socketConnection.sendEvent(WORKSHEET_VPM_PRINCIPAL_SOCKET, model);
            }
      }, () => {});
   }

   newEmbeddedTable(successCallback?: () => void, cancelCallback?: () => void): void {
      let ws = this.sheet;
      this.modalService.open(this.embeddedTableDialog, {backdrop: "static"}).result
         .then((result) => {
            ws.socketConnection.sendEvent("/events/ws/dialog/embedded-table-dialog-model", result);

            if(!!successCallback) {
               successCallback();
            }
         }, () => {
            if(!!cancelCallback) {
               cancelCallback();
            }
         });
   }

   newUploadTable(showMashUp: boolean, successCallback?: (model?: any) => void, cancelCallback?: () => void): void {
      this.onOpenImportDialog.emit(true);
      this.showMashUpData = showMashUp;

      this.modalService.open(this.importCsvDialog, {size: "lg", backdrop: "static"}).result
         .then((model) => {
            if(!!successCallback) {
               successCallback(model);
            }

            this.onOpenImportDialog.emit(false);
         }, () => {
            if(!!cancelCallback) {
               cancelCallback();
            }

            this.onOpenImportDialog.emit(false);
         });
   }

   newDatabaseQuery(dataSource: string, showMashUp: boolean , successCallback?: (model?: any) => void,
                    cancelCallback?: () => void): void
   {
      this.selectedDatabaseDataSource = dataSource;
      this.wsSqlQueryController.dataSource = dataSource;
      this.newTableName = this.getNextName("SQL Query");
      this.showMashUpData = showMashUp;

      this.modalService.open(this.sqlQueryDialog,
         {size: "xl", backdrop: "static", keyboard: false}).result
         .then((result: any) => {
            this.sheet.socketConnection.sendEvent(result.controller, result.model);

            if(!!successCallback) {
               successCallback(result.model);
            }
         }, () => {
            if(!!cancelCallback) {
               cancelCallback();
            }
         });
   }

   newVariable(successCallback?: () => void, cancelCallback?: () => void): void {
      this.modalService.open(this.variableAssemblyDialog, {backdrop: "static"}).result
         .then((result: any) => {
               this.sheet.socketConnection.sendEvent(result.controller, result.model);

            if(!!successCallback) {
               successCallback();
            }
            }, () => {
            if(!!cancelCallback) {
               cancelCallback();
            }
         });
   }

   newGrouping(successCallback?: () => void, cancelCallback?: () => void): void {
      this.modalService.open(this.groupingDialog, {size: "lg", backdrop: "static"}).result
         .then(
            (result: any) => {
               this.sheet.socketConnection.sendEvent(result.controller, result.model);

               if(!!successCallback) {
                  successCallback();
               }
            }, () => {
               if(!!cancelCallback) {
                  cancelCallback();
               }
            });
   }

   newTabularQuery(tabularDataSourceType: TabularDataSourceTypeModel,
                   successCallback?: () => void, cancelCallback?: () => void): void
   {
      this.selectedDataSourceType = tabularDataSourceType;
      this.newTableName = this.getNextName("Query");
      this.modalService.open(this.tabularQueryDialog, {size: "lg", backdrop: "static"}).result
         .then((result: any) => {
            this.sheet.socketConnection.sendEvent(result.controller, result.model);

            if(!!successCallback) {
               successCallback();
            }
         }, () => {
            if(!!cancelCallback) {
               cancelCallback();
            }
         });
   }

   getNextName(prefix: string): string {
      const tableNames = this.tables.map((table) => table.name.toUpperCase());
      let i = 1;
      let name = "";

      do {
         name = prefix + i;
         i++;
      }
      while(tableNames.indexOf(name.toUpperCase()) != -1);

      return name;
   }

   concatEnabled(): boolean {
      if(!this.isInitializedWorksheet()) {
         return false;
      }

      const worksheet = this.sheet as Worksheet;
      const assemblies = worksheet.currentFocusedAssemblies as WSAssembly[];

      if(assemblies.length < 2) {
         return false;
      }

      let cols: number = -1;

      for(let i = 0; i < assemblies.length; i++) {
         const assembly = assemblies[i];

         if(assembly.classType !== "TableAssembly") {
            return false;
         }

         const table = assembly as AbstractTableAssembly;
         const columnSelection = table.getPublicColumnSelection();

         if(i === 0) {
            cols = columnSelection.length;
         }
         else if(cols !== columnSelection.length) {
            return false;
         }
      }

      return true;
   }

   newConcatTable(operatorType: "intersect" | "union" | "minus"): void {
      if(!this.concatEnabled()) {
         return;
      }

      const tables = this.sheet.currentFocusedAssemblies as AbstractTableAssembly[];
      let operator: number;

      switch(operatorType) {
         case "intersect":
            operator = XConstants.INTERSECT;
            break;
         case "union":
            operator = XConstants.UNION;
            break;
         case "minus":
            operator = XConstants.MINUS;
            break;
         default:
            console.error(`Invalid operator type: ${operatorType}`);
      }

      const event = new WSConcatenateEvent();
      event.setTables(tables.map((table) => table.name));
      event.setOperator(operator);
      this.sheet.socketConnection.sendEvent(WORKSHEET_CONCAT_TABLES_URI, event);
   }

   joinEnabled(): boolean {
      if(!this.isInitializedWorksheet()) {
         return false;
      }

      const worksheet = this.sheet as Worksheet;
      const assemblies = worksheet.currentFocusedAssemblies as WSAssembly[];

      if(assemblies.length < 2) {
         return false;
      }

      for(const assembly of assemblies) {
         if(assembly.classType !== "TableAssembly") {
            return false;
         }
      }

      return true;
   }

   newJoinTable(operatorType: "cross" | "merge" | "inner"): void {
      if(!this.joinEnabled()) {
         return;
      }

      const tables = this.sheet.currentFocusedAssemblies as AbstractTableAssembly[];
      let operator: number;

      switch(operatorType) {
         case "cross":
            operator = WorksheetTableOperator.CROSS_JOIN;
            const crossJoinEvent = new WSCrossJoinEvent();
            crossJoinEvent.setTableNames(tables.map((table) => table.name));
            this.createCrossJoin(crossJoinEvent);
            break;
         case "merge":
            operator = WorksheetTableOperator.MERGE_JOIN;
            const mergeJoinEvent = new WSMergeJoinEvent();
            mergeJoinEvent.setTableNames(tables.map((table) => table.name));
            this.sheet.socketConnection.sendEvent(
               WORKSHEET_NEW_MERGE_JOIN_URI, mergeJoinEvent);
            break;
         case "inner":
            operator = WorksheetTableOperator.INNER_JOIN;
            const innerJoinEvent = new WSJoinTablesEvent();
            innerJoinEvent.setTables(tables.map((table) => table.name));
            this.sheet.socketConnection.sendEvent(
               WORKSHEET_NEW_INNER_JOIN_URI, innerJoinEvent);
            break;
         default:
            console.error(`Invalid operator type: ${operatorType}`);
      }
   }

   createCrossJoin(crossJoinEvent: WSCrossJoinEvent): void {
      if(this.crossJoinEnabled) {
         ComponentTool
            .showConfirmDialog(this.modalService, "_#(js:Cross Join)", "_#(js:cross.join.prompt)")
            .then((result: string) => {
                  if("ok" == result) {
                     this.sheet.socketConnection.sendEvent(WORKSHEET_NEW_CROSS_JOIN_URI, crossJoinEvent);
                  }
            });
      }
   }

   layoutWorksheetGraph(): void {
      const ws: Worksheet = <Worksheet> this.sheet;
      const event: WSLayoutGraphEvent = new WSLayoutGraphEvent();

      ws.assemblies().forEach((a) => {
         event.names.push(a.name);
         event.widths.push(a.width);
         event.heights.push(a.height);
      });

      ws.socketConnection.sendEvent(WORKSHEET_LAYOUT_URI, event);
   }

   isInitializedWorksheet(): boolean {
      return this.sheet && this.sheet.type === "worksheet" && (<Worksheet> this.sheet).init;
   }

   get isWorksheet(): boolean {
      return this.sheet && this.sheet.type === "worksheet";
   }

   get isViewsheet(): boolean {
      return this.sheet && this.sheet.type === "viewsheet";
   }

   get isScript(): boolean {
      return this.focusedTab && this.focusedTab.type === "script";
   }
   get isTableStyle(): boolean {
      return this.focusedTab && this.focusedTab.type === "tableStyle";
   }

   get isObjectSelected(): boolean {
      return this.focusedObjects && this.focusedObjects.length > 0;
   }

   get isPreview(): boolean {
      return this.sheet && this.sheet.type === "viewsheet"
         && ((<Viewsheet> this.sheet).preview || (<Viewsheet> this.sheet).linkview);
   }

   scrollLeft(): void {
      this.renderer.setProperty(this.hostElement.nativeElement, "scrollLeft",
         this.hostElement.nativeElement.scrollLeft - 4);

      this.scrolling = setTimeout(() => {
         this.scrollLeft();
      }, 50);
   }

   scrollRight(): void {
      this.renderer.setProperty(this.hostElement.nativeElement, "scrollLeft",
         this.hostElement.nativeElement.scrollLeft + 4);

      this.scrolling = setTimeout(() => {
         this.scrollRight();
      }, 50);
   }

   enableLayoutAlign(): boolean {
      return this.layoutAlignEnabled || this.layoutAlignEnabled || this.layoutAlignEnabled ||
         this.layoutAlignEnabled || this.layoutAlignEnabled || this.layoutAlignEnabled ||
         this.layoutDistributeEnabled || this.layoutDistributeEnabled || this.layoutResizeEnabled ||
         this.layoutResizeEnabled || this.layoutResizeEnabled || this.layoutResizeEnabled;
   }

   get layoutShowing(): boolean {
      return this.sheet && this.isViewsheet && this.currentLayout != null;
   }

   get isPrintLayout(): boolean {
      return this.sheet && this.isViewsheet && this.currentLayout && this.currentLayout.printLayout;
   }

   editPrintHeader(): void {
      this.currentLayout.currentPrintSection = PrintLayoutSection.HEADER;
      (<Viewsheet> this.sheet).notifyLayoutChange(false);
   }

   editPrintContent(): void {
      this.currentLayout.currentPrintSection = PrintLayoutSection.CONTENT;
      (<Viewsheet> this.sheet).notifyLayoutChange(false);
   }

   editPrintFooter(): void {
      this.currentLayout.currentPrintSection = PrintLayoutSection.FOOTER;
      (<Viewsheet> this.sheet).notifyLayoutChange(false);
   }

   isPrintLayoutSelected(type: PrintLayoutSection): boolean {
      return this.currentLayout.currentPrintSection == type;
   }

   selectGuideType(type: GuideBounds): void {
      this.currentLayout.guideType = type;
      (<Viewsheet> this.sheet).currentLayoutGuides = type;
      (<Viewsheet> this.sheet).notifyLayoutChange(true);
   }

   isGuideSelected(type: GuideBounds): boolean {
      return this.currentLayout.guideType == type;
   }

   zoomLayout(zoom: ZoomOptions): void {
      if(zoom == ZoomOptions.ZOOM_OUT) {
         this.zoom(true);
      }
      else if(zoom == ZoomOptions.ZOOM_IN) {
         this.zoom();
      }
      else {
         this.vs.scale = zoom;
      }

      this.scaleService.setScale(this.vs.scale);
      this.vs.notifyLayoutChange(true);
   }

   zoom(zoomOut: boolean = false) {
      if((this.vs.scale <= 0.2 && zoomOut) || (this.vs.scale >= 2.0 && !zoomOut)) {
         return;
      }

      this.vs.scale = Tool.numberCalculate(this.vs.scale, 0.2, zoomOut);
   }

   isZoomItemSelected(zoom: ZoomOptions): boolean {
      return this.vs.scale == zoom;
   }

   zoomOutEnabled(): boolean {
      return !!this.vs && !!this.vs.scale && Number(this.vs.scale.toFixed(2)) > 0.2 &&
         Number(this.vs.scale.toFixed(2)) <= 2.0;
   }

   zoomInEnabled(): boolean {
      return !!this.vs && !!this.vs.scale && Number(this.vs.scale.toFixed(2)) >= 0.2 &&
         Number(this.vs.scale.toFixed(2)) < 2.0;
   }

   get vs(): Viewsheet {
      return (<Viewsheet> this.sheet);
   }

   get worksheetOperationsDisabled(): boolean {
      return !this.isInitializedWorksheet() || (<Worksheet> this.sheet).isCompositeView();
   }

   get hiddenComposerIcon(): boolean {
      return window.innerWidth < 350;
   }

   @HostListener("window:resize")
   resizeListener(): void {
      if(!this.sheet) {
         if(!this.fullScreen) {
            this.editCollapsed = window.innerWidth < 685;
         }
         else {
            this.editCollapsed = window.innerWidth < 705;
         }

         this.moreCollapsed = window.innerWidth < 420;
         this.mergeMenuCollapsed = window.innerWidth < 410;
      }
      else if(this.isWorksheet) {
         this.editCollapsed = window.innerWidth < 995;

         if(!this.fullScreen) {
            this.moreCollapsed = window.innerWidth < 710;
         }
         else {
            this.moreCollapsed = window.innerWidth < 735;
         }

         this.mergeMenuCollapsed = window.innerWidth < 538;
      }
      else if((<Viewsheet> this.sheet).currentLayout) {
         if(!this.fullScreen) {
            this.editCollapsed = window.innerWidth < 1080;
         }
         else {
            this.editCollapsed = window.innerWidth < 1100;
         }

         this.moreCollapsed = window.innerWidth < 730;
         this.mergeMenuCollapsed = window.innerWidth < 669;
      }
      else { // viewsheet
         if(!this.fullScreen) {
            this.editCollapsed = window.innerWidth < 945;
         }
         else {
            this.editCollapsed = window.innerWidth < 965;
         }

         this.moreCollapsed = window.innerWidth < 680;
         this.previewCollapsed = window.innerWidth < 700;
         this.mergeMenuCollapsed = window.innerWidth < 560;
      }
   }

   toggleFullScreen(): void {
      if(!this.fullScreen) {
         this.fullScreenService.enterFullScreen();
      }
      else {
         this.fullScreenService.exitFullScreen();
      }
   }

   onFullScreenChange(): void {
      this.fullScreen = this.fullScreenService.fullScreenMode;
      this.resizeListener();
   }

   getPreviewActions: ToolbarAction[] =
       [
         {
            label: "_#(js:Preview)",
            iconClass: "preview-icon",
            buttonClass: "preview-button",
            tooltip: () => !this.sheet || this.isPreview ? "" : "_#(js:fl.action.previewVSDescription)",
            enabled: () => this.sheet && !this.isPreview,
            visible: () => !this.isWorksheet && !this.isScript && !this.isTableStyle,
            action: () => this.preview()
         },
         {
            label: "_#(js:Refresh)",
            iconClass: "refresh-icon",
            buttonClass: "refresh-button",
            tooltip: () => "_#(js:Refresh)",
            enabled: () => !!this.sheet && !this.isPreview,
            visible: () => !this.isWorksheet && !this.isScript && !this.isTableStyle,
            action: () => this.refresh()
         },
       ];

   getEditActions: ToolbarAction[] =
       [
         {
            label: "_#(js:Undo)",
            iconClass: "undo-icon",
            buttonClass: "undo-button",
            tooltip: () => "<b>_#(js:composer.action.undoToolTip)</b>",
            enabled: () => this.focusedTab && !this.isPreview && this.undoEnabled,
            visible: () => !this.isScript,
            action: () => this.undo()
         },
         {
            label: "_#(js:Redo)",
            iconClass: "redo-icon",
            buttonClass: "redo-button",
            tooltip: () => "<b>_#(js:composer.action.redoToolTip)</b>",
            enabled: () => this.focusedTab && !this.isPreview && this.redoEnabled,
            visible: () => !this.isScript,
            action: () => this.redo()
         },
         {
            label: "_#(js:Cut)",
            iconClass: "cut-icon",
            buttonClass: "cut-button",
            tooltip: () => "<b>_#(js:composer.action.cutToolTip)</b>",
            enabled: () => this.sheet && !this.isPreview &&
                           this.isObjectSelected && !this.layoutShowing,
            visible: () => !this.isScript && !this.isTableStyle,
            action: () => this.cut()
         },
         {
            label: "_#(js:Copy)",
            iconClass: "copy-icon",
            buttonClass: "copy-button",
            tooltip: () => "<b>_#(js:composer.action.copyToolTip)</b>",
            enabled: () => this.sheet && !this.isPreview &&
                           this.isObjectSelected && !this.layoutShowing,
            visible: () => !this.isScript && !this.isTableStyle,
            action: () => this.copy()
         },
         {
            label: "_#(js:Paste)",
            iconClass: "paste-icon",
            buttonClass: "paste-button",
            tooltip: () => "<b>_#(js:composer.action.pasteToolTip)</b>",
            enabled: () => this.sheet && !this.isPreview &&
                           this.showPaste && !this.layoutShowing && this.pasteEnabled(),
            visible: () => !this.isScript && !this.isTableStyle,
            action: () => this.paste()
         },
         {
            label: "_#(js:Copy Format)",
            iconClass: "format-painter-icon",
            buttonClass: "format-painter-button",
            tooltip: () => "<b>_#(js:Copy Format)</b>\n" + "_#(js:fl.action.formatPainterDes)",
            enabled: () => this.sheet && !this.isPreview && this.isObjectSelected &&
                           !this.layoutShowing && !this.deployed,
            visible: () => this.isViewsheet,
            action: () => this.enableFormatPainter()
         },
      ];

   getNonMenuToolbarActions(): ToolbarAction[] {
      return <ToolbarAction[]> [
         {
            label: "_#(js:Parameters)",
            iconClass: "enter-parameter-icon",
            buttonClass: "enter-parameters-button",
            tooltip: () => "_#(js:Enter) <b>_#(js:Parameters)</b>\n" + "_#(js:fl.action.resetVariablesDes)",
            enabled: () => !this.worksheetOperationsDisabled,
            visible: () => this.isInitializedWorksheet() && !this.deployed,
            action: () => this.enterParameters()
         },
         ...this.getVPMPrincipalToolbarActions(),
         {
            label: "_#(js:Layout)",
            iconClass: "layout-icon",
            buttonClass: "layout-ws-graph-button",
            tooltip: () => "<b>_#(js:Layout)</b>\n" + "_#(js:fl.action.layoutDescription)",
            enabled: () => !this.worksheetOperationsDisabled,
            visible: () => this.isInitializedWorksheet() && !this.deployed,
            action: () => this.layoutWorksheetGraph()
         },
         {
            label: "_#(js:Options)",
            iconClass: "setting-icon",
            buttonClass: "options-button",
            tooltip: () => this.getOptionsTooltipText(),
            enabled: () => !this.isPreview,
            visible: () => this.sheet && !this.deployed || this.isScript,
            action: () => this.options()
         },
         {
            label: "Help",
            iconClass: "help-question-mark-icon",
            buttonClass: "",
            tooltip: () => "Open Session",
            enabled: () => true,
            visible: () => this.showHelpButton && this.chatService.isChatOngoing(),
            action: () => this.openSession()
         },
         {
            label: "_#(js:Full Screen)",
            iconClass: "maximize-icon",
            buttonClass: "fullscreen-button",
            tooltip: () => "<b>_#(js:Full Screen)</b>\n" + "_#(js:composer.action.fullScreenDes)",
            enabled: () => true,
            visible: () => !this.fullScreen && this.moreCollapsed,
            action: () => this.toggleFullScreen()
         },
         {
            label: "_#(js:Exit Full Screen)",
            iconClass: "collapse-icon",
            buttonClass: "fullscreen-button",
            tooltip: () => "<b>_#(js:Exit Full Screen)</b>\n" + "_#(js:composer.action.exitFullScreenDes)",
            enabled: () => true,
            visible: () => this.fullScreen && this.moreCollapsed,
            action: () => this.toggleFullScreen()
         }
      ];
   }

   getOptionsTooltipText(): string {
      if(this.isScript) {
         return "<b>_#(js:Options)</b>\n" + "_#(js:fl.action.showScriptOptions)";
      }
      else {
         return "<b>_#(js:Options)</b>\n" + "_#(js:fl.action.showSPropertyDes)";
      }
   }

   openSession(): void {
      this.chatService.openSession(this.sheet.id, this.sheet.runtimeId);
   }

   getMergeMenuToolbarActions(): ToolbarActionGroup[] {
      return <ToolbarActionGroup[]>
         [this.editOperations, this.snappingOperations, this.layoutOperations];
   }

   mergeMenuOperations: ToolbarActionGroup = {
      label: "_#(js:More)",
      iconClass: "folder-plus-icon",
      buttonClass: "folder-plus-button",
      enabled: () => true,
      visible: () => this.mergeMenuCollapsed,
      action: () => {},
      actions: this.getMergeMenuToolbarActions()
   };

   newAssetOperations: ToolbarActionGroup  = {
      label: "_#(js:Create)",
      iconClass: "creation-icon",
      buttonClass: "creation-button",
      enabled: () => true,
      visible: () => true,
      action: () => {},
      actions: [
         {
            label: "_#(js:New Worksheet)",
            iconClass: "new-worksheet-icon",
            buttonClass: "new-worksheet-button",
            tooltip: () => "<b>_#(js:New Worksheet)</b>\n" + "_#(js:fl.action.newWorksheetDes)",
            enabled: () => this.worksheetPermission,
            visible: () => !this.deployed,
            action: () => this.newWorksheet()
         },
         {
            label: "_#(js:New Viewsheet)",
            iconClass: "new-viewsheet-icon",
            buttonClass: "new-viewsheet-button",
            tooltip: () => "<b>_#(js:New Viewsheet)</b>\n" + "_#(js:fl.action.newViewsheetDes)",
            enabled: () => this.viewsheetPermission,
            visible: () => !this.deployed,
            action: () => this.openViewsheetWizard()
         }
      ]
   };

   saveOperations: ToolbarActionGroup  = {
      label: "_#(js:Save)",
      iconClass: "save-icon",
      buttonClass: "save-button",
      tooltip: () => "_#(js:Save)",
      enabled: () => this.focusedTab && !this.isPreview &&
         (this.isViewsheet || !this.deployed),
      visible: () => true,
      action: () => {},
      actions: [
         {
            label: "_#(js:Save)",
            iconClass: "save-icon",
            buttonClass: "save-button",
            tooltip: () => this.saveTooltipText(),
            enabled: () => this.focusedTab && !this.isPreview,
            visible: () => true,
            action: () => this.save()
         },
         {
            label: "_#(js:Save As)",
            iconClass: "save-as-icon",
            buttonClass: "save-as-button",
            tooltip: () => this.saveAsTooltipText(),
            enabled: () => this.focusedTab && !this.isPreview,
            visible: () => !this.deployed,
            action: () => this.saveAs()
         }
      ]
   };

   fileOperations: ToolbarActionGroup  = {
      label: "_#(js:File)",
      iconClass: "file-icon",
      buttonClass: "file-button",
      enabled: () => true,
      visible: () => this.moreCollapsed,
      tooltip: () => "<b>_#(js:Full Screen)</b>\n" + "_#(js:composer.action.fullScreenDes)",
      action: () => {},
      actions: [
         {
            label: "_#(js:Full Screen)",
            iconClass: "maximize-icon",
            buttonClass: "fullscreen-button",
            tooltip: () => "<b>_#(js:Full Screen)</b>\n" + "_#(js:composer.action.fullScreenDes)",
            enabled: () => true,
            visible: () => !this.fullScreen,
            action: () => this.toggleFullScreen()
         },
         {
            label: "_#(js:Exit Full Screen)",
            iconClass: "collapse-icon",
            buttonClass: "fullscreen-button",
            tooltip: () => "<b>_#(js:Exit Full Screen)</b>\n" + "_#(js:composer.action.exitFullScreenDes)",
            enabled: () => true,
            visible: () => this.fullScreen,
            action: () => this.toggleFullScreen()
         }
      ]
   };

   saveTooltipText(): string {
      if(this.isViewsheet) {
         return "_#(js:composer.action.saveToolTip)\n_#(js:fl.action.saveVSDescription)";
      }
      else if(this.isWorksheet) {
         return "_#(js:composer.action.saveToolTip)\n_#(js:fl.action.saveDescription)";
      }
      else if(this.isTableStyle) {
         return "_#(js:composer.action.saveToolTip)\n_#(js:fl.action.saveStyleDescription)";
      }
      else if(this.isScript) {
         return "_#(js:composer.action.saveToolTip)\n_#(js:fl.action.saveScriptDescription)";
      }
      else {
         return "";
      }
   }

   saveAsTooltipText(): string {
      if(this.isViewsheet) {
         return "<b>_#(js:Save As)</b>\n_#(js:fl.action.saveVSAsDescription)";
      }
      else if(this.isWorksheet) {
         return `<b>_#(js:Save As)</b>\n_#(js:fl.action.saveAsDescription)`;
      }
      else if(this.isTableStyle) {
         return `<b>_#(js:Save As)</b>\n_#(js:fl.action.saveStyleAsDescription)`;
      }
      else if(this.isScript) {
         return `<b>_#(js:Save As)</b>\n_#(js:fl.action.saveScriptAsDescription)`;
      }
      else {
         return "";
      }
   }

   get previewOperations(): ToolbarActionGroup {
      return <ToolbarActionGroup> {
         label: "_#(js:Preview)",
         iconClass: "preview-icon",
         buttonClass: "preview-button",
         enabled: () => true,
         visible: () => true,
         action: () => {
         },
         actions: this.getPreviewActions
      };
   }

   get editOperations(): ToolbarActionGroup {
      return <ToolbarActionGroup> {
         label: "_#(js:Edit)",
         iconClass: "edit-icon",
         buttonClass: "edit-button",
         enabled: () => true,
         visible: () => true,
         action: () => {
         },
         actions: this.getEditActions
      };
   }

   moreOperations: ToolbarActionGroup = {
      label: "_#(js:More)",
      iconClass: "folder-plus-icon",
      buttonClass: "folder-plus-button",
      enabled: () => true,
      visible: () => true,
      action: () => {},
      actions: this.getNonMenuToolbarActions()
   };

   get layoutOperations(): ToolbarActionGroup {
      return <ToolbarActionGroup> {
         label: "_#(js:Arrange)",
         iconClass: "align-top-icon",
         buttonClass: "layout-align-button",
         enabled: () => this.enableLayoutAlign() && !this.isPreview,
         visible: () => this.sheet && this.isViewsheet && !this.deployed,
         action: () => {},
         actions: [
            {
               label: "_#(js:Align Top)",
               iconClass: "align-top-icon",
               buttonClass: "layout-align-top-button",
               enabled: () => this.layoutAlignEnabled,
               visible: () => true,
               action: () => this.layoutAlign(LayoutAlignment.TOP)
            },
            {
               label: "_#(js:Align Bottom)",
               iconClass: "align-bottom-icon",
               buttonClass: "layout-align-bottom-button",
               enabled: () => this.layoutAlignEnabled,
               visible: () => true,
               action: () => this.layoutAlign(LayoutAlignment.BOTTOM)
            },
            {
               label: "_#(js:Align Middle)",
               iconClass: "align-middle-icon",
               buttonClass: "layout-align-middle-button",
               enabled: () => this.layoutAlignEnabled,
               visible: () => true,
               action: () => this.layoutAlign(LayoutAlignment.MIDDLE)
            },
            {
               label: "_#(js:Align Center)",
               iconClass: "align-center-icon",
               buttonClass: "layout-align-center-button",
               enabled: () => this.layoutAlignEnabled,
               visible: () => true,
               action: () => this.layoutAlign(LayoutAlignment.CENTER)
            },
            {
               label: "_#(js:Align Left)",
               iconClass: "align-left-icon",
               buttonClass: "layout-align-left-button",
               enabled: () => this.layoutAlignEnabled,
               visible: () => true,
               action: () => this.layoutAlign(LayoutAlignment.LEFT)
            },
            {
               label: "_#(js:Align Right)",
               iconClass: "align-right-icon",
               buttonClass: "layout-align-right-button",
               enabled: () => this.layoutAlignEnabled,
               visible: () => true,
               action: () => this.layoutAlign(LayoutAlignment.RIGHT)
            },
            {
               label: "_#(js:Distribute Horizontally)",
               iconClass: "distribute-H-icon",
               buttonClass: "layout-dist-horizontal-button",
               enabled: () => this.layoutDistributeEnabled,
               visible: () => true,
               action: () => this.layoutDistribute(LayoutAlignment.DIST_H)
            },
            {
               label: "_#(js:Distribute Vertically)",
               iconClass: "distribute-V-icon",
               buttonClass: "layout-dist-vertical-button",
               enabled: () => this.layoutDistributeEnabled,
               visible: () => true,
               action: () => this.layoutDistribute(LayoutAlignment.DIST_V)
            },
            {
               label: "_#(js:Resize Min Width)",
               iconClass: "min-width-icon",
               buttonClass: "layout-resize-min-width-button",
               enabled: () => this.layoutResizeEnabled,
               visible: () => true,
               action: () => this.layoutResize(LayoutAlignment.RESIZE_MIN_W)
            },
            {
               label: "_#(js:Resize Max Width)",
               iconClass: "max-width-icon",
               buttonClass: "layout-resize-max-width-button",
               enabled: () => this.layoutResizeEnabled,
               visible: () => true,
               action: () => this.layoutResize(LayoutAlignment.RESIZE_MAX_W)
            },
            {
               label: "_#(js:Resize Min Height)",
               iconClass: "min-height-icon",
               buttonClass: "layout-resize-min-height-button",
               enabled: () => this.layoutResizeEnabled,
               visible: () => true,
               action: () => this.layoutResize(LayoutAlignment.RESIZE_MIN_H)
            },
            {
               label: "_#(js:Resize Max Height)",
               iconClass: "max-height-icon",
               buttonClass: "layout-resize-max-height-button",
               enabled: () => this.layoutResizeEnabled,
               visible: () => true,
               action: () => this.layoutResize(LayoutAlignment.RESIZE_MAX_H)
            },
         ]
      };
   }

   get snappingOperations(): ToolbarActionGroup {
      return <ToolbarActionGroup> {
         label: "_#(js:Snapping)",
         iconClass: "snap-grid-icon",
         buttonClass: "snap-options-button",
         enabled: () => this.sheet && !this.isPreview && !this.layoutShowing,
         visible: () => this.isViewsheet,
         action: () => {},
         actions: [
            {
               label: "_#(js:Snap to Grid)",
               iconClass: "form-check-input",
               buttonClass: "form-check",
               enabled: () => true,
               visible: () => true,
               action: () => this.snapToGridChanged()
            },
            {
               label: "_#(js:Snap to Objects)",
               iconClass: "form-check-input",
               buttonClass: "form-check",
               enabled: () => true,
               visible: () => true,
               action: () => this.snapToObjectsChanged()
            }
         ]
      };
   }

   /**
    * Returns the VPM principal toolbar actions.
    * This method makes it so that the toolbar icon is styled differently when the vpm principal
    * is enabled.
    */
   private getVPMPrincipalToolbarActions(): ToolbarAction[] {
      const deactivatedAction = {
         label: "_#(js:VPM User)",
         iconClass: "vpm-principal-icon",
         buttonClass: "vpm-principal-button",
         tooltip: () => "_#(js:Set VPM) <b>_#(js:Principal)</b>\n",
         enabled: () => !this.worksheetOperationsDisabled,
         visible: () => {
            return this.isInitializedWorksheet() && !(<Worksheet>this.sheet).hasVPMPrincipal &&
               !this.deployed && this.vpmToolbarButtonsVisible;
         },
         action: () => this.openVPMPrincipalDialog()
      };

      const activatedAction = {...deactivatedAction};
      activatedAction.iconClass = activatedAction.iconClass + " toolbar-icon--activated";
      activatedAction.visible = () => {
         return this.isInitializedWorksheet() && (<Worksheet>this.sheet).hasVPMPrincipal &&
            !this.deployed && this.vpmToolbarButtonsVisible;
      };

      return [deactivatedAction, activatedAction];
   }

   get jdbcExists(): boolean {
      return this.composerToolbarService.jdbcExists;
   }

   get sqlEnabled(): boolean {
      return this.composerToolbarService.sqlEnabled;
   }

   getDatabaseLabel(database: DatabaseDataSource): string {
      const duplicate = this.databaseDataSources
         .filter(t => t.label == database.label).length > 1;
      return duplicate ? database.name : database.label;
   }

   getTabularLabel(tabular: TabularDataSourceTypeModel): string {
      const duplicate = this.tabularDataSourceTypes
         .filter(t => t.label == tabular.label).length > 1;
      return duplicate ? tabular.dataSource : tabular.label;
   }

   pasteEnabled(): boolean {
      return this.isWorksheet ? !(<Worksheet> this.sheet).isCompositeView() : true;
   }
}

class AlignLayoutDelegate {
   constructor(protected delegate: VSLayoutObjectModel) {
   }

   get left(): number {
      return this.delegate.left;
   }

   set left(value: number) {
      this.delegate.left = value;
   }

   get top(): number {
      return this.delegate.top;
   }

   set top(value: number) {
      this.delegate.top = value;
   }

   get width(): number {
      return this.delegate.width;
   }

   set width(value: number) {
      this.delegate.width = value;
   }

   get height(): number {
      return this.delegate.height;
   }

   set height(value: number) {
      this.delegate.height = value;
   }
}

class AlignObjectDelegate {
   constructor(protected delegate: VSObjectModel) {
   }

   get left(): number {
      return this.delegate.objectFormat.left;
   }

   set left(value: number) {
      this.delegate.objectFormat.left = value;
   }

   get top(): number {
      return this.delegate.objectFormat.top;
   }

   set top(value: number) {
      this.delegate.objectFormat.top = value;
   }

   get width(): number {
      return LayoutUtil.getWidth(this.delegate);
   }

   set width(value: number) {
      LayoutUtil.setWidth(this.delegate, value);
   }

   get height(): number {
      return LayoutUtil.getHeight(this.delegate);
   }

   set height(value: number) {
      LayoutUtil.setHeight(this.delegate, value);
   }
}

class AlignVSViewsheetDelegate extends AlignObjectDelegate {
   constructor(protected delegate: VSViewsheetModel) {
      super(delegate);
   }

   get left(): number {
      return this.delegate.bounds.x;
   }

   set left(value: number) {
      this.delegate.objectFormat.left = value;
   }

   get top(): number {
      return this.delegate.bounds.y;
   }

   set top(value: number) {
      this.delegate.objectFormat.top = value;
   }

   get width(): number {
      return this.delegate.bounds.width;
   }

   set width(value: number) {
      this.delegate.objectFormat.width = value;
   }

   get height(): number {
      return this.delegate.bounds.height;
   }

   set height(value: number) {
      this.delegate.objectFormat.height = value;
   }
}

class AlignLineDelegate extends AlignObjectDelegate {
   constructor(protected delegate: VSLineModel) {
      super(delegate);
   }

   get width(): number {
      return this.delegate.objectFormat.width;
   }

   set width(value: number) {
      this.delegate.startLeft = value;
      this.delegate.objectFormat.width = value;
   }

   get height(): number {
      return this.delegate.objectFormat.height;
   }

   set height(value: number) {
      this.delegate.startTop = value;
      this.delegate.objectFormat.height = value;
   }
}
