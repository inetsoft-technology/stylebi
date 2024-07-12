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
import {DatePickerComponent} from "./date-picker.component";
import {DateTimePickerComponent} from "./date-time-picker.component";
import {DateValueEditorComponent} from "./date-value-editor.component";
import {DynamicValueEditorComponent} from "./dynamic-value-editor.component";
import {
   TimeInstantValueEditorComponent
} from "./time-instant-value-editor.component";
import {TimeValueEditorComponent} from "./time-value-editor.component";
import {TimepickerComponent} from "./timepicker.component";
import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import {MouseEventModule} from "../mouse-event/mouse-event.module";
import {NgbDatepickerModule} from "@ng-bootstrap/ng-bootstrap";
import {
   DynamicComboBoxModule
} from "../dynamic-combo-box/dynamic-combo-box.module";
import {FixedDropdownModule} from "../fixed-dropdown/fixed-dropdown.module";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      MouseEventModule,
      NgbDatepickerModule,
      DynamicComboBoxModule,
      FixedDropdownModule,
      ReactiveFormsModule,
   ],
   declarations: [
      DatePickerComponent,
      DateTimePickerComponent,
      DateValueEditorComponent,
      DynamicValueEditorComponent,
      TimeInstantValueEditorComponent,
      TimeValueEditorComponent,
      TimepickerComponent
   ],
   exports: [
      DatePickerComponent,
      DateTimePickerComponent,
      DateValueEditorComponent,
      DynamicValueEditorComponent,
      TimeInstantValueEditorComponent,
      TimeValueEditorComponent,
      TimepickerComponent
   ],
   providers: [],
})
export class DateTypeEditorModule {
}