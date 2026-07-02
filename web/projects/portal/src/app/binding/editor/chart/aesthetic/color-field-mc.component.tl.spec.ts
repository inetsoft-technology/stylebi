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
 * ColorFieldMc — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — getField/getFrames: colorField vs aggregate vs radar/contour frame paths
 *   Group 2 [Risk 2] — changeColorFrame: update field or binding frame and submit on change
 *   Group 3 [Risk 2] — getEditPaneId: CategoricalColor vs LinearColor vs StaticColor vs CombinedColor
 *   Group 4 [Risk 2] — isPrimaryField/isContour/getHint/isDropPaneAccept: chart-type guards
 *   Group 5 [Risk 2] — reset/setAestheticRef/syncAllChartAggregateInfo: aggregate sync contracts
 *
 * HTTP: no HTTP — chart binding editor local state only
 *
 * Out of scope:
 *   dragFieldMCComplete/drop/convert — DnD orchestration owned by AestheticFieldMc base
 *   openChanged/submitIfChanged — dropdown lifecycle, covered via changeColorFrame submit path
 */

import { ComponentFixture } from "@angular/core/testing";
import { render, screen } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { DndService } from "../../../../common/dnd/dnd.service";
import { GraphTypes } from "../../../../common/graph-types";
import {
   CategoricalColorModel,
   GradientColorModel,
   StaticColorModel
} from "../../../../common/data/visual-frame-model";
import { TestUtils } from "../../../../common/test/test-utils";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { AestheticInfo } from "../../../data/chart/aesthetic-info";
import { ChartEditorService } from "../../../services/chart/chart-editor.service";
import { FIELD_MC_PROVIDERS } from "./field-mc-test-helpers";
import { ColorFieldMc } from "./color-field-mc.component";

const EDIT_COLOR = "_#(Edit Color)";

async function renderColorFieldMc(
   bindingModel = TestUtils.createMockChartBindingModel(),
   extra: Record<string, unknown> = {}
) {
   const editorService = {
      changeChartAesthetic: vi.fn(),
      isDropPaneAccept: vi.fn().mockReturnValue(true),
      getDNDType: vi.fn().mockReturnValue(0)
   };
   const result = await render(ColorFieldMc, {
      providers: [
         ...FIELD_MC_PROVIDERS,
         { provide: ChartEditorService, useValue: editorService },
         { provide: DndService, useValue: {} },
         { provide: UIContextService, useValue: { isVS: () => false } }
      ],
      componentProperties: {
         bindingModel,
         assemblyName: "Chart1",
         ...extra
      }
   });
   return { ...result, editorService, bindingModel };
}

async function openColorDropdown(container: HTMLElement, fixture: ComponentFixture<ColorFieldMc>) {
   const trigger = container.querySelector(".visual-cell-container, [data-test='colorIcon']");
   if(trigger) {
      await userEvent.click(trigger);
      fixture.detectChanges();
      await fixture.whenStable();
   }
}

describe("ColorFieldMc — getField and getFrames [Group 1, Risk 2]", () => {
   it("should return colorField frame when aesthetic field is bound", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      const frame = new CategoricalColorModel();
      bindingModel.colorField = {
         fullName: "state",
         dataInfo: TestUtils.createMockChartDimensionRef("state"),
         frame
      };
      const { container } = await renderColorFieldMc(bindingModel);

      expect(container.querySelector('[data-test="color-field-container"]')).toBeTruthy();
      expect(container.querySelector("color-cell")).toBeTruthy();
      expect(container.querySelector("[data-test='colorIcon']")).toBeFalsy();
   });

   it("should use binding colorFrame for radar charts without color field", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      bindingModel.chartType = GraphTypes.CHART_RADAR;
      bindingModel.colorFrame = new StaticColorModel();
      const { container } = await renderColorFieldMc(bindingModel);

      expect(container.querySelector("[data-test='colorIcon']")).toBeTruthy();
   });
});

describe("ColorFieldMc — changeColorFrame [Group 2, Risk 2]", () => {
   it("should update field frame and emit onChange when frames change", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      const frame = new CategoricalColorModel();
      bindingModel.colorField = {
         fullName: "state",
         dataInfo: TestUtils.createMockChartDimensionRef("state"),
         frame
      };
      const { fixture, editorService } = await renderColorFieldMc(bindingModel);
      const nextFrame = new GradientColorModel();
      const changed = vi.fn();
      fixture.componentInstance.onChange.subscribe(changed);
      fixture.componentInstance.openChanged(true);

      fixture.componentInstance.changeColorFrame(nextFrame);
      fixture.componentInstance.submitIfChanged();

      expect(bindingModel.colorField.frame).toBe(nextFrame);
      expect(changed).toHaveBeenCalled();
      expect(editorService.changeChartAesthetic).toHaveBeenCalledWith("color");
   });
});

describe("ColorFieldMc — getEditPaneId [Group 3, Risk 2]", () => {
   it("should return LinearColor for contour charts", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      bindingModel.chartType = GraphTypes.CHART_MAP_CONTOUR;
      bindingModel.yfields = [TestUtils.createMockChartAggregateRef("Sum(qty)")];
      bindingModel.colorFrame = new GradientColorModel();
      const { container, fixture } = await renderColorFieldMc(bindingModel);

      await openColorDropdown(container, fixture);

      expect(document.querySelector("linear-color-pane")).toBeTruthy();
   });

   it("should return CategoricalColor for discrete dimension color field", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      const dim = TestUtils.createMockChartDimensionRef("state");
      bindingModel.colorField = { fullName: "state", dataInfo: dim, frame: new CategoricalColorModel() };
      const { container, fixture } = await renderColorFieldMc(bindingModel);

      await openColorDropdown(container, fixture);

      expect(document.querySelector("categorical-color-pane")).toBeTruthy();
   });

   it("should return StaticColor for unbound color with single frame", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      bindingModel.yfields = [TestUtils.createMockChartAggregateRef("Sum(qty)")];
      bindingModel.colorFrame = new StaticColorModel();
      const { container, fixture } = await renderColorFieldMc(bindingModel);

      await openColorDropdown(container, fixture);

      expect(document.querySelector("static-color-pane")).toBeTruthy();
   });
});

describe("ColorFieldMc — chart-type guards [Group 4, Risk 2]", () => {
   it("should mark pie charts as primary color field", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      bindingModel.chartType = GraphTypes.CHART_PIE;
      const { container } = await renderColorFieldMc(bindingModel);

      expect(container.querySelector(".primary-label")).toBeTruthy();
   });

   it("should disable drop pane and show contour hint for contour charts", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      bindingModel.chartType = GraphTypes.CHART_MAP_CONTOUR;
      bindingModel.yfields = [TestUtils.createMockChartAggregateRef("Sum(qty)")];
      bindingModel.colorFrame = new GradientColorModel();
      const { container } = await renderColorFieldMc(bindingModel);

      expect(screen.getByTitle(EDIT_COLOR)).toBeInTheDocument();
      expect(container.querySelector("[data-test='colorIcon'].icon-disabled")).toBeTruthy();
   });
});

describe("ColorFieldMc — aggregate sync [Group 5, Risk 2]", () => {
   it("should set colorField on binding model via setAestheticRef", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      const { fixture, container } = await renderColorFieldMc(bindingModel);
      const ref = { fullName: "qty", dataInfo: null, frame: null } as AestheticInfo;

      fixture.componentInstance.setAestheticRef(ref);
      fixture.componentInstance.ngOnChanges(null);
      fixture.detectChanges();

      expect(bindingModel.colorField).toBe(ref);
      expect(container.querySelector('[data-test="color-field-container"] chart-fieldmc')).toBeTruthy();
   });

   it("should clear oframes on reset to force submit", async () => {
      const { fixture } = await renderColorFieldMc();
      (fixture.componentInstance as any).oframes = JSON.stringify([{ clazz: "x" }]);

      fixture.componentInstance.reset();

      expect((fixture.componentInstance as any).oframes).toBe("");
   });
});
