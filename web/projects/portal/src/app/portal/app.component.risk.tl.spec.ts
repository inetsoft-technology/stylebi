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
 * PortalAppComponent — Pass 2: Async / state-consistency risk paths
 *
 * Covers: updateAccessibility, setTabOrder, checkDefaultTab, showPreferences,
 *         handleMessageEvent
 *
 * Shared fixtures from app.component.test-helpers.ts (Pass 1 sibling uses the same).
 * Mocking strategy: HttpClient replaced by vi.fn() mock — no MSW needed; all
 * HTTP calls are stub-intercepted at the service-mock level.
 */

import { vi } from "vitest";
import { Subject } from "rxjs";
import { PreferencesDialog } from "./dialog/preferences-dialog.component";
import {
   beforeEachCleanup,
   DASHBOARD_TAB,
   DATA_TAB,
   makePortalModel,
   renderComponent,
   REPORT_TAB,
   SCHEDULE_TAB,
} from "./app.component.test-helpers";

beforeEach(beforeEachCleanup);
afterEach(() => vi.restoreAllMocks());

// ── Group 1: updateAccessibility ──────────────────────────────────────────────

describe("PortalAppComponent — updateAccessibility", () => {

   it("adds 'accessible' class to document.body when model.accessible is true", async () => {
      const { comp } = await renderComponent();
      comp.model = makePortalModel({ accessible: true });

      comp.updateAccessibility();

      expect(document.body.classList.contains("accessible")).toBe(true);
   });

   it("does not add 'accessible' class to document.body when model.accessible is false", async () => {
      const { comp } = await renderComponent();
      comp.model = makePortalModel({ accessible: false });

      comp.updateAccessibility();

      expect(document.body.classList.contains("accessible")).toBe(false);
   });
});

// ── Group 2: setTabOrder ──────────────────────────────────────────────────────

describe("PortalAppComponent — setTabOrder", () => {

   it("defaults reportTabFirst=true and dataTabFirst=true when portalTabs is null", async () => {
      const { comp } = await renderComponent();
      comp.portalTabs = null as any;

      comp.setTabOrder();

      expect(comp.reportTabFirst).toBe(true);
      expect(comp.dataTabFirst).toBe(true);
   });

   it("sets reportTabFirst=true when report tab comes before dashboard tab", async () => {
      const { comp } = await renderComponent();
      // Pre-set to false to prove setTabOrder() actively computed the value (A2).
      comp.reportTabFirst = false;
      comp.portalTabs = [REPORT_TAB, DASHBOARD_TAB];

      comp.setTabOrder();

      expect(comp.reportTabFirst).toBe(true);
   });

   it("sets reportTabFirst=false when dashboard tab comes before report tab", async () => {
      const { comp } = await renderComponent();
      comp.portalTabs = [DASHBOARD_TAB, REPORT_TAB];

      comp.setTabOrder();

      expect(comp.reportTabFirst).toBe(false);
   });

   it("sets dataTabFirst=true when schedule tab comes before data tab", async () => {
      const { comp } = await renderComponent();
      // Pre-set to false to prove setTabOrder() actively computed the value (A2).
      comp.dataTabFirst = false;
      comp.portalTabs = [SCHEDULE_TAB, DATA_TAB];

      comp.setTabOrder();

      expect(comp.dataTabFirst).toBe(true);
   });

   it("sets dataTabFirst=false when data tab comes before schedule tab", async () => {
      const { comp } = await renderComponent();
      comp.portalTabs = [DATA_TAB, SCHEDULE_TAB];

      comp.setTabOrder();

      expect(comp.dataTabFirst).toBe(false);
   });
});

// ── Group 3: checkDefaultTab ──────────────────────────────────────────────────

describe("PortalAppComponent — checkDefaultTab", () => {

   it("does not navigate when currentUrl is not '/portal'", async () => {
      const { comp, routerMock } = await renderComponent();
      comp.currentUrl = "/portal/tab/dashboard";
      comp.model = makePortalModel({ homeVisible: false, hasDashboards: true });
      comp.portalTabs = [DASHBOARD_TAB, REPORT_TAB];

      comp.checkDefaultTab();

      expect(routerMock.navigate).not.toHaveBeenCalled();
   });

   it("does not navigate when model is null", async () => {
      const { comp, routerMock } = await renderComponent();
      comp.currentUrl = "/portal";
      comp.model = null as any; // testing the this.model guard
      comp.portalTabs = [DASHBOARD_TAB, REPORT_TAB];

      comp.checkDefaultTab();

      expect(routerMock.navigate).not.toHaveBeenCalled();
   });

   it("does not navigate when model.homeVisible is true", async () => {
      const { comp, routerMock } = await renderComponent();
      comp.currentUrl = "/portal";
      comp.model = makePortalModel({ homeVisible: true, hasDashboards: true });
      comp.portalTabs = [DASHBOARD_TAB, REPORT_TAB];

      comp.checkDefaultTab();

      expect(routerMock.navigate).not.toHaveBeenCalled();
   });

   it("does not navigate when portalTabs is undefined", async () => {
      const { comp, routerMock } = await renderComponent();
      comp.currentUrl = "/portal";
      comp.model = makePortalModel({ homeVisible: false, hasDashboards: true });
      comp.portalTabs = undefined as any;

      comp.checkDefaultTab();

      expect(routerMock.navigate).not.toHaveBeenCalled();
   });

   it("navigates to dashboard uri when hasDashboards=true and dashboard tab exists", async () => {
      const { comp, routerMock } = await renderComponent();
      comp.currentUrl = "/portal";
      comp.model = makePortalModel({ homeVisible: false, hasDashboards: true });
      comp.portalTabs = [DASHBOARD_TAB, REPORT_TAB];

      comp.checkDefaultTab();

      expect(routerMock.navigate).toHaveBeenCalledWith(["/portal/" + DASHBOARD_TAB.uri]);
   });

   it("navigates to report uri when hasDashboards=false and report tab exists", async () => {
      const { comp, routerMock } = await renderComponent();
      comp.currentUrl = "/portal";
      comp.model = makePortalModel({ homeVisible: false, hasDashboards: false });
      comp.portalTabs = [DASHBOARD_TAB, REPORT_TAB];

      comp.checkDefaultTab();

      expect(routerMock.navigate).toHaveBeenCalledWith(["/portal/" + REPORT_TAB.uri]);
   });
});

// ── Group 4: showPreferences ──────────────────────────────────────────────────

describe("PortalAppComponent — showPreferences", () => {

   it("opens PreferencesDialog with size='lg' and backdrop='static'", async () => {
      const { comp, modalSvc } = await renderComponent();

      comp.showPreferences();

      expect(modalSvc.open).toHaveBeenCalledWith(
         PreferencesDialog,
         expect.objectContaining({ size: "lg", backdrop: "static" }),
      );
   });

   it("calls historyBarService.refreshStatus() after dialog commits", async () => {
      const { comp, historyBarSvc } = await renderComponent();
      let resolveModal!: (v: unknown) => void;
      // Override private modalService — the default mock's result never resolves;
      // a resolvable promise is required to trigger modal.result.then(onCommit) (E2).
      (comp as any).modalService = {
         open: vi.fn(() => ({
            result:            new Promise<unknown>(res => { resolveModal = res; }),
            componentInstance: { onCommit: new Subject<string>(), onCancel: new Subject<void>() },
            close:             vi.fn(),
            dismiss:           vi.fn(),
         })),
      };

      comp.showPreferences();
      resolveModal("ok");
      await Promise.resolve(); // flush modal.result.then() microtask

      expect(historyBarSvc.refreshStatus).toHaveBeenCalled();
   });

   it("opens preferences dialog when queryParam openDialog=preferences is present", async () => {
      const { modalSvc } = await renderComponent({
         queryParams: { openDialog: "preferences" },
      });

      expect(modalSvc.open).toHaveBeenCalledWith(
         PreferencesDialog,
         expect.objectContaining({ size: "lg", backdrop: "static" }),
      );
   });
});

// ── Group 5: handleMessageEvent ───────────────────────────────────────────────

describe("PortalAppComponent — handleMessageEvent", () => {

   it("ignores events with null data", async () => {
      const { comp } = await renderComponent();
      const spy = vi.spyOn(comp, "showPreferences").mockImplementation(() => {});
      try {
         expect(() => comp.handleMessageEvent({ data: null } as any)).not.toThrow();
         expect(spy).not.toHaveBeenCalled();
      }
      finally {
         spy.mockRestore();
      }
   });

   it("ignores events when data.event is not 'openDialog'", async () => {
      const { comp } = await renderComponent();
      const spy = vi.spyOn(comp, "showPreferences").mockImplementation(() => {});
      try {
         comp.handleMessageEvent({ data: { event: "someOtherEvent", dialogName: "preferences" } } as any);
         expect(spy).not.toHaveBeenCalled();
      }
      finally {
         spy.mockRestore();
      }
   });

   it("ignores openDialog events when dialogName is not 'preferences'", async () => {
      const { comp } = await renderComponent();
      const spy = vi.spyOn(comp, "showPreferences").mockImplementation(() => {});
      try {
         comp.handleMessageEvent({ data: { event: "openDialog", dialogName: "profile" } } as any);
         expect(spy).not.toHaveBeenCalled();
      }
      finally {
         spy.mockRestore();
      }
   });

   it("calls showPreferences() when event is openDialog with dialogName=preferences", async () => {
      const { comp } = await renderComponent();
      const spy = vi.spyOn(comp, "showPreferences").mockImplementation(() => {});
      try {
         comp.handleMessageEvent({ data: { event: "openDialog", dialogName: "preferences" } } as any);
         expect(spy).toHaveBeenCalled();
      }
      finally {
         spy.mockRestore();
      }
   });
});
