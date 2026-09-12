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
 * RangeSlider — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 1] — getRangeX/getRangeWidth/getCurrentLabel: range cache from model
 *   Group 2 [Risk 1] — getTicks: step-based tick positions and labels
 *   Group 3 [Risk 3] — left handle drag: selectStart clamped to min and selectEnd
 *   Group 4 [Risk 3] — right handle drag: selectEnd clamped to max and selectStart
 *   Group 5 [Risk 3] — middle handle drag: moves both ends preserving range width
 *   Group 6 [Risk 3] — ngOnDestroy: document listener cleanup
 *
 * HTTP: no HTTP — pure UI widget, no service calls
 *
 * Out of scope:
 *   getFractionDigits/fixNum/getJump — private tick helpers, covered via getTicks output
 */

import { NO_ERRORS_SCHEMA, Renderer2 } from "@angular/core";
import { fireEvent, render, screen } from "@testing-library/angular";
import { RangeSlider } from "./range-slider.component";
import { RangeSliderOptions } from "./range-slider-options";

function createModel(overrides: Partial<RangeSliderOptions> = {}): RangeSliderOptions {
   const model = new RangeSliderOptions();
   Object.assign(model, overrides);
   return model;
}

interface DocumentListener {
   event: string;
   fn: (event: MouseEvent) => void;
   cancel: ReturnType<typeof vi.fn>;
}

function installRendererSpy(fixture: { debugElement: { injector: { get: (token: unknown) => Renderer2 } } }): {
   listeners: DocumentListener[];
} {
   const listeners: DocumentListener[] = [];
   const renderer = fixture.debugElement.injector.get(Renderer2);
   vi.spyOn(renderer, "listen").mockImplementation((_target: unknown, event: string, fn: (event: MouseEvent) => void) => {
      const cancel = vi.fn();
      listeners.push({ event, fn, cancel });
      return cancel;
   });
   return { listeners };
}

function mockSliderGeometry(container: HTMLElement, width: number, rangeX: number): void {
   const slider = container.querySelector(".range-slider-container") as HTMLElement;
   const leftHandle = container.querySelector(".slider-handle") as HTMLElement;
   if(!slider || !leftHandle) {
      return;
   }

   const rect = { left: 0, top: 0, width, height: 20, right: width, bottom: 20, x: 0, y: 0, toJSON: () => ({}) };
   slider.getBoundingClientRect = () => rect as DOMRect;
   leftHandle.getBoundingClientRect = () =>
      ({ left: rangeX, top: 0, width: 10, height: 20, right: rangeX + 10, bottom: 20, x: rangeX, y: 0, toJSON: () => ({}) } as DOMRect);
}

async function renderRangeSlider(modelOverrides: Partial<RangeSliderOptions> = {}) {
   const model = createModel(modelOverrides);
   const result = await render(RangeSlider, {
      schemas: [NO_ERRORS_SCHEMA],
      componentProperties: { model }
   });
   result.fixture.detectChanges();
   const rangeX = ((model.selectStart - model.min) / (model.max - model.min)) * model.width;
   mockSliderGeometry(result.container as HTMLElement, model.width, rangeX);
   return result;
}

function getHandles(container: HTMLElement): HTMLElement[] {
   return Array.from(container.querySelectorAll(".slider-handle"));
}

describe("RangeSlider — range cache [Group 1, Risk 1]", () => {
   it("should compute rangeX, rangeWidth, and currentLabel from model", async () => {
      const { container } = await renderRangeSlider({
         width: 200, min: 0, max: 100, selectStart: 25, selectEnd: 75
      });

      const tracked = container.querySelector(".slider-tracked") as HTMLElement;
      expect(tracked.style.left).toBe("50px");
      expect(tracked.style.width).toBe("100px");
      expect(screen.getByText("25..75")).toBeInTheDocument();
   });
});

describe("RangeSlider — getTicks [Group 2, Risk 1]", () => {
   it("should produce tick positions spanning min to max", async () => {
      const { container } = await renderRangeSlider({ width: 270, min: 1, max: 31 });

      const ticks = container.querySelectorAll(".slider-tick");
      expect(ticks.length).toBeGreaterThan(1);
      expect((ticks[0] as HTMLElement).style.left).toBe("0px");
      expect(parseInt((ticks[ticks.length - 1] as HTMLElement).style.left, 10)).toBeGreaterThan(0);
   });
});

describe("RangeSlider — left handle drag [Group 3, Risk 3]", () => {
   it("should move selectStart and emit sliderChanged during drag", async () => {
      const { fixture, container } = await renderRangeSlider(
         { width: 270, min: 1, max: 30, selectStart: 10, selectEnd: 20 }
      );
      const { listeners } = installRendererSpy(fixture);
      const changed = vi.fn();
      fixture.componentInstance.sliderChanged.subscribe(changed);

      fireEvent.mouseDown(getHandles(container)[0], { button: 0, pageX: 100 });
      listeners.find(l => l.event === "mousemove")!.fn({ pageX: 50 } as MouseEvent);
      fixture.detectChanges();

      expect(screen.getByText(/\d+\.\.\d+/)).toBeInTheDocument();
      const label = screen.getByText(/\d+\.\.\d+/).textContent ?? "";
      const [startStr, endStr] = label.split("..");
      const start = parseInt(startStr, 10);
      const end = parseInt(endStr, 10);
      expect(start).toBeLessThan(10);
      expect(start).toBeGreaterThanOrEqual(1);
      expect(start).toBeLessThanOrEqual(end);
      expect(changed).toHaveBeenCalledWith([start, end]);
   });
});

describe("RangeSlider — right handle drag [Group 4, Risk 3]", () => {
   it("should move selectEnd toward max without crossing selectStart", async () => {
      const { fixture, container } = await renderRangeSlider(
         { width: 270, min: 1, max: 30, selectStart: 5, selectEnd: 10 }
      );
      const { listeners } = installRendererSpy(fixture);

      fireEvent.mouseDown(getHandles(container)[1], { button: 0, pageX: 100 });
      listeners.find(l => l.event === "mousemove")!.fn({ pageX: 250 } as MouseEvent);
      fixture.detectChanges();

      const label = screen.getByText(/\d+\.\.\d+/).textContent ?? "";
      const [startStr, endStr] = label.split("..");
      const start = parseInt(startStr, 10);
      const end = parseInt(endStr, 10);
      expect(end).toBeGreaterThan(10);
      expect(end).toBeLessThanOrEqual(30);
      expect(end).toBeGreaterThanOrEqual(start);
   });
});

describe("RangeSlider — middle handle drag [Group 5, Risk 3]", () => {
   it("should shift both selectStart and selectEnd preserving range width", async () => {
      const { fixture, container } = await renderRangeSlider(
         { width: 270, min: 1, max: 30, selectStart: 5, selectEnd: 15 }
      );
      const { listeners } = installRendererSpy(fixture);

      const initialLabel = screen.getByText(/\d+\.\.\d+/).textContent ?? "";
      const [, initialEndStr] = initialLabel.split("..");
      const initialStart = parseInt(initialLabel.split("..")[0], 10);
      const initialEnd = parseInt(initialEndStr, 10);
      const initialRange = initialEnd - initialStart;

      fireEvent.mouseDown(container.querySelector(".slider-tracked")!, { button: 0, pageX: 100 });
      listeners.find(l => l.event === "mousemove")!.fn({ pageX: 200 } as MouseEvent);
      fixture.detectChanges();

      const label = screen.getByText(/\d+\.\.\d+/).textContent ?? "";
      const [startStr, endStr] = label.split("..");
      const start = parseInt(startStr, 10);
      const end = parseInt(endStr, 10);
      expect(end - start).toBe(initialRange);
      expect(start).toBeGreaterThan(5);
   });
});

describe("RangeSlider — ngOnDestroy cleanup [Group 6, Risk 3]", () => {
   // 🔁 Regression-sensitive: document drag listeners must cancel on destroy to avoid leaks
   it("should cancel document listeners on destroy", async () => {
      const { fixture, container } = await renderRangeSlider({});
      const { listeners } = installRendererSpy(fixture);

      fireEvent.mouseDown(getHandles(container)[0], { button: 0, pageX: 50 });
      fixture.destroy();

      expect(listeners[0].cancel).toHaveBeenCalled();
      expect(listeners[1].cancel).toHaveBeenCalled();
   });

   it("should ignore non-left-button mousedown", async () => {
      const { fixture, container } = await renderRangeSlider({});
      const { listeners } = installRendererSpy(fixture);

      fireEvent.mouseDown(getHandles(container)[0], { button: 2, pageX: 50 });

      expect(listeners).toHaveLength(0);
   });
});
