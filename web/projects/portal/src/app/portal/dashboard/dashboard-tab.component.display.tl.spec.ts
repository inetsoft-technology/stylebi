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
 * DashboardTabComponent — P3: display (lifecycle, getters, disabled state)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — ngOnInit: empty dashboards list → navigate to /portal/tab/dashboard
 *   Group 2 [Risk 2] — ngOnInit: model + name match → sets selectedDashboard; no navigation
 *   Group 3 [Risk 2] — ngOnDestroy: subscriptions.unsubscribe() prevents further emissions
 *   Group 4 [Risk 1] — editDeleteDashboardDisabled: returns true for each guard condition
 *   Group 5 [Risk 1] — getDashboardLabel: delegates to dashboard.label
 *
 * Out of scope (covered in other passes):
 *   selectTab navigation → P1 (interaction)
 *   deleteDashboard HTTP → P2 (risk)
 */

import {
   clearAllMocks,
   DASHBOARD_1,
   DASHBOARD_2,
   newDashboard$,
   ROUTER_MOCK,
   TAB_MODEL,
   renderComp,
} from "./dashboard-tab.component.test-helpers";
import { DashboardTabModel } from "./dashboard-tab-model";

beforeEach(() => clearAllMocks());
afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1 — ngOnInit: empty dashboards → navigate
// ---------------------------------------------------------------------------

describe("Group 1 — ngOnInit: empty dashboard list → navigate to root", () => {
   // 🔁 Regression-sensitive: when the route resolver returns no dashboards, the component
   //    must immediately navigate away — staying on the tab would show a blank page.
   it("should navigate to /portal/tab/dashboard when model has no dashboards", async () => {
      const emptyModel: DashboardTabModel = { ...TAB_MODEL, dashboards: [] };

      await renderComp({ model: emptyModel });

      expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(["/portal/tab/dashboard"]);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — ngOnInit: model with matching dashboard → sets selection
// ---------------------------------------------------------------------------

describe("Group 2 — ngOnInit: matching dashboard name → sets selectedDashboard", () => {
   it("should set selectedDashboard to the matched dashboard (no navigation)", async () => {
      const { comp } = await renderComp({ currentDashboard: "DB1" });

      expect(comp.selectedDashboard?.name).toBe("DB1");
      expect(comp.selectedDashboardIndex).toBe(0);
      // navigate must NOT have been called (dashboards exist and name matched)
      expect(ROUTER_MOCK.navigate).not.toHaveBeenCalled();
   });

   it("should NOT navigate when selectedDashboardName is null at ngOnInit time", async () => {
      // When no currentDashboard is set, selectedDashboardName=null; ngOnInit
      // finds no index match but dashboards.length>0 → no navigate.
      const { comp } = await renderComp();

      expect(comp.selectedDashboard).toBeFalsy();
      expect(ROUTER_MOCK.navigate).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 3 — ngOnDestroy: subscriptions cleaned up
// ---------------------------------------------------------------------------

describe("Group 3 — ngOnDestroy: unsubscribes from all streams", () => {
   it("should call subscriptions.unsubscribe() on destroy", async () => {
      // WHY private bypass: subscriptions is a private Subscription aggregate;
      //   accessing it is the only way to verify that unsubscribe is called.
      const { comp, fixture } = await renderComp();
      const unsubSpy = vi.spyOn((comp as any).subscriptions, "unsubscribe");

      fixture.destroy();

      expect(unsubSpy).toHaveBeenCalled();
   });

   it("should NOT call editDashboard after destroy when newDashboard$ emits", async () => {
      const { comp, fixture } = await renderComp();
      fixture.destroy();
      const editSpy = vi.spyOn(comp, "editDashboard").mockImplementation(() => {});

      newDashboard$.next();

      expect(editSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 4 — editDeleteDashboardDisabled
// ---------------------------------------------------------------------------

describe("Group 4 — editDeleteDashboardDisabled: guard conditions", () => {
   it("should return true when model.editable=false", async () => {
      const { comp } = await renderComp({
         currentDashboard: "DB1",
         model: { ...TAB_MODEL, editable: false },
      });

      expect(comp.editDeleteDashboardDisabled()).toBe(true);
   });

   it("should return true when selectedDashboard is null", async () => {
      const { comp } = await renderComp(); // no currentDashboard → null

      expect(comp.editDeleteDashboardDisabled()).toBe(true);
   });

   it("should return true when selectedDashboard.type='g' (global dashboard)", async () => {
      const { comp } = await renderComp({ currentDashboard: "DB1" });
      (comp.selectedDashboard as any).type = "g";

      expect(comp.editDeleteDashboardDisabled()).toBe(true);
   });

   it("should return false when editable=true, selectedDashboard set, type≠'g'", async () => {
      const { comp } = await renderComp({ currentDashboard: "DB1" });

      expect(comp.editDeleteDashboardDisabled()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — getDashboardLabel
// ---------------------------------------------------------------------------

describe("Group 5 — getDashboardLabel: delegates to dashboard.label", () => {
   it("should return the label property of the given DashboardModel", async () => {
      const { comp } = await renderComp();

      expect(comp.getDashboardLabel(DASHBOARD_1)).toBe("Dashboard 1");
      expect(comp.getDashboardLabel(DASHBOARD_2)).toBe("Dashboard 2");
   });
});
