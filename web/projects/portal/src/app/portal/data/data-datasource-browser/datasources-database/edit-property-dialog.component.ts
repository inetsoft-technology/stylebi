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

import { Component, Input, Output, EventEmitter, OnInit } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../../shared/util/tool";
import { PropertyInfo } from "./datasources-database.component";

@Component({
   selector: "edit-property-dialog",
   templateUrl: "edit-property-dialog.component.html",
   styleUrls: ["edit-property-dialog.component.scss"]
})
export class EditPropertyDialogComponent implements OnInit {
   @Input() info: any = {
      key: "",
      value: ""
   };
   @Output() onCommit: EventEmitter<PropertyInfo> = new EventEmitter<PropertyInfo>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   _existingNames: Array<string>;
   form: UntypedFormGroup;

   ngOnInit() {
      this.initForm();
   }

   @Input()
   set existingNames(names: any) {
      if(names != null && names.length > 0) {
         this._existingNames = Tool.clone(names);
      }
   }

   get existingNames(): Array<string> {
      return this._existingNames;
   }

   initForm(): void {
      this.form = new UntypedFormGroup({
         name: new UntypedFormControl(this.info ? this.info.key : "", [
            Validators.required, FormValidators.duplicateName(() => !!this._existingNames ? this._existingNames : [])
         ]),
         value: new UntypedFormControl(this.info ? this.info.value : "", [
            Validators.required,
         ])
      });

      this.form.controls["name"].valueChanges.subscribe((name) => {
         this.info.key = name;
      });

      this.form.controls["value"].valueChanges.subscribe((val) => {
         this.info.value = val;
      });
   }

   cancelChanges(): void {
      this.onCancel.emit("cancel");
   }

   saveChanges(): void {
      this.onCommit.emit(this.info);
   }
}
