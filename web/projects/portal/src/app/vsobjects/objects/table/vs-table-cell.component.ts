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
   ChangeDetectionStrategy,
   ChangeDetectorRef,
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   Renderer2,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { NgbDateStruct } from "@ng-bootstrap/ng-bootstrap";
import { Tool } from "../../../../../../shared/util/tool";
import { HyperlinkModel } from "../../../common/data/hyperlink-model";
import { TableDataPathTypes } from "../../../common/data/table-data-path-types";
import { GuiTool } from "../../../common/util/gui-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client/viewsheet-client.service";
import { Viewsheet } from "../../../composer/data/vs/viewsheet";
import { DropdownOptions } from "../../../widget/fixed-dropdown/dropdown-options";
import { DropdownRef } from "../../../widget/fixed-dropdown/fixed-dropdown-ref";
import { FixedDropdownService } from "../../../widget/fixed-dropdown/fixed-dropdown.service";
import { DebounceService } from "../../../widget/services/debounce.service";
import { ContextProvider } from "../../context-provider.service";
import { VSAnnotationModel } from "../../model/annotation/vs-annotation-model";
import { BaseTableCellModel } from "../../model/base-table-cell-model";
import { BaseTableModel } from "../../model/base-table-model";
import { ColumnOptionType } from "../../model/column-option-type";
import { DataTipService } from "../data-tip/data-tip.service";
import { PopComponentService } from "../data-tip/pop-component.service";
import { VSTableCellCalendar } from "./vs-table-cell-calendar.component";
import { CellBindingInfo } from "../../../binding/data/table/cell-binding-info";
import { XSchema } from "../../../common/data/xschema";

export enum CellType {
   TEXT,
   HTML,
   IMAGE,
   PRESENTER,
   HTML_PRESENTER,
   INPUT
}

/**
 * Represents a cell on a table for interactions. Needs to use an attribute selector
 * because display table-cell doesn't let you use certain td attributes like rowspan
 * and colspan which we require
 */
/* eslint-disable */
@Component({
   selector: "vs-table-cell,[vs-table-cell]",
   templateUrl: "vs-table-cell.component.html",
   styleUrls: ["vs-table-cell.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush,
   exportAs: "vsTableCell"
})
/* eslint-enable */
export class VSTableCell implements OnInit, OnChanges, OnDestroy {
   @Input() cell: BaseTableCellModel;
   @Input() table: BaseTableModel;
   @Input() viewsheet: Viewsheet;
   @Input() cellAnnotations: VSAnnotationModel[];
   @Input() isHeader: boolean = false;
   @Input() isForm: boolean = false;
   @Input() formVisible: boolean = false;
   @Input() columnEditorEnabled: boolean = false;
   @Input() isWrapped: boolean = false;
   @Input() dataTip: string = null;
   @Input() isFlyOnClick: boolean = false;
   @Input() width: number = 0;
   @Input() height: number = 0;
   @Input() linkUri: string;
   @Input() isRendered: boolean = true;
   @Input() selectedDataIndex;
   @Input() dataCellIndex;
   @Input() aggregateNames: string[];
   @Input() displayColSpan: number | null;
   @Input() dateComparisonDefined: boolean;
   @Output() nextCellChanged = new EventEmitter<BaseTableCellModel>();
   @Output() onLinkClicked: EventEmitter<{
      hyperlinks: HyperlinkModel[],
      xPos: number,
      yPos: number,
      numLinks: number
   }> = new EventEmitter<any>();
   @Output() drillClicked = new EventEmitter<void>();
   @Output() resizeColumn = new EventEmitter<number>();
   @Output() resizeRow = new EventEmitter<number>();
   @Output() rename = new EventEmitter<string>();
   @Output() formInputChanged = new EventEmitter<string>();
   @Output() onFlyover = new EventEmitter<MouseEvent>();
   @Output() onSelectCell = new EventEmitter<MouseEvent|TouchEvent>();
   @Output() focusNext = new EventEmitter<KeyboardEvent>();
   @ViewChild("staticHeaderContent") staticHeaderContent: ElementRef;
   @ViewChild("cellContent") cellContent: ElementRef;
   @ViewChild("drillIcon") drillIcon: ElementRef;
   @ViewChild("cellInput") cellInput: ElementRef;
   date: NgbDateStruct;
   editing: boolean; // editor showing
   private dropdownRef: DropdownRef;
   private otext: string;
   hBorderWidth: number;
   vBorderWidth: number;
   public numLinks: number = 0;
   public ColumnOptionType = ColumnOptionType;
   public showResizeZone: boolean = true;
   public alignItems: string;
   public justifyContent: string;
   public nameable = false;
   static readonly debounceKey: string = "table_dataTipEvent";
   isEmbedded: boolean = false;
   tableName: string;
   enabled: boolean = true;
   hasFlyover: boolean = false;
   cellType: CellType = CellType.TEXT;
   readonly CellType0 = CellType;
   resizeLabel: string = null;
   htmlText: string;
   formBgColor: string;
   private _selected: boolean = false;
   private safari: boolean = GuiTool.isSafari();
   private mobile: boolean = GuiTool.isMobileDevice();
   currentTimeout: any;

   get selected(): boolean {
      return this._selected;
   }

   @Input()
   set selected(selected: boolean) {
      if(!!this.currentTimeout) {
         clearTimeout(this.currentTimeout);
      }

      if(!selected) {
         // wait the blur event and the change event of the edit input before input be removed from dom.
         this.currentTimeout = setTimeout(() => {
            this._selected = selected;
            this.changeDetectionRef.detectChanges();
         });
      }
      else {
         this._selected = selected;
      }
   }

   constructor(private renderer: Renderer2,
               private elementRef: ElementRef,
               private dropdownService: FixedDropdownService,
               private viewsheetClientService: ViewsheetClientService,
               private dataTipService: DataTipService,
               private contextProvider: ContextProvider,
               private debounceService: DebounceService,
               private changeDetectionRef: ChangeDetectorRef,
               private popComponentService: PopComponentService,
               private zone: NgZone)
   {
   }

   ngOnInit() {
      this.updateCellInfo();
      this.updateCellType();
   }

   updateCellType() {
      const ocellType = this.cellType;
      this.cellType = CellType.TEXT;
      this.htmlText = typeof this.cell.cellLabel == "string" ? <string> this.cell.cellLabel : null;

      if(this.htmlText && this.htmlText.includes("\n")) {
         this.htmlText = this.htmlText.replace(/\n/g, "<br>");
      }

      if((this.viewer || this.preview || !this.isHeader) && !this.vsWizard) {
         if(this.selected && this.editing && (this.isEmbedded || this.isHeader) && this.nameable) {
            this.cellType = CellType.INPUT;
         }
      }
      else if(!this.vsWizard && !this.preview && this.selected && this.editing &&
         (this.isEmbedded || this.isHeader) && this.nameable)
      {
         this.cellType = CellType.INPUT;
      }

      if(this.cellType != CellType.INPUT) {
         if(this.cell.isImage) {
            this.cellType = CellType.IMAGE;
         }
         else if(this.cell.presenter) {
            this.cellType = this.cell.presenter == "H" ? CellType.HTML_PRESENTER
               : CellType.PRESENTER;
         }
         else if(this.htmlText != this.cell.cellLabel) {
            this.cellType = CellType.HTML;
         }
         else {
            const label = this.cell.cellLabel + "";

            if(/<[a-zA-Z]+>/.test(label)) {
               this.cellType = CellType.HTML;
            }
         }
      }

      if(ocellType != this.cellType) {
         this.changeDetectionRef.detectChanges();
      }
   }

   ngOnDestroy() {
      if(this.dropdownRef) {
         this.dropdownRef.close();
      }
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes["cell"] || changes["table"]) {
         if(this.cell && this.isForm && this.cell.editable &&
            this.cell.editorType === ColumnOptionType.DATE)
         {
            let date: Date = new Date(this.cell.cellData as string);
            date = !this.isValidDate(date, this.cell) ? new Date() : date;

            this.date = {
               year: date.getFullYear(),
               month: date.getMonth() + 1,
               day: date.getDate()
            };
         }

         const dataPath = this.cell.dataPath;
         const path = dataPath ? dataPath.path : "";
         const dtype = dataPath ? dataPath.type : null;
         this.nameable = dtype != TableDataPathTypes.GROUP_HEADER || this.aggregateNames &&
            path.length === 1 && (this.aggregateNames.indexOf(path[0]) > -1 ||
            path[0] == this.aggregateNames.join("/"));
         this.updateCellInfo();
      }

      if(this.cellAnnotations && this.cellAnnotations.length > 0) {
         this.cellAnnotations = this.cellAnnotations.filter((annotation) => {
            return annotation.row === this.cell.row && annotation.col === this.cell.col;
         });
      }

      const dataRowCount = this.table.rowCount - this.table.headerRowCount;
      const dataColCount = this.table.colCount - this.table.headerColCount;

      //Cell will be focused on enter key if it's the first one below current selected data cell
      if(changes.selectedDataIndex && this.selectedDataIndex && this.dataCellIndex) {
         if(this.selectedDataIndex.row + 1 == this.dataCellIndex.row &&
            this.selectedDataIndex.column == this.dataCellIndex.column)
         {
            this.nextCellChanged.emit(this.cell);
         }
         // last row
         else if(this.selectedDataIndex.row == dataRowCount - 1 &&
                 this.dataCellIndex.row == 0)
         {
            if(this.selectedDataIndex.column + 1 == this.dataCellIndex.column ||
               this.selectedDataIndex.column == dataColCount - 1 &&
               this.dataCellIndex.column == 0)
            {
               this.nextCellChanged.emit(this.cell);
            }
         }
      }

      if(changes.selected && this.selected && !!this.cellInput &&
         (!this.viewsheet || !this.viewsheet.formatPainterMode))
      {
         this.editing = this.table.editing = true;
         this.startEditing();
      }

      // if the width of the column is 0 then remove the padding
      if(this.width < 3) {
         this.renderer.setStyle(this.elementRef.nativeElement, "padding", this.width + "px");
         this.showResizeZone = false;

         // disable borders so column can be hidden
         if(this.width < 1) {
            this.renderer.setStyle(this.elementRef.nativeElement, "border", "none");
         }
      }

      this.updateCellType();
   }

   private updateCellInfo() {
      const nativeElement = this.elementRef.nativeElement;
      this.numLinks = 0;
      this.otext = this.cell.cellData as string;
      this.tableName = this.table.absoluteName;
      this.isEmbedded = (<any> this.table).embedded;
      this.enabled = this.table.enabled;
      this.hasFlyover = this.table.hasFlyover;

      if(this.cell.hyperlinks) {
         this.numLinks = this.cell.hyperlinks.length;
      }

      if(this.cell.vsFormatModel) {
         this.renderer.setStyle(nativeElement, "color", this.cell.vsFormatModel.foreground);
         this.alignItems = this.cell.vsFormatModel.alignItems;
         this.justifyContent = this.cell.vsFormatModel.justifyContent;
         this.formBgColor = GuiTool.getContrastColor(this.cell.vsFormatModel.foreground);
      }

      if(this.cell.rowSpan != 1) {
         this.renderer.setAttribute(nativeElement, "rowspan", this.cell.rowSpan + "");
      }
      else {
         this.renderer.removeAttribute(nativeElement, "rowspan");
      }

      if(this.cell.colSpan != 1 || this.displayColSpan != null) {
         const colSpan = this.displayColSpan != null ? this.displayColSpan : this.cell.colSpan;
         this.renderer.setAttribute(nativeElement, "colspan", colSpan + "");
      }
      else {
         this.renderer.removeAttribute(nativeElement, "colspan");
      }

      if(this.cell.vsFormatModel && this.cell.vsFormatModel.border && this.height > 0) {
         // border collapse accounts for 1 pixel. others need to be subtracted from the height.
         // otherwise the calc table header-column and detail cells will have different
         // heights due to the difference of height calculation for <table> and <td>
         const bottomWidth = Tool.getMarginSize(this.cell.vsFormatModel.border.bottom);
         const topWidth = Tool.getMarginSize(this.cell.vsFormatModel.border.top);

         if(this.table.objectType === "VSCalcTable" &&
            (bottomWidth === 0 || topWidth === 0) &&
            this.cell.row >= this.table.headerRowCount &&
            this.cell.col < this.table.headerColCount)
         {
            // handle edge case, lb-table on calc table doesn't collapse borders
            this.vBorderWidth = bottomWidth + topWidth;
         }
         else {
            let vWidth = (bottomWidth + topWidth) / 2;

            if(Tool.getBorderStyle(this.cell.vsFormatModel.border.bottom) == "double" ||
               Tool.getBorderStyle(this.cell.vsFormatModel.border.top) == "double")
            {
               vWidth = bottomWidth + topWidth - 1;
            }

            // account for no-border on one side. (26621)
            this.vBorderWidth = Math.max(0, vWidth);
         }

         this.hBorderWidth = Math.max(
            0, Tool.getMarginSize(this.cell.vsFormatModel.border.left) +
               Tool.getMarginSize(this.cell.vsFormatModel.border.right) - 1);
      }
      else {
         this.vBorderWidth = 0;
         this.hBorderWidth = 0;
      }
   }

   onEnter(event: MouseEvent): void {
      if(this.hasFlyover && !this.isFlyOnClick && this.enabled) {
         this.onFlyover.emit(event);
      }

      if(this.viewer || this.preview) {
         if(this.dataTip) {
            this.showDataTip(event);
         }
         else if(this.table.dataTip) {
            this.debounceService.debounce(DataTipService.DEBOUNCE_KEY, () => {
               this.zone.run(() => this.dataTipService.hideDataTip());
            }, 100, []);
         }
      }
   }

   @HostListener("contextmenu", ["$event"])
   onContextMenu(event: any) {
      this.zone.run(() => {
         this.onSelectCell.emit(event);
      });
   }

   @HostListener("touchstart", ["$event"])
   @HostListener("click", ["$event"])
   onDown(event: MouseEvent|TouchEvent): void {
      if(!(this.drillIcon && event.target === this.drillIcon.nativeElement
           || event instanceof MouseEvent && GuiTool.isMobileDevice()))
      {
         this.zone.run(() => {
            this.onSelectCell.emit(event);
         });
      }
   }

   private startEditing(): void {
      setTimeout(() => {
         if(!!this.cellInput) {
            this.cellInput.nativeElement.focus();
         }
      }, 200);
   }

   @HostListener("dblclick", ["$event"])
   @HostListener("touchend", ["$event"])
   turnOnEditMode(event: MouseEvent): void {
      this.editing = this.table.editing = this.nameable;
      this.updateCellType();
   }

   get selectedOption(): any {
      let idx = -1;

      if(this.cell.options && this.cell.options.length > 0) {
         idx = this.cell.options.indexOf(<string> this.cell.cellData);

         if(idx < 0) {
            idx = this.cell.options.indexOf(<string> this.cell.cellLabel);
         }

         if(idx < 0 && !this.cell.cellData) {
            this.changeFormInput(this.cell.options[0]);
         }
      }

      return idx >= 0 ? this.cell.options[idx] : this.cell.cellData;
   }

   get isShowLinkedHeader(): boolean {
      if(this.numLinks == 0) {
         return false;
      }

      if(this.viewer || !this.editing) {
         return !this.isEmbedded || !this.selected;
      }

      return !this.selected;
   }

   get boundingClientRect(): ClientRect {
      return this.elementRef.nativeElement.getBoundingClientRect();
   }

   get nativeElement(): HTMLTableDataCellElement {
      return this.elementRef.nativeElement;
   }

   clickLink(event: MouseEvent): void {
      if(this.isShowLinkedHeader && (this.viewer || this.preview) && event.button === 0 &&
         !event.ctrlKey && !event.metaKey && !event.shiftKey)
      {
         event.preventDefault();

         this.onLinkClicked.emit({
            hyperlinks: this.cell.hyperlinks,
            xPos: event.pageX,
            yPos: event.pageY,
            numLinks: this.numLinks
         });
      }
   }

   changeColumnWidth(event: MouseEvent | TouchEvent): void {
      let pageX: number;

      if(!this.mobile && event instanceof MouseEvent) {
         let mouseEvent: MouseEvent = <MouseEvent> event;

         if(mouseEvent.button != 0) {
            return;
         }

         pageX = event.pageX;
      }
      else if(event instanceof TouchEvent) {
         pageX = (<TouchEvent> event).targetTouches[0].pageX;
      }

      event.stopPropagation();
      event.preventDefault();
      this.resizeColumn.emit(pageX);
   }

   changeRowHeight(event: MouseEvent): void {
      if(event.button != 0) {
         return;
      }

      if(this.editing && this.otext != this.cell.cellData) {
         this.changeCellText(null);
      }
      else {
         event.stopPropagation();
         event.preventDefault();
         this.resizeRow.emit(event.pageY);
      }
   }

   changeCellText(event: KeyboardEvent): void {
      if(event && event.keyCode !== 13) {
         return;
      }

      // avoid committing twice (from onenter and focusout)
      if(this.otext != this.cell.cellData) {
         this.rename.emit(this.otext = this.cell.cellData as string);
      }

      this.editing = this.table.editing = false;
      this.updateCellType();

      if(!!event && (event.keyCode === 13 || event.which === 13)) {
         this.focusNext.emit(event);
      }
   }

   updateDate(date: any): void {
      this.date = date;
      let newDate: Date = new Date(this.date.year, this.date.month - 1, this.date.day, 0, 0, 0, 0);
      this.formInputChanged.emit(newDate.getTime() + "");
   }

   changeFormInput(value: Object, editorType?: string): void {
      let ntext = value as string;

      if(!!editorType && editorType == ColumnOptionType.INTEGER) {
         ntext = Math.round(Number(ntext)) + "";
      }

      if(this.isEmbedded && !(this.preview || this.viewer)) {
         this.rename.emit(this.otext = ntext);
         return;
      }

      this.formInputChanged.emit(ntext);
   }

   openCalendar(event: MouseEvent): void {
      const options: DropdownOptions = {
         position: {x: event.pageX, y: event.pageY},
         contextmenu: false,
         closeOnOutsideClick: true,
         autoClose: false
      };

      this.dropdownRef = this.dropdownService.open(VSTableCellCalendar, options);
      const calendar: VSTableCellCalendar = this.dropdownRef.componentInstance;
      calendar.date = this.date;

      const sub = calendar.onDateChange.subscribe((date) => {
         sub.unsubscribe();

         if(!Tool.isEquals(date, this.date)) {
            this.updateDate(date);
         }

         this.dropdownRef.close();
         this.dropdownRef = null;
      });
   }

   get presenter(): string {
      // Account for padding.
      return this.linkUri + "vs/table/presenter/" +
         encodeURIComponent(this.tableName) + "/"
         + this.cell.row + "/" + this.cell.col
         + "/" + Math.floor(this.width - 4) + "/" + Math.floor(this.height - 2) + "/"
         + Tool.encodeURIPath(this.viewsheetClientService.runtimeId)
         // Force reload in case image changes.
         + "?" + this.table.genTime;
   }

   get viewer(): boolean {
      return this.contextProvider.viewer;
   }

   get preview(): boolean {
      return this.contextProvider.preview;
   }

   get composer(): boolean {
      return this.contextProvider.composer;
   }

   get binding(): boolean {
      return this.contextProvider.binding;
   }

   get vsWizard(): boolean {
      return this.contextProvider.vsWizard;
   }

   get vsWizardPreview(): boolean {
      return this.contextProvider.vsWizardPreview;
   }

   get popComponentShow(): boolean {
      return (this.viewer || this.preview) &&
         this.popComponentService.isPopComponent(this.tableName) &&
         this.popComponentService.isPopComponentVisible(this.tableName);
   }

   /**
    * Show the cell data tip
    *
    * @param {MouseEvent} event the event that indicates where to show the datatip
    */
   private showDataTip(event: MouseEvent): void {
      this.debounceService.debounce(DataTipService.DEBOUNCE_KEY, () => {
         if(!this.dataTipService.isFrozen()) {
            this.dataTipService.hideDataTip();
         }

         //fix bug#38622 hide data tip when calc table cell is bound to text
         if(this.cell.bindingType == CellBindingInfo.BIND_TEXT ||
            this.cell.dataPath?.type == TableDataPathTypes.HEADER ||
            this.cell.grandTotalHeaderCell)
         {
            return;
         }

         this.dataTipService.showDataTip(
            this.tableName, this.dataTip, event.clientX, event.clientY,
            this.cell.row + "X" + this.cell.col, this.table.dataTipAlpha);

         this.changeDetectionRef.detectChanges();
      }, 100, []);
   }

   getCellHeight(px: boolean): string {
      return !px && this.safari ? "100%" : ((this.height - this.vBorderWidth) + "px");
   }

   get drillVisible(): boolean {
      if(this.cell.cellData == "" && this.table.cubeType != null) {
         return false;
      }

      return this.cell.drillOp && !this.cell.period && !this.vsWizard &&
         !this.vsWizardPreview && (!(<any> this.table)?.dateComparisonDefined ||
            !(<any> this.table)?.dateComparisonEnabled);
   }

   isValidDate(date: Date, cell: BaseTableCellModel): boolean {
      return date instanceof Date && !isNaN(date.getTime()) &&
         (XSchema.isDateType(cell.dataPath.dataType) || cell.dataPath.dataType == "Long" ||
         cell.editorType == ColumnOptionType.DATE);
   }
}
