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
import { CommonModule } from "@angular/common";
import { NgModule } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { NgbTooltipModule } from "@ng-bootstrap/ng-bootstrap";
import { FormatModule } from "../../../format/format.module";
import { WidgetDirectivesModule } from "../../../widget/directive/widget-directives.module";
import { ModalHeaderModule } from "../../../widget/modal-header/modal-header.module";
import { NotificationsModule } from "../../../widget/notifications/notifications.module";
import { TableStyleModule } from "../../../widget/table-style/table-style.module";
import { TableStyleDialog } from "../../dialog/table-style-dialog.component";
import { PreviewTableComponent } from "./preview-table.component";
import { VSPreviewTable } from "./vs-preview-table.component";
import { ScrollModule } from "../../../widget/scroll/scroll.module";
import { FixedDropdownModule } from "../../../widget/fixed-dropdown/fixed-dropdown.module";
import { MouseEventModule } from "../../../widget/mouse-event/mouse-event.module";

@NgModule({
   imports: [
      CommonModule,
      FormatModule,
      FormsModule,
      ModalHeaderModule,
      NotificationsModule,
      TableStyleModule,
      NgbTooltipModule,
      WidgetDirectivesModule,
      ScrollModule,
      FixedDropdownModule,
      MouseEventModule
   ],
   declarations: [PreviewTableComponent, VSPreviewTable, TableStyleDialog],
   exports: [
      PreviewTableComponent, VSPreviewTable, TableStyleDialog
   ],
   providers: [],
})
export class PreviewTableModule {
}
