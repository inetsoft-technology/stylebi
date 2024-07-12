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
import {HttpClient, HttpErrorResponse} from "@angular/common/http";
import {Component, HostListener, Inject, OnInit, ViewEncapsulation} from "@angular/core";
import {UntypedFormBuilder, UntypedFormGroup, Validators} from "@angular/forms";
import {MAT_DIALOG_DATA, MatDialog, MatDialogRef} from "@angular/material/dialog";
import {Observable, throwError} from "rxjs";
import {catchError} from "rxjs/operators";
import {DownloadService} from "../../../../../../../../shared/download/download.service";
import {FormValidators} from "../../../../../../../../shared/util/form-validators";
import {MessageDialog, MessageDialogType} from "../../../../../common/util/message-dialog";
import {FlatTreeNode} from "../../../../../common/util/tree/flat-tree-model";
import {RepositoryTreeNode} from "../../repository-tree-node";
import {ExportAssetsService} from "../export-assets.service";
import {RequiredAssetModel} from "../required-asset-model";
import {SelectAssetsDialogComponent} from "../select-assets-dialog/select-assets-dialog.component";
import {SelectedAssetModel} from "../selected-asset-model";
import {CommonKVModel} from "../../../../../../../../portal/src/app/common/data/common-kv-model";

export interface ExportAssetDialogData {
   selectedNodes: FlatTreeNode<RepositoryTreeNode>[];
}

@Component({
   selector: "em-export-asset-dialog",
   templateUrl: "./export-asset-dialog.component.html",
   styleUrls: ["./export-asset-dialog.component.scss"],
   encapsulation: ViewEncapsulation.None,
   host: { // eslint-disable-line @angular-eslint/no-host-metadata-property
      "class": "export-asset-dialog"
   }
})
export class ExportAssetDialogComponent implements OnInit {
   entities: SelectedAssetModel[] = [];
   selectedEntities: SelectedAssetModel[] = [];
   dependencies: RequiredAssetModel[] = [];
   selectedDependencies: RequiredAssetModel[] = [];

   form: UntypedFormGroup;
   loading = false;

   constructor(private http: HttpClient, private downloadService: DownloadService,
               private dialog: MatDialog, private dialogRef: MatDialogRef<ExportAssetDialogComponent>,
               @Inject(MAT_DIALOG_DATA) data: ExportAssetDialogData, fb: UntypedFormBuilder,
               private exportAssetsService: ExportAssetsService)
   {
      if(!!data.selectedNodes && data.selectedNodes.length > 0) {
         this.loading = true;
         this.initEntities(data.selectedNodes);
      }

      this.form = fb.group({
         fileName: [null, [Validators.required, FormValidators.isValidWindowsFileName]],
         overwrite: [true]
      });
   }

   ngOnInit() {
   }

   selectAssets(): void {
      this.dialog.open(SelectAssetsDialogComponent, {
         role: "dialog",
         width: "750px",
         maxWidth: "100%",
         maxHeight: "100%",
         disableClose: true,
         data: {
            selectedAssets: this.entities.slice()
         }
      }).afterClosed().subscribe(result => {
         if(result) {
            this.entities = result;
            this.selectedEntities = this.entities.filter(e => !!this.selectedEntities.find(s => this.entitiesEqual(e, s)));
            this.updateDependencies();
         }
      });
   }

   removeAssets(): void {
      const newEntities = this.entities.slice();
      this.selectedEntities.forEach(entity => {
         const index = newEntities.findIndex(e => this.entitiesEqual(e, entity));

         if(index !== -1) {
            newEntities.splice(index, 1);
         }
      });
      this.entities = newEntities;
      this.selectedEntities = [];
      this.updateDependencies();
   }

   finish(): void {
      this.loading = true;
      this.exportAssetsService.createExport(
         this.entities, this.selectedDependencies, !!this.form.get("overwrite").value,
         this.form.get("fileName").value, false)
         .subscribe(() => this.downloadExport());
   }

   @HostListener("window:keyup.esc", [])
   onKeyUp() {
      this.cancel();
   }

   cancel(): void {
      this.dialogRef.close(false);
   }

   private initEntities(selectedNodes: FlatTreeNode<RepositoryTreeNode>[]) {
      let users: CommonKVModel<string, string>[] = this.exportAssetsService.getUsers(selectedNodes);

      if(users.length == 0) {
         this.updateEntities(selectedNodes);
      }
      else {
         this.exportAssetsService.loadUserNode(users).subscribe((model) => {
            this.exportAssetsService.updateUserNodes(selectedNodes, model.nodes);
            this.updateEntities(selectedNodes);
         });
      }
   }

   private updateEntities(selectedNodes: FlatTreeNode<RepositoryTreeNode>[]) {
      this.exportAssetsService.getExportableAssets(selectedNodes).subscribe((model) => {
            this.loading = false;
            this.entities = model.allowedAssets;
            this.updateDependencies();
         },
         () => {
            this.loading = false;
         });
   }

   private updateDependencies(): void {
      if(this.entities.length === 0) {
         this.dependencies = [];
         this.selectedDependencies = [];
         return;
      }

      this.loading = true;
      const allSelected = this.selectedDependencies.length === this.dependencies.length;
      this.exportAssetsService.getDependentAssets(this.entities)
         .pipe(catchError(err => this.handleExportError(err)))
         .subscribe(response => {
            this.dependencies = response.requiredAssets;
            this.loading = false;

            if(allSelected) {
               this.selectedDependencies = this.dependencies.slice();
            }
            else {
               const old = this.selectedDependencies.slice();
               this.selectedDependencies = this.dependencies.filter(a => old.indexOf(a) !== -1);
            }
         });
   }

   private downloadExport(): void {
      this.downloadService.download("../em/content/repository/export/download");
      this.dialogRef.close(true);
   }

   private entitiesEqual(a: SelectedAssetModel, b: SelectedAssetModel): boolean
   {
      return a.path === b.path && a.type === b.type && a.user === b.user;
   }

   private handleExportError(error: HttpErrorResponse): Observable<any> {
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
}
