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
import { FormsModule } from "@angular/forms";
import { FixedDropdownModule } from "../fixed-dropdown/fixed-dropdown.module";
import { ColorComponentEditor } from "./color-component-editor.component";
import { ColorDropdown } from "./color-dropdown.component";
import { ColorEditorDialog } from "./color-editor-dialog.component";
import { ColorEditor } from "./color-editor.component";
import { ColorMap } from "./color-map.component";
import { ColorPicker } from "./color-picker.component";
import { ColorSlider } from "./color-slider.component";
import { ColorPane } from "./cp-color-pane.component";
import { GradientColorItem } from "./gradient-color-item.component";
import { GradientColorPane } from "./gradient-color-pane.component";
import { GradientColorPicker } from "./gradient-color-picker.component";
import { MouseEventModule } from "../mouse-event/mouse-event.module";
import { RecentColorService } from "./recent-color.service";

@NgModule({
   imports: [
      CommonModule,
      FormsModule,
      FixedDropdownModule,
      MouseEventModule,
   ],
   declarations: [
      ColorComponentEditor,
      ColorDropdown,
      ColorEditor,
      ColorEditorDialog,
      ColorMap,
      ColorPicker,
      ColorSlider,
      ColorPane,
      GradientColorItem,
      GradientColorPane,
      GradientColorPicker
   ],
   exports: [
      ColorComponentEditor,
      ColorDropdown,
      ColorEditor,
      ColorEditorDialog,
      ColorMap,
      ColorPicker,
      ColorSlider,
      ColorPane,
      GradientColorItem,
      GradientColorPane,
      GradientColorPicker
   ],
   providers: [
      RecentColorService,
   ],
})
export class ColorPickerModule {
}
