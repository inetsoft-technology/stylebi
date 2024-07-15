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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";

@Component({
   selector: "tabular-list-editor",
   templateUrl: "tabular-list-editor.component.html",
   styleUrls: ["tabular-list-editor.component.scss"]
})
export class TabularListEditor implements OnInit {
   @Input() value: any[];
   @Input() enabled: boolean = true;
   @Input() required: boolean = false;
   @Input() type: string;
   @Input() password: boolean = false;
   @Input() rows: number = 1;
   @Input() columns: number = 0;
   @Input() label: string;
   @Input() property: string;
   @Input() dataSource: string;
   @Input() editorPropertyNames: string[];
   @Input() editorPropertyValues: string[];
   @Input() pattern: string[];
   @Input() tags: Array<string>;
   @Input() labels: Array<string>;
   @Output() valueChange: EventEmitter<any[]> = new EventEmitter<any[]>();
   @Output() validChange: EventEmitter<boolean> = new EventEmitter<boolean>();
   selected: boolean[] = [];

   ngOnInit(): void {
      for(let val in this.value) {
         if(this.value.hasOwnProperty(val)) {
            this.selected.push(false);
         }
      }

      this.validChange.emit(!this.required || (this.required && this.value != null));
   }

   addClicked(): void {
      if(this.value == null) {
         this.value = [];
      }

      this.value.push(null);
      this.selected.push(false);
      this.valueChange.emit(this.value);
      this.validChange.emit(!this.required || (this.required && this.value != null));
   }

   removeClicked(): void {
      for(let i = 0; i < this.selected.length; i++) {
         if(this.selected[i] == true) {
            this.value.splice(i, 1);
            this.selected.splice(i, 1);
            i--;
         }
      }

      this.valueChange.emit(this.value);
      this.validChange.emit(!this.required || (this.required && this.value != null));
   }

   valueChanged(): void {
      this.valueChange.emit(this.value);
      this.validChange.emit(!this.required || (this.required && this.value != null));
   }

   public trackByIdx(index: number): number {
      return index;
   }

   isSelected(): boolean {
      return this.selected.indexOf(true) >= 0;
   }
}
