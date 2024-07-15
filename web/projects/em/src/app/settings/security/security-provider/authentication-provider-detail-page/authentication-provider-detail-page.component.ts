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
import { Component, OnInit } from "@angular/core";
import { UntypedFormGroup } from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";
import { ActivatedRoute } from "@angular/router";
import { Observable } from "rxjs";
import { ErrorHandlerService } from "../../../../common/util/error/error-handler.service";
import { ContextHelp } from "../../../../context-help";
import { Searchable } from "../../../../searchable";
import { ProviderDetailPage } from "../provider-detail-page";
import { AuthenticationProviderModel } from "../security-provider-model/authentication-provider-model";
import { SecurityProviderService } from "../security-provider.service";
import { Tool } from "../../../../../../../shared/util/tool";

const GET_PROVIDER_INFO = "../api/em/security/get-authentication-provider/";

@Searchable({
   title: "Add Authentication Provider",
   route: "/settings/security/provider/show-authentication-provider",
   keywords: [
      "em.settings.add", "em.settings.edit", "em.settings.authentication", "em.settings.provider"
   ]
})
@ContextHelp({
   route: "/settings/security/provider/show-authentication-provider",
   link: "EMSettingsSecurityProviderAuthentication"
})
@Component({
   selector: "em-authentication-provider-detail-page",
   templateUrl: "./authentication-provider-detail-page.component.html",
   styleUrls: ["./authentication-provider-detail-page.component.scss"]
})
export class AuthenticationProviderDetailPageComponent extends ProviderDetailPage
   implements OnInit
{
   form: UntypedFormGroup;
   model: Observable<AuthenticationProviderModel>;
   // Original provider name when page was opened
   name: string;
   isMultiTenant: boolean;

   constructor(private providerService: SecurityProviderService,
               private errorService: ErrorHandlerService,
               private route: ActivatedRoute,
               private http: HttpClient,
               dialog: MatDialog)
   {
      super(dialog);
   }

   ngOnInit() {
      this.http.get<boolean>("../api/em/navbar/isMultiTenant")
         .subscribe(isMultiTenant => {
            this.isMultiTenant = isMultiTenant;

            this.route.queryParams.subscribe((params) => {

               this.name = params.providerName;
               if(this.name) {
                  this.model = this.http.get<AuthenticationProviderModel>(GET_PROVIDER_INFO + Tool.byteEncodeURLComponent(this.name));
               }
            });
         });
   }

   submit(form: UntypedFormGroup) {
      this.providerService.updateAuthenticationProvider(this.name, form)
                          .add(() => this.onChanged(false));
   }
}
