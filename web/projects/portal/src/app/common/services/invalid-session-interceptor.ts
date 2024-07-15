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
import { Injectable, OnDestroy } from "@angular/core";
import { NEVER, Observable, Subject, throwError } from "rxjs";
import { catchError, takeUntil } from "rxjs/operators";
import { LogoutService } from "../../../../../shared/util/logout.service";

@Injectable()
export class InvalidSessionInterceptor implements HttpInterceptor {

   constructor(private logoutService: LogoutService) {
   }

   intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
      return next.handle(req).pipe(
         catchError(
            error => this.handleInvalidSession(error)
         )
      );
   }

   private handleInvalidSession(error: HttpErrorResponse): Observable<any> {
      if(error.status === 401) {
         // invalid session, redirect to login
         this.logoutService.sessionExpired();
         return NEVER;
      }
      else {
         return throwError(error);
      }
   }
}
