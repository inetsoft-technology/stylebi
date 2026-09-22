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

import { BehaviorSubject } from "rxjs";
import { filter } from "rxjs/operators";
import { ShareService } from "./share.service";

/**
 * Regression coverage for Bug #76924: ShareService.getViewsheetLink() computed sharedGlobal
 * from org info that can still be null/unresolved, so a host-org-owned viewsheet shared with a
 * non-host-org user could be encoded as a same-org "global/" link instead of "shared_global/" -
 * which the server then defaulted to the wrong org, causing "Read access denied".
 *
 * HOST_ORG_VS is an identifier for a viewsheet owned by "host-org" (mirrors
 * ShareService.getViewsheetLink()'s viewsheetId.endsWith("host-org") check).
 */
describe("ShareService", () => {
   const HOST_ORG_VS = "1^1^__NULL__^Folder/VS^host-org";

   // Mirrors AppInfoService.getCurrentOrgInfo()'s real contract (a BehaviorSubject, seeded
   // null, with the null seed filtered out) so these tests exercise ShareService against the
   // same stream shape - including replay-to-late-subscribers - it sees in production.
   function setup() {
      const orgInfo = new BehaviorSubject<{ key: string; value: string } | null>(null);
      const appInfoService = {
         getCurrentOrgInfo: () => orgInfo.pipe(filter((v: any) => v != null))
      } as any;
      const http = { get: vi.fn(), post: vi.fn() } as any;
      const service = new ShareService(http, appInfoService);
      return { service, orgInfo };
   }

   it("getViewsheetLink() builds a shared_global/ link once org info has resolved to a non-host-org user", () => {
      const { service, orgInfo } = setup();
      orgInfo.next({ key: "tenant-A", value: "Tenant A" });

      const link = service.getViewsheetLink(HOST_ORG_VS);

      expect(link).toContain("shared_global/");
   });

   it("getViewsheetLink() wrongly falls back to a same-org global/ link when org info hasn't resolved yet (the historical race)", () => {
      const { service } = setup();

      const link = service.getViewsheetLink(HOST_ORG_VS);

      expect(link).toContain("global/");
      expect(link).not.toContain("shared_global/");
   });

   it("getViewsheetLinkAsync() waits for org info to resolve instead of racing ahead", () => {
      const { service, orgInfo } = setup();
      let resolvedLink: string;
      service.getViewsheetLinkAsync(HOST_ORG_VS).subscribe(link => resolvedLink = link);

      expect(resolvedLink).toBeUndefined();

      orgInfo.next({ key: "tenant-A", value: "Tenant A" });

      expect(resolvedLink).toContain("shared_global/");
   });

   it("getViewsheetLinkAsync() resolves immediately when org info was already known", () => {
      const { service, orgInfo } = setup();
      orgInfo.next({ key: "tenant-A", value: "Tenant A" });

      let resolvedLink: string;
      service.getViewsheetLinkAsync(HOST_ORG_VS).subscribe(link => resolvedLink = link);

      expect(resolvedLink).toContain("shared_global/");
   });

   it("getViewsheetLinkAsync() builds a plain global/ link for a same-org viewsheet", () => {
      const { service, orgInfo } = setup();
      orgInfo.next({ key: "host-org", value: "Default" });

      let resolvedLink: string;
      service.getViewsheetLinkAsync("1^1^__NULL__^Folder/VS").subscribe(link => resolvedLink = link);

      expect(resolvedLink).toContain("global/");
      expect(resolvedLink).not.toContain("shared_global/");
   });
});
