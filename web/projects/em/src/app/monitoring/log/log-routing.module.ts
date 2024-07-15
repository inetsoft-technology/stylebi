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
import { inject, NgModule } from "@angular/core";
import { CanActivateFn, RouterModule, Routes, UrlTree } from "@angular/router";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { LogMonitoringPageComponent } from "./log-monitoring-page/log-monitoring-page.component";
import { LogViewLinks } from "./log-view-links";

export const canActivateLogViewer: CanActivateFn = (): Observable<boolean | UrlTree> => {
   const http = inject(HttpClient);
   return http.get<LogViewLinks>("../api/em/monitoring/log/links").pipe(
      map(links => {
         if(!links.fluentdLogging || !links.logViewUrl) {
            return true;
         }

         window.open(links.logViewUrl, "_blank");
         return false;
      })
   );
};

const routes: Routes = [
   {
      path: "",
      component: LogMonitoringPageComponent,
      canActivate: [canActivateLogViewer]
      // children: [
      //
      // ]
   }
];

@NgModule({
   imports: [RouterModule.forChild(routes)],
   exports: [RouterModule]
})
export class LogRoutingModule {
}
