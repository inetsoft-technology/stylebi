/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, EventEmitter, Input, OnInit, Output, Renderer2 } from "@angular/core";
import { ColumnDefinition } from "../../common/data/tabular/column-definition";

@Component({
   selector: "tabular-column-definition-editor",
   templateUrl: "tabular-column-definition-editor.component.html",
   styleUrls: ["tabular-column-definition-editor.component.scss"]
})
export class TabularColumnDefinitionEditor {
   @Input() enabled: boolean = true;
   @Input() required: boolean = false;
   @Output() valueChange: EventEmitter<ColumnDefinition[]> =
      new EventEmitter<ColumnDefinition[]>();
   @Output() validChange: EventEmitter<boolean> = new EventEmitter<boolean>();
   duplicateAlias: string;
   columnWidths: number[];
   resizePositions: number[];
   private readonly MIN_COL_WIDTH: number = 120;

   /** Column resizing fields */
   resizeLeft: number;
   private originalColumnWidth: number;
   private originalLeft: number;
   private resizeIndex: number;
   private pageXOrigin: number;
   private windowListeners: (() => void)[] = [];
   _value: ColumnDefinition[];

   @Input() set value(value: ColumnDefinition[]) {
      this._value = value;
      this.columnWidths = [];

      if(this._value) {
         for(let i = 0; i < this._value.length; i++) {
            this.columnWidths[i] = this.MIN_COL_WIDTH;
         }
      }

      this.updateResizePositions();

      Promise.resolve(null).then(() => {
         this.duplicateAlias = this.getDuplicateAlias();
         this.validChange.emit((!this.required || (this.required && this._value != null))
            && this.duplicateAlias == null);
      });
   }

   constructor(private renderer: Renderer2) {
   }

   valueChanged(): void {
      this.duplicateAlias = this.getDuplicateAlias();
      this.validChange.emit((!this.required || (this.required && this._value != null))
         && this.duplicateAlias == null);
      this.valueChange.emit(this._value);
   }

   getDuplicateAlias(): string {
      for(let i = 0; this._value && i < this._value.length; i++) {
         for(let j = i + 1; j < this._value.length; j++) {
            if(this._value[i].alias == this._value[j].alias) {
               return this._value[i].alias;
            }
         }
      }

      return null;
   }

   getElementHeight(ref: any): number {
      if(ref) {
         return ref.getBoundingClientRect().height;
      }

      return 0;
   }

   private updateResizePositions() {
      this.resizePositions = [];
      let width = 0;

      for(const colWidth of this.columnWidths) {
         width += colWidth;
         this.resizePositions.push(width);
      }
   }

   startResize(event: MouseEvent, index: number) {
      event.preventDefault();
      this.windowListeners = [
         this.renderer.listen("window", "mousemove", (e) => this.resizeMove(e)),
         this.renderer.listen("window", "mouseup", (e) => this.resizeEnd(e))
      ];

      this.resizeIndex = index;
      this.originalColumnWidth = this.columnWidths[index];
      this.originalLeft = this.resizePositions[index];
      this.resizeLeft = this.originalLeft;
      this.pageXOrigin = event.pageX;
   }

   private resizeMove(event: MouseEvent) {
      this.resizeLeft = this.originalLeft - this.pageXOrigin + event.pageX;
      const width = Math.max(this.resizeLeft - this.originalLeft + this.originalColumnWidth,
         this.MIN_COL_WIDTH);
      this.updateColumnSize(this.resizeIndex, width);
   }

   private resizeEnd(event: MouseEvent) {
      this.clearListeners();
      this.resizeLeft = this.originalLeft - this.pageXOrigin + event.pageX;
      const width = Math.max(this.resizeLeft - this.originalLeft + this.originalColumnWidth,
         this.MIN_COL_WIDTH);
      this.resizeLeft = undefined;
      this.updateColumnSize(this.resizeIndex, width);
      this.updateResizePositions();
   }

   private clearListeners() {
      this.windowListeners.forEach((listener) => listener());
      this.windowListeners = [];
   }

   private updateColumnSize(index: number, width: number) {
      this.columnWidths[index] = width;
   }
}
