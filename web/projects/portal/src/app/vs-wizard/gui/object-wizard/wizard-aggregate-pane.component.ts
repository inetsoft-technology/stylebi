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
import { ChartDimensionRef } from "../../../binding/data/chart/chart-dimension-ref";
import { ChartAggregateRef } from "../../../binding/data/chart/chart-aggregate-ref";
import { Tool } from "../../../../../../shared/util/tool";
import { CommonKVModel } from "../../../common/data/common-kv-model";
import { DateRangeRef } from "../../../common/util/date-range-ref";
import { XSchema } from "../../../common/data/xschema";
import { DataRef } from "../../../common/data/data-ref";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { UpdateColumnsEvent } from "../../model/event/update-columns-event";
import { FormatInfoModel } from "../../../common/data/format-info-model";
import { VSObjectFormatInfoModel } from "../../../common/data/vs-object-format-info-model";
import { AggregateFormula } from "../../../binding/util/aggregate-formula";
import { SetWizardBindingFormatEvent } from "../../model/event/set-wizard-binding-format-event";
import { SummaryAttrUtil } from "../../../binding/util/summary-attr-util";
import { VSObjectType } from "../../../common/data/vs-object-type";

const UPDATE_COLUMNS = "/events/vswizard/binding/update-columns";
const UPDATE_WIZARD_BINDING_FORMAT = "/events/vswizard/object/format";

@Component({
   selector: "wizard-aggregate-pane",
   templateUrl: "./wizard-aggregate-pane.component.html",
   styleUrls: ["./wizard-aggregate-pane.component.scss"]
})
export class VSWizardAggregatePane implements OnInit {
   @Input() dimensions: ChartDimensionRef[];
   @Input() measures: ChartAggregateRef[];
   @Input() details: DataRef[];
   @Input() isAssemblyBinding: boolean = false;
   @Input() isCube: boolean = false;
   @Input() hiddenFormulaColumns: string[];
   @Input() availableFields: DataRef[];
   @Input() grayedOutFields: DataRef[];
   @Input() formatMap: Map<string, VSObjectFormatInfoModel>;
   @Input() isDetail: boolean = true;
   @Input() autoOrder: boolean;
   @Input() showAutoOrder: boolean;
   @Input() objectType: VSObjectType;
   @Input() fixedFormulaMap: CommonKVModel<string, string>[];
   @Output() onEditAggregate: EventEmitter<null> = new EventEmitter<null>();
   @Output() onEditSecondColumn: EventEmitter<number> = new EventEmitter<number>();
   @Output() onAddAggregate: EventEmitter<null> = new EventEmitter<null>();
   @Output() onDeleteAggregate: EventEmitter<ChartAggregateRef> = new EventEmitter<ChartAggregateRef>();
   @Output() onEditDimension: EventEmitter<null> = new EventEmitter<null>();
   @Output() onAddDimension: EventEmitter<null> = new EventEmitter<null>();
   @Output() onDeleteDimension: EventEmitter<ChartDimensionRef> = new EventEmitter<ChartDimensionRef>();
   @Output() onUpdateFormat: EventEmitter<string> = new EventEmitter<string>();
   @Output() onUpdateDetails = new EventEmitter<string>();
   @Output() onAutoOrderChange = new EventEmitter<boolean>();

   constructor(private viewsheetClient: ViewsheetClientService) {
   }

   ngOnInit() {
   }

   addGroup(evt: number) {
      let dim = this.dimensions[evt];
      let ndim = Tool.clone(dim);
      ndim.dateLevel = DateRangeRef.getNextDateLevel(
         parseInt(dim.dateLevel, 10), dim.dataType) + "";
      this.dimensions.splice(evt + 1, 0, ndim);
      this.updateOriginalIndex(this.dimensions, evt);
      this.onAddDimension.emit();
   }

   addAggregate(evt: number) {
      let agg = this.measures[evt];
      let nagg: ChartAggregateRef = Tool.clone(agg);

      if(!!nagg["colorFrame"]) {
         nagg["colorFrame"] = null;
      }

      this.measures.splice(evt + 1, 0, nagg);
      this.updateOriginalIndex(this.measures, evt);
      this.onAddAggregate.emit();
   }

   private updateOriginalIndex(fields: ChartDimensionRef[] | ChartAggregateRef[],
                               startIndex: number): void
   {
      for(let i = startIndex + 1; i < fields.length; i++) {
         if(fields[i].original) {
            fields[i].original.index += 1;
         }
      }
   }

   deleteGroup(evt: number) {
      let deletedimension = this.dimensions[evt];
      this.dimensions.splice(evt, 1);
      this.fixDetails(this.dimensions, deletedimension.columnValue);
      this.onDeleteDimension.emit(deletedimension);
   }

   deleteAggregate(evt: number) {
      let deletedMeasure = this.measures[evt];
      this.measures.splice(evt, 1);
      this.fixDetails(this.measures, deletedMeasure.columnValue);
      this.onDeleteAggregate.emit(deletedMeasure);
   }

   deleteDetail(idx: number) {
      let name = this.details[idx].name;
      this.details.splice(idx, 1);
      this.onUpdateDetails.emit(name);
   }

   fixDetails(refs: DataRef[], name: string) {
      if(!this.isDetail) {
         return;
      }

      if(refs == null) {
         return;
      }

      for(let i = refs.length - 1; i >= 0; i--) {
         let ref = refs[i];

         if(ref.name == name) {
            refs.splice(i, 1);
         }
      }
   }

   showDimensionName(idx: number) {
      let dim = this.dimensions[idx];
      let isDate =  XSchema.isDateType(dim.dataType);

      if(!isDate || idx == 0) {
         return true;
      }

      let lastDim = this.dimensions[idx - 1];

      return dim.name != lastDim.name;
   }

   showAggregateName(idx: number) {
      if(idx == 0) {
         return true;
      }

      let agg = this.measures[idx];
      let lastAgg = this.measures[idx - 1];

      return agg.name != lastAgg.name;
   }

   showDimensionMore(idx: number) {
      let dim = this.dimensions[idx];
      let isDate =  XSchema.isDateType(dim.dataType);

      if(!isDate) {
         return false;
      }

      if(idx == this.dimensions.length - 1) {
         return true;
      }

      let nextDim = this.dimensions[idx + 1];

      return dim.name != nextDim.name;
   }

   showAggregateMore(idx: number) {
      let agg = this.measures[idx];

      if(idx == this.measures.length - 1) {
         return true;
      }

      let nextAgg = this.measures[idx + 1];

      return agg.name != nextAgg.name;
   }

   getForceDisplayFormula(idx: number): string {
      const index = this.findIndex(this.measures[idx].fullName);

      if(index > -1) {
         const kvModel = this.fixedFormulaMap[index];
         return !!kvModel ? kvModel.value : null;
      }

      return null;
   }

   private findIndex(key: string): number {
      if(!this.fixedFormulaMap || this.fixedFormulaMap.length == 0) {
         return -1;
      }

      let result = -1;
      this.fixedFormulaMap.some((kvModel, index) => {
         if(kvModel.key == key) {
            result = index;

            return true;
         }

         return false;
      });

      return result;
   }

   getFormat(ref: ChartDimensionRef | ChartAggregateRef): FormatInfoModel {
      if(!!this.formatMap) {
         return this.formatMap.get(this.isDetail ? ref.name : ref.fullName);
      }

      return null;
   }

   getMeasureFormat(idx: number): FormatInfoModel {
      if(!this.formatMap) {
         return null;
      }

      return this.formatMap.get(this.getAggrFormatKey(idx));
   }

   getGrayedOutValues() {
      let grayedOutValues = [];

      if(this.grayedOutFields != null && this.grayedOutFields.length > 0) {
         for(let fld of this.grayedOutFields) {
            grayedOutValues.push(fld.name);
         }
      }

      return grayedOutValues;
   }

   // move dimension (group) up by one position
   moveUpDim(idx: number) {
      const idx2 = this.findLastDim(idx);
      const up2 = idx - 1;
      const up = this.findFirstDim(up2);

      // copy block above to below the moved block
      for(let i = up; i <= up2; i++) {
         this.dimensions.splice(idx2 + 1 + i - up, 0, this.dimensions[i]);
      }

      // delete moved block
      this.dimensions.splice(up, up2 - up + 1);

      this.dimensions.forEach(d => d.original = null);

      if(this.autoOrder) {
         this.setAutoOrder(false);
      }
      else {
         this.onAddDimension.emit();
      }
   }

   // move dimension (group) down by one position
   moveDownDim(idx: number) {
      const idx2 = this.findLastDim(idx);
      const insert = idx2 + 1;
      const insert2 = this.findLastDim(insert);

      // copy moved to the end of the inserted to block
      for(let i = idx; i <= idx2; i++) {
         this.dimensions.splice(insert2 + 1 + i - idx, 0, this.dimensions[i]);
      }

      // delete moved block
      this.dimensions.splice(idx, idx2 - idx + 1);

      this.dimensions.forEach(d => d.original = null);

      if(this.autoOrder) {
         this.setAutoOrder(false);
      }
      else {
         this.onAddDimension.emit();
      }
   }

   // find first dim from the same column
   private findFirstDim(idx: number): number {
      while(idx > 0 && !this.showDimensionName(idx)) {
         idx--;
      }

      return idx;
   }

   // find last dim from the same column
   private findLastDim(idx: number): number {
      while(idx < this.dimensions.length - 1 && !this.showDimensionName(idx + 1)) {
         idx++;
      }

      return idx;
   }

   isMoveDownDimEnabled(idx: number): boolean {
      return this.findLastDim(idx) < this.dimensions.length - 1;
   }

   // move measure (group) up by one position
   moveUpMeasure(idx: number) {
      const idx2 = this.findLastMeasure(idx);
      const up2 = idx - 1;
      const up = this.findFirstMeasure(up2);

      // copy block above to below the moved block
      for(let i = up; i <= up2; i++) {
         this.measures.splice(idx2 + 1 + i - up, 0, this.measures[i]);
      }

      // delete moved block
      this.measures.splice(up, up2 - up + 1);

      this.measures.forEach(d => d.original = null);

      if(this.autoOrder) {
         this.setAutoOrder(false);
      }
      else {
         this.onAddAggregate.emit();
      }
   }

   // move measure (group) down by one position
   moveDownMeasure(idx: number) {
      const idx2 = this.findLastMeasure(idx);
      const insert = idx2 + 1;
      const insert2 = this.findLastMeasure(insert);

      // copy moved to the end of the inserted to block
      for(let i = idx; i <= idx2; i++) {
         this.measures.splice(insert2 + 1 + i - idx, 0, this.measures[i]);
      }

      // delete moved block
      this.measures.splice(idx, idx2 - idx + 1);

      this.measures.forEach(d => d.original = null);

      if(this.autoOrder) {
         this.setAutoOrder(false);
      }
      else {
         this.onAddAggregate.emit();
      }
   }

   // find first Measure from the same column
   private findFirstMeasure(idx: number): number {
      while(idx > 0 && !this.showAggregateName(idx)) {
         idx--;
      }

      return idx;
   }

   // find last Measure from the same column
   private findLastMeasure(idx: number): number {
      while(idx < this.measures.length - 1 && !this.showAggregateName(idx + 1)) {
         idx++;
      }

      return idx;
   }

   isMoveDownMeasureEnabled(idx: number): boolean {
      return this.findLastMeasure(idx) < this.measures.length - 1;
   }

   // move detail up by one position
   moveUpDetail(idx: number) {
      const up = idx - 1;

      let event = new UpdateColumnsEvent(idx, up, this.objectType);
      this.viewsheetClient.sendEvent(UPDATE_COLUMNS, event);
   }

   // move detail down by one position
   moveDownDetail(idx: number) {
      const down = idx + 1;

      let event = new UpdateColumnsEvent(idx, down, this.objectType);
      this.viewsheetClient.sendEvent(UPDATE_COLUMNS, event);
   }

   hasItem(): boolean {
      return (!!this.dimensions && this.dimensions.length > 0) ||
         (!!this.measures && this.measures.length > 0) ||
         (!!this.details && this.details.length > 0);
   }

   setAutoOrder(autoOrder: boolean) {
      this.autoOrder = autoOrder;
      this.onAutoOrderChange.emit(autoOrder);
   }

   /**
    * Return the map key in formatMap for aggregate field.
    * @param idx the aggregate index.
    */
   private getAggrFormatKey(idx: number): string {
      if(!this.measures || idx > this.measures.length) {
         return null;
      }

      let forceFormula = this.getForceDisplayFormula(idx);
      let measureName = this.measures[idx].name;
      let attribute = this.measures[idx].attribute;

      if(forceFormula == null && AggregateFormula.isSameTypeFormula(this.measures[idx].formula)) {
         if(this.formatMap.get(measureName) != null) {
            return measureName;
         }

         if(this.formatMap.get(attribute) != null) {
            return attribute;
         }
      }
      else if(forceFormula != null && !Tool.equalsIgnoreCase(forceFormula, "none")) {
         let formula = this.measures[idx].formula;

         if(SummaryAttrUtil.isWithFormula(formula) || SummaryAttrUtil.isPthFormula(formula) ||
            SummaryAttrUtil.isNthFormula(formula))
         {
            return forceFormula + "(" + measureName + ")";
         }

         return this.measures[idx].fullName.replace(formula, forceFormula);
      }
      else if(forceFormula == "none") {
         return measureName;
      }

      return this.measures[idx].fullName;
   }

   updateAggregateFormat(idx: number): void {
      if(!this.formatMap) {
         return;
      }

      const fieldName = this.getAggrFormatKey(idx);
      const format = this.formatMap.get(fieldName);

      if(format) {
         const event: SetWizardBindingFormatEvent = new SetWizardBindingFormatEvent(fieldName, format);
         this.viewsheetClient.sendEvent(UPDATE_WIZARD_BINDING_FORMAT, event);
      }
   }
}
