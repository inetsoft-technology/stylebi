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
import { Component, Input, OnInit } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { RadioButtonGeneralPaneModel } from "../../data/vs/radiobutton-general-pane-model";

@Component({
   selector: "radiobutton-general-pane",
   templateUrl: "radiobutton-general-pane.component.html",
})
export class RadioButtonGeneralPane implements OnInit {
   @Input() model: RadioButtonGeneralPaneModel;
   @Input() form: UntypedFormGroup;
   @Input() runtimeId: string;
   @Input() variableValues: string[];
   @Input() columnTreeRoot: TreeNodeModel = null;
   @Input() functionTreeRoot: TreeNodeModel = null;
   @Input() operatorTreeRoot: TreeNodeModel = null;
   @Input() scriptDefinitions: any = null;

   ngOnInit() {
      this.form.addControl("generalPropPaneForm", new UntypedFormGroup({}));
      this.form.addControl("titlePropPaneForm", new UntypedFormGroup({}));
      this.form.addControl("listValuesPaneForm", new UntypedFormGroup({}));
      this.form.addControl("sizePositionPaneForm", new UntypedFormGroup({}));
   }

   get titleHeightEnable() {
      return this.model.generalPropPaneModel.enabled == "True" && this.model.titlePropPaneModel.visible;
   }
}
