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
import { HttpErrorResponse } from "@angular/common/http";
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable, throwError } from "rxjs";
import { catchError } from "rxjs/operators";
import { ComponentTool } from "../../common/util/component-tool";
import { ShareEmailModel } from "./share-email-model";
import { ShareService } from "./share.service";
import { GuiTool } from "../../common/util/gui-tool";
import { Tool } from "../../../../../shared/util/tool";

@Component({
   selector: "share-email-dialog",
   templateUrl: "./share-email-dialog.component.html",
   styleUrls: ["./share-email-dialog.component.scss"]
})
export class ShareEmailDialogComponent implements OnInit {
   @Input() viewsheetId: string;
   @Input() viewsheetName: string;
   @Input() username: string;
   @Input() archive: boolean = false;
   @Input() archiveParameters: string;
   @Output() onCommit = new EventEmitter<void>();
   @Output() onCancel = new EventEmitter<void>();

   model: ShareEmailModel;
   historyEmails: string[];
   loading = false;
   form: UntypedFormGroup;
   formValid = () => !!this.form && this.form.valid;
   isIE = GuiTool.isIE();

   constructor(private shareService: ShareService, private modalService: NgbModal, fb: UntypedFormBuilder)
   {
      this.form = fb.group({
         emailForm: fb.group({})
      });
   }

   ngOnInit() {
      this.shareService.getEmailModel().subscribe(model => {
         this.model = model;

         if(this.viewsheetId) {
            if(this.username.endsWith(" - Host Organization")) {
               this.username = this.username.substring(0, this.username.length - 20);
            }

            this.model.emailModel.message =
               `${this.username} _#(js:em.settings.share.message.dashboard) ${this.viewsheetName}.`;

            if(!this.isIE) {
               this.model.emailModel.message = "<p>" + this.model.emailModel.message + "</p>";
            }
         }

         this.historyEmails = Tool.getHistoryEmails(this.model.historyEnabled);
      });
   }

   ok(): void {
      if(this.form.invalid) {
         return;
      }

      this.loading = true;
      const subject = this.model.emailModel.subject;
      const recipients = this.model.emailModel.toAddress.split(",");
      const message = this.model.emailModel.message;
      const ccs = this.model.emailModel.ccAddress.split(",");
      const bccs = this.model.emailModel.bccAddress.split(",");

      if(this.viewsheetId) {
         this.shareService.shareViewsheetInEmail(this.viewsheetId, recipients, subject, message,
            ccs, bccs)
            .pipe(catchError(error => this.handleError(error, this.model.emailModel.toAddress)))
            .subscribe(() => {
               ComponentTool.showMessageDialog(
                  this.modalService, ComponentTool.getDialogTitle("INFO"),
                  "_#(js:viewer.viewsheet.email.successful)");
               this.onCommit.emit();
            });
      }
   }

   cancel(): void {
      this.onCancel.emit();
   }

   private handleError<T>(error: HttpErrorResponse, toAddress: string): Observable<T> {
      ComponentTool.showMessageDialog(
         this.modalService, ComponentTool.getDialogTitle("ERROR"),
         "_#(js:common.mail.sendFailed)" + error.error.message + "_*" + toAddress);
      this.loading = false;
      return throwError(error);
   }
}
