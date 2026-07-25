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
   SimpleChanges
} from "@angular/core";
import { FormsModule } from "@angular/forms";
import { ClauseValueModel } from "../../../../../../data/model/datasources/database/vpm/condition/clause/clause-value-model";
import { VPMColumnModel } from "../../../../../../data/model/datasources/database/vpm/condition/vpm-column-model";
import { CustomSelectComponent, CustomSelectOption } from "../../../../../../../widget/custom-select/custom-select.component";

@Component({
    selector: "vpm-field-editor",
    templateUrl: "vpm-field-editor.component.html",
    styleUrls: ["vpm-field-editor.component.scss"],
    imports: [CustomSelectComponent, FormsModule]
})
export class VPMFieldEditorComponent implements OnChanges {
   @Input() value: ClauseValueModel;
   @Input() fields: VPMColumnModel[] = [];
   @Output() valueChange: EventEmitter<ClauseValueModel> = new EventEmitter<ClauseValueModel>();

   ngOnChanges(changes: SimpleChanges) {
      if((changes.hasOwnProperty("value") || changes.hasOwnProperty("fields")) &&
         !!this.value && !!this.fields)
      {
         const matchingRef = this.fields.find((field) => field.name === this.value.expression);

         if(!!matchingRef) {
            this.value.field = matchingRef;
         }
      }
   }

   get fieldOptions(): CustomSelectOption[] {
      return (this.fields ?? []).map((f) => ({
         label: f.name,
         value: f.name,
         title: f.name
      }));
   }

   onFieldSelect(fieldName: string): void {
      const matchingField = this.fields.find((f) => f.name === fieldName);

      if(!matchingField) {
         return;
      }

      this.value.expression = matchingField.name;
      this.value.field = matchingField;
      this.valueChange.emit(this.value);
   }
}
