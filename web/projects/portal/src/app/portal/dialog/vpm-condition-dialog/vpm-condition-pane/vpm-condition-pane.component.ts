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
import { Component, ElementRef, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { GuiTool } from "../../../../common/util/gui-tool";
import { VPMColumnModel } from "../../../data/model/datasources/database/vpm/condition/vpm-column-model";
import { ConditionItemModel } from "../../../data/model/datasources/database/vpm/condition/condition-item-model";
import {
   DataConditionItemPaneProvider
} from "../../../data/model/datasources/database/vpm/condition/clause/data-condition-item-pane-provider";
import { ConjunctionModel } from "../../../data/model/datasources/database/vpm/condition/conjunction/conjunction-model";
import { ConjunctionTypes } from "../../../data/model/datasources/database/vpm/condition/conjunction/conjunction-types";
import { ClauseModel } from "../../../data/model/datasources/database/vpm/condition/clause/clause-model";
import { Tool } from "../../../../../../../shared/util/tool";
import {
   ClauseOperationSymbols
} from "../../../data/model/datasources/database/vpm/condition/clause/clause-operation-symbols";
import { ClauseValueTypes } from "../../../data/model/datasources/database/vpm/condition/clause/clause-value-types";
import { OperationModel } from "../../../data/model/datasources/database/vpm/condition/clause/operation-model";
import {
   changeChildrenLevel,
   deleteCondition,
   fixConditions,
   isValidCondition
} from "../../../data/model/datasources/database/vpm/condition/util/vpm-condition.util";
import { ClauseValueModel } from "../../../data/model/datasources/database/vpm/condition/clause/clause-value-model";

@Component({
   selector: "vpm-condition-pane",
   templateUrl: "vpm-condition-pane.component.html",
   styleUrls: ["vpm-condition-pane.component.scss"]
})
export class VPMConditionPane implements OnInit {
   @Input() fields: VPMColumnModel[];
   @Input() provider: DataConditionItemPaneProvider;
   @Input() havingCondition: boolean = false;
   @Output() conditionListChange: EventEmitter<ConditionItemModel[]> = new EventEmitter<ConditionItemModel[]>();
   @Output() conditionChange = new EventEmitter<ConditionItemModel>();
   conjunction: ConjunctionModel;
   selectedIndex: number;
   ConjunctionTypes = ConjunctionTypes;
   private _conditionList: ConditionItemModel[]; // even indexes contain conditions, odd contain junctions
   private _condition: ClauseModel;

   /**
    * Input for conditionList. If condition list is empty, create and select a new empty clause.
    * @param conditionList
    */
   @Input()
   set conditionList(conditionList: ConditionItemModel[]) {
      this._conditionList = conditionList;

      if(!!conditionList && conditionList.length > 0) {
         if(!this.selectedIndex) {
            this.conditionItemSelected(0);
         }
         else if(this.selectedIndex < conditionList.length) {
            this.conditionItemSelected(this.selectedIndex);
         }
      }
      else {
         this._condition = this.emptyCondition();
         this.selectedIndex = null;
      }
   }

   get conditionList(): ConditionItemModel[] {
      return this._conditionList;
   }

   set condition(condition: ClauseModel) {
      let oldCondition = null;

      if(this.conditionList && this.selectedIndex) {
         oldCondition = this.conditionList[this.selectedIndex];
      }

      if(!Tool.isEquals(oldCondition, condition)) {
         this._condition = condition;
         this.conditionChange.emit(condition);
      }
      else {
         this.conditionChange.emit(null);
      }

      this._condition = condition;
   }

   get condition(): ClauseModel {
      return this._condition;
   }

   constructor(private elementRef: ElementRef) {
   }

   ngOnInit(): void {
      this.conjunction = {
         type: "conjunction",
         level: 0,
         junc: true,
         conjunction: ConjunctionTypes.AND,
         isNot: false
      };

      if(this.conditionList == null) {
         this.conditionList = [];
      }
      else if(this.conditionList.length > 0) {
         this.conditionItemSelected(0);
      }

      if(this._condition == null) {
         this._condition = this.emptyCondition();
      }
   }

   modifyDisable(): boolean {
      return this.selectedIndex == null ||
         (this.selectedIndex % 2 == 0 && !this.isConditionValid(this.condition));
   }

   containsSubquery() {
      return this.condition && (this.isSubqueryValue(this.condition.value1) ||
         this.isSubqueryValue(this.condition.value2) ||
         this.isSubqueryValue(this.condition.value3));
   }

   isSubqueryValue(value: ClauseValueModel): boolean {
      return !!value && ClauseValueTypes.SUBQUERY === value.type;
   }

   /**
    * Method to get a default empty ClauseModel.
    * @returns {ClauseModel}  the empty clause model
    */
   emptyCondition(): ClauseModel {
      let clause: ClauseModel = {
         type: "clause",
         junc: false,
         level: 0,
         negated: false,
         operation: {
            name: "equal to",
            symbol: ClauseOperationSymbols.EQUAL_TO
         },
         value1: {
            type: ClauseValueTypes.FIELD
         },
         value2: {
            type: ClauseValueTypes.VALUE
         },
         value3: {
            type: ClauseValueTypes.VALUE
         }
      };
      const operations: OperationModel[] = this.provider.getConditionOperations(clause);
      clause.operation = operations.some((op) =>
         op.symbol == ClauseOperationSymbols.EQUAL_TO) ? clause.operation : operations[0];
      return clause;
   }

   /**
    * Check if the given condition is valid.
    * @param condition  the condition to check
    * @returns {boolean}   true if valid
    */
   isConditionValid(condition: ClauseModel): boolean {
      return isValidCondition(condition);
   }

   /**
    * Called when a condition item is selected. Update the selected index and copy selected item.
    * @param index   the index of the selected condition item
    */
   conditionItemSelected(index: number): void {
      this.selectedIndex = index;
      this.selectCondition(index);

      if(this.conditionList[index] && this.conditionList[index].junc) {
         this.conjunction = <ConjunctionModel> Tool.clone(this.conditionList[index]);
      }
   }

   /**
    * Copy the selected condition item at the given index.
    * @param index   the index of the selected condition item.
    */
   private selectCondition(index: number) {
      if(index % 2 == 0) {
         this._condition = <ClauseModel> Tool.clone(this.conditionList[index]);
      }
   }

   /**
    * Get button text translation string for the Insert/Append button.
    * @returns {any}
    */
   get buttonText(): string {
      if(this.conditionList.length > 0) {
         return "_#(js:Insert)";
      }
      else {
         return "_#(js:Append)";
      }
   }

   /**
    * Insert the current clause model at the selected index.
    */
   insert(): void {
      const conditionList = Tool.clone(this.conditionList);
      const level: number = this.selectedIndex != null ? conditionList[this.selectedIndex].level : 0;
      const index: number = this.selectedIndex != null ?
         (this.selectedIndex % 2 == 0 ? this.selectedIndex : this.selectedIndex + 1) : 0;

      const condition: ClauseModel = Tool.clone(this.condition);
      condition.level = level;
      conditionList.splice(index, 0, condition);

      if(conditionList.length > 1) {
         let junction: ConjunctionModel = Tool.clone(this.conjunction);
         junction.level = level;
         conditionList.splice(index + 1, 0, junction);
      }
      else {
         this.selectedIndex = 0;
      }

      this.conditionListChange.emit(conditionList);
   }

   /**
    * Modify the condition item at the current selected index.
    */
   modify(): void {
      const conditionList = Tool.clone(this.conditionList);
      const level: number = conditionList[this.selectedIndex].level;

      if(this.selectedIndex % 2 == 0) {
         conditionList[this.selectedIndex] = Tool.clone(this.condition);
         conditionList[this.selectedIndex].level = level;
      }
      else {
         let junction: ConjunctionModel = Tool.clone(this.conjunction);
         junction.level = level;
         conditionList[this.selectedIndex] = junction;
      }

      this.conditionListChange.emit(conditionList);
   }

   /**
    * Delete the currently selected condition item.
    */
   deleteCondition(): void {
      const conditionList = Tool.clone(this.conditionList);
      deleteCondition(conditionList, this.selectedIndex);

      if(conditionList.length == 0) {
         this.selectedIndex = null;
      }
      else if(this.selectedIndex >= conditionList.length) {
         this.selectedIndex = conditionList.length - 1;
      }

      if(this.selectedIndex != undefined) {
         this.selectCondition(this.selectedIndex);
      }
      else {
         this._condition = this.emptyCondition();
      }

      this.conditionListChange.emit(conditionList);
   }

   /**
    * Clear all condition items from the condition list.
    */
   clear(): void {
      this.selectedIndex = null;
      this._condition = this.emptyCondition();
      this.conditionListChange.emit([]);
   }

   /**
    * Check if currently selected condition item can be indented.
    * @returns {boolean}   true if can be indented
    */
   canIndent(): boolean {
      if(this.selectedIndex == null || this.selectedIndex % 2 == 0) {
         return false;
      }

      const selectedLevel: number = this.conditionList[this.selectedIndex].level;

      for(let i = this.selectedIndex - 2; i >= 0; i -= 2) {
         if(selectedLevel > this.conditionList[i].level) {
            break;
         }

         if(selectedLevel == this.conditionList[i].level) {
            return true;
         }
      }

      for(let i = this.selectedIndex + 2; i < this.conditionList.length; i += 2) {
         if(selectedLevel > this.conditionList[i].level) {
            break;
         }

         if(selectedLevel == this.conditionList[i].level) {
            return true;
         }
      }

      return false;
   }

   /**
    * Check if currently selected condition item can be unindented.
    * @returns {boolean}   true if can be unindented
    */
   canUnindent(): boolean {
      if(this.selectedIndex == null || this.selectedIndex % 2 == 0 ||
         this.conditionList[this.selectedIndex].level == 0)
      {
         return false;
      }

      return true;
   }

   /**
    * Check if selected condition item can be moved up.
    * @returns {boolean}   true if can move up
    */
   canMoveUp(): boolean {
      return this.selectedIndex != null && this.conditionList.length > 2 &&
         this.selectedIndex % 2 == 0 && this.selectedIndex - 2 >= 0;
   }

   /**
    * Check if selected condition item can be moved down.
    * @returns {boolean}   true if can move down
    */
   canMoveDown(): boolean {
      return this.selectedIndex != null && this.conditionList.length > 2 &&
         this.selectedIndex % 2 == 0 &&
         this.selectedIndex + 2 < this.conditionList.length;
   }

   indent(): void {
      if(this.canIndent()) {
         const conditionList = Tool.clone(this.conditionList);
         changeChildrenLevel(conditionList, this.selectedIndex, 1);
         conditionList[this.selectedIndex].level += 1;
         fixConditions(conditionList);
         this.conditionListChange.emit(conditionList);
      }
   }

   unindent(): void {
      if(this.canUnindent()) {
         const conditionList = Tool.clone(this.conditionList);
         changeChildrenLevel(conditionList, this.selectedIndex, -1);
         conditionList[this.selectedIndex].level -= 1;
         fixConditions(conditionList);
         this.conditionListChange.emit(conditionList);
      }
   }

   /**
    * Move the currently selected condition item up.
    */
   up(): void {
      if(this.canMoveUp()) {
         const conditionList = Tool.clone(this.conditionList);
         const index: number = this.selectedIndex - 2;
         const selectedCondition: ConditionItemModel = Tool.clone(conditionList[this.selectedIndex]);
         const otherCondition: ConditionItemModel = Tool.clone(conditionList[index]);

         // swap levels
         const selectedLevel: number = selectedCondition.level;
         selectedCondition.level = conditionList[index].level;
         otherCondition.level = selectedLevel;

         // swap conditions
         conditionList[this.selectedIndex] = otherCondition;
         conditionList[index] = selectedCondition;
         this.selectedIndex = index;
         this.conditionListChange.emit(conditionList);
      }
   }

   /**
    * Move the currently selected condition item down.
    */
   down(): void {
      if(this.canMoveDown()) {
         const conditionList = Tool.clone(this.conditionList);
         const index: number = this.selectedIndex + 2;
         const selectedCondition: ConditionItemModel = Tool.clone(conditionList[this.selectedIndex]);
         const otherCondition: ConditionItemModel = Tool.clone(conditionList[index]);

         // swap levels
         const selectedLevel: number = selectedCondition.level;
         selectedCondition.level = this.conditionList[index].level;
         otherCondition.level = selectedLevel;

         // swap conditions
         conditionList[this.selectedIndex] = otherCondition;
         conditionList[index] = selectedCondition;
         this.selectedIndex = index;
         this.conditionListChange.emit(conditionList);
      }
   }

   getContainerClass(): string {
      return this.isQueryConditionPane() ? "query-container-fluid" : "";
   }

   getConditionListBoxClass(): string {
      return this.havingCondition ? "condition-list-box-having" : this.isQueryConditionPane() ?
         "condition-list-box-big" : "condition-list-box";
   }

   isQueryConditionPane(): boolean {
      return GuiTool.parentContainsClass(this.elementRef.nativeElement, "query-condition-pane");
   }

   public processDirtyCondition(): void {
      if(this.selectedIndex == null || this.selectedIndex < 0) {
         this.insert();
      }
      else {
         this.modify();
      }
   }

   conjunctionChanged(): void {
      if(this.selectedIndex != null && this.selectedIndex % 2 != 0) {
         this.conditionChange.emit(this.condition);
      }
   }
}
