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

export interface TimeZoneValue {
   timeZoneId: string;
   timeZoneLabel: string;
}

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
   selectedTimeZone: TimeZoneModel;
   private _timeZoneOptions: TimeZoneModel[];
   @Input()
   get timeZoneOptions(): TimeZoneModel[] { return this._timeZoneOptions; }
   set timeZoneOptions(value: TimeZoneModel[]) {
      this._timeZoneOptions = value;
      if(this.pendingValue != null && value?.length) {
         this.writeValue(this.pendingValue);
      }
   }
   @Input() startTimeEnabled: boolean = true;
   @Input() enabled: boolean = true;
   @Output() changed = new EventEmitter<string>();
   private onChange = (fn: any) => {};
   private onTouched: any;
   private pendingValue: TimeZoneValue | string | null = null;

   compareTimeZones = (a: TimeZoneModel, b: TimeZoneModel) =>
      a?.timeZoneId === b?.timeZoneId && a?.label === b?.label;

   ngOnInit(): void {
      if(!this.selectedTimeZone && this.timeZoneOptions?.length) {
         this.selectedTimeZone = this.timeZoneOptions[0];
      }
   }

   registerOnChange(fn: any): void {
      this.onChange = fn;
   }

   registerOnTouched(fn: any): void {
      this.onTouched = fn;
   }

   writeValue(obj: any): void {
      if(!this.timeZoneOptions?.length) {
         this.pendingValue = obj;
         return;
      }

      this.pendingValue = null;
      const id = typeof obj === "string" ? obj : (obj as TimeZoneValue)?.timeZoneId;
      const label = typeof obj === "string" ? null : (obj as TimeZoneValue)?.timeZoneLabel;
      const candidates = this.timeZoneOptions.filter(o => o.timeZoneId === id);

      if(!id || candidates.length === 0) {
         this.selectedTimeZone = this.timeZoneOptions[0];
      }
      else {
         const matched = label
            ? candidates.find(c => c.label === label)
            : null;
         this.selectedTimeZone = matched ?? candidates[0];
      }
   }

   onSelectionChanged() {
      const value: TimeZoneValue = {
         timeZoneId: this.selectedTimeZone?.timeZoneId ?? "",
         timeZoneLabel: this.selectedTimeZone?.label ?? ""
      };
      this.onChange(value);
      this.changed.emit(this.selectedTimeZone?.timeZoneId);
   }
}
