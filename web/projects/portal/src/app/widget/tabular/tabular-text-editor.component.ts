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
import {
   AfterViewInit,
   Component,
   ElementRef,
   EventEmitter,
   Input,
   NgZone,
   OnChanges,
   OnDestroy,
   OnInit,
   Output,
   SimpleChanges,
   ViewChild
} from "@angular/core";
import { CdkTextareaAutosize } from "@angular/cdk/text-field";
import { UntypedFormControl, ValidatorFn, Validators } from "@angular/forms";
import { debounceTime } from "rxjs/operators";

@Component({
   selector: "tabular-text-editor",
   templateUrl: "tabular-text-editor.component.html",
   styleUrls: ["tabular-text-editor.component.scss"]
})
export class TabularTextEditor implements OnInit, OnChanges, AfterViewInit, OnDestroy {
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
   @ViewChild(CdkTextareaAutosize) autosize: CdkTextareaAutosize;
   valueControl: UntypedFormControl;
   lastValue: string;
   passwordVisible: boolean = false;
   private resizeObserver: ResizeObserver;

   constructor(private elementRef: ElementRef, private ngZone: NgZone) {
   }

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

   ngAfterViewInit(): void {
      if(this.autosize) {
         this.resizeObserver = new ResizeObserver(() => {
            this.ngZone.run(() => {
               this.autosize.resizeToFitContent(true);
            });
         });

         // Observe the parent dialog if available, otherwise observe the component element
         const dialog = this.elementRef.nativeElement.closest("tabular-query-dialog");
         this.resizeObserver.observe(dialog || this.elementRef.nativeElement);
      }
   }

   ngOnDestroy(): void {
      if(this.resizeObserver) {
         this.resizeObserver.disconnect();
      }
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
