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
import { Component, Input, OnInit } from "@angular/core";
import { PaddingPaneModel } from "../model/padding-pane-model";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../shared/util/form-validators";

@Component({
   selector: "padding-pane",
   templateUrl: "padding-pane.component.html",
})
export class PaddingPane implements OnInit {
   @Input() model: PaddingPaneModel;
   @Input() form: UntypedFormGroup = new UntypedFormGroup({});

   initForm(): void {
      this.form.addControl("top", new UntypedFormControl({value: this.model.top},
         [Validators.required,
         FormValidators.isInteger(),
         FormValidators.positiveIntegerInRange
      ]));
      this.form.addControl("left", new UntypedFormControl({value: this.model.left},
         [Validators.required,
         FormValidators.isInteger(),
         FormValidators.positiveIntegerInRange
      ]));
      this.form.addControl("bottom", new UntypedFormControl({value: this.model.bottom},
         [Validators.required,
         FormValidators.isInteger(),
         FormValidators.positiveIntegerInRange
      ]));
      this.form.addControl("right", new UntypedFormControl({value: this.model.right},
         [Validators.required,
         FormValidators.isInteger(),
         FormValidators.positiveIntegerInRange
      ]));
   }

   ngOnInit(): void {
      this.initForm();
   }
}
