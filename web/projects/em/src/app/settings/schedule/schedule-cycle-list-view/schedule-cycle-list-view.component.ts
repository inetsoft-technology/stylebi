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
   Component,
   EventEmitter,
   Input,
   OnChanges, OnInit,
   Output,
   SimpleChanges, ViewChild
} from "@angular/core";
import { SelectionModel } from "@angular/cdk/collections";
import { MatSort } from "@angular/material/sort";
import { MatTableDataSource } from "@angular/material/table";
import { DataCycleInfo } from "../model/data-cycle-list-model";

@Component({
   selector: "em-schedule-cycle-list-view",
   templateUrl: "./schedule-cycle-list-view.component.html",
   styleUrls: ["./schedule-cycle-list-view.component.scss"]
})
export class ScheduleCycleListViewComponent implements OnInit, OnChanges {
   _dataSource: DataCycleInfo[];
   ds: MatTableDataSource<DataCycleInfo>;
   private searchStr = "";

   @Input() set dataSource(dataSource: DataCycleInfo[]) {
      this._dataSource = dataSource;
      this.ds = new MatTableDataSource(this.dataSource);

      if(!!this.dataSource) {
         this.ds.sort = this.sort;

         if(!!this._dataSource) {
            this._dataSource = this._dataSource.sort((a, b) => {
               let compareA = a.name.toLowerCase();
               let compareB = b.name.toLowerCase();

               return compareA < compareB ? -1 : 1;
            });
         }

         this.ds = new MatTableDataSource(this._dataSource);
      }

      this.ds.filter = this.searchStr;
   }

   get dataSource(): DataCycleInfo[] {
      return this._dataSource;
   }

   @Output() addCycle = new EventEmitter();
   @Output() editCycle = new EventEmitter<string>();
   @Output() removeCycles = new EventEmitter<DataCycleInfo[]>();

   @ViewChild(MatSort, { static: true }) sort: MatSort;

   selection = new SelectionModel<DataCycleInfo>(true, []);
   columns = ["selected", "name", "condition"];

   ngOnInit(): void {
      if(this.sort) {
         this.sort.active = "name";
         this.sort.direction = this.sort.start;
      }
   }

   sortData(sort: any) {
      if(!!this._dataSource) {
         let d = this.sort.direction === "asc" ? 1 :
            (this.sort.direction === "desc" ? -1 : 0);

         this._dataSource = this._dataSource.sort((a, b) => {
            let compareA = a.name.toLowerCase();
            let compareB = b.name.toLowerCase();

            return compareA < compareB ? -d : d;
         });
      }

      this.ds = new MatTableDataSource(this._dataSource);
      this.ds.filter = this.searchStr;
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.dataSource && this.dataSource != null) {
         const selectedIds = this.selection.selected.map((s) => s["name"]);
         const selected: DataCycleInfo[] = this.dataSource.filter(
            (row) => selectedIds.indexOf(row["name"]) !== -1
         );
         this.selection = new SelectionModel<DataCycleInfo>(true, selected);
      }
   }

   isAllSelected(): boolean {
      return this.selection.selected.length === this.dataSource.length;
   }

   /** Selects all rows if they are not all selected; otherwise clear selection. */
   masterToggle() {
      this.isAllSelected() ? this.selection.clear() :
         this.dataSource.forEach(row => this.selection.select(row));
   }

   applyFilter(filterValue: string): void {
      this.ds.filter = this.searchStr = filterValue.trim().toLowerCase();
   }
}
