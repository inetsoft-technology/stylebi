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
import { ReactiveFormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatDialogModule } from "@angular/material/dialog";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatListModule } from "@angular/material/list";
import { MatProgressBarModule } from "@angular/material/progress-bar";
import { MatSortModule } from "@angular/material/sort";
import { MatTableModule } from "@angular/material/table";
import { FileChooserModule } from "../../../../common/util/file-chooser/file-chooser.module";
import { MessageDialogModule } from "../../../../common/util/message-dialog.module";
import { ModalHeaderModule } from "../../../../common/util/modal-header/modal-header.module";
import { FlatTreeModule } from "../../../../common/util/tree/flat-tree.module";
import { ExportAssetDialogComponent } from "./export-asset-dialog/export-asset-dialog.component";
import { ExportAssetsService } from "./export-assets.service";
import { ImportAssetDialogComponent } from "./import-asset-dialog/import-asset-dialog.component";
import { RequiredAssetListComponent } from "./required-asset-list/required-asset-list.component";
import { SelectAssetsDialogComponent } from "./select-assets-dialog/select-assets-dialog.component";
import { SelectedAssetListComponent } from "./selected-asset-list/selected-asset-list.component";
import { FeatureFlagsModule } from "../../../../../../../shared/feature-flags/feature-flags.module";
import { SelectAssetFolderDialogComponent } from "./select-asset-folder-dialog/select-asset-folder-dialog.component";
import { InputNameDialogComponent } from "./input-name-dialog/input-name-dialog.component";

@NgModule({
   imports: [
      CommonModule,
      ReactiveFormsModule,
      MatButtonModule,
      MatCardModule,
      MatCheckboxModule,
      MatDialogModule,
      MatFormFieldModule,
      MatIconModule,
      MatInputModule,
      MatListModule,
      MatProgressBarModule,
      MatSortModule,
      MatTableModule,
      FileChooserModule,
      FlatTreeModule,
      MessageDialogModule,
      ModalHeaderModule,
      FeatureFlagsModule
   ],
   declarations: [
      ExportAssetDialogComponent,
      ImportAssetDialogComponent,
      RequiredAssetListComponent,
      SelectAssetsDialogComponent,
      SelectedAssetListComponent,
      SelectAssetFolderDialogComponent,
      InputNameDialogComponent
   ],
   exports: [
      ExportAssetDialogComponent,
      ImportAssetDialogComponent
   ],
   providers: [
      ExportAssetsService
   ],
})
export class ImportExportModule {
}
