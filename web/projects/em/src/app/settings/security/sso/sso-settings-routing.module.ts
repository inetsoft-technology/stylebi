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
import { NgModule } from "@angular/core";
import { RouterModule, Routes } from "@angular/router";
import { SsoSettingsPageComponent } from "./sso-settings-page/sso-settings-page.component";
import { SsoSettingsResolverService } from "./sso-settings-resolver.service";

const routes: Routes = [
   {
      path: "",
      component: SsoSettingsPageComponent,
      resolve: {
         model: SsoSettingsResolverService
      }
   }
];

@NgModule({
   imports: [RouterModule.forChild(routes)],
   exports: [RouterModule],
   providers: [
      SsoSettingsResolverService
   ]
})
export class SsoSettingsRoutingModule {
}
