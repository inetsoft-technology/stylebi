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
import { ConditionValueType } from "../../common/data/condition/condition-value-type";

/**
 * ConditionValueType pipe
 *
 * Converts a condition value type to its string representation
 *
 */
@Pipe({
   name: "conditionValueTypeToString"
})
export class ConditionValueTypePipe implements PipeTransform {
   transform(type: ConditionValueType): string {
      switch(type) {
         case ConditionValueType.VALUE:
            return "_#(js:Value)";
         case ConditionValueType.VARIABLE:
            return "_#(js:Variable)";
         case ConditionValueType.EXPRESSION:
            return "_#(js:Expression)";
         case ConditionValueType.FIELD:
            return "_#(js:Field)";
         case ConditionValueType.SUBQUERY:
            return "_#(js:Subquery)";
         case ConditionValueType.SESSION_DATA:
            return "_#(js:Session Data)";
         default:
            return null;
      }
   }
}
