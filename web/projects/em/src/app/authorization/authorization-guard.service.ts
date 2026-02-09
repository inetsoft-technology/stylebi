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
import { Observable } from "rxjs";
import { map } from "rxjs/operators";
import {
   AiAssistantService,
   ContextType
} from "../../../../shared/ai-assistant/ai-assistant.service";
import { AuthorizationService } from "./authorization.service";

export const authorizationGuard: CanActivateFn = (next: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<boolean> => {
   const service = inject(AuthorizationService);
   const router = inject(Router);
   const aiAssistantService = inject(AiAssistantService);
   const parent: string = next.data.permissionParentPath;
   const child: string = next.data.permissionChild;

   return service.getPermissions(parent).pipe(
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
               router.navigate([uri]);
            }
         }

         aiAssistantService.loadCurrentUser(true);
         aiAssistantService.setContextTypeFieldValue(ContextType.EM);

         return allowed;
      })
   );
};
