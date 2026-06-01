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
   Component, Input, Output, EventEmitter, OnChanges,
   SimpleChanges, AfterViewInit
} from "@angular/core";
import { Observable } from "rxjs";
import { UntypedFormControl, UntypedFormGroup, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { Tool } from "../../../../../shared/util/tool";
import { NgClass } from "@angular/common";
import { CustomSelectOption, CustomSelectComponent } from "../custom-select/custom-select.component";

@Component({
    selector: "variable-editor",
    templateUrl: "variable-editor.component.html",
    styleUrls: ["variable-editor.component.scss"],
    imports: [FormsModule, ReactiveFormsModule, NgClass, CustomSelectComponent]
})
export class VariableEditor implements OnChanges, AfterViewInit {
   @Input() value: string;
   @Input() showUseList: boolean = false;
   @Input() variablesFunction: () => Observable<any[]>;
   @Output() valueChange: EventEmitter<string> = new EventEmitter<string>();
   @Output() updateChoiceQuery: EventEmitter<boolean> = new EventEmitter<boolean>();
   variableName: string;
   variableList: string[];
   _useList: boolean = false;
   vform: UntypedFormGroup = new UntypedFormGroup({"name": new UntypedFormControl()});
   useListId: string = "useList-" + Tool.generateRandomUUID();

   ngAfterViewInit(): void {
      //fix a problem for IE
      //in IE if a select in form only has one option, it will Selected it by default
      //but we needn't.
      if(this.variableName == "" && this.variableList.length == 1) {
         this.vform.controls["name"].setValue(this.variableName);
      }
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(this.value != null && this.value.indexOf("$(") == 0 &&
         this.value.lastIndexOf(")") == (this.value.length - 1))
      {
         this.variableName = this.value.substring(2, this.value.length - 1);
      }

      if(this.variablesFunction) {
         let obs = this.variablesFunction();

         if(obs === null) {
            this.variableList = null;
         }
         else {
            obs.subscribe((list) => {
               this.variableList = list;
            });
         }
      }
   }

   get useList(): boolean {
      return this._useList;
   }

   @Input() set useList(useList: boolean) {
      this._useList = useList;
      this.updateChoiceQuery.emit(useList);
   }

   variableNameChanged(value: string): void {
      this.variableName = value == undefined ? "" : value;
      this.value = "$(" + this.variableName + ")";
      this.valueChange.emit(this.value);
   }

   get variableSelectOptions(): CustomSelectOption<string>[] {
      return [
         { value: "", label: "" },
         ...((this.variableList || []).map((variable) => ({
            value: variable,
            label: variable
         })))
      ];
   }

}
