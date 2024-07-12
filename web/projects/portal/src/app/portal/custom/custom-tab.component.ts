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
import { Component, OnInit } from "@angular/core";
import { DomSanitizer, SafeResourceUrl } from "@angular/platform-browser";
import { ActivatedRoute } from "@angular/router";
import { PortalTabsService } from "../services/portal-tabs.service";
import { PortalTab } from "../portal-tab";

@Component({
   templateUrl: "custom-tab.component.html",
   styleUrls: ["../portal-tab.component.scss", "custom-tab.component.scss"]
})
export class CustomTabComponent implements OnInit {
   tabs: PortalTab[];
   selectedIndex: number;
   //iframe source needs to be a SafeResourceUrl
   contentSource: SafeResourceUrl;

   constructor(private route: ActivatedRoute, private sanitationService: DomSanitizer,
               private portalTabsService: PortalTabsService)
   {
   }

   ngOnInit(): void {
      let name = this.route.snapshot.paramMap.get("name");

      this.portalTabsService.getCustomTabs().subscribe((tabs) => {
         this.tabs = tabs;

         for(let i = 0; i < tabs.length; i++) {
            if(tabs[i].name === name) {
               this.selectedIndex = i;
               this.openTab(tabs[i]);
               break;
            }
         }
      });
   }

   openTab(tab: PortalTab): void {
      this.contentSource =
         this.sanitationService.bypassSecurityTrustResourceUrl(tab.uri);
   }

   getTabLabel(tab: PortalTab): string {
      return tab.label;
   }
}