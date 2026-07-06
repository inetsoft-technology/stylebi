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
 * ChartAxisArea — Pass 3: Display
 *
 * Covers the items explicitly deferred from Pass 1/2 (see those files' headers):
 *   cursor getter, emitCursor axis-position math (per areaName branch + resizeEnable
 *   gating), createAxisResizeInfo bounds per axis, getSortIconTop/getDrillIconLeft/
 *   getDrillIconPosition, showSortIcon, minWidth, canvasX/canvasY.
 *
 * Uses the same TestBed.runInInjectionContext direct-instantiation convention documented
 * in chart-axis-area.component.interaction.tl.spec.ts.
 */

import { createComponent, makeAxis } from "./chart-axis-area.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: cursor getter [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — cursor", () => {
   it("should return null when the axis has no fields", () => {
      const { comp } = createComponent({ axis: makeAxis({ axisFields: [] }) });
      expect(comp.cursor).toBeNull();
   });

   it.each([
      ["bottom_x_axis", "row-resize"],
      ["top_x_axis", "row-resize"],
      ["right_y_axis", "col-resize"],
      ["left_y_axis", "col-resize"],
   ] as const)("should return %s cursor for areaName %s", (areaName, expected) => {
      const { comp } = createComponent({ axis: makeAxis({ areaName: areaName as any }) });
      expect(comp.cursor).toBe(expected);
   });

   it("should return null for an unrecognized areaName", () => {
      const { comp } = createComponent({ axis: makeAxis({ areaName: "plot_area" as any }) });
      expect(comp.cursor).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 2: emitCursor axis-position math [Risk 2]
// ---------------------------------------------------------------------------

// emitCursor is private; there's no public entry point that exercises its per-areaName math
// without also depending on onMove's other side effects, so it's invoked directly via bypass.
describe("ChartAxisArea — emitCursor", () => {
   it("should set the resize cursor when hovering within the margin of a bottom_x_axis", () => {
      const { comp } = createComponent({ axis: makeAxis({ areaName: "bottom_x_axis" }) });
      comp.resizeEnable = true;
      const emitted: any[] = [];
      comp.changeCursor.subscribe((v: any) => emitted.push(v));

      (comp as any).emitCursor(0, 2);

      expect(comp.resizeCursor).toBe("row-resize");
      expect(emitted).toEqual(["row-resize"]);
   });

   it("should NOT set a resize cursor when outside the margin of a bottom_x_axis", () => {
      const { comp } = createComponent({ axis: makeAxis({ areaName: "bottom_x_axis" }) });
      comp.resizeEnable = true;

      (comp as any).emitCursor(0, 20);

      expect(comp.resizeCursor).toBeNull();
   });

   it("should find the matching band and its index when scanning a top_x_axis from the end", () => {
      const { comp } = createComponent({
         axis: makeAxis({ areaName: "top_x_axis", axisSizes: [40, 60] })
      });
      comp.resizeEnable = true;

      // Loop scans from the last band backwards; eventY == axisSizes[1] (60) matches
      // on the very first (i=1) iteration, before any decrement happens.
      (comp as any).emitCursor(0, 60);

      expect(comp.resizeCursor).toBe("row-resize");
      expect(comp.resizeIdx).toBe(1);
   });

   it("should set the resize cursor when hovering within the margin of a right_y_axis", () => {
      const { comp } = createComponent({ axis: makeAxis({ areaName: "right_y_axis" }) });
      comp.resizeEnable = true;

      (comp as any).emitCursor(2, 0);

      expect(comp.resizeCursor).toBe("col-resize");
   });

   it("should set the resize cursor and index when scanning a left_y_axis band", () => {
      const { comp } = createComponent({
         axis: makeAxis({ areaName: "left_y_axis", axisSizes: [30, 30] })
      });
      comp.resizeEnable = true;

      (comp as any).emitCursor(30, 0);

      expect(comp.resizeCursor).toBe("col-resize");
      expect(comp.resizeIdx).toBe(0);
   });

   it("should NOT allow resizing the first left_y_axis band when its region is a period axis", () => {
      const { comp } = createComponent({
         axis: makeAxis({ areaName: "left_y_axis", axisSizes: [30, 30] })
      });
      // Mutate regions directly post-construction to avoid re-triggering the chartObject
      // setter's updateRegionTree(), which needs full region geometry (segTypes/points) to
      // build a real region tree — a minimal { period: true } stub would crash it.
      comp.chartObject.regions = [{ period: true }] as any;
      comp.resizeEnable = true;

      (comp as any).emitCursor(30, 0);

      expect(comp.resizeCursor).toBeNull();
   });

   it("should force the resize cursor to null when resizing is disabled, even if a band matched", () => {
      const { comp } = createComponent({ axis: makeAxis({ areaName: "bottom_x_axis" }) });
      comp.resizeEnable = false;
      const emitted: any[] = [];
      comp.changeCursor.subscribe((v: any) => emitted.push(v));

      (comp as any).emitCursor(0, 2);

      expect(comp.resizeCursor).toBeNull();
      expect(emitted).toEqual([null]);
   });

   it("should NOT re-emit changeCursor when the computed cursor is unchanged", () => {
      const { comp } = createComponent({ axis: makeAxis({ areaName: "bottom_x_axis" }) });
      comp.resizeEnable = true;
      (comp as any).emitCursor(0, 2);
      const emitted: any[] = [];
      comp.changeCursor.subscribe((v: any) => emitted.push(v));

      (comp as any).emitCursor(0, 2);

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 3: createAxisResizeInfo bounds per axis [Risk 2]
// ---------------------------------------------------------------------------

// createAxisResizeInfo is private; only reachable in practice via onDown's resize-start path,
// which would force every test to also fake pointer coordinates and a resize-cursor state
// unrelated to the bounds math itself, so it's invoked directly via bypass.
describe("ChartAxisArea — createAxisResizeInfo", () => {
   const regions = [{ tipIdx: 0 }] as any;

   it("should compute bounds for a bottom_x_axis", () => {
      const { comp } = createComponent({
         axis: makeAxis({ areaName: "bottom_x_axis", layoutBounds: { x: 0, y: 100, width: 200, height: 30 } as any })
      });
      comp.model = { ...comp.model, contentBounds: { x: 0, y: 10, width: 400, height: 300 } as any };

      const info = (comp as any).createAxisResizeInfo(regions);

      expect(info).toEqual(expect.objectContaining({
         minPosition: 11, maxPosition: 132, vertical: false, type: "axis"
      }));
   });

   it("should compute bounds for a top_x_axis", () => {
      const { comp } = createComponent({
         axis: makeAxis({ areaName: "top_x_axis", layoutBounds: { x: 0, y: 50, width: 200, height: 30 } as any })
      });
      comp.model = { ...comp.model, contentBounds: { x: 0, y: 10, width: 400, height: 300 } as any };

      const info = (comp as any).createAxisResizeInfo(regions);

      expect(info).toEqual(expect.objectContaining({
         minPosition: 48, maxPosition: 309, vertical: false, type: "axis"
      }));
   });

   it("should compute bounds for a left_y_axis", () => {
      const { comp } = createComponent({
         axis: makeAxis({ areaName: "left_y_axis", layoutBounds: { x: 20, y: 0, width: 30, height: 200 } as any })
      });
      comp.model = { ...comp.model, contentBounds: { x: 60, y: 0, width: 300, height: 400 } as any };

      const info = (comp as any).createAxisResizeInfo(regions);

      expect(info).toEqual(expect.objectContaining({
         minPosition: 18, maxPosition: 359, vertical: true, type: "axis"
      }));
   });

   it("should compute bounds for a right_y_axis", () => {
      const { comp } = createComponent({
         axis: makeAxis({ areaName: "right_y_axis", layoutBounds: { x: 300, y: 0, width: 30, height: 200 } as any })
      });
      comp.model = { ...comp.model, contentBounds: { x: 60, y: 0, width: 200, height: 400 } as any };

      const info = (comp as any).createAxisResizeInfo(regions);

      expect(info).toEqual(expect.objectContaining({
         minPosition: 61, maxPosition: 332, vertical: true, type: "axis"
      }));
   });

   it("should leave min/max undefined and vertical false for an unrecognized areaName", () => {
      const { comp } = createComponent({ axis: makeAxis({ areaName: "plot_area" as any }) });

      const info = (comp as any).createAxisResizeInfo(regions);

      expect(info).toEqual(expect.objectContaining({
         minPosition: undefined, maxPosition: undefined, vertical: false, type: "axis"
      }));
   });

   it("should include the resize index, fields and regions from the current axis state", () => {
      const { comp } = createComponent({
         axis: makeAxis({ areaName: "bottom_x_axis", axisFields: ["A", "B"] })
      });
      (comp as any).resizeIdx = 1;

      const info = (comp as any).createAxisResizeInfo(regions);

      expect(info.fields).toEqual(["A", "B"]);
      expect(info.resizeIdx).toBe(1);
      expect(info.regions).toBe(regions);
      expect(info.chartObject).toBe(comp.chartObject);
   });
});

// ---------------------------------------------------------------------------
// Group 4: getDrillIconPosition / getDrillIconLeft / getSortIconTop [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — getDrillIconPosition", () => {
   it("should sum band widths plus the 18-modulo offset for each preceding band", () => {
      const { comp } = createComponent({ axis: makeAxis({ axisSizes: [10, 25] }) });
      // index 2: reduce over [10, 25] -> (0 +10+|18%10|)=18 -> (18+25+|18%18|)=43
      expect(comp.getDrillIconPosition(2)).toBe(43);
   });

   it("should return 0 for index 0 (nothing to sum)", () => {
      const { comp } = createComponent({ axis: makeAxis({ axisSizes: [10, 25] }) });
      expect(comp.getDrillIconPosition(0)).toBe(0);
   });
});

describe("ChartAxisArea — getDrillIconLeft", () => {
   it("should delegate to getDrillIconPosition for a y axis", () => {
      const { comp } = createComponent({ axis: makeAxis({ axisType: "y", axisSizes: [10, 25] }) });
      expect(comp.getDrillIconLeft(1)).toBe(comp.getDrillIconPosition(1));
   });

   it("should use the negative capped layout-bounds x offset for an x axis", () => {
      const { comp } = createComponent({
         axis: makeAxis({ axisType: "x", layoutBounds: { x: 5, y: 0, width: 10, height: 10 } as any })
      });
      expect(comp.getDrillIconLeft(0)).toBe(-5);
   });

   it("should cap the negative offset at -18 for an x axis with a large layout-bounds x", () => {
      const { comp } = createComponent({
         axis: makeAxis({ axisType: "x", layoutBounds: { x: 50, y: 0, width: 10, height: 10 } as any })
      });
      expect(comp.getDrillIconLeft(0)).toBe(-18);
   });

   it("should return null for an axis type that is neither x nor y", () => {
      const { comp } = createComponent({ axis: makeAxis({ axisType: "z" as any }) });
      expect(comp.getDrillIconLeft(0)).toBeNull();
   });
});

describe("ChartAxisArea — getSortIconTop", () => {
   it("should subtract both the drill-icon height and the 20px sort gap for a y axis with a drill icon visible", () => {
      const { comp } = createComponent({
         axis: makeAxis({ axisType: "y", layoutBounds: { x: 0, y: 0, width: 10, height: 100 } as any })
      });
      (comp as any).drillVisible = true;
      expect(comp.getSortIconTop(["+"])).toBe(100 - 22 - 20);
   });

   it("should only subtract the drill-icon height for a y axis when the drill icon is hidden", () => {
      const { comp } = createComponent({
         axis: makeAxis({ axisType: "y", layoutBounds: { x: 0, y: 0, width: 10, height: 100 } as any })
      });
      (comp as any).drillVisible = false;
      expect(comp.getSortIconTop(["+"])).toBe(100 - 22);
   });

   it("should only subtract the 20px sort gap for an x axis with a drill icon visible (no drill-icon height)", () => {
      const { comp } = createComponent({
         axis: makeAxis({ axisType: "x", layoutBounds: { x: 0, y: 0, width: 10, height: 100 } as any })
      });
      (comp as any).drillVisible = true;
      expect(comp.getSortIconTop(["+"])).toBe(100 - 20);
   });

   it("should treat an all-empty axisOps as having no drill icon", () => {
      const { comp } = createComponent({
         axis: makeAxis({ axisType: "y", layoutBounds: { x: 0, y: 0, width: 10, height: 100 } as any })
      });
      (comp as any).drillVisible = true;
      expect(comp.getSortIconTop(["", ""])).toBe(100 - 22);
   });
});

// ---------------------------------------------------------------------------
// Group 5: showSortIcon [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — showSortIcon", () => {
   it("should be false when hideSortIcon is set, regardless of other state", () => {
      const { comp } = createComponent();
      comp.hideSortIcon = true;
      (comp as any).drillVisible = true;
      expect(comp.showSortIcon).toBe(false);
   });

   it("should be false when neither drillVisible nor onTitle is set", () => {
      const { comp } = createComponent();
      comp.hideSortIcon = false;
      (comp as any).drillVisible = false;
      comp.onTitle = false;
      expect(comp.showSortIcon).toBe(false);
   });

   it("should be false when the field is a calculated sort field, even if drillVisible", () => {
      const { comp } = createComponent();
      comp.hideSortIcon = false;
      (comp as any).drillVisible = true;
      (comp.chartObject as any).sortFieldIsCalc = true;
      expect(comp.showSortIcon).toBe(false);
   });

   it("should be true when drillVisible and the field is not a calculated sort field", () => {
      const { comp } = createComponent();
      comp.hideSortIcon = false;
      (comp as any).drillVisible = true;
      (comp.chartObject as any).sortFieldIsCalc = false;
      expect(comp.showSortIcon).toBe(true);
   });

   it("should be true via onTitle alone when there is no chartObject to check sortFieldIsCalc", () => {
      const { comp } = createComponent();
      comp.hideSortIcon = false;
      comp.onTitle = true;
      (comp as any)._chartObject = undefined;
      expect(comp.showSortIcon).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 6: minWidth [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — minWidth", () => {
   it("should floor to 20 when the sort icon is shown and the layout width is narrower", () => {
      const { comp } = createComponent({
         axis: makeAxis({ layoutBounds: { x: 0, y: 0, width: 10, height: 10 } as any })
      });
      comp.onTitle = true;
      (comp.chartObject as any).sortFieldIsCalc = false;
      expect(comp.minWidth).toBe(20);
   });

   it("should return the actual layout width when it already exceeds 20 and the sort icon is shown", () => {
      const { comp } = createComponent({
         axis: makeAxis({ layoutBounds: { x: 0, y: 0, width: 50, height: 10 } as any })
      });
      comp.onTitle = true;
      (comp.chartObject as any).sortFieldIsCalc = false;
      expect(comp.minWidth).toBe(50);
   });

   it("should return the raw layout width when the sort icon is hidden, even if narrower than 20", () => {
      const { comp } = createComponent({
         axis: makeAxis({ layoutBounds: { x: 0, y: 0, width: 10, height: 10 } as any })
      });
      comp.hideSortIcon = true;
      expect(comp.minWidth).toBe(10);
   });
});

// ---------------------------------------------------------------------------
// Group 7: canvasX / canvasY [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartAxisArea — canvasX / canvasY", () => {
   it("should be 0 when scrollLeft/scrollTop are unset", () => {
      const { comp } = createComponent();
      expect((comp as any).canvasX).toBe(0);
      expect((comp as any).canvasY).toBe(0);
   });

   it("should mirror scrollLeft for canvasX on an x axis", () => {
      const { comp } = createComponent({ axis: makeAxis({ axisType: "x" }) });
      comp.scrollLeft = 15;
      expect((comp as any).canvasX).toBe(15);
   });

   it("should mirror scrollTop for canvasY on a y axis", () => {
      const { comp } = createComponent({ axis: makeAxis({ axisType: "y" }) });
      comp.scrollTop = 25;
      expect((comp as any).canvasY).toBe(25);
   });
});
