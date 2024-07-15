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
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { AbstractControl, UntypedFormControl, ValidatorFn, Validators } from "@angular/forms";
import { debounceTime } from "rxjs/operators";

@Component({
   selector: "tabular-number-editor",
   templateUrl: "tabular-number-editor.component.html",
   styleUrls: ["tabular-number-editor.component.scss"]
})
export class TabularNumberEditor implements OnInit, OnChanges {
   @Input() value: number;
   @Input() min: number;
   @Input() max: number;
   @Input() enabled: boolean = true;
   @Input() required: boolean = false;
   @Output() valueChange: EventEmitter<number> = new EventEmitter<number>();
   @Output() validChange: EventEmitter<boolean> = new EventEmitter<boolean>();
   valueControl: UntypedFormControl;
   lastValue: number;

   ngOnInit(): void {
      this.minValidator = this.minValidator.bind(this);
      this.maxValidator = this.maxValidator.bind(this);
      let validators: ValidatorFn[] = [];

      if(!isNaN(this.min)) {
         validators.push(this.minValidator);
      }

      if(!isNaN(this.max)) {
         validators.push(this.maxValidator);
      }

      if(this.required) {
         validators.push(Validators.required);
      }

      this.valueControl = new UntypedFormControl(this.value, Validators.compose(validators));

      if(!this.enabled) {
         this.valueControl.disable();
      }

      this.valueControl.valueChanges.pipe(debounceTime(1000))
         .subscribe((newValue: number) => {
            this.valueChanged();
         });
   }

   minValidator(control: AbstractControl) {
      return control.value >= this.min ? null : {min: true};
   }

   maxValidator(control: AbstractControl) {
      return control.value <= this.max ? null : {max: true};
   }

   ngOnChanges(changes: SimpleChanges): void {
      if(this.valueControl) {
         if(changes["enabled"]) {
            if(this.enabled) {
               this.valueControl.enable();
            }
            else {
               this.valueControl.disable();
            }
         }
      }
   }

   valueChanged() {
      if(this.valueControl.pristine || this.lastValue !== this.value) {
         this.lastValue = this.value;
         this.validChange.emit(this.valueControl.valid || !this.enabled);

         if(this.valueControl.dirty) {
            this.valueChange.emit(this.value);
         }
      }
   }
}
