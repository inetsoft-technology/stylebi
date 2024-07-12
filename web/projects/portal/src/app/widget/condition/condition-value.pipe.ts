/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Pipe, PipeTransform } from "@angular/core";
import { ConditionValue } from "../../common/data/condition/condition-value";
import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import { ExpressionType } from "../../common/data/condition/expression-type";
import { ExpressionValue } from "../../common/data/condition/expression-value";
import { RankingValue } from "../../common/data/condition/ranking-value";
import { SubqueryValue } from "../../common/data/condition/subquery-value";

/**
 * ConditionValue pipe
 *
 * Converts a condition value to its string representation
 *
 */
@Pipe({
   name: "conditionValueToString"
})
export class ConditionValuePipe implements PipeTransform {
   transform(value: ConditionValue): string {
      if(value == null) {
         return null;
      }

      // if ranking value
      if(value.value && value.value.hasOwnProperty("n") &&
         (value.type == ConditionValueType.VALUE || value.type == ConditionValueType.VARIABLE))
      {
         let rankingValue: RankingValue = value.value;
         return !!rankingValue.dataRef ?
            rankingValue.n + " of " + rankingValue.dataRef.view : rankingValue.n;
      }
      else {
         switch(value.type) {
            case ConditionValueType.VALUE:
               return value.value;
            case ConditionValueType.VARIABLE:
               return value.value;
            case ConditionValueType.EXPRESSION:
               const expValue: ExpressionValue = value.value;

               if(expValue != null && expValue.expression != null) {
                  if(expValue.type == ExpressionType.SQL) {
                     return "Expression[SQL:" + expValue.expression + "]";
                  }
                  else {
                     return "Expression[Javascript:" + expValue.expression + "]";
                  }
               }

               return "";
            case ConditionValueType.FIELD:
               if(!!value.value) {
                  return value.value.view;
               }
               else {
                  return value.value;
               }
            case ConditionValueType.SUBQUERY:
               let subqueryValue: SubqueryValue = value.value;

               if(subqueryValue != null) {
                  return "Query[" + subqueryValue.query + "]";
               }

               return "";
            case ConditionValueType.SESSION_DATA:
               return value.value;
            default:
               return "";
         }
      }
   }
}
