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
import { MatOptionModule } from "@angular/material/core";
import { MatDialogModule } from "@angular/material/dialog";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatGridListModule } from "@angular/material/grid-list";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatListModule } from "@angular/material/list";
import { MatRadioModule } from "@angular/material/radio";
import { MatSelectModule } from "@angular/material/select";
import { MatSidenavModule } from "@angular/material/sidenav";
import { MatSnackBarModule } from "@angular/material/snack-bar";
import { MatTableModule } from "@angular/material/table";
import { MatTabsModule } from "@angular/material/tabs";
import { MatToolbarModule } from "@angular/material/toolbar";
import { MatTooltipModule } from "@angular/material/tooltip";
import { EditorPanelModule } from "../../../common/util/editor-panel/editor-panel.module";
import { FileChooserModule } from "../../../common/util/file-chooser/file-chooser.module";
import { FlatTreeModule } from "../../../common/util/tree/flat-tree.module";
import { TopScrollModule } from "../../../top-scroll/top-scroll.module";
import { ContentDataSpaceViewComponent } from "../content-data-space-view/content-data-space-view.component";
import { DataSpaceEditorPageComponent } from "./data-space-editor-page/data-space-editor-page.component";
import {
   DataSpaceFileSettingsViewComponent,
   DeleteDialog
} from "./data-space-file-settings-view/data-space-file-settings-view.component";
import { DataSpaceFolderSettingsViewComponent } from "./data-space-folder-settings-view/data-space-folder-settings-view.component";
import { DataSpaceRoutingModule } from "./data-space-routing.module";
import { DataSpaceTreeViewComponent } from "./data-space-tree-view/data-space-tree-view.component";
import { TextFileContentViewComponent } from "./text-file-content-view/text-file-content-view.component";
import { LoadingSpinnerModule } from "../../../common/util/loading-spinner/loading-spinner.module";
import {ModalHeaderModule} from "../../../common/util/modal-header/modal-header.module";

@NgModule({
    imports: [
        CommonModule,
        FormsModule,
        ReactiveFormsModule,
        MatButtonModule,
        MatCardModule,
        MatCheckboxModule,
        MatDialogModule,
        MatFormFieldModule,
        MatGridListModule,
        MatIconModule,
        MatInputModule,
        MatListModule,
        MatOptionModule,
        MatRadioModule,
        MatSelectModule,
        MatSidenavModule,
        MatSnackBarModule,
        MatTableModule,
        MatTabsModule,
        MatToolbarModule,
        MatTooltipModule,
        DataSpaceRoutingModule,
        EditorPanelModule,
        FlatTreeModule,
        FileChooserModule,
        TopScrollModule,
        LoadingSpinnerModule,
        ModalHeaderModule
    ],
   declarations: [
      ContentDataSpaceViewComponent,
      DataSpaceEditorPageComponent,
      DataSpaceFileSettingsViewComponent,
      DataSpaceFolderSettingsViewComponent,
      DataSpaceTreeViewComponent,
      DeleteDialog,
      TextFileContentViewComponent
   ]
})
export class DataSpaceModule {
}
