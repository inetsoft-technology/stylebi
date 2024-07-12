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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable } from "rxjs";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { Tool } from "../../../../../../../shared/util/tool";
import { ModelService } from "../../../../widget/services/model.service";
import {
   CalcConstant,
   CalculateInfo,
   ChangeCalcInfo,
   CompoundGrowthCalcInfo,
   MovingCalcInfo,
   PercentCalcInfo,
   RunningTotalCalcInfo,
   ValueOfCalcInfo
} from "../../../data/chart/calculate-info";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";
import { BindingService } from "../../../services/binding.service";
import { AggregateFormula } from "../../../util/aggregate-formula";
import { AssetUtil } from "../../../util/asset-util";
import { CalculatePaneDialog } from "./calculate-pane-dialog.component";
import { DimensionInfo } from "../../../data/dimension-info";
import { ComponentTool } from "../../../../common/util/component-tool";
import { CalculatorConstants } from "./calculator-constants";
import { StyleConstants } from "../../../../common/util/style-constants";
import { CrosstabBindingModel } from "../../../data/table/crosstab-binding-model";

@Component({
   selector: "calculate-pane",
   templateUrl: "calculate-pane.component.html",
   styleUrls: ["./calculate-pane.component.scss"]
})
export class CalculatePane {
   @Input() enabled: boolean = true;
   @Input() variables: any;
   @Input() percentageDirection: string;
   @Input() cube: boolean = false;
   @Input() supportPercentageDirection: boolean = false;
   @Output() onCalcChanged: EventEmitter<any> = new EventEmitter<any>();
   formulas: AggregateFormula[] = AssetUtil.getDefaultFormulas();
   baseCalcs: Array<any>;
   defaultCalcs: Array<any>;
   customCalc: CalculateInfo;
   agg: ChartAggregateRef;

   @Input() set aggregate(aggRef: ChartAggregateRef) {
      this.agg = aggRef;
      this.initBuildInCalcs();
   }

   get aggregate(): ChartAggregateRef {
      return this.agg;
   }

   constructor(private dialogService: NgbModal,
               private modelService: ModelService,
               private bindingService: BindingService,
               private uiContextService: UIContextService)
   {
   }

   get DIMS_URL(): string {
      return "../api/composer/dims";
   }

   get SUPPORT_RESET_URL(): string {
      return "../api/composer/supportReset";
   }

   get RESET_OPTIONS_URL(): string {
      return "../api/composer/resetOptions";
   }

   get isCrosstab(): boolean {
      return this.bindingService.objectType == "VSCrosstab" ||
         this.bindingService.objectType == "crosstab";
   }

   initBuildInCalcs() {
      let calcs = this.aggregate && this.aggregate.buildInCalcs ?
         this.aggregate.buildInCalcs : [];
      this.baseCalcs = [];

      for(let i = 0; i < calcs.length; i++) {
         let calc = Tool.clone(calcs[i]);
         this.baseCalcs.push(
            { label: (calcs[i] ? calc.name : "_#(js:None)"), data: calc });
      }

      this.calcChanged();
   }

   get calculateValue(): CalculateInfo {
      return this.customCalc ? this.customCalc
         : this.aggregate.calculateInfo;
   }

   set calculateValue(calc: CalculateInfo) {
      let isNull: boolean = calc == null;

      if(!isNull && typeof calc === "string") {
         let reg = /: null$/;
         isNull = reg.test(<any> calc);

         if(isNull) {
            calc = null;
         }
      }

      this.customCalc = !isNull && calc.classType === "CUSTOM"
         ? calc : null;
      this.aggregate.calculateInfo = this.customCalc ? null : calc;

      if(!this.customCalc) {
         this.onCalcChanged.emit({
            aggr: this.aggregate,
            percentageDirection: this.percentageDirection
         });
      }
   }

   openCalculateDialog() {
      let calculateDialog = ComponentTool.showDialog(this.dialogService, CalculatePaneDialog,
         (result: any) => {
            if(!!result) {
               this.aggregate.calculateInfo = result.calc;
               this.percentageDirection = result.percentageDirection;
               this.customCalc = null;
               this.calcChanged();
               this.onCalcChanged.emit({
                  aggr: this.aggregate,
                  percentageDirection: this.percentageDirection
               });
            }
         });

      calculateDialog.calculator = Tool.clone(this.aggregate.calculateInfo);
      calculateDialog.aggreName = this.getAggregateName();
      calculateDialog.crosstab = this.isCrosstab;
      calculateDialog.hasRow = this.hasRow();
      calculateDialog.hasCol = this.hasCol();
      calculateDialog.isVs = this.uiContextService.isVS();
      calculateDialog.runtimeId = this.bindingService.runtimeId;
      calculateDialog.variables = this.variables;
      calculateDialog.assemblyName = this.bindingService.assemblyName;
      calculateDialog.percentageDirection = this.percentageDirection;
      calculateDialog.supportPercentageDirection = this.supportPercentageDirection;
      calculateDialog.cube = this.cube;

      this.getDimensions().subscribe((data: Map<String, Array<DimensionInfo>>) => {
         if(!data) {
            return;
         }

         calculateDialog.percOfDims = data[CalculatorConstants.PERCENT_DIMS_TAG];
         calculateDialog.percOfLevels = data[CalculatorConstants.PERCENT_LEVEL_TAG];
         calculateDialog.valueOfDatas = data[CalculatorConstants.VALUE_OF_TAG];
         calculateDialog.breakByDims = data[CalculatorConstants.BREAK_BY_TAG];
         calculateDialog.movingDims = data[CalculatorConstants.MOVING_TAG];
      });

      this.isSupportReset().subscribe((data: any) => {
         calculateDialog.supportResetMap = data;
      });

      this.getResetOptions().subscribe((data: Map<string, Array<any>>) => {
         let map = new Map<string, Array<any>>();
         let arr = [
            CalculatorConstants.INNER_DIMENSION,
            CalculatorConstants.ROW_INNER_DIMENSION,
            CalculatorConstants.COLUMN_INNER_DIMENSION
         ];

         for(let key of arr) {
            let vars: Array<any> = data[key];
            let resetOptsData: Array<any> = new Array<any>();

            for(let i = 0; !!vars && i < vars.length; i++) {
               let opts: Array<any> = vars[i];
               resetOptsData.push({label: opts[0], data: parseInt(opts[1], 10)});
            }

            map.set(key, resetOptsData);
         }

         calculateDialog.resetOptsMap = map;
      });
   }

   private getDimensions(): Observable<any> {
      let params = this.bindingService.getURLParams();
      return this.modelService.getModel(this.DIMS_URL, params);
   }

   private isSupportReset(): Observable<any> {
      let params = this.bindingService.getURLParams();
      params = params.set("aggreName", this.getAggregateName());

      return this.modelService.getModel(this.SUPPORT_RESET_URL, params);
   }

   private getResetOptions(): Observable<any> {
      let params = this.bindingService.getURLParams();
      params = params.set("aggreName", this.getAggregateName());

      return this.modelService.getModel(this.RESET_OPTIONS_URL, params);
   }

   private calcChanged(): void {
      this.defaultCalcs = Tool.clone(this.baseCalcs);
      let dcalc: CalculateInfo = this.aggregate.calculateInfo;

      if(!dcalc || !this.defaultCalcs) {
         return;
      }

      let index = 0;

      for(let i = 0; i < this.defaultCalcs.length; i++) {
         if(this.isCalcEquals(dcalc, this.defaultCalcs[i].data)) {
            index = i;
            break;
         }
      }

      if(index == 0) {
         let label = this.toView(dcalc);
         let idx = label.indexOf("(");

         if(idx != -1) {
            let label0 = label.substring(0, idx);
            let label1 = label.substring(idx + 1, label.length - 1);
            let idx0 = label1.indexOf(",");

            if(idx0 != -1) {
               label1 = label1.substring(0, idx0) + "," + label1.substring(idx0 + 1);
               label = label0 + "(" + label1 + ")";
            }
         }

         this.defaultCalcs[this.defaultCalcs.length - 1].label = label;
         this.defaultCalcs[this.defaultCalcs.length - 1].data = dcalc;
         index = this.defaultCalcs.length - 1;
      }

      this.aggregate.calculateInfo = this.defaultCalcs[index].data;
   }

   private isCalcEquals(calc0: CalculateInfo, calc1: CalculateInfo): boolean {
      if(calc0 != null && calc1 == null || calc0 == null && calc1 != null) {
         return false;
      }

      if(calc0 == null && calc1 == null) {
         return true;
      }

      let calcInfo: CalculateInfo = null;

      if(calc0.classType == "PERCENT") {
         calcInfo = new PercentCalcInfo();
      }
      else if(calc0.classType == "CHANGE") {
         calcInfo = new ChangeCalcInfo();
      }
      else if(calc0.classType == "MOVING") {
         calcInfo = new MovingCalcInfo();
      }
      else if(calc0.classType == "RUNNINGTOTAL") {
         calcInfo = new RunningTotalCalcInfo();
      }
      else if(calc0.classType == "VALUE") {
         calcInfo = new ValueOfCalcInfo();
      }
      else if(calc0.classType == "COMPOUNDGROWTH") {
         calcInfo = new CompoundGrowthCalcInfo();
      }

      if(calcInfo != null) {
         Object.assign(calcInfo, calc0);
         return calcInfo.equals(calc1);
      }

      return Tool.isEquals(calc0, calc1);
   }

   private getColumnView(column: string): string {
      if(!column || CalcConstant.COLUMN_INNER == column || CalcConstant.ROW_INNER == column) {
         return null;
      }

      return column;
   }

   private toView(calc: CalculateInfo): string {
      if(calc.classType == "PERCENT") {
         let calc0 = <PercentCalcInfo>calc;
         let prefix = "_#(js:Percent of) ";
         let columm = this.getColumnView(calc0.columnName);

         if(!!columm) {
            return prefix + calc0.columnName;
         }

         return prefix +
            (calc0.level == StyleConstants.SUB_TOTAL ? "_#(js:Subtotal)" : "_#(js:Grand Total)");
      }
      else if(calc.classType == "CHANGE") {
         let calc0 = <ChangeCalcInfo>calc;
         let str: string;
         let fromVal: number = parseInt(calc0.from + "", 10);

         switch(fromVal) {
         case 0:
            str = "_#(js:Change from first)";
            break;
         case 1:
            str = "_#(js:Change from previous)";
            break;
         case 2:
            str = "_#(js:Change from next)";
            break;
         case 3:
            str = "_#(js:Change from last)";
            break;
         case 4:
            str = "_#(js:Change from previous year of)";
            break;
         case 5:
            str = "_#(js:Change from previous quarter of)";
            break;
         default:
            break;
         }

         if(calc0.asPercent) {
            str = "% " + str;
         }

         let columm = this.getColumnView(calc0.columnName);

         if(!!columm) {
            str += calc0.columnName != "null" ? " " + calc0.columnName : "";
         }

         return str;
      }
      else if(calc.classType == "VALUE") {
         let calc0 = <ValueOfCalcInfo>calc;
         let str: string;
         let fromVal: number = parseInt(calc0.from + "", 10);

         switch(fromVal) {
            case 0:
               str = "_#(js:Value of first)";
               break;
            case 1:
               str = "_#(js:Value of previous)";
               break;
            case 2:
               str = "_#(js:Value of next)";
               break;
            case 3:
               str = "_#(js:Value of last)";
               break;
            case 4:
               str = "_#(js:Value of previous year of)";
               break;
            case 5:
               str = "_#(js:Value of previous quarter of)";
               break;
            case 6:
               str = "_#(js:Value of previous week of)";
               break;
            default:
               break;
         }

         let columm = this.getColumnView(calc0.columnName);

         if(!!columm) {
            str += calc0.columnName != "null" ? " " + calc0.columnName : "";
         }

         return str;
      }
      else if(calc.classType == "MOVING") {
         let calc0 = <MovingCalcInfo> calc;
         return "_#(js:Moving) " + this.getCalcsLable(calc0) + " _#(js:calculationOf) " +
            (calc0.previous + calc0.next + (calc0.includeCurrentValue ? 1 : 0));
      }
      else if(calc.classType == "RUNNINGTOTAL" || calc.classType == "COMPOUNDGROWTH") {
         let calc0 = <RunningTotalCalcInfo>calc;
         let str: string = calc.classType == "COMPOUNDGROWTH" ? "_#(js:Compound Growth)" :
            "_#(js:Running) " + this.getCalcsLable(calc0);
         let level: number = parseInt(calc0.resetLevel + "", 10);

         switch(level) {
            case 0:
               str += " _#(js:of year)";
               break;
            case 1:
               str += " _#(js:of quarter)";
               break;
            case 2:
               str += " _#(js:of month)";
               break;
            case 3:
               str += " _#(js:of week)";
               break;
            case 4:
               str += " _#(js:of day)";
               break;
            case 5:
               str += " _#(js:of hour)";
               break;
            case 6:
               str += " _#(js:of minute)";
               break;
            case -1:
               break;
            default:
               break;
         }

         return str;
      }

      return "";
   }

   private getCalcsLable(calculator: MovingCalcInfo | RunningTotalCalcInfo): string {
      let formulas = this.formulas;
      let oagg: string = calculator.aggregate;
      let agg: string = oagg;

      let left = agg.indexOf("(");
      let right = agg.indexOf(")");

      if(left > -1 && Tool.isNumber(+agg.substring(left + 1, right))) {
         agg = agg.substring(0, left);
      }

      for(let i = 0; i < formulas.length; i++) {
         if(agg == formulas[i].formulaName) {
            return oagg.replace(agg, formulas[i].label);
         }
      }

      return "";
   }

   getAggregateName(): string {
      return this.aggregate ? this.aggregate.name : null;
   }

   hasRow(): boolean {
      return this.isCrosstab &&
         (<CrosstabBindingModel> this.bindingService.bindingModel).rows.length > 0;
   }

   hasCol(): boolean {
      return this.isCrosstab &&
         (<CrosstabBindingModel> this.bindingService.bindingModel).cols.length > 0;
   }
}
