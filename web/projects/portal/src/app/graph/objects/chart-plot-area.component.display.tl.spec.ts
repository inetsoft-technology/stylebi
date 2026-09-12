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
 * ChartPlotArea — Pass 3: Display
 *
 * Primary scope (per prescan): onMove cursor paths, changeCursor0, onAltDown/onAltUp,
 * getSingleClickRegions, selectIntersect, scrollContainerWidth/Height, isMaxModeHidden,
 * click (double-tap).
 *
 * Supplementary: the prescan's Pass 1/Pass 3 split never explicitly assigns onMove's
 * flyover/dataTip dispatch (lines preceding the cursor-path block) or its panning
 * short-circuit/reference-line branches to any pass — since Pass 3 is the last pass for
 * this component, Group 1/2/4/5 below close that gap so onMove has full baseline coverage.
 *
 * All onMove "cursor path" tests set event.button = 0 and viewerMode/previewMode as needed,
 * per the method's own gating (`(<any>event).button === 0`, then `viewerMode || previewMode`).
 */

import { GuiTool } from "../../common/util/gui-tool";
import { ChartTool } from "../model/chart-tool";
import { createComponent, makeModel, makeTile } from "./chart-plot-area.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

function moveEvent(overrides: Record<string, any> = {}): any {
   return { button: 0, offsetX: 0, offsetY: 0, ...overrides };
}

// ---------------------------------------------------------------------------
// Group 1: onMove — panning short-circuit [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — onMove while panning", () => {
   it("should update panX/panY from the panning origin and request change detection, without touching regions", () => {
      const { comp, changeRef } = createComponent();
      (comp as any).panning = { x: 100, y: 50 };
      vi.spyOn(ChartTool, "getTreeRegions");

      comp.onMove(moveEvent({ clientX: 130, clientY: 45 }) as any);

      expect(comp.panX).toBe(30);
      expect(comp.panY).toBe(-5);
      expect(changeRef.detectChanges).toHaveBeenCalled();
      expect(ChartTool.getTreeRegions).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2: onMove — flyover/dataTip dispatch [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — onMove flyover/dataTip dispatch", () => {
   it("should emit sendFlyover when flyover is enabled and not click-gated", () => {
      const { comp } = createComponent();
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      comp.flyover = true;
      comp.flyOnClick = false;
      const emitted: any[] = [];
      comp.sendFlyover.subscribe((v: any) => emitted.push(v));

      comp.onMove(moveEvent());

      expect(emitted).toEqual([
         expect.objectContaining({ chartObject: comp.chartObject, regions: [] }),
      ]);
   });

   it("should debounce showDataTip instead of the plain tooltip when a hover data tip is configured", () => {
      const { comp, debounceService } = createComponent();
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      comp.dataTip = "Tip1";
      comp.dataTipOnClick = false;
      const emitted: any[] = [];
      comp.showDataTip.subscribe((v: any) => emitted.push(v));

      comp.onMove(moveEvent());

      expect(debounceService.debounce).toHaveBeenCalledWith(
         "chart_dataTipEvent", expect.any(Function), 100, []
      );
      expect(emitted).toEqual([{ chartObject: comp.chartObject, regions: [] }]);
   });

   it("should emit the plain tooltip when there is no active data tip", () => {
      const { comp } = createComponent();
      const region = { tipIdx: 2 } as any;
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([0]);
      comp.chartObject.regions = [region];
      comp.dataTip = null;
      const emitted: any[] = [];
      comp.showTooltip.subscribe((v: any) => emitted.push(v));

      comp.onMove(moveEvent());

      expect(emitted).toEqual([{ tipIndex: 2, region }]);
   });
});

// ---------------------------------------------------------------------------
// Group 3: onMove — cursor paths [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — onMove cursor paths", () => {
   // changeCursor0 mutates the @HostBinding `hostCursor` field directly via zone.run — it
   // does NOT emit the `changeCursor` @Output (that's used by unrelated code paths like
   // selectChart's resize-end reset) — so these assertions read `comp.hostCursor` instead.
   it("should set a pointer cursor when the mouse is over a matching link point", () => {
      const { comp } = createComponent();
      const region = { rowIdx: 5, hyperlinks: null } as any;
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([0]);
      comp.chartObject.regions = [region];
      // row=-1/col=-1 short-circuit the row/col comparisons via the "< 0 means wildcard"
      // clauses, so the match doesn't depend on colIdx's real value — colIdx is still
      // invoked though (left operand of the `||`), so it must not crash on a bare region.
      comp.links = ["a/-1/-1"];
      vi.spyOn(ChartTool, "colIdx").mockReturnValue(3);
      comp.viewerMode = true;

      comp.onMove(moveEvent());

      expect(comp.hostCursor).toBe("pointer");
   });

   it("should set a pointer cursor for a hyperlinked region when there is no link match", () => {
      const { comp } = createComponent();
      const region = { hyperlinks: [{ name: "l1" }] } as any;
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([0]);
      comp.chartObject.regions = [region];
      comp.viewerMode = true;

      comp.onMove(moveEvent());

      expect(comp.hostCursor).toBe("pointer");
   });

   it("should set a pointer cursor over empty space when hasEmptyPlotLinkModel is set and nothing is under the cursor", () => {
      const { comp } = createComponent();
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      comp.hasEmptyPlotLinkModel = true;
      comp.previewMode = true;

      comp.onMove(moveEvent());

      expect(comp.hostCursor).toBe("pointer");
   });

   it("should NOT set a pointer cursor over empty space when hasEmptyPlotLinkModel is unset", () => {
      const { comp } = createComponent();
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      comp.hasEmptyPlotLinkModel = false;
      comp.viewerMode = true;
      comp.hostCursor = "pointer"; // start from a non-default value so "inherit" is a real change

      comp.onMove(moveEvent());

      expect(comp.hostCursor).toBe("inherit");
   });

   it("should inherit the cursor when nothing under the cursor is linked or hyperlinked", () => {
      const { comp } = createComponent();
      const region = { hyperlinks: null } as any;
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([0]);
      comp.chartObject.regions = [region];
      comp.viewerMode = true;
      comp.hostCursor = "pointer";

      comp.onMove(moveEvent());

      expect(comp.hostCursor).toBe("inherit");
   });

   it("should NOT touch the cursor at all outside viewer/preview mode", () => {
      const { comp, zone } = createComponent();
      const region = { hyperlinks: [{ name: "l1" }] } as any;
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([0]);
      comp.chartObject.regions = [region];
      comp.viewerMode = false;
      comp.previewMode = false;

      comp.onMove(moveEvent());

      expect(zone.run).not.toHaveBeenCalled();
      expect(comp.hostCursor).toBe("inherit");
   });
});

// ---------------------------------------------------------------------------
// Group 4: onMove — reference line drawing [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — onMove reference line", () => {
   function withReferenceLineCanvas(comp: any) {
      const canvas = document.createElement("canvas");
      const ctx = {} as CanvasRenderingContext2D;
      vi.spyOn(canvas, "getContext").mockReturnValue(ctx as any);
      comp.referenceLineCanvas = { nativeElement: canvas };
      return ctx;
   }

   it("should clear and draw the reference line for a region that has one", () => {
      const { comp, chartService } = createComponent();
      const ctx = withReferenceLineCanvas(comp);
      comp.chartObject.showReferenceLine = true;
      const region = { hyperlinks: null } as any;
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([0]);
      comp.chartObject.regions = [region];
      vi.spyOn(ChartTool, "refLine").mockReturnValue(true);
      const drawSpy = vi.spyOn(ChartTool, "drawReferenceLine").mockImplementation(() => {});

      comp.onMove(moveEvent());

      expect(chartService.clearCanvas).toHaveBeenCalledWith(ctx);
      expect(drawSpy).toHaveBeenCalledWith(ctx, region, 0, 0, 1);
   });

   it("should clear but NOT draw when no region under the cursor has a reference line", () => {
      const { comp, chartService } = createComponent();
      const ctx = withReferenceLineCanvas(comp);
      comp.chartObject.showReferenceLine = true;
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      vi.spyOn(ChartTool, "refLine").mockReturnValue(false);
      const drawSpy = vi.spyOn(ChartTool, "drawReferenceLine").mockImplementation(() => {});

      comp.onMove(moveEvent());

      expect(chartService.clearCanvas).toHaveBeenCalledWith(ctx);
      expect(drawSpy).not.toHaveBeenCalled();
   });

   it("should NOT touch the reference-line canvas when showReferenceLine is false", () => {
      const { comp, chartService } = createComponent();
      // referenceLineCanvas is left unset entirely — if the component tried to dereference
      // it here, this test would throw.
      comp.chartObject.showReferenceLine = false;
      vi.spyOn(ChartTool, "getTreeRegions").mockReturnValue([]);
      chartService.clearCanvas.mockClear();

      expect(() => comp.onMove(moveEvent())).not.toThrow();
      expect(chartService.clearCanvas).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 5: onMove — non-primary-button branch [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — onMove with a non-primary button", () => {
   it("should clear the tooltip when there is no active data tip", () => {
      const { comp } = createComponent();
      comp.dataTip = null;
      const emitted: any[] = [];
      comp.showTooltip.subscribe((v: any) => emitted.push(v));

      comp.onMove(moveEvent({ button: 2 }));

      expect(emitted).toEqual([null]);
   });

   it("should NOT clear the tooltip when a data tip is active", () => {
      const { comp } = createComponent();
      comp.dataTip = "Tip1";
      const emitted: any[] = [];
      comp.showTooltip.subscribe((v: any) => emitted.push(v));

      comp.onMove(moveEvent({ button: 2 }));

      expect(emitted).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 6: changeCursor0 [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — changeCursor0", () => {
   it("should update the host cursor via zone.run when it actually changes", () => {
      const { comp, zone } = createComponent();
      comp.hostCursor = "inherit";

      (comp as any).changeCursor0("pointer");

      expect(zone.run).toHaveBeenCalled();
      expect(comp.hostCursor).toBe("pointer");
   });

   it("should NOT invoke zone.run when the resolved cursor is unchanged", () => {
      const { comp, zone } = createComponent();
      comp.hostCursor = "pointer";

      (comp as any).changeCursor0("pointer");

      expect(zone.run).not.toHaveBeenCalled();
   });

   it("should force the cursor to 'inherit' while alt is held down, regardless of the requested cursor", () => {
      const { comp } = createComponent();
      comp.hostCursor = "pointer";
      (comp as any).altDown = true;

      (comp as any).changeCursor0("pointer");

      expect(comp.hostCursor).toBe("inherit");
   });
});

// ---------------------------------------------------------------------------
// Group 7: onAltDown / onAltUp [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — onAltDown / onAltUp", () => {
   it("should set altDown and immediately force the cursor to inherit", () => {
      const { comp } = createComponent();
      comp.hostCursor = "pointer";

      comp.onAltDown({} as any);

      expect((comp as any).altDown).toBe(true);
      expect(comp.hostCursor).toBe("inherit");
   });

   it("should clear altDown, restore the last requested cursor, and prevent default", () => {
      const { comp } = createComponent();
      comp.hostCursor = "pointer";
      (comp as any).cursorStyle = "pointer";
      comp.onAltDown({} as any);
      const event = { preventDefault: vi.fn() } as unknown as KeyboardEvent;

      comp.onAltUp(event);

      expect((comp as any).altDown).toBe(false);
      expect(comp.hostCursor).toBe("pointer");
      expect(event.preventDefault).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 8: getSingleClickRegions [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — getSingleClickRegions", () => {
   it("should filter out 'vo' regions when a text region is present", () => {
      const { comp } = createComponent({
         model: makeModel({ regionMetaDictionary: [{ areaType: "text" }, { areaType: "vo" }] as any })
      });
      const textRegion = { metaIdx: 0 } as any;
      const voRegion = { metaIdx: 1 } as any;

      const result = (comp as any).getSingleClickRegions([textRegion, voRegion]);

      expect(result).toEqual([textRegion]);
   });

   it("should take only the first region when there is no text region", () => {
      const { comp } = createComponent({
         model: makeModel({ regionMetaDictionary: [{ areaType: "vo" }] as any })
      });
      const region1 = { metaIdx: 0 } as any;
      const region2 = { metaIdx: 0 } as any;

      const result = (comp as any).getSingleClickRegions([region1, region2]);

      expect(result).toEqual([region1]);
   });

   it("should return an empty array unchanged when there are no candidate regions", () => {
      const { comp } = createComponent();

      const result = (comp as any).getSingleClickRegions([]);

      expect(result).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 9: selectIntersect [Risk 2]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — selectIntersect", () => {
   it("should include unselected regions that share a selectRow with a selected region", () => {
      const { comp } = createComponent();
      const selected = [{ selectRows: [1, 2] }] as any;
      const related = { selectRows: [2, 3] };
      const unrelated = { selectRows: [9] };
      const all = [related, unrelated, ...selected];

      const result = (comp as any).selectIntersect(selected, all);

      expect(result).toEqual([related, ...selected]);
   });

   it("should not duplicate an already-selected region even if it also matches by selectRows", () => {
      const { comp } = createComponent();
      const selected = [{ selectRows: [1] }] as any;
      const all = [...selected];

      const result = (comp as any).selectIntersect(selected, all);

      expect(result).toEqual(selected);
   });

   it("should return just the selected set when nothing else intersects", () => {
      const { comp } = createComponent();
      const selected = [{ selectRows: [1] }] as any;
      const unrelated = { selectRows: [2] };

      const result = (comp as any).selectIntersect(selected, [unrelated, ...selected]);

      expect(result).toEqual(selected);
   });
});

// ---------------------------------------------------------------------------
// Group 10: scrollContainerWidth / scrollContainerHeight [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — scrollContainerWidth / scrollContainerHeight", () => {
   it("should add the scrollbar width plus 1px when the tile bounds are taller than the layout bounds", () => {
      const { comp } = createComponent();
      comp.chartObject.layoutBounds = { x: 0, y: 0, width: 200, height: 100 } as any;
      comp.chartObject.bounds = { x: 0, y: 0, width: 200, height: 150 } as any;
      comp.scrollbarWidth = 15;

      expect(comp.scrollContainerWidth).toBe(200 + 15 + 1);
   });

   it("should NOT add scrollbar width when bounds fit within the layout bounds", () => {
      const { comp } = createComponent();
      comp.chartObject.layoutBounds = { x: 0, y: 0, width: 200, height: 100 } as any;
      comp.chartObject.bounds = { x: 0, y: 0, width: 200, height: 100 } as any;
      comp.scrollbarWidth = 15;

      expect(comp.scrollContainerWidth).toBe(200);
   });

   it("should add the scrollbar height plus 1px when the tile bounds are wider than the layout bounds", () => {
      const { comp } = createComponent();
      comp.chartObject.layoutBounds = { x: 0, y: 0, width: 200, height: 100 } as any;
      comp.chartObject.bounds = { x: 300, y: 0, width: 300, height: 100 } as any;
      comp.scrollbarWidth = 10;

      expect(comp.scrollContainerHeight).toBe(100 + 10 + 1);
   });

   it("should NOT add scrollbar height when bounds fit within the layout bounds", () => {
      const { comp } = createComponent();
      comp.chartObject.layoutBounds = { x: 0, y: 0, width: 200, height: 100 } as any;
      comp.chartObject.bounds = { x: 0, y: 0, width: 200, height: 100 } as any;
      comp.scrollbarWidth = 10;

      expect(comp.scrollContainerHeight).toBe(100);
   });
});

// ---------------------------------------------------------------------------
// Group 11: isMaxModeHidden [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — isMaxModeHidden", () => {
   it("should be true when the sheet is in max mode but this object is not the maximized one", () => {
      const { comp } = createComponent({
         model: makeModel({ sheetMaxMode: true, maxMode: false } as any)
      });
      expect(comp.isMaxModeHidden()).toBe(true);
   });

   it("should be false when this object is the maximized one", () => {
      const { comp } = createComponent({
         model: makeModel({ sheetMaxMode: true, maxMode: true } as any)
      });
      expect(comp.isMaxModeHidden()).toBe(false);
   });

   it("should be false when the sheet is not in max mode at all", () => {
      const { comp } = createComponent({
         model: makeModel({ sheetMaxMode: false } as any)
      });
      expect(comp.isMaxModeHidden()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 12: click (mobile double-tap) [Risk 1]
// ---------------------------------------------------------------------------

describe("ChartPlotArea — click (mobile double-tap lasso)", () => {
   beforeEach(() => vi.useFakeTimers());
   afterEach(() => { vi.clearAllTimers(); vi.useRealTimers(); });

   it("should enable selectionBoxWithTouch on mobile and reset it after 500ms", () => {
      const { comp } = createComponent();
      (comp as any).mobile = true;
      comp.selectionBoxWithTouch = false;

      comp.click({} as MouseEvent);
      expect(comp.selectionBoxWithTouch).toBe(true);

      vi.advanceTimersByTime(500);
      expect(comp.selectionBoxWithTouch).toBe(false);
   });

   it("should NOT toggle selectionBoxWithTouch when already enabled", () => {
      const { comp } = createComponent();
      (comp as any).mobile = true;
      comp.selectionBoxWithTouch = true;
      const setTimeoutSpy = vi.spyOn(globalThis, "setTimeout");

      comp.click({} as MouseEvent);

      expect(setTimeoutSpy).not.toHaveBeenCalled();
   });

   it("should do nothing on non-mobile devices", () => {
      const { comp } = createComponent();
      (comp as any).mobile = false;
      comp.selectionBoxWithTouch = false;

      comp.click({} as MouseEvent);

      expect(comp.selectionBoxWithTouch).toBe(false);
   });
});
