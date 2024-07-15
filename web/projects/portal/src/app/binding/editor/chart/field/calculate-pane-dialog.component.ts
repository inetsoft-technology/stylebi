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
import {Component, EventEmitter, Input, OnInit, Output} from "@angular/core";
import {
  AggregateCalcInfo,
  CalcConstant,
  CalculateInfo,
  ChangeCalcInfo,
  CompoundGrowthCalcInfo,
  MovingCalcInfo,
  PercentCalcInfo,
  RunningTotalCalcInfo,
  RunningTotalCalcRestLevel,
  ValueOfCalcInfo
} from "../../../data/chart/calculate-info";
import {DimensionInfo} from "../../../data/dimension-info";
import {AggregateFormula} from "../../../util/aggregate-formula";
import {DateRangeRef} from "../../../../common/util/date-range-ref";
import {SummaryAttrUtil} from "../../../util/summary-attr-util";
import {StyleConstants} from "../../../../common/util/style-constants";
import {Tool} from "../../../../../../../shared/util/tool";

@Component({
   selector: "calculate-pane-dialog",
   templateUrl: "calculate-pane-dialog.component.html",
   styleUrls: ["calculate-pane-dialog.component.scss"]
})
export class CalculatePaneDialog implements OnInit {
   @Input() aggreName: string;
   @Input() supportResetMap: Map<string, boolean>;
   @Input() resetOptsMap: Map<string, Array<any>>;
   @Input() valueOfDatas: Array<DimensionInfo>;
   @Input() breakByDims: Array<DimensionInfo>;
   @Input() percOfLevels: Array<DimensionInfo>;
   @Input() percOfDims: Array<DimensionInfo>;
   @Input() movingDims: Array<DimensionInfo>;
   @Input() isVs: boolean = false;
   @Input() runtimeId: string;
   @Input() variables: any;
   @Input() crosstab: boolean = false;
   @Input() hasRow: boolean;
   @Input() hasCol: boolean;
   @Input() cube: boolean = false;
   @Input() assemblyName: string;
   @Input() percentageDirection: string;
   @Input() supportPercentageDirection: boolean = false;

   _calculator: CalculateInfo;
   n: number = 1; // n or p

   @Input() set calculator(_calculator: CalculateInfo) {
      this._calculator = _calculator;

      if(this.hasAggregate) {
         let aggr: string = this._calculator["aggregate"];
         let left = aggr.indexOf("(");
         let right = aggr.lastIndexOf(")");

         if(left < 0 || !Tool.isNumber(+aggr.substring(left + 1, right))) {
            return;
         }

         this.n = +aggr.substring(left + 1, right);
         this._calculator["aggregate"] = aggr.substring(0, left) + aggr.substring(right + 1);
      }
   }

   get calculator(): CalculateInfo {
      return this._calculator;
   }

   get changeCalculator(): ChangeCalcInfo {
      return this._calculator as ChangeCalcInfo;
   }

   get changeColumn(): string {
      if(!this.changeCalculator) {
         return null;
      }

      let column = this.changeCalculator.columnName;
      return column == null ? this.valueOfDatas && this.valueOfDatas.length > 0 ?
         this.valueOfDatas[0].data : "" :  column;
   }

   set changeColumn(column: string) {
      if(!this.changeCalculator) {
         return;
      }

      this.changeCalculator.columnName = column;
   }

   get valueOfCalculator(): ValueOfCalcInfo {
      return this._calculator as ValueOfCalcInfo;
   }

   get valueOfColumn(): string {
      if(!this.valueOfCalculator) {
         return null;
      }

      let column = this.valueOfCalculator.columnName;
      return column == null ? this.valueOfDatas && this.valueOfDatas.length > 0 ?
         this.valueOfDatas[0].data : "" :  column;
   }

   set valueOfColumn(column: string) {
      if(!this.valueOfCalculator) {
         return;
      }

      this.valueOfCalculator.columnName = column;
   }

   get aggregateCalculator(): AggregateCalcInfo {
      return (this._calculator as unknown) as AggregateCalcInfo;
   }

   get runningTotalCalculator(): RunningTotalCalcInfo {
      return this._calculator as RunningTotalCalcInfo;
   }

   get movingCalculator(): MovingCalcInfo {
      return this._calculator as MovingCalcInfo;
   }

   get percDims(): Array<DimensionInfo> {
      let dims = [];

      if(this.percOfLevels) {
         dims = dims.concat(this.percOfLevels);
      }

      if(!this.crosstab && this.percOfDims) {
         dims = dims.concat(this.percOfDims);
      }

      return dims;
   }

   get supportReset(): boolean {
      if((this.calculator.classType != "RUNNINGTOTAL" &&
         this.calculator.classType != "COMPOUNDGROWTH") || !this.supportResetMap)
      {
         return false;
      }

      if(!this.runningTotalCalculator.breakBy && this.breakByDims.length > 0) {
         this.runningTotalCalculator.breakBy = this.breakByDims[0].data;
      }

      return this.supportResetMap[this.runningTotalCalculator.breakBy];
   }

   get resetOptsData(): Array<any> {
      if(!this.supportReset || !this.resetOptsMap) {
         return null;
      }

      return this.resetOptsMap.get(this.runningTotalCalculator.breakBy);
   }

   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   calcDatas: Array<any>;
   fromDatas1: Array<any>;
   fromDatasQuarter: Array<any>;
   fromDatasMonth: Array<any>;
   fromDatasDay: Array<any>;
   percents = [
      { label: "_#(js:Columns)", value: StyleConstants.PERCENTAGE_BY_COL + "" },
      { label: "_#(js:Rows)", value: StyleConstants.PERCENTAGE_BY_ROW + "" }
   ];
   aggregateDatas: Array<AggregateFormula>;

   constructor() {
   }

   ngOnInit(): void {
      if(this.cube) {
         this.calcDatas = [{label: "_#(js:Percent)", data: "PERCENT"}];
      }
      else {
         this.calcDatas = [
            {label: "_#(js:Percent)", data: "PERCENT"},
            {label: "_#(js:Change)", data: "CHANGE"},
            {label: "_#(js:Running)", data: "RUNNINGTOTAL"},
            {label: "_#(js:Sliding)", data: "MOVING"},
            {label: "_#(js:Value of)", data: "VALUE"},
            {label: "_#(js:Compound Growth)", data: "COMPOUNDGROWTH"}
         ];
      }

      this.fromDatas1 = [
         {label: "_#(js:First)", data: 0},
         {label: "_#(js:Previous)", data: 1},
         {label: "_#(js:Next)", data: 2},
         {label: "_#(js:Last)", data: 3}
      ];

      this.fromDatasQuarter = this.fromDatas1.concat([
         {label: "_#(js:Previous Year)", data: 4}
      ]);

      this.fromDatasMonth = this.fromDatasQuarter.concat([
         {label: "_#(js:Previous Quarter)", data: 5}
      ]);

      this.fromDatasDay = this.fromDatasQuarter.concat([
         {label: "_#(js:Previous Week)", data: 6}
      ]);

      this.aggregateDatas = [
         AggregateFormula.SUM, AggregateFormula.AVG,
         AggregateFormula.MAX, AggregateFormula.MIN,
         AggregateFormula.CONCAT,
         AggregateFormula.COUNT_ALL, AggregateFormula.COUNT_DISTINCT,
         AggregateFormula.MEDIAN, AggregateFormula.MODE,
         AggregateFormula.NTH_LARGEST,
         AggregateFormula.NTH_MOST_FREQUENT,
         AggregateFormula.NTH_SMALLEST,
         AggregateFormula.POPULATION_STANDARD_DEVIATION,
         AggregateFormula.PRODUCT,
         AggregateFormula.PTH_PERCENTILE,
         AggregateFormula.STANDARD_DEVIATION, AggregateFormula.VARIANCE,
         AggregateFormula.POPULATION_VARIANCE
      ];

      this.calculator = this.getBaseCalc(this.calcDatas, this.calculator);

      if(this.calculator.classType === "PERCENT") {
         let calc = <PercentCalcInfo> this.calculator;

         if(calc.level <= 0) {
            calc.level = 1;
         }
      }
   }

   /**
    * display aggregate option. if return true
    */
   get hasAggregate(): boolean {
      return this.calculator && (this.calculator.classType === "MOVING" ||
                                 this.calculator.classType === "RUNNINGTOTAL");
   }

   /**
    * visible of n or p
    */
   get npVisible(): boolean {
      return this.hasAggregate && SummaryAttrUtil.npVisible(this.calculator["aggregate"]);
   }

   /**
    * get n/p label
    * @return P: if formula is pth. otherwise, return N
    */
   get npLabel(): string {
      return SummaryAttrUtil.isPthFormula(this.calculator["aggregate"]) ? "_#(js:P)" : "_#(js:N)";
   }

   get fromDatas(): any[] {
      const col: string = (<ChangeCalcInfo> this.calculator).columnName;

      if(!this.valueOfDatas) {
         return this.fromDatas1;
      }

      if(this.valueOfDatas.some(v => v.data == col && v.dateTime &&
                                 v.dateLevel == DateRangeRef.QUARTER_INTERVAL))
      {
         return this.fromDatasQuarter;
      }

      if(this.valueOfDatas.some(v => v.data == col && v.dateTime &&
                                 v.dateLevel == DateRangeRef.MONTH_INTERVAL))
      {
         return this.fromDatasMonth;
      }

      if(this.valueOfDatas.some(v => v.data == col && v.dateTime &&
                                 v.dateLevel == DateRangeRef.DAY_INTERVAL))
      {
         return this.fromDatasDay;
      }

      return this.fromDatas1;
   }

   getDefaultPercentageLevel(): number {
      if(!this.percOfLevels || this.percOfLevels.length == 0) {
         return -1;
      }

      let level = parseInt(this.percOfLevels[0].data, 10);
      return isNaN(level) ? -1 : level;
   }

   getBaseCalc(calcs: Array<any>, calc: CalculateInfo): CalculateInfo {
      let dcalc: PercentCalcInfo = new PercentCalcInfo();
      dcalc.classType = "PERCENT";
      dcalc.level = this.getDefaultPercentageLevel();
      dcalc.view = "Percent of";

      if(calc == null) {
         return dcalc;
      }

      for(let i = 0; i < calcs.length; i++) {
         let bcalc = calcs[i];

         if(bcalc && bcalc.data == calc.classType) {
            return calc;
         }
      }

      return dcalc;
   }

   get percOfValue(): any {
      let column;

      if(this.calculator.classType === "PERCENT") {
         let calc = <PercentCalcInfo> this.calculator;
         column = calc.columnName ? calc.columnName : calc.level;
      }

      if((column == null || column < 0) && this.percDims.length) {
         column = this.percDims[0].data;
      }

      return column;
   }

   set percOfValue(val: any) {
      if(this.calculator.classType === "PERCENT") {
         let calc = <PercentCalcInfo> this.calculator;
         let levels = this.percOfLevels;
         let idx: number = -1;

         for(let i = 0; i < levels.length; i++) {
            if(levels[i].data == val) {
               idx = i;
               break;
            }
         }

         if(idx != -1) {
            calc.level = val;
            calc.columnName = null;
         }
         else {
            calc.level = this.getDefaultPercentageLevel();
            calc.columnName = val;
         }
      }
   }

   get breakBy(): any {
      let column = this.runningTotalCalculator.breakBy;

      if(!column && this.breakByDims && this.breakByDims.length) {
         column = this.breakByDims[0].data;
      }

      return column;
   }

   set breakBy(val: any) {
      this.runningTotalCalculator.breakBy = val;
   }

   calcTypeChange(classType: string) {
      if(classType === "PERCENT") {
         let calc = new PercentCalcInfo();
         calc.level = this.getDefaultPercentageLevel();
         calc.view = "Percent of";
         this.calculator = calc;
      }
      else if(classType === "CHANGE") {
         let calc = new ChangeCalcInfo();
         calc.columnName = this.valueOfDatas.length > 0 ? this.valueOfDatas[0].data : null;
         calc.from = 0;
         calc.asPercent = false;
         calc.view = "Change from first";
         this.calculator = calc;
      }
      else if(classType === "RUNNINGTOTAL") {
         let calc = new RunningTotalCalcInfo();
         calc.aggregate = "Sum";
         calc.view = "Running Sum";
         calc.resetLevel = -1;
         this.calculator = calc;
      }
      else if(classType === "MOVING") {
         let calc = new MovingCalcInfo();
         calc.aggregate = "Average";
         calc.previous = 2;
         calc.next = 2;
         calc.includeCurrentValue = true;
         calc.nullIfNoEnoughValue = false;
         calc.view = "Moving Sum of" + 3;

         if(this.crosstab) {
            calc.innerDim = this.hasRow ? CalcConstant.ROW_INNER : CalcConstant.COLUMN_INNER;
         }

         this.calculator = calc;
      }
      else if(classType === "VALUE") {
         let calc = new ValueOfCalcInfo();
         calc.columnName = this.valueOfDatas.length > 0 ? this.valueOfDatas[0].data : null;
         calc.from = 0;
         calc.view = "Value of first";
         this.calculator = calc;
      }
      else if(classType === "COMPOUNDGROWTH") {
         let calc = new CompoundGrowthCalcInfo();
         calc.view = "Compound Growth";
         calc.resetLevel = -1;
         this.calculator = calc;
      }

      this.calculator.classType = classType;
   }

   isNValid(): boolean {
      return this.n > 0;
   }

   getPercentOfLabel(): string {
      return this.percentageDirection ==  this.percents[1].value ?
         this.percents[1].label : this.percents[0].label;
   }

   okDisabled(): boolean {
      // if(this.calculator instanceof MovingCalcInfo) {
      //    let movingCalculator = <MovingCalcInfo> this.calculator;
      //    return (movingCalculator.previous < 1) || (movingCalculator.next < 1);
      // }

      return false;
   }

   cancel(evt: MouseEvent): void {
      evt.stopPropagation();
      this.onCancel.emit("cancel");
   }

   ok(evt: MouseEvent): void {
      if((<ChangeCalcInfo> this.calculator).columnName == "null") {
         (<ChangeCalcInfo> this.calculator).columnName = null;
      }

      if(this.npVisible) {
         this.n = this.n == null ? 1 : this.n;
         this.calculator["aggregate"] += ("(" + this.n + ")");
      }

      // reset the rest level when calculator do not support it.
      if(this.calculator.classType === "RUNNINGTOTAL" &&
         this.runningTotalCalculator.resetLevel != RunningTotalCalcRestLevel.NONE &&
         (!this.resetOptsData || this.resetOptsData.length == 0))
      {
         this.runningTotalCalculator.resetLevel = RunningTotalCalcRestLevel.NONE;
      }

      evt.stopPropagation();

      this.onCommit.emit({
         calc: this.calculator,
         percentageDirection: this.percentageDirection
      });
   }
}
