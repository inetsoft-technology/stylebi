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
import { SelectionModel } from "@angular/cdk/collections";
import { HttpClient, HttpErrorResponse } from "@angular/common/http";
import { AfterViewInit, Component, Input, OnInit, ViewChild } from "@angular/core";
import { MatDialog, MatDialogRef } from "@angular/material/dialog";
import { MatSnackBar, MatSnackBarConfig } from "@angular/material/snack-bar";
import { MatSort } from "@angular/material/sort";
import { MatTableDataSource } from "@angular/material/table";
import { Observable, throwError } from "rxjs";
import { catchError, finalize } from "rxjs/operators";
import { FeatureFlagValue } from "../../../../../../../shared/feature-flags/feature-flags.service";
import { PluginModel, PluginsModel } from "../../../../../../../shared/util/model/plugins-model";
import { Tool } from "../../../../../../../shared/util/tool";
import { StagedFileChooserComponent } from "../../../../common/util/file-chooser/staged-file-chooser/staged-file-chooser.component";
import { CreateDriverDialogComponent } from "./create-driver-dialog/create-driver-dialog.component";

const PLUGIN_URI = "../api/em/settings/content/plugins";

@Component({
   selector: "em-plugins-view",
   templateUrl: "./plugins-view.component.html",
   styleUrls: ["./plugins-view.component.scss"]
})
export class PluginsViewComponent implements OnInit, AfterViewInit {
   @ViewChild("fileChooser") fileChooser: StagedFileChooserComponent;
   @ViewChild(MatSort) sort: MatSort;
   FeatureFlags = FeatureFlagValue;

   @Input()
   get model(): PluginsModel {
      return this._model;
   }

   set model(model: PluginsModel) {
      this._model = model;

      if(this.model) {
         this.dataSource.data = this.model.plugins;
      }
   }

   loading = false;
   displayedColumns: string[] = ["select", "name", "version"];
   dataSource: MatTableDataSource<PluginModel> = new MatTableDataSource();
   selection = new SelectionModel<PluginModel>(true, []);

   get uploadHeader(): string {
      if(this.fileChooser && this.fileChooser.value && this.fileChooser.value.length) {
         return "_#(js:em.data.databases.pluginsReady)";
      }

      return "_#(js:em.data.databases.selectPlugins)";
   }

   private _model: PluginsModel;

   constructor(private http: HttpClient, public snackBar: MatSnackBar, public dialog: MatDialog) {
   }

   ngOnInit() {
   }

   ngAfterViewInit() {
      this.dataSource.sort = this.sort;
   }

   /**
    * Send request to upload the selected files then refresh drivers.
    */
   uploadPlugins() {
      this.loading = true;
      this.fileChooser.uploadFiles().subscribe(
         (id) => this.savePlugins(id),
         (error) => {
            this.snackBar.open("_#(js:em.data.databases.installError)", null, {duration: Tool.SNACKBAR_DURATION});
            console.log("Failed to install plugin(s): ", error);
            return throwError(error);
         }
      );
   }

   private savePlugins(id: string): void {
      this.http.post<PluginsModel>(PLUGIN_URI, {files: id})
         .pipe(
            finalize(() => this.clearLoading()),
            catchError(error => this.handleInstallPluginsError(error))
         )
         .subscribe(model => this.updateModel(model, "_#(js:em.data.databases.pluginSuccessful)"));
   }

   /**
    * Send request to delete all selected plugins on table
    */
   uninstallSelected() {
      let config = new MatSnackBarConfig();
      config.duration = Tool.SNACKBAR_DURATION;

      const dialogRef = this.dialog.open(UninstallDialog, {
         width: "300px"
      });

      dialogRef.afterClosed().subscribe(result => {
         if(result === "Yes") {
            let deletionModel = <PluginsModel>{plugins: this.selection.selected};
            this.loading = true;

            this.http.post<PluginsModel>(`${PLUGIN_URI}/delete`, deletionModel)
               .pipe(
                  finalize(() => this.clearLoading()),
                  catchError(error => this.handleUninstallPluginsError(error))
               )
               .subscribe(model => this.updateModel(model, "_#(js:em.data.databases.uninstallSuccess)"));
         }
      });
   }

   toggleSelection(plugin: PluginModel) {
      if(!plugin.readOnly) {
         this.selection.toggle(plugin);
      }
   }

   /** Whether the number of selected elements matches the total number of rows. */
   isAllSelected() {
      const numSelected = this.selection.selected.length;
      const numRows = this.dataSource.data.length;
      return numSelected === numRows;
   }

   /** Selects all rows if they are not all selected; otherwise clear selection. */
   masterToggle() {
      this.isAllSelected() ?
         this.selection.clear() :
         this.dataSource.data.forEach(row => this.selection.select(row));
   }

   createDriver(): void {
      this.dialog.open(CreateDriverDialogComponent, {
         width: "60vw",
         height: "80vh",
         disableClose: true,
         data: { plugins: this.model.plugins.map(p => p.id) }
      }).afterClosed().subscribe(added => {
         if(added) {
            this.http.get<PluginsModel>("../api/data/plugins")
               .subscribe(model => this.updateModel(model, "_#(js:em.data.databases.driver.pluginCreated)"));
         }
      });
   }

   private clearLoading(): void {
      this.loading = false;
      this.selection.clear();
   }

   private updateModel(model: PluginsModel, message: string): void {
      this.model = model;
      this.snackBar.open(message, null, {duration: Tool.SNACKBAR_DURATION});
   }

   private handleInstallPluginsError(error: HttpErrorResponse): Observable<PluginsModel> {
      let message: string;

      if(error.error && error.error.message === "_#(js:em.drivers.uploadDriverDuplicate)") {
         message = error.error.message;
      }
      else {
         message = "_#(js:em.data.databases.installError)";
      }

      this.snackBar.open(message, null, {duration: Tool.SNACKBAR_DURATION});
      console.log("Failed to install plugin(s): ", error);
      return throwError(error);
   }

   private handleUninstallPluginsError(error: HttpErrorResponse): Observable<PluginsModel> {
      this.snackBar.open("_#(js:em.data.databases.uninstallError)", null, {duration: Tool.SNACKBAR_DURATION});
      console.log("Failed to uninstall plugins(s): ", error);
      return throwError(error);
   }
}

@Component({
   selector: "em-uninstall-dialog",
   templateUrl: "uninstall-dialog.html"
})
export class UninstallDialog {
   constructor(public dialogRef: MatDialogRef<UninstallDialog>) {
   }
}
