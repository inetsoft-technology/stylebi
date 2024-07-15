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
import { Component, OnInit } from "@angular/core";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { AuthorizationService } from "../../../authorization/authorization.service";
import { Secured } from "../../../secured";

@Secured({
   route: "/settings/content",
   label: "Content"
})
@Component({
   selector: "em-content-settings-view",
   templateUrl: "./content-settings-view.component.html",
   styleUrls: ["./content-settings-view.component.scss"]
})
export class ContentSettingsViewComponent implements OnInit {
   readonly links = [
      {path: "/settings/content/repository", name: "repository", label: "_#(js:Repository)"},
      {path: "/settings/content/data-space", name: "data-space", label: "_#(js:Data Space)"},
      {
         path: "/settings/content/drivers-and-plugins",
         name: "drivers-and-plugins",
         label: "_#(js:Drivers and Plugins)"
      },
      {
         path: "/settings/content/materialized-views",
         name: "materialized-views",
         label: "_#(js:Materialized Views)"
      },
   ];
   visibleLinks = this.links;

   constructor(private pageTitle: PageHeaderService, private authzService: AuthorizationService) {
      authzService.getPermissions("/settings/content").subscribe((permissions) => {
         this.visibleLinks = this.links.filter((link) => permissions.permissions[link.name]);
      });
   }

   ngOnInit() {
      this.pageTitle.title = "Content Settings";
   }
}
