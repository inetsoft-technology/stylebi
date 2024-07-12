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
import {
   AfterViewInit,
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { ColumnRef } from "../../../../binding/data/column-ref";
import { AssemblyAction } from "../../../../common/action/assembly-action";
import { AssemblyActionGroup } from "../../../../common/action/assembly-action-group";
import { DateRangeRef } from "../../../../common/data/date-range-ref";
import { NumericRangeRef } from "../../../../common/data/numeric-range-ref";
import { XSchema } from "../../../../common/data/xschema";
import { StyleConstants } from "../../../../common/util/style-constants";
import { ViewsheetClientService } from "../../../../common/viewsheet-client";
import { ActionsContextmenuComponent } from "../../../../widget/fixed-dropdown/actions-contextmenu.component";
import { DropdownOptions } from "../../../../widget/fixed-dropdown/dropdown-options";
import { FixedDropdownService } from "../../../../widget/fixed-dropdown/fixed-dropdown.service";
import { AbstractTableAssembly } from "../../../data/ws/abstract-table-assembly";
import { BoundTableAssembly } from "../../../data/ws/bound-table-assembly";
import { ColumnInfo } from "../../../data/ws/column-info";
import { ComposedTableAssembly } from "../../../data/ws/composed-table-assembly";
import { EmbeddedTableAssembly } from "../../../data/ws/embedded-table-assembly";
import { MirrorTableAssembly } from "../../../data/ws/mirror-table-assembly";
import { RotatedTableAssembly } from "../../../data/ws/rotated-table-assembly";
import { TabularTableAssembly } from "../../../data/ws/tabular-table-assembly";
import { SQLBoundTableAssembly } from "../../../data/ws/sql-bound-table-assembly";
import { WSSetColumnVisibilityEvent } from "../socket/ws-set-column-visibility-event";
import { WSSortColumnEvent } from "../socket/ws-sort-column-event";
import { UnpivotTableAssembly } from "../../../data/ws/unpivot-table-assembly";
import { FeatureFlagsService, FeatureFlagValue } from "../../../../../../../shared/feature-flags/feature-flags.service";

interface ColumnButton {
   label: string;
   tooltip: string;
   clickFunction(): void;
   force: boolean;
   disabled?: boolean;
   editMode?: boolean; // enable in edit mode
}

/** Controllers */
const CONTROLLER_SET_COLUMN_VISIBILITY = "/events/composer/worksheet/set-column-visibility";
const CONTROLLER_SORT_COLUMN = "/events/composer/worksheet/sort-column";

@Component({
   selector: "ws-header-cell",
   templateUrl: "ws-header-cell.component.html",
   styleUrls: ["ws-header-cell.component.scss"]
})
export class WSHeaderCell implements OnInit, OnChanges, AfterViewInit {
   @Input() table: AbstractTableAssembly;
   @Input() colInfo: ColumnInfo;
   @Input() selected: boolean;
   @Input() canRemoveSelectedHeaders: boolean;
   @Input() focusedHeader: boolean;
   @Input() selectingColumnSource: boolean;
   @Input() showName: boolean;
   @Input() searchMatch = -1;
   @Input() searchQueryLength = -1;
   @Input() searchTarget = false;
   @Input() wrapColumnHeaders: boolean;
   @Output() onDelete = new EventEmitter<void>();
   @Output() onFormulaButtonClicked = new EventEmitter<void>();
   @Output() onAggregateButtonClicked = new EventEmitter<void>();
   @Output() onGroupButtonClicked = new EventEmitter<void>();
   @Output() onDateButtonClicked = new EventEmitter<[string, boolean]>();
   @Output() onNumericButtonClicked = new EventEmitter<[string, boolean]>();
   @Output() onChangeColumnType = new EventEmitter<void>();
   @Output() onChangeColumnDescription = new EventEmitter<void>();
   @Output() onInsertDataEvent = new EventEmitter<boolean>();
   @Output() onFocusHeaderReady = new EventEmitter<ElementRef>();
   @Output() onStartEditHeader = new EventEmitter<void>();
   @ViewChild("headerInput") headerInput: ElementRef;
   columnButtons: ColumnButton[] = [];
   caption: string;
   tooltip: string;
   displayName: string[] = ["","",""];
   columnSourceTooltip: string;
   columnSourceExists: boolean;
   isRest: boolean;

   /** If true, cell will appear grayed out */
   nonGroupColumn: boolean = false;

   constructor(public hostRef: ElementRef,
               private worksheetClient: ViewsheetClientService,
               private dropdownService: FixedDropdownService,
               private featureFlagService: FeatureFlagsService)
   {
   }

   ngOnInit(): void {
      this.initButtons();
      const info = this.table.info;

      if(info && !info.editMode && info.hasAggregate && !info.aggregate &&
         !this.colInfo.aggregate && !this.colInfo.group)
      {
         this.nonGroupColumn = true;
      }
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.hasOwnProperty("table") || changes.hasOwnProperty("colInfo")) {
         this.updateColumnCaption();
         this.updateColumnSourceFields();
         this.updateDisplayName();

         if(this.table instanceof BoundTableAssembly) {
            this.isRest = (this.table as BoundTableAssembly).info.sourceInfo.rest;
         }
      }

      if(changes.hasOwnProperty("showName") || changes.hasOwnProperty("searchMatch") ||
         changes.hasOwnProperty("searchQueryLength"))
      {
         this.updateDisplayName();
      }
   }

   ngAfterViewInit() {
      if(this.focusedHeader) {
         this.onFocusHeaderReady.emit(this.hostRef);
      }
   }

   @HostListener("contextmenu", ["$event"])
   public openContextmenu(event: MouseEvent) {
      event.preventDefault();
      event.stopPropagation();

      let options: DropdownOptions = {
         position: {x: event.clientX, y: event.clientY},
         contextmenu: true
      };

      let contextmenu: ActionsContextmenuComponent =
         this.dropdownService.open(ActionsContextmenuComponent, options).componentInstance;
      contextmenu.sourceEvent = event;
      contextmenu.actions = this.createActions();
   }

   startEditHeader(event: MouseEvent) {
      if(this.table.isCrosstabColumn(this.colInfo)) {
         return;
      }

      if((!this.showName || this.table.isWSEmbeddedTable()) &&
         event.target === event.currentTarget)
      {
         this.onStartEditHeader.emit();
      }
   }

   private updateColumnCaption(): void {
      this.tooltip = ColumnRef.getTooltip(this.colInfo.ref);
      this.caption = ColumnRef.getCaption(this.colInfo.ref);
   }

   private updateColumnSourceFields(): void {
      this.columnSourceExists = this.table instanceof RotatedTableAssembly ||
         this.table instanceof ComposedTableAssembly && this.colInfo.ref.entity != null;
      let columnSourceTooltip: string;

      if(!(this.table instanceof ComposedTableAssembly) || this.isExternalMirror(this.table)) {
         columnSourceTooltip = "_#(js:composer.ws.columnSourceTooltip.notComposedTable)";
      }
      else if(this.table instanceof RotatedTableAssembly || this.colInfo.ref.entity != null) {
         columnSourceTooltip = "_#(js:composer.ws.columnSourceTooltip.locateSource)";
      }
      else if(this.colInfo.aggregate) {
         columnSourceTooltip = "_#(js:composer.ws.columnSourceTooltip.columnAggregate)";
      }
      else if(this.colInfo.group) {
         columnSourceTooltip = "_#(js:composer.ws.columnSourceTooltip.columnGroup)";
      }
      else if(this.colInfo.crosstab) {
         columnSourceTooltip = "_#(js:composer.ws.columnSourceTooltip.columnCrosstab)";
      }
      else if(this.colInfo.ref.expression) {
         columnSourceTooltip = "_#(js:composer.ws.columnSourceTooltip.columnExpression)";
      }
      else {
         columnSourceTooltip = "_#(js:composer.ws.columnSourceTooltip.columnDefault)";
      }

      this.columnSourceTooltip = columnSourceTooltip;
   }

   private isExternalMirror(table: AbstractTableAssembly): boolean {
      return table instanceof MirrorTableAssembly && table.info.mirrorInfo.outerMirror;
   }

   private initButtons() {
      this.columnButtons = [];
      const isCrosstabColumn = this.table.isCrosstabColumn(this.colInfo);
      // for some reason we had logic to not show button other than sort in runtime mode.
      // that doesn't seem to make any sense
      //const isRuntime = this.table.isRuntime();

      if(!isCrosstabColumn && !this.colInfo.timeSeries) {
         this.columnButtons.push(this.getSortButton());
      }

      if(!isCrosstabColumn) {
         this.columnButtons.push({
            label: this.colInfo.visible ? "visible" : "invisible",
            tooltip: "_#(js:Visibility)",
            clickFunction: this.setColumnVisibility.bind(this),
            force: !this.colInfo.visible,
         });
      }

      if(this.colInfo.ref.expression) {
         this.columnButtons.push(this.getExpressionButton());
      }

      if(this.colInfo.aggregate) {
         this.columnButtons.push({
            label: "aggregate",
            tooltip: "_#(js:Aggregate)",
            clickFunction: this.clickAggregateButton.bind(this),
            force: false
         });
      }
      else if(this.colInfo.group) {
         this.columnButtons.push({
            label: "group",
            tooltip: "_#(js:Group)",
            clickFunction: this.clickGroupButton.bind(this),
            force: false
         });
      }

      let typeButton = this.getColumnDataTypeButton();

      if(typeButton != null) {
         this.columnButtons.push(typeButton);
      }
   }

   private getColumnDataTypeButton(): ColumnButton {
      let classType = this.colInfo.ref.dataRefModel.classType;

      if(classType === "DateRangeRef" || classType == "NumericRangeRef") {
         return null;
      }

      let dataType = this.colInfo.ref?.dataType;
      let label = "text";
      let tooltip = "_#(js:String Type)";

      if(dataType == XSchema.DATE) {
         label = "date";
         tooltip = "_#(js:Date Type)";
      }
      else if(dataType == XSchema.TIME) {
         label = "time";
         tooltip = "_#(js:Time Type)";
      }
      else if(dataType == XSchema.TIME_INSTANT) {
         label = "timeinstant";
         tooltip = "_#(js:TimeInstant Type)";
      }
      else if(dataType == XSchema.BOOLEAN) {
         label = "boolean";
         tooltip = "_#(js:Boolean Type)";
      }
      else if(dataType == XSchema.INTEGER) {
         label = "numeric";
         tooltip = "_#(js:Integer Type)";
      }
      else if(dataType == XSchema.DOUBLE) {
         label = "numeric";
         tooltip = "_#(js:Double Type)";
      }
      else if(dataType == XSchema.SHORT) {
         label = "numeric";
         tooltip = "_#(js:Short Type)";
      }
      else if(dataType == XSchema.LONG) {
         label = "numeric";
         tooltip = "_#(js:Long Type)";
      }
      else if(dataType == XSchema.FLOAT) {
         label = "numeric";
         tooltip = "_#(js:Float Type)";
      }
      else if(dataType == XSchema.BYTE) {
         label = "numeric";
         tooltip = "_#(js:Byte Type)";
      }
      else if(dataType == XSchema.CHARACTER || dataType == XSchema.CHAR) {
         tooltip = "_#(js:Character Type)";
      }

      return {
         label: label,
         tooltip: tooltip,
         clickFunction: this.supportChangeColumnType() ? this.clickTypeButton.bind(this) : null,
         disabled: !this.supportChangeColumnType(),
         force: true,
         editMode: true
      };
   }

   private getExpressionButton(): ColumnButton {
      let cb: ColumnButton;

      if(this.colInfo.ref.dataRefModel.classType === "DateRangeRef") {
         cb = {
            label: "date_range",
            tooltip: "_#(js:Date Range)",
            clickFunction: this.clickDateButton.bind(this),
            force: true
         };
      }
      else if(this.colInfo.ref.dataRefModel.classType === "NumericRangeRef") {
         cb = {
            label: "numeric_range",
            tooltip: "_#(js:Numeric Range)",
            clickFunction: this.clickNumericButton.bind(this),
            force: true
         };
      }
      else {
         cb = {
            label: "expression",
            tooltip: "_#(js:Formula)",
            clickFunction: this.clickExpressionButton.bind(this),
            force: true
         };
      }

      return cb;
   }

   private getSortButton(): ColumnButton {
      let label: string;
      let force = false;

      switch(this.colInfo.sortType) {
      case StyleConstants.SORT_ASC:
         label = "sort-asc";
         force = true;
         break;
      case StyleConstants.SORT_DESC:
         label = "sort-desc";
         force = true;
         break;
      case StyleConstants.SORT_NONE:
      default:
         label = "sort";
      }

      return {
         label: label,
         tooltip: "_#(js:Sort)",
         clickFunction: this.clickSortButton.bind(this),
         force: force
      };
   }

   public setColumnVisibility(showAll: boolean = false) {
      const event = new WSSetColumnVisibilityEvent();
      event.setAssemblyName(this.table.name);
      event.setColumnName([this.colInfo.name]);
      event.setShowAll(showAll);
      this.worksheetClient.sendEvent(CONTROLLER_SET_COLUMN_VISIBILITY, event);
   }

   public clickSortButton() {
      const event = new WSSortColumnEvent();
      event.setInfo(this.colInfo);
      event.setTableName(this.colInfo.assembly);
      this.worksheetClient.sendEvent(CONTROLLER_SORT_COLUMN, event);
   }

   public clickAggregateButton() {
      this.onAggregateButtonClicked.emit();
   }

   public clickGroupButton() {
      this.onGroupButtonClicked.emit();
   }

   public clickDateButton() {
      this.onDateButtonClicked.emit([this.colInfo.ref.name, false]);
   }

   public clickNumericButton() {
      this.onNumericButtonClicked.emit([this.colInfo.ref.name, false]);
   }

   public clickExpressionButton() {
      this.onFormulaButtonClicked.emit();
   }

   public clickTypeButton() {
      this.onChangeColumnType.emit();
   }

   public updateDisplayName() {
      let name = this.showName ? this.caption : this.colInfo.header;

      if(this.searchMatch != null && this.searchMatch != -1 && this.searchQueryLength > 0) {
         const p1 = this.searchMatch;
         const p2 = this.searchMatch + this.searchQueryLength;
         this.displayName = [];
         this.displayName.push(name.substring(0, p1));
         this.displayName.push(name.substring(p1, p2));
         this.displayName.push(name.substring(p2, name.length));
      }
      else {
         this.displayName = [name,"",""];
      }
   }

   public deleteColumns() {
      this.onDelete.emit();
   }

   public canCreateNumericRangeColumn(): boolean {
      const type = this.colInfo.ref.dataType;

      return !this.colInfo.ref.expression && !this.colInfo.crosstab && (
         type === XSchema.FLOAT || type === XSchema.DOUBLE ||
         type === XSchema.SHORT || type === XSchema.INTEGER ||
         type === XSchema.LONG || type === XSchema.BYTE);
   }

   public canCreateDateRangeColumn(): boolean {
      const type = this.colInfo.ref.dataType;

      return !this.colInfo.ref.expression && !this.colInfo.crosstab &&
         (type === XSchema.TIME_INSTANT || type === XSchema.DATE);
   }

   public createActions(): AssemblyActionGroup[] {
      let actions: AssemblyAction[] = [];
      actions.push(this.createColumnVisibilityAction());

      if(!this.table.isCrosstabColumn(this.colInfo)) {
         actions.push(this.createShowAllColumnsAction());
         actions.push(this.createDeleteColumnsAction());

         if(this.table instanceof EmbeddedTableAssembly) {
            actions.push(...this.createInsertColumnActions());
         }

         if(this.table.columnTypeEnabled || this.colInfo.ref.expression) {
            actions.push(this.createColumnTypeAction());
         }

         if(this.canCreateDateRangeColumn() || this.canCreateNumericRangeColumn()) {
            actions.push(this.createValueRangeAction());
         }
      }
      else {
         actions.push(this.createShowAllColumnsAction());
      }

      actions.push(this.createColumnDescriptionAction());

      return [new AssemblyActionGroup(actions)];
   }

   private createColumnVisibilityAction(): AssemblyAction {
      const enabled = !this.table.isCrosstabColumn(this.colInfo)
         && !this.table.info.editMode;

      if(this.colInfo.visible) {
         return {
            id: () => "worksheet table-header hide-column",
            label: () => "_#(js:Hide Column)",
            icon: () => "eye-off-icon",
            enabled: () => enabled,
            visible: () => true,
            action: () => this.setColumnVisibility()
         };
      }
      else {
         return {
            id: () => "worksheet table-header show-column",
            label: () => "_#(js:Show Column)",
            icon: () => "eye-icon",
            enabled: () => enabled,
            visible: () => true,
            action: () => this.setColumnVisibility()
         };
      }
   }

   private createColumnDescriptionAction(): AssemblyAction {
      return {
         id: () => "worksheet table-header column-description",
         label: () => "_#(js:Column Description)",
         icon: () => "place-holder-icon",
         enabled: () => !this.colInfo.crosstab || this.colInfo.group,
         visible: () => true,
         action: () => this.onChangeColumnDescription.emit()
      };
   }

   private createShowAllColumnsAction(): AssemblyAction {
      let enabled: boolean =
         !!this.table.info.privateSelection.find((ref) => !ref.visible)
            && !this.table.info.editMode;

      return {
         id: () => "worksheet table-header show-all-columns",
         label: () => "_#(js:Show All Columns)",
         icon: () => "place-holder-icon",
         enabled: () => enabled,
         visible: () => true,
         action: () => this.setColumnVisibility(true)
      };
   }

   private createDeleteColumnsAction(): AssemblyAction {
      return {
         id: () => "worksheet table-header delete-columns",
         label: () => "_#(js:Delete Columns)",
         icon: () => "place-holder-icon",
         enabled: () => this.canRemoveSelectedHeaders,
         visible: () => true,
         action: () => this.deleteColumns()
      };
   }

   private createInsertColumnActions(): AssemblyAction[] {
      return [
         {
            id: () => "worksheet table-header insert-column",
            label: () => "_#(js:Insert Column)",
            icon: () => "place-holder-icon",
            enabled: () => this.table.info.editMode && this.table.isWSEmbeddedTable(),
            visible: () => true,
            action: () => this.onInsertDataEvent.emit(true)
         },
         {
            id: () => "worksheet table-header append-column",
            label: () => "_#(js:Append Column)",
            icon: () => "place-holder-icon",
            enabled: () => this.table.info.editMode && this.table.isWSEmbeddedTable(),
            visible: () => true,
            action: () => this.onInsertDataEvent.emit(false)
         }
      ];
   }

   private createColumnTypeAction(): AssemblyAction {
      const enabled = this.supportChangeColumnType();

      return {
         id: () => "worksheet table-header column-type",
         label: () => "_#(js:Column Type)...",
         icon: () => "place-holder-icon",
         enabled: () => enabled,
         visible: () => true,
         action: () => this.onChangeColumnType.emit()
      };
   }

   private supportChangeColumnType(): boolean {
      const isRangeRef = this.colInfo.ref.dataRefModel.classType === "NumericRangeRef" ||
         this.colInfo.ref.dataRefModel.classType === "DateRangeRef";
      const isExpressionAggregate = this.colInfo.ref.expression && this.table.info.aggregate &&
         this.table.aggregateInfo && this.table.aggregateInfo.aggregates &&
         this.table.aggregateInfo.aggregates
            .find(ref => ref.name === this.colInfo.ref.name) != null;

      return !isRangeRef && !isExpressionAggregate && (this.colInfo.ref.expression ||
         (this.table.isWSEmbeddedTable() || this.table.isSnapshotTable() ||
            this.table instanceof EmbeddedTableAssembly ||
            this.table instanceof TabularTableAssembly ||
            this.table instanceof SQLBoundTableAssembly ||
            this.table instanceof UnpivotTableAssembly) &&
            (this.table instanceof UnpivotTableAssembly ? !this.table.info.aggregate :
            this.table.mode != "live" || !this.table.info.aggregate));
   }

   private createValueRangeAction(): AssemblyAction {
      let emission: [string, boolean] = [this.colInfo.header, true];
      const enabled = !this.table.info.editMode && (!this.colInfo || !this.colInfo.aggregate);

      return {
         id: () => "worksheet table-header value-range",
         label: () => "_#(js:New Range Column)...",
         icon: () => "place-holder-icon",
         enabled: () => enabled,
         visible: () => true,
         action: () => this.canCreateNumericRangeColumn() ?
            this.onNumericButtonClicked.emit(emission) :
            this.onDateButtonClicked.emit(emission)
      };
   }
}
