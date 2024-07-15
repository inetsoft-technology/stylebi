/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { SpinnerGeneralPaneModel } from "../../data/vs/spinner-general-pane-model";

@Component({
   selector: "spinner-general-pane",
   templateUrl: "spinner-general-pane.component.html",
})
export class SpinnerGeneralPane implements OnInit {
   @Input() model: SpinnerGeneralPaneModel;
   @Input() form: UntypedFormGroup;
   @Input() variableValues: string[];
   @Input() vsId: string = null;
   @Input() columnTreeRoot: TreeNodeModel = null;
   @Input() functionTreeRoot: TreeNodeModel = null;
   @Input() operatorTreeRoot: TreeNodeModel = null;
   @Input() scriptDefinitions: any = null;
   @Output() isInputValid: EventEmitter<boolean> = new EventEmitter<boolean>();

   onValidChange(valid: boolean) {
      this.isInputValid.emit(valid);
   }

   ngOnInit(): void {
      this.form.addControl("generalPropPaneForm", new UntypedFormGroup({}));
      this.form.addControl("numericRangePaneForm", new UntypedFormGroup({}));
      this.form.addControl("sizePositionPaneForm", new UntypedFormGroup({}));
   }
}
