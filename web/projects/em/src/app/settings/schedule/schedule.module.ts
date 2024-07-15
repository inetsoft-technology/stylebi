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
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatAutocompleteModule } from "@angular/material/autocomplete";
import { MatBottomSheetModule } from "@angular/material/bottom-sheet";
import { MatButtonModule } from "@angular/material/button";
import { MatButtonToggleModule } from "@angular/material/button-toggle";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { DateAdapter, ErrorStateMatcher, MatNativeDateModule } from "@angular/material/core";
import { MatDatepickerModule } from "@angular/material/datepicker";
import { MatDialogModule } from "@angular/material/dialog";
import { MatDividerModule } from "@angular/material/divider";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatGridListModule } from "@angular/material/grid-list";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatListModule } from "@angular/material/list";
import { MatMenuModule } from "@angular/material/menu";
import { MatPaginatorIntl, MatPaginatorModule } from "@angular/material/paginator";
import { MatProgressBarModule } from "@angular/material/progress-bar";
import { MatProgressSpinnerModule } from "@angular/material/progress-spinner";
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
import { AngularResizeEventModule } from "../../../../../shared/resize-event/angular-resize-event.module";
import { LocalizedMatPaginator } from "../../../../../shared/util/localized-mat-paginator";
import { CustomValueAccessorModule } from "../../common/custom-value-accessor/custom-value-accessor.module";
import { CustomNativeDateAdapter } from "../../common/util/datepicker/custom-native-date-adapter.service";
import { DatepickerModule } from "../../common/util/datepicker/datepicker.module";
import { EditorPanelModule } from "../../common/util/editor-panel/editor-panel.module";
import { EmErrorStateMatcher } from "../../common/util/error/em-error-state-matcher";
import { LoadingSpinnerModule } from "../../common/util/loading-spinner/loading-spinner.module";
import { MatCkeditorModule } from "../../common/util/mat-ckeditor/mat-ckeditor.module";
import { MessageDialogModule } from "../../common/util/message-dialog.module";
import { ModalHeaderModule } from "../../common/util/modal-header/modal-header.module";
import { TableViewModule } from "../../common/util/table/table-view.module";
import { FlatTreeModule } from "../../common/util/tree/flat-tree.module";
import { MonitoringDataService } from "../../monitoring/monitoring-data.service";
import { TopScrollModule } from "../../top-scroll/top-scroll.module";
import { RepositoryModule } from "../content/repository/repository.module";
import { EmailPickerModule } from "../email-picker/email-picker.module";
import { ResourcePermissionModule } from "../security/resource-permission/resource-permission.module";
import { EditTaskFolderDialogComponent } from "./edit-task-folder-dialog/edit-task-folder-dialog.component";
import { ImportExportTaskModule } from "./import-export/import-export.module";
import { MoveTaskFolderDialogComponent } from "./move-folder-dialog/move-task-folder-dialog.component";
import { ParameterTableModule } from "./parameter-table/parameter-table.module";
import { ScheduleConfigSaveGuard } from "./schedule-configuration-page/schedule-config-save.guard";
import { ScheduleConfigurationPageComponent } from "./schedule-configuration-page/schedule-configuration-page.component";
import { ServerLocationEditorComponent } from "./schedule-configuration-page/server-location-editor/server-location-editor.component";
import { ServerLocationsViewComponent } from "./schedule-configuration-page/server-locations-view/server-locations-view.component";
import { TimeRangeEditorComponent } from "./schedule-configuration-page/time-range-editor/time-range-editor.component";
import { TimeRangesViewComponent } from "./schedule-configuration-page/time-ranges-view/time-ranges-view.component";
import { EditClasspathTextDialogComponent } from "./schedule-configuration-view/schedule-classpath-dialog/edit-classpath-text-dialog/edit-classpath-text-dialog.component";
import { ScheduleClasspathDialogComponent } from "./schedule-configuration-view/schedule-classpath-dialog/schedule-classpath-dialog.component";
import { ScheduleConfigurationViewComponent } from "./schedule-configuration-view/schedule-configuration-view.component";
import { ScheduleCycleEditorPageComponent } from "./schedule-cycle-editor-page/schedule-cycle-editor-page.component";
import { ScheduleCycleSaveGuard } from "./schedule-cycle-editor-page/schedule-cycle-save.guard";
import { ScheduleCycleListPageComponent } from "./schedule-cycle-list-page/schedule-cycle-list-page.component";
import { ScheduleCycleListViewComponent } from "./schedule-cycle-list-view/schedule-cycle-list-view.component";
import { ScheduleCycleOptionsPaneComponent } from "./schedule-cycle-options-pane/schedule-cycle-options-pane.component";
import { ScheduleFolderTreeComponent } from "./schedule-folder-tree/schedule-folder-tree.component";
import { ScheduleRoutingModule } from "./schedule-routing.module";
import { ScheduleSettingsPageComponent } from "./schedule-settings-page/schedule-settings-page.component";
import { ScheduleStatusPageComponent } from "./schedule-status-page/schedule-status-page.component";
import { ScheduleSaveGuard } from "./schedule-task-editor-page/schedule-save.guard";
import { ScheduleTaskEditorPageComponent } from "./schedule-task-editor-page/schedule-task-editor-page.component";
import { ScheduleTaskListComponent } from "./schedule-task-list/schedule-task-list.component";
import { BackupActionEditorComponent } from "./task-action-pane/backup-action-editor/backup-action-editor.component";
import { BackupFileComponent } from "./task-action-pane/backup-file/backup-file.component";
import { BatchActionEditorComponent } from "./task-action-pane/batch-action-editor/batch-action-editor.component";
import { BatchAddParametersDialogComponent } from "./task-action-pane/batch-add-parameters-dialog/batch-add-parameters-dialog.component";
import { BatchEmbeddedParametersDialogComponent } from "./task-action-pane/batch-embedded-parameters-dialog/batch-embedded-parameters-dialog.component";
import { BatchQueryParametersDialogComponent } from "./task-action-pane/batch-query-parameters-dialog/batch-query-parameters-dialog.component";
import { BurstEmailDialogComponent } from "./task-action-pane/burst-email-dialog/burst-email-dialog.component";
import { BurstEmailEmbeddedPaneComponent } from "./task-action-pane/burst-email-dialog/burst-email-embedded-pane/burst-email-embedded-pane.component";
import { BurstEmailQueryPaneComponent } from "./task-action-pane/burst-email-dialog/burst-email-query-pane/burst-email-query-pane.component";
import { DeliveryEmailsComponent } from "./task-action-pane/delivery-emails/delivery-emails.component";
import { EditPermissionDialogComponent } from "./task-action-pane/edit-permission-dialog/edit-permission-dialog.component";
import { EmCSVConfigPaneComponent } from "./task-action-pane/em-csv-config-pane.component";
import { NotificationEmailsComponent } from "./task-action-pane/notification-emails/notification-emails.component";
import { ScheduleAlertsComponent } from "./task-action-pane/schedule-alerts/schedule-alerts.component";
import { ScheduleTaskSelectComponent } from "./task-action-pane/schedule-task-select/schedule-task-select.component";
import { ServerSaveComponent } from "./task-action-pane/server-save/server-save.component";
import { TaskActionPaneComponent } from "./task-action-pane/task-action-pane.component";
import { ViewsheetActionEditorComponent } from "./task-action-pane/viewsheet-action-editor/viewsheet-action-editor.component";
import { CompletionConditionEditorComponent } from "./task-condition-pane/completion-condition-editor/completion-condition-editor.component";
import { DailyConditionEditorComponent } from "./task-condition-pane/daily-condition-editor/daily-condition-editor.component";
import { HourlyConditionEditorComponent } from "./task-condition-pane/hourly-condition-editor/hourly-condition-editor.component";
import { MonthlyConditionEditorComponent } from "./task-condition-pane/monthly-condition-editor/monthly-condition-editor.component";
import { RunOnceConditionEditorComponent } from "./task-condition-pane/run-once-condition-editor/run-once-condition-editor.component";
import { StartTimeEditorComponent } from "./task-condition-pane/start-time-editor/start-time-editor.component";
import { TaskConditionPaneComponent } from "./task-condition-pane/task-condition-pane.component";
import { TimeConditionEditorComponent } from "./task-condition-pane/time-condition-editor/time-condition-editor.component";
import { TimeZoneSelectComponent } from "./task-condition-pane/time-zone-select/time-zone-select-component";
import { WeeklyConditionEditorComponent } from "./task-condition-pane/weekly-condition-editor/weekly-condition-editor.component";
import { ExecuteAsDialogComponent } from "./task-options-pane/execute-as-dialog.component";
import { TaskOptionsPane } from "./task-options-pane/task-options-pane.component";
import { TimePickerComponent } from "./task-condition-pane/time-picker/time-picker.component";
import { FeatureFlagsModule } from "../../../../../shared/feature-flags/feature-flags.module";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      ReactiveFormsModule,
      MatAutocompleteModule,
      MatBottomSheetModule,
      MatButtonModule,
      MatButtonToggleModule,
      MatCardModule,
      MatCheckboxModule,
      MatDatepickerModule,
      MatDialogModule,
      MatDividerModule,
      MatFormFieldModule,
      MatGridListModule,
      MatIconModule,
      MatInputModule,
      MatListModule,
      MatMenuModule,
      MatNativeDateModule,
      MatPaginatorModule,
      MatProgressBarModule,
      MatProgressSpinnerModule,
      MatRadioModule,
      MatSelectModule,
      MatSelectModule,
      MatSlideToggleModule,
      MatSnackBarModule,
      MatSortModule,
      MatTableModule,
      MatTabsModule,
      MatTooltipModule,
      MatTreeModule,
      AngularResizeEventModule,
      CustomValueAccessorModule,
      DatepickerModule,
      ScheduleRoutingModule,
      EditorPanelModule,
      EmailPickerModule,
      FlatTreeModule,
      MessageDialogModule,
      ParameterTableModule,
      RepositoryModule,
      ResourcePermissionModule,
      TableViewModule,
      TopScrollModule,
      ImportExportTaskModule,
      MatSidenavModule,
      LoadingSpinnerModule,
      MatCkeditorModule,
      MatToolbarModule,
      ModalHeaderModule,
      FeatureFlagsModule
   ],
   exports: [
      MatButtonModule,
      MatButtonToggleModule,
      MatGridListModule
   ],
   declarations: [
      BackupActionEditorComponent,
      BackupFileComponent,
      BurstEmailDialogComponent,
      BurstEmailEmbeddedPaneComponent,
      BurstEmailQueryPaneComponent,
      CompletionConditionEditorComponent,
      DailyConditionEditorComponent,
      DeliveryEmailsComponent,
      EditPermissionDialogComponent,
      ExecuteAsDialogComponent,
      EmCSVConfigPaneComponent,
      HourlyConditionEditorComponent,
      MonthlyConditionEditorComponent,
      NotificationEmailsComponent,
      RunOnceConditionEditorComponent,
      ScheduleAlertsComponent,
      ScheduleConfigurationPageComponent,
      ScheduleConfigurationViewComponent,
      ScheduleCycleEditorPageComponent,
      ScheduleCycleListPageComponent,
      ScheduleCycleListViewComponent,
      ScheduleCycleOptionsPaneComponent,
      ScheduleSettingsPageComponent,
      ScheduleStatusPageComponent,
      ScheduleTaskEditorPageComponent,
      ScheduleTaskListComponent,
      ServerSaveComponent,
      StartTimeEditorComponent,
      TaskActionPaneComponent,
      TaskConditionPaneComponent,
      TaskOptionsPane,
      TimeConditionEditorComponent,
      TimePickerComponent,
      TimeZoneSelectComponent,
      TimeRangeEditorComponent,
      TimeRangesViewComponent,
      ViewsheetActionEditorComponent,
      WeeklyConditionEditorComponent,
      ServerLocationsViewComponent,
      ServerLocationEditorComponent,
      ScheduleClasspathDialogComponent,
      EditClasspathTextDialogComponent,
      MoveTaskFolderDialogComponent,
      EditTaskFolderDialogComponent,
      ScheduleFolderTreeComponent,
      BatchActionEditorComponent,
      ScheduleTaskSelectComponent,
      BatchQueryParametersDialogComponent,
      BatchEmbeddedParametersDialogComponent,
      BatchAddParametersDialogComponent
   ],
   providers: [
      ScheduleConfigSaveGuard,
      ScheduleCycleSaveGuard,
      ScheduleSaveGuard,
      MonitoringDataService,
      {
         provide: DateAdapter,
         useClass: CustomNativeDateAdapter
      },
      {
         provide: MatPaginatorIntl,
         useClass: LocalizedMatPaginator
      },
      {
         provide: ErrorStateMatcher,
         useClass: EmErrorStateMatcher
      },
   ]
})
export class ScheduleModule {
}
