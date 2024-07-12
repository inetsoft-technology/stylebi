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
import { ValueMode } from "../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { GeneralPropPaneModel } from "../model/general-prop-pane-model";

@Component({
   selector: "general-prop-pane",
   templateUrl: "general-prop-pane.component.html",
})
export class GeneralPropPane implements OnInit {
   @Input() vsId: string;
   @Input() assemblyName: string;
   @Input() model: GeneralPropPaneModel;
   @Input() form: UntypedFormGroup;
   @Input() variableValues: string[];
   @Input() layoutObject: boolean = false;
   @Input() columnTreeRoot: TreeNodeModel = null;
   @Input() functionTreeRoot: TreeNodeModel = null;
   @Input() operatorTreeRoot: TreeNodeModel = null;
   @Input() scriptDefinitions: any = null;
   mode: ValueMode = ValueMode.TEXT;
   enabledValues: any[] = [
      {value: "True", label: "_#(js:True)"},
      {value: "False", label: "_#(js:False)"}];

   get showEnabledGroup(): boolean {
      return !!this.model["showEnabledGroup"];
   }

   initForm(): void {
      this.form.addControl("basicGeneralPaneForm", new UntypedFormGroup({}));
   }

   initEnabled() {
      if(this.model.enabled == "true") {
         this.model.enabled = this.enabledValues[0].value;
      }
      else if(this.model.enabled == "false") {
         this.model.enabled = this.enabledValues[1].value;
      }
   }

   ngOnInit(): void {
      this.initForm();
      this.initEnabled();
   }
}
