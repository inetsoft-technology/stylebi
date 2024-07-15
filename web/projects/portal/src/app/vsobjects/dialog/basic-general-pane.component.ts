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
import { Component, Input, OnInit } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { ValueMode } from "../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { FormValidators } from "../../../../../shared/util/form-validators";
import { TreeNodeModel } from "../../widget/tree/tree-node-model";
import { BasicGeneralPaneModel } from "../model/basic-general-pane-model";

const SHOW_VALUE: string = "Show";
const SHOW_LABEL: string = "_#(js:Show)";
const HIDE_VALUE: string = "Hide";
const HIDE_LABEL: string = "_#(js:Hide)";
const HIDE_ON_PRINT_VALUE: string = "Hide on Print and Export";
const HIDE_ON_PRINT_LABEL: string = "_#(js:Hide on Print and Export)";

@Component({
   selector: "basic-general-pane",
   templateUrl: "basic-general-pane.component.html",
})
export class BasicGeneralPane implements OnInit {
   @Input() vsId: string;
   @Input() assemblyName: string;
   @Input() model: BasicGeneralPaneModel;
   @Input() form: UntypedFormGroup;
   @Input() variableValues: string[];
   @Input() layoutObject: boolean = false;
   @Input() columnTreeRoot: TreeNodeModel = null;
   @Input() functionTreeRoot: TreeNodeModel = null;
   @Input() operatorTreeRoot: TreeNodeModel = null;
   @Input() scriptDefinitions: any = null;
   @Input() submitOnChange: boolean = true;
   mode: ValueMode = ValueMode.TEXT;

   visibleValues: any[] = [
      {value: SHOW_VALUE, label: SHOW_LABEL},
      {value: HIDE_VALUE, label: HIDE_LABEL},
      {value: HIDE_ON_PRINT_VALUE, label: HIDE_ON_PRINT_LABEL}];

   initForm(): void {
      this.form.addControl("name", new UntypedFormControl({value: this.model.name,
         disabled: !this.model.nameEditable || this.layoutObject}, [
         FormValidators.exists(this.model.objectNames),
         Validators.required,
         FormValidators.nameSpecialCharacters,
         FormValidators.doesNotStartWithNumber,
         FormValidators.notWhiteSpace
      ]));
   }

   ngOnInit(): void {
      this.initForm();
   }

   get visibleValue(): string {
      if(this.model.visible == "1" || this.model.visible == "true" || this.model.visible == "show") {
         return this.visibleValues[0].value;
      }
      else if(this.model.visible == "2" || this.model.visible == "false" || this.model.visible == "hide") {
         return this.visibleValues[1].value;
      }
      else if(this.model.visible == "4") {
         return this.visibleValues[2].value;
      }
      else {
         return this.model.visible;
      }
   }

   updateVisible(value: string): void {
      if(value == SHOW_VALUE) {
         this.model.visible = "1";
      }
      else if(value == HIDE_VALUE) {
         this.model.visible = "2";
      }
      else if(value == HIDE_ON_PRINT_VALUE) {
         this.model.visible = "4";
      }
      else {
         this.model.visible = value;
      }
   }
}
