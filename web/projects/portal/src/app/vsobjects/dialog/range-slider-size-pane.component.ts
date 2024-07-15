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
import { Component, Input, OnInit } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { XSchema } from "../../common/data/xschema";
import { FormValidators } from "../../../../../shared/util/form-validators";
import { RangeSliderDataPaneModel } from "../model/range-slider-data-pane-model";
import { RangeSliderSizePaneModel } from "../model/range-slider-size-pane-model";
import { TimeInfoType } from "../../composer/data/vs/time-info-type";

interface RangeType {
   label: string;
   value: number;
}

@Component({
   selector: "range-slider-size-pane",
   templateUrl: "range-slider-size-pane.component.html"
})

export class RangeSliderSizePane implements OnInit {
   @Input() model: RangeSliderSizePaneModel;
   @Input() dataModel: RangeSliderDataPaneModel;
   @Input() form: UntypedFormGroup;
   rangeOptions: RangeType[] = [];
   dateOptions: RangeType[] = [
      { label: "_#(js:Year)", value: TimeInfoType.YEAR },
      { label: "_#(js:Month)", value: TimeInfoType.MONTH },
      { label: "_#(js:Day)", value: TimeInfoType.DAY}
   ];
   dateTimeOptions: RangeType[] = [
      { label: "_#(js:Year)", value: TimeInfoType.YEAR },
      { label: "_#(js:Month)", value: TimeInfoType.MONTH },
      { label: "_#(js:Day)", value: TimeInfoType.DAY },
      { label: "_#(js:Hour)", value: TimeInfoType.HOUR },
      { label: "_#(js:Minute)", value: TimeInfoType.MINUTE }
   ];
   timeOptions: RangeType[] = [
      { label: "_#(js:Hour)", value: TimeInfoType.HOUR_OF_DAY},
      { label: "_#(js:Minute)", value: TimeInfoType.MINUTE_OF_DAY}
   ];
   numberOptions: RangeType[] = [{ label: "_#(js:Number)", value: TimeInfoType.NUMBER }];
   memberOptions: RangeType[] = [{ label: "_#(js:Member)", value: TimeInfoType.MEMBER }];

   ngOnInit(): void {
      this.initRanges();
      this.initForm();
      this.updateRangeSizeDisabled();
      this.updateMaxRangeSizeDisabled();
   }

   initRanges(): void {
      if(!this.dataModel.composite) {
         if(this.model.rangeType == TimeInfoType.NUMBER) {
            this.rangeOptions = this.numberOptions;
         }
         else if(this.model.rangeType == TimeInfoType.MEMBER) {
            this.rangeOptions = this.memberOptions;
         }
         else {
            if(this.dataModel.selectedColumns && this.dataModel.selectedColumns.length > 0) {
               let dataType: string = this.dataModel.selectedColumns[0].dataType;

               if(dataType == XSchema.TIME) {
                  this.rangeOptions = this.timeOptions;
               }
               else if(dataType == XSchema.TIME_INSTANT) {
                  this.rangeOptions = this.dateTimeOptions;
               }
               else {
                  this.rangeOptions = this.dateOptions;
               }
            }
            else {
               this.rangeOptions = this.dateOptions;
            }
         }

         let found: boolean = false;

         for(let range of this.rangeOptions) {
            if(range.value == this.model.rangeType) {
               found = true;
               break;
            }
         }

         if(!found) {
            this.model.rangeType = this.rangeOptions[0].value;
         }
      }
   }

   initForm(): void {
      this.form.addControl("length", new UntypedFormControl(this.model.length, [
         FormValidators.positiveNonZeroIntegerInRange,
         FormValidators.isInteger(),
         Validators.required,
      ]));
      this.form.addControl("rangeSize", new UntypedFormControl(this.model.rangeSize, [
         FormValidators.positiveIntegerInRange,
      ]));
      this.form.addControl("maxRangeSize", new UntypedFormControl(this.model.maxRangeSize, [
         FormValidators.positiveIntegerInRange,
      ]));
   }

   updateRangeSizeDisabled(): void {
      if(this.model.logScale || this.model.rangeType != TimeInfoType.NUMBER ||
         this.dataModel.composite)
      {
         this.form.get("rangeSize").disable();
      }
      else {
         this.form.get("rangeSize").enable();
      }
   }

   updateMaxRangeSizeDisabled(): void {
      if(this.model.logScale || this.dataModel.composite) {
         this.form.get("maxRangeSize").disable();
      }
      else {
         this.form.get("maxRangeSize").enable();
      }
   }

   get logScaleDisabled(): boolean {
      return this.model.rangeType != TimeInfoType.NUMBER;
   }
}
