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
import { Component, HostListener, Inject, OnDestroy, ViewEncapsulation } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from "@angular/material/dialog";
import { Observable, throwError, timer } from "rxjs";
import { catchError, filter, switchMap, take, timeout } from "rxjs/operators";
import { FeatureFlagValue } from "../../../../../../../../shared/feature-flags/feature-flags.service";
import { DateTypeFormatter } from "../../../../../../../../shared/util/date-type-formatter";
import { Tool } from "../../../../../../../../shared/util/tool";
import { MessageDialog, MessageDialogType } from "../../../../../common/util/message-dialog";
import { convertToKey } from "../../../../security/users/identity-id";
import { ImportAssetResponse } from "../../model/import-asset-response";
import { ExportedAssetsModel } from "../exported-assets-model";
import { RequiredAssetModel } from "../required-asset-model";
import { RepositoryEntryType } from "../../../../../../../../shared/data/repository-entry-type.enum";
import { RepositoryFlatNode, RepositoryTreeNode } from "../../repository-tree-node";
import { SelectAssetFolderDialogComponent } from "../select-asset-folder-dialog/select-asset-folder-dialog.component";
import { RepositoryTreeDataSource } from "../../repository-tree-data-source";

@Component({
   selector: "em-import-asset-dialog",
   templateUrl: "./import-asset-dialog.component.html",
   styleUrls: ["./import-asset-dialog.component.scss"],
   encapsulation: ViewEncapsulation.None,
   host: { // eslint-disable-line @angular-eslint/no-host-metadata-property
      "class": "import-asset-dialog"
   }
})
export class ImportAssetDialogComponent implements OnDestroy {
   uploadForm: UntypedFormGroup;
   importForm: UntypedFormGroup;
   selected: RequiredAssetModel[] = [];
   uploaded = false;
   private targetNode: RepositoryFlatNode;
   readonly FeatureFlagValue = FeatureFlagValue;

   get loading(): boolean {
      return this._loading;
   }

   set loading(value: boolean) {
      this._loading = value;

      if(value) {
         this.uploadForm.get("file").disable();
      }
      else {
         this.uploadForm.get("file").enable();
      }
   }

   private _loading = false;

   get model(): ExportedAssetsModel {
      return this._model;
   }

   set model(value: ExportedAssetsModel) {
      let oldModel = this._model;
      this._model = value;

      if(value) {
         this.selected = this.model.dependentAssets.slice();
         this.importForm.get("overwrite").setValue(this.model.overwriting);
      }
      else {
         this.selected = [];
      }
   }

   private updateDependenciesApplyTargetDefaultStatus(): void {
      let hasDependencies = this.model?.dependentAssets?.length > 0;

      if(hasDependencies) {
         this.importForm.get("dependenciesApplyTarget").enable();
         this.importForm.get("dependenciesApplyTarget").setValue(this.getDependenciesApplyTargetDefault(this.model));
      }
      else {
         this.importForm.get("dependenciesApplyTarget").disable();
      }
   }

   get targetFolderLabel(): string {
      let targetLocation = this.targetNode?.data;

      if(!targetLocation || targetLocation.path == "/") {
         return "/";
      }

      let path = !!targetLocation ? targetLocation?.path : "/";

      if(targetLocation.type == RepositoryEntryType.USER || !!targetLocation.owner) {
         let userFolderLabel;

         if(targetLocation.path.startsWith(Tool.MY_REPORTS)) {
            if(targetLocation.path == Tool.MY_REPORTS) {
               return "_#(js:User Private Assets)/" + targetLocation.owner + "/";
            }
            else {
               userFolderLabel = targetLocation.path.substring(Tool.MY_REPORTS.length + 1) + "/";
               return "_#(js:User Private Assets)/" + targetLocation.owner + "/" + userFolderLabel;
            }
         }
      }

      return (path.startsWith("/") ? path : "/" + path) + "/";
   }

   get selectedTargetFolder(): boolean {
      return !!this.targetNode?.data && this.targetNode?.data.path != "/";
   }

   get dependenciesApplyTarget(): boolean {
      return !!this.importForm.get("dependenciesApplyTarget").value;
   }

   get targetFolderVisible(): boolean {
      if(!this.model) {
         return false;
      }

      return this.model.selectedEntities.some(entry => this.entrySupportImportToTarget(entry.type)) ||
         this.model.dependentAssets.some(asset => this.assetSupportImportToTarget(asset.type));
   }

   private _model: ExportedAssetsModel;

   constructor(private http: HttpClient, private dialog: MatDialog,
      private dialogRef: MatDialogRef<ImportAssetDialogComponent>,
      @Inject(MAT_DIALOG_DATA) data: any, fb: UntypedFormBuilder) {
      this.uploadForm = fb.group({
         file: [null, Validators.required]
      });
      this.importForm = fb.group({
         overwrite: [true],
         dependenciesApplyTarget: [true]
      });
   }

   ngOnDestroy() {
      this.clearImportCache();
   }

   private clearImportCache(): void {
      this.http.get<ExportedAssetsModel>("../api/em/content/repository/import/clear-cache")
         .subscribe();
   }

   upload(): void {
      this.loading = true;
      const file = this.uploadForm.get("file").value[0];
      this.http.post<ExportedAssetsModel>("../api/em/content/repository/set-jar-file", file)
         .pipe(catchError(err => this.handleUploadError(err)))
         .subscribe(info => {
            this.updateAssetInfo(info);
            this.updateDependenciesApplyTargetDefaultStatus();
         });
   }

   back(): void {
      this.clearImportCache()
      this.uploaded = false;
      this.model = null;
   }

   finish(): void {
      let targetLocation = this.targetNode?.data;
      this.loading = true;
      const ignoreList = this.model.dependentAssets
         .map((asset) => this.isAssetIgnored(asset) ? asset.index : -1)
         .filter(i => i !== -1)
         .map(i => `${i}`);
      const uri = `../api/em/content/repository/import/${this.importForm.get("overwrite").value}`;
      const importId = Tool.generateRandomUUID();
      const options = { params: new HttpParams().set("importId", importId) };

      if(targetLocation) {
         options.params = options.params
            .set("targetLocation", targetLocation.path)
            .set("locationType", targetLocation.type)
            .set("dependenciesApplyTarget", this.importForm.get("dependenciesApplyTarget").value);

         if(targetLocation.owner) {
            options.params = options.params.set("locationUser", convertToKey(targetLocation.owner));
         }
      }

      timer(0, 2000) // poll every 2 seconds
         .pipe(
            switchMap(() => this.http.post<ImportAssetResponse>(uri, ignoreList, options)),
            filter(response => response.complete),
            take(1),
            timeout(600000) // time out after 10 minutes
         )
         .subscribe(
            response => this.onImportComplete(response),
            err => this.handleImportError(err)
         );
   }

   @HostListener("window:keyup.esc", [])
   onKeyUp(): void {
      this.cancel();
   }

   cancel(): void {
      this.dialogRef.close(null);
   }

   private isAssetIgnored(asset: RequiredAssetModel): boolean {
      return !this.selected.some(s =>
         s.name === asset.name && s.type === asset.type && s.user === asset.user);
   }

   private updateAssetInfo(info: ExportedAssetsModel): void {
      if(info && info.newerVersion) {
         const ref = this.dialog.open(MessageDialog, {
            data: {
               title: "_#(js:Warning)",
               content: "_#(js:partial.deploy.version.newer)",
               type: MessageDialogType.CONFIRMATION
            }
         });
         ref.afterClosed().subscribe((proceed: boolean) => {
            if(!proceed) {
               this.cancel();
               this.uploaded = true;
               this.loading = false;
            }
            else {
               this.model = info;
               this.updateModelGetImportedPath();
               this.uploaded = true;
               this.loading = false;
            }
         });
      }
      else {
         this.model = info;
         this.updateModelGetImportedPath();
         this.uploaded = true;
         this.loading = false;
      }
   }

   private onImportComplete(response: ImportAssetResponse): void {
      this.loading = false;
      let type: MessageDialogType;
      let title: string;
      let content: string;

      if(response.failedAssets.length > 0) {
         // Partially failed due to permission denial
         type = MessageDialogType.WARNING;
         title = "_#(js:Warning)";
         content = "_#(js:em.import.fail.fileList) " + response.failedAssets.join(", ");
      }
      else if(response.ignoreUserAssets.length > 0) {
         type = MessageDialogType.WARNING;
         title = "_#(js:Warning)";
         content = "_#(js:em.import.ignoreUserAssets) " + response.ignoreUserAssets.join(", ");
      }
      else {
         if(response.failed) {
            // Failed due to other reason
            type = MessageDialogType.ERROR;
            title = "_#(js:Error)";
            content = "_#(js:em.import.fail)";
         }
         else {
            type = MessageDialogType.INFO;
            title = "_#(js:Success)";
            content = "_#(js:em.import.success) _#(js:em.import.restart)";
         }
      }

      this.dialog.open(MessageDialog, { data: { title, content, type } })
         .afterClosed().subscribe(() => this.dialogRef.close(true));
   }

   private handleUploadError(error: HttpErrorResponse): Observable<ExportedAssetsModel> {
      this.loading = false;
      let message = error.error && error.error.message ? error.error.message :
         "_#(js:repository.importAsset.missingFileErr)";

      if(error.error instanceof ProgressEvent && error.error.loaded === 0) {
         message = "_#(js:repository.importAsset.missingFileErr)";
      }

      this.dialog.open(MessageDialog, {
         data: {
            title: "_#(js:Error)",
            content: message,
            type: MessageDialogType.ERROR
         }
      });
      return throwError(error);
   }

   private handleImportError(error: HttpErrorResponse): Observable<ImportAssetResponse> {
      this.loading = false;
      this.dialog.open(MessageDialog, {
         data: {
            title: "_#(js:Error)",
            content: error.error.message,
            type: MessageDialogType.ERROR
         }
      });
      return throwError(error);
   }

   getDateLabel(): string {
      if(this.model != null && this.model.deploymentDate != null) {
         return DateTypeFormatter.getLocalTime(this.model.deploymentDate, this.model.dateFormat);
      }

      return "";
   }

   openSelectLocation() {
      const dialogRef = this.dialog.open(SelectAssetFolderDialogComponent, {
         role: "dialog",
         width: "750px",
         maxWidth: "100%",
         maxHeight: "100%",
         disableClose: true
      });

      dialogRef.componentInstance.defaultSelectedNode = this.targetNode;
      dialogRef.componentInstance.rootNodesTypeFun = this.getRootTypesFun();
      dialogRef.componentInstance.rootChildrenFilter = this.getLocationNodeFilter();

      dialogRef.afterClosed().subscribe((res) => {
         if(res) {
            this.targetNode = res;
            this.updateModelGetImportedPath();
         }
      });
   }

   private updateModelGetImportedPath(): void {
      if(this.targetNode?.data) {
         const options = { params: new HttpParams() };
         let targetLocation = this.targetNode?.data;
         const uri = "../api/em/content/repository/update-import-info";
         options.params = options.params
            .set("targetLocation", targetLocation.path)
            .set("locationType", targetLocation.type);

         if(targetLocation.owner) {
            options.params = options.params.set("locationUser", convertToKey(targetLocation.owner));
         }

         this.http.get<ExportedAssetsModel>(uri, options).subscribe(result => {
            if(result) {
               this.model = result;
            }
         });
      }
   }

   private entrySupportImportToTarget(type: number): boolean {
      return type == RepositoryEntryType.VIEWSHEET || type == RepositoryEntryType.WORKSHEET ||
         type == RepositoryEntryType.DATA_SOURCE;
   }

   private assetSupportImportToTarget(type: string): boolean {
      return type == "WORKSHEET" || type == "VIEWSHEET" || type == "XDATASOURCE";
   }

   private getLocationNodeFilter(): (node: RepositoryTreeNode) => boolean {
      return (data: RepositoryTreeNode) => {
         if(!data) {
            return false;
         }

         let result = data.type != RepositoryEntryType.TRASHCAN_FOLDER
            && data.type != RepositoryEntryType.RECYCLEBIN_FOLDER
            && data.type != RepositoryEntryType.LIBRARY_FOLDER
            && (data.type & RepositoryEntryType.DASHBOARD_FOLDER) != RepositoryEntryType.DASHBOARD_FOLDER
            && data.path != "Users' Dashboards"
            && data.path != "Schedule Tasks"
            && (data.type & RepositoryEntryType.FOLDER) == RepositoryEntryType.FOLDER
            && (data.type & RepositoryEntryType.DATA_SOURCE) != RepositoryEntryType.DATA_SOURCE
            && !data.builtIn && !(!!data.path && data.path.indexOf(Tool.BUILT_IN_ADMIN_REPORTS) === 0);

         if((data.type & RepositoryEntryType.USER_FOLDER) == RepositoryEntryType.USER_FOLDER) {
            result = result &&
               (data.type & RepositoryEntryType.WORKSHEET_FOLDER) != RepositoryEntryType.WORKSHEET_FOLDER;
         }

         return result;
      };
   }

   private getRootTypesFun(): (dataSource: RepositoryTreeDataSource) => RepositoryEntryType[] {
      return (dataSource: RepositoryTreeDataSource) => {
         if(!dataSource) {
            return [];
         }

         let allWs = true;
         let allDataSource = true;

         this.model.selectedEntities.forEach(entry => {
            if(!this.entrySupportImportToTarget(entry.type)) {
               return;
            }

            if(entry.type != RepositoryEntryType.WORKSHEET) {
               allWs = false;
            }

            if(!this.isDatasourceItemEntry(entry.type)) {
               allDataSource = false;
            }
         });

         this.model.dependentAssets.forEach(asset => {
            if(!this.assetSupportImportToTarget(asset.type)) {
               return;
            }

            if(asset.type != "WORKSHEET") {
               allWs = false;
            }

            if(!this.isDatasourceItemAsset(asset.type)) {
               allDataSource = false;
            }
         });

         let hasRepositoryPermission =
            dataSource.data.some(node => node?.data?.type == RepositoryEntryType.REPOSITORY_FOLDER);
         let types: RepositoryEntryType[] = [];

         if(allWs || !hasRepositoryPermission) {
            types.push(RepositoryEntryType.WORKSHEET_FOLDER);
         }
         else {
            types.push(RepositoryEntryType.REPOSITORY_FOLDER);
         }

         if(allDataSource) {
            types.push(RepositoryEntryType.DATA_SOURCE_FOLDER);
         }

         return types;
      };
   }

   private isDatasourceItemEntry(entryType: number): boolean {
      return (entryType & RepositoryEntryType.DATA_SOURCE) == RepositoryEntryType.DATA_SOURCE ||
         entryType == RepositoryEntryType.DATA_MODEL || entryType == RepositoryEntryType.PARTITION ||
         entryType == RepositoryEntryType.LOGIC_MODEL || entryType == RepositoryEntryType.QUERY;
   }

   private isDatasourceItemAsset(assetType: string): boolean {
      return assetType == "XDATASOURCE" || assetType == "XPARTITION" || assetType == "XLOGICALMODEL" ||
         assetType == "XQUERY";
   }

   private getDependenciesApplyTargetDefault(info: ExportedAssetsModel): boolean {
      if(info) {
         let dependentAssets = info.dependentAssets;

         if(!dependentAssets || !info.selectedEntities) {
            return true;
         }

         let dependencyMap: Map<string, string[]> = new Map<string, string[]>();

         dependentAssets.forEach(asset => {
            let requiredBy = asset.requiredBy;

            if(!requiredBy) {
               return;
            }

            let requiredByAssets = requiredBy.split(", ");

            requiredByAssets.forEach(r => {
               let dependencies = dependencyMap.get(r);

               if(!dependencies) {
                  dependencies = [];
               }

               dependencies.push(asset.name);
               dependencyMap.set(r, dependencies);
            });
         });

         let entryDependencies = null;

         for(let entry of info.selectedEntities) {
            if(!entryDependencies) {
               entryDependencies = dependencyMap.get(entry.path);
            }
            else if(!Tool.isEquals(entryDependencies, dependencyMap.get(entry.path))) {
               return true;
            }
         }

         return false;
      }

      return true;
   }
}
