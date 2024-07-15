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
import { Pipe, PipeTransform } from "@angular/core";
import { ConditionOperation } from "../../common/data/condition/condition-operation";

/**
 * ConditionOperation pipe
 *
 * Converts a condition operation to its string representation
 *
 */
@Pipe({
   name: "conditionOperationToString"
})
export class ConditionOperationPipe implements PipeTransform {
   transform(operation: ConditionOperation): string {
      switch(operation) {
         case ConditionOperation.NONE:
            return "_#(js:none)";
         case ConditionOperation.EQUAL_TO:
            return "_#(js:equal to)";
         case ConditionOperation.ONE_OF:
            return "_#(js:one of)";
         case ConditionOperation.LESS_THAN:
            return "_#(js:less than)";
         case ConditionOperation.GREATER_THAN:
            return "_#(js:greater than)";
         case ConditionOperation.BETWEEN:
            return "_#(js:between)";
         case ConditionOperation.STARTING_WITH:
            return "_#(js:starting with)";
         case ConditionOperation.CONTAINS:
            return "_#(js:contains)";
         case ConditionOperation.NULL:
            return "_#(js:null)";
         case ConditionOperation.TOP_N:
            return "_#(js:top)";
         case ConditionOperation.BOTTOM_N:
            return "_#(js:bottom)";
         case ConditionOperation.DATE_IN:
            return "_#(js:in range)";
         case ConditionOperation.LIKE:
            return "_#(js:like)";
         default:
            return "";
      }
   }
}
