/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { convertToKey } from "../../em/src/app/settings/security/users/identity-id";
import { CurrentUser } from "../../portal/src/app/portal/current-user";

interface OrganizationDomains {
   id: string;
   subDomainIds: string[];
}

@Injectable({
   providedIn: "root"
})
export class WizPortalService {
   styleBIUrl: string = "";
   wizServiceUrl: string = "";
   userId: string = "";
   domain: string = "";

   constructor(private http: HttpClient) {
      this.http.get("../api/assistant/get-stylebi-url").subscribe((url: string) => {
         this.styleBIUrl = url || "";
      });

      this.http.get<string>("../api/wiz/service-url").subscribe((url: string) => {
         this.wizServiceUrl = url || "";
      });

      this.http.get<CurrentUser>("../api/portal/get-current-user").subscribe((user: CurrentUser) => {
         this.userId = convertToKey(user.name);
      });

      this.http.get<OrganizationDomains>("../api/public/appDomains").subscribe((domains) => {
         if(domains?.id) {
            this.domain = JSON.stringify({
               domain: domains.id,
               subDomains: domains.subDomainIds ?? []
            });
         }
      });
   }
}
