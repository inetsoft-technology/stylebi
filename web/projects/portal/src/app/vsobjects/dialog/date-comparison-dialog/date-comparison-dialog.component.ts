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
import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from "@angular/core";
import { ScriptPaneTreeModel } from "../../../widget/dialog/script-pane/script-pane-tree-model";
import { ModelService } from "../../../widget/services/model.service";
import { DateComparisonPaneModel } from "../../model/date-comparison-pane-model";
import { DateComparisonDialogModel } from "../../model/date-comparison-dialog-model";
import { DateComparisonService } from "../../util/date-comparison.service";
import { DateComparisonPaneComponent } from "./date-comparison-pane.component";
import { VSObjectType } from "../../../common/data/vs-object-type";
import { CommonKVModel } from "../../../common/data/common-kv-model";
import { ValueTypes } from "../../model/dynamic-value-model";
import { IntervalLevel } from "../../model/interval-pane-model";
import { XConstants } from "../../../common/util/xconstants";

const DATE_COMPARISON_URI: string = "composer/vs/date-comparison-model";

@Component({
   selector: "date-comparison-dialog",
   templateUrl: "./date-comparison-dialog.component.html",
   styleUrls: ["./date-comparison-dialog.component.scss"]
})
export class DateComparisonDialog implements OnInit {
   @Input() dateComparisonDialogModel: DateComparisonDialogModel;
   @Input() scriptTreeModel: ScriptPaneTreeModel;
   @Input() variableValues: string[] = [];
   @Input() runtimeId: string;
   @Input() assemblyName: string;
   @Input() assemblyType: VSObjectType;
   @Output() onCommit = new EventEmitter<DateComparisonDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @Output() onApply = new EventEmitter<{collapse: boolean, result: any}>();
   @Output() onClear = new EventEmitter<any>();
   @ViewChild(DateComparisonPaneComponent) dateComparisonPane: DateComparisonPaneComponent;

   isShareDateComparison: boolean;
   shareAssemblyType: number;

   constructor(private dateComparisonService: DateComparisonService,
               private modelService: ModelService)
   {
   }

   ngOnInit() {
      this.isShareDateComparison = !!this.dateComparisonDialogModel.shareFromAssembly;
      this.updateShareAssemblyType();
   }

   get isSharePaneVisible(): boolean {
      return this.dateComparisonDialogModel.shareFromAvailableAssemblies.length > 0;
   }

   get shareFromAvailableNames() {
      return !!this.dateComparisonDialogModel.shareFromAvailableAssemblies
         ? this.dateComparisonDialogModel.shareFromAvailableAssemblies.map(obj => obj.key) : [];
   }

   get toDateDisabled(): boolean {
      let dateComparisonPaneModel = this.dateComparisonDialogModel?.dateComparisonPaneModel;
      // year-to-date is already from beginning of year to now, the option
      // on the period pane is irrelavent so we just gray it out to avoid confusion.
      if(dateComparisonPaneModel.periodPaneModel.custom ||
         dateComparisonPaneModel?.periodPaneModel?.standardPeriodPaneModel?.dateLevel?.type != ValueTypes.VALUE ||
         dateComparisonPaneModel.intervalPaneModel.level.type != ValueTypes.VALUE ||
         dateComparisonPaneModel.intervalPaneModel.contextLevel.type != ValueTypes.VALUE)
      {
         return false;
      }

      let periodLevel = dateComparisonPaneModel?.periodPaneModel?.standardPeriodPaneModel?.dateLevel.value + "";
      let contextLevel = dateComparisonPaneModel.intervalPaneModel.contextLevel.value + "";
      let intervalLevel = this.convertIntervalLevel(dateComparisonPaneModel.intervalPaneModel.level.value) + "";

      return periodLevel == intervalLevel && intervalLevel == contextLevel;
   }

   get intervalEndDate(): any {
      const interval = this.dateComparisonDialogModel?.dateComparisonPaneModel?.intervalPaneModel;
      return interval && !interval.endDayAsToDate && interval.intervalEndDate
         ? interval.intervalEndDate.value : null;
   }

   private convertIntervalLevel(level: number): number {
      switch(level + "") {
         case "" + IntervalLevel.YEAR:
         case "" + IntervalLevel.YEAR_TO_DATE:
            return XConstants.YEAR_DATE_GROUP;
         case "" + IntervalLevel.QUARTER:
         case "" + IntervalLevel.QUARTER_TO_DATE:
            return XConstants.QUARTER_DATE_GROUP;
         case "" + IntervalLevel.MONTH:
         case "" + IntervalLevel.MONTH_TO_DATE:
            return XConstants.MONTH_DATE_GROUP;
         case "" + IntervalLevel.WEEK:
         case "" + IntervalLevel.WEEK_TO_DATE:
            return XConstants.WEEK_DATE_GROUP;
         case "" + IntervalLevel.DAY:
            return XConstants.DAY_DATE_GROUP;
         default:
            return -1;
      }
   }

   updateShareStatus(share: boolean) {
      this.isShareDateComparison = share;

      if(this.isShareDateComparison && !!this.dateComparisonDialogModel.shareFromAssembly) {
         this.updateShareFrom(this.dateComparisonDialogModel.shareFromAssembly);
      }
   }

   updateShareFrom(from: string) {
      this.dateComparisonDialogModel.shareFromAssembly = from;
      this.updateShareAssemblyType();
      const uri = "../api/" + DATE_COMPARISON_URI + "/" + from + "/" + this.runtimeId;

      this.modelService.getModel(uri, null)
         .subscribe((data: DateComparisonPaneModel) => {
            this.dateComparisonDialogModel.dateComparisonPaneModel = data;
         });
   }

   close(): void {
      this.onCancel.emit("cancel");
   }

   ok(): void {
      this.submit(true);
   }

   apply(event: boolean): void {
      this.submit(false, event);
   }

   private submit(commit: boolean, collapse: boolean = false) {
      if(!this.isShareDateComparison) {
         this.dateComparisonDialogModel.shareFromAssembly = null;
      }

      if(this.toDateDisabled) {
         let standardPeriodPaneModel = this.dateComparisonDialogModel?.
            dateComparisonPaneModel?.periodPaneModel?.standardPeriodPaneModel;

         if(!!standardPeriodPaneModel) {
            standardPeriodPaneModel.toDate = false;
         }
      }

      if(commit) {
         this.onCommit.emit(this.dateComparisonDialogModel);
      }
      else {
         this.onApply.emit({collapse: collapse, result: this.dateComparisonDialogModel});
      }
   }

   isValid(): boolean {
      if(this.isShareDateComparison) {
         return !!this.dateComparisonDialogModel.shareFromAssembly;
      }

      return !!this.dateComparisonPane && this.dateComparisonPane.isValidDateComparison;
   }

   clearDateComparison() {
      this.onClear.emit();
   }

   updateShareAssemblyType() {
      if(!this.isShareDateComparison) {
         return;
      }

      let shareObj: CommonKVModel<any, any> = this.dateComparisonDialogModel.shareFromAvailableAssemblies
         .find(obj => obj.key == this.dateComparisonDialogModel.shareFromAssembly);
      this.shareAssemblyType = !!shareObj ? shareObj.value : 0;
   }
}
