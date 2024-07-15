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
import { Observable, of as observableOf } from "rxjs";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { BrowseDataModel } from "../browse-data-model";
import { DataRef } from "../data-ref";
import { Condition } from "./condition";
import { ConditionItemPaneProvider } from "./condition-item-pane-provider";
import { ConditionOperation } from "./condition-operation";
import { ConditionValueType } from "./condition-value-type";
import { ExpressionType } from "./expression-type";
import { ExpressionValue } from "./expression-value";

/**
 * ConditionItemPaneProvider
 *
 * Provides the necessary data for the ConditionItemPane component.
 *
 * The motivation for this interface is the fact that different components may want to
 * utilize the ConditionItemPane in a different way such as showing different condition
 * operations / value types. Since it's impossible to handle all the ways in
 * which this component will be used (implementation would get messy), this interface
 * needs to be implemented to provide the specific data given the currently selected
 * condition.
 */

export abstract class AssetConditionItemPaneProvider implements ConditionItemPaneProvider
{
   /**
    * Gets the available condition operations
    */
   abstract getConditionOperations(condition: Condition): ConditionOperation[];

   /**
    * Gets the available condition value types
    */
   abstract getConditionValueTypes(condition: Condition): ConditionValueType[];

   /**
    * Gets the available expression types
    */
   abstract getExpressionTypes(condition: Condition): ExpressionType[];

   /**
    * Determines whether negation is allowed
    */
   abstract isNegationAllowed(condition: Condition): boolean;

   /**
    * Fetches the available data for the selected field
    */
   abstract getData(condition: Condition): Observable<BrowseDataModel>;

   /**
    * Fetches the available variables for the selected field
    */
   abstract getVariables(condition: Condition): Observable<any[]>;

   /**
    * Fetches the expression dialog column tree for the given condition
    */
   abstract getColumnTree(value: ExpressionValue, variableNames?: string[]): Observable<TreeNodeModel>;

   getScriptDefinitions(value: ExpressionValue): Observable<any> {
      return observableOf(null);
   }

   /**
    * Return if support browsing data in the condition value editor.
    */
   isBrowseDataEnabled(condition: Condition): boolean {
      return true;
   }

   /**
    * Return the grayed out fields.
    */
   getGrayedOutFields(): DataRef[] {
      return null;
   }

   /**
    * Set formula(add or edit) for adhoc condition pane.
    */
   setFormula(formula: any): void {
   }

   /**
    * Get sqlMergeable
    */
   isSqlMergeable(): boolean {
      return true;
   }

   trimVariables(variables: string[]): string[] {
      let result: string[] = [];

      if(variables) {
         for(let variable of variables) {
            if(variable.startsWith("$(")) {
               variable = variable.slice(2, -1);
            }

            result.push(variable);
         }
      }

      return result;
   }

}
