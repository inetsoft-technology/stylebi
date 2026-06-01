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
import { Component, Input, Output, EventEmitter } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { CustomSelectOption, CustomSelectComponent } from "../custom-select/custom-select.component";

@Component({
    selector: "boolean-value-editor",
    templateUrl: "boolean-value-editor.component.html",
    styleUrls: ["boolean-value-editor.component.scss"],
    imports: [FormsModule, CustomSelectComponent]
})
export class BooleanValueEditor {
   @Input() value: boolean = false;
   @Output() valueChange: EventEmitter<boolean> = new EventEmitter<boolean>();

   get booleanSelectOptions(): CustomSelectOption<string>[] {
      return [
         { value: "false", label: "_#(False)" },
         { value: "true", label: "_#(True)" }
      ];
   }
}
