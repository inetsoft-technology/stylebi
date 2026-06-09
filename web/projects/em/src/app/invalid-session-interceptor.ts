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
            error => this.handleInvalidSession(req, error)
         )
      );
   }

   private handleInvalidSession(req: HttpRequest<any>, error: HttpErrorResponse): void {
      if((error.status === 401 || error.status === 403) && this.isSameOrigin(req.url)) {
         this.logoutService.sessionExpired();
      }
   }

   /**
    * Only the application's own (same-origin) responses indicate an expired
    * session. A 401/403 from an absolute URL pointing at an external service
    * (e.g. the OAuth proxy at data.inetsoft.com) is unrelated to the StyleBI
    * session and must not trigger a logout.
    */
   private isSameOrigin(url: string): boolean {
      // A non-http(s) scheme (e.g. data:, blob:, mailto:) is never one of the
      // application's own session-bearing responses; treat it as cross-origin so
      // it cannot trigger a logout.
      if(/^[a-z][a-z0-9+.-]*:/i.test(url) && !/^https?:/i.test(url)) {
         return false;
      }

      // relative path (e.g. "../api/...") is always same-origin
      if(!/^(https?:)?\/\//i.test(url)) {
         return true;
      }

      try {
         // normalize protocol-relative URLs ("//host/path") before comparing
         const absolute = url.startsWith("//") ? `${window.location.protocol}${url}` : url;
         return new URL(absolute).origin === window.location.origin;
      }
      catch(e) {
         // Malformed URL — treat as cross-origin to avoid spurious logouts.
         return false;
      }
   }
}
