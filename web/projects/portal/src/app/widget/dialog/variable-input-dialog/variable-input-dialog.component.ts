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
   Component,
   OnInit,
   Output,
   EventEmitter,
   Input,
   ViewChildren,
   QueryList, HostListener, ViewChild
} from "@angular/core";

import { XSchema } from "../../../common/data/xschema";
import { VariableInputDialogModel } from "./variable-input-dialog-model";
import { VariableInfo } from "../../../common/data/variable-info";
import { VariableCollectionSelector } from "../variable-collection-selector/variable-collection-selector.component";
import {
   VariableValueEditor
} from "../variable-list-dialog/variable-value-editor/variable-value-editor.component";

@Component({
   selector: "variable-input-dialog",
   templateUrl: "variable-input-dialog.component.html",
   styleUrls: ["variable-input-dialog.component.scss"]
})
export class VariableInputDialog implements OnInit {
   @ViewChildren(VariableValueEditor) valueEditors: QueryList<VariableValueEditor>;
   @Input() model: VariableInputDialogModel;
   @Input() enterParameters: boolean = true;
   @ViewChildren(VariableCollectionSelector) selectors: QueryList<VariableCollectionSelector>;
   @Output() onCommit: EventEmitter<Object> =
      new EventEmitter<Object>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   readonly timeInstantFormat = "YYYY-MM-DD HH:mm:ss";
   public XSchema = XSchema;
   dataTypeList = XSchema.standardDataTypeList;
   formValid = () => this.model;

   ngOnInit(): void {
      this.initModel();
   }

   private initModel() {
      this.model.varInfos.forEach((info) => {
         if(info.type === XSchema.BOOLEAN) {
            const bval = info.value != null && info.value.length > 0 &&
               info.value[0] != "false" && !!info.value[0];
            info.value = [bval];

            if(!!info.values) {
               info.objValues = info.values.map(v => v == "true");
            }
         }
         else if(info.value === null) {
            info.value = [];
         }
         // need to preserve quotes in case string containing comma was quoted in original value.
         else if(info.usedInOneOf && Array.isArray(info.value)) {
            info.value = [info.value.map(v => this.quoteString(v)).join(",")];
         }
      });
   }

   clearDisabled(): boolean {
      if(!this.model || !this.model.varInfos) {
         return true;
      }

      for(const varInfo of this.model.varInfos) {
         if(!varInfo.value) {
            continue;
         }

         for(const value of varInfo.value) {
            if(!!value) {
               return false;
            }
         }
      }

      return true;
   }

   clear() {
      this.model.varInfos.forEach((info) => {
         info.value = [];
         info.userSelected = false;
      });
   }

   onMouseUp(event: MouseEvent) {
      if(this.valueEditors && this.valueEditors.length > 0) {
         this.valueEditors.forEach(child => {
            child.onMouseUp(event);
         });
      }
   }

   cancel(): void {
      if(this.enterParameters) {
         this.onCancel.emit("cancelSheet");
      }
      else {
         this.clear();
         this.onCancel.emit("");
      }
   }

   saveChanges(): void {
      this.prepareModel();
      const event = <VariableInputDialogModel> Object.assign(this.model);
      // clear out the choices and values used to populate the dialog in order to reduce the message
      // size
      event.varInfos.forEach((varInfo => {
         varInfo.choices = null;
         varInfo.values = null;
      }));
      this.onCommit.emit(event);
   }

   private prepareModel() {
      this.model.varInfos.forEach((info, index) => {
         let temp: any[] = [];

         for(let i = 0; !!info.value && i < info.value.length; i++) {
            if(info.value[i] !== undefined && info.value[i] !== "") {
               temp.push(info.value[i]);
            }
         }

         if(temp.length === 1 && info.usedInOneOf && info.style === 0 &&
            typeof temp[0] === "string")
         {
            temp = this.splitValue(temp[0]);
         }

         this.model.varInfos[index].value = temp.length === 0 ? [null] : temp;
      });
   }

   private splitValue(value: string): string[] {
      const output: string[] = [];

      if(!value) {
         return [];
      }

      let startIndex = 0;
      let quoted: string = "";
      let length = value.length;
      let current = "";

      for(let index = 0; index < length; index++) {
         const ch = value[index];

         // support both single quote and double quote
         if(index === startIndex && (ch === '"' || ch === "'")) {
            quoted = ch;
            continue;
         }

         if(ch === "\\") {
            index += 1;

            if(index < length) {
               current += value[index];
            }
            else {
               current += "\\";
               output.push(current);
            }

            continue;
         }

         if(quoted && ch === quoted) {
            index += 1;

            if(index < length) {
               let beforeComma = "";

               // lookahead to comma
               while(index < length && value[index] !== ",") {
                  beforeComma += value[index];
                  index += 1;
               }

               // malformed input, just append it to the value
               if(beforeComma.trim()) {
                  current += beforeComma;
               }

               output.push(current);
               startIndex = index + 1;
               quoted = "";
               current = "";

               // skip space until quote
               for(let k = startIndex; k < length; k++) {
                  if(value[k] != " ") {
                     startIndex = k;
                     index = k - 1;
                     break;
                  }
               }
            }
            else {
               output.push(current);
            }

            continue;
         }

         if(!quoted && ch === ",") {
            output.push(current.trim());
            startIndex = index + 1;
            quoted = "";
            current = "";
            continue;
         }

         // skip leading space
         if(!quoted && ch === " " && index == startIndex) {
            startIndex++;
         }
         else {
            current += ch;
         }

         if(index === length - 1) {
            output.push(current.trim());
         }
      }

      return output;
   }

   private quoteString(s: string): string {
      if(!s.includes("'") && !s.includes('"') && !s.includes(",")) {
         return s;
      }

      return s.includes("'") ? '"' + s + '"' : "'" + s + "'";
   }

   getVariableValueString(varInfo: VariableInfo): string {
      if(!varInfo.value) {
         return null;
      }

      if(varInfo.usedInOneOf) {
         return varInfo.value.join(",");
      }

      return varInfo.value[0];
   }

   setVariableValueString(varInfo: VariableInfo, value: string) {
      varInfo.value = [value];
   }

   getVariableValues(varInfo: VariableInfo): string[] {
      if(varInfo?.type == XSchema.BOOLEAN) {
         return varInfo.objValues;
      }

      return varInfo?.values;
   }

   setVariableValue(varInfo: VariableInfo, value: any) {
      varInfo.value = value;
      varInfo.userSelected = true;
   }
}
