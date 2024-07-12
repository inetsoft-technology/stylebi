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
import { ColumnOptionType } from "../../../vsobjects/model/column-option-type";
import { TextInputColumnOptionPaneModel } from "../../data/vs/textinput-column-option-pane-model";

@Component({
   selector: "textinput-column-option-pane",
   templateUrl: "textinput-column-option-pane.component.html",
})
export class TextInputColumnOptionPane implements OnInit {
   @Input() model: TextInputColumnOptionPaneModel;
   @Input() form: UntypedFormGroup;
   inputControlList: string[] = [ColumnOptionType.TEXT, ColumnOptionType.DATE,
      ColumnOptionType.INTEGER, ColumnOptionType.FLOAT, ColumnOptionType.PASSWORD];
   inputControlLabels: string[] = ["_#(js:Text)", "_#(js:Date)", "_#(js:Integer)",
                                   "_#(js:Float)", "_#(js:Password)"];

   ngOnInit() {
      if(!this.model.type) {
         this.model.type = ColumnOptionType.TEXT;
      }
   }
}
