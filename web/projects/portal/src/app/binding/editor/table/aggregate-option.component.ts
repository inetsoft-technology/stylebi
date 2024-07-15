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
import { Component, Input, OnInit, Output, EventEmitter } from "@angular/core";
import { DataRef } from "../../../common/data/data-ref";
import { StyleConstants } from "../../../common/util/style-constants";
import { UIContextService } from "../../../common/services/ui-context.service";
import { BAggregateRef } from "../../data/b-aggregate-ref";
import { AggregateFormula } from "../../util/aggregate-formula";
import { AssetUtil } from "../../util/asset-util";
import { SummaryAttrUtil } from "../../util/summary-attr-util";
import { BindingService } from "../../services/binding.service";
import { Tool } from "../../../../../../shared/util/tool";
import { DataRefType } from "../../../common/data/data-ref-type";
import { CrosstabOptionInfo } from "../../data/table/crosstab-option-info";
import { CrosstabBindingModel } from "../../data/table/crosstab-binding-model";
import { PercentCalcInfo } from "../../data/chart/calculate-info";
import {ChartAggregateRef} from "../../data/chart/chart-aggregate-ref";

@Component({
   selector: "aggregate-option",
   templateUrl: "aggregate-option.component.html",
   styleUrls: ["aggregate-option.component.scss"]
})
export class AggregateOption implements OnInit {
   @Input() groupNum: number;
   @Input() vsId: any;
   @Input() variables: any;
   @Input() percentSupport: boolean = true;
   @Input() grayedOutValues: string[] = [];
   @Input() availableFields: DataRef[];
   aggregateStatus: boolean;
   formulas: AggregateFormula[];
   formulaObjs: any[];
   percents: any[];
   private _aggregate: BAggregateRef;
   @Output() apply: EventEmitter<any> = new EventEmitter<any>();

   @Input()
   set aggregate(_aggregate: BAggregateRef) {
      this._aggregate = _aggregate;
      this.formulas = this.getFormulaList();
      this.formulaObjs = AggregateFormula.getFormulaObjs(this.formulas);

      if(!this._aggregate.percentage) {
         this._aggregate.percentage = StyleConstants.PERCENTAGE_NONE + "";
      }
   }

   get aggregate(): BAggregateRef {
      return this._aggregate;
   }

   get chartAggregate(): ChartAggregateRef {
      return this.aggregate as ChartAggregateRef;
   }

   public constructor(private uiContextService: UIContextService,
                      private bindingService: BindingService) {
   }

   ngOnInit() {
      this.percents = this.getPercents();
   }

   get cube(): boolean {
      let bindingModel = this.bindingService.bindingModel;
      let isCubeSource = bindingModel != null && bindingModel.source != null ?
         bindingModel.source.source.startsWith("___inetsoft_cube_") : false;

      return isCubeSource;
   }

   private getFormulaList(): AggregateFormula[] {
      return AssetUtil.getFormulas(this.cube ? "cube" : this.aggregate?.dataType);
   }

   isPercentageFormula(): boolean {
      return this.isDynamic(this.aggregate.formula) ? this.groupNum > 0
         : SummaryAttrUtil.isPercentageFormula(this.aggregate.formula);
   }

   isPthFormula(): boolean {
      return SummaryAttrUtil.isPthFormula(this.aggregate.formula);
   }

   isNthFormula(): boolean {
      return SummaryAttrUtil.isNthFormula(this.aggregate.formula);
   }

   getPercents() {
      return this.groupNum <= 1 ? SummaryAttrUtil.PERCENT_TYPE_NO_GROUPS :
         SummaryAttrUtil.PERCENT_TYPE_WITH_GROUPS;
   }

   changeFormulaValue(): void {
      this.aggregate.num = (this.isNthFormula() || this.isPthFormula()) ? 1 : 0;
   }

   private isDynamic(formulaValue: string): boolean {
      return formulaValue && (formulaValue.charAt(0) == "=" || formulaValue.charAt(0) == "$");
   }

   get assemblyName(): string {
      return this.bindingService.assemblyName;
   }

   isCrosstab(): boolean {
      let objectType = this.bindingService.objectType;
      return objectType == "VSCrosstab" || objectType == "crosstab";
   }

   get crosstabOption(): CrosstabOptionInfo {
      let bindingModel = this.bindingService.bindingModel;
      return this.isCrosstab() && !!bindingModel ?
         (<CrosstabBindingModel> bindingModel).option : null;
   }

   get percentageDirection(): string {
      return !!this.crosstabOption ? this.crosstabOption.percentageByValue : null;
   }

   updateCalc(result: any) {
      if(!!result) {
         this._aggregate = result.aggr;
         let calcinfo = !!this._aggregate ? this._aggregate.calculateInfo : null;

         if(!!calcinfo && calcinfo.classType == "PERCENT") {
            this.crosstabOption.percentageByValue = result.percentageDirection;
            // this.crosstabOption.percentageByValue = (calcinfo as PercentCalcInfo).byRow ?
            //    StyleConstants.PERCENTAGE_BY_ROW + "" : StyleConstants.PERCENTAGE_BY_COL + "";
         }
      }
   }
}
