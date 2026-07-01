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
import {
   Component,
   ElementRef,
   forwardRef,
   Inject,
   Input,
   Renderer2,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR, FormsModule } from "@angular/forms";
import { NgbDateParserFormatter, NgbDateStruct, NgbInputDatepicker, NgbDatepicker, NgbDatepickerMonth } from "@ng-bootstrap/ng-bootstrap";
import { DateTypeFormatter } from "../../../../../shared/util/date-type-formatter";
import { Tool } from "../../../../../shared/util/tool";
import { CustomSelectOption, CustomSelectComponent } from "../custom-select/custom-select.component";

export const DATE_VALUE_ACCESSOR: any = {
   provide: NG_VALUE_ACCESSOR,
   useExisting: forwardRef(() => DateValueEditorComponent), // eslint-disable-line @typescript-eslint/no-use-before-define
   multi: true
};

@Component({
    selector: "date-value-editor",
    templateUrl: "date-value-editor.component.html",
    styleUrls: ["date-value-editor.component.scss"],
    providers: [DATE_VALUE_ACCESSOR],
    imports: [NgbInputDatepicker, NgbDatepickerMonth, FormsModule, CustomSelectComponent]
})
export class DateValueEditorComponent implements ControlValueAccessor {
   @ViewChild("datepickerMax") ngbDatepicker;
   @ViewChild("datepickerContent", { static: true }) datepickerContent: TemplateRef<any>;
   @Input() format: string = DateTypeFormatter.ISO_8601_DATE_FORMAT;
   @Input() disabled: boolean = false;
   date: NgbDateStruct;
   initDate: NgbDateStruct;
   dateValue: any;
   onChange = (_: any) => {};
   onTouched = () => {};
   private closeDatepickerListener: () => any;
   private readonly defaultYearWindow: number = 10;

   @Input() set model(dateString: string) {
      let dateObject = new Date(dateString);

      if(!!dateString && !isNaN(dateObject.getTime())) {
         let date1: NgbDateStruct = {
            year: dateObject.getFullYear(),
            month: dateObject.getMonth() + 1,
            day: dateObject.getDate()
         };

         this.changeDate(date1);
         this.initDate = this.date;
      }
   }

   constructor(@Inject(DOCUMENT) private document: Document,
               private ngbDateParserFormatter: NgbDateParserFormatter,
               private renderer: Renderer2,
               private element: ElementRef)
   {
   }

   changeDate(date: string | NgbDateStruct) {
      if(!(typeof date === "string") && !Tool.isEquals(this.date, date)) {
         this.date = date;
         this.propagateDateChange();
      }
   }

   change(date: any): void {
      if(typeof date === "string") {
         const fmtDate = DateTypeFormatter.formatStr(date, DateTypeFormatter.ISO_8601_DATE_FORMAT);

         if(fmtDate != null) {
            const newDate = this.ngbDateParserFormatter.parse(fmtDate);

            if(newDate != null && !Tool.isEquals(this.date, newDate)) {
               this.date = newDate;
               this.initDate = this.date;
               this.propagateDateChange();
            }
         }
         else {
            const newDate = this.ngbDateParserFormatter.parse(date);

            if(newDate != null && !Tool.isEquals(this.date, newDate)) {
               this.date = newDate;
               this.initDate = this.date;
               this.propagateDateChange();

               return;
            }

            this.initDate = {year: null, month: null, day: null};

            if(this.date != null) {
               this.date = null;
               this.propagateDateChange();
            }
         }
      }
   }

   propagateDateChange(touched: boolean = true) {
      if(touched) {
         this.onTouched();
      }

      if(this.date != null) {
         if(this.date.year && this.date.year < 100) {
            this.date.year = Math.floor(new Date().getFullYear() / 100) * 100 + this.date.year;
         }

         let formattedDate = this.ngbDateParserFormatter.format(this.date);
         formattedDate = DateTypeFormatter
            .transformValue(formattedDate, DateTypeFormatter.ISO_8601_DATE_FORMAT, this.format);
         this.onChange(formattedDate);
      }
      else {
         this.onChange(null);
      }
   }

   /**
    * Write a new value to the element.
    */
   writeValue(date: string) {
      let formattedDate = DateTypeFormatter
         .transformValue(date, this.format, DateTypeFormatter.ISO_8601_DATE_FORMAT);
      this.date = this.ngbDateParserFormatter.parse(formattedDate);
      this.initDate = this.date ? this.date : { year: null, month: null, day: null };
   }

   /**
    * Set the function to be called when the control receives a change event.
    */
   registerOnChange(fn: any) {
      this.onChange = fn;
   }

   /**
    * Set the function to be called when the control receives a touch event.
    */
   registerOnTouched(fn: any) {
      this.onTouched = fn;
   }

   /**
    * This function is called when the control status changes to or from "DISABLED".
    * Depending on the value, it will enable or disable the appropriate DOM element.
    *
    * @param isDisabled
    */
   setDisabledState(isDisabled: boolean) {
      this.disabled = isDisabled;
   }

   toggleDatepicker(event?: MouseEvent) {
      event?.preventDefault();
      event?.stopPropagation();
      this.ngbDatepicker.toggle();
      this.initDateValue();
      this.initCloseDatepickerListener();
   }

   getMonthSelectOptions(datepicker: NgbDatepicker): CustomSelectOption<number>[] {
      const displayed = this.getDisplayedMonth(datepicker);

      return this.getAvailableMonths(datepicker, displayed.year).map((month) => ({
         value: month,
         label: datepicker.i18n.getMonthShortName(month, displayed.year)
      }));
   }

   getYearSelectOptions(datepicker: NgbDatepicker): CustomSelectOption<number>[] {
      const displayed = this.getDisplayedMonth(datepicker);
      const minYear = datepicker.state.minDate?.year ?? displayed.year - this.defaultYearWindow;
      const maxYear = datepicker.state.maxDate?.year ?? displayed.year + this.defaultYearWindow;
      const options: CustomSelectOption<number>[] = [];

      for(let year = minYear; year <= maxYear; year++) {
         options.push({
            value: year,
            label: datepicker.i18n.getYearNumerals(year)
         });
      }

      return options;
   }

   selectMonth(datepicker: NgbDatepicker, month: number): void {
      const displayed = this.getDisplayedMonth(datepicker);
      datepicker.navigateTo({ year: displayed.year, month, day: 1 });
   }

   selectYear(datepicker: NgbDatepicker, year: number): void {
      const displayed = this.getDisplayedMonth(datepicker);
      const availableMonths = this.getAvailableMonths(datepicker, year);
      const month = availableMonths.includes(displayed.month) ? displayed.month : availableMonths[0];

      datepicker.navigateTo({ year, month, day: 1 });
   }

   navigateMonth(datepicker: NgbDatepicker, offset: number): void {
      const displayed = this.getDisplayedMonth(datepicker);
      let year = displayed.year;
      let month = displayed.month + offset;

      while(month < 1) {
         month += 12;
         year--;
      }

      while(month > 12) {
         month -= 12;
         year++;
      }

      const minDate = datepicker.state.minDate;
      const maxDate = datepicker.state.maxDate;

      if(minDate && (year < minDate.year || year === minDate.year && month < minDate.month)) {
         year = minDate.year;
         month = minDate.month;
      }

      if(maxDate && (year > maxDate.year || year === maxDate.year && month > maxDate.month)) {
         year = maxDate.year;
         month = maxDate.month;
      }

      datepicker.navigateTo({ year, month, day: 1 });
   }

   canNavigateMonth(datepicker: NgbDatepicker, offset: number): boolean {
      const displayed = this.getDisplayedMonth(datepicker);
      const minDate = datepicker.state.minDate;
      const maxDate = datepicker.state.maxDate;
      let year = displayed.year;
      let month = displayed.month + offset;

      while(month < 1) {
         month += 12;
         year--;
      }

      while(month > 12) {
         month -= 12;
         year++;
      }

      if(minDate && (year < minDate.year || year === minDate.year && month < minDate.month)) {
         return false;
      }

      if(maxDate && (year > maxDate.year || year === maxDate.year && month > maxDate.month)) {
         return false;
      }

      return true;
   }

   initDateValue() {
      if(this.initDate == null || this.initDate.year == null &&
         this.initDate.month == null && this.initDate.day == null)
      {
         let dateObject = new Date();
         const today = {
            year: dateObject.getFullYear(),
            month: dateObject.getMonth() + 1,
            day: dateObject.getDate()
         };

         this.ngbDatepicker?.navigateTo(today);
      }
   }

   initCloseDatepickerListener(): void {
      if(this.closeDatepickerListener == null) {
         this.closeDatepickerListener = this.renderer.listen("document", "mouseup",
            (event: MouseEvent) => {
               const target = event.target as Node;
               const popup = this.document.querySelector("ngb-datepicker.dropdown-menu.show");

               if(!this.element.nativeElement.contains(target) &&
                  !(popup instanceof HTMLElement && popup.contains(target)))
               {
                  this.ngbDatepicker.close();

                  // remove the listener
                  this.closeDatepickerListener();
                  this.closeDatepickerListener = null;
               }
            });
      }
   }

   closeDatepicker(event: MouseEvent) {
      if(!this.element.nativeElement.contains(event.target)) {
         this.ngbDatepicker.close();
      }
   }

   private getDisplayedMonth(datepicker: NgbDatepicker): NgbDateStruct {
      const displayedMonth: any = datepicker.state.months?.[0];
      return displayedMonth?.firstDate ?? displayedMonth ?? datepicker.state.firstDate ?? this.initDate;
   }

   private getAvailableMonths(datepicker: NgbDatepicker, year: number): number[] {
      const minDate = datepicker.state.minDate;
      const maxDate = datepicker.state.maxDate;
      const startMonth = minDate && minDate.year === year ? minDate.month : 1;
      const endMonth = maxDate && maxDate.year === year ? maxDate.month : 12;
      const months: number[] = [];

      for(let month = startMonth; month <= endMonth; month++) {
         months.push(month);
      }

      return months;
   }
}
