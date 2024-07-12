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
   VariableInputDialogModule
} from "../dialog/variable-input-dialog/variable-input-dialog.module";
import { SimpleTableComponent } from "./simple-table.component";
import { NgbTooltip } from "@ng-bootstrap/ng-bootstrap";
import { ScrollModule } from "../scroll/scroll.module";

@NgModule({
   imports: [
      CommonModule,
      NgbTooltip,
      ScrollModule
   ],
   declarations: [
      SimpleTableComponent
   ],
   exports: [
      SimpleTableComponent
   ]
})
export class SimpleTableModule {
}
