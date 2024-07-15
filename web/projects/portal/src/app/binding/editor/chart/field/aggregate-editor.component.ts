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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { DataRef } from "../../../../common/data/data-ref";
import { DataRefType } from "../../../../common/data/data-ref-type";
import { GraphTypes } from "../../../../common/graph-types";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";
import { AggregateFormula } from "../../../util/aggregate-formula";
import { AssetUtil } from "../../../util/asset-util";

@Component({
   selector: "aggregate-editor",
   templateUrl: "aggregate-editor.component.html",
   styleUrls: ["./aggregate-editor.component.scss"]
})
export class AggregateEditor implements OnInit {
   @Input() fieldType: string;
   @Input() chartType: number;
   @Input() availableFields: Array<DataRef>;
   @Input() variables: any;
   @Input() vsId: string;
   @Input() grayedOutValues: string[] = [];
   @Input() isVSWizard = false;
   @Input() isSecondaryAxisSupported = false;
   @Output() apply: EventEmitter<any> = new EventEmitter<any>();
   aggregateStatus: boolean;
   formulaObjs: any[];
   _aggregate: ChartAggregateRef;

   @Input()
   set aggregate(_aggregate: ChartAggregateRef) {
      this._aggregate = _aggregate;
   }
   get aggregate(): ChartAggregateRef {
      return this._aggregate;
   }

   constructor(private dialogService: NgbModal, private uiContextService: UIContextService) {
   }

   get cube(): boolean {
      return this.aggregate && (this.aggregate.refType & DataRefType.CUBE) != 0; // eslint-disable-line no-bitwise
   }

   ngOnInit(): void {
      let formulas: AggregateFormula[] = AssetUtil.getFormulas(this.cube ? "cube" : this.aggregate?.dataType);
      this.formulaObjs = AggregateFormula.getFormulaObjs(formulas);
   }

   get discreteValue(): boolean {
      return this.aggregate.discrete;
   }

   set discreteValue(val: boolean) {
      this.aggregate.discrete = val;

      if(val) {
         this.aggregate.secondaryY = false;
      }
   }

   get secondaryValue(): boolean {
      return this.aggregate.secondaryY;
   }

   set secondaryValue(val: boolean) {
      this.aggregate.secondaryY = val;

      if(val) {
         this.aggregate.discrete = false;
      }
   }

   discreteDisabled(): boolean {
      if(this.cube) {
         return true;
      }

      if(GraphTypes.isCandle(this.chartType) || GraphTypes.isStock(this.chartType) ||
         GraphTypes.isMekko(this.chartType))
      {
         return true;
      }

      // the current implementation of discrete measure in ChartVSAQuery doesn't work
      // well with calc field based on aggregate. if the grouping is performed in two
      // stages (see chart.aoa.mirror), the discrete query can't be simplied created
      // and joined with the main query. it would require extensive rewrite to support
      // it. we may consider it when need arises. (13.1)
      if(this.isCalculateRef()) {
         return true;
      }

      // non-aggregate discrete doesn't make much sense can generates sql error since
      // non-aggregated column can't be grouped.
      if(this.aggregate.formula == "None" || this.aggregate.formula == "none" ||
         !this.isAggregated() || !this.aggregate.aggregated)
      {
         return true;
      }

      if(!this.fieldType) {
         return false;
      }

      if(this.fieldType == "size" && GraphTypes.isInterval(this.chartType)) {
         return true;
      }

      return this.fieldType == "HighField" || this.fieldType == "CloseField"
         || this.fieldType == "OpenField" || this.fieldType == "LowField";
   }

   private isNoneFormula() {
      return this.aggregate.formula == "None" || this.aggregate.formula == "none" ||
         this.aggregate.formula == "";
   }

   private isCalculateRef(): boolean {
      return this.availableFields && this.availableFields
         .some(f => f.name == this.aggregate.name && f.classType == "CalculateRef" &&
               !(<any>f).baseOnDetail);
   }

   isAggregated(): boolean {
      return !this.isBoxplot() && !GraphTypes.isGantt(this.chartType) || this.discreteValue;
   }

   isTrendSupported(): boolean {
      return !this.isBoxplot() && !GraphTypes.isGantt(this.chartType) &&
         !GraphTypes.isContour(this.chartType);
   }

   isBoxplot(): boolean {
      return GraphTypes.CHART_BOXPLOT == this.chartType;
   }

   applyClick() {
      this.apply.emit(false);
   }
}
