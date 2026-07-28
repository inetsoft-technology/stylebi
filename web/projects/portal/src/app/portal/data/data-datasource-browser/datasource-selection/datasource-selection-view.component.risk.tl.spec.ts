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
 * DatasourceSelectionViewComponent — Pass 2 (Risk / async / lifecycle)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnInit: getDatasourceSelectionViewModel subscription sets comp.model;
 *                        paramMap subscription sets parentPath (verified via create() navigate);
 *                        queryParamMap sets openByGettingStarted (verified via cancel() behavior)
 *   Group 2 [Risk 3] — ngOnDestroy: subscriptions unsubscribed and nulled
 *   Group 3 [Risk 3] — create (JDBC / other): navigate to datasources/database/listing with
 *                        name and parentPath relative to route.parent
 *   Group 4 [Risk 3] — create (TABULAR): navigate to datasources/datasource/listing with name
 *   Group 5 [Risk 3] — create (CUBE): navigate to datasources/datasource/xmla/new (no name)
 *   Group 6 [Risk 2] — create: uses selectedListingName when listingName param is falsy
 *
 * Out of scope this pass:
 *   getListings, isCreateDisabled, cancel, canDeactivate — covered in Pass 1.
 */

import { DatasourceType } from "../datasource-type";
import { AssetConstants } from "../../../../common/data/asset-constants";
import { MOCK_MODEL, renderComp } from "./datasource-selection-view.component.test-helpers";

// ── Global lifecycle ─────────────────────────────────────────────────────────

afterEach(() => {
   vi.restoreAllMocks();
});

// ── Group 1 — ngOnInit subscriptions [Risk 3] ────────────────────────────────

describe("DatasourceSelectionViewComponent — ngOnInit", () => {
   // 🔁 Regression-sensitive: if getDatasourceSelectionViewModel subscription is broken,
   // model stays null and getListings() returns [] for every filter combination.

   it("should set comp.model from getDatasourceSelectionViewModel subscription", async () => {
      const { comp } = await renderComp();

      expect(comp.model).toEqual(MOCK_MODEL);
   });

   it("should set parentPath from route paramMap (verified via create navigation)", async () => {
      const { comp, routerMock, parentRouteMock } = await renderComp({
         parentPath: "Analytics/Sales",
         datasourceType: DatasourceType.JDBC,
      });

      comp.create("MySQL");

      // parentPath="Analytics/Sales" must appear as the third navigate segment
      expect(routerMock.navigate).toHaveBeenCalledWith(
         ["datasources/database/listing", "MySQL", "Analytics/Sales"],
         { relativeTo: parentRouteMock },
      );
   });

   it("should set openByGettingStarted when gettingStartedRouteTime is in query params", async () => {
      // Assert the flag directly — cancel() uses setTimeout(100) which hangs under Zone
      // on a loaded CI TL worker when polled via waitFor.
      const { comp, gettingStartedMock, routerMock } = await renderComp({ gettingStarted: true });
      gettingStartedMock.isConnectTo.mockReturnValue(true);

      expect((comp as any).openByGettingStarted).toBe(true);

      vi.useFakeTimers();
      try {
         comp.cancel();
         vi.advanceTimersByTime(100);
         expect(gettingStartedMock.continue).toHaveBeenCalledTimes(1);
         expect(routerMock.navigate).not.toHaveBeenCalled();
      }
      finally {
         vi.useRealTimers();
      }
   });
});

// ── Group 2 — ngOnDestroy [Risk 3] ───────────────────────────────────────────

// subscriptions is a private field on DatasourceSelectionViewComponent; (comp as any) access
// is the only way to spy on unsubscribe() and verify the field is nulled after destroy without
// adding public accessors to production code.
describe("DatasourceSelectionViewComponent — ngOnDestroy", () => {
   // 🔁 Regression-sensitive: if subscriptions are not unsubscribed, the model Observable
   // (which may be a long-lived BehaviorSubject) continues updating a destroyed component.

   it("should call unsubscribe and null the subscriptions object on destroy", async () => {
      const { comp, fixture } = await renderComp();
      const sub = (comp as any).subscriptions;
      const unsubSpy = vi.spyOn(sub, "unsubscribe");

      fixture.destroy();

      expect(unsubSpy).toHaveBeenCalledTimes(1);
      expect((comp as any).subscriptions).toBeNull();
   });
});

// ── Group 3 — create: JDBC / other type [Risk 3] ─────────────────────────────

describe("DatasourceSelectionViewComponent — create (JDBC / other type)", () => {
   // 🔁 Regression-sensitive: the default case must navigate to database/listing, not
   // datasource/listing — the database listing page shows the schema/connection editor
   // while datasource/listing shows the tabular connector wizard.

   it("should navigate to datasources/database/listing with name and parentPath", async () => {
      const { comp, routerMock, parentRouteMock } = await renderComp({
         parentPath: "Reports",
         datasourceType: DatasourceType.JDBC,
      });

      comp.create("MySQL");

      expect(routerMock.navigate).toHaveBeenCalledWith(
         ["datasources/database/listing", "MySQL", "Reports"],
         { relativeTo: parentRouteMock },
      );
   });

   it("should navigate to datasources/database/listing with empty parentPath when none is set", async () => {
      const { comp, routerMock, parentRouteMock } = await renderComp({
         parentPath: "",
         datasourceType: DatasourceType.JDBC,
      });

      comp.create("PostgreSQL");

      expect(routerMock.navigate).toHaveBeenCalledWith(
         ["datasources/database/listing", "PostgreSQL", ""],
         { relativeTo: parentRouteMock },
      );
   });
});

// ── Group 4 — create: TABULAR type [Risk 3] ──────────────────────────────────

describe("DatasourceSelectionViewComponent — create (TABULAR type)", () => {
   // 🔁 Regression-sensitive: TABULAR connectors use the datasource listing wizard, not the
   // database schema editor. Routing to the wrong path would show the wrong UI.

   it("should navigate to datasources/datasource/listing with name and parentPath", async () => {
      const { comp, routerMock, parentRouteMock } = await renderComp({
         parentPath: "MyFolder",
         datasourceType: DatasourceType.TABULAR,
      });

      comp.create("Salesforce");

      expect(routerMock.navigate).toHaveBeenCalledWith(
         ["datasources/datasource/listing", "Salesforce", "MyFolder"],
         { relativeTo: parentRouteMock },
      );
   });
});

// ── Group 5 — create: CUBE type [Risk 3] ─────────────────────────────────────

describe("DatasourceSelectionViewComponent — create (CUBE type)", () => {
   // 🔁 Regression-sensitive: CUBE/XMLA navigation does NOT include the listing name —
   // adding it would break the server-side route match.

   it("should navigate to datasources/datasource/xmla/new with only parentPath (no listing name)", async () => {
      const { comp, routerMock, parentRouteMock } = await renderComp({
         parentPath: "OLAPFolder",
         datasourceType: DatasourceType.CUBE,
      });

      comp.create("SSAS");

      expect(routerMock.navigate).toHaveBeenCalledWith(
         ["datasources/datasource/xmla/new", "OLAPFolder"],
         { relativeTo: parentRouteMock },
      );
   });
});

// ── Group 6 — create: uses selectedListingName when listingName param is falsy [Risk 2] ──

describe("DatasourceSelectionViewComponent — create (fallback to selectedListingName)", () => {
   // 🔁 Regression-sensitive: the listing pane calls create("") to trigger the button action,
   // relying on selectedListingName being used as the fallback. An empty string passed as the
   // name segment to the server results in a 404.

   it("should use selectedListingName when listingName param is an empty string", async () => {
      const { comp, routerMock, parentRouteMock } = await renderComp({
         datasourceType: DatasourceType.JDBC,
      });
      comp.selectedListingName = "MySQL";

      comp.create(""); // empty string → falsy → falls back to selectedListingName

      expect(routerMock.navigate).toHaveBeenCalledWith(
         ["datasources/database/listing", "MySQL", ""],
         { relativeTo: parentRouteMock },
      );
   });

   it("should use selectedListingName when listingName param is null", async () => {
      const { comp, routerMock, parentRouteMock } = await renderComp({
         datasourceType: DatasourceType.TABULAR,
      });
      comp.selectedListingName = "Salesforce";

      comp.create(null);

      expect(routerMock.navigate).toHaveBeenCalledWith(
         ["datasources/datasource/listing", "Salesforce", ""],
         { relativeTo: parentRouteMock },
      );
   });
});
