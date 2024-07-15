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
import { TextPaneModel } from "../../data/vs/text-pane-model";
import { ComboMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";

@Component({
   selector: "text-pane",
   templateUrl: "text-pane.component.html"
})
export class TextPane {
   @Input() model: TextPaneModel;
   @Input() variableValues: string[];
   @Input() layoutObject: boolean = false;
   @Input() vsId: string = null;

   selectTextType(type: ComboMode) {
      let oldType = ComboMode.VALUE;

      if(this.model && this.model.text && this.model.text.startsWith("=")) {
         oldType = ComboMode.EXPRESSION;
      }
      else if(this.model && this.model.text && this.model.text.startsWith("$(")) {
         oldType = ComboMode.VARIABLE;
      }

      if(type !== oldType) {
         if(type == ComboMode.EXPRESSION && (!this.model.text || !this.model.text.startsWith("="))) {
            this.model.text = "=";
         }
         else if(type == ComboMode.VARIABLE && (!this.model.text || !this.model.text.startsWith("$("))) {
            if(this.variableValues.length > 0) {
               this.model.text = this.variableValues[0];
            }
            else {
               this.model.text = "";
            }
         }
         else {
            this.model.text = "";
         }
      }
   }
}
