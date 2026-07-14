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
 * ComboBoxEditor — single pass
 *
 * Direct instantiation — two constructor dependencies (DialogService, HttpClient),
 * neither with `inject()` calls.
 *
 * Scope: ngOnInit, ngOnChanges, showSelectionListDialog/showVariableListDialog (modal
 * open + resolve/reject), updatDefaultValues (HTTP POST + label-prefix + stale-default
 * reset, private — exercised through its public callers), updateType, resetValid,
 * calendarEnabled, updateCalendar, toggleQuery, onDateRangeChanged, onDefaultvalueChanged,
 * currentPattern, validateDateValue, validateDateRange, showDateRangeWarning,
 * showDateRanges, changeNodefault, isDefaultValue, datePrompt, dropdownMinWidth.
 *
 * Risk-first coverage:
 *   Group 4 [Risk 3] — updatDefaultValues (via showSelectionListDialog/toggleQuery):
 *                       the HTTP POST + label-prefix + stale-default-reset side effects
 *   Group 9 [Risk 3] — validateDateRange: digit-by-digit min/max comparison, the most
 *                       intricate pure logic in the component
 *   Group 8 [Risk 2] — onDateRangeChanged / onDefaultvalueChanged: the isInputValid
 *                       emission gates that drive the rest of the dialog's validity
 *   Group 6 [Risk 2] — updateCalendar: embedded vs non-embedded dispatch, range cleanup
 *   Remaining groups [Risk 1/2] — single-purpose getters/setters and dispatch tables
 *
 * Confirmed bugs (it.fails): none
 *
 * Mocking strategy: DialogService.open() is mocked to return `{ result: Promise }`,
 * matching the NgbModalRef-like shape the component calls `.result.then(onResolve,
 * onReject)` on. HttpClient.post() is mocked to return `of(...)` so the POST completes
 * synchronously within the test.
 */

import { of } from "rxjs";
import { XSchema } from "../../common/data/xschema";
import { ComboBoxEditor } from "./combo-box-editor.component";
import { ComboBoxEditorModel } from "../model/combo-box-editor-model";
import { ComboBoxDefaultValueListModel } from "../model/combo-box-queryList-model";
import { DialogService } from "../../widget/slide-out/dialog-service.service";
import { HttpClient } from "@angular/common/http";

afterEach(() => vi.restoreAllMocks());

function makeModel(overrides: Partial<ComboBoxEditorModel> = {}): ComboBoxEditorModel {
   return Object.assign({
      type: "",
      embedded: false,
      query: false,
      noDefault: true,
      valid: true,
      dataType: XSchema.STRING,
      calendar: false,
      defaultValue: null,
      minDate: "",
      maxDate: "",
      selectionListDialogModel: {
         selectionListEditorModel: {
            table: "", column: "col", value: "col", dataType: XSchema.STRING,
            tables: [], localizedTables: [], form: false, ltableDescriptions: [],
         },
      },
      variableListDialogModel: { labels: [], values: [], dataType: XSchema.STRING } as any,
   }, overrides);
}

function makeValueListEntry(overrides: Partial<ComboBoxDefaultValueListModel> = {}): ComboBoxDefaultValueListModel {
   return Object.assign({ label: "Label1", formatValue: "Value1", value: "v1" }, overrides);
}

function createComponent(modelOverrides: Partial<ComboBoxEditorModel> = {}) {
   const modalService = { open: vi.fn() };
   const http = { post: vi.fn(() => of([] as ComboBoxDefaultValueListModel[])) };
   const comp = new ComboBoxEditor(modalService as unknown as DialogService, http as unknown as HttpClient);
   comp.model = makeModel(modelOverrides);
   comp.general = null;
   comp.runtimeId = "rt1";
   comp.sortType = 0;
   comp.embeddedDataDown = false;
   return { comp, modalService, http };
}

// ---------------------------------------------------------------------------
// Group 1: ngOnInit / ngOnChanges [Risk 2]
// ---------------------------------------------------------------------------

describe("ComboBoxEditor — ngOnInit", () => {
   it("should mark noDefault true when defaultValue is null", () => {
      const { comp } = createComponent({ defaultValue: null });
      comp.ngOnInit();
      expect(comp.model.noDefault).toBe(true);
   });

   it("should mark noDefault false when defaultValue is set", () => {
      const { comp } = createComponent({ defaultValue: "abc" });
      comp.ngOnInit();
      expect(comp.model.noDefault).toBe(false);
   });

   it("should evaluate the initial default-value validity", () => {
      const { comp } = createComponent({ defaultValue: "abc", calendar: false });
      const emitted: boolean[] = [];
      comp.isInputValid.subscribe((v: boolean) => emitted.push(v));

      comp.ngOnInit();

      expect(emitted).toEqual([true]);
   });
});

describe("ComboBoxEditor — ngOnChanges", () => {
   const change = { currentValue: 1, previousValue: 0, firstChange: false, isFirstChange: () => false };

   it("should refresh default values when sortType changes", () => {
      const { comp } = createComponent();
      const spy = vi.spyOn(comp as any, "updatDefaultValues");
      comp.ngOnChanges({ sortType: change });
      expect(spy).toHaveBeenCalled();
   });

   it("should refresh default values when embeddedDataDown changes", () => {
      const { comp } = createComponent();
      const spy = vi.spyOn(comp as any, "updatDefaultValues");
      comp.ngOnChanges({ embeddedDataDown: change });
      expect(spy).toHaveBeenCalled();
   });

   it("should NOT refresh default values for unrelated changes", () => {
      const { comp } = createComponent();
      const spy = vi.spyOn(comp as any, "updatDefaultValues");
      comp.ngOnChanges({ enableDataType: change });
      expect(spy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 2: resetValid / calendarEnabled [Risk 1]
// ---------------------------------------------------------------------------

describe("ComboBoxEditor — resetValid / calendarEnabled", () => {
   it("should mark the model valid", () => {
      const { comp } = createComponent({ valid: false });
      comp.resetValid();
      expect(comp.model.valid).toBe(true);
   });

   it.each([
      [XSchema.DATE, true],
      [XSchema.TIME, true],
      [XSchema.TIME_INSTANT, true],
      [XSchema.STRING, false],
   ])("should report calendarEnabled=%s for dataType %s", (dataType, expected) => {
      const { comp } = createComponent({ dataType: dataType as any });
      expect(comp.calendarEnabled()).toBe(expected);
   });
});

// ---------------------------------------------------------------------------
// Group 3: updateType [Risk 2]
// ---------------------------------------------------------------------------

describe("ComboBoxEditor — updateType", () => {
   it("should propagate the new type to the model and the variable-list dialog model", () => {
      const { comp } = createComponent({ dataType: XSchema.STRING });
      comp.updateType(XSchema.INTEGER);
      expect(comp.model.dataType).toBe(XSchema.INTEGER);
      expect(comp.model.variableListDialogModel.dataType).toBe(XSchema.INTEGER);
   });

   it("should enable serverTZ for TIME/TIME_INSTANT types", () => {
      const { comp } = createComponent({ dataType: XSchema.STRING });
      comp.updateType(XSchema.TIME);
      expect(comp.model.serverTZ).toBe(true);
   });

   it("should keep serverTZ false for a non-time type", () => {
      const { comp } = createComponent({ dataType: XSchema.STRING });
      comp.updateType(XSchema.INTEGER);
      expect(comp.model.serverTZ).toBeFalsy();
   });

   it("should reset the date range", () => {
      const { comp } = createComponent({ minDate: "2024-01-01", maxDate: "2024-12-31" });
      comp.updateType(XSchema.INTEGER);
      expect(comp.model.minDate).toBe("");
      expect(comp.model.maxDate).toBe("");
   });

   it("should turn off the calendar for a non-date-like type", () => {
      const { comp } = createComponent({ calendar: true, dataType: XSchema.DATE });
      comp.updateType(XSchema.STRING);
      expect(comp.model.calendar).toBe(false);
   });

   it("should leave the calendar untouched for a date-like type", () => {
      const { comp } = createComponent({ calendar: true, dataType: XSchema.STRING });
      comp.updateType(XSchema.DATE);
      expect(comp.model.calendar).toBe(true);
   });

   it("should reset validity as the last step", () => {
      const { comp } = createComponent({ valid: false });
      comp.updateType(XSchema.STRING);
      expect(comp.model.valid).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 4: updatDefaultValues (via its public callers) [Risk 3]
// ---------------------------------------------------------------------------

describe("ComboBoxEditor — updatDefaultValues (HTTP refresh)", () => {
   function withGeneral(comp: ComboBoxEditor) {
      comp.general = { generalPropPaneModel: { basicGeneralPaneModel: { name: "obj1" } } } as any;
   }

   it("should skip the HTTP call entirely when there is no general pane model", () => {
      const { comp, http } = createComponent();
      comp.toggleQuery();
      expect(http.post).not.toHaveBeenCalled();
   });

   it("should POST the model with the runtime/sort/embedded params and store the response", () => {
      const { comp, http } = createComponent();
      comp.runtimeId = "rt1";
      comp.sortType = 2;
      comp.embeddedDataDown = true;
      withGeneral(comp);
      const entries = [makeValueListEntry()];
      http.post.mockReturnValue(of(entries));

      comp.toggleQuery();

      expect(http.post).toHaveBeenCalledWith(
         "../api/composer/vs/comboboxeditor/larbel", comp.model, expect.anything()
      );
      const params = (http.post as any).mock.calls[0][2].params;
      expect(params.toString()).toBe("runtimeId=rt1&sortType=2&embeddedDataDown=true&objectId=obj1");
      expect(comp.valueList).toBe(entries);
   });

   it("should prefix formatValue with the label when querying and the query label mismatches the value", () => {
      const { comp, http } = createComponent({
         query: true,
         selectionListDialogModel: {
            selectionListEditorModel: { column: "col_a", value: "col_b" } as any,
         },
      });
      withGeneral(comp);
      const entries = [makeValueListEntry({ label: "L1", formatValue: "V1" })];
      http.post.mockReturnValue(of(entries));

      comp.toggleQuery();

      expect(comp.valueList[0].formatValue).toBe("L1 | V1");
   });

   it("should NOT prefix formatValue when the query label matches the value", () => {
      const { comp, http } = createComponent({
         query: true,
         selectionListDialogModel: {
            selectionListEditorModel: { column: "same", value: "same" } as any,
         },
      });
      withGeneral(comp);
      const entries = [makeValueListEntry({ label: "L1", formatValue: "V1" })];
      http.post.mockReturnValue(of(entries));

      comp.toggleQuery();

      expect(comp.valueList[0].formatValue).toBe("V1");
   });

   it("should prefix formatValue when embedded and the variable list labels/values differ", () => {
      const { comp, http } = createComponent({
         embedded: true,
         variableListDialogModel: { labels: ["a"], values: ["b"], dataType: XSchema.STRING } as any,
      });
      withGeneral(comp);
      const entries = [makeValueListEntry({ label: "L1", formatValue: "V1" })];
      http.post.mockReturnValue(of(entries));

      comp.toggleQuery();

      expect(comp.valueList[0].formatValue).toBe("L1 | V1");
   });

   it("should NOT prefix formatValue when embedded and the variable list labels/values are identical", () => {
      const { comp, http } = createComponent({
         embedded: true,
         variableListDialogModel: { labels: ["a", "b"], values: ["a", "b"], dataType: XSchema.STRING } as any,
      });
      withGeneral(comp);
      const entries = [makeValueListEntry({ label: "L1", formatValue: "V1" })];
      http.post.mockReturnValue(of(entries));

      comp.toggleQuery();

      expect(comp.valueList[0].formatValue).toBe("V1");
   });

   it("should reset a stale default value to the first entry when it's no longer in the list", () => {
      const { comp, http } = createComponent({ defaultValue: "gone" });
      withGeneral(comp);
      const entries = [makeValueListEntry({ value: "v1" }), makeValueListEntry({ value: "v2" })];
      http.post.mockReturnValue(of(entries));

      comp.toggleQuery();

      expect(comp.model.defaultValue).toBe("v1");
   });

   it("should keep the default value unchanged when it is still present in the list", () => {
      const { comp, http } = createComponent({ defaultValue: "v2" });
      withGeneral(comp);
      const entries = [makeValueListEntry({ value: "v1" }), makeValueListEntry({ value: "v2" })];
      http.post.mockReturnValue(of(entries));

      comp.toggleQuery();

      expect(comp.model.defaultValue).toBe("v2");
   });

   it("should NOT touch the default value when there was none to begin with", () => {
      const { comp, http } = createComponent({ defaultValue: null });
      withGeneral(comp);
      const entries = [makeValueListEntry({ value: "v1" })];
      http.post.mockReturnValue(of(entries));

      comp.toggleQuery();

      expect(comp.model.defaultValue).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 5: showSelectionListDialog / showVariableListDialog [Risk 2]
// ---------------------------------------------------------------------------

describe("ComboBoxEditor — showSelectionListDialog", () => {
   it("should update the selection-list model and refresh default values on confirm", async () => {
      const { comp, modalService } = createComponent();
      const result = { selectionListEditorModel: { column: "c", value: "c" } };
      modalService.open.mockReturnValue({ result: Promise.resolve(result) });
      const spy = vi.spyOn(comp as any, "updatDefaultValues").mockImplementation(() => {});

      comp.showSelectionListDialog();
      await Promise.resolve().then(() => {});

      expect(comp.model.selectionListDialogModel).toBe(result);
      expect(spy).toHaveBeenCalled();
   });

   it("should leave the model untouched when the dialog is dismissed", async () => {
      const { comp, modalService } = createComponent();
      const original = comp.model.selectionListDialogModel;
      modalService.open.mockReturnValue({ result: Promise.reject("dismissed") });

      comp.showSelectionListDialog();
      await Promise.resolve().catch(() => {}).then(() => {});

      expect(comp.model.selectionListDialogModel).toBe(original);
   });
});

describe("ComboBoxEditor — showVariableListDialog", () => {
   it("should reset validity before opening the dialog", () => {
      const { comp, modalService } = createComponent({ valid: false });
      modalService.open.mockReturnValue({ result: new Promise(() => {}) }); // never resolves

      comp.showVariableListDialog();

      expect(comp.model.valid).toBe(true);
   });

   it("should update the variable-list model and refresh default values on confirm", async () => {
      const { comp, modalService } = createComponent();
      const result = { labels: ["a"], values: ["b"], dataType: XSchema.STRING };
      modalService.open.mockReturnValue({ result: Promise.resolve(result) });
      const spy = vi.spyOn(comp as any, "updatDefaultValues").mockImplementation(() => {});

      comp.showVariableListDialog();
      await Promise.resolve().then(() => {});

      expect(comp.model.variableListDialogModel).toBe(result);
      expect(spy).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 6: updateCalendar [Risk 2]
// ---------------------------------------------------------------------------

describe("ComboBoxEditor — updateCalendar", () => {
   it("should turn off query when enabling the calendar on an embedded list", () => {
      const { comp } = createComponent({ embedded: true, calendar: true, query: true });
      comp.updateCalendar();
      expect(comp.model.query).toBe(false);
   });

   it("should clear invalid date-range values when disabling the calendar on an embedded list", () => {
      const { comp } = createComponent({
         embedded: true, calendar: false, dataType: XSchema.DATE,
         minDate: "not-a-date-but-not-dynamic", maxDate: "2024-12-31",
      });

      comp.updateCalendar();

      expect(comp.model.minDate).toBe("");
   });

   it("should clear both dates when the range itself is invalid (min after max)", () => {
      const { comp } = createComponent({
         embedded: true, calendar: false, dataType: XSchema.DATE,
         minDate: "2025-01-01", maxDate: "2024-01-01",
      });

      comp.updateCalendar();

      expect(comp.model.minDate).toBe("");
      expect(comp.model.maxDate).toBe("");
   });

   it("should emit valid=true for a non-embedded list when the calendar is enabled", () => {
      const { comp } = createComponent({ embedded: false, calendar: true });
      const emitted: boolean[] = [];
      comp.isInputValid.subscribe((v: boolean) => emitted.push(v));

      comp.updateCalendar();

      expect(emitted).toEqual([true]);
   });

   it("should NOT emit anything for a non-embedded list when the calendar is disabled", () => {
      const { comp } = createComponent({ embedded: false, calendar: false });
      const emitted: boolean[] = [];
      comp.isInputValid.subscribe((v: boolean) => emitted.push(v));

      comp.updateCalendar();

      expect(emitted).toHaveLength(0);
   });

   it("should refresh default values as its first step", () => {
      const { comp } = createComponent({ embedded: false, calendar: false });
      const spy = vi.spyOn(comp as any, "updatDefaultValues");

      comp.updateCalendar();

      expect(spy).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 7: toggleQuery [Risk 1]
// ---------------------------------------------------------------------------

describe("ComboBoxEditor — toggleQuery", () => {
   it("should disable the calendar when query is enabled", () => {
      const { comp } = createComponent({ query: true, calendar: true });
      comp.toggleQuery();
      expect(comp.model.calendar).toBe(false);
   });

   it("should leave the calendar untouched when query is disabled", () => {
      const { comp } = createComponent({ query: false, calendar: true });
      comp.toggleQuery();
      expect(comp.model.calendar).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 8: onDateRangeChanged / onDefaultvalueChanged [Risk 2]
// ---------------------------------------------------------------------------

describe("ComboBoxEditor — onDateRangeChanged", () => {
   // onDateRangeChanged() always ends by calling onDefaultvalueChanged(model.defaultValue),
   // which emits isInputValid a second time on its own — so every case here observes two
   // emissions, not one. With defaultValue left at its null default, that second emission
   // is always true (onDefaultvalueChanged short-circuits to true for a null default).
   it("should emit valid=true for the range check when the calendar is disabled, regardless of date values", () => {
      const { comp } = createComponent({ calendar: false, minDate: "bad", maxDate: "bad" });
      const emitted: boolean[] = [];
      comp.isInputValid.subscribe((v: boolean) => emitted.push(v));

      comp.onDateRangeChanged();

      expect(emitted).toEqual([true, true]);
   });

   it("should emit valid=true for the range check when the calendar is enabled and the range is valid", () => {
      const { comp } = createComponent({
         calendar: true, dataType: XSchema.DATE, minDate: "2024-01-01", maxDate: "2024-12-31",
      });
      const emitted: boolean[] = [];
      comp.isInputValid.subscribe((v: boolean) => emitted.push(v));

      comp.onDateRangeChanged();

      expect(emitted).toEqual([true, true]);
   });

   it("should emit valid=false for the range check when the calendar is enabled and the range is invalid", () => {
      const { comp } = createComponent({
         calendar: true, dataType: XSchema.DATE, minDate: "2024-12-31", maxDate: "2024-01-01",
      });
      const emitted: boolean[] = [];
      comp.isInputValid.subscribe((v: boolean) => emitted.push(v));

      comp.onDateRangeChanged();

      expect(emitted).toEqual([false, true]);
   });

   it("should emit valid=false when the range itself is fine but one endpoint's value is malformed", () => {
      // Exercises the first two `&&` operands (validateDateValue(min)/(max)) independently
      // from the third (validateDateRange) — minDate here fails its own pattern check even
      // though there's no real "range" comparison problem.
      const { comp } = createComponent({
         calendar: true, dataType: XSchema.DATE, minDate: "not-a-date-but-not-dynamic", maxDate: "2024-12-31",
      });
      const emitted: boolean[] = [];
      comp.isInputValid.subscribe((v: boolean) => emitted.push(v));

      comp.onDateRangeChanged();

      expect(emitted).toEqual([false, true]);
   });

   it("should emit valid=false when the min endpoint is fine but the max endpoint's value is malformed", () => {
      // Isolates the SECOND `&&` operand (validateDateValue(max)) alone being false — the
      // previous test only covers the first operand (min) failing.
      const { comp } = createComponent({
         calendar: true, dataType: XSchema.DATE, minDate: "2024-01-01", maxDate: "not-a-date-but-not-dynamic",
      });
      const emitted: boolean[] = [];
      comp.isInputValid.subscribe((v: boolean) => emitted.push(v));

      comp.onDateRangeChanged();

      expect(emitted).toEqual([false, true]);
   });

   it("should re-evaluate the default value's validity as its last step", () => {
      const { comp } = createComponent({ calendar: false });
      const spy = vi.spyOn(comp, "onDefaultvalueChanged");

      comp.onDateRangeChanged();

      expect(spy).toHaveBeenCalledWith(comp.model.defaultValue);
   });
});

describe("ComboBoxEditor — onDefaultvalueChanged", () => {
   it("should emit valid=true when the calendar is off, regardless of the default value", () => {
      const { comp } = createComponent({ calendar: false });
      const emitted: boolean[] = [];
      comp.isInputValid.subscribe((v: boolean) => emitted.push(v));

      comp.onDefaultvalueChanged("anything");

      expect(emitted).toEqual([true]);
   });

   it("should emit valid=false when the calendar is on and the default value falls outside the date range", () => {
      const { comp } = createComponent({
         calendar: true, dataType: XSchema.DATE, minDate: "2024-06-01", maxDate: "",
      });
      const emitted: boolean[] = [];
      comp.isInputValid.subscribe((v: boolean) => emitted.push(v));

      comp.onDefaultvalueChanged("2024-01-01"); // before minDate

      expect(emitted).toEqual([false]);
   });

   it("should emit valid=false when the calendar is on and the default value doesn't match the date pattern", () => {
      const { comp } = createComponent({ calendar: true, dataType: XSchema.DATE });
      const emitted: boolean[] = [];
      comp.isInputValid.subscribe((v: boolean) => emitted.push(v));

      comp.onDefaultvalueChanged("not-a-real-date");

      expect(emitted).toEqual([false]);
   });

   it("should emit valid=true when the calendar is on and the default value is within range and well-formed", () => {
      const { comp } = createComponent({
         calendar: true, dataType: XSchema.DATE, minDate: "2024-01-01", maxDate: "2024-12-31",
      });
      const emitted: boolean[] = [];
      comp.isInputValid.subscribe((v: boolean) => emitted.push(v));

      comp.onDefaultvalueChanged("2024-06-15");

      expect(emitted).toEqual([true]);
   });
});

// ---------------------------------------------------------------------------
// Group 9: currentPattern / validateDateValue / validateDateRange [Risk 3]
// ---------------------------------------------------------------------------

describe("ComboBoxEditor — currentPattern", () => {
   it("should use the DATE pattern for DATE", () => {
      const { comp } = createComponent({ dataType: XSchema.DATE });
      expect(comp.currentPattern).toBe(comp.DATE_PATTERN);
   });

   it("should use the DATETIME pattern for TIME_INSTANT", () => {
      const { comp } = createComponent({ dataType: XSchema.TIME_INSTANT });
      expect(comp.currentPattern).toBe(comp.DATETIME_PATTERN);
   });

   it("should use the TIME pattern for TIME", () => {
      const { comp } = createComponent({ dataType: XSchema.TIME });
      expect(comp.currentPattern).toBe(comp.TIME_PATTERN);
   });

   it("should be null for any other type", () => {
      const { comp } = createComponent({ dataType: XSchema.STRING });
      expect(comp.currentPattern).toBeNull();
   });
});

describe("ComboBoxEditor — validateDateValue", () => {
   it("should be valid for an empty/null/dynamic value regardless of pattern", () => {
      const { comp } = createComponent({ dataType: XSchema.DATE });
      expect(comp.validateDateValue("")).toBe(true);
      expect(comp.validateDateValue(null)).toBe(true);
      expect(comp.validateDateValue("$(myVar)")).toBe(true);
      expect(comp.validateDateValue("=1+1")).toBe(true);
   });

   it("should validate a real value against the current pattern", () => {
      const { comp } = createComponent({ dataType: XSchema.DATE });
      expect(comp.validateDateValue("2024-03-15")).toBe(true);
      expect(comp.validateDateValue("not-a-date")).toBe(false);
   });

   it("should be valid for any real value when there is no pattern for the current type", () => {
      const { comp } = createComponent({ dataType: XSchema.STRING });
      expect(comp.validateDateValue("literally anything")).toBe(true);
   });
});

describe("ComboBoxEditor — validateDateRange", () => {
   it("should be valid when min is before max (DATE)", () => {
      const { comp } = createComponent({ dataType: XSchema.DATE });
      expect(comp.validateDateRange("2024-01-01", "2024-12-31")).toBe(true);
   });

   it("should be invalid when min is after max (DATE)", () => {
      const { comp } = createComponent({ dataType: XSchema.DATE });
      expect(comp.validateDateRange("2024-12-31", "2024-01-01")).toBe(false);
   });

   it("should be valid when min equals max (DATE)", () => {
      const { comp } = createComponent({ dataType: XSchema.DATE });
      expect(comp.validateDateRange("2024-06-15", "2024-06-15")).toBe(true);
   });

   it("should compare down to the day when the year and month are equal (DATE)", () => {
      const { comp } = createComponent({ dataType: XSchema.DATE });
      expect(comp.validateDateRange("2024-06-20", "2024-06-15")).toBe(false);
   });

   it("should be valid when either endpoint is empty/dynamic (nothing to compare)", () => {
      const { comp } = createComponent({ dataType: XSchema.DATE });
      expect(comp.validateDateRange("", "2024-01-01")).toBe(true);
      expect(comp.validateDateRange("2024-01-01", "$(var)")).toBe(true);
   });

   it("should be valid when there is no pattern for the current type", () => {
      const { comp } = createComponent({ dataType: XSchema.STRING });
      expect(comp.validateDateRange("z", "a")).toBe(true);
   });

   it("should compare down to the hour for TIME_INSTANT", () => {
      const { comp } = createComponent({ dataType: XSchema.TIME_INSTANT });
      expect(comp.validateDateRange("2024-01-01 10:00:00", "2024-01-01 09:00:00")).toBe(false);
      expect(comp.validateDateRange("2024-01-01 09:00:00", "2024-01-01 10:00:00")).toBe(true);
   });

   // BUG: DATETIME_PATTERN has no capturing groups for minutes/seconds — only year/month/day/
   // whitespace/hour are captured (5 groups), so the match array's length (6, including the
   // full match) happens to equal maxSize (6) for non-DATE types. The comparison loop in
   // validateDateRange only ever reads real values at indices 1-5; index 6 is undefined on
   // both sides, which the `==` branch treats as "equal" and exits the loop without ever
   // inspecting minutes/seconds. A minute-level difference within the same hour is silently
   // treated as a valid range.
   it.fails("does not actually detect a minute-level range violation for TIME_INSTANT", () => {
      const { comp } = createComponent({ dataType: XSchema.TIME_INSTANT });
      expect(comp.validateDateRange("2024-01-01 10:05:00", "2024-01-01 10:02:00")).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 10: showDateRangeWarning / showDateRanges [Risk 1]
// ---------------------------------------------------------------------------

describe("ComboBoxEditor — showDateRangeWarning / showDateRanges", () => {
   it("should warn when both dates are individually valid but the range itself is backwards", () => {
      const { comp } = createComponent({
         dataType: XSchema.DATE, minDate: "2024-12-31", maxDate: "2024-01-01",
      });
      expect(comp.showDateRangeWarning()).toBe(true);
   });

   it("should NOT warn when the range is valid", () => {
      const { comp } = createComponent({
         dataType: XSchema.DATE, minDate: "2024-01-01", maxDate: "2024-12-31",
      });
      expect(comp.showDateRangeWarning()).toBe(false);
   });

   it("should NOT warn (there's nothing coherent to compare) when one endpoint's own value is malformed", () => {
      const { comp } = createComponent({
         dataType: XSchema.DATE, minDate: "not-a-date-but-not-dynamic", maxDate: "2024-12-31",
      });
      expect(comp.showDateRangeWarning()).toBe(false);
   });

   it("should show date ranges when calendar is on and the type isn't TIME", () => {
      const { comp } = createComponent({ calendar: true, dataType: XSchema.DATE });
      expect(comp.showDateRanges()).toBe(true);
   });

   it("should hide date ranges for TIME even when calendar is on", () => {
      const { comp } = createComponent({ calendar: true, dataType: XSchema.TIME });
      expect(comp.showDateRanges()).toBe(false);
   });

   it("should hide date ranges when calendar is off", () => {
      const { comp } = createComponent({ calendar: false, dataType: XSchema.DATE });
      expect(comp.showDateRanges()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 11: changeNodefault / isDefaultValue [Risk 2]
// ---------------------------------------------------------------------------

describe("ComboBoxEditor — changeNodefault", () => {
   it("should clear the default value when noDefault is enabled", () => {
      const { comp } = createComponent({ noDefault: true, defaultValue: "abc" });
      comp.changeNodefault();
      expect(comp.model.defaultValue).toBeNull();
   });

   it("should leave the default value untouched when noDefault is disabled", () => {
      const { comp } = createComponent({ noDefault: false, defaultValue: "abc" });
      comp.changeNodefault();
      expect(comp.model.defaultValue).toBe("abc");
   });

   it("should re-evaluate default-value validity with the (now-cleared) value", () => {
      const { comp } = createComponent({ noDefault: true, defaultValue: "abc" });
      const spy = vi.spyOn(comp, "onDefaultvalueChanged");
      comp.changeNodefault();
      expect(spy).toHaveBeenCalledWith(null);
   });
});

describe("ComboBoxEditor — isDefaultValue", () => {
   it("should be true when the default value is before minDate for a DATE type", () => {
      const { comp } = createComponent({ dataType: XSchema.DATE, minDate: "2024-06-01", maxDate: "" });
      expect(comp.isDefaultValue("2024-01-01")).toBe(true);
   });

   it("should be true when the default value is after maxDate for a DATE type", () => {
      const { comp } = createComponent({ dataType: XSchema.DATE, minDate: "", maxDate: "2024-06-01" });
      expect(comp.isDefaultValue("2024-12-31")).toBe(true);
   });

   it("should be false when the default value is within range", () => {
      const { comp } = createComponent({ dataType: XSchema.DATE, minDate: "2024-01-01", maxDate: "2024-12-31" });
      expect(comp.isDefaultValue("2024-06-15")).toBe(false);
   });

   it("should be false for a non-date type regardless of range", () => {
      const { comp } = createComponent({ dataType: XSchema.STRING, minDate: "2024-06-01", maxDate: "" });
      expect(comp.isDefaultValue("2024-01-01")).toBe(false);
   });

   it("should ignore a dynamic (non-literal) minDate/maxDate", () => {
      const { comp } = createComponent({ dataType: XSchema.DATE, minDate: "$(var)", maxDate: "" });
      // The `&&`/`||` chain short-circuits on the falsy (but non-boolean) "" maxDate operand,
      // so the actual return value here is "" rather than a strict `false` — toBeFalsy() per
      // the A2 rule instead of toBe(false).
      expect(comp.isDefaultValue("2024-01-01")).toBeFalsy();
   });
});

// ---------------------------------------------------------------------------
// Group 12: datePrompt / dropdownMinWidth [Risk 1]
// ---------------------------------------------------------------------------

describe("ComboBoxEditor — datePrompt", () => {
   it("should prompt for a date-only format for DATE", () => {
      const { comp } = createComponent({ dataType: XSchema.DATE });
      expect(comp.datePrompt).toBe("yyyy-mm-dd");
   });

   it("should prompt for a full datetime format for any non-TIME, non-DATE type", () => {
      const { comp } = createComponent({ dataType: XSchema.TIME_INSTANT });
      expect(comp.datePrompt).toBe("yyyy-MM-dd HH:mm:ss");
   });

   it("should prompt for a time-only format for TIME", () => {
      const { comp } = createComponent({ dataType: XSchema.TIME });
      expect(comp.datePrompt).toBe("HH:mm:ss");
   });
});

describe("ComboBoxEditor — dropdownMinWidth", () => {
   it("should be null when the dropdown body ViewChild hasn't been resolved", () => {
      const { comp } = createComponent();
      expect(comp.dropdownMinWidth).toBeNull();
   });

   it("should read the first element child's offsetWidth once the dropdown body is present", () => {
      const { comp } = createComponent();
      (comp as any).dropdownBody = { nativeElement: { firstElementChild: { offsetWidth: 42 } } };

      expect(comp.dropdownMinWidth).toBe(42);
   });
});
