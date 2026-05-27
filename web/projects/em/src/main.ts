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
import {platformBrowserDynamic} from "@angular/platform-browser-dynamic";

import { httpInterceptorProviders } from "./app/app.module";
import { importProvidersFrom } from "@angular/core";
import { AppComponent } from "./app/app.component";
import { MatSelectModule } from "@angular/material/select";
import { AppRoutingModule } from "./app/app-routing.module";
import { PageHeaderModule } from "./app/page-header/page-header.module";
import { MatToolbarModule } from "@angular/material/toolbar";
import { MatTableModule } from "@angular/material/table";
import { MatSnackBarModule } from "@angular/material/snack-bar";
import { MatMenuModule } from "@angular/material/menu";
import { MatInputModule } from "@angular/material/input";
import { MatIconModule } from "@angular/material/icon";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatDialogModule } from "@angular/material/dialog";
import { MatCardModule } from "@angular/material/card";
import { MatButtonModule } from "@angular/material/button";
import { LayoutModule } from "@angular/cdk/layout";
import { ReactiveFormsModule } from "@angular/forms";
import { provideAnimations } from "@angular/platform-browser/animations";
import { BrowserModule, HammerModule, bootstrapApplication } from "@angular/platform-browser";
import { CustomRouteReuseStrategy } from "./app/custom-route-reuse-strategy";
import { RouteReuseStrategy } from "@angular/router";
import { ScheduleTaskNamesService } from "../../shared/schedule/schedule-task-names.service";
import { ScheduleUsersService } from "../../shared/schedule/schedule-users.service";
import { SsoHeartbeatInterceptor } from "../../shared/sso/sso-heartbeat-interceptor";
import { EmClientInterceptor } from "../../portal/src/app/common/services/emclient-interceptor";
import { InvalidSessionInterceptor } from "./app/invalid-session-interceptor";
import { RequestedWithInterceptor } from "../../portal/src/app/common/services/requested-with-interceptor";
import { HttpParamsCodecInterceptor } from "../../portal/src/app/common/services/http-params-codec-interceptor";
import { CsrfInterceptor } from "./app/csrf-interceptor";
import { HTTP_INTERCEPTORS, withInterceptorsFromDi, provideHttpClient } from "@angular/common/http";
import { SsoHeartbeatService } from "../../shared/sso/sso-heartbeat.service";


bootstrapApplication(AppComponent, {
    providers: [
        importProvidersFrom(BrowserModule, ReactiveFormsModule, HammerModule, LayoutModule, MatButtonModule, MatCardModule, MatDialogModule, MatFormFieldModule, MatIconModule, MatInputModule, MatMenuModule, MatSnackBarModule, MatTableModule, MatToolbarModule, PageHeaderModule, AppRoutingModule, MatSelectModule),
        SsoHeartbeatService,
        httpInterceptorProviders,
        ScheduleUsersService,
        ScheduleTaskNamesService,
        { provide: RouteReuseStrategy, useClass: CustomRouteReuseStrategy },
        provideAnimations(),
        provideHttpClient(withInterceptorsFromDi())
    ]
})
   .catch(err => console.error(err));
