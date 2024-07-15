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
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from "@angular/core";
import { MatTableDataSource } from "@angular/material/table";
import { TableModel } from "./table-model";
import { TableInfo } from "./table-info";
import { SelectionModel } from "@angular/cdk/collections";
import { DeviceType } from "./expandable-row-table/expandable-row-table.component";
import { CustomSortDataSource } from "./custom-sort-data-source";

export enum TableAction {
   DELETE, EDIT, ADD
}

@Component({
   selector: "em-table-view",
   templateUrl: "./table-view.component.html",
   styleUrls: ["./table-view.component.scss"]
})
export class TableView<T extends TableModel> implements OnChanges {
   TableAction = TableAction;
   @Input() flatCard = false;
   @Input() trackByProp: string = "id";
   @Input() dataSource: T[] = [];
   @Input() tableInfo: TableInfo;
   @Input() selectAllOnInit = false;
   @Input() collapsible = false;
   @Input() expanded = true;
   @Input() noScrollCount: number;
   @Input() linkFields: string[];
   @Input() emptyError: string = "";
   @Input() showEmptyError: boolean = false;
   @Input() expandableRow: boolean = false;
   @Input() fillContainer: boolean = false;
   @Input() fillFlexContainer: boolean = false;
   @Input() fitContent: boolean = false;
   @Input() sortingDataAccessor: ((data: T, sortHeaderId: string) => string | number);
   @Input() sortingTimeCols: string[];
   @Output() removeSelection = new EventEmitter<T[]>();
   @Output() editSelected = new EventEmitter<T>();
   @Output() add = new EventEmitter<void>();
   @Output() selectionChanged = new EventEmitter<SelectionModel<TableModel>>(true);
   @Output() clickCell = new EventEmitter<any>();
   @Output() deviceTypeChanged = new EventEmitter<DeviceType>();
   matTableDataSource: MatTableDataSource<TableModel>;
   displayColumns: string[] = [];
   selection = new SelectionModel<TableModel>(true, []);

   ngOnChanges(changes: SimpleChanges) {
      if(changes.dataSource && this.dataSource != null) {
         this.refreshDataSource(changes);
      }

      if((changes.dataSource || changes.tableInfo) && this.tableInfo != null) {
         this.refreshTableInfo();
      }
   }

   get fields(): string[] {
      return this.tableInfo.columns.map((col) => col.field);
   }

   get headers(): string[] {
      return this.tableInfo.columns.map((col) => col.header);
   }

   get maxHeight(): string {
      return this.noScrollCount ? (this.noScrollCount + 1) * 48 + "" : "unset";
   }

   get emptyErrorVisible() {
      return this.showEmptyError && (!this.dataSource || this.dataSource.length == 0);
   }

   refreshDataSource(changes: SimpleChanges) {
      this.matTableDataSource = new CustomSortDataSource(this.dataSource,
         this.sortingTimeCols, this.sortingDataAccessor);

      if(this.tableInfo?.selectionEnabled) {
         const selectedIds = this.selection.selected.map((s) => s[this.trackByProp]);
         const selected: TableModel[] = this.dataSource.filter(
            (row) => selectedIds.indexOf(row[this.trackByProp]) !== -1
         );
         this.selection = new SelectionModel<TableModel>(true, selected);

         if(this.selectAllOnInit && changes.dataSource.previousValue == null) {
            this.selection.clear();
            this.matTableDataSource.data.forEach(row => this.selection.select(row));
         }

         this.selectionChanged.emit(this.selection);
      }
   }

   refreshTableInfo() {
      this.displayColumns = this.fields.slice(0);

      if(this.tableInfo.selectionEnabled) {
         this.displayColumns.unshift("selected");
      }
   }

   isSingleSelection(): boolean {
      return this.selection.hasValue() && this.selection.selected.length === 1;
   }

   hasSelection(): boolean {
      return this.selection != null && this.selection.selected.length > 0;
   }

   hasButtons() {
     return (!this.tableInfo.actions) ||
      (this.tableInfo.actions.includes(TableAction.DELETE) ||
      this.tableInfo.actions.includes(TableAction.EDIT) ||
      this.tableInfo.actions.includes(TableAction.ADD));
   }
}
