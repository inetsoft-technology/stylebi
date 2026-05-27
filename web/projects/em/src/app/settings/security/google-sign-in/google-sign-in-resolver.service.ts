import { HttpClient } from "@angular/common/http";
import { inject } from "@angular/core";
import { ActivatedRouteSnapshot, ResolveFn, RouterStateSnapshot } from "@angular/router";
import { Observable } from "rxjs";
import { GoogleSignInModel } from "./google-sign-in-setting/google-sign-in-model";

export const googleSignInResolver: ResolveFn<GoogleSignInModel> = (route: ActivatedRouteSnapshot, state: RouterStateSnapshot): Observable<GoogleSignInModel> => {
   const httpClient = inject(HttpClient);
   return httpClient.get<GoogleSignInModel>("../api/em/security/googleSignIn");
};
