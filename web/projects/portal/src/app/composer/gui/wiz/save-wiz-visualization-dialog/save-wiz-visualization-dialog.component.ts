/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import { Component, EventEmitter, Input, AfterViewInit, NgZone, OnInit, Output } from "@angular/core";
import { UntypedFormControl, UntypedFormGroup, Validators } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { TreeNodeModel } from "../../../../widget/tree/tree-node-model";
import { SaveViewsheetDialogModel } from "../../../data/vs/save-viewsheet-dialog-model";
import { AssetEntry } from "../../../../../../../shared/data/asset-entry";
import { SaveViewsheetDialogModelValidator } from "../../../data/vs/save-viewsheet-dialog-model-validator";
import { ModelService } from "../../../../widget/services/model.service";
import { FormValidators } from "../../../../../../../shared/util/form-validators";
import { Tool } from "../../../../../../../shared/util/tool";
import { ComponentTool } from "../../../../common/util/component-tool";

export type VisualizationScope = "public" | "shared" | "private";

export interface SaveWizVisualizationDialogModel extends SaveViewsheetDialogModel {
   visualizationScope?: VisualizationScope;
}

const SAVE_VIEWSHEET_DIALOG_VALIDATION_URI = "../api/composer/vs/save-viewsheet-dialog-model/";
const CONFIRM_MESSAGE = {
   title: "_#(js:Confirm)",
   options: {"yes": "_#(js:Yes)", "no": "_#(js:No)"},
   optionsOnlyOk: {"ok": "_#(js:OK)"},
   optionsWithCancel: {"yes": "_#(js:Yes)", "no": "_#(js:No)", "cancel": "_#(js:Cancel)"}
};

@Component({
   selector: "save-wiz-visualization-dialog",
   templateUrl: "./save-wiz-visualization-dialog.component.html",
   styleUrls: ["./save-wiz-visualization-dialog.component.scss"]
})
export class SaveWizVisualizationDialog implements OnInit, AfterViewInit {
   @Input() model: SaveWizVisualizationDialogModel;
   @Input() defaultFolder: AssetEntry;
   @Input() runtimeId: string;
   @Input() standaloneVisualization = false;
   @Output() onCommit = new EventEmitter<SaveWizVisualizationDialogModel>();
   @Output() onCancel = new EventEmitter<string>();

   form: UntypedFormGroup;
   /** true = showing save-type selection page; false = showing name/tree page */
   showTypePage = true;
   formValid = () => this.model && this.form && this.form.valid;

   constructor(private modelService: ModelService,
               private modalService: NgbModal,
               private zone: NgZone) {
   }

   ngOnInit(): void {
      this.initForm();

      if(this.model.name && this.model.name.indexOf("Untitled-") === 0) {
         this.model.name = "";
      }
   }

   ngAfterViewInit(): void {
      if(!this.model.visualizationScope) {
         // radio mode defaults to "public"; checkbox mode defaults to "private" (unchecked)
         this.model.visualizationScope = this.standaloneVisualization ? "public" : "private";
      }
   }

   /** Next is enabled only when there is a valid scope to navigate to tree page */
   get canNext(): boolean {
      if(this.standaloneVisualization) {
         return true;
      }

      // checkbox mode: only allowed when checkbox is checked (shared)
      return this.model.visualizationScope === "shared";
   }

   /** Checkbox mode: direct OK without tree page (checkbox unchecked → private) */
   okDirect(): void {
      this.model.visualizationScope = "private";
      this.onCommit.emit(this.model);
   }

   initForm() {
      this.form = new UntypedFormGroup({
         name: new UntypedFormControl(this.model.name, [
            Validators.required,
            FormValidators.assetEntryBannedCharacters,
            FormValidators.assetNameStartWithCharDigit
         ])
      });
   }

   next(): void {
      this.showTypePage = false;
   }

   back(): void {
      this.showTypePage = true;
   }

   selectFolder(node: TreeNodeModel) {
      console.log(node);
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
      this.modelService.sendModel<SaveViewsheetDialogModelValidator>(
         SAVE_VIEWSHEET_DIALOG_VALIDATION_URI + Tool.byteEncode(this.runtimeId), this.model)
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
      if(!this.showTypePage) {
         this.zone.run(() => this.ok());
      }
   }
}
