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
import { AppInfoService } from "../../../../../../shared/util/app-info.service";
import { AuthorizationService } from "../../../authorization/authorization.service";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Secured } from "../../../secured";

@Secured({
   route: "/settings/presentation",
   label: "Presentation"
})
@Component({
   selector: "em-presentation-nav-view",
   templateUrl: "./presentation-nav-view.component.html",
   styleUrls: ["./presentation-nav-view.component.scss"]
})
export class PresentationNavViewComponent implements OnInit {
   readonly links = [
      { path: "/settings/presentation/settings", name: "settings", label: "_#(js:Global Settings)" },
      { path: "/settings/presentation/org-settings", name: "org-settings", label: "_#(js:Organization Settings)" },
      { path: "/settings/presentation/themes", name: "themes", label: "_#(js:Themes)" }
   ];
   visibleLinks = this.links;

   constructor(private pageTitle: PageHeaderService, private authzService: AuthorizationService,
               private http: HttpClient, private appInfoService: AppInfoService)
   {
      appInfoService.isEnterprise().subscribe((isEnterprise) => {
         if(isEnterprise) {
            authzService.getPermissions("/settings/presentation").subscribe(permissions => {
               this.http.get("../api/em/navbar/isMultiTenant").subscribe((isMultiTenant: boolean) =>
               {
                  this.visibleLinks = this.links.filter(link => permissions.permissions[link.name]);
                  this.renameOrgSettings(isMultiTenant);
               })
            });
         }
         else {
            this.visibleLinks = [
               { path: "/settings/presentation/settings", name: "settings", label: "_#(js:Global Settings)" }];
         }
      })
   }

   ngOnInit(): void {
      this.pageTitle.title = "_#(js:Presentation)";
   }

   renameOrgSettings(isMultiTenant: boolean): void {
      let noOrgScope = true;

      for(let i = 0; i < this.visibleLinks.length; i ++) {
         let link = this.visibleLinks[i];

         if(link.name == "org-settings") {
            if(!isMultiTenant) {
               this.visibleLinks.splice(i, 1);
               i --;
            }
            else {
               noOrgScope = false;
            }
         }
      }

      if(noOrgScope) {
         for(let i = 0; i < this.visibleLinks.length; i ++) {
            let link = this.visibleLinks[i];

            if(link.name == "settings") {
               link.label = "_#(js:Settings)"
            }
         }
      }
   }
}
