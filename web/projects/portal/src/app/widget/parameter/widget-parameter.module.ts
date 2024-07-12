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
   ParameterDialogComponent
} from "./parameter-dialog/parameter-dialog.component";
import {ParameterPage} from "./parameter-page/parameter-page.component";
import {FormsModule} from "@angular/forms";
import { DateTimeValueDialog } from "./date-time-value-dialog.component";
import { DateTypeEditorModule } from "../date-type-editor/date-type-editor.module";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      DateTypeEditorModule,
   ],
   declarations: [
      DateTimeValueDialog,
      ParameterDialogComponent,
      ParameterPage
   ],
   exports: [
      ParameterDialogComponent,
      ParameterPage
   ],
   providers: [],
})
export class WidgetParameterModule {
}
