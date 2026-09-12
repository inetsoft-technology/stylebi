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
 * VSRectangle - configurable drop shadow.
 *
 * Risk-first coverage:
 *   Group 1 - ngOnChanges: the derived css box-shadow for the configured settings
 *   Group 2 - round corner clamping (pre-existing behavior, guarded against regression)
 *
 * The shadow used to be a hardcoded scss rule; it is now computed from
 * model.shadowInfo, so the defaults must still approximate the old look.
 *
 * Mocking strategy:
 *   - direct class instantiation with lightweight service stubs; no DOM or HTTP
 *     interception is needed, so this is a plain spec rather than a .tl one
 */
import { NgZone } from "@angular/core";

import { TestUtils } from "../../../common/test/test-utils";
import { ShapeShadowUtil } from "../../../common/util/shape-shadow-util";
import { VSRectangleModel } from "../../model/vs-rectangle-model";
import { VSRectangle } from "./vs-rectangle.component";

function createComponent(modelOverrides: Partial<VSRectangleModel> = {}) {
   const viewsheetClient = { runtimeId: "runtime-1", sendEvent: vi.fn() };
   const comp = new VSRectangle(
      viewsheetClient as any,
      {} as any,
      new NgZone({}),
      { viewer: false, preview: false, composer: false, binding: false } as any,
      { isDataTip: vi.fn().mockReturnValue(false) } as any
   );
   const model = TestUtils.createMockVSRectangleModel("Rectangle1");
   model.objectFormat.width = 200;
   model.objectFormat.height = 100;
   comp.model = { ...model, ...modelOverrides };
   return { comp };
}

describe("VSRectangle - Group 1: shadow css", () => {
   it("should not set a box-shadow when the shadow is off", () => {
      const { comp } = createComponent({ shadow: false });

      comp.ngOnChanges({ model: {} as any });

      expect(comp.shadowCss).toBeNull();
   });

   it("should approximate the previously hardcoded shadow with the defaults", () => {
      const { comp } = createComponent({ shadow: true });

      comp.ngOnChanges({ model: {} as any });

      // was scss: box-shadow: 5px 5px 3px 3px rgba(0,0,0,0.3)
      expect(comp.shadowCss).toBe("5px 5px 6px rgba(0, 0, 0, 0.3)");
   });

   it("should honor the configured color, opacity, distance and blur", () => {
      const { comp } = createComponent({
         shadow: true,
         shadowInfo: {
            color: "#ff8800", alpha: 50, direction: "E", distance: 10, blur: 2
         }
      });

      comp.ngOnChanges({ model: {} as any });

      expect(comp.shadowCss).toBe("10px 0px 2px rgba(255, 136, 0, 0.5)");
   });

   it("should cast the shadow up and to the left for a northwest direction", () => {
      const { comp } = createComponent({
         shadow: true,
         shadowInfo: {
            color: "#000000", alpha: 100, direction: "NW", distance: 4, blur: 0
         }
      });

      comp.ngOnChanges({ model: {} as any });

      expect(comp.shadowCss).toBe("-4px -4px 0px rgba(0, 0, 0, 1)");
   });
});

describe("ShapeShadowUtil", () => {
   it("should map every direction to the expected signed offsets", () => {
      const expected: {[dir: string]: [number, number]} = {
         N: [0, -3], NE: [3, -3], E: [3, 0], SE: [3, 3],
         S: [0, 3], SW: [-3, 3], W: [-3, 0], NW: [-3, -3]
      };

      for(const dir of ShapeShadowUtil.DIRECTIONS) {
         const shadow = { color: "#000000", alpha: 100, direction: dir,
                          distance: 3, blur: 0 };
         expect([ShapeShadowUtil.getOffsetX(shadow),
                 ShapeShadowUtil.getOffsetY(shadow)]).toEqual(expected[dir]);
      }
   });

   it("should fall back to black for a malformed color", () => {
      const shadow = { color: "nonsense", alpha: 100, direction: "SE",
                       distance: 1, blur: 0 };
      expect(ShapeShadowUtil.getRgba(shadow)).toBe("rgba(0, 0, 0, 1)");
   });

   it("should expand the #rgb shorthand", () => {
      const shadow = { color: "#f00", alpha: 100, direction: "SE",
                       distance: 1, blur: 0 };
      expect(ShapeShadowUtil.getRgba(shadow)).toBe("rgba(255, 0, 0, 1)");
   });
});

describe("VSRectangle - Group 2: round corner", () => {
   it("should clamp the round corner to the smaller of width and height", () => {
      const { comp } = createComponent({ roundCornerValue: 500 });
      comp.model.objectFormat.width = 200;
      comp.model.objectFormat.height = 60;

      comp.ngOnChanges({ model: {} as any });

      expect(comp.roundCornerValue).toBe(60);
   });
});
