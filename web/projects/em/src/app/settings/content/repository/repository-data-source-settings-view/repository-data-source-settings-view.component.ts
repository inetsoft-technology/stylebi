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
import { Component, EventEmitter, Input, Output, ViewChild } from "@angular/core";
import { NgForm } from "@angular/forms";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { DatabaseDefinitionModel } from "../../../../../../../shared/util/model/database-definition-model";
import { CustomDatabaseInfoModel } from "../../../../../../../shared/util/model/database-info-model";
import {
   DatabaseOptionType,
   DatasourceDatabaseType
} from "../../../../../../../shared/util/model/datasource-database-type";
import {
   DriverAvailability,
   DriverInfo
} from "../../../../../../../shared/util/model/driver-availability";
import { StagedFileChooserComponent } from "../../../../common/util/file-chooser/staged-file-chooser/staged-file-chooser.component";
import { ResourcePermissionModel } from "../../../security/resource-permission/resource-permission-model";

@Component({
   selector: "em-repository-data-source-settings-view",
   templateUrl: "./repository-data-source-settings-view.component.html",
   styleUrls: ["./repository-data-source-settings-view.component.scss"]
})
export class RepositoryDataSourceSettingsViewComponent {
   @Input() selectedTab = 0;
   @Input() permissions: ResourcePermissionModel;
   @Input() isEqual: boolean;

   @Output() selectedTabChanged = new EventEmitter<number>();
   @Output() applyClicked = new EventEmitter<void>();
   @Output() resetClicked = new EventEmitter<void>();
   @Output() unsavedChanges = new EventEmitter<boolean>();

   datasourceChanged: boolean = false;

   get disabled(): boolean {
      return this.isEqual || !this.datasourceChanged;
   }

   changeDatasource(): void {
      this.datasourceChanged = true;
   }
}
