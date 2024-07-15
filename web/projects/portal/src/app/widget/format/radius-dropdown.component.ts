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
import { Component, Output, Input, EventEmitter, OnInit } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../shared/util/form-validators";
import { DebounceService } from "../services/debounce.service";
import { debounceTime } from "rxjs/operators";

const RADIUS_INPUT_DKEY = "radiusDropdownInputKey";

@Component({
   selector: "radius-dropdown",
   templateUrl: "radius-dropdown.component.html"
})
export class RadiusDropdown implements OnInit {
   @Input() set radius(value: number) {
      this._radius = value;

      if(this.form && this.form.get("radius")) {
         this.form.get("radius").setValue(this.radius);
         this.form.updateValueAndValidity();
      }
   }

   get radius() {
      return this._radius;
   }

   @Input() form: UntypedFormGroup;
   @Input() set disabled(value: boolean) {
      this._disable = value;
      this.updateRadiusStatus(value);
   }

   get disabled() {
      return this._disable;
   }

   @Output() radiusChange: EventEmitter<number> = new EventEmitter<number>();

   @Input()
   set max(value: number) {
      this._max = value;

      if(this.form && this.form.get("radius")) {
         this.form.get("radius").setValidators([FormValidators.positiveIntegerInRange,
            Validators.max(this._max), FormValidators.requiredNumber]);
         this.form.get("radius").setValue(this.radius);
         this.form.updateValueAndValidity();
      }
   }

   get max(): number {
      return this._max;
   }

   private _max: number;
   _disable: boolean;
   _radius: number;

   constructor(private debounceService: DebounceService) {}

   ngOnInit(): void {
      this.initForm();
   }

   initForm(): void {
      if(!this.form) {
         this.form = new UntypedFormGroup({});
      }

      this.form.addControl("radius", new UntypedFormControl(
         this.radius, [FormValidators.positiveIntegerInRange,
            Validators.max(this.max), FormValidators.requiredNumber]));

      this.form.get("radius").valueChanges
         .pipe(debounceTime(500))
         .subscribe((radius: number) => {
         if(this.radius != radius && radius != null && radius + "" != "") {
            this.radius = radius;
            this.radiusChange.emit(radius);
         }
      });

      this.updateRadiusStatus(this.disabled);
   }

   private updateRadiusStatus(value: boolean) {
      if(this.form && this.form.get("radius")) {
         if(value) {
            this.form.get("radius").disable();
         }
         else {
            this.form.get("radius").enable();
         }
      }
   }
}
