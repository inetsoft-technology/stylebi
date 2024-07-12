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
import {
   Component, EventEmitter,
   Input, OnChanges, Output, SimpleChanges,
} from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { MatSnackBar } from "@angular/material/snack-bar";
import {
   DataSourceEditorModel,
   DataSourceSettingsPage
} from "../../../../../../../shared/util/datasource/data-source-settings-page";
import { Tool } from "../../../../../../../shared/util/tool";

@Component({
   selector: "em-repository-data-source-settings-page",
   templateUrl: "./repository-data-source-settings-page.component.html",
   styleUrls: ["./repository-data-source-settings-page.component.scss"]
})
export class RepositoryDataSourceSettingsPageComponent extends DataSourceSettingsPage implements OnChanges {
   @Input() model: DataSourceEditorModel;
   @Input() selectedTab = 0;
   @Input() smallDevice: boolean;
   @Input() set auditPath(auditPath: string) {
      this._auditPath = auditPath;
   }
   @Output() cancel = new EventEmitter<void>();
   @Output() editorChanged = new EventEmitter<string>();
   @Output() selectedTabChanged = new EventEmitter<number>();
   @Output() unsavedChanges = new EventEmitter<boolean>();
   private _oldModel: DataSourceEditorModel;

   constructor(http: HttpClient, private snackBar: MatSnackBar) {
      super(http);
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.model) {
         this.setModel(this.model.settings);
         this._oldModel = Tool.clone(this.model);
      }
   }

   public afterDatabaseSave(): void {
      let name = this.model.settings.dataSource == null ? this.model.path : this.database.name;
      this.editorChanged.emit(name);
   }

   public showMessage(message: string) {
      this.snackBar.open(message, null, {duration: Tool.SNACKBAR_DURATION});
   }

   reset() {
      if(this.smallDevice) {
         this.cancel.emit();
      }

      this.model = Tool.clone(this._oldModel);
      this.setModel(this.model.settings);
   }
}
