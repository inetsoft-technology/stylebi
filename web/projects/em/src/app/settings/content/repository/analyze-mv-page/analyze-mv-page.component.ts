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
import { HttpClient, HttpParams } from "@angular/common/http";
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from "@angular/core";
import { MatDialog, MatDialogConfig } from "@angular/material/dialog";
import { Subject, timer } from "rxjs";
import { takeUntil } from "rxjs/operators";
import { RepositoryEntryType } from "../../../../../../../shared/data/repository-entry-type.enum";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import { AnalyzeMVResponse } from "../../../../../../../shared/util/model/mv/analyze-mv-response";
import { MaterializedModel } from "../../../../../../../shared/util/model/mv/materialized-model";
import { MVExceptionResponse } from "../../../../../../../shared/util/model/mv/mv-exception-response";
import { NameLabelTuple } from "../../../../../../../shared/util/name-label-tuple";
import { ErrorHandlerService } from "../../../../common/util/error/error-handler.service";
import { ExpandableRowTableInfo } from "../../../../common/util/table/expandable-row-table/expandable-row-table-info";
import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";
import { ColumnInfo } from "../../../../common/util/table/column-info";
import { SecurityEnabledEvent } from "../../../security/security-settings-page/security-enabled-event";
import { MVManagementModel } from "../../materialized-views/mv-management-view/mv-management-model";
import { MvExceptionsDialogComponent } from "../mv-exceptions-dialog/mv-exceptions-dialog.component";
import { RepositoryTreeNode } from "../repository-tree-node";

const MATERIALIZATION_NO_MVS_MESSAGE = "_#(js:viewer.viewsheet.materialization.noMVs)";
const MATERIALIZATION_NO_ASSETS_MESSAGE = "_#(js:viewer.viewsheet.materialization.noAssets)";

@Component({
   selector: "em-analyze-mv-page",
   templateUrl: "./analyze-mv-page.component.html",
   styleUrls: ["./analyze-mv-page.component.scss"]
})
export class AnalyzeMvPageComponent implements OnInit, OnDestroy {
   @Input() nodesToAnalyze: RepositoryTreeNode[];
   @Output() mvChanged = new EventEmitter<void>();
   loading = false;
   analyzed = false;
   models: MaterializedModel[] = [];
   mvTableInfo: ExpandableRowTableInfo;
   mvTableColumns: ColumnInfo[] = [
      {field: "sheets", header: "_#(js:Asset)"},
      {field: "table", header: "_#(js:Table)"},
      {field: "users", header: "_#(js:Users)"},
      {field: "existString", header: "_#(js:Exists)"},
      {field: "dataString", header: "_#(js:Has Data)"},
      {field: "cycle", header: "_#(js:Cycle)"},
      {field: "lastModifiedTime", header: "_#(js:Last Modified Time)"}
   ];
   mvTableMediumDeviceHeaders: ColumnInfo[] = [
      {field: "sheets", header: "_#(js:Asset)"},
      {field: "table", header: "_#(js:Table)"},
   ];
   existingMVTableInfo: ExpandableRowTableInfo = {
      selectionEnabled: true,
      title: "_#(js:Existing MVs)",
      columns: this.mvTableColumns,
      mediumDeviceHeaders: this.mvTableMediumDeviceHeaders,
      actions: []
   };
   selection: MaterializedModel[] = [];
   fullData = true;
   bypass = false;
   groupExpanded = false;
   cycles: NameLabelTuple[] = [];
   securityEnabled = false;
   enterprise: boolean;

   get hideData(): boolean {
      return this._hideData;
   }

   set hideData(value: boolean) {
      this._hideData = value;

      if(value) {
         this._hideExist = false;
      }

      this.refreshAnalyzedResult();
   }

   get hideExist(): boolean {
      return this._hideExist;
   }

   set hideExist(value: boolean) {
      this._hideExist = value;

      if(value) {
         this._hideData = false;
      }

      this.refreshAnalyzedResult();
   }


   get runInBackground(): boolean {
      return this._runInBackground;
   }

   set runInBackground(value: boolean) {
      this._runInBackground = value;

      if(value) {
         this.generateData = true;
      }
   }

   get generateData(): boolean {
      return this._generateData;
   }

   set generateData(_generateData: boolean) {
      this._generateData = _generateData;

      if(!_generateData) {
         this.runInBackground = false;
      }
   }

   get mvCycle(): string {
      return this._mvCycle;
   }

   set mvCycle(value: string) {
      this._mvCycle = value;
      const request = {
         mvNames: this.selection.map(m => m.name),
         cycle: this.mvCycle
      };
      this.http.post("../api/em/content/repository/mv/set-cycle", request)
         .subscribe(() => this.refreshAnalyzedResult());
   }

   get fullDataVisible(): boolean {
      return this.nodesToAnalyze != null && this.nodesToAnalyze.length > 0 &&
         this.nodesToAnalyze.some(node => node.type === RepositoryEntryType.VIEWSHEET);
   }

   get groupExpandedVisible(): boolean {
      return this.nodesToAnalyze != null && this.nodesToAnalyze.length > 0 &&
         this.nodesToAnalyze.some(node => !node.owner);
   }

   get showPlanDisabled(): boolean {
      // there may be information (faults) when no MV is generated (48224).
      // return this.itemsSelected;
      return false;
   }

   get showCreateUpdateDisabled(): boolean {
      return this.itemsSelected;
   }

   get itemsSelected(): boolean {
      return !this.selection || !this.selection.length;
   }

   get noMvsMessage() {
      return MATERIALIZATION_NO_MVS_MESSAGE;
   }

   get noAssetsMessage() {
      return MATERIALIZATION_NO_ASSETS_MESSAGE;
   }

   private destroy$ = new Subject<void>();
   private _hideData = false;
   private _hideExist = false;
   private _mvCycle = "";
   private _runInBackground = false;
   private _generateData = true;

   constructor(private http: HttpClient, private dialog: MatDialog,
               private appInfoService: AppInfoService,
               private errorService: ErrorHandlerService)
   {
      this.http.get("../api/em/security/get-enable-security")
         .subscribe((event: SecurityEnabledEvent) => this.securityEnabled = event.enable);

      appInfoService.isEnterprise().subscribe(info => this.enterprise = info);
   }

   ngOnInit() {
      const uri = "../api/em/content/materialized-view/info";
      const data = { nodes: this.nodesToAnalyze };
      this.http.post<MVManagementModel>(uri, data)
         .subscribe(model => {
            this.models = this.localizeModel(model.mvs);
         });
   }

   ngOnDestroy() {
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   analyzeMV() {
      this.loading = true;

      let onlyViewsheetsAndWorksheets = this.nodesToAnalyze
         .every(node => node.type === RepositoryEntryType.VIEWSHEET ||
            node.type === RepositoryEntryType.WORKSHEET);

      if(onlyViewsheetsAndWorksheets) {
         const request = {
            expanded: this.groupExpanded,
            full: this.fullData,
            bypass: this.bypass,
            nodes: this.nodesToAnalyze
         };

         this.http.post("../api/em/content/repository/mv/analyze", request).subscribe(
            () => {
            },
            (error) => {
               this.errorService.showSnackBar(error);
               this.loading = false;
            },
            () => {
               this.checkCompleted();
            });
      }
      else {
         this.errorService.showSnackBar(null, "_#(js:em.mv.folderFound)");
         this.loading = false;
      }
   }

   checkCompleted(retryDelayMillis: number = 500) {
      this.http.get("../api/em/content/materialized-view/check-analysis")
         .subscribe((response: AnalyzeMVResponse) => {
            this.processAnalyzeResult(response, retryDelayMillis);
         });
   }

   processAnalyzeResult(response: AnalyzeMVResponse, retryDelayMillis: number) {
      //check status later if not completed.
      if(!response.completed) {
         timer(retryDelayMillis)
            .pipe(takeUntil(this.destroy$))
            .subscribe(() => this.checkCompleted(Math.min(5000, retryDelayMillis * 1.5)));
         return;
      }

      this.loading = false;

      if(response.exception) {
         this.http.get("../api/em/content/repository/mv/exceptions").subscribe((exceptions: MVExceptionResponse) => {
            //navigate to exception page
            const ref = this.dialog.open(MvExceptionsDialogComponent, <MatDialogConfig>{
               data: {
                  exceptions: exceptions.exceptions
               },
               maxWidth: "40vw",
               maxHeight: "75vh",
               disableClose: true
            });

            ref.afterClosed().subscribe((proceed: boolean) => {
               if(proceed) {
                  this.showCreateMVPage(response);
               }
            });
         });
      }
      else {
         this.showCreateMVPage(response);
      }
   }

   showCreateMVPage(response: AnalyzeMVResponse) {
      this.models = response.status;
      this.mvTableInfo = {
         selectionEnabled: true,
         title: "_#(js:em.mv.createTitle)",
         columns: this.mvTableColumns,
         mediumDeviceHeaders: this.mvTableMediumDeviceHeaders,
         actions: []
      };
      this.cycles = response.cycles;
      this.mvCycle = response.onDemand ? response.defaultCycle : "";
      this.runInBackground = response.runInBackground;
      this.analyzed = true;
   }

   private refreshAnalyzedResult() {
      let params = new HttpParams()
         .set("hideData", String(this.hideData))
         .set("hideExist", String(this.hideExist));
      this.http.get("../api/em/content/repository/mv/get-model", {params})
         .subscribe((response: AnalyzeMVResponse) => {
            this.models = this.localizeModel(response.status);
         });
   }

   selectionChanged(tableModels: MaterializedModel[]) {
      this.selection = tableModels;
   }


   deleteSelected() {
      let materializedSelection: MaterializedModel[] = this.selection;
      let removeModel: MVManagementModel = <MVManagementModel>{
         mvs: materializedSelection
      };

      this.loading = true;

      this.http.post("../api/em/content/materialized-view/remove", removeModel).subscribe(() => {
         this.models = this.models.filter(data =>
            !materializedSelection.map(mv => mv.name).includes(data.name));
         this.loading = false;
         this.mvChanged.emit();
      });
   }

   clearAnalysis() {
      this.analyzed = false;
      this.models = this.models.filter(mv => mv.exists === true);
   }

   create() {
      this.loading = true;
      const request = {
         mvNames: this.selection.map(m => m.name),
         noData: !this.generateData,
         runInBackground: this.runInBackground,
         cycle: this.mvCycle
      };

      this.http.post("../api/em/content/repository/mv/create", request).subscribe(() => {
         this.refreshAnalyzedResult();
         const dialogRef = this.dialog.open(MessageDialog, <MatDialogConfig>{
            data: {
               content: "_#(js:em.alert.createMV)",
               type: MessageDialogType.INFO
            }
         });
         dialogRef.afterClosed().subscribe(() => {
            this.loading = false;
            this.mvChanged.emit();
         });
      },
         (error) => {
         this.errorService.showDialog(error);
         this.loading = false;
      });
   }

   showPlan() {
      const request = {
         mvNames: this.selection.map(m => m.name)
      };

      this.http.post("../api/em/content/repository/mv/show-plan", request)
         .subscribe((plan: string) => {
            this.dialog.open(MessageDialog, <MatDialogConfig>{
               data: {
                  title: "_#(js:Optimize Plan)",
                  content: plan,
                  type: MessageDialogType.WARNING
               }
            });
         });
   }

   private localizeModel(models: MaterializedModel[]): MaterializedModel[] {
      return models.map(mv => {
         return {
            ...mv,
            dataString: mv.hasData ? "_#(js:Yes)" : "_#(js:No)",
            incrementalString: mv.incremental ? "_#(js:Yes)" : "_#(js:No)",
            validString: mv.valid ? "_#(js:True)" : "_#(js:False)",
            existString: mv.exists ? "_#(js:True)" : "_#(js:False)"
         };
      });
   }
}
