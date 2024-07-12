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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../shared/util/form-validators";

@Component({
   selector: "name-input-dialog",
   templateUrl: "name-input-dialog.component.html",
})
export class NameInputDialog implements OnInit {
   @Input() title: string = "_#(js:Input Name)";
   @Input() existedNames: string[];
   @Input() inputName: string;
   @Output() onCommit: EventEmitter<string> = new EventEmitter<string>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   form: UntypedFormGroup;

   ngOnInit(): void {
      this.initForm();
   }

   initForm() {
      this.form = new UntypedFormGroup({
         name: new UntypedFormControl(null, [Validators.required,
            FormValidators.exists(this.existedNames, {trimSurroundingWhitespace: true})])
      });
   }

   okClicked(evt: MouseEvent): void {
      evt.stopPropagation();
      this.onCommit.emit(this.inputName);
   }

   cancelClicked(evt: MouseEvent): void {
      evt.stopPropagation();
      this.onCancel.emit("cancel");
   }
}
