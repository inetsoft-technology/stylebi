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

import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from "@angular/common/http";
import { Injectable, Injector } from "@angular/core";
import { Observable, from, throwError } from "rxjs";
import { catchError, switchMap } from "rxjs/operators";
import { AiAssistantService } from "../ai-assistant.service";
import { AssistantApiService } from "./assistant-api.service";

/**
 * Intercepts 401 responses from the assistant server, refreshes the bearer token,
 * and retries the original request once. Handles JWT expiry transparently.
 *
 * Uses lazy Injector.get() to avoid the circular DI cycle that would arise from
 * injecting AssistantApiService in the constructor (AssistantApiService → HttpClient
 * → HTTP_INTERCEPTORS → this interceptor → AssistantApiService).
 */
@Injectable()
export class AssistantAuthInterceptor implements HttpInterceptor {
   constructor(private injector: Injector) {}

   intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
      const assistantService = this.injector.get(AiAssistantService);
      const apiService = this.injector.get(AssistantApiService);
      const baseUrl = assistantService.chatAppServerUrl.replace(/\/$/, "");

      // Only intercept requests going to the assistant server.
      if(!baseUrl || !req.url.startsWith(baseUrl)) {
         return next.handle(req);
      }

      return next.handle(req).pipe(
         catchError(err => {
            if(err instanceof HttpErrorResponse && err.status === 401) {
               apiService.clearToken();

               return from(apiService.loadToken()).pipe(
                  switchMap(newToken => {
                     if(!newToken) {
                        return throwError(() => err);
                     }

                     const retried = req.clone({
                        headers: req.headers.set("Authorization", `Bearer ${newToken}`)
                     });

                     return next.handle(retried);
                  })
               );
            }

            return throwError(() => err);
         })
      );
   }
}
