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
 * FileFormatPane — Pass 3: Display
 *
 * Scope (per prescan Pass 3 method list): getExport (7-way string-to-enum dispatch,
 * including the unrecognized-type fallback), matchLayoutVisible (3-condition boolean getter).
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — getExport: one case per branch plus the fallback, plus
 *                       case-insensitivity since the method lowercases its input
 *   Group 2 [Risk 2] — matchLayoutVisible: each of the 3 operands' independent
 *                       falsy/truthy contribution (B2)
 *
 * Confirmed bugs (it.fails): none
 */

import { FileFormatType } from "../model/file-format-type";
import { createComponent, makeModel } from "./file-format-pane.component.test-helpers";

// ---------------------------------------------------------------------------
// Group 1: getExport [Risk 2]
// ---------------------------------------------------------------------------

describe("FileFormatPane — getExport", () => {
   it.each([
      ["excel", FileFormatType.EXPORT_TYPE_EXCEL],
      ["powerpoint", FileFormatType.EXPORT_TYPE_POWERPOINT],
      ["pdf", FileFormatType.EXPORT_TYPE_PDF],
      ["snapshot", FileFormatType.EXPORT_TYPE_SNAPSHOT],
      ["png", FileFormatType.EXPORT_TYPE_PNG],
      ["html", FileFormatType.EXPORT_TYPE_HTML],
      ["csv", FileFormatType.EXPORT_TYPE_CSV],
   ])("should map '%s' to %i", (type, expected) => {
      const { comp } = createComponent();
      expect(comp.getExport(type)).toBe(expected);
   });

   it("should be case-insensitive", () => {
      const { comp } = createComponent();
      expect(comp.getExport("PDF")).toBe(FileFormatType.EXPORT_TYPE_PDF);
   });

   it("should fall back to Excel for an unrecognized type", () => {
      const { comp } = createComponent();
      expect(comp.getExport("bogus")).toBe(FileFormatType.EXPORT_TYPE_EXCEL);
   });
});

// ---------------------------------------------------------------------------
// Group 2: matchLayoutVisible [Risk 2]
// ---------------------------------------------------------------------------

describe("FileFormatPane — matchLayoutVisible", () => {
   it("should be true for a plain non-HTML, non-CSV, non-PDF format", () => {
      const { comp } = createComponent({
         model: makeModel({ formatType: FileFormatType.EXPORT_TYPE_EXCEL }),
      });
      expect(comp.matchLayoutVisible).toBe(true);
   });

   it("should be false when the format type is HTML", () => {
      const { comp } = createComponent({
         model: makeModel({ formatType: FileFormatType.EXPORT_TYPE_HTML }),
      });
      expect(comp.matchLayoutVisible).toBe(false);
   });

   it("should be false when the format type is CSV", () => {
      const { comp } = createComponent({
         model: makeModel({ formatType: FileFormatType.EXPORT_TYPE_CSV }),
      });
      expect(comp.matchLayoutVisible).toBe(false);
   });

   it("should be false for PDF with a print layout", () => {
      const { comp } = createComponent({
         model: makeModel({ formatType: FileFormatType.EXPORT_TYPE_PDF, hasPrintLayout: true }),
      });
      expect(comp.matchLayoutVisible).toBe(false);
   });

   it("should be true for PDF without a print layout", () => {
      const { comp } = createComponent({
         model: makeModel({ formatType: FileFormatType.EXPORT_TYPE_PDF, hasPrintLayout: false }),
      });
      expect(comp.matchLayoutVisible).toBe(true);
   });

   it("should be true for a non-PDF format even when hasPrintLayout is true", () => {
      const { comp } = createComponent({
         model: makeModel({ formatType: FileFormatType.EXPORT_TYPE_EXCEL, hasPrintLayout: true }),
      });
      expect(comp.matchLayoutVisible).toBe(true);
   });
});
