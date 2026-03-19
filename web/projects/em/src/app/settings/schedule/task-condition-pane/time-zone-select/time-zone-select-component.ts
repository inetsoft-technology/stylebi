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
import { Component, EventEmitter, forwardRef, Input, OnChanges, OnInit, Output, SimpleChanges } from "@angular/core";
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
export class TimeZoneSelectComponent implements OnInit, OnChanges, ControlValueAccessor {
   selectedTimeZone: TimeZoneModel;
   @Input()  timeZoneLabel: string;
   @Input()  timeZoneOptions: TimeZoneModel[];
   @Input()  startTimeEnabled: boolean = true;
   @Input()  serverTimeZone: string;
   @Input()  enabled: boolean = true;
   @Output() labelChanged = new EventEmitter<string>();
   @Output() changed = new EventEmitter<string>();
   private onChange = (fn: any) => {};
   private onTouched: any;

   compareTimeZones = (a: TimeZoneModel, b: TimeZoneModel) =>
      a?.timeZoneId === b?.timeZoneId && a?.label === b?.label;

   ngOnInit(): void {
      if(!this.selectedTimeZone) {
         this.selectedTimeZone = this.timeZoneOptions[0];
      }
   }

   // selectedTimeZone is null on the first call; writeValue handles that case via timeZoneLabel
   ngOnChanges(changes: SimpleChanges): void {
      if(changes.timeZoneLabel && this.selectedTimeZone && this.timeZoneOptions) {
         const newLabel = changes.timeZoneLabel.currentValue as string;
         const candidates = this.timeZoneOptions.filter(o => o.timeZoneId === this.selectedTimeZone.timeZoneId);
         const matched = newLabel
            ? candidates.find(c => c.label === newLabel)
            : null;

         if(matched && matched !== this.selectedTimeZone) {
            this.selectedTimeZone = matched;
         }
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
         return;
      }

      const candidates = this.timeZoneOptions.filter(o => o.timeZoneId === obj);

      if(!obj || candidates.length === 0) {
         this.selectedTimeZone = this.timeZoneOptions[0];
      }
      else {
         const matched = this.timeZoneLabel
            ? candidates.find(c => c.label === this.timeZoneLabel)
            : null;
         this.selectedTimeZone = matched ?? candidates[0];
      }
   }

   setTimeZoneLabel(changed: boolean) {
      const label = this.selectedTimeZone?.label ?? "";
      this.labelChanged.emit(label);

      if(changed) {
         this.onChange(this.selectedTimeZone?.timeZoneId);
         this.changed.emit(this.selectedTimeZone?.timeZoneId);
      }
   }
}
