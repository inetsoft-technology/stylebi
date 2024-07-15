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
import {
   HttpErrorResponse,
   HttpEvent,
   HttpHandler,
   HttpInterceptor,
   HttpRequest
} from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { tap } from "rxjs/operators";
import { LogoutService } from "../../../shared/util/logout.service";

@Injectable()
export class InvalidSessionInterceptor implements HttpInterceptor {

   constructor(private logoutService: LogoutService) {
   }

   intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
      if(req.method === "GET" && !req.headers.has("X-Requested-With")) {
         req = req.clone({headers: req.headers.set("X-Requested-With", "XMLHttpRequest")});
      }

      return next.handle(req).pipe(
         tap(
            () => {},
            error => this.handleInvalidSession(error)
         )
      );
   }

   private handleInvalidSession(error: HttpErrorResponse): void {
      if(error.status === 401 || error.status === 403) {
         this.logoutService.sessionExpired();
      }
   }
}
