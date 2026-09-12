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
 * ChartLegendContainer - Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] - ngOnInit / ngOnChanges: scale-container lookup and legend background sync
 *   Group 2 [Risk 3] - startMoveOrResize: disabled guard, resize-start emit, drag-state priming
 *   Group 3 [Risk 2] - endMoveOrResize: resize reset, drag-complete emit, state cleanup
 *   Group 4 [Risk 2] - onMove / draggedEnoughDistance: Manhattan threshold and outline trigger
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope:
 *   getResizeEdges, createLegendResizeInfo, and buildOutlineRectangle geometry classifications
 *   are covered in chart-legend-container.component.display.tl.spec.ts.
 *
 * Mocking strategy:
 *   - direct class instantiation with lightweight context-provider data
 *   - DOM rectangles and chart/legend elements supplied by shared test helpers
 */

import { GuiTool } from "../../common/util/gui-tool";
import { InteractArea } from "../../widget/resize/element-interact.directive";
import {
   createComponent,
   makeLegend,
   makeLegendContainerElement,
} from "./chart-legend-container.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("ChartLegendContainer - Group 1: ngOnInit / ngOnChanges", () => {
   it("should delegate ngOnInit to ngOnChanges(null)", () => {
      const { comp } = createComponent();
      // Suppress the real ngOnChanges body because this test only verifies the lifecycle
      // delegation contract from ngOnInit to ngOnChanges(null).
      const changesSpy = vi.spyOn(comp, "ngOnChanges").mockImplementation(() => {});

      try {
         comp.ngOnInit();
         expect(changesSpy).toHaveBeenCalledWith(null);
      }
      finally {
         changesSpy.mockRestore();
      }
   });

   it("should refresh the scaleContainer when chartContainer changes", () => {
      const { comp } = createComponent();
      const scaleContainer = document.createElement("div");
      const closestSpy = vi.spyOn(GuiTool, "closest").mockReturnValue(scaleContainer);

      try {
         comp.ngOnChanges({ chartContainer: {} as any });
         expect(closestSpy).toHaveBeenCalledWith(comp.chartContainer, ".scale-container");
         // Bypass: scaleContainer is private and has no public accessor; inspect it directly
         // to verify that ngOnChanges cached the closest scale-container element.
         expect((comp as any).scaleContainer).toBe(scaleContainer);
      }
      finally {
         closestSpy.mockRestore();
      }
   });

   it("should derive the background from the last legend object when the legend changes", () => {
      const { comp } = createComponent({
         legend: makeLegend({
            legendObjects: [{ background: "#111" } as any, { background: "#222" } as any],
         }),
      });

      comp.ngOnChanges({ legend: {} as any });

      expect(comp.background).toBe("#222");
   });
});

describe("ChartLegendContainer - Group 2: startMoveOrResize", () => {
   it("should return early when both move and resize are disabled", () => {
      const { comp } = createComponent({ moveEnable: false, resizeEnable: false });
      const resizeStart = vi.fn();
      comp.onLegendResizeStart.subscribe(resizeStart);

      comp.startMoveOrResize({ x: 10, y: 20, area: InteractArea.RIGHT_EDGE }, makeLegendContainerElement(), false);

      expect(comp.isMoving).toBe(false);
      expect(comp.isResizing).toBe(false);
      expect(resizeStart).not.toHaveBeenCalled();
   });

   it("should enter resizing mode and emit resize info when started from a resize edge", () => {
      const { comp } = createComponent();
      const emitted: any[] = [];
      comp.onLegendResizeStart.subscribe(v => emitted.push(v));

      comp.startMoveOrResize(
         { x: 10, y: 20, area: InteractArea.RIGHT_EDGE },
         makeLegendContainerElement(),
         false
      );

      expect(comp.isResizing).toBe(true);
      expect(emitted).toEqual([
         expect.objectContaining({ type: "legend", vertical: true, legend: comp.legend }),
      ]);
   });

   it("should prime drag state from the legend and chart bounds when moving starts", () => {
      const { comp } = createComponent();
      const legendContainer = makeLegendContainerElement();

      comp.startMoveOrResize(
         { x: 11, y: 22, area: InteractArea.CENTER },
         legendContainer,
         true
      );

      expect(comp.isMoving).toBe(true);
      // Bypass: these drag bookkeeping fields are private with no public projection, so
      // assert them directly to verify startMoveOrResize primed the drag session correctly.
      expect((comp as any).legendBounds).toEqual(legendContainer.getBoundingClientRect());
      expect((comp as any).chartRect).toEqual(comp.chartContainer.getBoundingClientRect());
      expect((comp as any).eventXdown).toBe(11);
      expect((comp as any).eventYdown).toBe(22);
   });
});

describe("ChartLegendContainer - Group 3: endMoveOrResize", () => {
   it("should leave resize mode without emitting a move when a resize finishes", () => {
      const { comp } = createComponent();
      comp.isResizing = true;
      const moveEmitted = vi.fn();
      comp.onLegendMove.subscribe(moveEmitted);

      comp.endMoveOrResize({ x: 0, y: 0, area: InteractArea.RIGHT_EDGE });

      expect(comp.isResizing).toBe(false);
      expect(moveEmitted).not.toHaveBeenCalled();
   });

   it("should emit a built move outline when dragging ended far enough in the center area", () => {
      const { comp } = createComponent();
      const emitted: any[] = [];
      comp.onLegendMove.subscribe(v => emitted.push(v));
      comp.startMoveOrResize(
         { x: 10, y: 10, area: InteractArea.CENTER },
         makeLegendContainerElement(),
         true
      );
      comp.onMove({ x: 20, y: 20, clientX: 40, clientY: 50 } as MouseEvent);
      const outlineSpy = vi.spyOn(comp as any, "buildOutlineRectangle").mockReturnValue({
         x: 7,
         y: 8,
         area: InteractArea.LEFT_EDGE,
      });

      try {
         comp.endMoveOrResize({ x: 30, y: 40, area: InteractArea.CENTER });
         expect(outlineSpy).toHaveBeenCalledWith(30, 40);
         expect(emitted).toEqual([{ x: 7, y: 8, area: InteractArea.LEFT_EDGE }]);
         expect(comp.isMoving).toBe(false);
         // Bypass: draggedDistance is private; check the internal reset because there is no
         // public API exposing whether the completed drag state was cleared.
         expect((comp as any).draggedDistance).toBe(0);
      }
      finally {
         outlineSpy.mockRestore();
      }
   });

   it("should reset drag state without emitting when movement did not cross the threshold", () => {
      const { comp } = createComponent();
      const moveEmitted = vi.fn();
      comp.onLegendMove.subscribe(moveEmitted);
      comp.startMoveOrResize(
         { x: 10, y: 10, area: InteractArea.CENTER },
         makeLegendContainerElement(),
         true
      );

      comp.endMoveOrResize({ x: 11, y: 11, area: InteractArea.CENTER });

      expect(moveEmitted).not.toHaveBeenCalled();
      expect(comp.isMoving).toBe(false);
      // Bypass: draggedDistance is private; direct inspection verifies cleanup on the
      // below-threshold path as well.
      expect((comp as any).draggedDistance).toBe(0);
   });

   it("should NOT emit a move when the drag ended outside the center area, even if dragged far enough", () => {
      const { comp } = createComponent();
      const moveEmitted = vi.fn();
      comp.onLegendMove.subscribe(moveEmitted);
      comp.startMoveOrResize(
         { x: 10, y: 10, area: InteractArea.CENTER },
         makeLegendContainerElement(),
         true
      );
      comp.onMove({ x: 20, y: 20, clientX: 40, clientY: 50 } as MouseEvent);

      comp.endMoveOrResize({ x: 30, y: 40, area: InteractArea.RIGHT_EDGE });

      expect(moveEmitted).not.toHaveBeenCalled();
      expect(comp.isMoving).toBe(false);
   });
});

describe("ChartLegendContainer - Group 4: onMove / draggedEnoughDistance", () => {
   it("should report false before the drag reaches the 3-pixel threshold", () => {
      const { comp } = createComponent();
      comp.startMoveOrResize(
         { x: 10, y: 10, area: InteractArea.CENTER },
         makeLegendContainerElement(),
         true
      );

      comp.onMove({ x: 11, y: 11, clientX: 21, clientY: 21 } as MouseEvent);

      expect(comp.draggedEnoughDistance()).toBe(false);
   });

   it("should build an outline once the Manhattan drag distance reaches the threshold", () => {
      const { comp } = createComponent();
      comp.startMoveOrResize(
         { x: 10, y: 10, area: InteractArea.CENTER },
         makeLegendContainerElement(),
         true
      );
      const outlineSpy = vi.spyOn(comp as any, "buildOutlineRectangle").mockImplementation(() => ({
         x: 1,
         y: 2,
         area: InteractArea.CENTER,
      }));

      try {
         comp.onMove({ x: 14, y: 10, clientX: 44, clientY: 55 } as MouseEvent);
         expect(comp.draggedEnoughDistance()).toBe(true);
         expect(outlineSpy).toHaveBeenCalledWith(44, 55);
      }
      finally {
         outlineSpy.mockRestore();
      }
   });
});
