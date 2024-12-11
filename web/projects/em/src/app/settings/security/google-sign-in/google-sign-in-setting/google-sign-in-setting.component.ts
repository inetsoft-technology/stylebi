import { HttpClient } from "@angular/common/http";
import { Component, OnDestroy } from "@angular/core";
import { ActivatedRoute } from "@angular/router";
import { Subscription } from "rxjs";
import { map } from "rxjs/operators";
import { Tool } from "../../../../../../../shared/util/tool";
import { ContextHelp } from "../../../../context-help";
import { PageHeaderService } from "../../../../page-header/page-header.service";
import { Secured } from "../../../../secured";
import { OpenIdAttributesModel } from "../../sso/sso-settings-model";
import { GoogleSignInModel } from "./google-sign-in-model";

@Secured({
   route: "/settings/security/googleSignIn",
   label: "Sign In With Google",
   hiddenForMultiTenancy: true
})
@ContextHelp({
   route: "/settings/security/googleSignIn",
   link: "EMSelfSignup"
})
@Component({
   selector: "em-google-sign-in-setting",
   templateUrl: "./google-sign-in-setting.component.html",
   styleUrls: ["./google-sign-in-setting.component.scss"]
})
export class GoogleSignInSettingComponent implements OnDestroy {
   private readonly subscription: Subscription;
   model: GoogleSignInModel;
   omodel: GoogleSignInModel;
   changed: boolean = false;

   constructor(private httpClient: HttpClient, activatedRoute: ActivatedRoute,
               private pageTitle: PageHeaderService)
   {
      this.subscription = activatedRoute.data.pipe(
         map((data: Record<"model", GoogleSignInModel>) => data.model),
      ).subscribe(model => this.resetModel(model));
      this.pageTitle.title = "_#(js:Security Settings:Sign In With Google)";
   }

   ngOnDestroy(): void {
      this.subscription.unsubscribe();
   }

   toggle(): void {
      this.model.enable = !this.model.enable;
      this.checkModelChanged();
   }

   modelChanged(changedModel: OpenIdAttributesModel): void {
      this.model.openIdAttributesModel = changedModel;
      this.checkModelChanged();
   }

   private checkModelChanged(): void {
      this.changed = !Tool.isEquals(this.model, this.omodel);
   }

   private resetModel(nmodel: GoogleSignInModel): void {
      this.model = nmodel;
      this.omodel = Tool.clone(this.model);
      this.changed = false;
   }

   public submit(): void {
      this.httpClient.post("../api/em/security/googleSignIn", this.model)
         .subscribe(() => this.reset());
   }

   reset(): void {
      this.httpClient.get<GoogleSignInModel>("../api/em/security/googleSignIn")
         .subscribe(model => this.resetModel(model));
   }
}
