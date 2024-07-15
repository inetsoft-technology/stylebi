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
import { Component, EventEmitter, Input, Output, QueryList, ViewChildren } from "@angular/core";
import { NgControl } from "@angular/forms";
import { JoinModel } from "../../../../../../model/datasources/database/physical-model/join-model";
import { joinMap } from "../../../../../../model/datasources/database/physical-model/join-type.config";
import { JoinType } from "../../../../../../model/datasources/database/physical-model/join-type.enum";
import { ValueLabelPair } from "../../../../../../model/datasources/database/value-label-pair";
import { MergingRule } from "../../../../../../model/datasources/database/physical-model/merging-rule.enum";
import { Cardinality } from "../../../../../../model/datasources/database/physical-model/cardinality.enum";
import {DataType} from "../../../../common-components/join-thumbnail.service";

@Component({
   selector: "edit-join-dialog",
   templateUrl: "edit-join-dialog.component.html"
})
export class EditJoinDialog {
   @Input() removeEnabled: boolean = true;
   @Input() dataType: string = DataType.PHYSICAL;

   _joinModel: JoinModel;
   get joinModel(): JoinModel {
      return this._joinModel;
   }

   @Input()
   set joinModel(joinModel: JoinModel) {
      this._joinModel = joinModel;

      if(joinModel) {
         this.selectedJoinOrderPriority = joinModel.orderPriority;
      }
   }

   @Output() onCommit: EventEmitter<JoinModel> = new EventEmitter<JoinModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChildren(NgControl) controls: QueryList<NgControl>;

   joinTypeOptions: ValueLabelPair[] = [
      { value: JoinType.EQUAL, label: "_#(js:Equal [=])" },
      { value: JoinType.LEFT_OUTER, label: "_#(js:Left Outer [*=])" },
      { value: JoinType.RIGHT_OUTER, label: "_#(js:Right Outer [=*])" },
      { value: JoinType.GREATER, label: "_#(js:Greater [>])" },
      { value: JoinType.LESS, label: "_#(js:Less [<])" },
      { value: JoinType.GREATER_EQUAL, label: "_#(js:Greater or Equal [>=])" },
      { value: JoinType.LESS_EQUAL, label: "_#(js:Less or Equal [<=])" },
      { value: JoinType.NOT_EQUAL, label: "_#(js:Not Equal [<>])" }
   ];
   fullOuterJoinTypeOptions: ValueLabelPair[] = [
      { value: JoinType.EQUAL, label: "_#(js:Equal [=])" },
      { value: JoinType.LEFT_OUTER, label: "_#(js:Left Outer [*=])" },
      { value: JoinType.RIGHT_OUTER, label: "_#(js:Right Outer [=*])" },
      { value: JoinType.FULL_OUTER, label: "_#(js:Full Outer [*=*])" },
      { value: JoinType.GREATER, label: "_#(js:Greater [>])" },
      { value: JoinType.LESS, label: "_#(js:Less [<])" },
      { value: JoinType.GREATER_EQUAL, label: "_#(js:Greater or Equal [>=])" },
      { value: JoinType.LESS_EQUAL, label: "_#(js:Less or Equal [<=])" },
      { value: JoinType.NOT_EQUAL, label: "_#(js:Not Equal [<>])" }
   ];
   selectedJoinOrderPriority: number = 1;
   MergingRule = MergingRule;
   Cardinality = Cardinality;

   /**
    * Called when order priority is changed. If it is a valid input, change the selected joins order
    * priority.
    * @param valid   if the new order priority is valid
    */
   validateOrderPriority(valid: boolean): void {
      if(valid) {
         this.joinModel.orderPriority = this.selectedJoinOrderPriority;
      }
   }

   get joinTypes(): ValueLabelPair[] {
      return !!this.joinModel && this.joinModel.supportFullOuter
         ? this.fullOuterJoinTypeOptions
         : this.joinTypeOptions;
   }

   get joinPreview(): string {
      return `${this.joinModel.table}.${this.joinModel.column}`
         + ` ${joinMap(this.joinModel.type)} `
         + `${this.joinModel.foreignTable}.${this.joinModel.foreignColumn}`;
   }

   get okDisabled(): boolean {
      return !!this.controls?.some(control => control.dirty && control.invalid);
   }

   ok(): void {
      this.joinModel.delete = false;
      this.onCommit.emit(this.joinModel);
   }

   cancel(): void {
      this.onCancel.emit(null);
   }

   remove(): void {
      this.joinModel.delete = true;
      this.onCommit.emit(this.joinModel);
   }
}
