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
import { MatTableModule } from "@angular/material/table";
import { MatTabsModule } from "@angular/material/tabs";
import { MessageDialogModule } from "../../common/util/message-dialog.module";
import { ModalHeaderModule } from "../../common/util/modal-header/modal-header.module";
import { TableViewModule } from "../../common/util/table/table-view.module";
import { TopScrollModule } from "../../top-scroll/top-scroll.module";
import { ParameterTableModule } from "../schedule/parameter-table/parameter-table.module";
import { ContentRoutingModule } from "./content-routing.module";
import { ContentSettingsViewComponent } from "./content-settings-view/content-settings-view.component";
import { DataSpaceUploadDialogComponent } from "./content-data-space-view/data-space-upload-dialog/data-space-upload-dialog.component";
import { FileChooserModule } from "../../common/util/file-chooser/file-chooser.module";

@NgModule({
    imports: [
        CommonModule,
        ContentRoutingModule,
        FormsModule,
        MatButtonModule,
        MatCardModule,
        MatCheckboxModule,
        MatDialogModule,
        MatFormFieldModule,
        MatIconModule,
        MatInputModule,
        MatOptionModule,
        MatRadioModule,
        MatSelectModule,
        MatTableModule,
        MatTabsModule,
        MatListModule,
        MatGridListModule,
        ParameterTableModule,
        ReactiveFormsModule,
        MessageDialogModule,
        TableViewModule,
        TopScrollModule,
        FileChooserModule,
        ModalHeaderModule
    ],
   declarations: [
      ContentSettingsViewComponent,
      DataSpaceUploadDialogComponent
   ]
})
export class ContentModule {
}
