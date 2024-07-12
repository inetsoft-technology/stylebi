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
import { UntypedFormControl } from "@angular/forms";

@Component({
   selector: "tabular-boolean-editor",
   templateUrl: "tabular-boolean-editor.component.html"
})
export class TabularBooleanEditor implements OnInit, OnChanges {
   @Input() value: boolean;
   @Input() label: string;
   @Input() enabled: boolean = true;
   @Input() required: boolean = false;
   @Output() valueChange: EventEmitter<boolean> = new EventEmitter<boolean>();
   valueControl: UntypedFormControl;

   ngOnInit(): void {
      this.valueControl = new UntypedFormControl();

      if(!this.enabled) {
         this.valueControl.disable();
      }

      this.valueControl.valueChanges
         .subscribe((newValue: boolean) => {
            if(this.valueControl.dirty) {
               this.valueChange.emit(newValue);
            }
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
}
