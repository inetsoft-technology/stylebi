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
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";

import { FormValidators } from "../../../../../../shared/util/form-validators";
import { WorksheetOptionPaneModel } from "../../data/ws/worksheet-option-pane-model";

@Component({
   selector: "worksheet-option-pane",
   templateUrl: "worksheet-option-pane.component.html",
})
export class WorksheetOptionPane implements OnInit {
   @Input() model: WorksheetOptionPaneModel;
   @Input() form: UntypedFormGroup;

   ngOnInit(): void {
      this.initForm();
   }

   initForm(): void {
      this.form.addControl("name", new UntypedFormControl({value: this.model.name, disabled: true}, [
         FormValidators.containsSpecialChars,
         Validators.required,
         FormValidators.doesNotStartWithNumber
      ]));
      this.form.addControl("alias", new UntypedFormControl(this.model.alias, [
         FormValidators.assetEntryBannedCharacters, FormValidators.assetNameStartWithCharDigit
      ]));
      this.form.addControl("dataSource", new UntypedFormControl(this.model.dataSource));
      this.form.addControl("description", new UntypedFormControl(this.model.description));
   }
}
