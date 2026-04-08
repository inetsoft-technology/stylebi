/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import { HttpRequest } from "@angular/common/http";
import { of } from "rxjs";
import { SsoHeartbeatInterceptor } from "./sso-heartbeat-interceptor";
import { SsoHeartbeatService } from "./sso-heartbeat.service";

function makeRequest(url: string): HttpRequest<any> {
   return new HttpRequest("GET", url);
}

function makeHandler() {
   return { handle: jest.fn().mockReturnValue(of(null)) };
}

describe("SsoHeartbeatInterceptor", () => {
   let service: SsoHeartbeatService;
   let interceptor: SsoHeartbeatInterceptor;

   beforeEach(() => {
      service = new SsoHeartbeatService();
      interceptor = new SsoHeartbeatInterceptor(service);
   });

   it("calls heartbeat for relative URLs", () => {
      const heartbeatSpy = jest.spyOn(service, "heartbeat");
      const handler = makeHandler();

      interceptor.intercept(makeRequest("../api/some-endpoint"), handler as any).subscribe();

      expect(heartbeatSpy).toHaveBeenCalledTimes(1);
      expect(handler.handle).toHaveBeenCalled();
   });

   it("does not call heartbeat for absolute http URLs", () => {
      const heartbeatSpy = jest.spyOn(service, "heartbeat");
      const handler = makeHandler();

      interceptor.intercept(makeRequest("http://example.com/api"), handler as any).subscribe();

      expect(heartbeatSpy).not.toHaveBeenCalled();
      expect(handler.handle).toHaveBeenCalled();
   });

   it("does not call heartbeat for absolute https URLs", () => {
      const heartbeatSpy = jest.spyOn(service, "heartbeat");
      const handler = makeHandler();

      interceptor.intercept(makeRequest("https://example.com/api"), handler as any).subscribe();

      expect(heartbeatSpy).not.toHaveBeenCalled();
      expect(handler.handle).toHaveBeenCalled();
   });

   it("always passes the request to the next handler", () => {
      const handler = makeHandler();
      interceptor.intercept(makeRequest("../api/data"), handler as any).subscribe();
      interceptor.intercept(makeRequest("https://external.com/data"), handler as any).subscribe();

      expect(handler.handle).toHaveBeenCalledTimes(2);
   });

   it("emits heartbeats observable when heartbeat is called on service", (done) => {
      service.heartbeats.subscribe(() => done());
      interceptor.intercept(makeRequest("../api/endpoint"), makeHandler() as any).subscribe();
   });
});
