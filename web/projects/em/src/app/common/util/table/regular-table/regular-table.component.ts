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
import { SelectionModel } from "@angular/cdk/collections";
import { AfterViewInit, Component, EventEmitter, Input, Output, ViewChild } from "@angular/core";
import { MatSort } from "@angular/material/sort";
import { MatTableDataSource } from "@angular/material/table";
import { TableInfo } from "../table-info";
import { TableModel } from "../table-model";

@Component({
   selector: "em-regular-table",
   templateUrl: "./regular-table.component.html",
   styleUrls: ["./regular-table.component.scss"]
})
export class RegularTableComponent<T extends TableModel> implements AfterViewInit {
   @Input() maxHeight: number;
   @Input() selection: SelectionModel<T>;
   @Input() displayColumns: string[];
   @Input() tableInfo: TableInfo;
   @Input() linkFields: string[];
   @Output() selectionChanged = new EventEmitter<SelectionModel<TableModel>>(true);
   @Output() clickCell = new EventEmitter<any>();
   @ViewChild(MatSort) sort: MatSort;
   private _matTableDataSource: MatTableDataSource<T>;

   @Input() set matTableDataSource(value: MatTableDataSource<T>) {
      this._matTableDataSource = value;

      if(this._matTableDataSource && !this._matTableDataSource.sort) {
         this._matTableDataSource.sort = this.sort;
      }
   }

   get matTableDataSource(): MatTableDataSource<T> {
      return this._matTableDataSource;
   }

   ngAfterViewInit() {
      if(this.matTableDataSource && !this.matTableDataSource.sort) {
         this.matTableDataSource.sort = this.sort;
      }
   }

   /** Selects all rows if they are not all selected; otherwise clear selection. */
   masterToggle() {
      this.isAllSelected() ?
         this.selection.clear() :
         this.matTableDataSource.data.forEach(row => this.selection.select(row));
      this.selectionChanged.emit(this.selection);
   }

   isAllSelected(): boolean {
      return this.selection.selected.length === this.matTableDataSource.data.length;
   }

   toggleRow(row) {
      this.selection.toggle(row);
      this.selectionChanged.emit(this.selection);
   }

   clickTableCell(r: any, field: string) {
      if(this.clickCell != null) {
         this.clickCell.emit({row: r, property: field});
      }
   }

   get fields(): string[] {
      return this.tableInfo.columns.map((col) => col.field);
   }

   get headers(): string[] {
      return this.tableInfo.columns.map((col) => col.header);
   }

   hasLink(field: string): boolean {
      if(this.linkFields == null) {
         return false;
      }

      for(let fld of this.linkFields) {
         if(fld == field) {
            return true;
         }
      }

      return false;
   }
}
