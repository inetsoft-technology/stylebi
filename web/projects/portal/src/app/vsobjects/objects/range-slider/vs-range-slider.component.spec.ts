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
 * Bug #77917: a RefreshVSObjectCommand for an earlier range that arrives while the user is
 * still changing the range must not drop the user's latest range.
 *
 * Uses the real DebounceService under fake timers. A refresh is applied the way the viewer
 * does it: a new model object through the model setter, then ngOnChanges().
 * 5 labels, maxRangeBarWidth=200 -> widthBetweenTicks=47.75.
 */

import { SimpleChange } from "@angular/core";
import { GuiTool } from "../../../common/util/gui-tool";
import { DebounceService } from "../../../widget/services/debounce.service";
import { VSRangeSliderModel } from "../../model/vs-range-slider-model";
import { NavigationKeys } from "../navigation-keys";
import {
   createVSRangeSlider,
   makeVSRangeSliderModel,
   stubViewChildRefs,
   VSRangeSliderTestContext,
} from "./vs-range-slider.component.test-helpers";

const TICK = 47.75;

describe("VSRangeSlider – refresh while the range is being changed (Bug #77917)", () => {
   let ctx: VSRangeSliderTestContext;
   let pageX: ReturnType<typeof vi.spyOn>;

   function create(overrides: Partial<VSRangeSliderModel> = {}): void {
      ctx = createVSRangeSlider({ selectStart: 0, selectEnd: 4, ...overrides });
      const zone: any = { runOutsideAngular: (fn: () => any) => fn() };
      (ctx.comp as any).debounceService = new DebounceService(zone);
      stubViewChildRefs(ctx.comp);
   }

   function refresh(overrides: Partial<VSRangeSliderModel>): void {
      const old = ctx.comp.model;
      const model = makeVSRangeSliderModel(overrides);
      ctx.comp.model = model;
      ctx.comp.ngOnChanges({ model: new SimpleChange(old, model, false) } as any);
   }

   function sent(): string[] {
      return ctx.viewsheetClient.sendEvent.mock.calls
         .filter((call: any[]) => call[0].startsWith("/events/selectionList/update/"))
         .map((call: any[]) => `${call[1].selectStart}-${call[1].selectEnd}`);
   }

   function key(k: NavigationKeys): void {
      (ctx.comp as any).navigate(k);
   }

   function press(handle: number, x: number): void {
      pageX.mockReturnValue(x);
      ctx.comp.mouseDown(new MouseEvent("mousedown") as any, handle);
   }

   function move(x: number): void {
      pageX.mockReturnValue(x);
      ctx.comp.mouseMove(new MouseEvent("mousemove") as any);
   }

   function release(): void {
      ctx.comp.mouseUp(new MouseEvent("mouseup") as any);
   }

   function shown(): string {
      return `${ctx.comp.model.selectStart}-${ctx.comp.model.selectEnd}`;
   }

   beforeEach(() => {
      vi.useFakeTimers();
      vi.spyOn(GuiTool, "measureText").mockReturnValue(10);
      vi.spyOn(GuiTool, "isButton1" as any).mockReturnValue(true);
      pageX = vi.spyOn(GuiTool, "pageX" as any).mockReturnValue(0);
   });

   afterEach(() => {
      vi.useRealTimers();
      vi.restoreAllMocks();
   });

   describe("keyboard", () => {
      beforeEach(() => {
         create();
         ctx.comp.mouseHandle = ctx.comp.handleType.Left;
      });

      it("sends the last key range when the refresh of an earlier send lands in the debounce window", () => {
         key(NavigationKeys.RIGHT);
         vi.advanceTimersByTime(300);
         expect(sent()).toEqual(["1-4"]);

         key(NavigationKeys.RIGHT);
         expect(shown()).toBe("2-4");

         vi.advanceTimersByTime(100);
         refresh({ selectStart: 1, selectEnd: 4 });
         vi.advanceTimersByTime(300);

         expect(sent()).toEqual(["1-4", "2-4"]);
      });

      it("continues the next key from the local range after a refresh in the debounce window", () => {
         key(NavigationKeys.RIGHT);
         vi.advanceTimersByTime(300);
         key(NavigationKeys.RIGHT);
         vi.advanceTimersByTime(100);
         refresh({ selectStart: 1, selectEnd: 4 });
         expect(shown()).toBe("2-4");
         expect(ctx.comp.leftHandlePosition).toBeCloseTo(2 * TICK);

         key(NavigationKeys.RIGHT);
         vi.advanceTimersByTime(300);

         expect(sent()).toEqual(["1-4", "3-4"]);
         expect(shown()).toBe("3-4");
      });

      it("drops a pending key range when a refresh changes the labels", () => {
         key(NavigationKeys.RIGHT);
         vi.advanceTimersByTime(100);
         refresh({
            labels: ["A", "B", "C"], values: ["10", "20", "30"], selectStart: 0, selectEnd: 2,
         });
         expect(shown()).toBe("0-2");
         expect(ctx.comp.rightHandlePosition).toBeCloseTo(191);

         vi.advanceTimersByTime(300);
         expect(sent()).toEqual([]);

         key(NavigationKeys.RIGHT);
         vi.advanceTimersByTime(300);
         expect(sent()).toEqual(["1-2"]);
      });

      it("does not send a pending key range after the slider is destroyed", () => {
         key(NavigationKeys.RIGHT);
         ctx.comp.ngOnDestroy();
         vi.advanceTimersByTime(300);

         expect(sent()).toEqual([]);
      });

      it("does not send a pending key range after a newer mouse range", () => {
         key(NavigationKeys.RIGHT);
         expect(shown()).toBe("1-4");

         // drag the left handle from 1 to 3 before the key debounce fires. The pressed key
         // range is sent when the drag starts, never after the drag.
         press(ctx.comp.handleType.Left, 100);
         move(100 + 2 * TICK);
         release();
         expect(sent()).toEqual(["1-4", "3-4"]);

         vi.advanceTimersByTime(300);
         expect(sent()).toEqual(["1-4", "3-4"]);
      });

      it("stores the key range when the slider is not submitted on change", () => {
         create({ submitOnChange: false });
         ctx.comp.mouseHandle = ctx.comp.handleType.Left;

         key(NavigationKeys.RIGHT);
         vi.advanceTimersByTime(100);
         refresh({ selectStart: 0, selectEnd: 4, submitOnChange: false });
         vi.advanceTimersByTime(300);

         expect(ctx.comp._unappliedSelections).toEqual({ start: 1, end: 4 });
         expect(sent()).toEqual([]);
      });
   });

   describe("mouse drag", () => {
      beforeEach(() => create());

      it("sends the release range when the refresh of the previous drag arrives mid-drag", () => {
         press(ctx.comp.handleType.Left, 100);
         move(100 + TICK);
         release();
         expect(sent()).toEqual(["1-4"]);

         press(ctx.comp.handleType.Left, 200);
         move(200 + TICK);
         expect(shown()).toBe("2-4");

         refresh({ selectStart: 1, selectEnd: 4 });
         expect(shown()).toBe("2-4");
         expect(ctx.comp.leftHandlePosition).toBeCloseTo(2 * TICK);

         move(200 + 2 * TICK);
         release();

         expect(sent()).toEqual(["1-4", "3-4"]);
         expect(shown()).toBe("3-4");
         expect(ctx.comp.leftHandlePosition).toBeCloseTo(3 * TICK);
      });

      it("keeps the refreshed range when the drag ends where it started", () => {
         press(ctx.comp.handleType.Left, 100);
         move(100 + TICK);
         refresh({ selectStart: 2, selectEnd: 3 });
         move(100);
         release();

         expect(sent()).toEqual([]);
         expect(shown()).toBe("2-3");
         expect(ctx.comp.leftHandlePosition).toBeCloseTo(2 * TICK);
         expect(ctx.comp.rightHandlePosition).toBeCloseTo(3 * TICK);
      });

      it("keeps the refreshed range when the labels changed during the drag", () => {
         press(ctx.comp.handleType.Left, 100);
         move(100 + TICK);
         refresh({
            labels: ["A", "B", "C"], values: ["10", "20", "30"], selectStart: 0, selectEnd: 2,
         });
         expect(ctx.comp.currentLabel).toBe("A..C");
         expect(ctx.comp.leftHandlePosition).toBeCloseTo(0);
         release();

         expect(sent()).toEqual([]);
         expect(shown()).toBe("0-2");
         expect(ctx.comp.rightHandlePosition).toBeCloseTo(191);
      });

      it("applies refreshes after a touch is cancelled", () => {
         press(ctx.comp.handleType.Left, 100);
         move(100 + TICK);
         ctx.comp.cancelDrag();
         expect(shown()).toBe("0-4");

         refresh({ selectStart: 2, selectEnd: 3 });

         expect(sent()).toEqual([]);
         expect(shown()).toBe("2-3");
         expect(ctx.comp.leftHandlePosition).toBeCloseTo(2 * TICK);
      });

      it("ends the drag when the window loses focus before the release", () => {
         press(ctx.comp.handleType.Left, 100);
         move(100 + TICK);
         refresh({ selectStart: 2, selectEnd: 3 });

         const blur = ctx.renderer.listen.mock.calls
            .find((call: any[]) => call[0] === "window" && call[1] === "blur");
         blur[2]();
         expect(shown()).toBe("2-3");

         refresh({ selectStart: 3, selectEnd: 4 });
         expect(sent()).toEqual([]);
         expect(shown()).toBe("3-4");
         expect(ctx.comp.leftHandlePosition).toBeCloseTo(3 * TICK);
      });

      it("drops a drag whose release was missed when the next drag starts", () => {
         press(ctx.comp.handleType.Left, 100);
         move(100 + TICK);
         refresh({ selectStart: 2, selectEnd: 3 });

         press(ctx.comp.handleType.Left, 300);
         expect(shown()).toBe("2-3");
         release();

         expect(sent()).toEqual([]);
         expect(shown()).toBe("2-3");
         expect(ctx.comp.leftHandlePosition).toBeCloseTo(2 * TICK);
      });

      it("applies a refresh immediately when no drag is in progress", () => {
         refresh({ selectStart: 2, selectEnd: 3 });

         expect(shown()).toBe("2-3");
         expect(ctx.comp.leftHandlePosition).toBeCloseTo(2 * TICK);
         expect(ctx.comp.rightHandlePosition).toBeCloseTo(3 * TICK);
      });
   });
});
