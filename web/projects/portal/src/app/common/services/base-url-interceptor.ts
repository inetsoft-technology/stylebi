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
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from "@angular/common/http";
import { Observable } from "rxjs";
import { BaseHrefService } from "./base-href.service";

@Injectable({
   providedIn: "root"
})
export class BaseUrlInterceptor implements HttpInterceptor {
   baseUrl: string;

   constructor(baseHrefService: BaseHrefService) {
      this.baseUrl = baseHrefService.getTokenBaseHref();
   }

   intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
      if(this.baseUrl && !/^https?:/.test(req.url)) {
         req = req.clone({url: `${this.baseUrl}/${req.url}`});
      }

      return next.handle(req);
   }
}