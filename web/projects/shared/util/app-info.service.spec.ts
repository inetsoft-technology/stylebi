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

import { Subject } from "rxjs";
import { AppInfoService } from "./app-info.service";

/**
 * Regression coverage for Bug #76924: currentOrgInfo is a BehaviorSubject seeded with null and
 * only updated once the async "../api/org/info" request resolves. getCurrentOrgInfo() must not
 * hand that seed null to subscribers, since a consumer reading it synchronously (e.g. a field set
 * from a subscribe() callback) can't tell "not loaded yet" apart from a real, empty value.
 */
describe("AppInfoService", () => {
   function setup() {
      const orgInfoResponse = new Subject<{ key: string; value: string }>();
      const httpClient = { get: vi.fn().mockReturnValue(orgInfoResponse) } as any;
      const service = new AppInfoService(httpClient);
      return { service, orgInfoResponse };
   }

   it("should not emit the seeded null before the org info request resolves", () => {
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

   it("should replay the real value to a late subscriber without an intervening null", () => {
      const { service, orgInfoResponse } = setup();
      orgInfoResponse.next({ key: "tenant-A", value: "Tenant A" });

      const received: any[] = [];
      service.getCurrentOrgInfo().subscribe(v => received.push(v));

      expect(received).toEqual([{ key: "tenant-A", value: "Tenant A" }]);
   });
});
