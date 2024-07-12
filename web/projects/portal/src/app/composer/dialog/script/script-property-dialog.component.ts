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
import { Component, EventEmitter, Input, Output, OnInit }    from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../../shared/util/form-validators";

@Component({
   selector: "script-property-dialog",
   templateUrl: "script-property-dialog.component.html",
})
export class ScriptPropertyDialogComponent implements OnInit{
   @Input() comment: string;
   @Input() fontSize: number = 14;
   @Output() onCommit = new EventEmitter<{comment: string, size: number}>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   form: UntypedFormGroup;

   saveChanges(): void {
      this.onCommit.emit({
         comment: this.comment,
         size: this.form.get("fontSize").value
      });
   }

   cancelChanges(): void {
      this.onCancel.emit();
   }

   ngOnInit(): void {
      this.initForm();
   }

   initForm(): void {
      this.form = new UntypedFormGroup({
         fontSize: new UntypedFormControl(this.fontSize, [
            Validators.required,
            FormValidators.positiveNonZeroIntegerInRange,
         ]),
      });
   }
}
