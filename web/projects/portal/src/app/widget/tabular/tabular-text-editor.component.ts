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
import {
   Component,
   EventEmitter,
   Input,
   OnChanges,
   OnInit,
   Output,
   SimpleChanges
} from "@angular/core";
import { UntypedFormControl, ValidatorFn, Validators } from "@angular/forms";
import { debounceTime } from "rxjs/operators";

@Component({
   selector: "tabular-text-editor",
   templateUrl: "tabular-text-editor.component.html",
   styleUrls: ["tabular-text-editor.component.scss"]
})
export class TabularTextEditor implements OnInit, OnChanges {
   @Input() value: string;
   @Input() password: boolean = false;
   @Input() rows: number = 1;
   @Input() columns: number = 0;
   @Input() enabled: boolean = true;
   @Input() required: boolean = false;
   @Input() placeholder: string = "";
   @Input() pattern: string;
   @Output() valueChange: EventEmitter<string> = new EventEmitter<string>();
   @Output() validChange: EventEmitter<boolean> = new EventEmitter<boolean>();
   valueControl: UntypedFormControl;
   lastValue: string;
   passwordVisible: boolean = false;

   ngOnInit(): void {
      let validators: ValidatorFn[] = [];

      if(this.required) {
         validators.push(Validators.required);
      }

      if(this.pattern) {
         validators.push(Validators.pattern(this.pattern));
      }

      this.valueControl = new UntypedFormControl(this.value, validators);

      if(!this.enabled) {
         this.valueControl.disable();
      }

      this.valueControl.valueChanges.pipe(debounceTime(1000))
         .subscribe((newValue: string) => {
            this.valueChanged();
         });
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
