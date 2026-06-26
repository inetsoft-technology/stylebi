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
 * DashboardTabComponent — P2: risk (deleteDashboard HTTP flow + updateSelectedDashboard)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — deleteDashboard: confirm ok → DELETE → success=true, dashboards remain
 *                       → updateModel → selectDashboard(first)
 *   Group 2 [Risk 3] — deleteDashboard: confirm ok → DELETE → success=true, no dashboards left
 *                       → navigate to /portal/tab/dashboard
 *   Group 3 [Risk 2] — deleteDashboard: DELETE → success=false (body) → showMessageDialog
 *   Group 4 [Risk 2] — deleteDashboard: DELETE → HTTP error → showMessageDialog(noPermission)
 *   Group 5 [Risk 2] — deleteDashboard: editDeleteDashboardDisabled → skips confirm dialog
 *   Group 6 [Risk 2] — updateSelectedDashboard: name match; identifier fallback; not found → null
 *
 * Out of scope (covered in other passes):
 *   selectTab navigation → P1 (interaction)
 *   ngOnInit / ngOnDestroy / getDashboardLabel → P3 (display)
 */

import { waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { of } from "rxjs";

import { server } from "@test-mocks/server";
import { ComponentTool } from "../../common/util/component-tool";
import {
   clearAllMocks,
   DASHBOARD_1,
   DASHBOARD_2,
   ROUTER_MOCK,
   MODEL_SERVICE_MOCK,
   TAB_MODEL,
   renderComp,
} from "./dashboard-tab.component.test-helpers";
import { DashboardTabModel } from "./dashboard-tab-model";

beforeEach(() => clearAllMocks());
afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1 — deleteDashboard: success, dashboards remain → selectDashboard(first)
// ---------------------------------------------------------------------------

describe("Group 1 — deleteDashboard: success with remaining dashboards", () => {
   // 🔁 Regression-sensitive: after delete the component must call selectDashboard(dashboards[0])
   //    and navigate to that dashboard, not stay on the deleted one.
   it("should navigate to the first remaining dashboard after a successful delete", async () => {
      const updatedModel: DashboardTabModel = {
         ...TAB_MODEL,
         dashboards: [DASHBOARD_2],
      };
      MODEL_SERVICE_MOCK.getModel.mockReturnValueOnce(of(updatedModel));
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");

      server.use(
         http.delete("*/api/portal/dashboard/deleteDashboard/DB1", () =>
            MswHttpResponse.json(true)
         )
      );

      const { comp } = await renderComp({ currentDashboard: "DB1" });
      ROUTER_MOCK.navigate.mockClear();

      comp.deleteDashboard();

      await waitFor(() =>
         expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(
            ["/portal/tab/dashboard/vs/dashboard/DB2"],
            expect.anything()
         )
      );
      expect(comp.selectedDashboardIndex).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — deleteDashboard: success, no dashboards remain → navigate root
// ---------------------------------------------------------------------------

describe("Group 2 — deleteDashboard: success with no dashboards left", () => {
   it("should navigate to /portal/tab/dashboard when no dashboards remain after delete", async () => {
      const emptyModel: DashboardTabModel = { ...TAB_MODEL, dashboards: [] };
      MODEL_SERVICE_MOCK.getModel.mockReturnValueOnce(of(emptyModel));
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");

      server.use(
         http.delete("*/api/portal/dashboard/deleteDashboard/DB1", () =>
            MswHttpResponse.json(true)
         )
      );

      const { comp } = await renderComp({ currentDashboard: "DB1" });
      ROUTER_MOCK.navigate.mockClear();

      comp.deleteDashboard();

      await waitFor(() =>
         expect(ROUTER_MOCK.navigate).toHaveBeenCalledWith(["/portal/tab/dashboard"])
      );
      expect(comp.selectedDashboard).toBeNull();
      expect(comp.selectedDashboardIndex).toBe(-1);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — deleteDashboard: success=false (response body) → error dialog
// ---------------------------------------------------------------------------

describe("Group 3 — deleteDashboard: DELETE returns false body → showMessageDialog", () => {
   it("should show dashboardDeleteError dialog when DELETE returns false", async () => {
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");

      server.use(
         http.delete("*/api/portal/dashboard/deleteDashboard/DB1", () =>
            MswHttpResponse.json(false)
         )
      );

      const { comp } = await renderComp({ currentDashboard: "DB1" });
      comp.deleteDashboard();

      await waitFor(() => expect(msgSpy).toHaveBeenCalled());
      expect(msgSpy).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:Error)",
         "_#(js:viewer.dashboardDeleteError)"
      );
   });
});

// ---------------------------------------------------------------------------
// Group 4 — deleteDashboard: HTTP error → noPermission dialog
// ---------------------------------------------------------------------------

describe("Group 4 — deleteDashboard: HTTP error → showMessageDialog(noPermission)", () => {
   it("should show noPermission dialog on HTTP error during delete", async () => {
      vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const msgSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue("ok");

      server.use(
         http.delete("*/api/portal/dashboard/deleteDashboard/DB1", () =>
            MswHttpResponse.error()
         )
      );

      const { comp } = await renderComp({ currentDashboard: "DB1" });
      comp.deleteDashboard();

      await waitFor(() => expect(msgSpy).toHaveBeenCalled());
      expect(msgSpy).toHaveBeenCalledWith(
         expect.anything(),
         "_#(js:Error)",
         "_#(js:common.noPermission)"
      );
   });
});

// ---------------------------------------------------------------------------
// Group 5 — deleteDashboard: guard (editDeleteDashboardDisabled) → early return
// ---------------------------------------------------------------------------

describe("Group 5 — deleteDashboard: skips when editDeleteDashboardDisabled", () => {
   it("should NOT open confirm dialog when model.editable=false", async () => {
      const notEditableModel: DashboardTabModel = { ...TAB_MODEL, editable: false };
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");

      const { comp } = await renderComp({ currentDashboard: "DB1", model: notEditableModel });

      comp.deleteDashboard();

      expect(confirmSpy).not.toHaveBeenCalled();
   });

   it("should NOT open confirm dialog when selectedDashboard is null", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const { comp } = await renderComp(); // no currentDashboard → selectedDashboard=null

      comp.deleteDashboard();

      expect(confirmSpy).not.toHaveBeenCalled();
   });

   it("should NOT open confirm dialog when selectedDashboard.type='g' (global)", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const { comp } = await renderComp({ currentDashboard: "DB1" });
      // Override type after render to simulate a global dashboard
      (comp.selectedDashboard as any).type = "g";

      comp.deleteDashboard();

      expect(confirmSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 6 — updateSelectedDashboard: name match, identifier fallback, not found
// ---------------------------------------------------------------------------

describe("Group 6 — updateSelectedDashboard: name, identifier fallback, not found", () => {
   // WHY private bypass: updateSelectedDashboard is private; its effects are observed via
   //   the public selectedDashboard / selectedDashboardName / selectedDashboardIndex fields,
   //   which are set as BehaviorSubject emissions resolve during render.
   it("should resolve selectedDashboard by name when name matches a dashboard", async () => {
      const { comp } = await renderComp({ currentDashboard: "DB2" });
      expect(comp.selectedDashboard?.name).toBe("DB2");
      expect(comp.selectedDashboardIndex).toBe(1);
   });

   it("should fall back to identifier when name does not match any dashboard", async () => {
      // "db1-id" is the identifier of DASHBOARD_1; its name is "DB1"
      const { comp } = await renderComp({ currentDashboard: "db1-id" });
      expect(comp.selectedDashboard?.name).toBe("DB1");
      expect(comp.selectedDashboardName).toBe("DB1");
   });

   it("should set selectedDashboard=null when neither name nor identifier matches", async () => {
      const { comp } = await renderComp({ currentDashboard: "nonexistent" });
      // updateSelectedDashboard sets null when not found;
      // ngOnInit also doesn't find the name so leaves it null.
      expect(comp.selectedDashboard).toBeNull();
   });
});
