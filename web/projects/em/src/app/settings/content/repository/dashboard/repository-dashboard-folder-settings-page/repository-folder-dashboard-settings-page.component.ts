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
import {HttpClient} from "@angular/common/http";
import {Component, EventEmitter, Input, Output} from "@angular/core";
import {RepositoryEditorModel} from "../../../../../../../../shared/util/model/repository-editor-model";
import {ErrorHandlerService} from "../../../../../common/util/error/error-handler.service";
import {MatDialog} from "@angular/material/dialog";
import {MatSnackBar} from "@angular/material/snack-bar";
import {RepositoryFolderDashboardSettingsModel} from "./repository-folder-dashboard-settings-model";
import {Tool} from "../../../../../../../../shared/util/tool";

export interface RepositoryFolderDashboardEditorModel extends RepositoryEditorModel {
   dashboardFolderSettings: RepositoryFolderDashboardSettingsModel;
   owner: string;
}

@Component({
   selector: "em-repository-folder-dashboard-settings-page",
   templateUrl: "./repository-folder-dashboard-settings-page.component.html",
   styleUrls: ["./repository-folder-dashboard-settings-page.component.scss"]
})
export class RepositoryFolderDashboardSettingsPageComponent {
   @Input() model: RepositoryFolderDashboardEditorModel;
   @Input() selectedTab = 0;
   @Input() smallDevice: boolean;
   @Output() cancel = new EventEmitter<void>();
   @Output() selectedTabChanged = new EventEmitter<number>();
   @Output() editorChanged = new EventEmitter<string>();
   @Output() unsavedChanges = new EventEmitter<boolean>();

   constructor(private http: HttpClient,
               private errorService: ErrorHandlerService,
               private dialog: MatDialog,
               private snackbar: MatSnackBar) {
   }

   changeModel(model: RepositoryFolderDashboardSettingsModel) {
      this.http.post("../api/em/content/repository/folder/dashboard", model)
         .subscribe((newModel: RepositoryFolderDashboardSettingsModel) => {
               if(newModel) {
                  this.editorChanged.emit();
               }
            },
            (error) => {
               if(error.error.type === "MessageException") {
                  this.snackbar.open(error.error.message, "_#(js:Close)", {duration: Tool.SNACKBAR_DURATION});
               }
            });
   }
}
