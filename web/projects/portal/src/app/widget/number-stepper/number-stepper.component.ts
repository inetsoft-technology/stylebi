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
   Component,
   ElementRef,
   EventEmitter,
   forwardRef,
   HostBinding,
   Input,
   OnDestroy,
   Output,
   ViewChild,
} from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from "@angular/forms";

export type NumberStepperSize = "sm" | "md" | "lg";

@Component({
   selector: "number-stepper",
   templateUrl: "./number-stepper.component.html",
   styleUrls: ["./number-stepper.component.scss"],
   providers: [{
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => NumberStepperComponent),
      multi: true,
   }],
})
export class NumberStepperComponent implements ControlValueAccessor, OnDestroy {
   @Input() min: number = null;
   @Input() max: number = null;
   @Input() step: number = 1;
   @Input() unit: string = null;
   @Input() size: NumberStepperSize = "md";
   @Input() id: string = null;
   @Input() ariaLabel: string = null;
   @Input() inputmode: "numeric" | "decimal" = "numeric";
   @Input() invalid: boolean = false;
   @Input()
   set disabled(value: boolean) {
      this.explicitlyDisabled = value;
   }

   @Output() valueChange = new EventEmitter<number>();

   @ViewChild("inputRef") inputRef: ElementRef<HTMLInputElement>;

   @HostBinding("class.sm") get smClass(): boolean { return this.size === "sm"; }
   @HostBinding("class.lg") get lgClass(): boolean { return this.size === "lg"; }
   @HostBinding("class.has-unit") get hasUnitClass(): boolean { return !!this.unit; }
   @HostBinding("class.is-invalid") get invalidClass(): boolean { return this.invalid; }
   @HostBinding("class.disabled") get disabledClass(): boolean { return this.isDisabled; }

   displayValue: string = "";
   private _value: number = null;
   private explicitlyDisabled: boolean = false;
   private formDisabled: boolean = false;
   private holdInitTimer: any = null;
   private holdRepeatTimer: any = null;
   private holdAccelTimer: any = null;
   private holdRepeatInterval: number = 60;
   private onChange: (value: number) => void = () => {};
   private onTouched: () => void = () => {};

   get value(): number {
      return this._value;
   }

   get isDisabled(): boolean {
      return this.explicitlyDisabled || this.formDisabled;
   }

   get isDecrementDisabled(): boolean {
      return this.isDisabled || (this.min !== null && this._value !== null && this._value <= this.min);
   }

   get isIncrementDisabled(): boolean {
      return this.isDisabled || (this.max !== null && this._value !== null && this._value >= this.max);
   }

   writeValue(value: number): void {
      this._value = value ?? null;
      this.displayValue = value != null ? String(value) : "";
   }

   registerOnChange(fn: (value: number) => void): void {
      this.onChange = fn;
   }

   registerOnTouched(fn: () => void): void {
      this.onTouched = fn;
   }

   setDisabledState(isDisabled: boolean): void {
      this.formDisabled = isDisabled;
   }

   onDecrementClick(): void {
      if(!this.isDecrementDisabled) {
         this.performStep(-1);
         this.inputRef?.nativeElement?.focus();
      }
   }

   onIncrementClick(): void {
      if(!this.isIncrementDisabled) {
         this.performStep(1);
         this.inputRef?.nativeElement?.focus();
      }
   }

   startHold(direction: 1 | -1): void {
      this.cancelHold();
      this.holdRepeatInterval = 60;

      this.holdInitTimer = setTimeout(() => {
         this.holdInitTimer = null;

         const doRepeat = () => {
            this.performStep(direction);
            this.holdRepeatTimer = setTimeout(doRepeat, this.holdRepeatInterval);
         };

         doRepeat();

         this.holdAccelTimer = setTimeout(() => {
            this.holdRepeatInterval = 20;
         }, 1500);
      }, 300);
   }

   cancelHold(): void {
      if(this.holdInitTimer != null) {
         clearTimeout(this.holdInitTimer);
         this.holdInitTimer = null;
      }

      if(this.holdRepeatTimer != null) {
         clearTimeout(this.holdRepeatTimer);
         this.holdRepeatTimer = null;
      }

      if(this.holdAccelTimer != null) {
         clearTimeout(this.holdAccelTimer);
         this.holdAccelTimer = null;
      }
   }

   onInputChange(event: Event): void {
      this.displayValue = (event.target as HTMLInputElement).value;
   }

   onInputBlur(): void {
      const parsed = parseFloat(this.displayValue);

      if(isNaN(parsed)) {
         this.displayValue = this._value != null ? String(this._value) : "";
      }
      else {
         const clamped = this.clamp(parsed);
         this._value = clamped;
         this.displayValue = String(clamped);
         this.onChange(clamped);
         this.valueChange.emit(clamped);
      }

      this.onTouched();
   }

   onInputKeydown(event: KeyboardEvent): void {
      switch(event.key) {
      case "ArrowUp":
         event.preventDefault();
         this.performStep(1, event.shiftKey ? 10 : 1);
         break;
      case "ArrowDown":
         event.preventDefault();
         this.performStep(-1, event.shiftKey ? 10 : 1);
         break;
      case "PageUp":
         event.preventDefault();
         this.performStep(1, 10);
         break;
      case "PageDown":
         event.preventDefault();
         this.performStep(-1, 10);
         break;
      case "Home":
         event.preventDefault();
         if(this.min !== null) {
            this.commitValue(this.min);
         }
         break;
      case "End":
         event.preventDefault();
         if(this.max !== null) {
            this.commitValue(this.max);
         }
         break;
      }
   }

   onWheel(event: WheelEvent): void {
      if(document.activeElement !== this.inputRef?.nativeElement) {
         return;
      }

      event.preventDefault();
      this.performStep(event.deltaY < 0 ? 1 : -1);
   }

   ngOnDestroy(): void {
      this.cancelHold();
   }

   private performStep(direction: 1 | -1, multiplier: number = 1): void {
      const current = this._value ?? 0;
      const delta = direction * this.step * multiplier;
      const raw = current + delta;
      this.commitValue(raw);
   }

   private commitValue(raw: number): void {
      const precision = this.getDecimalPrecision();
      const rounded = precision > 0 ? parseFloat(raw.toFixed(precision)) : Math.round(raw);
      const clamped = this.clamp(rounded);
      this._value = clamped;
      this.displayValue = String(clamped);
      this.onChange(clamped);
      this.valueChange.emit(clamped);
   }

   private clamp(value: number): number {
      let result = value;

      if(this.min !== null) {
         result = Math.max(this.min, result);
      }

      if(this.max !== null) {
         result = Math.min(this.max, result);
      }

      return result;
   }

   private getDecimalPrecision(): number {
      const stepStr = String(this.step);
      const dotIndex = stepStr.indexOf(".");
      return dotIndex >= 0 ? stepStr.length - dotIndex - 1 : 0;
   }
}
