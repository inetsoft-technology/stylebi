import { NgModule } from "@angular/core";
import { CommonModule } from "@angular/common";
import { MatCardModule } from "@angular/material/card";
import { EditorPanelModule } from "../../../common/util/editor-panel/editor-panel.module";
import { GoogleSignInSettingComponent } from "./google-sign-in-setting/google-sign-in-setting.component";
import { GoogleSignInRoutingModule } from "./google-signin-routing.module";
import { MatSlideToggleModule } from "@angular/material/slide-toggle";
import { SSOSettingsModule } from "../sso/sso-settings.module";

@NgModule({
  declarations: [
     GoogleSignInSettingComponent
  ],
    imports: [
       CommonModule,
       GoogleSignInRoutingModule,
       EditorPanelModule,
       MatCardModule,
       MatSlideToggleModule,
       SSOSettingsModule
    ]
})
export class GoogleSignInModule { }
