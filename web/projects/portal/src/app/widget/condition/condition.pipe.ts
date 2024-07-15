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
import { Pipe, PipeTransform } from "@angular/core";
import { Condition } from "../../common/data/condition/condition";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { ConditionOperationPipe } from "./condition-operation.pipe";
import { ConditionValuePipe } from "./condition-value.pipe";

/**
 * Condition pipe
 *
 * Converts a condition object to its string representation
 *
 */
@Pipe({
   name: "conditionToString"
})
export class ConditionPipe implements PipeTransform {
   transform(condition: Condition): string {
      let indent: string = "";

      for(let i = 0; i < condition.level; i++) {
         indent += "....";
      }

      let conditionValuePipe: ConditionValuePipe = new ConditionValuePipe();
      let value: string = "";

      if(condition.values != null) {
         for(let i = 0; i < condition.values.length; i++) {
            value += conditionValuePipe.transform(condition.values[i]);

            if(i != condition.values.length - 1) {
               value += ",";
            }
         }
      }

      let orEqualTo = "";

      if((condition.operation === ConditionOperation.GREATER_THAN ||
         condition.operation === ConditionOperation.LESS_THAN) && condition.equal)
      {
         orEqualTo = " or equal to";
      }

      return indent + "[" + (condition.field != null ? condition.field.view : "") + "]"
         + (condition.negated ? "[_#(js:is not)]" : "[_#(js:is)]")
         + "[" + new ConditionOperationPipe().transform(condition.operation) + orEqualTo + "]"
         + (condition.operation != ConditionOperation.NULL ?
            "[" + value + "]" : "");
   }
}
