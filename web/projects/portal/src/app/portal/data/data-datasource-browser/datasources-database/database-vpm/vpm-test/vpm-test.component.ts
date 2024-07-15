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
import { Component, Input } from "@angular/core";
import { VPMDefinitionModel } from "../../../../model/datasources/database/vpm/vpm-definition-model";
import { TestDataModel } from "../../../../model/datasources/database/vpm/test-data-model";
import { HttpClient, HttpErrorResponse } from "@angular/common/http";
import { VpmTestEvent } from "../../../../model/datasources/database/events/vpm-test-event";

const VPM_TEST_URI: string = "../api/data/vpm/test";

@Component({
   selector: "vpm-test",
   templateUrl: "vpm-test.component.html"
})
export class VPMTestComponent {
   @Input() vpm: VPMDefinitionModel;
   @Input() testData: TestDataModel = {
      users: [],
      roles: []
   };
   @Input() databaseName: string;
   testUserType: boolean = true;
   private _selectedUser: string;
   private _selectedRole: string;
   resultMessage: string;
   errorMessage: string;

   constructor(private httpClient: HttpClient)
   {}

   get selectedUser(): string {
      if(!this._selectedUser && !!this.testData && !!this.testData.users &&
         this.testData.users.length > 0)
      {
         this._selectedUser = this.testData.users[0]?.value;
      }

      return this._selectedUser;
   }

   set selectedUser(user: string) {
      this._selectedUser = user;
   }

   get selectedRole(): string {
      if(!this._selectedRole && !!this.testData && !!this.testData.roles &&
         this.testData.roles.length > 0)
      {
         this._selectedRole = this.testData.roles[0]?.value;
      }

      return this._selectedRole;
   }

   set selectedRole(role: string) {
      this._selectedRole = role;
   }

   test(): void {
      const type: string = this.testUserType ? "user" : "role";
      const selection: string = this.testUserType ? this.selectedUser : this.selectedRole;
      let event: VpmTestEvent = new VpmTestEvent(this.databaseName, type, selection, this.vpm);
      this.httpClient.post<string>(VPM_TEST_URI, event)
         .subscribe(
            data => {
               // Message is not interpolated properly unless this is done.
               this.resultMessage = data
                  .replace(/\\"/g, "\"")
                  .replace(/\\n/g, "\n")
                  .replace(/\\t/g, "\t");
               this.errorMessage = null;
            },
            (err: HttpErrorResponse) => {
               this.resultMessage = null;
               let error = JSON.parse(err.error);
               this.errorMessage = error.message;
               //this.notifications.danger("data.vpm.retrievingTestFailure");
            }
         );
   }
}
