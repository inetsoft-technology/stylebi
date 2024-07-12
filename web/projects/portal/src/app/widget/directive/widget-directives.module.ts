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
import {DefaultFocusDirective} from "./default-focus.directive";
import {DisableDropDirective} from "./disable-drop.directive";
import {EnterClickDirective} from "./enter-click.directive";
import {EnterSubmitDirective} from "./enter-submit.directive";
import {ExistsDirective} from "./exists-validator.directive";
import {InputTrimDirective} from "./input-trim.directive";
import {MaxNumberDirective} from "./max-number-validator.directive";
import {MinNumberDirective} from "./min-number-validator.directive";
import {OutOfZoneDirective} from "./out-of-zone.directive";
import {ResizableTableDirective} from "./resizable-table.directive";
import {SelectionBoxDirective} from "./selection-box.directive";
import {SortColumnDirective} from "./sort-column.directive";

@NgModule({
   imports: [
      CommonModule,
   ],
   declarations: [
      DefaultFocusDirective,
      DisableDropDirective,
      EnterClickDirective,
      EnterSubmitDirective,
      ExistsDirective,
      InputTrimDirective,
      MaxNumberDirective,
      MinNumberDirective,
      OutOfZoneDirective,
      ResizableTableDirective,
      SelectionBoxDirective,
      SortColumnDirective,
   ],
   exports: [
      DefaultFocusDirective,
      DisableDropDirective,
      EnterClickDirective,
      EnterSubmitDirective,
      ExistsDirective,
      InputTrimDirective,
      MaxNumberDirective,
      MinNumberDirective,
      OutOfZoneDirective,
      ResizableTableDirective,
      SelectionBoxDirective,
      SortColumnDirective,
   ],
   providers: [],
})
export class WidgetDirectivesModule {
}
