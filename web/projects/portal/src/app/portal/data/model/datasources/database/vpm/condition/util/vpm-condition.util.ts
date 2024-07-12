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
import { ConditionItemModel } from "../condition-item-model";
import { ClauseModel } from "../clause/clause-model";
import { ClauseOperationSymbols } from "../clause/clause-operation-symbols";
import { ConjunctionModel } from "../conjunction/conjunction-model";
import { ClauseValueModel } from "../clause/clause-value-model";

/**
 * Checks whether the condition list is valid.
 *
 * @param conditionList the condition list to check
 *
 * @returns true if condition list is valid, false otherwise
 */
export function isValidConditionList(conditionList: ConditionItemModel[]): boolean {
   for(let i = 0; i < conditionList.length; i += 2) {
      const condition: ClauseModel = conditionList[i] as ClauseModel;

      if(!isValidCondition(condition)) {
         return false;
      }
   }

   return true;
}

/**
 * Checks whether the condition is valid
 *
 * @param condition the condition to check
 *
 * @returns true if condition is valid, false otherwise
 */
export function isValidCondition(condition: ClauseModel): boolean {
   const symbol: string = condition.operation.symbol;
   let exp1 = condition.value1.expression != null ? (condition.value1.expression + "").trim() : null;
   let exp2 = condition.value2.expression != null ? (condition.value2.expression + "").trim() : null;
   let exp3 = condition.value3.expression != null ? (condition.value3.expression + "").trim() : null;

   if(symbol === ClauseOperationSymbols.BETWEEN &&
      !!exp2 && exp2 !== "$()" && !!exp3 && exp3 !== "$()")
   {
      return true;
   }

   // if first clause value is null, invalid
   if(isEmptyValue(condition.value1)) {
      return false;
   }

   if(symbol === ClauseOperationSymbols.IN && isEmptyValue(condition.value2)) {
      return false;
   }

   if(symbol !== ClauseOperationSymbols.EXISTS && symbol !== ClauseOperationSymbols.UNIQUE &&
      symbol !== ClauseOperationSymbols.IS_NULL)
   {
      // if uses 2nd value and expression is null, invalid
      if(isEmptyValue(condition.value2)) {
         return false;
      }

      // if uses 3rd value and expression is null, invalid
      if(symbol == ClauseOperationSymbols.BETWEEN && isEmptyValue(condition.value3)) {
         return false;
      }
   }

   return true;
}

/**
 * Whether the value is empty.
 */
function isEmptyValue(value: ClauseValueModel) {
   return !value || (value.expression == null || value.expression === "" || value.expression == "$()") && !value.query;
}

/**
 * Delete the condition at the given index from the condition list.
 * @param conditionList the condition list to delete from
 * @param index   the index of the condition to delete
 */
export function deleteCondition(conditionList: ConditionItemModel[], index: number): void {
   if(conditionList.length == 1) {
      conditionList.splice(0, 1);
      return;
   }

   let leftJunctionIndex: number = index - 1;
   let rightJunctionIndex: number = index + 1;
   let removeJunctionIndex: number;
   let leftJunction: ConjunctionModel;

   if(leftJunctionIndex >= 0) {
      leftJunction = <ConjunctionModel> conditionList[leftJunctionIndex];
   }

   if(leftJunctionIndex < 0) {
      removeJunctionIndex = rightJunctionIndex;
   }
   else if(rightJunctionIndex >= conditionList.length) {
      removeJunctionIndex = leftJunctionIndex;
   }
   else {
      if(leftJunction.level == conditionList[index].level) {
         removeJunctionIndex = leftJunctionIndex;
      }
      else {
         removeJunctionIndex = rightJunctionIndex;
      }
   }

   changeChildrenLevel(conditionList, removeJunctionIndex, -1);
   let idx: number = removeJunctionIndex < index ? removeJunctionIndex : index;
   conditionList.splice(idx, 2);
   fixConditions(conditionList);
}

/**
 * Change children level of an item.
 * @param conditionList
 * @param index the specified index.
 * @param level the specified level increment.
 */
export function changeChildrenLevel(conditionList: ConditionItemModel[], index: number,
                                    level: number): void
{
   for(let i = index + 2; i < conditionList.length; i += 2) {
      if(conditionList[index].level < conditionList[i].level) { // is a child
         conditionList[i].level += level;
      }
      else { // look for over
         break;
      }
   }
}

/**
 * Goes through the condition list and adjusts the level of each condition
 * so that the condition list is still valid. Useful after operations that
 * change the structure of the condition list.
 */
export function fixConditions(conditionList: ConditionItemModel[]): void {
   for(let i = 0; i < conditionList.length; i += 2) {
      let condition: ConditionItemModel = <ConditionItemModel> conditionList[i];
      condition.level = 0;

      if(i - 1 >= 0 && conditionList[i - 1].level > condition.level) {
         condition.level = conditionList[i - 1].level;
      }

      if(i + 1 < conditionList.length &&
         conditionList[i + 1].level > condition.level)
      {
         condition.level = conditionList[i + 1].level;
      }
   }
}