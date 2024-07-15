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
import { MatTableDataSource } from "@angular/material/table";
import { EmailListService, UserEmailModel } from "../../email-list.service";


@Component({
   selector: "em-burst-email-embedded-pane",
   templateUrl: "./burst-email-embedded-pane.component.html",
   styleUrls: ["./burst-email-embedded-pane.component.scss"]
})
export class BurstEmailEmbeddedPaneComponent implements OnInit {
   @Input() emails: string;
   @Output() emailsChange = new EventEmitter<string>();
   selectedUsers: UserEmailModel[] = [];
   users: UserEmailModel[];
   tableDataSource = new MatTableDataSource<UserEmailModel>();
   displayedColumns: string[] = ["name", "email"];

   constructor(private emailListService: EmailListService) {
   }

   ngOnInit() {
      this.emailListService.getUserEmails().subscribe(model => {
         this.users = model.users;
      });

      if(this.users) {
         this.sortUsers();
      }

      if(!!this.emails) {
         const items: string[] = this.emails.split(":");

         if(this.emails.endsWith(":")) {
            items.pop();
         }

         let user: string = items[0];
         let userIdx = this.users.findIndex(u => u.userID.name == user);
         let organization = userIdx >= 0 ? this.users[userIdx].userID.organization : null;

         for(let i = 1; i < items.length; i++) {
            const addr: string = items[i];

            if(i == items.length - 1 && !this.emails.endsWith(":")) {
               this.selectedUsers.push(<UserEmailModel> {userID: {name: user, organization: organization}, email: addr});
            }
            else {
               const email = addr.substring(0, addr.lastIndexOf(","));
               this.selectedUsers.push(<UserEmailModel> {userID: {name: user, organization: organization}, email: email});
               user = addr.substring(addr.lastIndexOf(",") + 1);
            }
         }

         if(this.emails.endsWith(":")) {
            this.selectedUsers.push(<UserEmailModel> {userID: {name: user, organization: organization}, email: null});
         }

         this.tableDataSource.data = this.selectedUsers;
      }
   }

   // add selected users to table and output selected emails to parent component
   addSelectedUsers() {
      this.tableDataSource.data = this.selectedUsers;
      this.emitEmails();
   }

   // outputting selected users to parent component
   private emitEmails() {
      if(this.selectedUsers.length) {
         this.emailsChange.emit(this.selectedUsers.map(user => (<UserEmailModel>user).userID.name + ":" + (<UserEmailModel>user).email).join(","));
      }
   }

   // puts the inputted email in its spot
   inputEmail(email: string, name: string) {
      for(let user of this.selectedUsers) {
         if(user.userID.name === name) {
            user.email = email;
            this.emitEmails();
            break;
         }
      }
   }

   // sort all-users list
   sortUsers() {
      this.users.sort((x, y) => x.userID.name.localeCompare(y.userID.name));
   }

   // for multiple selects since we won't be using the same initial array to fill in selected
   checkEquality(a: UserEmailModel, b: UserEmailModel): boolean {
      return a.userID.name === b.userID.name;
   }
}
