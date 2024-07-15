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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { ModelService } from "../services/model.service";
import { RepositoryEntry } from "../../../../../shared/data/repository-entry";
import { AddRepositoryFolderEvent } from "./add-repository-folder-event";
import { MessageCommand } from "../../common/viewsheet-client/message-command";
import { Tool } from "../../../../../shared/util/tool";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { UntypedFormControl, ValidationErrors, Validators } from "@angular/forms";
import { FormValidators } from "../../../../../shared/util/form-validators";
import { ComponentTool } from "../../common/util/component-tool";

const ADD_FOLDER_URI = "../api/portal/tree/add-folder";

@Component({
   selector: "add-repository-folder-dialog",
   templateUrl: "add-repository-folder-dialog.component.html"
})
export class AddRepositoryFolderDialog implements OnInit {
   @Output() onCommit: EventEmitter<string> = new EventEmitter<string>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @Input() entry: RepositoryEntry;
   @Input() edit: boolean = false;
   nameControl: UntypedFormControl;
   name: string;
   alias: string;
   description: string;

   constructor(private modelService: ModelService, private modalService: NgbModal) {
   }

   ngOnInit(): void {
      if(this.edit && this.entry) {
         this.name = this.entry.name;
         this.alias = this.entry.alias;
         this.description = this.entry.description;
      }

      this.nameControl = new UntypedFormControl(null, [Validators.required, this.endsWithPeriod,
         FormValidators.assetEntryBannedCharacters, FormValidators.assetNameStartWithCharDigit]);
   }

   closeDialog(): void {
      this.onCancel.emit("cancel");
   }

   okClicked(): void {
      this.dispatchAddRepositoryFolderEvent();
   }

   private dispatchAddRepositoryFolderEvent(confirmed: boolean = false) {
      let event = new AddRepositoryFolderEvent(this.entry, this.name, this.alias,
         this.description, this.edit);

      this.modelService.sendModel<MessageCommand>(ADD_FOLDER_URI, event)
         .subscribe((res) => {
            if(!!res.body) {
               let messageCommand = res.body;

               if(messageCommand.type !== "CONFIRM") {
                  ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
                     messageCommand.message);
               }
               else if(!confirmed) {
                  ComponentTool.showConfirmDialog(this.modalService, "_#(js:Confirm)",
                     messageCommand.message).then((buttonClicked) => {
                     if(buttonClicked === "ok") {
                        this.dispatchAddRepositoryFolderEvent(true);
                     }
                  });
               }
            }
            else {
               this.onCommit.emit("ok");
            }
         });
   }

   isValid(): boolean {
      return this.nameControl && this.nameControl.valid;
   }

   // Trailing periods will be removed from folder names in Windows, so trailing periods
   // should be prevented otherwise the resulting directory structure is different from what's expected
   private endsWithPeriod(control: UntypedFormControl): ValidationErrors {
      return (!!control && /\.$/.test(control.value)) ? {endsWithPeriod: true} : null;
   }
}
