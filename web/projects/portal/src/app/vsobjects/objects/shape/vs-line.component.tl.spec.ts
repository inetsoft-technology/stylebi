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
 * VSLine - single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] - ngOnChanges: derived line style, arrow URLs, and line length
 *   Group 2 [Risk 2] - ngAfterViewChecked: double-line render gating
 *   Group 3 [Risk 2] - drawParallelLines: vertical, horizontal, and diagonal geometry branches
 *   Group 4 [Risk 1] - markerSuffix / lineRotationAngle: sanitized ids and indicator rotation
 *
 * Confirmed bugs: none
 *
 * Out of scope:
 *   Template rendering, annotations, and inherited AbstractVSObject interaction flows
 *
 * Mocking strategy:
 *   - direct class instantiation with lightweight service stubs
 *   - SVG line refs mocked as nativeElement coordinate holders
 */

import { NgZone } from "@angular/core";

import { TestUtils } from "../../../common/test/test-utils";
import { StyleConstants } from "../../../common/util/style-constants";
import { VSLineModel } from "../../model/vs-line-model";
import { VSLine } from "./vs-line.component";

afterEach(() => vi.restoreAllMocks());

function makeModel(overrides: Partial<VSLineModel> = {}): VSLineModel {
   const model = TestUtils.createMockVSLineModel("Line 1/A");
   model.objectFormat.width = 120;
   model.objectFormat.height = 80;
   return { ...model, ...overrides };
}

function makeSvgLineRef() {
   return {
      nativeElement: {
         x1: { baseVal: { value: 0 } },
         y1: { baseVal: { value: 0 } },
         x2: { baseVal: { value: 0 } },
         y2: { baseVal: { value: 0 } },
      },
   } as any;
}

function createComponent(modelOverrides: Partial<VSLineModel> = {}) {
   const viewsheetClient = { runtimeId: "runtime-1", sendEvent: vi.fn() };
   const comp = new VSLine(
      viewsheetClient as any,
      {} as any,
      new NgZone({}),
      { viewer: false, preview: false, composer: false, binding: false } as any,
      { isDataTip: vi.fn().mockReturnValue(false) } as any
   );
   comp.model = makeModel(modelOverrides);
   return { comp, viewsheetClient };
}

describe("VSLine - Group 1: ngOnChanges", () => {
   it("should compute line style, arrow URLs, and line length from the current model", () => {
      const { comp } = createComponent({
         startLeft: 0,
         startTop: 0,
         endLeft: 3,
         endTop: 4,
         lineStyle: StyleConstants.DASH_LINE,
         startAnchorStyle: StyleConstants.EMPTY_ARROW,
         endAnchorStyle: StyleConstants.FILLED_ARROW,
      });

      comp.ngOnChanges({ model: {} as any });

      expect(comp.doubleLine).toBe(false);
      expect(comp.lineWidth).toBe(1);
      expect(comp.lineDash).toBe("3, 4");
      expect(comp.arrowStartUrl).toBe("url(#empty-arrow-startruntime_1Line_1_A)");
      expect(comp.arrowEndUrl).toBe("url(#filled-arrow-endruntime_1Line_1_A)");
      expect(comp.lineLength).toBe(5);
   });
});

describe("VSLine - Group 2: ngAfterViewChecked", () => {
   it("should call drawParallelLines only when line refs exist and the line style is double", () => {
      const { comp } = createComponent({ lineStyle: StyleConstants.DOUBLE_LINE });
      comp.line1 = makeSvgLineRef();
      comp.line2 = makeSvgLineRef();
      const drawSpy = vi.spyOn(comp, "drawParallelLines").mockImplementation(() => {});

      try {
         comp.ngAfterViewChecked();
         expect(drawSpy).toHaveBeenCalledTimes(1);

         drawSpy.mockClear();
         comp.model = makeModel({ lineStyle: StyleConstants.THIN_LINE });
         comp.ngAfterViewChecked();

         expect(drawSpy).not.toHaveBeenCalled();
      }
      finally {
         drawSpy.mockRestore();
      }
   });
});

describe("VSLine - Group 3: drawParallelLines", () => {
   it("should offset vertical double lines on the x-axis by 1.5px", () => {
      const { comp } = createComponent({
         startLeft: 10,
         startTop: 5,
         endLeft: 10,
         endTop: 25,
         lineStyle: StyleConstants.DOUBLE_LINE,
      });
      comp.line1 = makeSvgLineRef();
      comp.line2 = makeSvgLineRef();

      comp.drawParallelLines();

      expect(comp.line1.nativeElement.x1.baseVal.value).toBe(11.5);
      expect(comp.line1.nativeElement.x2.baseVal.value).toBe(11.5);
      expect(comp.line2.nativeElement.x1.baseVal.value).toBe(8.5);
      expect(comp.line2.nativeElement.x2.baseVal.value).toBe(8.5);
   });

   it("should offset horizontal double lines on the y-axis by 1.5px", () => {
      const { comp } = createComponent({
         startLeft: 10,
         startTop: 5,
         endLeft: 30,
         endTop: 5,
         lineStyle: StyleConstants.DOUBLE_LINE,
      });
      comp.line1 = makeSvgLineRef();
      comp.line2 = makeSvgLineRef();

      comp.drawParallelLines();

      expect(comp.line1.nativeElement.y1.baseVal.value).toBe(6.5);
      expect(comp.line1.nativeElement.y2.baseVal.value).toBe(6.5);
      expect(comp.line2.nativeElement.y1.baseVal.value).toBe(3.5);
      expect(comp.line2.nativeElement.y2.baseVal.value).toBe(3.5);
   });

   it("should compute distinct diagonal endpoints for both parallel lines", () => {
      const { comp } = createComponent({
         startLeft: 0,
         startTop: 0,
         endLeft: 6,
         endTop: 8,
         lineStyle: StyleConstants.DOUBLE_LINE,
      });
      comp.line1 = makeSvgLineRef();
      comp.line2 = makeSvgLineRef();

      comp.drawParallelLines();

      expect(comp.line1.nativeElement.x1.baseVal.value).toBeCloseTo(1.2, 6);
      expect(comp.line1.nativeElement.y1.baseVal.value).toBeCloseTo(-0.9, 6);
      expect(comp.line1.nativeElement.x2.baseVal.value).toBeCloseTo(7.2, 6);
      expect(comp.line1.nativeElement.y2.baseVal.value).toBeCloseTo(7.1, 6);
      expect(comp.line2.nativeElement.x1.baseVal.value).toBeCloseTo(-1.2, 6);
      expect(comp.line2.nativeElement.y1.baseVal.value).toBeCloseTo(0.9, 6);
      expect(comp.line2.nativeElement.x2.baseVal.value).toBeCloseTo(4.8, 6);
      expect(comp.line2.nativeElement.y2.baseVal.value).toBeCloseTo(8.9, 6);
   });
});

describe("VSLine - Group 4: display helpers", () => {
   it("should sanitize runtime id and assembly name in markerSuffix", () => {
      const { comp } = createComponent();

      expect(comp.markerSuffix).toBe("runtime_1Line_1_A");
   });

   it("should compute the indicator rotation angle from the line endpoints", () => {
      const { comp } = createComponent({
         startLeft: 0,
         startTop: 0,
         endLeft: 0,
         endTop: 5,
      });

      expect(comp.lineRotationAngle).toBeCloseTo(Math.PI / 2, 6);
   });
});
