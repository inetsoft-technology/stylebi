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
import { Component, HostBinding, HostListener, Inject, OnDestroy, ViewEncapsulation } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef, MatDialogContent, MatDialogActions } from "@angular/material/dialog";
import { Observable, Subject, throwError, timer } from "rxjs";
import { catchError, filter, switchMap, take, timeout } from "rxjs/operators";
import { DateTypeFormatter } from "../../../../../../../../shared/util/date-type-formatter";
import { RepositoryEntryType } from "../../../../../../../../shared/data/repository-entry-type.enum";
import { Tool } from "../../../../../../../../shared/util/tool";
import { MessageDialog, MessageDialogType } from "../../../../../common/util/message-dialog";
import { convertToKey } from "../../../../security/users/identity-id";
import { ImportAssetResponse } from "../../model/import-asset-response";
import { RepositoryTreeDataSource } from "../../repository-tree-data-source";
import { RepositoryFlatNode, RepositoryTreeNode } from "../../repository-tree-node";
import { ExportedAssetsModel } from "../exported-assets-model";
import { RequiredAssetModel } from "../required-asset-model";
import { SelectAssetFolderDialogComponent } from "../select-asset-folder-dialog/select-asset-folder-dialog.component";
import { BookmarkConflict } from "../bookmark-conflict";
import { BookmarkConflictResolution } from "../bookmark-conflict-resolution";
import { ImportAssetRequest } from "../import-asset-request";
import { MatProgressBar } from "@angular/material/progress-bar";
import { RequiredAssetListComponent } from "../required-asset-list/required-asset-list.component";
import { SelectedAssetListComponent } from "../selected-asset-list/selected-asset-list.component";
import { MatCheckbox } from "@angular/material/checkbox";
import { MatIconButton, MatButton } from "@angular/material/button";
import { MatInput } from "@angular/material/input";
import { MatIcon } from "@angular/material/icon";
import { FileChooserComponent } from "../../../../../common/util/file-chooser/file-chooser/file-chooser.component";
import { MatFormField, MatLabel, MatSuffix, MatError } from "@angular/material/form-field";
import { MatTable, MatHeaderCellDef, MatCellDef, MatHeaderRowDef, MatRowDef, MatHeaderCell, MatCell, MatHeaderRow, MatRow, MatColumnDef } from "@angular/material/table";
import { MatRadioButton } from "@angular/material/radio";
import { ModalHeaderComponent } from "../../../../../common/util/modal-header/modal-header.component";
import { NgIf } from "@angular/common";

@Component({
    selector: "em-import-asset-dialog",
    templateUrl: "./import-asset-dialog.component.html",
    styleUrls: ["./import-asset-dialog.component.scss"],
    encapsulation: ViewEncapsulation.None,
    imports: [NgIf, ModalHeaderComponent, MatDialogContent, FormsModule, ReactiveFormsModule, MatFormField, MatLabel, FileChooserComponent, MatIcon, MatSuffix, MatError, MatInput, MatIconButton, MatCheckbox, SelectedAssetListComponent, RequiredAssetListComponent, MatProgressBar, MatDialogActions, MatButton, MatTable, MatColumnDef, MatHeaderCellDef, MatCellDef, MatHeaderRowDef, MatRowDef, MatHeaderCell, MatCell, MatHeaderRow, MatRow, MatRadioButton]
})
export class ImportAssetDialogComponent implements OnDestroy {
   @HostBinding("class") hostClass = "import-asset-dialog";
   uploadForm: UntypedFormGroup;
   importForm: UntypedFormGroup;
   uploaded = false;
   showBookmarkConflicts = false;
   bookmarkConflicts: BookmarkConflict[] = [];
   conflictsLoading = false;
   conflictTableData: Array<{kind: "header", viewsheetPath: string} | {kind: "row", conflict: BookmarkConflict}> = [];
   readonly conflictColumns = ["user", "bookmarkName", "existingModified", "importedModified", "keep"];
   isGroupHeader = (_: number, row: any) => row.kind === "header";
   private bookmarkResolutions: BookmarkConflictResolution[] = [];
   private targetNode: RepositoryFlatNode;
   private _selected: RequiredAssetModel[] = [];
   private readonly conflictsRefresh$ = new Subject<HttpParams>();

   get selected(): RequiredAssetModel[] { return this._selected; }

   set selected(value: RequiredAssetModel[]) {
      const old = this._selected;
      this._selected = value;

      if(this.model && this.hasViewsheetChange(old, value)) {
         this.fetchBookmarkConflicts();
      }
   }

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
      this._model = value;

      if(value) {
         this._selected = this.model.dependentAssets.slice();
         this.importForm.get("overwrite").setValue(this.model.overwriting);
      }
      else {
         this._selected = [];
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

      this.conflictsRefresh$
         .pipe(
            switchMap(params =>
               this.http.get<BookmarkConflict[]>(
                  `../api/em/content/repository/import-bookmark-conflicts/${this.model?.importId}`,
                  { params }
               ).pipe(catchError(err => { this.conflictsLoading = false; return throwError(err); }))
            )
         )
         .subscribe(conflicts => {
            this.conflictsLoading = false;
            // Sort conflicts where timestamps differ to the top (they're more actionable).
            this.bookmarkConflicts = (conflicts || []).sort((a, b) => {
               const aSortKey = a.existingModified === a.importedModified ? 1 : 0;
               const bSortKey = b.existingModified === b.importedModified ? 1 : 0;
               return aSortKey - bSortKey;
            });
            this.buildConflictTableData();
            this.bookmarkResolutions = this.bookmarkConflicts.map(c => {
               const existing = this.bookmarkResolutions.find(
                  r => r.viewsheetPath === c.viewsheetPath &&
                       r.user === c.user &&
                       r.bookmarkName === c.bookmarkName);
               return {
                  viewsheetPath: c.viewsheetPath,
                  user: c.user,
                  bookmarkName: c.bookmarkName,
                  keepImported: existing ? existing.keepImported : true
               };
            });
         });
   }

   ngOnDestroy() {
      this.conflictsRefresh$.complete();
      this.clearImportCache();
   }

   private clearImportCache(): void {
      this.http.delete<ExportedAssetsModel>(`../api/em/content/repository/import/${this.model?.importId}`)
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
      if(this.bookmarkConflicts.length > 0) {
         this.showBookmarkConflicts = true;
         return;
      }

      this.doImport([]);
   }

   finishWithResolutions(): void {
      this.doImport(this.bookmarkResolutions.filter(r => !r.keepImported));
   }

   backFromConflicts(): void {
      this.showBookmarkConflicts = false;
   }

   getResolution(conflict: BookmarkConflict): boolean {
      const r = this.bookmarkResolutions.find(
         x => x.viewsheetPath === conflict.viewsheetPath &&
              x.user === conflict.user &&
              x.bookmarkName === conflict.bookmarkName);
      return r ? r.keepImported : true;
   }

   setResolution(conflict: BookmarkConflict, keepImported: boolean): void {
      const r = this.bookmarkResolutions.find(
         x => x.viewsheetPath === conflict.viewsheetPath &&
              x.user === conflict.user &&
              x.bookmarkName === conflict.bookmarkName);
      if(r) {
         r.keepImported = keepImported;
      }
   }

   setAllKeepImported(keepImported: boolean): void {
      this.bookmarkResolutions.forEach(r => r.keepImported = keepImported);
   }

   setAllKeepLatest(): void {
      this.bookmarkConflicts.forEach(c => {
         const r = this.bookmarkResolutions.find(
            x => x.viewsheetPath === c.viewsheetPath &&
                 x.user === c.user &&
                 x.bookmarkName === c.bookmarkName);
         if(r) {
            r.keepImported = c.importedModified >= c.existingModified;
         }
      });
   }

   private buildConflictTableData(): void {
      const map = new Map<string, BookmarkConflict[]>();
      for(const c of this.bookmarkConflicts) {
         const group = map.get(c.viewsheetPath) || [];
         group.push(c);
         map.set(c.viewsheetPath, group);
      }
      const rows: typeof this.conflictTableData = [];
      for(const [viewsheetPath, conflicts] of map.entries()) {
         rows.push({kind: "header", viewsheetPath});
         conflicts.forEach(c => rows.push({kind: "row", conflict: c}));
      }
      this.conflictTableData = rows;
   }

   private doImport(bookmarkResolutions: BookmarkConflictResolution[]): void {
      let targetLocation = this.targetNode?.data;
      this.loading = true;
      const ignoreList = this.model.dependentAssets
         .map((asset) => this.isAssetIgnored(asset) ? asset.index : -1)
         .filter(i => i !== -1)
         .map(i => `${i}`);
      const uri = `../api/em/content/repository/import/${this.model?.importId}`;
      const options = {
         params: new HttpParams()
            .set("overwrite", this.importForm.get("overwrite").value)
            .set("background", "true")
      };

      if(targetLocation) {
         options.params = options.params
            .set("targetLocation", targetLocation.path)
            .set("locationType", targetLocation.type)
            .set("dependenciesApplyTarget", this.importForm.get("dependenciesApplyTarget").value);

         if(targetLocation.owner) {
            options.params = options.params.set("locationUser", convertToKey(targetLocation.owner));
         }
      }

      const requestBody: ImportAssetRequest = { ignoreList, bookmarkResolutions };

      timer(0, 2000) // poll every 2 seconds
         .pipe(
            switchMap(() => this.http.post<ImportAssetResponse>(uri, requestBody, options)),
            filter(response => response.complete),
            take(1),
            timeout(600000) // time out after 10 minutes
         )
         .subscribe(
            response => this.onImportComplete(response),
            err => this.handleImportError(err)
         );
   }

   private fetchBookmarkConflicts(): void {
      const ignoreList = this.model?.dependentAssets
         ?.map((asset) => this.isAssetIgnored(asset) ? asset.index : -1)
         .filter(i => i !== -1)
         .map(i => `${i}`) ?? [];

      let params = new HttpParams();

      for(const item of ignoreList) {
         params = params.append("ignoreList", item);
      }

      const targetLocation = this.targetNode?.data;

      if(targetLocation) {
         params = params
            .set("targetLocation", targetLocation.path)
            .set("locationType", targetLocation.type)
            .set("dependenciesApplyTarget", this.importForm.get("dependenciesApplyTarget").value);

         if(targetLocation.owner) {
            params = params.set("locationUser", convertToKey(targetLocation.owner));
         }
      }

      this.conflictsLoading = true;
      this.conflictsRefresh$.next(params);
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

   private hasViewsheetChange(a: RequiredAssetModel[], b: RequiredAssetModel[]): boolean {
      const vsIndices = (list: RequiredAssetModel[]) =>
         new Set(list.filter(x => x.type === "VIEWSHEET").map(x => x.index));
      const setA = vsIndices(a);
      const setB = vsIndices(b);

      if(setA.size !== setB.size) { return true; }

      for(const i of setA) {
         if(!setB.has(i)) { return true; }
      }

      return false;
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
         const uri = `../api/em/content/repository/update-import-info/${this.model?.importId}`;
         options.params = options.params
            .set("targetLocation", targetLocation.path)
            .set("locationType", targetLocation.type);

         if(targetLocation.owner) {
            options.params = options.params.set("locationUser", convertToKey(targetLocation.owner));
         }

         this.http.get<ExportedAssetsModel>(uri, options).subscribe(result => {
            if(result) {
               this.model = result;
               this.fetchBookmarkConflicts();
            }
         });
      }
      else {
         this.fetchBookmarkConflicts();
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
