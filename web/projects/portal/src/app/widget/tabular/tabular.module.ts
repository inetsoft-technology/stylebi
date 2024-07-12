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
import {CommonModule} from "@angular/common";
import {NgModule} from "@angular/core";
import {
   TabularAutocompleteEditor
} from "./tabular-autocomplete-editor.component";
import {TabularBooleanEditor} from "./tabular-boolean-editor.component";
import {
   TabularColumnDefinitionEditor
} from "./tabular-column-definition-editor.component";
import {TabularDateEditor} from "./tabular-date-editor.component";
import {TabularFileBrowser} from "./tabular-file-browser.component";
import {TabularFileEditor} from "./tabular-file-editor.component";
import {
   TabularHttpParameterEditorComponent
} from "./tabular-http-parameter-editor.component";
import {TabularListEditor} from "./tabular-list-editor.component";
import {TabularNumberEditor} from "./tabular-number-editor.component";
import {
   TabularQueryParameterEditor
} from "./tabular-query-parameter-editor.component";
import {
   TabularRestParametersEditorComponent
} from "./tabular-rest-parameters-editor.component";
import {TabularTagsEditor} from "./tabular-tags-editor.component";
import {TabularTextEditor} from "./tabular-text-editor.component";
import {TabularViewComponent} from "./tabular-view.component";
import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import {NgbTypeaheadModule} from "@ng-bootstrap/ng-bootstrap";
import {ExpandStringModule} from "../expand-string/expand-string.module";
import {
   DateTypeEditorModule
} from "../date-type-editor/date-type-editor.module";
import {TreeModule} from "../tree/tree.module";

@NgModule({
   imports: [
      CommonModule,
      ReactiveFormsModule,
      NgbTypeaheadModule,
      FormsModule,
      ExpandStringModule,
      DateTypeEditorModule,
      TreeModule,
   ],
   declarations: [
      TabularAutocompleteEditor,
      TabularBooleanEditor,
      TabularColumnDefinitionEditor,
      TabularDateEditor,
      TabularFileBrowser,
      TabularFileEditor,
      TabularHttpParameterEditorComponent,
      TabularListEditor,
      TabularNumberEditor,
      TabularQueryParameterEditor,
      TabularRestParametersEditorComponent,
      TabularTagsEditor,
      TabularTextEditor,
      TabularViewComponent
   ],
   exports: [
      TabularAutocompleteEditor,
      TabularBooleanEditor,
      TabularColumnDefinitionEditor,
      TabularDateEditor,
      TabularFileBrowser,
      TabularFileEditor,
      TabularHttpParameterEditorComponent,
      TabularListEditor,
      TabularNumberEditor,
      TabularQueryParameterEditor,
      TabularRestParametersEditorComponent,
      TabularTagsEditor,
      TabularTextEditor,
      TabularViewComponent,
   ],
   providers: [],
})
export class TabularModule {
}
