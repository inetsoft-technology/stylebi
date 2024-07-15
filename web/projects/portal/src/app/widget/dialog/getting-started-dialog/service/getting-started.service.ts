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
import { Injectable } from "@angular/core";
import { ActivatedRoute, ActivatedRouteSnapshot, Params, Router } from "@angular/router";
import { ComponentTool } from "../../../../common/util/component-tool";
import { GettingStartedDialog } from "../getting-started-dialog.component";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { WhetherStayComposerDialog } from "../whether-stay-composer-dialog.component";
import { WSObjectType } from "../../../../composer/dialog/ws/new-worksheet-dialog.component";
import { AssetConstants } from "../../../../common/data/asset-constants";
import { Tool } from "../../../../../../../shared/util/tool";
import { PortalDataType } from "../../../../portal/data/data-navigation-tree/portal-data-type";
import { AssetEntryHelper } from "../../../../common/data/asset-entry-helper";
import { Observable, Subject } from "rxjs";
import { HttpClient } from "@angular/common/http";
import { StringWrapper } from "../../../../portal/data/model/datasources/database/string-wrapper";
import { GettingStartedAssetDefaultFolder } from "../getting-started-asset-default-folder";
import { LocalStorage } from "../../../../common/util/local-storage.util";

const GET_CREATE_QUERY_DEFAULT_FOLDER_URI = "../api/portal/getting-started/query/defaultFolder";
const GET_CREATE_WS_DEFAULT_FOLDER_URI = "../api/portal/getting-started/ws/defaultFolder";
const GET_CREATE_VS_DEFAULT_FOLDER_URI = "../api/portal/getting-started/vs/defaultFolder";

export enum GettingStartedStep {
   CONNECT_TO,
   UPLOAD,
   CREATE_QUERY,
   START_FROM_SCRATCH,
   CUSTOMIZE_DATA,
   CREATE_DASHBOARD
}

export enum StepIndex {
   CONNECT_TO_OR_UPLOAD,
   CREATE_QUERY_EMPTY_WS,
   CREATE_DASHBOARD
}

export interface GettingStartedEditSheetEvent {
   op: GettingStartedStep;
   baseDataSourceType?: WSObjectType;
   baseDataSource?: string;
   sheetId?: string;
   newSheet?: boolean;
   baseWs?: string;
   folder?: string;
}

@Injectable({
   providedIn: "root"
})
export class GettingStartedService {
   private stepIndex: number = 0;
   private datasource: string;
   private _datasourceType: string;
   private worksheetId: string;
   private processing: boolean = false;
   private gettingStartedDialog: GettingStartedDialog;
   private currentStep: GettingStartedStep;
   private showWsTip: boolean = false;
   private editSheetSubject: Subject<GettingStartedEditSheetEvent> = new Subject<GettingStartedEditSheetEvent>();
   private _finished: boolean;

   get editSheet(): Observable<GettingStartedEditSheetEvent> {
      return this.editSheetSubject.asObservable();
   }

   get showGettingStartedMessage(): boolean {
      return this.isCustomizeData() && this.showWsTip;
   }

   set showGettingStartedMessage(show: boolean) {
      this.showWsTip = show;
   }

   get finished(): boolean {
      return this._finished;
   }

   get datasourceType(): string {
      return this._datasourceType;
   }

   constructor(private router: Router, private route: ActivatedRoute, private modalService: NgbModal,
      private http: HttpClient) {
   }

   start(force: boolean): void {
      if(!force && LocalStorage.getItem("started.noshow") == "true") {
         return;
      }

      this.processing = true;
      this.showGettingStarted();
   }

   finish(): void {
      this.closeGettingStarted();
      this.processing = false;
      this._finished = true;
   }

   isProcessing(): boolean {
      return this.processing;
   }

   isConnectTo(): boolean {
      return this.isProcessing() && this.currentStep == GettingStartedStep.CONNECT_TO;
   }

   isEditWs(): boolean {
      return this.isUploadFile() || this.isCreateQuery() || this.isStartFromScratch() || this.isCustomizeData();
   }

   isUploadFile(): boolean {
      return this.isProcessing() && this.currentStep == GettingStartedStep.UPLOAD;
   }

   isCreateQuery(): boolean {
      return this.isProcessing() && this.currentStep == GettingStartedStep.CREATE_QUERY;
   }

   isStartFromScratch(): boolean {
      return this.isProcessing() && this.currentStep == GettingStartedStep.START_FROM_SCRATCH;
   }

   isCustomizeData(): boolean {
      return this.isProcessing() && this.currentStep == GettingStartedStep.CUSTOMIZE_DATA;
   }

   isCreateDashboard(): boolean {
      return this.isProcessing() && this.currentStep == GettingStartedStep.CREATE_DASHBOARD;
   }

   getCurrentStep(): number {
      return this.stepIndex;
   }

   setDataSourcePath(source: string, type?: string): void {
      this.datasource = source;
      this._datasourceType = type;
   }

   getDataSourcePath(): string {
      return this.datasource;
   }

   setWorksheetId(wsId: string): void {
      this.worksheetId = wsId;
   }

   getWorksheetId(): string {
      return this.worksheetId;
   }

   toConnectDataSource(): void {
      this.currentStep = GettingStartedStep.CONNECT_TO;

      if(!!this.datasource) {
         const queryParams: any = {
            scope: AssetConstants.QUERY_SCOPE,
            gettingStartedRouteTime: new Date().getTime()
         };

         let uri;

         if(this.datasourceType === PortalDataType.DATABASE) {
            uri = "portal/tab/data/datasources/database";
         }
         else if(this.datasourceType === PortalDataType.XMLA_SOURCE) {
            uri = "portal/tab/data/datasources/datasource/xmla/edit/";
         }
         else {
            uri = "portal/tab/data/datasources/datasource";
         }

         this.router.navigate([uri, Tool.byteEncode(this.datasource)],
            { queryParams }).then(result => {
               if(!result) {
                  ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                     "_#(js:getting.started.new.asset.unauthorized)" + "_*" + "_#(js:Data Source)")
                     .then(() => {
                        this.continue();
                     });
               }
            }
            );
      }
      else {
         let queryParams = {
            scope: AssetConstants.QUERY_SCOPE,
            gettingStartedRouteTime: new Date().getTime()
         };

         this.router.navigate(["portal/tab/data/datasources/listing"],
            { queryParams: queryParams }).then(result => {
               if(!result) {
                  ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                     "_#(js:getting.started.new.asset.unauthorized)" + "_*" + "_#(js:Data Source)")
                     .then(() => {
                        this.continue();
                     });
               }
            }
            );
      }
   }

   toUpLoadFile(): void {
      this.http.get<GettingStartedAssetDefaultFolder>(GET_CREATE_WS_DEFAULT_FOLDER_URI).subscribe(result => {
         if(result.errorMessage) {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", result.errorMessage)
               .then(() => {
                  this.continue();
               });

            return;
         }

         this.currentStep = GettingStartedStep.UPLOAD;
         let composer = this.currentIsComposer();

         if(!!this.worksheetId) {
            if(composer) {
               this.openWs(GettingStartedStep.UPLOAD);
            }
            else {
               let params = {
                  wsWizard: true,
                  scope: AssetEntryHelper.QUERY_SCOPE
               };

               this.routeToOpenWsBy(this.worksheetId, params);
            }
         }
         else {
            if(composer) {
               this.editSheetSubject.next({
                  op: this.currentStep,
                  newSheet: true,
                  folder: result.folderId
               });
            }
            else {
               let queryParams = {
                  wsWizard: true,
                  scope: AssetEntryHelper.QUERY_SCOPE,
                  folder: result.folderId,
                  closeOnComplete: true
               };

               this.router.navigate(["composer"], { queryParams: queryParams });
            }
         }
      });
   }

   openVsOnPortal(id: string): void {
      this.router.navigate(["portal/tab/report/vs/view", id]);
   }

   private routeToOpenWsBy(wsId: string, params?: Params): void {
      if(params) {
         params.wsId = decodeURIComponent(wsId);
      }
      else {
         params = {
            wsId: decodeURIComponent(wsId)
         };
      }

      this.router.navigate(["composer"], { queryParams: params });
   }

   private openWs(op: GettingStartedStep): void {
      let queryType = this.datasourceType == PortalDataType.DATABASE ?
         WSObjectType.DATABASE_QUERY : WSObjectType.TABULAR;

      this.editSheetSubject.next({
         op: op,
         sheetId: this.worksheetId,
         baseDataSourceType: queryType,
         baseDataSource: this.datasource
      });
   }

   toCreateQuery(): void {
      this.http.get<GettingStartedAssetDefaultFolder>(GET_CREATE_QUERY_DEFAULT_FOLDER_URI).subscribe(result => {
         if(result.errorMessage) {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", result.errorMessage)
               .then(() => {
                  this.continue();
               });

            return;
         }

         this.currentStep = GettingStartedStep.CREATE_QUERY;
         let queryType = this.datasourceType == PortalDataType.DATABASE ?
            WSObjectType.DATABASE_QUERY : WSObjectType.TABULAR;
         let composer = this.currentIsComposer();

         if(this.worksheetId) {
            if(composer) {
               this.openWs(GettingStartedStep.CREATE_QUERY);
            }
            else {
               let params = {
                  wsWizard: true,
                  scope: AssetEntryHelper.QUERY_SCOPE,
                  baseDataSourceType: queryType,
                  baseDataSource: this.datasource
               };

               this.routeToOpenWsBy(this.worksheetId, params);
            }
         }
         else {
            if(composer) {
               this.editSheetSubject.next({
                  op: this.currentStep,
                  baseDataSource: this.datasource,
                  baseDataSourceType: queryType,
                  newSheet: true,
                  folder: result.folderId
               });
            }
            else {
               let queryParams = {
                  wsWizard: true,
                  scope: AssetEntryHelper.QUERY_SCOPE,
                  baseDataSourceType: queryType,
                  baseDataSource: this.datasource,
                  folder: result.folderId,
                  closeOnComplete: true
               };

               this.router.navigate(["composer"], { queryParams: queryParams });
            }
         }
      });
   }

   toCreateWs(): void {
      this.http.get<GettingStartedAssetDefaultFolder>(GET_CREATE_WS_DEFAULT_FOLDER_URI).subscribe(result => {
         if(result.errorMessage) {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", result.errorMessage)
               .then(() => {
                  this.continue();
               });

            return;
         }

         this.currentStep = GettingStartedStep.START_FROM_SCRATCH;
         let composer = this.currentIsComposer();

         if(this.worksheetId) {
            if(composer) {
               this.openWs(GettingStartedStep.START_FROM_SCRATCH);
            }
            else {
               this.routeToOpenWsBy(this.worksheetId);
            }
         }
         else {
            if(composer) {
               this.editSheetSubject.next({
                  op: this.currentStep,
                  newSheet: true,
                  folder: result.folderId
               });
            }
            else {
               let queryParams = {
                  wsWizard: true,
                  scope: AssetEntryHelper.QUERY_SCOPE,
                  folder: result.folderId,
                  closeOnComplete: true
               };

               this.router.navigate(["composer"], { queryParams: queryParams });
            }
         }
      });
   }

   toCreateDashboard(): void {
      this.http.get<GettingStartedAssetDefaultFolder>(GET_CREATE_VS_DEFAULT_FOLDER_URI).subscribe(result => {
         if(result.errorMessage) {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", result.errorMessage)
               .then(() => {
                  this.continue();
               });

            return;
         }

         this.currentStep = GettingStartedStep.CREATE_DASHBOARD;
         let composer = this.currentIsComposer();

         if(composer) {
            this.editSheetSubject.next({
               op: this.currentStep,
               baseWs: this.worksheetId,
               newSheet: true,
               folder: result.folderId
            });
         }
         else {
            let queryParams = {
               vsWizard: true,
               baseWS: this.worksheetId,
               folder: result.folderId,
               closeOnComplete: true
            };

            this.currentStep = GettingStartedStep.CREATE_DASHBOARD;
            this.router.navigate(["composer"], { queryParams: queryParams });
         }
      });
   }

   closeGettingStarted(): void {
      if(this.gettingStartedDialog) {
         this.gettingStartedDialog.ok();
      }
   }

   whetherStayComposer(leaveCallback?: Function): void {
      ComponentTool.showDialog(this.modalService, WhetherStayComposerDialog, () => {
         this.currentStep = GettingStartedStep.CUSTOMIZE_DATA;
         this.showWsTip = true;
      },
         { backdrop: "static" },
         (result) => {
            if(leaveCallback) {
               leaveCallback();
            }

            this.continue(StepIndex.CREATE_DASHBOARD);
         }
      );
   }

   continue(step?: StepIndex): void {
      if(step) {
         this.stepIndex = step.valueOf() % Object.keys(StepIndex).length;
      }

      // should always go back to the home page so if a user cancels the getting started, the
      // user is not left at the location that is different from where he started.
      this.router.navigate(["/portal/tab/report"], {});
      this.showGettingStarted();
   }

   private showGettingStarted(): void {
      if(!this.processing) {
         return;
      }

      this.closeGettingStarted();

      this.gettingStartedDialog = ComponentTool.showDialog(
         this.modalService, GettingStartedDialog, () => { },
         { backdrop: true, windowClass: "getting-started-dialog", animation: true },
         (result) => {
            this.finish();
         }
      );
   }

   private currentIsComposer(): boolean {
      if(this.route.snapshot["_routerState"]) {
         return this.route.snapshot["_routerState"].url?.startsWith("/composer");
      }

      return false;
   }
}
