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
import { Observable } from "rxjs";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { BrowseDataModel } from "../browse-data-model";
import { DataRef } from "../data-ref";
import { Condition } from "./condition";
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
export interface ConditionItemPaneProvider {
   /**
    * Gets the available condition operations
    */
   getConditionOperations(condition: Condition): ConditionOperation[];

   /**
    * Gets the available condition value types
    */
   getConditionValueTypes(condition: Condition): ConditionValueType[];

   /**
    * Gets the available expression types
    */
   getExpressionTypes(condition: Condition): ExpressionType[];

   /**
    * Determines whether negation is allowed
    */
   isNegationAllowed(condition: Condition): boolean;

   /**
    * Fetches the available data for the selected field
    */
   getData(condition: Condition): Observable<BrowseDataModel>;

   /**
    * Fetches the available variables for the selected field
    */
   getVariables(condition: Condition): Observable<any[]>;

   /**
    * Fetches the expression dialog column tree for the given condition
    */
   getColumnTree(value: ExpressionValue, variableNames?: string[], oldFormulaName?: string): Observable<TreeNodeModel>;

   getScriptDefinitions(value: ExpressionValue): any;

   /**
    * Return if support browsing data in the condition value editor.
    */
   isBrowseDataEnabled(condition: Condition): boolean;

   /**
    * Return the grayed out fields.
    */
   getGrayedOutFields(): DataRef[];

   /**
    * Set formula(add or edit) for adhoc condition pane.
    */
   setFormula(formula: any): void;

   /**
    * Get sqlMergeable
    */
   isSqlMergeable(): boolean;
}
