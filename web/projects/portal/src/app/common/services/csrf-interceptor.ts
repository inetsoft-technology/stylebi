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
import { DOCUMENT, ɵparseCookieValue as parseCookieValue } from "@angular/common";
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from "@angular/common/http";
import { Inject, Injectable } from "@angular/core";
import { Observable } from "rxjs";

const COOKIE_NAME = "XSRF-TOKEN";
const HEADER_NAME = "X-XSRF-TOKEN";

/**
 * Handles setting the CSRF token in the HTTP request headers. We're not using Angular's provided
 * module because it ignores non-mutating requests (e.g. GET, HEAD).
 */
@Injectable()
export class CsrfInterceptor implements HttpInterceptor {
   private _token: string = null;
   private parsedCookies: string = null;

   constructor(@Inject(DOCUMENT) private document: any) {
   }

   intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
      const token = this.token;

      if(token !== null && !req.headers.has(HEADER_NAME)) {
         req = req.clone({headers: req.headers.set(HEADER_NAME, token)});
      }

      return next.handle(req);
   }

   private get token(): string {
      const cookies = this.document.cookie || "";

      if(this.parsedCookies !== cookies) {
         this._token = parseCookieValue(cookies, COOKIE_NAME);
         this.parsedCookies = cookies;
      }

      return this._token;
   }
}