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
import { Component, OnInit } from "@angular/core";
import { MatDialog } from "@angular/material/dialog";
import { ContextHelp } from "../context-help";
import { Searchable } from "../searchable";
import { ChangePasswordService } from "./change-password.service";
import { PageHeaderService } from "../page-header/page-header.service";
import { MessageDialog, MessageDialogType } from "../common/util/message-dialog";

@Searchable({
   route: "/password",
   title: "Change Password",
   keywords: []
})
@ContextHelp({
   route: "/password",
   link: "EMPassword"
})
@Component({
   selector: "em-password",
   templateUrl: "./password.component.html",
   styleUrls: ["./password.component.scss"]
})
export class PasswordComponent implements OnInit {
   loading = false;

   constructor(private dialog: MatDialog,
               private pageTitle: PageHeaderService,
               private service: ChangePasswordService)
   {
   }

   ngOnInit() {
      this.pageTitle.title = "_#(js:Change Password)";
   }

   changePassword(result: {oldPwd: string; newPwd: string}): void {
      const oldPassword = result.oldPwd;
      const password = result.newPwd;

      this.loading = true;
      this.service.verifyOldPassword(oldPassword).subscribe((verifyResult) => {
         this.loading = false;

         if(verifyResult) {
            this.doChangePassword(password);
         }
         else {
            this.service.notify("_#(js:em.changePassword.mustMatch)");
         }
      }, (error) => {
         console.error("Failed to verify old password: ", error);
         this.service.notify("_#(js:em.changePassword.matchOldPwdError)");
         this.loading = false;
      });
   }

   private doChangePassword(password: string): void {
      this.dialog.open(MessageDialog, {
         data: {
            title: "_#(js:em.security.userPasswordChangedTitle)",
            content: "_#(js:em.security.userPasswordChanged)",
            type: MessageDialogType.CONFIRMATION
         }
      }).afterClosed().subscribe(val => {
         if(val) {
            this.loading = true;
            this.service.changePassword(password).subscribe(
               (success) => {
                  if(success) {
                     this.service.notify("_#(js:em.changePassword.success)");
                     window.open("../logout?fromEm=true", "_self");
                  } else {
                     this.service.notify("_#(js:em.changePassword.failure)");
                  }

                  this.loading = false;
               },
               (error) => {
                  console.error("Failed to change password: ", error);
                  this.service.notify("_#(js:em.changePassword.error)");
                  this.loading = false;
               }
            );
         }
      });
   }
}
