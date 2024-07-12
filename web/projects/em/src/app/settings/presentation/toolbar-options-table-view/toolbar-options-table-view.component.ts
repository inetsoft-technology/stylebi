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
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from "@angular/core";

export interface ToolbarOption {
   id: string;
   visible: boolean;
   enabled: boolean;
   arrange?: boolean;
   alias?: string;
}

@Component({
   selector: "em-toolbar-options-table-view",
   templateUrl: "./toolbar-options-table-view.component.html",
   styleUrls: ["./toolbar-options-table-view.component.scss"]
})
export class ToolbarOptionsTableViewComponent implements OnChanges {
   @Input() dataSource: ToolbarOption[];
   @Input() title: string = "_#(js:Toolbar Options)";
   @Input() reorder: boolean = true;
   @Output() toolbarSettingsChanged: EventEmitter<ToolbarOption[]> = new EventEmitter();
   columnNames: string[] = ["id", "visible", "arrange"];

   ngOnChanges(changes: SimpleChanges) {
      if((changes.dataSource || changes.reorder) && this.dataSource != null) {
         if(this.reorder) {
            for(let row of this.dataSource) {
               row.arrange = true;
            }
         }
         else {
            this.columnNames = ["id", "visible"];
         }
      }
   }

   upButtonEnabled(index: number): boolean {
      return index > 0 && index < this.dataSource.length - 2;
   }

   downButtonEnabled(index: number): boolean {
      return index >= 0 && index < this.dataSource.length - 3;
   }

   moveUp(index: number) {
      const array = this.dataSource.slice(0);
      const element = array.splice(index, 1);
      array.splice(index - 1, 0, ...element);
      this.dataSource = array;
      this.fireToolbarSettingsChangedEvent();
   }

   moveDown(index: number) {
      const array = this.dataSource.slice(0);
      const element = array.splice(index, 1);
      array.splice(index + 1, 0, ...element);
      this.dataSource = array;
      this.fireToolbarSettingsChangedEvent();
   }

   changeVisible(row: ToolbarOption) {
      this.fireToolbarSettingsChangedEvent();
   }

   fireToolbarSettingsChangedEvent() {
      let array = this.dataSource.map((elem) => {
         return { id: elem.id, visible: elem.visible, enabled: elem.enabled };
      });
      this.toolbarSettingsChanged.emit(array);
   }
}
