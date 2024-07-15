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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { BaseTableCellModel } from "../../../../../vsobjects/model/base-table-cell-model";

@Component({
   selector: "view-sample-data-dialog",
   templateUrl: "view-sample-data-dialog.component.html",
   styleUrls: ["view-sample-data-dialog.component.scss"]
})
export class ViewSampleDataDialog {
   @Input() tableData: BaseTableCellModel[][] = [];
   @Output() onCancel: EventEmitter<any> = new EventEmitter();
   @Output() onCommit: EventEmitter<any> = new EventEmitter();

   cancel() {
      this.onCancel.emit();
   }
}
