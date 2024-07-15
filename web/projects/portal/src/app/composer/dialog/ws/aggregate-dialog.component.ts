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
import { DOCUMENT } from "@angular/common";
import { HttpParams } from "@angular/common/http";
import { Component, EventEmitter, Inject, Input, OnInit, Output, } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Tool } from "../../../../../../shared/util/tool";
import { AggregateInfo } from "../../../binding/data/aggregate-info";
import { ColumnRef } from "../../../binding/data/column-ref";
import { DataRef } from "../../../common/data/data-ref";
import { DateRangeRef } from "../../../common/data/date-range-ref";
import { XSchema } from "../../../common/data/xschema";
import { ComponentTool } from "../../../common/util/component-tool";
import { XConstants } from "../../../common/util/xconstants";
import { ModelService } from "../../../widget/services/model.service";
import { AggregateDialogModel } from "../../data/ws/aggregate-dialog-model";
import { CheckModelTrap } from "../../data/ws/check-model-trap";
import { CheckModelTrapEvent } from "../../gui/ws/socket/check-model-trap-event";

export interface LabelDGroup {
   label: string;
   dgroup: XConstants;
}

const DATE_GROUPS: LabelDGroup[] = [
   { label: "_#(js:Year)", dgroup: XConstants.YEAR_DATE_GROUP },
   { label: "_#(js:Quarter)", dgroup: XConstants.QUARTER_DATE_GROUP },
   { label: "_#(js:Month)", dgroup: XConstants.MONTH_DATE_GROUP },
   { label: "_#(js:Week)", dgroup: XConstants.WEEK_DATE_GROUP },
   { label: "_#(js:Day)", dgroup: XConstants.DAY_DATE_GROUP },
   { label: "_#(js:Quarter of Year)", dgroup: XConstants.QUARTER_OF_YEAR_DATE_GROUP },
   { label: "_#(js:Month of Year)", dgroup: XConstants.MONTH_OF_YEAR_DATE_GROUP },
   { label: "_#(js:Week of Year)", dgroup: XConstants.WEEK_OF_YEAR_DATE_GROUP },
   { label: "_#(js:Day of Month)", dgroup: XConstants.DAY_OF_MONTH_DATE_GROUP },
   { label: "_#(js:Day of Week)", dgroup: XConstants.DAY_OF_WEEK_DATE_GROUP },
];

const TIME_INSTANCE_GROUPS: LabelDGroup[] = [
   { label: "_#(js:Year)", dgroup: XConstants.YEAR_DATE_GROUP },
   { label: "_#(js:Quarter)", dgroup: XConstants.QUARTER_DATE_GROUP },
   { label: "_#(js:Month)", dgroup: XConstants.MONTH_DATE_GROUP },
   { label: "_#(js:Week)", dgroup: XConstants.WEEK_DATE_GROUP },
   { label: "_#(js:Day)", dgroup: XConstants.DAY_DATE_GROUP },
   { label: "_#(js:Hour)", dgroup: XConstants.HOUR_DATE_GROUP },
   { label: "_#(js:Minute)", dgroup: XConstants.MINUTE_DATE_GROUP },
   { label: "_#(js:Second)", dgroup: XConstants.SECOND_DATE_GROUP },
   { label: "_#(js:Quarter of Year)", dgroup: XConstants.QUARTER_OF_YEAR_DATE_GROUP },
   { label: "_#(js:Month of Year)", dgroup: XConstants.MONTH_OF_YEAR_DATE_GROUP },
   { label: "_#(js:Week of Year)", dgroup: XConstants.WEEK_OF_YEAR_DATE_GROUP },
   { label: "_#(js:Day of Month)", dgroup: XConstants.DAY_OF_MONTH_DATE_GROUP },
   { label: "_#(js:Day of Week)", dgroup: XConstants.DAY_OF_WEEK_DATE_GROUP },
   { label: "_#(js:Hour of Day)", dgroup: XConstants.HOUR_OF_DAY_DATE_GROUP },
   { label: "_#(js:Minute of Hour)", dgroup: XConstants.MINUTE_OF_HOUR_DATE_GROUP},
   { label: "_#(js:Second of Minute)", dgroup: XConstants.SECOND_OF_MINUTE_DATE_GROUP},
];

const TIME_GROUPS: LabelDGroup[] = [
   { label: "_#(js:Hour)", dgroup: XConstants.HOUR_DATE_GROUP },
   { label: "_#(js:Minute)", dgroup: XConstants.MINUTE_DATE_GROUP },
   { label: "_#(js:Second)", dgroup: XConstants.SECOND_DATE_GROUP },
   { label: "_#(js:Hour of Day)", dgroup: XConstants.HOUR_OF_DAY_DATE_GROUP },
   { label: "_#(js:Minute of Hour)", dgroup: XConstants.MINUTE_OF_HOUR_DATE_GROUP},
   { label: "_#(js:Second of Minute)", dgroup: XConstants.SECOND_OF_MINUTE_DATE_GROUP},

];

const AGGREGATE_MODEL_REST_URI: string = "../api/composer/ws/dialog/aggregate-dialog-model/";
const SET_AGGREGATE_SOCKET_URI: string = "/events/ws/dialog/aggregate-dialog-model";
const CHECK_MODEL_TRAP_REST_URI: string = "../api/composer/worksheet/check-model-trap/";

@Component({
   selector: "aggregate-dialog",
   templateUrl: "aggregate-dialog.component.html",
   styleUrls: ["aggregate-dialog.component.scss"]
})
export class AggregateDialog implements OnInit {
   @Input() runtimeId: string;
   @Input() tableName: string;
   @Output() onCommit = new EventEmitter<any>();
   @Output() onCancel = new EventEmitter<string>();
   model: AggregateDialogModel;
   trapFields: ColumnRef[] = [];
   valid: boolean = false;
   toggleContent: "_#(js:Switch to Table)" | "_#(js:Switch to Crosstab)";
   private oldAggregateInfo: AggregateInfo;
   formValid = () => this.valid;

   constructor(private modelService: ModelService,
               private modalService: NgbModal,
               @Inject(DOCUMENT) private document: Document)
   {
   }

   ngOnInit(): void {
      const uri = AGGREGATE_MODEL_REST_URI + Tool.byteEncode(this.runtimeId);
      const params = new HttpParams().set("table", this.tableName);

      this.modelService.getModel(uri, params).subscribe(
         (data) => {
            this.model = <AggregateDialogModel> data;
            this.toggleContent = this.model.info.crosstab ? "_#(js:Switch to Table)" : "_#(js:Switch to Crosstab)";
         },
         () => {
            console.error("Could not get aggregate data.");
         }
      );
   }

   toggle(): void {
      this.model.info.crosstab = !this.model.info.crosstab;

      if(this.model.info.crosstab) {
         this.toggleContent = "_#(js:Switch to Table)";
      }
      else {
         this.toggleContent = "_#(js:Switch to Crosstab)";
      }
   }

   ok(): void {
      if(!this.model.info.crosstab &&
         this.model.info.groups.length + this.model.info.aggregates.length > this.model.maxCol)
      {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:common.oganization.colMaxCount)" + "_*" + this.model.maxCol);
         return;
      }

      // no need to send back column list and a large list can cause message to exceed
      // size limit. (58674)
      this.model.columns = [];
      this.onCommit.emit({model: this.model, controller: SET_AGGREGATE_SOCKET_URI});
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   clearDisabled(): boolean {
      return !this.model ||
         (this.model.info.aggregates.length === 0 && this.model.info.groups.length === 0);
   }

   // Works by creating new model object and then updating children via their OnChanges
   clear(): void {
      this.model.info.aggregates = [];
      this.model.info.groups = [];
      this.model = Object.assign({}, this.model);
      this.oldAggregateInfo = Tool.clone(this.model.info);
   }

   validChange(valid: boolean) {
      this.valid = valid;
      this.checkTrap();
   }

   checkTrap() {
      const oldAggregateInfo = this.oldAggregateInfo || this.model.info;
      this.oldAggregateInfo = Tool.clone(this.model.info);

      const event = new CheckModelTrapEvent();
      event.setTableName(this.model.name);
      event.setOldAggregateInfo(oldAggregateInfo);
      event.setNewAggregateInfo(this.model.info);

      this.modelService.sendModel<CheckModelTrap>(CHECK_MODEL_TRAP_REST_URI + Tool.byteEncode(this.runtimeId), event)
         .subscribe((res) => {
            const checkTrap: CheckModelTrap = res.body;

            if(checkTrap.containsTrap) {
               /** Blur active elements to avoid this angular issue:
                * {@link https://github.com/angular/angular/issues/16820} */
               if(this.document.activeElement instanceof HTMLElement) {
                  this.document.activeElement.blur();
               }

               ComponentTool.showTrapAlert(this.modalService, true, null,
                  {backdrop: false })
                  .then((buttonClicked) => {
                     if(buttonClicked === "undo") {
                        this.model = {...this.model, info: oldAggregateInfo};
                     }
                     else {
                        this.setTrapFields(checkTrap.trapFields);
                     }
                  });
            }
            else {
               this.setTrapFields(checkTrap.trapFields);
            }
         });
   }

   private setTrapFields(grayedFields: ColumnRef[]) {
      const updatedTrapFields: ColumnRef[] = [];

      for(const grayField of grayedFields) {
         const matchedColumn =
            this.model.columns.find((column) => ColumnRef.equal(grayField, column));

         if(matchedColumn != null) {
            updatedTrapFields.push(matchedColumn);
         }
      }

      this.trapFields = updatedTrapFields;
   }

   public static dateTimeGroups(ref: DataRef): LabelDGroup[] {
      let dateRange: DateRangeRef = this.getDateRangeRef(ref);
      let dategroup: LabelDGroup[] = [];

      if(Tool.isDate(ref.dataType) || dateRange != null) {
         let odtype: string = dateRange == null ? null :
            dateRange.originalType;

         if(ref.dataType == XSchema.DATE || XSchema.DATE === odtype) {
            dategroup = DATE_GROUPS;
         }
         else if(ref.dataType == XSchema.TIME || XSchema.TIME === odtype)
         {
            dategroup = TIME_GROUPS;
         }
         // if base data ref is date range ref, means it is from a date column,
         // to treat it as date type no matter it is part option or not, so when
         // user rename a group ref, the renamed ref will also show a date
         // option for user to choose, it will be more usability
         else {
            dategroup = TIME_INSTANCE_GROUPS;
         }
      }

      return dategroup;
   }

   public static getDateRangeRef(ref: DataRef): DateRangeRef {
      if(ref.classType === "DateRangeRef") {
         return <DateRangeRef> ref;
      }
      else if(ref.classType === "ColumnRef") {
         return this.getDateRangeRef((<ColumnRef> ref).dataRefModel);
      }

      return null;
   }
}
