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
import {
   Component,
   ElementRef,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges,
   ViewChild,
   Renderer2
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { DomSanitizer, SafeHtml, SafeStyle } from "@angular/platform-browser";
import { DragEvent } from "../../../common/data/drag-event";
import { TableTransfer } from "../../../common/data/dnd-transfer";
import { GuiTool } from "../../../common/util/gui-tool";
import { Tool } from "../../../../../../shared/util/tool";
import { SelectionValue } from "../../../composer/data/vs/selection-value";
import { SelectionValueModel } from "../../model/selection-value-model";
import { VSFormatModel } from "../../model/vs-format-model";
import { VSSelectionBaseModel } from "../../model/vs-selection-base-model";
import { VSSelectionTreeModel } from "../../model/vs-selection-tree-model";
import { CellRegion } from "./cell-region";
import { MODE } from "./selection-tree-controller";
import { VSSelection } from "./vs-selection.component";
import {ComponentTool} from "../../../common/util/component-tool";
import { ContextProvider } from "../../context-provider.service";

@Component({
   selector: "selection-list-cell",
   templateUrl: "selection-list-cell.component.html",
   styleUrls: ["selection-list-cell.component.scss"]
})
export class SelectionListCell implements OnInit, OnChanges {
   static INDENT_SIZE = 8;

   @Input() selectionValue: SelectionValueModel;
   @Input() indent: number = 4;
   @Input() measureMax: number;
   @Input() measureMin: number;
   @Input() measureRatio: number;
   @Input() cellWidth: number;
   @Input() barWidth: number;
   @Input() textWidth: number;
   @Input() minWidth: number;
   @Input() isEmbedded: boolean;
   @Input() scrollbarWidth: number;
   @Input() keyNav: boolean = false;
   @Input() maxMode: boolean = false;
   @Output() selectionStateChanged = new EventEmitter<any>();
   @Output() resizeCell = new EventEmitter<number>();
   @Output() resizeMeasures = new EventEmitter<{text: number, bar: number}>();
   @Output() resizeCellHeight = new EventEmitter<number>();
   @Output() regionClicked = new EventEmitter<{event: MouseEvent,
      region: CellRegion, selectionValue: SelectionValueModel}>();

   @ViewChild("cell") cell: ElementRef;

   CellRegion = CellRegion;
   cellFormat: VSFormatModel;
   measureTextFormat: VSFormatModel;
   measureBarFormat: VSFormatModel;
   measureNBarFormat: VSFormatModel;
   singleSelection: boolean = false;
   labelWidth: number;
   showText: boolean;
   _textWidth: number;
   textRight: number;
   public valuePadding = SelectionListCell.INDENT_SIZE;
   showBar: boolean;
   _barWidth: number;
   barX: number;
   barY: SafeStyle;
   barSize: number;
   barResizing: boolean = false;
   measureTextSelected: boolean = false;
   measureBarSelected: boolean = false;
   measureNBarSelected: boolean = false;
   labelSelected: boolean = false;
   resizeBorderHeight: number;
   resizeshow: boolean = false;
   bottomMargin: number = 0;
   nodeIndent: number;
   labelLeft: number = 18;
   measureTextHAlign: string;
   measureTextVAlign: string;
   isParentIDTree: boolean = false;
   htmlLabel: SafeHtml;
   mobile = GuiTool.isMobileDevice();

   constructor(public vsSelectionComponent: VSSelection,
               private sanitization: DomSanitizer,
               private renderer: Renderer2,
               public contextProvider: ContextProvider,
               private modalService: NgbModal) {
   }

   ngOnInit(): void {
      this.updateModelInfo();
      this.setMeasureWidths();
      this.setTextWidths();
      this.setLabelHeight();
   }

   get vsWizard(): boolean {
      return this.contextProvider.vsWizard;
   }

   private updateModelInfo() {
      let model: VSSelectionBaseModel = this.vsSelectionComponent.model;
      this.cellFormat = this.vsSelectionComponent.controller.getCellFormat(this.selectionValue);
      this.nodeIndent = this.indent + this.selectionValue.level * SelectionListCell.INDENT_SIZE;

      if((<VSSelectionTreeModel>model).singleSelectionLevels != null && model.singleSelection
         && (<VSSelectionTreeModel>model).mode == MODE.COLUMN)
      {
         this.singleSelection = (<VSSelectionTreeModel>model).singleSelectionLevels
            .indexOf(this.selectionValue.level) != -1;
      }
      else {
         this.singleSelection = model.singleSelection;
      }

      this.showText = model.showText && model.measure != null;
      this.showBar = model.showBar && model.measure != null;
      this.measureTextFormat = model.measureFormats["Measure Text" + this.selectionValue.level];
      this.measureBarFormat = model.measureFormats["Measure Bar" + this.selectionValue.level];
      this.measureNBarFormat = model.measureFormats["Measure Bar(-)" + this.selectionValue.level];
      this.bottomMargin = this.cellFormat ? Tool.getMarginSize(this.cellFormat.border.top) : 0;
      this.measureTextHAlign = GuiTool.getFlexHAlign(this.measureTextFormat.hAlign);
      this.measureTextVAlign = GuiTool.getFlexVAlign(this.measureTextFormat.vAlign);
      this.isParentIDTree = model.objectType === "VSSelectionTree" && (<VSSelectionTreeModel> model).mode == MODE.ID;

      switch(this.measureTextFormat.vAlign) {
      case "top":
         this.barY = this.sanitization.bypassSecurityTrustStyle("2px");
         break;
      case "bottom":
         this.barY = this.sanitization.bypassSecurityTrustStyle("calc(100% - 1em - 1px)");
         break;
      default:
         this.barY = this.sanitization.bypassSecurityTrustStyle("calc(50% - 1em / 2)");
         break;
      }

      this.htmlLabel = this.isHTML(this.selectionValue.label) ?
         this.sanitization.bypassSecurityTrustHtml(this.selectionValue.label) : null;
   }

   ngOnChanges(changes: SimpleChanges) {
      this.updateModelInfo();
      this.setMeasureWidths();
      this.setTextWidths();
      this.setLabelHeight();
   }

   @Input() set selectedCells(cells: Map<string, Map<number, boolean>>) {
      let identifier: string = this.vsSelectionComponent.getIdentifier(this.selectionValue);

      if(cells && cells.has(identifier)) {
         let cellMap: Map<number, boolean> = cells.get(identifier);
         this.labelSelected = cellMap.get(CellRegion.LABEL);
         this.measureTextSelected = cellMap.get(CellRegion.MEASURE_TEXT);
         this.measureBarSelected = cellMap.get(CellRegion.MEASURE_BAR);
         this.measureNBarSelected = cellMap.get(CellRegion.MEASURE_N_BAR);

         // Focus for Section 508 -- for screen readers.
         if(this.keyNav && this.cell) {
            this.cell.nativeElement.focus();
         }
      }
      else {
         this.labelSelected = false;
         this.measureTextSelected = false;
         this.measureBarSelected = false;
         this.measureNBarSelected = false;
      }
   }

   // return the selection box class
   getIconClass(): string {
      let icon: string;

      if(this.selectionValue == null) {
         return null;
      }
      else if(this.selectionValue.others) {
         icon = this.singleSelection ? "select-other-circle-icon" : "select-other-icon icon-size1";
      }
      else if(this.selectionValue.more) {
         icon = "chevron-circle-arrow-right-icon icon-size1";
      }
      else {
         let state: number = this.selectionValue.state & SelectionValue.DISPLAY_STATES;

         switch(state) {
         case SelectionValue.STATE_SELECTED:
         case SelectionValue.STATE_SELECTED | SelectionValue.STATE_INCLUDED:
            icon = this.singleSelection ? "selected-auto-circle-icon icon-size1" : "selected-icon icon-size1";
            break;
         case SelectionValue.STATE_SELECTED | SelectionValue.STATE_EXCLUDED:
            icon = this.singleSelection ? "selected-auto-circle-icon icon-size1 icon-disabled" :
               "selected-icon icon-size1 icon-disabled";
            break;
         case SelectionValue.STATE_INCLUDED:
            icon = this.singleSelection ? "select-empty-circle-icon icon-size1" :
               "selected-auto-icon icon-size1";
            break;
         case SelectionValue.STATE_EXCLUDED:
            icon = this.singleSelection ? "select-excluded-circle-icon icon-size1" :
               "select-excluded-icon icon-size1";
            break;
         default:
            icon = this.singleSelection ? "select-empty-circle-icon icon-size1" :
               "select-empty-icon icon-size1";
            break;
         }
      }

      return icon;
   }

   clickLabel(event: MouseEvent) {
      // toggle checkbox on label if in viewer
      if(this.contextProvider.viewer || this.contextProvider.preview) {
         this.click(event);
      }
   }

   get toggleEnabled(): boolean {
      return this.contextProvider.viewer || this.contextProvider.preview;
   }

   getCellTooltip(): string {
      if(this.toggleEnabled) {
         return "_#(js:viewer.viewsheet.selection.toggleStyle)";
      }
      else {
         return "";
      }
   }

   click(event: MouseEvent) {
      if(event.button != 0 || this.contextProvider.vsWizard) {
         return;
      }

      let toggleAll = false;
      let toggle = false;

      if(this.toggleEnabled && event.altKey) {
         if(event.shiftKey || this.isParentIDTree) {
            toggleAll = true;
         }
         else {
            toggle = true;
         }
      }

      this.selectionStateChanged.emit({ toggle, toggleAll });
      this.selectRegion(event, CellRegion.LABEL);
   }

   // toggle tree node expanded status
   toggleFolder(event: MouseEvent) {
      event.stopPropagation();
      this.vsSelectionComponent.controller.toggleNode(this.selectionValue);
      this.vsSelectionComponent.folderToggled();
   }

   // tree toggle icon
   getTreeIconClass(): string {
      if(this.vsSelectionComponent.controller.isNodeOpen(this.selectionValue)) {
         return "minus-box-outline-icon icon-size1";
      }
      else {
         return "plus-box-outline-icon icon-size1";
      }
   }

   isFolder(): boolean {
      return this.selectionValue && "selectionList" in this.selectionValue;
   }

   get isList(): boolean {
      return this.vsSelectionComponent.model.objectType === "VSSelectionList";
   }

   onDragStart(event: DragEvent): void {
      if(this.isEmbedded) {
         return;
      }

      const idx = this.selectionValue.level;
      const assemblyName = this.vsSelectionComponent.getAssemblyName();
      let transferData: TableTransfer = new TableTransfer("details", idx, assemblyName);
      transferData.objectType = "vsselection";

      Tool.setTransferData(event.dataTransfer,
         {
            dragName: ["tableBinding"],
            dragSource: transferData
         });

      event.stopPropagation();
   }

   onResizeCellHeightStart(): void {
      if(this.cellFormat.wrapping.wordWrap == "break-word") {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
             "_#(js:common.viewsheet.selectionListAndTree.cannotResizeWrapCell)");
      }
      else {
         this.resizeshow = true;
         this.resizeBorderHeight = this.vsSelectionComponent.cellHeight;
      }
   }

   onResizeCellHeight(event: any): void {
      if(this.cellFormat.wrapping.wordWrap == "break-word") {
         return;
      }

      this.resizeBorderHeight += event.deltaRect.bottom;
      this.renderer.setStyle(this.cell.nativeElement, "height", this.resizeBorderHeight + "px");
   }

   onResizeCellHeightEnd(): void {
      if(this.cellFormat.wrapping.wordWrap == "break-word") {
         return;
      }

      this.resizeshow = false;
      this.vsSelectionComponent.model.cellHeight = this.resizeBorderHeight;
      this.vsSelectionComponent.updateCellHeight();
      this.setLabelHeight();
   }

   private setLabelHeight(): void {
      if(!this.cellFormat || this.cellFormat.wrapping.whiteSpace == "nowrap") {
         return;
      }

      const maxLines: number = this.selectionValue.maxLines || 1;
      const labelLength: number = GuiTool.measureText(this.selectionValue.label,
                                                      this.cellFormat.font);
      const lines: number = Math.ceil(labelLength / this.labelWidth);
   }

   onResizeMeasuresMove(event: any, region: number): void {
      if(region === CellRegion.MEASURE_BAR) {
         this.barResizing = true;
         this._barWidth -= event.deltaRect.left;
         this.barX -= event.deltaRect.left;
      }
      else if(region === CellRegion.MEASURE_TEXT) {
         this._textWidth -= event.deltaRect.left;
      }
   }

   onResizeMeasuresEnd(): void {
      this.barResizing = false;
      const maxBarWidth = this.cellWidth - this.labelLeft;
      this._barWidth = (this._barWidth < 0) ? Math.ceil(this.cellWidth * 0.05) :
                          (this._barWidth > maxBarWidth) ? maxBarWidth : this._barWidth;

      const maxTextWidth = maxBarWidth - this._barWidth;
      this._textWidth = (this._textWidth < this.minWidth) ? this.minWidth :
                           (this._textWidth > maxTextWidth) ? maxTextWidth : this._textWidth;

      this.resizeMeasures.emit({text: this._textWidth, bar: this._barWidth});
   }

   selectRegion(event: MouseEvent, region: CellRegion): void {
      if(region == null) {
         region = (this.selectionValue.measureValue < 0) ? CellRegion.MEASURE_N_BAR
            : CellRegion.MEASURE_BAR;
      }

      this.regionClicked.emit({event: event, region: region, selectionValue: this.selectionValue});
   }

   setMenuCell(event: MouseEvent): void {
      this.vsSelectionComponent.model.contextMenuCell = this.selectionValue;
      // right click on cell in viewer should select cell to enable  cell level context menu
      this.selectRegion(event, CellRegion.LABEL);
   }

   private setMeasureWidths(): void {
      this._barWidth = (this.barWidth <= 0 && this.showBar) ? Math.ceil(this.cellWidth / 4) : this.barWidth;
      this._barWidth = Math.min(this.cellWidth, this._barWidth);
      const gap: number = 2;
      const barWidth: number = this._barWidth - gap * 2;
      let innerBarWidth: number = 0;

      if(this.measureMin == this.measureMax) {
         innerBarWidth = barWidth;
      }
      else if(this.measureMax > 0 && this.selectionValue.measureValue < 0) {
         const negativew = -this.measureMin * barWidth / (this.measureMax - this.measureMin);
         innerBarWidth = this.selectionValue.measureValue * negativew;
      }
      else {
         innerBarWidth = this.selectionValue.measureValue * barWidth;
      }

      if(this.measureMin >= 0) {
         this.barX = 0;
         this.barSize = innerBarWidth;
      }
      // has negative and positive
      else if(this.measureMax > 0) {
         const zero = barWidth * -this.measureMin / (this.measureMax - this.measureMin);

         // negative bar
         if(innerBarWidth < 0) {
            this.barSize = -innerBarWidth;
            this.barX = zero + innerBarWidth;
         }
         // positive bar
         else {
            this.barSize = innerBarWidth;
            this.barX = zero;
         }
      }
      // only negative
      else {
         this.barX = barWidth + innerBarWidth;
         this.barSize = -innerBarWidth;
      }

      if(isNaN(this.barSize)) {
         this.barSize = 0;
      }

      this.barX += gap;
   }

   private setTextWidths(): void {
      const textArea = Math.max(0, this.cellWidth - this._barWidth - this.labelLeft -
         this.nodeIndent);
      this._textWidth = (this.textWidth < 0) ?
                           Math.ceil(textArea * this.measureRatio) : this.textWidth;
      this._textWidth = Math.min(this.cellWidth - this._barWidth, this._textWidth);
      this.textRight = this._barWidth;

      if(this.showText) {
         const minimumWidth: number = this.selectionValue.measureLabel  && this.measureTextFormat
            ? GuiTool.measureText(this.selectionValue.measureLabel, this.measureTextFormat.font)
            : 0;
         this._textWidth = Math.max(this._textWidth, minimumWidth);
      }

      this.labelWidth = textArea - this._textWidth;
   }

   get ariaExpanded(): boolean {
      return this.isList || !this.isFolder() ? null :
         !!this.vsSelectionComponent.controller.isNodeOpen(this.selectionValue);
   }

   get ariaSelected(): boolean {
      let state: number = this.selectionValue.state & SelectionValue.DISPLAY_STATES;

      switch(state) {
      case SelectionValue.STATE_SELECTED:
      case SelectionValue.STATE_SELECTED | SelectionValue.STATE_INCLUDED:
      case SelectionValue.STATE_SELECTED | SelectionValue.STATE_EXCLUDED:
         return true;
      case SelectionValue.STATE_INCLUDED:
      case SelectionValue.STATE_EXCLUDED:
      default:
         return false;
      }
   }

   startResize(event: MouseEvent) {
      this.vsSelectionComponent.startResize(event);
   }

   get height(): string {
      return this.cellFormat.wrapping.wordWrap == "break-word" ?
         "auto" : this.vsSelectionComponent.cellHeight + "px";
   }

   isHTML(str: string) {
      const doc = new DOMParser().parseFromString(str, "text/html");
      return Array.from(doc.body.childNodes).some(node => node.nodeType === 1);
   }
}
