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
import { MatButtonModule } from "@angular/material/button";
import { MatButtonToggleModule } from "@angular/material/button-toggle";
import { MatGridListModule } from "@angular/material/grid-list";
import { DynamicValueEditorComponent } from "./dynamic-value-editor.component";
import { NgModule } from "@angular/core";
import { MatInputModule } from "@angular/material/input";
import { CommonModule } from "@angular/common";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatIconModule } from "@angular/material/icon";
import { MatMenuModule } from "@angular/material/menu";
import { MatAutocompleteModule } from "@angular/material/autocomplete";
import { DynamicComboBoxComponent } from "./dynamic-combo-box.component";
import { FormulaEditorDialogComponent } from "./formula-editor-dialog.component";
import { ModalHeaderModule } from "../common/util/modal-header/modal-header.module";
import { MatDialogModule } from "@angular/material/dialog";
import { ScriptPaneComponent } from "./script-pane.component";
import { FlatTreeModule } from "../common/util/tree/flat-tree.module";
import { FormulaEditorService } from "./formula-editor.service";
import { ScriptTreeViewComponent } from "./script-tree-view.component";
import { MatProgressBarModule } from "@angular/material/progress-bar";
import { MatDatepickerModule } from "@angular/material/datepicker";
import { DatepickerModule } from "../common/util/datepicker/datepicker.module";
import { MatTooltipModule } from "@angular/material/tooltip";
import { CodemirrorService } from "../../../../shared/util/codemirror/codemirror.service";
import { DefaultCodemirrorService } from "../../../../shared/util/codemirror/default-codemirror.service";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      ReactiveFormsModule,
      MatAutocompleteModule,
      MatButtonModule,
      MatMenuModule,
      MatIconModule,
      MatInputModule,
      ModalHeaderModule,
      MatDialogModule,
      MatGridListModule,
      MatTooltipModule,
      FlatTreeModule,
      MatProgressBarModule,
      MatDatepickerModule,
      DatepickerModule
   ],
   exports: [
      MatButtonModule,
      MatButtonToggleModule,
      MatGridListModule,
      DynamicValueEditorComponent,
      DynamicComboBoxComponent,
      FormulaEditorDialogComponent,
      ScriptPaneComponent,
      ScriptTreeViewComponent
   ],
   declarations: [
      DynamicValueEditorComponent,
      DynamicComboBoxComponent,
      FormulaEditorDialogComponent,
      ScriptPaneComponent,
      ScriptTreeViewComponent
   ],
   providers: [
      FormulaEditorService,
      {
         provide: CodemirrorService,
         useClass: DefaultCodemirrorService,
         deps: []
      }
   ]
})
export class WidgetModule {
}
