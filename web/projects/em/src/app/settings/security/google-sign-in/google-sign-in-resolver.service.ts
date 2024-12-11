import { HttpClient } from "@angular/common/http";
import { Injectable } from "@angular/core";
import { ActivatedRouteSnapshot, RouterStateSnapshot } from "@angular/router";
import { Observable } from "rxjs";
import { GoogleSignInModel } from "./google-sign-in-setting/google-sign-in-model";

@Injectable()
export class GoogleSignInResolver {
   constructor(private httpClient: HttpClient) {
   }

   resolve(route: ActivatedRouteSnapshot,
           state: RouterStateSnapshot): Observable<GoogleSignInModel>
   {
      return this.httpClient.get<GoogleSignInModel>("../api/em/security/googleSignIn");
   }
}
