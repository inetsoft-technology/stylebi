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
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, ValidationErrors, Validators } from "@angular/forms";
import { TimeRange } from "../../../../../../../shared/schedule/model/time-condition-model";
import { FormValidators } from "../../../../../../../shared/util/form-validators";

export interface StartTimeData {
   startTime: string;
   timeRange: TimeRange;
   startTimeSelected: boolean;
}

export interface StartTimeChange extends StartTimeData {
   valid: boolean;
}

@Component({
   selector: "em-start-time-editor",
   templateUrl: "./start-time-editor.component.html",
   styleUrls: ["./start-time-editor.component.scss"]
})
export class StartTimeEditorComponent implements OnInit, OnChanges {
   @Input() timeRanges: TimeRange[] = [];
   @Input() startTimeEnabled = true;
   @Input() timeRangeEnabled = true;
   @Input() timeZone: string;
   @Input() timeZoneLabel: string;
   @Input() showMeridian: boolean;
   @Output() startTimeChanged = new EventEmitter<StartTimeChange>();

   @Input()
   get data(): StartTimeData {
      const value = {
         startTime: null,
         timeRange: null,
         startTimeSelected: true
      };

      value.startTime = this.form.get("startTime").value;

      if(this.form.get("selectedType").value !== "START_TIME") {
         value.timeRange = this.form.get("timeRange").value;
         value.startTimeSelected = false;
      }

      return value;
   }

   set data(value: StartTimeData) {
      if(value) {
         this.startTimeData = value;
         this.form.get("startTime").setValue(value.startTime);
         this.form.get("timeRange").setValue(value.timeRange);
         this.form.get("selectedType").setValue(
            value.startTimeSelected ? "START_TIME" : "TIME_RANGE");
      }
      else {
         this.startTimeData = {
            startTime: "01:30:00",
            timeRange: null,
            startTimeSelected: true
         };

         this.form.get("startTime").setValue("01:30:00");
         this.form.get("timeRange").setValue(null);
         this.form.get("selectedType").setValue(
            this.startTimeEnabled ? "START_TIME" : "TIME_RANGE");
      }

      this.enableOptions();
   }

   form: UntypedFormGroup;
   startTimeData: StartTimeData;

   constructor(fb: UntypedFormBuilder) {
      this.form = fb.group({
         startTime: [null, [this.optionRequired("START_TIME"), FormValidators.isTime()]],
         timeRange: [null, [this.optionRequired("TIME_RANGE")]],
         selectedType: ["START_TIME"]
      });
      this.form.get("timeRange").disable();
   }

   ngOnInit() {
      if(!this.startTimeEnabled) {
         this.form.get("selectedType").setValue("TIME_RANGE");
         this.enableOptions();
      }

      this.form.get("startTime").valueChanges.subscribe((val) => {
         if(this.startTimeData?.startTime != val) {
            this.updateStartTime();
         }
      });
   }

   ngOnChanges(changes: SimpleChanges) {
      if(changes.timeRangeEnabled) {
         this.enableOptions();
      }
   }

   updateStartTime() {
      this.form.updateValueAndValidity();
      const event = Object.assign({
         valid: this.form.valid,
         startTimeSelected: this.form.get("selectedType").value === "START_TIME"
      }, this.data);

      this.startTimeChanged.emit(event);
   }

   fireStartTimeChanged() {
      this.enableOptions();

      const event = Object.assign({
         valid: this.form.valid,
         startTimeSelected: this.form.get("selectedType").value === "START_TIME"
      }, this.data);

      this.startTimeChanged.emit(event);
   }

   compareTimeRange(r1: TimeRange, r2: TimeRange): boolean {
      const n1 = r1 ? r1.name : null;
      const n2 = r2 ? r2.name : null;
      return n1 === n2;
   }

   private enableOptions(): void {
      const selectedType = this.form.get("selectedType").value;

      if(selectedType === "TIME_RANGE" && this.timeRangeEnabled) {
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
}
