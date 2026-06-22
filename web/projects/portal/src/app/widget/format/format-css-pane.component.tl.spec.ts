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
 * FormatCSSPane — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — selectCSSClass(): index==-1 → clears selectedCSSClassIndexes to [];
 *     index not yet selected → appended; index already selected → removed (toggle-off);
 *     cssClass is always the comma-joined names of currently selected classes;
 *     cssClassChange is emitted with the new value
 *   Group 2 [Risk 3] — selectCSSID(): sets selectedCSSIDIndex and cssID, emits cssIDChange
 *   Group 3 [Risk 2] — ngOnChanges(): selectedCSSIDIndex set from cssIDs.indexOf(cssID);
 *     selectedCSSClassIndexes set from cssClass.split(",").map(indexOf)
 *   Group 4 [Risk 1] — initial state: selectedCSSIDIndex=-1, selectedCSSClassIndexes=[]
 *
 * Confirmed bugs (it.fails):
 *   None.
 *
 * Out of scope:
 *   Template/NgbDropdown interaction — library-level.
 */

import { NO_ERRORS_SCHEMA, SimpleChanges } from "@angular/core";
import { render } from "@testing-library/angular";
import { FormatCSSPane } from "./format-css-pane.component";

// ---------------------------------------------------------------------------
// Shared fixture
// ---------------------------------------------------------------------------

async function renderComponent(overrides: {
   cssID?: string;
   cssClass?: string;
   cssIDs?: string[];
   cssClasses?: string[];
   cssType?: string;
} = {}) {
   const inputs = {
      cssID: "",
      cssClass: "",
      cssIDs: [],
      cssClasses: [],
      cssType: "",
      ...overrides,
   };

   const { fixture } = await render(FormatCSSPane, {
      schemas: [NO_ERRORS_SCHEMA],
      componentImports: [],
      componentInputs: inputs,
   });
   const comp = fixture.componentInstance as FormatCSSPane;
   return { comp, fixture };
}

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: selectCSSClass [Risk 3]
// ---------------------------------------------------------------------------

describe("FormatCSSPane — selectCSSClass", () => {
   // 🔁 Regression-sensitive: index==-1 must clear all selections (reset/no-class option).
   it("should clear all selections when index is -1", async () => {
      const { comp } = await renderComponent({
         cssClasses: ["bold", "italic", "underline"],
         cssClass: "bold,italic",
      });
      comp.selectCSSClass("", -1);
      expect(comp.selectedCSSClassIndexes).toEqual([]);
      expect(comp.cssClass).toBe("");
   });

   it("should add an index when the class is not yet selected", async () => {
      const { comp } = await renderComponent({ cssClasses: ["bold", "italic"] });
      comp.selectCSSClass("bold", 0);
      expect(comp.selectedCSSClassIndexes).toContain(0);
      expect(comp.cssClass).toBe("bold");
   });

   it("should remove an already-selected index (toggle-off)", async () => {
      const { comp } = await renderComponent({
         cssClasses: ["bold", "italic"],
         cssClass: "bold",
      });
      comp["selectedCSSClassIndexes"] = [0];
      comp.selectCSSClass("bold", 0);
      expect(comp.selectedCSSClassIndexes).not.toContain(0);
      expect(comp.cssClass).toBe("");
   });

   it("should accumulate multiple selected classes", async () => {
      const { comp } = await renderComponent({ cssClasses: ["bold", "italic", "underline"] });
      comp.selectCSSClass("bold", 0);
      comp.selectCSSClass("italic", 1);
      expect(comp.cssClass).toBe("bold,italic");
   });

   it("should emit cssClassChange with the new cssClass value", async () => {
      const { comp } = await renderComponent({ cssClasses: ["bold", "italic"] });
      const emitSpy = vi.spyOn(comp.cssClassChange, "emit");
      comp.selectCSSClass("italic", 1);
      expect(emitSpy).toHaveBeenCalledWith("italic");
   });

   it("should emit cssClassChange with empty string after clearing", async () => {
      const { comp } = await renderComponent({ cssClasses: ["bold"] });
      comp["selectedCSSClassIndexes"] = [0];
      const emitSpy = vi.spyOn(comp.cssClassChange, "emit");
      comp.selectCSSClass("", -1);
      expect(emitSpy).toHaveBeenCalledWith("");
   });
});

// ---------------------------------------------------------------------------
// Group 2: selectCSSID [Risk 3]
// ---------------------------------------------------------------------------

describe("FormatCSSPane — selectCSSID", () => {
   it("should set selectedCSSIDIndex and cssID, then emit cssIDChange", async () => {
      const { comp } = await renderComponent({
         cssIDs: ["id-a", "id-b", "id-c"],
      });
      const emitSpy = vi.spyOn(comp.cssIDChange, "emit");

      comp.selectCSSID("id-b", 1);

      expect(comp.selectedCSSIDIndex).toBe(1);
      expect(comp.cssID).toBe("id-b");
      expect(emitSpy).toHaveBeenCalledWith("id-b");
   });

   it("should update selectedCSSIDIndex when switching IDs", async () => {
      const { comp } = await renderComponent({ cssIDs: ["id-a", "id-b"] });
      comp.selectCSSID("id-a", 0);
      comp.selectCSSID("id-b", 1);
      expect(comp.selectedCSSIDIndex).toBe(1);
      expect(comp.cssID).toBe("id-b");
   });
});

// ---------------------------------------------------------------------------
// Group 3: ngOnChanges [Risk 2]
// ---------------------------------------------------------------------------

describe("FormatCSSPane — ngOnChanges", () => {
   it("should set selectedCSSIDIndex from cssIDs.indexOf(cssID)", async () => {
      const { comp, fixture } = await renderComponent({
         cssIDs: ["id-a", "id-b", "id-c"],
         cssID: "id-b",
      });
      // ngOnChanges is triggered by Angular on componentInputs; verify result
      expect(comp.selectedCSSIDIndex).toBe(1);
   });

   it("should leave selectedCSSIDIndex as -1 when cssID is not in cssIDs", async () => {
      const { comp } = await renderComponent({
         cssIDs: ["id-a", "id-b"],
         cssID: "id-z",
      });
      expect(comp.selectedCSSIDIndex).toBe(-1);
   });

   it("should set selectedCSSClassIndexes from cssClass.split(',').map(indexOf)", async () => {
      const { comp } = await renderComponent({
         cssClasses: ["bold", "italic", "underline"],
         cssClass: "bold,underline",
      });
      expect(comp.selectedCSSClassIndexes).toEqual([0, 2]);
   });

   it("should set selectedCSSClassIndexes to [] when cssClass is empty", async () => {
      const { comp } = await renderComponent({
         cssClasses: ["bold", "italic"],
         cssClass: "",
      });
      expect(comp.selectedCSSClassIndexes).toEqual([]);
   });

   it("should leave selectedCSSIDIndex -1 when cssIDs is empty", async () => {
      const { comp } = await renderComponent({ cssIDs: [], cssID: "id-a" });
      expect(comp.selectedCSSIDIndex).toBe(-1);
   });
});

// ---------------------------------------------------------------------------
// Group 4: initial state [Risk 1]
// ---------------------------------------------------------------------------

describe("FormatCSSPane — initial state", () => {
   it("should start with selectedCSSIDIndex=-1 and selectedCSSClassIndexes=[]", async () => {
      const { comp } = await renderComponent();
      expect(comp.selectedCSSIDIndex).toBe(-1);
      expect(comp.selectedCSSClassIndexes).toEqual([]);
   });
});
