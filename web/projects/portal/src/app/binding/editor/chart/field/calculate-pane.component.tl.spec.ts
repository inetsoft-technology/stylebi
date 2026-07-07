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
 * CalculatePane - single pass + async
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] - aggregate setter: initBuildInCalcs populates baseCalcs
 *   Group 2 [Risk 3] - calculateValue setter: emits onCalcChanged for non-custom calcs
 *   Group 3 [Risk 3] - openCalculateDialog: dialog props, HTTP endpoints, async dim loading
 *   Group 4 [Risk 2] - hasRow/hasCol crosstab helpers and getAggregateName
 *
 * Mocking strategy:
 *   - BindingService, UIContextService, NgbModal -> test doubles
 *   - ComponentTool.showDialog -> spy returning assignment-capture dialog instance
 *   - ModelService -> real service with provideHttpClient() + MSW handlers
 *
 * Confirmed bugs (it.fails): none
 */

import { HttpParams, provideHttpClient } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";

import { server } from "@test-mocks/server";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { StyleConstants } from "../../../../common/util/style-constants";
import { ComponentTool } from "../../../../common/util/component-tool";
import { ModelService } from "../../../../widget/services/model.service";
import { PercentCalcInfo } from "../../../data/chart/calculate-info";
import { ChartAggregateRef } from "../../../data/chart/chart-aggregate-ref";
import { CrosstabBindingModel } from "../../../data/table/crosstab-binding-model";
import { BindingService } from "../../../services/binding.service";
import { CalculatorConstants } from "./calculator-constants";
import { CalculatePane } from "./calculate-pane.component";
import { CalculatePaneDialog } from "./calculate-pane-dialog.component";

const MODAL_MOCK = {
   open: vi.fn().mockImplementation(() => ({
      componentInstance: {},
      result: Promise.resolve(undefined),
      close: vi.fn(),
      dismiss: vi.fn(),
   })),
};

function makeBindingServiceStub(overrides: Partial<{
   objectType: string;
   runtimeId: string;
   assemblyName: string;
   bindingModel: CrosstabBindingModel;
}> = {}): BindingService {
   const bindingModel = overrides.bindingModel ?? {
      rows: [{ name: "Region" }],
      cols: [{ name: "Year" }],
   } as CrosstabBindingModel;

   return {
      objectType: overrides.objectType ?? "chart",
      runtimeId: overrides.runtimeId ?? "rt-1",
      assemblyName: overrides.assemblyName ?? "Chart1",
      bindingModel,
      getURLParams: () => new HttpParams()
         .set("vsId", overrides.runtimeId ?? "rt-1")
         .set("assemblyName", overrides.assemblyName ?? "Chart1"),
   } as unknown as BindingService;
}

function makeUIContextStub(isVS = true): UIContextService {
   return { isVS: () => isVS } as UIContextService;
}

function makeAggregateRef(): ChartAggregateRef {
   const percentCalc = new PercentCalcInfo();
   percentCalc.classType = "PERCENT";
   percentCalc.name = "Percent";
   percentCalc.level = 1;
   percentCalc.view = "Percent of";

   const agg = new ChartAggregateRef();
   agg.name = "Sum(Sales)";
   agg.buildInCalcs = [null, percentCalc];
   agg.calculateInfo = null;
   return agg;
}

interface RenderOpts {
   bindingService?: BindingService;
   uiContextService?: UIContextService;
   cube?: boolean;
   percentageDirection?: string;
}

async function renderPane(opts: RenderOpts = {}) {
   const bindingService = opts.bindingService ?? makeBindingServiceStub();
   const uiContextService = opts.uiContextService ?? makeUIContextStub();
   const aggregate = makeAggregateRef();

   const { fixture } = await render(CalculatePane, {
      providers: [
         provideHttpClient(),
         ModelService,
         { provide: BindingService, useValue: bindingService },
         { provide: UIContextService, useValue: uiContextService },
         { provide: NgbModal, useValue: MODAL_MOCK },
      ],
      schemas: [NO_ERRORS_SCHEMA],
      componentInputs: {
         aggregate,
         cube: opts.cube ?? false,
         percentageDirection: opts.percentageDirection ?? String(StyleConstants.PERCENTAGE_BY_COL),
         supportPercentageDirection: true,
         variables: { param1: "value1" },
      },
   });

   return {
      comp: fixture.componentInstance as CalculatePane,
      fixture,
      bindingService,
      uiContextService,
   };
}

let capturedDialog: any;

afterEach(() => {
   capturedDialog = null;
   vi.restoreAllMocks();
});

describe("Group 1 - aggregate setter initBuildInCalcs", () => {
   it("should populate baseCalcs from aggregate buildInCalcs when aggregate is assigned", async () => {
      const { comp } = await renderPane();
      const agg = makeAggregateRef();

      comp.aggregate = agg;

      expect(comp.baseCalcs).toHaveLength(2);
      expect(comp.baseCalcs[0].label).toBe("_#(js:None)");
      expect(comp.baseCalcs[1].label).toBe("Percent");
      expect(comp.baseCalcs[1].data.classType).toBe("PERCENT");
   });
});

describe("Group 2 - calculateValue setter", () => {
   it("should emit onCalcChanged when a non-custom calculate value is assigned", async () => {
      const { comp } = await renderPane();
      const agg = makeAggregateRef();
      comp.aggregate = agg;
      const emitSpy = vi.spyOn(comp.onCalcChanged, "emit");
      const calc = new PercentCalcInfo();
      calc.classType = "PERCENT";
      calc.level = 1;

      comp.calculateValue = calc;

      expect(agg.calculateInfo).toBe(calc);
      expect(comp.customCalc).toBeNull();
      expect(emitSpy).toHaveBeenCalledWith({
         aggr: agg,
         percentageDirection: String(StyleConstants.PERCENTAGE_BY_COL),
      });
   });
});

describe("Group 3 - openCalculateDialog", () => {
   beforeEach(() => {
      vi.spyOn(ComponentTool, "showDialog").mockImplementation(
         (_modal, _type, _onCommit) => {
            capturedDialog = {};
            return capturedDialog;
         },
      );
   });

   it("should configure the dialog and request composer calculator endpoints", async () => {
      const dimsSpy = vi.fn();
      const supportResetSpy = vi.fn();
      const resetOptionsSpy = vi.fn();

      server.use(
         http.get("*/api/composer/dims", ({ request }) => {
            dimsSpy(request.url);
            return HttpResponse.json({
               [CalculatorConstants.PERCENT_DIMS_TAG]: [{ data: "Region", label: "Region" }],
               [CalculatorConstants.PERCENT_LEVEL_TAG]: [{ data: "1", label: "Grand Total" }],
               [CalculatorConstants.VALUE_OF_TAG]: [{ data: "OrderDate", label: "Order Date" }],
               [CalculatorConstants.BREAK_BY_TAG]: [{ data: "Year", label: "Year" }],
               [CalculatorConstants.MOVING_TAG]: [{ data: "Month", label: "Month" }],
            });
         }),
         http.get("*/api/composer/supportReset", ({ request }) => {
            supportResetSpy(request.url);
            return HttpResponse.json({ Year: true });
         }),
         http.get("*/api/composer/resetOptions", ({ request }) => {
            resetOptionsSpy(request.url);
            return HttpResponse.json({
               [CalculatorConstants.INNER_DIMENSION]: [["Year", "0"]],
               [CalculatorConstants.ROW_INNER_DIMENSION]: [["Quarter", "1"]],
               [CalculatorConstants.COLUMN_INNER_DIMENSION]: [["Month", "2"]],
            });
         }),
      );

      const bindingService = makeBindingServiceStub({
         objectType: "VSCrosstab",
         runtimeId: "rt-calc",
         assemblyName: "Crosstab1",
      });
      const { comp } = await renderPane({ bindingService });
      const agg = makeAggregateRef();
      const calc = new PercentCalcInfo();
      calc.classType = "PERCENT";
      calc.level = 1;
      agg.calculateInfo = calc;
      comp.aggregate = agg;

      comp.openCalculateDialog();

      expect(ComponentTool.showDialog).toHaveBeenCalledWith(
         MODAL_MOCK,
         CalculatePaneDialog,
         expect.any(Function),
      );
      expect(capturedDialog.aggreName).toBe("Sum(Sales)");
      expect(capturedDialog.crosstab).toBe(true);
      expect(capturedDialog.hasRow).toBe(true);
      expect(capturedDialog.hasCol).toBe(true);
      expect(capturedDialog.isVs).toBe(true);
      expect(capturedDialog.runtimeId).toBe("rt-calc");
      expect(capturedDialog.assemblyName).toBe("Crosstab1");
      expect(capturedDialog.variables).toEqual({ param1: "value1" });
      expect(capturedDialog.percentageDirection).toBe(String(StyleConstants.PERCENTAGE_BY_COL));
      expect(capturedDialog.supportPercentageDirection).toBe(true);
      expect(capturedDialog.calculator.classType).toBe("PERCENT");

      await waitFor(() => expect(dimsSpy).toHaveBeenCalled());
      await waitFor(() => expect(supportResetSpy).toHaveBeenCalled());
      await waitFor(() => expect(resetOptionsSpy).toHaveBeenCalled());
      expect(supportResetSpy.mock.calls[0][0]).toContain("aggreName=Sum(Sales)");
      expect(resetOptionsSpy.mock.calls[0][0]).toContain("aggreName=Sum(Sales)");
   });

   it("should populate dialog dimension data after HTTP subscriptions complete", async () => {
      server.use(
         http.get("*/api/composer/dims", () => {
            return HttpResponse.json({
               [CalculatorConstants.PERCENT_DIMS_TAG]: [{ data: "Product", label: "Product" }],
               [CalculatorConstants.PERCENT_LEVEL_TAG]: [{ data: "2", label: "Subtotal" }],
               [CalculatorConstants.VALUE_OF_TAG]: [{ data: "ShipDate", label: "Ship Date" }],
               [CalculatorConstants.BREAK_BY_TAG]: [{ data: "Quarter", label: "Quarter" }],
               [CalculatorConstants.MOVING_TAG]: [{ data: "Week", label: "Week" }],
            });
         }),
         http.get("*/api/composer/supportReset", () => {
            return HttpResponse.json({ Quarter: false });
         }),
         http.get("*/api/composer/resetOptions", () => {
            return HttpResponse.json({
               [CalculatorConstants.INNER_DIMENSION]: [["Day", "4"]],
               [CalculatorConstants.ROW_INNER_DIMENSION]: [["Hour", "5"]],
               [CalculatorConstants.COLUMN_INNER_DIMENSION]: [["Minute", "6"]],
            });
         }),
      );

      const { comp } = await renderPane();
      comp.aggregate = makeAggregateRef();

      comp.openCalculateDialog();

      expect(capturedDialog).toBeTruthy();

      await waitFor(() => {
         expect(capturedDialog.percOfDims).toEqual([{ data: "Product", label: "Product" }]);
      });
      await waitFor(() => {
         expect(capturedDialog.valueOfDatas).toEqual([{ data: "ShipDate", label: "Ship Date" }]);
      });
      await waitFor(() => {
         expect(capturedDialog.supportResetMap).toEqual({ Quarter: false });
      });
      await waitFor(() => {
         expect(capturedDialog.resetOptsMap.get(CalculatorConstants.INNER_DIMENSION))
            .toEqual([{ label: "Day", data: 4 }]);
      });
   });
});

describe("Group 4 - crosstab helpers", () => {
   it("should report row and column presence for crosstab bindings", async () => {
      const bindingService = makeBindingServiceStub({
         objectType: "VSCrosstab",
         bindingModel: {
            rows: [{ name: "Region" }],
            cols: [],
         } as CrosstabBindingModel,
      });
      const { comp } = await renderPane({ bindingService });
      comp.aggregate = makeAggregateRef();

      expect(comp.hasRow()).toBe(true);
      expect(comp.hasCol()).toBe(false);
   });

   it("should return the aggregate name from the current aggregate ref", async () => {
      const { comp } = await renderPane();
      const agg = makeAggregateRef();
      comp.aggregate = agg;

      expect(comp.getAggregateName()).toBe("Sum(Sales)");
   });
});
