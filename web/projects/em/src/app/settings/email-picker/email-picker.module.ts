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
import { MatButtonModule } from "@angular/material/button";
import { MatDialogModule } from "@angular/material/dialog";
import { MatDividerModule } from "@angular/material/divider";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatListModule } from "@angular/material/list";
import { MatSelectModule } from "@angular/material/select";
import { MatTreeModule } from "@angular/material/tree";
import { ModalHeaderModule } from "../../common/util/modal-header/modal-header.module";
import { EmailListDialogComponent } from "./email-list-dialog/email-list-dialog.component";
import { EmailPickerComponent } from "./email-picker.component";

@NgModule({
   imports: [
      CommonModule,
      MatDialogModule,
      MatButtonModule,
      FormsModule,
      ReactiveFormsModule,
      MatInputModule,
      MatIconModule,
      MatTreeModule,
      MatSelectModule,
      MatListModule,
      MatDividerModule,
      MatAutocompleteModule,
      ModalHeaderModule
   ],
   declarations: [
      EmailPickerComponent,
      EmailListDialogComponent
   ],
   exports: [
      EmailPickerComponent,
      EmailListDialogComponent
   ]
})
export class EmailPickerModule {
}
