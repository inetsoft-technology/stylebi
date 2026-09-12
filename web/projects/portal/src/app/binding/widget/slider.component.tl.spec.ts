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
 * Slider — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 1] — ngOnInit/ngOnChanges: tick and label cache initialization
 *   Group 2 [Risk 2] — moveHandleHere: click-to-set value, emit sliderChanged/changeCompleted, clamp
 *   Group 3 [Risk 3] — mouseDown: document listener drag; value commit on mouseup; stale drag state
 *   Group 4 [Risk 3] — ngOnDestroy: cancel document mousemove/mouseup listeners
 *   Group 5 [Risk 2] — enabled guard: disabled slider must not register drag listeners
 *
 * HTTP: no HTTP — pure UI widget, no service calls
 *
 * Out of scope:
 *   getFractionDigits/fixNum/getJump — private tick-overlap helpers, covered via getTicks output
 *   dragNode — not used in binding widget Slider (viewer VSSlider has separate spec)
 */

import { NO_ERRORS_SCHEMA, Renderer2 } from "@angular/core";
import { fireEvent, render, screen } from "@testing-library/angular";
import { Slider } from "./slider.component";
import { SliderOptions } from "./slider-options";

// JSDOM does not implement canvas — stub getContext so tick overlap math does not throw
beforeAll(() => {
   vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue({
      font: "",
      measureText: (text: string) => ({ width: text.length * 6 })
   } as any);
});

function createModel(overrides: Partial<SliderOptions> = {}): SliderOptions {
   const model = new SliderOptions();
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

async function renderSlider(
   modelOverrides: Partial<SliderOptions> = {},
   options: { enabled?: boolean } = {}
) {
   const result = await render(Slider, {
      schemas: [NO_ERRORS_SCHEMA],
      componentProperties: {
         model: createModel(modelOverrides),
         enabled: options.enabled ?? true
      }
   });
   result.fixture.detectChanges();
   return result;
}

describe("Slider — tick and label initialization [Group 1, Risk 1]", () => {
   it("should build ticks for min/max range and format the current label", async () => {
      const { container } = await renderSlider({ min: 0, max: 10, increment: 1, value: 5 });

      expect(container.querySelectorAll(".slider-tick").length).toBeGreaterThan(0);
      expect(screen.getByText("5")).toBeInTheDocument();
      expect((container.querySelector(".slider-tracked") as HTMLElement).style.width).toBe("135px");
   });

   it("should omit min tick when minVisible is false", async () => {
      const { container } = await renderSlider({
         min: 0, max: 10, increment: 1, value: 5, minVisible: false
      });

      expect(container.querySelectorAll(".slider-tick").length).toBe(10);
   });
});

function clickTrack(container: HTMLElement, offsetX: number): void {
   const track = container.querySelector(".slider-track") as HTMLElement;
   const event = new MouseEvent("click", { bubbles: true });
   Object.defineProperty(event, "offsetX", { value: offsetX });
   track.dispatchEvent(event);
}

describe("Slider — moveHandleHere [Group 2, Risk 2]", () => {
   it("should set model value from click offset and emit sliderChanged + changeCompleted", async () => {
      const { fixture, container } = await renderSlider({ min: 0, max: 100, value: 0 });
      const changed = vi.fn();
      const completed = vi.fn();
      fixture.componentInstance.sliderChanged.subscribe(changed);
      fixture.componentInstance.changeCompleted.subscribe(completed);

      clickTrack(container, 135);

      expect(screen.getByText("50")).toBeInTheDocument();
      expect(changed).toHaveBeenCalledWith(50);
      expect(completed).toHaveBeenCalledWith(true);
      expect((container.querySelector(".slider-tracked") as HTMLElement).style.width).toBe("135px");
   });

   it("should clamp click position to line width", async () => {
      const { container } = await renderSlider({ min: 0, max: 100, value: 50 });

      clickTrack(container, 999);

      expect(screen.getByText("100")).toBeInTheDocument();
      expect((container.querySelector(".slider-tracked") as HTMLElement).style.width).toBe("270px");
   });
});

describe("Slider — mouseDown drag [Group 3, Risk 3]", () => {
   // 🔁 Regression-sensitive: drag uses document-level listeners; missing cleanup leaks handlers
   it("should update label during document mousemove and commit value on mouseup", async () => {
      const { fixture, container } = await renderSlider({ min: 0, max: 100, value: 50 });
      const { listeners } = installRendererSpy(fixture);
      const changed = vi.fn();
      fixture.componentInstance.sliderChanged.subscribe(changed);

      fireEvent.mouseDown(container.querySelector(".slider-handle")!, { button: 0, pageX: 100 });

      const mousemove = listeners.find(l => l.event === "mousemove")!.fn;
      const mouseup = listeners.find(l => l.event === "mouseup")!.fn;

      mousemove({ pageX: 235 } as MouseEvent);
      fixture.detectChanges();
      expect(changed).toHaveBeenCalled();
      expect(container.querySelector(".slider-handle.is-dragging")).toBeTruthy();

      mouseup({} as MouseEvent);
      fixture.detectChanges();
      expect(container.querySelector(".slider-handle.is-dragging")).toBeFalsy();
      expect(parseInt(screen.getByText(/^\d+$/).textContent ?? "0", 10)).toBeGreaterThan(50);
      expect(listeners[0].cancel).toHaveBeenCalled();
      expect(listeners[1].cancel).toHaveBeenCalled();
   });

   it("should ignore non-left-button mousedown", async () => {
      const { fixture, container } = await renderSlider({});
      const { listeners } = installRendererSpy(fixture);

      fireEvent.mouseDown(container.querySelector(".slider-handle")!, { button: 2, pageX: 100 });

      expect(listeners).toHaveLength(0);
      expect(container.querySelector(".slider-handle.is-dragging")).toBeFalsy();
   });
});

describe("Slider — ngOnDestroy cleanup [Group 4, Risk 3]", () => {
   it("should cancel document listeners on destroy", async () => {
      const { fixture, container } = await renderSlider({});
      const { listeners } = installRendererSpy(fixture);

      fireEvent.mouseDown(container.querySelector(".slider-handle")!, { button: 0, pageX: 50 });
      fixture.destroy();

      expect(listeners[0].cancel).toHaveBeenCalled();
      expect(listeners[1].cancel).toHaveBeenCalled();
   });
});

describe("Slider — enabled guard [Group 5, Risk 2]", () => {
   it("should not start drag when disabled", async () => {
      const { fixture, container } = await renderSlider({}, { enabled: false });
      const { listeners } = installRendererSpy(fixture);

      expect(container.querySelector(".slider.disabled")).toBeTruthy();
      fireEvent.mouseDown(container.querySelector(".slider-handle")!, { button: 0, pageX: 100 });

      expect(listeners).toHaveLength(0);
      expect(container.querySelector(".slider-handle.is-dragging")).toBeFalsy();
   });
});
