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
import { Directive, Input } from "@angular/core";
import { NG_VALIDATORS, Validator, AbstractControl, ValidationErrors } from "@angular/forms";
import { FormValidators } from "../../../../../shared/util/form-validators";

@Directive({
   selector: "[nameExists]",
   providers: [{provide: NG_VALIDATORS, useExisting: ExistsDirective, multi: true}]
})
export class ExistsDirective implements Validator {
   @Input() names: string[] = [];
   @Input() trimSurroundingWhitespace: boolean = false;

   validate(control: AbstractControl): ValidationErrors {
      return FormValidators.exists(this.names,
         {trimSurroundingWhitespace: this.trimSurroundingWhitespace})(control);
   }
}