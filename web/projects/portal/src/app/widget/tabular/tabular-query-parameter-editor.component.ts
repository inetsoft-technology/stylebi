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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { QueryParameter } from "../../common/data/tabular/query-parameter";
import { TabularDateEditor } from "./tabular-date-editor.component";
import { TabularNumberEditor } from "./tabular-number-editor.component";
import { TabularBooleanEditor } from "./tabular-boolean-editor.component";
import { FormsModule } from "@angular/forms";
import { TabularTextEditor } from "./tabular-text-editor.component";
import { CustomSelectOption, CustomSelectComponent } from "../custom-select/custom-select.component";

@Component({
    selector: "tabular-query-parameter-editor",
    templateUrl: "tabular-query-parameter-editor.component.html",
    imports: [
    TabularTextEditor,
    FormsModule,
    TabularBooleanEditor,
    TabularNumberEditor,
    TabularDateEditor, CustomSelectComponent]
})
export class TabularQueryParameterEditor implements OnInit {
   @Input() value: QueryParameter;
   @Input() enabled: boolean = true;
   @Input() required: boolean = false;
   @Output() valueChange: EventEmitter<QueryParameter> = new EventEmitter<QueryParameter>();
   readonly typeSelectOptions: CustomSelectOption<string>[] = [
      { label: "_#(String)", value: "STRING" },
      { label: "_#(Character)", value: "CHAR" },
      { label: "_#(Integer)", value: "INTEGER" },
      { label: "_#(Byte)", value: "BYTE" },
      { label: "_#(Short)", value: "SHORT" },
      { label: "_#(Long)", value: "LONG" },
      { label: "_#(Float)", value: "FLOAT" },
      { label: "_#(Double)", value: "DOUBLE" },
      { label: "_#(Boolean)", value: "BOOLEAN" },
      { label: "_#(Date)", value: "DATE" },
      { label: "_#(Time)", value: "TIME" },
      { label: "_#(Time Instant)", value: "TIME_INSTANT" }
   ];

   ngOnInit(): void {
      if(this.value == null) {
         this.value = <QueryParameter>{
            name: null,
            type: "STRING",
            variable: false,
            value: null
         };

         setTimeout(() => this.valueChanged(), 0);
      }
   }

   valueChanged(): void {
      this.valueChange.emit(this.value);
   }
}
