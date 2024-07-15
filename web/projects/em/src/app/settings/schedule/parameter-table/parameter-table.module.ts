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
import { MatDatepickerModule } from "@angular/material/datepicker";
import { MatDialogModule } from "@angular/material/dialog";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatRadioModule } from "@angular/material/radio";
import { MatSelectModule } from "@angular/material/select";
import { MatTableModule } from "@angular/material/table";
import { CustomValueAccessorModule } from "../../../common/custom-value-accessor/custom-value-accessor.module";
import { MessageDialogModule } from "../../../common/util/message-dialog.module";
import { ModalHeaderModule } from "../../../common/util/modal-header/modal-header.module";
import { AddParameterDialogComponent } from "../add-parameter-dialog/add-parameter-dialog.component";
import { ParameterTableComponent } from "./parameter-table.component";
import { WidgetModule } from "../../../widget/widget.module";

@NgModule({
   imports: [
      CommonModule,
      CustomValueAccessorModule,
      FormsModule,
      ReactiveFormsModule,
      MatAutocompleteModule,
      MatButtonModule,
      MatCardModule,
      MatCheckboxModule,
      MatDatepickerModule,
      MatDialogModule,
      MatFormFieldModule,
      MatIconModule,
      MatInputModule,
      MatRadioModule,
      MatSelectModule,
      MatTableModule,
      MessageDialogModule,
      ModalHeaderModule,
      WidgetModule
   ],
   exports: [
      AddParameterDialogComponent,
      ParameterTableComponent
   ],
   declarations: [
      ParameterTableComponent,
      AddParameterDialogComponent
   ]
})
export class ParameterTableModule {
}