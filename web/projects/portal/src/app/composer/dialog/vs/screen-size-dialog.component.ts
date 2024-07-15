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
import { Component, Input, Output, EventEmitter, OnInit } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { ScreenSizeDialogModel } from "../../data/vs/screen-size-dialog-model";
import { Tool } from "../../../../../../shared/util/tool";

@Component({
   selector: "screen-size-dialog",
   templateUrl: "screen-size-dialog.component.html"
})
export class ScreenSizeDialog implements OnInit {
   @Input() index: number;
   _devices: ScreenSizeDialogModel[];
   model: ScreenSizeDialogModel;
   form: UntypedFormGroup;
   @Output() onCommit: EventEmitter<ScreenSizeDialogModel> =
      new EventEmitter<ScreenSizeDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   formValid = () => this.model && this.form && this.form.valid &&
      !this.mismatch() && !this.duplicateLabel();

   ngOnInit() {
      if(this.index == -1) {
         this.model = {
            label: "",
            description: "",
            id: null,
            minWidth: 0,
            maxWidth: 100,
            tempId: Date.now() + ""
         };
      }
      else {
         this.model = this.devices[this.index];
         this.devices.splice(this.index, 1);
      }

      this.initForm();
   }

   @Input()
   set devices(value: ScreenSizeDialogModel[]) {
      this._devices = Tool.clone(value);
   }

   get devices(): ScreenSizeDialogModel[] {
      return this._devices;
   }

   initForm(): void {
      this.form = new UntypedFormGroup({
         label: new UntypedFormControl(this.model.label, [
            Validators.required,
         ]),
         widthRangeStart: new UntypedFormControl(this.model.minWidth, [
            Validators.required,
            FormValidators.positiveIntegerInRange,
         ]),
         widthRangeEnd: new UntypedFormControl(this.model.maxWidth, [
            Validators.required,
            FormValidators.positiveIntegerInRange,
         ])
      });
   }

   mismatch(): boolean {
      return (this.model.minWidth > this.model.maxWidth);
   }

   duplicateLabel(): boolean {
      for(let device of this.devices) {
         if(device.label === this.model.label) {
            return true;
         }
      }

      return false;
   }

   cancelChanges(): void {
      this.onCancel.emit("cancel");
   }

   saveChanges(): void {
      this.onCommit.emit(this.model);
   }
}
