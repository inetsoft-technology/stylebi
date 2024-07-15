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
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from "@angular/core";
import { Observable } from "rxjs";
import { AssetUtil } from "../../binding/util/asset-util";
import { AggregateRef, getAggregateDataType } from "../../common/data/aggregate-ref";
import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import { RankingValue } from "../../common/data/condition/ranking-value";
import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";
import { Tool } from "../../../../../shared/util/tool";

@Component({
   selector: "top-n-editor",
   templateUrl: "top-n-editor.component.html"
})
export class TopNEditor implements OnChanges {
   @Input() field: DataRef;
   @Input() type: ConditionValueType;
   @Input() variablesFunction: () => Observable<any[]>;
   @Input() value: RankingValue;
   @Output() valueChange: EventEmitter<RankingValue> = new EventEmitter<RankingValue>();
   public Tool = Tool;
   public ConditionValueType = ConditionValueType;
   n: any;
   dataRef: DataRef;
   aggFields: DataRef[];
   groupFields: DataRef[];

   @Input() set fields(value: DataRef[]) {
      this.aggFields = [];
      this.groupFields = [];

      value.forEach((ref: DataRef) => {
         if(ref.classType === "AggregateRef") {
            const dataType = getAggregateDataType(ref as AggregateRef);

            if(dataType !== XSchema.BOOLEAN) {
               this.aggFields.push(ref);
            }
         }
         else if(ref.classType === "GroupRef") {
            this.groupFields.push(ref);
         }
      });
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.hasOwnProperty("value")) {
         if(this.value != null) {
            this.n = this.value.n;
            this.dataRef = this.value.dataRef;
         }
         else {
            const n = this.type == ConditionValueType.VALUE ? 3 : "$()";
            const dataRef = null;

            setTimeout(() => {
               this.valueChanged({n, dataRef});
            });
         }
      }
   }

   valueChanged(value: RankingValue): void {
      this.valueChange.emit(value);
   }

   nChanged(n: number | string) {
      this.valueChanged({...this.value, n});
   }

   dataRefChanged(dataRef: DataRef) {
      this.valueChanged({...this.value, dataRef});
   }

   aggregateFieldDropdownVisible(): boolean {
      return !!this.groupFields && !!this.aggFields && this.aggFields.length > 0 &&
         !!AssetUtil.findRefByName(this.groupFields, this.field.name);
   }

   isEquals = (a, b) => a === b || a && b && a.view == b.view;
}
