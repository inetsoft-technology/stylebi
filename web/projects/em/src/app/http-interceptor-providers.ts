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
import { HTTP_INTERCEPTORS } from "@angular/common/http";
import { EmClientInterceptor } from "../../../portal/src/app/common/services/emclient-interceptor";
import { HttpParamsCodecInterceptor } from "../../../portal/src/app/common/services/http-params-codec-interceptor";
import { RequestedWithInterceptor } from "../../../portal/src/app/common/services/requested-with-interceptor";
import { SsoHeartbeatInterceptor } from "../../../shared/sso/sso-heartbeat-interceptor";
import { CsrfInterceptor } from "./csrf-interceptor";
import { InvalidSessionInterceptor } from "./invalid-session-interceptor";

export const httpInterceptorProviders = [
   { provide: HTTP_INTERCEPTORS, useClass: CsrfInterceptor, multi: true },
   { provide: HTTP_INTERCEPTORS, useClass: HttpParamsCodecInterceptor, multi: true },
   { provide: HTTP_INTERCEPTORS, useClass: RequestedWithInterceptor, multi: true },
   { provide: HTTP_INTERCEPTORS, useClass: InvalidSessionInterceptor, multi: true },
   { provide: HTTP_INTERCEPTORS, useClass: EmClientInterceptor, multi: true },
   { provide: HTTP_INTERCEPTORS, useClass: SsoHeartbeatInterceptor, multi: true }
];
