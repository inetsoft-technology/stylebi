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
import { Condition } from "../data/condition/condition";
import { ConditionOperation } from "../data/condition/condition-operation";
import { ConditionValueType } from "../data/condition/condition-value-type";
import { JunctionOperator } from "../data/condition/junction-operator";
import { JunctionOperatorType } from "../data/condition/junction-operator-type";
import { DataRef } from "../data/data-ref";
import { GroupRef } from "../data/group-ref";
import { ConditionList } from "./condition-list";

export function deleteCondition(conditionList: ConditionList, index: number): void {
   if(conditionList.length == 1) {
      conditionList.splice(0, 1);
      return;
   }

   let ljidx: number = index - 1;
   let rjidx: number = index + 1;
   let rmjidx: number;
   let ljun: JunctionOperator;

   if(ljidx >= 0) {
      ljun = <JunctionOperator> conditionList[ljidx];
   }

   if(ljidx < 0) {
      rmjidx = rjidx;
   }
   else if(rjidx >= conditionList.length) {
      rmjidx = ljidx;
   }
   else {
      if(ljun.level == conditionList[index].level) {
         rmjidx = ljidx;
      }
      else {
         rmjidx = rjidx;
      }
   }

   changeChildrenLevel(conditionList, rmjidx, -1);
   let idx = rmjidx < index ? rmjidx : index;
   conditionList.splice(idx, 2);
   fixConditions(conditionList);
}

/**
 * Change children level of an item.
 * @param conditionList
 * @param index the specified index.
 * @param level the specified level increment.
 */
export function changeChildrenLevel(conditionList: ConditionList, index: number, level: number): void {
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
export function fixConditions(conditionList: ConditionList): void {
   for(let i = 0; i < conditionList.length; i += 2) {
      let condition = <Condition> conditionList[i];
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

/**
 * Checks whether the condition list is valid.
 *
 * @param conditionList the condition list to check
 *
 * @returns true if condition list is valid, false otherwise
 */
export function isValidConditionList(conditionList: ConditionList): boolean {
   const rankingConditionList: ConditionList = [];

   for(let i = 0; i < conditionList.length; i += 2) {
      const condition = conditionList[i] as Condition;

      if(!isValidCondition(condition)) {
         return false;
      }

      if(condition.operation === ConditionOperation.TOP_N ||
         condition.operation === ConditionOperation.BOTTOM_N)
      {
         if(rankingConditionList.length > 0) {
            rankingConditionList.push({
               jsonType: "junction",
               type: JunctionOperatorType.AND,
               level: 0
            } as JunctionOperator);
         }

         rankingConditionList.push(condition);
      }
   }

   return isValidRankingConditionList(rankingConditionList);
}

function isValidRankingConditionList(rankingConditionList: ConditionList): boolean {
   const groupRefViews = new Set<string>();

   for(let i = 0; i < rankingConditionList.length; i += 2) {
      const condition = rankingConditionList[i] as Condition;
      const field = condition.field;

      if(field != null && field.classType === "GroupRef")
      {
         groupRefViews.add(field.view);
      }
   }

   const groupRefArray = Array.from(groupRefViews);
   const counts = new Array(groupRefArray.length + 1).fill(0);

   for(let i = 0; i < rankingConditionList.length; i += 2) {
      const condition = rankingConditionList[i] as Condition;
      const field = condition.field;
      const index = groupRefArray.indexOf(field.view) + 1;
      counts[index]++;
   }

   return !counts.some((count) => count > 1);
}

/**
 * Checks whether the condition is valid
 *
 * @param condition the condition to check
 *
 * @returns true if condition is valid, false otherwise
 */
export function isValidCondition(condition: Condition): boolean {
   if(condition.operation !== ConditionOperation.NULL &&
      (condition.values === null || condition.values.length === 0)) {
      return false;
   }

   if(condition.operation === ConditionOperation.BETWEEN && condition.values.length < 2) {
      return false;
   }

   if(condition.operation === ConditionOperation.ONE_OF && condition.values.length === 0) {
      return false;
   }

   let values = condition.values;

   if(condition.operation === ConditionOperation.EQUAL_TO) {
      values = [condition.values[0]];
   }

   for(const value of values) {
      if((condition.operation !== ConditionOperation.ONE_OF &&
          (!value || value.value == null || value.value === "")) || value.value === "$()")
      {
         return false;
      }

      if(value.type === ConditionValueType.EXPRESSION && (value.value.expression == null ||
            value.value.expression === "")) {
         return false;
      }

      if(value.type === ConditionValueType.SUBQUERY && value.value.query == null) {
         return false;
      }

      if(value.type === ConditionValueType.FIELD) {
         const attrRef = condition.field;
         const valueRef = value.value as DataRef;

         if(attrRef != null && valueRef != null &&
            ((attrRef.classType === "AggregateRef" && valueRef.classType !== "AggregateRef") ||
               (attrRef.classType !== "AggregateRef" && valueRef.classType === "AggregateRef")))
         {
            return false;
         }
      }
   }

   return true;
}