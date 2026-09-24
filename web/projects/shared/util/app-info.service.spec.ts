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

import { Subject, throwError } from "rxjs";
import { AppInfoService } from "./app-info.service";

/**
 * Regression coverage for Bug #76924: getCurrentOrgInfo() must not hand a subscriber anything
 * before the async "../api/org/info" request resolves, since a consumer reading it synchronously
 * (e.g. a field set from a subscribe() callback) can't tell "not loaded yet" apart from a real,
 * empty value. It must also guarantee the stream eventually emits something even when that
 * request errors (see UNKNOWN_ORG_INFO), or any take(1)-based consumer (e.g.
 * ShareService.getViewsheetLinkAsync()) hangs indefinitely.
 *
 * Regression coverage for Bug #76996: getCurrentOrgInfo() previously cached the first resolved
 * value for the service's lifetime in a BehaviorSubject, so every subscriber - including ones
 * that subscribe long after an org switch - kept seeing the org info from whenever the service
 * was first constructed. It must instead issue a fresh "../api/org/info" request for every new
 * subscription.
 */
describe("AppInfoService", () => {
   function setup() {
      const orgInfoResponse = new Subject<{ key: string; value: string }>();
      const httpClient = { get: vi.fn().mockReturnValue(orgInfoResponse) } as any;
      const service = new AppInfoService(httpClient);
      return { service, orgInfoResponse };
   }

   it("should not emit anything before the org info request resolves", () => {
      const { service } = setup();
      const received: any[] = [];
      service.getCurrentOrgInfo().subscribe(v => received.push(v));

      expect(received).toEqual([]);
   });

   it("should emit the real value once the org info request resolves", () => {
      const { service, orgInfoResponse } = setup();
      const received: any[] = [];
      service.getCurrentOrgInfo().subscribe(v => received.push(v));

      orgInfoResponse.next({ key: "host-org", value: "Default" });

      expect(received).toEqual([{ key: "host-org", value: "Default" }]);
   });

   it("should issue a fresh org info request for each new subscriber instead of replaying a stale cached value", () => {
      const orgInfoResponses = [
         new Subject<{ key: string; value: string }>(),
         new Subject<{ key: string; value: string }>()
      ];
      let orgInfoCalls = 0;
      // AppInfoService also calls httpClient.get("../api/enterprise") once at construction time
      // (unrelated to org info), so route by URL rather than assuming org info is the only or
      // first call.
      const httpClient = {
         get: vi.fn((url: string) =>
            url === "../api/org/info" ? orgInfoResponses[orgInfoCalls++] : new Subject())
      } as any;
      const service = new AppInfoService(httpClient);

      const first: any[] = [];
      service.getCurrentOrgInfo().subscribe(v => first.push(v));
      orgInfoResponses[0].next({ key: "tenant-A", value: "Tenant A" });
      expect(first).toEqual([{ key: "tenant-A", value: "Tenant A" }]);

      // A later subscription (e.g. after the session's org has changed) must see the current
      // org, not a replay of whatever the first subscription saw.
      const second: any[] = [];
      service.getCurrentOrgInfo().subscribe(v => second.push(v));
      orgInfoResponses[1].next({ key: "tenant-B", value: "Tenant B" });

      expect(second).toEqual([{ key: "tenant-B", value: "Tenant B" }]);
      expect(orgInfoCalls).toBe(2);
   });

   it("should fall back to UNKNOWN_ORG_INFO instead of hanging forever when the org info request errors", () => {
      const { service, orgInfoResponse } = setup();
      const received: any[] = [];
      service.getCurrentOrgInfo().subscribe(v => received.push(v));

      orgInfoResponse.error(new Error("network error"));

      expect(received).toEqual([AppInfoService.UNKNOWN_ORG_INFO]);
   });

   it("should still guarantee an eventual emission when the HTTP call itself errors synchronously", () => {
      const httpClient = { get: vi.fn().mockReturnValue(throwError(() => new Error("500"))) } as any;
      const service = new AppInfoService(httpClient);

      const received: any[] = [];
      service.getCurrentOrgInfo().subscribe(v => received.push(v));

      expect(received).toEqual([AppInfoService.UNKNOWN_ORG_INFO]);
   });
});
