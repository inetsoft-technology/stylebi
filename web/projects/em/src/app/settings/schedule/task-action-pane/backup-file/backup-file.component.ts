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
import { HttpClient, HttpErrorResponse } from "@angular/common/http";
import { Component, EventEmitter, Input, OnDestroy, Output } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { DomSanitizer } from "@angular/platform-browser";
import { Observable, Subject, Subscription, throwError, timer } from "rxjs";
import { catchError, first, map, scan, switchMap, takeUntil, tap } from "rxjs/operators";
import { CommonKVModel } from "../../../../../../../portal/src/app/common/data/common-kv-model";
import { RepositoryEntryType } from "../../../../../../../shared/data/repository-entry-type.enum";
import { FeatureFlagValue } from "../../../../../../../shared/feature-flags/feature-flags.service";
import { RepositoryEditorModel } from "../../../../../../../shared/util/model/repository-editor-model";
import { Tool } from "../../../../../../../shared/util/tool";
import { MessageDialog, MessageDialogType } from "../../../../common/util/message-dialog";
import { FlatTreeNode } from "../../../../common/util/tree/flat-tree-model";
import { FlatTreeSelectNodeEvent } from "../../../../common/util/tree/flat-tree-view.component";
import { ContentRepositoryService } from "../../../content/repository/content-repository-page/content-repository.service";
import { ExportAssetsService, ExportStatusModel } from "../../../content/repository/import-export/export-assets.service";
import {
   SelectedAssetModel,
   SelectedAssetModelList
} from "../../../content/repository/import-export/selected-asset-model";
import { RepositoryTreeDataSource } from "../../../content/repository/repository-tree-data-source";
import { RepositoryFlatNode, RepositoryTreeNode } from "../../../content/repository/repository-tree-node";
import { ServerPathInfoModel } from "../../../../../../../portal/src/app/vsobjects/model/server-path-info-model";
import { convertToKey, removeOrganization } from "../../../security/users/identity-id";

export interface BackupPathsSave {
   valid: boolean;
   enabled: boolean;
   path: string;
   ftp?: boolean;
   useCredential?: boolean;
   secretId?: string;
   username?: string;
   password?: string;
   assets: SelectedAssetModel[];
}

@Component({
   selector: "em-backup-file",
   templateUrl: "./backup-file.component.html",
   styleUrls: ["./backup-file.component.scss"],
   providers: [
      RepositoryTreeDataSource
   ]
})
export class BackupFileComponent implements OnDestroy {
   @Input() enabled = true;
   @Input() set path(p: string) {
      if(!this._serverPath) {
         this.backupForm.get("path").setValue(p, { emitEvent: false });
      }
   }

   @Input() set serverPath(p: ServerPathInfoModel) {
      this._serverPath = p;

      if(!!p) {
         this.backupForm.get("path").setValue(p.path, { emitEvent: false });
         this.backupForm.get("ftp").setValue(p.ftp, { emitEvent: false });
         this.backupForm.get("useCredential").setValue(p.useCredential, { emitEvent: false });
         this.backupForm.get("secretId").setValue(p.secretId, { emitEvent: false });
         this.backupForm.get("username").setValue(p.username, { emitEvent: false });
         this.backupForm.get("password").setValue(p.password, { emitEvent: false });
         this.toggleFTP();
      }
   }

   @Input() selectedEntities: SelectedAssetModel[] = [];
   @Input() cloudSecrets: boolean;
   @Output() backupPathsChanged = new EventEmitter<BackupPathsSave>();

   FeatureFlagValue = FeatureFlagValue;
   _serverPath: ServerPathInfoModel;
   private destroy$ = new Subject<void>();
   backupForm: UntypedFormGroup;
   entitySelection: SelectedAssetModel[] = [];
   exportableNodes: FlatTreeNode<RepositoryTreeNode>[];
   exportableFolders: FlatTreeNode<RepositoryTreeNode>[];
   selectedUserFolders: FlatTreeNode<RepositoryTreeNode>[];
   private selectSubscription = Subscription.EMPTY;
   prompt = "";

   constructor(public dataSource: RepositoryTreeDataSource,
      private snackBar: MatSnackBar,
      private fb: UntypedFormBuilder,
      private dialog: MatDialog,
      private service: ContentRepositoryService,
      private exportService: ExportAssetsService,
      private http: HttpClient, sanitizer: DomSanitizer) {
      this.backupForm = this.fb.group({
         path: ["", [Validators.required]],
         ftp: [false],
         useCredential: [false],
         secretId: [""],
         username: [""],
         password: [""]
      });

      this.backupForm.get("path").valueChanges.subscribe(() => this.fireBackupChanged());
      this.backupForm.get("ftp").valueChanges.subscribe(() => this.fireBackupChanged());
      this.backupForm.get("useCredential").valueChanges.subscribe(() => this.fireBackupChanged());
      this.backupForm.get("secretId").valueChanges.subscribe(() => this.fireBackupChanged());
      this.backupForm.get("username").valueChanges.subscribe(() => this.fireBackupChanged());
      this.backupForm.get("password").valueChanges.subscribe(() => this.fireBackupChanged());
      this.backupForm.get("useCredential").disable();
      this.backupForm.get("secretId").disable();
      this.backupForm.get("username").disable();
      this.backupForm.get("password").disable();

      service.changes.pipe(
         takeUntil(this.destroy$),
         catchError((warning: HttpErrorResponse) => {
            const message = warning.error ? warning.error.message : warning.message;
            console.error("Failed to get repository changes: ", warning);
            this.snackBar.open(message, null, { duration: Tool.SNACKBAR_DURATION });
            return throwError(warning);
         })
      ).subscribe(() => this.refreshTree());

      this.selectSubscription = service.selectedNodeChanges.subscribe(() => {
         this.exportableNodes = this.selectedNodes.filter((node) => {
            const type = node.data.type;

            //Additional data source connections are not exportable
            if((type & RepositoryEntryType.DATA_SOURCE) == RepositoryEntryType.DATA_SOURCE) {
               return (node.data.type & RepositoryEntryType.FOLDER) == RepositoryEntryType.FOLDER;
            }

            if((type & RepositoryEntryType.PARTITION) == RepositoryEntryType.PARTITION ||
               (type & RepositoryEntryType.LOGIC_MODEL) == RepositoryEntryType.LOGIC_MODEL) {
               return true;
            }

            return (type & RepositoryEntryType.FOLDER) != RepositoryEntryType.FOLDER &&
               (type & RepositoryEntryType.TRASHCAN) != RepositoryEntryType.TRASHCAN &&
               node.data.path.indexOf(Tool.RECYCLE_BIN) != 0 &&
               node.data.path.indexOf(Tool.BUILT_IN_ADMIN_REPORTS) != 0;
         });
         this.exportableFolders = this.selectedNodes.filter((node) => {
            return (node.data.type & RepositoryEntryType.FOLDER) == RepositoryEntryType.FOLDER &&
               (node.data.type & RepositoryEntryType.TRASHCAN_FOLDER) != RepositoryEntryType.TRASHCAN_FOLDER &&
               !(node.data.label == Tool.RECYCLE_BIN && node.data.path == "/") &&
               (node.data.path.indexOf(Tool.RECYCLE_BIN) != 0) &&
               node.data.path.indexOf(Tool.BUILT_IN_ADMIN_REPORTS) != 0 &&
               !!node.data.children && node.data.children.length != 0;
         });
         this.selectedUserFolders = this.selectedNodes
            .filter((node) => node.data.type == RepositoryEntryType.USER_FOLDER);
      });

      this.dataSource.dataSubject.subscribe(() => {
         this.dataSource.data = this.service.hideTrash(this.dataSource.data);
      });
   }

   ngOnDestroy() {
      this.selectSubscription.unsubscribe();
      this.destroy$.next();
   }

   public get selectedNodes(): RepositoryFlatNode[] {
      return this.service.selectedNodes;
   }

   public get selectedNodesObs(): Observable<RepositoryFlatNode[]> {
      return this.service.selectedNodeChanges;
   }

   public selectNode(evt: FlatTreeSelectNodeEvent) {
      const node = <RepositoryFlatNode>evt.node;

      if(evt.event.ctrlKey && this.service.selectedNodes.length > 0) {
         const selectedNodes = this.service.selectedNodes;
         selectedNodes.push(node);
         this.service.selectedNodes = selectedNodes;
      }
      else {
         this.service.selectedNodes = [node];
      }
   }

   fireBackupChanged() {
      this.backupForm.updateValueAndValidity();
      this.backupPathsChanged.emit({
         valid: !this.enabled || this.backupForm.valid &&
            this.selectedEntities && this.selectedEntities.length > 0,
         enabled: this.enabled,
         path: this.backupForm.get("path").value,
         ftp: this.backupForm.get("ftp").value,
         useCredential: this.backupForm.get("useCredential").value,
         secretId: this.backupForm.get("secretId").value?.trim(),
         username: this.backupForm.get("username").value,
         password: this.backupForm.get("password").value,
         assets: this.selectedEntities
      });
   }

   private refreshTree(editorModel?: RepositoryEditorModel) {
      this.dataSource.refresh().subscribe((newNodes) => {
         // refresh tree and re-select selected nodes that still match
         const selected = this.service.selectedNodes;
         this.service.clearSelectedNodes();

         while(selected.length > 0) {
            const node = selected.pop();
            const newNode = newNodes.find((n) => node.equals(n));

            if(newNode != null) {
               this.service.selectNode(newNode);
            }
         }
      });
   }

   add() {
      const assetEntities = this.exportableNodes
         .filter((node) =>
            !this.selectedEntities.some((entity) =>
               entity.path == node.data.path && this.isSameType(entity.type, node.data.type)))
         .map((node) => <SelectedAssetModel>{
            label: this.getExportableLabel(node.data),
            path: node.data.path ? node.data.path : node.data.label,
            type: node.data.type,
            typeName: "",
            typeLabel: "",
            user: node.data.owner,
            description: node.data.description,
            icon: node.data.icon
         });

      this.filterEntities(assetEntities);
   }

   isSameType(entityType: number, nodeType: number): boolean {
      return (entityType & nodeType) === entityType || (nodeType & entityType) === nodeType;
   }

   getExportableLabel(node: RepositoryTreeNode): string {
      if(node.type === RepositoryEntryType.SCHEDULE_TASK || !node.path) {
         return removeOrganization(node.path, node.owner.orgID);
      }
      else {
         return node.path;
      }
   }

   addChildren() {
      let users: CommonKVModel<string, string>[] = this.exportService.getUsers(this.selectedUserFolders);

      if(users.length == 0) {
         this.addEntities();
      }
      else {
         this.exportService.loadUserNode(users).subscribe((model) => {
            this.exportService.updateUserNodes(this.selectedNodes, model.nodes);
            this.service.selectedNodes = this.selectedNodes;
            this.addEntities();
         });
      }
   }

   private addEntities() {
      const assets: SelectedAssetModel[] = [];

      for(let i = 0; i < this.exportableFolders.length; i++) {
         const folderData = this.exportableFolders[i].data;

         if(folderData.children.length == 0 &&
            this.exportableNodes.some((exportableNode) => exportableNode.data === folderData) &&
            !this.selectedEntities.some((entity) => entity.path == folderData.path)) {
            assets.push(<SelectedAssetModel>{
               path: folderData.path,
               type: folderData.type,
               typeName: "",
               typeLabel: "",
               user: folderData.owner,
               description: folderData.description
            });
            continue;
         }

         const queue: RepositoryTreeNode[] = [folderData];
         this.addChildNode(queue, assets);
      }

      this.filterEntities(assets);
   }

   private filterEntities(assets: SelectedAssetModel[]) {
      const uri = "../api/em/content/repository/export/check-permission";
      const statusUri = "../api/em/content/repository/export/check-permission/status";
      const valueUri = "../api/em/content/repository/export/check-permission/value";
      const data = { selectedAssets: assets };

      return this.http.post(uri, data)
         .pipe(
            catchError(err => this.handleCheckPermissionError(err)),
            switchMap(() => this.pollForStatus(statusUri, 1500, 1000)),
            switchMap(() => this.http.get<SelectedAssetModelList>(valueUri)),
            map(result => <SelectedAssetModelList>{
               selectedAssets: assets,
               allowedAssets: result.selectedAssets
            })
         )
         .subscribe((result) => {
            const deniedAssets: SelectedAssetModel[] = assets
               .filter((asset) => result && !result.allowedAssets
                  .some((allowedAsset) => allowedAsset.path === asset.path &&
                     allowedAsset.type === asset.type));

            if(deniedAssets.length > 0) {
               this.prompt = "_#(js:em.export.nopermission): " + deniedAssets
                  .map((asset) => asset.path).join(", ");
            }

            this.selectedEntities = !!result ? this.selectedEntities.concat(result.selectedAssets)
               : this.selectedEntities;
            this.fireBackupChanged();
         });
   }

   private pollForStatus(statusUri: string, pollInterval: number, maxAttempts: number): Observable<ExportStatusModel> {
      const checkAttempts: (attempts: number) => void = attempts => {
         if(attempts > maxAttempts) {
            throw new Error("Maximum number of attempts exceeded");
         }
      };

      return timer(0, pollInterval)
         .pipe(
            scan(attempts => ++attempts, 0),
            tap(attempts => checkAttempts(attempts)),
            switchMap(() => this.http.get<ExportStatusModel>(statusUri)),
            first(status => status.ready)
         );
   }

   private handleCheckPermissionError(error: HttpErrorResponse): Observable<SelectedAssetModelList> {
      this.dialog.open(MessageDialog, {
         data: {
            title: "_#(js:Error)",
            content: error.error.message,
            type: MessageDialogType.ERROR
         }
      });
      return throwError(error);
   }

   addChildNode(queue: RepositoryTreeNode[], assets: SelectedAssetModel[]) {
      while(queue.length > 0) {
         const node = queue.shift();

         if(node.path.indexOf(Tool.BUILT_IN_ADMIN_REPORTS) != -1) {
            continue;
         }

         let type = node.type;
         let path = node.path ? node.path : node.label;
         let label = this.getExportableLabel(node);

         if((type & RepositoryEntryType.FOLDER) == RepositoryEntryType.FOLDER && node.children.length > 0) {
            node.children.forEach(child => queue.push(child))
         }

         if(!this.selectedEntities.some((entity) => entity.path == path)) {
            // additional datasource connection should not be exported
            // cube models should not be exported, they are implicitly dependencies of the XMLA data source
            if(type == RepositoryEntryType.DATA_SOURCE || type == RepositoryEntryType.CUBE) {
               continue;
            }

            if(node.label !== "_#(js:Data Model)" &&
               ((type & RepositoryEntryType.FOLDER) != RepositoryEntryType.FOLDER ||
                  (type & RepositoryEntryType.DATA_SOURCE) == RepositoryEntryType.DATA_SOURCE ||
                  (type & RepositoryEntryType.LOGIC_MODEL) == RepositoryEntryType.LOGIC_MODEL ||
                  (type & RepositoryEntryType.PARTITION) == RepositoryEntryType.PARTITION)) {
               assets.push(<SelectedAssetModel>{
                  label: label,
                  path: path,
                  type: type,
                  typeName: "",
                  typeLabel: "",
                  user: node.owner,
                  description: node.description,
                  icon: node.icon
               });
            }
         }
      }
   }

   selectEntity(entity: SelectedAssetModel, event: MouseEvent) {
      if(event.ctrlKey) {
         this.entitySelection.push(entity);
      }
      else {
         this.entitySelection = [entity];
      }
   }

   remove() {
      let backupChanged = false;

      for(let entity of this.entitySelection) {
         const idx = this.selectedEntities.indexOf(entity);

         if(idx != -1) {
            this.selectedEntities.splice(idx, 1);

            backupChanged = true;
         }
      }

      if(backupChanged) {
         this.entitySelection = [];
         this.fireBackupChanged();
      }
   }

   removeAll() {
      this.entitySelection = [];
      this.selectedEntities = [];
      this.fireBackupChanged();
   }

   get entrySelected(): boolean {
      return this.exportableNodes && this.exportableNodes.length > 0;
   }

   get folderSelected(): boolean {
      return (!!this.exportableFolders && this.exportableFolders.length > 0) ||
         (!!this.selectedUserFolders && this.selectedUserFolders.length > 0);
   }

   getIcon(asset: SelectedAssetModel): string {
      const type: number = asset.type;

      if(asset.icon) {
         return asset.icon;
      }

      switch(type) {
         case RepositoryEntryType.VIEWSHEET:
            return "viewsheet-icon";
         case RepositoryEntryType.WORKSHEET:
            return "worksheet-icon";
         case RepositoryEntryType.TRASHCAN:
            return "trash-icon";
         case RepositoryEntryType.BEAN:
            return "report-bean-icon";
         case RepositoryEntryType.SCRIPT:
            return "javascript-icon";
         case RepositoryEntryType.META_TEMPLATE:
            return "report-meta-icon";
         case RepositoryEntryType.PARAMETER_SHEET:
            return "report-param-only-icon";
         case RepositoryEntryType.TABLE_STYLE:
            return "style-icon";
         case RepositoryEntryType.QUERY:
            return "db-table-icon";
         case RepositoryEntryType.LOGIC_MODEL:
            return "logical-model-icon";
         case RepositoryEntryType.PARTITION:
            return "partition-icon";
         case RepositoryEntryType.VPM:
            return "vpm-icon";
         case RepositoryEntryType.DATA_SOURCE:
            return "database-icon";
         case RepositoryEntryType.DASHBOARD:
            return "viewsheet-book-icon";
         case RepositoryEntryType.SCHEDULE_TASK:
            return "datetime-field-icon";
         default:
            return null;
      }
   }

   toggleFTP() {
      if(this.backupForm.get("ftp").value) {
         this.backupForm.get("useCredential").enable({ emitEvent: false });
         this.backupForm.get("secretId").enable({ emitEvent: false });
         this.backupForm.get("username").enable({ emitEvent: false });
         this.backupForm.get("password").enable({ emitEvent: false });
      }
      else {
         this.backupForm.get("useCredential").disable({ emitEvent: false });
         this.backupForm.get("secretId").disable({ emitEvent: false });
         this.backupForm.get("username").disable({ emitEvent: false });
         this.backupForm.get("password").disable({ emitEvent: false });
      }
   }
}
