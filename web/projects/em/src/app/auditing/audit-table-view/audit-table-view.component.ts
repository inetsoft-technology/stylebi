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
import { HttpParams } from "@angular/common/http";
import { AfterViewInit, Component, Input, OnInit, ViewChild } from "@angular/core";
import { FormGroup } from "@angular/forms";
import { MatPaginator } from "@angular/material/paginator";
import { MatSort, Sort } from "@angular/material/sort";
import { MatTableDataSource } from "@angular/material/table";
import { EMPTY, Observable, of } from "rxjs";
import { mergeMap } from "rxjs/operators";
import { DateTypeFormatter } from "../../../../../shared/util/date-type-formatter";
import { AuditRecordList, AuditRecordParameters } from "./audit-record";

export interface AuditRowRenderer<R> {
   name: string;
   label: string;
   value: (value: R) => any;
}

@Component({
   selector: "em-audit-table-view",
   templateUrl: "./audit-table-view.component.html",
   styleUrls: ["./audit-table-view.component.scss"]
})
export class AuditTableViewComponent<R> implements OnInit, AfterViewInit {
   @Input() dateRangeVisible = true;
   @Input() displayedColumns: string[] = [];
   @Input() parameterForm: FormGroup;
   @Input() fetchParameters: () => Observable<AuditRecordParameters> = () => EMPTY;
   @Input() fetchData: (params: HttpParams, additional: { [key: string]: any; }) => Observable<AuditRecordList<R>> = () => EMPTY;
   @Input() columnRenderers: AuditRowRenderer<R>[] = [];
   @Input() isIdentityInfo: boolean = false;
   @Input() orgNames: string[] = [];

   @ViewChild(MatSort) sort: MatSort;
   @ViewChild(MatPaginator) paginator: MatPaginator;

   minDate = this.floorSeconds(new Date().getTime());
   maxDate = this.minDate + 1000;
   dataSource = new MatTableDataSource<R>([]);
   totalRowCount = 0;
   loading = false;
   sortColumn: string = ""
   sortDirection: string = ""
   initDate = 0;

   private _startDate = this.minDate;
   private _endDate = this.maxDate;

   constructor() {
   }

   get startDate(): number {
      return this._startDate;
   }

   set startDate(value: number) {
      this._startDate = value;
   }

   get startDateLabel(): string {
      return DateTypeFormatter.format(this.startDate, "YYYY-MM-DD HH:mm:ss", false);
   }

   get endDate(): number {
      return this._endDate;
   }

   set endDate(value: number) {
      this._endDate = value;
   }

   get endDateLabel(): string {
      return DateTypeFormatter.format(this.endDate, "YYYY-MM-DD HH:mm:ss", false);
   }

   ngOnInit(): void {
      this.loading = true;
      this.fetchParameters().subscribe(
         params=> {
            if(this.dateRangeVisible) {
               this.minDate = this.floorSeconds(params.startTime); // round down to the nearest second
               this.maxDate = this.floorSeconds(params.endTime) + 1000; // round up to the nearest second
               this._startDate = this.minDate;
               this._endDate = this.maxDate;
               this.initDate = this.maxDate;

               if(this._startDate < this._endDate - 24 * 60 * 60 * 1000) {
                  this._startDate = this._endDate - 24 * 60 * 60 * 1000;
               }
            }

            this.onParameterChange();
         },
         error => {
            console.error("Failed to get parameters: ", error);
            this.loading = false;
         });
   }

   ngAfterViewInit(): void {
      this.paginator.page.subscribe(() => this.onParameterChange());
   }

   apply() {
      this.loading = true;
      this.fetchParameters().subscribe(
         params=> {
            if(this.dateRangeVisible) {
               this.maxDate = this.floorSeconds(params.endTime) + 1000;// round up to the nearest second

               if(this.maxDate > this.initDate) {
                  this._endDate = this.maxDate;
                  this.initDate = this.maxDate;
               }
            }

            this.onParameterChange();
         },
         error => {
            console.error("Failed to get parameters: ", error);
            this.loading = false;
         }
      );
   }

   onParameterChange(): void {
      if(!this.parameterForm.valid) {
         this.loading = false;
         return;
      }

      this.loading = true;
      const additional = this.parameterForm.value;
      let params = new HttpParams()
         .set("offset", this.paginator.pageIndex * this.paginator.pageSize)
         .set("limit", this.paginator.pageSize)
         .set("sortColumn", this.sortColumn)
         .set("sortDirection", this.sortDirection);

      if(this.dateRangeVisible) {
         params = params
            .set("startTime", this._startDate)
            .set("endTime", this._endDate);
      }

      this.fetchData(params, additional)
         .pipe(mergeMap(list=> {
            // bug #64525, if filtered list is smaller than the current page row index, switch to
            // last page in filtered list
            if(this.paginator.pageIndex * this.paginator.pageSize >= list.totalRowCount) {
               const totalPageCount = Math.floor((list.totalRowCount / this.paginator.pageSize)) +
                  (list.totalRowCount % this.paginator.pageSize == 0 ? 0 : 1);
               this.paginator.pageIndex = totalPageCount - 1;
               params = params.set("offset", this.paginator.pageIndex * this.paginator.pageSize)
               return this.fetchData(params, additional);
            }

            return of(list);
         }))
         .subscribe({
            next: list => {
               this.totalRowCount = list.totalRowCount;
               this.dataSource.data = list.rows;
            },
            complete: () => this.loading = false
         });
   }

   changeSort(sortState: Sort) {
      this.sortColumn = sortState.active;
      this.sortDirection = sortState.direction;
      this.onParameterChange();
   }

   private floorSeconds(ts: number): number {
      return ts - (ts % 1000);
   }

   static getDisplayDate(ts: number, dateFormat: string): string {
      return DateTypeFormatter.format(ts, dateFormat, false);
   }
}
