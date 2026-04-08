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
import { EMPTY, of, Subject } from "rxjs";
import { SsoHeartbeatDispatcherService } from "./sso-heartbeat-dispatcher.service";

function makeHeartbeatService(subject: Subject<void>) {
   return { heartbeats: subject.asObservable() };
}

function makeHttp(heartbeatUrl: string | null = "https://sso.example.com/heartbeat") {
   return {
      get: jest.fn().mockImplementation((url: string) => {
         if(url.includes("sso-heartbeat-model")) {
            return of({ url: heartbeatUrl });
         }
         // sendHeartbeat call — just return EMPTY (success, no value needed)
         return EMPTY;
      })
   };
}

describe("SsoHeartbeatDispatcherService", () => {
   let heartbeatSubject: Subject<void>;
   let mockHttp: ReturnType<typeof makeHttp>;
   let mockHeartbeatService: ReturnType<typeof makeHeartbeatService>;
   let service: SsoHeartbeatDispatcherService;

   beforeEach(() => {
      heartbeatSubject = new Subject<void>();
      mockHttp = makeHttp();
      mockHeartbeatService = makeHeartbeatService(heartbeatSubject);
      service = new SsoHeartbeatDispatcherService(mockHttp as any, mockHeartbeatService as any);
   });

   afterEach(() => {
      try {
         service.ngOnDestroy();
      } catch {
         // already destroyed in the test — ignore
      }
   });

   it("dispatch() calls sendHeartbeat with the URL when a heartbeat fires", () => {
      service.dispatch();
      heartbeatSubject.next();

      // Should have called GET for heartbeat model, then GET for the actual heartbeat URL
      const calls = mockHttp.get.mock.calls.map((c: any[]) => c[0]);
      expect(calls).toContain("https://sso.example.com/heartbeat");
   });

   it("constructor fetches the heartbeat URL from the SSO model endpoint", () => {
      service.dispatch();
      heartbeatSubject.next();

      const modelCall = mockHttp.get.mock.calls.find((c: any[]) =>
         (c[0] as string).includes("sso-heartbeat-model")
      );
      expect(modelCall).toBeDefined();
   });

   it("dispatch() sends heartbeat with withCredentials: true", () => {
      service.dispatch();
      heartbeatSubject.next();

      const heartbeatCall = mockHttp.get.mock.calls.find((c: any[]) =>
         c[0] === "https://sso.example.com/heartbeat"
      );
      expect(heartbeatCall).toBeDefined();
      expect(heartbeatCall[1]).toEqual({ withCredentials: true });
   });

   it("does not send heartbeat when SSO model returns null URL", () => {
      const noUrlHttp = makeHttp(null);
      const s = new SsoHeartbeatDispatcherService(noUrlHttp as any, mockHeartbeatService as any);
      s.dispatch();
      heartbeatSubject.next();

      // Only one call should have been made — to sso-heartbeat-model; no heartbeat GET
      expect(noUrlHttp.get.mock.calls).toHaveLength(1);
      s.ngOnDestroy();
   });

   it("ngOnDestroy stops dispatching after destruction", () => {
      service.dispatch();
      service.ngOnDestroy();
      mockHttp.get.mockClear();

      heartbeatSubject.next();

      // No new HTTP calls after destroy
      expect(mockHttp.get).not.toHaveBeenCalled();
   });
});
