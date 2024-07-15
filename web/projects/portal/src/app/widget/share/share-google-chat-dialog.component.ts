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
import { HttpErrorResponse } from "@angular/common/http";
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable, throwError } from "rxjs";
import { catchError } from "rxjs/operators";
import { ComponentTool } from "../../common/util/component-tool";
import { ShareService } from "./share.service";

@Component({
   selector: "share-google-chat-dialog",
   templateUrl: "share-google-chat-dialog.component.html",
   styleUrls: ["share-google-chat-dialog.component.scss"]
})
export class ShareGoogleChatDialog implements OnInit {
   @Input() viewsheetId: string;
   @Input() viewsheetName: string;
   @Input() username: string;
   @Input() archive: boolean = false;
   @Input() archiveParameters: string;
   @Output() onCommit = new EventEmitter<void>();
   @Output() onCancel = new EventEmitter<void>();
   loading = false;
   form: UntypedFormGroup;
   formValid = () => !!this.form && this.form.valid;

   constructor(private shareService: ShareService, private modalService: NgbModal, fb: UntypedFormBuilder)
   {
      this.form = fb.group({
         message: ["", Validators.required]
      });
   }

   ngOnInit(): void {
      let message: string;

      if(this.viewsheetId) {
         message = `${this.username} _#(js:em.settings.share.message.dashboard) ${this.viewsheetName}.`;
      }

      this.form.get("message").setValue(message, {emitEvents: false});
   }

   ok(): void {
      if(this.form.invalid) {
         return;
      }

      this.loading = true;

      if(this.viewsheetId) {
         this.shareService.shareViewsheetInGoogleChat(this.viewsheetId, this.form.get("message").value)
            .pipe(catchError(error => this.handleError(error)))
            .subscribe(() => this.onCommit.emit());
      }
   }

   cancel(): void {
      this.onCancel.emit();
   }

   private handleError<T>(error: HttpErrorResponse): Observable<T> {
      ComponentTool.showMessageDialog(
         this.modalService, ComponentTool.getDialogTitle("ERROR"),
         "Failed to post message to chat.");
      this.loading = false;
      return throwError(error);
   }
}
