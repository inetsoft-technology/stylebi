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
import { Component, Output, EventEmitter, Input } from "@angular/core";
import { TitleFormatDialogModel } from "../model/dialog/title-format-dialog-model";

@Component({
   selector: "title-format-dialog",
   templateUrl: "title-format-dialog.component.html",
})
export class TitleFormatDialog {
   @Input() model: TitleFormatDialogModel;
   @Input() variableValues: string[] = [];
   @Input() viewer: boolean = false;
   @Input() vsId: string = null;
   formValid = () => this.isValid();
   @Output() onCommit: EventEmitter<TitleFormatDialogModel> =
      new EventEmitter<TitleFormatDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @Output() onApply = new EventEmitter<{collapse: boolean, result: any}>();

   ok(): void {
      this.onCommit.emit(this.model);
   }

   apply(event: boolean): void {
      this.onApply.emit({collapse: event, result: this.model});
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   isValid() {
      return true;
   }
}
