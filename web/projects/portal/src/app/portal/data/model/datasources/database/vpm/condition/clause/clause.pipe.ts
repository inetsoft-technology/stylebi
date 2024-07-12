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
import { ClauseModel } from "./clause-model";
import { ClauseOperationSymbols } from "./clause-operation-symbols";
import { ClauseValueModel } from "./clause-value-model";
import { ClauseValueTypes } from "./clause-value-types";

/**
 * Clause pipe
 *
 * Converts a clause into its string representation
 *
 */
@Pipe({
   name: "clauseToString"
})
export class ClausePipe implements PipeTransform {
   transform(clause: ClauseModel): string {
      let indent: string = "";

      for(let i = 0; i < clause.level; i++) {
         indent += "....";
      }

      const exp1: string = this.getClauseValueModelString(clause.value1);
      const exp2: string = this.getClauseValueModelString(clause.value2);
      const exp3: string = this.getClauseValueModelString(clause.value3);
      const symbol: string = clause.operation.symbol;
      let expString: string = "";

      if(symbol == ClauseOperationSymbols.EXISTS || symbol == ClauseOperationSymbols.UNIQUE) {
         expString = symbol + " " + exp1;
      }
      else {
         expString = exp1 + " " + symbol;
      }

      if(symbol != ClauseOperationSymbols.EXISTS && symbol != ClauseOperationSymbols.IS_NULL &&
         symbol != ClauseOperationSymbols.UNIQUE)
      {
         expString += " " + exp2;

         if(symbol == ClauseOperationSymbols.BETWEEN) {
            expString += (exp3 + "").length > 0 ? " and " + exp3 : "";
         }
      }

      if(clause.negated) {
         expString = "not " + expString;
      }

      return indent + expString;
   }

   private getClauseValueModelString(valueModel: ClauseValueModel): string {
      if(valueModel.type == ClauseValueTypes.SUBQUERY) {
         return valueModel.query ? `(${valueModel.query.simpleModel.sqlString})` : "";
      }
      else if(valueModel.expression) {
         return valueModel.expression;
      }

      return "";
   }
}