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
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbDropdownModule } from "@ng-bootstrap/ng-bootstrap";
import { WidgetDirectivesModule } from "../directive/widget-directives.module";
import { AlphaDropdown } from "./alpha-dropdown.component";
import { FormatCSSPane } from "./format-css-pane.component";
import { FormatPane } from "./format-pane.component";
import { GridLineDropdown } from "./grid-line-dropdown.component";
import { LineArrowTypeDropdown } from "./line-arrow-type-dropdown.component";
import { RadiusDropdown } from "./radius-dropdown.component";
import { RotationRadioGroup } from "./rotation-radio-group.component";
import { StyleDropdown } from "./style-dropdown.component";
import {FixedDropdownModule} from "../fixed-dropdown/fixed-dropdown.module";
import { MouseEventModule } from "../mouse-event/mouse-event.module";

@NgModule({
   imports: [
      CommonModule,
      WidgetDirectivesModule,
      ReactiveFormsModule,
      FormsModule,
      NgbDropdownModule,
      FixedDropdownModule,
      MouseEventModule,
   ],
   declarations: [
      AlphaDropdown,
      FormatCSSPane,
      FormatPane,
      GridLineDropdown,
      LineArrowTypeDropdown,
      RadiusDropdown,
      RotationRadioGroup,
      StyleDropdown
   ],
   exports: [
      AlphaDropdown,
      FormatCSSPane,
      FormatPane,
      GridLineDropdown,
      LineArrowTypeDropdown,
      RadiusDropdown,
      RotationRadioGroup,
      StyleDropdown
   ],
   providers: [],
})
export class WidgetFormatModule {
}
