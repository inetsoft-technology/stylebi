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
import { ChangeDetectorRef, Component, EventEmitter, Input, OnInit, Output, } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { LocalStorage } from "../../../common/util/local-storage.util";
import { EmailDialogModel } from "../../model/email-dialog-model";
import { EmailValidationResponse } from "./email-validation-response";
import { ComponentTool } from "../../../common/util/component-tool";
import { ModelService } from "../../../widget/services/model.service";
import { HttpParams } from "@angular/common/http";
import { Tool } from "../../../../../../shared/util/tool";

const CHECK_EMAIL_VALID_URI: string = "../api/vs/check-email-valid";
const MAIL_HISTORY_KEY = LocalStorage.MAIL_HISTORY_KEY;

@Component({
   selector: "email-dialog",
   templateUrl: "email-dialog.component.html",
})
export class EmailDialog implements OnInit {
   @Input() model: EmailDialogModel;
   @Input() exportTypes: {label: string, value: string}[] = [];
   @Input() securityEnabled: boolean = false;
   @Input() sendFunction:
      (model: EmailDialogModel, commitFn: Function, stopLoadFn: Function) => void;
   @Output() onCommit: EventEmitter<EmailDialogModel> =
      new EventEmitter<EmailDialogModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   form: UntypedFormGroup;
   historyEmails: string[];
   formValid = () => !!this.form && this.form.valid;
   showLoading: boolean = false;

   constructor(private modelService: ModelService, private modalService: NgbModal,
               private changeDetectorRef: ChangeDetectorRef)
   {
   }

   ngOnInit(): void {
      this.initForm();
      this.historyEmails = Tool.getHistoryEmails(this.model.historyEnabled);
      this.changeDetectorRef.detectChanges();
   }

   initForm(): void {
      this.form = new UntypedFormGroup({
         emailForm: new UntypedFormGroup({}),
      });
   }

   validate(): boolean {
      return false;
   }

   ok(): void {
      const params = new HttpParams()
         .set("toAddrs", this.model.emailPaneModel.toAddress)
         .set("ccAddrs", this.model.emailPaneModel.ccAddress);

      if(!this.model.fileFormatPaneModel.includeCurrent &&
         this.model.fileFormatPaneModel.selectedBookmarks.length == 0)
      {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:common.fileformatPane.notvoid)");
         return;
      }

      this.modelService.getModel<EmailValidationResponse>(CHECK_EMAIL_VALID_URI, params)
         .subscribe(
            (data: EmailValidationResponse) => {
               const type = data.messageCommand.type;

               if(type == "OK") {
                  this.showLoading = true;

                  this.sendFunction(this.model, () => {
                     this.showLoading = false;
                     this.addToHistory(data.addressHistory);
                     this.onCommit.emit(this.model);
                  }, () => {
                     this.showLoading = false;
                  });
               }
               else {
                  const message = data.messageCommand.message;
                  ComponentTool.showMessageDialog(this.modalService, ComponentTool.getDialogTitle(type),
                     message);
               }
            },
            (err) => {
               // TODO handle error
               console.error("Failed to check if export valid: ", err);
            }
         );
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   /**
    * Add the email addresses to the local history. Shared across to and cc fields
    *
    * @param {string[]} newAddresses the addresses to add to the typeahead history
    */
   private addToHistory(newAddresses: string[]): void {
      if(!this.model.historyEnabled) {
         return;
      }

      const currentHistoryModel = Tool.getHistoryEmails(this.model.historyEnabled);

      newAddresses.forEach((address) => {
         if(currentHistoryModel.indexOf(address) === -1) {
            currentHistoryModel.push(address);
         }
      });

      LocalStorage.setItem(MAIL_HISTORY_KEY, JSON.stringify(currentHistoryModel));
   }

   get emailExportTypes(): {label: string, value: string}[] {
      return this.exportTypes.filter(p => p.value != "Snapshot");
   }
}
