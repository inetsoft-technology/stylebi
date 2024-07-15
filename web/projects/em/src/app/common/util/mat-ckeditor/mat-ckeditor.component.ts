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

import { FocusMonitor } from "@angular/cdk/a11y";
import { coerceBooleanProperty } from "@angular/cdk/coercion";
import {
   AfterViewInit,
   ChangeDetectorRef,
   Component,
   DoCheck,
   ElementRef,
   EventEmitter,
   HostBinding,
   Input,
   OnDestroy,
   OnInit,
   Optional,
   Output,
   Self
} from "@angular/core";
import { ControlValueAccessor, FormGroupDirective, NgControl, NgForm } from "@angular/forms";
import {
   ErrorStateMatcher,
   mixinDisabled,
   mixinDisableRipple,
   mixinErrorState,
   mixinTabIndex
} from "@angular/material/core";
import { MatFormFieldControl } from "@angular/material/form-field";
import { Subject } from "rxjs";

export class MatCkeditorBase {
   stateChanges = new Subject<void>();

   constructor(public _elementRef: ElementRef,
               public _defaultErrorStateMatcher: ErrorStateMatcher,
               public _parentForm: NgForm,
               public _parentFormGroup: FormGroupDirective,
               public ngControl: NgControl) {}
}

export const _MatCkEditorMixinBase =
   mixinDisableRipple(mixinTabIndex(mixinDisabled(mixinErrorState(MatCkeditorBase))));

@Component({
   selector: "em-mat-ckeditor",
   templateUrl: "./mat-ckeditor.component.html",
   styleUrls: ["./mat-ckeditor.component.scss"],
   providers: [
      { provide: MatFormFieldControl, useExisting: MatCkeditorComponent}
   ]
})
export class MatCkeditorComponent
   extends _MatCkEditorMixinBase
   implements OnInit, OnDestroy, DoCheck, AfterViewInit, MatFormFieldControl<string>, ControlValueAccessor
{
   @Input() advanced = true;
   @Input() errorStateMatcher: ErrorStateMatcher;
   @Output() valueChange = new EventEmitter<string>();
   @HostBinding() id = `em-mat-ckeditor-${MatCkeditorComponent.nextId++}`;
   @HostBinding("attr.aria-describedby") describedBy = "";
   @HostBinding("class.floating") shouldLabelFloat = true;

   @Input()
   get placeholder(): string {
      return this._placeholder;
   }

   set placeholder(value: string) {
      this._placeholder = value;
      this.stateChanges.next();
   }

   @Input()
   get required(): boolean {
      return this._required;
   }

   set required(value: boolean) {
      this._required = coerceBooleanProperty(value);
      this.stateChanges.next();
   }

   @Input()
   get disabled(): boolean {
      return this._disabled;
   }

   set disabled(value: boolean) {
      this._disabled = coerceBooleanProperty(value);
      this.stateChanges.next();
   }

   get value(): string {
      return this._value;
   }

   set value(value: string) {
      const val = value ?? "";

      if(val !== this._value) {
         this._value = value ?? "";
         this.stateChanges.next();
      }
   }

   get empty(): boolean {
      return !this.value || !this.value.length;
   }

   get content(): string {
      return this.value;
   }

   set content(value: string) {
      this.value = value;
      this.stateChanges.next();
      this.valueChange.emit(value);

      if(this._onChange) {
         this._onChange(value);
      }
   }

   stateChanges = new Subject<void>();
   focused = false;
   controlType = "em-mat-ckeditor";

   private _placeholder: string;
   private _required = false;
   private _disabled = false;
   private _onChange: (event: any) => void;
   private _onTouched: () => void;
   private _value: string;
   private static nextId = 0;

   constructor(@Optional() @Self() public ngControl: NgControl,
               private changeDetector: ChangeDetectorRef, private focusMonitor: FocusMonitor,
               private element: ElementRef, defaultErrorStateMatcher: ErrorStateMatcher,
               @Optional() parentForm: NgForm, @Optional() parentFormGroup: FormGroupDirective)
   {
      super(element, defaultErrorStateMatcher, parentForm, parentFormGroup, ngControl);
      focusMonitor.monitor(element.nativeElement, true).subscribe((origin) => {
         const oldFocused = this.focused;
         this.focused = !!origin;
         this.stateChanges.next();

         if(oldFocused && !this.focused && this._onTouched) {
            this._onTouched();
         }
      });

      if(this.ngControl != null) {
         this.ngControl.valueAccessor = this;
      }
   }

   ngOnInit(): void {
   }

   ngAfterViewInit(): void {
   }

   ngOnDestroy(): void {
      this.focusMonitor.stopMonitoring(this.element.nativeElement);
      this.stateChanges.complete();
   }

   ngDoCheck() {
      if(this.ngControl) {
         this.updateErrorState();
      }
   }

   writeValue(obj: any): void {
      this.value = obj as string;
   }

   registerOnChange(fn: any): void {
      this._onChange = fn;
   }

   registerOnTouched(fn: any): void {
      this._onTouched = fn;
   }

   setDisabledState(isDisabled: boolean): void {
      this.disabled = isDisabled;
      this.stateChanges.next();
   }

   setDescribedByIds(ids: string[]): void {
      this.describedBy = ids.join(" ");
   }

   onContainerClick(event: MouseEvent): void {
   }

}
