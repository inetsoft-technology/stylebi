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
import { Component, ElementRef, EventEmitter, Output, ViewChild } from "@angular/core";
import { TimeInstant } from "../../common/data/time-instant";
import { DateTypeFormatter } from "../../../../../shared/util/date-type-formatter";
import { AnnotationFilterOption, RemoveAnnotationsCondition} from "../model/remove-annotations-condition";
import { FormGroup, UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../shared/util/form-validators";

@Component({
   selector: "remove-bookmarks-dialog",
   templateUrl: "./remove-bookmarks-dialog.component.html",
   styleUrls: ["./remove-bookmarks-dialog.component.scss"]
})
export class RemoveBookmarksDialog {
   @Output() onCommit = new EventEmitter<RemoveAnnotationsCondition>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("dropdownInput") dropdownInput: ElementRef;
   format = DateTypeFormatter.ISO_8601_DATE_FORMAT;
   dateTime: TimeInstant;
   condition: RemoveAnnotationsCondition;
   AnnotationFilterOption = AnnotationFilterOption;
   form: FormGroup;

   constructor() {
      let date = DateTypeFormatter.currentTimeInstantInFormat(this.format);
      this.dateTime = DateTypeFormatter.toTimeInstant(date, this.format);

      this.condition = {
         filterOption: AnnotationFilterOption.OLDER_THAN,
         filterTime: date
      };

      this.form = new UntypedFormGroup({
         filterDate: new UntypedFormControl(this.condition.filterTime, [
            Validators.required,
            FormValidators.isDate(),
         ])
      });

      this.form.controls["filterDate"].valueChanges.subscribe(value => {
         this.condition.filterTime = value;
         this.dateTime = DateTypeFormatter.toTimeInstant(value, this.format);
      });
   }

   get dropdownWidth(): number {
      return this.dropdownInput && this.dropdownInput.nativeElement
         ? this.dropdownInput.nativeElement.offsetWidth : null;
   }

   get selectedFilterOptionLabel(): string {
      if(!this.condition) {
         return "";
      }

      return this.getFilterOptionLabel(this.condition.filterOption);
   }

   getFilterOptionLabel(option: AnnotationFilterOption): string {
      if(option == AnnotationFilterOption.OLDER_THAN) {
         return "_#(js:Older than)";
      }
      else {
         return "_#(js:Not accessed since)";
      }
   }

   isSelectedOption(option: AnnotationFilterOption) {
      return option == this.condition?.filterOption;
   }

   cancelChanges(): void {
      this.onCancel.emit("cancel");
   }

   commitChanges(): void {
      this.onCommit.emit(this.condition);
   }

   selectOption(option: AnnotationFilterOption) {
      this.condition.filterOption = option;
   }

   setFilterDate(value: string) {
      this.condition.filterTime = value;
      this.dateTime = DateTypeFormatter.toTimeInstant(value, this.format);
      this.form.get("filterDate").setValue(value);
   }
}
