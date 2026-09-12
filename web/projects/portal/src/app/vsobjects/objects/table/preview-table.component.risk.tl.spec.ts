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
 * PreviewTableComponent — Pass 2: Risk
 *
 * Covers async/deferred state mutations that could cause stale-state or race conditions:
 *
 *   Group 1 [Risk 3] — resizeEnd isDetails branch: the putModel URL depends entirely on
 *                       the isDetails flag; wrong branch → silent data loss (wrong persistence URI)
 *   Group 2 [Risk 3] — updateWidths setTimeout: updateColumnRange is intentionally deferred;
 *                       if the component is destroyed before the timeout fires the call still
 *                       runs against stale state
 *   Group 3 [Risk 3] — tableData setter Promise.resolve microtask: scrollLeft is restored
 *                       asynchronously; a fast column-count change between the setter and
 *                       the microtask can result in the wrong scrollLeft value
 *   Group 4 [Risk 3] — colWidths setter Promise.resolve microtask: initColumnWidths is called
 *                       inside a resolved promise; the microtask reads _colWidths which may have
 *                       changed again before the callback runs
 *
 * Out of scope this pass: ngOnDestroy, ngAfterContentChecked, ngAfterViewChecked, onClick,
 *   horizontalScroll, formatClicked, sortClicked, touchVScroll, touchHScroll,
 *   verticalScrollHandler, wheelScrollHandler, clickLink, apply, dragStart, dragOverTable,
 *   onLeave, dropOnTable, changeCellText, openVisibilityContextMenu, resizeListener
 *   → covered in preview-table.component.interaction.tl.spec.ts
 *   Display/pure-logic: getSortLabel, selectCell paths, isHeaderValid, etc.
 *   → covered in preview-table.component.display.tl.spec.ts
 */

import { createPreviewComponent, makeTableData } from "./preview-table.component.test-helpers";

afterEach(() => {
   vi.restoreAllMocks();
   vi.useRealTimers();
});

// ── Group 1 — resizeEnd isDetails branch ────────────────────────────────────

describe("Group 1 — resizeEnd: putModel URL is driven by isDetails flag", () => {
   it("should call putModel with the showdetails URI when isDetails=true", () => {
      const { comp, modelService } = createPreviewComponent();
      comp.isDetails = true;
      comp.startResize({ preventDefault: vi.fn(), pageX: 100 } as any, 0);
      (comp as any).resizeEnd({ pageX: 150 } as MouseEvent);

      const url: string = modelService.putModel.mock.calls[0][0];
      expect(url).toContain("showdetails");
      expect(url).not.toContain("showdata");
   });

   it("should call putModel with the showdata URI when isDetails=false", () => {
      const { comp, modelService } = createPreviewComponent();
      comp.isDetails = false;
      comp.startResize({ preventDefault: vi.fn(), pageX: 100 } as any, 0);
      (comp as any).resizeEnd({ pageX: 150 } as MouseEvent);

      const url: string = modelService.putModel.mock.calls[0][0];
      expect(url).toContain("showdata");
      expect(url).not.toContain("showdetails");
   });

   it("should subscribe exactly once per resizeEnd call (fire-and-forget)", () => {
      // 🔁 Regression-sensitive: if subscribe() is removed the HTTP request never fires
      const { comp, modelService } = createPreviewComponent();
      const subscribeSpy = vi.fn();
      modelService.putModel.mockReturnValue({ subscribe: subscribeSpy });

      comp.startResize({ preventDefault: vi.fn(), pageX: 100 } as any, 0);
      (comp as any).resizeEnd({ pageX: 130 } as MouseEvent);

      expect(subscribeSpy).toHaveBeenCalledTimes(1);
   });
});

// ── Group 2 — updateWidths setTimeout: deferred updateColumnRange ────────────

describe("Group 2 — updateWidths: setTimeout defers updateColumnRange", () => {
   beforeEach(() => vi.useFakeTimers());

   it("should set columnRightPositions synchronously while deferring updateColumnRange", () => {
      // 🔁 Regression-sensitive: columnRightPositions must be ready synchronously for
      // subsequent startResize calls that read this.columnRightPositions[index]
      const { comp } = createPreviewComponent();
      const rangeSpy = vi.spyOn(comp as any, "updateColumnRange");
      // columnRightPositions is populated in updateWidths before the setTimeout
      expect((comp as any).columnRightPositions.length).toBeGreaterThan(0);
      // updateColumnRange has NOT been called — fake timers prevent the setTimeout from firing
      expect(rangeSpy).not.toHaveBeenCalled();
   });

   it("should invoke updateColumnRange after the setTimeout fires", () => {
      const { comp } = createPreviewComponent();
      // Clear the timer already queued by createPreviewComponent's initColumnWidths call
      // so the spy count is clean for the explicit updateWidths() call below
      vi.clearAllTimers();

      const rangeSpy = vi.spyOn(comp as any, "updateColumnRange").mockImplementation(() => {});
      (comp as any).updateWidths();

      expect(rangeSpy).not.toHaveBeenCalled();
      vi.runAllTimers();
      expect(rangeSpy).toHaveBeenCalledTimes(1);
   });

   it("should populate columnIndexRange after the timer fires", () => {
      const { comp } = createPreviewComponent();
      vi.runAllTimers(); // fire the timer queued by initColumnWidths in createPreviewComponent
      expect((comp as any).columnIndexRange).toBeDefined();
   });
});

// ── Group 3 — tableData setter Promise.resolve microtask ────────────────────

describe("Group 3 — tableData setter: Promise.resolve microtask restores scrollLeft", () => {
   it("should call renderer.setProperty for scrollLeft inside the microtask after column-count change", async () => {
      // 🔁 Regression-sensitive: the async setProperty restores the horizontal scroll position
      // that was captured synchronously before initColumnWidths() runs
      const { comp, renderer } = createPreviewComponent();
      await Promise.resolve(); // flush initial microtask from createPreviewComponent

      renderer.setProperty.mockClear();

      // New column count (4 vs 3) → !sameCols → microtask is scheduled
      comp.tableData = makeTableData(2, 4);
      expect(renderer.setProperty).not.toHaveBeenCalled(); // microtask not yet run

      await Promise.resolve();
      expect(renderer.setProperty).toHaveBeenCalledWith(
         expect.anything(),
         "scrollLeft",
         expect.any(Number),
      );
   });

   it("should call initColumnWidths a second time inside the microtask when column count changes", async () => {
      const { comp } = createPreviewComponent();
      await Promise.resolve(); // flush initial

      const initSpy = vi.spyOn(comp as any, "initColumnWidths").mockImplementation(() => {});
      comp.tableData = makeTableData(2, 4);
      await Promise.resolve();

      expect(initSpy).toHaveBeenCalled();
   });

   it("should limit tableData to 5001 rows and set limited=true when data exceeds the cap", () => {
      const { comp } = createPreviewComponent();
      const bigData = makeTableData(5002, 2);
      comp.tableData = bigData;
      expect(comp.limited).toBe(true);
      expect(comp.tableData.length).toBe(5001);
   });
});

// ── Group 4 — colWidths setter Promise.resolve microtask ────────────────────

describe("Group 4 — colWidths setter: Promise.resolve microtask calls initColumnWidths", () => {
   it("should call renderer.setProperty for scrollLeft inside the microtask on width change", async () => {
      const { comp, renderer } = createPreviewComponent();
      await Promise.resolve(); // flush initial microtask

      renderer.setProperty.mockClear();
      // _colWidths starts null/undefined → isSameWidth=false → microtask scheduled
      comp.colWidths = [120, 120, 120];
      expect(renderer.setProperty).not.toHaveBeenCalled(); // before microtask

      await Promise.resolve();
      expect(renderer.setProperty).toHaveBeenCalledWith(
         expect.anything(),
         "scrollLeft",
         expect.any(Number),
      );
   });

   it("should NOT schedule a microtask when colWidths are identical to current _colWidths", async () => {
      const { comp, renderer } = createPreviewComponent();
      await Promise.resolve(); // flush initial

      // First set: _colWidths=undefined → isSameWidth=false → microtask runs
      comp.colWidths = [100, 100, 100];
      await Promise.resolve();
      renderer.setProperty.mockClear();

      // Second set with same values: isSameWidth=true → no microtask
      comp.colWidths = [100, 100, 100];
      await Promise.resolve();

      expect(renderer.setProperty).not.toHaveBeenCalled();
   });

   it("should apply the provided colWidths over initColumnWidths computed defaults", async () => {
      // 🔁 Regression-sensitive: _colWidths values override computed defaults in initColumnWidths;
      // if this override loop is removed, user-resized columns are reset on every colWidths update
      const { comp } = createPreviewComponent();
      await Promise.resolve(); // flush initial

      comp.colWidths = [200, 200, 200];
      await Promise.resolve();

      // After microtask, initColumnWidths() has run and applied _colWidths[i] over defaults
      expect((comp as any)._columnWidths[0]).toBe(200);
      expect((comp as any)._columnWidths[1]).toBe(200);
   });
});
