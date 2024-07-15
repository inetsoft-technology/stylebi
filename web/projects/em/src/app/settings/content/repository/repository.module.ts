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
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatNativeDateModule } from "@angular/material/core";
import { MatOptionModule } from "@angular/material/core";
import { MatDatepickerModule } from "@angular/material/datepicker";
import { MatDialogModule } from "@angular/material/dialog";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatGridListModule } from "@angular/material/grid-list";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatListModule } from "@angular/material/list";
import { MatMenuModule } from "@angular/material/menu";
import { MatPaginatorModule } from "@angular/material/paginator";
import { MatProgressBarModule } from "@angular/material/progress-bar";
import { MatRadioModule } from "@angular/material/radio";
import { MatSelectModule } from "@angular/material/select";
import { MatSidenavModule } from "@angular/material/sidenav";
import { MatSlideToggleModule } from "@angular/material/slide-toggle";
import { MatSnackBarModule } from "@angular/material/snack-bar";
import { MatSortModule } from "@angular/material/sort";
import { MatTableModule } from "@angular/material/table";
import { MatTabsModule } from "@angular/material/tabs";
import { MatToolbarModule } from "@angular/material/toolbar";
import { MatTooltipModule } from "@angular/material/tooltip";
import { MatTreeModule } from "@angular/material/tree";
import { DatepickerModule } from "../../../common/util/datepicker/datepicker.module";
import { EditorPanelModule } from "../../../common/util/editor-panel/editor-panel.module";
import { ErrorHandlerService } from "../../../common/util/error/error-handler.service";
import { FileChooserModule } from "../../../common/util/file-chooser/file-chooser.module";
import { LoadingSpinnerModule } from "../../../common/util/loading-spinner/loading-spinner.module";
import { MessageDialogModule } from "../../../common/util/message-dialog.module";
import { ModalHeaderModule } from "../../../common/util/modal-header/modal-header.module";
import { TableViewModule } from "../../../common/util/table/table-view.module";
import { FlatTreeModule } from "../../../common/util/tree/flat-tree.module";
import { TopScrollModule } from "../../../top-scroll/top-scroll.module";
import { ParameterTableModule } from "../../schedule/parameter-table/parameter-table.module";
import { ResourcePermissionModule } from "../../security/resource-permission/resource-permission.module";
import { AnalyzeMvPageComponent } from "./analyze-mv-page/analyze-mv-page.component";
import { ContentRepositoryPageComponent } from "./content-repository-page/content-repository-page.component";
import { ContentRepositorySaveGuard } from "./content-repository-page/content-repository-save.guard";
import { RepositoryFolderDashboardSettingsPageComponent } from "./dashboard/repository-dashboard-folder-settings-page/repository-folder-dashboard-settings-page.component";
import { RepositoryFolderDashboardSettingsViewComponent } from "./dashboard/repository-dashboard-folder-settings-view/repository-folder-dashboard-settings-view.component";
import { RepositoryDashboardSettingsPageComponent } from "./dashboard/repository-dashboard-settings-page/repository-dashboard-settings-page.component";
import { RepositoryDashboardSettingsViewComponent } from "./dashboard/repository-dashboard-settings-view/repository-dashboard-settings-view.component";
import { ImportExportModule } from "./import-export/import-export.module";
import { MaterializeSheetDialogComponent } from "./materialize-sheet-dialog/materialize-sheet-dialog.component";
import { MoveAssetDialogComponent } from "./move-assets-dialog/move-asset-dialog.component";
import { MvExceptionsDialogComponent } from "./mv-exceptions-dialog/mv-exceptions-dialog.component";
import { RepositoryDataSourceFolderSettingsPageComponent } from "./repository-data-source-folder-settings-page/repository-data-source-folder-settings-page.component";
import { RepositoryDataSourceFolderSettingsViewComponent } from "./repository-data-source-folder-settings-view/repository-data-source-folder-settings-view.component";
import { RepositoryDataSourceSettingsPageComponent } from "./repository-data-source-settings-page/repository-data-source-settings-page.component";
import { RepositoryDataSourceSettingsViewComponent } from "./repository-data-source-settings-view/repository-data-source-settings-view.component";
import { RepositoryEditorPageComponent } from "./repository-editor-page/repository-editor-page.component";
import { RepositoryFolderRecycleBinPageComponent } from "./repository-folder-recycle-bin-page/repository-folder-recycle-bin-page.component";
import { RepositoryRecycleBinSettingsViewComponent } from "./repository-folder-recycle-bin-view/repository-recycle-bin-settings-view.component";
import { RepositoryFolderSettingsPageComponent } from "./repository-folder-settings-page/repository-folder-settings-page.component";
import { RepositoryFolderSettingsViewComponent } from "./repository-folder-settings-view/repository-folder-settings-view.component";
import { RepositoryFolderTrashcanSettingsPageComponent } from "./repository-folder-trashcan-settings-page/repository-folder-trashcan-settings-page.component";
import { RepositoryFolderTrashcanSettingsViewComponent } from "./repository-folder-trashcan-settings-view/repository-folder-trashcan-settings-view.component";
import { RepositoryPermissionEditorPageComponent } from "./repository-permission-editor-page/repository-permission-editor-page.component";
import { RepositoryRecycleBinPageComponent } from "./repository-recycle-bin-page/repository-recycle-bin-page.component";
import { AutoSaveRecycleBinComponent } from "./auto-save-recycle-bin/auto-save-recycle-bin.component";
import { AutoSaveRecycleBinPageComponent } from "./auto-save-recycle-bin/auto-save-recycle-bin-page.component";
import { RepositoryRecycleBinViewComponent } from "./repository-recycle-bin-view/repository-recycle-bin-view.component";
import { RepositoryRoutingModule } from "./repository-routing.module";
import { RepositorySheetSettingsViewComponent } from "./repository-sheet-settings-view/repository-sheet-settings-view.component";
import { RepositoryTreeViewComponent } from "./repository-tree-view/repository-tree-view.component";
import { RepositoryViewsheetSettingsPageComponent } from "./repository-viewsheet-settings-page/repository-viewsheet-settings-page.component";
import { RepositoryViewsheetSettingsViewComponent } from "./repository-viewsheet-settings-view/repository-viewsheet-settings-view.component";
import { RepositoryWorksheetSettingsPageComponent } from "./repository-worksheet-settings-page/repository-worksheet-settings-page.component";
import { RepositoryWorksheetSettingsViewComponent } from "./repository-worksheet-settings-view/repository-worksheet-settings-view.component";
import { RestoreAssetDialogComponent } from "./auto-save-recycle-bin/restore-asset-dialog.component";
import { AutoSaveFolderPageComponent } from "./auto-save-recycle-bin/auto-save-folder-page.component";
import {
   RepositoryScheduleTaskFolderSettingsViewComponent
} from "./repository-schedule-task-folder-settings-view/repository-schedule-task-folder-settings-view.component";
import {
   RepositoryScheduleTaskFolderSettingsPageComponent
} from "./repository-schedule-task-folder-settings-page/repository-schedule-task-folder-settings-page.component";
import { RepositoryScriptSettingsPageComponent } from "./repository-script-settings-page/repository-script-settings-page.component";

@NgModule({
   imports: [
      CommonModule,
      DatepickerModule,
      FormsModule,
      ReactiveFormsModule,
      MatButtonModule,
      MatCardModule,
      MatCheckboxModule,
      MatDatepickerModule,
      MatDialogModule,
      MatFormFieldModule,
      MatGridListModule,
      MatIconModule,
      MatInputModule,
      MatListModule,
      MatMenuModule,
      MatNativeDateModule,
      MatOptionModule,
      MatPaginatorModule,
      MatProgressBarModule,
      MatRadioModule,
      MatSelectModule,
      MatSidenavModule,
      MatSlideToggleModule,
      MatSnackBarModule,
      MatSortModule,
      MatTableModule,
      MatTabsModule,
      MatToolbarModule,
      MatTooltipModule,
      MatTreeModule,
      EditorPanelModule,
      FlatTreeModule,
      LoadingSpinnerModule,
      MessageDialogModule,
      ParameterTableModule,
      RepositoryRoutingModule,
      ResourcePermissionModule,
      TableViewModule,
      FileChooserModule,
      ImportExportModule,
      TopScrollModule,
      ModalHeaderModule
   ],
   declarations: [
      AnalyzeMvPageComponent,
      ContentRepositoryPageComponent,
      MvExceptionsDialogComponent,
      RepositoryDashboardSettingsPageComponent,
      RepositoryDashboardSettingsViewComponent,
      RepositoryDataSourceFolderSettingsPageComponent,
      RepositoryDataSourceFolderSettingsViewComponent,
      RepositoryDataSourceSettingsPageComponent,
      RepositoryDataSourceSettingsViewComponent,
      RepositoryEditorPageComponent,
      RepositoryFolderDashboardSettingsPageComponent,
      RepositoryFolderDashboardSettingsViewComponent,
      RepositoryFolderRecycleBinPageComponent,
      RepositoryFolderSettingsPageComponent,
      RepositoryFolderSettingsViewComponent,
      RepositoryFolderTrashcanSettingsPageComponent,
      RepositoryFolderTrashcanSettingsViewComponent,
      RepositoryPermissionEditorPageComponent,
      RepositoryRecycleBinPageComponent,
      RepositoryScheduleTaskFolderSettingsPageComponent,
      RepositoryScheduleTaskFolderSettingsViewComponent,
      AutoSaveRecycleBinComponent,
      AutoSaveFolderPageComponent,
      AutoSaveRecycleBinPageComponent,
      RestoreAssetDialogComponent,
      RepositoryRecycleBinSettingsViewComponent,
      RepositoryRecycleBinViewComponent,
      RepositoryScriptSettingsPageComponent,
      RepositorySheetSettingsViewComponent,
      RepositoryTreeViewComponent,
      RepositoryViewsheetSettingsPageComponent,
      RepositoryViewsheetSettingsViewComponent,
      RepositoryWorksheetSettingsPageComponent,
      RepositoryWorksheetSettingsViewComponent,
      MaterializeSheetDialogComponent,
      MoveAssetDialogComponent
   ],
   providers: [
      ErrorHandlerService,
      ContentRepositorySaveGuard
   ],
   exports: [
      RepositoryTreeViewComponent
   ]
})
export class RepositoryModule {
}
