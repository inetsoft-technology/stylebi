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
import {
   Component,
   EventEmitter,
   Input,
   Output,
} from "@angular/core";
import { TableStylePaneModel } from "../../widget/table-style/table-style-pane-model";

@Component({
   selector: "table-style-dialog",
   templateUrl: "table-style-dialog.component.html",
})
export class TableStyleDialog {
   @Input() styleModel: TableStylePaneModel;
   @Output() onCommit: EventEmitter<string> = new EventEmitter<string>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();

   constructor() {
   }

   ok(): void {
      this.onCommit.emit(this.styleModel.tableStyle);
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }
}
