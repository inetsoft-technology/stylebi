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
import { HammerModule, bootstrapApplication } from "@angular/platform-browser";
import { provideAnimations } from "@angular/platform-browser/animations";
import { provideRouter, withInMemoryScrolling, withRouterConfig } from "@angular/router";
import { provideHttpClient, withInterceptorsFromDi } from "@angular/common/http";
import { importProvidersFrom, provideZoneChangeDetection } from "@angular/core";
import { LayoutModule } from "@angular/cdk/layout";
import { ReactiveFormsModule } from "@angular/forms";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatDialogModule } from "@angular/material/dialog";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { MatMenuModule } from "@angular/material/menu";
import { MatSelectModule } from "@angular/material/select";
import { MatSnackBarModule } from "@angular/material/snack-bar";
import { MatTableModule } from "@angular/material/table";
import { MatToolbarModule } from "@angular/material/toolbar";
import { RouteReuseStrategy } from "@angular/router";
import { ScheduleTaskNamesService } from "../../shared/schedule/schedule-task-names.service";
import { ScheduleUsersService } from "../../shared/schedule/schedule-users.service";
import { SsoHeartbeatService } from "../../shared/sso/sso-heartbeat.service";
import { AppComponent } from "./app/app.component";
import { APP_ROUTES } from "./app/app.routes";
import { CustomRouteReuseStrategy } from "./app/custom-route-reuse-strategy";
import { httpInterceptorProviders } from "./app/http-interceptor-providers";

bootstrapApplication(AppComponent, {
   providers: [
      provideZoneChangeDetection(),importProvidersFrom(ReactiveFormsModule, HammerModule, LayoutModule,
         MatButtonModule, MatCardModule, MatDialogModule, MatFormFieldModule, MatIconModule,
         MatInputModule, MatMenuModule, MatSnackBarModule, MatTableModule, MatToolbarModule,
         MatSelectModule),
      SsoHeartbeatService,
      httpInterceptorProviders,
      ScheduleUsersService,
      ScheduleTaskNamesService,
      { provide: RouteReuseStrategy, useClass: CustomRouteReuseStrategy },
      provideAnimations(),
      provideHttpClient(withInterceptorsFromDi()),
      provideRouter(
         APP_ROUTES,
         withRouterConfig({ onSameUrlNavigation: "reload" }),
         withInMemoryScrolling({ anchorScrolling: "enabled" })
      )
   ]
})
   .catch(err => console.error(err));
