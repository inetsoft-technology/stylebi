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
 * ChartTypeButton — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — constructor: load customChartTypes and chartStyles from editor service
 *   Group 2 [Risk 1] — getIconPath: chart type and multi-style icon mapping
 *   Group 3 [Risk 3] — checkValid: skip unchanged / incompatible type warning + reset
 *   Group 4 [Risk 2] — processChangeChartType/toggled: emit onChange and apply on dropdown close
 *   Group 5 [Risk 2] — isStackMeasuresVisible/isStackMeasures: visibility from binding shape
 *   Group 6 [Risk 3] — subscription lifecycle: constructor subscribes without ngOnDestroy
 *
 * HTTP: no HTTP — ChartEditorService mocked with of()
 *
 * Suspected bugs (header only):
 *   Constructor subscriptions — getCustomChartTypes/getChartStyles never unsubscribed; prescan async_zones=2
 *
 * Out of scope:
 *   openChartTypeDialog — opens modal picker, covered via checkValid/processChangeChartType
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render, screen, waitFor } from "@testing-library/angular";
import { of } from "rxjs";
import { GraphTypes } from "../../common/graph-types";
import { TestUtils } from "../../common/test/test-utils";
import { ComponentTool } from "../../common/util/component-tool";
import { ChartAggregateRef } from "../data/chart/chart-aggregate-ref";
import { ChartEditorService } from "../services/chart/chart-editor.service";
import { ChartTypeButton } from "./chart-type-button.component";
import { ChartStylesModel } from "./chart-style-pane.component";

function createEditorService(bindingModel = TestUtils.createMockChartBindingModel()): ChartEditorService {
   return {
      bindingModel,
      changeChartType: vi.fn(),
      getCustomChartTypes: vi.fn().mockReturnValue(of(["999"])),
      getChartStyles: vi.fn().mockReturnValue(of({
         styles: [{ label: "Bar", data: GraphTypes.CHART_BAR }],
         stackStyles: [{ label: "Stack Bar", data: GraphTypes.CHART_BAR_STACK }]
      } as ChartStylesModel))
   } as unknown as ChartEditorService;
}

async function renderButton(
   editorService?: ChartEditorService,
   props: Record<string, unknown> = {}
) {
   return render(ChartTypeButton, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: ChartEditorService, useValue: editorService ?? createEditorService() },
         { provide: NgbModal, useValue: {} }
      ],
      componentProperties: props
   });
}

describe("ChartTypeButton — constructor subscriptions [Group 1, Risk 2]", () => {
   it("should load customChartTypes and chartStyles from editor service", async () => {
      const editorService = createEditorService();
      const { container } = await renderButton(editorService);

      await waitFor(() => {
         expect(editorService.getCustomChartTypes).toHaveBeenCalled();
         expect(editorService.getChartStyles).toHaveBeenCalled();
      });

      expect(container.querySelector("chart-style-pane")).toBeFalsy();
      expect(screen.getByTitle("_#(Select Chart Style)")).toBeInTheDocument();
   });
});

describe("ChartTypeButton — getIconPath [Group 2, Risk 1]", () => {
   it("should return multi-style icon when multiStyles without refName", async () => {
      const { container } = await renderButton(undefined, {
         multiStyles: true,
         refName: null,
         chartType: GraphTypes.CHART_BAR
      });

      expect(container.querySelector(".chart-multi-style-icon")).toBeTruthy();
   });

   it("should map chart types to icon class names", async () => {
      const { container, rerender } = await renderButton(undefined, {
         chartType: GraphTypes.CHART_BAR,
         multiStyles: false,
         refName: "Sum(id)"
      });

      expect(container.querySelector(".chart-bar-icon")).toBeTruthy();

      await rerender({
         componentProperties: {
            chartType: GraphTypes.CHART_PIE,
            multiStyles: false,
            refName: "Sum(id)"
         }
      });

      expect(container.querySelector(".chart-pie-icon")).toBeTruthy();
   });
});

describe("ChartTypeButton — checkValid [Group 3, Risk 3]", () => {
   it("should skip changeChartType when type and flags are unchanged", async () => {
      const editorService = createEditorService();
      const model = editorService.bindingModel;
      model.chartType = GraphTypes.CHART_BAR;
      model.multiStyles = false;
      model.stackMeasures = false;

      const { fixture } = await renderButton(editorService, {
         chartType: GraphTypes.CHART_BAR,
         multiStyles: false,
         stackMeasures: false
      });
      fixture.componentInstance.dropdown = { close: vi.fn() } as any;

      fixture.componentInstance.checkValid();

      expect(editorService.changeChartType).not.toHaveBeenCalled();
   });

   it("should reset chart type and warn on incompatible aggregate types", async () => {
      const editorService = createEditorService();
      const agg1 = TestUtils.createMockChartAggregateRef("Sum(id)") as ChartAggregateRef;
      agg1.chartType = GraphTypes.CHART_BAR;
      agg1.rtchartType = GraphTypes.CHART_BAR;
      const agg2 = TestUtils.createMockChartAggregateRef("Sum(qty)") as ChartAggregateRef;
      agg2.chartType = GraphTypes.CHART_STOCK;
      agg2.rtchartType = GraphTypes.CHART_STOCK;
      editorService.bindingModel.yfields = [agg1, agg2];
      editorService.bindingModel.xfields = [];

      const { fixture, container } = await renderButton(editorService, {
         refName: "Sum(id)",
         chartType: GraphTypes.CHART_PIE,
         multiStyles: false,
         stackMeasures: false
      });
      fixture.componentInstance.dropdown = { close: vi.fn() } as any;

      const warnSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockImplementation(() => null as any);
      fixture.componentInstance.checkValid();
      fixture.detectChanges();

      expect(container.querySelector(".chart-bar-icon")).toBeTruthy();
      expect(warnSpy).toHaveBeenCalled();
      expect(editorService.changeChartType).not.toHaveBeenCalled();
      warnSpy.mockRestore();
   });
});

describe("ChartTypeButton — processChangeChartType / toggled [Group 4, Risk 2]", () => {
   it("should call editorService.changeChartType and emit onChange", async () => {
      const editorService = createEditorService();
      const changed = vi.fn();
      const { fixture } = await renderButton(editorService, {
         chartType: GraphTypes.CHART_LINE,
         multiStyles: true,
         stackMeasures: true,
         refName: "Sum(id)"
      });
      fixture.componentInstance.onChange.subscribe(changed);

      fixture.componentInstance.processChangeChartType();

      expect(editorService.changeChartType).toHaveBeenCalledWith(
         GraphTypes.CHART_LINE, true, true, "Sum(id)"
      );
      expect(changed).toHaveBeenCalledWith(GraphTypes.CHART_LINE);
   });

   it("should apply pending changes when dropdown closes", async () => {
      const editorService = createEditorService();
      editorService.bindingModel.chartType = GraphTypes.CHART_BAR;
      const { fixture } = await renderButton(editorService, {
         chartType: GraphTypes.CHART_LINE,
         multiStyles: false,
         stackMeasures: false
      });
      fixture.componentInstance.dropdown = { close: vi.fn() } as any;

      fixture.componentInstance.toggled(false);

      expect(editorService.changeChartType).toHaveBeenCalled();
   });
});

describe("ChartTypeButton — stack measures visibility [Group 5, Risk 2]", () => {
   it("should hide stack measures when fewer than two XY aggregates", async () => {
      const editorService = createEditorService();
      const agg = TestUtils.createMockChartAggregateRef("Sum(id)");
      editorService.bindingModel.xfields = [agg];
      editorService.bindingModel.yfields = [];
      editorService.bindingModel.separated = false;

      const { fixture } = await renderButton(editorService, {
         chartType: GraphTypes.CHART_BAR_STACK,
         stackMeasures: false,
         multiStyles: false
      });

      expect(fixture.componentInstance.isStackMeasuresVisible()).toBe(false);
   });

   it("should expose stackMeasures when binding is stack type and not separated", async () => {
      const editorService = createEditorService();
      editorService.bindingModel.separated = false;
      const { fixture } = await renderButton(editorService, {
         chartType: GraphTypes.CHART_BAR_STACK,
         stackMeasures: true,
         multiStyles: false
      });

      expect(fixture.componentInstance.isStackMeasures).toBe(true);
   });
});

describe("ChartTypeButton — subscription lifecycle [Group 6, Risk 3]", () => {
   it("should subscribe in constructor but not implement ngOnDestroy", async () => {
      const editorService = createEditorService();
      const { fixture } = await renderButton(editorService);

      await waitFor(() => {
         expect(editorService.getCustomChartTypes).toHaveBeenCalled();
         expect(editorService.getChartStyles).toHaveBeenCalled();
      });

      expect((fixture.componentInstance as any).ngOnDestroy).toBeUndefined();
   });
});
