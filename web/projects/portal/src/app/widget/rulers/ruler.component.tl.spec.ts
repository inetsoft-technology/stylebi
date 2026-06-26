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
 * Ruler — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — guide/ruler position getters: horizontal vs vertical branching
 *   Group 2 [Risk 2] — scale setter: normalizes to 2 decimal places
 *   Group 3 [Risk 1] — preventMouseEvents; ngAfterViewInit triggers updateRulerSize
 *
 * Out of scope: updateRulerSize canvas tick drawing (requires layout + canvas context mocking)
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ChangeDetectorRef } from "@angular/core";
import { render } from "@testing-library/angular";
import { Ruler } from "./ruler.component";

async function renderRuler(props: Record<string, unknown> = {}) {
   const detectChanges = vi.fn();
   const result = await render(Ruler, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: ChangeDetectorRef, useValue: { detectChanges } },
      ],
      componentProperties: {
         horizontal: true,
         showGuides: true,
         guideTop: 10,
         guideLeft: 20,
         guideWidth: 100,
         guideHeight: 50,
         top: 5,
         left: 15,
         bottom: 25,
         right: 35,
         ...props,
      },
   });
   return { comp: result.fixture.componentInstance, detectChanges };
}

describe("Ruler — guide and ruler position getters — horizontal [Group 1, Risk 2]", () => {

   it("should map guide styles for horizontal orientation", async () => {
      const { comp } = await renderRuler({ horizontal: true });

      expect(comp.guideTopStyle).toBe(0);
      expect(comp.guideLeftStyle).toBe(20);
      expect(comp.guideWidthStyle).toBe("100px");
      expect(comp.guideHeightStyle).toBe("100%");
      expect(comp.rulerTopStyle).toBe(5);
      expect(comp.rulerLeftStyle).toBe(33);
      expect(comp.rulerBottomStyle).toBe("auto");
      expect(comp.rulerRightStyle).toBe("35px");
   });

   it("should map guide styles for vertical orientation", async () => {
      const { comp } = await renderRuler({ horizontal: false });

      expect(comp.guideTopStyle).toBe(10);
      expect(comp.guideLeftStyle).toBe(0);
      expect(comp.guideWidthStyle).toBe("100%");
      expect(comp.guideHeightStyle).toBe("50px");
      expect(comp.rulerTopStyle).toBe(23);
      expect(comp.rulerLeftStyle).toBe(15);
      expect(comp.rulerBottomStyle).toBe("25px");
      expect(comp.rulerRightStyle).toBe("auto");
   });
});

describe("Ruler — scale setter — normalization [Group 2, Risk 2]", () => {

   it("should round scale to two decimal places via setter", async () => {
      const { comp } = await renderRuler();
      const updateSpy = vi.spyOn(comp, "updateRulerSize").mockImplementation(() => {});

      comp.scale = 1.23456;

      expect(comp["_scale"]).toBe(1.23);
      expect(updateSpy).toHaveBeenCalled();
   });
});

describe("Ruler — lifecycle and event guard [Group 3, Risk 1]", () => {

   it("should call updateRulerSize on ngAfterViewInit", async () => {
      const { comp } = await renderRuler();
      const updateSpy = vi.spyOn(comp, "updateRulerSize").mockImplementation(() => {});

      comp.ngAfterViewInit();

      expect(updateSpy).toHaveBeenCalled();
   });

   it("should prevent default on preventMouseEvents", async () => {
      const { comp } = await renderRuler();
      const event = { preventDefault: vi.fn() };

      comp.preventMouseEvents(event);

      expect(event.preventDefault).toHaveBeenCalled();
   });

   it("should bail out of updateRulerSize when offsetParent is null", async () => {
      const { comp } = await renderRuler();
      Object.defineProperty(comp.ruler.nativeElement, "offsetParent", { value: null });

      expect(() => comp.updateRulerSize()).not.toThrow();
   });
});
