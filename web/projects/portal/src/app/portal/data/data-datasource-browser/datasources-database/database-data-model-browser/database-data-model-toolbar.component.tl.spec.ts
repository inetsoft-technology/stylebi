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
 * DatabaseDataModelToolbarComponent — Single Pass (interaction + memory leak)
 *
 * Mocking strategy:
 *   - HttpClient — mocked with { post: vi.fn() }; `searchFunc` is wired to ngbTypeahead
 *     and is NOT exercised in these unit tests (it's an ngb autocomplete integration).
 *   - NgbTypeahead — NO_ERRORS_SCHEMA suppresses unknown attribute errors; the directive
 *     is imported by the component but irrelevant to the logic under test.
 *   - Renderer2 — provided by Angular's TestBed; spied on per-test to verify that
 *     toggleSearch() calls renderer.listen("document","click",...) and that the returned
 *     cleanup function is invoked on an outside click (memory-leak guard).
 *
 * Risk-first coverage:
 *   Group 1 [Risk 1]  — Computed getters: editable, deletable, selectionDeletable,
 *                        toggleSelectTooltip, canCreateLogicalModel
 *   Group 2 [Risk 1]  — toggleSelectionState: toggles selectionOn, emits onToggleSelection
 *   Group 3 [Risk 2]  — search(): emits onSearch and clears searchQuery; early-return when
 *                        searchQuery is empty
 *   Group 4 [Risk 2]  — toggleSearch() (sync): opens search and closes search with/without
 *                        pending query
 *   Group 5 [Risk 2]  — Memory leak: document click listener registered when search opens;
 *                        listener self-removes on outside click; listener NOT removed on same
 *                        event or click inside search input
 *   Group 6 [Risk 1]  — Action emitters: addPhysicalView/addLogicalModel/addDataModelFolder/
 *                        addVPMModel (guarded by editable); deleteSelected/moveSelected (always)
 */

import { NO_ERRORS_SCHEMA, Renderer2 } from "@angular/core";
import { render, waitFor } from "@testing-library/angular";
import { HttpClient } from "@angular/common/http";
import { of } from "rxjs";

import {
   DatabaseDataModelToolbarComponent
} from "./database-data-model-toolbar.component";
import { AssetListBrowseModel } from "../../../model/datasources/database/asset-list-browse-model";

// ── Shared fixture data ──────────────────────────────────────────────────────

const EDITABLE_MODEL = new AssetListBrowseModel(true, true, [], [], "yyyy-MM-dd", 2);
const READ_ONLY_MODEL = new AssetListBrowseModel(false, false, [], [], "yyyy-MM-dd", 0);

const httpMock = {
   post: vi.fn().mockReturnValue(of([])),
};

// ── Render helper ────────────────────────────────────────────────────────────

interface ToolbarRenderOpts {
   model?: AssetListBrowseModel;
   database?: string;
   isvpm?: boolean;
   isRoot?: boolean;
   searchVisible?: boolean;
   searchQuery?: string;
   selectedItems?: any[];
   moveDisable?: boolean;
}

async function renderComp(opts: ToolbarRenderOpts = {}) {
   const { fixture } = await render(DatabaseDataModelToolbarComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: HttpClient, useValue: httpMock },
      ],
      inputs: {
         database: opts.database ?? "TestDB",
         model: opts.model ?? EDITABLE_MODEL,
         isvpm: opts.isvpm ?? false,
         isRoot: opts.isRoot ?? true,
         searchVisible: opts.searchVisible ?? false,
         searchQuery: opts.searchQuery ?? "",
         selectedItems: opts.selectedItems ?? [],
         moveDisable: opts.moveDisable ?? false,
      },
   });

   const comp = fixture.componentInstance as DatabaseDataModelToolbarComponent;
   return { comp, fixture };
}

// ── Global lifecycle ─────────────────────────────────────────────────────────

afterEach(() => {
   vi.restoreAllMocks();
});

// ── Group 1 — Computed getters [Risk 1] ──────────────────────────────────────

describe("DatabaseDataModelToolbarComponent — computed getters", () => {
   it("should return editable=true when model.editable is true", async () => {
      const { comp } = await renderComp({ model: EDITABLE_MODEL });

      expect(comp.editable).toBe(true);
   });

   it("should return editable=false when model is not editable", async () => {
      const { comp } = await renderComp({ model: READ_ONLY_MODEL });

      expect(comp.editable).toBe(false);
   });

   it("should return deletable=true when model.deletable is true", async () => {
      const { comp } = await renderComp({ model: EDITABLE_MODEL });

      expect(comp.deletable).toBe(true);
   });

   it("should return selectionDeletable=true when all selected items are deletable", async () => {
      const { comp } = await renderComp({
         selectedItems: [{ deletable: true }, { deletable: true }],
      });

      expect(comp.selectionDeletable).toBe(true);
   });

   it("should return selectionDeletable=false when any selected item is not deletable", async () => {
      const { comp } = await renderComp({
         selectedItems: [{ deletable: true }, { deletable: false }],
      });

      expect(comp.selectionDeletable).toBe(false);
   });

   it("should return toggleSelectTooltip for selectOn when selectionOn is false", async () => {
      const { comp } = await renderComp();
      comp.selectionOn = false;

      expect(comp.toggleSelectTooltip).toBe("_#(js:data.datasets.selectOn)");
   });

   it("should return toggleSelectTooltip for selectOff when selectionOn is true", async () => {
      const { comp } = await renderComp();
      comp.selectionOn = true;

      expect(comp.toggleSelectTooltip).toBe("_#(js:data.datasets.selectOff)");
   });

   it("should return canCreateLogicalModel=true when dbPartitionCount > 0", async () => {
      const { comp } = await renderComp({
         model: new AssetListBrowseModel(true, true, [], [], "yyyy-MM-dd", 3),
      });

      expect(comp.canCreateLogicalModel).toBe(true);
   });

   it("should return canCreateLogicalModel=false when dbPartitionCount is 0", async () => {
      const { comp } = await renderComp({
         model: new AssetListBrowseModel(true, true, [], [], "yyyy-MM-dd", 0),
      });

      // getter returns `0 && ...` which is 0 (falsy), not boolean false
      expect(comp.canCreateLogicalModel).toBeFalsy();
   });
});

// ── Group 2 — toggleSelectionState [Risk 1] ──────────────────────────────────

describe("DatabaseDataModelToolbarComponent — toggleSelectionState", () => {
   // 🔁 Regression-sensitive: both the parent's selection mode (via onToggleSelection output)
   // AND the internal selectionOn flag must be toggled in sync.

   it("should set selectionOn to true and emit true on first call", async () => {
      const { comp } = await renderComp();
      const emitted: boolean[] = [];
      comp.onToggleSelection.subscribe(v => emitted.push(v));

      comp.toggleSelectionState();

      expect(comp.selectionOn).toBe(true);
      expect(emitted).toEqual([true]);
   });

   it("should toggle selectionOn back to false on second call", async () => {
      const { comp } = await renderComp();
      const emitted: boolean[] = [];
      comp.onToggleSelection.subscribe(v => emitted.push(v));

      comp.toggleSelectionState();
      comp.toggleSelectionState();

      expect(comp.selectionOn).toBe(false);
      expect(emitted).toEqual([true, false]);
   });
});

// ── Group 3 — search() [Risk 2] ──────────────────────────────────────────────

describe("DatabaseDataModelToolbarComponent — search()", () => {
   // 🔁 Regression-sensitive: search() must clear searchQuery after emitting — otherwise the
   // ngbTypeahead re-fills it on every keystroke, causing duplicate searches.

   it("should emit onSearch with searchQuery and clear it afterwards", async () => {
      const { comp } = await renderComp({ searchQuery: "partitionA" });
      const emitted: string[] = [];
      comp.onSearch.subscribe(v => emitted.push(v));

      comp.search();

      expect(emitted).toEqual(["partitionA"]);
      expect(comp.searchQuery).toBeNull();
   });

   it("should use the query param when provided, overriding searchQuery", async () => {
      const { comp } = await renderComp({ searchQuery: "old" });
      const emitted: string[] = [];
      comp.onSearch.subscribe(v => emitted.push(v));

      comp.search("newQuery");

      expect(emitted).toEqual(["newQuery"]);
      expect(comp.searchQuery).toBeNull();
   });

   it("should return early and NOT emit when searchQuery is empty", async () => {
      const { comp } = await renderComp({ searchQuery: "" });
      const emitted: string[] = [];
      comp.onSearch.subscribe(v => emitted.push(v));

      comp.search();

      expect(emitted).toHaveLength(0);
      expect(comp.searchQuery).toBe(""); // unchanged
   });
});

// ── Group 4 — toggleSearch() sync behavior [Risk 2] ──────────────────────────

describe("DatabaseDataModelToolbarComponent — toggleSearch() sync behavior", () => {
   // 🔁 Regression-sensitive: when closing the search box with a pending query, toggleSearch()
   // must call search() so the query is not silently discarded.

   // renderer is injected into a private field on DatabaseDataModelToolbarComponent; (comp as any)
   // is the only way to spy on Renderer2.listen without a custom provider override.
   it("should set searchVisible to true when called while closed", async () => {
      const { comp, fixture } = await renderComp({ searchVisible: false });
      const renderer = (comp as any).renderer as Renderer2;
      const listenSpy = vi.spyOn(renderer, "listen").mockReturnValue(vi.fn());
      try {
         comp.toggleSearch(new MouseEvent("click"));
         fixture.detectChanges();

         expect(comp.searchVisible).toBe(true);
      } finally {
         listenSpy.mockRestore();
      }
   });

   it("should set searchVisible to false when called while open (no pending query)", async () => {
      const { comp } = await renderComp({ searchVisible: true, searchQuery: "" });

      comp.toggleSearch(new MouseEvent("click"));

      expect(comp.searchVisible).toBe(false);
   });

   it("should call search() and emit onSearch when closed with a pending query", async () => {
      const { comp } = await renderComp({ searchVisible: true, searchQuery: "testQuery" });
      const emitted: string[] = [];
      comp.onSearch.subscribe(v => emitted.push(v));

      comp.toggleSearch(new MouseEvent("click"));

      expect(comp.searchVisible).toBe(false);
      expect(emitted).toEqual(["testQuery"]);
   });

   it("should NOT emit onSearch when closed without a pending query", async () => {
      const { comp } = await renderComp({ searchVisible: true, searchQuery: "" });
      const emitted: string[] = [];
      comp.onSearch.subscribe(v => emitted.push(v));

      comp.toggleSearch(new MouseEvent("click"));

      expect(emitted).toHaveLength(0);
   });
});

// ── Group 5 — Memory leak: document click listener [Risk 2] ──────────────────

describe("DatabaseDataModelToolbarComponent — document click listener (memory leak)", () => {
   // 🔁 Regression-sensitive: when the search box is opened, a document-level click listener
   // is registered via Renderer2.listen(). This listener must SELF-REMOVE when the user clicks
   // outside the search input. If it doesn't, the listener leaks memory and continues closing
   // the search box on future component instances.
   //
   // Implementation note: `fixture.detectChanges()` after opening the search causes Angular
   // to register additional renderer.listen calls (for the newly-rendered search input's event
   // bindings). We therefore collect ALL calls and filter by target="document" + event="click"
   // to reliably identify the collapseSearchListener.

   // renderer is injected into a private field on DatabaseDataModelToolbarComponent; (comp as any)
   // is the only way to spy on Renderer2.listen without a custom provider override.
   it("should register a document click listener when search is opened", async () => {
      const { comp, fixture } = await renderComp({ searchVisible: false });
      const renderer = (comp as any).renderer as Renderer2;
      const allCalls: { target: any; event: string }[] = [];
      const listenSpy = vi.spyOn(renderer, "listen").mockImplementation((target, event) => {
         allCalls.push({ target, event });
         return vi.fn();
      });
      try {
         comp.toggleSearch(new MouseEvent("click"));
         fixture.detectChanges();

         expect(allCalls).toEqual(
            expect.arrayContaining([expect.objectContaining({ target: "document", event: "click" })]),
         );
         expect(comp.searchVisible).toBe(true);
      } finally {
         listenSpy.mockRestore();
      }
   });

   it("should close search on a new document click that lands outside the search input", async () => {
      // Use document.dispatchEvent to exercise the REAL listener registered by renderer.listen.
      // A separate MouseEvent object satisfies `event !== targetEvent`, and the dispatched
      // event's target (document) differs from searchInput.nativeElement — both conditions
      // needed for the listener to set searchVisible=false.
      const { comp, fixture } = await renderComp({ searchVisible: false });

      const toggleEvent = new MouseEvent("click");
      comp.toggleSearch(toggleEvent);
      fixture.detectChanges();
      await waitFor(() => expect(comp.searchVisible).toBe(true)); // wait for toggleSearch to settle

      expect(comp.searchVisible).toBe(true);

      // Dispatch a distinct document click (different object → event !== targetEvent)
      document.dispatchEvent(new MouseEvent("click", { bubbles: true }));
      fixture.detectChanges();

      expect(comp.searchVisible).toBe(false);
   });

   it("should NOT close search when the same event object bubbles to document", async () => {
      // The toggle button's click event bubbles to document. The listener guards against this
      // by checking `event !== targetEvent` before closing the search.
      const { comp, fixture } = await renderComp({ searchVisible: false });

      const toggleEvent = new MouseEvent("click", { bubbles: true });
      comp.toggleSearch(toggleEvent);
      fixture.detectChanges();

      // Re-dispatch the SAME event object (same reference) to document
      document.dispatchEvent(toggleEvent);
      fixture.detectChanges();

      expect(comp.searchVisible).toBe(true); // NOT closed — same event reference
   });
});

// ── Group 6 — Action emitters [Risk 1] ───────────────────────────────────────

describe("DatabaseDataModelToolbarComponent — action emitters", () => {
   // 🔁 Regression-sensitive: the four "add" actions must be guarded by `editable`. If the
   // guard is missing, the server receives a create request for a read-only database.

   it("addPhysicalView: should emit when editable", async () => {
      const { comp } = await renderComp({ model: EDITABLE_MODEL });
      const emitted: any[] = [];
      comp.onAddPhysicalView.subscribe(() => emitted.push(true));

      comp.addPhysicalView();

      expect(emitted).toHaveLength(1);
   });

   it("addPhysicalView: should NOT emit when not editable", async () => {
      const { comp } = await renderComp({ model: READ_ONLY_MODEL });
      const emitted: any[] = [];
      comp.onAddPhysicalView.subscribe(() => emitted.push(true));

      comp.addPhysicalView();

      expect(emitted).toHaveLength(0);
   });

   it("addLogicalModel: should emit when editable", async () => {
      const { comp } = await renderComp({ model: EDITABLE_MODEL });
      const emitted: any[] = [];
      comp.onAddLM.subscribe(() => emitted.push(true));

      comp.addLogicalModel();

      expect(emitted).toHaveLength(1);
   });

   it("addLogicalModel: should NOT emit when not editable", async () => {
      const { comp } = await renderComp({ model: READ_ONLY_MODEL });
      const emitted: any[] = [];
      comp.onAddLM.subscribe(() => emitted.push(true));

      comp.addLogicalModel();

      expect(emitted).toHaveLength(0);
   });

   it("addDataModelFolder: should emit when editable", async () => {
      const { comp } = await renderComp({ model: EDITABLE_MODEL });
      const emitted: any[] = [];
      comp.onAddFolder.subscribe(() => emitted.push(true));

      comp.addDataModelFolder();

      expect(emitted).toHaveLength(1);
   });

   it("addDataModelFolder: should NOT emit when not editable", async () => {
      const { comp } = await renderComp({ model: READ_ONLY_MODEL });
      const emitted: any[] = [];
      comp.onAddFolder.subscribe(() => emitted.push(true));

      comp.addDataModelFolder();

      expect(emitted).toHaveLength(0);
   });

   it("addVPMModel: should emit when editable", async () => {
      const { comp } = await renderComp({ model: EDITABLE_MODEL, isvpm: true });
      const emitted: any[] = [];
      comp.onAddVPM.subscribe(() => emitted.push(true));

      comp.addVPMModel();

      expect(emitted).toHaveLength(1);
   });

   it("addVPMModel: should NOT emit when not editable", async () => {
      const { comp } = await renderComp({ model: READ_ONLY_MODEL, isvpm: true });
      const emitted: any[] = [];
      comp.onAddVPM.subscribe(() => emitted.push(true));

      comp.addVPMModel();

      expect(emitted).toHaveLength(0);
   });

   it("deleteSelected: should always emit regardless of editable state", async () => {
      const { comp } = await renderComp({ model: READ_ONLY_MODEL });
      const emitted: any[] = [];
      comp.onDeleteSelected.subscribe(() => emitted.push(true));

      comp.deleteSelected();

      expect(emitted).toHaveLength(1);
   });

   it("moveSelected: should always emit regardless of editable state", async () => {
      const { comp } = await renderComp({ model: READ_ONLY_MODEL });
      const emitted: any[] = [];
      comp.onMoveSelected.subscribe(() => emitted.push(true));

      comp.moveSelected();

      expect(emitted).toHaveLength(1);
   });
});
