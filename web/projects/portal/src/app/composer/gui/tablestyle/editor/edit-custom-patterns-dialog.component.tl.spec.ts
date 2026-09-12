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
 * EditCustomPatternsDialog — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — isInvalid: null → false (falsy = no error); negative → true; NaN → true;
 *     non-integer float → true; valid non-negative integer → false
 *   Group 2 [Risk 3] — disabledOk: returns true only when a numeric field is invalid AND
 *     disableGroupTab() is true (Row/Column custom type); for group-total types the tab gate is
 *     separate, so a non-zero invalid value does not block ok
 *   Group 3 [Risk 2] — startValue / fromValue / toValue getters: null specModel fields produce
 *     null strings; numeric values are converted to their string representation
 *   Group 4 [Risk 2] — setStartValue / setFromValue / setToValue: string input is converted via
 *     parseFloat and stored on specModel
 *   Group 5 [Risk 2] — defaultTab: ROW_GROUP_TOTAL and COLUMN_GROUP_TOTAL → groupingTab;
 *     all other types (Row, Column, etc.) → rowColTab
 *   Group 6 [Risk 2] — disableRowTab / disableGroupTab: correct boolean per custom type
 *   Group 7 [Risk 1] — startLabel / repeatLabel / rangeLabel: "Row" type → row-specific labels;
 *     non-Row type → column-specific labels
 *   Group 8 [Risk 1] — cancel / ok: emit corresponding EventEmitter outputs
 *
 * Confirmed bugs (it.fails):
 *   None.
 *
 * Out of scope:
 *   Template rendering — uses NO_ERRORS_SCHEMA; tab navigation is integration-level.
 *   NgbNav tab switching — library-level; tested via ngb own tests.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { EditCustomPatternsDialog } from "./edit-custom-patterns-dialog.component";
import { TableStyleUtil } from "../../../../common/util/table-style-util";
import { SpecificationModel } from "../../../data/tablestyle/specification-model";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------

function makeSpec(overrides: Partial<SpecificationModel> = {}): SpecificationModel {
   return {
      label: "Custom",
      id: 0,
      specFormat: null,
      customType: TableStyleUtil.ROW,
      start: null,
      repeat: false,
      from: null,
      to: null,
      all: false,
      ...overrides,
   };
}

async function renderComponent(specOverrides: Partial<SpecificationModel> = {}) {
   const specModel = makeSpec(specOverrides);
   const { fixture } = await render(EditCustomPatternsDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      componentInputs: { specModel },
   });
   const comp = fixture.componentInstance as EditCustomPatternsDialog;
   return { comp, fixture, specModel };
}

// ---------------------------------------------------------------------------
// Group 1: isInvalid [Risk 3]
// ---------------------------------------------------------------------------

describe("EditCustomPatternsDialog — isInvalid", () => {
   // 🔁 Regression-sensitive: null must return false so empty fields don't block the OK button.
   it("should return false for null (empty field is not invalid)", async () => {
      const { comp } = await renderComponent();
      expect(comp.isInvalid(null)).toBe(false);
   });

   it("should return false for a valid non-negative integer (0)", async () => {
      const { comp } = await renderComponent();
      expect(comp.isInvalid(0)).toBe(false);
   });

   it("should return false for a valid positive integer", async () => {
      const { comp } = await renderComponent();
      expect(comp.isInvalid(5)).toBe(false);
   });

   it("should return true for a negative integer", async () => {
      const { comp } = await renderComponent();
      expect(comp.isInvalid(-1)).toBe(true);
   });

   it("should return true for NaN", async () => {
      const { comp } = await renderComponent();
      expect(comp.isInvalid(NaN)).toBe(true);
   });

   it("should return true for a non-integer float (1.5)", async () => {
      const { comp } = await renderComponent();
      expect(comp.isInvalid(1.5)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2: disabledOk [Risk 3]
// ---------------------------------------------------------------------------

describe("EditCustomPatternsDialog — disabledOk", () => {
   // 🔁 Regression-sensitive: for Row/Column type, an invalid start/from/to should disable OK
   //    because the row-column tab is active and the user must fix the value before saving.
   it("should return true when start is negative and customType is Row (disableGroupTab=true)", async () => {
      const { comp } = await renderComponent({ customType: TableStyleUtil.ROW, start: -1 });
      expect(comp.disabledOk()).toBe(true);
   });

   it("should return true when from is NaN and customType is Column", async () => {
      const { comp } = await renderComponent({ customType: TableStyleUtil.COLUMN, from: NaN });
      expect(comp.disabledOk()).toBe(true);
   });

   it("should return false when all fields are null and customType is Row", async () => {
      const { comp } = await renderComponent({ customType: TableStyleUtil.ROW, start: null, from: null, to: null });
      expect(comp.disabledOk()).toBe(false);
   });

   it("should return false when start is negative but customType is ROW_GROUP_TOTAL (disableGroupTab=false)", async () => {
      // For group-total type: disableGroupTab()=false → disabledOk returns false even if invalid
      const { comp } = await renderComponent({ customType: TableStyleUtil.ROW_GROUP_TOTAL, start: -1 });
      expect(comp.disabledOk()).toBe(false);
   });

   it("should return false when all fields are valid", async () => {
      const { comp } = await renderComponent({ customType: TableStyleUtil.ROW, start: 0, from: 1, to: 5 });
      expect(comp.disabledOk()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3: startValue / fromValue / toValue getters [Risk 2]
// ---------------------------------------------------------------------------

describe("EditCustomPatternsDialog — value getters", () => {
   it("startValue should return null when start is null", async () => {
      const { comp } = await renderComponent({ start: null });
      expect(comp.startValue).toBeNull();
   });

   it("startValue should return string representation of a number", async () => {
      const { comp } = await renderComponent({ start: 3 });
      expect(comp.startValue).toBe("3");
   });

   it("fromValue should return null when from is null", async () => {
      const { comp } = await renderComponent({ from: null });
      expect(comp.fromValue).toBeNull();
   });

   it("fromValue should return string representation of a number", async () => {
      const { comp } = await renderComponent({ from: 2 });
      expect(comp.fromValue).toBe("2");
   });

   it("toValue should return null when to is null", async () => {
      const { comp } = await renderComponent({ to: null });
      expect(comp.toValue).toBeNull();
   });

   it("toValue should return string representation of a number", async () => {
      const { comp } = await renderComponent({ to: 10 });
      expect(comp.toValue).toBe("10");
   });
});

// ---------------------------------------------------------------------------
// Group 4: setStartValue / setFromValue / setToValue [Risk 2]
// ---------------------------------------------------------------------------

describe("EditCustomPatternsDialog — value setters", () => {
   it("setStartValue should parse string to float and store on specModel", async () => {
      const { comp, specModel } = await renderComponent();
      comp.setStartValue("7");
      expect(specModel.start).toBe(7);
   });

   it("setFromValue should parse string to float and store on specModel", async () => {
      const { comp, specModel } = await renderComponent();
      comp.setFromValue("3");
      expect(specModel.from).toBe(3);
   });

   it("setToValue should parse string to float and store on specModel", async () => {
      const { comp, specModel } = await renderComponent();
      comp.setToValue("15");
      expect(specModel.to).toBe(15);
   });

   it("setStartValue should produce NaN for non-numeric string", async () => {
      const { comp, specModel } = await renderComponent();
      comp.setStartValue("abc");
      expect(isNaN(specModel.start)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 5: defaultTab [Risk 2]
// ---------------------------------------------------------------------------

describe("EditCustomPatternsDialog — defaultTab", () => {
   it("should return groupingTab for ROW_GROUP_TOTAL", async () => {
      const { comp } = await renderComponent({ customType: TableStyleUtil.ROW_GROUP_TOTAL });
      expect(comp.defaultTab()).toBe(comp["groupingTab"]);
   });

   it("should return groupingTab for COLUMN_GROUP_TOTAL", async () => {
      const { comp } = await renderComponent({ customType: TableStyleUtil.COLUMN_GROUP_TOTAL });
      expect(comp.defaultTab()).toBe(comp["groupingTab"]);
   });

   it("should return rowColTab for ROW type", async () => {
      const { comp } = await renderComponent({ customType: TableStyleUtil.ROW });
      expect(comp.defaultTab()).toBe(comp["rowColTab"]);
   });

   it("should return rowColTab for COLUMN type", async () => {
      const { comp } = await renderComponent({ customType: TableStyleUtil.COLUMN });
      expect(comp.defaultTab()).toBe(comp["rowColTab"]);
   });
});

// ---------------------------------------------------------------------------
// Group 6: disableRowTab / disableGroupTab [Risk 2]
// ---------------------------------------------------------------------------

describe("EditCustomPatternsDialog — disableRowTab / disableGroupTab", () => {
   it("disableRowTab should return true for ROW_GROUP_TOTAL", async () => {
      const { comp } = await renderComponent({ customType: TableStyleUtil.ROW_GROUP_TOTAL });
      expect(comp.disableRowTab()).toBe(true);
   });

   it("disableRowTab should return true for COLUMN_GROUP_TOTAL", async () => {
      const { comp } = await renderComponent({ customType: TableStyleUtil.COLUMN_GROUP_TOTAL });
      expect(comp.disableRowTab()).toBe(true);
   });

   it("disableRowTab should return false for ROW", async () => {
      const { comp } = await renderComponent({ customType: TableStyleUtil.ROW });
      expect(comp.disableRowTab()).toBe(false);
   });

   it("disableGroupTab should return true for ROW", async () => {
      const { comp } = await renderComponent({ customType: TableStyleUtil.ROW });
      expect(comp.disableGroupTab()).toBe(true);
   });

   it("disableGroupTab should return true for COLUMN", async () => {
      const { comp } = await renderComponent({ customType: TableStyleUtil.COLUMN });
      expect(comp.disableGroupTab()).toBe(true);
   });

   it("disableGroupTab should return false for ROW_GROUP_TOTAL", async () => {
      const { comp } = await renderComponent({ customType: TableStyleUtil.ROW_GROUP_TOTAL });
      expect(comp.disableGroupTab()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 7: startLabel / repeatLabel / rangeLabel [Risk 1]
// ---------------------------------------------------------------------------

describe("EditCustomPatternsDialog — label methods", () => {
   it("startLabel should return start-row label for Row type", async () => {
      const { comp } = await renderComponent({ customType: TableStyleUtil.ROW });
      expect(comp.startLabel()).toContain("Start Row");
   });

   it("startLabel should return start-column label for non-Row type", async () => {
      const { comp } = await renderComponent({ customType: TableStyleUtil.COLUMN });
      expect(comp.startLabel()).toContain("Start Column");
   });

   it("repeatLabel should return row label for Row type", async () => {
      const { comp } = await renderComponent({ customType: TableStyleUtil.ROW });
      expect(comp.repeatLabel()).toContain("row");
   });

   it("repeatLabel should return col label for non-Row type", async () => {
      const { comp } = await renderComponent({ customType: TableStyleUtil.COLUMN });
      expect(comp.repeatLabel()).toContain("col");
   });

   it("rangeLabel should return Column Range for Row type", async () => {
      const { comp } = await renderComponent({ customType: TableStyleUtil.ROW });
      expect(comp.rangeLabel()).toContain("Column Range");
   });

   it("rangeLabel should return Row Range for non-Row type", async () => {
      const { comp } = await renderComponent({ customType: TableStyleUtil.COLUMN });
      expect(comp.rangeLabel()).toContain("Row Range");
   });
});

// ---------------------------------------------------------------------------
// Group 8: cancel / ok [Risk 1]
// ---------------------------------------------------------------------------

describe("EditCustomPatternsDialog — cancel / ok", () => {
   it("should emit onCancel when cancel() is called", async () => {
      const { comp } = await renderComponent();
      const emitSpy = vi.spyOn(comp.onCancel, "emit");
      comp.cancel();
      expect(emitSpy).toHaveBeenCalled();
   });

   it("should emit onCommit when ok() is called", async () => {
      const { comp } = await renderComponent();
      const emitSpy = vi.spyOn(comp.onCommit, "emit");
      comp.ok();
      expect(emitSpy).toHaveBeenCalled();
   });
});
