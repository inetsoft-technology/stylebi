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
import { Injectable } from "@angular/core";
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from "@angular/router";
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import { AiAssistantService } from "../../../../shared/ai-assistant/ai-assistant.service";
import { AuthorizationService } from "./authorization.service";

@Injectable()
export class AuthorizationGuard implements CanActivate {
   constructor(private service: AuthorizationService,
               private router: Router,
               private aiAssistantService: AiAssistantService)
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
            }

            this.aiAssistantService.loadCurrentUser(true);
            this.aiAssistantService.context = "em";

            return allowed;
         })
      );
   }
}
