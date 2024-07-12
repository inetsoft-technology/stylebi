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
import { HttpParams } from "@angular/common/http";
import {
   Component,
   ElementRef,
   EventEmitter,
   Input,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   QueryList,
   SimpleChanges,
   TemplateRef,
   ViewChild,
   ViewChildren
} from "@angular/core";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { map } from "rxjs/operators";
import { DownloadService } from "../../../../../../../shared/download/download.service";
import { Tool } from "../../../../../../../shared/util/tool";
import { Range } from "../../../../common/data/range";
import { ComponentTool } from "../../../../common/util/component-tool";
import { ViewsheetClientService } from "../../../../common/viewsheet-client";
import { ConsoleDialogComponent } from "../../../../widget/console-dialog/console-dialog.component";
import { ConsoleMessage } from "../../../../widget/console-dialog/console-message";
import { FixedDropdownDirective } from "../../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { FormulaEditorDialogModel } from "../../../../widget/formula-editor/formula-editor-dialog-model";
import { FormulaEditorDialog } from "../../../../widget/formula-editor/formula-editor-dialog.component";
import { DragService } from "../../../../widget/services/drag.service";
import { ModelService } from "../../../../widget/services/model.service";
import { DialogService } from "../../../../widget/slide-out/dialog-service.service";
import { SlideOutOptions } from "../../../../widget/slide-out/slide-out-options";
import { AbstractTableAssembly } from "../../../data/ws/abstract-table-assembly";
import { BoundTableAssembly } from "../../../data/ws/bound-table-assembly";
import { ColumnMouseEvent } from "../../../data/ws/column-mouse.event";
import { ColumnTypeDialogModel } from "../../../data/ws/column-type-dialog-model";
import { ExpressionDialogModel } from "../../../data/ws/expression-dialog-model";
import { ExpressionDialogModelValidator } from "../../../data/ws/expression-dialog-model-validator";
import { SelectColumnSourceEvent } from "../../../data/ws/select-column-source.event";
import { SnapshotEmbeddedTableAssembly } from "../../../data/ws/snapshot-embedded-table-assembly";
import { SQLBoundTableAssembly } from "../../../data/ws/sql-bound-table-assembly";
import { TabularTableAssembly } from "../../../data/ws/tabular-table-assembly";
import { ValueRangeDialogModel } from "../../../data/ws/value-range-dialog-model";
import { ValueRangeDialogModelValidator } from "../../../data/ws/value-range-dialog-model-validator";
import { Worksheet } from "../../../data/ws/worksheet";
import { WSTableButton, WSTableMode } from "../../../data/ws/ws-table-assembly";
import { DateRangeOptionDialog } from "../../../dialog/ws/date-range-option-dialog.component";
import { NumericRangeOptionDialog } from "../../../dialog/ws/numeric-range-option-dialog.component";
import { ReorderColumnsDialog } from "../../../dialog/ws/reorder-columns-dialog.component";
import { WSAssemblyEvent } from "../socket/ws-assembly-event";
import { WSColumnDescriptionEvent } from "../socket/ws-column-description-event";
import { WSColumnTypeEvent } from "../socket/ws-column-type-event";
import { WSInsertColumnsEvent } from "../socket/ws-insert-columns/ws-insert-columns-event";
import { WSReplaceColumnsEvent } from "../socket/ws-replace-column/ws-replace-columns-event";
import { WSSetRuntimeEvent } from "../socket/ws-set-runtime-event";
import { WSAssemblyIcon } from "../ws-assembly-icon";
import { LocalStorage } from "../../../../common/util/local-storage.util";
import { ShowHideColumnsDialogComponent } from "../../../dialog/ws/show-hide-columns-dialog.component";
import { RelationalJoinTableAssembly } from "../../../data/ws/relational-join-table-assembly";
import { AbstractJoinTableAssembly } from "../../../data/ws/abstract-join-table-assembly";

interface WSTableButtonInfo {
   id: string;
   label: WSTableButton;
   viewText: string;
   clickFunction?: Function;
   disabledFunc?: Function;
}

const EXPRESSION_REST_URI = "../api/composer/ws/expression-dialog-model/";
const EXPRESSION_VALIDATION_URI = "../api/composer/ws/expression-dialog-model/";
const EXPRESSION_SOCKET_URI = "/events/ws/dialog/expression-dialog-model";
const TABLE_MODE_SOCKET_URI = "/events/composer/worksheet/table-mode/";
const TABLE_MODE_SET_RUNTIME_SOCKET_URI = "/events/composer/worksheet/table-mode/set-runtime";
const RUN_QUERY_SOCKET_URI = "/events/composer/worksheet/query/run";
const STOP_QUERY_SOCKET_URI = "/events/composer/worksheet/query/stop";
const VALUE_RANGE_REST_URI = "../api/composer/ws/value-range-option-dialog-model/";
const VALUE_RANGE_REST_VALIDATOR_URI = "../api/composer/ws/value-range-option-dialog-model/validate/";
const VALUE_RANGE_SOCKET_URI = "/events/composer/worksheet/value-range/";
const EXPORT_TABLE_URI = "../export/worksheet/";
const COLUMN_TYPE_VALIDATION_URI = "../api/composer/ws/column-type-validation/";
const COLUMN_TYPE_SOCKET_URI = "/events/composer/worksheet/column-type";
const COLUMN_DESCRIPTION_SOCKET_URI = "/events/composer/worksheet/column-description";

/**
 * The details pane of the worksheet composer
 * Its purpose is to display additional details and controls about the worksheet,
 * i.e. (selected) table contents and functions.
 */
@Component({
   selector: "ws-details-pane",
   templateUrl: "ws-details-pane.component.html",
   styleUrls: ["ws-details-pane.component.scss"]
})
export class WSDetailsPaneComponent implements OnChanges, OnDestroy, OnInit {
   @Input() worksheet: Worksheet;
   @Input() table: AbstractTableAssembly;
   @Input() selectingColumnSource: boolean;
   @Input() consoleMessages: ConsoleMessage[];
   @Input() freeFormSqlEnabled = true;
   @Output() onInsertColumns = new EventEmitter<WSInsertColumnsEvent>();
   @Output() onReplaceColumns = new EventEmitter<WSReplaceColumnsEvent>();
   @Output() onOpenAssemblyConditionDialog = new EventEmitter<string>();
   @Output() onOpenAggregateDialog = new EventEmitter<string>();
   @Output() onOpenSortColumnDialog = new EventEmitter<string>();
   @Output() onEditQuery = new EventEmitter<AbstractTableAssembly>();
   @Output() onToggleAutoUpdate = new EventEmitter<AbstractTableAssembly>();
   @Output() onToggleShowColumnName = new EventEmitter<boolean>();
   @Output() onSelectColumnSource = new EventEmitter<SelectColumnSourceEvent>();
   @Output() onOozColumnMouseEvent = new EventEmitter<ColumnMouseEvent>();
   @Output() consoleMessagesChange = new EventEmitter<ConsoleMessage[]>();
   @ViewChild("importCsvDialog") importCsvDialog: TemplateRef<any>;
   @ViewChild("columnTypeDialog") columnTypeDialog: TemplateRef<any>;
   @ViewChild("columnDescriptionDialog") columnDescriptionDialog: TemplateRef<any>;
   @ViewChild("consoleDialog") consoleDialog: ConsoleDialogComponent;
   @ViewChildren(FixedDropdownDirective) dropdowns: QueryList<FixedDropdownDirective>;
   iconCss: string;
   showName: boolean = false;

   /** Field holding data necessary for dialog inputs */
   dialogData: any;
   columnDescription: string;

   /** Callbacks for validating dialogs */
   submitColumnTypeCallback: Function;
   readonly valueRangeDialogCallback =
      (model: ValueRangeDialogModel) => this.validateValueRangeModel(model);

   tableButtons: WSTableButtonInfo[] = [];
   tableModeButtons: WSTableButtonInfo[] = [];
   rowRange: Range;

   searchBarEnabled: boolean = false;
   _searchQuery: string;
   searchIndex: number = 0;
   searchLength: number = 0;
   searchResultCount: string;
   _searchBar: ElementRef;

   get searchQuery(): string {
      return this._searchQuery;
   }

   set searchQuery(value: string) {
      this._searchQuery = value ? value.replace(/^\s+/, "").replace(/\s+$/, "") : "";
   }

   @ViewChild("searchBar") set searchBar(ref: ElementRef) {
      this._searchBar = ref;

      if(!!this._searchBar) {
         this._searchBar.nativeElement.focus();
      }
   }

   constructor(private modalService: DialogService,
               private ngbModal: NgbModal,
               private worksheetClient: ViewsheetClientService,
               private modelService: ModelService,
               private dragService: DragService,
               private downloadService: DownloadService)
   {
   }

   ngOnInit(): void {
      this.tableButtons = [
         {
            id: "ws-table-search-toggle",
            label: "search-toggle",
            viewText: "_#(js:Search Column Headers)",
            clickFunction: () => this.toggleSearchBar()
         },
         {
            id: "ws-table-wrap-column-headers",
            label: "wrap-column-headers",
            viewText: "_#(js:Wrap Column Headers)",
            clickFunction: () => this.toggleWrapColumnHeaders()
         },
         {
            id: "ws-table-condition",
            label: "condition",
            viewText: "_#(js:Define Condition)",
            clickFunction: () => this.openConditionDialog()
         },
         {
            id: "ws-table-has-condition",
            label: "has-condition",
            viewText: "_#(js:Define Condition)",
            clickFunction: () => this.openConditionDialog()
         },
         {
            id: "ws-table-group",
            label: "group",
            viewText: "_#(js:Group and Aggregate)",
            clickFunction: () => this.openAggregateDialog()
         },
         {
            id: "ws-table-sort",
            label: "sort",
            viewText: "_#(js:Sort Column_s)",
            clickFunction: () => this.onOpenSortColumnDialog.emit(this.table.name)
         },
         {
            id: "ws-table-edit-query",
            label: "edit-query",
            viewText: "_#(js:Edit Query)",
            clickFunction: () => this.onEditQuery.emit(this.table)
         },
         {
            id: "ws-table-expression",
            label: "expression",
            viewText: "_#(js:Create Expression)",
            clickFunction: () => this.openFormulaEditorDialog()
         },
         {
            id: "ws-table-show-hide-columns",
            label: "visible",
            viewText: "_#(js:Show/Hide Columns)",
            clickFunction: () => this.openShowHideColumnsDialog()
         },
         {
            id: "ws-table-reorder-columns",
            label: "reorder-columns",
            viewText: "_#(js:Reorder Table Columns)",
            disabledFunc: () => !this.isSupportChangeColumnOrder(),
            clickFunction: () => this.openReorderColumnsDialog()
         },
         {
            id: "ws-table-import-file",
            label: "import-data-file",
            viewText: "_#(js:Import Data File)",
            clickFunction: () => this.openImportCSVDialog()
         },
         {
            id: "ws-mirror-auto-update-enabled",
            label: "mirror-auto-update-enabled",
            viewText: "_#(js:Disable Auto Update)",
            clickFunction: () => this.toggleMirrorAutoUpdate()
         },
         {
            id: "ws-mirror-auto-update-disabled",
            label: "mirror-auto-update-disabled",
            viewText: "_#(js:Enable Auto Update)",
            clickFunction: () => this.toggleMirrorAutoUpdate()
         },
         {
            id: "ws-run-query",
            label: "run-query",
            viewText: "_#(js:Run Query)",
            clickFunction: () => this.runQuery()
         },
         {
            id: "ws-stop-query",
            label: "stop-query",
            viewText: "_#(js:Stop Query)",
            clickFunction: () => this.cancelQuery()
         }
      ];

      this.tableButtons.splice(0, 0, {
         id: "ws-export-table",
         label: "export",
         viewText: "_#(js:Export)",
         clickFunction: () => this.exportTable()
      });
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes["table"]) {
         this.populateTableModeButtons();
         this.rowRange = null;

         if(this.table != null) {
            this.iconCss = WSAssemblyIcon.getIcon(this.table);
         }

         if(this.searchBarEnabled &&
            this.headerNameChanged(changes["table"].currentValue, changes["table"].previousValue))
         {
            this.toggleSearchBar();
         }
      }
   }

   headerNameChanged(currentTable: AbstractTableAssembly, previousTable: AbstractTableAssembly){
      if(currentTable?.colInfos?.length == previousTable?.colInfos?.length) {

         for(let i = 0; i < currentTable.colInfos.length; i++) {
            const current = currentTable.colInfos[i].header;
            const previous = previousTable.colInfos[i].header;

            if(current != previous) {
               return true;
            }
         }
      }

      return false;
   }

   ngOnDestroy() {
   }

   private populateTableModeButtons() {
      this.tableModeButtons = [];

      if(this.table == null) {
         return;
      }

      const modes = this.table.modes;

      for(let mode of modes) {
         switch(mode) {
            case "default":
               this.tableModeButtons.push(
                  {
                     id: "ws-table-mode-default",
                     label: "default",
                     viewText: "_#(js:Meta Data View)",
                  });
               break;
            case "live":
               this.tableModeButtons.push(
                  {
                     id: "ws-table-mode-live",
                     label: "live",
                     viewText: "_#(js:Live Data View)",
                  });
               break;
            case "full":
               this.tableModeButtons.push(
                  {
                     id: "ws-table-mode-full",
                     label: "full",
                     viewText: "_#(js:Meta Detail View)",
                  });
               break;
            case "detail":
               this.tableModeButtons.push(
                  {
                     id: "ws-table-mode-detail",
                     label: "detail",
                     viewText: "_#(js:Live Detail View)",
                  });
               break;
            case "edit":
               this.tableModeButtons.push(
                  {
                     id: "ws-table-mode-edit",
                     label: "edit",
                     viewText: "_#(js:Edit)",
                  });
               break;
            default:
               console.warn("Unhandled table mode: " + mode);
         }
      }

      for(const button of this.tableModeButtons) {
         button.clickFunction = () => this.changeTableMode(button.label as WSTableMode);
      }
   }

   getTableStatus(): string {
      let status: string = "";

      if(!this.table.info.editable) {
         status += "-Not Editable";
      }

      if(this.table instanceof BoundTableAssembly) {
         if(this.table.info.sourceInfo && this.table.info.sourceInfo.view) {
            status += ` [${this.table.info.sourceInfo.view}]`;
         }
      }

      const info = this.table.info.mirrorInfo;

      if(!!info && info.outerMirror && !!info.entry && !!info.entry.description &&
         !!info.modified)
      {
         status += `-[${info.entry.description} ${info.modified}]`;
      }

      if(this.rowRange) {
         status += `: ${this.rowRange.start}-${this.rowRange.end}`;
      }

      if(this.table.totalRows != null) {
         status += `, ${this.table.totalRows} _#(js:records)`;
      }

      if(this.table.exceededMaximum) {
         status += `, ${this.table.exceededMaximum}`;

         if(this.table instanceof RelationalJoinTableAssembly) {
            status += ` _#(js:composer.ws.preview.limitedPreviewForJoin)`;
         }
      }
      else if(this.table.info.runtime) {
         status += " <FULL PREVIEW>";
      }
      else if(this.table.info.live && !this.table.isEmbeddedTable() &&
         !(this.table instanceof TabularTableAssembly))
      {
         status += `, _#(js:composer.ws.preview.limitedPreview)`;

         if(this.table instanceof AbstractJoinTableAssembly) {
            status += ` _#(js:composer.ws.preview.limitedPreviewForJoin)`;
         }
      }

      return status;
   }

   dataModeEnabled(): boolean {
      return this.table && (this.table.isRuntime() || this.table.mode === "live");
   }

   updateRowRange(rowRange: Range) {
      this.rowRange = rowRange;
   }

   selectColumnSource(event: SelectColumnSourceEvent): void {
      this.onSelectColumnSource.emit(event);
   }

   oozColumnMouseEvent(event: ColumnMouseEvent): void {
      this.onOozColumnMouseEvent.emit(event);
   }

   openAggregateDialog() {
      this.onOpenAggregateDialog.emit(this.table.name);
   }

   openFormulaEditorDialog(columnIndex?: number) {
      let params = new HttpParams().set("tableName", Tool.byteEncode(this.table.name));
      params = params.set("showOriginalName", "true");

      if(columnIndex != null) {
         params = params.set("columnIndex", "" + columnIndex);
      }

      this.modelService.getModel(EXPRESSION_REST_URI + Tool.byteEncode(this.worksheet.runtimeId), params)
         .subscribe((model: ExpressionDialogModel) => {
            const onCommit = (result: any) => {
               const newModel: ExpressionDialogModel = {
                  tableName: model.tableName,
                  oldName: model.oldName,
                  newName: result.formulaName,
                  dataType: result.dataType,
                  formulaType: result.formulaType,
                  expression: result.expression
               };

               this.worksheetClient.sendEvent(EXPRESSION_SOCKET_URI, newModel);
            };

            const dialog = ComponentTool.showDialog(this.modalService, FormulaEditorDialog, onCommit, {
               objectId: this.table.name,
               windowClass: "formula-dialog",
               limitResize: false
            } as SlideOutOptions);
            dialog.formulaName = model.oldName;
            dialog.dataType = model.dataType;
            dialog.formulaType = model.formulaType;
            dialog.expression = model.expression;
            dialog.columnTreeRoot = model.columnTree;
            dialog.scriptDefinitions = model.scriptDefinitions;
            dialog.isVSContext = false;
            dialog.submitCallback = (formulaModel: FormulaEditorDialogModel) =>
               this.submitExpressionCallback(model, formulaModel);
            dialog.sqlMergeable = model.sqlMergeable;
            dialog.checkDuplicatesInColumnTree = false;
            dialog.showOriginalName = true;
         });
   }

   openConditionDialog() {
      if(this.table.isEmbeddedTable()) {
         ComponentTool.showMessageDialog(this.ngbModal, "_#(js:Info)",
                                         "_#(js:composer.ws.filter-snapshopt)");
         return;
      }

      this.onOpenAssemblyConditionDialog.emit(this.table.name);
   }

   openShowHideColumnsDialog() {
      const onCommit = (resolve: any) => {
         if(resolve) {
            this.worksheetClient.sendEvent(resolve.controller, resolve.model);
         }
      };

      const dialog = ComponentTool.showDialog(this.modalService, ShowHideColumnsDialogComponent, onCommit, {
         objectId: this.table.name,
      } as SlideOutOptions);

      dialog.runtimeId = this.worksheet.runtimeId;
      dialog.table = this.table;
      dialog.showColumnName = this.showName;
   }

   isSupportChangeColumnOrder() {
      let tableClassType = this.table?.tableClassType;

      if((this.table.mode == "default" || this.table.mode == "live") &&
         this.table.aggregateInfo != null && this.table.aggregateInfo.crosstab &&
         this.table.aggregateInfo.groups != null)
      {
         return false;
      }

      if(tableClassType != "SQLBoundTableAssembly") {
         return true;
      }

      // it's risky and not significant to update sql string when change column order,
      // do don't support change order for sql edited table(59268).
      return !(this.table as SQLBoundTableAssembly).sqlEdited;
   }

   openReorderColumnsDialog() {
      if(this.table.isSnapshotTable()) {
         ComponentTool.showMessageDialog(this.ngbModal, "_#(js:Info)",
                                         "_#(js:composer.ws.reorder-snapshopt)");
         return;
      }

      const onCommit = (resolve: any) => {
         if(resolve) {
            this.worksheetClient.sendEvent(resolve.controller, resolve.model);
         }
      };

      const dialog = ComponentTool.showDialog(this.modalService, ReorderColumnsDialog, onCommit, {
         objectId: this.table.name,
      } as SlideOutOptions);

      dialog.runtimeId = this.worksheet.runtimeId;
      dialog.tableName = this.table.name;
      dialog.showColumnName = this.showName;
   }

   public openDateRangeOptionDialog(column: string, source: boolean) {
      this.openValueRangeDialog(column, source, false);
   }

   public openNumericRangeOptionDialog(column: string, source: boolean) {
      this.openValueRangeDialog(column, source, true);
   }

   private openValueRangeDialog(column: string, source: boolean, numeric: boolean) {
      const params = this.getValueRangeParams(column, source, numeric);

      this.modelService.getModel(VALUE_RANGE_REST_URI + Tool.byteEncode(this.worksheet.runtimeId), params)
         .subscribe((model: ValueRangeDialogModel) => {
            const onCommit = (resolve: any) => {
               if(resolve) {
                  const controller = VALUE_RANGE_SOCKET_URI + Tool.byteEncode(this.table.name) +
                     (source ? `/${Tool.byteEncode(column)}` : "");
                  this.worksheetClient.sendEvent(controller, resolve);
               }
            };

            if(numeric) {
               this.createNumericRangeOptionDialog(model, onCommit);
            }
            else {
               this.createDateRangeOptionDialog(model, onCommit);
            }
         });
   }

   private toggleSearchBar() {
      this.searchBarEnabled = !this.searchBarEnabled;

      if(!this.searchBarEnabled) {
         this.clearSearch();
      }
   }

   public checkEnterKey(event: KeyboardEvent){
      if(event.keyCode == 13) {
         this.searchNext();
      }
   }

   public clearSearch() {
      this.searchQuery = null;
   }

   public searchPrevious() {
      this.searchIndex --;

      if(this.searchIndex <= 0) {
         this.searchIndex = this.searchLength;
      }

      this.searchResultCount = this.searchIndex + "/" + this.searchLength;
   }

   public searchNext() {
      this.searchIndex ++;

      if(this.searchLength == 0) {
         this.searchIndex = 0;
      }
      else if(this.searchIndex > this.searchLength) {
         this.searchIndex = 1;
      }

      this.searchResultCount = this.searchIndex + "/" + this.searchLength;
   }

   public onSearchResultUpdate(index: number, length: number) {
      this.searchIndex = index;
      this.searchLength = length;

      if(this.searchLength != 0 &&
         (this.searchIndex <= 0 || this.searchIndex > this.searchLength))
      {
         this.searchIndex = 1;
      }

      setTimeout(() => {
         this.searchResultCount = this.searchIndex + "/" + this.searchLength;
      });
   }

   private createNumericRangeOptionDialog(
      model: ValueRangeDialogModel, onCommit: (value: any) => any)
   {
      const dialog = ComponentTool.showDialog(this.modalService, NumericRangeOptionDialog, onCommit, {
         objectId: this.table.name,
      } as SlideOutOptions);

      dialog.model = model;
      dialog.submitCallback = this.valueRangeDialogCallback;
   }

   private createDateRangeOptionDialog(
      model: ValueRangeDialogModel, onCommit: (value: any) => any)
   {
      const dialog = ComponentTool.showDialog(this.modalService, DateRangeOptionDialog, onCommit, {
         objectId: this.table.name,
      } as SlideOutOptions);

      dialog.model = model;
      dialog.submitCallback = this.valueRangeDialogCallback;
   }

   public openImportCSVDialog() {
      this.ngbModal.open(this.importCsvDialog, {size: "lg", backdrop: "static"}).result
         .then(() => {}, () => {});
   }

   public openChangeColumnTypeDialog(columnIndex: number) {
      this.dialogData = this.table.colInfos[columnIndex];
      this.submitColumnTypeCallback = this._submitColumnTypeCallback.bind(this, columnIndex);

      this.ngbModal.open(this.columnTypeDialog, {backdrop: "static"}).result
         .then((result: any) => {
            const event: WSColumnTypeEvent = new WSColumnTypeEvent();
            event.setTableName(this.table.name);
            event.setColumnIndex(columnIndex);
            event.setDataType(result.dataType);
            event.setFormatSpec(result.formatSpec);
            event.setLive(this.table.mode == "live");
            this.worksheetClient.sendEvent(COLUMN_TYPE_SOCKET_URI, event);
         }, () => {});
   }

   public openChangeColumnDesDialog(columnIndex: number) {
      const col = this.table.colInfos[columnIndex];
      this.columnDescription = col == null || col.ref == null ? "" : col.ref.description;

      this.ngbModal.open(this.columnDescriptionDialog, {backdrop: "static"}).result
         .then((result: any) => {
            const event: WSColumnDescriptionEvent = new WSColumnDescriptionEvent();
            event.setTableName(this.table.name);
            event.setColumnIndex(col.index);
            event.setDescription(result);
            this.worksheetClient.sendEvent(COLUMN_DESCRIPTION_SOCKET_URI, event);
         }, () => {});
   }

   private getValueRangeParams(column: string, source: boolean, numeric: boolean): HttpParams {
      let params = new HttpParams()
         .set("table", Tool.byteEncode(this.table.name))
         .set("numeric", "" + numeric);

      if(!source) {
         params = params.set("expcolumn", column);
      }
      else {
         params = params.set("fromcolumn", column);
      }

      return params;
   }

   private validateValueRangeModel(model: ValueRangeDialogModel): Promise<boolean> {
      if(model.newName != null) {
         const uri = VALUE_RANGE_REST_VALIDATOR_URI + Tool.byteEncode(this.worksheet.runtimeId);
         const params = new HttpParams().set("table", Tool.byteEncode(this.table.name));

         return this.modelService.sendModel<ValueRangeDialogModelValidator>(uri, model, params).pipe(
            map(response => response.body),
            map((validator) => {
               if(validator.invalidName != null) {
                  ComponentTool.showMessageDialog(this.ngbModal, "_#(js:Error)", validator.invalidName,
                     {"ok": "OK"}, {backdrop: false});
                  return false;
               }

               return true;
            })
         ).toPromise();
      }
      else {
         return Promise.resolve(true);
      }
   }

   public changeTableMode(mode: WSTableMode): void {
      const event = new WSAssemblyEvent();
      event.setAssemblyName(this.table.name);
      this.worksheetClient.sendEvent(TABLE_MODE_SOCKET_URI + mode, event);
   }

   public setRuntime(runtimeSelected: boolean) {
      this.dropdowns.forEach(d => d.close());
      const event = new WSSetRuntimeEvent();
      event.setTableName(this.table.name);
      event.setRuntimeSelected(runtimeSelected);
      this.worksheetClient.sendEvent(TABLE_MODE_SET_RUNTIME_SOCKET_URI, event);
   }

   public setShowName(show: boolean) {
      this.dropdowns.forEach(d => d.close());
      this.showName = show;
      this.onToggleShowColumnName.emit(this.showName);
   }

   public getTableModeButton(mode: string): WSTableButtonInfo {
      return this.tableModeButtons.find(el => el.label === mode);
   }

   public exportTable() {
      const uri = EXPORT_TABLE_URI +
         Tool.encodeURIPath(this.worksheet.runtimeId) + "/" +
         encodeURIComponent(this.table.name);
      this.downloadService.download(uri);
   }

   private toggleMirrorAutoUpdate() {
      this.onToggleAutoUpdate.emit(this.table);
   }

   /**
    * Function to be used on the submission of the formula editor dialog.
    *
    * @param populatedModel the initial dialog model which came from the server.
    * @param formulaEditorDialogModel the model from the formula editor dialog.
    *
    * @returns a promise which returns true if the model is valid and should be submitted,
    *            false otherwise.
    */
   private submitExpressionCallback(
      populatedModel: ExpressionDialogModel,
      formulaEditorDialogModel: FormulaEditorDialogModel): Promise<boolean>
   {
      let model: ExpressionDialogModel = {
         tableName: populatedModel.tableName,
         oldName: populatedModel.oldName,
         newName: formulaEditorDialogModel.formulaName,
         dataType: formulaEditorDialogModel.dataType,
         formulaType: formulaEditorDialogModel.formulaType,
         expression: formulaEditorDialogModel.expression
      };

      return this.modelService.sendModel(EXPRESSION_VALIDATION_URI + Tool.byteEncode(this.worksheet.runtimeId), model)
         .toPromise().then((res: any) => {
            let promise: Promise<boolean> = Promise.resolve(true);

            if(!!res.body) {
               let validator: ExpressionDialogModelValidator = res.body;

               if(validator.invalidName) {
                  promise = promise
                     .then(() => ComponentTool.showMessageDialog(this.ngbModal, "_#(js:Error)", validator.invalidName,
                        {"ok": "OK"},
                        {backdrop: false })
                        .then(() => false));
               }

               if(validator.invalidExpression) {
                  promise = promise
                     .then(() => ComponentTool.showConfirmDialog(this.ngbModal, "_#(js:Confirm)", validator.invalidExpression,
                        {"yes": "Yes", "no": "No"},
                        {backdrop: false })
                        .then((buttonClicked) => buttonClicked === "yes"));
               }
            }

            return promise;
         });
   }

   private _submitColumnTypeCallback(
      index: number, dialogModel: ColumnTypeDialogModel, confirmed: boolean = false): Promise<boolean>
   {
      let model: WSColumnTypeEvent = new WSColumnTypeEvent();
      model.setTableName(this.table.name);
      model.setColumnIndex(index);
      model.setDataType(dialogModel.dataType);
      model.setFormatSpec(dialogModel.formatSpec);
      model.setLive(this.table.mode == "live");
      model.setConfirmed(confirmed || !!dialogModel.removeNonconvertible);

      return this.modelService.sendModel(COLUMN_TYPE_VALIDATION_URI + Tool.byteEncode(this.worksheet.runtimeId), model)
         .toPromise().then((res: any) => {
            let promise: Promise<boolean> = Promise.resolve(true);

            if(!!res.body) {
               let message = res.body;

               if(!confirmed) {
                  promise = ComponentTool.showConfirmDialog(this.ngbModal, "_#(js:Confirm)", message,
                     {"yes": "_#(js:Continue)", "no": "_#(js:Cancel)"})
                     .then((val: string) => {
                        if("yes" == val) {
                           return this._submitColumnTypeCallback(index, dialogModel, true);
                        }

                        return false;
                     });
               }

               // promise = promise.then(() => ComponentTool.showMessageDialog(this.ngbModal, "_#(js:Error)", message, {"ok": "OK"}, {
               //    backdrop: false,
               // }).then(() => false));
            }

            return promise;
         });
   }

   private runQuery() {
      const event = new WSAssemblyEvent();
      event.setAssemblyName(this.table.name);
      this.worksheet.socketConnection.sendEvent(RUN_QUERY_SOCKET_URI, event);
   }

   private cancelQuery() {
      const event = new WSAssemblyEvent();
      event.setAssemblyName(this.table.name);
      this.worksheet.socketConnection.sendEvent(STOP_QUERY_SOCKET_URI, event);
   }

   get formatAll(): boolean {
      return this.table instanceof TabularTableAssembly ||
         this.table instanceof SnapshotEmbeddedTableAssembly;
   }

   openConsoleDialog(): void {
      const options: NgbModalOptions = {
         backdrop: "static",
         windowClass: "console-dialog"
      };
      this.ngbModal.open(this.consoleDialog, options).result
         .then((messageLevels: string[]) => {
            this.worksheet.messageLevels = messageLevels;
            this.consoleMessagesChange.emit(this.consoleMessages);
         }, () => {
            this.consoleMessagesChange.emit(this.consoleMessages);
         });
   }

   isTableButtonVisible(button: WSTableButtonInfo): boolean {
      return this.table.tableButtons.indexOf(button.label) !== -1 &&
         !(button.id === "ws-table-edit-query" &&
            this.table.tableClassType === "SQLBoundTableAssembly" &&
            !this.freeFormSqlEnabled && (this.table as SQLBoundTableAssembly).sqlEdited) &&
         (button.label != "visible" || this.table.mode != "edit");
   }

   isTableButtonToggled(button: WSTableButtonInfo): boolean {
      return this.table.tableButtons.indexOf(button.label) !== -1 &&
         (button.id === "ws-table-search-toggle" && this.searchBarEnabled ||
         button.id === "ws-table-wrap-column-headers" && this.isWrapColumnHeadersEnabled());
   }

   private toggleWrapColumnHeaders(): void {
      LocalStorage.setItem("ws.table.wrap-column-headers", !this.isWrapColumnHeadersEnabled() + "");
   }

   isWrapColumnHeadersEnabled(): boolean {
      return "true" === LocalStorage.getItem("ws.table.wrap-column-headers");
   }
}
