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
import { Injectable } from "@angular/core";
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { ComponentTool } from "../../common/util/component-tool";
import { PortalTab } from "../portal-tab";
import { PortalTabsService } from "./portal-tabs.service";

const URL_TAB_MAP = {
   "tab/schedule": "Schedule",
   "tab/dashboard": "Dashboard",
   "tab/report": "Report",
   "tab/data": "Data"
};

const DASHBOARD_TAB_ERROR_MSG = "_#(js:em.security.permit.view)";

@Injectable()
export class CanTabActivateService implements CanActivate {

   constructor(private portalTabsService: PortalTabsService,
               private router: Router,
               private modalService: NgbModal)
   {
      this.portalTabsObs = this.portalTabsService.getPortalTabs();
   }

   /**
    *
    * @param {ActivatedRouteSnapshot} route
    * @param {RouterStateSnapshot} state
    * @returns {Observable<boolean>} returns true if specified tab name appears indicating
    * that the user has permission to access the tab since otherwise it would not be there.
    */
   canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> {
      let tabName = this.routeToTabName(route.url.map( seg => seg.path).join("/"));

      return this.portalTabsObs.pipe(
         map(tabs => {
            let result = tabs.some(tab => tab.name === tabName) || tabName == "";

            if(!result) {
               ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
               ("_#(js:common.permit.view)" + "_*" + tabName))
                  .then(() => this.router.navigate(["/portal"]));
            }

            return result;
         })
      );
   }

   /**
    *
    * @param {string} path
    * @returns {string} name of tab that needs permission (to appear in portaltabs), if
    * there is no tab then it returns empty string to indicate none needed
    */
   routeToTabName(path: string): string {
      const result = URL_TAB_MAP[path];
      return !!result ? result : "";
   }

   portalTabsObs: Observable<PortalTab[]>;
}
