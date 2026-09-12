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
import { Routes } from "@angular/router";
import { canComponentDeactivate } from "../../../../../../shared/util/guard/can-component-deactivate.service";
import { ErrorHandlerService } from "../../../common/util/error/error-handler.service";
import { SecurityProviderService } from "./security-provider.service";
import { AuthenticationProviderDetailPageComponent } from "./authentication-provider-detail-page/authentication-provider-detail-page.component";
import { AuthorizationProviderDetailPageComponent } from "./authorization-provider-detail-page/authorization-provider-detail-page.component";
import { SecurityProviderPageComponent } from "./security-provider-page/security-provider-page.component";

export const SECURITY_PROVIDER_ROUTES: Routes = [
   {
      path: "",
      providers: [SecurityProviderService, ErrorHandlerService],
      children: [
         {
            path: "show-authorization-provider",
            component: AuthorizationProviderDetailPageComponent,
            canDeactivate: [canComponentDeactivate]
         },
         {
            path: "show-authentication-provider",
            component: AuthenticationProviderDetailPageComponent,
            canDeactivate: [canComponentDeactivate]
         },
         {
            path: "",
            component: SecurityProviderPageComponent
         }
      ]
   }
];
