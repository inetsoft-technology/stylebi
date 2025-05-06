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
import { APP_BASE_HREF } from "@angular/common";
import { HTTP_INTERCEPTORS, HttpClientModule } from "@angular/common/http";
import { ApplicationRef, DoBootstrap, NgModule } from "@angular/core";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { BrowserModule } from "@angular/platform-browser";
import { NgbModalModule } from "@ng-bootstrap/ng-bootstrap";
import { SsoHeartbeatInterceptor } from "../../../../shared/sso/sso-heartbeat-interceptor";
import { SsoHeartbeatService } from "../../../../shared/sso/sso-heartbeat.service";
import { BaseUrlInterceptor } from "../common/services/base-url-interceptor";
import { CsrfInterceptor } from "../common/services/csrf-interceptor";
import { FirstDayOfWeekService } from "../common/services/first-day-of-week.service";
import { HttpDebounceInterceptor } from "../common/services/http-debounce-interceptor";
import { HttpParamsCodecInterceptor } from "../common/services/http-params-codec-interceptor";
import { LicenseInfoService } from "../common/services/license-info.service";
import { RequestedWithInterceptor } from "../common/services/requested-with-interceptor";
import { ViewDataService } from "../viewer/services/view-data.service";
import { DomService } from "../widget/dom-service/dom.service";
import { DebounceService } from "../widget/services/debounce.service";
import { DragService } from "../widget/services/drag.service";
import { FontService } from "../widget/services/font.service";
import { ModelService } from "../widget/services/model.service";

export const httpInterceptorProviders = [
   {provide: HTTP_INTERCEPTORS, useClass: HttpDebounceInterceptor, multi: true},
   {provide: HTTP_INTERCEPTORS, useClass: HttpParamsCodecInterceptor, multi: true},
   {provide: HTTP_INTERCEPTORS, useClass: CsrfInterceptor, multi: true},
   {provide: HTTP_INTERCEPTORS, useClass: RequestedWithInterceptor, multi: true},
   {provide: HTTP_INTERCEPTORS, useClass: SsoHeartbeatInterceptor, multi: true},
   {provide: HTTP_INTERCEPTORS, useClass: BaseUrlInterceptor, multi: true},
];

export function GetAppBaseHref(): string {
   return document.getElementsByTagName(
      "inetsoft-base")?.item(0)?.attributes?.getNamedItem("href")?.value;
}

@NgModule({
   imports: [
      BrowserModule,
      HttpClientModule,
      NgbModalModule,
      FormsModule,
      ReactiveFormsModule,
   ],
   providers: [
      SsoHeartbeatService,
      httpInterceptorProviders,
      ViewDataService,
      LicenseInfoService,
      FirstDayOfWeekService,
      DragService,
      FontService,
      ModelService,
      DebounceService,
      DomService,
      {provide: APP_BASE_HREF, useFactory: GetAppBaseHref},
   ],
})
export class AppBaseElementModule implements DoBootstrap {
   ngDoBootstrap(appRef: ApplicationRef): void {
   }
}
