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
 * FiltersPane — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnInit deduplication: filters whose column matches a sharedFilter column
 *     are removed from model.filters to prevent double-listing the same filter
 *   Group 2 [Risk 3] — add(): moves selected filter(s) to sharedFilters, sets filterId=column,
 *     updates both selection lists; handles single and multiple selections
 *   Group 3 [Risk 2] — remove(): moves selected sharedFilter(s) back to filters, updates both
 *     selection lists; handles single and multiple selections
 *   Group 4 [Risk 2] — isAddEnabled / isRemoveEnabled: boolean getters reflect selection state
 *     in both directions
 *   Group 5 [Risk 1] — selectFilter + isFilterSelected: plain-click selection via selectWithEvent;
 *     ngOnInit auto-selects index 0 when filters are non-empty
 *   Group 6 [Risk 1] — selectSharedFilter + isSharedFilterSelected: plain-click selection;
 *     ngOnInit auto-selects index 0 when sharedFilters are non-empty
 *
 * Confirmed bugs (it.fails):
 *   None.
 *
 * Out of scope:
 *   Ctrl/shift multi-select — MultiSelectList behaviour is tested in its own unit tests;
 *     these tests verify FiltersPane delegates to MultiSelectList correctly.
 *   Template rendering — FiltersPane uses NO_ERRORS_SCHEMA; DOM assertions are integration-level.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { FiltersPane } from "./filters-pane.component";
import { FiltersPaneModel } from "../../data/vs/filters-pane-model";
import { FilterModel } from "../../data/vs/filter-model";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

function makeFilter(column: string, filterId: string = ""): FilterModel {
   const f = new FilterModel();
   f.column = column;
   f.filterId = filterId;
   return f;
}

function createModel(overrides: Partial<FiltersPaneModel> = {}): FiltersPaneModel {
   return {
      filters: [],
      sharedFilters: [],
      ...overrides,
   };
}

async function renderComponent(modelOverrides: Partial<FiltersPaneModel> = {}) {
   const model = createModel(modelOverrides);
   const { fixture } = await render(FiltersPane, {
      schemas: [NO_ERRORS_SCHEMA],
      componentInputs: { model },
   });
   const comp = fixture.componentInstance as FiltersPane;
   return { comp, fixture, model };
}

// ---------------------------------------------------------------------------
// Group 1: ngOnInit deduplication [Risk 3]
// ---------------------------------------------------------------------------

describe("FiltersPane — ngOnInit deduplication", () => {
   // 🔁 Regression-sensitive: on init, any filter in model.filters that shares its column name
   //    with an existing sharedFilter must be removed to avoid showing the same filter twice.
   it("should remove from filters any entry whose column matches a sharedFilter column", async () => {
      const shared = makeFilter("col1", "col1");
      const dup = makeFilter("col1");
      const unique = makeFilter("col2");
      const { model } = await renderComponent({
         sharedFilters: [shared],
         filters: [dup, unique],
      });

      expect(model.filters).toHaveLength(1);
      expect(model.filters[0].column).toBe("col2");
   });

   it("should leave filters untouched when there are no sharedFilters", async () => {
      const f1 = makeFilter("a");
      const f2 = makeFilter("b");
      const { model } = await renderComponent({ filters: [f1, f2], sharedFilters: [] });
      expect(model.filters).toHaveLength(2);
   });

   it("should remove only the duplicate entry when multiple filters exist", async () => {
      const shared = makeFilter("col1", "col1");
      const dup = makeFilter("col1");
      const keep1 = makeFilter("col2");
      const keep2 = makeFilter("col3");
      const { model } = await renderComponent({
         sharedFilters: [shared],
         filters: [keep1, dup, keep2],
      });

      expect(model.filters).toHaveLength(2);
      expect(model.filters.map(f => f.column)).toEqual(["col2", "col3"]);
   });

   it("should handle multiple sharedFilters deduplicating multiple filters", async () => {
      const shared1 = makeFilter("col1", "col1");
      const shared2 = makeFilter("col2", "col2");
      const dup1 = makeFilter("col1");
      const dup2 = makeFilter("col2");
      const keep = makeFilter("col3");
      const { model } = await renderComponent({
         sharedFilters: [shared1, shared2],
         filters: [dup1, dup2, keep],
      });

      expect(model.filters).toHaveLength(1);
      expect(model.filters[0].column).toBe("col3");
   });
});

// ---------------------------------------------------------------------------
// Group 2: add() [Risk 3]
// ---------------------------------------------------------------------------

describe("FiltersPane — add", () => {
   // 🔁 Regression-sensitive: add() must move the selected filter from filters to sharedFilters
   //    and set filterId = column; if filterId is not set the shared filter is not properly tracked.
   it("should move the selected filter from filters to sharedFilters", async () => {
      const f1 = makeFilter("colA");
      const f2 = makeFilter("colB");
      const { comp, model } = await renderComponent({ filters: [f1, f2], sharedFilters: [] });
      // ngOnInit selects index 0 by default
      comp.add();

      expect(model.filters).toHaveLength(1);
      expect(model.sharedFilters).toHaveLength(1);
      expect(model.sharedFilters[0].column).toBe("colA");
   });

   it("should set filterId equal to column on the moved filter", async () => {
      const f = makeFilter("myCol");
      const { comp, model } = await renderComponent({ filters: [f], sharedFilters: [] });
      comp.add();

      expect(model.sharedFilters[0].filterId).toBe("myCol");
   });

   it("should not move any filter when no filter is selected (isAddEnabled=false)", async () => {
      const f1 = makeFilter("colA");
      const { comp, model } = await renderComponent({ filters: [f1], sharedFilters: [] });
      // Deselect by resizing
      comp["filtersSelection"].setSize(0);
      comp["filtersSelection"].setSize(1); // clears selection
      comp.add();

      expect(model.filters).toHaveLength(1);
      expect(model.sharedFilters).toHaveLength(0);
   });

   it("should select the new sharedFilter range after add", async () => {
      const f1 = makeFilter("colA");
      const f2 = makeFilter("colB");
      const { comp } = await renderComponent({ filters: [f1, f2], sharedFilters: [] });
      comp.add(); // moves colA (index 0) to sharedFilters

      // The newly added shared filter (index 0) should be selected
      expect(comp.isSharedFilterSelected(0)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 3: remove() [Risk 2]
// ---------------------------------------------------------------------------

describe("FiltersPane — remove", () => {
   it("should move the selected sharedFilter back to filters", async () => {
      const shared = makeFilter("colA", "colA");
      const { comp, model } = await renderComponent({ filters: [], sharedFilters: [shared] });
      // ngOnInit selects sharedFilter index 0 by default
      comp.remove();

      expect(model.sharedFilters).toHaveLength(0);
      expect(model.filters).toHaveLength(1);
      expect(model.filters[0].column).toBe("colA");
   });

   it("should not move any filter when no sharedFilter is selected (isRemoveEnabled=false)", async () => {
      const shared = makeFilter("colA", "colA");
      const { comp, model } = await renderComponent({ filters: [], sharedFilters: [shared] });
      // Clear the selection
      comp["sharedFiltersSelection"].setSize(0);
      comp["sharedFiltersSelection"].setSize(1);
      comp.remove();

      expect(model.sharedFilters).toHaveLength(1);
      expect(model.filters).toHaveLength(0);
   });

   it("should select the moved filter in the filters list after remove", async () => {
      const shared = makeFilter("colA", "colA");
      const { comp } = await renderComponent({ filters: [], sharedFilters: [shared] });
      comp.remove();

      // The newly moved filter (index 0) should be selected in filters
      expect(comp.isFilterSelected(0)).toBe(true);
   });

   it("should move multiple selected sharedFilters back to filters", async () => {
      const shared1 = makeFilter("col1", "col1");
      const shared2 = makeFilter("col2", "col2");
      const { comp, model } = await renderComponent({ filters: [], sharedFilters: [shared1, shared2] });
      // Select both
      comp.selectSharedFilter(0, { ctrlKey: false, shiftKey: false } as MouseEvent);
      comp.selectSharedFilter(1, { ctrlKey: true, shiftKey: false } as MouseEvent);
      comp.remove();

      expect(model.sharedFilters).toHaveLength(0);
      expect(model.filters).toHaveLength(2);
   });
});

// ---------------------------------------------------------------------------
// Group 4: isAddEnabled / isRemoveEnabled [Risk 2]
// ---------------------------------------------------------------------------

describe("FiltersPane — isAddEnabled / isRemoveEnabled", () => {
   it("should return true from isAddEnabled when a filter is selected", async () => {
      const f = makeFilter("colA");
      const { comp } = await renderComponent({ filters: [f], sharedFilters: [] });
      // ngOnInit selects index 0
      expect(comp.isAddEnabled()).toBe(true);
   });

   it("should return false from isAddEnabled when no filter is selected", async () => {
      const f = makeFilter("colA");
      const { comp } = await renderComponent({ filters: [f], sharedFilters: [] });
      comp["filtersSelection"].setSize(0);
      comp["filtersSelection"].setSize(1); // reset without selecting
      expect(comp.isAddEnabled()).toBe(false);
   });

   it("should return false from isAddEnabled when filters list is empty", async () => {
      const { comp } = await renderComponent({ filters: [], sharedFilters: [] });
      expect(comp.isAddEnabled()).toBe(false);
   });

   it("should return true from isRemoveEnabled when a sharedFilter is selected", async () => {
      const shared = makeFilter("colA", "colA");
      const { comp } = await renderComponent({ filters: [], sharedFilters: [shared] });
      // ngOnInit selects sharedFilter index 0
      expect(comp.isRemoveEnabled()).toBe(true);
   });

   it("should return false from isRemoveEnabled when no sharedFilter is selected", async () => {
      const shared = makeFilter("colA", "colA");
      const { comp } = await renderComponent({ filters: [], sharedFilters: [shared] });
      comp["sharedFiltersSelection"].setSize(0);
      comp["sharedFiltersSelection"].setSize(1);
      expect(comp.isRemoveEnabled()).toBe(false);
   });

   it("should return false from isRemoveEnabled when sharedFilters list is empty", async () => {
      const { comp } = await renderComponent({ filters: [], sharedFilters: [] });
      expect(comp.isRemoveEnabled()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 5: selectFilter + isFilterSelected [Risk 1]
// ---------------------------------------------------------------------------

describe("FiltersPane — selectFilter + isFilterSelected", () => {
   it("should auto-select index 0 on ngOnInit when filters are non-empty", async () => {
      const f = makeFilter("colA");
      const { comp } = await renderComponent({ filters: [f], sharedFilters: [] });
      expect(comp.isFilterSelected(0)).toBe(true);
   });

   it("should NOT auto-select any filter on ngOnInit when filters is empty", async () => {
      const { comp } = await renderComponent({ filters: [], sharedFilters: [] });
      expect(comp.isAddEnabled()).toBe(false);
   });

   it("should select the clicked index on plain click", async () => {
      const f1 = makeFilter("colA");
      const f2 = makeFilter("colB");
      const { comp } = await renderComponent({ filters: [f1, f2], sharedFilters: [] });

      comp.selectFilter(1, { ctrlKey: false, shiftKey: false } as MouseEvent);

      expect(comp.isFilterSelected(1)).toBe(true);
      expect(comp.isFilterSelected(0)).toBe(false);
   });

   it("should deselect previously selected filter when clicking a different one", async () => {
      const f1 = makeFilter("colA");
      const f2 = makeFilter("colB");
      const { comp } = await renderComponent({ filters: [f1, f2], sharedFilters: [] });
      // 0 is selected after init
      comp.selectFilter(1, { ctrlKey: false, shiftKey: false } as MouseEvent);

      expect(comp.isFilterSelected(0)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 6: selectSharedFilter + isSharedFilterSelected [Risk 1]
// ---------------------------------------------------------------------------

describe("FiltersPane — selectSharedFilter + isSharedFilterSelected", () => {
   it("should auto-select sharedFilter index 0 on ngOnInit when sharedFilters are non-empty", async () => {
      const shared = makeFilter("colA", "colA");
      const { comp } = await renderComponent({ filters: [], sharedFilters: [shared] });
      expect(comp.isSharedFilterSelected(0)).toBe(true);
   });

   it("should NOT auto-select any sharedFilter when sharedFilters is empty", async () => {
      const { comp } = await renderComponent({ filters: [], sharedFilters: [] });
      expect(comp.isRemoveEnabled()).toBe(false);
   });

   it("should select the clicked sharedFilter index on plain click", async () => {
      const s1 = makeFilter("colA", "colA");
      const s2 = makeFilter("colB", "colB");
      const { comp } = await renderComponent({ filters: [], sharedFilters: [s1, s2] });

      comp.selectSharedFilter(1, { ctrlKey: false, shiftKey: false } as MouseEvent);

      expect(comp.isSharedFilterSelected(1)).toBe(true);
      expect(comp.isSharedFilterSelected(0)).toBe(false);
   });

   it("should deselect previous sharedFilter when clicking a different one", async () => {
      const s1 = makeFilter("colA", "colA");
      const s2 = makeFilter("colB", "colB");
      const { comp } = await renderComponent({ filters: [], sharedFilters: [s1, s2] });
      // 0 is selected after init
      comp.selectSharedFilter(1, { ctrlKey: false, shiftKey: false } as MouseEvent);

      expect(comp.isSharedFilterSelected(0)).toBe(false);
   });
});
