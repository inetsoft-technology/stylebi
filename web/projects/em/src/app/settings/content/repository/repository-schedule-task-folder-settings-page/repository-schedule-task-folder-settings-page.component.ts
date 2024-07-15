/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
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
import { ScheduleTaskFolderEditorModel } from "./schedule-task-folder-editor-model";
import { ScheduleTaskFolderSettingsModel } from "./schedule-task-folder-settings-model";
import {
   ResourcePermissionModel
} from "../../../security/resource-permission/resource-permission-model";
import {
   DataSourceEditorModel
} from "../../../../../../../shared/util/datasource/data-source-settings-page";
import {
   DataSourceSettingsModel
} from "../../../../../../../shared/util/model/data-source-settings-model";
import {
   DataSourceFolderSettingsModel
} from "../repository-data-source-folder-settings-page/data-source-folder-settings-model";

@Component({
   selector: "em-repository-schedule-task-folder-settings-page",
   templateUrl: "./repository-schedule-task-folder-settings-page.component.html"
})
export class RepositoryScheduleTaskFolderSettingsPageComponent implements OnInit {
   @Input() model: ScheduleTaskFolderEditorModel;
   @Output() editorChanged = new EventEmitter<string>();
   @Output() cancel = new EventEmitter<void>();
   @Output() unsavedChanges = new EventEmitter<boolean>();

   get securityEnabled(): boolean {
      return !!this.model;
   }

   constructor(private http: HttpClient, private snackBar: MatSnackBar) {
   }

   ngOnInit() {
   }

   onFolderChanged(model: ScheduleTaskFolderSettingsModel){
      const uri = "../api/em/settings/content/repository/scheduleTaskFolder";
      const params = new HttpParams()
         .set("path", this.model.path);
      this.http.post<ScheduleTaskFolderSettingsModel>(uri, model, {params})
         .pipe(catchError(error => this.handleApplyError(error)))
         .subscribe(newModel => {
            this.model.folder = newModel;
            this.editorChanged.emit(null);
         });
   }

   private handleApplyError(error: HttpErrorResponse): Observable<ScheduleTaskFolderSettingsModel> {
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
