/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpErrorResponse } from "@angular/common/http";
import { Injectable, NgZone } from "@angular/core";
import { Router } from "@angular/router";
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

@Injectable()
export class ServiceUnavailableInterceptor implements HttpInterceptor {
   private redirected = false;

   constructor(private router: Router, private zone: NgZone) {
   }

   intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
      this.redirected = false;

      if(req.url.endsWith("/ping")) {
         return next.handle(req);
      }

      return next.handle(req).pipe(
         catchError((error: HttpErrorResponse) => {
            if(!this.redirected && (error.status === 503 || error.status == 502)) {
               this.redirected = true;

               this.zone.run(() => {
                  this.router.navigate(['/reload'],
                     {queryParams: { redirectTo: this.router.url }, replaceUrl: true})
               });
            }

            return throwError(error);
         })
      );
   }
}
