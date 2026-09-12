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
 * ParameterPage - single-pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - ok(): validation guard, date/time transformation, and submit payload
 *   Group 2 [Risk 2] - layout getters and radio defaults
 *   Group 3 [Risk 2] - openDateTimeValue dialog wiring and changeValue
 *   Group 4 [Risk 2] - clear(), cancel(), canSubmit(), and decimal validation
 */

import { ComponentTool } from "../../../common/util/component-tool";
import { DateTypeFormatter } from "../../../../../../shared/util/date-type-formatter";
import { ParameterPageModel } from "../parameter-page-model";
import {
   ChoiceParameterModel,
   ListParameterModel,
   OptionParameterModel,
   RepletParameterModel,
} from "../replet-parameter-model";
import { ParameterPage } from "./parameter-page.component";

function makeSimpleParameter(
   overrides: Partial<RepletParameterModel> = {},
): RepletParameterModel {
   return {
      name: "simple",
      alias: "",
      value: "123",
      type: "SimpleParameter",
      tooltip: "",
      multi: false,
      decimalType: false,
      required: false,
      ...overrides,
   };
}

function makeOptionParameter(
   overrides: Partial<OptionParameterModel> = {},
): OptionParameterModel {
   return {
      ...makeSimpleParameter({
         name: "option",
         type: "OptionParameter",
         value: null,
      }),
      choicesLabel: ["A", "B"],
      choicesValue: ["A", "B"],
      dataTruncated: false,
      selectedValues: [true, false],
      ...overrides,
   };
}

function makeListParameter(
   overrides: Partial<ListParameterModel> = {},
): ListParameterModel {
   return {
      ...makeSimpleParameter({
         name: "list",
         type: "ListParameter",
         value: null,
      }),
      choicesLabel: ["A", "B"],
      choicesValue: ["A", "B"],
      dataTruncated: false,
      values: ["A"],
      ...overrides,
   };
}

function makeChoiceParameter(
   overrides: Partial<ChoiceParameterModel> = {},
): ChoiceParameterModel {
   return {
      ...makeSimpleParameter({
         name: "radio",
         type: "RadioParameter",
         value: null,
      }),
      choicesLabel: ["One", "Two"],
      choicesValue: ["1", "2"],
      dataTruncated: false,
      ...overrides,
   };
}

function makePageModel(overrides: Partial<ParameterPageModel> = {}): ParameterPageModel {
   return {
      pageType: "parameters",
      reportTitle: "Orders",
      reportDesc: "",
      paramValues: new Map(),
      params: [],
      footerText: "",
      ...overrides,
   };
}

afterEach(() => {
   vi.restoreAllMocks();
});

describe("ParameterPage - single pass", () => {
   describe("Group 1 - ok submit and validation", () => {
      it("should transform date and multi-value simple parameters before emitting onSubmit", () => {
         const comp = new ParameterPage({ open: vi.fn() } as never);
         comp.pageModel = makePageModel({
            params: [
               makeSimpleParameter({
                  name: "date",
                  type: "DateParameter",
                  value: "2024-02-03",
               }),
               makeSimpleParameter({
                  name: "multi",
                  value: "a,b",
                  multi: true,
               }),
            ],
         });
         const submitSpy = vi.spyOn(comp.onSubmit, "emit");

         comp.ok();

         expect(submitSpy).toHaveBeenCalledWith([
            expect.objectContaining({ name: "date", value: "2024-02-03 00:00:00" }),
            expect.objectContaining({ name: "multi", value: "^[a,b]^" }),
         ]);
      });

      it("should stop submit and show an error dialog when a date parameter value is invalid", () => {
         const comp = new ParameterPage({ open: vi.fn() } as never);
         comp.pageModel = makePageModel({
            params: [
               makeSimpleParameter({
                  name: "shipDate",
                  alias: "Ship Date",
                  type: "DateParameter",
                  value: "bad-date",
               }),
            ],
         });
         vi.spyOn(DateTypeFormatter, "toTimeInstant").mockReturnValue(null);
         const messageSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockResolvedValue(undefined);
         const submitSpy = vi.spyOn(comp.onSubmit, "emit");

         comp.ok();

         expect(messageSpy).toHaveBeenCalledWith(
            expect.anything(),
            "_#(js:Error)",
            "_#(js:viewer.wrongDateFmt.note4)_*Ship Date,YYYY-MM-DD",
         );
         expect(submitSpy).not.toHaveBeenCalled();
      });
   });

   describe("Group 2 - page state helpers", () => {
      it("should derive side pane and width classes from report description visibility", () => {
         const comp = new ParameterPage({ open: vi.fn() } as never);
         comp.pageModel = makePageModel({ reportDesc: "desc" });

         expect(comp.sidePaneVisible).toBe(true);
         expect(comp.formWidthClasses).toBe("col-lg-9 col-md-10");
         expect(comp.fieldWidthClasses).toBe("col-xl-6 col-lg-8 col-md-10 col-sm-12");
      });

      it("should initialize missing radio values from the first choice", () => {
         const comp = new ParameterPage({ open: vi.fn() } as never);
         const pageModel = makePageModel({
            params: [makeChoiceParameter({ choicesValue: ["1", "2"], value: null })],
         });

         comp.initRadioDefaultValue(pageModel);

         expect(pageModel.params[0].value).toBe("1");
      });
   });

   describe("Group 3 - dialog and value updates", () => {
      it("should open DateTimeValueDialog and append multi date values on commit", () => {
         const comp = new ParameterPage({ open: vi.fn() } as never);
         const param = makeSimpleParameter({
            type: "DateTimeParameter",
            value: "2024-01-01 00:00:00",
            multi: true,
         });
         const dialogStub: Record<string, unknown> = {};
         const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockImplementation(
            (_modal, _type, onCommit, _options, onCancel) => {
               onCommit("2024-01-02 00:00:00");
               onCancel("noop");
               return dialogStub as never;
            },
         );

         comp.openDateTimeValue(param);

         expect(showDialogSpy).toHaveBeenCalled();
         expect(dialogStub).toEqual(
            expect.objectContaining({
               promptTime: true,
               promptDate: true,
               format: "YYYY-MM-DD HH:mm:ss",
               date: "2024-01-01 00:00:00,2024-01-02 00:00:00",
            }),
         );
         expect(param.value).toBe("2024-01-01 00:00:00,2024-01-02 00:00:00");
      });

      it("should mark a parameter as changed when changeValue is called", () => {
         const comp = new ParameterPage({ open: vi.fn() } as never);
         const param = makeSimpleParameter({ value: "old", changed: false });

         comp.changeValue("new", param);

         expect(param.value).toBe("new");
         expect(param.changed).toBe(true);
      });
   });

   describe("Group 4 - clear, cancel, and submit guards", () => {
      it("should clear option, list, and scalar parameter values by type", () => {
         const comp = new ParameterPage({ open: vi.fn() } as never);
         comp.pageModel = makePageModel({
            params: [
               makeOptionParameter(),
               makeListParameter(),
               makeSimpleParameter({ value: "x" }),
            ],
         });

         comp.clear();

         expect((comp.pageModel.params[0] as OptionParameterModel).selectedValues).toEqual([false, false]);
         expect((comp.pageModel.params[1] as ListParameterModel).values).toEqual([]);
         expect(comp.pageModel.params[2].value).toBeNull();
      });

      it("should block submit when a required parameter is empty", () => {
         const comp = new ParameterPage({ open: vi.fn() } as never);
         comp.pageModel = makePageModel({
            params: [
               makeSimpleParameter({ required: true, value: "" }),
            ],
         });

         expect(comp.canSubmit()).toBe(false);
      });

      it("should block submit when a decimal parameter has an invalid value", () => {
         const comp = new ParameterPage({ open: vi.fn() } as never);
         comp.pageModel = makePageModel({
            params: [
               makeSimpleParameter({ decimalType: true, value: "abc" }),
            ],
         });

         expect(comp.canSubmit()).toBe(false);
      });

      it("should emit null on cancel", () => {
         const comp = new ParameterPage({ open: vi.fn() } as never);
         comp.pageModel = makePageModel({ params: [] });
         const submitSpy = vi.spyOn(comp.onSubmit, "emit");

         comp.cancel();

         expect(submitSpy).toHaveBeenCalledWith(null);
      });
   });
});
