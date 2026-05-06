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
import {ChangeDetectorRef, Component, HostListener, NgZone, OnDestroy, OnInit, ViewChild} from "@angular/core";
import { ActivatedRoute, ParamMap, Router } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable, of as observableOf, Subscription } from "rxjs";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { DataSourceDefinitionModel } from "../../../../../../../shared/util/model/data-source-definition-model";
import { Tool } from "../../../../../../../shared/util/tool";
import { ComponentTool } from "../../../../common/util/component-tool";
import { GuiTool } from "../../../../common/util/gui-tool";
import { DeleteDatasourceInfo } from "../../commands/delete-datasource-info";
import { DataNotificationsComponent } from "../../data-notifications.component";
import { InputNameDescDialog } from "../../input-name-desc-dialog/input-name-desc-dialog.component";
import { DataSourceInfo } from "../../model/data-source-info";
import { DatasourcesDatasourceDialogComponent } from "./datasources-datasource-dialog/datasources-datasource-dialog.component";
import {
   GettingStartedService, StepIndex
} from "../../../../widget/dialog/getting-started-dialog/service/getting-started.service";
import { PortalDataType } from "../../data-navigation-tree/portal-data-type";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import { WSObjectType } from "../../../../composer/dialog/ws/new-worksheet-dialog.component";
import { DataSourceConnectionStatusRequest } from "../../model/data-source-connection-status-request";
import { DataSourceStatus } from "../../model/data-source-status";
import { DatasourceBrowserService } from "../datasource-browser.service";

const DATASOURCES_URI: string = "../api/portal/data/datasources";
const DATASOURCE_STATUSES_URI = "../api/data/datasources/statuses";

export interface AdditionalInfo {
   name: string;
   tooltip: string;
}

@Component({
   selector: "datasources-datasource",
   templateUrl: "datasources-datasource.component.html",
   styleUrls: ["datasources-datasource.component.scss"]
})
export class DatasourcesDatasourceComponent implements OnInit, OnDestroy{
   @ViewChild("dataNotifications") dataNotifications: DataNotificationsComponent;
   defaultDataSource: DataSourceDefinitionModel = {
      name: "",
      oldName: null,
      parentPath: "",
      type: null,
      deletable: true,
      tabularView: null,
      sequenceNumber: 0
   };
   datasource: DataSourceDefinitionModel = Tool.clone(this.defaultDataSource);
   originalDatasource: DataSourceDefinitionModel;
   datasourceType: string;
   datasourcePath: string;
   parentPath = "";
   editing = false;
   datasourceValid = false;
   additionalList: AdditionalInfo[] = [];
   selectedAdditionalIndex: number[] = [];
   private routeParamSubscription: Subscription;
   enterprise: boolean;
   private attemptingConnectionStatus: string = "_#(js:data.datasources.attemptingToConnectToDataSource)";
   private failedConnectionStatus: string = "_#(js:data.datasources.problemRetrievingDataSourceStatus)";
   dataSourceConnected: boolean = null;
   dataSourceStatusMessage: string = null;
   loadingDataSourceStatus = false;

   constructor(private httpClient: HttpClient,
               private modalService: NgbModal,
               private route: ActivatedRoute,
               private router: Router,
               private zone: NgZone,
               private changeRef: ChangeDetectorRef,
               private appInfoService: AppInfoService,
               private datasourceBrowserService: DatasourceBrowserService,
               private gettingStartedService: GettingStartedService)
   {
   }

   ngOnInit(): void {
      this.appInfoService.isEnterprise().subscribe(info => this.enterprise = info);
      // subscribe to route parameters and update current database model
      this.routeParamSubscription = this.route.paramMap
         .subscribe((routeParams: ParamMap) => {
            this.datasourcePath = Tool.byteDecode(routeParams.get("datasourcePath"));
            this.parentPath = routeParams.get("parentPath");
            this.datasourceType = routeParams.get("datasourceType");
            const listingName = routeParams.get("listingName");
            this.datasource = Tool.clone(this.defaultDataSource);
            this.originalDatasource = Tool.clone(this.datasource);

            if(listingName) {
               this.editing = false;
               this.httpClient.get<DataSourceDefinitionModel>(
                  DATASOURCES_URI + "/listing/" + listingName)
                  .subscribe(data => {
                     this.datasource = data;
                     this.defaultDataSource = Tool.clone(this.datasource);
                     this.originalDatasource = Tool.clone(this.datasource);
                     this.loadDataSourceStatus();
                  });
            }
            else if(!!this.datasourceType) {
               this.datasource.type = this.datasourceType;
               this.editing = false;
               this.httpClient.post<DataSourceDefinitionModel>(DATASOURCES_URI + "/refreshView", this.datasource)
                  .subscribe(
                     data => {
                           // Run change detection to set the datasource
                           this.zone.run(() => {
                           this.datasource = data;
                              this.defaultDataSource = Tool.clone(this.datasource);
                              this.originalDatasource = Tool.clone(this.datasource);
                              this.loadDataSourceStatus();
                           });
                     },
                     () => {
                        this.dataNotifications.notifications.danger("_#(js:data.datasources.refreshViewError)");
                     }
                  );
            }
            else {
               this.editing = true;
               this.refreshDataSource();
            }
         });
   }


   /**
    * Send request to get the data source definition for the original data source name.
    */
   private refreshDataSource(): void {
      this.httpClient.get<DataSourceDefinitionModel>(DATASOURCES_URI + "/"
         + Tool.encodeURIComponentExceptSlash(this.datasourcePath))
         .subscribe(
            data => {
               this.datasource = data;
               this.updateAdditionalList();
               this.defaultDataSource = Tool.clone(this.datasource);
               this.originalDatasource = Tool.clone(this.datasource);
               this.loadDataSourceStatus();
            },
            (error) => {
               if(error.status == 403) {
                  ComponentTool.showMessageDialog(
                     this.modalService, "Unauthorized",
                     "_#(js:data.databases.noEditPermissionError)");
                  this.close();
               }
               else {
                  this.dataNotifications.notifications.danger("_#(js:data.datasources.getDataSourceError)");
               }
            }
         );
   }

   ngOnDestroy(): void {
      if(this.routeParamSubscription) {
         this.routeParamSubscription.unsubscribe();
         this.routeParamSubscription = null;
      }
   }

   get canCreateQuery(): boolean {
      return !!this.datasourcePath && this.dataSourceConnected === true;
   }

   get queryStatusLabel(): string {
      if(!this.datasourcePath) {
         return "Save first";
      }

      if(this.loadingDataSourceStatus) {
         return "Checking";
      }

      if(this.dataSourceConnected === true) {
         return "Healthy";
      }

      if(this.dataSourceConnected === false) {
         return "Unavailable";
      }

      return "Unknown";
   }

   get queryStatusMessage(): string {
      if(!this.datasourcePath) {
         return "Save this data source first, then create a new query from it.";
      }

      if(this.loadingDataSourceStatus) {
         return "Checking connection health for this data source.";
      }

      if(this.dataSourceConnected === true) {
         return "Connection is healthy. Create a new query from this data source.";
      }

      return this.dataSourceStatusMessage
         || "This data source is not currently healthy. Fix the connection before creating a query.";
   }

   get queryStatusIconClass(): string {
      if(!this.datasourcePath || this.loadingDataSourceStatus) {
         return "help-question-mark-icon";
      }

      return this.dataSourceConnected ? "submit-icon" : "alert-circle-icon";
   }

   createQuery(): void {
      if(!this.canCreateQuery) {
         return;
      }

      const params = new HttpParams()
         .set("wsWizard", "true")
         .set("baseDataSource", this.datasourcePath)
         .set("baseDataSourceType", `${WSObjectType.TABULAR}`);
      GuiTool.openBrowserTab("composer", params);
   }

   refreshDataSourceStatus(): void {
      if(this.loadingDataSourceStatus || !this.datasourcePath) {
         return;
      }

      this.loadDataSourceStatus();
   }

   moveDataSource(): void {
      const datasource = this.currentDataSourceInfo;

      if(!datasource || !(datasource.editable && datasource.deletable)) {
         return;
      }

      this.datasourceBrowserService.moveDataSource(datasource, this.parentPath,
         () => {
            this.datasourceBrowserService.refreshTree();
            this.close(true);
         },
         (error) => this.dataNotifications.notifications.danger(error.error.message));
   }

   deleteDataSource(): void {
      const datasource = this.currentDataSourceInfo;

      if(!datasource || !datasource.deletable) {
         return;
      }

      this.datasourceBrowserService.deleteDataSourceByInfo(datasource,
         (type: string, message: string) => {
            if(type === "success") {
               this.datasourceBrowserService.refreshTree();
               this.close(true);
            }
            else if(type === "danger") {
               this.dataNotifications.notifications.danger(message);
            }
         });
   }

   @HostListener("window:beforeunload", ["$event"])
   beforeunloadHandler(event) {
      if(JSON.stringify(this.datasource) != JSON.stringify(this.originalDatasource)) {
         return "_#(js:unsave.changes.message)";
      }

      return null;
   }

   datasourceChanged(value: DataSourceDefinitionModel) {
      this.datasource = value;
      this.originalDatasource = Tool.clone(this.datasource);
      this.updateAdditionalList();
   }

   private loadDataSourceStatus(): void {
      if(!this.datasourcePath) {
         this.loadingDataSourceStatus = false;
         this.dataSourceConnected = null;
         this.dataSourceStatusMessage = null;
         return;
      }

      this.loadingDataSourceStatus = true;
      this.dataSourceStatusMessage = this.attemptingConnectionStatus;
      const request = <DataSourceConnectionStatusRequest> {
         paths: [this.datasourcePath],
         updateStatus: true,
         timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone
      };

      this.httpClient.post<DataSourceStatus[]>(DATASOURCE_STATUSES_URI, request)
         .subscribe(
            (statuses) => {
               const status = statuses?.[0];
               this.dataSourceConnected = status?.connected ?? false;
               this.dataSourceStatusMessage = status?.message || this.failedConnectionStatus;
            },
            () => {
               this.dataSourceConnected = false;
               this.dataSourceStatusMessage = this.failedConnectionStatus;
            },
            () => this.loadingDataSourceStatus = false
         );
   }

   canDeactivate(): Observable<boolean> | Promise<boolean> | boolean {
      if(JSON.stringify(this.datasource) == JSON.stringify(this.originalDatasource)) {
         return true;
      }

      const message = "_#(js:unsave.changes.message)";
      return ComponentTool.showMessageDialog(this.modalService, "_#(js:Confirm)", message, {
         "Yes": "_#(js:Yes)",
         "No": "_#(js:No)"
      }).then((value) => {
         return Promise.resolve(value === "Yes");
      });
   }

   get originalName(): string {
      if(this.datasourcePath) {
         const parts = this.datasourcePath.split("/");
         return parts[parts.length - 1];
      }

      return null;
   }

   get currentDataSourceInfo(): DataSourceInfo {
      if(!this.datasourcePath) {
         return null;
      }

      return {
         name: this.datasource.name || this.originalName,
         path: this.datasourcePath,
         type: {
            name: this.datasourceType || PortalDataType.DATA_SOURCE,
            label: this.datasourceType || PortalDataType.DATA_SOURCE
         },
         createdBy: null,
         createdDate: 0,
         createdDateLabel: "",
         dateFormat: "",
         editable: true,
         deletable: this.datasource.deletable,
         hasSubFolder: false
      };
   }

   updateAdditionalList() {
      if(this.datasource.tabularView) {
         if(!!this.datasource.additionalConnections) {
            this.additionalList = this.datasource.additionalConnections
               .sort((d1,d2) => d1?.name > d2?.name ? 1 : 0)
               .map(ds => <AdditionalInfo> {
                  name: ds.name,
                  tooltip: ds.description
               });
         }
         else {
            this.additionalList = [];
         }
      }
   }

   selectAdditional(event: MouseEvent, index: number) {
      if(event.ctrlKey) {
         this.selectedAdditionalIndex.push(index);
      }
      else {
         this.selectedAdditionalIndex = [index];
      }
   }

   private getSelectedAdditional(): DataSourceDefinitionModel[] {
      return Tool.clone(this.selectedAdditionalIndex
         .map(index => this.datasource.additionalConnections[index]));
   }

   additionalSelected(index: number): boolean {
      return this.selectedAdditionalIndex.indexOf(index) >= 0;
   }

   newAdditional() {
      const dialog = ComponentTool.showDialog(this.modalService, DatasourcesDatasourceDialogComponent, (ds: DataSourceDefinitionModel) => {
         if(!this.datasource.additionalConnections) {
            this.datasource.additionalConnections = [];
         }

         this.datasource.additionalConnections.push(ds);
         this.updateAdditionalList();
      }, { size: "lg", backdrop: "static" });

      const newDs = Tool.clone(this.defaultDataSource);
      newDs.parentDataSource = this.originalDatasource.name;
      newDs.additionalConnections = null;

      for(let i = 1;; i++) {
         const name = `${this.datasource.name} ${i}`;

         if(this.additionalList.findIndex(ads => ads?.name === name) === -1) {
            newDs.name = name;
            break;
         }
      }

      dialog.title = "_#(js:New Additional Connection)";
      dialog.datasource = newDs;
      dialog.usedNames = this.additionalList?.map(d => d.name);
   }

   deleteAdditional() {
      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
         "_#(js:common.datasource.removeSelectedItem)"
         + ComponentTool.MESSAGEDIALOG_MESSAGE_CONNECTION)
         .then((result) => {
            if(result === "ok") {
               this.deleteAdditionals();
            }
         });
   }

   deleteAdditionals() {
      let datasources = this.getSelectedAdditional().map(ds => ds.name);

      const deleteInfo: DeleteDatasourceInfo = {
         datasources
      };

      this.datasource.additionalConnections = this.datasource.additionalConnections
         .filter(ds => !deleteInfo.datasources.some(delDs => delDs === ds.name));
      this.selectedAdditionalIndex = [];

      this.updateAdditionalList();
   }

   get additionalDataSources() {
      return this.datasource.additionalConnections;
   }

   editAdditional() {
      const dss = this.getSelectedAdditional();

      if(!!dss && dss.length == 1) {
         const dialog = ComponentTool.showDialog(this.modalService, DatasourcesDatasourceDialogComponent, (ds: DataSourceDefinitionModel) => {
            this.datasource.additionalConnections[this.selectedAdditionalIndex[0]] = ds;
            this.updateAdditionalList();
         }, { size: "lg", backdrop: "static" });
         dialog.title = "_#(js:Edit Additional Connection)";
         dialog.datasource = Tool.clone(dss[0]);
         dialog.usedNames = this.additionalList.map(ds => ds?.name).filter(name => name !== dss[0].name);
      }
   }

   renameAdditional() {
      const dss = this.getSelectedAdditional();

      if(!!dss && dss.length == 1) {
         const ds = dss[0];

         const dialog = ComponentTool.showDialog(this.modalService, InputNameDescDialog, (result) => {
            this.datasource.additionalConnections[this.selectedAdditionalIndex[0]].name =
               result.name;
            this.datasource.additionalConnections[this.selectedAdditionalIndex[0]].description =
               result.description;
            this.updateAdditionalList();
         });

         dialog.value = ds.name;
         dialog.description = ds.description;
         dialog.title = "_#(js:Rename Additional Connection)";
         dialog.namePattern = FormValidators.DATASOURCE_NAME_REGEXP;
         dialog.hasDuplicateCheck = (value: string) => {
            let duplicate = this.checkDuplicate(value);
            return observableOf(duplicate.duplicate);
         };
      }
   }

   checkDuplicate(value: string): any {
      let duplicate: boolean = false;
      let message: string = "";

      if(this.datasource.name == value.trim()) {
         message = "_#(js:common.datasource.nameInvalid)";
         duplicate = true;
      }
      else if(!!this.additionalDataSources && this.getSelectedAdditional()[0].name != value.trim()
         && this.additionalDataSources.map((source) => source.name).indexOf(value.trim()) != -1)
      {
         message = "_#(js:common.datasource.nameExists)";
         duplicate = true;
      }

      return {duplicate: duplicate, message: message};
   }

   /**
    * Called when use clicks ok. Send request to check for duplicate data source then save the data
    * source.
    */
   ok(): void {
      if(!this.editing || this.datasource.name !== this.originalName) {
         this.httpClient.get<boolean>(DATASOURCES_URI + "/checkDuplicate/"
                                      + Tool.encodeURIComponentExceptSlash(this.datasource.name))
            .subscribe(
               duplicate => {
                  if(duplicate) {
                     ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                        "_#(js:data.datasources.duplicateDataSourceName)");
                  }
                  else {
                     this.saveDataSource();
                  }
               }
            );
      }
      else {
         this.saveDataSource();
      }
   }

   /**
    * Send request to save the data source on the server then close page.
    */
   private saveDataSource(): void {
      let request: Observable<DataSourceInfo>;

      if(this.editing) {
         request = this.httpClient.put<DataSourceInfo>(DATASOURCES_URI + "/"
            + Tool.encodeURIComponentExceptSlash(this.originalName), this.datasource);
      }
      else {
         this.datasource.parentPath = this.parentPath;
         request = this.httpClient.post<DataSourceInfo>(DATASOURCES_URI, this.datasource);
      }

      request
         .subscribe(
            () => {
               this.originalDatasource = Tool.clone(this.datasource);

               if(!this.editing && this.gettingStartedService.isConnectTo()) {
                  this.gettingStartedService.setDataSourcePath(this.datasourcePath, this.datasourceType);
                  this.gettingStartedService.continue(StepIndex.CREATE_QUERY_EMPTY_WS);
               }

               this.close(true);
            },
            (err) => {
               let message;
               const error = err && err.error || {};

               if(error.error === "messageException" && error.message) {
                  message = error.message;
               }
               else {
                  message = "_#(js:data.datasources.saveDataSourceError)";
               }

               this.dataNotifications.notifications.danger(message);
            }
         );
   }

   /**
    * Close page and navigate to datasources browsing page.
    * @param created if a datasource was created
    */
   close(created: boolean = false): void {
      const index = this.datasourcePath ? this.datasourcePath.lastIndexOf("/") : -1;
      let parentPath = null;

      if(this.parentPath) {
         parentPath = this.parentPath;
      }
      else if(this.datasourcePath != "/" && index > -1) {
         parentPath = this.datasourcePath.substr(0, index);
      }

      const extras = {
         queryParams: {
            path: parentPath || "/",
            scope: 0
         }
      };

      this.router.navigate(["/portal/tab/data/datasources"], extras);
   }

    updateDatasourceValid($event: boolean) {
      this.datasourceValid = $event;
       this.changeRef.detectChanges();
    }
}
