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
import { Component, Input, OnChanges, SimpleChanges, OnInit,
         Output, EventEmitter } from "@angular/core";
import { VSCalcTableEditorService } from "../../services/table/vs-calc-table-editor.service";
import { BindingService } from "../../services/binding.service";
import { DataRef } from "../../../common/data/data-ref";
import { AggregateFormula } from "../../util/aggregate-formula";
import { AssetUtil } from "../../util/asset-util";
import { StyleConstants } from "../../../common/util/style-constants";
import { SummaryAttrUtil } from "../../util/summary-attr-util";
import { XSchema } from "../../../common/data/xschema";

const DEFAULT_N_P_NVALUE: number = 1;

@Component({
   selector: "calc-aggregate-option",
   templateUrl: "calc-aggregate-option.component.html",
   styleUrls: ["calc-aggregate-option.component.scss"]
})
export class CalcAggregateOption implements OnInit, OnChanges {
   @Input() dataRef: DataRef;
   @Output() apply: EventEmitter<any> = new EventEmitter<any>();
   nullPercent: any[];
   rowPercent: any[];
   colPercent: any[];
   rowColPercent: any[];
   aggregate: any = {};
   formulas: AggregateFormula[] = new Array<AggregateFormula>();
   _nStr: string = "";

   public constructor(private editorService: VSCalcTableEditorService,
      private bindingService: BindingService) {
      this.initOptions();
   }

   ngOnInit() {
      this._nStr = this.nValue != null ? this.nValue + "" : "";
   }

   ngOnChanges(changes: SimpleChanges) {
      this.formulas = AssetUtil.getAggregateModel(this.dataRef);

      if(this.dataRef?.dataType == XSchema.BOOLEAN) {
         this.formulas = this.formulas.concat(AggregateFormula.NTH_MOST_FREQUENT);
      }
   }

   get availableFields() {
      return this.bindingService.getBindingModel() ?
         this.bindingService.getBindingModel().availableFields : null;
   }

   isGrayedOut(name: string): boolean {
      return this.bindingService.isGrayedOutField(name);
   }

   get formula(): string {
      if(this.editorService.getCellBinding() == null) {
         return "";
      }

      return this.editorService.getCellBinding().formula;
   }

   get rowGroup(): boolean {
      return this.editorService.hasRowGroup();
   }

   get colGroup(): boolean {
      return this.editorService.hasColGroup();
   }

   get groupNum(): number {
      return this.editorService.getGroupNum();
   }

   get baseFormula(): string {
      if(this.formula == null) {
         return "none";
      }

      return this.getBaseFormula();
   }

   set baseFormula(val: string) {
      this.aggregate.formula = val;

      if(this.isPercent()) {
         this.aggregate.percentage = StyleConstants.PERCENTAGE_NONE;
      }

      if(this.getAggregateFormula(val).hasN) {
         this.nStr = this.nStr ? this.nStr : "" + DEFAULT_N_P_NVALUE;
      }

      this.updateFormula();
   }

   get secondCol(): string {
      if(this.formula == null || !this.isTwoColumns()) {
         return null;
      }

      let columnName: string = this.getSecondColumn();

      if(columnName != "undefined" && columnName != null) {
         return columnName;
      }

      if(this.availableFields != null && this.availableFields.length > 0) {
         columnName = this.availableFields[0].name;
         this.secondCol = columnName;
      }

      return columnName;
   }

   set secondCol(val: string) {
      this.aggregate.secondCol = val;
      this.updateFormula();
   }

   get percentage(): number {
      if(this.formula == null || !this.isPercent()) {
         return 0;
      }

      return this.getPercentValue();
   }

   set percentage(val: number) {
      this.aggregate.percentage = val + "";
      this.updateFormula();
   }

   initOptions() {
      this.nullPercent = [
         { value: StyleConstants.PERCENTAGE_NONE, label: "_#(js:None)" }
      ];
      this.rowPercent = [
         { value: StyleConstants.PERCENTAGE_NONE, label: "_#(js:None)" },
         { value: StyleConstants.PERCENTAGE_OF_ROW_GROUP, label: "_#(js:Row Group)" },
         { value: StyleConstants.PERCENTAGE_OF_ROW_GRANDTOTAL, label: "_#(js:Row GrandTotal)" }
      ];
      this.colPercent = [
         { value: StyleConstants.PERCENTAGE_NONE, label: "_#(js:None)" },
         { value: StyleConstants.PERCENTAGE_OF_COL_GROUP, label: "_#(js:Col Group)" },
         { value: StyleConstants.PERCENTAGE_OF_COL_GRANDTOTAL, label: "_#(js:Col GrandTotal)" }
      ];
      this.rowColPercent = [
         { value: StyleConstants.PERCENTAGE_NONE, label: "_#(js:None)" },
         { value: StyleConstants.PERCENTAGE_OF_ROW_GROUP, label: "_#(js:Row Group)" },
         { value: StyleConstants.PERCENTAGE_OF_ROW_GRANDTOTAL, label: "_#(js:Row GrandTotal)" },
         { value: StyleConstants.PERCENTAGE_OF_COL_GROUP, label: "_#(js:Col Group)" },
         { value: StyleConstants.PERCENTAGE_OF_COL_GRANDTOTAL, label: "_#(js:Col GrandTotal)" }
      ];
   }

   getPercents() {
      let hasRow = this.rowGroup;
      let hasCol = this.colGroup;

      if(hasRow && hasCol) {
         return this.rowColPercent;
      }
      else if(hasRow) {
         return this.rowPercent;
      }
      else if(hasCol) {
         return this.colPercent;
      }
      else {
         return this.nullPercent;
      }
   }

   getAggregateFormula(formula0?: string): AggregateFormula {
      let formula = formula0 ? formula0 : this.formula;
      let allFormulas = this.formulas;

      if(formula == null || formula == "" || allFormulas == null) {
         return AggregateFormula.NONE;
      }

      for(let i = 0; i < allFormulas.length; i++) {
         if(formula.indexOf(allFormulas[i].formulaName) == 0) {
            return allFormulas[i];
         }
      }

      return AggregateFormula.NONE;
   }

   getBaseFormula() {
      return this.getAggregateFormula().formulaName;
   }

   get hasN(): boolean {
      return this.getAggregateFormula(this.aggregate.formula).hasN;
   }

   get nStr(): string {
      return this._nStr;
   }

   set nStr(str: string) {
      this._nStr = str;
      const val = parseInt(str + "", 10);
      this.aggregate.nValue = val < 1 || isNaN(val) ? DEFAULT_N_P_NVALUE : val;
      this.updateFormula();
   }

   get nValue(): number {
      const val = parseInt(this.getSecondColumn(), 10);
      return val == 0 || isNaN(val) ? null : val;
   }

   isTwoColumns() {
      let fls = ["First", "Last", "Correlation", "Covariance", "WeightedAverage"];
      let formula = this.aggregate.formula ? this.aggregate.formula :
         this.getBaseFormula();

      for(let i = 0; i < fls.length; i++) {
         if(fls[i] == formula) {
            return true;
         }
      }

      return false;
   }

   getSecondColumn() {
      let formula = this.formula;
      let idx = formula.indexOf("(");
      let idx0 = formula.lastIndexOf(")");

      if(idx < 0) {
         return null;
      }

      return formula.substring(idx + 1, idx0);
   }

   isPercent() {
      if(!this.rowGroup && !this.colGroup) {
         return false;
      }

      let fls = ["Sum", "Average", "DistinctCount", "Count", "Max", "Min", "Median",
         "StandardDeviation", "Variance", "Mode"];
      let formula = this.baseFormula;

      for(let i = 0; i < fls.length; i++) {
         if(fls[i] == formula) {
            return this.groupNum > 0;
         }
      }

      return false;
   }

   getPercentValue() {
      let formula = this.formula;
      let idx = formula.indexOf("<");
      let idx0 = formula.indexOf(">");

      if(idx < 0) {
         return 0;
      }

      const v = parseInt(formula.substring(idx + 1, idx0), 10);
      return !v ? 0 : v;
   }

   updateFormula() {
      let formula = this.aggregate.formula ? this.aggregate.formula : this.getBaseFormula();

      if(this.isPercent()) {
         formula += "<" + this.aggregate.percentage + ">";
      }

      if(this.hasN) {
         formula += "(" + this.aggregate.nValue + ")";
      }

      if(this.isTwoColumns()) {
         formula += "(" + this.aggregate.secondCol + ")";
      }

      if(this.editorService.getCellBinding() == null) {
         return;
      }

      this.editorService.getCellBinding().formula = formula;

      return formula;
   }

   getNPLabel(): string {
      return AggregateFormula.getNPLabel(this.baseFormula);
   }

   isPthFormula(): boolean {
      return SummaryAttrUtil.isPthFormula(this.baseFormula);
   }

   isNthFormula(): boolean {
      return SummaryAttrUtil.isNthFormula(this.baseFormula);
   }

   isNValid(): boolean {
      return parseInt(this.nStr, 10) > 0;
   }

   submit() {
      this.apply.emit(false);
   }
}
