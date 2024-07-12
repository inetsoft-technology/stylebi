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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { Condition } from "../../common/data/condition/condition";
import { ConditionItemPaneProvider } from "../../common/data/condition/condition-item-pane-provider";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { JunctionOperator } from "../../common/data/condition/junction-operator";
import { JunctionOperatorType } from "../../common/data/condition/junction-operator-type";
import { SubqueryTable } from "../../common/data/condition/subquery-table";
import { DataRef } from "../../common/data/data-ref";
import { Tool } from "../../../../../shared/util/tool";

@Component({
   selector: "simple-condition-pane",
   templateUrl: "simple-condition-pane.component.html",
   styleUrls: ["simple-condition-pane.component.scss"]
})
export class SimpleConditionPane implements OnInit {
   public ConditionOperation = ConditionOperation;
   @Input() subqueryTables: SubqueryTable[];
   @Input() fields: DataRef[];
   @Input() provider: ConditionItemPaneProvider;
   @Input() isVSContext = true;
   @Input() variableNames: string[];
   @Output() conditionListChange = new EventEmitter<any[]>();
   condition: Condition;
   selectedIndex: number;
   private _conditionList: any[]; // even indexes contain conditions, odd contain junctions

   @Input()
   set conditionList(conditionList: any[]) {
      this._conditionList = Tool.clone(conditionList);

      if(this.selectedIndex >= 0 && this.selectedIndex < this._conditionList.length) {
         if(this.selectedIndex % 2 !== 0) {
            this.condition = null;
         }
         else if(this.selectedIndex % 2 === 0) {
            this.condition = Tool.clone(this.conditionList[this.selectedIndex]);
         }
      }
      else {
         this.condition = null;
         this.selectedIndex = null;
      }
   }

   get conditionList(): any[] {
      return this._conditionList;
   }

   ngOnInit(): void {
      if(this.conditionList == null) {
         this.conditionList = [];
      }
   }

   more(): void {
      if(this.conditionList.length > 0) {
         this.conditionList.push(<JunctionOperator> {
            jsonType: "junction",
            type: JunctionOperatorType.AND,
            level: 0
         });
      }

      let newCondition: Condition = <Condition> {
         jsonType: "condition",
         field: this.fields != null && this.fields.length > 0 ? this.fields[0] : null,
         operation: this.provider.getConditionOperations(null)[0],
         values: [],
         level: 0,
         equal: false,
         negated: false
      };

      this.conditionList.push(newCondition);
      this.conditionItemSelected(this.conditionList.length - 1);
      this.conditionListChange.emit(this.conditionList);
   }

   fewer(): void {
      if(this.selectedIndex == null || this.selectedIndex % 2 != 0) {
         return;
      }

      let selectedIndex = this.selectedIndex;
      this.conditionList.splice(selectedIndex, 1);

      if(this.selectedIndex < this.conditionList.length) {
         this.conditionList.splice(selectedIndex, 1);
      }
      else if(this.conditionList.length > 0) {
         this.conditionList.splice(selectedIndex - 1, 1);
         selectedIndex -= 2;
      }
      else {
         selectedIndex = null;
      }

      this.conditionItemSelected(selectedIndex);
      this.conditionListChange.emit(this.conditionList);
   }

   modify(): void {
      if(this.selectedIndex % 2 == 0) {
         let updatedConditionList = [...this.conditionList];
         updatedConditionList[this.selectedIndex] = Tool.clone(this.condition);
         updatedConditionList[this.selectedIndex].level = 0;
         this.conditionListChange.emit(updatedConditionList);
      }
   }

   conditionItemSelected(index: number): void {
      this.selectedIndex = index;

      if(index == null || index % 2 != 0) {
         this.condition = null;
      }
      else if(index % 2 == 0) {
         this.condition = Tool.clone(this.conditionList[index]);
      }
   }

   conditionChange(condition: Condition) {
      this.condition = condition;
      this.modify();
   }
}
