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
import {
   Component,
   ElementRef,
   EventEmitter,
   Input,
   OnInit,
   Output,
   ViewChild,
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Condition } from "../../common/data/condition/condition";
import { ConditionItemPaneProvider } from "../../common/data/condition/condition-item-pane-provider";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { JunctionOperator } from "../../common/data/condition/junction-operator";
import { JunctionOperatorType } from "../../common/data/condition/junction-operator-type";
import { SubqueryTable } from "../../common/data/condition/subquery-table";
import { DataRef } from "../../common/data/data-ref";
import {
   changeChildrenLevel,
   deleteCondition,
   fixConditions,
   isValidCondition
} from "../../common/util/condition.util";
import { Tool } from "../../../../../shared/util/tool";
import { ComponentTool } from "../../common/util/component-tool";
import { GuiTool } from "../../common/util/gui-tool";

@Component({
   selector: "condition-pane",
   templateUrl: "condition-pane.component.html",
   styleUrls: ["condition-pane.component.scss"]
})
export class ConditionPane implements OnInit {
   JunctionOperatorType = JunctionOperatorType;
   ConditionOperation = ConditionOperation;
   @Input() subqueryTables: SubqueryTable[];
   @Input() fields: DataRef[];
   @Input() table: string;
   @Input() vsId: string;
   @Input() provider: ConditionItemPaneProvider;
   @Input() onlyOr: boolean = false;
   @Input() isVSContext = true;
   @Input() isHighlight: boolean = false;
   @Input() showExpression = true;
   @Input() fillParent: boolean;
   @Input() showOriginalName: boolean = false;
   @Output() conditionListChange: EventEmitter<any[]> = new EventEmitter<any[]>();
   @Output() conditionChange = new EventEmitter<{selectedIndex: number, condition: Condition}>();
   @Output() junctionChange = new EventEmitter<{selectedIndex: number, junctionType: JunctionOperatorType}>();
   @ViewChild("itemPane") itemPane: ElementRef<any>;
   @ViewChild("conditionPane") conditionPane: ElementRef<any>;
   junctionType: JunctionOperatorType;
   selectedIndex: number;
   private _conditionList: any[]; // even indexes contain conditions, odd contain junctions
   private _condition: Condition;
   _availableFields: DataRef[];

   @Input()
   set conditionList(conditionList: any[]) {
      this._conditionList = conditionList;

      if(conditionList && conditionList.length > 0) {
         if(!this.selectedIndex) {
            this.conditionItemSelected(0);
         }
         else if(this.selectedIndex < conditionList.length) {
            this.conditionItemSelected(this.selectedIndex);
         }
      }
      else {
         if((this.selectedIndex != null && this.selectedIndex != undefined) || !this.condition) {
            this._condition = this.emptyCondition();
            this.selectedIndex = null;
         }
      }
   }

   get conditionList(): any[] {
      return this._conditionList;
   }

   set condition(condition: Condition) {
      let oldCondition = null;

      if(this.conditionList && this.selectedIndex != null){
         oldCondition = this.conditionList[this.selectedIndex];
      }

      if(!Tool.isEquals(oldCondition, condition) && this.selectedIndex % 2 == 0) {
         this._condition = condition;
         this.conditionChange.emit({selectedIndex: this.selectedIndex, condition: condition});
      }
      else {
         this.conditionChange.emit(null);
      }
   }

   get condition(): Condition {
      return this._condition;
   }

   @Input()
   set availableFields(availableFields: DataRef[]) {
      this._availableFields = availableFields;

      if((this.selectedIndex == null || this.selectedIndex == undefined) && this.condition) {
         let fieldExist =
            availableFields?.find(field => Tool.isEquals(field, this.condition.field));

         if(!fieldExist) {
            this.condition = this.emptyCondition();
         }
      }
   }

   get availableFields(): DataRef[] {
      return this._availableFields;
   }

   constructor(private modalService: NgbModal) {
   }

   ngOnInit(): void {
      this.junctionType = this.onlyOr ?
         JunctionOperatorType.OR : JunctionOperatorType.AND;

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

   get listPaneHeight(): number {
      if(this.fillParent && this.itemPane && this.conditionPane) {
         let itemPaneRect = GuiTool.getElementRect(this.itemPane.nativeElement);
         let conditionPaneRect = GuiTool.getElementRect(this.conditionPane.nativeElement);

         return conditionPaneRect.height - itemPaneRect.height;
      }

      return Number.NaN;
   }

   emptyCondition(): Condition {
      return {
         jsonType: "condition",
         field: null,
         operation: ConditionOperation.EQUAL_TO,
         values: [],
         level: 1,
         equal: false,
         negated: false
      };
   }

   insert(): boolean {
      if(this.condition == null || this.condition.field == null) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", "_#(js:field.required)");
         return false;
      }

      const conditionList = Tool.clone(this.conditionList);
      const level: number = this.selectedIndex != null
         ? conditionList[this.selectedIndex].level : 0;
      const index: number = this.selectedIndex != null
         ? (this.selectedIndex % 2 == 0 ? this.selectedIndex : this.selectedIndex + 1) : 0;

      const condition: Condition = Tool.clone(this.condition);
      condition.level = level;
      conditionList.splice(index, 0, condition);

      if(conditionList.length > 1) {
         let junction: JunctionOperator = <JunctionOperator> {
            jsonType: "junction",
            type: this.junctionType,
            level: level
         };

         conditionList.splice(index + 1, 0, junction);
      }
      else {
         this.selectedIndex = null;
      }

      this.conditionListChange.emit(conditionList);
      return true;
   }

   modify(): void {
      const conditionList = Tool.clone(this.conditionList);

      if(this.selectedIndex % 2 == 0) {
         const level: number = conditionList[this.selectedIndex].level;
         conditionList[this.selectedIndex] = Tool.clone(this.condition);
         conditionList[this.selectedIndex].level = level;
      }
      else {
         conditionList[this.selectedIndex] = <JunctionOperator> {
            jsonType: "junction",
            type: this.junctionType,
            level: this.conditionList[this.selectedIndex].level
         };
      }

      this.conditionListChange.emit(conditionList);
   }

   public saveOption(): string {
      if(!this.isConditionValid()) {
         return null;
      }

      return this.selectedIndex != null ? "modify" : "insert";
   }

   public save(): string {
      if(!this.isConditionValid()) {
         return null;
      }

      if(this.selectedIndex != null) {
         this.modify();
         return "modify";
      }
      else {
         return this.insert() ? "insert" : null;
      }
   }

   delete(): void {
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

   clear(): void {
      this.selectedIndex = null;
      this._condition = this.emptyCondition();
      this.conditionListChange.emit([]);
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

   up(): void {
      if(this.canMoveUp()) {
         const conditionList = Tool.clone(this.conditionList);
         const index: number = this.selectedIndex - 2;
         const selectedCondition: Condition = Tool.clone(conditionList[this.selectedIndex]);
         const otherCondition: Condition = Tool.clone(conditionList[index]);

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

   down(): void {
      if(this.canMoveDown()) {
         const conditionList = Tool.clone(this.conditionList);
         const index: number = this.selectedIndex + 2;
         const selectedCondition: Condition = Tool.clone(conditionList[this.selectedIndex]);
         const otherCondition: Condition = Tool.clone(conditionList[index]);

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

   public isConditionValid(): boolean {
      return isValidCondition(this.condition);
   }

   conditionItemSelected(index: number): void {
      this.selectedIndex = index;
      this.selectCondition(index);

      if(this.conditionList[index] && this.conditionList[index].jsonType == "junction") {
         this.junctionType = this.conditionList[index].type;
         this.updateDirtyJunction();
      }
   }

   private selectCondition(index: number) {
      if(index % 2 == 0) {
         this._condition = Tool.clone(this.conditionList[index]);
         this.conditionChange.emit(null);
      }
   }

   updateDirtyJunction() {
      if(this.selectedIndex % 2 == 1 &&
         this.junctionType != this.conditionList[this.selectedIndex].type)
      {
         this.junctionChange.emit(
            {selectedIndex: this.selectedIndex, junctionType: this.junctionType});
      }
      else {
         this.junctionChange.emit(null);
      }
   }

   canMoveUp(): boolean {
      return this.selectedIndex != null && this.conditionList.length > 2 &&
         this.selectedIndex % 2 == 0 && this.selectedIndex - 2 >= 0;
   }

   canMoveDown(): boolean {
      return this.selectedIndex != null && this.conditionList.length > 2 &&
         this.selectedIndex % 2 == 0 &&
         this.selectedIndex + 2 < this.conditionList.length;
   }

   canIndent(): boolean {
      if(this.selectedIndex == null || this.selectedIndex % 2 == 0) {
         return false;
      }

      for(let i = this.selectedIndex - 2; i >= 0; i -= 2) {
         if(this.conditionList[this.selectedIndex].level > this.conditionList[i].level) {
            break;
         }

         if(this.conditionList[this.selectedIndex].level == this.conditionList[i].level) {
            return true;
         }
      }

      for(let i = this.selectedIndex + 2; i < this.conditionList.length; i += 2) {
         if(this.conditionList[this.selectedIndex].level > this.conditionList[i].level) {
            break;
         }

         if(this.conditionList[this.selectedIndex].level == this.conditionList[i].level) {
            return true;
         }
      }

      return false;
   }

   canUnindent(): boolean {
      return !(this.selectedIndex == null || this.selectedIndex % 2 == 0 ||
               this.conditionList[this.selectedIndex].level == 0);
   }

   get buttonText(): string {
      if(this.conditionList.length > 0) {
         return "_#(js:Insert)";
      }
      else {
         return "_#(js:Append)";
      }
   }

   expressionRenamed(event: {oname: string, nname: string}) {
      this.conditionList = this.conditionList.map(c => {
         return c.field && c.field.name == event.oname
            ? {...c, field: {...c.field,
                             name: event.nname,
                             view: event.nname,
                             attribute: event.nname}}
            : c;
      });
      this.conditionListChange.emit(this.conditionList);
   }
}
