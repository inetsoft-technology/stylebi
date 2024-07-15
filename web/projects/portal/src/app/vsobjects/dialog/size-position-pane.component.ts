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
import { Component, Input, OnInit } from "@angular/core";
import { SizePositionPaneModel } from "../model/size-position-pane-model";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../shared/util/form-validators";

@Component({
   selector: "size-position-pane",
   templateUrl: "size-position-pane.component.html",
   styleUrls: ["size-position-pane.component.scss"]
})
export class SizePositionPane implements OnInit {
   @Input() model: SizePositionPaneModel;
   @Input() form: UntypedFormGroup = new UntypedFormGroup({});
   @Input() isGroup: boolean = false;
   @Input() isViewsheet: boolean = false;
   @Input() layoutObject: boolean = false;
   @Input() showScaleVertical: boolean = false;
   showCellHeight: boolean;
   showTitleHeight: boolean;
   _titleHeightEnable: boolean = true;

   @Input() set titleHeightEnable(enable: boolean) {
      this._titleHeightEnable = enable;

      if(!this.form || !this.form.controls["titleHeight"]) {
         return;
      }

      if(enable) {
         this.form.controls["titleHeight"].enable();
      }
      else {
         this.form.controls["titleHeight"].disable();
      }
   }

   get titleHeightEnable() {
      return this._titleHeightEnable;
   }

   initForm(): void {
      this.form.addControl("top", new UntypedFormControl({value: this.model.top,
         disabled: (!this.layoutEnabled || this.model.locked)},
         [Validators.required,
         FormValidators.isInteger(),
         FormValidators.positiveIntegerInRange
      ]));
      this.form.addControl("left", new UntypedFormControl({value: this.model.left,
         disabled: (!this.layoutEnabled || this.model.locked)},
         [Validators.required,
         FormValidators.isInteger(),
         FormValidators.positiveIntegerInRange
      ]));
      this.form.addControl("width", new UntypedFormControl({value: this.model.width,
         disabled: (!this.layoutEnabled || this.model.locked)},
         [Validators.required,
         FormValidators.isInteger(),
         FormValidators.positiveNonZeroIntegerInRange
      ]));
      this.form.addControl("height", new UntypedFormControl({value: this.model.height,
         disabled: (!this.layoutEnabled || this.model.locked)},
         [Validators.required,
         FormValidators.isInteger(),
         FormValidators.positiveNonZeroIntegerInRange
      ]));

      if(this.showTitleHeight) {
         this.form.addControl("titleHeight", new UntypedFormControl(
            {value: this.model.titleHeight, disabled: !this.titleHeightEnable}, [
            Validators.required,
            FormValidators.isInteger(),
            FormValidators.positiveNonZeroIntegerInRange
         ]));
      }

      if(this.showCellHeight) {
         this.form.addControl("cellHeight", new UntypedFormControl(this.model.cellHeight, [
            Validators.required,
            FormValidators.isInteger(),
            FormValidators.positiveNonZeroIntegerInRange
         ]));
      }

      if(this.showScaleVertical) {
         this.form.addControl("scaleVertical", new UntypedFormControl(
            {value: this.model.scaleVertical, disabled: (!this.layoutEnabled || this.model.locked)}, []));
      }
   }

   ngOnInit(): void {
      this.showCellHeight = !!this.model.cellHeight;
      this.showTitleHeight = !!this.model.titleHeight;
      this.initForm();
   }

   get layoutEnabled(): boolean {
      return !this.model.container && !this.layoutObject;
   }
}
