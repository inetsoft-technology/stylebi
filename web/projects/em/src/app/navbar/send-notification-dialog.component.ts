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
import { Component, HostListener, OnInit } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators, FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatDialogRef, MatDialogContent, MatDialogActions, MatDialogClose } from "@angular/material/dialog";
import { Secured } from "../secured";
import { MatButton } from "@angular/material/button";

import { MatInput } from "@angular/material/input";
import { MatFormField, MatLabel, MatError } from "@angular/material/form-field";
import { ModalHeaderComponent } from "../common/util/modal-header/modal-header.component";
import { NgIf } from "@angular/common";

@Secured({
   route: "/notification",
   label: "Notification",
   hiddenForMultiTenancy: true
})
@Component({
    selector: "em-send-notification-dialog",
    templateUrl: "./send-notification-dialog.component.html",
    styleUrls: ["./send-notification-dialog.component.scss"],
    imports: [NgIf, ModalHeaderComponent, MatDialogContent, FormsModule, ReactiveFormsModule, MatFormField, MatLabel, MatInput, MatError, MatDialogActions, MatButton, MatDialogClose]
})
export class SendNotificationDialogComponent implements OnInit {
   form: UntypedFormGroup;

   constructor(private dialog: MatDialogRef<SendNotificationDialogComponent>, fb: UntypedFormBuilder) {
      this.form = fb.group({
         message: ["", Validators.required]
      });
   }

   ngOnInit() {
   }

   @HostListener("window:keyup.esc", [])
   onKeyUp() {
      this.cancel();
   }

   cancel(): void {
      this.dialog.close();
   }
}
