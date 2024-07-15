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
import {CommonModule} from "@angular/common";
import {NgModule} from "@angular/core";
import {VariableListDialog} from "./variable-list-dialog.component";
import {ModalHeaderModule} from "../../modal-header/modal-header.module";
import {
   VariableListEditor
} from "./variable-list-editor/variable-list-editor.component";
import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import {
   LargeFormFieldModule
} from "../../large-form-field/large-form-field.module";
import {
   VariableValueEditor
} from "./variable-value-editor/variable-value-editor.component";
import {
   DateTypeEditorModule
} from "../../date-type-editor/date-type-editor.module";

@NgModule({
   imports: [
      CommonModule,
      ModalHeaderModule,
      FormsModule,
      LargeFormFieldModule,
      ReactiveFormsModule,
      DateTypeEditorModule,
   ],
   declarations: [
      VariableListDialog,
      VariableListEditor,
      VariableValueEditor
   ],
   exports: [
      VariableListDialog,
      VariableListEditor,
      VariableValueEditor
   ],
   providers: [],
})
export class VariableListDialogModule {
}
