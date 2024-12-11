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
import { Component, EventEmitter, forwardRef, Input, OnInit, Output } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR, } from "@angular/forms";
import { TimeZoneModel } from "../../../../../../../shared/schedule/model/time-zone-model";

@Component({
   selector: "em-time-zone-select",
   templateUrl: "./time-zone-select-component.html",
   styleUrls: ["./time-zone-select-component.scss"],
   providers: [
      {
         provide: NG_VALUE_ACCESSOR,
         useExisting: forwardRef(() => TimeZoneSelectComponent),
         multi: true
      }
   ]
})
export class TimeZoneSelectComponent implements OnInit, ControlValueAccessor {
   timeZoneId: string;
   @Input()  timeZoneOptions: TimeZoneModel[];
   @Input()  startTimeEnabled: boolean = true;
   @Input()  serverTimeZone: string;
   @Input()  enabled: boolean = true;
   @Output() labelChanged = new EventEmitter<string>();
   @Output() changed = new EventEmitter<string>();
   private onChange = (fn: any) => {};
   private onTouched: any;

   constructor() {
   }

   ngOnInit(): void {
      if(!this.timeZoneId) {
         this.timeZoneId = this.timeZoneOptions[0].timeZoneId;
      }
   }

   registerOnChange(fn: any): void {
      this.onChange = fn;
   }

   registerOnTouched(fn: any): void {
      this.onTouched = fn;
   }

   writeValue(obj: any): void {
      if((!obj || !this.timeZoneOptions.find(option => option.timeZoneId == obj))) {
         this.timeZoneId = this.timeZoneOptions[0].timeZoneId;
      }
      else {
         this.timeZoneId = obj;
      }
   }

   isEmpty(id: string) {
      return !this.timeZoneId;
   }

   setTimeZoneLabel(changed: boolean) {
      const id = this.timeZoneId;
      let tz = this.timeZoneOptions.find(option => option.timeZoneId == id);
      let timeZoneLabel;

      if(!tz) {
         tz = this.timeZoneOptions[0];
      }

      if (tz == this.timeZoneOptions[0]) {
         timeZoneLabel = this.serverTimeZone;
      }
      else {
         timeZoneLabel = tz.label;
      }

      this.labelChanged.emit(timeZoneLabel);

      if(changed) {
         this.onChange(this.timeZoneId);
         this.changed.emit(this.timeZoneId);
      }
   }
}
