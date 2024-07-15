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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { ComboboxGeneralPaneModel } from "../../data/vs/combobox-general-pane-model";

@Component({
   selector: "combobox-general-pane",
   templateUrl: "combobox-general-pane.component.html",
})
export class ComboboxGeneralPane implements OnInit {
   @Input() model: ComboboxGeneralPaneModel;
   @Input() form: UntypedFormGroup;
   @Input() runtimeId: string;
   @Input() variableValues: string[];
   @Input() columnTreeRoot: TreeNodeModel = null;
   @Input() functionTreeRoot: TreeNodeModel = null;
   @Input() operatorTreeRoot: TreeNodeModel = null;
   @Input() scriptDefinitions: any = null;
   @Output() isInputValid: EventEmitter<boolean> = new EventEmitter<boolean>();

   initForm(): void {
      this.form.addControl("generalForm", new UntypedFormGroup({}));
      this.form.addControl("sizePositionPaneForm", new UntypedFormGroup({}));
   }

   ngOnInit(): void {
      this.initForm();
   }

   onValidChange(valid: boolean) {
      this.isInputValid.emit(valid);
   }
}
