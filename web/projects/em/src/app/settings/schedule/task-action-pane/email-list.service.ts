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
import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { IdentityId } from "../../security/users/identity-id";

export interface UserEmailModel {
   userID: IdentityId;
   email: string;
   memberOf: string[];
}

export interface UserEmailsModel {
   users: UserEmailModel[];
}

export interface GroupEmailModel {
   name: string;
   label: string;
   memberOf: string[];
}

export interface EmailListModel {
   groups: GroupEmailModel[];
   users: UserEmailModel[];
}

@Injectable({
   providedIn: "root"
})
export class EmailListService {
   constructor(private http: HttpClient) {
   }

   // returns just user emails
   getUserEmails(): Observable<UserEmailsModel> {
      return this.http.get<UserEmailsModel>("../api/em/schedule/task/action/emails/user");
   }

   // returns both user and group emails
   getEmailList(): Observable<EmailListModel> {
      return this.http.get<EmailListModel>("../api/em/schedule/task/action/emails");
   }

   isEmailBrowserEnabled(): Observable<boolean> {
      return this.http.get<boolean>("../api/em/schedule/task/action/email-browser-enabled");
   }
}
