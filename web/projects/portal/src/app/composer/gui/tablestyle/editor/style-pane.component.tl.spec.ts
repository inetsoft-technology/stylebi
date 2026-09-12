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
 * StylePaneComponent — Pass 1
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — selectRegion: sets tableStyle.selectedRegion to the given region;
 *                        calls TableStyleUtil.selectRegionTree(tableStyle)
 *   Group 2  [Risk 3] — getRegionFormat for regions: returns correct format object for
 *                        BODY/HEADER_ROW/HEADER_COLUMN/TRAILER_ROW/TRAILER_COLUMN
 *   Group 3  [Risk 2] — getRegionFormat for specList: returns specList[n].specFormat when
 *                        selectedRegion is a numeric string (not a named region, not a border)
 *   Group 4  [Risk 2] — getBorderFormat: returns correct border format for
 *                        TOP/RIGHT/LEFT/BOTTOM; returns null when selectedRegion is a
 *                        named region (not a border)
 *   Group 5  [Risk 2] — updateFormat: sets tableStyle.isModified=true; emits onUpdateTableStyle
 *   Group 6  [Risk 1] — isRegion / isRegionBorder: boundary checks (known constants return
 *                        true, others return false)
 *
 * Confirmed bugs: none
 *
 * Suspected bugs (header only):
 *   Suspicion A — getRegionFormat falls through to `style.bodyRegionFormat` in the else branch
 *     when selectedRegion IS a border region; this is intentional per the control flow but
 *     callers likely expect `getBorderFormat()` to be called instead. Tests verify the
 *     boundary to catch accidental merging of the two code paths.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";

import { StylePaneComponent } from "./style-pane.component";
import { TableStyleUtil } from "../../../../common/util/table-style-util";
import { TableStyleModel } from "../../../data/tablestyle/table-style-model";
import { TableStyleFormatModel } from "../../../data/tablestyle/table-style-format-model";
import { SpecificationModel } from "../../../data/tablestyle/specification-model";

// ---------------------------------------------------------------------------
// Factory helpers
// ---------------------------------------------------------------------------

function makeSpec(id: number): SpecificationModel {
   return {
      id,
      label: `Spec ${id}`,
      specFormat: { specId: id } as any,
      customType: "Row",
      start: 0,
      repeat: false,
      from: 0,
      to: 0,
      all: false,
   };
}

function makeStyleFormat(overrides: Partial<TableStyleFormatModel> = {}): TableStyleFormatModel {
   return {
      topBorderFormat: { topBorder: true } as any,
      bottomBorderFormat: { bottomBorder: true } as any,
      rightBorderFormat: { rightBorder: true } as any,
      leftBorderFormat: { leftBorder: true } as any,
      bodyRegionFormat: { body: true } as any,
      headerRowFormat: { headerRow: true } as any,
      trailerRowFormat: { trailerRow: true } as any,
      headerColFormat: { headerCol: true } as any,
      trailerColFormat: { trailerCol: true } as any,
      specList: [makeSpec(0), makeSpec(1), makeSpec(2)],
      origianlIndex: 0,
      ...overrides,
   };
}

function makeTableStyle(overrides: Partial<TableStyleModel> = {}): TableStyleModel {
   return {
      id: "style-1",
      type: "tableStyle",
      label: "Test Style",
      newAsset: false,
      styleName: "Test Style",
      styleId: "style-1",
      styleFormat: makeStyleFormat(),
      cssStyleFormat: null as any,
      selectedRegion: TableStyleUtil.BODY,
      selectedRegionLabel: "Body",
      regionsTreeRoot: null as any,
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(tableStyle: TableStyleModel = makeTableStyle()) {
   const { fixture } = await render(StylePaneComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      // Prevent TableStylePreviewPaneComponent / TableStyleFormatPaneComponent from being
      // instantiated — their DI chains reach ModelService which fires HTTP and calls
      // NgbModal.open in handleError, triggering NG0205 after fixture teardown.
      componentImports: [],
      componentProperties: {
         tableStyle,
      },
   });
   const comp = fixture.componentInstance as StylePaneComponent;
   // Wire @ViewChild notifications stub (null under NO_ERRORS_SCHEMA)
   (comp as any).notifications = { success: vi.fn() };
   return { fixture, comp };
}

// ---------------------------------------------------------------------------
// Per-test reset
// ---------------------------------------------------------------------------

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: selectRegion [Risk 3]
// ---------------------------------------------------------------------------

describe("StylePaneComponent — selectRegion", () => {

   // 🔁 Regression-sensitive: selectRegion must set selectedRegion AND call
   //    selectRegionTree so the tree UI highlights the correct node.
   it("should set tableStyle.selectedRegion to the supplied region", async () => {
      const tableStyle = makeTableStyle();
      const { comp } = await renderComponent(tableStyle);

      comp.selectRegion(TableStyleUtil.HEADER_ROW);

      expect(tableStyle.selectedRegion).toBe(TableStyleUtil.HEADER_ROW);
   });

   // 🔁 Regression-sensitive: without calling selectRegionTree the tree sidebar stays
   //    out of sync with the format panel, leading to stale region labels.
   it("should call TableStyleUtil.selectRegionTree with the tableStyle", async () => {
      const tableStyle = makeTableStyle();
      const { comp } = await renderComponent(tableStyle);
      const spy = vi.spyOn(TableStyleUtil, "selectRegionTree");

      comp.selectRegion(TableStyleUtil.BODY);

      expect(spy).toHaveBeenCalledWith(tableStyle);
   });

   it("should update selectedRegion before calling selectRegionTree", async () => {
      const tableStyle = makeTableStyle();
      const { comp } = await renderComponent(tableStyle);
      let capturedRegion: string | undefined;
      vi.spyOn(TableStyleUtil, "selectRegionTree").mockImplementation((s) => {
         capturedRegion = s.selectedRegion;
      });

      comp.selectRegion(TableStyleUtil.TRAILER_COLUMN);

      expect(capturedRegion).toBe(TableStyleUtil.TRAILER_COLUMN);
   });
});

// ---------------------------------------------------------------------------
// Group 2: getRegionFormat for named regions [Risk 3]
// ---------------------------------------------------------------------------

describe("StylePaneComponent — getRegionFormat (named regions)", () => {

   // 🔁 Regression-sensitive: each named region must map to the distinct format object;
   //    a wrong mapping causes the wrong panel to be shown and changes to bleed across regions.
   it("should return bodyRegionFormat when selectedRegion is BODY", async () => {
      const tableStyle = makeTableStyle({ selectedRegion: TableStyleUtil.BODY });
      const { comp } = await renderComponent(tableStyle);

      expect(comp.getRegionFormat()).toBe(tableStyle.styleFormat.bodyRegionFormat);
   });

   it("should return headerRowFormat when selectedRegion is HEADER_ROW", async () => {
      const tableStyle = makeTableStyle({ selectedRegion: TableStyleUtil.HEADER_ROW });
      const { comp } = await renderComponent(tableStyle);

      expect(comp.getRegionFormat()).toBe(tableStyle.styleFormat.headerRowFormat);
   });

   it("should return headerColFormat when selectedRegion is HEADER_COLUMN", async () => {
      const tableStyle = makeTableStyle({ selectedRegion: TableStyleUtil.HEADER_COLUMN });
      const { comp } = await renderComponent(tableStyle);

      expect(comp.getRegionFormat()).toBe(tableStyle.styleFormat.headerColFormat);
   });

   it("should return trailerRowFormat when selectedRegion is TRAILER_ROW", async () => {
      const tableStyle = makeTableStyle({ selectedRegion: TableStyleUtil.TRAILER_ROW });
      const { comp } = await renderComponent(tableStyle);

      expect(comp.getRegionFormat()).toBe(tableStyle.styleFormat.trailerRowFormat);
   });

   it("should return trailerColFormat when selectedRegion is TRAILER_COLUMN", async () => {
      const tableStyle = makeTableStyle({ selectedRegion: TableStyleUtil.TRAILER_COLUMN });
      const { comp } = await renderComponent(tableStyle);

      expect(comp.getRegionFormat()).toBe(tableStyle.styleFormat.trailerColFormat);
   });
});

// ---------------------------------------------------------------------------
// Group 3: getRegionFormat for specList (numeric region) [Risk 2]
// ---------------------------------------------------------------------------

describe("StylePaneComponent — getRegionFormat (specList / numeric region)", () => {

   it("should return specList[0].specFormat when selectedRegion is '0'", async () => {
      const tableStyle = makeTableStyle({ selectedRegion: "0" });
      const { comp } = await renderComponent(tableStyle);

      expect(comp.getRegionFormat()).toBe(tableStyle.styleFormat.specList[0].specFormat);
   });

   it("should return specList[2].specFormat when selectedRegion is '2'", async () => {
      const tableStyle = makeTableStyle({ selectedRegion: "2" });
      const { comp } = await renderComponent(tableStyle);

      expect(comp.getRegionFormat()).toBe(tableStyle.styleFormat.specList[2].specFormat);
   });

   it("should return undefined for an out-of-range index", async () => {
      const tableStyle = makeTableStyle({ selectedRegion: "99" });
      const { comp } = await renderComponent(tableStyle);

      expect(comp.getRegionFormat()).toBeUndefined();
   });
});

// ---------------------------------------------------------------------------
// Group 4: getBorderFormat [Risk 2]
// ---------------------------------------------------------------------------

describe("StylePaneComponent — getBorderFormat", () => {

   it("should return topBorderFormat when selectedRegion is TOP_BORDER", async () => {
      const tableStyle = makeTableStyle({ selectedRegion: TableStyleUtil.TOP_BORDER });
      const { comp } = await renderComponent(tableStyle);

      expect(comp.getBorderFormat()).toBe(tableStyle.styleFormat.topBorderFormat);
   });

   it("should return rightBorderFormat when selectedRegion is RIGHT_BORDER", async () => {
      const tableStyle = makeTableStyle({ selectedRegion: TableStyleUtil.RIGHT_BORDER });
      const { comp } = await renderComponent(tableStyle);

      expect(comp.getBorderFormat()).toBe(tableStyle.styleFormat.rightBorderFormat);
   });

   it("should return leftBorderFormat when selectedRegion is LEFT_BORDER", async () => {
      const tableStyle = makeTableStyle({ selectedRegion: TableStyleUtil.LEFT_BORDER });
      const { comp } = await renderComponent(tableStyle);

      expect(comp.getBorderFormat()).toBe(tableStyle.styleFormat.leftBorderFormat);
   });

   it("should return bottomBorderFormat when selectedRegion is BOTTOM_BORDER", async () => {
      const tableStyle = makeTableStyle({ selectedRegion: TableStyleUtil.BOTTOM_BORDER });
      const { comp } = await renderComponent(tableStyle);

      expect(comp.getBorderFormat()).toBe(tableStyle.styleFormat.bottomBorderFormat);
   });

   it("should return null when selectedRegion is a named (non-border) region", async () => {
      const tableStyle = makeTableStyle({ selectedRegion: TableStyleUtil.BODY });
      const { comp } = await renderComponent(tableStyle);

      expect(comp.getBorderFormat()).toBeNull();
   });

   it("should return null when selectedRegion is a numeric string", async () => {
      const tableStyle = makeTableStyle({ selectedRegion: "1" });
      const { comp } = await renderComponent(tableStyle);

      expect(comp.getBorderFormat()).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 5: updateFormat [Risk 2]
// ---------------------------------------------------------------------------

describe("StylePaneComponent — updateFormat", () => {

   it("should set tableStyle.isModified to true", async () => {
      const tableStyle = makeTableStyle();
      tableStyle.isModified = false;
      const { comp } = await renderComponent(tableStyle);

      comp.updateFormat();

      expect(tableStyle.isModified).toBe(true);
   });

   it("should emit onUpdateTableStyle", async () => {
      const tableStyle = makeTableStyle();
      const { comp } = await renderComponent(tableStyle);
      const spy = vi.fn();
      comp.onUpdateTableStyle.subscribe(spy);

      comp.updateFormat();

      expect(spy).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 6: isRegion / isRegionBorder [Risk 1]
// ---------------------------------------------------------------------------

describe("StylePaneComponent — isRegion", () => {

   it("should return true for BODY", async () => {
      const { comp } = await renderComponent();
      expect(comp.isRegion(TableStyleUtil.BODY)).toBe(true);
   });

   it("should return true for HEADER_ROW", async () => {
      const { comp } = await renderComponent();
      expect(comp.isRegion(TableStyleUtil.HEADER_ROW)).toBe(true);
   });

   it("should return true for HEADER_COLUMN", async () => {
      const { comp } = await renderComponent();
      expect(comp.isRegion(TableStyleUtil.HEADER_COLUMN)).toBe(true);
   });

   it("should return true for TRAILER_ROW", async () => {
      const { comp } = await renderComponent();
      expect(comp.isRegion(TableStyleUtil.TRAILER_ROW)).toBe(true);
   });

   it("should return true for TRAILER_COLUMN", async () => {
      const { comp } = await renderComponent();
      expect(comp.isRegion(TableStyleUtil.TRAILER_COLUMN)).toBe(true);
   });

   it("should return false for a border region", async () => {
      const { comp } = await renderComponent();
      expect(comp.isRegion(TableStyleUtil.TOP_BORDER)).toBe(false);
   });

   it("should return false for a numeric string", async () => {
      const { comp } = await renderComponent();
      expect(comp.isRegion("0")).toBe(false);
   });
});

describe("StylePaneComponent — isRegionBorder", () => {

   it("should return true for TOP_BORDER", async () => {
      const { comp } = await renderComponent();
      expect(comp.isRegionBorder(TableStyleUtil.TOP_BORDER)).toBe(true);
   });

   it("should return true for RIGHT_BORDER", async () => {
      const { comp } = await renderComponent();
      expect(comp.isRegionBorder(TableStyleUtil.RIGHT_BORDER)).toBe(true);
   });

   it("should return true for LEFT_BORDER", async () => {
      const { comp } = await renderComponent();
      expect(comp.isRegionBorder(TableStyleUtil.LEFT_BORDER)).toBe(true);
   });

   it("should return true for BOTTOM_BORDER", async () => {
      const { comp } = await renderComponent();
      expect(comp.isRegionBorder(TableStyleUtil.BOTTOM_BORDER)).toBe(true);
   });

   it("should return false for BODY", async () => {
      const { comp } = await renderComponent();
      expect(comp.isRegionBorder(TableStyleUtil.BODY)).toBe(false);
   });

   it("should return false for a numeric string", async () => {
      const { comp } = await renderComponent();
      expect(comp.isRegionBorder("1")).toBe(false);
   });
});
