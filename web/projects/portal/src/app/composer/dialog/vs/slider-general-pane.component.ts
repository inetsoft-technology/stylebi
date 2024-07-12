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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { SliderGeneralPaneModel } from "../../data/vs/slider-general-pane-model";

@Component({
   selector: "slider-general-pane",
   templateUrl: "slider-general-pane.component.html",
})
export class SliderGeneralPane implements OnInit {
   @Input() model: SliderGeneralPaneModel;
   @Input() form: UntypedFormGroup;
   @Input() variableValues: string[];
   @Input() vsId: string = null;
   @Input() columnTreeRoot: TreeNodeModel = null;
   @Input() functionTreeRoot: TreeNodeModel = null;
   @Input() operatorTreeRoot: TreeNodeModel = null;
   @Input() scriptDefinitions: any = null;
   @Output() isInputValid: EventEmitter<boolean> = new EventEmitter<boolean>();

   ngOnInit(): void {
      this.form.addControl("generalForm", new UntypedFormGroup({}));
      this.form.addControl("numericForm", new UntypedFormGroup({}));
      this.form.addControl("sizePositionPaneForm", new UntypedFormGroup({}));
   }

   onValidChange(valid: boolean) {
      this.isInputValid.emit(valid);
   }
}
