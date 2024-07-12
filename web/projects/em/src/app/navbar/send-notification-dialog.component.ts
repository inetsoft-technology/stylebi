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
import { Component, HostListener, OnInit } from "@angular/core";
import { UntypedFormBuilder, UntypedFormGroup, Validators } from "@angular/forms";
import { MatDialogRef } from "@angular/material/dialog";
import { Secured } from "../secured";

@Secured({
   route: "/notification",
   label: "Notification",
   hiddenForMultiTenancy: true
})
@Component({
   selector: "em-send-notification-dialog",
   templateUrl: "./send-notification-dialog.component.html",
   styleUrls: ["./send-notification-dialog.component.scss"]
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
