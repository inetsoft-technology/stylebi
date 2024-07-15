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
import { animate, state, style, transition, trigger } from "@angular/animations";
import { BreakpointObserver, Breakpoints } from "@angular/cdk/layout";
import {
   ChangeDetectorRef,
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { Subject } from "rxjs";
import { takeUntil } from "rxjs/operators";
import { ColumnInfo } from "../column-info";
import { RegularTableComponent } from "../regular-table/regular-table.component";
import { TableModel } from "../table-model";
import { ExpandableRowTableInfo } from "./expandable-row-table-info";

export enum DeviceType {
   SMALL = 0,
   MEDIUM = 1,
   LARGE = 2
}

@Component({
   selector: "em-expandable-row-table",
   templateUrl: "./expandable-row-table.component.html",
   styleUrls: ["./expandable-row-table.component.scss"],
   animations: [
      trigger("detailExpand", [
         state("collapsed, void", style({height: "0px", minHeight: "0"})),
         state("expanded", style({height: "*"})),
         transition("expanded <=> collapsed", animate("225ms cubic-bezier(0.4, 0.0, 0.2, 1)")),
         transition("expanded <=> void", animate("225ms cubic-bezier(0.4, 0.0, 0.2, 1)")),
      ]),
   ],
})
export class ExpandableRowTableComponent<T extends TableModel> extends RegularTableComponent<T> implements OnChanges, OnInit, OnDestroy {
   @Input() tableInfo: ExpandableRowTableInfo;
   @Input() fillContainer: boolean;
   @Output() deviceTypeChanged = new EventEmitter<DeviceType>();
   deviceType = DeviceType.LARGE;
   expandedElement: T | null;
   allColumns: ColumnInfo[] = [];
   private destroy$ = new Subject<void>();

   constructor(private breakpointObserver: BreakpointObserver,
               private changeDetector: ChangeDetectorRef)
   {
      super();
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.tableInfo) {
         this.refreshColumns();
      }
   }

   ngOnInit() {
      this.allColumns = this.tableInfo.columns.slice();
      this.breakpointObserver.observe(Breakpoints.Handset)
         .pipe(takeUntil(this.destroy$))
         .subscribe(result => {
            if(result.matches) {
               setTimeout(() => {
                  this.deviceType = DeviceType.SMALL;
                  this.refreshColumns();
                  this.deviceTypeChanged.emit(this.deviceType);
               });
            }
         });
      this.breakpointObserver.observe(Breakpoints.Tablet)
         .pipe(takeUntil(this.destroy$))
         .subscribe(result => {
            if(result.matches) {
               setTimeout(() => {
                  this.deviceType = DeviceType.MEDIUM;
                  this.refreshColumns();
                  this.deviceTypeChanged.emit(this.deviceType);
               });
            }
         });
      this.breakpointObserver.observe(Breakpoints.Web)
         .pipe(takeUntil(this.destroy$))
         .subscribe(result => {
            if(result.matches) {
               setTimeout(() => {
                  this.deviceType = DeviceType.LARGE;
                  this.refreshColumns();
                  this.deviceTypeChanged.emit(this.deviceType);
               });
            }
         });
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   get showExpandTable(): boolean {
      const visibleColumns = this.visibleColumns;
      return !!visibleColumns && !!this.allColumns &&
         visibleColumns.length !== this.allColumns.length;
   }

   get visibleColumns(): ColumnInfo[] {
      let visibleColumns;

      if(this.isHandsetDevice() && this.tableInfo.smallDeviceHeaders) {
         visibleColumns = this.tableInfo.smallDeviceHeaders;
      }
      else if((this.isTabletDevice() || this.isHandsetDevice()) &&
         this.tableInfo.mediumDeviceHeaders)
      {
         visibleColumns = this.tableInfo.mediumDeviceHeaders;
      }
      else if(this.tableInfo.largeDeviceHeaders) {
         visibleColumns = this.tableInfo.largeDeviceHeaders;
      }
      else {
         visibleColumns = this.allColumns;
      }

      return visibleColumns;
   }

   get expandingFields(): string[] {
      if(!this.showExpandTable && !this.tableInfo) {
         return [];
      }

      const visibleColumnFields = this.visibleColumns.map((col) => col.field);
      return this.allColumns
         .map(col => col.field)
         .filter(field => visibleColumnFields.indexOf(field) < 0);
   }

   get expandingHeaders(): string[] {
      if(!this.showExpandTable || !this.tableInfo) {
         return [];
      }

      const visibleColumnHeaders = this.visibleColumns.map((col) => col.header);
      return this.allColumns
         .map(col => col.header)
         .filter(header => visibleColumnHeaders.indexOf(header) < 0);
   }

   refreshColumns() {
      if(!!this.tableInfo && this.allColumns.length > 0) {
         this.tableInfo.columns = this.visibleColumns.slice();

         if(!this.showExpandTable) {
            this.expandedElement = null;
         }
      }

      this.refreshTableInfo();
      this.changeDetector.detectChanges();
   }

   isHandsetDevice(): boolean {
      return this.deviceType === DeviceType.SMALL;
   }

   isTabletDevice(): boolean {
      return this.deviceType === DeviceType.MEDIUM;
   }

   refreshTableInfo() {
      this.displayColumns = this.fields.slice(0);

      if(this.tableInfo.selectionEnabled) {
         this.displayColumns.unshift("selected");
      }
   }

   expandRow(row: T) {
      if(this.expandedElement === row || !this.showExpandTable) {
         this.expandedElement = null;
      }
      else {
         this.expandedElement = row;
      }
   }
}