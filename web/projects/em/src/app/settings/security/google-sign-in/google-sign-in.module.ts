import { NgModule } from "@angular/core";
import { CommonModule } from "@angular/common";
import { MatCardModule } from "@angular/material/card";

import { GoogleSignInSettingComponent } from "./google-sign-in-setting/google-sign-in-setting.component";
import { GoogleSignInRoutingModule } from "./google-signin-routing.module";
import { MatSlideToggleModule } from "@angular/material/slide-toggle";
import { SSOSettingsModule } from "../sso/sso-settings.module";

@NgModule({
    imports: [
    CommonModule,
    GoogleSignInRoutingModule,
    MatCardModule,
    MatSlideToggleModule,
    SSOSettingsModule,
    GoogleSignInSettingComponent
]
})
export class GoogleSignInModule { }
