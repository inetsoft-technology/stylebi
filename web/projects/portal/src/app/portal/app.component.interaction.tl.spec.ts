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
 * PortalAppComponent — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 1]  — openComposerEnabled: boolean getter true / false
 *   Group 2  [Risk 2]  — ngOnInit HTTP model load: model / aiAssistant / logoutUrl / currentUrl
 *   Group 3  [Risk 2]  — showGettingStarted: URL guard, already-shown guard, force flag
 *   Group 4  [Risk 2]  — refreshCreationPermissions: five model-field updates and null guard
 *   Group 5  [Risk 1]  — logOut, showDocument, showListings: simple delegation contracts
 *   Group 6  [Risk 1]  — profiling: HTTP URL construction and model toggle
 *   Group 7  [Risk 1]  — getTab + tooltip helpers: found / not-found
 *   Group 8  [Risk 2]  — isTabSelected: null guards, CUSTOM branch, regular tab match
 *   Group 9  [Risk 3]  — openComposer: vsWizard/wsWizard URL building, composer-already-open path
 *   Group 10 [Risk 2]  — launchComposer: disabled guard, open window, already-open dialog
 *
 * Mocking strategy:
 *   HttpClient is mocked with vi.fn() (not MSW) to avoid NG0205 (Injector already destroyed)
 *   that fires when constructor-level HTTP calls in HistoryBarService / LicenseInfoService race
 *   the ATL fixture teardown. The existing app.component.tl.spec.ts uses the same strategy.
 *
 * Out of scope this pass (app.component.risk.tl.spec.ts):
 *   updateAccessibility, setTabOrder, checkDefaultTab, showPreferences, handleMessageEvent
 *
 * Out of scope this pass (existing app.component.tl.spec.ts):
 *   ngOnDestroy subscription cleanup, window 'message' listener lifecycle memory-leak tests
 *
 * Shared helpers: see app.component.test-helpers.ts
 */

import { of } from "rxjs";

import { PortalTabs } from "./portal-tab";
import {
   DASHBOARD_TAB,
   DATA_TAB,
   REPORT_TAB,
   SCHEDULE_TAB,
   beforeEachCleanup,
   makePortalModel,
   renderComponent,
} from "./app.component.test-helpers";

// ---------------------------------------------------------------------------
// Global cleanup — must run before every test in this file
// ---------------------------------------------------------------------------

beforeEach(beforeEachCleanup);
afterEach(() => vi.restoreAllMocks());

// ===========================================================================
// Group 1 — openComposerEnabled [Risk 1]
// ===========================================================================

describe("PortalAppComponent — Group 1: openComposerEnabled getter", () => {

   it("should return true when model.composerEnabled is true", async () => {
      const { comp } = await renderComponent();
      comp.model = makePortalModel({ composerEnabled: true });
      expect(comp.openComposerEnabled).toBe(true);
   });

   it("should return false when model.composerEnabled is false", async () => {
      const { comp } = await renderComponent();
      comp.model = makePortalModel({ composerEnabled: false });
      expect(comp.openComposerEnabled).toBe(false);
   });
});

// ===========================================================================
// Group 2 — ngOnInit HTTP model load + constructor subscription [Risk 2]
// ===========================================================================

describe("PortalAppComponent — Group 2: ngOnInit HTTP model load", () => {

   it("should assign the HTTP response to comp.model", async () => {
      const { comp } = await renderComponent();
      expect(comp.model).toBeDefined();
      expect(comp.model.title).toBe("Test Portal");
   });

   it("should propagate aiAssistantVisible from model to aiAssistantService", async () => {
      const { comp, aiAssistantSvc } = await renderComponent();
      // Default model has aiAssistantVisible: false; ngOnInit assigns it to the service.
      expect(aiAssistantSvc.aiAssistantVisible).toBe(comp.model.aiAssistantVisible);
   });

   it("should call logoutService.setLogoutUrl with the model logout URL", async () => {
      const { logoutSvc } = await renderComponent();
      expect(logoutSvc.setLogoutUrl).toHaveBeenCalledWith("http://example.com/logout");
   });

   // 🔁 Regression-sensitive: constructor subscription wires currentUrl; if unset,
   // isTabSelected / showGettingStarted fail silently.
   it("should set comp.currentUrl from CurrentRouteService before ngOnInit", async () => {
      const { comp } = await renderComponent({ currentUrl: "/portal/tab/report" });
      expect(comp.currentUrl).toBe("/portal/tab/report");
   });
});

// ===========================================================================
// Group 3 — showGettingStarted [Risk 2]
// ===========================================================================

describe("PortalAppComponent — Group 3: showGettingStarted", () => {

   it("should NOT call gettingStartedService.start when URL does not start with /portal/tab/report",
      async () => {
         const { gettingStartedSvc } = await renderComponent({ currentUrl: "/portal" });
         expect(gettingStartedSvc.start).not.toHaveBeenCalled();
      }
   );

   // 🔁 Regression-sensitive: "false" string from API means do NOT show — any other truthy value means show.
   it("should NOT call gettingStartedService.start when API returns the string 'false'", async () => {
      const { gettingStartedSvc } = await renderComponent({
         currentUrl: "/portal/tab/report",
         preInitHttpResponses: ["false"],  // 1st http.get: getting-started → "false"
      });
      expect(gettingStartedSvc.start).not.toHaveBeenCalled();
   });

   it("should call gettingStartedService.start(false) when API returns a non-force truthy value",
      async () => {
         const { gettingStartedSvc } = await renderComponent({
            currentUrl: "/portal/tab/report",
            preInitHttpResponses: ["true"],  // truthy but not "force"
         });
         expect(gettingStartedSvc.start).toHaveBeenCalledWith(false);
      }
   );

   it("should call gettingStartedService.start(true) when API returns 'force'", async () => {
      const { gettingStartedSvc } = await renderComponent({
         currentUrl: "/portal/tab/report",
         preInitHttpResponses: ["force"],
      });
      expect(gettingStartedSvc.start).toHaveBeenCalledWith(true);
   });

   // Risk Point: isGettingStartedShown flag prevents repeated HTTP calls on subsequent navigation
   it("should NOT fire HTTP again after getting started was already shown", async () => {
      const { comp, httpMock } = await renderComponent({
         currentUrl: "/portal/tab/report",
         preInitHttpResponses: ["true"],  // first call → shown → isGettingStartedShown = true
      });
      httpMock.get.mockClear();
      comp.showGettingStarted();  // guard: isGettingStartedShown is true → early return
      expect(httpMock.get).not.toHaveBeenCalled();
   });
});

// ===========================================================================
// Group 4 — refreshCreationPermissions [Risk 2]
// ===========================================================================

describe("PortalAppComponent — Group 4: refreshCreationPermissions", () => {

   // 🔁 Regression-sensitive: all five fields must be updated atomically; missing one
   // leaves the UI in an inconsistent permission state.
   it("should update all five creation-permission fields on the model", async () => {
      const { comp, httpMock } = await renderComponent();
      const permissions = {
         composerEnabled:      true,
         dashboardEnabled:     true,
         newDatasourceEnabled: true,
         newWorksheetEnabled:  true,
         newViewsheetEnabled:  true,
      };
      httpMock.get.mockReturnValueOnce(of(permissions));
      comp.refreshCreationPermissions();

      expect(comp.model.composerEnabled).toBe(true);
      expect(comp.model.dashboardEnabled).toBe(true);
      expect(comp.model.newDatasourceEnabled).toBe(true);
      expect(comp.model.newWorksheetEnabled).toBe(true);
      expect(comp.model.newViewsheetEnabled).toBe(true);
   });

   it("should NOT update model fields when HTTP response is null", async () => {
      const { comp, httpMock } = await renderComponent();
      const before = {
         composerEnabled:      comp.model.composerEnabled,
         dashboardEnabled:     comp.model.dashboardEnabled,
         newDatasourceEnabled: comp.model.newDatasourceEnabled,
         newWorksheetEnabled:  comp.model.newWorksheetEnabled,
         newViewsheetEnabled:  comp.model.newViewsheetEnabled,
      };
      httpMock.get.mockReturnValueOnce(of(null));
      comp.refreshCreationPermissions();

      expect(comp.model.composerEnabled).toBe(before.composerEnabled);
      expect(comp.model.dashboardEnabled).toBe(before.dashboardEnabled);
   });

   it("should send HTTP GET to the correct refresh-creation-permissions endpoint", async () => {
      const { comp, httpMock } = await renderComponent();
      httpMock.get.mockClear();
      httpMock.get.mockReturnValueOnce(of({}));
      comp.refreshCreationPermissions();
      expect(httpMock.get).toHaveBeenCalledWith(
         expect.stringContaining("refresh-creation-permissions"),
      );
   });
});

// ===========================================================================
// Group 5 — logOut, showDocument, showListings [Risk 1]
// ===========================================================================

describe("PortalAppComponent — Group 5: logOut / showDocument / showListings", () => {

   it("logOut should delegate to logoutService.logout()", async () => {
      const { comp, logoutSvc } = await renderComponent();
      comp.logOut();
      expect(logoutSvc.logout).toHaveBeenCalledTimes(1);
   });

   it("showDocument should open helpURL in a new window when helpURL is set", async () => {
      const { comp } = await renderComponent();
      comp.model = makePortalModel({ helpURL: "http://help.example.com" });
      const openSpy = vi.spyOn(window, "open").mockReturnValue(null);
      try {
         comp.showDocument();
         expect(openSpy).toHaveBeenCalledWith(
            expect.stringContaining("http://help.example.com"),
         );
      }
      finally {
         openSpy.mockRestore();
      }
   });

   it("showDocument should NOT open a window when helpURL is null", async () => {
      const { comp } = await renderComponent();
      comp.model = makePortalModel({ helpURL: null });
      const openSpy = vi.spyOn(window, "open").mockReturnValue(null);
      try {
         comp.showDocument();
         expect(openSpy).not.toHaveBeenCalled();
      }
      finally {
         openSpy.mockRestore();
      }
   });

   // 🔁 Regression-sensitive: navigate path and query-params shape are consumed by the
   // router guard; changing either breaks the data listing route.
   it("showListings should navigate to datasources/listing with path '/' and scope '0'", async () => {
      const { comp, routerMock } = await renderComponent();
      comp.showListings();
      expect(routerMock.navigate).toHaveBeenCalledWith(
         ["portal/tab/data/datasources/listing", ""],
         { queryParams: { path: "/", scope: "0" } },
      );
   });
});

// ===========================================================================
// Group 6 — profiling [Risk 1]
// ===========================================================================

describe("PortalAppComponent — Group 6: profiling", () => {

   // 🔁 Regression-sensitive: URL encodes the NEGATED profiling state — toggling the
   // boolean in the wrong direction sends the wrong value to the server.
   it("should GET set-profiling/true when profiling was false, then set model.profiling to true",
      async () => {
         const { comp, httpMock } = await renderComponent();
         comp.model = makePortalModel({ profiling: false });
         httpMock.get.mockClear();
         comp.profiling();
         expect(httpMock.get).toHaveBeenCalledWith(
            expect.stringContaining("set-profiling/true"),
         );
         expect(comp.model.profiling).toBe(true);
      }
   );

   it("should GET set-profiling/false when profiling was true, then set model.profiling to false",
      async () => {
         const { comp, httpMock } = await renderComponent();
         comp.model = makePortalModel({ profiling: true });
         httpMock.get.mockClear();
         comp.profiling();
         expect(httpMock.get).toHaveBeenCalledWith(
            expect.stringContaining("set-profiling/false"),
         );
         expect(comp.model.profiling).toBe(false);
      }
   );
});

// ===========================================================================
// Group 7 — getTab + tooltip helpers [Risk 1]
// ===========================================================================

describe("PortalAppComponent — Group 7: getTab and tooltip helpers", () => {

   it("getTab should return the matching PortalTab when the name is present", async () => {
      const { comp } = await renderComponent();
      comp.portalTabs = [DASHBOARD_TAB, REPORT_TAB];
      const result = comp.getTab(PortalTabs.DASHBOARD);
      expect(result).toEqual(DASHBOARD_TAB);
   });

   it("getTab should return null when the tab name is not in portalTabs", async () => {
      const { comp } = await renderComponent();
      comp.portalTabs = [REPORT_TAB];
      expect(comp.getTab(PortalTabs.DATA)).toBeNull();
   });

   it("getDashboardTabTooltip should return '_#(js:Dashboard)' when DASHBOARD tab exists", async () => {
      const { comp } = await renderComponent();
      comp.portalTabs = [DASHBOARD_TAB];
      expect(comp.getDashboardTabTooltip()).toBe("_#(js:Dashboard)");
   });

   it("getDashboardTabTooltip should return empty string when DASHBOARD tab is absent", async () => {
      const { comp } = await renderComponent();
      comp.portalTabs = [REPORT_TAB];
      expect(comp.getDashboardTabTooltip()).toBe("");
   });

   it("getDataTabTooltip should return '_#(js:Data)' when DATA tab exists", async () => {
      const { comp } = await renderComponent();
      comp.portalTabs = [DATA_TAB];
      expect(comp.getDataTabTooltip()).toBe("_#(js:Data)");
   });

   it("getDataTabTooltip should return empty string when DATA tab is absent", async () => {
      const { comp } = await renderComponent();
      comp.portalTabs = [];
      expect(comp.getDataTabTooltip()).toBe("");
   });

   it("getScheduleTabTooltip should return '_#(js:Schedule)' when SCHEDULE tab exists", async () => {
      const { comp } = await renderComponent();
      comp.portalTabs = [SCHEDULE_TAB];
      expect(comp.getScheduleTabTooltip()).toBe("_#(js:Schedule)");
   });

   it("getScheduleTabTooltip should return empty string when SCHEDULE tab is absent", async () => {
      const { comp } = await renderComponent();
      comp.portalTabs = [];
      expect(comp.getScheduleTabTooltip()).toBe("");
   });
});

// ===========================================================================
// Group 8 — isTabSelected [Risk 2]
// ===========================================================================

describe("PortalAppComponent — Group 8: isTabSelected", () => {

   it("should return false when portalTabs is null or undefined", async () => {
      const { comp } = await renderComponent();
      comp.portalTabs = null;
      comp.currentUrl = "/portal/tab/dashboard";
      expect(comp.isTabSelected(PortalTabs.DASHBOARD)).toBe(false);
   });

   it("should return false when currentUrl is null or empty", async () => {
      const { comp } = await renderComponent();
      comp.portalTabs = [DASHBOARD_TAB];
      comp.currentUrl = null;
      expect(comp.isTabSelected(PortalTabs.DASHBOARD)).toBe(false);
   });

   // 🔁 Regression-sensitive: CUSTOM tab uses a different match branch
   // (indexOf on URL) rather than iterating portalTabs by tab.uri.
   it("should return true for CUSTOM when currentUrl contains 'custom'", async () => {
      const { comp } = await renderComponent();
      comp.portalTabs = [];  // CUSTOM branch does not iterate tabs
      comp.currentUrl = "/portal/tab/custom/MyCustomTab";
      expect(comp.isTabSelected(PortalTabs.CUSTOM)).toBe(true);
   });

   it("should return true when a regular tab's uri appears in currentUrl", async () => {
      const { comp } = await renderComponent();
      comp.portalTabs = [DASHBOARD_TAB];
      comp.currentUrl = "/portal/tab/dashboard/someDashboard";
      expect(comp.isTabSelected(PortalTabs.DASHBOARD)).toBe(true);
   });

   it("should return false when no regular tab uri matches the currentUrl", async () => {
      const { comp } = await renderComponent();
      comp.portalTabs = [DASHBOARD_TAB];
      comp.currentUrl = "/portal/tab/report/someReport";
      expect(comp.isTabSelected(PortalTabs.DASHBOARD)).toBe(false);
   });
});

// ===========================================================================
// Group 9 — openComposer [Risk 3]
// ===========================================================================

describe("PortalAppComponent — Group 9: openComposer", () => {

   // 🔁 Regression-sensitive: vsWizard=true when wizard status is null (first time).
   it("should open composer with vsWizard=true when vs=true and wizard status is null", async () => {
      const { comp, httpMock } = await renderComponent({ composerOpen: false });
      // The default httpMock returns makePortalModel() which has no viewsheetWizardStatus
      // (undefined == null → vsWizard = true).
      httpMock.get.mockReturnValueOnce(of({ viewsheetWizardStatus: null, worksheetWizardStatus: null }));
      const openSpy = vi.spyOn(window, "open").mockReturnValue(null);
      try {
         comp.openComposer(true);
         expect(openSpy).toHaveBeenCalledWith("composer?vsWizard=true", "_blank");
      }
      finally {
         openSpy.mockRestore();
      }
   });

   it("should open composer with vsWizard=false when viewsheetWizardStatus is 'hide'", async () => {
      const { comp, httpMock } = await renderComponent({ composerOpen: false });
      httpMock.get.mockReturnValueOnce(of({ viewsheetWizardStatus: "hide", worksheetWizardStatus: "hide" }));
      const openSpy = vi.spyOn(window, "open").mockReturnValue(null);
      try {
         comp.openComposer(true);
         expect(openSpy).toHaveBeenCalledWith("composer?vsWizard=false", "_blank");
      }
      finally {
         openSpy.mockRestore();
      }
   });

   it("should open composer with wsWizard=true when vs=false and wizard status is null", async () => {
      const { comp, httpMock } = await renderComponent({ composerOpen: false });
      httpMock.get.mockReturnValueOnce(of({ viewsheetWizardStatus: null, worksheetWizardStatus: null }));
      const openSpy = vi.spyOn(window, "open").mockReturnValue(null);
      try {
         comp.openComposer(false);
         expect(openSpy).toHaveBeenCalledWith("composer?wsWizard=true", "_blank");
      }
      finally {
         openSpy.mockRestore();
      }
   });

   // 🔁 Regression-sensitive: when composer is already open, a BroadcastChannel message
   // must be sent AND a confirmation dialog must be shown — missing either breaks the UX.
   // Use vi.spyOn on the prototype so the spy works regardless of stub lifecycle.
   it("should postMessage('vsWizard') and show dialog when composer is already open and vs=true",
      async () => {
         const postMsgSpy = vi.spyOn(BroadcastChannel.prototype, "postMessage")
            .mockImplementation(() => {});
         try {
            const { comp, modalSvc } = await renderComponent({ composerOpen: true });
            comp.openComposer(true);
            expect(postMsgSpy).toHaveBeenCalledWith("vsWizard");
            expect(modalSvc.open).toHaveBeenCalled();
         }
         finally {
            postMsgSpy.mockRestore();
         }
      }
   );

   it("should postMessage('wsWizard') when composer is already open and vs=false", async () => {
      const postMsgSpy = vi.spyOn(BroadcastChannel.prototype, "postMessage")
         .mockImplementation(() => {});
      try {
         const { comp } = await renderComponent({ composerOpen: true });
         comp.openComposer(false);
         expect(postMsgSpy).toHaveBeenCalledWith("wsWizard");
      }
      finally {
         postMsgSpy.mockRestore();
      }
   });
});

// ===========================================================================
// Group 10 — launchComposer [Risk 2]
// ===========================================================================

describe("PortalAppComponent — Group 10: launchComposer", () => {

   // 🔁 Regression-sensitive: composerEnabled guard must block window.open —
   // bypassing it opens a blank composer for users without permission.
   it("should NOT open a new window when composerEnabled is false", async () => {
      const { comp } = await renderComponent({ composerOpen: false });
      comp.model = makePortalModel({ composerEnabled: false });
      const openSpy = vi.spyOn(window, "open").mockReturnValue(null);
      try {
         comp.launchComposer();
         expect(openSpy).not.toHaveBeenCalled();
      }
      finally {
         openSpy.mockRestore();
      }
   });

   it("should open 'composer' in a new window when composerEnabled and composer is not open",
      async () => {
         const { comp } = await renderComponent({ composerOpen: false });
         comp.model = makePortalModel({ composerEnabled: true });
         const openSpy = vi.spyOn(window, "open").mockReturnValue(null);
         try {
            comp.launchComposer();
            expect(openSpy).toHaveBeenCalledWith("composer", "_blank");
         }
         finally {
            openSpy.mockRestore();
         }
      }
   );

   it("should show confirmation dialog when composerEnabled and composer is already open",
      async () => {
         const { comp, modalSvc } = await renderComponent({ composerOpen: true });
         comp.model = makePortalModel({ composerEnabled: true });
         comp.launchComposer();
         expect(modalSvc.open).toHaveBeenCalled();
      }
   );
});
