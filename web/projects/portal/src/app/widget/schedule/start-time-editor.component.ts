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
import { Component, EventEmitter, forwardRef, Input, OnInit, Output } from "@angular/core";
import {
   ControlValueAccessor,
   UntypedFormBuilder,
   UntypedFormGroup,
   NG_VALUE_ACCESSOR,
   ValidationErrors,
   Validators
} from "@angular/forms";
import { TimeRange } from "../../../../../shared/schedule/model/time-condition-model";
import { StartTimeData } from "./start-time-data";

@Component({
   selector: "w-start-time-editor",
   templateUrl: "./start-time-editor.component.html",
   styleUrls: ["./start-time-editor.component.scss"],
   providers: [
      {
         provide: NG_VALUE_ACCESSOR,
         useExisting: forwardRef(() => StartTimeEditor),
         multi: true
      }
   ]
})
export class StartTimeEditor implements OnInit, ControlValueAccessor {
   @Input() timeRanges: TimeRange[] = [];
   @Input() startTimeEnabled = true;
   @Input() timeRangeEnabled = true;
   @Input() timeZoneName: string;
   @Input() serverTimeZone = true;
   @Input() showMeridian = true;
   @Input() set model(m: StartTimeData) {
      this._model = m;

      if(m && m.startTime && this.isChangeStateTime(m.startTime)) {
         this.startTimeModel = m.startTime;
      }

      this.data = m;
   }

   @Output() modelChange = new EventEmitter<StartTimeData>();
   form: UntypedFormGroup;
   private onChange: any;
   private onTouched: any;
   _model: StartTimeData;
   startTimeModel: any;

   private get data(): StartTimeData {
      const value = {
         startTime: null,
         timeRange: null,
         startTimeSelected: true
      };

      if(this.form.get("selectedType").value !== "START_TIME") {
         this._model.timeRange = this.form.get("timeRange").value;
         value.timeRange = <any> this._model.timeRange;
         value.startTimeSelected = false;
      }

      value.startTime = this.form.get("startTime").value;

      return value;
   }

   private set data(value: StartTimeData) {
      if(value) {
         this.form.get("startTime").setValue(value.startTime);
         this.form.get("timeRange").setValue(value.timeRange);
         this.form.get("selectedType").setValue(
            value.startTimeSelected || !this.timeRangeEnabled ? "START_TIME" : "TIME_RANGE");
      }
      else {
         this.form.get("startTime").setValue(null);
         this.form.get("timeRange").setValue(null);
         this.form.get("selectedType").setValue(
            this.startTimeEnabled || !this.timeRangeEnabled ? "START_TIME" : "TIME_RANGE");
      }

      this.enableOptions();
   }

   constructor(fb: UntypedFormBuilder) {
      this.form = fb.group({
         startTime: [null, [this.optionRequired("START_TIME")]],
         timeRange: [null, [this.optionRequired("TIME_RANGE")]],
         selectedType: ["START_TIME"]
      });
      this.form.get("timeRange").disable();
   }

   ngOnInit(): void {
      if(!this.startTimeEnabled || !this.timeRangeEnabled) {
         this.form.get("selectedType").setValue(
            this.timeRangeEnabled ? "TIME_RANGE" : "START_TIME");
         this.enableOptions();
      }

      this.form.get("startTime").valueChanges.subscribe((val) => {
         this.startTimeChanged(val);
      });
   }

   registerOnChange(fn: any): void {
      this.onChange = fn;
   }

   registerOnTouched(fn: any): void {
      this.onTouched = fn;
   }

   setDisabledState(isDisabled: boolean): void {
      if(isDisabled) {
         this.form.disable();
      }
      else {
         this.form.enable();
         this.enableOptions();
      }
   }

   writeValue(obj: any): void {
      if(!!obj) {
         this.data = <StartTimeData> obj;
      }
   }

   startTimeChanged(model: any) {
      if(model != null) {
         const data = Object.assign({
            valid: this.form.valid,
            startTimeSelected: this.form.get("selectedType").value === "START_TIME"
         }, this.data);

         this.modelChange.emit(data);
      }
   }

   fireStartTimeChanged() {
      this.enableOptions();

      if(this.onChange) {
         const data = Object.assign({
            valid: this.form.valid,
            startTimeSelected: this.form.get("selectedType").value === "START_TIME"
         }, this.data);

         this.modelChange.emit(data);
         this.onChange(data);
      }

      if(this.onTouched) {
         this.onTouched();
      }
   }

   isChangeStateTime(m: any): boolean {
      return m.hour != this.startTimeModel?.hour || m.minute != this.startTimeModel?.minute
         || m.second != this.startTimeModel?.second;
   }

   compareTimeRange(r1: TimeRange, r2: TimeRange): boolean {
      const n1 = r1 ? r1.name : null;
      const n2 = r2 ? r2.name : null;
      return n1 === n2;
   }

   private enableOptions(): void {
      const selectedType = this.form.get("selectedType").value;

      if(selectedType === "TIME_RANGE" && !this.timeRangeEnabled) {
         this.form.get("startTime").enable();
         this.form.get("timeRange").disable();
      }
      else if(selectedType === "TIME_RANGE" && this.timeRangeEnabled) {
         this.form.get("startTime").disable();
         this.form.get("timeRange").enable();
      }
      else {
         this.form.get("startTime").enable();
         this.form.get("timeRange").disable();
      }

      if(this.form.get("selectedType").value === "TIME_RANGE" &&
         !this.form.get("timeRange").value && this.timeRanges.length > 0)
      {
         this.form.get("timeRange").setValue(this.timeRanges[0]);
      }
   }

   private optionRequired(type: string): (AbstractControl) => ValidationErrors | null {
      return (control) => {
         if(control) {
            const group = control.parent;

            if(group && group.get("selectedType").value === type) {
               return Validators.required(control);
            }
         }

         return null;
      };
   }

   public startTimeSelected(): boolean {
      return this.form.get("selectedType").value == "START_TIME";
   }
}
