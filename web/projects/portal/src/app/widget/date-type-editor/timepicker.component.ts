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
   Component, Input, OnInit, Output, EventEmitter,
} from "@angular/core";
import { NgbTimeStruct } from "@ng-bootstrap/ng-bootstrap";
import { UntypedFormControl, UntypedFormGroup } from "@angular/forms";

/* Copied from ng-bootstrap, no longer exported */
function padNumber(value: number): string {
   if(isNumber(value)) {
      return `0${value}`.slice(-2);
   }
   else {
      return "";
   }
}

function toInteger(value: any): number {
   return parseInt(`${value}`, 10);
}

function isNumber(value: any): value is number {
   return !isNaN(toInteger(value));
}

class NgbTime {
   hour: number;
   minute: number;
   second: number;

   constructor(hour?: number, minute?: number, second?: number) {
      this.hour = toInteger(hour);
      this.minute = toInteger(minute);
      this.second = toInteger(second);
   }

   changeHour(step = 1) { this.updateHour((isNaN(this.hour) ? 0 : this.hour) + step); }

   updateHour(hour: number) {
      if (isNumber(hour)) {
         this.hour = (hour < 0 ? 24 + hour : hour) % 24;
      } else {
         this.hour = NaN;
      }
   }

   changeMinute(step = 1) { this.updateMinute((isNaN(this.minute) ? 0 : this.minute) + step); }

   updateMinute(minute: number) {
      if (isNumber(minute)) {
         this.minute = minute % 60 < 0 ? 60 + minute % 60 : minute % 60;
         this.changeHour(Math.floor(minute / 60));
      } else {
         this.minute = NaN;
      }
   }

   changeSecond(step = 1) { this.updateSecond((isNaN(this.second) ? 0 : this.second) + step); }

   updateSecond(second: number) {
      if (isNumber(second)) {
         this.second = second < 0 ? 60 + second % 60 : second % 60;
         this.changeMinute(Math.floor(second / 60));
      } else {
         this.second = NaN;
      }
   }

   isValid(checkSecs = true) {
      return isNumber(this.hour) && isNumber(this.minute) && (checkSecs ? isNumber(this.second) : true);
   }

   toString() { return `${this.hour || 0}:${this.minute || 0}:${this.second || 0}`; }
}
/* end copied from ng-bootstrap */

@Component({
   selector: "time-picker",
   templateUrl: "timepicker.component.html",
   styleUrls: ["timepicker.component.scss"]
})
export class TimepickerComponent implements OnInit {
   @Input() meridian: boolean = false;
   @Input() spinners: boolean = false;
   @Input() seconds: boolean = false;
   @Input() hourStep: number = 1;
   @Input() minuteStep: number = 1;
   @Input() secondStep: number = 1;
   @Input() readonlyInputs: number;
   @Input() size: string;
   @Input() disabled: boolean = false;

   @Output() timeChange: EventEmitter<NgbTimeStruct> = new EventEmitter<NgbTimeStruct>();

   formControlSize: string;
   buttonSize: string;
   formattedHour: string;
   formattedMinute: string;
   formattedSecond: string;
   private _model: NgbTime;

   form: UntypedFormGroup;

   @Input() set model(value: NgbTime) {
      this._model = value ? new NgbTime(value.hour, value.minute, value.second) : new NgbTime();

      if(!this.seconds && (!value || !isNumber(value.second))) {
         this._model.second = 0;
      }

      this.formatTime();
   }

   get model(): NgbTime {
      return this._model;
   }

   ngOnInit() {
      this.setControlSize();
      this.initForm();
   }

   private initForm() {
      this.form = new UntypedFormGroup({
         hourInput: new UntypedFormControl(this.formattedHour),
         minuteInput: new UntypedFormControl(this.formattedMinute),
         secondInput: new UntypedFormControl(this.formattedSecond)
      });
   }

   changeHour(step: number): void {
      this.model.changeHour(step);
      this.propogateTimeChange();
   }

   handleHour(event: KeyboardEvent): void {
      if(event.key === "ArrowUp" || event.key === "ArrowDown") {
         event.preventDefault();
      }

      if (event.key === "ArrowUp") {
         this.changeHour(1);
      }
      else if (event.key === "ArrowDown") {
         this.changeHour(-1);
      }
   }

   changeMinute(step: number): void {
      this.model.changeMinute(step);
      this.propogateTimeChange();
   }

   handleMinute(event: KeyboardEvent): void {
      if(event.key === "ArrowUp" || event.key === "ArrowDown") {
         event.preventDefault();
      }

      if (event.key === "ArrowUp") {
         this.changeMinute(1);
      }
      else if (event.key === "ArrowDown") {
         this.changeMinute(-1);
      }
   }

   changeSecond(step: number): void {
      this.model.changeSecond(step);
      this.propogateTimeChange();
   }

   handleSecond(event: KeyboardEvent): void {
      if(event.key === "ArrowUp" || event.key === "ArrowDown") {
         event.preventDefault();
      }

      if (event.key === "ArrowUp") {
         this.changeSecond(1);
      }
      else if (event.key === "ArrowDown") {
         this.changeSecond(-1);
      }
   }

   private propogateTimeChange() {
      this.formatTime();
      this.form.controls["hourInput"].setValue(this.formattedHour);
      this.form.controls["minuteInput"].setValue(this.formattedMinute);
      this.form.controls["secondInput"].setValue(this.formattedSecond);
      this.timeChange.emit(this.getTimeStruct());
   }

   updateHour(newValue: string): void {
      const isPM: boolean = this.model.hour >= 12;
      const enteredHour = toInteger(newValue);

      if(this.meridian && (isPM && enteredHour < 12 || !isPM && enteredHour === 12)) {
         this.model.updateHour(enteredHour + 12);
      }
      else {
         this.model.updateHour(enteredHour);
      }

      this.propogateTimeChange();
   }

   updateMinute(newValue: string): void {
      this.model.updateMinute(toInteger(newValue));
      this.propogateTimeChange();
   }

   updateSecond(newValue: string): void {
      this.model.updateSecond(toInteger(newValue));
      this.propogateTimeChange();
   }

   toggleMeridian(): void {
      if(this.meridian) {
         this.changeHour(12);
      }
   }

   setControlSize(): void {
      switch(this.size) {
      case "small":
         this.formControlSize = "form-control-sm";
         this.buttonSize = "btn-sm";
         break;
      case "large":
         this.formControlSize = "form-control-lg";
         this.buttonSize = "btn-lg";
         break;
      default:
         this.formControlSize = null;
         this.buttonSize = null;
      }
   }

   private formatTime(): void {
      // Format Hour
      if(isNumber(this.model.hour)) {
         if(this.meridian) {
            this.formattedHour = padNumber(this.model.hour % 12 === 0 ? 12 : this.model.hour % 12);
         }
         else {
            this.formattedHour = padNumber(this.model.hour % 24);
         }
      }
      else {
         this.formattedHour = padNumber(NaN);
      }

      // Format Minute
      this.formattedMinute = padNumber(this.model.minute);

      // Format Second
      this.formattedSecond = padNumber(this.model.second);
   }

   private getTimeStruct(): NgbTimeStruct {
      return this.model ?
         { hour: this.model.hour, minute: this.model.minute, second: this.model.second } : null;
   }
}