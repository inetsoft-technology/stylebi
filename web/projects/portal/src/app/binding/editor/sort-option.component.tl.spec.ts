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
 * SortOption — single pass (+竞态 +边界)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — ngOnInit/getSortOrders: base, value, and manual sort options
 *   Group 2 [Risk 3] — changeOrderType: manual vs numeric order and sortByCol seeding
 *   Group 3 [Risk 3] — changeRankingOption: top/bottom N syncs value sort direction
 *   Group 4 [Risk 2] — isSortEnabled/isRankingColEnabled: timeSeries and aggregate guards
 *   Group 5 [Risk 3] — openDialog: check-variables then availableValues manual order assembly
 *   Group 6 [Risk 2] — getCurrentOrder: manual order detection from manualOrder list
 *
 * HTTP: MSW inline server.use() for check-variables and availableValues endpoints
 *
 * Out of scope:
 *   sortItemVisible / trackByFn — template helpers with trivial logic
 */

import { HttpParams, provideHttpClient } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render, waitFor } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { http, HttpResponse } from "msw";
import { server } from "@test-mocks/server";
import { UIContextService } from "../../common/services/ui-context.service";
import { StyleConstants } from "../../common/util/style-constants";
import { ComponentTool } from "../../common/util/component-tool";
import { XSchema } from "../../common/data/xschema";
import { TestUtils } from "../../common/test/test-utils";
import { ModelService } from "../../widget/services/model.service";
import { BindingService } from "../services/binding.service";
import { SortOption } from "./sort-option.component";

const bindingModel = TestUtils.createMockBindingModel("chart");
bindingModel.source = { source: "test", type: 1 } as any;

const bindingServiceMock = {
   assemblyName: "Chart1",
   bindingModel,
   getURLParams: vi.fn(() => new HttpParams().set("vsId", "vs1").set("assemblyName", "Chart1")),
};

const modalMock = { open: vi.fn() };

function createDimension() {
   const dim = TestUtils.createMockBDimensionRef("state");
   dim.order = StyleConstants.SORT_ASC;
   const aggMap: any = { label: "Sum(sales)", value: "Sum(sales)" };
   dim.sortOptionModel = { aggregateRefs: [aggMap] };
   dim.manualOrder = null;
   dim.rankingOption = null;
   return dim;
}

async function renderSortOption(props: Record<string, unknown> = {}) {
   const dimension = (props.dimension as any) ?? createDimension();
   const { fixture } = await render(SortOption, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         provideHttpClient(),
         ModelService,
         { provide: BindingService, useValue: bindingServiceMock },
         { provide: UIContextService, useValue: { isAdhoc: vi.fn() } },
         { provide: NgbModal, useValue: modalMock },
      ],
      componentProperties: {
         dimension,
         sortSupported: true,
         rankingSupported: true,
         ...props,
      },
   });
   return { fixture, comp: fixture.componentInstance as SortOption, dimension };
}

afterEach(() => vi.restoreAllMocks());

describe("SortOption — single pass", () => {

   describe("Group 1 — ngOnInit / getSortOrders [Risk 2]", () => {
      it("should include value sort options when aggregates exist", async () => {
         const { comp } = await renderSortOption();

         const values = comp.getSortOrders().map(o => o.value);
         expect(values).toContain(StyleConstants.SORT_VALUE_ASC);
         expect(values).toContain("manual");
      });

      it("should omit value sort options when aggregates are empty", async () => {
         const dim = createDimension();
         dim.sortOptionModel = { aggregateRefs: [] };
         const { comp } = await renderSortOption({ dimension: dim });

         const values = comp.getSortOrders().map(o => o.value);
         expect(values).not.toContain(StyleConstants.SORT_VALUE_ASC);
      });
   });

   describe("Group 2 — changeOrderType [Risk 3]", () => {
      it("should set manual specific order type for manual selection", async () => {
         const { comp, dimension } = await renderSortOption();

         comp.changeOrderType("manual");

         expect(dimension.order).toBe(StyleConstants.SORT_SPECIFIC);
         expect(dimension.specificOrderType).toBe("manual");
         expect(comp.currentOrder).toBe("manual");
      });

      it("should seed sortByCol for value-based sort orders", async () => {
         const { comp, dimension } = await renderSortOption();

         comp.changeOrderType(StyleConstants.SORT_VALUE_DESC);

         expect(dimension.sortByCol).toBe("Sum(sales)");
      });
   });

   describe("Group 3 — changeRankingOption [Risk 3]", () => {
      it("should sync descending value sort for top N ranking", async () => {
         const { comp, dimension } = await renderSortOption();

         comp.changeRankingOption(String(StyleConstants.TOP_N));

         expect(dimension.rankingOption).toBe(String(StyleConstants.TOP_N));
         expect(dimension.order & StyleConstants.SORT_VALUE_DESC).toBe(StyleConstants.SORT_VALUE_DESC);
         expect(dimension.sortByCol).toBe("Sum(sales)");
      });
   });

   describe("Group 4 — isSortEnabled / isRankingColEnabled [Risk 2]", () => {
      it("should disable sort for date dimensions in time series mode", async () => {
         const dim = createDimension();
         dim.dataType = XSchema.DATE;
         const { comp } = await renderSortOption({ dimension: dim, timeSeries: true });

         expect(comp.isSortEnabled()).toBe(false);
      });

      it("should enable ranking column when ranking option is set", async () => {
         const dim = createDimension();
         dim.rankingOption = String(StyleConstants.TOP_N);
         const { comp } = await renderSortOption({ dimension: dim });

         expect(comp.isRankingColEnabled()).toBe(true);
      });

      it("should disable ranking column when ranking is none", async () => {
         const { comp } = await renderSortOption();

         expect(comp.isRankingColEnabled()).toBe(false);
      });
   });

   describe("Group 5 — openDialog [Risk 3]", () => {
      it("should load available values and open manual ordering dialog", async () => {
         const { comp, dimension } = await renderSortOption();
         const dialogInstance: any = { manualOrders: null, valueLabelList: null };
         vi.spyOn(ComponentTool, "showDialog").mockReturnValue(dialogInstance);

         comp.openDialog();

         await waitFor(() => {
            expect(ComponentTool.showDialog).toHaveBeenCalled();
            expect(comp.manualOrders).toEqual(["East", "West"]);
         });
      });

      it("should open variable dialog when check-variables returns entries", async () => {
         server.use(
            http.get("*/api/vsdata/check-variables", () =>
               HttpResponse.json([{ name: "var1", type: "string" }])
            )
         );
         const { comp } = await renderSortOption();
         const varDialog: any = { model: null };
         vi.spyOn(ComponentTool, "showDialog").mockReturnValue(varDialog);

         comp.openDialog();

         await waitFor(() => expect(ComponentTool.showDialog).toHaveBeenCalled());
      });
   });

   describe("Group 6 — getCurrentOrder [Risk 2]", () => {
      it("should return manual when manualOrder has values", async () => {
         const dim = createDimension();
         dim.manualOrder = ["East", "West"];
         const { comp } = await renderSortOption({ dimension: dim });

         expect(comp.getCurrentOrder()).toBe("manual");
      });
   });
});
