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
import { Component, Input, Output, EventEmitter } from "@angular/core";
import { VSSortingDialogModel } from "../../data/vs/vs-sorting-dialog-model";

@Component({
   selector: "vs-sorting-dialog",
   templateUrl: "vs-sorting-dialog.component.html",
})
export class VSSortingDialog {
   @Input() model: VSSortingDialogModel;
   @Output() onCommit: EventEmitter<VSSortingDialogModel> =
      new EventEmitter<VSSortingDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   controller: string = "../api/composer/vs/vs-sorting-dialog-model";

   close(): void {
      this.onCancel.emit("cancel");
   }

   ok() {
      this.onCommit.emit(this.model);
   }
}
