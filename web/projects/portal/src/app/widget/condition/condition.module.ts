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
import {BinaryConditionEditor} from "./binary-condition-editor.component";
import {BooleanValueEditor} from "./boolean-value-editor.component";
import {CharValueEditor} from "./char-value-editor.component";
import {ConditionPipe} from "./condition.pipe";
import {ConditionDialog} from "./condition-dialog.component";
import {ConditionEditor} from "./condition-editor.component";
import {ConditionFieldComboComponent} from "./condition-field-combo.component";
import {
   ConditionFieldComboListComponent
} from "./condition-field-combo-list.component";
import {ConditionItemPane} from "./condition-item-pane.component";
import {ConditionList} from "./condition-list.component";
import {ConditionOperationPipe} from "./condition-operation.pipe";
import {ConditionPane} from "./condition-pane.component";
import {ConditionValuePipe} from "./condition-value.pipe";
import {ConditionValueTypePipe} from "./condition-value-type.pipe";
import {DateInValueEditor} from "./date-in-value-editor.component";
import {ExpressionEditor} from "./expression-editor.component";
import {FieldEditor} from "./field-editor.component";
import {JunctionOperatorPipe} from "./junction-operator.pipe";
import {NumberValueEditor} from "./number-value-editor.component";
import {OneOfConditionEditor} from "./one-of-condition-editor.component";
import {SessionDataEditor} from "./session-data-editor.component";
import {SimpleConditionPane} from "./simple-condition-pane.component";
import {StringValueEditor} from "./string-value-editor.component";
import {SubqueryDialog} from "./subquery-dialog.component";
import {SubqueryEditor} from "./subquery-editor.component";
import {TopNEditor} from "./top-n-editor.component";
import {ValueEditor} from "./value-editor.component";
import {VariableEditor} from "./variable-editor.component";
import {ConditionDialogService} from "./condition-dialog.service";
import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import {ModalHeaderModule} from "../modal-header/modal-header.module";
import {FixedDropdownModule} from "../fixed-dropdown/fixed-dropdown.module";
import {TreeModule} from "../tree/tree.module";
import {PipeModule} from "../pipe/pipe.module";
import {FormulaEditorModule} from "../formula-editor/formula-editor.module";
import {
   DateTypeEditorModule
} from "../date-type-editor/date-type-editor.module";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      ModalHeaderModule,
      FixedDropdownModule,
      TreeModule,
      PipeModule,
      FormulaEditorModule,
      DateTypeEditorModule,
      ReactiveFormsModule,
   ],
   declarations: [
      BinaryConditionEditor,
      BooleanValueEditor,
      CharValueEditor,
      ConditionPipe,
      ConditionDialog,
      ConditionEditor,
      ConditionFieldComboComponent,
      ConditionFieldComboListComponent,
      ConditionItemPane,
      ConditionList,
      ConditionOperationPipe,
      ConditionPane,
      ConditionValuePipe,
      ConditionValueTypePipe,
      DateInValueEditor,
      ExpressionEditor,
      FieldEditor,
      JunctionOperatorPipe,
      NumberValueEditor,
      OneOfConditionEditor,
      SessionDataEditor,
      SimpleConditionPane,
      StringValueEditor,
      SubqueryDialog,
      SubqueryEditor,
      TopNEditor,
      ValueEditor,
      VariableEditor
   ],
   exports: [
      BinaryConditionEditor,
      BooleanValueEditor,
      CharValueEditor,
      ConditionPipe,
      ConditionDialog,
      ConditionEditor,
      ConditionFieldComboComponent,
      ConditionFieldComboListComponent,
      ConditionItemPane,
      ConditionList,
      ConditionOperationPipe,
      ConditionPane,
      ConditionValuePipe,
      ConditionValueTypePipe,
      DateInValueEditor,
      ExpressionEditor,
      FieldEditor,
      JunctionOperatorPipe,
      NumberValueEditor,
      OneOfConditionEditor,
      SessionDataEditor,
      SimpleConditionPane,
      StringValueEditor,
      SubqueryDialog,
      SubqueryEditor,
      TopNEditor,
      ValueEditor,
      VariableEditor
   ],
   providers: [
      ConditionDialogService
   ],
})
export class ConditionModule {
}
