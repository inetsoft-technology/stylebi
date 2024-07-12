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
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatButtonToggleModule } from "@angular/material/button-toggle";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatNativeDateModule } from "@angular/material/core";
import { MatOptionModule } from "@angular/material/core";
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
import { LocalizedMatPaginator } from "../../../../../shared/util/localized-mat-paginator";
import { EditorPanelModule } from "../../common/util/editor-panel/editor-panel.module";
import { FileChooserModule } from "../../common/util/file-chooser/file-chooser.module";
import { MessageDialogModule } from "../../common/util/message-dialog.module";
import { ModalHeaderModule } from "../../common/util/modal-header/modal-header.module";
import { ScrollNavModule } from "../../common/util/scroll-nav/scroll-nav.module";
import { TableViewModule } from "../../common/util/table/table-view.module";
import { DashboardsSettingsSortableTableViewComponent } from "./dashboards-settings-sortable-table-view/dashboards-settings-sortable-table-view.component";
import { ExportMenuOptionsViewComponent } from "./export-menu-options-view/export-menu-options-view.component";
import { AddFontFaceDialogComponent } from "./look-and-feel-settings-view/add-font-dialog/add-font-face-dialog.component";
import { EditFontsDialogComponent } from "./look-and-feel-settings-view/edit-fonts-dialog/edit-fonts-dialog.component";
import { LookAndFeelSettingsViewComponent } from "./look-and-feel-settings-view/look-and-feel-settings-view.component";
import { EditPortalTabDialogComponent } from "./portal-integration-view/edit-portal-tab-dialog/edit-portal-tab-dialog.component";
import { PortalIntegrationViewComponent } from "./portal-integration-view/portal-integration-view.component";
import { AddDataSourceTypeDialogComponent } from "./presentation-data-source-visibility-settings/add-data-source-type-dialog/add-data-source-type-dialog.component";
import { PresentationOrgSaveGuard } from "./presentation-settings-view/presentation-org-save-guard";
import { PresentationOrgSettingsViewComponent } from "./presentation-settings-view/presentation-org-settings-view.component";
import { PresentationTimeSettingsViewComponent } from "./presentation-time-settings-view/presentation-time-settings-view.component";
import { PresentationDashboardSettingsViewComponent } from "./presentation-dashboard-settings-view/presentation-dashboard-settings-view.component";
import { PresentationDataSourceVisibilitySettingsViewComponent } from "./presentation-data-source-visibility-settings/presentation-data-source-visibility-settings-view.component";
import { PresentationExportMenuSettingsViewComponent } from "./presentation-export-menu-settings-view/presentation-export-menu-settings-view.component";
import { EditFontMappingDialogComponent } from "./presentation-font-mapping-settings-view/edit-font-mapping-dialog/edit-font-mapping-dialog.component";
import { PresentationFontMappingSettingsViewComponent } from "./presentation-font-mapping-settings-view/presentation-font-mapping-settings-view.component";
import { PresentationFormatsSettingsViewComponent } from "./presentation-formats-settings-view/presentation-formats-settings-view.component";
import { PresentationLoginBannerSettingsViewComponent } from "./presentation-login-banner-settings-view/presentation-login-banner-settings-view.component";
import { PresentationNavViewComponent } from "./presentation-nav-view/presentation-nav-view.component";
import { PresentationPdfGenerationSettingsViewComponent } from "./presentation-pdf-generation-settings-view/presentation-pdf-generation-settings-view.component";
import { PresentationRoutingModule } from "./presentation-routing.module";
import { PresentationSaveGuard } from "./presentation-settings-view/presentation-save.guard";
import { PresentationSettingsViewComponent } from "./presentation-settings-view/presentation-settings-view.component";
import { PresentationShareSettingsViewComponent } from "./presentation-share-settings-view/presentation-share-settings-view.component";
import { AddThemeDialogComponent } from "./presentation-themes-view/add-theme-dialog/add-theme-dialog.component";
import { PresentationThemesViewComponent } from "./presentation-themes-view/presentation-themes-view.component";
import { ThemeCssViewComponent } from "./presentation-themes-view/theme-css-view/theme-css-view.component";
import { ThemeEditorViewComponent } from "./presentation-themes-view/theme-editor-view/theme-editor-view.component";
import { ThemeListViewComponent } from "./presentation-themes-view/theme-list-view/theme-list-view.component";
import { ThemePropertiesViewComponent } from "./presentation-themes-view/theme-properties-view/theme-properties-view.component";
import { ThemesSaveGuard } from "./presentation-themes-view/themes-save.guard";
import { PresentationViewsheetToolbarOptionsViewComponent } from "./presentation-viewsheet-toolbar-options-view/presentation-viewsheet-toolbar-options-view.component";
import { ToolbarOptionsTableViewComponent } from "./toolbar-options-table-view/toolbar-options-table-view.component";
import { WebMapSettingsViewComponent } from "./webmap-settings-view/webmap-settings-view.component";
import { WelcomePageSettingsViewComponent } from "./welcome-page-settings-view/welcome-page-settings-view.component";
import { PresentationComposerMessageSettingsViewComponent } from "./presentation-composer-message-settings-view/presentation-composer-message-settings-view.component";
import { ColorPickerModule } from "ngx-color-picker";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      ReactiveFormsModule,
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
      MatNativeDateModule,
      MatOptionModule,
      MatPaginatorModule,
      MatProgressBarModule,
      MatRadioModule,
      MatSelectModule,
      MatSlideToggleModule,
      MatSnackBarModule,
      MatSortModule,
      MatTableModule,
      MatToolbarModule,
      MatTooltipModule,
      MatTreeModule,
      PresentationRoutingModule,
      FileChooserModule,
      ColorPickerModule,
      MessageDialogModule,
      ScrollNavModule,
      TableViewModule,
      ModalHeaderModule,
      MatTabsModule,
      MatSidenavModule,
      MatMenuModule,
      EditorPanelModule
   ],
   declarations: [
      AddFontFaceDialogComponent,
      AddDataSourceTypeDialogComponent,
      DashboardsSettingsSortableTableViewComponent,
      EditFontMappingDialogComponent,
      EditFontsDialogComponent,
      EditPortalTabDialogComponent,
      ExportMenuOptionsViewComponent,
      LookAndFeelSettingsViewComponent,
      PortalIntegrationViewComponent,
      PresentationTimeSettingsViewComponent,
      PresentationDashboardSettingsViewComponent,
      PresentationDataSourceVisibilitySettingsViewComponent,
      PresentationExportMenuSettingsViewComponent,
      PresentationFontMappingSettingsViewComponent,
      PresentationFormatsSettingsViewComponent,
      PresentationLoginBannerSettingsViewComponent,
      PresentationOrgSettingsViewComponent,
      PresentationPdfGenerationSettingsViewComponent,
      PresentationSettingsViewComponent,
      PresentationShareSettingsViewComponent,
      PresentationViewsheetToolbarOptionsViewComponent,
      ToolbarOptionsTableViewComponent,
      WelcomePageSettingsViewComponent,
      AddThemeDialogComponent,
      PresentationThemesViewComponent,
      PresentationNavViewComponent,
      ThemeListViewComponent,
      ThemeEditorViewComponent,
      ThemePropertiesViewComponent,
      ThemeCssViewComponent,
      PresentationComposerMessageSettingsViewComponent,
      WebMapSettingsViewComponent
   ],
   providers: [
      PresentationSaveGuard,
      PresentationOrgSaveGuard,
      ThemesSaveGuard,
      {provide: MatPaginatorIntl, useClass: LocalizedMatPaginator}
   ]
})
export class PresentationModule {
}
