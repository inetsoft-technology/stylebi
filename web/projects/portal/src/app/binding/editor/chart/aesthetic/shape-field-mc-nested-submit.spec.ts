/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * Bug #78604, the real Edit Shape popup (ShapeFieldMc) with a texture/line picked from a
 * nested combo box: the popup stays open in both event orders, and the picked value is
 * submitted exactly once, whether the popup is then closed with Apply or an outside click.
 */

import { ComponentFixture, TestBed } from "@angular/core/testing";
import {
   CategoricalLineModel,
   CategoricalTextureModel
} from "../../../../common/data/visual-frame-model";
import { DndService } from "../../../../common/dnd/dnd.service";
import { GraphTypes } from "../../../../common/graph-types";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { TestUtils } from "../../../../common/test/test-utils";
import { ChartEditorService } from "../../../services/chart/chart-editor.service";
import { FIELD_MC_PROVIDERS } from "./field-mc-test-helpers";
import { ShapeFieldMc } from "./shape-field-mc.component";

const tick = (ms: number = 0) => new Promise<void>((resolve) => setTimeout(resolve, ms));

function fire(target: EventTarget, type: string): void {
   target.dispatchEvent(new MouseEvent(type, { bubbles: true, cancelable: true, button: 0 }));
}

async function clickOn(target: EventTarget, timerFirst: boolean): Promise<void> {
   fire(target, "mousedown");

   if(timerFirst) {
      await tick(10);
   }

   fire(target, "mouseup");
   fire(target, "click");
   await tick(10);
}

function last(selector: string): HTMLElement {
   const elements = document.body.querySelectorAll<HTMLElement>(selector);
   return elements[elements.length - 1];
}

function openDropdowns(): number {
   return document.body.querySelectorAll("fixed-dropdown").length;
}

function categoricalTexture(): any {
   const frame = new CategoricalTextureModel();
   frame.textures = [0, 1, 2, 3];
   return frame;
}

function categoricalLine(): any {
   const frame = new CategoricalLineModel();
   frame.lines = [0, 1, 2, 3];
   return frame;
}

function categoricalBinding(chartType: number, frame: any) {
   const bindingModel = TestUtils.createMockChartBindingModel();
   bindingModel.chartType = chartType;
   bindingModel.yfields = [TestUtils.createMockChartAggregateRef("Sum(qty)")];
   bindingModel.shapeField = {
      fullName: "state",
      dataInfo: TestUtils.createMockChartDimensionRef("state"),
      frame
   };
   return bindingModel;
}

function combinedBinding(chartType: number) {
   const bindingModel = TestUtils.createMockChartBindingModel();
   bindingModel.chartType = chartType;
   bindingModel.yfields = [TestUtils.createMockChartAggregateRef("Sum(qty)"),
                           TestUtils.createMockChartAggregateRef("Sum(price)")];
   return bindingModel;
}

describe("ShapeFieldMc nested texture/line pick submit (Bug #78604)", () => {
   let fixture: ComponentFixture<ShapeFieldMc>;
   let changeChartAesthetic: ReturnType<typeof vi.fn>;

   async function render(bindingModel: any): Promise<void> {
      changeChartAesthetic = vi.fn();
      TestBed.configureTestingModule({
         imports: [ShapeFieldMc],
         providers: [
            ...FIELD_MC_PROVIDERS,
            {
               provide: ChartEditorService,
               useValue: {
                  changeChartAesthetic,
                  isDropPaneAccept: vi.fn().mockReturnValue(true),
                  getDNDType: vi.fn().mockReturnValue(0)
               }
            },
            { provide: DndService, useValue: {} },
            { provide: UIContextService, useValue: { isVS: () => false } }
         ]
      });
      fixture = TestBed.createComponent(ShapeFieldMc);
      fixture.componentInstance.bindingModel = bindingModel;
      fixture.componentInstance.assemblyName = "Chart1";
      document.body.appendChild(fixture.nativeElement);
      fixture.detectChanges();
   }

   async function open(toggle: HTMLElement): Promise<void> {
      toggle.click();
      fixture.detectChanges();
      await tick(10);
      fixture.detectChanges();
   }

   // open Edit Shape, open the first nested combo and pick its last option
   async function pick(pane: string, toggle: string, option: string,
                       timerFirst: boolean): Promise<void>
   {
      await open(fixture.nativeElement.querySelector(".visual-cell-container"));
      expect(document.body.querySelector(pane)).not.toBeNull();
      await open(document.body.querySelector<HTMLElement>(toggle));
      expect(openDropdowns()).toBe(2);

      await clickOn(last(option), timerFirst);
      fixture.detectChanges();

      expect(openDropdowns()).toBe(1);
      expect(document.body.querySelector(pane)).not.toBeNull();
   }

   async function clickOutside(): Promise<void> {
      await clickOn(document.body, false);
      fixture.detectChanges();
   }

   afterEach(() => {
      fixture?.destroy();
      document.body.querySelectorAll("fixed-dropdown").forEach((e) => e.remove());
   });

   const cases = [
      {
         name: "categorical texture",
         binding: () => categoricalBinding(GraphTypes.CHART_BAR, categoricalTexture()),
         pane: "categorical-shape-pane", toggle: "texture-combo-box .dropdown-toggle",
         option: ".texture-pane > div",
         value: (b: any) => b.shapeField.frame.textures[0],
         // categorical pane submits only when the popup is applied or closed
         submitsOnPick: false
      },
      {
         name: "categorical line",
         binding: () => categoricalBinding(GraphTypes.CHART_LINE, categoricalLine()),
         pane: "categorical-shape-pane", toggle: "line-combo-box .dropdown-toggle",
         option: ".line-pane",
         value: (b: any) => b.shapeField.frame.lines[0],
         submitsOnPick: false
      },
      {
         name: "combined texture",
         binding: () => combinedBinding(GraphTypes.CHART_BAR),
         pane: "combined-shape-pane", toggle: "static-texture-editor .dropdown-toggle",
         option: ".texture-pane > div",
         value: (b: any) => b.yfields[0].textureFrame.texture,
         // combined pane submits each change right away (shapeChanged -> submitIfChanged)
         submitsOnPick: true
      },
      {
         name: "combined line",
         binding: () => combinedBinding(GraphTypes.CHART_LINE),
         pane: "combined-shape-pane", toggle: "static-line-editor .dropdown-toggle",
         option: ".line-pane",
         value: (b: any) => b.yfields[0].lineFrame.line,
         submitsOnPick: true
      }
   ];

   for(const c of cases) {
      for(const timerFirst of [false, true]) {
         const order = timerFirst ? "timer before click" : "click before timer";

         it(`${c.name}: Apply submits the picked value once (${order})`, async () => {
            const binding = c.binding();
            await render(binding);
            const before = c.value(binding);

            await pick(c.pane, c.toggle, c.option, timerFirst);

            expect(c.value(binding)).not.toEqual(before);
            expect(changeChartAesthetic).toHaveBeenCalledTimes(c.submitsOnPick ? 1 : 0);

            last(`${c.pane} .submit-icon`).click();
            await tick(10);
            fixture.detectChanges();

            expect(openDropdowns()).toBe(0);
            expect(changeChartAesthetic).toHaveBeenCalledTimes(1);
            expect(changeChartAesthetic).toHaveBeenCalledWith("shape");
         });

         it(`${c.name}: an outside click submits the picked value once (${order})`,
            async () => {
               const binding = c.binding();
               await render(binding);
               const before = c.value(binding);

               await pick(c.pane, c.toggle, c.option, timerFirst);
               await clickOutside();

               expect(c.value(binding)).not.toEqual(before);
               expect(openDropdowns()).toBe(0);
               expect(changeChartAesthetic).toHaveBeenCalledTimes(1);
            });
      }
   }
});
