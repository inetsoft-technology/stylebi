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
 * DatabaseVPMBrowserComponent — Pass 2 (Risk / async / destructive operations)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnInit: paramMap subscription extracts databaseName;
 *                        refreshModels() fires HTTP GET → model + models populated
 *   Group 2 [Risk 3] — ngOnDestroy: routeParamSubscription unsubscribed and nulled
 *   Group 3 [Risk 3] — refreshModels: HTTP success → model.editable set + models sorted;
 *                        HTTP error → error swallowed silently, models stays empty
 *   Group 4 [Risk 3] — search: empty query → no HTTP; valid query → POST → models updated +
 *                        searchView=true; HTTP error → models=[] + danger notification
 *   Group 5 [Risk 3] — deleteModel: empty items → no dialog; confirm ok → POST → item removed
 *                        from models + callback + folderChange + success notification;
 *                        confirm ok → POST error → danger notification;
 *                        confirm cancel → no HTTP call
 *   Group 6 [Risk 3] — deleteSelected: delegates to deleteModel; selectedItems cleared on success
 *   Group 7 [Risk 2] — editable / deletable: both false when model is null; correct values
 *                        when model is set
 *   Group 8 [Risk 2] — currentSearchFolderLabel: "/" or null → rootLabel; non-root → path string
 *   Group 9 [Risk 2] — reSearch: restores searchQuery to currentSearchQuery and calls search
 *   Group 10 [Risk 2] — clearSearch: clears query/visible/view and triggers refreshModels
 *
 * Out of scope this pass:
 *   sortOptionsChanged, toggleSelectionState, editModel, addModel, renameModel,
 *   setShowDetailsItem, openTreeContextmenu, createActions — covered in Pass 1.
 */

import { waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";

import { server } from "@test-mocks/server";
import { ComponentTool } from "../../../../../../common/util/component-tool";
import { MOCK_VPM, renderComp } from "./database-vpm-browser.component.test-helpers";

// ── Global lifecycle ─────────────────────────────────────────────────────────

afterEach(() => {
   vi.restoreAllMocks();
});

// ── Group 1 — ngOnInit: route subscription → refreshModels [Risk 3] ──────────

describe("DatabaseVPMBrowserComponent — ngOnInit", () => {
   // 🔁 Regression-sensitive: if paramMap subscription is broken, databaseName stays undefined
   // and the browse URL becomes malformed, silently returning no VPMs.

   it("should extract databaseName from route paramMap", async () => {
      const { comp } = await renderComp({ databaseName: "ReportsDB" });

      expect(comp.databaseName).toBe("ReportsDB");
   });

   it("should populate model and models after the initial HTTP GET to browse", async () => {
      server.use(
         http.get("*/api/data/vpm/browse/*", () =>
            MswHttpResponse.json({
               editable: false,
               deletable: true,
               items: [{ ...MOCK_VPM, name: "InitialVPM" }],
               names: ["InitialVPM"],
               dateFormat: "yyyy-MM-dd",
            }),
         ),
      );
      const { comp } = await renderComp();

      await waitFor(() => expect(comp.models.length).toBeGreaterThan(0));
      expect(comp.models[0].name).toBe("InitialVPM");
      expect(comp.model).not.toBeNull();
      expect(comp.model!.editable).toBe(false);
   });
});

// ── Group 2 — ngOnDestroy: subscription cleanup [Risk 3] ─────────────────────

// routeParamSubscription is a private field on DatabaseVPMBrowserComponent; (comp as any) access
// is the only way to verify it is nulled after destroy without adding a public getter to production code.
describe("DatabaseVPMBrowserComponent — ngOnDestroy", () => {
   // 🔁 Regression-sensitive: if routeParamSubscription is not unsubscribed, it continues to
   // call refreshModels() on subsequent route changes for a destroyed component.

   it("should null routeParamSubscription after destroy", async () => {
      const { comp, fixture } = await renderComp();
      expect((comp as any).routeParamSubscription).not.toBeNull();

      fixture.destroy();

      expect((comp as any).routeParamSubscription).toBeNull();
   });
});

// ── Group 3 — refreshModels [Risk 3] ─────────────────────────────────────────

// refreshModels is a private method on DatabaseVPMBrowserComponent; (comp as any) access is
// required to call it directly so the test can verify HTTP behavior in isolation without
// triggering the full ngOnInit route subscription chain.
describe("DatabaseVPMBrowserComponent — refreshModels", () => {
   // 🔁 Regression-sensitive: browse uses databaseName in the URL path; if databaseName is
   // missing or not set before the call, the server receives a truncated URL and returns no items.

   it("should set model and models from a successful HTTP GET response", async () => {
      server.use(
         http.get("*/api/data/vpm/browse/*", () =>
            MswHttpResponse.json({
               editable: true,
               deletable: true,
               items: [{ ...MOCK_VPM, name: "AlphaVPM" }, { ...MOCK_VPM, name: "BetaVPM" }],
               names: ["AlphaVPM", "BetaVPM"],
               dateFormat: "yyyy-MM-dd",
            }),
         ),
      );
      const { comp } = await renderComp();

      await (comp as any).refreshModels();

      expect(comp.model?.editable).toBe(true);
      expect(comp.models.length).toBe(2);
      // Default sort is ASCENDING by name
      expect(comp.models[0].name).toBe("AlphaVPM");
   });

   it("should swallow HTTP errors silently and leave models empty", async () => {
      server.use(
         http.get("*/api/data/vpm/browse/*", () =>
            MswHttpResponse.json({ error: "Internal error" }, { status: 500 }),
         ),
      );
      const { comp } = await renderComp();
      comp.models = [];

      await (comp as any).refreshModels();

      expect(comp.models).toHaveLength(0);
   });
});

// ── Group 4 — search [Risk 3] ────────────────────────────────────────────────

describe("DatabaseVPMBrowserComponent — search", () => {
   // 🔁 Regression-sensitive: search with an empty query must never send a POST — the server
   // would treat it as a full-database query and return all VPMs.

   it("should not set searchView or POST when query is empty/null", async () => {
      const { comp } = await renderComp();
      const postSpy = vi.fn();
      server.use(http.post("*/api/data/vpm/search", () => { postSpy(); return MswHttpResponse.json({}); }));

      comp.search(null);

      // No wait — null/empty guard in search() returns synchronously without queuing HTTP.
      expect(postSpy).not.toHaveBeenCalled();
      expect(comp.searchView).toBe(false);
   });

   it("should POST and update models when a valid query is provided", async () => {
      // Suppress the initial browse so initial models stays empty and we can
      // unambiguously detect when the search POST result lands.
      server.use(
         http.get("*/api/data/vpm/browse/*", () =>
            MswHttpResponse.json({ editable: true, deletable: true, items: [], names: [], dateFormat: "yyyy-MM-dd" }),
         ),
         http.post("*/api/data/vpm/search", () =>
            MswHttpResponse.json({
               editable: true,
               deletable: true,
               items: [{ ...MOCK_VPM, name: "SearchResult" }],
               names: ["SearchResult"],
               dateFormat: "yyyy-MM-dd",
            }),
         ),
      );
      const { comp } = await renderComp();
      // Wait for initial (empty) browse to complete so its HTTP is settled
      await waitFor(() => expect(comp.model).toBeDefined());

      comp.search("SearchResult");

      await waitFor(() => expect(comp.models.some(m => m.name === "SearchResult")).toBe(true));
      expect(comp.searchView).toBe(true);
      expect(comp.models[0].name).toBe("SearchResult");
   });

   it("should clear models and show danger notification when the search POST fails", async () => {
      server.use(
         http.post("*/api/data/vpm/search", () =>
            MswHttpResponse.json({ error: "Search failed" }, { status: 500 }),
         ),
      );
      const { comp } = await renderComp();
      // Wait for the initial browse to complete so comp.models is populated;
      // the search error handler must then clear it back to [].
      await waitFor(() => expect(comp.models.length).toBeGreaterThan(0));

      comp.search("anything");

      await waitFor(() =>
         expect((comp.notifications as any).danger).toHaveBeenCalledWith(
            "_#(js:data.datasets.searchError)",
         ),
      );
      expect(comp.models).toHaveLength(0);
   });
});

// ── Group 5 — deleteModel [Risk 3] ───────────────────────────────────────────

describe("DatabaseVPMBrowserComponent — deleteModel", () => {
   // 🔁 Regression-sensitive: must remove the exact item from comp.models via indexOf —
   // if models is repopulated (refreshed) before splice, the deleted item can reappear.

   it("should return immediately without dialog when items list is empty", async () => {
      const { comp } = await renderComp();
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      try {
         comp.deleteModel([]);

         expect(confirmSpy).not.toHaveBeenCalled();
      } finally {
         confirmSpy.mockRestore();
      }
   });

   it("should POST remove, splice item from models, call callback, folderChange, and success notification", async () => {
      const { comp, folderChangeMock } = await renderComp();
      // Wait for initial browse to complete so comp.models[0] is the same
      // reference that's inside the array; passing a spread clone would break indexOf.
      await waitFor(() => expect(comp.models.length).toBeGreaterThan(0));
      const item = comp.models[0];
      const callbackSpy = vi.fn();
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      try {
         comp.deleteModel([item], callbackSpy);

         await waitFor(() =>
            expect((comp.notifications as any).success).toHaveBeenCalledWith(
               "_#(js:data.vpm.removeSuccess)",
            ),
         );
         expect(comp.models).toHaveLength(0);
         expect(callbackSpy).toHaveBeenCalledTimes(1);
         expect(folderChangeMock.emitFolderChange).toHaveBeenCalledTimes(1);
      } finally {
         confirmSpy.mockRestore();
      }
   });

   it("should show danger notification when the POST remove request fails", async () => {
      server.use(
         http.post("*/api/data/vpm/remove", () =>
            MswHttpResponse.json({ error: "Server error" }, { status: 500 }),
         ),
      );
      const { comp } = await renderComp();
      comp.models = [{ ...MOCK_VPM }];
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      try {
         comp.deleteModel([comp.models[0]]);

         await waitFor(() =>
            expect((comp.notifications as any).danger).toHaveBeenCalledWith(
               "_#(js:data.vpm.removeError)",
            ),
         );
         expect(comp.models).toHaveLength(1); // item NOT removed on error
      } finally {
         confirmSpy.mockRestore();
      }
   });

   it("should skip the POST entirely when the user cancels the confirm dialog", async () => {
      const { comp } = await renderComp();
      comp.models = [{ ...MOCK_VPM }];
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");
      const postSpy = vi.fn();
      server.use(http.post("*/api/data/vpm/remove", () => { postSpy(); return MswHttpResponse.json({}); }));
      try {
         vi.useFakeTimers();
         comp.deleteModel([comp.models[0]]);
         await vi.runAllTimersAsync();

         expect(postSpy).not.toHaveBeenCalled();
         expect(comp.models).toHaveLength(1);
      } finally {
         vi.useRealTimers();
         confirmSpy.mockRestore();
      }
   });
});

// ── Group 6 — deleteSelected [Risk 3] ────────────────────────────────────────

describe("DatabaseVPMBrowserComponent — deleteSelected", () => {
   // 🔁 Regression-sensitive: selectedItems must be cleared in the success callback so the
   // selection toolbar count resets; leaving it populated shows a stale badge count.

   it("should clear selectedItems after a successful delete", async () => {
      const { comp } = await renderComp();
      // Same reference fix as deleteModel test
      await waitFor(() => expect(comp.models.length).toBeGreaterThan(0));
      const item = comp.models[0];
      comp.selectedItems = [item];
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      try {
         comp.deleteSelected();

         await waitFor(() =>
            expect((comp.notifications as any).success).toHaveBeenCalledWith(
               "_#(js:data.vpm.removeSuccess)",
            ),
         );
         expect(comp.selectedItems).toHaveLength(0);
         expect(comp.models).toHaveLength(0);
      } finally {
         confirmSpy.mockRestore();
      }
   });
});

// ── Group 7 — editable / deletable getters [Risk 2] ──────────────────────────

describe("DatabaseVPMBrowserComponent — editable / deletable", () => {
   it("should return false for both when model is null", async () => {
      const { comp } = await renderComp();
      comp.model = null;

      expect(comp.editable).toBe(false);
      expect(comp.deletable).toBe(false);
   });

   it("should return model.editable and model.deletable when model is set", async () => {
      const { comp } = await renderComp();
      comp.model = { editable: true, deletable: false } as any;

      expect(comp.editable).toBe(true);
      expect(comp.deletable).toBe(false);
   });
});

// ── Group 8 — currentSearchFolderLabel [Risk 2] ───────────────────────────────

describe("DatabaseVPMBrowserComponent — currentSearchFolderLabel", () => {
   it("should return the rootLabel when currentFolderPathString is '/'", async () => {
      const { comp } = await renderComp();
      comp.currentFolderPathString = "/";

      expect(comp.currentSearchFolderLabel).toBe(comp.rootLabel);
   });

   it("should return the folder path when currentFolderPathString is a non-root path", async () => {
      const { comp } = await renderComp();
      comp.currentFolderPathString = "/Finance";

      expect(comp.currentSearchFolderLabel).toBe("/Finance");
   });
});

// ── Group 9 — reSearch [Risk 2] ──────────────────────────────────────────────

describe("DatabaseVPMBrowserComponent — reSearch", () => {
   it("should call search() with the stored currentSearchQuery", async () => {
      const { comp } = await renderComp();
      comp.currentSearchQuery = "budgets";
      const searchSpy = vi.spyOn(comp, "search").mockImplementation(() => {});
      try {
         comp.reSearch();

         expect(searchSpy).toHaveBeenCalledWith("budgets");
         expect(comp.searchQuery).toBe("budgets");
      } finally {
         searchSpy.mockRestore();
      }
   });
});

// ── Group 10 — clearSearch [Risk 2] ──────────────────────────────────────────

// refreshModels is a private method on DatabaseVPMBrowserComponent; (comp as any) spy intercepts it
// so clearSearch can be verified in isolation without triggering a real HTTP browse request.
describe("DatabaseVPMBrowserComponent — clearSearch", () => {
   it("should reset search state and trigger refreshModels", async () => {
      const { comp } = await renderComp();
      comp.searchQuery = "something";
      comp.searchVisible = true;
      comp.searchView = true;
      const refreshSpy = vi.spyOn(comp as any, "refreshModels").mockResolvedValue(undefined);
      try {
         comp.clearSearch();

         expect(comp.searchQuery).toBeNull();
         expect(comp.searchVisible).toBe(false);
         expect(comp.searchView).toBe(false);
         expect(refreshSpy).toHaveBeenCalledTimes(1);
      } finally {
         refreshSpy.mockRestore();
      }
   });
});
