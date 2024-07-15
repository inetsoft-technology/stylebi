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
import { Component, Input, OnInit, OnDestroy } from "@angular/core";
import { UntypedFormGroup, UntypedFormControl } from "@angular/forms";
import { FloatEditorModel } from "../model/float-editor-model";
import { FormValidators } from "../../../../../shared/util/form-validators";

@Component({
   selector: "float-editor",
   templateUrl: "float-editor.component.html"
})
export class FloatEditor implements OnInit, OnDestroy {
   @Input() model: FloatEditorModel;
   @Input() parentForm: UntypedFormGroup;
   form: UntypedFormGroup;

   ngOnInit(): void {
      this.form = new UntypedFormGroup({
         "min": new UntypedFormControl(this.model.minimum, []),
         "max": new UntypedFormControl(this.model.maximum, [])
      }, FormValidators.smallerThan("min", "max"));

      if(this.parentForm) {
         this.parentForm.addControl("float-editor", this.form);
      }
   }

   ngOnDestroy(): void {
      if(this.parentForm) {
         this.parentForm.removeControl("float-editor");
      }
   }
}
