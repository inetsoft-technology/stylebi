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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { RepositoryFolderTrashcanTableModel } from "../repository-folder-trashcan-settings-page/repository-folder-trashcan-table-model";
import { TableInfo } from "../../../../common/util/table/table-info";

@Component({
   selector: "em-repository-folder-trashcan-settings-view",
   templateUrl: "./repository-folder-trashcan-settings-view.component.html",
   styleUrls: ["./repository-folder-trashcan-settings-view.component.scss"]
})
export class RepositoryFolderTrashcanSettingsViewComponent {
   @Input() model: RepositoryFolderTrashcanTableModel[];
   @Input() reportsTableInfo: TableInfo;
   @Input() smallDevice: boolean;
   @Output() removeReports = new EventEmitter<RepositoryFolderTrashcanTableModel[]>();
   @Output() restoreReports = new EventEmitter<RepositoryFolderTrashcanTableModel[]>();
   @Output() cancel = new EventEmitter<void>();
   @Output() unsavedChanges = new EventEmitter<boolean>();
   reports: RepositoryFolderTrashcanTableModel[];
}
