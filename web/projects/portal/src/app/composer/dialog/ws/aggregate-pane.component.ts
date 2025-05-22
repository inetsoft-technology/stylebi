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
import { Tool } from "../../../../../../shared/util/tool";
import { ColumnRef } from "../../../binding/data/column-ref";
import { AggregateFormula } from "../../../binding/util/aggregate-formula";
import { AssetUtil } from "../../../binding/util/asset-util";
import { AggregateRef } from "../../../common/data/aggregate-ref";
import { DataRef } from "../../../common/data/data-ref";
import { DataRefType } from "../../../common/data/data-ref-type";
import { GroupRef } from "../../../common/data/group-ref";
import { XSchema } from "../../../common/data/xschema";
import { StyleConstants } from "../../../common/util/style-constants";
import { XConstants } from "../../../common/util/xconstants";
import { ValueLabelPair } from "../../../portal/data/model/datasources/database/value-label-pair";
import { AggregateDialogModel } from "../../data/ws/aggregate-dialog-model";
import { AggregateDialog, LabelDGroup } from "./aggregate-dialog.component";
import { DateRangeRef } from "../../../common/data/date-range-ref";
import { DateLevelExamplesService } from "../../../common/services/date-level-examples.service";
import { SummaryAttrUtil } from "../../../binding/util/summary-attr-util";

@Component({
   selector: "aggregate-pane",
   templateUrl: "aggregate-pane.component.html",
})
export class AggregatePane {
   @Input() trapFields: ColumnRef[] = [];
   private _model: AggregateDialogModel;
   @Output() validChange: EventEmitter<boolean> = new EventEmitter<boolean>();
   rows: Row[];
   refList: ColumnRef[];
   aggregateRefList: ColumnRef[];
   groups: LabelDGroup[][] = [];
   aggregates: AggregateFormula[][] = [];
   valid: boolean = false;
   dateLevelExamples: [][] = [];

   readonly EMPTY_REF_INDEX = -1;
   private readonly baseGroups: LabelDGroup[] = [
      {label: "_#(js:None)", dgroup: XConstants.NONE_DATE_GROUP}
   ];
   private readonly _empty: Row = {
      selectedRef: null,
      isGroup: null,
      percentage: null,
      percentageOption: StyleConstants.NONE
   };

   percentageOptions: ValueLabelPair[] = [
      {label: "_#(js:None)", value: StyleConstants.PERCENTAGE_NONE},
      {label: "_#(js:Grand Total)", value: StyleConstants.PERCENTAGE_OF_GRANDTOTAL},
      {label: "_#(js:Sub Total)", value: StyleConstants.PERCENTAGE_OF_GROUP},
   ];

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

   private modelChanged(): void {
      this.refList = [];
      this.aggregateRefList = this.model.columns;
      this.rows = [];

      for(let group of this.model.info.groups) {
         // in case group is on a date-range column in the table, instead of date group
         // of a date column, should just use the date-range column as base
         let originColumn: ColumnRef = group.dgroup === XConstants.NONE_DATE_GROUP
            ? <ColumnRef> group.ref
            : AssetUtil.getOriginalColumn(<ColumnRef> group.ref, this.model.columns);
         this.rows.push({
            selectedRef: originColumn,
            isGroup: true,
            group: this.getGroupOf(group),
            dgroup: group.dgroup,
            percentage: false,
            percentageOption: StyleConstants.PERCENTAGE_NONE,
            timeSeries: group.timeSeries
         });
      }

      for(let agg of this.model.info.aggregates) {
         let percentageOption = agg.percentageOption;

         if(!percentageOption) {
            percentageOption = agg.percentage ?
               StyleConstants.PERCENTAGE_OF_GRANDTOTAL : StyleConstants.PERCENTAGE_NONE;
         }

         this.rows.push({
            selectedRef: AssetUtil.getOriginalColumn(<ColumnRef> agg.ref, this.model.columns),
            isGroup: false,
            aggregate: AggregateFormula.getFormula(agg.formulaName),
            aggregateRef: agg.ref2,
            percentage: agg.percentage,
            percentageOption: percentageOption,
            n: agg.n != null ? agg.n + "" : ""
         });
      }

      this.rows.forEach((row, i) => this.populateOptions(row, i));
      this.rows.forEach((row, i) => {
         if(row.isGroup) {
            delete row.aggregate;
            delete row.aggregateRef;
         }
         else {
            delete row.group;
            delete row.dgroup;
         }
      });

      const remaining = this.model.columns.filter(
         c => !this.refList.some(r => ColumnRef.equal(r, c)));
      this.refList.push(...remaining);

      this.refList.sort((a, b) => {
         if(a.view < b.view) {
            return -1;
         }
         else if (a.view > b.view) {
            return 1;
         }
         else {
            return 0;
         }
      });

      this.rows.push(this.empty);
      this.updateState();
   }

   private getGroupOf(ref: GroupRef): string {
      if(ref.assemblyName) {
         return ref.assemblyName;
      }

      if(ref.dgroup) {
         return ref.attribute;
      }

      return this.baseGroups[0].label;
   }

   private get empty(): Row {
      return Object.assign({}, this._empty);
   }

   updateModel(): void {
      this.model.info.groups = [];
      this.model.info.aggregates = [];

      this.rows.forEach((row) => {
         if(row.isGroup === true) {
            let group: GroupRef = {
               classType: "GroupRef",
               assemblyName: this.model.groupMap[row.selectedRef.name]
                  .find((el: string) => el === row.group),
               ref: row.selectedRef,
               dgroup: row.dgroup,
               timeSeries: row.timeSeries
            };

            this.model.info.groups.push(group);
         }
         else if(row.isGroup === false) {
            let n = parseInt(row.n + "", 10);

            if(isNaN(n) || n < 1) {
               row.n = "";
               n = 1;
            }

            let aggregate: AggregateRef = {
               classType: "AggregateRef",
               ref: row.selectedRef,
               ref2: row.aggregateRef,
               formulaName: row.aggregate.formulaName,
               percentage: row.percentage,
               percentageOption: row.percentageOption,
               n: n
            };

            this.model.info.aggregates.push(aggregate);
         }
      });
   }

   resetRow(listIndex: number, refIndex: number): void {
      if(refIndex === this.EMPTY_REF_INDEX) { // empty entry
         this.rows.splice(listIndex, 1);
         this.groups.splice(listIndex, 1);
         this.aggregates.splice(listIndex, 1);

         if(this.rows.length === 0) {
            this.rows.push(this.empty);
         }
      }
      else {
         let row = this.rows[listIndex];
         row.selectedRef = this.refList[refIndex];
         row.isGroup = row.selectedRef.refType ? (row.selectedRef.refType != DataRefType.MEASURE &&
            row.selectedRef.refType != DataRefType.CUBE_MEASURE) :
            !XSchema.isNumericType(row.selectedRef.dataType);

         if(this.rows[listIndex + 1] == null) {
            this.rows[listIndex + 1] = this.empty;
         }

         this.populateOptions(row, listIndex);
         this.resetAggregateDropdowns(row, listIndex);
         row.percentage = false;
         row.percentageOption = StyleConstants.PERCENTAGE_NONE;
      }

      this.updateState();
   }

   populateOptions(row: Row, index: number): void {
      let num = 0;
      this.groups[index] = [
         ...this.baseGroups,
         ...(this.model.groupMap[row.selectedRef.name] || []).map((group) => {
            return {label: group, dgroup: --num};
         }),
         ...AggregateDialog.dateTimeGroups(row.selectedRef)
      ];

      if(Tool.isDate(row.selectedRef.dataType) || AggregateDialog.getDateRangeRef(row.selectedRef) != null) {
         this.examplesService.loadDateLevelExamples(this.groups[index].map(val => val.dgroup + ""),
            row.selectedRef.dataType).subscribe((data: any) => {
            this.dateLevelExamples[index] = data.dateLevelExamples;
         });
      }

      this.aggregates[index] = AssetUtil.getAggregateModel(row.selectedRef, true);
   }

   getIndexOfRef(refList: ColumnRef[], row: any, secondAggregate: boolean = false): number {
      if(row == null || !row.selectedRef) {
         return this.EMPTY_REF_INDEX;
      }

      let ref = secondAggregate && !row.isGroup ? row.aggregateRef : row.selectedRef;
      let index = refList.findIndex((el) => ref.name === el.name);

      if(index == -1 && row.isGroup &&
         (row.dgroup != XConstants.NONE_DATE_GROUP ||
            (!!ref.dataRefModel?.option && ref.dataRefModel.option != XConstants.NONE_DATE_GROUP)) &&
          !!ref.dataRefModel?.ref)
      {
         // group column was renamed
         ref = AssetUtil.getOriginalColumn(<ColumnRef> ref.dataRefModel.ref, this.model.columns);
         index = refList.findIndex((el) => el.name == ref.name);

         if(index == -1) {
            // group base column was renamed
            index = refList.findIndex((el) => !!el.alias && !!el.dataRefModel ? el.dataRefModel.name === ref.name : false);
         }
      }

      return index;
   }

   getTooltip(_ref: ColumnRef): string {
      return ColumnRef.getTooltip(_ref);
   }

   private resetAggregateDropdowns(row: Row, listIndex: number): void {
      const isDateTimeGroup: boolean = this.groups[listIndex].length > 1 &&
         (row.selectedRef.classType === "DateRangeRef" || Tool.isDate(row.selectedRef.dataType));
      row.group = isDateTimeGroup ? this.groups[listIndex][1].label :
         this.groups[listIndex][0].label;
      row.dgroup = isDateTimeGroup ? this.groups[listIndex][1].dgroup :
         this.groups[listIndex][0].dgroup;
      const aggregateFormulaIndex = this.aggregates[listIndex].findIndex(
         (formula) => formula.formulaName === row.selectedRef.defaultFormula);
      const formulaIndex = aggregateFormulaIndex >= 0 ? aggregateFormulaIndex : 0;
      this.aggregateChange(row, listIndex, formulaIndex);
   }

   getGroupObjs(index: number) {
      return this.groups[index].map((obj) => ({label: obj.label, value: obj.dgroup}));
   }

   getGroupVal(row: Row, listIndex: number): XConstants {
      if(row.dgroup > 0) {
         let findIndex = this.groups[listIndex].findIndex((group) => group.dgroup === row.dgroup);

         if(findIndex < 0) {
            return XConstants.NONE_DATE_GROUP;
         }

         return this.groups[listIndex][findIndex].dgroup;
      }
      else if(row.group) {
         let findIndex = this.groups[listIndex].findIndex((group) => group.label === row.group);

         if(findIndex == -1) {
            return null;
         }

         return this.groups[listIndex][findIndex].dgroup;
      }
      else {
         return this.groups[listIndex][0].dgroup;
      }
   }

   getAggregateIndexOf(row: Row, listIndex: number): number {
      return this.aggregates[listIndex]
         .findIndex((agg) => agg.formulaName === row.aggregate.formulaName);
   }

   isGroupChange(row: Row, listIndex: number, checked: boolean, isGroupCheckbox: boolean): void {
      row.isGroup = checked ? isGroupCheckbox : null;

      if(row.isGroup == null) {
         this.resetRow(listIndex, this.EMPTY_REF_INDEX);
         return;
      }

      if(isGroupCheckbox) {
         row.percentage = false;
         row.percentageOption = StyleConstants.PERCENTAGE_NONE;
      }

      if(checked) {
         this.resetAggregateDropdowns(row, listIndex);
      }

      this.updateState();
   }

   groupChange(row: Row, i: number, dgroup: XConstants): void {
      let findIndex = this.groups[i].findIndex((group) => group.dgroup === dgroup);
      row.group = this.groups[i][findIndex].label;
      row.dgroup = this.groups[i][findIndex].dgroup;

      if(row.dgroup < 0) {
         row.dgroup = XConstants.NONE_DATE_GROUP;
      }

      this.updateState();
   }

   aggregateChange(row: Row, listIndex: number, formulaIndex: number): void {
      row.aggregate = this.aggregates[listIndex][formulaIndex];

      if(row.aggregate.twoColumns) {
         row.aggregateRef = this.aggregateRefList[0];
         row.percentage = false;
         row.percentageOption = StyleConstants.PERCENTAGE_NONE;
      }
      else {
         delete row.aggregateRef;
      }

      this.updateState();
   }

   percentageChange(percentageOption: number, row: Row) {
      row.percentageOption = percentageOption;
      row.percentage = percentageOption != StyleConstants.PERCENTAGE_NONE;
      this.updateState();
   }

   updateState(): void {
      this.updateModel();
      this.verify();
   }

   verify(): void {
      this.valid = true;

      outer: for(let i = 0; i < this.rows.length - 1; i++) {
         let baseRow = this.rows[i];

         for(let j = i + 1; j < this.rows.length - 1; j++) {
            let compareRow = this.rows[j];

            // Can't have same ref group && agg
            if(baseRow.isGroup !== compareRow.isGroup &&
               baseRow.selectedRef.name === compareRow.selectedRef.name)
            {
               this.valid = false;
            }
            // Can't have same exact group.
            else if(baseRow.selectedRef.name == compareRow.selectedRef.name &&
                    baseRow.isGroup && compareRow.isGroup &&
                    baseRow.dgroup == compareRow.dgroup)
            {
               this.valid = false;
            }

            if(!this.valid) {
               break outer;
            }
         }
      }

      this.validChange.emit(this.valid);
   }

   trapField(ref: ColumnRef): boolean {
      return this.trapFields.indexOf(ref) >= 0;
   }

   getNPLabel(aggregate: AggregateFormula): string {
      return AggregateFormula.getNPLabel(aggregate.formulaName);
   }

   isLastGroup(row: Row): boolean {
      let lastRow = row;

      for(let i = 0; i < this.rows.length; i++) {
         let r = this.rows[i];

         if(r.isGroup) {
            lastRow = r;
         }
      }

      return lastRow == row;
   }

   isTimeSeriesDisabled(row: Row): boolean {
      return !row.isGroup || !this.isLastGroup(row) || !row.dgroup ||
         !XSchema.isDateType(row.selectedRef.dataType) ||
         (row.dgroup & XConstants.PART_DATE_GROUP) != 0;
   }

   isByFormula(aggregate: AggregateFormula): boolean {
      return SummaryAttrUtil.isByFormula(aggregate.formulaName);
   }
}

interface Row {
   selectedRef: DataRef | null;
   isGroup: boolean | null;
   group?: string;
   dgroup?: number;
   aggregate?: AggregateFormula;
   aggregateRef?: DataRef;
   percentage: boolean | null;
   percentageOption: number;
   timeSeries?: boolean;
   n?: string;
}
