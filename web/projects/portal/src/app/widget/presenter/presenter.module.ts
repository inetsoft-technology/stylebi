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
import {CommonModule} from "@angular/common";
import {NgModule} from "@angular/core";
import {EditImageDialog} from "./edit-image-dialog.component";
import {FormatPresenterPane} from "./format-presenter-pane.component";
import {PresenterPropertyDialog} from "./presenter-property-dialog.component";
import {HelpLinkModule} from "../help-link/help-link.module";
import {WidgetFormatModule} from "../format/widget-format.module";
import {ColorPickerModule} from "../color-picker/color-picker.module";
import {FontPaneModule} from "../font-pane/font-pane.module";
import {FormsModule} from "@angular/forms";
import {WidgetDirectivesModule} from "../directive/widget-directives.module";
import {ImageEditorModule} from "../image-editor/image-editor.module";
import {TreeModule} from "../tree/tree.module";

@NgModule({
   imports: [
      CommonModule,
      HelpLinkModule,
      WidgetFormatModule,
      ColorPickerModule,
      FontPaneModule,
      FormsModule,
      WidgetDirectivesModule,
      ImageEditorModule,
      TreeModule,
   ],
   declarations: [
      EditImageDialog,
      FormatPresenterPane,
      PresenterPropertyDialog
   ],
   exports: [
      EditImageDialog,
      FormatPresenterPane,
      PresenterPropertyDialog
   ],
   providers: [],
})
export class PresenterModule {
}
