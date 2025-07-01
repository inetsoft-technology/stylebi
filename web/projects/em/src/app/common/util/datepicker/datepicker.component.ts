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
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { DateAdapter, MAT_DATE_FORMATS } from "@angular/material/core";
import { DateTypeFormatter } from "../../../../../../shared/util/date-type-formatter";
import { Subscription } from "rxjs";
import { DayjsDateAdapter } from "./dayjs-date-adapter.service";
import dayjs from "dayjs";
import customParseFormat from "dayjs/plugin/customParseFormat";

dayjs.extend(customParseFormat);

export const DEFAULT_FORMATS = {
   parse: {
      dateInput: "YYYY-MM-DD",
   },
   display: {
      dateInput: "YYYY-MM-DD",
      monthYearLabel: "YYYY MM",
      dateA11yLabel: "YYYY-MM-DD",
      monthYearA11yLabel: "YYYY MMMM",
   },
};

@Component({
   selector: "em-datepicker",
   templateUrl: "datepicker.component.html",
   styleUrls: ["datepicker.component.scss"],
   providers: [
      {provide: DateAdapter, useClass: DayjsDateAdapter},
      {provide: MAT_DATE_FORMATS, useValue: DEFAULT_FORMATS},
   ],
})
export class DatepickerComponent implements OnInit, OnDestroy {
   @Input() isVisibleLabel: boolean;
   @Input() form: UntypedFormGroup;
   @Output() dateValue: EventEmitter<Date> = new EventEmitter<Date>();
   private dateDayjs: dayjs.Dayjs;
   private dateSubscription: Subscription;

   @Input()
   set date(_date: Date) {
      const oldValue = this.dateDayjs?.toDate();
      this.dateDayjs = !!_date ? dayjs(DateTypeFormatter.format(_date,
         DateTypeFormatter.ISO_8601_DATE_FORMAT), DateTypeFormatter.ISO_8601_DATE_FORMAT) : null;

      if(this.form && !this.form.controls["date"]) {
         this.initForm();
      }

      if(this.form && oldValue?.getTime() != _date?.getTime()) {
         this.form.controls["date"].setValue(this.dateDayjs, {emitEvent: false});
      }
   }

   get date() {
      return !!this.dateDayjs ? this.dateDayjs.toDate() : null;
   }

   constructor() {
      this.initForm();
   }

   ngOnInit(): void {
      this.subscribeDateChanged();
   }

   ngOnDestroy(): void {
      this.unsubscribeDateChanged();
   }

   initForm(): void {
      this.form = !!this.form ? this.form : new UntypedFormGroup({});
      this.form.addControl("date", new UntypedFormControl(this.dateDayjs, [Validators.required]));
   }

   private subscribeDateChanged(): void {
      this.unsubscribeDateChanged();

      this.dateSubscription = this.form.controls["date"].valueChanges.subscribe((dayjsDate: dayjs.Dayjs) => {
         if(dayjsDate != null) {
            this.dateValue.emit(dayjsDate.toDate());
         }
      });
   }

   private unsubscribeDateChanged(): void {
      if(this.dateSubscription) {
         this.dateSubscription.unsubscribe();
         this.dateSubscription = null;
      }
   }
}
