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
import { LayoutModule } from "@angular/cdk/layout";
import { HTTP_INTERCEPTORS, HttpClientModule } from "@angular/common/http";
import { NgModule } from "@angular/core";
import { ReactiveFormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatDialogModule } from "@angular/material/dialog";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatMenuModule } from "@angular/material/menu";
import { MatSnackBarModule } from "@angular/material/snack-bar";
import { MatTableModule } from "@angular/material/table";
import { MatToolbarModule } from "@angular/material/toolbar";
import { BrowserModule, HammerModule } from "@angular/platform-browser";
import { BrowserAnimationsModule } from "@angular/platform-browser/animations";
import { HttpParamsCodecInterceptor } from "../../../portal/src/app/common/services/http-params-codec-interceptor";
import { RequestedWithInterceptor } from "../../../portal/src/app/common/services/requested-with-interceptor";
import { DownloadModule } from "../../../shared/download/download.module";
import { FeatureFlagsModule } from "../../../shared/feature-flags/feature-flags.module";
import { ScheduleUsersService } from "../../../shared/schedule/schedule-users.service";
import { SsoHeartbeatInterceptor } from "../../../shared/sso/sso-heartbeat-interceptor";
import { SsoHeartbeatService } from "../../../shared/sso/sso-heartbeat.service";
import { AppRoutingModule } from "./app-routing.module";
import { AppComponent } from "./app.component";
import { MessageDialogModule } from "./common/util/message-dialog.module";
import { ModalHeaderModule } from "./common/util/modal-header/modal-header.module";
import { CsrfInterceptor } from "./csrf-interceptor";
import { InvalidSessionInterceptor } from "./invalid-session-interceptor";
import { ManageFavoritesComponent } from "./manage-favorites/manage-favorites.component";
import { NavbarComponent } from "./navbar/navbar.component";
import { SendNotificationDialogComponent } from "./navbar/send-notification-dialog.component";
import { PageHeaderModule } from "./page-header/page-header.module";
import { ChangePasswordFormComponent } from "./password/change-password-form.component";
import { PasswordComponent } from "./password/password.component";
import {MatSelectModule} from "@angular/material/select";

export const httpInterceptorProviders = [
   {provide: HTTP_INTERCEPTORS, useClass: CsrfInterceptor, multi: true},
   {provide: HTTP_INTERCEPTORS, useClass: HttpParamsCodecInterceptor, multi: true},
   {provide: HTTP_INTERCEPTORS, useClass: RequestedWithInterceptor, multi: true},
   {provide: HTTP_INTERCEPTORS, useClass: InvalidSessionInterceptor, multi: true},
   {provide: HTTP_INTERCEPTORS, useClass: SsoHeartbeatInterceptor, multi: true}
];

@NgModule({
    imports: [
        BrowserModule,
        BrowserAnimationsModule,
        ReactiveFormsModule,
        HttpClientModule,
        HammerModule,
        LayoutModule,
        MatButtonModule,
        MatCardModule,
        MatDialogModule,
        MatFormFieldModule,
        MatIconModule,
        MatInputModule,
        MatMenuModule,
        MatSnackBarModule,
        MatTableModule,
        MatToolbarModule,
        PageHeaderModule,
        AppRoutingModule,
        DownloadModule,
        MessageDialogModule,
        ModalHeaderModule,
        FeatureFlagsModule,
        MatSelectModule
    ],
   providers: [
      SsoHeartbeatService,
      httpInterceptorProviders,
      ScheduleUsersService
   ],
   declarations: [
      AppComponent,
      ChangePasswordFormComponent,
      ManageFavoritesComponent,
      NavbarComponent,
      PasswordComponent,
      SendNotificationDialogComponent
   ],
   bootstrap: [AppComponent]
})
export class AppModule {
}
