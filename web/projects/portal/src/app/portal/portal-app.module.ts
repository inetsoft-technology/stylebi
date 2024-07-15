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
import {
   NgbCollapseModule,
   NgbDatepickerModule,
   NgbDropdownModule,
   NgbModal,
   NgbNavModule,
   NgbPopoverModule,
   NgbTimepickerModule,
   NgbTooltipModule,
   NgbTypeaheadModule
} from "@ng-bootstrap/ng-bootstrap";
import { MonitoringDataService } from "../../../../em/src/app/monitoring/monitoring-data.service";
import { CkeditorWrapperModule } from "../../../../shared/ckeditor-wrapper/ckeditor-wrapper.module";
import { FeatureFlagsModule } from "../../../../shared/feature-flags/feature-flags.module";
import { PORTAL, ScheduleUsersService } from "../../../../shared/schedule/schedule-users.service";
import { CodemirrorService } from "../../../../shared/util/codemirror/codemirror.service";
import { DefaultCodemirrorService } from "../../../../shared/util/codemirror/default-codemirror.service";
import { CanDeactivateGuard } from "../common/services/can-deactivate-guard.service";
import { UIContextService } from "../common/services/ui-context.service";
import { FormatModule } from "../format/format.module";
import { DataTreeValidatorService } from "../vsobjects/dialog/data-tree-validator.service";
import { AssetTreeModule } from "../widget/asset-tree/asset-tree.module";
import { ConditionModule } from "../widget/condition/condition.module";
import { DateTypeEditorModule } from "../widget/date-type-editor/date-type-editor.module";
import { InputNameDialogModule } from "../widget/dialog/input-name-dialog/input-name-dialog.module";
import { MessageDialogModule } from "../widget/dialog/message-dialog/message-dialog.module";
import { ScriptPaneModule } from "../widget/dialog/script-pane/script-pane.module";
import { SQLQueryDialogModule } from "../widget/dialog/sql-query-dialog/sql-query-dialog.module";
import { WidgetDirectivesModule } from "../widget/directive/widget-directives.module";
import { DropdownViewModule } from "../widget/dropdown-view/dropdown-view.module";
import { DynamicComboBoxModule } from "../widget/dynamic-combo-box/dynamic-combo-box.module";
import { EmailDialogModule } from "../widget/email-dialog/email-dialog.module";
import { ExpandStringModule } from "../widget/expand-string/expand-string.module";
import { FixedDropdownModule } from "../widget/fixed-dropdown/fixed-dropdown.module";
import { GenericSelectableListModule } from "../widget/generic-selectable-list/generic-selectable-list.module";
import { HelpLinkModule } from "../widget/help-link/help-link.module";
import { IdentityTreeModule } from "../widget/identity-tree/identity-tree.module";
import { ModalHeaderModule } from "../widget/modal-header/modal-header.module";
import { MouseEventModule } from "../widget/mouse-event/mouse-event.module";
import { NotificationsModule } from "../widget/notifications/notifications.module";
import { RepositoryTreeModule } from "../widget/repository-tree/repository-tree.module";
import { ResponsiveTabsModule } from "../widget/responsive-tabs/responsive-tabs.module";
import { WidgetScheduleModule } from "../widget/schedule/widget-schedule.module";
import { ScrollableTableModule } from "../widget/scrollable-table/scrollable-table.module";
import { DebounceService } from "../widget/services/debounce.service";
import { SimpleTableModule } from "../widget/simple-table/simple-table.module";
import { SplitPaneModule } from "../widget/split-pane/split-pane.module";
import { StandardDialogModule } from "../widget/standard-dialog/standard-dialog.module";
import { TabularModule } from "../widget/tabular/tabular.module";
import { AutoCollapseToolbarModule } from "../widget/toolbar/auto-collapse-toolbar/auto-collapse-toolbar.module";
import { TreeModule } from "../widget/tree/tree.module";
import { PortalAppRoutingModule } from "./app-routing.module";
import { PortalAppComponent } from "./app.component";
import { CustomTabComponent } from "./custom/custom-tab.component";
import { DashboardTabComponent } from "./dashboard/dashboard-tab.component";
import { DashboardLandingComponent } from "./dashboard/landing/dashboard-landing.component";
import { AssetItemListViewComponent } from "./data/asset-item-list-view/asset-item-list-view.component";
import { AssetDescriptionComponent } from "./data/data-datasource-browser/asset-description/asset-description.component";
import { DataDatasourceBrowserComponent } from "./data/data-datasource-browser/data-datasource-browser.component";
import { DataSourcesBrowser } from "./data/data-datasource-browser/data-source-browser/data-sources-browser.component";
import { DatasourceBrowserService } from "./data/data-datasource-browser/datasource-browser.service";
import { DatasourceCategoryPaneComponent } from "./data/data-datasource-browser/datasource-selection/datasource-category-pane/datasource-category-pane.component";
import { DatasourceListingPaneComponent } from "./data/data-datasource-browser/datasource-selection/datasource-listing/datasource-listing-pane.component";
import { DatasourceListingComponent } from "./data/data-datasource-browser/datasource-selection/datasource-listing/datasource-listing.component";
import { DatasourceSearchComponent } from "./data/data-datasource-browser/datasource-selection/datasource-search/datasource-search.component";
import { DatasourceSelectionViewComponent } from "./data/data-datasource-browser/datasource-selection/datasource-selection-view.component";
import { AdditionalDatasourceDialog } from "./data/data-datasource-browser/datasources-database/additional-datasource-dialog";
import { DatabaseCommonComponentsModule } from "./data/data-datasource-browser/datasources-database/common-components/database-common-components.module";
import { DataModelBrowserService } from "./data/data-datasource-browser/datasources-database/database-data-model-browser/data-model-browser.service";
import { DataModelFolderBrowserComponent } from "./data/data-datasource-browser/datasources-database/database-data-model-browser/data-models-browser/data-model-folder-browser.component";
import { DatabaseDataModelBrowserComponent } from "./data/data-datasource-browser/datasources-database/database-data-model-browser/database-data-model-browser.component";
import { DatabaseDataModelToolbarComponent } from "./data/data-datasource-browser/datasources-database/database-data-model-browser/database-data-model-toolbar.component";
import { DatabaseVPMBrowserComponent } from "./data/data-datasource-browser/datasources-database/database-data-model-browser/database-vpm-browser/database-vpm-browser.component";
import { ChoseAdditionalConnectionDialog } from "./data/data-datasource-browser/datasources-database/database-physical-model/chose-additional-connection-dialog/chose-additional-connection-dialog.component";
import { ChosePhysicalViewDialog } from "./data/data-datasource-browser/datasources-database/database-physical-model/chose-physical-view-dialog/chose-physical-view-dialog.component";
import { DataModelScriptPane } from "./data/data-datasource-browser/datasources-database/database-physical-model/data-model-script-pane/data-model-script-pane.component";
import { DatabasePhysicalModelComponent } from "./data/data-datasource-browser/datasources-database/database-physical-model/database-physical-model.component";
import { LogicalModelAttributeDialog } from "./data/data-datasource-browser/datasources-database/database-physical-model/logical-model/attribute-dialog/logical-model-attribute-dialog.component";
import { PhysicalTableTreeNodeComponent } from "./data/data-datasource-browser/datasources-database/database-physical-model/logical-model/attribute-dialog/physical-table-tree/physical-table-tree-node/physical-table-tree-node.component";
import { PhysicalTableTreeComponent } from "./data/data-datasource-browser/datasources-database/database-physical-model/logical-model/attribute-dialog/physical-table-tree/physical-table-tree.component";
import { AutoDrillDialog } from "./data/data-datasource-browser/datasources-database/database-physical-model/logical-model/attribute-editor/auto-drill-dialog/data-auto-drill-dialog.component";
import { ParameterDialog } from "./data/data-datasource-browser/datasources-database/database-physical-model/logical-model/attribute-editor/auto-drill-dialog/parameter-dialog/parameter-dialog.component";
import { SelectAttributePaneComponent } from "./data/data-datasource-browser/datasources-database/database-physical-model/logical-model/attribute-editor/auto-drill-dialog/select-attribute-pane.component";
import { SelectQueryFieldPaneComponent } from "./data/data-datasource-browser/datasources-database/database-physical-model/logical-model/attribute-editor/auto-drill-dialog/select-query-field-pane.component";
import { SelectWorksheetDialog } from "./data/data-datasource-browser/datasources-database/database-physical-model/logical-model/attribute-editor/auto-drill-dialog/select-worksheet-dialog.component";
import { LogicalModelAttributeEditor } from "./data/data-datasource-browser/datasources-database/database-physical-model/logical-model/attribute-editor/logical-model-attribute-editor.component";
import { LogicalModelColumnEditor } from "./data/data-datasource-browser/datasources-database/database-physical-model/logical-model/column-attribute-editor/logical-model-column-editor.component";
import { ElementTreeNode } from "./data/data-datasource-browser/datasources-database/database-physical-model/logical-model/element-tree-node/element-tree-node.component";
import { LogicalModelEntityDialog } from "./data/data-datasource-browser/datasources-database/database-physical-model/logical-model/entity-dialog/logical-model-entity-dialog.component";
import { LogicalModelEntityEditor } from "./data/data-datasource-browser/datasources-database/database-physical-model/logical-model/entity-editor/logical-model-entity-editor.component";
import { LogicalModelEntityPane } from "./data/data-datasource-browser/datasources-database/database-physical-model/logical-model/entity-pane/logical-model-entity-pane.component";
import { LogicalModelExpressionEditor } from "./data/data-datasource-browser/datasources-database/database-physical-model/logical-model/expression-attribute-editor/logical-model-expression-editor.component";
import { LogicalModelExpressionDialog } from "./data/data-datasource-browser/datasources-database/database-physical-model/logical-model/expression-dialog/logical-model-expression-dialog.component";
import { LogicalModelPropertyPane } from "./data/data-datasource-browser/datasources-database/database-physical-model/logical-model/logical-model-property-pane.component";
import { LogicalModelComponent } from "./data/data-datasource-browser/datasources-database/database-physical-model/logical-model/logical-model.component";
import { PhysicalGraphPane } from "./data/data-datasource-browser/datasources-database/database-physical-model/physical-graph-pane/physical-graph-pane.component";
import { PhysicalJoinEditPane } from "./data/data-datasource-browser/datasources-database/database-physical-model/physical-join-edit-pane/physical-join-edit-pane.component";
import { PhysicalModelEditTableComponent } from "./data/data-datasource-browser/datasources-database/database-physical-model/physical-model-edit-table/physical-model-edit-table.component";
import { AddJoinDialog } from "./data/data-datasource-browser/datasources-database/database-physical-model/physical-model-edit-table/physical-table-joins/add-join-dialog/add-join-dialog.component";
import { EditJoinDialog } from "./data/data-datasource-browser/datasources-database/database-physical-model/physical-model-edit-table/physical-table-joins/edit-join-dialog/edit-join-dialog.component";
import { PhysicalTableJoinsComponent } from "./data/data-datasource-browser/datasources-database/database-physical-model/physical-model-edit-table/physical-table-joins/physical-table-joins.component";
import { PhysicalModelNetworkGraphComponent } from "./data/data-datasource-browser/datasources-database/database-physical-model/physical-model-network-graph/physical-model-network-graph.component";
import { PhysicalModelTableTreeNodeComponent } from "./data/data-datasource-browser/datasources-database/database-physical-model/physical-model-table-tree/physical-model-table-tree-node/physical-model-table-tree-node.component";
import { PhysicalModelTableTreeComponent } from "./data/data-datasource-browser/datasources-database/database-physical-model/physical-model-table-tree/physical-model-table-tree.component";
import { PhysicalStatusBarComponent } from "./data/data-datasource-browser/datasources-database/database-physical-model/physical-status-bar.component";
import { DataQueryModelService } from "./data/data-datasource-browser/datasources-database/database-query/data-query-model.service";
import { DatabaseVPMComponent } from "./data/data-datasource-browser/datasources-database/database-vpm/database-vpm.component";
import { VPMConditionsComponent } from "./data/data-datasource-browser/datasources-database/database-vpm/vpm-conditions/vpm-conditions.component";
import { VPMHiddenColumnsComponent } from "./data/data-datasource-browser/datasources-database/database-vpm/vpm-hidden-columns/vpm-hidden-columns.component";
import { VPMLookupComponent } from "./data/data-datasource-browser/datasources-database/database-vpm/vpm-lookup/vpm-lookup.component";
import { VPMTestComponent } from "./data/data-datasource-browser/datasources-database/database-vpm/vpm-test/vpm-test.component";
import { DatasourcesDatabaseComponent } from "./data/data-datasource-browser/datasources-database/datasources-database.component";
import { DriverWizardComponent } from "./data/data-datasource-browser/datasources-database/driver-wizard/driver-wizard.component";
import { EditPropertyDialogComponent } from "./data/data-datasource-browser/datasources-database/edit-property-dialog.component";
import { DatasourcesDatasourceDialogComponent } from "./data/data-datasource-browser/datasources-datasource/datasources-datasource-dialog/datasources-datasource-dialog.component";
import { DatasourcesDatasourceEditorComponent } from "./data/data-datasource-browser/datasources-datasource/datasources-datasource-editor/datasources-datasource-editor.component";
import { DatasourcesDatasourceComponent } from "./data/data-datasource-browser/datasources-datasource/datasources-datasource.component";
import { DatasourcesXmlaComponent } from "./data/data-datasource-browser/datasources-xmla/datasources-xmla.component";
import { ViewSampleDataDialog } from "./data/data-datasource-browser/datasources-xmla/view-sample-data-dialog/view-sample-data-dialog.component";
import { DataBrowserService } from "./data/data-folder-browser/data-browser.service";
import { DataFolderBrowserComponent } from "./data/data-folder-browser/data-folder-browser.component";
import { DataFolderListViewComponent } from "./data/data-folder-browser/data-folder-list-view/data-folder-list-view.component";
import { FilesBrowserComponent } from "./data/data-folder-browser/files-browser/files-browser.component";
import { MoveAssetDialogDataConfig } from "./data/data-folder-browser/move-asset-dialog-data-config";
import { DataSourcesTreeActionsService } from "./data/data-navigation-tree/data-sources-tree-actions.service";
import { DataSourcesTreeViewComponent } from "./data/data-navigation-tree/data-sources-tree-view.component";
import { DataNotificationsComponent } from "./data/data-notifications.component";
import { DataTabComponent } from "./data/data-tab.component";
import { InputNameDescDialog } from "./data/input-name-desc-dialog/input-name-desc-dialog.component";
import { MoveAssetDialogComponent } from "./data/move-asset-dialog/move-asset-dialog.component";
import { MoveDataModelDialog } from "./data/move-data-model-dialog/move-data-model-dialog.component";
import { MoveDataSourceDialogComponent } from "./data/move-datasource-dialog/move-datasource-dialog.component";
import { DataModelNameChangeService } from "./data/services/data-model-name-change.service";
import { DataPhysicalModelService } from "./data/services/data-physical-model.service";
import { FolderChangeService } from "./data/services/folder-change.service";
import { AnalyzeMVDialog } from "./dialog/analyze-mv/analyze-mv-dialog.component";
import { AnalyzeMVPane } from "./dialog/analyze-mv/analyze-mv-view/analyze-mv-pane.component";
import { CreateMVPane } from "./dialog/analyze-mv/create-mv-view/create-mv-pane.component";
import { MVExceptionsPortalDialogComponent } from "./dialog/analyze-mv/mv-exception-portal-dialog/mv-exceptions-portal-dialog.component";
import { ArrangeDashboardDialog } from "./dialog/arrange-dashboard-dialog.component";
import { AutoJoinTablesDialog } from "./dialog/auto-join-tables-dialog/auto-join-tables-dialog.component";
import { ChangePasswordDialog } from "./dialog/change-password-dialog.component";
import { ChooseTableDialog } from "./dialog/choose-table-dialog/choose-table-dialog.component";
import { EditDashboardDialog } from "./dialog/edit-dashboard-dialog.component";
import { InlineViewDialog } from "./dialog/inline-view-dialog/inline-view-dialog.component";
import { PhysicalTableAliasesDialog } from "./dialog/physical-table-aliases-dialog/physical-table-aliases-dialog.component";
import { PreferencesDialog } from "./dialog/preferences-dialog.component";
import { DataConditionModule } from "./dialog/vpm-condition-dialog/data-condition.module";
import { PortalRedirectComponent } from "./portal-redirect.component";
import { RepositoryDesktopViewComponent } from "./report/desktop/repository-desktop-view.component";
import { RepositoryListViewComponent } from "./report/list/repository-list-view.component";
import { RepositoryMobileViewComponent } from "./report/mobile/repository-mobile-view.component";
import { ReportTabComponent } from "./report/report-tab.component";
import { PortalReportComponent } from "./report/report/portal-report.component";
import { RepositoryTreeViewComponent } from "./report/tree/repository-tree-view.component";
import { WelcomePageComponent } from "./report/welcome/welcome-page.component";
import { ScheduleSaveGuard } from "./schedule/schedule-save.guard";
import { ScheduleTabComponent } from "./schedule/schedule-tab.component";
import { ActionAccordion } from "./schedule/schedule-task-editor/actions/action-accordian/action-accordion.component";
import { TaskActionPane } from "./schedule/schedule-task-editor/actions/task-action-pane.component";
import { AddParameterDialog } from "./schedule/schedule-task-editor/add-parameter-dialog/add-parameter-dialog.component";
import { TaskConditionPane } from "./schedule/schedule-task-editor/conditions/task-condition-pane.component";
import { EditableTableComponent } from "./schedule/schedule-task-editor/editable-table/editable-table.component";
import { ExecuteAsDialog } from "./schedule/schedule-task-editor/execute-as-dialog/execute-as-dialog.component";
import { TaskOptionsPane } from "./schedule/schedule-task-editor/options/task-options-pane.component";
import { ParameterTable } from "./schedule/schedule-task-editor/parameter-table/parameter-table.component";
import { ScheduleTaskDialog } from "./schedule/schedule-task-editor/schedule-task-dialog.component";
import { ScheduleTaskEditorComponent } from "./schedule/schedule-task-editor/schedule-task-editor.component";
import { SelectDashboardDialog } from "./schedule/schedule-task-editor/select-dashboard-dialog/select-dashboard-dialog.component";
import { CreateTaskFolderDialogComponent } from "./schedule/schedule-task-list/create-task-folder-dialog.component";
import { EditTaskFolderDialog } from "./schedule/schedule-task-list/edit-task-folder-dialog/edit-task-folder-dialog.component";
import { MoveTaskDialogComponent } from "./schedule/schedule-task-list/move-task-dialog/move-task-dialog.component";
import { TaskFolderBrowserComponent } from "./schedule/schedule-task-list/move-task-dialog/task-folder-browser/task-folder-browser.component";
import { ScheduleTaskListComponent } from "./schedule/schedule-task-list/schedule-task-list.component";
import { CanDatabaseCreateActivateService } from "./services/can-database-create-activate.service";
import { CanDatabaseModelActivateService } from "./services/can-database-model-activate.service";
import { CanTabActivateService } from "./services/can-tab-activate.service";
import { CurrentRouteService } from "./services/current-route.service";
import { DashboardTabResolver } from "./services/dashboard-tab-resolver.service";
import { HideNavService } from "./services/hide-nav.service";
import { HistoryBarService } from "./services/history-bar.service";
import { PortalModelService } from "./services/portal-model.service";
import { PortalTabsService } from "./services/portal-tabs.service";
import { ReportTabResolver } from "./services/report-tab-resolver.service";
import { RouteEntryResolver } from "./services/route-entry-resolver.service";
import { RouteSourceResolver } from "./services/route-source-resolver.service";

@NgModule({
   declarations: [
      PortalAppComponent,
      AddJoinDialog,
      AdditionalDatasourceDialog,
      ActionAccordion,
      AddParameterDialog,
      AnalyzeMVDialog,
      AnalyzeMVPane,
      ArrangeDashboardDialog,
      AutoDrillDialog,
      AutoJoinTablesDialog,
      ChangePasswordDialog,
      ChooseTableDialog,
      CreateMVPane,
      CreateTaskFolderDialogComponent,
      CustomTabComponent,
      DatabasePhysicalModelComponent,
      DatabaseVPMBrowserComponent,
      DatabaseVPMComponent,
      DashboardLandingComponent,
      DashboardTabComponent,
      DataDatasourceBrowserComponent,
      DataFolderBrowserComponent,
      DataFolderListViewComponent,
      DataModelScriptPane,
      DataNotificationsComponent,
      DatasourceCategoryPaneComponent,
      DatasourceListingComponent,
      DatasourceListingPaneComponent,
      DataSourcesBrowser,
      DatasourcesDatabaseComponent,
      DatasourcesDatasourceComponent,
      DatasourcesXmlaComponent,
      DatasourceSearchComponent,
      DatasourceSelectionViewComponent,
      DataSourcesTreeViewComponent,
      DataTabComponent,
      EditableTableComponent,
      EditDashboardDialog,
      EditPropertyDialogComponent,
      ElementTreeNode,
      ExecuteAsDialog,
      FilesBrowserComponent,
      EditJoinDialog,
      InlineViewDialog,
      InputNameDescDialog,
      LogicalModelAttributeDialog,
      LogicalModelAttributeEditor,
      LogicalModelComponent,
      LogicalModelColumnEditor,
      LogicalModelExpressionEditor,
      LogicalModelExpressionDialog,
      LogicalModelEntityDialog,
      LogicalModelEntityEditor,
      LogicalModelEntityPane,
      LogicalModelPropertyPane,
      MoveAssetDialogComponent,
      MoveDataSourceDialogComponent,
      MVExceptionsPortalDialogComponent,
      ParameterDialog,
      ParameterTable,
      PortalRedirectComponent,
      PortalReportComponent,
      PreferencesDialog,
      PhysicalJoinEditPane,
      PhysicalGraphPane,
      PhysicalModelEditTableComponent,
      PhysicalTableAliasesDialog,
      PhysicalModelNetworkGraphComponent,
      PhysicalModelTableTreeComponent,
      PhysicalModelTableTreeNodeComponent,
      PhysicalStatusBarComponent,
      PhysicalTableJoinsComponent,
      PhysicalModelTableTreeComponent,
      PhysicalTableTreeComponent,
      PhysicalTableTreeNodeComponent,
      ReportTabComponent,
      RepositoryDesktopViewComponent,
      RepositoryListViewComponent,
      RepositoryMobileViewComponent,
      RepositoryTreeViewComponent,
      ScheduleTabComponent,
      ScheduleTaskDialog,
      ScheduleTaskEditorComponent,
      ScheduleTaskListComponent,
      SelectDashboardDialog,
      SelectWorksheetDialog,
      TaskActionPane,
      TaskConditionPane,
      TaskOptionsPane,
      VPMConditionsComponent,
      VPMHiddenColumnsComponent,
      VPMLookupComponent,
      VPMTestComponent,
      WelcomePageComponent,
      MoveTaskDialogComponent,
      TaskFolderBrowserComponent,
      ChoseAdditionalConnectionDialog,
      EditTaskFolderDialog,
      ChosePhysicalViewDialog,
      DatabaseDataModelBrowserComponent,
      DatabaseDataModelToolbarComponent,
      DataModelFolderBrowserComponent,
      MoveDataModelDialog,
      AssetDescriptionComponent,
      AssetItemListViewComponent,
      DatasourcesDatasourceEditorComponent,
      DatasourcesDatasourceDialogComponent,
      DriverWizardComponent,
      ViewSampleDataDialog,
      SelectAttributePaneComponent,
      SelectQueryFieldPaneComponent
   ],
   imports: [
      CommonModule,
      FormsModule,
      ReactiveFormsModule,
      PortalAppRoutingModule,
      FormatModule,
      FeatureFlagsModule,
      WidgetDirectivesModule,
      AssetTreeModule,
      TreeModule,
      ModalHeaderModule,
      NotificationsModule,
      SplitPaneModule,
      ScrollableTableModule,
      EmailDialogModule,
      DateTypeEditorModule,
      HelpLinkModule,
      ConditionModule,
      FixedDropdownModule,
      InputNameDialogModule,
      AutoCollapseToolbarModule,
      ScriptPaneModule,
      IdentityTreeModule,
      SQLQueryDialogModule,
      ResponsiveTabsModule,
      StandardDialogModule,
      RepositoryTreeModule,
      MessageDialogModule,
      MouseEventModule,
      WidgetScheduleModule,
      GenericSelectableListModule,
      DropdownViewModule,
      ExpandStringModule,
      TabularModule,
      NgbTooltipModule,
      NgbNavModule,
      NgbDatepickerModule,
      NgbTimepickerModule,
      NgbTypeaheadModule,
      NgbCollapseModule,
      NgbDropdownModule,
      NgbPopoverModule,
      DatabaseCommonComponentsModule,
      DataConditionModule,
      DynamicComboBoxModule,
      SimpleTableModule,
      CkeditorWrapperModule
   ],
   bootstrap: [
      PortalAppComponent
   ],
   providers: [
      CanTabActivateService,
      CanDatabaseModelActivateService,
      CanDatabaseCreateActivateService,
      CanDeactivateGuard,
      DataModelNameChangeService,
      DataTreeValidatorService,
      FolderChangeService,
      PortalTabsService,
      DebounceService,
      ScheduleSaveGuard,
      RouteEntryResolver,
      RouteSourceResolver,
      ReportTabResolver,
      DashboardTabResolver,
      DataPhysicalModelService,
      CurrentRouteService,
      HideNavService,
      PortalModelService,
      PortalModelService,
      MonitoringDataService,
      HistoryBarService,
      DataBrowserService,
      DatasourceBrowserService,
      DataModelBrowserService,
      MoveAssetDialogDataConfig,
      ScheduleUsersService,
      {
         provide: PORTAL,
         useValue: true
      },
      DataSourcesTreeActionsService,
      UIContextService,
      NgbModal,
      {
         provide: CodemirrorService,
         useClass: DefaultCodemirrorService
      },
      DataQueryModelService,
   ]
})
export class PortalAppModule {
}
