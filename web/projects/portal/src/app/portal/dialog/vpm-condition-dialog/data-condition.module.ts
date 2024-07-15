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
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { ConditionModule } from "../../../widget/condition/condition.module";
import { DateTypeEditorModule } from "../../../widget/date-type-editor/date-type-editor.module";
import { FixedDropdownModule } from "../../../widget/fixed-dropdown/fixed-dropdown.module";
import { ModalHeaderModule } from "../../../widget/modal-header/modal-header.module";
import {
   ClauseValueTypePipe
} from "../../data/model/datasources/database/vpm/condition/clause/clause-value-type.pipe";
import {
   ClausePipe
} from "../../data/model/datasources/database/vpm/condition/clause/clause.pipe";
import {
   ConjunctionPipe
} from "../../data/model/datasources/database/vpm/condition/conjunction/conjunction.pipe";
import { PortalAppModule } from "../../portal-app.module";
import { VPMConditionDialog } from "./vpm-condition-dialog.component";
import {
   OneOfVpmConditionEditor
} from "./vpm-condition-pane/vpm-condition-item-pane/vpm-condition-editor/one-of-vpm-condition-editor.component";
import {
   VPMConditionEditor
} from "./vpm-condition-pane/vpm-condition-item-pane/vpm-condition-editor/vpm-condition-editor.component";
import {
   VPMFieldEditorComponent
} from "./vpm-condition-pane/vpm-condition-item-pane/vpm-condition-editor/vpm-field-editor/vpm-field-editor.component";
import {
   VpmSubqueryEditorComponent
} from "./vpm-condition-pane/vpm-condition-item-pane/vpm-condition-editor/vpm-subquery-editor/vpm-subquery-editor.component";
import {
   VPMValueEditorComponent
} from "./vpm-condition-pane/vpm-condition-item-pane/vpm-condition-editor/vpm-value-editor/vpm-value-editor.component";
import {
   VPMVariableEditor
} from "./vpm-condition-pane/vpm-condition-item-pane/vpm-condition-editor/vpm-variable-editor/vpm-variable-editor.component";
import { VPMConditionItemPane } from "./vpm-condition-pane/vpm-condition-item-pane/vpm-condition-item-pane.component";
import {
   VPMTrinaryConditionEditor
} from "./vpm-condition-pane/vpm-condition-item-pane/vpm-trinary-condition-editor/vpm-trinary-condition-editor.component";
import { VPMConditionPane } from "./vpm-condition-pane/vpm-condition-pane.component";

@NgModule({
   imports: [
      CommonModule,
      NgbModule,
      FormsModule,
      ReactiveFormsModule,
      ConditionModule,
      FixedDropdownModule,
      DateTypeEditorModule,
      ModalHeaderModule,
   ],
   declarations: [
      VPMConditionEditor,
      VPMConditionItemPane,
      VPMConditionPane,
      VPMFieldEditorComponent,
      VPMTrinaryConditionEditor,
      VPMValueEditorComponent,
      VPMVariableEditor,
      OneOfVpmConditionEditor,
      ClausePipe,
      ConjunctionPipe,
      ClauseValueTypePipe,
      VpmSubqueryEditorComponent,
      VPMConditionDialog,
   ],
   exports: [
      VPMConditionEditor,
      VPMConditionItemPane,
      VPMConditionPane,
      VPMFieldEditorComponent,
      VPMTrinaryConditionEditor,
      VPMValueEditorComponent,
      VPMVariableEditor,
      OneOfVpmConditionEditor,
      ClausePipe,
      ConjunctionPipe,
      ClauseValueTypePipe,
      VpmSubqueryEditorComponent,
      VPMConditionDialog,
   ]
})
export class DataConditionModule {
}
