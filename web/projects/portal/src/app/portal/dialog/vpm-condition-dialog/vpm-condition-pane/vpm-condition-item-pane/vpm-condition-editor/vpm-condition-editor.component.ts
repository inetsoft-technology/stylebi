/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from "@angular/core";
import { ClauseValueModel } from "../../../../../data/model/datasources/database/vpm/condition/clause/clause-value-model";
import { VPMColumnModel } from "../../../../../data/model/datasources/database/vpm/condition/vpm-column-model";
import { OperationModel } from "../../../../../data/model/datasources/database/vpm/condition/clause/operation-model";
import { XSchema } from "../../../../../../common/data/xschema";
import { Observable } from "rxjs";
import { ClauseValueTypes } from "../../../../../data/model/datasources/database/vpm/condition/clause/clause-value-types";
import { BasicSqlQueryModel } from "../../../../../../composer/data/ws/basic-sql-query-model";
import { SqlQueryDialogModel } from "../../../../../../composer/data/ws/sql-query-dialog-model";

@Component({
   selector: "vpm-condition-editor",
   templateUrl: "vpm-condition-editor.component.html",
   styleUrls: ["vpm-condition-editor.component.scss"]
})
export class VPMConditionEditor implements OnChanges {
   @Input() value: ClauseValueModel;
   @Input() values: string[];
   @Input() operation: OperationModel;
   @Input() fields: VPMColumnModel[] = [];
   @Input() valueTypes: string[] = [];
   @Input() enableBrowseData: boolean = true;
   @Input() type: string = XSchema.STRING;
   @Input() dataFunction: () => Observable<string[]>;
   @Input() primaryValue: boolean = false;
   @Input() varShowDate: boolean = false;
   @Input() datasource: string;
   @Input() isWSQuery: boolean;
   @Output() valueChange: EventEmitter<ClauseValueModel> = new EventEmitter<ClauseValueModel>();
   @Output() valueChanges: EventEmitter<string[]> = new EventEmitter<string[]>();
   ClauseValueTypes = ClauseValueTypes;

   ngOnChanges(changes: SimpleChanges): void {
      if(this.value == null) {
         this.value = <ClauseValueModel> {
            expression: null,
            type: ClauseValueTypes.VALUE
         };
      }
   }

   /**
    * A type was selected for the condition. Update condition value and type.
    * @param type
    */
   selectType(type: string): void {
      if(type != this.value.type) {
         let value: string = null;
         let field: VPMColumnModel;

         if(type === ClauseValueTypes.FIELD && this.fields.length > 0) {
            value = this.fields[0].name;
            field = this.fields[0];
         }
         else if(type === ClauseValueTypes.VARIABLE) {
            value = "$()";
         }
         else if(type === ClauseValueTypes.SESSION_DATA) {
            value = "$(_USER_)";
         }

         this.value = <ClauseValueModel> {
            expression: value,
            type: type,
            field: field
         };

         this.valueChanged();
      }
   }

   /**
    * Called when value is changed. Emit the new value.
    */
   valueChanged(): void {
      this.valueChange.emit(this.value);
   }

   /**
    * Called when a value is changed. Update the values expression and emit the new value.
    * @param val
    */
   conditionValueChanged(val: string) {
      this.value.expression = val;
      this.valueChanged();
   }

   /**
    * Called when subquery is changed.
    * @param val
    */
   conditionSubQueryValueChanged(val: SqlQueryDialogModel) {
      this.value.query = val;
      this.valueChanged();
   }
}
