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
import {StandardDialogComponent} from "./standard-dialog.component";
import {DialogButtonsDirective} from "./dialog-buttons.directive";
import {DialogContentDirective} from "./dialog-content.directive";
import {DialogTabDirective} from "./dialog-tab.directive";
import {TabbedDialogComponent} from "./tabbed-dialog.component";
import {ModalHeaderModule} from "../modal-header/modal-header.module";
import {WidgetDirectivesModule} from "../directive/widget-directives.module";
import {NgbNavModule} from "@ng-bootstrap/ng-bootstrap";

@NgModule({
   imports: [
      CommonModule,
      ModalHeaderModule,
      WidgetDirectivesModule,
      NgbNavModule,
   ],
   declarations: [
      DialogButtonsDirective,
      DialogContentDirective,
      DialogTabDirective,
      StandardDialogComponent,
      TabbedDialogComponent
   ],
   exports: [
      DialogButtonsDirective,
      DialogContentDirective,
      DialogTabDirective,
      StandardDialogComponent,
      TabbedDialogComponent
   ],
   providers: [],
})
export class StandardDialogModule {
}
