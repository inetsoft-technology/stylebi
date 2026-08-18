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

import { HTTP_INTERCEPTORS, HttpClient } from "@angular/common/http";
import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { TestBed } from "@angular/core/testing";
import { LogoutService } from "../../../shared/util/logout.service";
import { InvalidSessionInterceptor } from "./invalid-session-interceptor";

describe("InvalidSessionInterceptor", () => {
   let http: HttpClient;
   let httpMock: HttpTestingController;
   let logoutService: { sessionExpired: any };

   beforeEach(() => {
      logoutService = { sessionExpired: vi.fn() };

      TestBed.configureTestingModule({
         imports: [HttpClientTestingModule],
         providers: [
            { provide: LogoutService, useValue: logoutService },
            { provide: HTTP_INTERCEPTORS, useClass: InvalidSessionInterceptor, multi: true }
         ]
      });

      http = TestBed.inject(HttpClient);
      httpMock = TestBed.inject(HttpTestingController);
   });

   afterEach(() => {
      httpMock.verify();
   });

   function flushError(url: string, status: number): void {
      http.get(url).subscribe({ next: () => {}, error: () => {} });
      httpMock.expectOne(url).flush("", { status, statusText: "" });
   }

   it("should log out on a same-origin 401, which is how the server reports a lost session", () => {
      flushError("../api/em/general/settings/model", 401);
      expect(logoutService.sessionExpired).toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive (Bug #76017): a 403 means the session is valid but the user is not
   // permitted to access that resource. Treating it as an expired session logged EM users out of
   // the whole application as soon as any page issued a request they lacked permission for --
   // e.g. Settings > General eagerly fetching /api/em/schedule/users-model without the
   // "settings/schedule/tasks" permission.
   it("should NOT log out on a same-origin 403 permission denial", () => {
      flushError("../api/em/schedule/users-model", 403);
      expect(logoutService.sessionExpired).not.toHaveBeenCalled();
   });

   it("should not log out on other same-origin error statuses", () => {
      flushError("../api/em/general/settings/model", 500);
      flushError("../api/em/general/settings/model", 404);
      expect(logoutService.sessionExpired).not.toHaveBeenCalled();
   });

   it("should not log out on a 401 from a cross-origin service", () => {
      flushError("https://data.inetsoft.com/oauth/token", 401);
      expect(logoutService.sessionExpired).not.toHaveBeenCalled();
   });

   it("should log out on a 401 from an absolute same-origin URL", () => {
      flushError(`${window.location.origin}/api/em/general/settings/model`, 401);
      expect(logoutService.sessionExpired).toHaveBeenCalled();
   });

   it("should mark GET requests as XHR so the server answers with 401 instead of a login redirect", () => {
      http.get("../api/em/favorites").subscribe({ next: () => {}, error: () => {} });
      const req = httpMock.expectOne("../api/em/favorites");
      expect(req.request.headers.get("X-Requested-With")).toBe("XMLHttpRequest");
      req.flush({});
   });

   it("should succeed without logging out", () => {
      http.get("../api/em/favorites").subscribe();
      httpMock.expectOne("../api/em/favorites").flush({});
      expect(logoutService.sessionExpired).not.toHaveBeenCalled();
   });
});
