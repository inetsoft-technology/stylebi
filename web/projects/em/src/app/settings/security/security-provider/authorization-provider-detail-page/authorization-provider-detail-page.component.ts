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
import { HttpClient } from "@angular/common/http";
import { Component, OnInit } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";
import { ActivatedRoute } from "@angular/router";
import { Observable } from "rxjs";
import { ContextHelp } from "../../../../context-help";
import { Searchable } from "../../../../searchable";
import { ProviderDetailPage } from "../provider-detail-page";
import { AuthorizationProviderModel } from "../security-provider-model/authorization-provider-model";
import { SecurityProviderService } from "../security-provider.service";

const GET_PROVIDER_INFO = "../api/em/security/get-authorization-provider/";

@Searchable({
   title: "Add Authorization Provider",
   route: "/settings/security/provider/show-authorization-provider",
   keywords: [
      "em.settings.add", "em.settings.edit", "em.settings.authorization",
      "em.settings.provider"
   ]
})
@ContextHelp({
   route: "/settings/security/provider/show-authorization-provider",
   link: "EMSettingsSecurityProviderAuthorization"
})
@Component({
   selector: "em-authorization-provider-detail-page",
   templateUrl: "./authorization-provider-detail-page.component.html",
   styleUrls: ["./authorization-provider-detail-page.component.scss"]
})
export class AuthorizationProviderDetailPageComponent extends ProviderDetailPage implements OnInit {
   form: UntypedFormGroup;
   model: Observable<AuthorizationProviderModel>;
   // Original provider name when page was opened
   name: string;

   constructor(private providerService: SecurityProviderService,
               private route: ActivatedRoute,
               private http: HttpClient,
               dialog: MatDialog)
   {
      super(dialog);
   }

   ngOnInit() {
      this.route.queryParams.subscribe((params) => {
         this.name = params.providerName;

         if(this.name) {
            this.model = this.http.get<AuthorizationProviderModel>(GET_PROVIDER_INFO + this.name);
         }
      });
   }

   submit(form: UntypedFormGroup) {
      this.providerService.updateAuthorizationProvider(this.name, form)
                          .add(() => this.onChanged(false));
   }
}
