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
import {SelectionModel} from "@angular/cdk/collections";
import {HttpClient} from "@angular/common/http";
import {Component, OnDestroy, OnInit} from "@angular/core";
import {MatDialog, MatDialogConfig} from "@angular/material/dialog";
import {MatSnackBarConfig} from "@angular/material/snack-bar";
import {Subject} from "rxjs";
import {takeUntil} from "rxjs/operators";
import {AnalyzeMVResponse} from "../../../../../../../shared/util/model/mv/analyze-mv-response";
import {MaterializedModel} from "../../../../../../../shared/util/model/mv/materialized-model";
import {MVExceptionResponse} from "../../../../../../../shared/util/model/mv/mv-exception-response";
import {NameLabelTuple} from "../../../../../../../shared/util/name-label-tuple";
import {Tool} from "../../../../../../../shared/util/tool";
import {ExpandableRowTableInfo} from "../../../../common/util/table/expandable-row-table/expandable-row-table-info";
import {MessageDialog, MessageDialogType} from "../../../../common/util/message-dialog";
import {ColumnInfo} from "../../../../common/util/table/column-info";
import {TableModel} from "../../../../common/util/table/table-model";
import {PageHeaderService} from "../../../../page-header/page-header.service";
import {ConnectionStatus} from "../../../security/security-provider/security-provider-model/connection-status";
import {MvExceptionsDialogComponent} from "../../repository/mv-exceptions-dialog/mv-exceptions-dialog.component";
import {MVManagementModel} from "./mv-management-model";
import {DeviceType} from "../../../../common/util/table/expandable-row-table/expandable-row-table.component";
import {SortTypes} from "../../../../../../../shared/util/sort/sort-types";
import {MVChangeService} from "./mv-change.service";

@Component({
   selector: "em-mv-management-view",
   templateUrl: "./mv-management-view.component.html",
   styleUrls: ["./mv-management-view.component.scss"],
   providers: [
      MVChangeService
   ]
})
export class MvManagementViewComponent implements OnInit, OnDestroy {
   // Table
   headers: string[] = ["_#(js:Asset)", "_#(js:Table)", "_#(js:Users)", "_#(js:Has Data)", "_#(js:Last Modified)", "_#(js:Status)", "_#(js:Cycle)", "_#(js:Incremental)", "_#(js:Size MB)", "_#(js:Valid)"];
   headerCols = {
      "_#(js:Asset)": "sheets",
      "_#(js:Table)": "table",
      "_#(js:Users)": "users",
      "_#(js:Has Data)": "dataString",
      "_#(js:Last Modified)": "lastModifiedTime",
      "_#(js:Status)": "status",
      "_#(js:Cycle)": "cycle",
      "_#(js:Incremental)": "incrementalString",
      "_#(js:Size MB)": "size",
      "_#(js:Valid)": "validString"
   };
   mvSmallDeviceExpandingColumns = {};
   mvMediumDeviceExpandingColumns = {};
   mvLargeDeviceExpandingColumns = {};
   dataSource: MaterializedModel[] = [];
   columnInfo: ColumnInfo[] = [];
   tableInfo: ExpandableRowTableInfo;
   sortingDataAccessor: ((data: MaterializedModel, sortHeaderId: string) => string | number);

   // Selects
   showStatus: string[] = ["_#(js:All)"];
   showCycle: string[] = ["_#(js:All)"];
   showUser: string[] = ["_#(js:All)"];
   setCycle: NameLabelTuple[] = [];
   selectedStatus: string = this.showStatus[0];
   selectedCycle: string = this.showCycle[0];
   selectedUser: string = this.showUser[0];
   selectedSetCycle: string = "";
   selection: SelectionModel<TableModel> = new SelectionModel();
   selectedHeaders: string[] = [];
   deviceType: DeviceType = DeviceType.LARGE;

   // Checkboxes
   runInBackground: boolean = false;
   showDateAsAges: boolean = false;

   // loading animation
   loading: boolean = false;

   unfilteredList: MaterializedModel[] = [];

   private destroy$ = new Subject<void>();
   SortTypes = SortTypes;
   cycleNameSort: SortTypes = SortTypes.ASCENDING;
   setCycleNameSort: SortTypes = SortTypes.ASCENDING;

   constructor(private pageTitle: PageHeaderService,
               private http: HttpClient,
               private dialog: MatDialog,
               private mvChangeService: MVChangeService)
   {
      this.mvSmallDeviceExpandingColumns = {
         "_#(js:Asset)": "sheets"
      };
      this.mvMediumDeviceExpandingColumns = {
         "_#(js:Asset)": "sheets",
         "_#(js:Status)": "status"
      };
      this.mvLargeDeviceExpandingColumns = Tool.clone(this.headerCols);

      this.selectedHeaders = Object.keys(this.mvLargeDeviceExpandingColumns);
      this.columnInfo = this.generateColumns(this.mvLargeDeviceExpandingColumns);
      this.refreshTableInfo();

      this.sortingDataAccessor = (data, sortHeaderId) => {
         if(sortHeaderId === "lastModifiedTime") {
            return (this.showDateAsAges ? -1 : 1) * data["lastModifiedTimestamp"];
         }

         return data[sortHeaderId];
      };

      this.mvChangeService.mvChanged
         .pipe(takeUntil(this.destroy$))
         .subscribe(() => this.refreshMVList());
   }

   private refreshTableInfo(): void {
      const mvSmallDeviceExpandingColumnInfo = this.generateColumns(
         this.mvSmallDeviceExpandingColumns);
      const mvMediumDeviceExpandingColumnInfo = this.generateColumns(
         this.mvMediumDeviceExpandingColumns);
      const mvLargeDeviceExpandingColumnInfo = this.generateColumns(
         this.mvLargeDeviceExpandingColumns);

      this.tableInfo = <ExpandableRowTableInfo>{
         title: "",
         selectionEnabled: true,
         columns: this.columnInfo,
         smallDeviceHeaders: mvSmallDeviceExpandingColumnInfo,
         mediumDeviceHeaders: mvMediumDeviceExpandingColumnInfo,
         largeDeviceHeaders: mvLargeDeviceExpandingColumnInfo,
         actions: []
      };
   }

   ngOnInit(): void {
      this.pageTitle.title = "Materialized Views";
      this.refreshMVList(true);
   }

   ngOnDestroy(): void {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   selectionChanged(selection: SelectionModel<TableModel>): void {
      this.selection = selection;

      if(this.selection.selected.length) {
         let cycles: string[] = this.selection.selected.map(sel => (<MaterializedModel>sel).cycle);
         let sameSetCycle = cycles.every(cyc => cyc === cycles[0]);
         this.selectedSetCycle =
            sameSetCycle ? (<MaterializedModel>this.selection.selected[0]).cycle :
               this.setCycle[0].name; // since they'll be the same, we can just take the first one
      }
   }

   refreshMVList(init: boolean = false): void {
      this.http.post("../api/em/content/materialized-view/info", null)
         .subscribe((model: MVManagementModel) => {
            model.mvs.map(mv => {
               mv.dataString = mv.hasData ? "_#(js:Yes)" : "_#(js:No)";
               mv.incrementalString = mv.incremental ? "_#(js:Yes)" : "_#(js:No)";
               mv.validString = mv.valid ? "_#(js:True)" : "_#(js:False)";
            });

            this.dataSource = model.mvs;
            this.unfilteredList = model.mvs;
            this.showDateAsAges = model.showDateAsAges;

            this.initShowOptions("status", this.showStatus);
            this.initShowOptions("cycle", this.showCycle);
            this.initShowOptions("users", this.showUser);

            this.setCycle = model.dataCycles;
            this.sortItems(this.setCycle, this.cycleNameSort, null, "label");

            if(init) {
               this.runInBackground = model.runInBackground;
            }

            this.filterList();
         });
   }

   filterHeaders(sel: string[]): void {
      let newHeaderCols;

      if(this.deviceType === DeviceType.SMALL) {
         this.mvSmallDeviceExpandingColumns = {};
         newHeaderCols = this.mvSmallDeviceExpandingColumns;
      }
      else if(this.deviceType === DeviceType.MEDIUM) {
         this.mvMediumDeviceExpandingColumns = {};
         newHeaderCols = this.mvMediumDeviceExpandingColumns;
      }
      else {
         this.mvLargeDeviceExpandingColumns = {};
         newHeaderCols = this.mvLargeDeviceExpandingColumns;
      }

      for(let key in this.headerCols) {
         if(sel.indexOf(key) >= 0) {
            newHeaderCols[key] = this.headerCols[key];
         }
      }

      this.columnInfo = this.generateColumns(newHeaderCols);
      this.refreshTableInfo();
   }

   deleteSelected(): void {
      let materializedSelection: MaterializedModel[] = this.selection.selected.map(sel => <MaterializedModel>sel);
      let removeModel: MVManagementModel = <MVManagementModel>{
         mvs: materializedSelection
      };

      this.loading = true;

      this.http.post("../api/em/content/materialized-view/remove", removeModel).subscribe({
         complete: () => {
            this.refreshMVList(true);
            this.selection.clear();
            this.loading = false;
         }
      });
   }

   validate(): void {
      // analyze
      let materializedSelection: MaterializedModel[] = this.selection.selected.map(sel => <MaterializedModel>sel);
      let analyzeModel: MVManagementModel = <MVManagementModel>{
         mvs: materializedSelection
      };

      this.loading = true;

      this.http.post("../api/em/content/materialized-view/analysis", analyzeModel).subscribe(
         (response: AnalyzeMVResponse) => {
            if(response.exception) {
               let config = new MatSnackBarConfig();
               config.duration = Tool.SNACKBAR_DURATION;
               this.loading = false;

               this.dialog.open(MessageDialog, {
                  data: {
                     title: "_#(js:Status)",
                     content: "_#(js:Error)",
                     type: MessageDialogType.ERROR
                  }
               });
            }
            else {
               this.analysisCompleted();
            }
         }, () => this.loading = false);
   }

   private generateColumns(headerCols: any): ColumnInfo[] {
      return this.headers
         .filter(header => headerCols.hasOwnProperty(header))
         .map((val: string) => <ColumnInfo>{
            header: val,
            field: headerCols[val]
         });
   }

   private analysisCompleted(): void {
      this.http.get("../api/em/content/materialized-view/check-analysis").subscribe((response: AnalyzeMVResponse) => {
         if(!response.completed) {
            setTimeout(() => this.analysisCompleted(), 5000);
            return;
         }

         let config = new MatSnackBarConfig();
         config.duration = Tool.SNACKBAR_DURATION;
         this.loading = false;

         if(response.exception) {
            this.http.get("../api/em/content/repository/mv/exceptions").subscribe(
               (exceptions: MVExceptionResponse) =>
               {
                  // open the exception dialog
                  this.dialog.open(MvExceptionsDialogComponent, <MatDialogConfig>{
                     data: {
                        exceptions: exceptions.exceptions
                     },
                     maxWidth: "40vw",
                     maxHeight: "75vh",
                     disableClose: true
                  });
               });
         }
         else {
            this.dialog.open(MessageDialog, {
               data: {
                  title: "_#(js:Status)",
                  content: "_#(js:Success)!",
                  type: MessageDialogType.INFO
               }
            });
         }

         this.refreshMVList();
      });
   }

   setShowDate(): void {
      let materializedSelection: MaterializedModel[] = this.selection.selected.map(sel => <MaterializedModel>sel);
      let setModel: MVManagementModel = <MVManagementModel>{
         mvs: materializedSelection,
         showDateAsAges: this.showDateAsAges
      };

      this.http.post("../api/em/content/materialized-view/date-as-ages", setModel)
         .subscribe(() => this.refreshMVList());
   }

   setDataCycle(value: string): void {
      let materializedSelection: MaterializedModel[] = this.selection.selected.map(sel => <MaterializedModel>sel);
      let setModel: MVManagementModel = <MVManagementModel>{
         mvs: materializedSelection,
         cycle: value
      };

      this.http.post("../api/em/content/materialized-view/data-cycle", setModel).subscribe(() => {
         let selectedNames: string[] = this.selection.selected.map(sel => (<MaterializedModel>sel).name);
         this.dataSource.forEach(data => {
            if(selectedNames.includes(data.name)) {
               data.cycle = value;
            }
         });
      });
   }

   apply(): void {
      // apply
      let materializedSelection: MaterializedModel[] = this.selection.selected.map(sel => <MaterializedModel>sel);
      let applyModel: MVManagementModel = <MVManagementModel>{
         mvs: materializedSelection,
         runInBackground: this.runInBackground
      };

      this.loading = true;

      this.http.post("../api/em/content/materialized-view/update", applyModel)
         .subscribe((status: ConnectionStatus) => {
            let config = new MatSnackBarConfig();
            config.duration = Tool.SNACKBAR_DURATION;
            config.panelClass = ["max-width"];

            this.refreshMVList();
            this.loading = false;
            this.dialog.open(MessageDialog, {
               data: {
                  title: "_#(js:Status)",
                  content: status.status,
                  type: MessageDialogType.INFO
               }
            });
         }, () => this.loading = false);
   }

   filterList(): void {
      let filteredList = this.unfilteredList.slice();

      // check status, cycle, and user
      if(this.selectedStatus != "_#(js:All)") {
         filteredList = filteredList.filter(mv => mv.status === this.selectedStatus);
      }

      if(this.selectedCycle != "_#(js:All)") {
         filteredList = filteredList
            .filter(mv => mv.cycle === (this.selectedCycle === "_#(js:None)" ? "" : this.selectedCycle));
      }

      if(this.selectedUser != "_#(js:All)") {
         filteredList = filteredList.filter(mv => mv.users === this.selectedUser);
      }

      this.dataSource = filteredList;
   }

   initShowOptions(type: string, list: string[]): void {
      let types = this.dataSource.map(mv => mv[type]);

      types.forEach(t => {
         let val = t;

         if(type === "cycle") {
            val = t === "" ? "_#(js:None)" : t;
         }

         if(!list.includes(val)) {
            list.push(val);
         }
      });

      if(type === "cycle" && list != null) {
         this.sortItems(list, SortTypes.ASCENDING, "_#(js:All)");
      }
   }

   private sortItems(list: any[], sort: SortTypes, fistValue?: string,
                     sortProperty?: string): void
   {
      if(!list) {
         return;
      }

      list.sort((a: any, b: any): number => {
         if(!!sortProperty) {
            a = !!a ? a[sortProperty] : a;
            b = !!b ? b[sortProperty] : b;
         }

         if(fistValue && (a === fistValue || b === fistValue)) {
            if(a === fistValue && !!b) {
               return -1;
            }
            else if(b === fistValue && !!a) {
               return 1;
            }

            return 0;
         }

         a = !a ? a : a.toLowerCase();
         b = !b ? b : b.toLowerCase();

         return (sort == SortTypes.ASCENDING ? 1 : -1) * (a === b ? 0 : a > b ? 1 : -1);
      });
   }

   deviceTypeChanged(deviceType: DeviceType): void {
      this.deviceType = deviceType;

      if(this.deviceType === DeviceType.SMALL) {
         this.selectedHeaders = Object.keys(this.mvSmallDeviceExpandingColumns);
      }
      else if(this.deviceType === DeviceType.MEDIUM) {
         this.selectedHeaders = Object.keys(this.mvMediumDeviceExpandingColumns);
      }
      else {
         this.selectedHeaders = Object.keys(this.mvLargeDeviceExpandingColumns);
      }
   }

   updateCycleSort() {
      if(this.cycleNameSort === SortTypes.ASCENDING) {
         this.cycleNameSort = SortTypes.DESCENDING;
      }
      else {
         this.cycleNameSort = SortTypes.ASCENDING;
      }

      this.sortItems(this.showCycle, this.cycleNameSort, "_#(js:All)");
   }

   updateSetCycleSort() {
      if(this.setCycleNameSort === SortTypes.ASCENDING) {
         this.setCycleNameSort = SortTypes.DESCENDING;
      }
      else {
         this.setCycleNameSort = SortTypes.ASCENDING;
      }

      this.sortItems(this.setCycle, this.setCycleNameSort, null, "label");
   }
}
