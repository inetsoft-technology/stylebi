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
import { Component, Input } from "@angular/core";
import { InputLabelPaneModel } from "../../data/vs/input-label-pane-model";
import { ComboMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";

@Component({
   selector: "input-label-pane",
   templateUrl: "input-label-pane.component.html"
})
export class InputLabelPane {
   @Input() model: InputLabelPaneModel;
   @Input() variableValues: string[];
   @Input() vsId: string;

   positionOptions = [
    { label: "_#(Left)", value: "left" },
    { label: "_#(Right)", value: "right" },
    { label: "_#(Top)", value: "top" },
    { label: "_#(Bottom)", value: "bottom" }
   ];

   selectTextType(type: ComboMode) {
      let oldType = ComboMode.VALUE;

      if(this.model && this.model.labelText && this.model.labelText.startsWith("=")) {
         oldType = ComboMode.EXPRESSION;
      }
      else if(this.model && this.model.labelText && this.model.labelText.startsWith("$(")) {
         oldType = ComboMode.VARIABLE;
      }

      if(type !== oldType) {
         if(type == ComboMode.EXPRESSION && (!this.model.labelText || !this.model.labelText.startsWith("="))) {
            this.model.labelText = "=";
         }
         else if(type == ComboMode.VARIABLE && (!this.model.labelText || !this.model.labelText.startsWith("$("))) {
            if(this.variableValues && this.variableValues.length > 0) {
               this.model.labelText = this.variableValues[0];
            }
            else {
               this.model.labelText = "";
            }
         }
         else {
            this.model.labelText = "";
         }
      }
   }
}