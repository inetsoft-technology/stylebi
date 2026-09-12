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

/**
 * DatasourceSelectionViewComponent — Pass 1 (Interaction / sync logic / navigation)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 1]  — getListings: model null → []; no filter → all; search by name;
 *                        search by keyword; category filter; search + category combined
 *   Group 2 [Risk 1]  — isCreateDisabled: null selected → true; valid name in list → false;
 *                        selected listing filtered out → true
 *   Group 3 [Risk 2]  — cancel (normal path): navigate to /portal/tab/data/datasources with
 *                        scope; parentPath empty → path="/"; parentPath set → path=parentPath
 *   Group 4 [Risk 2]  — cancel (GettingStarted path): isConnectTo=true → leaveByCancelButton +
 *                        continue() after 100ms; isConnectTo=false → normal navigate
 *   Group 5 [Risk 2]  — canDeactivate: always returns true; non-GettingStarted → no continue;
 *                        GettingStarted + conditions met → continue() triggered after 100ms;
 *                        continueTriggered prevents second firing
 *
 * Out of scope this pass:
 *   ngOnInit (route subscriptions), ngOnDestroy, create() (HTTP+navigation) → Pass 2.
 */

import { waitFor } from "@testing-library/angular";
import { AssetConstants } from "../../../../common/data/asset-constants";
import {
   MOCK_LISTING_MYSQL,
   MOCK_LISTING_SALESFORCE,
   MOCK_MODEL,
   renderComp,
} from "./datasource-selection-view.component.test-helpers";

// ── Global lifecycle ─────────────────────────────────────────────────────────

afterEach(() => {
   vi.restoreAllMocks();
});

// ── Group 1 — getListings [Risk 1] ───────────────────────────────────────────

describe("DatasourceSelectionViewComponent — getListings", () => {
   it("should return empty array when model is null", async () => {
      const { comp } = await renderComp();
      comp.model = null;

      expect(comp.getListings()).toEqual([]);
   });

   it("should return all listings when no search or category filter is set", async () => {
      const { comp } = await renderComp();

      const result = comp.getListings();

      expect(result).toHaveLength(2);
      expect(result.map(l => l.name)).toContain("MySQL");
      expect(result.map(l => l.name)).toContain("Salesforce");
   });

   it("should filter listings by search string matching the name", async () => {
      const { comp } = await renderComp();
      comp.searchString = "mysql";

      const result = comp.getListings();

      expect(result).toHaveLength(1);
      expect(result[0].name).toBe("MySQL");
   });

   it("should filter listings by search string matching a keyword", async () => {
      const { comp } = await renderComp();
      comp.searchString = "crm";

      const result = comp.getListings();

      expect(result).toHaveLength(1);
      expect(result[0].name).toBe("Salesforce");
   });

   it("should filter listings by selected category", async () => {
      const { comp } = await renderComp();
      comp.selectedCategory = "Cloud";

      const result = comp.getListings();

      expect(result).toHaveLength(1);
      expect(result[0].name).toBe("Salesforce");
   });

   it("should apply both search and category filters together", async () => {
      const { comp } = await renderComp();
      comp.searchString = "mysql";
      comp.selectedCategory = "Cloud"; // MySQL is in Database, not Cloud

      const result = comp.getListings();

      expect(result).toHaveLength(0);
   });
});

// ── Group 2 — isCreateDisabled [Risk 1] ──────────────────────────────────────

describe("DatasourceSelectionViewComponent — isCreateDisabled", () => {
   // 🔁 Regression-sensitive: a selected listing that no longer matches the current search
   // filter must still disable the Create button — otherwise the button sends a stale name.

   it("should return true when selectedListingName is null", async () => {
      const { comp } = await renderComp();
      comp.selectedListingName = null;

      expect(comp.isCreateDisabled()).toBe(true);
   });

   it("should return false when selectedListingName exists in the filtered list", async () => {
      const { comp } = await renderComp();
      comp.selectedListingName = "MySQL";

      expect(comp.isCreateDisabled()).toBe(false);
   });

   it("should return true when the selected listing is filtered out by category", async () => {
      const { comp } = await renderComp();
      comp.selectedListingName = "MySQL"; // MySQL is in Database
      comp.selectedCategory = "Cloud";    // filter leaves only Salesforce

      expect(comp.isCreateDisabled()).toBe(true);
   });
});

// ── Group 3 — cancel (normal path) [Risk 2] ──────────────────────────────────

describe("DatasourceSelectionViewComponent — cancel (normal navigate)", () => {
   // 🔁 Regression-sensitive: parentPath="" must resolve to path="/" in query params;
   // a missing "/" causes the server to reject the navigation as invalid.

   it("should navigate to /portal/tab/data/datasources with path='/' when parentPath is empty", async () => {
      const { comp, routerMock } = await renderComp({ parentPath: "" });

      comp.cancel();

      expect(routerMock.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources"],
         { queryParams: { path: "/", scope: AssetConstants.QUERY_SCOPE } },
      );
   });

   it("should navigate with the parentPath when it is set", async () => {
      const { comp, routerMock } = await renderComp({ parentPath: "SomeFolder/Sub" });

      comp.cancel();

      expect(routerMock.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources"],
         { queryParams: { path: "SomeFolder/Sub", scope: AssetConstants.QUERY_SCOPE } },
      );
   });

   it("should NOT call gettingStartedService.continue() when not in Getting Started flow", async () => {
      const { comp, gettingStartedMock } = await renderComp({ gettingStarted: false });

      comp.cancel();

      expect(gettingStartedMock.continue).not.toHaveBeenCalled();
   });
});

// ── Group 4 — cancel (GettingStarted path) [Risk 2] ──────────────────────────

// leaveByCancelButton is a private field on DatasourceSelectionViewComponent; (comp as any)
// access verifies the guard flag is set without adding a public getter to production code.
describe("DatasourceSelectionViewComponent — cancel (GettingStarted path)", () => {
   // 🔁 Regression-sensitive: when opened by Getting Started with isConnectTo=true,
   // cancel() must NOT navigate to the datasources list — that would bypass the wizard step.

   it("should call gettingStartedService.continue() and set leaveByCancelButton when isConnectTo=true", async () => {
      const { comp, gettingStartedMock, routerMock } = await renderComp({ gettingStarted: true });
      gettingStartedMock.isConnectTo.mockReturnValue(true);

      comp.cancel();

      // cancel() schedules continue() via a 100ms setTimeout; waitFor polls until it fires.
      await waitFor(() => expect(gettingStartedMock.continue).toHaveBeenCalledTimes(1));
      expect((comp as any).leaveByCancelButton).toBe(true);
      expect(routerMock.navigate).not.toHaveBeenCalled();
   });

   it("should fall through to normal navigate when gettingStarted=true but isConnectTo=false", async () => {
      const { comp, routerMock, gettingStartedMock } = await renderComp({ gettingStarted: true });
      gettingStartedMock.isConnectTo.mockReturnValue(false);

      comp.cancel();

      expect(routerMock.navigate).toHaveBeenCalledWith(
         ["/portal/tab/data/datasources"],
         expect.objectContaining({ queryParams: expect.objectContaining({ scope: AssetConstants.QUERY_SCOPE }) }),
      );
      expect(gettingStartedMock.continue).not.toHaveBeenCalled();
   });
});

// ── Group 5 — canDeactivate [Risk 2] ─────────────────────────────────────────

// continueTriggered is a private field on DatasourceSelectionViewComponent; (comp as any)
// access verifies the deduplication flag without adding a public getter to production code.
describe("DatasourceSelectionViewComponent — canDeactivate", () => {
   // 🔁 Regression-sensitive: the Getting Started wizard depends on canDeactivate() calling
   // continue() EXACTLY once when navigating away mid-flow. Double-firing breaks the wizard.

   it("should always return true", async () => {
      const { comp } = await renderComp();

      const result = comp.canDeactivate();

      expect(result).toBe(true);
   });

   it("should NOT call continue() when not opened by Getting Started", async () => {
      const { comp, gettingStartedMock } = await renderComp({ gettingStarted: false });
      gettingStartedMock.isConnectTo.mockReturnValue(true);

      comp.canDeactivate(
         undefined,
         undefined,
         { url: "/other-route" } as any,
         { url: "/portal/tab/data/somewhere" } as any,
      );

      // No wait — openByGettingStarted=false means no setTimeout is ever scheduled.
      expect(gettingStartedMock.continue).not.toHaveBeenCalled();
   });

   it("should call continue() once when navigating away from the Getting Started wizard mid-flow", async () => {
      const { comp, gettingStartedMock } = await renderComp({ gettingStarted: true });
      gettingStartedMock.isConnectTo.mockReturnValue(true);
      // currentState URL NOT starting with /portal/tab/data/datasources/listing/ → triggers
      comp.canDeactivate(
         undefined,
         undefined,
         { url: "/portal/tab/data/datasources/other" } as any,
         { url: "/portal/tab/data/elsewhere" } as any,
      );

      // canDeactivate schedules continue() via a 100ms setTimeout; waitFor polls until it fires.
      await waitFor(() => expect(gettingStartedMock.continue).toHaveBeenCalledTimes(1));
      expect((comp as any).continueTriggered).toBe(true);
   });

   it("should NOT call continue() a second time when continueTriggered is already true", async () => {
      const { comp, gettingStartedMock } = await renderComp({ gettingStarted: true });
      gettingStartedMock.isConnectTo.mockReturnValue(true);

      // First call — triggers continue()
      comp.canDeactivate(undefined, undefined, { url: "/other" } as any, { url: "/other" } as any);
      await waitFor(() => expect(gettingStartedMock.continue).toHaveBeenCalledTimes(1));

      // Second call — continueTriggered=true guards the setTimeout branch; no new timer is scheduled.
      comp.canDeactivate(undefined, undefined, { url: "/other" } as any, { url: "/other" } as any);
      expect(gettingStartedMock.continue).toHaveBeenCalledTimes(1); // still 1
   });
});
