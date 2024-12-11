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
   HttpClient,
   HttpErrorResponse,
   HttpHeaders,
   HttpParams,
   HttpResponse
} from "@angular/common/http";
import { Injectable } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Observable, throwError } from "rxjs";
import { catchError } from "rxjs/operators";
import { ComponentTool } from "../../common/util/component-tool";

@Injectable({
   providedIn: "root"
})
export class ModelService {
   private readonly headers: HttpHeaders;
   private readonly formHeaders: HttpHeaders;
   private _errorHandler: (error: any) => boolean;

   constructor(private http: HttpClient, private modalService: NgbModal) {
      this.headers = new HttpHeaders({
         "Content-Type": "application/json",
         "X-Requested-With": "XMLHttpRequest",
         "Cache-Control": "no-cache",
         "Pragma": "no-cache"
      });
      this.formHeaders = new HttpHeaders({
         "Content-Type": "application/x-www-form-urlencoded",
         "X-Requested-With": "XMLHttpRequest"
      });
   }

   get errorHandler(): (error: any) => boolean {
      return this._errorHandler;
   }

   set errorHandler(handler: (error: any) => boolean) {
      this._errorHandler = handler;
   }

   getCurrentOrganization(): Observable<string> {
      return this.http.get<string>("../api/em/navbar/organization");

   }

   getOrgMVGlobalResource(org: string): Observable<boolean> {
      return this.http.get<boolean>("../api/portal/content/materialized-view/isOrgAccessGlobalMV/"+org);
   }

   getModel<T>(controller: string, params?: HttpParams): Observable<T> {
      const options = {
         headers: this.headers,
         params: params
      };
      return this.http.get<T>(controller, options).pipe(
         catchError((error) => this.handleError<T>(error))
      );
   }

   sendModel<T>(controller: string, model: any, params?: HttpParams): Observable<HttpResponse<T>> {
      return this.http.post<T>(controller, model, {
         headers: this.headers,
         observe: "response",
         params: params }
      ).pipe(
         catchError((error) => this.handleError<HttpResponse<T>>(error))
      );
   }

   sendModelByForm<T>(controller: string, formValue: any, params?: HttpParams): Observable<HttpResponse<T>> {
      return this.http.post<T>(controller, formValue, {
         headers: this.formHeaders,
         observe: "response",
         params: params }
      ).pipe(
         catchError((error) => this.handleError<HttpResponse<T>>(error))
      );
   }

   /**
    * Use put method to send model.
    */
   putModel<T>(controller: string, model: any, params?: HttpParams): Observable<HttpResponse<T>> {
      return this.http.put<T>(controller, model, {
         headers: this.headers,
         observe: "response",
         params: params }
      ).pipe(
         catchError((error) => this.handleError<HttpResponse<T>>(error))
      );
   }

   private handleError<T>(res: HttpErrorResponse): Observable<T> {
      let error;

      try {
         error = this.getError(res);
      }
      catch(ignore) {
      }

      let errMsg = (error && error.hasOwnProperty("message")) ? error.message :
         res.status == 403 ? "_#(js:server.error.connectionForbidden)" :
         error && !(error === "" || error instanceof ProgressEvent) ? error :
            res.status ? `${res.status} - ${res.statusText}` : "_#(js:server.error.connectionLost)";

      if(!error || !this.errorHandler || !this.errorHandler(error)) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)", errMsg);
      }

      return throwError(errMsg);
   }

   private getError(res: HttpErrorResponse): any {
      if(!res) {
         return null;
      }

      let error = res.error;

      while(error && error.error && !error.message) {
         error = error.error;
      }

      return error;
   }
}
