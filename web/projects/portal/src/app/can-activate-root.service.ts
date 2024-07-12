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
import { DOCUMENT } from "@angular/common";
import { Inject, Injectable } from "@angular/core";
import { ActivatedRouteSnapshot, CanActivate, RouterStateSnapshot } from "@angular/router";
import { Observable } from "rxjs";
import { map, tap } from "rxjs/operators";
import { LicenseInfo } from "./common/data/license-info";
import { LicenseInfoService } from "./common/services/license-info.service";

const INVALID_LICENSE_URL = "../error/invalid-license";
const REMOTE_DEVELOPER_LICENSE = "../error/remote-developer-license";

@Injectable()
export class CanActivateRootService implements CanActivate {
   constructor(private licenseInfoService: LicenseInfoService,
               @Inject(DOCUMENT) private document: any)
   {
   }

   private isComponentAvailable(info: LicenseInfo, url: string): boolean {
      if(!info.valid) {
         return false;
      }

      return true;
   }

   canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> {
      return this.licenseInfoService.getLicenseInfo().pipe(
         map((info: LicenseInfo) => {
            if(!info.valid) {
               this.document.defaultView.location.replace(INVALID_LICENSE_URL);
               return false;
            }

            if(!info.access) {
               this.document.defaultView.location.replace(REMOTE_DEVELOPER_LICENSE);
               return false;
            }

            if(!this.isComponentAvailable(info, state.url)) {
               this.document.defaultView.location.replace(INVALID_LICENSE_URL);
               return false;
            }

            return true;
         })
      );
   }
}
