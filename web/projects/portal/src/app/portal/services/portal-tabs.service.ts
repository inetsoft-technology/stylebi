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
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { map, shareReplay } from "rxjs/operators";
import { PortalTab } from "../portal-tab";

const PORTAL_TABS_URI: string = "../api/portal/get-portal-tabs";

@Injectable()
export class PortalTabsService {
   private portalTabs: Observable<PortalTab[]>;

   constructor(public http: HttpClient) {
   }

   getCustomPortalTab(name: string): Observable<PortalTab> {
      return this.getPortalTabs().pipe(
         map((portalTabs) => {
            return portalTabs.filter((portalTab) => {
               return portalTab.custom && portalTab.name === name;
            })[0];
         }));
   }

   getCustomTabs(): Observable<PortalTab[]> {
      return this.getPortalTabs().pipe(
         map((portalTabs) => {
            return portalTabs.filter((portalTab) => {
               return portalTab.custom && portalTab.visible;
            });
         }));
   }

   getPortalTabs(): Observable<PortalTab[]> {
      if(!this.portalTabs) {
         this.portalTabs = this.http.get<PortalTab[]>(PORTAL_TABS_URI)
            .pipe(shareReplay(1));
      }

      return this.portalTabs;
   }
}
