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
import { Component, EventEmitter, Input, NgZone, OnInit, Output } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { SaveViewsheetDialogModel } from "../../data/vs/save-viewsheet-dialog-model";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { SaveViewsheetDialogModelValidator } from "../../data/vs/save-viewsheet-dialog-model-validator";
import { ModelService } from "../../../widget/services/model.service";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../shared/util/tool";
import { ComponentTool } from "../../../common/util/component-tool";

const SAVE_VIEWSHEET_DIALOG_VALIDATION_URI = "../api/composer/vs/save-viewsheet-dialog-model/";
const CONFIRM_MESSAGE = {
   title: "_#(js:Confirm)",
   options: {"yes": "_#(js:Yes)", "no": "_#(js:No)"},
   optionsOnlyOk: {"ok": "_#(js:OK)"},
   optionsWithCancel: {"yes": "_#(js:Yes)", "no": "_#(js:No)", "cancel": "_#(js:Cancel)"}
};

@Component({
   selector: "save-viewsheet-dialog",
   templateUrl: "save-viewsheet-dialog.component.html",
   styleUrls: ["./save-viewsheet-dialog.component.scss"]
})
export class SaveViewsheetDialog implements OnInit {
   @Input() model: SaveViewsheetDialogModel;
   @Input() defaultFolder: AssetEntry;
   @Input() runtimeId: string;
   form: UntypedFormGroup;
   @Output() onCommit = new EventEmitter<SaveViewsheetDialogModel>();
   @Output() onCancel = new EventEmitter<string>();
   formValid = () => this.model && this.form && this.form.valid;

   constructor(private modelService: ModelService,
               private modalService: NgbModal,
               private zone: NgZone) {
   }

   ngOnInit(): void {
      this.initForm();

      if(this.model.name.indexOf("Untitled-") == 0) {
         this.model.name = "";
      }
   }

   initForm() {
      this.form = new UntypedFormGroup({
         name: new UntypedFormControl(this.model.name, [
            Validators.required,
            FormValidators.assetEntryBannedCharacters,
            FormValidators.assetNameStartWithCharDigit
         ]),
         viewsheetOptionsPaneForm: new UntypedFormGroup({})
      });
   }

   selectFolder(node: TreeNodeModel) {
      if(!node.leaf) {
         this.model.parentId = node.data.identifier;
      }
      else {
         const id: string = node.data.identifier;
         const lastDash = id.lastIndexOf("/");
         this.model.parentId = id.substring(0, lastDash);
         this.model.name = node.label;
      }
   }

   selectNodeOnLoadFn(root: TreeNodeModel): TreeNodeModel[] {
      return [root.children[0]];
   }

   cancelChanges(): void {
      this.onCancel.emit("cancel");
   }

   ok(): void {
      this.modelService.sendModel<SaveViewsheetDialogModelValidator>(SAVE_VIEWSHEET_DIALOG_VALIDATION_URI +
                                  Tool.byteEncode(this.runtimeId), this.model)
         .subscribe((res) => {
            let validator: SaveViewsheetDialogModelValidator = res.body;
            let promise = Promise.resolve(true);

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
            else if(!!validator.permissionDenied) {
               if(validator.permissionDenied.length > 0) {
                  ComponentTool.showMessageDialog(
                     this.modalService, "Error", validator.permissionDenied)
                     .then(() => {});
                  return;
               }
            }

            promise.then((confirmed) => {
               if(confirmed) {
                  this.onCommit.emit(this.model);
               }
            });
         });
   }

   enter() {
      this.zone.run(() => this.ok());
   }
}
