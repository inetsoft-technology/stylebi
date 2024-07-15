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
   HttpInterceptor,
   HttpRequest,
   HttpResponse
} from "@angular/common/http";
import { Injectable } from "@angular/core";
import { Observable} from "rxjs";
import { publishReplay, refCount, tap } from "rxjs/operators";

@Injectable()
export class HttpDebounceInterceptor implements HttpInterceptor {
   private readonly requests = new Map<string, Observable<HttpEvent<any>>>();

   intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
      if(req.method !== "GET") {
         return next.handle(req);
      }

      const key = req.urlWithParams;

      if(this.requests.has(key)) {
         return this.requests.get(key);
      }

      const result = next.handle(req).pipe(
         tap(
            (event) => {
               if(event instanceof HttpResponse) {
                  this.requests.delete(key);
               }
            },
            () => this.requests.delete(key)
         ),
         publishReplay(1),
         refCount()
      );

      this.requests.set(key, result);
      return result;
   }
}
