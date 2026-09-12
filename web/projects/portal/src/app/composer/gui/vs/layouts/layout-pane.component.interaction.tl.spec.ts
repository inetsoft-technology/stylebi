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
 * LayoutPane — Pass 1: Interaction
 *
 * 回归主体：navigation / user flows / pure computations
 *
 *   Group 1  — getAssemblyName: always null
 *   Group 2  — getPLayoutSize (private): unit conversion (inches, mm, default)
 *   Group 3  — getBackgroundImage: pure SVG data URL construction
 *   Group 4  — getSnapGridStyle: returns {} when snapToGrid=false, background-image style when true
 *   Group 5  — getLayoutObjects: returns objects/headerObjects/footerObjects by currentPrintSection
 *   Group 6  — onSnap: sets currentSnapGuides
 *   Group 7  — trackByFn: returns object.name
 *   Group 8  — guideSize getter/setter: basic property round-trip
 *   Group 9  — sizeGuidesVisible: based on printLayout || guideType
 *   Group 10 — updateObjectList (private): appends new object; updates existing by name
 *   Group 11 — getNextObjectName (private): generates unique prefixed names
 *   Group 12 — processUpdateLayoutUndoStateCommand (private): emits when id matches runtimeId
 */

import { LayoutPane } from "./layout-pane.component";
import { VSLayoutModel, PrintLayoutMeasures } from "../../../data/vs/vs-layout-model";
import { PrintLayoutSection } from "../../../../vsobjects/model/layout/print-layout-section";
import { GuideBounds } from "../../../../vsobjects/model/layout/guide-bounds";
import { AssemblyType } from "../assembly-type";
import {
   makeComponent,
   makeLayoutObject,
} from "./layout-pane.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: getAssemblyName [Risk 1]
// ---------------------------------------------------------------------------

describe("LayoutPane — getAssemblyName", () => {

   it("always returns null (required abstract CommandProcessor override)", () => {
      const { comp } = makeComponent();
      expect(comp.getAssemblyName()).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 2: getPLayoutSize (private) [Risk 2]
// ---------------------------------------------------------------------------

describe("LayoutPane — getPLayoutSize (private)", () => {

   it("converts inches to points: asize * 72", () => {
      const { comp } = makeComponent();
      const result = (comp as any).getPLayoutSize(2, "inches");
      expect(result).toBe(2 * PrintLayoutMeasures.INCH_POINT); // 144
   });

   it("converts mm to points: asize / 25.4 * 72", () => {
      const { comp } = makeComponent();
      const result = (comp as any).getPLayoutSize(25.4, "mm");
      expect(result).toBeCloseTo(PrintLayoutMeasures.INCH_POINT); // 72
   });

   it("returns asize unchanged for unknown unit", () => {
      const { comp } = makeComponent();
      expect((comp as any).getPLayoutSize(100, "px")).toBe(100);
      expect((comp as any).getPLayoutSize(50, "pt")).toBe(50);
   });

   it("handles zero size for any unit", () => {
      const { comp } = makeComponent();
      expect((comp as any).getPLayoutSize(0, "inches")).toBe(0);
      expect((comp as any).getPLayoutSize(0, "mm")).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 3: getBackgroundImage [Risk 1]
// ---------------------------------------------------------------------------

describe("LayoutPane — getBackgroundImage", () => {

   it("returns a data URL string containing the snapGrid value", () => {
      const { comp } = makeComponent();
      const url = comp.getBackgroundImage(20);
      expect(url).toContain("url");
      expect(url).toContain("data:image/svg+xml");
      expect(url).toContain("20");
   });

   it("embeds the snapGrid size in the SVG width and height", () => {
      const { comp } = makeComponent();
      const url = comp.getBackgroundImage(15);
      expect(url).toContain("width%3D%2215%22");
      expect(url).toContain("height%3D%2215%22");
   });
});

// ---------------------------------------------------------------------------
// Group 4: getSnapGridStyle [Risk 1]
// ---------------------------------------------------------------------------

describe("LayoutPane — getSnapGridStyle", () => {

   it("returns empty object when snapToGrid is false", () => {
      const { comp } = makeComponent();
      comp.snapToGrid = false;
      const style = comp.getSnapGridStyle();
      expect(Object.keys(style).length).toBe(0);
   });

   it("returns background-image style when snapToGrid is true", () => {
      const { comp } = makeComponent();
      comp.snapToGrid = true;
      comp.vs.snapGrid = 10;
      const style = comp.getSnapGridStyle() as any;
      expect(style["background-image"]).toBeTruthy();
      expect(style["background-image"]).toContain("url");
   });

   it("uses vs.snapGrid value in the background-image", () => {
      const { comp } = makeComponent();
      comp.snapToGrid = true;
      comp.vs.snapGrid = 25;
      const style = comp.getSnapGridStyle() as any;
      expect(style["background-image"]).toContain("25");
   });
});

// ---------------------------------------------------------------------------
// Group 5: getLayoutObjects [Risk 2]
// ---------------------------------------------------------------------------

describe("LayoutPane — getLayoutObjects", () => {

   it("returns objects when currentPrintSection is CONTENT", () => {
      const { comp, mocks } = makeComponent();
      const obj = makeLayoutObject("Obj1");
      mocks.vsLayout.objects = [obj];
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.CONTENT;

      expect(comp.getLayoutObjects()).toEqual([obj]);
   });

   it("returns headerObjects when currentPrintSection is HEADER", () => {
      const { comp, mocks } = makeComponent();
      const hdr = makeLayoutObject("Hdr1");
      mocks.vsLayout.headerObjects = [hdr];
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.HEADER;

      expect(comp.getLayoutObjects()).toEqual([hdr]);
   });

   it("returns footerObjects when currentPrintSection is FOOTER", () => {
      const { comp, mocks } = makeComponent();
      const ftr = makeLayoutObject("Ftr1");
      mocks.vsLayout.footerObjects = [ftr];
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.FOOTER;

      expect(comp.getLayoutObjects()).toEqual([ftr]);
   });

   it("returns empty array when section has no objects", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.objects = [];
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.CONTENT;

      expect(comp.getLayoutObjects()).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 6: onSnap [Risk 1]
// ---------------------------------------------------------------------------

describe("LayoutPane — onSnap", () => {

   it("sets currentSnapGuides to the provided snap object", () => {
      const { comp } = makeComponent();
      const snap = { x: 100, y: 200 };
      comp.onSnap(snap);
      expect(comp.currentSnapGuides).toEqual({ x: 100, y: 200 });
   });

   it("sets currentSnapGuides to null when null is passed", () => {
      const { comp } = makeComponent();
      comp.onSnap({ x: 50, y: 50 });
      comp.onSnap(null);
      expect(comp.currentSnapGuides).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 7: trackByFn [Risk 1]
// ---------------------------------------------------------------------------

describe("LayoutPane — trackByFn", () => {

   it("returns the object name for identity tracking", () => {
      const { comp } = makeComponent();
      const obj = makeLayoutObject("Text1");
      expect(comp.trackByFn(0, obj)).toBe("Text1");
   });

   it("returns a different name for each distinct object", () => {
      const { comp } = makeComponent();
      const a = makeLayoutObject("Alpha");
      const b = makeLayoutObject("Beta");
      expect(comp.trackByFn(0, a)).not.toBe(comp.trackByFn(1, b));
   });
});

// ---------------------------------------------------------------------------
// Group 8: guideSize getter/setter [Risk 1]
// ---------------------------------------------------------------------------

describe("LayoutPane — guideSize getter/setter", () => {

   it("stores and returns the value via getter", () => {
      const { comp } = makeComponent();
      const dim = { width: 320, height: 568 } as any;
      comp.guideSize = dim;
      expect(comp.guideSize).toBe(dim);
   });

   it("initial guideSize is a Dimension(0,0)", () => {
      const { comp } = makeComponent();
      expect(comp.guideSize.width).toBe(0);
      expect(comp.guideSize.height).toBe(0);
   });
});

// ---------------------------------------------------------------------------
// Group 9: sizeGuidesVisible [Risk 2]
// ---------------------------------------------------------------------------

describe("LayoutPane — sizeGuidesVisible", () => {

   it("is true when printLayout is true", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.printLayout = true;
      mocks.vsLayout.guideType = GuideBounds.GUIDES_NONE;
      expect(comp.sizeGuidesVisible).toBe(true);
   });

   it("is true when guideType is set (non-NONE)", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.printLayout = false;
      mocks.vsLayout.guideType = GuideBounds.GUIDES_16_9_LANDSCAPE;
      expect(comp.sizeGuidesVisible).toBe(true);
   });

   it("is false when printLayout is false and guideType is GUIDES_NONE", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.printLayout = false;
      mocks.vsLayout.guideType = GuideBounds.GUIDES_NONE;
      expect(comp.sizeGuidesVisible).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 10: updateObjectList (private) [Risk 2]
// ---------------------------------------------------------------------------

describe("LayoutPane — updateObjectList (private)", () => {

   it("appends a new object when not already in the list", () => {
      const { comp } = makeComponent();
      const obj = makeLayoutObject("NewObj");
      const result = (comp as any).updateObjectList([], obj);
      expect(result.some((o: any) => o.name === "NewObj")).toBe(true);
   });

   it("replaces an existing object with the same name", () => {
      const { comp } = makeComponent();
      const original = makeLayoutObject("Obj1");
      const updated  = makeLayoutObject("Obj1");
      updated.width = 200;

      const list = [original];
      const result = (comp as any).updateObjectList(list, updated);

      expect(result.length).toBe(1);
      expect(result[0].width).toBe(200);
   });

   it("creates a new single-element list when objects arg is null", () => {
      const { comp } = makeComponent();
      const obj = makeLayoutObject("Obj1");
      const result = (comp as any).updateObjectList(null, obj);
      expect(result).toHaveLength(1);
      expect(result[0].name).toBe("Obj1");
   });
});

// ---------------------------------------------------------------------------
// Group 11: getNextObjectName (private) [Risk 2]
// ---------------------------------------------------------------------------

describe("LayoutPane — getNextObjectName (private)", () => {

   it("returns 'Content_Text1' for TEXT_ASSET in CONTENT section when no existing objects", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.CONTENT;
      mocks.vsLayout.objects = [];
      mocks.vsLayout.headerObjects = [];
      mocks.vsLayout.footerObjects = [];

      const name = (comp as any).getNextObjectName(AssemblyType.TEXT_ASSET);
      expect(name).toBe("Content_Text1");
   });

   it("returns 'Header_Image1' for IMAGE_ASSET in HEADER section", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.HEADER;
      mocks.vsLayout.objects = [];
      mocks.vsLayout.headerObjects = [];
      mocks.vsLayout.footerObjects = [];

      const name = (comp as any).getNextObjectName(AssemblyType.IMAGE_ASSET);
      expect(name).toBe("Header_Image1");
   });

   it("increments suffix when name is already taken", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.CONTENT;
      mocks.vsLayout.objects = [makeLayoutObject("Content_Text1")];
      mocks.vsLayout.headerObjects = [];
      mocks.vsLayout.footerObjects = [];

      const name = (comp as any).getNextObjectName(AssemblyType.TEXT_ASSET);
      expect(name).toBe("Content_Text2");
   });

   it("returns 'Footer_PageBreak1' for PAGEBREAK_ASSET in FOOTER section", () => {
      const { comp, mocks } = makeComponent();
      mocks.vsLayout.currentPrintSection = PrintLayoutSection.FOOTER;
      mocks.vsLayout.objects = [];
      mocks.vsLayout.headerObjects = [];
      mocks.vsLayout.footerObjects = [];

      const name = (comp as any).getNextObjectName(AssemblyType.PAGEBREAK_ASSET);
      expect(name).toBe("Footer_PageBreak1");
   });
});

// ---------------------------------------------------------------------------
// Group 12: processUpdateLayoutUndoStateCommand (private) [Risk 2]
// ---------------------------------------------------------------------------

describe("LayoutPane — processUpdateLayoutUndoStateCommand (private)", () => {

   it("emits onUpdateLayoutUndoState when command.id matches runtimeId", () => {
      const { comp } = makeComponent();
      comp.runtimeId = "rt-abc";

      const spy = vi.fn();
      comp.onUpdateLayoutUndoState.subscribe(spy);

      (comp as any).processUpdateLayoutUndoStateCommand({ id: "rt-abc", undoEnabled: true, redoEnabled: false });

      expect(spy).toHaveBeenCalledTimes(1);
      expect(spy.mock.calls[0][0].id).toBe("rt-abc");
   });

   it("does NOT emit when command.id does not match runtimeId", () => {
      const { comp } = makeComponent();
      comp.runtimeId = "rt-abc";

      const spy = vi.fn();
      comp.onUpdateLayoutUndoState.subscribe(spy);

      (comp as any).processUpdateLayoutUndoStateCommand({ id: "other-id", undoEnabled: true });

      expect(spy).not.toHaveBeenCalled();
   });
});
