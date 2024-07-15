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
import { AfterViewInit, Directive, ElementRef, Input } from "@angular/core";
import {
   NG_VALIDATORS,
   Validator,
   AbstractControl,
   ValidationErrors,
   Validators
} from "@angular/forms";

@Directive({
   selector: "[minNumber]",
   providers: [{provide: NG_VALIDATORS, useExisting: MinNumberDirective, multi: true}]
})
export class MinNumberDirective implements Validator, AfterViewInit {
   @Input() minNumber: number;

   constructor(private elementRef: ElementRef) {
   }

   ngAfterViewInit(): void {
      const element = this.elementRef.nativeElement;

      if(element.type === "number") {
         element.min = this.minNumber;
      }
   }

   validate(control: AbstractControl): ValidationErrors {
      return Validators.min(this.minNumber)(control);
   }
}