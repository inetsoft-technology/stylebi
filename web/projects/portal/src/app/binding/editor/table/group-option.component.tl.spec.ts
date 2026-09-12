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
 * GroupOption — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — applyClick: manual-order reset when specific sort is empty
 *   Group 2 [Risk 2] — getDateLevelOpts / ngOnInit: date, time, and datetime option lists
 *   Group 3 [Risk 2] — isLast / hasAgg / isDateType: crosstab position and type guards
 *   Group 4 [Risk 2] — summarize / summarizeCheck: table vs crosstab group-total storage
 *   Group 5 [Risk 3] — dateInterval / dateLevelChange: clamp, order reset, manual-order clear
 *   Group 6 [Risk 2] — timeSeriesSupported / toggleTimeSeries: outer dim and detail visibility
 *
 * HTTP: DateLevelExamplesService via direct mock (no network in these tests)
 *
 * Out of scope:
 *   getSummarizeLabel / changeSummarizeValue — thin wrappers over summarize getter/setter
 *   getFieldGourpTotalKey — covered via summarize crosstab path
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { of } from "rxjs";
import { DataRefType } from "../../../common/data/data-ref-type";
import { DateLevelExamplesService } from "../../../common/services/date-level-examples.service";
import { UIContextService } from "../../../common/services/ui-context.service";
import { TestUtils } from "../../../common/test/test-utils";
import { StyleConstants } from "../../../common/util/style-constants";
import { XConstants } from "../../../common/util/xconstants";
import { XSchema } from "../../../common/data/xschema";
import { BindingService } from "../../services/binding.service";
import { CrosstabBindingModel } from "../../data/table/crosstab-binding-model";
import { GroupOption } from "./group-option.component";

const examplesServiceMock = {
   loadDateLevelExamples: vi.fn(() => of({ dateLevelExamples: ["2024"] })),
};

const uiContextMock = {
   isAdhoc: vi.fn().mockReturnValue(false),
};

function createBindingService(objectType: string, model: any) {
   return {
      objectType,
      getBindingModel: vi.fn(() => model),
   };
}

function createCrosstabModel(): CrosstabBindingModel {
   const model = TestUtils.createMockCrosstabBindingModel();
   model.rows = [TestUtils.createMockBDimensionRef("state"), TestUtils.createMockBDimensionRef("city")];
   model.cols = [TestUtils.createMockBDimensionRef("region")];
   model.aggregates = [TestUtils.createMockBAggregateRef("sales")];
   model.suppressGroupTotal = {};
   model.option = { summaryOnly: true } as any;
   return model;
}

async function renderGroupOption(props: Record<string, any> = {}) {
   const objectType = props["_objectType"] ?? "crosstab";
   const model = props["_model"] ?? createCrosstabModel();
   const field = props.field ?? TestUtils.createMockBDimensionRef("state");
   field.dataType = props["_dataType"] ?? XSchema.DATE;
   field.dateLevel = props["_dateLevel"] ?? XConstants.YEAR_DATE_GROUP + "";
   field.dateInterval = props["_dateInterval"] ?? 1;
   field.order = props["_order"] ?? StyleConstants.SORT_ASC;
   field.manualOrder = props["_manualOrder"] ?? null;
   field.specificOrderType = props["_specificOrderType"] ?? null;
   field.timeSeries = props["_timeSeries"] ?? false;
   field.summarize = props["_summarize"] ?? "true";

   const renderProps = {
      field,
      fieldType: props.fieldType ?? "rows",
      dragIndex: props.dragIndex ?? 1,
      vsId: "vs1",
      variables: [],
      isOuterDimRef: props.isOuterDimRef ?? false,
      source: model.source,
   };

   const { fixture } = await render(GroupOption, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: BindingService, useValue: createBindingService(objectType, model) },
         { provide: UIContextService, useValue: uiContextMock },
         { provide: DateLevelExamplesService, useValue: examplesServiceMock },
      ],
      componentProperties: renderProps,
   });

   return { comp: fixture.componentInstance as GroupOption, field, model };
}

afterEach(() => vi.restoreAllMocks());

describe("GroupOption — single pass", () => {

   describe("Group 1 — applyClick [Risk 2]", () => {
      it("should reset specific manual sort when manual order is empty", async () => {
         const { comp, field } = await renderGroupOption({
            _order: StyleConstants.SORT_SPECIFIC,
            _specificOrderType: "manual",
            _manualOrder: [],
         });
         const applySpy = vi.spyOn(comp.apply, "emit");

         comp.applyClick();

         expect(field.order).toBe(StyleConstants.SORT_NONE);
         expect(applySpy).toHaveBeenCalledWith(false);
      });

      it("should clear manual order when sort is not specific", async () => {
         const { comp } = await renderGroupOption({
            _order: StyleConstants.SORT_DESC,
         });
         comp.field.manualOrder = ["A", "B"];

         comp.applyClick();

         expect(comp.field.manualOrder).toBeNull();
      });
   });

   describe("Group 2 — getDateLevelOpts / ngOnInit [Risk 2]", () => {
      it("should load date-level examples on init", async () => {
         const { comp } = await renderGroupOption({ _dataType: XSchema.DATE });

         expect(examplesServiceMock.loadDateLevelExamples).toHaveBeenCalled();
         expect(comp.dateLevelExamples).toEqual(["2024"]);
      });

      it("should expose date-level options for date fields", async () => {
         const { comp } = await renderGroupOption({ _dataType: XSchema.DATE });
         expect(comp.getDateLevelOpts()).toBe(comp.dateGroups);
      });

      it("should expose time-level options for time fields", async () => {
         const { comp } = await renderGroupOption({ _dataType: XSchema.TIME });
         expect(comp.getDateLevelOpts()).toBe(comp.timeGroups);
      });

      it("should expose datetime-level options for time-instant fields", async () => {
         const { comp } = await renderGroupOption({ _dataType: XSchema.TIME_INSTANT });
         expect(comp.getDateLevelOpts()).toBe(comp.dateTimeGroups);
      });
   });

   describe("Group 3 — isLast / hasAgg / isDateType [Risk 2]", () => {
      it("should detect last row dimension on crosstab", async () => {
         const { comp } = await renderGroupOption({ fieldType: "rows", dragIndex: 1 });

         expect(comp.isLast()).toBe(true);
         expect(comp.hasAgg()).toBe(true);
      });

      it("should treat table binding as never last and detect date types", async () => {
         const model = TestUtils.createMockTableBindingModel();
         model.aggregates = [];
         const { comp } = await renderGroupOption({
            _objectType: "table",
            _model: model,
            fieldType: "groups",
            dragIndex: 0,
         });

         expect(comp.isTable()).toBe(true);
         expect(comp.isLast()).toBe(false);
         expect(comp.isDateType()).toBe(true);
      });

      it("should exclude cube date fields from date-type grouping", async () => {
         const { comp, field } = await renderGroupOption({ _dataType: XSchema.DATE });
         field.refType = DataRefType.CUBE;

         expect(comp.isDateType()).toBe(false);
      });
   });

   describe("Group 4 — summarize / summarizeCheck [Risk 2]", () => {
      it("should read and write summarize on table fields", async () => {
         const model = TestUtils.createMockTableBindingModel();
         const { comp, field } = await renderGroupOption({
            _objectType: "table",
            _model: model,
            _summarize: "false",
         });

         expect(comp.summarize).toBe("false");
         comp.summarizeCheck = true;
         expect(field.summarize).toBe("true");
      });

      it("should store crosstab group totals in suppressGroupTotal map", async () => {
         const model = createCrosstabModel();
         const { comp } = await renderGroupOption({
            _model: model,
            fieldType: "rows",
            dragIndex: 1,
         });

         comp.summarize = "false";

         expect(model.suppressGroupTotal["state:rows1"]).toBe(true);
         expect(comp.summarizeCheck).toBe(false);
      });
   });

   describe("Group 5 — dateInterval / dateLevelChange [Risk 3]", () => {
      it("should clamp date interval to the level maximum", async () => {
         const { comp, field } = await renderGroupOption({
            _dateLevel: XConstants.QUARTER_DATE_GROUP + "",
            _dateInterval: 1,
         });

         comp.dateInterval = 99;

         expect(field.dateInterval).toBe(4);
      });

      it("should clear manual order when date level changes under specific sort", async () => {
         const { comp, field } = await renderGroupOption({
            _order: StyleConstants.SORT_SPECIFIC,
            _manualOrder: ["A"],
            _dateLevel: XConstants.YEAR_DATE_GROUP + "",
         });

         comp.dateLevelChange(XConstants.MONTH_DATE_GROUP + "");

         expect(field.manualOrder).toBeNull();
         expect(field.dateLevel).toBe(XConstants.MONTH_DATE_GROUP + "");
      });

      it("should disable time series when date level is none", async () => {
         const { comp, field } = await renderGroupOption({
            _order: StyleConstants.SORT_SPECIFIC,
            _dateLevel: XConstants.YEAR_DATE_GROUP + "",
            _timeSeries: true,
         });
         field.timeSeries = true;

         comp.dateLevelChange(XConstants.NONE_DATE_GROUP + "");

         expect(field.timeSeries).toBe(false);
         expect(field.order).toBe(StyleConstants.SORT_ASC);
      });
   });

   describe("Group 6 — timeSeriesSupported / toggleTimeSeries [Risk 2]", () => {
      it("should support time series for inner date dimensions with summary-only binding", async () => {
         const { comp } = await renderGroupOption({
            isOuterDimRef: false,
            _dateLevel: XConstants.YEAR_DATE_GROUP + "",
         });

         expect(comp.timeSeriesSupported()).toBe(true);
      });

      it("should not support time series for outer dimensions", async () => {
         const { comp } = await renderGroupOption({ isOuterDimRef: true });

         expect(comp.timeSeriesSupported()).toBe(false);
      });

      it("should reset interval and order when enabling time series", async () => {
         const { comp, field } = await renderGroupOption({
            _dateLevel: XConstants.YEAR_DATE_GROUP + "",
            _order: StyleConstants.SORT_DESC,
         });

         comp.toggleTimeSeries(true);

         expect(field.dateInterval).toBe(1);
         expect(field.order).toBe(StyleConstants.SORT_ASC);
      });
   });
});
