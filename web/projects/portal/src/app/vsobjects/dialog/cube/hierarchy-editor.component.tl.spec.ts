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
 * HierarchyEditor - single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - selectedColumn setter: DATE / TIME / TIME_INSTANT / invalid-input guards
 *   Group 2 [Risk 2] - ngOnInit: date level list seed and examples-service request wiring
 *   Group 3 [Risk 2] - getDateLevelExample: lookup by configured date-level order
 *   Group 4 [Risk 1] - template binding: radio selection updates selectedMember.option
 *   Group 5 [Risk 1] - DateRangeRef getter passthroughs
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope:
 *   DateLevelExamplesService HTTP integration is not exercised because this component depends
 *   only on the service contract, not on direct HttpClient behavior.
 *
 * Mocking strategy:
 *   - ATL render for template-driven FormsModule radio binding
 *   - DateLevelExamplesService replaced with a vi.fn() observable stub
 */

import { render, screen } from "@testing-library/angular";
import userEvent from "@testing-library/user-event";
import { of as observableOf } from "rxjs";

import { DateLevelExamplesService } from "../../../common/services/date-level-examples.service";
import { XSchema } from "../../../common/data/xschema";
import { DateRangeRef } from "../../../common/util/date-range-ref";
import { HierarchyEditor } from "./hierarchy-editor.component";
import { VSDimensionMemberModel } from "../../model/vs-dimension-member-model";
import { OutputColumnRefModel } from "../../model/output-column-ref-model";
import { HierarchyEditorModel } from "../../model/hierarchy-editor-model";

afterEach(() => vi.restoreAllMocks());

function makeDataRef(overrides: Partial<OutputColumnRefModel> = {}): OutputColumnRefModel {
   return {
      entity: "",
      attribute: "order_date",
      dataType: XSchema.STRING,
      refType: 0,
      ...overrides,
   };
}

function makeMember(overrides: Partial<VSDimensionMemberModel> = {}): VSDimensionMemberModel {
   return {
      option: 1,
      dataRef: makeDataRef(),
      ...overrides,
   };
}

function createExamplesService(examples: string[] = []) {
   return {
      loadDateLevelExamples: vi.fn().mockReturnValue(
         observableOf({ dateLevelExamples: examples })
      ),
   };
}

async function renderComponent(
   componentInputs: Partial<{ model: HierarchyEditorModel; selectedColumn: unknown }> = {},
   examples: string[] = []
) {
   const examplesService = createExamplesService(examples);
   const renderResult = await render(HierarchyEditor, {
      componentInputs: {
         model: {},
         ...componentInputs,
      },
      providers: [{ provide: DateLevelExamplesService, useValue: examplesService }],
   });
   return { ...renderResult, comp: renderResult.fixture.componentInstance, examplesService };
}

describe("HierarchyEditor - Group 1: selectedColumn setter", () => {
   it("should enable only date fieldsets for a DATE member", async () => {
      const member = makeMember({ dataRef: makeDataRef({ dataType: XSchema.DATE }) });
      const { comp } = await renderComponent({ selectedColumn: member });

      expect(comp.selectedMember).toBe(member);
      expect(comp.disableDate).toBe(false);
      expect(comp.disableTime).toBe(true);
   });

   it("should enable only time fieldsets for a TIME member", async () => {
      const member = makeMember({ dataRef: makeDataRef({ dataType: XSchema.TIME }) });
      const { comp } = await renderComponent({ selectedColumn: member });

      expect(comp.selectedMember).toBe(member);
      expect(comp.disableDate).toBe(true);
      expect(comp.disableTime).toBe(false);
   });

   it("should enable both date and time fieldsets for a TIME_INSTANT member", async () => {
      const member = makeMember({ dataRef: makeDataRef({ dataType: XSchema.TIME_INSTANT }) });
      const { comp } = await renderComponent({ selectedColumn: member });

      expect(comp.selectedMember).toBe(member);
      expect(comp.disableDate).toBe(false);
      expect(comp.disableTime).toBe(false);
   });

   it("should reset to an empty selected member when selectedColumn is not a valid option-bearing member", async () => {
      const { comp } = await renderComponent({ selectedColumn: { dataRef: makeDataRef({ dataType: XSchema.DATE }) } });

      expect(comp.selectedMember).toEqual({});
      expect(comp.disableDate).toBe(true);
      expect(comp.disableTime).toBe(true);
   });

   it("should reset to an empty selected member when selectedColumn is null", async () => {
      const { comp } = await renderComponent({ selectedColumn: null });

      expect(comp.selectedMember).toEqual({});
      expect(comp.disableDate).toBe(true);
      expect(comp.disableTime).toBe(true);
   });

   it("should treat option=0 as a falsy guard path and reset the selected member", async () => {
      const zeroOptionMember = makeMember({
         option: 0,
         dataRef: makeDataRef({ dataType: XSchema.TIME_INSTANT }),
      });
      const { comp } = await renderComponent({ selectedColumn: zeroOptionMember });

      expect(comp.selectedMember).toEqual({});
      expect(comp.disableDate).toBe(true);
      expect(comp.disableTime).toBe(true);
   });
});

describe("HierarchyEditor - Group 2: ngOnInit", () => {
   it("should seed dateLevels in DateRangeRef order and load examples for timeInstant", async () => {
      const examples = ["Y", "Q", "M"];
      const { comp, examplesService } = await renderComponent({}, examples);

      expect(comp.dateLevels).toEqual([
         DateRangeRef.QUARTER_OF_YEAR_PART + "",
         DateRangeRef.MONTH_OF_YEAR_PART + "",
         DateRangeRef.WEEK_OF_YEAR_PART + "",
         DateRangeRef.DAY_OF_MONTH_PART + "",
         DateRangeRef.DAY_OF_WEEK_PART + "",
         DateRangeRef.HOUR_OF_DAY_PART + "",
         DateRangeRef.MINUTE_OF_HOUR_PART + "",
         DateRangeRef.SECOND_OF_MINUTE_PART + "",
         DateRangeRef.YEAR_INTERVAL + "",
         DateRangeRef.QUARTER_INTERVAL + "",
         DateRangeRef.MONTH_INTERVAL + "",
         DateRangeRef.WEEK_INTERVAL + "",
         DateRangeRef.DAY_INTERVAL + "",
         DateRangeRef.HOUR_INTERVAL + "",
         DateRangeRef.MINUTE_INTERVAL + "",
         DateRangeRef.SECOND_INTERVAL + "",
      ]);
      expect(examplesService.loadDateLevelExamples).toHaveBeenCalledWith(comp.dateLevels, "timeInstant");
      expect(comp.dateLevelExamples).toEqual(examples);
   });
});

describe("HierarchyEditor - Group 3: getDateLevelExample", () => {
   it("should return the example aligned with the matching date level", async () => {
      const { comp } = await renderComponent({}, ["quarter", "month", "week"]);
      comp.dateLevels = [
         DateRangeRef.QUARTER_OF_YEAR_PART + "",
         DateRangeRef.MONTH_OF_YEAR_PART + "",
         DateRangeRef.WEEK_OF_YEAR_PART + "",
      ];

      expect(comp.getDateLevelExample(DateRangeRef.MONTH_OF_YEAR_PART)).toBe("month");
   });

   it("should return undefined when the requested level is not present", async () => {
      const { comp } = await renderComponent({}, ["quarter"]);
      comp.dateLevels = [DateRangeRef.QUARTER_OF_YEAR_PART + ""];

      expect(comp.getDateLevelExample(DateRangeRef.DAY_INTERVAL)).toBeUndefined();
   });
});

describe("HierarchyEditor - Group 4: template binding", () => {
   it("should update selectedMember.option when the user selects a different radio option", async () => {
      const user = userEvent.setup();
      const member = makeMember({
         option: DateRangeRef.YEAR_INTERVAL,
         dataRef: makeDataRef({ dataType: XSchema.TIME_INSTANT }),
      });
      const { comp } = await renderComponent({ selectedColumn: member });

      await user.click(screen.getByLabelText("_#(Minute)"));

      expect(comp.selectedMember.option).toBe(DateRangeRef.MINUTE_INTERVAL);
   });
});

describe("HierarchyEditor - Group 5: DateRangeRef getters", () => {
   it("should expose the expected DateRangeRef constants through getters", async () => {
      const { comp } = await renderComponent();

      expect({
         yearInterval: comp.yearInterval,
         quarterOfYearPart: comp.quarterOfYearPart,
         monthOfYearPart: comp.monthOfYearPart,
         weekOfYearPart: comp.weekOfYearPart,
         dayOfMonthPart: comp.dayOfMonthPart,
         dayOfWeekPart: comp.dayOfWeekPart,
         hourOfDayPart: comp.hourOfDayPart,
         minuteOfHourPart: comp.minuteOfHourPart,
         secondOfMinutePart: comp.secondOfMinutePart,
         quarterInterval: comp.quarterInterval,
         monthInterval: comp.monthInterval,
         weekInterval: comp.weekInterval,
         dayInterval: comp.dayInterval,
         hourInterval: comp.hourInterval,
         minuteInterval: comp.minuteInterval,
         secondInterval: comp.secondInterval,
      }).toEqual({
         yearInterval: DateRangeRef.YEAR_INTERVAL,
         quarterOfYearPart: DateRangeRef.QUARTER_OF_YEAR_PART,
         monthOfYearPart: DateRangeRef.MONTH_OF_YEAR_PART,
         weekOfYearPart: DateRangeRef.WEEK_OF_YEAR_PART,
         dayOfMonthPart: DateRangeRef.DAY_OF_MONTH_PART,
         dayOfWeekPart: DateRangeRef.DAY_OF_WEEK_PART,
         hourOfDayPart: DateRangeRef.HOUR_OF_DAY_PART,
         minuteOfHourPart: DateRangeRef.MINUTE_OF_HOUR_PART,
         secondOfMinutePart: DateRangeRef.SECOND_OF_MINUTE_PART,
         quarterInterval: DateRangeRef.QUARTER_INTERVAL,
         monthInterval: DateRangeRef.MONTH_INTERVAL,
         weekInterval: DateRangeRef.WEEK_INTERVAL,
         dayInterval: DateRangeRef.DAY_INTERVAL,
         hourInterval: DateRangeRef.HOUR_INTERVAL,
         minuteInterval: DateRangeRef.MINUTE_INTERVAL,
         secondInterval: DateRangeRef.SECOND_INTERVAL,
      });
   });
});
