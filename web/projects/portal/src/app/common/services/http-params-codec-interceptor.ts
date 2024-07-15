/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import {
   HttpEvent,
   HttpHandler,
   HttpInterceptor, HttpParams,
   HttpRequest
} from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { UriCodec } from "../util/uri-codec";

@Injectable()
export class HttpParamsCodecInterceptor implements HttpInterceptor {
   intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
      if(req.params) {
         const params = req.params.keys().reduce<HttpParams>((keyParams, key) => {
            return req.params.getAll(key).reduce<HttpParams>((valueParams, value) => {
               return valueParams.append(key, value);
            }, keyParams);
         }, new HttpParams({encoder: new UriCodec()}));
         req = req.clone({
            params: params
         });
      }

      return next.handle(req);
   }
}