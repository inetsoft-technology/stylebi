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
 * ChartLegendContainer - Pass 3: Display
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] - getResizeEdges: edge bitmask for all legend placements
 *   Group 2 [Risk 2] - createLegendResizeInfo: min/max/vertical bounds per legend side
 *   Group 3 [Risk 3] - buildOutlineRectangle: top/right/bottom/left/center classification
 *   Group 4 [Risk 2] - buildOutlineRectangle: viewer scale-container offset correction
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope:
 *   Lifecycle wiring and drag state transitions are covered in
 *   chart-legend-container.component.interaction.tl.spec.ts.
 *
 * Mocking strategy:
 *   - direct class instantiation with shared legend/container factories
 *   - private geometry helpers invoked explicitly with documented bypass comments
 */

import { GuiTool } from "../../common/util/gui-tool";
import {
   InteractArea,
   ResizeOptions,
} from "../../widget/resize/element-interact.directive";
import { LegendOption } from "../model/legend-option";
import {
   createComponent,
   makeChartContainer,
   makeLegendContainerElement,
   makeRect,
} from "./chart-legend-container.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("ChartLegendContainer - Group 1: getResizeEdges", () => {
   it("should enable the bottom edge for a top legend", () => {
      const { comp } = createComponent({ legendOption: LegendOption.TOP });
      expect(comp.getResizeEdges()).toBe(ResizeOptions.BOTTOM);
   });

   it("should enable the left edge for a right legend", () => {
      const { comp } = createComponent({ legendOption: LegendOption.RIGHT });
      expect(comp.getResizeEdges()).toBe(ResizeOptions.LEFT);
   });

   it("should enable the top edge for a bottom legend", () => {
      const { comp } = createComponent({ legendOption: LegendOption.BOTTOM });
      expect(comp.getResizeEdges()).toBe(ResizeOptions.TOP);
   });

   it("should enable the right edge for a left legend", () => {
      const { comp } = createComponent({ legendOption: LegendOption.LEFT });
      expect(comp.getResizeEdges()).toBe(ResizeOptions.RIGHT);
   });

   it("should enable both right and bottom edges for an in-place legend", () => {
      const { comp } = createComponent({ legendOption: LegendOption.IN_PLACE });
      expect(comp.getResizeEdges()).toBe(ResizeOptions.RIGHT | ResizeOptions.BOTTOM);
   });
});

describe("ChartLegendContainer - Group 2: createLegendResizeInfo", () => {
   // Bypass: createLegendResizeInfo is private pure computation with no public wrapper that
   // exposes its min/max/vertical contract directly, so the tests invoke it via `as any`.
   it("should compute top-legend resize bounds from chart height and legend min height", () => {
      const { comp } = createComponent({
         legendOption: LegendOption.TOP,
         chartRect: makeRect({ width: 200, height: 100 }),
      });

      expect((comp as any).createLegendResizeInfo(InteractArea.BOTTOM_EDGE)).toEqual(
         expect.objectContaining({
            minPosition: 8,
            maxPosition: 50,
            vertical: false,
            type: "legend",
            legend: comp.legend,
         })
      );
   });

   it("should compute right-legend resize bounds from chart width and legend min width", () => {
      const { comp } = createComponent({
         legendOption: LegendOption.RIGHT,
         chartRect: makeRect({ width: 200, height: 100 }),
      });

      expect((comp as any).createLegendResizeInfo(InteractArea.LEFT_EDGE)).toEqual(
         expect.objectContaining({
            minPosition: 100,
            maxPosition: 188,
            vertical: true,
         })
      );
   });

   it("should compute bottom-legend resize bounds from half chart height to remaining height", () => {
      const { comp } = createComponent({
         legendOption: LegendOption.BOTTOM,
         chartRect: makeRect({ width: 200, height: 100 }),
      });

      expect((comp as any).createLegendResizeInfo(InteractArea.TOP_EDGE)).toEqual(
         expect.objectContaining({
            minPosition: 50,
            maxPosition: 92,
            vertical: false,
         })
      );
   });

   it("should compute left-legend resize bounds from legend min width to half chart width", () => {
      const { comp } = createComponent({
         legendOption: LegendOption.LEFT,
         chartRect: makeRect({ width: 200, height: 100 }),
      });

      expect((comp as any).createLegendResizeInfo(InteractArea.RIGHT_EDGE)).toEqual(
         expect.objectContaining({
            minPosition: 12,
            maxPosition: 100,
            vertical: true,
         })
      );
   });
});

describe("ChartLegendContainer - Group 3: buildOutlineRectangle", () => {
   function primeDrag(comp: any) {
      comp.chartContainer = makeChartContainer(makeRect({
         left: 100,
         top: 200,
         width: 200,
         height: 100,
         right: 300,
         bottom: 300,
      }));
      comp.startMoveOrResize(
         { x: 10, y: 10, area: InteractArea.CENTER },
         makeLegendContainerElement(makeRect({
            left: 120,
            top: 220,
            width: 40,
            height: 20,
            right: 160,
            bottom: 240,
         })),
         true
      );
   }

   // Bypass: buildOutlineRectangle is a private geometry helper; direct calls are necessary
   // to assert exact edge classification and outline dimensions per branch.
   it("should classify a drag above the chart as TOP_EDGE", () => {
      const { comp } = createComponent();
      primeDrag(comp);
      vi.spyOn(GuiTool, "isIE").mockReturnValue(true);

      expect((comp as any).buildOutlineRectangle(10, -20)).toEqual({
         x: 20,
         y: -10,
         area: InteractArea.TOP_EDGE,
      });
      expect(comp.outlineTop).toBe(200);
      expect(comp.outlineLeft).toBe(100);
      expect(comp.outlineWidth).toBe(200);
      expect(comp.outlineHeight).toBe(10);
   });

   it("should classify a drag beyond the right side as RIGHT_EDGE", () => {
      const { comp } = createComponent();
      primeDrag(comp);
      vi.spyOn(GuiTool, "isIE").mockReturnValue(true);

      expect((comp as any).buildOutlineRectangle(170, 10)).toEqual({
         x: 180,
         y: 20,
         area: InteractArea.RIGHT_EDGE,
      });
      expect(comp.outlineLeft).toBe(260);
      expect(comp.outlineTop).toBe(200);
      expect(comp.outlineWidth).toBe(20);
      expect(comp.outlineHeight).toBe(100);
   });

   it("should classify a drag below the chart as BOTTOM_EDGE", () => {
      const { comp } = createComponent();
      primeDrag(comp);
      vi.spyOn(GuiTool, "isIE").mockReturnValue(true);

      expect((comp as any).buildOutlineRectangle(10, 75)).toEqual({
         x: 20,
         y: 85,
         area: InteractArea.BOTTOM_EDGE,
      });
      expect(comp.outlineTop).toBe(290);
      expect(comp.outlineLeft).toBe(100);
      expect(comp.outlineWidth).toBe(200);
      expect(comp.outlineHeight).toBe(10);
   });

   it("should classify a drag beyond the left side as LEFT_EDGE", () => {
      const { comp } = createComponent();
      primeDrag(comp);
      vi.spyOn(GuiTool, "isIE").mockReturnValue(true);

      expect((comp as any).buildOutlineRectangle(-20, 10)).toEqual({
         x: -10,
         y: 20,
         area: InteractArea.LEFT_EDGE,
      });
      expect(comp.outlineTop).toBe(200);
      expect(comp.outlineLeft).toBe(100);
      expect(comp.outlineWidth).toBe(20);
      expect(comp.outlineHeight).toBe(100);
   });

   it("should keep a drag inside the chart as CENTER and use legend dimensions", () => {
      const { comp } = createComponent();
      primeDrag(comp);
      vi.spyOn(GuiTool, "isIE").mockReturnValue(true);

      expect((comp as any).buildOutlineRectangle(30, 20)).toEqual({
         x: 40,
         y: 30,
         area: InteractArea.CENTER,
      });
      expect(comp.outlineLeft).toBe(140);
      expect(comp.outlineTop).toBe(230);
      expect(comp.outlineWidth).toBe(40);
      expect(comp.outlineHeight).toBe(20);
   });
});

describe("ChartLegendContainer - Group 4: viewer offset adjustment", () => {
   it("should subtract the scale-container offset for viewer mode on non-IE browsers", () => {
      const scaleContainer = document.createElement("div");
      vi.spyOn(scaleContainer, "getBoundingClientRect").mockReturnValue(
         makeRect({ left: 50, top: 70 })
      );
      const { comp } = createComponent({
         contextProvider: { viewer: true, preview: false },
      });
      const closestSpy = vi.spyOn(GuiTool, "closest").mockReturnValue(scaleContainer);
      const ieSpy = vi.spyOn(GuiTool, "isIE").mockReturnValue(false);

      try {
         comp.ngOnChanges(null);
         comp.chartContainer = makeChartContainer(makeRect({
            left: 100,
            top: 200,
            width: 200,
            height: 100,
            right: 300,
            bottom: 300,
         }));
         comp.startMoveOrResize(
            { x: 10, y: 10, area: InteractArea.CENTER },
            makeLegendContainerElement(makeRect({
               left: 120,
               top: 220,
               width: 40,
               height: 20,
               right: 160,
               bottom: 240,
            })),
            true
         );

         const info = (comp as any).buildOutlineRectangle(10, -20);

         expect(info.area).toBe(InteractArea.TOP_EDGE);
         expect(comp.outlineLeft).toBe(50);
         expect(comp.outlineTop).toBe(130);
         expect(closestSpy).toHaveBeenCalled();
      }
      finally {
         closestSpy.mockRestore();
         ieSpy.mockRestore();
      }
   });

   it("should NOT apply the scale-container offset on IE, even in viewer mode", () => {
      const scaleContainer = document.createElement("div");
      vi.spyOn(scaleContainer, "getBoundingClientRect").mockReturnValue(
         makeRect({ left: 50, top: 70 })
      );
      const { comp } = createComponent({
         contextProvider: { viewer: true, preview: false },
      });
      const closestSpy = vi.spyOn(GuiTool, "closest").mockReturnValue(scaleContainer);
      const ieSpy = vi.spyOn(GuiTool, "isIE").mockReturnValue(true);

      try {
         comp.ngOnChanges(null);
         comp.chartContainer = makeChartContainer(makeRect({
            left: 100, top: 200, width: 200, height: 100, right: 300, bottom: 300,
         }));
         comp.startMoveOrResize(
            { x: 10, y: 10, area: InteractArea.CENTER },
            makeLegendContainerElement(makeRect({
               left: 120, top: 220, width: 40, height: 20, right: 160, bottom: 240,
            })),
            true
         );

         (comp as any).buildOutlineRectangle(10, -20);

         expect(comp.outlineLeft).toBe(100);
         expect(comp.outlineTop).toBe(200);
      }
      finally {
         closestSpy.mockRestore();
         ieSpy.mockRestore();
      }
   });

   it("should NOT apply an offset in viewer mode when no scale-container was found", () => {
      const { comp } = createComponent({
         contextProvider: { viewer: true, preview: false },
      });
      const closestSpy = vi.spyOn(GuiTool, "closest").mockReturnValue(null);
      const ieSpy = vi.spyOn(GuiTool, "isIE").mockReturnValue(false);

      try {
         comp.ngOnChanges(null);
         comp.chartContainer = makeChartContainer(makeRect({
            left: 100, top: 200, width: 200, height: 100, right: 300, bottom: 300,
         }));
         comp.startMoveOrResize(
            { x: 10, y: 10, area: InteractArea.CENTER },
            makeLegendContainerElement(makeRect({
               left: 120, top: 220, width: 40, height: 20, right: 160, bottom: 240,
            })),
            true
         );

         (comp as any).buildOutlineRectangle(10, -20);

         expect(comp.outlineLeft).toBe(100);
         expect(comp.outlineTop).toBe(200);
      }
      finally {
         closestSpy.mockRestore();
         ieSpy.mockRestore();
      }
   });
});
