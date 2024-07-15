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

import {
   Component, ElementRef, EventEmitter, Input, Output, Renderer2,
   ViewChild
} from "@angular/core";
import { FixedDropdownDirective } from "../fixed-dropdown/fixed-dropdown.directive";
import { Tool } from "../../../../../shared/util/tool";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../common/util/component-tool";

@Component({
   selector: "target-label-pane",
   templateUrl: "target-label-pane.component.html"
})
export class TargetLabelPane {
   @Input() label: string = "";
   @ViewChild(FixedDropdownDirective) dropdown: FixedDropdownDirective;
   @Output() labelChange: EventEmitter<any> = new EventEmitter<any>();

   hints: string[] = ["_#(js:designer.chartProp.targetLabelHint)",
                      "{0} " + "_#(js:Target Value)",
                      "{1} " + "_#(js:Target Formula)",
                      "{2} " + "_#(js:Field Name)"];

   constructor(private elem: ElementRef, private renderer: Renderer2,
               private modalService: NgbModal) {
   }

   labelIsValid(s: string): boolean {
      let temp = s.replace(/\}/g, "}|")
         .replace(/\{/g, "|{")
         .split("|");

      for(let i = 0; i < temp.length; i++) {
         let str = temp[i];
         //does the string not contain any brackets? then it is valid
         let validRegularText = /^[^{}]*$/.test(str);
         let arr = str.split(",");
         let embeddedValue = false;

         // if it does contain brackets, it must be {1} format in range 0-2
         if(arr.length == 1) {
            embeddedValue =  /^\{[0-2]}$/.test(arr[0]);
         }
         // if has format like {0,number,$###}
         else if(arr.length > 2) {
            embeddedValue = /^\{[0-2]$/.test(arr[0]);
         }

         if(!validRegularText && !embeddedValue) {
            return false;
         }
      }

      return true;
   }

   onLabelChange(isOpen: boolean) {
      let valid: boolean = this.labelIsValid(this.label);

      if(!isOpen && valid) {
         this.labelChange.emit(this.label.trim());
      }
      else if(!isOpen && !valid) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Invalid label)", "_#(js:parse.argument.number)_*" + this.label);
      }

      this.dropdown.close();
   }
}
