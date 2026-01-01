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
import { inject } from "@angular/core";
import {
   ActivatedRouteSnapshot,
   CanActivateFn,
   Router,
   RouterStateSnapshot
} from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { Tool } from "../../../../../shared/util/tool";
import { ComponentTool } from "../../common/util/component-tool";
import { PortalTabsService } from "./portal-tabs.service";

export const canTabActivate: CanActivateFn = (next: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> => {
   const portalTabsService = inject(PortalTabsService);
   const modalService = inject(NgbModal);
   const router = inject(Router);
   const tabName = routeToTabName(next.url.map( seg => seg.path).join("/"));

   return portalTabsService.getPortalTabs().pipe(
      map(tabs => {
         const result = tabs.some(tab => tab.name === tabName) || tabName == "";

         if(!result) {
            ComponentTool.showMessageDialog(modalService, "_#(js:Error)",
               Tool.formatCatalogString("_#(js:common.permit.view)", [tabName]))
               .then(() => router.navigate(["/portal"]));
         }

         return result;
      })
   );
};

const URL_TAB_MAP = {
   "tab/schedule": "Schedule",
   "tab/dashboard": "Dashboard",
   "tab/report": "Report",
   "tab/data": "Data"
};

function routeToTabName(path: string): string {
   const result = URL_TAB_MAP[path];
   return !!result ? result : "";
}
