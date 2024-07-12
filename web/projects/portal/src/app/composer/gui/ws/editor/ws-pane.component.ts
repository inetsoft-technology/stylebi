/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { DOCUMENT } from "@angular/common";
import {
   ChangeDetectorRef,
   Component,
   EventEmitter,
   Inject,
   Injector,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   SimpleChanges,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { fromEvent, merge, Subscription } from "rxjs";
import { filter, map } from "rxjs/operators";
import { AssetEntry } from "../../../../../../../shared/data/asset-entry";
import { AssetType } from "../../../../../../../shared/data/asset-type";
import { DownloadService } from "../../../../../../../shared/download/download.service";
import { Tool } from "../../../../../../../shared/util/tool";
import { Notification } from "../../../../common/data/notification";
import { Point } from "../../../../common/data/point";
import { VariableInfo } from "../../../../common/data/variable-info";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { ComponentTool } from "../../../../common/util/component-tool";
import { CommandProcessor, ViewsheetClientService } from "../../../../common/viewsheet-client";
import { MessageCommand } from "../../../../common/viewsheet-client/message-command";
import { ClearLoadingCommand } from "../../../../vsobjects/command/clear-loading-command";
import { ShowLoadingMaskCommand } from "../../../../vsobjects/command/show-loading-mask-command";
import { UpdateUndoStateCommand } from "../../../../vsobjects/command/update-unto-state-command";
import { AssetTreeService } from "../../../../widget/asset-tree/asset-tree.service";
import { ConsoleMessage } from "../../../../widget/console-dialog/console-message";
import { VariableInputDialogModel } from "../../../../widget/dialog/variable-input-dialog/variable-input-dialog-model";
import { NotificationsComponent } from "../../../../widget/notifications/notifications.component";
import { DebounceService } from "../../../../widget/services/debounce.service";
import { DragService } from "../../../../widget/services/drag.service";
import { ModelService } from "../../../../widget/services/model.service";
import {
   ComposerDialogServiceFactory,
   DialogService
} from "../../../../widget/slide-out/dialog-service.service";
import { SlideOutOptions } from "../../../../widget/slide-out/slide-out-options";
import { SlideOutService } from "../../../../widget/slide-out/slide-out.service";
import { AbstractTableAssembly } from "../../../data/ws/abstract-table-assembly";
import { ColumnInfo } from "../../../data/ws/column-info";
import { ColumnMouseEvent } from "../../../data/ws/column-mouse.event";
import { ComposedTableAssembly } from "../../../data/ws/composed-table-assembly";
import { CompositeTableAssembly } from "../../../data/ws/composite-table-assembly";
import { RelationalJoinTableAssembly } from "../../../data/ws/relational-join-table-assembly";
import { RotatedTableAssembly } from "../../../data/ws/rotated-table-assembly";
import { SelectColumnSourceEvent } from "../../../data/ws/select-column-source.event";
import { SQLBoundTableAssembly } from "../../../data/ws/sql-bound-table-assembly";
import { TableAssemblyFactory } from "../../../data/ws/table-assembly-factory";
import { TabularTableAssembly } from "../../../data/ws/tabular-table-assembly";
import { Worksheet } from "../../../data/ws/worksheet";
import { WSAssembly } from "../../../data/ws/ws-assembly";
import { WSGroupingAssembly } from "../../../data/ws/ws-grouping-assembly";
import { WSTableAssembly } from "../../../data/ws/ws-table-assembly";
import { WSVariableAssembly } from "../../../data/ws/ws-variable-assembly";
import { AggregateDialog } from "../../../dialog/ws/aggregate-dialog.component";
import { AssemblyConditionDialog } from "../../../dialog/ws/assembly-condition-dialog.component";
import { SortColumnDialog } from "../../../dialog/ws/sort-column-dialog.component";
import { SQLQueryDialog } from "../../../../widget/dialog/sql-query-dialog/sql-query-dialog.component";
import { TabularQueryDialog } from "../../../dialog/ws/tabular-query-dialog.component";
import { selectingHeaderSource } from "../../../util/worksheet-util";
import { ComposerToolbarService } from "../../composer-toolbar.service";
import { ResizeHandlerService } from "../../resize-handler.service";
import { ReopenSheetCommand } from "../../vs/command/reopen-sheet-command";
import { CloseSheetCommand } from "../socket/close-sheet-command";
import { ExpiredSheetCommand } from "../socket/expired-sheet/expired-sheet-command";
import { OpenSheetEventValidator } from "../socket/open-ws/open-sheet-event-validator";
import { OpenWorksheetCommand } from "../socket/open-ws/open-ws-command";
import { OpenWorksheetEvent } from "../socket/open-ws/open-ws-event";
import { RefreshWorksheetCommand } from "../socket/refresh-worksheet-command";
import { SaveSheetCommand } from "../socket/save-sheet-command";
import { SetVPMPrincipalCommand } from "../socket/set-vpm-principal-command";
import { SetWorksheetInfoCommand } from "../socket/set-worksheet-info-command";
import { TouchAssetEvent } from "../socket/touch-asset-event";
import { WSAddAssemblyCommand } from "../socket/ws-add-assembly-command";
import { WSAssemblyEvent } from "../socket/ws-assembly-event";
import { WSCollectVariablesCommand } from "../socket/ws-collect-variables/ws-collect-variables-command";
import { WSCollectVariablesOverEvent } from "../socket/ws-collect-variables/ws-collect-variables-over-event";
import { WSExportCommand } from "../socket/ws-export-command";
import { WSFocusAssembliesCommand } from "../socket/ws-focus-assemblies-command";
import { WSInitCommand } from "../socket/ws-init-command";
import { WSInsertColumnsEvent } from "../socket/ws-insert-columns/ws-insert-columns-event";
import { WSInsertColumnsEventValidator } from "../socket/ws-insert-columns/ws-insert-columns-event-validator";
import { WSFocusCompositeTableCommand } from "../socket/ws-join/ws-focus-composite-table-command";
import { WSLoadTableDataCountCommand } from "../socket/ws-load-table-data-count-command";
import { WSMoveAssembliesCommand } from "../socket/ws-move/ws-move-assemblies-command";
import { WSMoveSchemaTablesCommand } from "../socket/ws-move/ws-move-schema-tables-command";
import { WSRefreshAssemblyCommand } from "../socket/ws-refresh-assembly/ws-refresh-assembly-command";
import { WSRemoveAssemblyCommand } from "../socket/ws-remove-assembly-command";
import { WSReplaceColumnsEvent } from "../socket/ws-replace-column/ws-replace-columns-event";
import { WSReplaceColumnsEventValidator } from "../socket/ws-replace-column/ws-replace-columns-event-validator";
import { WsChangeService } from "./ws-change.service";
import { ForceNotCloseWorksheetCommand } from "../socket/force-not-close-worksheet-command";
import { WSSetMessageLevelsCommand } from "../socket/ws-set-message-levels-command";
import { WsFinishPasteWithCutCommand } from "../socket/ws-finish-paste-with-cut-command";
import { WsSqlQueryController } from "./ws-sql-query-controller";
import { SQLConditionItemPaneProvider } from "../../../dialog/ws/sql-condition-item-pane-provider";
import { HttpClient } from "@angular/common/http";
import {
   GettingStartedService
} from "../../../../widget/dialog/getting-started-dialog/service/getting-started.service";
import { WSEditAssemblyCommand } from "../socket/ws-edit-assembly/ws-edit-assembly-command";
import { SaveWorksheetCommand } from "../socket/save-worksheet-command";

const URI_OPEN_WORKSHEET = "/events/ws/open";
const URI_OPEN_WORKSHEET_VALIDATOR = "../api/ws/open";
const URI_NEW_WORKSHEET = "/events/ws/new";
const URI_INSERT_COLUMNS_VALIDATOR = "../api/composer/worksheet/insert-columns/";
const URI_INSERT_COLUMNS = "/events/composer/worksheet/insert-columns";
const URI_REPLACE_COLUMNS_VALIDATOR = "../api/composer/worksheet/replace-columns/";
const URI_REPLACE_COLUMNS = "/events/composer/worksheet/replace-columns";
const URI_VARIABLE_INPUT = "/events/ws/dialog/variable-input-dialog";
const URI_LOAD_TABLE_DATA_COUNT = "/events/composer/worksheet/table-data-count";
const URI_MIRROR_AUTO_UPDATE = "/events/composer/worksheet/mirror-auto-update";
const URI_CANCEL_LOADING = "/events/composer/worksheet/cancel-loading";
const URI_TOUCH_ASSET = "/events/composer/touch-asset";

const TABLE_DATA_COUNT_MILLISECOND_DELAY = 500;

/**
 * The worksheet pane of the worksheet composer.
 * <p>Its purpose is to contain the worksheet environment.
 */
@Component({
   selector: "ws-pane",
   templateUrl: "ws-pane.component.html",
   styleUrls: ["ws-pane.component.scss"],
   providers: [
      ViewsheetClientService,
      DebounceService,
      {
         provide: DialogService,
         useFactory: ComposerDialogServiceFactory,
         deps: [NgbModal, SlideOutService, Injector, UIContextService]
      },
      WsChangeService
   ]
})
export class WSPaneComponent extends CommandProcessor implements OnDestroy, OnInit, OnChanges {
   /** The worksheet currently in view */
   @Input() worksheet: Worksheet;
   @Input() pasteEnabled: boolean;
   @Input() set active(active: boolean) {
      if(active) {
         this.changeDetector.reattach();
         this.initKeyListeners();
      }
      else {
         this.changeDetector.detach();
         this.destroyKeyListeners();
      }
   }

   @Output() onCut = new EventEmitter<Worksheet>();
   @Output() onCopy = new EventEmitter<Worksheet>();
   @Output() onPaste = new EventEmitter<[Worksheet, Point]>();
   @Output() onPasteWithCutFinish = new EventEmitter<[string, string[]]>();
   @Output() onRemoveWSAssembly = new EventEmitter<string>();
   @Output() onRenameWSAssembly = new EventEmitter<string[]>();
   @Output() onUpdateWorksheet = new EventEmitter<[Worksheet, Worksheet]>();
   @Output() onSheetClose = new EventEmitter<Worksheet>();
   @Output() onSheetReload = new EventEmitter<Worksheet>();
   @Output() onSaveWorksheet = new EventEmitter<{worksheet: Worksheet, close: boolean, updateDep: boolean}>();
   @Output() onTransformFinished = new EventEmitter<Worksheet>();
   @Output() onWorksheetCompositionChanged = new EventEmitter<Worksheet>();
   @Output() onEditJoin = new EventEmitter<Worksheet>();
   @Output() onWorksheetCancel = new EventEmitter<[Worksheet, number]>();
   @Output() onSaveWorksheetFinish = new EventEmitter<Worksheet>();

   /** WS dialogs */
   @ViewChild("concatenateTablesDialog") concatenateTablesDialog: TemplateRef<any>;
   @ViewChild("variableInputDialog") variableInputDialog: TemplateRef<any>;

   /** Notifications */
   @ViewChild("notifications") notifications: NotificationsComponent;

   /** Column source selection */
   selectingColumnSource = false;
   columnSourceTable: AbstractTableAssembly | null;
   joinPoint: number = -1;

   /** Information to send to certain dialogs. */
   dialogData: any;

   private focusSubscription: Subscription;
   private keyEventsSubscription: Subscription | null;
   private confirmExpiredDisplayed: boolean = false;
   private heartbeatSubscription: Subscription;
   private renameTransformSubscription: Subscription;
   private transformSubscription: Subscription;
   private dragColumnsSubscription: Subscription;
   private loadingEventCount: number = 0;
   preparingData: boolean = false;
   private firstTime: boolean = true;
   consoleMessageMap: {[assemblyName: string]: ConsoleMessage[]} = {};
   private showColumnName: boolean = false;

   get sqlEnabled(): boolean {
      return this.composerToolbarService.sqlEnabled;
   }

   get freeFormSqlEnabled(): boolean {
      return this.composerToolbarService.freeFormSqlEnabled;
   }

   get crossJoinEnabled(): boolean {
      return this.composerToolbarService.crossJoinEnabled;
   }

   get showGettingStartedMessage(): boolean {
      return this.gettingStartedService.showGettingStartedMessage;
   }

   set showGettingStartedMessage(show: boolean) {
      this.gettingStartedService.showGettingStartedMessage = show;
   }

   constructor(private resizeHandlerService: ResizeHandlerService,
               private changeDetector: ChangeDetectorRef,
               private worksheetClient: ViewsheetClientService,
               private modalService: NgbModal,
               private modelService: ModelService,
               private dragService: DragService,
               private downloadService: DownloadService,
               private dialogService: DialogService,
               private debounceService: DebounceService,
               private composerToolbarService: ComposerToolbarService,
               @Inject(DOCUMENT) document: any,
               private zone: NgZone,
               private wsChangeService: WsChangeService,
               private http: HttpClient,
               private gettingStartedService: GettingStartedService)
   {
      super(worksheetClient, zone, true);
   }

   ngOnInit(): void {
      this.worksheet.socketConnection = this.worksheetClient;
      this.setup();
      this.openWorksheet();
      this.worksheet.clearFocusedAssemblies();
      this.subscribeToFocus();
      this.initDragAssetColumnsListener();
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.hasOwnProperty("worksheet")) {
         const worksheetChanges = changes["worksheet"];
         const previousWorksheet = worksheetChanges.previousValue as Worksheet;

         // Correct loading may have been set on the previous worksheet, so need to keep in sync
         if(!worksheetChanges.isFirstChange() && previousWorksheet != null &&
            previousWorksheet.runtimeId === this.worksheet.runtimeId)
         {
            this.worksheet.loading = previousWorksheet.loading;
         }

         this.subscribeToFocus();
      }
   }

   ngOnDestroy(): void {
      this.cleanup();
      this.dragService.disposeDragDataListener(AssetTreeService.getDragName(AssetType.COLUMN));
      this.dragService.disposeDragDataListener(AssetTreeService.getDragName(AssetType.PHYSICAL_COLUMN));
      this.dragColumnsSubscription.unsubscribe();
      this.renameTransformSubscription.unsubscribe();
      this.transformSubscription.unsubscribe();
      this.destroyKeyListeners();
   }

   public onSplitDrag(): void {
      this.resizeHandlerService.onHorizontalDrag();
   }

   public concatenateTables(...tables: AbstractTableAssembly[]): void {
      this.dialogData = tables;
      this.modalService
         .open(this.concatenateTablesDialog, {backdrop: "static"}).result
         .then(() => {}, () => {});
   }

   public enterParams(varInfos?: VariableInfo[], refreshColumns: boolean = false): void {
      if(varInfos) {
         this.dialogData = {varInfos: varInfos};
      }

      this.modalService.open(this.variableInputDialog, {backdrop: "static"}).result
         .then(
            (model: VariableInputDialogModel) => {
               let event = new WSCollectVariablesOverEvent(model);
               event.setInitial(!this.worksheet.init);
               event.setRefreshColumns(refreshColumns);
               this.worksheetClient.sendEvent(URI_VARIABLE_INPUT, event);
            },
            () => {
               if(!this.worksheet.init) {
                  this.onSheetClose.emit(this.worksheet);
               }
            }
         );
   }

   public processNotification(notification: Notification): void {
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

   setup(): void {
      this.worksheet.socketConnection.connect();
      this.heartbeatSubscription = this.worksheet.socketConnection.onHeartbeat.subscribe(() => {
         this.touchAsset();
      });
   }

   cleanup(): void {
      super.cleanup();
      this.heartbeatSubscription.unsubscribe();
   }

   public getAssemblyName(): string {
      return null;
   }

   public addVariable(variable: WSVariableAssembly): void {
      const index = this.worksheet.variables.findIndex((v) => v.name === variable.name);

      if(index >= 0) {
         this.refreshAssembly(variable, index, this.worksheet.variables);
      }
      else {
         this.worksheet.variables.push(variable);
      }
   }

   public addGrouping(grouping: WSGroupingAssembly): void {
      const index = this.worksheet.groupings.findIndex((g) => g.name === grouping.name);

      if(index >= 0) {
         this.refreshAssembly(grouping, index, this.worksheet.groupings);
      }
      else {
         this.worksheet.groupings.push(grouping);
      }
   }

   editJoin(): void {
      this.onEditJoin.emit(this.worksheet);
   }

   selectCompositeTable(table: CompositeTableAssembly): void {
      this.worksheet.selectedCompositeTable = table;
      this.onWorksheetCompositionChanged.emit(this.worksheet);
   }

   worksheetCompositionChanged(): void {
      this.onWorksheetCompositionChanged.emit(this.worksheet);
   }

   worksheetCancel(): void {
      this.worksheet.current = this.joinPoint;
      this.onWorksheetCancel.emit([this.worksheet, this.joinPoint]);
   }

   cut(): void {
      this.onCut.emit(this.worksheet);
   }

   copy(): void {
      this.onCopy.emit(this.worksheet);
   }

   paste(pasteInfo: [Worksheet, Point]): void {
      this.onPaste.emit(pasteInfo);
   }

   insertColumns(insertColsEvent: WSInsertColumnsEvent): void {
      this.modelService.sendModel(URI_INSERT_COLUMNS_VALIDATOR + Tool.byteEncode(this.worksheetClient.runtimeId),
         insertColsEvent).toPromise().then((res: any) => {
         if(!!res.body) {
            let validator: WSInsertColumnsEventValidator = res.body;
            let promise = Promise.resolve(true);

            if(validator.trap) {
               promise = promise.then(
                  () => ComponentTool.showTrapAlert(this.modalService, false, validator.trap)
                     .then((buttonClicked) => buttonClicked === "yes"));
            }

            promise.then((confirmed) => {
               if(confirmed) {
                  this.worksheetClient.sendEvent(URI_INSERT_COLUMNS, insertColsEvent);
               }
            });
         }
      });
   }

   replaceColumns(replaceColsEvent: WSReplaceColumnsEvent): void {
      this.modelService.sendModel(URI_REPLACE_COLUMNS_VALIDATOR + Tool.byteEncode(this.worksheetClient.runtimeId),
          replaceColsEvent).toPromise().then((res: any) => {
         if (!!res.body) {
            let validator: WSReplaceColumnsEventValidator = res.body;
            let promise = Promise.resolve(true);

            if(validator.deletion) {
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", validator.deletion);

               return;
            }
            else if(validator.trap) {
               promise = promise.then(
                   () => ComponentTool.showTrapAlert(this.modalService, false, validator.trap)
                       .then((buttonClicked) => buttonClicked === "yes"));
            }

            promise.then((confirmed) => {
               if(confirmed) {
                  this.worksheetClient.sendEvent(URI_REPLACE_COLUMNS, replaceColsEvent);
               }
            });
         }
      });
   }

   openAssemblyConditionDialog(tableName: string): void {
      const dialog = ComponentTool.showDialog(this.dialogService, AssemblyConditionDialog, () => {},  {
         objectId: tableName,
         windowClass: "condition-dialog",
         limitResize: false
      } as SlideOutOptions);

      dialog.assemblyName = tableName;
      dialog.worksheet = this.worksheet;
   }

   openAggregateDialog(tableName: string): void {
      const onCommit = (resolve: any) => {
         if(resolve) {
            this.worksheetClient.sendEvent(resolve.controller, resolve.model);
         }
      };

      const dialog: AggregateDialog = ComponentTool.showDialog(this.dialogService, AggregateDialog,
         onCommit, {
            objectId: tableName,
            windowClass: "aggregate-dialog"
         } as SlideOutOptions);

      dialog.runtimeId = this.worksheet.runtimeId;
      dialog.tableName = tableName;
   }

   openSortColumnDialog(tableName: string): void {
      const onCommit = (resolve: any) => {
         if(resolve) {
            this.worksheetClient.sendEvent(resolve.controller, resolve.model);
         }
      };

      const dialog = ComponentTool.showDialog(this.dialogService, SortColumnDialog, onCommit, {
         objectId: tableName,
         windowClass: "sort-column-dialog"
      } as SlideOutOptions);

      dialog.runtimeId = this.worksheet.runtimeId;
      dialog.tableName = tableName;
      dialog.showColumnName = this.showColumnName;
   }

   editQuery(table: AbstractTableAssembly, singleQuery?: boolean): void {
      if(table.tableClassType == "SQLBoundTableAssembly") {
         this.openSqlQueryDialog(table as SQLBoundTableAssembly, singleQuery);
      }
      else if(table.tableClassType == "TabularTableAssembly") {
         this.openTabularQueryDialog(table as TabularTableAssembly);
      }
   }

   openSqlQueryDialog(table: SQLBoundTableAssembly, singleQuery?: boolean): void {
      const onCommit = (resolve: any) => {
         if(resolve) {
            this.worksheetClient.sendEvent(resolve.controller, resolve.model);

            if(resolve?.model?.mashUpData) {
               this.worksheet.singleQuery = false;
            }
         }
      };

      const onCancel = () => {
         if(this.worksheet.singleQuery) {
            this.onSheetClose.emit(this.worksheet);
         }
      };

      let options: SlideOutOptions = {
         objectId: table.name,
         windowClass: "query-dialog"
      };

      if(!!singleQuery) {
         options = {
            size: "xl",
            backdrop: "static",
            keyboard: false,
            popup: true
         };
      }

      const dialog = ComponentTool.showDialog(this.dialogService, SQLQueryDialog, onCommit,
         options, onCancel);

      let controller: WsSqlQueryController = new WsSqlQueryController(this.http, this.modelService);
      controller.runtimeId = this.worksheet.runtimeId;
      controller.tableName = table.name;
      controller.dataSource = table.info?.sourceInfo?.source;
      dialog.controller = controller;
      dialog.crossJoinEnabled = this.crossJoinEnabled;
      dialog.mashUpData = singleQuery;
   }

   openTabularQueryDialog(table: TabularTableAssembly): void {
      const onCommit = (resolve: any) => {
         if(resolve) {
            this.worksheetClient.sendEvent(resolve.controller, resolve.model);
         }
      };

      const dialog = ComponentTool.showDialog(this.dialogService, TabularQueryDialog, onCommit, {
         objectId: table.name,
         windowClass: "query-dialog"
      } as SlideOutOptions);

      dialog.runtimeId = this.worksheet.runtimeId;
      dialog.tableName = table.name;
      dialog.dataSourceType = table.info.dataSourceType;
   }

   toggleAutoUpdate(assembly: WSAssembly): void {
      const event = new WSAssemblyEvent();
      event.setAssemblyName(assembly.name);
      this.worksheetClient.sendEvent(URI_MIRROR_AUTO_UPDATE, event);
   }

   toggleShowColumnName(event: boolean): void {
      this.showColumnName = event;
   }

   selectColumnSource(event: SelectColumnSourceEvent): void {
      this.columnSourceTable = null;
      const {sourceTable, outerAttribute, offsetLeft} = event;
      sourceTable.setFocusedAttribute(outerAttribute, offsetLeft);
      this.worksheet.selectOnlyAssembly(sourceTable);
   }

   oozColumnMouseEvent(hcmEvent: ColumnMouseEvent): void {
      const {event, colInfo} = hcmEvent;
      this.updateSelectingColumnSource(event);
      let colSourceTable: AbstractTableAssembly | undefined;

      if(colInfo != null && this.worksheet.focusedTable instanceof ComposedTableAssembly) {
         const entity = this.getSourceTableName(colInfo);

         if(entity != null) {
            colSourceTable = this.worksheet.tables.find((table) => table.name === entity);
         }
      }

      if(colSourceTable != this.columnSourceTable) {
         this.zone.run(() => this.columnSourceTable = colSourceTable || null);
      }
   }

   private getSourceTableName(colInfo: ColumnInfo): string {
      if(this.worksheet.focusedTable instanceof RotatedTableAssembly) {
         let depends = this.worksheet.focusedTable.dependeds;
         return !!depends && depends.length != 0 ? depends[0].assemblyName : null;
      }

      return colInfo.ref.dataRefModel.entity;
   }

   cancelLoading(): void {
      this.worksheetClient.sendEvent(URI_CANCEL_LOADING);
   }

   private addTable(table: WSTableAssembly): AbstractTableAssembly {
      const tableInstance = TableAssemblyFactory.getTable(table);
      const index = this.worksheet.tables.findIndex((t) => t.name === table.name);

      // Already present, so refresh
      if(index >= 0) {
         this.refreshAssembly(tableInstance, index, this.worksheet.tables);
      }
      else {
         this.worksheet.tables.push(tableInstance);
         this.fetchTableDataCount(tableInstance);
         this.worksheet.selectOnlyAssembly(tableInstance);
      }

      return tableInstance;
   }

   private fetchTableDataCount(table: WSTableAssembly, wait: boolean = false): void {
      if(!table.rowsCompleted) {
         if(!wait) {
            const event = new WSAssemblyEvent();
            event.setAssemblyName(table.name);
            this.worksheetClient.sendEvent(URI_LOAD_TABLE_DATA_COUNT, event);
         }
         else {
            this.debounceService.debounce(`data count: ${table.name}`,
               () => this.fetchTableDataCount(table),
               TABLE_DATA_COUNT_MILLISECOND_DELAY,
               []);
         }
      }
   }

   private subscribeToFocus(): void {
      if(this.focusSubscription) {
         this.focusSubscription.unsubscribe();
         this.focusSubscription = null;
      }
   }

   private touchAsset(): void {
      if(this.worksheet.runtimeId) {
         let event = new TouchAssetEvent();
         event.setDesign(true);
         event.setChanged(this.worksheet.isModified() &&
                          this.worksheet.autoSaveTS != this.worksheet.currentTS);
         event.setUpdate(false);
         this.worksheet.autoSaveTS = this.worksheet.currentTS;
         this.worksheet.socketConnection.sendEvent(URI_TOUCH_ASSET, event);
      }
   }

   private initDragAssetColumnsListener(): void {
      const dragColumnsObs = merge(
         this.dragService.registerDragDataListener(AssetTreeService.getDragName(AssetType.COLUMN)),
         this.dragService.registerDragDataListener(AssetTreeService.getDragName(AssetType.PHYSICAL_COLUMN))).pipe(
         filter((data) => data != null),
         map((data) => {
            let entries = JSON.parse(data);

            if(!(entries instanceof Array)) {
               entries = [entries];
            }

            return entries as AssetEntry[];
         }));
      const dragEndObs = this.dragService.dragEndSubject.pipe(map(() => null));

      this.dragColumnsSubscription = merge(dragColumnsObs, dragEndObs)
         .subscribe((entries) => {
            this.worksheet.tables.forEach(table => {
               table.canDropAssetColumns = table.isValidToInsert(entries);
            });
         });

      this.renameTransformSubscription = this.worksheetClient.onRenameTransformFinished.subscribe((message) => {
         if(message.id == this.worksheet.runtimeId) {
            this.onSaveWorksheet.emit({worksheet: this.worksheet, close: false, updateDep: true});
         }
      });

      this.transformSubscription = this.worksheetClient.onTransformFinished.subscribe(
         (message) => {
            if(message.id == this.worksheet.runtimeId) {
               this.onTransformFinished.emit(this.worksheet);
            }
         });
   }

   private initKeyListeners(): void {
      this.zone.runOutsideAngular(() => {
         this.keyEventsSubscription = merge(
            fromEvent(document, "keydown"),
            fromEvent(document, "keyup")
         ).subscribe((event: KeyboardEvent) => {
            this.updateSelectingColumnSource(event);
         });
      });
   }

   private destroyKeyListeners(): void {
      if(this.keyEventsSubscription && !this.keyEventsSubscription.closed) {
         this.keyEventsSubscription.unsubscribe();
         this.keyEventsSubscription = null;
      }
   }

   private updateSelectingColumnSource(event: MouseEvent | KeyboardEvent): void {
      const selecting = selectingHeaderSource(event);

      if(this.selectingColumnSource != selecting) {
         this.zone.run(() => this.selectingColumnSource = selecting);
      }
   }

   private openWorksheet(): void {
      if(!this.worksheet.newSheet || this.worksheet.autoSaveFile != null) {
         this.openExistingWorksheet();
      }
      else {
         this.worksheet.socketConnection.sendEvent(URI_NEW_WORKSHEET, null);
      }
   }

   private openExistingWorksheet(): void {
      const event = new OpenWorksheetEvent();
      event.setId(this.worksheet.id);

      if(this.worksheet.gettingStarted) {
         event.setGettingStartedWs(true);
         event.setCreateQuery(this.gettingStartedService.isCreateQuery());
      }

      if(this.worksheet.autoSaveFile != null) {
         event.setOpenAutoSavedFile(true);
         this.worksheet.socketConnection.sendEvent(URI_OPEN_WORKSHEET, event);

         return;
      }

      this.modelService.sendModel(URI_OPEN_WORKSHEET_VALIDATOR, event).forEach((res) => {
         let promise: Promise<any> = Promise.resolve(null);
         let validator: OpenSheetEventValidator = <OpenSheetEventValidator> res.body;

         if(!!validator) {
            if(validator.forbiddenSourcesMessage) {
               ComponentTool.showMessageDialog(
                  this.modalService, "_#(js:Error)", validator.forbiddenSourcesMessage)
                  .then(() => this.onSheetClose.emit(this.worksheet));
               return;
            }

            if(validator.autoSaveFileExists) {
               promise = promise.then(() => {
                  return this.confirm("_#(js:designer.designer.autosavedFileExists)")
                     .then((val: boolean) => event.setOpenAutoSavedFile(val));
               });
            }
         }

         promise.then(() => {
            this.worksheet.socketConnection.sendEvent(URI_OPEN_WORKSHEET, event);
         });
      });
   }

   private refreshAssembly(newAssembly: WSAssembly, index: number, assemblies: WSAssembly[]): void {
      const oldAssembly = assemblies[index];

      if(this.worksheet.isAssemblyFocused(oldAssembly)) {
         this.worksheet.replaceFocusedAssembly(oldAssembly, newAssembly);
      }

      assemblies[index] = newAssembly;
      this.wsChangeService.changedAssembly(oldAssembly?.name);

      if(newAssembly.classType === "TableAssembly") {
         const table = newAssembly as WSTableAssembly;

         if(table.tableClassType === "RelationalJoinTableAssembly" && table.totalRows === 0 &&
            table.rowsCompleted && table.info.live && !table.info.runtime &&
            this.worksheet.focusedTable === table)
         {
            const message = Tool.formatCatalogString(
               "_#(js:composer.ws.joinTableLiveDataZeroRowsUserHint)", [table.name]);
            this.notifications.info(message);
            const consoleMessages = this.consoleMessageMap[table.name] || [];
            consoleMessages.push({message, type: "INFO"});
            this.consoleMessageMap[table.name] = consoleMessages;
         }
      }
   }

   private confirm(text: string): Promise<boolean> {
      return ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", text,
         {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
         .then((result: string) => result === "yes")
         .catch(() => false);
   }

   private processWSCollectVariablesCommand(command: WSCollectVariablesCommand): void {
      this.enterParams(command.varInfos, command.refreshColumns);
   }

   // After rename dependency, should reload the viewsheet so it can get latest data.
   private processReopenSheetCommand(command: ReopenSheetCommand) {
      if(command.id == this.worksheet.id) {
         this.onSheetReload.emit(this.worksheet);
      }
   }

   private processOpenWorksheetCommand(command: OpenWorksheetCommand): void {
      this.worksheet.socketConnection.runtimeId = command.worksheet.runtimeId;
      this.dialogService.setSheetId(command.worksheet.runtimeId);
      let callback = this.worksheet.callBackFun;

      // If worksheet is initialized push a new one to notify other components
      if(command.worksheet.init) {
         let update: Worksheet = new Worksheet(this.worksheet);
         update = Object.assign(update, command.worksheet);
         this.onUpdateWorksheet.emit([this.worksheet, update]);
      }
      else {
         Object.assign(this.worksheet, command.worksheet);
      }

      if(callback) {
         callback();
         this.worksheet.callBackFun = null;
      }
   }

   private processWSInitCommand(command: WSInitCommand): void {
      this.composerToolbarService.jdbcExists = command.jdbcExists;
      this.composerToolbarService.sqlEnabled = command.sqlEnabled;
      this.composerToolbarService.freeFormSqlEnabled = command.freeFormSqlEnabled;
      this.composerToolbarService.crossJoinEnabled = command.crossJoinEnabled;
   }

   private processRefreshWorksheetCommand(command: RefreshWorksheetCommand): void {
      if(this.worksheet.jspAssemblyGraph) {
         this.worksheet.jspAssemblyGraph.setSuspendDrawing(true);
      }

      const tables: AbstractTableAssembly[] =
         TableAssemblyFactory.getTables(command.assemblies
            .filter((a) => a.classType === "TableAssembly") as WSTableAssembly[]);
      const variables: WSVariableAssembly[] = command.assemblies
         .filter((a) => a.classType === "VariableAssembly") as WSVariableAssembly[];
      const groupings: WSGroupingAssembly[] = command.assemblies
         .filter((a) => a.classType === "GroupingAssembly") as WSGroupingAssembly[];

      const updatedWorksheet = new Worksheet(this.worksheet);
      updatedWorksheet.tables = tables;
      updatedWorksheet.variables = variables;
      updatedWorksheet.groupings = groupings;
      updatedWorksheet.init = true;
      updatedWorksheet.updateSecondaryAssemblyReferences();

      // usability, select primary by default
      if(this.firstTime) {
         tables.filter(t => t.primary).forEach(t => updatedWorksheet.selectAssembly(t));
         this.firstTime = false;
      }

      tables.forEach((table) => this.fetchTableDataCount(table));
      this.onUpdateWorksheet.emit([this.worksheet, updatedWorksheet]);
   }

   private processWSAddAssemblyCommand(command: WSAddAssemblyCommand): void {
      this.worksheet.jspAssemblyGraph.setSuspendDrawing(true);

      switch(command.assembly.classType) {
         case "TableAssembly":
            this.addTable(command.assembly as WSTableAssembly);
            break;
         case "VariableAssembly":
            this.addVariable(command.assembly as WSVariableAssembly);
            break;
         case "GroupingAssembly":
            this.addGrouping(command.assembly as WSGroupingAssembly);
            break;
         default:
            console.error("Adding assembly of unknown type");
      }
   }

   private processWSEditAssemblyCommand(command: WSEditAssemblyCommand): void {
      if(command.assembly?.classType == "TableAssembly") {
         this.editQuery(<AbstractTableAssembly> command.assembly, true);
      }
   }

   private processWSRefreshAssemblyCommand(command: WSRefreshAssemblyCommand): void {
      this.worksheet.jspAssemblyGraph.setSuspendDrawing(true);

      const oldName = command.oldName;
      let assembly = command.assembly;

      let index: number;
      let assemblies: WSAssembly[];

      if((index = this.worksheet.tables.findIndex((el) => el.name === oldName)) !== -1) {
         assemblies = this.worksheet.tables;
         assembly = TableAssemblyFactory.getTable(<WSTableAssembly> assembly);
         this.fetchTableDataCount(assembly as WSTableAssembly);
         const table = <WSTableAssembly> assembly;

         if(table.info.live && table.rowsCompleted && table.hasMaxRow && table.totalRows == 0 &&
            table.info.runtime == false)
         {
            this.processNotification({
               type: "info",
               message: "_#(js:composer.ws.preview.fullPreviewHint)"
            });
         }
      }
      else if((index = this.worksheet.variables.findIndex((el) => el.name === oldName)) !== -1) {
         assemblies = this.worksheet.variables;
      }
      else if((index = this.worksheet.groupings.findIndex((el) => el.name === oldName)) !== -1) {
         assemblies = this.worksheet.groupings;
      }

      // Might be a refresh command for an assembly that hasn't been added yet.
      if(index >= 0) {
         this.refreshAssembly(assembly, index, assemblies);
      }

      if(assembly != null && oldName !== assembly.name) {
         this.dialogService.objectDelete(oldName);
         this.onRenameWSAssembly.emit([oldName, assembly.name]);
      }
   }

   private processMessageCommand(command: MessageCommand): void {
      let isConfirm: boolean = false;

      switch(command.type) {
         case "OK":
            this.notifications.success(command.message);
            break;
         case "INFO":
            this.notifications.info(command.message);
            break;
         case "WARNING":
         case "ERROR":
            this.processMessageCommand0(command, this.modalService, this.worksheetClient);
            break;
         case "CONFIRM":
            isConfirm = true;
            break;
         default:
            this.notifications.warning(command.message);
      }

      if(isConfirm) {
         this.confirm(command.message).then((ok: boolean) => {
            if(ok) {
               // process confirm
               for(let key in command.events) {
                  if(command.events.hasOwnProperty(key)) {
                     let evt: any = command.events[key];
                     evt.confirmed = true;
                     this.worksheetClient.sendEvent(key, evt);
                  }
               }
            }
            else if(command.noEvents) {
               for(let key in command.noEvents) {
                  if(command.noEvents.hasOwnProperty(key)) {
                     let evt: any = command.noEvents[key];
                     evt.confirmed = true;
                     this.worksheetClient.sendEvent(key, evt);
                  }
               }
            }
         });
      }

      // add a console message
      if(!!command.message && !!command.assemblyName &&
         (command.type == "INFO" || command.type == "WARNING" || command.type == "ERROR"))
      {
         let consoleMessages = this.consoleMessageMap[command.assemblyName];

         if(!consoleMessages) {
            consoleMessages = [];
            this.consoleMessageMap[command.assemblyName] = consoleMessages;
         }

         consoleMessages.push({
            message: command.message,
            type: command.type
         });
      }
   }

   private processExpiredSheetCommand(command: ExpiredSheetCommand): void {
      if(!this.confirmExpiredDisplayed) {
         this.confirmExpiredDisplayed = true;
         this.heartbeatSubscription.unsubscribe();

         const message: string = "_#(js:common.expiredSheets)" + "_*" +
            (!!this.worksheet && !!this.worksheet.label ? "Worksheet " + this.worksheet.label
            : "worksheet ");
         this.confirm(message).then((ok) => {
            this.confirmExpiredDisplayed = false;

            if(ok) {
               this.onSheetReload.emit(this.worksheet);
            }
         });
      }
   }

   private processWSRemoveAssemblyCommand(command: WSRemoveAssemblyCommand): void {
      let index: number;
      let assemblies: WSAssembly[];
      this.dialogService.objectDelete(command.assemblyName);

      if((index = this.worksheet.tables.findIndex((el) => el.name === command.assemblyName)) !== -1) {
         assemblies = this.worksheet.tables;
      }
      else if((index = this.worksheet.variables.findIndex((el) => el.name === command.assemblyName)) !== -1) {
         assemblies = this.worksheet.variables;
      }
      else if((index = this.worksheet.groupings.findIndex((el) => el.name === command.assemblyName)) !== -1) {
         assemblies = this.worksheet.groupings;
      }

      if(index < 0) {
         this.notifications.warning(`Assembly ${command.assemblyName} was not be found in this worksheet.`);
      }
      else {
         this.worksheet.deselectAssembly(assemblies[index]);
         assemblies.splice(index, 1);
         this.onRemoveWSAssembly.emit(command.assemblyName);
      }
   }

   private processWSMoveAssembliesCommand(command: WSMoveAssembliesCommand): void {
      this.worksheet.jspAssemblyGraph.setSuspendDrawing(true);

      for(let i = 0; i < command.assemblyNames.length; i++) {
         const name = command.assemblyNames[i];
         const assembly = this.worksheet.assemblies().find((el) => el.name === name);

         if(assembly !== undefined) {
            assembly.top = command.tops[i];
            assembly.left = command.lefts[i];
         }
      }
   }

   private processWSMoveSchemaTablesCommand(command: WSMoveSchemaTablesCommand): void {
      if(this.worksheet.jspSchemaGraph) {
         this.worksheet.jspSchemaGraph.setSuspendDrawing(true);
      }

      const joinTable = this.worksheet.tables
         .find((t) => t.name === command.joinTableName);

      if(joinTable instanceof RelationalJoinTableAssembly) {
         for(let i = 0; i < command.assemblyNames.length; i++) {
            const name = command.assemblyNames[i];
            joinTable.info.schemaTableInfos[name].left = command.lefts[i];
            joinTable.info.schemaTableInfos[name].top = command.tops[i];
         }
      }
   }

   private processWSFocusCompositeTableCommand(command: WSFocusCompositeTableCommand): void {
      const joinTable = this.worksheet.tables
         .find((table) => table.name === command.compositeTableName);

      if(joinTable != null) {
         this.selectCompositeTable(joinTable as RelationalJoinTableAssembly);
         this.worksheet.clearFocusedAssemblies();
         this.worksheet.selectAssembly(joinTable);
      }
   }

   /**
    * Used to update undo/redo state of ws.
    * @param {UpdateUndoStateCommand} command
    */
   private processUpdateUndoStateCommand(command: UpdateUndoStateCommand): void {
      let update = new Worksheet(this.worksheet);
      update.points = command.points;
      update.current = command.current;
      update.currentTS = (new Date()).getTime();
      update.savePoint = command.savePoint;
      this.onUpdateWorksheet.emit([this.worksheet, update]);
   }

   private processWSExportCommand(command: WSExportCommand): void {
      this.downloadService.download("../reports" + command.url);
   }

   private processSaveSheetCommand(command: SaveSheetCommand): void {
      this.worksheet.newSheet = false;
      this.worksheet.savePoint = command.savePoint;
      this.worksheet.id = command.id;
      this.notifications.success("_#(js:common.worksheet.saveSuccess)");
      this.onSaveWorksheetFinish.emit(this.worksheet);
   }

   private processCloseSheetCommand(command: CloseSheetCommand): void {
      this.onSheetClose.emit(this.worksheet);
   }

   private processSetWorksheetInfoCommand(command: SetWorksheetInfoCommand): void {
      this.worksheet.label = command.label;
   }

   private processForceNotCloseWorksheetCommand(command: ForceNotCloseWorksheetCommand): void {
      this.worksheet.closeProhibited = command.closeProhibited;
   }

   private processWSLoadTableDataCountCommand(command: WSLoadTableDataCountCommand): void {
      const tables = this.worksheet.tables;
      const index = tables.findIndex((t) => t.name === command.name);
      const table = tables[index];

      if(table) {
         table.rowsCompleted = command.completed;
         table.totalRows = command.count;
         table.exceededMaximum = command.exceededMsg;
         const newTable = TableAssemblyFactory.getTable(table);
         this.refreshAssembly(newTable, index, tables);

         if(!newTable.rowsCompleted) {
            this.fetchTableDataCount(newTable, true);
         }
      }
   }

   private processClearLoadingCommand(command: ClearLoadingCommand): void {
      this.loadingEventCount -= command.count;
      this.updateLoadingMask();
   }

   private processShowLoadingMaskCommand(command: ShowLoadingMaskCommand): void {
      // don't increment the second command that turns on preparing data label
      if(!command.preparingData) {
         this.loadingEventCount++;
      }

      this.preparingData = command.preparingData;
      this.updateLoadingMask();
   }

   private updateLoadingMask(): void {
      this.worksheet.loading = this.loadingEventCount !== 0;
   }

   private processWSFocusAssembliesCommand(command: WSFocusAssembliesCommand): void {
      const assemblies = this.worksheet.assemblies();
      this.worksheet.currentFocusedAssemblies = command.assemblyNames.map(
         (name) => assemblies.find((a) => a.name === name));
   }

   private processSetVPMPrincipalCommand(command: SetVPMPrincipalCommand): void {
      this.worksheet.hasVPMPrincipal = command.hasVPMPrincipal;
   }

   private processWSSetMessageLevelsCommand(command: WSSetMessageLevelsCommand): void {
      this.worksheet.messageLevels = command.messageLevels;
   }

   private processWSFinishPasteWithCutCommand(command: WsFinishPasteWithCutCommand) {
      this.onPasteWithCutFinish.emit([command.sourceSheetId, command.assemblies]);
   }

   private processSaveWorksheetCommand(command: SaveWorksheetCommand) {
      this.onSaveWorksheet.emit({worksheet: this.worksheet, close: command.close, updateDep: false});
   }
}
