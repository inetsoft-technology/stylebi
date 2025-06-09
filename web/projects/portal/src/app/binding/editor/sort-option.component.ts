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
import {
   Component,
   EventEmitter,
   Input,
   OnInit,
   Output,
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { DataRef } from "../../common/data/data-ref";
import { DataRefType } from "../../common/data/data-ref-type";
import { VariableInfo } from "../../common/data/variable-info";
import { XSchema } from "../../common/data/xschema";
import { UIContextService } from "../../common/services/ui-context.service";
import { StyleConstants } from "../../common/util/style-constants";
import { Tool } from "../../../../../shared/util/tool";
import { VariableInputDialogModel } from "../../widget/dialog/variable-input-dialog/variable-input-dialog-model";
import { VariableInputDialog } from "../../widget/dialog/variable-input-dialog/variable-input-dialog.component";
import { ValueMode } from "../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { ModelService } from "../../widget/services/model.service";
import { BDimensionRef } from "../data/b-dimension-ref";
import { GroupCondition, NamedGroupInfo } from "../data/named-group-info";
import { ValueLabelModel } from "../data/value-label-model";
import { GetAvailableValuesEvent } from "../event/get-available-values-event";
import { BindingService } from "../services/binding.service";
import { ManualOrderingDialog } from "./manual-ordering-dialog.component";
import { ComponentTool } from "../../common/util/component-tool";
import { XConstants } from "../../common/util/xconstants";
import { SourceInfo } from "../data/source-info";
import {
   FeatureFlagsService,
} from "../../../../../shared/feature-flags/feature-flags.service";

const GENERAL_SORT_OPTIONS: any[] = [
   {label: "_#(js:None)", value: StyleConstants.SORT_NONE},
   {label: "_#(js:Ascending)", value: StyleConstants.SORT_ASC},
   {label: "_#(js:Descending)", value: StyleConstants.SORT_DESC}];
const VALUE_SORT_OPTIONS: any[] = [
   {
      label: "_#(js:common.widget.SortOption.byAsc)",
      value: StyleConstants.SORT_VALUE_ASC
   },
   {
      label: "_#(js:common.widget.SortOption.byDesc)",
      value: StyleConstants.SORT_VALUE_DESC
   }];
const RANKING_OPTIONS: any[] = [
   {label: "_#(js:None)", value: StyleConstants.NONE + ""},
   {label: "_#(js:Top)", value: StyleConstants.TOP_N + ""},
   {label: "_#(js:Bottom)", value: StyleConstants.BOTTOM_N + ""}];

@Component({
   selector: "sort-option",
   templateUrl: "sort-option.component.html",
   styleUrls: ["sort-option.component.scss"]
})
export class SortOption implements OnInit {
   @Input() set dimension(dim: BDimensionRef) {
      this._dimension = dim;
      this.sortOrders = this.getSortOrders();
   }

   get dimension() {
      return this._dimension;
   }

   @Input() elemType: string;
   @Input() fieldType: string;
   @Input() dragIndex: number;
   @Input() vsId: any;
   @Input() variables: any;
   @Input() grayedOutValues: string[] = [];
   @Input() isOtherSupported: boolean = true;
   @Input() sortSupported: boolean = true;
   @Input() rankingSupported: boolean = true;
   @Input() isOuterDimRef: boolean;
   @Input() source: SourceInfo;
   @Output() dialogOpen = new EventEmitter<boolean>();

   _dimension: BDimensionRef;
   groups: Object[] = [];
   valueLabelList: ValueLabelModel[];
   manualOrders: string[];
   sortOrders: any[];
   rankingOptions: any[];
   public ValueMode = ValueMode;
   aggregates: any[] = [];
   private _timeSeries: boolean;
   currentOrder: any;

   public constructor(private bindingService: BindingService,
                      private modelService: ModelService,
                      private modalService: NgbModal,
                      private uiContextService: UIContextService,
                      private featureFlagsService: FeatureFlagsService)
   {
   }

   ngOnInit(): void {
      this.initAggrs();
      this.sortOrders = this.getSortOrders();
      this.rankingOptions = RANKING_OPTIONS;
      this.fixSortOrder();
      this.fixSortByCol();
      this.fixRankingCol();

      this.dimension.specificOrderType = this.isSpecificSortOrder() ? "manual" : null;
      this.currentOrder = this.getCurrentOrder();
   }

   @Input() set timeSeries(timeSeries: boolean) {
      this._timeSeries = timeSeries;
   }

   get timeSeries(): boolean {
      return this._timeSeries;
   }

   initAggrs() {
      if(this.dimension && this.dimension.sortOptionModel) {
         this.aggregates = this.dimension.sortOptionModel.aggregateRefs;
      }
   }

   /**
    * Fix the sort oder, if it's depends value and the aggregates has been removed all, set
    * it with the none order.
    */
   private fixSortOrder(): void {
      if(this.isEmptyAggregate() && this.isSortByColVisible()) {
         let order = StyleConstants.SORT_ASC;
         this.dimension.order = order;
      }
   }

   /**
    * Fix the name of sort aggregate value if the aggregate has been changed. Just use the
    * first aggregate name.
    */
   private fixSortByCol(): void {
      // init sortByCol to avoid is null or dirty value.
      if(this.isSortByColVisible() &&
         !Tool.isDynamic(this.dimension.sortByCol) &&
         this.getAggregateIndex(this.dimension.sortByCol) < 0)
      {
         this.dimension.sortByCol = this.aggregates.length > 0 ?
            this.aggregates[0].value : null;
      }
   }

   /**
    * Fix the ranking column value if the aggregate has been changed. Just use the
    * first aggregate name. Only when the value is not dynamic we need do this.
    */
   private fixRankingCol() {
      if(this.isRankingColEnabled() &&
         !Tool.isDynamic(this.dimension.rankingCol) &&
         this.getAggregateIndex(this.dimension.rankingCol) < 0)
      {
         let found = false;

         if(!!this.dimension.rankingCol && this.aggregates && this.aggregates.length > 0) {
            for(let agg of this.aggregates) {
               if(!!agg.value && agg.value.indexOf("(" + this.dimension.rankingCol + ")") >= 0) {
                  this.dimension.rankingCol = agg.value;
                  found = true;
                  break;
               }
            }
         }

         if(!found) {
            if(this.aggregates && this.aggregates.length > 0) {
               this.dimension.rankingCol = this.aggregates[0].value;
               return;
            }

            this.dimension.rankingCol = null;
            this.dimension.rankingOption = "" + StyleConstants.SORT_NONE;
         }
      }
   }

   /*
   * Check if the sort value option can be used
   *
   * @returns {boolean} whether the sort oder depends on value.
   **/
   isSortByColVisible(): boolean {
      let order = this.dimension.order;

      if(this.isSpecificSortOrder()) {
         order -= StyleConstants.SORT_SPECIFIC;
      }

      return order == StyleConstants.SORT_VALUE_ASC || order == StyleConstants.SORT_VALUE_DESC;
   }


   /*
   * Check if the sort order option can be used
   *
   * @returns {boolean} whether the dimension is date type or dynamic.
   **/
   isSortEnabled(): boolean {
      if(this.timeSeries && XSchema.isDateType(this.dimension.dataType)) {
         return false;
      }

      return (!this.timeSeries || Tool.isDynamic(this.dimension.columnValue)) &&
         this.sortSupported;
   }

   /*
   * Check if the ranking column option can be used
   *
   * @returns {boolean} whether the ranking oder is not null.
   **/
   isRankingColEnabled(): boolean {
      return this.isRankingEnable() && !!this.dimension.rankingOption &&
         this.dimension.rankingOption != StyleConstants.SORT_NONE + "";
   }

   isSpecificSortOrder(): boolean {
      return this.dimension && (this.dimension.order & StyleConstants.SORT_SPECIFIC) != 0;
   }

   isRankingEnable() {
      return this.rankingSupported && !this.isEmptyAggregate();
   }

   isEmptyAggregate(): boolean {
      return !this.aggregates || this.aggregates.length == 0;
   }

   /**
    * Create sort orders depend on the aggregates and the dimension type.
    *
    * returns {any[]} the list contains the base orders and the value orders if aggregates
    *                 is not empty and the specifcal order if supported.
    */
   public getSortOrders(): any[] {
      let sorts: any[] = GENERAL_SORT_OPTIONS;
      sorts = sorts.concat(!this.isEmptyAggregate() ? VALUE_SORT_OPTIONS : []);

      if(this.isSpecificalSortSupport()) {
         sorts.push({label: "_#(js:Manual)", value: "manual"});
      }

      return sorts;
   }

   /**
    * Check if the specifical sort order can be supported.
    */
   isSpecificalSortSupport(namedgroup = false): boolean {
      const model = this.bindingService.bindingModel;

      if(namedgroup || !namedgroup && this.isDateType() &&
         (this.dimension.dateLevel == (XConstants.NONE_DATE_GROUP + "") ||
            this.dimension.dateInterval > 1))
      {
         return false;
      }

      return true;
   }

   /**
    * Check if the dimension model is date type
    *
    * @returns {boolean} whether the dimension is date type.
    */
   private isDateType(): boolean {
      if(this.dimension == null) {
         return false;
      }

      let dataType = this.dimension.dataType;

      return dataType == XSchema.DATE || dataType == XSchema.TIME_INSTANT ||
         dataType == XSchema.TIME;
   }

   changeOrderType(order: any) {
      let val = null;

      if(order == "manual") {
         val = StyleConstants.SORT_SPECIFIC;

         if(order == "manual") {
            this.dimension.specificOrderType = "manual";
         }
         else if(this.dimension.specificOrderType == "manual") {
            this.dimension.specificOrderType = null;
         }
      }
      else {
         val = typeof order == "string" ? parseInt(order, 10) : order;
      }

      this.currentOrder = order;
      this.dimension.order = val;
      this.dimension.manualOrder = val == StyleConstants.SORT_SPECIFIC ? this.dimension.manualOrder : null;
      this.dimension.sortByCol = (val & StyleConstants.SORT_VALUE_ASC) == StyleConstants.SORT_VALUE_ASC ||
         (val & StyleConstants.SORT_VALUE_DESC) == StyleConstants.SORT_VALUE_DESC ?
         (!this.dimension.sortByCol || parseInt(this.dimension.sortByCol, 10) == -1 ?
            this.aggregates[0].value : this.dimension.sortByCol) : null;

      this.dimension.timeSeries = this.timeSeries ? this.dimension.timeSeries : false;
   }

   get rankingOption(): string {
      return this.dimension && !!this.dimension.rankingOption ?
         this.dimension.rankingOption : StyleConstants.NONE + "";
   }

   changeRankingOption(val: string) {
      this.dimension.rankingOption = val == StyleConstants.NONE + "" ? null : val;
      let rankingNone: boolean = !this.isRankingColEnabled();
      this.dimension.rankingN = !this.dimension.rankingN ? "3" : this.dimension.rankingN;
      this.dimension.rankingCol = !this.dimension.rankingCol && this.aggregates.length > 0 || !this.isAggregateExist(this.dimension.rankingCol) ?
         this.aggregates[0].value : this.dimension.rankingCol;

      if(val == StyleConstants.TOP_N + "" && !this.dimension.timeSeries) {
         this.changeOrderType(StyleConstants.SORT_VALUE_DESC);
         this.dimension.sortByCol = this.dimension.rankingCol;
      }
      else if(val == StyleConstants.BOTTOM_N + "" && !this.dimension.timeSeries) {
         this.changeOrderType(StyleConstants.SORT_VALUE_ASC);
         this.dimension.sortByCol = this.dimension.rankingCol;
      }
   }

   private isAggregateExist(aggr: string): boolean {
      if(!this.aggregates || this.aggregates.length == 0) {
         return false;
      }

      return this.aggregates.find(item => item.value == aggr);
   }

   changeRankingN(val: string) {
      if(!isNaN(parseFloat(val))) {
         this.dimension.rankingN = parseFloat(val).toFixed(0);
      }
      else {
         this.dimension.rankingN = val;
      }
   }

   private getAggregateIndex(name: string): number {
      if(!name || this.isEmptyAggregate()) {
         return -1;
      }

      for(let i = 0; i < this.aggregates.length; i++) {
         if(name == this.aggregates[i].value) {
            return i;
         }
      }

      return -1;
   }

   showEdit(): boolean {
      if(this.dimension && this.timeSeries && Tool.isDate(this.dimension.dataType)) {
         return false;
      }
      else {
         return this.isSpecificSortOrder() && this.isSpecificalSortSupport() &&
            this.currentOrder == "manual";
      }
   }

   openDialog() {
      this.getVariables((vars) => {
         let url: string = "../api/vsdata/availableValues";
         let dref: DataRef  = this.dimension;

         const event = new GetAvailableValuesEvent(dref, vars);
         this.modelService.putModel<any>(url, event, this.bindingService.getURLParams())
            .subscribe(res => {
               this.valueLabelList = <ValueLabelModel[]> res.body.list;
               this.valueLabelList = this.valueLabelList == null ? [] : this.valueLabelList;
               let existingList = null;

               if(this.valueLabelList.length > 5001) {
                  this.valueLabelList = this.valueLabelList.slice(0, 5001);
               }

               if(this.dimension.manualOrder) {
                  existingList = this.dimension.manualOrder
                     .map(v => v || "")
                     .filter(value => this.valueLabelList.find((valLabel) => this.isSameValue(valLabel.value, value)) != null);
               }
               else if(this.dimension.namedGroupInfo?.groups) {
                  existingList = this.dimension.namedGroupInfo.groups
                     .map(v => v.name)
                     .filter(value => this.valueLabelList.find((valLabel) => valLabel.value == value) != null);
               }

               if(existingList == null || existingList.length == 0) {
                  this.manualOrders = this.valueLabelList.map((valLabel) => valLabel.value).map(v => v || "").concat([]);
               }
               else {
                  this.manualOrders = existingList;
                  this.manualOrders = this.manualOrders.concat(
                     this.valueLabelList.filter(valLabel => {
                        return this.manualOrders.find((val) => this.isSameValue(valLabel.value, val)) == null;
                     }).map((valLabel) => valLabel.value || ""));
               }

               this.openManualOrderingDialog();
            });
      });
   }

   private isSameValue(val1: any, val2: any): boolean {
      let isNull1 = !val1 || val1 == "";
      let isNull2 = !val2 || val2 == "";

      return isNull1 && isNull2 || val1 == val2;
   }

   private getVariables(onFinish: (vars: VariableInfo[]) => void): void {
      let url: string = "../api/vsdata/check-variables";

      // check if variables need to be set before getting the manual order list
      this.modelService.getModel<VariableInfo[]>(url,
         this.bindingService.getURLParams())
         .subscribe(vars => {
            if(!vars || vars.length == 0) {
               onFinish(null);
            }
            else {
               const dialog = ComponentTool.showDialog(this.modalService, VariableInputDialog,
                  (model: VariableInputDialogModel) => {
                     onFinish(model.varInfos);
                  });
               dialog.model = <VariableInputDialogModel>{varInfos: vars};
            }
         });
   }

   private openManualOrderingDialog(): void {
      const dialog = ComponentTool.showDialog(this.modalService, ManualOrderingDialog,
         (result: string[]) => {
            if(!!result) {
               result = result.map(v => !!v ? v + "" : "");
            }

            this.dimension.manualOrder = result;

            this.dimension.customNamedGroupInfo = new NamedGroupInfo();
            // use expert namedgroup according to ReportDimensionRef.createComparator function.
            this.dimension.customNamedGroupInfo.type = NamedGroupInfo.EXPERT_NAMEDGROUP_INFO;
            this.dimension.customNamedGroupInfo.groups = result.map(v => {
               const group = new GroupCondition();
               group.name = v;
               group.value = [v];
               return group;
            });
         }
      );

      dialog.manualOrders = Tool.clone(this.manualOrders);
      dialog.valueLabelList = this.valueLabelList;
      dialog.helpLinkKey = "ManualOrdering";
   }

   isValidN(): boolean {
      return !this.isRankingColEnabled() || Tool.isDynamic(this.dimension.rankingN) ||
         parseInt(this.dimension.rankingN, 10) > 0;
   }

   isOtherEnabled(): boolean {
      return this.isRankingColEnabled() && !this.isEmptyAggregate() && this.isOtherSupported &&
         (this.dimension.refType & DataRefType.CUBE) != DataRefType.CUBE;
   }

   get assemblyName(): string {
      return this.bindingService.assemblyName;
   }

   getCurrentOrder(): any {
      let order = this.dimension.order;
      let manualOrders = this.dimension?.manualOrder;

      if(this.isSpecificalSortSupport() && !!manualOrders && manualOrders.length > 0) {
         if(!this.isSpecificSortOrder()) {
            this.dimension.order = StyleConstants.SORT_SPECIFIC;
         }

         return "manual";
      }

      if(this.dimension.order == StyleConstants.SORT_SPECIFIC &&
         this.dimension.specificOrderType == "manual" && this.isSpecificalSortSupport())
      {
         return "manual";
      }

      if(this.sortOrders.map(a => a.value).includes(order)) {
         return order;
      }

      return this.sortOrders[0].value;
   }

   sortItemVisible(sort: any): boolean {
      if(sort == null || sort.value != "manual") {
         return true;
      }

      return !this.dimension || !this.isDateType() ||
         this.isDateType() && this.dimension.dateLevel != (XConstants.NONE_DATE_GROUP + "");
   }

   trackByFn(index, item): number {
      return index;
   }
}
