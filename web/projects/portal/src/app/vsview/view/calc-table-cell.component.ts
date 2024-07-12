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
import { Component, ElementRef, EventEmitter, Input, Output } from "@angular/core";
import { VSCalcTableEditorService } from "../../binding/services/table/vs-calc-table-editor.service";
import { DataRefType } from "../../common/data/data-ref-type";
import { CalcDropTarget, CalcTableTransfer } from "../../common/data/dnd-transfer";
import { DragEvent } from "../../common/data/drag-event";
import { Rectangle } from "../../common/data/rectangle";
import { TableCell } from "../../common/data/tablelayout/table-cell";
import { DndService } from "../../common/dnd/dnd.service";
import { Tool } from "../../../../../shared/util/tool";
import { VSFormatModel } from "../../vsobjects/model/vs-format-model";

@Component({
   selector: "calc-table-cell",
   templateUrl: "calc-table-cell.component.html",
   styleUrls: ["calc-table-cell.component.scss"]
})
export class CalcTableCellComponent {
   _cell: TableCell;
   @Input() spanCell: TableCell; // original cell if this is part of a span cell
   @Input() cellWords: string[] = [];
   @Input() columnWidth: number;
   @Input() rowHeight: number;
   @Input() rowIndex: number;
   @Input() columnIndex: number;
   @Input() assemblyName: string;
   @Output() cellResize = new EventEmitter<any>();
   @Output() dragOverChange: EventEmitter<TableCell> = new EventEmitter<TableCell>();

   @Input() set cell(c: TableCell) {
      this._cell = c;
      this.cellWords = this.createCellWords(this._cell.text);
   }

   get cell(): TableCell {
      return this._cell;
   }

   public constructor(private dndService: DndService,
                      private editorService: VSCalcTableEditorService,
                      private elem: ElementRef)
   {
   }

   get selectedCells(): any[] {
      return this.editorService.getSelectCells();
   }

   private get cellFormat(): VSFormatModel {
      return <VSFormatModel> this.cell.vsFormat;
   }

   getDivStyle() {
      const dragOver: boolean = this.cell.isDragOver || this.spanCell && this.spanCell.isDragOver;
      const style = {
         "overflow": "hidden",
         "width": this.columnWidth + "px",
         "height": this.rowHeight + "px",
         "color": this.cellFormat.foreground,
         "font": this.cellFormat.font ? this.cellFormat.font : "11px arial",
         "text-align": this.cellFormat.hAlign,
         "cursor": "default",
         "border-top": dragOver ? "2px solid #66DD66" : "2px solid transparent",
         "border-left": dragOver ? "2px solid #66DD66" : "2px solid transparent",
         "border-bottom": dragOver ? "2px solid #66DD66" : "2px solid transparent",
         "border-right": dragOver ? "2px solid #66DD66" : "2px solid transparent",
         "text-decoration": this.cellFormat.decoration
      };

      if(this.spanCell) {
         if(this.cell.col != this.spanCell.col) {
            style["border-left"] = "none";
         }

         if(this.cell.col + 1 != this.spanCell.col + this.spanCell.span.width) {
            style["border-right"] = "none";
         }

         if(this.cell.row != this.spanCell.row) {
            style["border-top"] = "none";
         }

         if(this.cell.row + 1 != this.spanCell.row + this.spanCell.span.height) {
            style["border-bottom"] = "none";
         }
      }
      else if(this.cell.span) {
         if(this.cell.span.width > 1) {
            style["border-right"] = "none";
         }

         if(this.cell.span.height > 1) {
            style["border-bottom"] = "none";
         }
      }

      return style;
   }

   getTextTableStyle() {
      let style = {
         "display": "table",
         "width": this.columnWidth + "px",
         "height": this.rowHeight + "px"
      };

      return style;
   }

   getTextCellStyle() {
      let style = {
         "display": "table-cell",
         "vertical-align": this.cellFormat.vAlign,
         "text-align": "left"
      };

      return style;
   }

   createCellWords(text: string): string[] {
      let wsp = /(\s+)/;
      let words = !!text ? text.split(wsp) : [];
      return words;
   }

   dragStarted(event: DragEvent) {
      let transfer: CalcTableTransfer =
         new CalcTableTransfer(this.getCellRect(this.cell), this.assemblyName);
      Tool.setTransferData(event.dataTransfer, {"dragSource": transfer});
      this.dndService.setDragStartStyle(event, this.cell.text);
   }

   public dragOver(event: DragEvent): void {
      event.preventDefault();

      const isDragOver = this.cell.isDragOver || this.spanCell && this.spanCell.isDragOver;

      if(this.isDragAccaptable() && !isDragOver) {
         this.spanCell ? this.spanCell.isDragOver = true : this.cell.isDragOver = true;
         this.dragOverChange.emit(this.cell);
      }
   }

   private isDragAccaptable(): boolean {
      const trans: any = this.dndService.getTransfer();

      if(!trans || Object.keys(trans).length == 0) {
         return false;
      }

      if(!trans.column) {
         return true;
      }

      let values: any = trans.column;

      for(let i = 0; i < values.length; i++) {
         if(!values[i]) {
            return false;
         }

         let val: any = values[i];

         if(val.properties && val.properties.refType != null) {
            let type: number = parseInt(val.properties.refType, 10);

            if((type & DataRefType.CUBE) != 0) {
               return false;
            }
         }
      }

      return true;
   }

   dragLeave(): void {
      this.spanCell ? delete this.spanCell.isDragOver : delete this.cell.isDragOver;
      this.dragOverChange.emit(this.cell);
   }

   dropOnCell(event: DragEvent) {
      event.preventDefault();

      if(!this.isDragAccaptable()) {
         return;
      }

      const cell: TableCell = this.cell.baseInfo ? this.spanCell : this.cell;
      this.cell.baseInfo ? delete this.spanCell.isDragOver : delete this.cell.isDragOver;
      let dropTarget: CalcDropTarget = new CalcDropTarget(this.getCellRect(cell),
                                                          this.assemblyName);
      this.dndService.processOnDrop(event, dropTarget);
      this.editorService.getTableLayout().selectedRect = this.getCellRect(cell);
      this.editorService.setSelectCells([].concat(cell));
   }

   private getCellRect(cell: TableCell): Rectangle {
      return new Rectangle(
         cell.col,
         cell.row,
         cell.span == null ? 1 : cell.span.width,
         cell.span == null ? 1 : cell.span.height
      );
   }

   resizeCell(event: MouseEvent, op: string): void {
      if(event.button != 0) {
         return;
      }

      event.preventDefault();
      event.stopPropagation();
      this.cellResize.emit({x: event.pageX, y: event.pageY, op: op});
   }

   getResizeColStyle() {
      return {
         "top": "0px",
         "right": "0px",
         "width": "5px",
         "height": (this.getClientRect().height - 5) + "px",
      };
   }

   getResizeRowStyle() {
      return {
         "bottom": "0px",
         "left": "0px",
         "width": (this.getClientRect().width - 5) + "px",
         "height": "5px",
      };
   }

   getClientRect() {
      return this.elem.nativeElement.parentElement.getBoundingClientRect();
   }
}
