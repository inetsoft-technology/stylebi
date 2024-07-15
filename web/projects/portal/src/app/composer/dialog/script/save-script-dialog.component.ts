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
import { Component, EventEmitter, Input, NgZone, OnInit, Output } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { ComponentTool } from "../../../common/util/component-tool";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ModelService } from "../../../widget/services/model.service";
import { HttpClient } from "@angular/common/http";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { SaveScriptDialogModel } from "../../data/script/save-script-dialog-model";
import { SaveLibraryDialogModelValidator } from "../../data/tablestyle/save-library-dialog-model-validator";

const SAVE_SCRIPT_DIALOG_VALIDATION_URI = "../api/composer/script/save-script-dialog/";
const CONFIRM_MESSAGE = {
   title: "_#(js:Confirm)",
   options: {"yes": "_#(js:Yes)", "no": "_#(js:No)"},
   optionsOnlyOk: {"ok": "_#(js:OK)"},
   optionsWithCancel: {"yes": "_#(js:Yes)", "no": "_#(js:No)", "cancel": "_#(js:Cancel)"}
};
@Component({
   selector: "save-script-dialog",
   templateUrl: "save-script-dialog.component.html",
})
export class SaveScriptDialog implements OnInit {
   @Input() defaultFolder: AssetEntry;
   @Input() model: SaveScriptDialogModel;
   @Output() onCommit = new EventEmitter<SaveScriptDialogModel>();
   @Output() onCancel = new EventEmitter<string>();
   form: UntypedFormGroup;
   formValid = () => this.model.name && this.form && this.form.valid;

   constructor(private zone: NgZone,
               private modalService: NgbModal,
               private modelService: ModelService,
               private http: HttpClient)
   {
   }

   cancelChanges(): void {
      this.onCancel.emit();
   }

   ngOnInit() {
      if(this.defaultFolder.path == "/" || this.defaultFolder.path == "Script Function") {
         this.defaultFolder.path = "/Script Function";
      }

      this.initForm();
   }

   initForm() {
      this.form = new UntypedFormGroup({
         name: new UntypedFormControl(this.model.name, [
            Validators.required,
            FormValidators.assetEntryBannedCharacters,
            FormValidators.assetNameStartWithCharDigit])});

      this.form.get("name").valueChanges.subscribe((val) => {
         if(this.model.name != val) {
            this.model.name = val;
         }
      });
   }

   ok(){
      this.http.post<SaveLibraryDialogModelValidator>(SAVE_SCRIPT_DIALOG_VALIDATION_URI, this.model).subscribe((res) => {
         let validator: SaveLibraryDialogModelValidator = res;
         let promise = Promise.resolve(true);

         if(!!validator.permissionDenied) {
            ComponentTool.showMessageDialog(
               this.modalService, "_#(js:Error)", validator.permissionDenied);
            return;
         }

         if(!!validator.alreadyExists) {
            promise = promise.then(() => {
               return validator.allowOverwrite ?
                  ComponentTool.showConfirmDialog(this.modalService,
                     CONFIRM_MESSAGE.title, validator.alreadyExists,
                     CONFIRM_MESSAGE.options)
                  .then((buttonClicked) => buttonClicked === "yes") :
                  ComponentTool.showConfirmDialog(this.modalService,
                     "_#(js:Error)", "_#(js:agile.replet.duplicated)",
                     CONFIRM_MESSAGE.optionsOnlyOk).then(() => false);
            });
         }

         promise.then((confirmed) => {
            if(confirmed) {
               this.onCommit.emit(this.model);
            }
         });
      });
   }

   selectFolder(node: TreeNodeModel) {
      if(!node.leaf) {
         this.model.identifier = node.data.identifier;
      }
      else {
         this.model.identifier = node.data.identifier;
         this.model.name = node.label;
         this.form.get("name").setValue(node.label);
      }
   }

   enter() {
      this.zone.run(() => this.ok());
   }
}
