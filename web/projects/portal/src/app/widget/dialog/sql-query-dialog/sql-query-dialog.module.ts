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
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import {
   DatabaseCommonComponentsModule
} from "../../../portal/data/data-datasource-browser/datasources-database/common-components/database-common-components.module";
import {
   DataQueryModelService
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/data-query-model.service";
import {
   DatabaseQueryComponent
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/database-query.component";
import {
   FieldsPaneComponent
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/query-main/fields-pane/fields-pane.component";
import {
   QueryConditionsPaneComponent
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/query-main/query-condition-pane/query-conditions-pane.component";
import {
   BrowseFieldValuesDialogComponent
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/query-main/query-field-pane/browse-field-values/browse-field-values-dialog.component";
import { EditDataTypeDialogComponent } from "../../../portal/data/data-datasource-browser/datasources-database/database-query/query-main/query-field-pane/edit-data-type-dialog/edit-data-type-dialog.component";
import {
   EditFieldDialogComponent
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/query-main/query-field-pane/edit-field-dialog/edit-field-dialog.component";
import {
   QueryFieldsPaneComponent
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/query-main/query-field-pane/query-fields-pane.component";
import {
   QueryGroupingPaneComponent
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/query-main/query-grouping-pane/query-grouping-pane.component";
import {
   QueryJoinEditPane
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/query-main/query-link-pane/query-join-editor-pane/query-join-edit-pane.component";
import {
   QueryLinkGraphPaneComponent
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/query-main/query-link-pane/query-link-graph-pane/query-link-graph-pane.component";
import {
   QueryLinkPaneComponent
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/query-main/query-link-pane/query-link-pane.component";
import {
   QueryNetworkGraphPaneComponent
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/query-main/query-link-pane/query-network-graph-pane/query-network-graph-pane.component";
import {
   QueryTablePropertiesDialogComponent
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/query-main/query-link-pane/query-table-properties-dialog/query-table-properties-dialog.component";
import {
   QuerySortPaneComponent
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/query-main/query-sort-pane/query-sort-pane.component";
import {
   DataPhysicalModelService
} from "../../../portal/data/services/data-physical-model.service";
import {
   DataConditionModule
} from "../../../portal/dialog/vpm-condition-dialog/data-condition.module";
import { DropdownViewModule } from "../../dropdown-view/dropdown-view.module";
import { SplitPaneModule } from "../../split-pane/split-pane.module";
import { SQLQueryDialog } from "./sql-query-dialog.component";
import { SQLQueryDialogListComponent } from "./sql-query-dialog-list.component";
import { SQLQueryJoinDialog } from "./sql-query-join-dialog.component";
import { ModalHeaderModule } from "../../modal-header/modal-header.module";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { TreeModule } from "../../tree/tree.module";
import { WidgetDirectivesModule } from "../../directive/widget-directives.module";
import { SlideOutModule } from "../../slide-out/slide-out.module";
import { ConditionModule } from "../../condition/condition.module";
import { HelpLinkModule } from "../../help-link/help-link.module";
import { NgbNavModule, NgbTooltip } from "@ng-bootstrap/ng-bootstrap";
import { SimpleTableModule } from "../../simple-table/simple-table.module";
import {
   FreeFormSqlPaneComponent
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/query-sql/free-form-sql-pane.component";
import {
   QueryPreviewTableComponent
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/query-preview/query-preview-table.component";
import { ScrollModule } from "../../scroll/scroll.module";
import { NotificationsModule } from "../../notifications/notifications.module";
import {
   SqlQueryPreviewPaneComponent
} from "../../../portal/data/data-datasource-browser/datasources-database/database-query/query-preview/sql-query-preview-pane.component";
import { SimpleQueryPaneComponent } from "./simple-query-pane.component";

@NgModule({
   imports: [
      CommonModule,
      ModalHeaderModule,
      ReactiveFormsModule,
      FormsModule,
      TreeModule,
      WidgetDirectivesModule,
      SlideOutModule,
      ConditionModule,
      HelpLinkModule,
      NgbNavModule,
      SimpleTableModule,
      SplitPaneModule,
      DatabaseCommonComponentsModule,
      DropdownViewModule,
      DataConditionModule,
      ScrollModule,
      NgbTooltip,
      NotificationsModule
   ],
   declarations: [
      SQLQueryDialog,
      SQLQueryDialogListComponent,
      SimpleQueryPaneComponent,
      SQLQueryJoinDialog,
      DatabaseQueryComponent,
      QueryLinkPaneComponent,
      QueryLinkGraphPaneComponent,
      QueryNetworkGraphPaneComponent,
      QueryJoinEditPane,
      QueryTablePropertiesDialogComponent,
      QueryFieldsPaneComponent,
      EditFieldDialogComponent,
      EditDataTypeDialogComponent,
      BrowseFieldValuesDialogComponent,
      QueryConditionsPaneComponent,
      FieldsPaneComponent,
      QuerySortPaneComponent,
      FreeFormSqlPaneComponent,
      SqlQueryPreviewPaneComponent,
      QueryPreviewTableComponent,
      QueryGroupingPaneComponent,
   ],
   exports: [
      SQLQueryDialog,
      SQLQueryDialogListComponent,
      SQLQueryJoinDialog
   ],
   providers: [
      DataQueryModelService,
      DataPhysicalModelService
   ],
})
export class SQLQueryDialogModule {
}
