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
import { MatAutocompleteModule } from "@angular/material/autocomplete";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatDialogModule } from "@angular/material/dialog";
import { MatDividerModule } from "@angular/material/divider";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatListModule } from "@angular/material/list";
import { MatProgressBarModule } from "@angular/material/progress-bar";
import { MatProgressSpinnerModule } from "@angular/material/progress-spinner";
import { MatRadioModule } from "@angular/material/radio";
import { MatSnackBarModule } from "@angular/material/snack-bar";
import { MatSortModule } from "@angular/material/sort";
import { MatStepperModule } from "@angular/material/stepper";
import { MatTableModule } from "@angular/material/table";
import { FeatureFlagsModule } from "../../../../../../shared/feature-flags/feature-flags.module";
import { FileChooserModule } from "../../../common/util/file-chooser/file-chooser.module";
import { LoadingSpinnerModule } from "../../../common/util/loading-spinner/loading-spinner.module";
import { TopScrollModule } from "../../../top-scroll/top-scroll.module";
import { ContentDriversAndPluginsViewComponent } from "../content-drivers-and-plugins-view/content-drivers-and-plugins-view.component";
import { DriversAndPluginsRoutingModule } from "./drivers-and-plugins-routing.module";
import { CreateDriverDialogComponent } from "./plugins-view/create-driver-dialog/create-driver-dialog.component";
import { PluginsViewComponent, UninstallDialog } from "./plugins-view/plugins-view.component";
import { ModalHeaderModule } from "../../../common/util/modal-header/modal-header.module";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      ReactiveFormsModule,
      MatIconModule,
      MatInputModule,
      MatFormFieldModule,
      MatCardModule,
      MatListModule,
      MatDividerModule,
      MatTableModule,
      MatSortModule,
      MatIconModule,
      MatButtonModule,
      MatProgressBarModule,
      MatCheckboxModule,
      MatSnackBarModule,
      MatDialogModule,
      DriversAndPluginsRoutingModule,
      FileChooserModule,
      TopScrollModule,
      FeatureFlagsModule,
      MatStepperModule,
      MatRadioModule,
      LoadingSpinnerModule,
      MatAutocompleteModule,
      MatProgressSpinnerModule,
      ModalHeaderModule
   ],
   declarations: [
      PluginsViewComponent,
      UninstallDialog,
      ContentDriversAndPluginsViewComponent,
      CreateDriverDialogComponent
   ]
})
export class DriversAndPluginsModule {
}
