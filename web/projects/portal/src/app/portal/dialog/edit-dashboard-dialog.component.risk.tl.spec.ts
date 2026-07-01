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
 * EditDashboardDialog - Pass 2: Risk
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - closeDialog and editDashboard output/error contracts
 *   Group 2 [Risk 3] - okClicked new-dashboard duplicate and create flows
 *   Group 3 [Risk 3] - okClicked existing-dashboard rename flows
 *
 * Mocking strategy:
 *   - direct HttpClient -> provideHttpClient() + MSW
 *   - dialog helpers / browser-tab launch -> spies
 */

import { waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";

import { Tool } from "../../../../../shared/util/tool";
import { ComponentTool } from "../../common/util/component-tool";
import { GuiTool } from "../../common/util/gui-tool";
import { server } from "@test-mocks/server";
import {
   createEditDashboardDialog,
   makeDashboardModel,
} from "./edit-dashboard-dialog.component.test-helpers";

afterEach(() => {
   vi.restoreAllMocks();
});

describe("EditDashboardDialog - risk", () => {
   describe("Group 1 - closeDialog and editDashboard", () => {
      it("should emit cancel from closeDialog", () => {
         const { comp } = createEditDashboardDialog();
         const cancelSpy = vi.spyOn(comp.onCancel, "emit");

         comp.closeDialog();

         expect(cancelSpy).toHaveBeenCalledWith("cancel");
      });

      it("should post an edited dashboard, open composer when requested, and emit commit", async () => {
         let requestBody: unknown;
         server.use(
            http.post("*/api/portal/dashboard/edit/*", async ({ request }) => {
               requestBody = await request.json();
               return HttpResponse.json({});
            }),
         );
         const openBrowserSpy = vi.spyOn(GuiTool, "openBrowserTab").mockImplementation(() => {});
         const { comp } = createEditDashboardDialog();
         const commitSpy = vi.spyOn(comp.onCommit, "emit");
         comp.dashboard = makeDashboardModel({ identifier: "dash-id" });
         comp.oldName = "Construction";
         comp.compose = true;

         comp.editDashboard(comp.dashboard);

         await waitFor(() => expect(commitSpy).toHaveBeenCalledWith(comp.dashboard));
         expect(requestBody).toEqual(comp.dashboard);
         expect(openBrowserSpy).toHaveBeenCalledWith(
            "composer",
            expect.objectContaining({
               get: expect.any(Function),
            }),
         );
      });

      it("should show no-permission and emit cancel when editDashboard fails", async () => {
         server.use(
            http.post("*/api/portal/dashboard/edit/*", () =>
               HttpResponse.json({ message: "denied" }, { status: 403 }),
            ),
         );
         const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog")
            .mockReturnValue(Promise.resolve());
         const { comp } = createEditDashboardDialog();
         const cancelSpy = vi.spyOn(comp.onCancel, "emit");
         comp.dashboard = makeDashboardModel();
         comp.oldName = "Construction";

         comp.editDashboard(comp.dashboard);
         await waitFor(() => expect(messageSpy).toHaveBeenCalled());
         await Promise.resolve();

         expect(cancelSpy).toHaveBeenCalledWith("cancel");
      });
   });

   describe("Group 2 - okClicked for new dashboards", () => {
      it("should show the duplicate-name dialog and stop before creating a new dashboard", async () => {
         server.use(
            http.get("*/api/portal/dashboard/duplicate/*", () =>
               HttpResponse.json(true),
            ),
         );
         const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog")
            .mockReturnValue(Promise.resolve());
         const { comp } = createEditDashboardDialog();
         comp.dashboard = makeDashboardModel({ path: "Examples/Construction Dashboard" });
         comp.name = "Construction";
         comp.isNew = true;

         comp.okClicked();

         await waitFor(() =>
            expect(messageSpy).toHaveBeenCalledWith(
               expect.anything(),
               "_#(js:Error)",
               "_#(js:viewer.dashboard.nameValid)",
            ),
         );
      });

      it("should create a new dashboard, clear target fields in compose mode, and emit commit", async () => {
         let requestBody: unknown;
         server.use(
            http.get("*/api/portal/dashboard/duplicate/*", () =>
               HttpResponse.json(false),
            ),
            http.post("*/api/portal/dashboard/new", async ({ request }) => {
               requestBody = await request.json();
               return HttpResponse.json({
                  name: "Construction",
                  identifier: "new-id",
                  path: null,
               });
            }),
         );
         const cloneSpy = vi.spyOn(Tool, "clone").mockImplementation(value => structuredClone(value));
         const openBrowserSpy = vi.spyOn(GuiTool, "openBrowserTab").mockImplementation(() => {});
         const { comp } = createEditDashboardDialog();
         const commitSpy = vi.spyOn(comp.onCommit, "emit");
         comp.dashboard = makeDashboardModel();
         comp.name = "Construction";
         comp.isNew = true;
         comp.compose = true;

         comp.okClicked();

         await waitFor(() =>
            expect(commitSpy).toHaveBeenCalledWith(
               expect.objectContaining({ identifier: "new-id", name: "Construction" }),
            ),
         );
         expect(requestBody).toEqual(expect.objectContaining({
            name: "Construction",
            path: null,
            identifier: null,
         }));
         expect(openBrowserSpy).toHaveBeenCalled();
         expect(cloneSpy).toHaveBeenCalledWith(comp.dashboard);
      });

      it("should show no-permission and cancel when duplicate validation fails for a new dashboard", async () => {
         server.use(
            http.get("*/api/portal/dashboard/duplicate/*", () =>
               HttpResponse.json({ message: "denied" }, { status: 403 }),
            ),
         );
         const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog")
            .mockReturnValue(Promise.resolve());
         const { comp } = createEditDashboardDialog();
         const cancelSpy = vi.spyOn(comp.onCancel, "emit");
         comp.dashboard = makeDashboardModel();
         comp.name = "Construction";
         comp.isNew = true;

         comp.okClicked();
         await waitFor(() => expect(messageSpy).toHaveBeenCalled());
         await Promise.resolve();

         expect(cancelSpy).toHaveBeenCalledWith("cancel");
      });
   });

   describe("Group 3 - okClicked for existing dashboards", () => {
      it("should show the duplicate-name dialog when a renamed dashboard conflicts", async () => {
         server.use(
            http.get("*/api/portal/dashboard/duplicate/*", () =>
               HttpResponse.json(true),
            ),
         );
         const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog")
            .mockReturnValue(Promise.resolve());
         const { comp } = createEditDashboardDialog();
         comp.dashboard = makeDashboardModel({ name: "Construction__GLOBAL" });
         comp.oldName = "Construction__GLOBAL";
         comp.name = "Renamed";
         comp.global = true;
         comp.isNew = false;

         comp.okClicked();

         await waitFor(() =>
            expect(messageSpy).toHaveBeenCalledWith(
               expect.anything(),
               "_#(js:Error)",
               "_#(js:viewer.nameValid)",
            ),
         );
      });

      it("should delegate to editDashboard with the global suffix when the renamed dashboard is unique", async () => {
         server.use(
            http.get("*/api/portal/dashboard/duplicate/*", () =>
               HttpResponse.json(false),
            ),
         );
         const { comp } = createEditDashboardDialog();
         comp.dashboard = makeDashboardModel({ name: "Construction__GLOBAL" });
         comp.oldName = "Construction__GLOBAL";
         comp.name = "Renamed";
         comp.global = true;
         comp.isNew = false;
         const editSpy = vi.spyOn(comp, "editDashboard").mockImplementation(() => {});

         comp.okClicked();

         await waitFor(() =>
            expect(editSpy).toHaveBeenCalledWith(
               expect.objectContaining({ name: "Renamed__GLOBAL" }),
            ),
         );
      });

      it("should call editDashboard directly when the dashboard name is unchanged", () => {
         const { comp } = createEditDashboardDialog();
         comp.dashboard = makeDashboardModel({ name: "Construction" });
         comp.oldName = "Construction";
         comp.name = "Construction";
         comp.global = false;
         comp.isNew = false;
         const editSpy = vi.spyOn(comp, "editDashboard").mockImplementation(() => {});

         comp.okClicked();

         expect(editSpy).toHaveBeenCalledWith(expect.objectContaining({ name: "Construction" }));
      });
   });
});
