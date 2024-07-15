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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormControl, ValidatorFn } from "@angular/forms";
import { ValidatorMessageInfo } from "../../../widget/dialog/input-name-dialog/input-name-dialog.component";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { FormValidators } from "../../../../../../shared/util/form-validators";
import { ComponentTool } from "../../../common/util/component-tool";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { HttpClient, HttpParams } from "@angular/common/http";

const TASK_FOLDER_CHECK_DUPLICATE_URI = "../api/portal/schedule/folder/check-duplicate";

@Component({
   selector: "create-task-folder-dialog",
   templateUrl: "create-task-folder-dialog.component.html",
   styleUrls: ["create-task-folder-dialog.component.scss"]
})
export class CreateTaskFolderDialogComponent implements OnInit {
   @Input() rootNode: TreeNodeModel;
   @Input() selectedNodes: TreeNodeModel[];
   @Output() onCommit = new EventEmitter<any>();
   @Output() onCancel = new EventEmitter<string>();

   value: string;
   control: UntypedFormControl;
   formValid = () => !!this.control && !this.control.errors;
   validators: ValidatorFn[] = [
      FormValidators.required,
      FormValidators.alphanumericalCharacters,
   ];
   validatorMessages: ValidatorMessageInfo[] = [
      {validatorName: "required", message: "Name is required"},
      {validatorName: "alphanumericalCharacters", message: "Name must contain alphanumeric characters"}
   ];

   constructor(private modalService: NgbModal,
               private http: HttpClient)
   {
   }

   ngOnInit(): void {
      this.initFormControl();
   }

   private initFormControl() {
      this.control = new UntypedFormControl(this.value, this.validators);
   }

   public selectNode(node: TreeNodeModel[]): void {
      this.selectedNodes = node;
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   ok(): void {
      if(!!this.control.value) {
         let params: HttpParams = new HttpParams().set("folderName", this.control.value);
         this.http.post(TASK_FOLDER_CHECK_DUPLICATE_URI, this.parentFolder, {params})
            .subscribe((duplicate: boolean) => {
               if(duplicate) {
                  ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", "Duplicate Name");
               }
               else {
                  this.onCommit.emit({folderName: this.control.value, parentFolder : this.parentFolder});
               }
            });
      }
   }

   get parentFolder(): AssetEntry {
      if(this.selectedNodes.length == 1) {
         return this.selectedNodes[0].data;
      }

      return null;
   }

   get errorMessage(): string {
      for(let msgInfo of this.validatorMessages) {
         if(this.control.errors && this.control.errors[msgInfo.validatorName]) {
            return msgInfo.message;
         }
      }

      return null;
   }

}
