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
import { FocusMonitor } from "@angular/cdk/a11y";
import { coerceBooleanProperty } from "@angular/cdk/coercion";
import {
   AfterViewInit,
   ChangeDetectorRef,
   Component,
   DoCheck,
   ElementRef, EventEmitter,
   HostBinding,
   Input,
   OnDestroy,
   OnInit,
   Optional, Output,
   Renderer2,
   Self,
   ViewChild
} from "@angular/core";
import { ControlValueAccessor, FormGroupDirective, NgControl, NgForm } from "@angular/forms";
import { ErrorStateMatcher, mixinDisabled, mixinDisableRipple, mixinErrorState, mixinTabIndex } from "@angular/material/core";
import { MatFormFieldControl } from "@angular/material/form-field";
import { Subject } from "rxjs";
import { FileData } from "../../../../../../../shared/util/model/file-data";
import { Tool } from "../../../../../../../shared/util/tool";

export class FileChooserBase {
   stateChanges = new Subject<void>();

   constructor(public _elementRef: ElementRef,
               public _defaultErrorStateMatcher: ErrorStateMatcher,
               public _parentForm: NgForm,
               public _parentFormGroup: FormGroupDirective,
               public ngControl: NgControl) {}
}

export const _FileChooserMixinBase = mixinDisableRipple(
   mixinTabIndex(mixinDisabled(mixinErrorState(FileChooserBase))));

@Component({
   selector: "em-file-chooser",
   templateUrl: "./file-chooser.component.html",
   styleUrls: ["./file-chooser.component.scss"],
   providers: [
      { provide: MatFormFieldControl, useExisting: FileChooserComponent }
   ],
})
export class FileChooserComponent
   extends _FileChooserMixinBase
   implements OnInit, OnDestroy, DoCheck, AfterViewInit, MatFormFieldControl<FileData[]>, ControlValueAccessor
{
   @Input() accept: string;
   @Input() hidden = false;
   @Input() errorStateMatcher: ErrorStateMatcher;
   @Output() valueChange = new EventEmitter<FileData[]>();
   @HostBinding() id = `em-file-chooser-${FileChooserComponent.nextId++}`;
   @HostBinding("attr.aria-describedby") describedBy = "";
   @ViewChild("fileInput", { static: true }) fileInput: ElementRef<HTMLInputElement>;

   @Input()
   get placeholder(): string {
      return this._placeholder;
   }

   set placeholder(value: string) {
      this._placeholder = value;
      this.stateChanges.next();
   }

   @Input()
   get required() {
      return this._required;
   }

   set required(value) {
      this._required = coerceBooleanProperty(value);
      this.stateChanges.next();
   }

   @Input()
   get disabled() {
      return this._disabled;
   }

   set disabled(value) {
      this._disabled = coerceBooleanProperty(value);
      this.stateChanges.next();
   }

   @Input()
   get multiple(): boolean {
      return this._multiple;
   }

   set multiple(value: boolean) {
      this._multiple = value;
      this.updateMultiple();
   }

   @HostBinding("class.floating")
   get shouldLabelFloat() {
      return this.focused || !this.empty;
   }

   get value(): FileData[] {
      return this._value;
   }

   set value(value: FileData[]) {
      this._value = value;
      this.stateChanges.next();
   }

   get empty(): boolean {
      return !this.value || !this.value.length;
   }

   get fileName(): string {
      if(this.empty) {
         return "";
      }

      return this.value.map(f => f.name).join(", ");
   }

   stateChanges = new Subject<void>();
   focused = false;
   controlType = "em-file-chooser";
   private _placeholder: string;
   private _required = false;
   private _disabled = false;
   private _onChange: (event: any) => void;
   private _onTouched: () => void;
   private _value: FileData[];
   private _multiple = false;
   private static nextId = 0;

   constructor(@Optional() @Self() public ngControl: NgControl,
               private changeDetector: ChangeDetectorRef, private focusMonitor: FocusMonitor,
               private element: ElementRef, defaultErrorStateMatcher: ErrorStateMatcher,
               @Optional() parentForm: NgForm, @Optional() parentFormGroup: FormGroupDirective,
               private renderer: Renderer2)
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

   ngOnInit() {
   }

   ngAfterViewInit(): void {
      this.updateMultiple();
   }

   ngOnDestroy(): void {
      this.focusMonitor.stopMonitoring(this.element.nativeElement);
      this.stateChanges.complete();
   }

   ngDoCheck(): void {
      if(this.ngControl) {
         this.updateErrorState();
      }
   }

   writeValue(obj: any): void {
      this.value = <FileData[]> obj;
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
      if(!this.hidden) {
         this.browse();
      }
   }

   browse(): void {
      if(this.fileInput && this.fileInput.nativeElement) {
         this.fileInput.nativeElement.click();
      }
   }

   onFileChange(event: any): void {
      Tool.readFileData(event).subscribe(files => {
         if(files.length == 0 && !!!this.fileInput.nativeElement.value) {
            return;
         }

         this.value = files.length === 0 ? null : files;
         this.stateChanges.next();
         this.valueChange.emit(this.value);

         if(this._onChange) {
            this._onChange(this.value);
         }

         // Bug #32970. Empty the value of the input regardless of whether the upload was successful.
         this.fileInput.nativeElement.value = null;
      });
   }

   private updateMultiple(): void {
      if(this.fileInput && this.fileInput.nativeElement) {
         if(this.multiple) {
            this.renderer.setAttribute(this.fileInput.nativeElement, "multiple", "multiple");
         }
         else {
            this.renderer.removeAttribute(this.fileInput.nativeElement, "multiple");
         }
      }
   }
}
