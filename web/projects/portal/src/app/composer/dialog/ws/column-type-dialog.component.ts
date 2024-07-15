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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { XSchema } from "../../../common/data/xschema";
import { ColumnInfo } from "../../data/ws/column-info";
import { ColumnTypeDialogModel } from "../../data/ws/column-type-dialog-model";

interface FormatPair {
   format: string;
   label: string;
}

const dateFmts: FormatPair[] = [
   "MM/dd/yyyy",
   "yyyy-MM-dd",
   "EEEEE, MMMMM dd, yyyy",
   "MMMM d, yyyy",
   "MM/d/yy",
   "d-MMM-yy",
   "MM.d.yyyy",
   "MMM. d, yyyy",
   "d MMMMM yyyy",
   "MMMMM yy",
   "MM-yy"
].map((label) => {
   return {format: "DateFormat", label: label};
});

const timeFmts: FormatPair[] = [
   "h:mm a",
   "h:mm:ss a",
   "h:mm:ss a, z"
].map((label) => {
   return {format: "DateFormat", label: label};
});

const timeInstantFmts: FormatPair[] = [
   "MM/dd/yyyy hh:mm a",
   "MM/dd/yyyy hh:mm:ss a"
].map((label) => {
   return {format: "DateFormat", label: label};
});

const numberFmts: FormatPair[] = [
   {format: "CurrencyFormat", label: "CurrencyFormat"},
   {format: "PercentFormat", label: "PercentFormat"}
];

@Component({
   selector: "column-type-dialog",
   templateUrl: "column-type-dialog.component.html",
   styleUrls: ["column-type-dialog.component.scss"]
})
export class ColumnTypeDialog implements OnInit {
   @Input() colInfo: ColumnInfo;
   @Input() formatAll: boolean = false;
   @Input() submitCallback: (_?: ColumnTypeDialogModel) => Promise<boolean> =
      () => Promise.resolve(true);
   @Output() onCommit: EventEmitter<ColumnTypeDialogModel> =
      new EventEmitter<ColumnTypeDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   readonly dataTypeList = XSchema.standardDataTypeList;
   form: UntypedFormGroup;
   currentFmtList: FormatPair[];
   formValid = () => this.form && this.form.valid;
   removeNonconvertible: boolean = false;
   loading: boolean = false;

   ngOnInit() {
      this.initForm();
   }

   initForm() {
      this.form = new UntypedFormGroup({
         dataType: new UntypedFormControl(this.colInfo.ref.dataType, [
            Validators.required
         ]),
      });

      if(this.colInfo.ref.dataType === XSchema.STRING || this.formatAll) {
         this.form.addControl("formatSpec", new UntypedFormControl("NONE", [
            Validators.required
         ]));

         this.form.get("dataType").valueChanges.forEach((dataType: string) => {
            this.form.get("formatSpec").patchValue("NONE", {emitEvent: false});
            this.updateFormat();
         });
      }
   }

   formatLabels(): string[] {
      if(!this.currentFmtList) {
         return null;
      }

      const formatLabels: string[] = [];
      formatLabels.push("NONE");
      this.currentFmtList.forEach((fmt) => formatLabels.push(fmt.label));

      return formatLabels;
   }

   updateFormat() {
      let dataType = this.form.get("dataType").value;

      if(XSchema.isNumericType(dataType)) {
         this.currentFmtList = numberFmts;
      }
      else if(dataType === XSchema.DATE) {
         this.currentFmtList = dateFmts;
      }
      else if(dataType === XSchema.TIME) {
         this.currentFmtList = timeFmts;
      }
      else if(dataType === XSchema.TIME_INSTANT) {
         this.currentFmtList = timeInstantFmts;
      }
      else {
         this.currentFmtList = null;
      }
   }

   onFormatChange(format: string) {
      this.form.get("formatSpec").patchValue(format, {emitEvent: false});
   }

   cancelChanges() {
      this.onCancel.emit("cancel");
   }

   saveChanges() {
      let model: ColumnTypeDialogModel = this.form.getRawValue();
      model.removeNonconvertible = !!this.removeNonconvertible;

      if(model.formatSpec) {
         model.formatSpec = this.transformFormat(model.formatSpec, model.dataType);
      }

      this.loading = true;
      this.submitCallback(model).then((success) => {
         this.loading = false;

         if(success) {
            this.onCommit.emit(model);
         }
      });
   }

   transformFormat(format: string, dataType: string): string {
      if(this.currentFmtList) {
         let formatPair = this.currentFmtList.find((fmt) => fmt.label === format);

         if(formatPair) {
            return formatPair.format + ":" + formatPair.label;
         }
      }

      let formatResult: string;

      if(XSchema.isNumericType(dataType)) {
         formatResult = "DecimalFormat" + ":" + format;
      }
      else if(XSchema.DATE == dataType) {
         formatResult = "DateFormat" + ":" + format;
      }
      else if(XSchema.TIME == dataType) {
         formatResult = "TimeFormat" + ":" + format;
      }
      else if(XSchema.TIME_INSTANT == dataType) {
         formatResult = "TimeInstantFormat" + ":" + format;
      }
      else {
         formatResult = "MessageFormat" + ":" + format;
      }

      return formatResult;
   }
}
