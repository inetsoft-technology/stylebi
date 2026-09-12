/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

/**
 * Test-environment stub for @ckeditor/ckeditor5-angular.
 *
 * The real package initialises a full browser editor (canvas, MutationObserver, etc.)
 * that cannot run inside jsdom. This stub replaces it with a minimal Angular component
 * that satisfies the CkeditorWrapperComponent template:
 *
 *   <ckeditor [editor]="…" [config]="…" [disabled]="…" [(ngModel)]="…" (ready)="…">
 *
 * Aliased globally in vitest-base.config.ts so every test that (transitively) imports
 * CkeditorWrapperComponent or MatCkeditorComponent gets this no-op automatically.
 */

import { Component, EventEmitter, forwardRef, Input, NgModule, Output } from "@angular/core";
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from "@angular/forms";

@Component({
   selector: "ckeditor",
   standalone: true,
   template: "",
   providers: [
      {
         provide: NG_VALUE_ACCESSOR,
         useExisting: forwardRef(() => CKEditorComponent),
         multi: true,
      },
   ],
})
export class CKEditorComponent implements ControlValueAccessor {
   @Input() editor: any;
   @Input() config: any;
   @Input() disabled = false;
   @Output() ready = new EventEmitter<any>();
   @Output() change = new EventEmitter<any>();
   @Output() blur = new EventEmitter<any>();
   @Output() focus = new EventEmitter<any>();

   writeValue(_value: any): void {}
   registerOnChange(_fn: any): void {}
   registerOnTouched(_fn: any): void {}
   setDisabledState(_isDisabled: boolean): void {}
}

@NgModule({
   imports: [CKEditorComponent],
   exports: [CKEditorComponent],
})
export class CKEditorModule {}
