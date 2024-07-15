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
import { Component, EventEmitter, Input, NgZone, OnInit, Output } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { Tool } from "../../../../../../shared/util/tool";
import { ComponentTool } from "../../../common/util/component-tool";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ModelService } from "../../../widget/services/model.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { SaveWorksheetDialogModel } from "../../data/ws/save-worksheet-dialog-model";
import { SaveWorksheetDialogModelValidator } from "../../data/ws/save-worksheet-dialog-model-validator";
import { Worksheet } from "../../data/ws/worksheet";

const SAVE_WORKSHEET_DIALOG_VALIDATION_URI = "../api/composer/ws/dialog/save-worksheet-dialog-model/";
const CONFIRM_MESSAGE = {
   title: "_#(js:Confirm)",
   options: {"yes": "_#(js:Yes)", "no": "_#(js:No)"},
   optionsOnlyOk: {"ok": "_#(js:OK)"},
   optionsWithCancel: {"yes": "_#(js:Yes)", "no": "_#(js:No)", "cancel": "_#(js:Cancel)"}
};

@Component({
   selector: "save-worksheet-dialog",
   templateUrl: "save-worksheet-dialog.component.html"
})
export class SaveWorksheetDialog implements OnInit {
   @Input() worksheet: Worksheet;
   @Input() socket: ViewsheetClientService;
   @Input() showReportRepository: boolean;
   @Input() defaultFolder: AssetEntry;
   @Input() readOnly: boolean = false;
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   model: SaveWorksheetDialogModel;
   REST_CONTROLLER: string = "../api/composer/ws/dialog/save-worksheet-dialog-model/";
   form: UntypedFormGroup;
   formValid = () => this.form && this.form.valid;

   constructor(private modelService: ModelService,
               private modalService: NgbModal,
               private zone: NgZone)
   {
   }

   ngOnInit(): void {
      this.modelService.getModel(this.REST_CONTROLLER + Tool.byteEncode(this.worksheet.runtimeId))
         .subscribe((data) => {
            this.model = <SaveWorksheetDialogModel> data;
         });

      this.form = new UntypedFormGroup({
         assetRepositoryPaneForm: new UntypedFormGroup({}),
         worksheetOptionPaneForm: new UntypedFormGroup({})
      });
   }

   cancelChanges(): void {
      this.onCancel.emit();
   }

   saveChanges(): void {
      this.model.assetRepositoryPaneModel.name = this.model.assetRepositoryPaneModel.name.trim();
      this.model.worksheetOptionPaneModel.alias = this.model.worksheetOptionPaneModel.alias.trim();

      this.modelService.sendModel(SAVE_WORKSHEET_DIALOG_VALIDATION_URI +
                                  Tool.byteEncode(this.worksheet.runtimeId), this.model)
         .subscribe((res) => {
            let promise = Promise.resolve(true);

            if(!!res.body) {
               let validator: SaveWorksheetDialogModelValidator = res.body;

               if(!!validator.alreadyExists) {
                  if("Cannot overwrite an opened asset." === validator.alreadyExists) {
                     ComponentTool.showMessageDialog(this.modalService,
                                                   "Error", validator.alreadyExists);
                     return;
                  }
                  else {
                     promise = promise.then(() => {
                        return ComponentTool.showConfirmDialog(
                           this.modalService, CONFIRM_MESSAGE.title, validator.alreadyExists,
                           CONFIRM_MESSAGE.options)
                           .then((buttonClicked) => buttonClicked === "yes");
                     });
                  }
               }
               else if(!!validator.permissionDenied) {
                  if(validator.permissionDenied.length > 0) {
                     ComponentTool.showMessageDialog(
                        this.modalService, "Error", validator.permissionDenied)
                        .then(() => {});
                     return;
                  }
               }
            }

            promise.then((confirmed) => {
               if(confirmed) {
                  this.onCommit.emit(this.model);
               }
            });
         });
   }

   enter(): void {
      this.zone.run(() => this.saveChanges());
   }

   getDefaultFolder(): AssetEntry | null {
      return this.defaultFolder;
   }
}
