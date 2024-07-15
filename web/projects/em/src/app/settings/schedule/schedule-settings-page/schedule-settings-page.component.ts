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
import { AuthorizationService } from "../../../authorization/authorization.service";
import { PageHeaderService } from "../../../page-header/page-header.service";
import { Secured } from "../../../secured";

@Secured({
   route: "/settings/schedule",
   label: "Schedule"
})
@Component({
   selector: "em-schedule-settings-page",
   templateUrl: "./schedule-settings-page.component.html",
   styleUrls: ["./schedule-settings-page.component.scss"]
})
export class ScheduleSettingsPageComponent implements OnInit {
   links = [
      { path: "/settings/schedule/tasks", label: "_#(js:Tasks)", name: "tasks" },
      { path: "/settings/schedule/cycles", label: "_#(js:Data Cycles)", name: "cycles" },
      { path: "/settings/schedule/settings", label: "_#(js:Settings)", name: "settings" },
      { path: "/settings/schedule/status", label: "_#(js:Status)", name: "status" }
   ];
   visibleLinks = this.links;

   constructor(private pageTitle: PageHeaderService, private authzService: AuthorizationService) {
      authzService.getPermissions("/settings/schedule").subscribe((permissions) => {
         this.visibleLinks = this.links.filter((link) => permissions.permissions[link.name]);
      });
   }

   ngOnInit() {
      this.pageTitle.title = "Schedule Settings";
   }
}
