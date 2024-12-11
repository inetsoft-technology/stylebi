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
import {
   Component,
   EventEmitter,
   OnInit,
   Output,
   TemplateRef,
   ViewChild
} from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { update } from "lodash";
import { ModelService } from "../../widget/services/model.service";
import { PreferencesDialogModel } from "./preferences-dialog-model";
import { Tool } from "../../../../../shared/util/tool";
import { UntypedFormControl, ValidationErrors } from "@angular/forms";
import { FormValidators } from "../../../../../shared/util/form-validators";
import { FileTypes } from "../../common/data/file-types";
import { ComponentTool } from "../../common/util/component-tool";

const PREFERENCES_DIALOG_MODEL_URI: string = "../api/portal/preferences-dialog-model";

@Component({
   selector: "preferences-dialog",
   templateUrl: "preferences-dialog.component.html",
   styleUrls: ["preferences-dialog.component.scss"]
})
export class PreferencesDialog implements OnInit {
   @Output() onCommit: EventEmitter<string> = new EventEmitter<string>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("changePasswordDialog") changePasswordDialog: TemplateRef<any>;
   model: PreferencesDialogModel;
   emailControl: UntypedFormControl;
   isButtonEnabled: boolean = false;

   constructor(private modalService: NgbModal,
               private modelService: ModelService)
   {
   }

   ngOnInit(): void {
      this.modelService.getModel(PREFERENCES_DIALOG_MODEL_URI).subscribe(
         (data) => {
            this.model = <PreferencesDialogModel> data;

            if(this.model.disable) {
               this.emailControl.disable()
            }
         }
      );

      this.emailControl = new UntypedFormControl(null,
         [FormValidators.emailList(), FormValidators.duplicateTokens()]);
   }

   closeDialog(): void {
      this.onCancel.emit("cancel");
   }

   okClicked(): void {
      this.modelService.sendModel(PREFERENCES_DIALOG_MODEL_URI, this.model).subscribe(
         (data) => {
            this.onCommit.emit("ok");
         },
         (error) => {
            if(!!error && error.message) {
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", error.message);
            }
         }
      );
   }

   changePasswordClicked(): void {
      this.modalService.open(this.changePasswordDialog).result.then(
         () => {
         },
         () => {
         });
   }

   isValid(): boolean {
      return this.emailControl && this.emailControl.valid;
   }

   updateOkState(): boolean {
      this.isButtonEnabled = true;
      return this.isButtonEnabled;
   }

   /**
    * Check the email format when the value is not null
    */
   emailFormatIfNotNull(control: UntypedFormControl): ValidationErrors {
      if(!control.value) {
         return null;
      }

      return FormValidators.emailSpecialCharacters(control) != null ?
         {emailFormatIfNotNull: true} : null;
   }

   disableForSSO(): boolean {
      return this.model.disable;
   }

   protected readonly update = update;
}
