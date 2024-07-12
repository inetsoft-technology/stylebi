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
import { Injectable } from "@angular/core";
import { ActivatedRouteSnapshot, Resolve, RouterStateSnapshot } from "@angular/router";
import { Observable } from "rxjs";
import { publishReplay, refCount } from "rxjs/operators";
import { SetPrincipalCommand } from "../../vsobjects/command/set-principal-command";
import { ModelService } from "../../widget/services/model.service";

export const VIEWSHEET_PRINCIPAL_URI = "../api/viewsheet/get-principal";

@Injectable()
export class PrincipalResolver implements Resolve<SetPrincipalCommand> {
   private command: Observable<SetPrincipalCommand>;

   constructor(private service: ModelService) {
   }

   resolve(route: ActivatedRouteSnapshot,
           state: RouterStateSnapshot): Observable<SetPrincipalCommand>
   {
      if(!this.command) {
         this.command = this.service.getModel<SetPrincipalCommand>(VIEWSHEET_PRINCIPAL_URI).pipe(
            publishReplay(1),
            refCount()
         );
      }

      return this.command;
   }
}