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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { ColumnRef } from "../../../binding/data/column-ref";
import { AggregateFormula } from "../../../binding/util/aggregate-formula";
import { AssetUtil } from "../../../binding/util/asset-util";
import { SummaryAttrUtil } from "../../../binding/util/summary-attr-util";
import { AggregateRef } from "../../../common/data/aggregate-ref";
import { DataRef } from "../../../common/data/data-ref";
import { GroupRef } from "../../../common/data/group-ref";
import { DateRangeRef } from "../../../common/util/date-range-ref";
import { AggregateDialogModel } from "../../data/ws/aggregate-dialog-model";
import { AggregateDialog } from "./aggregate-dialog.component";
import { XSchema } from "../../../common/data/xschema";
import { XConstants } from "../../../common/util/xconstants";
import { Tool } from "../../../../../../shared/util/tool";
import { DateLevelExamplesService } from "../../../common/services/date-level-examples.service";

@Component({
   selector: "crosstab-pane",
   templateUrl: "crosstab-pane.component.html"
})
export class CrosstabPane {
   @Input() trapFields: ColumnRef[] = [];
   @Output() validChange: EventEmitter<boolean> = new EventEmitter<boolean>();
   refList: ColumnRef[];
   aggregateRefList: DataRef[];
   columnHeader: HeaderGroup;
   rowHeaders: HeaderGroup[];
   measure: MeasureAggregate;
   valid: boolean = false;
   private _empty: HeaderGroup | MeasureAggregate;
   private readonly baseGroups: {label: string, dgroup: XConstants}[] = [
      {label: "_#(js:None)", dgroup: XConstants.NONE_DATE_GROUP}];
   private _model: AggregateDialogModel;

   @Input()
   set model(model: AggregateDialogModel) {
      this._model = {...model};
      this.modelChanged();
   }

   get model(): AggregateDialogModel {
      return this._model;
   }

   constructor(private examplesService: DateLevelExamplesService) {
   }

   modelChanged() {
      this.refList = [{name: " "} as ColumnRef, ...this.model.columns];
      this.aggregateRefList = this.model.columns;
      this._empty = {
         selectedRef: this.refList[0],
      };
      const headerGroups: HeaderGroup[] = [];

      for(let group of this.model.info.groups) {
         headerGroups.push({
            selectedRef: AssetUtil.getOriginalColumn(<ColumnRef> group.ref, this.model.columns),
            group: group.assemblyName ? group.assemblyName : this.baseGroups[0].label,
            dgroup: group.dgroup,
            timeSeries: group.timeSeries,
            dateLevelExamples: []
         });
      }

      if(this.columnHeader == null || headerGroups[0] != null) {
         this.columnHeader = headerGroups[0];
      }
      else {
         this.columnHeader = null;
      }

      if(this.columnHeader == null) {
         this.rowHeaders = [...headerGroups];
      }
      else {
         this.rowHeaders = headerGroups.slice(1);
      }

      if(this.model.info.aggregates.length > 0) {
         let agg: AggregateRef = this.model.info.aggregates[0];
         this.measure = {
            selectedRef: AssetUtil.getOriginalColumn(<ColumnRef> agg.ref, this.model.columns),
            aggregate: AggregateFormula.getFormula(agg.formulaName),
            aggregateRef: agg.ref2,
            percentage: agg.percentage,
            n: agg.n != null ? agg.n + "" : ""
         };
      }
      else {
         this.measure = undefined;
      }

      this.rowHeaders.forEach((row) => this.populateGroups(row));
      this.rowHeaders.push(this.empty);

      if(this.columnHeader == undefined) {
         this.columnHeader = this.empty;
      }
      else {
         this.populateGroups(this.columnHeader);
      }

      if(this.measure == undefined) {
         this.measure = this.empty;
      }
      else {
         this.populateAggregates(this.measure);
      }

      this.updateState();
   }

   populateGroups(header: HeaderGroup) {
      let num = 0;
      header.availableGroups = [
         ...this.baseGroups,
         ...this.model.groupMap[header.selectedRef.name].map((group) => {
            return {label: group, dgroup: --num};
         }),
         ...AggregateDialog.dateTimeGroups(header.selectedRef)
      ];

      if(Tool.isDate(header.selectedRef.dataType) || AggregateDialog.getDateRangeRef(header.selectedRef) != null) {
         this.examplesService.loadDateLevelExamples(header.availableGroups.map(val => val.dgroup + ""),
            header.selectedRef.dataType).subscribe((data: any) => {
            header.dateLevelExamples = data.dateLevelExamples;
         });
      }
   }

   getTooltip(_ref: ColumnRef): string {
      return ColumnRef.getTooltip(_ref);
   }

   private get empty() {
      return Object.assign({}, this._empty);
   }

   populateAggregates(measure: MeasureAggregate) {
      measure.availableAggregates = AssetUtil.getAggregateModel(measure.selectedRef, true);
   }

   getIndexOfRef(refList: DataRef[], row: any, secondAggregate: boolean = false): number {
      if(row == null || !row.selectedRef) {
         return -1;
      }

      let isGroup = !!row.availableGroups;
      let ref = secondAggregate && !isGroup ? row.aggregateRef : row.selectedRef;
      let index = refList.findIndex((el) => ref.name === el.name);

      if(index == -1 && row.dgroup != XConstants.NONE_DATE_GROUP && !!ref.dataRefModel?.ref) {
         // group column was renamed
         ref = AssetUtil.getOriginalColumn(<ColumnRef> ref.dataRefModel.ref, this.model.columns);
         index = refList.findIndex((el) => el.name == ref.name);

         if(index == -1) {
            // group base column was renamed
            index = refList.findIndex((el: any) => !!el.alias && !!el.dataRefModel ? el.dataRefModel.name === ref.name : false);
         }
      }

      return index;
   }

   getAggregateIndex(): number {
      if(!this.measure.availableAggregates || this.measure.aggregate == undefined) {
         return undefined;
      }

      return this.measure.availableAggregates
         .findIndex((agg) => agg.formulaName === this.measure.aggregate.formulaName);
   }

   getGroupVal(header: HeaderGroup) {
      if(!header.availableGroups) {
         return null;
      }

      if(header.dgroup > 0) {
         let findIndex = header.availableGroups.findIndex((group) => group.dgroup === header.dgroup);
         return header.availableGroups[findIndex].dgroup;
      }
      else if(header.group) {
         let findIndex = header.availableGroups.findIndex((group) => group.label === header.group);
         return header.availableGroups[findIndex].dgroup;
      }
      else {
         return 0;
      }
   }

   getGroupValues(header: HeaderGroup) {
      return header.availableGroups ? header.availableGroups.map(obj => ({label: obj.label, value: obj.dgroup})) : [];
   }

   resetGroupHeader(header: HeaderGroup, refIndex: number, rowHeaderIndex?: number) {
      if(refIndex == 0) { // empty entry
         if(rowHeaderIndex === undefined) { // is column header
            Object.keys(header).forEach((key) => delete header[key]);
            Object.assign(header, this.empty);
         }
         else {
            this.rowHeaders.splice(rowHeaderIndex, 1);
         }
      }
      else {
         header.selectedRef = this.refList[refIndex];

         if(rowHeaderIndex !== undefined && this.rowHeaders[rowHeaderIndex + 1] == undefined) {
            this.rowHeaders[rowHeaderIndex + 1] = this.empty;
         }

         this.populateGroups(header);
         header.group = header.availableGroups[0].label;
         header.dgroup = header.availableGroups[0].dgroup;

         if(XSchema.isDateType(header.selectedRef.dataType)) {
            header.dgroup = DateRangeRef.getNextDateLevel(-1, header.selectedRef.dataType);
            header.group = header.availableGroups.find(g => g.dgroup == header.dgroup).label;
         }
      }

      this.updateState();
   }

   resetAggregateMeasure(refIndex: number) {
      if(refIndex == 0) {
         this.measure = this.empty;
      }
      else {
         this.measure.selectedRef = this.refList[refIndex];
         this.measure.availableAggregates =
            AssetUtil.getAggregateModel(this.measure.selectedRef, true);
         let index = this.measure.availableAggregates.findIndex(agg =>
            agg.formulaName == this.measure.selectedRef.defaultFormula);

         this.aggregateChange(index == -1 ? 0 : index);
      }

      this.updateState();
   }

   aggregateChange(index: number) {
      this.measure.aggregate = this.measure.availableAggregates[index];

      if(this.measure.aggregate.twoColumns) {
         this.measure.aggregateRef = this.aggregateRefList[0];
         this.measure.percentage = false;
      }
      else {
         this.measure.aggregateRef = undefined;
      }

      this.updateState();
   }

   headerGroupChange(header: HeaderGroup, dgroup: XConstants) {
      let findIndex = header.availableGroups.findIndex((group) => group.dgroup === dgroup);
      header.group = header.availableGroups[findIndex].label;
      header.dgroup = header.availableGroups[findIndex].dgroup;

      if(header.dgroup < 0) {
         header.dgroup = XConstants.NONE_DATE_GROUP;
      }

      this.updateState();
   }

   updateState() {
      this.updateModel();
      this.verify();
   }

   private verify() {
      this.valid = !!this.columnHeader.group &&
         this.rowHeaders.length > 1 && !!this.measure.aggregate;

      this.validChange.emit(this.valid);
   }

   updateModel() {
      const groups: GroupRef[] = [];
      const aggregates: AggregateRef[] = [];

      if(this.columnHeader.selectedRef !== this.refList[0]) {
         groups.push(this.toGroupRef(this.columnHeader));
      }

      for(let i = 0; i < this.rowHeaders.length - 1; i++) {
         groups.push(this.toGroupRef(this.rowHeaders[i]));
      }

      if(this.measure.selectedRef !== this.refList[0]) {
         let n = parseInt(this.measure.n + "", 10);

         if(isNaN(n)) {
            this.measure.n = "";
            n = 0;
         }

         aggregates.push({
            classType: "AggregateRef",
            ref: this.measure.selectedRef,
            ref2: this.measure.aggregateRef,
            formulaName: this.measure.aggregate.formulaName,
            percentage: this.measure.percentage,
            n: n
         });
      }

      this.model.info.groups = groups;
      this.model.info.aggregates = aggregates;
   }

   trapField(ref: ColumnRef): boolean {
      return this.trapFields.indexOf(ref) >= 0;
   }

   private toGroupRef(header: HeaderGroup): GroupRef {
      return {
         classType: "GroupRef",
         assemblyName: this.model.groupMap[header.selectedRef.name]
            .find((el: string) => el === header.group),
         ref: header.selectedRef,
         dgroup: header.dgroup,
         timeSeries: header.timeSeries
      };
   }

   getNPLabel(aggregate: AggregateFormula): string {
      return AggregateFormula.getNPLabel(aggregate.formulaName);
   }

   isTimeSeriesDisabled(row: HeaderGroup) {
      return !row.group || !XSchema.isDateType(row.selectedRef.dataType) || !row.dgroup ||
         (row.dgroup & XConstants.PART_DATE_GROUP) != 0;
   }

   isByFormula(aggregate: AggregateFormula): boolean {
      return SummaryAttrUtil.isByFormula(aggregate.formulaName);
   }
}

function headerGroupEquals(header1: HeaderGroup, header2: HeaderGroup): boolean {
   if(header1 == header2) {
      return true;
   }

   if(!header1 || !header2) {
      return false;
   }

   return header1.group === header2.group &&
      header1.dgroup === header2.dgroup &&
      ColumnRef.equal(header1.selectedRef, header2.selectedRef);
}

interface HeaderGroup {
   selectedRef: ColumnRef;
   availableGroups?: {label: string, dgroup?: number}[];
   group?: string;
   dgroup?: number;
   timeSeries?: boolean;
   dateLevelExamples?: string[];
}

interface MeasureAggregate {
   selectedRef: ColumnRef;
   availableAggregates?: AggregateFormula[];
   aggregate?: AggregateFormula;
   aggregateRef?: DataRef;
   percentage?: boolean;
   n?: string;
}
