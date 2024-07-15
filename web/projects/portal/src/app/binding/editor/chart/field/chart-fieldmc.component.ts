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
import { HttpParams } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { Tool } from "../../../../../../../shared/util/tool";
import { ChartRef } from "../../../../common/data/chart-ref";
import { DataRefType } from "../../../../common/data/data-ref-type";
import { DataTransfer, ObjectType } from "../../../../common/data/dnd-transfer";
import { DndService } from "../../../../common/dnd/dnd.service";
import { GraphTypes } from "../../../../common/graph-types";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { ComponentTool } from "../../../../common/util/component-tool";
import { DateComparisonService } from "../../../../vsobjects/util/date-comparison.service";
import { AbstractBindingRef } from "../../../data/abstract-binding-ref";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";
import { ChartBindingModel } from "../../../data/chart/chart-binding-model";
import { ChartAestheticTransfer, ChartTransfer } from "../../../data/chart/chart-transfer";
import { BindingService } from "../../../services/binding.service";
import { ChartEditorService } from "../../../services/chart/chart-editor.service";
import { GraphUtil } from "../../../util/graph-util";
import { FieldMC } from "../../field-mc";
import {
   FeatureFlagsService,
   FeatureFlagValue
} from "../../../../../../../shared/feature-flags/feature-flags.service";
import { StyleConstants } from "../../../../common/util/style-constants";

@Component({
   selector: "chart-fieldmc",
   templateUrl: "chart-fieldmc.component.html",
   styleUrls: ["../../fieldmc.component.scss"]
})
export class ChartFieldmc extends FieldMC {
   @Input() fieldType: string;
   @Input() isEnabled: boolean = true;
   @Input() isAesthetic: boolean = false;
   @Input() index: number;
   @Input() dragComplete: Function;
   @Input() currentAggr: ChartAggregateRef;
   @Input() grayedOutValues: string[] = [];
   @Input() targetField;
   @Output() onChangeAesthetic: EventEmitter<any> = new EventEmitter<any>();
   @Output() onConvert: EventEmitter<any> = new EventEmitter<any>();
   CONVERT_TO_MEASURE = 1;
   CONVERT_TO_DIMENSION = 2;
   private _field: ChartRef;
   private _originalField: ChartRef;
   dialogOpened: boolean = false;

   @Input() set field(field: ChartRef) {
      this._field = field;
      this._originalField = Tool.clone(field);
   }

   get field(): ChartRef {
      return this._field;
   }

   get aggregateField(): ChartAggregateRef {
      return this.field as ChartAggregateRef;
   }

   constructor(bindingService: BindingService,
               private editorService: ChartEditorService,
               private dndService: DndService,
               private modalService: NgbModal,
               private dcService: DateComparisonService,
               protected uiContextService: UIContextService,
               private featureFlagsService: FeatureFlagsService)
   {
      super(bindingService, uiContextService);
   }

   get bindingModel(): ChartBindingModel {
      return this.editorService.bindingModel;
   }

   get params(): HttpParams {
      return this.bindingService.getURLParams();
   }

   get multiStyles(): boolean {
      return this.bindingModel.multiStyles;
   }

   get stackMeasures(): boolean {
      return this.bindingModel.stackMeasures;
   }

   get chartType(): number {
      return this.currentAggr ? this.currentAggr.chartType : this.bindingModel.chartType;
   }

   isSecondaryAxisSupported(agg: ChartAggregateRef): boolean {
      let binding: ChartBindingModel = this.editorService.bindingModel;

      return GraphUtil.isSecondaryAxisSupported(binding, agg);
   }

   imgOpacity(): number {
      let opacity = 0.5;

      if(this.fieldType !== "geofields" &&
         this.isRefConvertEnabled(this.field, this.fieldType))
      {
         opacity = 1;
      }

      return opacity;
   }

   isRefConvertEnabled(field: ChartRef, fieldType: string): boolean {
      return GraphUtil.isRefConvertEnabled(this.chartType, field, fieldType);
   }

   changeColumnValue(value: string): void {
      if(this.isEmptyDynamicValue(value)) {
         return;
      }

      if(Tool.isDynamic(value)) {
         this.field.columnValue = value;
      }
      else {
         this.field.columnValue = this.field.caption != null ? this.field.caption : value;
      }

      if(GraphUtil.isAllChartAggregate(this.currentAggr)) {
         this.onChangeAesthetic.emit();
      }
      else {
         this.changeChartRef();
      }
   }

   openChange(open: boolean): void {
      if(!open) {
         this.dropdown.close();

         if(!!this.field && !this.field.measure) {
            let dim: any = this.field;
            let manualOrders = dim?.manualOrder;

            if((manualOrders == null || manualOrders.length == 0) &&
               dim.order == StyleConstants.SORT_SPECIFIC &&
               dim.specificOrderType == "manual")
            {
               dim.order = StyleConstants.SORT_NONE;
            }
         }

         if(this.bindingModel.hasDateComparison &&
            this.dcService.checkBindingField(<AbstractBindingRef>this.field,
               <AbstractBindingRef>this._originalField, this.isAesthetic))
         {
            ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
               "_#(js:date.comparison.changeBindingField.confirm)")
               .then((buttonClicked) => {
                  if(buttonClicked === "ok") {
                     this.processChange();
                  }
                  else {
                     Object.assign(this.field, this._originalField);
                  }
               });
         }
         else {
            if(!!this.field) {
               let dim: any = this.field;
               delete dim["specificOrderType"];
            }

            this.processChange();
         }
      }
   }

   processChange(): void {
      this.changeAggregateFormula();

      if(this.isBindingChanged()) {
         this.bindingUpdated();

         // edit aesthetic field of allChartAggregateInfo for multistyle chart,
         // need to sync the updated info to all xy aggergate field.
         if(GraphUtil.isAllChartAggregate(this.currentAggr)) {
            this.onChangeAesthetic.emit();
         }
         else {
            this.changeChartRef();
         }
      }
   }

   /**
    * formula may be set to none (e.g. boxplot), if it's set to discrete, should
    * be aggregated (otherwise just change measure to dimension)
    */
   changeAggregateFormula() {
      if(this.field.classType == "aggregate") {
         let aggRef: ChartAggregateRef = <ChartAggregateRef> this.field;

         if(aggRef.discrete && aggRef.formula == "none") {
            aggRef.formula = "Sum";
         }
      }
   }

   changeChartRef(): void {
      this.editorService.changeChartRef(this.field, this.fieldType);
   }

   isSortSupported(): boolean {
      // funnel fixed as sort-by-value
      if(GraphTypes.isFunnel(this.bindingModel.chartType) && this.fieldType == "yfields" &&
         this.index == this.bindingModel.yfields.length - 1)
      {
         return false;
      }

      return this.fieldType != "geofields";
   }

   convert(): void {
      let convertType = this.field.measure ?
         this.CONVERT_TO_DIMENSION : this.CONVERT_TO_MEASURE;
      GraphUtil.convertChartRef(this.bindingModel, this.currentAggr, this.field);
      this.onConvert.emit({name: this.field.dataRefModel.name, type: convertType});
   }

   get cellLabel(): string {
      if(this.field.view) {
         return this.field.view;
      }

      return this.field.measure ? (<ChartAggregateRef>this.field).oriFullName : this.field.name;
   }

   get cellValue(): string {
      let str: string = "";

      if(this.field == null) {
         return "";
      }

      if(this.field.caption && this.field.columnValue != null &&
         this.field.columnValue != "" && !Tool.isDynamic(this.field.columnValue))
      {
         str = this.field.caption;
      }
      else if(this.field.columnValue) {
         str = this.field.columnValue;
      }

      if(str == null || str == "") {
         str = this.field.view;
      }

      return str;
   }

   get strippedDrillmemberVariables(): string[] {
      let strippedList: string[] = [];

      if(this.variables == null) {
         return strippedList;
      }

      this.variables.forEach((variable: string) => {

         if(variable.indexOf(".drillMember") == -1) {
            strippedList.push(variable);
         }
      });
      return strippedList;
   }

   geoBtnVisible(): boolean {
      if(this.field.measure) {
         return (this.fieldType == "yfields" || this.fieldType == "xfields") &&
            GraphTypes.isGeo(this.chartType);
      }
      else {
         return this.fieldType === "geofields";
      }
   }

   dragStart(event: any): void {
      if(!this.field) {
         return;
      }

      let dragType: number = this.editorService.getDNDType(this.fieldType);

      if(dragType < 0) {
         return;
      }

      let transfer: DataTransfer;
      let refs: Array<ChartRef> = [this.field];

      if(this.fieldType == "color" || this.fieldType == "shape" ||
         this.fieldType == "size" || this.fieldType == "text")
      {
         transfer = new ChartAestheticTransfer(dragType + "", refs, this.currentAggr,
                                               this.assemblyName, this.targetField);
      }
      else {
         transfer = new ChartTransfer(dragType + "", refs, this.assemblyName);
      }

      transfer.objectType = <ObjectType> this.bindingService.objectType;
      Tool.setTransferData(event.dataTransfer, {dragSource: transfer});
      this.dndService.setDragStartStyle(event, this.field.columnValue);
   }

   /**
    * Get the content show in chart dimension mc.
    */
   private getDisplayLabel(field: ChartRef): string {
      return field == null || field.dataRefModel == null ? "" : field.dataRefModel.view;
   }

   getFieldClassType(): string {
      return GraphUtil.isDimensionRef(this.field) ||
         GraphUtil.isGeoRef(this.field) ? "Dimension" : "Measure";
   }

   convertBtnTitle(): string {
      return GraphUtil.isDimensionRef(this.field) ? "_#(js:Convert To Measure)"
         : "_#(js:Convert To Dimension)";
   }

   isEditMeasure(): boolean {
      if(!this.field || !this.field.measure) {
         return true;
      }

      return this.field.refType != DataRefType.CUBE_MEASURE || this.isSqlServer();
   }

   isChartRefMeasure(fields: ChartRef[]): boolean {
      for(let i = 0; !!fields && i < fields.length; i++) {
         if(fields[i].measure) {
            return true;
         }
      }

      return false;
   }

   isVisibleChartTypeButton(): boolean {
      if(this.fieldType != "yfields" && this.fieldType != "xfields" || !this.field.measure) {
         return false;
      }

      if(this.isChartRefMeasure(this.bindingModel.xfields) &&
         this.isChartRefMeasure(this.bindingModel.yfields) &&
         this.multiStyles && !this.isAesthetic &&
         !(<ChartAggregateRef>this.field).discrete)
      {
         return this.field.original && this.field.original.source == "Y";
      }

      return this.field.measure && this.multiStyles
         && !this.isAesthetic && !(<ChartAggregateRef>this.field).discrete;
   }

   isOuterDimRef(): boolean {
      let bindingModel = this.bindingModel;
      let field = this.field;

      if(this.fieldType == "xfields") {
         for(let i = 0; i < bindingModel.xfields.length; i++) {
            let ref2: ChartRef = bindingModel.xfields[i];

            if(!GraphUtil.isDimensionRef(ref2)) {
               break;
            }

            if(field.fullName != ref2.fullName) {
               continue;
            }

            return i != bindingModel.xfields.length - 1;
         }
      }
      else if(this.fieldType == "yfields") {
         for(let i = 0; i < bindingModel.yfields.length; i++) {
            let ref2: ChartRef = bindingModel.yfields[i];

            if(!GraphUtil.isDimensionRef(ref2)) {
               break;
            }

            if(field.fullName != ref2.fullName) {
               continue;
            }

            let ctype: number = bindingModel.chartType;

            // for candle and stock, the dimension in y is a outer dimension
            // no measure, use fake Y column so this is outer
            return i != bindingModel.yfields.length - 1 ||
               ctype == GraphTypes.CHART_STOCK ||
               ctype == GraphTypes.CHART_CANDLE ||
               bindingModel.xfields.length > 0 &&
               GraphUtil.getMeasures(bindingModel.xfields).length == 0;
         }
      }

      return false;
   }

   getTitle(): string {
      return this.getFieldClassType() == "Dimension"
               ? "_#(js:Edit Dimension)" : "_#(js:Edit Measure)";
   }
}
