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
import { SelectionModel } from "@angular/cdk/collections";
import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { MatTableDataSource } from "@angular/material/table";
import { TimeRange } from "../../../../../../../shared/schedule/model/time-condition-model";
import { DateTypeFormatter } from "../../../../../../../shared/util/date-type-formatter";
import { TimeRangeEditorComponent } from "../time-range-editor/time-range-editor.component";
import { ResourcePermissionModel } from "../../../security/resource-permission/resource-permission-model";

@Component({
   selector: "em-time-ranges-view",
   templateUrl: "./time-ranges-view.component.html",
   styleUrls: ["./time-ranges-view.component.scss"]
})
export class TimeRangesViewComponent implements OnInit, OnChanges {
   @Output() timeRangesChanged = new EventEmitter<TimeRange[]>();

   @Input()
   get timeRanges(): TimeRange[] {
      return this._timeRanges;
   }

   set timeRanges(ranges: TimeRange[]) {
      this._timeRanges = ranges || [];
      this.dataSource.data = this._timeRanges;
   }

   displayedColumns = ["select", "name", "startTime", "endTime"];
   dataSource = new MatTableDataSource<TimeRange>([]);
   selection = new SelectionModel<TimeRange>(true, []);
   templatePermissionModel: ResourcePermissionModel;

   private _timeRanges: TimeRange[] = [];

   constructor(private dialog: MatDialog) {
   }

   ngOnChanges() {
      this.selection.clear();

      if(this.templatePermissionModel == null && this._timeRanges && this._timeRanges.length > 0) {
         this.templatePermissionModel = this._timeRanges[0].permissions;
      }
   }

   ngOnInit() {
   }

   isAllSelected(): boolean {
      const numSelected = this.selection.selected.length;
      const numRows = this.dataSource.data.length;
      return numSelected === numRows;
   }

   masterToggle(): void {
      this.isAllSelected() ?
         this.selection.clear() :
         this.dataSource.data.forEach((row) => this.selection.select(row));
   }

   addTimeRange(): void {
      this.openTimeRangeEditor();
   }

   editTimeRange(range: TimeRange): void {
      this.selection.clear();
      this.selection.select(range);
      this.openTimeRangeEditor(range);
   }

   removeTimeRange(): void {
      const selected = this.selection.selected.slice();
      selected.forEach((row) => {
         const idx = this.timeRanges.indexOf(row);

         if(idx >= 0) {
            this.timeRanges.splice(idx, 1);
         }
      });
      this.dataSource.data = this.timeRanges;
      this.selection.clear();
      this.timeRangesChanged.emit(this.timeRanges);
   }

   formatTime(value: string): string {
      let instant = DateTypeFormatter.toTimeInstant(value, "HH:mm:ss");
      let date = DateTypeFormatter.timeInstantToDate(instant);
      let formatValue = DateTypeFormatter.format(date, "hh:mm A");

      if(formatValue == null) {
         instant = DateTypeFormatter.toTimeInstant(value, "HH:mm");
         date = DateTypeFormatter.timeInstantToDate(instant);

         return DateTypeFormatter.format(date, "hh:mm A");
      }

      return formatValue;
   }

   private openTimeRangeEditor(oldRange?: TimeRange): void {
      const config = {
         maxWidth: "100%",
         maxHeight: "100%",
         disableClose: true,
         data: {
            range: oldRange || this.createTimeRange(),
            ranges: this.timeRanges
         }
      };
      this.dialog.open(TimeRangeEditorComponent, config).afterClosed().subscribe(
         (range: TimeRange) => {
            if(range) {
               range.modified = true;

               if(range.defaultRange && (!oldRange || !oldRange.defaultRange)) {
                  this.timeRanges.forEach(r => r.defaultRange = false);
               }

               if(oldRange) {
                  if(oldRange.name != range.name) {
                     range.label = range.name;
                  }

                  Object.assign(oldRange, range);
               }
               else {
                  this.timeRanges.push(range);
               }

               this.dataSource.data = this.timeRanges;
               this.timeRangesChanged.emit(this.timeRanges);
            }
         });
   }

   private createTimeRange(): TimeRange {
      const permissions = this.templatePermissionModel == null ? null :
         <ResourcePermissionModel> {
            displayActions: this.templatePermissionModel.displayActions,
            securityEnabled: this.templatePermissionModel.securityEnabled,
            requiresBoth: this.templatePermissionModel.requiresBoth,
            derivePermissionLabel: this.templatePermissionModel.derivePermissionLabel,
            grantReadToAll: false,
            grantReadToAllVisible: this.templatePermissionModel.grantReadToAllVisible,
            grantReadToAllLabel: this.templatePermissionModel.grantReadToAllLabel,
            changed: false,
         };

      return { name: null, startTime: null, endTime: null, defaultRange: false, permissions: permissions };
   }
}
