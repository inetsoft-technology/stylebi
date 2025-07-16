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
import { HttpClient, HttpParams } from "@angular/common/http";
import {
   Component,
   EventEmitter,
   Input,
   OnDestroy,
   OnInit,
   Output,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Subject, Subscription, timer } from "rxjs";
import { filter, switchMap, take, takeUntil, timeout } from "rxjs/operators";
import { RepositoryEntryType } from "../../../../../../shared/data/repository-entry-type.enum";
import { MVManagementModel } from "../../../../../../em/src/app/settings/content/materialized-views/mv-management-view/mv-management-model";
import { SecurityEnabledEvent } from "../../../../../../em/src/app/settings/security/security-settings-page/security-enabled-event";
import { DateTypeFormatter } from "../../../../../../shared/util/date-type-formatter";
import { AnalyzeMVResponse } from "../../../../../../shared/util/model/mv/analyze-mv-response";
import { CreateMvResponse } from "../../../../../../shared/util/model/mv/create-mv-response";
import { CreateUpdateMvRequest } from "../../../../../../shared/util/model/mv/create-update-mv-request";
import { MaterializedModel } from "../../../../../../shared/util/model/mv/materialized-model";
import { MVExceptionResponse } from "../../../../../../shared/util/model/mv/mv-exception-response";
import { NameLabelTuple } from "../../../../../../shared/util/name-label-tuple";
import { Tool } from "../../../../../../shared/util/tool";
import { ComponentTool } from "../../../common/util/component-tool";
import { AnalyzeMVPortalRequest } from "../../../vsobjects/model/analyze-mv-portal-request";
import { RepositoryTreeService } from "../../../widget/repository-tree/repository-tree.service";
import { AnalyzeMVModel } from "../../data/model/analyze-mv-model";
import { MVTreeModel } from "../../data/model/mv-tree-model";
import { AnalyzeMVPane } from "./analyze-mv-view/analyze-mv-pane.component";
import { CreateMVPane } from "./create-mv-view/create-mv-pane.component";
import { MVExceptionsPortalDialogComponent } from "./mv-exception-portal-dialog/mv-exceptions-portal-dialog.component";

@Component({
   selector: "analyze-mv-dialog",
   templateUrl: "./analyze-mv-dialog.component.html",
   styleUrls: ["./analyze-mv-dialog.component.scss"]
})
export class AnalyzeMVDialog implements OnInit, OnDestroy {
   @Input() selectedNodes: MVTreeModel[] = [];
   @Output() onCommit: EventEmitter<string> = new EventEmitter<string>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild(CreateMVPane) createMVPane: CreateMVPane;
   @ViewChild(AnalyzeMVPane) analyzeMVPane: AnalyzeMVPane;
   analyzed = false;
   loading = false;
   canCreateOrUpdate: boolean = true;
   models: MaterializedModel[] = null;
   existingModels: MaterializedModel[] = null;
   analyzeMVModel: AnalyzeMVModel = null;
   hideData = false;
   hideExist = false;
   cycles: NameLabelTuple[] = [];
   mvCycle = "";
   securityEnabled = false;
   runInBackground: boolean = false;
   private subscription = new Subscription();
   private destroy$ = new Subject<void>();

   constructor(private repositoryTreeService: RepositoryTreeService,
               private http: HttpClient,
               private modalService: NgbModal)
   {
      this.subscription.add(this.http.get("../api/em/security/get-enable-security")
         .subscribe((event: SecurityEnabledEvent) => this.securityEnabled = event.enable));
      this.analyzeMVModel = <AnalyzeMVModel> {
         fullData: true,
         bypass: false,
         groupExpanded: false,
         applyParentVsParameters: false
      };
   }

   ngOnInit() {
      this.refreshModels();
   }

   ngOnDestroy() {
      this.subscription.unsubscribe();
      this.destroy$.next();
      this.destroy$.unsubscribe();
   }

   refreshModels() {
      const request = <AnalyzeMVPortalRequest>{
         expanded: this.analyzeMVModel.groupExpanded,
         full: this.analyzeMVModel.fullData,
         bypass: this.analyzeMVModel.bypass,
         applyParentVsParameters: this.analyzeMVModel.applyParentVsParameters,
         nodes: this.selectedNodes
      };

      this.http.post("../api/portal/content/materialized-view/info", request)
         .subscribe((mvManagementModel: MVManagementModel) => {
            mvManagementModel.mvs.map(mv => {
               mv.dataString = mv.hasData ? "_#(js:Yes)" : "_#(js:No)";
               mv.existString = mv.exists ? "_#(js:True)" : "_#(js:False)";

               if(mv.lastModifiedTimestamp != 0) {
                  mv.lastModifiedTime = DateTypeFormatter.getLocalTime(mv.lastModifiedTimestamp,
                     mvManagementModel.dateFormat);
               }
            });

            this.existingModels = mvManagementModel.mvs;
         });
   }

   analyzeMV() {
      const request = <AnalyzeMVPortalRequest>{
         expanded: this.analyzeMVModel.groupExpanded,
         full: this.analyzeMVModel.fullData,
         bypass: this.analyzeMVModel.bypass,
         applyParentVsParameters: this.analyzeMVModel.applyParentVsParameters,
         nodes: this.selectedNodes
      };
      this.loading = true;

      let onlyViewsheetsAndWorksheets: boolean = request.nodes.reduce((previous: boolean, current: MVTreeModel) => {
         return previous && (current.type == RepositoryEntryType.VIEWSHEET ||
            current.type == RepositoryEntryType.WORKSHEET);
      }, true);

      if(onlyViewsheetsAndWorksheets) {
         this.http.post("../api/portal/content/repository/mv/analyze", request).subscribe(
            () => {
            },
            (error) => {
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                  error.error.message);
               this.loading = false;
            },
            () => {
               this.checkCompleted();
            });
      }
      else {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:em.mv.folderFound)");
         this.loading = false;
      }
   }

   deleteMV() {
      let materializedSelection: MaterializedModel[] = this.analyzeMVPane.selectedMVs.map(
         sel => <MaterializedModel> sel);
      let removeModel: MVManagementModel = <MVManagementModel>{
         mvs: materializedSelection
      };
      this.loading = true;
      setTimeout(() => this.loading = false, 30000);
      this.http.post("../api/em/content/materialized-view/remove", removeModel).subscribe(() => {
         this.refreshModels();
         this.loading = false;
      });
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
         this.http.get("../api/em/content/repository/mv/exceptions").subscribe(
            (exceptions: MVExceptionResponse) =>
            {
               //navigate to exception page
               let dialog: MVExceptionsPortalDialogComponent = ComponentTool.showDialog(
                  this.modalService, MVExceptionsPortalDialogComponent,
                  () => this.showCreateMVPage(response), {backdrop: "static"});
               dialog.exceptions = exceptions.exceptions;
         });
      }
      else {
         this.showCreateMVPage(response);
      }
   }

   showCreateMVPage(response: AnalyzeMVResponse) {
      response.status.map(mv => {
         if(mv.lastModifiedTimestamp != 0) {
            mv.lastModifiedTime = DateTypeFormatter.getLocalTime(mv.lastModifiedTimestamp,
               response.dateFormat);
         }
      });

      this.models = response.status;
      this.cycles = response.cycles;
      this.mvCycle = response.onDemand ? response.defaultCycle : "";
      this.runInBackground = response.runInBackground;
      this.analyzed = true;
   }

   refresh(hide: string) {
      switch(hide) {
      case "DATA":
         this.hideExist = this.hideData ? false : this.hideExist;
         break;
      case "EXIST":
         this.hideData = this.hideExist ? false : this.hideData;
         break;
      default:
         break;
      }

      let params = new HttpParams().set("hideData", String(this.hideData))
         .set("hideExist", String(this.hideExist));
      this.http.get("../api/em/content/repository/mv/get-model", {params: params})
         .subscribe((response: AnalyzeMVResponse) => {
            this.models = response.status;
         });
      this.refreshModels();
   }

   setCycle(request: CreateUpdateMvRequest) {
      this.http.post("../api/em/content/repository/mv/set-cycle", request)
         .subscribe(() => this.refresh(""));
   }

   create(request: CreateUpdateMvRequest) {
      this.loading = true;
      const createId = Tool.generateRandomUUID();
      const options = { params: new HttpParams().set("createId", createId) };

      timer(0, 2000)
         .pipe(
            switchMap(() =>
               this.http.post<CreateMvResponse>("../api/em/content/repository/mv/create", request, options)),
            filter(response => response.complete),
            take(1),
            timeout(600000)
         )
         .subscribe(() => {
               this.refresh("ALL");
               const dialog = ComponentTool.showMessageDialog(this.modalService, "_#(js:Info)",
                  "_#(js:em.alert.createMV)");
               dialog.then(() => {
                  this.loading = false;
                  this.okClicked();
               });
            },
            (error) => {
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                  error.error.message);
               this.loading = false;
            });
   }

   showPlan(request: CreateUpdateMvRequest) {
      this.http.post("../api/em/content/repository/mv/show-plan", request)
         .subscribe((plan: string) => {
            const dialog = ComponentTool.showMessageDialog(this.modalService, "_#(js:Optimize Plan)",
               plan);
         });
   }

   createOrUpdate() {
      if(this.createMVPane) {
         this.createMVPane.createOrUpdate();
      }
   }

   selectedMVsChanged(mvs: string[]) {
      this.canCreateOrUpdate = mvs && mvs.length > 0;
   }

   showPlanClicked() {
      if(this.createMVPane) {
         this.createMVPane.showPlanClicked();
      }
   }

   okClicked(): void {
      this.onCommit.emit("ok");
   }

   closeDialog(): void {
      this.onCancel.emit("cancel");
   }
}
