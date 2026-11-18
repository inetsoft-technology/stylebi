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

import { draftTargetLabel, dynamicValueLabel, hyperlinkRegionLabel, isDialogRadioSwitch } from "./editor-context";

describe("dynamicValueLabel", () => {
   it("shows a bare name as the property key", () => {
      expect(dynamicValueLabel("visible")).toBe("visible");
      expect(dynamicValueLabel("rangeValues[0]")).toBe("rangeValues[0]");
   });

   it("shows a namespaced name as the namespace and the key", () => {
      expect(dynamicValueLabel("dateComparison:periodsBack")).toBe("dateComparison periodsBack");
      expect(dynamicValueLabel("source:parentIdColumn")).toBe("source parentIdColumn");
      expect(dynamicValueLabel("chartRegion:legend/0/title"))
         .toBe("chartRegion legend/0/title");
   });

   it("splits only at the first colon", () => {
      expect(dynamicValueLabel("binding:a[0].sort.sortByField"))
         .toBe("binding a[0].sort.sortByField");
   });

   it("tolerates a missing name", () => {
      expect(dynamicValueLabel(undefined)).toBe("");
      expect(dynamicValueLabel(null)).toBe("");
   });
});

describe("hyperlinkRegionLabel", () => {
   it("reads each region key in words", () => {
      expect(hyperlinkRegionLabel("link")).toBe("link");
      expect(hyperlinkRegionLabel("title")).toBe("title link");
      expect(hyperlinkRegionLabel("emptyPlot")).toBe("empty plot link");
      expect(hyperlinkRegionLabel("cell[2,1]")).toBe("cell row 2, col 1");
      expect(hyperlinkRegionLabel("field[Total Sales]")).toBe("field 'Total Sales'");
      expect(hyperlinkRegionLabel("axis[Region]")).toBe("axis of 'Region'");
      expect(hyperlinkRegionLabel("text[Sales]")).toBe("text 'Sales'");
   });

   it("keeps a bracket inside a column name", () => {
      expect(hyperlinkRegionLabel("field[a]b]")).toBe("field 'a]b'");
   });

   it("passes an unknown key through and tolerates a missing one", () => {
      expect(hyperlinkRegionLabel("weird")).toBe("weird");
      expect(hyperlinkRegionLabel(undefined)).toBe("");
   });
});

describe("draftTargetLabel", () => {
   it("labels the three draft-only kinds, pending or not", () => {
      expect(draftTargetLabel({ kind: "worksheetVariableDefault", assembly: "V" }))
         .toBe("V → default value");
      expect(draftTargetLabel({ kind: "worksheetVariableDefault", assembly: "V", pending: true }))
         .toBe("V → default value (new variable)");
      expect(draftTargetLabel({ kind: "vsHyperlinkExpression", assembly: "T", name: "title" }))
         .toBe("T → hyperlink: title link");
      expect(draftTargetLabel({ kind: "worksheetSqlQuery", assembly: "Q" })).toBe("Q → SQL");
      expect(draftTargetLabel({ kind: "worksheetSqlQuery", assembly: "Q", pending: true }))
         .toBe("Q → SQL (new query)");
   });

   it("has no label for any other kind", () => {
      expect(draftTargetLabel({ kind: "dynamicValue", assembly: "T", name: "visible" })).toBeNull();
      expect(draftTargetLabel({ kind: "calcField", assembly: "T" })).toBeNull();
   });
});

describe("isDialogRadioSwitch", () => {
   it("is true for the two scripts of one assembly's dialog, in both directions", () => {
      expect(isDialogRadioSwitch({ kind: "assemblyMain", assembly: "Submit1" },
                                 { kind: "assemblyOnClick", assembly: "Submit1" })).toBe(true);
      expect(isDialogRadioSwitch({ kind: "assemblyOnClick", assembly: "Submit1" },
                                 { kind: "assemblyMain", assembly: "Submit1" })).toBe(true);
   });

   it("is true for the viewsheet Script dialog's onInit/onRefresh", () => {
      expect(isDialogRadioSwitch({ kind: "viewsheetOnInit" }, { kind: "viewsheetOnLoad" })).toBe(true);
   });

   it("is false for another assembly, another family, the same location or a missing side", () => {
      expect(isDialogRadioSwitch({ kind: "assemblyMain", assembly: "A" },
                                 { kind: "assemblyOnClick", assembly: "B" })).toBe(false);
      expect(isDialogRadioSwitch({ kind: "assemblyOnClick", assembly: "A" },
                                 { kind: "viewsheetOnInit" })).toBe(false);
      expect(isDialogRadioSwitch({ kind: "calcField", assembly: "T", name: "A" },
                                 { kind: "calcField", assembly: "T", name: "B" })).toBe(false);
      expect(isDialogRadioSwitch({ kind: "assemblyMain", assembly: "A" },
                                 { kind: "assemblyMain", assembly: "A" })).toBe(false);
      expect(isDialogRadioSwitch(null, { kind: "viewsheetOnLoad" })).toBe(false);
      expect(isDialogRadioSwitch({ kind: "viewsheetOnInit" }, undefined)).toBe(false);
   });
});
