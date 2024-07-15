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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { RepositoryEditorModel } from "../../../../../../../../shared/util/model/repository-editor-model";
import { Tool } from "../../../../../../../../shared/util/tool";
import { ErrorHandlerService } from "../../../../../common/util/error/error-handler.service";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { convertToKey, IdentityId } from "../../../../security/users/identity-id";
import { RepositoryDashboardSettingsModel } from "./repository-dashboard-settings-model";

export interface RepositoryDashboardEditorModel extends RepositoryEditorModel {
   dashboardSettings: RepositoryDashboardSettingsModel;
   owner: IdentityId;
}

@Component({
   selector: "em-repository-dashboard-settings-page",
   templateUrl: "./repository-dashboard-settings-page.component.html",
   styleUrls: ["./repository-dashboard-settings-page.component.scss"]
})
export class RepositoryDashboardSettingsPageComponent {
   @Input() model: RepositoryDashboardEditorModel;
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

   changeModel(model: RepositoryDashboardSettingsModel) {
      model.name = !this.model.owner && !model.name.endsWith("__GLOBAL") ?
         model.name + "__GLOBAL" : model.name;
      model.oname = !this.model.owner && !model.oname.endsWith("__GLOBAL") ?
         model.oname + "__GLOBAL" : model.oname;
      let params = new HttpParams().set("path", Tool.byteEncode(this.model.path));
      params = this.model.owner != null ? params.set("owner", convertToKey(this.model.owner)) : params;
      this.http.post("../api/em/content/repository/dashboard", model, {params})
         .subscribe((newModel: RepositoryDashboardSettingsModel) => {
               if(newModel) {
                  this.editorChanged.emit(newModel.name);
               }
            },
            (error) => {
               if(error.error.type === "MessageException") {
                  this.snackbar.open(error.error.message, "_#(js:Close)", {duration: Tool.SNACKBAR_DURATION});
                  this.model.dashboardSettings.name = this.model.dashboardSettings.oname;
                  this.model.dashboardSettings = Tool.clone(this.model.dashboardSettings);
               }
            });
   }
}
