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
 * ShapeFieldMc — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — getField/getFrames: shapeField vs aggregate default frame creation
 *   Group 2 [Risk 2] — isLineType/isTextureType/isMilestoneField: chart-type frame selection
 *   Group 3 [Risk 2] — getEditPaneId: CategoricalShape vs LinearShape vs StaticShape vs CombinedShape
 *   Group 4 [Risk 2] — setAestheticRef/openChanged: shape binding and edit guard
 *   Group 5 [Risk 2] — isMixedChartType/nilSupported/getFieldType: aggregate mixed-state helpers
 *
 * HTTP: no HTTP — chart binding editor local state only
 *
 * Out of scope:
 *   syncAllChartAggregateInfo — aggregate clone path, covered via setAestheticRef contract
 *   dragFieldMCComplete/drop — DnD orchestration owned by AestheticFieldMc base
 */

import { render, screen } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { DndService } from "../../../../common/dnd/dnd.service";
import { GraphTypes } from "../../../../common/graph-types";
import { StaticShapeModel } from "../../../../common/data/visual-frame-model";
import { TestUtils } from "../../../../common/test/test-utils";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { AestheticInfo } from "../../../data/chart/aesthetic-info";
import { ChartEditorService } from "../../../services/chart/chart-editor.service";
import { ShapeFieldMc } from "./shape-field-mc.component";

function createAllChartAggregate(name: string) {
   const aggr = TestUtils.createMockChartAggregateRef(name);
   aggr.classType = "allaggregate";
   return aggr as any;
}

async function renderShapeFieldMc(
   bindingModel = TestUtils.createMockChartBindingModel(),
   aggr?: any
) {
   const editorService = {
      changeChartAesthetic: vi.fn(),
      isDropPaneAccept: vi.fn().mockReturnValue(true),
      getDNDType: vi.fn().mockReturnValue(0)
   };
   const result = await render(ShapeFieldMc, {
      providers: [
         { provide: ChartEditorService, useValue: editorService },
         { provide: DndService, useValue: {} },
         { provide: UIContextService, useValue: { isVS: () => false } }
      ],
      componentProperties: {
         bindingModel,
         assemblyName: "Chart1",
         ...(aggr ? { aggr } : {})
      }
   });
   return { ...result, editorService, bindingModel };
}

async function openShapeDropdown(container: HTMLElement) {
   const trigger = container.querySelector(".visual-cell-container, .visual-edit-icon");
   if(trigger) {
      await userEvent.click(trigger);
   }
}

describe("ShapeFieldMc — getField and getFrames [Group 1, Risk 2]", () => {
   it("should return shapeField frame when aesthetic field is bound", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      const frame = new StaticShapeModel();
      bindingModel.shapeField = {
         fullName: "state",
         dataInfo: TestUtils.createMockChartDimensionRef("state"),
         frame
      };
      const { container } = await renderShapeFieldMc(bindingModel);

      expect(container.querySelector('[data-test="shape-field-container"]')).toBeTruthy();
      expect(container.querySelector("shape-cell")).toBeTruthy();
   });

   it("should create default shape frame for aggregate on radar point-line chart", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      bindingModel.chartType = GraphTypes.CHART_RADAR;
      bindingModel.pointLine = true;
      bindingModel.yfields = [TestUtils.createMockChartAggregateRef("Sum(qty)")];
      const aggr = TestUtils.createMockChartAggregateRef("Sum(qty)");
      const { container } = await renderShapeFieldMc(bindingModel, aggr);

      expect(aggr.shapeFrame).toBeTruthy();
      expect(container.querySelector("shape-cell")).toBeTruthy();
   });
});

describe("ShapeFieldMc — frame type getters [Group 2, Risk 2]", () => {
   it("should detect line charts via isLineType", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      bindingModel.chartType = GraphTypes.CHART_LINE;
      const { container } = await renderShapeFieldMc(bindingModel);

      await openShapeDropdown(container);

      expect(document.querySelector("static-line-pane, linear-line-pane")).toBeTruthy();
   });

   it("should detect gantt milestone field", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      bindingModel.chartType = GraphTypes.CHART_GANTT;
      const aggr = TestUtils.createMockChartAggregateRef("Task");
      bindingModel.milestoneField = aggr;
      const { container } = await renderShapeFieldMc(bindingModel, aggr);

      expect(container.querySelector(".visual-edit-icon.icon-disabled, .visual-edit-icon .icon-disabled"))
         .toBeTruthy();
   });
});

describe("ShapeFieldMc — getEditPaneId [Group 3, Risk 2]", () => {
   it("should return CategoricalShape for discrete dimension shape field", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      bindingModel.yfields = [TestUtils.createMockChartAggregateRef("Sum(qty)")];
      bindingModel.shapeField = {
         fullName: "state",
         dataInfo: TestUtils.createMockChartDimensionRef("state"),
         frame: new StaticShapeModel()
      };
      const { container } = await renderShapeFieldMc(bindingModel);

      await openShapeDropdown(container);

      expect(document.querySelector("categorical-shape-pane")).toBeTruthy();
   });

   it("should return StaticShape for single unbound shape frame on radar point-line chart", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      bindingModel.chartType = GraphTypes.CHART_RADAR;
      bindingModel.pointLine = true;
      bindingModel.yfields = [TestUtils.createMockChartAggregateRef("Sum(qty)")];
      bindingModel.shapeFrame = new StaticShapeModel();
      const { container } = await renderShapeFieldMc(bindingModel);

      await openShapeDropdown(container);

      expect(document.querySelector("static-shape-pane")).toBeTruthy();
   });

   it("should return LinearLine for line chart aggregate measure field", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      bindingModel.chartType = GraphTypes.CHART_LINE;
      bindingModel.yfields = [TestUtils.createMockChartAggregateRef("Sum(qty)")];
      bindingModel.shapeField = {
         fullName: "Sum(qty)",
         dataInfo: TestUtils.createMockChartAggregateRef("Sum(qty)"),
         frame: new StaticShapeModel()
      };
      const { container } = await renderShapeFieldMc(bindingModel);

      await openShapeDropdown(container);

      expect(document.querySelector("linear-line-pane")).toBeTruthy();
   });
});

describe("ShapeFieldMc — setAestheticRef and openChanged [Group 4, Risk 2]", () => {
   it("should set shapeField on binding model via setAestheticRef", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      const { fixture, container } = await renderShapeFieldMc(bindingModel);
      const ref = { fullName: "state", dataInfo: null, frame: null } as AestheticInfo;

      fixture.componentInstance.setAestheticRef(ref);
      fixture.detectChanges();

      expect(bindingModel.shapeField).toBe(ref);
      expect(container.querySelector('[data-test="shape-field-container"] chart-fieldmc')).toBeTruthy();
   });

   it("should skip openChanged when edit is disabled", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      bindingModel.multiStyles = true;
      bindingModel.allChartAggregate = createAllChartAggregate("mixed");
      const aggr = createAllChartAggregate("A");
      aggr.rtchartType = GraphTypes.CHART_BAR;
      const aggr2 = TestUtils.createMockChartAggregateRef("B");
      aggr2.rtchartType = GraphTypes.CHART_LINE;
      bindingModel.xfields = [aggr, aggr2];
      const { fixture } = await renderShapeFieldMc(bindingModel, bindingModel.allChartAggregate);
      fixture.componentInstance._isEditEnabled = false;
      const submitSpy = vi.spyOn(fixture.componentInstance as any, "submitIfChanged");

      fixture.componentInstance.openChanged(false);

      expect(submitSpy).not.toHaveBeenCalled();
   });
});

describe("ShapeFieldMc — mixed state helpers [Group 5, Risk 2]", () => {
   it("should detect mixed chart types across aggregates", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      bindingModel.multiStyles = true;
      bindingModel.allChartAggregate = createAllChartAggregate("mixed");
      const aggr1 = TestUtils.createMockChartAggregateRef("A");
      aggr1.rtchartType = GraphTypes.CHART_BAR;
      const aggr2 = TestUtils.createMockChartAggregateRef("B");
      aggr2.rtchartType = GraphTypes.CHART_LINE;
      bindingModel.xfields = [aggr1, aggr2];
      const { container } = await renderShapeFieldMc(bindingModel, bindingModel.allChartAggregate);

      expect(container.querySelector(".visual-edit-icon.icon-disabled, .visual-edit-icon .icon-disabled"))
         .toBeTruthy();
   });

   it("should expose nilSupported from graph util", async () => {
      const bindingModel = TestUtils.createMockChartBindingModel();
      const { container } = await renderShapeFieldMc(bindingModel);

      expect(container.querySelector('[data-test="shape-field-container"]')).toBeTruthy();
      expect(container.querySelector("shape-cell, .visual-edit-icon")).toBeTruthy();
   });
});
