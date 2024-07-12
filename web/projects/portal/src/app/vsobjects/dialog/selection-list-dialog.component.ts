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
import { Component, ViewChild, Input, Output, EventEmitter } from "@angular/core";
import { SelectionListDialogModel } from "../model/selection-list-dialog-model";
import { SelectionListEditor } from "./selection-list-editor.component";

@Component({
   selector: "selection-list-dialog",
   templateUrl: "selection-list-dialog.component.html",
})
export class SelectionListDialog {
   @ViewChild(SelectionListEditor) selectionListEditor: SelectionListEditor;
   @Input() model: SelectionListDialogModel;
   @Input() runtimeId: string;
   @Input() showApplySelection: boolean = true;
   @Output() onCommit = new EventEmitter<SelectionListDialogModel>();
   @Output() onCancel = new EventEmitter<string>();

   ok(): void {
      this.selectionListEditor.updateModel();
      this.onCommit.emit(this.model);
   }

   close(): void {
      this.onCancel.emit("cancel");
   }
}
