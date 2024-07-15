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
   OnChanges,
   Input,
   Output,
   EventEmitter,
   ViewChild,
   SimpleChanges
} from "@angular/core";
import { ClauseValueModel } from "../../../../../../data/model/datasources/database/vpm/condition/clause/clause-value-model";
import { VPMColumnModel } from "../../../../../../data/model/datasources/database/vpm/condition/vpm-column-model";
import { FixedDropdownDirective } from "../../../../../../../widget/fixed-dropdown/fixed-dropdown.directive";

@Component({
   selector: "vpm-field-editor",
   templateUrl: "vpm-field-editor.component.html",
   styleUrls: ["vpm-field-editor.component.scss"]
})
export class VPMFieldEditorComponent implements OnChanges {
   @Input() value: ClauseValueModel;
   @Input() fields: VPMColumnModel[] = [];
   @Output() valueChange: EventEmitter<ClauseValueModel> = new EventEmitter<ClauseValueModel>();
   @ViewChild(FixedDropdownDirective) fieldsDropdown: FixedDropdownDirective;

   ngOnChanges(changes: SimpleChanges) {
      if(changes.hasOwnProperty("value") || changes.hasOwnProperty("fields")) {
         if(!!this.value && !!this.fields) {
            const matchingRef = this.fields.find((field) => {
               return field.name === this.value.expression;
            });

            if(!!matchingRef) {
               this.value.field = matchingRef;
            }
         }
      }
   }

   /**
    * Called when a field is selected. Update the conditions expression and emit the new value.
    * @param field
    */
   selectField(field: VPMColumnModel) {
      this.value.expression = field.name;
      this.value.field = field;
      this.valueChange.emit(this.value);
      this.fieldsDropdown.close();
   }
}