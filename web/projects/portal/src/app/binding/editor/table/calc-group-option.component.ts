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
import { HttpClient, HttpParams } from "@angular/common/http";
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Tool } from "../../../../../../shared/util/tool";
import { AggregateRef } from "../../../common/data/aggregate-ref";
import { DataRef } from "../../../common/data/data-ref";
import { CalcTableCell } from "../../../common/data/tablelayout/calc-table-cell";
import { CalcTableLayout } from "../../../common/data/tablelayout/calc-table-layout";
import { CalcTableRow } from "../../../common/data/tablelayout/calc-table-row";
import { VariableInfo } from "../../../common/data/variable-info";
import { XSchema } from "../../../common/data/xschema";
import { ComponentTool } from "../../../common/util/component-tool";
import { StyleConstants } from "../../../common/util/style-constants";
import { XConstants } from "../../../common/util/xconstants";
import { VariableInputDialogModel } from "../../../widget/dialog/variable-input-dialog/variable-input-dialog-model";
import { VariableInputDialog } from "../../../widget/dialog/variable-input-dialog/variable-input-dialog.component";
import { NamedGroupInfo } from "../../data/named-group-info";
import { CellBindingInfo } from "../../data/table/cell-binding-info";
import { OrderModel } from "../../data/table/order-model";
import { TopNModel } from "../../data/table/topn-model";
import { ValueLabelModel } from "../../data/value-label-model";
import { GetAvailableValuesEvent } from "../../event/get-available-values-event";
import { VSCalcTableEditorService } from "../../services/table/vs-calc-table-editor.service";
import { SummaryAttrUtil } from "../../util/summary-attr-util";
import { ManualOrderingDialog } from "../manual-ordering-dialog.component";
import { CalcNamedGroupDialog } from "./calc-named-group-dialog.component";
import { DateLevelExamplesService } from "../../../common/services/date-level-examples.service";

@Component({
   selector: "calc-group-option",
   templateUrl: "calc-group-option.component.html",
   styleUrls: ["calc-group-option.component.scss"]
})
export class CalcGroupOption implements OnInit, OnChanges {
   @Input() cellBinding: CellBindingInfo;
   @Input() field: DataRef;
   @Input() runtimeId: string;
   @Input() aggregates: () => AggregateRef[];
   @Input() sourceName: string;
   @Input() assemblyName: string;
   @Input() variableValues: string[];
   @Output() apply = new EventEmitter<boolean | "apply">();
   @Output() dialogOpen = new EventEmitter<boolean>();
   @ViewChild("namedGroupDialog") CalcNamedGroupDialog: TemplateRef<any>;
   SORT_OPTIONS: any[] = [
      {label: "_#(js:None)", value: StyleConstants.SORT_NONE},
      {label: "_#(js:Ascending)", value: StyleConstants.SORT_ASC},
      {label: "_#(js:Descending)", value: StyleConstants.SORT_DESC }];
   AGGR_SORT_OPTIONS: any[] = [
      {label: "_#(js:None)", value: StyleConstants.SORT_NONE},
      {label: "_#(js:Ascending)", value: StyleConstants.SORT_ASC},
      {label: "_#(js:Descending)", value: StyleConstants.SORT_DESC},
      {label: "_#(js:common.widget.SortOption.byAsc)", value: StyleConstants.SORT_VALUE_ASC},
      {label: "_#(js:common.widget.SortOption.byDesc)", value: StyleConstants.SORT_VALUE_DESC}
   ];
   TOP_OPTION: any[] = [
      {label: "_#(js:None)", value: StyleConstants.NONE},
      {label: "_#(js:Top)", value: StyleConstants.TOP_N},
      {label: "_#(js:Bottom)", value: StyleConstants.BOTTOM_N}
   ];
   DATE_LEVEL_OPTIONS: any[] = [
      {label: "_#(js:Year)", value: XConstants.YEAR_DATE_GROUP},
      {label: "_#(js:Quarter)", value: XConstants.QUARTER_DATE_GROUP},
      {label: "_#(js:Month)", value: XConstants.MONTH_DATE_GROUP},
      {label: "_#(js:Week)", value: XConstants.WEEK_DATE_GROUP},
      {label: "_#(js:Day)", value: XConstants.DAY_DATE_GROUP},
      {label: "_#(js:Quarter of Year)", value: XConstants.QUARTER_OF_YEAR_DATE_GROUP},
      {label: "_#(js:Month of Year)", value: XConstants.MONTH_OF_YEAR_DATE_GROUP},
      {label: "_#(js:Week of Year)", value: XConstants.WEEK_OF_YEAR_DATE_GROUP},
      {label: "_#(js:Day of Month)", value: XConstants.DAY_OF_MONTH_DATE_GROUP},
      {label: "_#(js:Day of Week)", value: XConstants.DAY_OF_WEEK_DATE_GROUP},
      {label: "_#(js:None)", value: XConstants.NONE_DATE_GROUP}
   ];
   TIME_LEVEL_OPTIONS: any[] = [
      {label: "_#(js:Hour)", value: XConstants.HOUR_DATE_GROUP},
      {label: "_#(js:Minute)", value: XConstants.MINUTE_DATE_GROUP},
      {label: "_#(js:Second)", value: XConstants.SECOND_DATE_GROUP},
      {label: "_#(js:Hour of Day)", value: XConstants.HOUR_OF_DAY_DATE_GROUP},
      {label: "_#(js:Minute of Hour)", value: XConstants.MINUTE_OF_HOUR_DATE_GROUP},
      {label: "_#(js:Second of Minute)", value: XConstants.SECOND_OF_MINUTE_DATE_GROUP},
      {label: "_#(js:None)", value: XConstants.NONE_DATE_GROUP}
   ];
   TIME_INSTANT_LEVEL_OPTIONS: any[] = [
      {label: "_#(js:Year)", value: XConstants.YEAR_DATE_GROUP},
      {label: "_#(js:Quarter)", value: XConstants.QUARTER_DATE_GROUP},
      {label: "_#(js:Month)", value: XConstants.MONTH_DATE_GROUP},
      {label: "_#(js:Week)", value: XConstants.WEEK_DATE_GROUP},
      {label: "_#(js:Day)", value: XConstants.DAY_DATE_GROUP},
      {label: "_#(js:Hour)", value: XConstants.HOUR_DATE_GROUP},
      {label: "_#(js:Minute)", value: XConstants.MINUTE_DATE_GROUP},
      {label: "_#(js:Second)", value: XConstants.SECOND_DATE_GROUP},
      {label: "_#(js:Quarter of Year)", value: XConstants.QUARTER_OF_YEAR_DATE_GROUP},
      {label: "_#(js:Month of Year)", value: XConstants.MONTH_OF_YEAR_DATE_GROUP},
      {label: "_#(js:Week of Year)", value: XConstants.WEEK_OF_YEAR_DATE_GROUP},
      {label: "_#(js:Day of Month)", value: XConstants.DAY_OF_MONTH_DATE_GROUP},
      {label: "_#(js:Day of Week)", value: XConstants.DAY_OF_WEEK_DATE_GROUP},
      {label: "_#(js:Hour of Day)", value: XConstants.HOUR_OF_DAY_DATE_GROUP},
      {label: "_#(js:Minute of Hour)", value: XConstants.MINUTE_OF_HOUR_DATE_GROUP},
      {label: "_#(js:Second of Minute)", value: XConstants.SECOND_OF_MINUTE_DATE_GROUP},
      {label: "_#(js:None)", value: XConstants.NONE_DATE_GROUP}
   ];
   aggrs: AggregateRef[] = new Array<AggregateRef>();
   ngNames: any;
   dateLevelOpts: any[];
   dateLevelExamples: string[];

   /**
    * Cell binding: summary binding in a cell.
    */
   get SUMMARY(): number {
      return 3;
   }

   public constructor(private editorService: VSCalcTableEditorService,
                      private examplesService: DateLevelExamplesService,
                      private dialogService: NgbModal,
                      private httpClient: HttpClient)
   {
   }

   ngOnInit(): void {
      this.ngNames = this.getNgNames();
      this.dateLevelOpts = this.getDateLevelOpts();

      if(this.isDateType()) {
         this.examplesService.loadDateLevelExamples(this.dateLevelOpts.map((opt) => opt.value), this.field.dataType)
            .subscribe((data: any) => this.dateLevelExamples = data.dateLevelExamples);
      }

      // sort by value no longer valid, default to none
      if(!this.sorts.some(s => s.value == this.order.type)) {
         this.order.type = StyleConstants.SORT_NONE;
      }
   }

   ngOnChanges(changes: SimpleChanges) {
      this.aggrs = this.aggregates();

      if(changes.field) {
         const manualOrder = {label: "_#(js:Manual)", value: StyleConstants.SORT_SPECIFIC};
         this.SORT_OPTIONS.push(manualOrder);
         this.AGGR_SORT_OPTIONS.push(manualOrder);
      }
   }

   isDateIntervalDisabled(): boolean {
      return this.order.option > 9 || this.isDatePart() || this.order.option == 0;
   }

   public openManualDialog(): void {
      this.getVariables((vars) => {
         const url = "../api/vsdata/availableValues";
         const selected = this.tableModel.selectedRect;
         const params = new HttpParams().append("vsId", this.runtimeId)
            .append("row", selected.y.toString())
            .append("col", selected.x.toString())
            .append("assemblyName", this.assemblyName)
            .append("dateLevel", this.order.option);
         const event = new GetAvailableValuesEvent(this.field, vars);

         this.httpClient.put<any>(url, event, {params})
            .subscribe(res => {
               const dialog = ComponentTool.showDialog(this.dialogService, ManualOrderingDialog,
                  (result: string[]) => this.cellBinding.order.manualOrder = result);
               dialog.manualOrders = Tool.clone(this.getSortedManualOrders(res.list));
               dialog.helpLinkKey = "CreatingFreehandSortandRank";
               dialog.valueLabelList = res.list;
            });
      });
   }

   private getVariables(onFinish: (vars: VariableInfo[]) => void): void {
      const params = new HttpParams().append("vsId", this.runtimeId)
         .append("assemblyName", this.assemblyName);

      // check if variables need to be set before getting the manual order list
      this.httpClient.get<VariableInfo[]>("../api/vsdata/check-variables", {params})
         .subscribe(vars => {
            if(!vars || vars.length == 0) {
               onFinish(null);
            }
            else {
               const dialog = ComponentTool.showDialog(this.dialogService, VariableInputDialog,
                  (model: VariableInputDialogModel) => {
                     onFinish(model.varInfos);
                  });
               dialog.model = <VariableInputDialogModel>{varInfos: vars};
            }
         });
   }

   getSortedManualOrders(valueLabelList: ValueLabelModel[]): string[] {
      if(!this.cellBinding?.order?.manualOrder) {
         return valueLabelList.map((valLabel) => valLabel.value);
      }

      const existingList = this.cellBinding.order.manualOrder
          .map(value => value == null ? "" : value)
         .filter(value => valueLabelList.find((valLabel) => (!valLabel.value && (!value || value == "")) ||valLabel.value == value) != null);

      return valueLabelList.sort((a, b) => {
         const i1 = existingList.indexOf(!a.value ? "" : a.value);
         const i2 = existingList.indexOf(!b.value ? "" : b.value);
         return i1 - i2;
      }).map((valLabel) => valLabel.value || "");
   }

   getNgNames() {
      let custom: NamedGroupInfo = <NamedGroupInfo> {
         name: "Custom",
         type: NamedGroupInfo.EXPERT_NAMEDGROUP_INFO,
         groups: [],
         conditions: []
      };

      let names = [
         {
         label: "", name: "", value: null
      },
         {
         label: "_#(js:Customize)", name: "Custom", value: custom
      }];

      if(this.editorService == null || this.editorService.namedGroups == null) {
         return names;
      }

      let groups = this.editorService.namedGroups;

      for(let i = 0; i < groups.length; i++) {
         let ng: NamedGroupInfo = <NamedGroupInfo> {
            name: groups[i],
            type: NamedGroupInfo.ASSET_NAMEDGROUP_INFO
         };

         names.push({
            label: groups[i], name: groups[i], value: ng
         });
      }

      return names;
   }

   changeNamedGroup(label: string) {
      this.getNgNames().forEach((name) => {
         if(name.name === label) {
            this.order.info = name.value;
         }
      });
   }

   isDisabledNamedGroup(): boolean {
      if(this.editorService && this.editorService.namedGroups &&
         this.order.info && this.order.info.name && this.order.info.name != "null")
      {
         let groups = this.editorService.namedGroups;

         for(let i: number = 0; i < groups.length ; i++) {
            if(this.order.info.name == groups[i]) {
               return true;
            }
         }
      }

      return !this.order.info || !this.order.info.name || this.order.info.name == "null";
   }

   get sorts(): any[] {
      return this.aggrs.length > 0 ? this.AGGR_SORT_OPTIONS : this.SORT_OPTIONS;
   }

   openNamedGroupEdit() {
      this.dialogOpen.emit(true);
      let dialog: CalcNamedGroupDialog = ComponentTool.showDialog(
         this.dialogService, CalcNamedGroupDialog, (result: OrderModel) =>
            {
               this.dialogOpen.emit(false);
               this.order.info = result.info;
               this.order.others = result.others;
               this.order.info.type = this.order.info.conditions.length > 0 ?
                  (this.order.info.name == "Custom" ?
                   NamedGroupInfo.EXPERT_NAMEDGROUP_INFO :
                   NamedGroupInfo.ASSET_NAMEDGROUP_INFO) : this.order.info.type;
               // submit change so manual order dialog would pick up correct values
               this.apply.emit("apply");
            },
         {windowClass: "condition-dialog"},
         () => {
            this.dialogOpen.emit(false);
         }
      );

      dialog.order = this.order;
      dialog.runtimeId = this.runtimeId;
      dialog.field = this.field;
      dialog.table = this.sourceName;
      dialog.assemblyName = this.assemblyName;
      dialog.variableValues = this.variableValues;
   }

   changeTopnType(evt: any) {
      let topType = <number>evt;

      if(this.topN.type != StyleConstants.NONE) {
         this.topN.topn = 3;

         if(this.aggrs.length != 0) {
            if(this.topN.sumCol == -1) {
               this.topN.sumCol = 0;
            }

            this.order.type = this.topN.type == StyleConstants.TOP_N ?
               StyleConstants.SORT_VALUE_DESC : StyleConstants.SORT_VALUE_ASC;
            this.order.sortValue = this.aggrs[0]?.name;
         }
      }
   }

   isDateType() {
      if(this.field == null) {
         return false;
      }

      let dataType = this.field.dataType;
      return dataType == XSchema.DATE || dataType == XSchema.TIME_INSTANT ||
         dataType == XSchema.TIME;
   }

   isDatePart() {
      return (this.order.option & XConstants.PART_DATE_GROUP) != 0;
   }

   getDateLevelOpts(): any {
      switch(this.field.dataType) {
      case XSchema.DATE:
         return this.DATE_LEVEL_OPTIONS;
      case XSchema.TIME_INSTANT:
         return this.TIME_INSTANT_LEVEL_OPTIONS;
      case XSchema.TIME:
         return this.TIME_LEVEL_OPTIONS;
      }
   }

   get tableModel(): CalcTableLayout {
      return this.editorService.getTableLayout();
   }

   isDisableRanking(): boolean {
      if(this.tableModel == null || this.tableModel.tableRows.length == 0) {
         return true;
      }

      let tableRows: CalcTableRow[] = this.tableModel.tableRows;

      for(let i = 0; i < tableRows.length; i++) {
         for(let j = 0; j < tableRows[i].tableCells.length; j++) {
            let cell: CalcTableCell = tableRows[i].tableCells[j];

            if(cell != null && cell.bindingType == this.SUMMARY &&
               this.aggrs != null && this.aggrs.length > 0)
            {
               return false;
            }
         }
      }

      return true;
   }

   get sortValue(): string {
      if(this.order.sortCol >= 0 && this.aggrs.length > this.order.sortCol) {
         this.order.sortValue = this.aggrs[this.order.sortCol].view;
      }
      else if(this.aggrs.length > 0) {
         this.order.sortValue = this.aggrs[0].view;
         this.order.sortCol = 0;
      }
      else {
         this.order.sortValue = null;
         this.order.sortCol = -1;
      }

      return this.order.sortValue;
   }

   set sortValue(colValue: string) {
      for(let i = 0; i < this.aggrs.length; i++) {
         if(colValue == this.aggrs[i].view) {
            this.order.sortCol = i;
         }
      }
   }

   get sumColValue(): string {
      if(this.topN.sumCol != -1 && this.topN.sumCol < this.aggrs.length) {
         return this.aggrs[this.topN.sumCol].view;
      }

      return null;
   }

   set sumColValue(value: string) {
      for(let i = 0;  i < this.aggrs.length; i++) {
         if(this.aggrs[i].view == value) {
            this.topN.sumCol = i;
         }
      }
   }

   get order(): OrderModel {
      return this.cellBinding.order;
   }

   get topN(): TopNModel {
      return this.cellBinding.topn;
   }

   changeTimeSeries() {
      if(this.cellBinding.timeSeries) {
         this.order.type = StyleConstants.SORT_ASC;
      }
   }

   isValidN(): boolean {
      return !this.isRankingColEnabled() || Tool.isDynamic(this.topN.topn + "") ||
         this.topN.topn > 0;
   }

   /*
   * Check if the ranking column option can be used
   *
   * @returns {boolean} whether the ranking oder is not null.
   **/
   isRankingColEnabled(): boolean {
      return this.topN.type != StyleConstants.SORT_NONE;
   }

   getMaxForLevel(): number {
      return SummaryAttrUtil.getMaxForLevel(this.order.option);
   }

   sortItemVisible(sort: any): boolean {
      if(sort == null || sort.value != StyleConstants.SORT_SPECIFIC) {
         return true;
      }

      return !this.order || !this.isDateType() || this.isDateType() && this.order.option != XConstants.NONE_DATE_GROUP;
   }

   levelChanged(opt: number): void {
      this.order.option = opt;
      let orderInfo = this.order;

      if(orderInfo && orderInfo.option == XConstants.NONE_DATE_GROUP &&
         orderInfo.type == StyleConstants.SORT_SPECIFIC)
      {
         orderInfo.type = StyleConstants.SORT_NONE;
      }
   }
}
