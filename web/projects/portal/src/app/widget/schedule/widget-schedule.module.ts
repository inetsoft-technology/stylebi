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
import {CSVConfigPane} from "./csv-config-pane.component";
import {SimpleScheduleDialog} from "./simple-schedule-dialog.component";
import {StartTimeEditor} from "./start-time-editor.component";
import {WidgetDirectivesModule} from "../directive/widget-directives.module";
import {FormsModule, ReactiveFormsModule} from "@angular/forms";
import {HelpLinkModule} from "../help-link/help-link.module";
import {
   NgbTimepickerModule,
   NgbTypeaheadModule
} from "@ng-bootstrap/ng-bootstrap";
import {EmailDialogModule} from "../email-dialog/email-dialog.module";

@NgModule({
   imports: [
      CommonModule,
      WidgetDirectivesModule,
      FormsModule,
      HelpLinkModule,
      ReactiveFormsModule,
      NgbTypeaheadModule,
      EmailDialogModule,
      NgbTimepickerModule,
   ],
   declarations: [
      CSVConfigPane,
      SimpleScheduleDialog,
      StartTimeEditor
   ],
   exports: [
      CSVConfigPane,
      SimpleScheduleDialog,
      StartTimeEditor
   ],
   providers: [],
})
export class WidgetScheduleModule {
}
