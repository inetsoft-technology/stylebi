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
import { MatButtonToggleModule } from "@angular/material/button-toggle";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatNativeDateModule } from "@angular/material/core";
import { MatDatepickerModule } from "@angular/material/datepicker";
import { MatDialogModule } from "@angular/material/dialog";
import { MatDividerModule } from "@angular/material/divider";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatListModule } from "@angular/material/list";
import { MatMenuModule } from "@angular/material/menu";
import { MatPaginatorIntl, MatPaginatorModule } from "@angular/material/paginator";
import { MatRadioModule } from "@angular/material/radio";
import { MatSelectModule } from "@angular/material/select";
import { MatSlideToggleModule } from "@angular/material/slide-toggle";
import { MatSnackBarModule } from "@angular/material/snack-bar";
import { MatSortModule } from "@angular/material/sort";
import { MatTableModule } from "@angular/material/table";
import { MatToolbarModule } from "@angular/material/toolbar";
import { MatTooltipModule } from "@angular/material/tooltip";
import { LocalizedMatPaginator } from "../../../../../shared/util/localized-mat-paginator";
import { EditorPanelModule } from "../../common/util/editor-panel/editor-panel.module";
import { ErrorHandlerService } from "../../common/util/error/error-handler.service";
import { LoadingSpinnerModule } from "../../common/util/loading-spinner/loading-spinner.module";
import { MessageDialogModule } from "../../common/util/message-dialog.module";
import { ModalHeaderModule } from "../../common/util/modal-header/modal-header.module";
import { ScrollNavModule } from "../../common/util/scroll-nav/scroll-nav.module";
import { EmailPickerModule } from "../email-picker/email-picker.module";
import { CacheSettingsViewComponent } from "./cache-settings-view/cache-settings-view.component";
import {
   BackupDialog,
   DataSpaceSettingsViewComponent,
} from "./data-space-settings-view/data-space-settings-view.component";
import { EmailSettingsViewComponent } from "./email-settings-view/email-settings-view.component";
import { GeneralRoutingModule } from "./general-routing.module";
import { GeneralSaveGuard } from "./general-settings-page/general-save.guard";
import { GeneralSettingsPageComponent } from "./general-settings-page/general-settings-page.component";
import { EditLicenseKeyDialogComponent } from "./license-key-settings-view/edit-license-key-dialog/edit-license-key-dialog.component";
import { LicenseKeyListComponent } from "./license-key-settings-view/license-key-list/license-key-list.component";
import { LicenseKeySettingsViewComponent } from "./license-key-settings-view/license-key-settings-view.component";
import { LocalizationDialogComponent } from "./localization-settings-view/localization-dialog/localization-dialog.component";
import { LocalizationSettingsViewComponent } from "./localization-settings-view/localization-settings-view.component";
import { MVSettingsViewComponent } from "./mv-settings-view/mv-settings-view.component";
import { PerformanceSettingsViewComponent } from "./performance-settings-view/performance-settings-view.component";

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
      MatIconModule,
      MatInputModule,
      MatListModule,
      MatMenuModule,
      MatNativeDateModule,
      MatPaginatorModule,
      MatRadioModule,
      MatSelectModule,
      MatSlideToggleModule,
      MatSnackBarModule,
      MatSortModule,
      MatTableModule,
      MatToolbarModule,
      MatTooltipModule,
      EditorPanelModule,
      EmailPickerModule,
      GeneralRoutingModule,
      MessageDialogModule,
      ScrollNavModule,
      LoadingSpinnerModule,
      ModalHeaderModule
   ],
   declarations: [
      BackupDialog,
      CacheSettingsViewComponent,
      DataSpaceSettingsViewComponent,
      EditLicenseKeyDialogComponent,
      EmailSettingsViewComponent,
      GeneralSettingsPageComponent,
      LicenseKeyListComponent,
      LicenseKeySettingsViewComponent,
      LocalizationDialogComponent,
      LocalizationSettingsViewComponent,
      MVSettingsViewComponent,
      PerformanceSettingsViewComponent
   ],
   providers: [
      ErrorHandlerService,
      MatDatepickerModule,
      GeneralSaveGuard,
      {provide: MatPaginatorIntl, useClass: LocalizedMatPaginator},
   ]
})
export class GeneralModule {
}
