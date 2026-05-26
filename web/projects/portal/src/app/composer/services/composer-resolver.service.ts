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
import { HttpClient } from "@angular/common/http";
import { inject } from "@angular/core";
import { ActivatedRouteSnapshot, ResolveFn, Router, RouterStateSnapshot } from "@angular/router";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable, throwError } from "rxjs";
import { catchError, map } from "rxjs/operators";
import { ComponentTool } from "../../common/util/component-tool";
import { SetPrincipalCommand } from "../../vsobjects/command/set-principal-command";

const NO_PERMISSION_ERROR = "_#(js:composer.authorization.permissionDenied)";

export const composerResolver: ResolveFn<SetPrincipalCommand> = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<SetPrincipalCommand> => {
   const http = inject(HttpClient);
   const modalService = inject(NgbModal);
   const router = inject(Router);

   return http.get<SetPrincipalCommand>("../api/viewsheet/get-principal").pipe(
      catchError((error) =>  {
         return throwError(error);
      })
   ).pipe(map((data: SetPrincipalCommand) => {
      if(!data.worksheetPermission && !data.viewsheetPermission && !data.tableStylePermission &&
         !data.scriptPermission) {
         ComponentTool.showMessageDialog(modalService, "_#(js:Error)", NO_PERMISSION_ERROR)
            .then(() => router.navigate(["/portal/tab/report"]));
      }

      return data;
   }));
}