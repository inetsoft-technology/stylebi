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
   ChangeDetectorRef,
   AfterViewChecked,
   Component,
   Inject,
   EventEmitter,
   HostListener,
   Input,
   NgZone,
   Renderer2,
   ViewChild,
   ElementRef,
   ChangeDetectionStrategy,
   Output
} from "@angular/core";
import { DOCUMENT } from "@angular/common";
import { GetCellBindingCommand } from "../../binding/command/get-cell-binding-command";
import { GetCellScriptCommand } from "../../binding/command/get-cell-script-command";
import { GetPredefinedNamedGroupCommand } from "../../binding/command/get-predefined-named-group-command";
import { GetTableLayoutCommand } from "../../binding/command/get-table-layout-command";
import { CellBindingInfo } from "../../binding/data/table/cell-binding-info";
import { VSCalcTableEditorService } from "../../binding/services/table/vs-calc-table-editor.service";
import { Rectangle } from "../../common/data/rectangle";
import { TableDataPath } from "../../common/data/table-data-path";
import { CalcTableCell } from "../../common/data/tablelayout/calc-table-cell";
import { CalcTableLayout } from "../../common/data/tablelayout/calc-table-layout";
import { TableCell } from "../../common/data/tablelayout/table-cell";
import { CommandProcessor } from "../../common/viewsheet-client/command-processor";
import { ViewsheetClientService } from "../../common/viewsheet-client/index";
import { ResizeCalcTableCellEvent } from "../../vsobjects/event/resize-calc-table-cell-event";
import { VSCalcTableModel } from "../../vsobjects/model/vs-calctable-model";
import { VSFormatModel } from "../../vsobjects/model/vs-format-model";
import { VSObjectModel } from "../../vsobjects/model/vs-object-model";
import { GuiTool } from "../../common/util/gui-tool";
import { AssemblyLoadingCommand } from "../../vsobjects/command/assembly-loading-command";
import { ClearAssemblyLoadingCommand } from "../../vsobjects/command/clear-assembly-loading-command";

@Component({
   selector: "vs-calc-table-layout",
   templateUrl: "vs-calc-table-layout.component.html",
   styleUrls: ["vs-calc-table-layout.component.scss"],
   changeDetection: ChangeDetectionStrategy.OnPush
})
export class CalcTableLayoutPane extends CommandProcessor implements AfterViewChecked {
   @Output() calcTableLayout = new EventEmitter<CalcTableLayout>();
   public static GROUP_SYMBOL: string = "\u039E";
   public static SUMMARY_SYMBOL: string = "\u2211";
   public static RIGHT_ARROW: string = "\u2192";
   public static DOWN_ARROW: string = "\u2193";
   tableModel: CalcTableLayout;
   vsObjectModel: VSObjectModel;
   private prevMouseLocationx: number = -1;
   private prevMouseLocationy: number = -1;
   private resizeRow: number = -1;
   private resizeCol: number = -1;
   private newWidth: number = -1;
   private newHeight: number = -1;
   private resizeOp: string = null;
   private spanmap = new Map<string, TableCell>();
   private scrollTop = 0;
   private scrollLeft = 0;
   private loadingCount: number = 0;
   @ViewChild("colResize") colResize: ElementRef;
   @ViewChild("rowResize") rowResize: ElementRef;
   @ViewChild("tableContainer") tableContainer: ElementRef;
   private readonly endResizeListener = this.onMouseUp.bind(this);
   isFirefox = GuiTool.isFF();
   isEdge = GuiTool.isEdge();

   constructor(private editorService: VSCalcTableEditorService,
               private clientService: ViewsheetClientService,
               private changeRef: ChangeDetectorRef,
               private renderer: Renderer2,
               @Inject(DOCUMENT) private document: any,
               zone: NgZone)
   {
      super(clientService, zone);
   }

   @Input() set vsObject(vsObject: VSObjectModel) {
      this.editorService.loadTableModel();
      this.vsObjectModel = vsObject;
   }

   ngAfterViewChecked() {
      if(this.tableContainer && this.tableContainer.nativeElement) {
         this.tableContainer.nativeElement.scrollTop = this.scrollTop;
         this.tableContainer.nativeElement.scrollLeft = this.scrollLeft;
      }
   }

   onScroll(event: Event) {
      this.scrollLeft = this.tableContainer.nativeElement.scrollLeft;
      this.scrollTop = this.tableContainer.nativeElement.scrollTop;
   }

   getSpanCell(r: number, c: number): TableCell {
      return this.spanmap[r + "," + c];
   }

   get selectedCells(): any[] {
      return this.tableModel ? this.tableModel.selectedCells ?
         this.tableModel.selectedCells : [] : [];
   }

   get selectedRect(): Rectangle {
      return this.tableModel ? this.tableModel.selectedRect : new Rectangle(0, 0, 0, 0);
   }

   set selectedRect(rect: Rectangle) {
      this.tableModel.selectedRect = rect;
   }

   getAssemblyName(): string {
      return this.vsObjectModel ? this.vsObjectModel.absoluteName : null;
   }

   getCellStyle(cell: CalcTableCell, r: number, c: number) {
      let style = {
         "height": this.tableModel.tableRows[r].height + "px",
         "width": this.tableModel.tableColumns[c].width + "px",
         "background-color": this.getBackground(cell, r, c),
      };

      this.setSpanCellStyle(style, cell, r, c);
      this.setSelectStyle(style, cell, r, c);

      return style;
   }

   updateFirstSelected(row: number, col: number): void {
      let model: VSCalcTableModel = <VSCalcTableModel> this.vsObjectModel;
      model.firstSelectedRow = row;
      model.firstSelectedColumn = col;
   }

   // For merged cell, only push the firstbase cell to the selectedCells.
   clickCell(event: MouseEvent, ocell: CalcTableCell) {
      let cell: CalcTableCell = this.findBaseCell(ocell);
      let row: number = cell.row;
      let col: number = cell.col;
      let isCurrentlySelected = this.selectedCells.indexOf(ocell) >= 0;

      if(event.button === 2 && isCurrentlySelected) {
         return;
      }

      let time = this.tableModel.selectedCells && this.tableModel.selectedCells.length > 0 ? 200 : 0;

      // make sure the current change is committed before changing the selected cell
      setTimeout(() => {
         if(!event.ctrlKey && !event.shiftKey) {
            this.updateFirstSelected(row, col);
            this.selectedCells.splice(0, this.selectedCells.length);
            this.addSelectedCell(cell);
            this.selectedRect = this.getCellRect(cell);
         }
         else if(this.selectedCells.length == 0) {
            this.updateFirstSelected(row, col);
            this.addSelectedCell(cell);
            this.selectedRect = this.getCellRect(cell);
         }
         else {
            this.addShiftCells(cell);
         }

         this.selectCell(this.selectedCells);
      }, time);
   }

   selectCell(cells: CalcTableCell[]) {
      const regions: TableDataPath[] = [];
      const map: Map<number, number[]> = new Map<number, number[]>();

      for(let i = 0; i < cells.length; i++) {
         regions.push(cells[i].cellPath);
         let row: number = cells[i].row;
         let col: number = cells[i].col;
         let selectedColumns = map.get(row) || [];
         map.set(row, selectedColumns.concat(col));
      }

      this.editorService.loadCellBinding();
      this.vsObjectModel.selectedRegions = regions;
      (<VSCalcTableModel> this.vsObjectModel).selectedData = map;
      this.detectChanges();
   }

   detectChanges() {
      this.changeRef.detectChanges();
   }

   private addSelectedCell(cell: CalcTableCell) {
      if(this.tableModel) {
         if(!this.tableModel.selectedCells) {
            this.tableModel.selectedCells = [];
         }

         this.tableModel.selectedCells.push(cell);
      }
   }

   private replaceObject(newModel: CalcTableLayout,
                         oldModel: CalcTableLayout): CalcTableLayout
   {
      if(oldModel) {
         newModel.selectedCells = oldModel.selectedCells;
         newModel.selectedRect = oldModel.selectedRect;
         newModel.selectedRegions = oldModel.selectedRegions;
         this.addSelectCells();
         this.editorService.loadCellBinding();
      }

      return newModel;
   }

   protected processGetTableLayoutCommand(command: GetTableLayoutCommand): void {
      let oldCalc: CalcTableLayout = this.editorService.getTableLayout();
      let newCalc: CalcTableLayout = <CalcTableLayout> command.layout;
      this.tableModel = this.replaceObject(newCalc, oldCalc);
      this.editorService.setTableLayout(this.tableModel);
      this.calcTableLayout.emit(this.tableModel);
      this.createSpanMap();
   }

   private createSpanMap(): void {
      this.spanmap = new Map<string, TableCell>();

      this.tableModel.tableRows.forEach(row => row.tableCells.forEach(cell => {
         if(cell.span) {
            for(let r = 0; r < cell.span.height; r++) {
               for(let c = 0; c < cell.span.width; c++) {
                  if(r != 0 || c != 0) {
                     this.spanmap[(r + cell.row) + "," + (c + cell.col)] = cell;
                  }
               }
            }
         }
      }));
   }

   protected processGetCellBindingCommand(command: GetCellBindingCommand): void {
      this.editorService.resetCellBinding(command);
   }

   protected processGetCellScriptCommand(command: GetCellScriptCommand): void {
      this.editorService.getCellBinding().value = command.script;
      this.changeSelectCell(this.editorService.getCellBinding());
      // getCellScript is called after switching to formula, when the script is retrieved
      // we set the script back otherwise the serve would still have the old (e.g. column)
      // value, the the editor field will be updated to the wrong value on refresh
      this.editorService.setCellBinding();
   }

   changeSelectCell(bind: CellBindingInfo) {
      let row = this.selectedRect.y;
      let col = this.selectedRect.x;
      this.tableModel.tableRows[row].tableCells[col].text = this.getCellContent(bind);
   }

   /**
    * Get cell content according to the binding.
    */
   getCellContent(bind: CellBindingInfo): string {
      if(!bind || bind == null) {
         return null;
      }

      if(bind.type == CellBindingInfo.BIND_FORMULA) {
         return "=" + (bind.value ? bind.value : "");
      }

      if(bind.type == CellBindingInfo.BIND_COLUMN) {
         return bind.value;
      }

      let cellContent: string = bind.value;

      cellContent = bind.btype == CellBindingInfo.GROUP ?
         CalcTableLayoutPane.GROUP_SYMBOL + cellContent :
         bind.btype == CellBindingInfo.SUMMARY ?
         CalcTableLayoutPane.SUMMARY_SYMBOL + cellContent : cellContent;
      cellContent = "[" + cellContent + "]";
      cellContent = bind.expansion == CellBindingInfo.EXPAND_H ?
         CalcTableLayoutPane.RIGHT_ARROW + cellContent :
         bind.expansion == CellBindingInfo.EXPAND_V ?
         CalcTableLayoutPane.DOWN_ARROW + cellContent : cellContent;

      return cellContent;
   }

   protected processGetPredefinedNamedGroupCommand(
      command: GetPredefinedNamedGroupCommand): void
   {
      this.editorService.namedGroups = command.namedGroups;
   }

   findBaseCell(cell: CalcTableCell) {
      if(cell.baseInfo == null) {
         return cell;
      }

      let r = cell.baseInfo.x;
      let c = cell.baseInfo.y;

      if(this.tableModel == null) {
         return null;
      }

      return this.tableModel.tableRows[r].tableCells[c];
   }

   // Add shift cells to select cells.
   addShiftCells(cell: CalcTableCell) {
      this.selectedRect = this.getCellRect(cell);

      for(let i = 0; i < this.selectedCells.length; i++) {
         let rect = this.getCellRect(this.selectedCells[i]);
         this.selectedRect = this.mergeDimension(this.selectedRect, rect);
         this.validateDimension(this.selectedRect);
      }

      this.addSelectCells();
   }

   getRectangle(r: number, c: number) {
      let cell = this.tableModel.tableRows[r].tableCells[c];
      return this.getCellRect(cell);
   }

   getCellRect(cell: CalcTableCell) {
      return cell.span == null ? new Rectangle(cell.col, cell.row, 1, 1) :
         new Rectangle(cell.col, cell.row, cell.span.width, cell.span.height);
   }

   /**
    * Merge dimension.
    */
   mergeDimension(first: Rectangle, second: Rectangle): Rectangle {
      let x = Math.min(first.x, second.x);
      let y = Math.min(first.y, second.y);
      let width = Math.max(first.x + first.width, second.x + second.width) - x;
      let height = Math.max(first.y + first.height, second.y + second.height) - y;

      return new Rectangle(x, y, width, height);
   }

   validateDimension(rec: Rectangle) {
      for(let i = rec.x; i < rec.x + rec.width; i++) {
         for(let j = rec.y; j < rec.y + rec.height; j++) {
            let scope: any = this.getRectangle(j, i);

            if(scope.x < this.selectedRect.x || scope.y < this.selectedRect.y ||
               scope.x + scope.width > this.selectedRect.x + this.selectedRect.width ||
               scope.y + scope.height > this.selectedRect.y + this.selectedRect.height)
            {
               this.selectedRect = this.mergeDimension(this.selectedRect, scope);
               this.validateDimension(this.selectedRect);
            }
         }
      }
   }

   addSelectCells() {
      this.selectedCells.splice(0, this.selectedCells.length);

      if(this.selectedRect == null) {
         return;
      }

      let r = this.selectedRect.y;
      let c = this.selectedRect.x;
      let w = this.selectedRect.width;
      let h = this.selectedRect.height;

      for(let i = r; i < r + h; i++) {
         for(let j = c; j < c + w; j++) {
            let cell = this.tableModel.tableRows[i].tableCells[j];

            if(cell.baseInfo == null) {
               this.addSelectedCell(cell);
            }
         }
      }
   }

   getBackground(cell: CalcTableCell, r: number, c: number) {
      const cellFormat = <VSFormatModel> cell.vsFormat;

      if(cellFormat != null && cellFormat.background != null) {
         return cellFormat.background;
      }

      return null;
   }

   setSelectStyle(style: any, cell: CalcTableCell, r: number, c: number) {
      if(this.selectedCells == null) {
         return;
      }

      for(let i = 0; i < this.selectedCells.length; i++) {
         let selectedCell = this.selectedCells[i];
         let row = selectedCell.row;
         let col = selectedCell.col;

         if(row == cell.row && col == cell.col) {
            style["background-color"] = "#b2d8ff";
         }

         if(cell.baseInfo != null && row == cell.baseInfo.x && col == cell.baseInfo.y) {
            style["background-color"] = "#b2d8ff";
         }
      }
   }

   setSpanCellStyle(style: any, cell: CalcTableCell, r: number, c: number) {
      let span = cell.span;
      let baseInfo = cell.baseInfo;

      // Process span cell(the first merge cell)
      if(span != null) {
         // span on x
         if(span.width > 1) {
            style["border-right-color"] = "#bbbec0";
         }

         // span on y
         if(span.height > 1) {
            style["border-bottom-color"] = "#bbbec0";
         }
      }

      // Process span cell(other merge cells)
      if(baseInfo != null) {
         // span on y
         if(r >= baseInfo.x && r <= baseInfo.x + baseInfo.height) {
            if(c > baseInfo.y) {
               style["border-left-color"] = "#bbbec0";
            }

            if(c < baseInfo.y + baseInfo.width - 1) {
               style["border-right-color"] = "#bbbec0";
            }
         }

         // span on x
         if(c >= baseInfo.y && c <= baseInfo.y + baseInfo.width) {
            if(r > baseInfo.x) {
               style["border-top-color"] = "#bbbec0";
            }

            if(r < baseInfo.x + baseInfo.height - 1) {
               style["border-bottom-color"] = "#bbbec0";
            }
         }
      }
   }

   resizeCell(evt: any, row: number, col: number): void {
      this.prevMouseLocationx = evt.x;
      this.prevMouseLocationy = evt.y;
      this.newWidth = this.tableModel.tableColumns[col].width;
      this.newHeight = this.tableModel.tableRows[row].height;
      this.resizeRow = row;
      this.resizeCol = col;
      this.resizeOp = evt.op;
      this.document.addEventListener("mouseup", this.endResizeListener);
   }

   onMouseMove(event: MouseEvent): void {
      if(this.prevMouseLocationx == -1 && this.prevMouseLocationy == -1 ||
         this.resizeRow == -1 && this.resizeCol == -1) {
         return;
      }

      let movex = event.pageX - this.prevMouseLocationx;
      let movey = event.pageY - this.prevMouseLocationy;
      this.newWidth = this.newWidth + movex;
      this.newHeight = this.newHeight + movey;
      this.prevMouseLocationx = event.pageX;
      this.prevMouseLocationy = event.pageY;

      if(this.resizeCol >= 0 && this.resizeOp == "ResizeColumn") {
         this.renderer.setStyle(this.colResize.nativeElement, "left", event.pageX + "px");
         this.renderer.setStyle(this.colResize.nativeElement, "visibility", "visible");
      }

      if(this.resizeRow >= 0 && this.resizeOp == "ResizeRow") {
         this.renderer.setStyle(this.rowResize.nativeElement, "top", event.pageY + "px");
         this.renderer.setStyle(this.rowResize.nativeElement, "visibility", "visible");
      }
   }

   onMouseUp(): void {
      if(this.resizeRow == -1 && this.resizeCol == -1) {
         return;
      }

      let resizeEvent: ResizeCalcTableCellEvent  =
         new ResizeCalcTableCellEvent(this.vsObjectModel.absoluteName,
            this.resizeRow, this.resizeCol, this.newWidth, this.newHeight,
            this.resizeOp);
      this.clientService
         .sendEvent("/events/vs/calctable/tablelayout/resize", resizeEvent);

      this.renderer.setStyle(this.colResize.nativeElement, "visibility", "hidden");
      this.renderer.setStyle(this.rowResize.nativeElement, "visibility", "hidden");

      this.prevMouseLocationx = -1;
      this.prevMouseLocationy = -1;
      this.resizeRow = -1;
      this.resizeCol = -1;
      this.resizeOp = null;
      this.document.removeEventListener("mouseup", this.endResizeListener);
   }

   public trackByIdx(index, item) {
      return index;
   }

   get isLoading(): boolean {
      return this.loadingCount > 0;
   }

   protected processAssemblyLoadingCommand(command: AssemblyLoadingCommand) {
      this.loadingCount += (command ? command.count : 1);
      this.detectChanges();
   }

   protected processClearAssemblyLoadingCommand(command: ClearAssemblyLoadingCommand) {
      this.loadingCount = Math.max(0, this.loadingCount - (command ? command.count : 1));
      this.detectChanges();
   }
}
