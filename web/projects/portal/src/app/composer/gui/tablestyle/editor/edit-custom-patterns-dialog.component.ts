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
import { Component, EventEmitter, Input, NgZone, OnInit, Output } from "@angular/core";
import { SpecificationModel } from "../../../data/tablestyle/specification-model";
import { TableStyleUtil } from "../../../../common/util/table-style-util";

@Component({
   selector: "edit-custom-patterns-dialog",
   templateUrl: "edit-custom-patterns-dialog.component.html",
})
export class EditCustomPatternsDialog {
   @Input() specModel: SpecificationModel;
   @Output() onCancel = new EventEmitter<string>();
   @Output() onCommit = new EventEmitter();
   groupLevels: Array<{ label: string, value: number }> = TableStyleUtil.GROUP_LEVELS;
   rowColTab = "edit-custom-patterns-dialog-row-column-tab";
   groupingTab = "edit-custom-patterns-dialog-row-grouping-tab";

   constructor(private zone: NgZone) {}

   startLabel(): string {
      return this.specModel.customType == "Row" ? "_#(js:Start Row)" : "_#(js:Start Column)";
   }

   repeatLabel(): string {
      return this.specModel.customType == "Row" ? "_#(js:Repeat every nth row)" : "_#(js:Repeat every nth col)";
   }

   rangeLabel(): string {
      return this.specModel.customType == "Row" ? "_#(js:Column Range)" : "_#(js:Row Range)";
   }

   disabledOk(): boolean {
      return (this.isInvalid(this.specModel.start) || this.isInvalid(this.specModel.from)
         || this.isInvalid(this.specModel.to)) && this.disableGroupTab();
   }

   get startValue(): string {
      return this.specModel.start == null ? null :
         this.specModel.start.toString();
   }

   get fromValue(): string {
      return this.specModel.from == null ? null :
         this.specModel.from.toString();
   }

   get toValue(): string {
      return this.specModel.to == null ? null :
         this.specModel.to.toString();
   }

   setStartValue(value: string) {
      this.specModel.start = parseFloat(value);
   }

   setToValue(value: string) {
      this.specModel.to = parseFloat(value);
   }

   setFromValue(value: string) {
      this.specModel.from = parseFloat(value);
   }

   defaultTab(): string {
      return this.specModel.customType === TableStyleUtil.ROW_GROUP_TOTAL ||
         this.specModel.customType === TableStyleUtil.COLUMN_GROUP_TOTAL ?
         this.groupingTab : this.rowColTab;
   }

   isInvalid(value: number): boolean {
      if(value == null) {
         return false;
      }

      return !Number.isInteger(value) || isNaN(value) || value < 0;
   }

   disableRowTab(): boolean {
      return this.specModel.customType === TableStyleUtil.ROW_GROUP_TOTAL  ||
         this.specModel.customType === TableStyleUtil.COLUMN_GROUP_TOTAL;
   }

   disableGroupTab(): boolean {
      return this.specModel.customType === TableStyleUtil.ROW  ||
         this.specModel.customType === TableStyleUtil.COLUMN;
   }

   cancel(): void {
      this.onCancel.emit();
   }

   ok() {
      this.onCommit.emit();
   }

   enter() {
      this.zone.run(() => this.ok());
   }

   protected readonly TableStyleUtil = TableStyleUtil;
}
