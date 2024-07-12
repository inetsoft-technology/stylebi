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
import { HttpClient, HttpErrorResponse, HttpParams } from "@angular/common/http";
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { MatSnackBar } from "@angular/material/snack-bar";
import { Observable, throwError } from "rxjs";
import { catchError } from "rxjs/operators";
import { Tool } from "../../../../../../../shared/util/tool";
import { DataSourceFolderEditorModel } from "./data-source-folder-editor-model";
import { DataSourceFolderSettingsModel } from "./data-source-folder-settings-model";

@Component({
   selector: "em-repository-data-source-folder-settings-page",
   templateUrl: "./repository-data-source-folder-settings-page.component.html",
   styleUrls: ["./repository-data-source-folder-settings-page.component.scss"]
})
export class RepositoryDataSourceFolderSettingsPageComponent implements OnInit, OnChanges {
   @Input() model: DataSourceFolderEditorModel;
   @Input() selectedTab = 0;
   @Input() smallDevice: boolean;
   @Output() cancel = new EventEmitter<void>();
   @Output() selectedTabChanged = new EventEmitter<number>();
   @Output() editorChanged = new EventEmitter<string>();
   @Output() unsavedChanges = new EventEmitter<boolean>();
   folderModel: DataSourceFolderSettingsModel;

   get securityEnabled(): boolean {
      return !!this.folderModel && !!this.folderModel.permissions;
   }

   constructor(private http: HttpClient, private snackBar: MatSnackBar) {
   }

   ngOnInit() {
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(changes.model) {
         this.folderModel = this.model.folder;
      }
   }

   onFolderChanged(model: DataSourceFolderSettingsModel): void {
      const parentFolder = this.getParentFolder(this.model.path);
      let newPath: string;

      if(parentFolder === "/" || parentFolder === "") {
         newPath = model.name;
      }
      else {
         newPath = `${parentFolder}/${model.name}`;
      }

      let auditPath = this.model.path;

      if(!!this.model.fullPath) {
         auditPath = this.model.fullPath;
      }

      const uri = "../api/em/settings/content/repository/dataSourceFolder";
      const params = new HttpParams()
         .set("path", this.model.path);
      this.http.post<DataSourceFolderSettingsModel>(uri, model, {params})
         .pipe(catchError(error => this.handleApplyError(error)))
         .subscribe(newModel => {
            this.model.path = newPath;
            this.model.folder = newModel;
            this.editorChanged.emit(newModel.name);
         });
   }

   private getParentFolder(path: string): string {
      if(path === "/") {
         return "/";
      }

      const slash: number = path.lastIndexOf("/");
      return slash >= 0 ? path.substring(0, slash) : "/";
   }

   private handleApplyError(error: HttpErrorResponse): Observable<DataSourceFolderSettingsModel> {
      console.error("Failed to save folder settings: ", error);
      let message: string;

      if(error.error && error.error.type === "MessageException") {
         message = error.error.message;
      }
      else {
         message = "Failed to save folder settings.";
      }

      this.snackBar.open(message, "_#(js:Close)",
         { duration: Tool.SNACKBAR_DURATION, panelClass: ["max-width"] });
      return throwError(error);
   }
}
