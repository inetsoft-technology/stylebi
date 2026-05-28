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
import { UntypedFormControl, UntypedFormGroup, Validators, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { CreateMeasureDialogModel } from "./create-measure-dialog-model";
import { XSchema } from "../../../common/data/xschema";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { VSAssemblyScriptPane } from "../../../widget/dialog/vsassembly-script-pane/vsassembly-script-pane.component";
import { InputTrimDirective } from "../../../widget/directive/input-trim.directive";
import { NgIf, NgSwitch, NgSwitchCase, NgFor, NgSwitchDefault } from "@angular/common";

@Component({
    selector: "create-measure-dialog",
    templateUrl: "create-measure-dialog.component.html",
    standalone: true,
    imports: [
        NgIf,
        NgSwitch,
        NgSwitchCase,
        FormsModule,
        ReactiveFormsModule,
        InputTrimDirective,
        NgFor,
        NgSwitchDefault,
        VSAssemblyScriptPane,
    ],
})

export class CreateMeasureDialog implements OnInit {
   @Input() model: CreateMeasureDialogModel;
   @Input() field: string;
   @Input() name: string;
   showWarning: boolean;
   form: UntypedFormGroup;
   controller: string = "../api/composer/vs/create-measure-dialog-model";
   dataTypeList = XSchema.standardDataTypeList;
   shown: boolean = false;
   @Output() onCommit: EventEmitter<CreateMeasureDialogModel> =
      new EventEmitter<CreateMeasureDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();

   ngOnInit() {
      this.setInitialValues();
      this.initForm();
   }

   initForm() {
      this.form = new UntypedFormGroup({
         name: new UntypedFormControl(this.model.name, [
            Validators.required,
            FormValidators.calcSpecialCharacters,
         ]),
         dataType: new UntypedFormControl(this.model.dataType, [
            Validators.required
         ]),
         view: new UntypedFormControl(this.model.view, [
            Validators.required
         ])
      });
   }

   setInitialValues() {
      this.model.name = this.name;
      if(this.field == "aggregate") {
         this.model.view = "script";
         this.model.dataType = "Double";
      }
      else if(this.field == "detail") {
         this.model.view = "sql";
         this.model.dataType = "String";
      }

      this.shown = true;
   }

   cancelChanges(): void {
      this.onCancel.emit("cancel");
   }

   saveChanges(): void {
      this.model.name = this.model.name.trim();
      this.onCommit.emit(this.model);
   }
}
