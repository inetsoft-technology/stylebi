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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";

@Component({
   selector: "range-slider-edit-dialog",
   templateUrl: "range-slider-edit-dialog.component.html",
})
export class RangeSliderEditDialog implements OnInit {
   @Input() currentMin: number;
   @Input() currentMax: number;
   @Input() rangeMin: number;
   @Input() rangeMax: number;

   @Output() onCommit: EventEmitter<{min: number, max: number}> =
      new EventEmitter<{min: number, max: number}>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();

   rangeForm: UntypedFormGroup;

   ngOnInit() {
      this.initForm();
   }

   initForm(): void {
      this.rangeForm = new UntypedFormGroup({});

      this.rangeForm.addControl("min", new UntypedFormControl({}, [
         Validators.required,
         Validators.min(this.rangeMin),
         Validators.max(this.rangeMax)
      ]));
      this.rangeForm.addControl("max", new UntypedFormControl({}, [
         Validators.required,
         Validators.min(this.rangeMin),
         Validators.max(this.rangeMax)
      ]));
   }

   close(): void {
      this.onCancel.emit("cancel");
   }

   ok(): void {
      this.onCommit.emit({
         min: this.currentMin,
         max: this.currentMax
      });
   }
}
