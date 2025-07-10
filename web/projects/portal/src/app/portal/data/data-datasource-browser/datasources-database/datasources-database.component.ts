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
   AfterViewInit,
   Component,
   ElementRef,
   EventEmitter,
   HostListener,
   Input,
   NgZone,
   OnInit,
   Output,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgForm, UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { ActivatedRoute, ParamMap, Router } from "@angular/router";
import { NgbModal, NgbModalOptions } from "@ng-bootstrap/ng-bootstrap";
import { Observable, of as observableOf, Subscription, throwError } from "rxjs";
import { catchError, debounceTime, distinctUntilChanged, map, switchMap, tap } from "rxjs/operators";
import { RepositoryEntryType } from "../../../../../../../shared/data/repository-entry-type.enum";
import { StompClientService } from "../../../../../../../shared/stomp/stomp-client.service";
import {
   DataSourceEditorModel,
   DataSourceSettingsPage
} from "../../../../../../../shared/util/datasource/data-source-settings-page";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { DataSourceSettingsModel } from "../../../../../../../shared/util/model/data-source-settings-model";
import {
   AccessDatabaseInfoModel,
   CustomDatabaseInfoModel,
   DatabaseInfoModel,
   DatabaseNameInfoModel,
   InformixDatabaseInfoModel,
   OracleDatabaseInfoModel,
   PostgreSQLDatabaseInfoModel,
   SQLServerDatabaseInfoModel
} from "../../../../../../../shared/util/model/database-info-model";
import { DatasourceDatabaseType } from "../../../../../../../shared/util/model/datasource-database-type";
import { DriverInfo } from "../../../../../../../shared/util/model/driver-availability";
import { Tool } from "../../../../../../../shared/util/tool";
import { AssetConstants } from "../../../../common/data/asset-constants";
import { ComponentTool } from "../../../../common/util/component-tool";
import { DeleteDatasourceInfo } from "../../commands/delete-datasource-info";
import { DataNotificationsComponent } from "../../data-notifications.component";
import { DatabaseDefinitionModel } from "../../../../../../../shared/util/model/database-definition-model";
import { InputNameDescDialog } from "../../input-name-desc-dialog/input-name-desc-dialog.component";
import { AdditionalDatasourceDialog } from "./additional-datasource-dialog";
import { NetworkLocationModel } from "../../../../../../../shared/util/model/network-location-model";
import { FeatureFlagsService, FeatureFlagValue } from "../../../../../../../shared/feature-flags/feature-flags.service";
import { DriverWizardComponent } from "./driver-wizard/driver-wizard.component";
import { EditPropertyDialogComponent } from "./edit-property-dialog.component";
import {
   GettingStartedService, StepIndex
} from "../../../../widget/dialog/getting-started-dialog/service/getting-started.service";
import { PortalDataType } from "../../data-navigation-tree/portal-data-type";
import { AppInfoService } from "../../../../../../../shared/util/app-info.service";

const CHECK_DELETE_ADDITIONAL = "../api/portal/data/databases/additional/check/";
const DATABASES_URI: string = "../api/data/databases";
const PORTAL_DATABASE_URI = "../api/portal/data/databases/";
const PORTAL_DATABASE_REFRESH = "../api/portal/data/datasource/refresh-metadata";
const URL_PREFIX: string = "jdbc:ucanaccess://";

export interface AdditionalInfo {
   name: string;
   url: string;
   tooltip: string;
}

export interface PropertyInfo {
   propertyName: string;
   propertyValue: string;
}

@Component({
   selector: "datasources-database",
   templateUrl: "datasources-database.component.html",
   styleUrls: ["datasources-database.component.scss"]
})
export class DatasourcesDatabaseComponent extends DataSourceSettingsPage implements OnInit, AfterViewInit {
   @Input() uploadEnabled = false;
   @Input() inputFocus: boolean = false;
   @Output() onInfoNotify = new EventEmitter<string>(); // content
   @ViewChild("dataNotifications") dataNotifications: DataNotificationsComponent;
   @ViewChild("databaseForm") databaseForm: NgForm;
   @ViewChild("input") input: ElementRef;
   @ViewChild("editPropertyDialog") editPropertyDialog: TemplateRef<any>;
   readonly FeatureFlagValue = FeatureFlagValue;
   DatasourceDatabaseType = DatasourceDatabaseType;
   form: UntypedFormGroup;
   model: DataSourceEditorModel;
   originalModel: DataSourceEditorModel;
   namePattern: RegExp = FormValidators.DATASOURCE_NAME_REGEXP;
   databasePath: string;
   parentPath = "";
   inputFiles: any[] = [];
   additionalList: AdditionalInfo[] = [];
   selectedAdditionalIndex: number[] = [];
   _testQuery: string;
   selectedProperty: any[] = [];
   private routeParamSubscription: Subscription;
   enterprise: boolean;

   searchFunc: (text: Observable<string>) => Observable<any[]> = (text: Observable<string>) =>
      text.pipe(
         debounceTime(300),
         distinctUntilChanged(),
         map(term => !this.driverAvailability ? [] :
            this.driverAvailability.driverClasses
               .filter(driver => driver.toLowerCase().includes(term.toLowerCase()))
               .slice(0, 8)
         ));

   constructor(private route: ActivatedRoute,
               private router: Router,
               private zone: NgZone,
               private httpClient: HttpClient,
               private modalService: NgbModal,
               private featureFlagsService: FeatureFlagsService,
               private appInfoService: AppInfoService,
               private gettingStartedService: GettingStartedService,
               stompClient: StompClientService)
   {
      super(httpClient, stompClient);
   }

   ngOnInit() {
      super.ngOnInit();
      this.appInfoService.isEnterprise().subscribe(info => this.enterprise = info);

      if(this.additionalVisible) {
         // subscribe to route parameters and update current database model
         this.routeParamSubscription = this.route.paramMap.pipe(
            switchMap((routeParams: ParamMap) => {
               this.databasePath = Tool.byteDecode(routeParams.get("databasePath"));
               this.parentPath = routeParams.get("parentPath");
               const listingName = routeParams.get("listingName");

               if(listingName) {
                  return this.httpClient.get("../api/data/database/listing/"
                     + Tool.encodeURIComponentExceptSlash(listingName));
               }
               else if(!this.databasePath) {
                  return this.httpClient.get("../api/data/database/default");
               }

               return this.httpClient.get(PORTAL_DATABASE_URI +
                  Tool.encodeURIComponentExceptSlash(this.databasePath));
            }),
            catchError((error) => {
               if(error.status == 403) {
                  ComponentTool.showMessageDialog(this.modalService, "Unauthorized",
                     "_#(js:data.databases.noEditPermissionError)");
               }
               else if(error.error == "Datasource does not exist!") {
                  ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                     "_#(js:data.datasources.findDataSourceError)");
               }
               else {
                  ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                     "_#(js:em.data.databases.getDatabaseError)");
               }

               this.close();
               return throwError(error);
            }),
            tap((model: DataSourceSettingsModel) => this.uploadEnabled = model.uploadEnabled),
            map((model: DataSourceSettingsModel) =>
               <DataSourceEditorModel> {
                  path: !!this.databasePath ? this.databasePath : this.parentPath,
                  type: RepositoryEntryType.DATA_SOURCE,
                  settings: model
               }))
            .subscribe(model => {
               this.model = model;
               this.originalModel = Tool.clone(model);
               this.setModel(model.settings);
               this.updateAdditionalList();
               // this.updateProperties();
            });
      }
      else {
         this.refreshDefaultTestQuery();
      }

      this.initForm();
   }

   ngAfterViewInit() {
      if(this.input && this.inputFocus) {
         this.input.nativeElement.focus();
      }
   }

   setModel(newModel: DataSourceSettingsModel): void {
      super.setModel(newModel);
      this.refreshDefaultTestQuery();
   }

   isCreateDB(): boolean {
      return this.parentPath != null || this.parentPath == "" || this.parentPath == "/";
   }

   updateAdditionalList() {
      if(this.additionalVisible) {
         this.additionalList = [];
         let additionalDataSources: DatabaseDefinitionModel[]
            = this.model.settings.additionalDataSources;

         if(!additionalDataSources) {
            return;
         }

         for(let datasource of additionalDataSources) {
            let name: string = datasource.name;
            let info: DatabaseInfoModel = datasource.info;
            let url: string = this.getDBUrl(datasource.type, datasource.network, info);

            this.additionalList.push({
               name,
               url,
               tooltip: datasource.description
            });
         }
      }
   }

   canDeactivate(): Observable<boolean> | Promise<boolean> | boolean {
      if(JSON.stringify(this.database) == JSON.stringify(this.originalDatabase)) {
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

   @HostListener("window:beforeunload", ["$event"])
   beforeunloadHandler(event) {
      if(JSON.stringify(this.database) != JSON.stringify(this.originalDatabase)) {
         return "_#(js:unsave.changes.message)";
      }

      return null;
   }

   /**
    * File was changed in file input. Save the current select files.
    * @param event
    */
   updateFiles(event: any): void {
      this.inputFiles = [];

      if(event && event.target && event.target.files) {
         for(let i = 0; i < event.target.files.length; i++) {
            const file = event.target.files[i];

            if(!this.inputFiles.some(f => f.name === file.name)) {
               this.inputFiles.push(file);
            }
         }
      }
   }

   createDriver(): void {
      ComponentTool.showDialog(this.modalService, DriverWizardComponent,
         () => {}, {size: "lg", backdrop: "static"});
   }

   /**
    * Check if the driver is currently available for the selected database type.
    * @returns {boolean}   true if driver is available
    */
   get driverAvailable(): boolean {
      if(!this.driverAvailability) {
         return false;
      }
      else if(this.database.type === DatasourceDatabaseType.CUSTOM ||
              this.database.type === DatasourceDatabaseType.ACCESS)
      {
         return !!this.database.info && this.driverAvailability.driverClasses
            .includes((<CustomDatabaseInfoModel> this.database.info).driverClass);
      }
      else {
         const driver: DriverInfo = this.driverAvailability.drivers
            .find(driverInfo => driverInfo.type === this.database.type);
         return !!driver && driver.installed;
      }
   }

   /**
    * Check if database name is required for the database type.
    * @returns {boolean}   true if database name is required
    */
   get databaseNameRequired(): boolean {
      return (this.database.type === DatasourceDatabaseType.DB2 ||
         this.database.type === DatasourceDatabaseType.INFORMIX ||
         this.database.type === DatasourceDatabaseType.MYSQL ||
         this.database.type === DatasourceDatabaseType.POSTGRESQL ||
         this.database.type === DatasourceDatabaseType.SYBASE ||
         this.database.type === DatasourceDatabaseType.SQLSERVER);
   }

   public afterDatabaseSave(): void {
      this.originalDatabase = Tool.clone(this.database);
      this.originalModel = Tool.clone(this.model);

      if(this.gettingStartedService.isConnectTo()) {
         this.gettingStartedService.setDataSourcePath(this.getDatabasePath(), PortalDataType.DATABASE);
         this.gettingStartedService.continue(StepIndex.CREATE_QUERY_EMPTY_WS);
      }

      this.close(true);
   }

   private getDatabasePath(): string {
      if(!!this.databasePath) {
         return this.databasePath;
      }

      if(this.parentPath) {
         return (this.parentPath.lastIndexOf("/") == this.parentPath.length - 1 ? this.parentPath : this.parentPath + "/")
            + this.database.name;
      }

      return this.database.name;
   }

   public showMessage(message: string, title?: string, testConnection?: boolean) {
      if(!this.additionalVisible) {
         this.zone.runOutsideAngular(() => {
            this.onInfoNotify.emit(message);
         });
      }
      else if(testConnection == true) {
         if(title === "OK") {
            this.dataNotifications.notifications.success(message);
         }
         else if(title == "ERROR") {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", message).then();
         }
      }
      else if(!!this.dataNotifications) {
         this.dataNotifications.notifications.info(message);
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

   additionalSelected(index: number): boolean {
      return this.selectedAdditionalIndex.indexOf(index) >= 0;
   }

   newAdditional() {
      this.httpClient.get("../api/data/database/default").subscribe((model: DataSourceSettingsModel) => {
         let dialog = ComponentTool.showDialog(this.modalService, AdditionalDatasourceDialog,
            (additional: DatabaseDefinitionModel) => {

            if(!this.model.settings.additionalDataSources) {
               this.model.settings.additionalDataSources = [];
            }

            this.model.settings.additionalDataSources.push(additional);

            this.updateAdditionalList();

         }, {size: "lg", backdrop: "static"});

         dialog.additionalDataSource = model.dataSource;
         dialog.primaryDatabasePath = this.databasePath;
         dialog.uploadEnabled = model.uploadEnabled;
         dialog.title = "_#(js:New Additional Connection)";
         dialog.hasDuplicateCheck = (value: string) => {
            let duplicate: boolean = false;
            let message: string = "";

            if(this.model.settings.dataSource.name == value.trim()) {
               message = "_#(js:common.datasource.nameInvalid)";
               duplicate = true;
            }
            else if(!!this.additionalDataSources && this.additionalDataSources.map(
               (source) => source.name).indexOf(value.trim()) != -1)
            {
               message = "_#(js:common.datasource.nameExists)";
               duplicate = true;
            }

            return observableOf({duplicate: duplicate, duplicateMessage: message});
         };
      });
   }

   deleteAdditional() {
      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
         "_#(js:common.datasource.removeSelectedItem)"
         + ComponentTool.MESSAGEDIALOG_MESSAGE_CONNECTION)
         .then((result) => {
            if(result === "ok") {
               this.deleteAdditional0();
            }
         });
   }

   deleteAdditional0() {
      let datasources = this.getSelectedAdditional().map(ds => ds.name);

      const deleteInfo: DeleteDatasourceInfo = {
         datasources
      };

      this.http.put<DeleteDatasourceInfo>(CHECK_DELETE_ADDITIONAL
         + Tool.encodeURIComponentExceptSlash(this.databasePath), deleteInfo)
         .subscribe((response) =>
      {
         if(!!response && !!response.datasources && response.datasources.length > 0) {
            console.log(response.datasources.join());
            ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
               Tool.formatCatalogString("_#(js:common.datasource.connectionUsedByExtendedModel)",
                  [response.datasources.join()]))
               .then((result) => {
                  if(result === "ok") {
                     this.deleteAdditionals(deleteInfo);
                  }
               });
         }
         else {
            this.deleteAdditionals(deleteInfo);
         }
      });
   }

   get additionalDataSources() {
      return this.model.settings.additionalDataSources;
   }

   private deleteAdditionals(deleteInfo: DeleteDatasourceInfo): void {
      this.model.settings.additionalDataSources = this.model.settings.additionalDataSources
         .filter(ds => !deleteInfo.datasources.some(delDs => delDs === ds.name));
      this.selectedAdditionalIndex = [];

      this.updateAdditionalList();
   }

   editAdditional() {
      const dss = this.getSelectedAdditional();

      if(!!dss && dss.length == 1) {
         let ds = dss[0];
         let dialog = ComponentTool.showDialog(this.modalService, AdditionalDatasourceDialog,
            (model: DatabaseDefinitionModel) => {
               this.model.settings.additionalDataSources[this.selectedAdditionalIndex[0]] = model;
               this.updateAdditionalList();
            }, {size: "lg", backdrop: "static"});

         dialog.uploadEnabled = this.uploadEnabled;
         dialog.additionalDataSource = ds;
         dialog.primaryDatabasePath = this.databasePath;
         dialog.title = "_#(js:Edit Additional Connection)";
         dialog.hasDuplicateCheck = (value: string) => observableOf(this.checkDuplicate(value));
      }
   }

   checkDuplicate(value: string): any {
      let duplicate: boolean = false;
      let message: string = "";

      if(this.model.settings.dataSource.name == value.trim()) {
         message = "_#(js:common.datasource.nameInvalid)";
         duplicate = true;
      }
      else if(!!this.additionalDataSources && this.getSelectedAdditional()[0].name != value.trim()
         && this.additionalDataSources.map((source) => source.name).indexOf(value.trim()) != -1)
      {
         message = "_#(js:common.datasource.nameExists)";
         duplicate = true;
      }

      return {duplicate: duplicate, duplicateMessage: message};
   }

   renameAdditional() {
      const dss = this.getSelectedAdditional();

      if(!!dss && dss.length == 1) {
         const ds = dss[0];

         const dialog = ComponentTool.showDialog(this.modalService, InputNameDescDialog, (result) => {
            this.model.settings.additionalDataSources[this.selectedAdditionalIndex[0]].name =
               result.name;
            this.model.settings.additionalDataSources[this.selectedAdditionalIndex[0]].description =
               result.description;
            this.updateAdditionalList();
         }, {backdrop: "static"});

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

   protected isDatabaseEqual(): boolean {
      return super.isDatabaseEqual() && Tool.isEquals(this.model, this.originalModel);
   }

   needSave(): boolean {
      return !this.isDatabaseEqual() || this.databasePath == null;
   }

   private getSelectedAdditional(): DatabaseDefinitionModel[] {
      return Tool.clone(this.selectedAdditionalIndex
         .map(index => this.model.settings.additionalDataSources[index]));
   }

   close(saved: boolean = false): void {
      const index = this.databasePath ? this.databasePath.lastIndexOf("/") : -1;
      let parentPath = null;

      if(this.parentPath) {
         parentPath = this.parentPath;
      }
      else if(this.databasePath != "/" && index > -1) {
         parentPath = this.databasePath.substr(0, index);
      }

      const extras = {
         queryParams: {
            path: parentPath,
            scope: AssetConstants.QUERY_SCOPE
         }
      };

      if(!saved && this.gettingStartedService.isConnectTo()) {
         this.gettingStartedService.continue();
      }

      this.router.navigate(["/portal/tab/data/datasources"], extras);
   }

   isCustom(type: string): boolean {
      return type === DatasourceDatabaseType.CUSTOM ||
         type === DatasourceDatabaseType.ACCESS;
   }

   testQueryEnabled(type: string): boolean {
      return type === DatasourceDatabaseType.CUSTOM;
   }

   isFilePathEnabled(type: string): boolean {
      return type === DatasourceDatabaseType.ACCESS;
   }

   getDBUrl(type: DatasourceDatabaseType, network: NetworkLocationModel, info: DatabaseInfoModel): string {
      let url: string = "";

      if(this.isCustom(type)) {
         let customer: CustomDatabaseInfoModel = <CustomDatabaseInfoModel> info;
         url = customer.customUrl;

         if(url == URL_PREFIX && DatasourceDatabaseType.ACCESS == type) {
            let access: AccessDatabaseInfoModel = <AccessDatabaseInfoModel> info;
            url = URL_PREFIX + access.dataSourceName;
         }
      }
      else if(info.customEditMode && info.customUrl) {
         url = info.customUrl;
      }
      else if(DatasourceDatabaseType.DB2 == type) {
         let db2: DatabaseNameInfoModel = <DatabaseNameInfoModel> info;
         url = "jdbc:db2://" + network.hostName + ":" + network.portNumber + "/" + db2.databaseName;
      }
      else if(DatasourceDatabaseType.INFORMIX == type) {
         let informix: InformixDatabaseInfoModel = <InformixDatabaseInfoModel> info;
         let serverName: string = informix.serverName;
         let dbLocale: string = informix.databaseLocale;
         url = "jdbc:informix-sqli://" + network.hostName + ":" + network.portNumber
            + "/" + informix.databaseName;

         if(!serverName) {
            url += Tool.isEmpty(dbLocale) ? ":INFORMIXSERVER=" + serverName
               : ":INFORMIXSERVER=" + serverName + ";db_locale=" + dbLocale;
         }
         else if(!dbLocale) {
            url += ";dbLocale=" + dbLocale;
         }
      }
      else if(DatasourceDatabaseType.MYSQL == type) {
         let mysql: DatabaseNameInfoModel = <DatabaseNameInfoModel> info;
         let dbName = mysql.databaseName;
         url = "jdbc:mysql://" + network.hostName + ":" + network.portNumber + (Tool.isEmpty(dbName) ? "" : "/" + dbName);
      }
      else if(DatasourceDatabaseType.ORACLE == type) {
         let oracle: OracleDatabaseInfoModel = <OracleDatabaseInfoModel> info;
         url = "jdbc:oracle:thin:@" + network.hostName + ":" + network.portNumber + "/" + oracle.sid;
      }
      else if(DatasourceDatabaseType.POSTGRESQL == type) {
         let postgresSql: PostgreSQLDatabaseInfoModel = <PostgreSQLDatabaseInfoModel> info;
         url = "jdbc:postgresql://" + network.hostName + ":" + network.portNumber + "/" + postgresSql.databaseName;
      }
      else if(DatasourceDatabaseType.SQLSERVER == type) {
         let sqlServer: SQLServerDatabaseInfoModel = <SQLServerDatabaseInfoModel> info;
         let instanceName = sqlServer.instanceName;
         url = "jdbc:sqlserver://" + network.hostName + (Tool.isEmpty(instanceName) ? ":" + network.portNumber
            : "\\" + instanceName + ":" + network.portNumber);
      }

      return url;
   }

   typeChanged(newType: DatasourceDatabaseType, supportToggleCredential: boolean, useCredentialId: boolean): void {
      super.typeChanged(newType, supportToggleCredential, useCredentialId);
      this.refreshDefaultTestQuery();
   }

   refreshDefaultTestQuery(): void {
      if(!this.testQueryEnabled(this.database?.type) || !!this.database?.info?.["testQuery"]) {
         return;
      }

      let params = new HttpParams()
         .set("type", this.database.type)
         .set("driver", this.customInfo.driverClass);

      this.httpClient.get<string>("../api/data/database/test-query/default",
         { params }).subscribe(query =>
      {
         this._testQuery = query ?? this._testQuery;
      });
   }

   get testQuery(): string {
      return this.database?.info?.["testQuery"]
         ? this.database.info["testQuery"]
         : this._testQuery;
   }

   setTestQuery(testQuery: string): void {
      this.database.info["testQuery"] = testQuery;
   }

   initForm(): void {
      this.form = new UntypedFormGroup({
         "name": new UntypedFormControl(this.model, [Validators.required]),
         "value": new UntypedFormControl(this.model, [Validators.required])
      });
   }

   newProperty() {
      this.showEditPropertyDialog();
   }

   selectProperty(event: MouseEvent, prop: any) {
      const findIndex: number = this.selectedProperty.findIndex(p => p.key == prop.key);

      if(event.ctrlKey && findIndex == -1) {
         this.selectedProperty.push(prop);
      }
      else if(event.ctrlKey && findIndex != -1) {
         this.selectedProperty.splice(findIndex, 1);
      }
      else if(event.shiftKey) {
         const keys = Object.keys(this.database.info.poolProperties).sort();
         const selKeys = this.selectedProperty.map(a => a.key);
         let topSel = keys.length;
         let botSel = -1;
         const idx = keys.indexOf(prop.key);

         for(let i = 0; i < keys.length; i++) {
            if(selKeys.includes(keys[i])) {
               topSel = Math.min(topSel, i);
               botSel = Math.max(topSel, i);
            }
         }

         if(topSel <= botSel && idx >= 0) {
            this.selectedProperty = [];
            const start = (idx > topSel) ? topSel : idx;
            const end = (idx > topSel) ? idx : botSel;

            for(let i = start; i <= end; i++) {
               this.selectedProperty.push({key: keys[i], value: this.database.info.poolProperties[keys[i]]});
            }
         }

         event.preventDefault();
      }
      else {
         this.selectedProperty = findIndex == -1 ? [prop] : [];
      }
   }

   isSelectedProperty(prop: any): boolean {
      return this.selectedProperty.findIndex(p => p.key == prop.key) != -1;
   }

   editProperty() {
      this.showEditPropertyDialog(true);
   }

   showEditPropertyDialog(edit: boolean = false): void {
      let modalOptions: NgbModalOptions = {
         backdrop: "static",
      };

      let dialog = ComponentTool.showDialog(this.modalService, EditPropertyDialogComponent,
         (result: any) => {
            if(edit) {
               delete this.database.info.poolProperties[Tool.clone(this.selectedProperty[0]).key];
               this.selectedProperty = [];
            }

            if(!this.database.info.poolProperties) {
               this.database.info.poolProperties = {};
            }

            this.database.info.poolProperties[result.key] = result.value;
            this.selectedProperty = [result];
      }, modalOptions);

      let editProp = Tool.clone(this.selectedProperty[0]);
      dialog.existingNames = this.getExistingNames(editProp);

      if(edit) {
         dialog.info = editProp;
      }
   }

   deleteProperty() {
      const message = "_#(js:data.databases.confirmDeleteProperty)";

      ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)", message,
         {"yes": "_#(js:Yes)", "no": "_#(js:No)"})
         .then((buttonClicked) => {

            if(buttonClicked === "yes") {
               let selected: any[] = Tool.clone(this.selectedProperty);

               for(let i: number = 0; i < selected.length; i++) {
                  let prop = selected[i];
                  delete this.database.info.poolProperties[prop.key];
                  this.selectedProperty.splice(i, 1);
               }
            }
         });
   }

   getExistingNames(editProp: any): string[] {
      let allNames: string[] =
         this.database?.info?.poolProperties ? Object.keys(this.database.info.poolProperties) : [];

      if(!editProp) {
         return allNames;
      }

      return allNames.filter(name => name != editProp.key);
   }

   changeCustomEditMode(val: boolean) {
      this.database.info.customEditMode = val;

      if(val) {
         let params: HttpParams = new HttpParams().set("path", this.primaryDatabasePath);

         this.httpClient.post<string>("../api/portal/data/database/customUrl",
            this.database, {params}).subscribe(url =>
         {
            this.database.info.customUrl = url;
         });
      }
      else {
         let params: HttpParams = new HttpParams().set("path", this.primaryDatabasePath);

         this.httpClient.post<DatabaseDefinitionModel>("../api/portal/data/database/definition",
            this.database, {params}).subscribe(database =>
         {
            this.database.info = database.info;
            this.database.network = database.network;
         });
      }
   }

   refreshMetadata() {
      const params = new HttpParams().set("dataSource", this.databasePath);
      this.http.get<boolean>(PORTAL_DATABASE_REFRESH, {params}).subscribe(success =>
      {
         if(success) {
            this.dataNotifications.notifications.success("_#(js:data.databases.refreshSuccess)");
         }
         else {
            this.dataNotifications.notifications.success("_#(js:data.databases.refreshFailed)");
         }
      });
   }
}
