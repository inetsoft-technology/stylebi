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
 * ChartAxisArea — Sort Axis icon vs. axis-area gestures (Bug #78094)
 *
 * The sort icon is a child of #axisArea, so its pointer events bubble to onDown/onUp/onDblClick.
 * A zero-movement click on the icon used to select (and right-click / double-click used to
 * select / brush) the axis label lying under the icon, because the selection runs on pointerup,
 * before clickSort() sees the click.
 *
 * These tests render the real template (real outOfZone and template listeners) and dispatch real
 * bubbling DOM events. Only the canvas hit-test getTreeRegions is stubbed, to return one label
 * region standing in for the label under the icon. jsdom has window.PointerEvent, so the
 * pointer branch (supportPointEvent() == true) is the one exercised.
 */

import { render } from "@testing-library/angular";
import { Subject } from "rxjs";
import { ChartAxisArea } from "./chart-axis-area.component";
import { ChartService } from "../services/chart.service";
import { DebounceService } from "../../widget/services/debounce.service";
import { ContextProvider } from "../../vsobjects/context-provider.service";
import { ScaleService } from "../../widget/services/scale/scale-service";
import { ChartRegion } from "../model/chart-region";
import { makeAxis, makeModel } from "./chart-axis-area.component.test-helpers";

const LABEL_REGION = { index: 0, rowIdx: 0, noselect: false } as unknown as ChartRegion;

async function renderAxis() {
   vi.spyOn(ChartAxisArea.prototype as any, "getTreeRegions").mockReturnValue([LABEL_REGION]);
   const selectRegion = vi.fn();
   const sortAxis = vi.fn();
   const brushChart = vi.fn();

   const result = await render(ChartAxisArea, {
      providers: [
         { provide: ChartService, useValue: { clearCanvas: vi.fn(), drawRectangle: vi.fn() } },
         { provide: DebounceService, useValue: { debounce: vi.fn() } },
         { provide: ContextProvider, useValue: { vsWizard: false, vsWizardPreview: false } },
         {
            provide: ScaleService,
            useValue: { getScale: () => new Subject<number>(), getCurrentScale: () => 1 }
         },
      ],
      componentInputs: {
         model: makeModel(),
         chartObject: makeAxis({ areaName: "left_y_axis", axisType: "y" }),
         onTitle: true,
      },
      on: { selectRegion, sortAxis, brushChart },
   });

   const comp = result.fixture.componentInstance;
   const container = result.container as HTMLElement;
   const icon = container.querySelector(".axis__sort-icon") as HTMLElement;
   const span = icon.querySelector("span") as HTMLElement;
   const canvas = container.querySelector("canvas") as HTMLElement;

   return { comp, icon, span, canvas, selectRegion, sortAxis, brushChart };
}

const AT = { clientX: 40, clientY: 110, bubbles: true, cancelable: true };

function pointer(target: Element, type: string, init: any = AT): void {
   target.dispatchEvent(new PointerEvent(type, init));
}

function mouse(target: Element, type: string, init: any = AT): void {
   target.dispatchEvent(new MouseEvent(type, init));
}

/** Full event sequence of one primary-button click without movement. */
function click(target: Element): void {
   pointer(target, "pointerdown");
   mouse(target, "mousedown");
   pointer(target, "pointerup");
   mouse(target, "mouseup");
   mouse(target, "click");
}

afterEach(() => vi.restoreAllMocks());

describe("ChartAxisArea — Sort Axis icon (Bug #78094)", () => {
   it("should render the sort icon inside the axis area", async () => {
      const { icon } = await renderAxis();
      expect(icon).toBeTruthy();
      expect(icon.closest(".chart-axis-area")).toBeTruthy();
   });

   it("should sort without selecting the label under the icon on a click on the icon", async () => {
      const { comp, icon, selectRegion, sortAxis } = await renderAxis();
      click(icon);
      expect(sortAxis).toHaveBeenCalledTimes(1);
      expect(selectRegion).not.toHaveBeenCalled();
      expect(comp.isMouseDown).toBe(false);
   });

   it("should sort without selecting on a click on the icon's inner span", async () => {
      const { comp, span, selectRegion, sortAxis } = await renderAxis();
      click(span);
      expect(sortAxis).toHaveBeenCalledTimes(1);
      expect(selectRegion).not.toHaveBeenCalled();
      expect(comp.isMouseDown).toBe(false);
   });

   it("should not select the label under the icon on a right click on the icon", async () => {
      const { icon, selectRegion } = await renderAxis();
      const right = { ...AT, button: 2, buttons: 2 };
      pointer(icon, "pointerdown", right);
      pointer(icon, "pointerup", right);
      mouse(icon, "contextmenu", right);
      expect(selectRegion).not.toHaveBeenCalled();
   });

   it("should not brush the chart on a double click on the icon", async () => {
      const { span, brushChart, selectRegion } = await renderAxis();
      click(span);
      click(span);
      mouse(span, "dblclick");
      expect(brushChart).not.toHaveBeenCalled();
      expect(selectRegion).not.toHaveBeenCalled();
   });

   it("should still select a label on a click on the axis canvas", async () => {
      const { canvas, selectRegion, sortAxis } = await renderAxis();
      click(canvas);
      expect(selectRegion).toHaveBeenCalledTimes(1);
      expect(selectRegion.mock.calls[0][0].regions).toEqual([LABEL_REGION]);
      expect(sortAxis).not.toHaveBeenCalled();
   });

   it("should still brush the chart on a double click on the axis canvas", async () => {
      const { canvas, brushChart } = await renderAxis();
      mouse(canvas, "dblclick");
      expect(brushChart).toHaveBeenCalledTimes(1);
   });

   it("should not select, and leave no armed press, when a canvas press is released on the icon", async () => {
      const { comp, canvas, icon, selectRegion } = await renderAxis();
      pointer(canvas, "pointerdown");
      pointer(icon, "pointerup");
      expect(comp.isMouseDown).toBe(false);
      expect(selectRegion).not.toHaveBeenCalled();

      // a later pointerup back at the original down point must not select without a new press
      pointer(canvas, "pointerup");
      expect(selectRegion).not.toHaveBeenCalled();
   });
});
