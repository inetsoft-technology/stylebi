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
import { HttpClient, HttpErrorResponse, HttpParams } from "@angular/common/http";
import { Directive, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { of } from "rxjs";
import { catchError } from "rxjs/operators";
import { ResourcePermissionModel } from "../../../em/src/app/settings/security/resource-permission/resource-permission-model";
import { ConnectionStatus } from "../../../em/src/app/settings/security/security-provider/security-provider-model/connection-status";
import { DataSourceSettingsModel } from "../model/data-source-settings-model";
import { DatabaseDefinitionModel } from "../model/database-definition-model";
import {
   AccessDatabaseInfoModel,
   CustomDatabaseInfoModel,
   DatabaseInfoModel,
   DatabaseNameInfoModel,
   InformixDatabaseInfoModel,
   ODBCDatabaseInfoModel,
   OracleDatabaseInfoModel,
   SQLServerDatabaseInfoModel
} from "../model/database-info-model";
import { DatabaseOptionType, DatasourceDatabaseType } from "../model/datasource-database-type";
import { DriverAvailability, DriverInfo } from "../model/driver-availability";
import { RepositoryEditorModel } from "../model/repository-editor-model";
import { Tool } from "../tool";

const TEST_ADDITIONAL = "../api/portal/data/databases/additional/test";

export interface DataSourceEditorModel extends RepositoryEditorModel {
   settings: DataSourceSettingsModel;
}

export enum Connection {
   TRANSACTION_READ_UNCOMMITTED = 1,
   TRANSACTION_READ_COMMITTED = 2,
   TRANSACTION_REPEATABLE_READ = 4,
   TRANSACTION_SERIALIZABLE = 8
}

export enum TableNameOption {
   CATALOG_SCHEMA_OPTION = 0,
   SCHEMA_OPTION = 1,
   TABLE_OPTION = 2,
   DEFAULT_OPTION = 3
}

@Directive()
export abstract class DataSourceSettingsPage implements OnInit {
   @Input() database: DatabaseDefinitionModel;
   @Input() additionalVisible: boolean = true;
   @Input() primaryDatabasePath: string;
   @Input() showTestMessage: boolean = true;
   @Output() onTested: EventEmitter<{type: string, message: string}> = new EventEmitter<{type: string, message: string}>();
   public abstract model: DataSourceEditorModel;
   public abstract showMessage(msg: string, title?: string, testConnection?: boolean): void;
   public abstract afterDatabaseSave(): void;

   databaseOptions: DatabaseOptionType[] = [
      {value: DatasourceDatabaseType.ORACLE, label: "_#(js:em.data.databases.oracle)"},
      {value: DatasourceDatabaseType.SQLSERVER, label: "_#(js:em.data.databases.sqlserver)"},
      {value: DatasourceDatabaseType.MYSQL, label: "_#(js:em.data.databases.mysql)"},
      {value: DatasourceDatabaseType.DB2, label: "_#(js:em.data.databases.db2)"},
      {value: DatasourceDatabaseType.INFORMIX, label: "_#(js:em.data.databases.informix)"},
      {value: DatasourceDatabaseType.POSTGRESQL, label: "_#(js:em.data.databases.postgresql)"},
      {value: DatasourceDatabaseType.ACCESS, label: "_#(js:em.data.databases.access)"},
      {value: DatasourceDatabaseType.CUSTOM, label: "_#(js:em.data.databases.custom)"},
   ];

   isolation: any[] = [
      {value: -1, label: "_#(js:Default)"},
      {value: Connection.TRANSACTION_READ_UNCOMMITTED, label: "_#(js:READ_UNCOMMITTED)"},
      {value: Connection.TRANSACTION_READ_COMMITTED, label: "_#(js:READ_COMMITTED)"},
      {value: Connection.TRANSACTION_REPEATABLE_READ, label: "_#(js:REPEATABLE_READ)"},
      {value: Connection.TRANSACTION_SERIALIZABLE, label: "_#(js:SERIALIZABLE)"}
   ];

   tableOptions: any[] = [
      {value: TableNameOption.DEFAULT_OPTION, label: "_#(js:Default)"},
      {value: TableNameOption.CATALOG_SCHEMA_OPTION, label: "_#(js:Catalog.Schema.Table)"},
      {value: TableNameOption.SCHEMA_OPTION, label: "_#(js:Schema.Table)"},
      {value: TableNameOption.TABLE_OPTION, label: "_#(js:Table)"}
   ];

   originalDatabase: DatabaseDefinitionModel;
   permissions: ResourcePermissionModel;
   originalPermissions: ResourcePermissionModel;
   driverAvailability: DriverAvailability;
   dataSourceSettingsValid: boolean = true;
   databaseStatus: string = "_#(js:em.security.testlogin.note4)";
   protected _auditPath: string;

   get isEqual(): boolean {
      return this.isDatabaseEqual() && this.isPermissionsEqual;
   }

   protected isDatabaseEqual(): boolean {
      return Tool.isEquals(this.database, this.originalDatabase);
   }

   get isPermissionsEqual(): boolean {
      return Tool.isEquals(this.permissions, this.originalPermissions);
   }

   get customInfo(): CustomDatabaseInfoModel {
      return this.database.info as CustomDatabaseInfoModel;
   }

   get databaseInfo(): DatabaseInfoModel {
      return this.database.info as DatabaseInfoModel;
   }

   get accessInfo(): AccessDatabaseInfoModel {
      return this.database.info as AccessDatabaseInfoModel;
   }

   get databaseNameInfo(): DatabaseNameInfoModel {
      return this.database.info as DatabaseNameInfoModel;
   }

   get informixInfo(): InformixDatabaseInfoModel {
      return this.database.info as InformixDatabaseInfoModel;
   }

   get odbcInfo(): ODBCDatabaseInfoModel {
      return this.database.info as ODBCDatabaseInfoModel;
   }

   get oracleInfo(): OracleDatabaseInfoModel {
      return this.database.info as OracleDatabaseInfoModel;
   }

   get sqlServerInfo(): SQLServerDatabaseInfoModel {
      return this.database.info as SQLServerDatabaseInfoModel;
   }

   get supportToggleCredential() {
      return this.database?.authentication?.credentialVisible;
   }

   get useCredentialId() {
      return !this.database?.authentication?.useCredentialId;
   }

   constructor(protected http: HttpClient) {
   }

   ngOnInit() {
      this.refreshDrivers();
   }

   setModel(newModel: DataSourceSettingsModel): void {
      this.database = newModel.dataSource;

      if(this.database && "testQuery" in this.database.info) {
         const info: any = this.database.info;
         info.testQuery = info.testQuery == null ? "" : info.testQuery;
      }

      this.originalDatabase = Tool.clone(this.database);
      this.permissions = newModel.permissions;
      this.originalPermissions = Tool.clone(this.permissions);
   }

   /**
    * Send request to refresh the available drivers.
    */
   protected refreshDrivers(): void {
      this.http.get<DriverAvailability>("../api/em/settings/content/repository/dataSource/driverAvailability")
         .subscribe(data => {
               this.driverAvailability = data;

               if(data.odbcAvailable) {
                  this.databaseOptions.unshift({
                     value: DatasourceDatabaseType.ODBC,
                     label: "_#(js:em.data.databases.odbc)"
                  });
               }
            },
            () => this.showMessage("_#(js:data.databases.getDriversError)", "_#(js:Error)")
         );
   }

   reset(): void {
      this.database = this.originalDatabase;
      this.originalDatabase = Tool.clone(this.database);

      this.permissions = this.originalPermissions;
      this.originalPermissions = Tool.clone(this.permissions);
   }

   onSettingsChanged(changesValid: boolean): void {
      this.dataSourceSettingsValid = changesValid;
   }

   /**
    * Called when user selects a new database type. Set up initial data and downloadUrl.
    * @param newType
    */
   typeChanged(newType: DatasourceDatabaseType, supportToggleCredential: boolean, useCredentialId: boolean): void {
      if(!!this.driverAvailability) {
         const driver: DriverInfo = this.driverAvailability.drivers
            .find(driverInfo => driverInfo.type === newType);

         if(!!driver) {
            if(driver.defaultPort === 0) {
               this.database.network = null;
            }
            else {
               this.database.network = {
                  hostName: "localhost",
                  portNumber: driver.defaultPort
               };
            }
         }

         this.database.tableNameOption = 3;
         this.database.transactionIsolation = -1;
         this.database.changeDefaultDB = false;
         this.database.defaultDatabase = null;
         this.database.ansiJoin = false;
         this.database.info = {
            poolProperties: {},
            customEditMode: false,
            customUrl: null
         };
         this.database.authentication = {
            required: false,
            userName: null,
            password: null,
            useCredentialId: useCredentialId,
            credentialId: null,
            credentialVisible: supportToggleCredential
         };

         if(newType == DatasourceDatabaseType.ACCESS) {
            this.database.info = <CustomDatabaseInfoModel> {
               driverClass: "net.ucanaccess.jdbc.UcanaccessDriver",
               jdbcUrl: "jdbc:ucanaccess://"
            };
         }

         if(DatasourceDatabaseType.CUSTOM === newType) {
            this.database.info.customEditMode = true;
         }
      }
   }

   needSave(): boolean {
      return !this.isDatabaseEqual();
   }

   /**
    * Send request to save the database on the server then close page.
    */
   saveDatabase(): void {
      let params = new HttpParams().set("path", this.model.path);
      let settingModel = Tool.clone(this.model.settings);

      if(!this.needSave()) {
         settingModel.dataSource = null;
      }

      if(this.isPermissionsEqual && !!settingModel.permissions) {
         settingModel.permissions.changed = false;
      }

      this.http.post<ConnectionStatus>("../api/data/databases", settingModel, {params}).pipe(
         catchError((error) => {
            this.showMessage(error.error?.message ? error.error.message : "_#(js:em.data.databases.error)");
            return of(null);
         })
      ).subscribe(connection => {
         if(connection && connection.status === "Duplicate") {
            this.showMessage("_#(js:em.data.databases.duplicateDatabaseName)");
         }
         else if(connection && connection.status === "Duplicate Folder") {
            this.showMessage("_#(js:em.data.databases.duplicateFolder)");
         }
         else {
            this.afterDatabaseSave();
         }
      });
   }

   testDatabase(): void {
      let params = new HttpParams().set("path", this.primaryDatabasePath);
      this.http.post<ConnectionStatus>(TEST_ADDITIONAL, this.database, {params}).pipe(
         catchError((error: HttpErrorResponse) => {
            if(this.showTestMessage) {
               let message = "_#(js:em.data.databases.error)";

               if(error.status === 504) {
                  message = "_#(js:em.data.databases.error.gatewayTimeout)";
               }

               if(!!this.database.cloudError) {
                  message += "\n" + this.database.cloudError;
               }

               this.showMessage(message, "ERROR", true);
            }
            else {
               this.onTested.emit({
                  type: "ERROR",
                  message: "_#(js:em.data.databases.error)"
               });
            }

            return of(null);
         })
      ).subscribe((connection: ConnectionStatus) => {
         this.databaseStatus = connection.status;

         if(this.showTestMessage && !!this.database.cloudError) {
            if(!connection.connected) {
               this.databaseStatus += "\n" + this.database.cloudError;
            }

            this.showMessage(this.databaseStatus, connection.connected ? "OK" : "ERROR", true);
         }
         else {
            this.onTested.emit({
               type: connection.connected ? "OK" : "ERROR",
               message: this.databaseStatus
            });
         }
      });
   }
}
