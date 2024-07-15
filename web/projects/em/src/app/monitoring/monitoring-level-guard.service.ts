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
import {
   ActivatedRouteSnapshot, CanActivate, Router,
   RouterStateSnapshot
} from "@angular/router";
import { MonitorLevel, MonitorLevelService } from "./monitor-level.service";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";

@Injectable()
export class MonitoringLevelGuard implements CanActivate {
   constructor(private monitorLevelService: MonitorLevelService, private router: Router) {
   }

   canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> {
      let isException = state.url == "/monitoring/exceptions";

      return this.monitorLevelService.monitorLevelForGuard().pipe(
         map((level: number) => {
            if(level <= MonitorLevel.OFF || isException && level <= MonitorLevel.MEDIUM) {
               this.router.navigate(["monitoring/monitoringoff"]);
            }

            return true;
         })
      );
   }
}