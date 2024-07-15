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
import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";

import { XSchema } from "../../../../common/data/xschema";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { DateTypeFormatter } from "../../../../../../../shared/util/date-type-formatter";
import {
   TimeInstantValueEditorComponent
} from "../../../date-type-editor/time-instant-value-editor.component";
import { DateValueEditorComponent } from "../../../date-type-editor/date-value-editor.component";

@Component({
   selector: "variable-value-editor",
   templateUrl: "variable-value-editor.component.html",
   styleUrls: ["variable-value-editor.component.scss"],
})
export class VariableValueEditor implements OnInit {
   @ViewChild(DateValueEditorComponent) dateValueEditor;
   @ViewChild(TimeInstantValueEditorComponent) timeInstantValueEditor;
   @Input() value: any;
   @Input() type: string = XSchema.STRING;
   @Input() form: UntypedFormGroup;
   @Input() dateFormat: string = DateTypeFormatter.ISO_8601_DATE_FORMAT;
   @Input() timeFormat: string = DateTypeFormatter.ISO_8601_TIME_FORMAT;
   @Input() timeInstantFormat: string = DateTypeFormatter.ISO_8601_TIME_INSTANT_FORMAT;
   @Input() oneOf: boolean = false;
   @Output() valueChange = new EventEmitter<any>();
   public XSchema = XSchema;
   private _disabled: boolean;

   @Input() set disabled(disabled: boolean) {
      if(this.disabled != disabled) {
         this._disabled = disabled;
         this.setFormEnabled();
      }
   }

   get disabled(): boolean {
      return this._disabled;
   }

   initForm(): void {
      if(!this.form) {
         this.form = new UntypedFormGroup({});
      }

      const doublePattern = this.oneOf ? "-?[0-9]+(\.[0-9]+)?(,-?[0-9]+(\.[0-9]+)?)*"
          : "-?[0-9]+(\.[0-9]+)?";
      const intPattern = this.oneOf ? /^[-+]?[0-9,]+(,^[-+]?[0-9,]+)*$/ : /^[-+]?\d+$/;

      this.form.addControl("double", new UntypedFormControl(this.value, [
         Validators.pattern(doublePattern)
      ]));

      this.form.addControl("float", new UntypedFormControl(this.value, [
         Validators.pattern(doublePattern)
      ]));

      this.form.addControl("integer", new UntypedFormControl(this.value, [
         FormValidators.integerInRange(true), Validators.pattern(intPattern)
      ]));

      this.form.addControl("short", new UntypedFormControl(this.value, [
         FormValidators.shortInRange(), Validators.pattern(intPattern)
      ]));

      this.form.addControl("byte", new UntypedFormControl(this.value, [
         FormValidators.byteInRange(), Validators.pattern(intPattern)
      ]));

      this.form.addControl("long", new UntypedFormControl(this.value, [
         Validators.pattern(intPattern)
      ]));

      this.setFormEnabled();
   }

   /**
    * Set the value to a boolean type when type is boolean
    */
   setCheckedStatus(): void {
      if(this.type == XSchema.BOOLEAN) {
         this.value = this.value + "" == "true";
      }
   }

   setFormEnabled() {
      if(!this.form) {
         return;
      }

      if(this.disabled) {
         this.form.disable();
      }
      else {
         this.form.enable();
      }
   }

   ngOnInit(): void {
      this.initForm();
      this.setCheckedStatus();
   }

   changeValue(value: any) {
      this.value = value;
      this.valueChange.emit(value);
   }

   isValid(): boolean {
      return !this.form.get(this.type) || this.form.get(this.type).valid;
   }

   onMouseUp(event: MouseEvent) {
      if(this.dateValueEditor) {
         this.dateValueEditor.closeDatepicker(event);
      }

      if(this.timeInstantValueEditor) {
         this.timeInstantValueEditor.closeDatepicker(event);
      }
   }
}
