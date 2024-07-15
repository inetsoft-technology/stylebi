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
import { Injectable } from "@angular/core";
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from "@angular/router";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { AuthorizationService } from "./authorization.service";
import { MatDialog, MatDialogConfig } from "@angular/material/dialog";
import { MessageDialog, MessageDialogType } from "../common/util/message-dialog";

@Injectable()
export class AuthorizationGuard implements CanActivate {
   constructor(private service: AuthorizationService, private router: Router,
               private dialog: MatDialog, private http: HttpClient)
   {
   }

   canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> {
      const parent: string = route.data.permissionParentPath;
      const child: string = route.data.permissionChild;

      return this.service.getPermissions(parent).pipe(
         map( p => [p.permissions, p.labels, p.multiTenancyHiddenComponents]),
         map(([p, l, h]) => {
            const allowed = !!p[child];

            if(!allowed) {
               // find first permitted child and redirect to that
               const redirect = Object.keys(p).find(name => p[name] === true && name != "notification");

               if(redirect) {
                  //force orgAdmin redirect to a page that is not external logs
                  const monitoringRedirect = Object.keys(p).find(name => p[name] === true && name === "queries");
                  const usersRedirect = Object.keys(p).find(name => p[name] === true && name === "users");

                  const uri = parent ? monitoringRedirect ? `${parent}/${monitoringRedirect}`
                           : usersRedirect ? `${parent}/${usersRedirect}`
                           :`${parent}/${redirect}` : redirect;
                  this.router.navigate([uri]);
               }

               this.http.get("../api/em/navbar/isMultiTenant").subscribe((isMultiTenant: boolean) =>
               {
                  if(isMultiTenant == false || parent != "settings") {
                     if(!h[child]) {
                        this.dialog.open(MessageDialog, <MatDialogConfig>{
                           width: "350px",
                           data: {
                              title: "_#(js:Error)",
                              content: "_#(js:em.security.permit.view) " + (parent? parent: child) +
                                 (redirect ? ". _#(js:em.security.permit.redirect) " + l[redirect] : ""),
                              type: MessageDialogType.ERROR
                           }
                        });
                     }
                  }
               })
            }

            return allowed;
         })
      );
   }
}
