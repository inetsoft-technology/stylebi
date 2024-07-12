/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { HttpClient, HttpHeaders } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { interval, Observable, of as observableOf, throwError } from "rxjs";
import { first, map, mergeMap, tap, timeoutWith } from "rxjs/operators";

export interface OAuthTokens {
   accessToken: string;
   refreshToken: string;
   issued: string;
   expiration: string;
   scope: string;
   properties: {[name: string]: any};
   method: string;
}

interface Tokens {
   accessToken: string;
   refreshToken: string;
   issued: number;
   expiration: number;
   scope?: string;
   properties?: {[name: string]: any};
}

export interface OAuthParameters {
   license: string;
   serviceName?: string;
   user?: string;
   password?: string;
   clientId?: string;
   clientSecret?: string;
   scope?: string[];
   authorizationUri?: string;
   tokenUri?: string;
   flags?: string[];
   additionalParameters?: {[name: string]: string};
   method?: string;
}

interface LoginResponse {
   token: string;
}

interface CreateJobResponse {
   authorizationPageUrl: string;
   dataUrl: string;
}

interface AuthorizationJob extends CreateJobResponse {
   jwt: string;
}

interface AuthorizationResponse {
   accessToken?: string;
   refreshToken?: string;
   issued?: string;
   expiration?: string;
   scope?: string;
   properties?: {[name: string]: any};
   errorType?: string;
   errorMessage?: string;
   errorUri?: string;
}

interface AuthorizationResult extends AuthorizationResponse {
   complete: boolean;
}

const GRANT_PASSWORD_URI = "../api/portal/data/datasources/grant-password";

@Injectable({
   providedIn: "root"
})
export class OAuthAuthorizationService {
   constructor(private http: HttpClient) {
   }

   authorize(parameters: OAuthParameters): Observable<OAuthTokens> {
      if(OAuthAuthorizationService.hasValidParamsForPasswordGrantAuth(parameters)) {
         return this.doPasswordGrantAuth(parameters);
      }

      if(!OAuthAuthorizationService.isValid(parameters)) {
         return throwError({
            error: "parameters",
            errorMessage: "One or more OAuth parameters is missing",
            errorUri: null
         });
      }

      return this.login(parameters.license)
         .pipe(
            mergeMap(jwt => this.createJob(jwt, parameters)),
            tap(job => window.open(job.authorizationPageUrl, "_blank")),
            mergeMap(job =>
               interval(1000).pipe( // poll every one second
                  mergeMap(() => this.getResult(job)), // poll the server for the result
                  first(result => result.complete), // stop when the job is complete
                  timeoutWith(330000, observableOf<AuthorizationResult>({ // timeout after 5.5 minutes
                     complete: true,
                     errorType: "timeout",
                     errorMessage: "Timed out waiting for authorization",
                     errorUri: null
                  }))
               )),
            mergeMap(response => {
               if(response.errorType) {
                  return throwError({
                     error: response.errorType,
                     errorMessage: response.errorMessage,
                     errorUri: response.errorUri
                  });
               }
               else {
                  return observableOf<OAuthTokens>({
                     ...response,
                     accessToken: response.accessToken,
                     refreshToken: response.refreshToken,
                     issued: response.issued,
                     expiration: response.expiration,
                     scope: response.scope,
                     properties: response.properties,
                     method: parameters.method
                  });
               }
            })
         );
   }

   private doPasswordGrantAuth(parameters: OAuthParameters): Observable<OAuthTokens> {
      const params = {...parameters};
      delete params.serviceName;
      delete params.method;

      return this.http.post<Tokens>(GRANT_PASSWORD_URI, params)
         .pipe(map(response => {
            let expiration = null;

            if(response.expiration > 0) {
               expiration = new Date(response.expiration).toISOString();
            }

            return <OAuthTokens> {
               accessToken: response.accessToken,
               scope: response.scope,
               refreshToken: response.refreshToken,
               expiration,
               method: parameters.method
            };
         }));
   }

   private login(license: string): Observable<string> {
      const headers = new HttpHeaders()
         .set("Content-Type", "application/json")
         .set("Accept", "application/json");
      const options = {headers};
      const request = {license};
      return this.http.post<LoginResponse>("https://data.inetsoft.com/login", request, options)
         .pipe(map(response => response.token));
   }

   private createJob(jwt: string, parameters: OAuthParameters): Observable<AuthorizationJob> {
      const headers = new HttpHeaders()
         .set("Content-Type", "application/json")
         .set("Accept", "application/json")
         .set("Authorization", `Bearer ${jwt}`);
      const options = {headers};
      const request = {
         license: parameters.license,
         serviceName: parameters.serviceName,
         clientId: parameters.clientId,
         clientSecret: parameters.clientSecret,
         scope: parameters.scope,
         authorizationUri: parameters.authorizationUri,
         tokenUri: parameters.tokenUri,
         flags: parameters.flags,
         additionalParameters: parameters.additionalParameters
      };

      return this.http.post<CreateJobResponse>("https://data.inetsoft.com/job", request, options)
         .pipe(map(response => Object.assign({jwt}, response)));
   }

   private getResult(job: AuthorizationJob): Observable<AuthorizationResult> {
      const headers = new HttpHeaders()
         .set("Content-Type", "application/json")
         .set("Accept", "application/json")
         .set("Authorization", `Bearer ${job.jwt}`);
      const options = {headers};
      return this.http.get<AuthorizationResponse>(job.dataUrl, options)
         .pipe(map(response => {
            if(response) {
               return Object.assign({complete: true}, response);
            }
            else {
               return {complete: false};
            }
         }));
   }

   private static isValid(parameters: OAuthParameters): boolean {
      if(!parameters) {
         return false;
      }

      if(!parameters.method) {
         return false;
      }

      if(parameters.serviceName) {
         return true;
      }

      return !!parameters.clientId && !!parameters.clientSecret && !!parameters.scope &&
         !!parameters.authorizationUri && !!parameters.clientSecret;
   }

   private static hasValidParamsForPasswordGrantAuth(parameters: OAuthParameters): boolean {
      if(parameters == null) {
         return false;
      }

      if(!parameters.method || !parameters.tokenUri) {
         return false;
      }

      if(parameters.serviceName) {
         return true;
      }

      return !!parameters.user && !!parameters.password  && !parameters.authorizationUri;
   }
}