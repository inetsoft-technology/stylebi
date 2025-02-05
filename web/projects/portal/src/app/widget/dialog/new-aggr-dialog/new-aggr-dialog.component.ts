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
import { Component, OnInit, Output, EventEmitter, Input } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { NewAggrDialogModel } from "./new-aggr-dialog-model";
import { XSchema } from "../../../common/data/xschema";
import { AssetUtil } from "../../../binding/util/asset-util";
import { AggregateFormula } from "../../../binding/util/aggregate-formula";
import { SummaryAttrUtil } from "../../../binding/util/summary-attr-util";

@Component({
   selector: "new-aggr-dialog",
   templateUrl: "./new-aggr-dialog.component.html"
})
export class NewAggrDialog implements OnInit {
   @Input() model: NewAggrDialogModel;
   controller: string = "../api/composer/vs/new-aggr-dialog-model/1";
   form: UntypedFormGroup;
   formulas: any[];
   _nStr: string = "";
   @Output() onCommit: EventEmitter<NewAggrDialogModel> = new EventEmitter<NewAggrDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();

   aggregates: string[] = ["Sum", "Average", "Max", "Min", "Count",
      "Distinct Count", "First", "Last", "Correlation", "Covariance",
      "Variance", "Median", "Mode", "Std Deviation", "Variance (Pop)",
      "Std Deviation (Pop)", "Weighted Average"];

   ngOnInit(): void {
      this.initForm();
   }

   initForm(): void {
      this.form = new UntypedFormGroup({
         field: new UntypedFormControl(this.model.field, [
            Validators.required,
         ])
      });
   }

   cancelChanges(): void {
      this.onCancel.emit("cancel");
   }

   saveChanges(): void {
      this.onCommit.emit(this.model);
   }

   fieldChanged(field: string): void {
      const fieldIndex: number = this.model.fields.indexOf(field);
      const fieldType: string = fieldIndex !== -1 ? this.model.fieldsType[fieldIndex] : null;
      let formulaList = AssetUtil.getFormulas(fieldType, true);
      this.formulas = AggregateFormula.getFormulaObjs(formulaList);
      this.model.aggregate = XSchema.isNumericType(fieldType) ? "Sum" : "Count";
   }

   isWithFormula(): boolean {
      return SummaryAttrUtil.isWithFormula(this.model.aggregate);
   }

   changeWithCol(evt: string) {
      if(this.isWithFormula()) {
         this.model.with = this.model.fields[0];
      }
   }

   hasN(): boolean {
      return SummaryAttrUtil.isNthFormula(this.model.aggregate) ||
         SummaryAttrUtil.isPthFormula(this.model.aggregate);
   }

   get nStr(): string {
      return this._nStr;
   }

   set nStr(value: string) {
      this._nStr = value;
      const val = parseInt(value, 10);
      this.model.numValue = val < 1 || isNaN(val) ? "1" : val + "";
   }

   getNPLabel(): string {
      return AggregateFormula.getNPLabel(this.model.aggregate);
   }

   isNValid(): boolean {
      return parseInt(this.nStr, 10) > 0;
   }

   getInputClass(val: any): string {
      if(this.model.grayedOutValues.indexOf(val) >= 0) {
         return "grayed-out-field";
      }

      return "";
   }
}
