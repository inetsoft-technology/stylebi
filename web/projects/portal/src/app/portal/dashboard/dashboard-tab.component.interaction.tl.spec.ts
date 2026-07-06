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
 * DashboardTabComponent — P1: interaction (selectTab navigation flows)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — selectTab to a different tab: router.navigate to the dashboard route
 *   Group 2 [Risk 3] — selectTab same tab, asset not loading: reloadDashboard → double navigate
 *   Group 3 [Risk 2] — selectTab same tab, loading, confirm "ok": reloadDashboard fires
 *   Group 4 [Risk 2] — selectTab same tab, loading, confirm dismissed: NO reloadDashboard
 *   Group 5 [Risk 1] — currentRouteService.dashboard subscription updates selectedDashboardName
 *
 * Out of scope (covered in other passes):
 *   deleteDashboard / editDashboard / openArrangeDashboard → P2 (risk)
 *   ngOnInit / ngOnDestroy / editDeleteDashboardDisabled → P3 (display)
 */

import { waitFor } from "@testing-library/angular";

import { ComponentTool } from "../../common/util/component-tool";
import {
   clearAllMocks,
   DASHBOARD_1,
   DASHBOARD_2,
   newDashboard$,
   ROUTER_MOCK,
   ASSET_LOADING_SERVICE_MOCK,
   TAB_MODEL,
   renderComp,
} from "./dashboard-tab.component.test-helpers";

beforeEach(() => clearAllMocks());
afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1 — selectTab to a different dashboard
// ---------------------------------------------------------------------------

describe("Group 1 — selectTab: navigate to a different dashboard (no reload)", () => {
   // 🔁 Regression-sensitive: when the selected dashboard changes, the route must navigate
   //    to the new dashboard URL — NOT trigger a reload of the current one.
   it("should navigate to the target dashboard URL when switching to a different tab", async () => {
      const { comp } = await renderComp({ currentDashboard: "DB1" });

      comp.selectTab(1); // switch from DB1 → DB2

      expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
         ["/portal/tab/dashboard/vs/dashboard/DB2"],
         { queryParams: {} }
      );
   });

   it("should set selectedDashboard to a clone of the target dashboard", async () => {
      const { comp } = await renderComp({ currentDashboard: "DB1" });

      comp.selectTab(1);

      expect(comp.selectedDashboard?.name).toBe("DB2");
   });
});

// ---------------------------------------------------------------------------
// Group 2 — selectTab same tab, not loading (reload without confirm)
// ---------------------------------------------------------------------------

describe("Group 2 — selectTab same tab, not loading: reloadDashboard without confirm", () => {
   // 🔁 Regression-sensitive: reload navigates to a temp route first to force Angular to
   //    re-create the dashboard component, then immediately navigates to the real route.
   //    Both navigations must fire; omitting either breaks the reload mechanism.
   it("should navigate twice (temp then target) when reloading a tab that is not loading", async () => {
      const { comp } = await renderComp({ currentDashboard: "DB1" });
      ROUTER_MOCK.navigate.mockClear();

      comp.selectTab(0); // same tab, isLoading=false → no confirm

      await waitFor(() => expect(ROUTER_MOCK.navigate).toHaveBeenCalledTimes(2));
      expect(ROUTER_MOCK.navigate).toHaveBeenNthCalledWith(
         1,
         ["/portal/tab/dashboard"],
         { queryParams: { notLoadDashboard: "true" } }
      );
      expect(ROUTER_MOCK.navigate).toHaveBeenNthCalledWith(
         2,
         ["/portal/tab/dashboard/vs/dashboard/DB1"],
         { queryParams: {} }
      );
   });
});

// ---------------------------------------------------------------------------
// Group 3 — selectTab same tab, loading, confirm "ok"
// ---------------------------------------------------------------------------

describe("Group 3 — selectTab same tab, loading: confirm ok → reloadDashboard", () => {
   it("should show confirm dialog and call reloadDashboard after user clicks ok", async () => {
      ASSET_LOADING_SERVICE_MOCK.isLoading.mockReturnValueOnce(true);
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const { comp } = await renderComp({ currentDashboard: "DB1" });
      ROUTER_MOCK.navigate.mockClear();

      comp.selectTab(0);

      expect(confirmSpy).toHaveBeenCalled();
      await waitFor(() => expect(ROUTER_MOCK.navigate).toHaveBeenCalledTimes(2)); // reloadDashboard double navigate
   });
});

// ---------------------------------------------------------------------------
// Group 4 — selectTab same tab, loading, confirm dismissed
// ---------------------------------------------------------------------------

describe("Group 4 — selectTab same tab, loading: confirm dismissed → no navigate", () => {
   it("should NOT navigate when user dismisses the reload confirm dialog", async () => {
      ASSET_LOADING_SERVICE_MOCK.isLoading.mockReturnValueOnce(true);
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");
      const { comp } = await renderComp({ currentDashboard: "DB1" });
      ROUTER_MOCK.navigate.mockClear();

      comp.selectTab(0);

      await new Promise<void>(r => setTimeout(r, 0));
      expect(ROUTER_MOCK.navigate).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 5 — currentRouteService.dashboard subscription
// ---------------------------------------------------------------------------

describe("Group 5 — currentRouteService.dashboard subscription: updates selected name", () => {
   it("should set selectedDashboardName from the currentRouteService.dashboard stream", async () => {
      // The BehaviorSubject("DB1") emits during construction via currentRouteService.dashboard,
      // which sets selectedDashboardName before route.data fires.
      const { comp } = await renderComp({ currentDashboard: "DB1" });
      expect(comp.selectedDashboard?.name).toBe("DB1");
   });

   it("should strip ;hasBaseEntry= suffix from dashboard names with embedded query params", async () => {
      const { comp } = await renderComp({ currentDashboard: "DB1;hasBaseEntry=true" });
      // After stripping, the name resolves to "DB1" → matched dashboard found
      expect(comp.selectedDashboard?.name).toBe("DB1");
   });

   it("should call editDashboard(null) when newDashboard$ emits", async () => {
      const { comp } = await renderComp();
      const editSpy = vi.spyOn(comp, "editDashboard").mockImplementation(() => {});

      newDashboard$.next();

      expect(editSpy).toHaveBeenCalledWith(null);
   });
});
