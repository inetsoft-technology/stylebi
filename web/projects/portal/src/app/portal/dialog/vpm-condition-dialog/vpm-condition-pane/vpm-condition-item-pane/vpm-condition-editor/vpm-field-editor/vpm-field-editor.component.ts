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
import { ClauseValueModel } from "../../../../../../data/model/datasources/database/vpm/condition/clause/clause-value-model";
import { VPMColumnModel } from "../../../../../../data/model/datasources/database/vpm/condition/vpm-column-model";
import { DataRef } from "../../../../../../../common/data/data-ref";
import { ConditionFieldComboModel } from "../../../../../../../widget/condition/condition-field-combo-model";

@Component({
   selector: "vpm-field-editor",
   templateUrl: "vpm-field-editor.component.html",
   styleUrls: ["vpm-field-editor.component.scss"]
})
export class VPMFieldEditorComponent implements OnChanges {
   @Input() value: ClauseValueModel;
   @Input() fields: VPMColumnModel[] = [];
   @Output() valueChange: EventEmitter<ClauseValueModel> = new EventEmitter<ClauseValueModel>();
   fieldsModel: ConditionFieldComboModel = {
      list: [],
      tree: {children: []}
   };

   ngOnChanges(changes: SimpleChanges) {
      if(changes.hasOwnProperty("value") || changes.hasOwnProperty("fields")) {
         this.fieldsModel = this.createFieldsModel();

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
   selectField(field: DataRef) {
      const matchingField = this.fields.find((vpmField) => vpmField.name === field?.name);

      if(!matchingField) {
         return;
      }

      this.value.expression = matchingField.name;
      this.value.field = matchingField;
      this.valueChange.emit(this.value);
   }

   get selectedField(): DataRef | null {
      if(!this.value?.expression) {
         return null;
      }

      return this.fieldsModel.list.find((field) => field.name === this.value.expression) ?? null;
   }

   private createFieldsModel(): ConditionFieldComboModel {
      const list: DataRef[] = (this.fields ?? []).map((field) => ({
         name: field.name,
         view: field.name,
         attribute: field.columnName,
         entity: field.tableName,
         dataType: field.type,
         description: field.physicalTableName ? `${field.tableName} (${field.physicalTableName})` :
            field.tableName
      }));
      const entityMap = new Map<string, DataRef[]>();

      for(const field of list) {
         const entity = field.entity || "_#(js:Query Fields)";
         const entityFields = entityMap.get(entity) ?? [];
         entityFields.push(field);
         entityMap.set(entity, entityFields);
      }

      return {
         list,
         tree: {
            children: Array.from(entityMap.entries()).map(([entity, entityFields]) => ({
               label: entity,
               leaf: false,
               children: entityFields.map((field) => ({
                  label: field.view,
                  data: field,
                  tooltip: field.description,
                  leaf: true
               }))
            }))
         }
      };
   }
}
