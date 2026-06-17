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
 * VSObjectContainer — Pass 3: Display
 *
 * Coverage of pure-computation display helpers:
 *   Group 1   getAssemblyAsClass — spaces → dashes
 *   Group 2   trackByName — returns absoluteName
 *   Group 3   isAtBottom — math against allAssemblyBounds.bottom
 *   Group 4   zIndex — base + container accumulation + annotation boost
 *   Group 5   isInScrollViewport — intersection math (private, via isObjectRendered path)
 *   Group 6   isObjectRendered — virtualScrolling flag and renderedObjects map
 *   Group 7   getActualWidth — dataTip realWidth vs objectFormat.width
 *   Group 8   isChartAnnotationSelected — selectedAnnotations membership
 *   Group 9   needsZIndexBoost — data tip and pop component cases
 *   Group 10  toolbarForceHidden — delegates to miniToolbarService
 */

import {
   makeComponent,
   makeVSObject,
   makeVsInfo,
   makeObjectFormat,
} from "./vs-object-container.component.test-helpers";

// ---------------------------------------------------------------------------
// Group 1 — getAssemblyAsClass
// ---------------------------------------------------------------------------

describe("Group 1 — getAssemblyAsClass: spaces replaced with dashes", () => {
   it("should replace spaces with dashes in absoluteName", () => {
      const { comp } = makeComponent();
      const obj = makeVSObject({ absoluteName: "My Chart 1" });
      expect(comp.getAssemblyAsClass(obj)).toBe("My-Chart-1");
   });

   it("should return the name unchanged when no spaces present", () => {
      const { comp } = makeComponent();
      const obj = makeVSObject({ absoluteName: "Chart1" });
      expect(comp.getAssemblyAsClass(obj)).toBe("Chart1");
   });
});

// ---------------------------------------------------------------------------
// Group 2 — trackByName
// ---------------------------------------------------------------------------

describe("Group 2 — trackByName: returns absoluteName", () => {
   it("should return the absoluteName of the item", () => {
      const { comp } = makeComponent();
      const obj = makeVSObject({ absoluteName: "Gauge1" });
      expect(comp.trackByName(0, obj)).toBe("Gauge1");
   });
});

// ---------------------------------------------------------------------------
// Group 3 — isAtBottom
// ---------------------------------------------------------------------------

describe("Group 3 — isAtBottom: math against allAssemblyBounds.bottom", () => {
   it("should return false when scaleToScreen=false and scaleToScreenOnly=true", () => {
      const { comp } = makeComponent();
      comp.scaleToScreen = false;
      const obj = makeVSObject({ objectFormat: makeObjectFormat({ top: 90, height: 10 }) });
      comp.vsInfo = makeVsInfo([obj]);
      comp.allAssemblyBounds = { top: 0, left: 0, bottom: 100, right: 200 };
      expect(comp.isAtBottom(0, true)).toBe(false);
   });

   it("should return false when allAssemblyBounds is null", () => {
      const { comp } = makeComponent();
      comp.allAssemblyBounds = null;
      const obj = makeVSObject();
      comp.vsInfo = makeVsInfo([obj]);
      expect(comp.isAtBottom(0, false)).toBe(false);
   });

   it("should return true when top+height equals allAssemblyBounds.bottom (scaleToScreen=true)", () => {
      const { comp } = makeComponent();
      comp.scaleToScreen = true;
      const obj = makeVSObject({ objectFormat: makeObjectFormat({ top: 90, height: 10 }) });
      comp.vsInfo = makeVsInfo([obj]);
      comp.allAssemblyBounds = { top: 0, left: 0, bottom: 100, right: 200 };
      expect(comp.isAtBottom(0, false)).toBe(true);
   });

   it("should return false when top+height does not reach bottom", () => {
      const { comp } = makeComponent();
      comp.scaleToScreen = true;
      const obj = makeVSObject({ objectFormat: makeObjectFormat({ top: 50, height: 10 }) });
      comp.vsInfo = makeVsInfo([obj]);
      comp.allAssemblyBounds = { top: 0, left: 0, bottom: 100, right: 200 };
      expect(comp.isAtBottom(0, false)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — zIndex
// ---------------------------------------------------------------------------

describe("Group 4 — zIndex: base + container traversal + annotation boost", () => {
   it("should return objectFormat.zIndex for a standalone object with no container", () => {
      const { comp } = makeComponent();
      const obj = makeVSObject({ objectFormat: makeObjectFormat({ zIndex: 5 }) });
      comp.vsInfo = makeVsInfo([obj]);
      expect(comp.zIndex(obj)).toBe(5);
   });

   it("should accumulate parent container zIndex", () => {
      const { comp } = makeComponent();
      const parent = makeVSObject({ absoluteName: "Container1", objectFormat: makeObjectFormat({ zIndex: 3 }) });
      const child = makeVSObject({
         absoluteName: "Chart1",
         container: "Container1",
         objectFormat: makeObjectFormat({ zIndex: 2 }),
      });
      comp.vsInfo = makeVsInfo([parent, child]);
      expect(comp.zIndex(child)).toBe(5); // 2 + 3
   });

   it("should add 5000 to zIndex when object has assemblyAnnotationModels", () => {
      const { comp } = makeComponent();
      const obj = makeVSObject({ objectFormat: makeObjectFormat({ zIndex: 1 }) });
      obj.assemblyAnnotationModels = [{ absoluteName: "ann1" } as any];
      comp.vsInfo = makeVsInfo([obj]);
      expect(comp.zIndex(obj)).toBe(5001);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — isInScrollViewport (via isObjectRendered)
// ---------------------------------------------------------------------------

describe("Group 5 — isObjectRendered: virtualScrolling flag and renderedObjects map", () => {
   it("should return true when virtualScrolling=false (all objects rendered)", () => {
      const { comp } = makeComponent();
      comp.virtualScrolling = false;
      const obj = makeVSObject({ absoluteName: "T1" });
      expect(comp.isObjectRendered(obj)).toBe(true);
   });

   it("should return false when virtualScrolling=true and object not yet in renderedObjects", () => {
      const { comp } = makeComponent();
      comp.virtualScrolling = true;
      const obj = makeVSObject({ absoluteName: "T1" });
      expect(comp.isObjectRendered(obj)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — getActualWidth
// ---------------------------------------------------------------------------

describe("Group 6 — getActualWidth: dataTip realWidth vs objectFormat.width", () => {
   it("should return objectFormat.width when object is not a dataTip", () => {
      const dataTipSvc = {
         showHideDataTip: { subscribe: vi.fn() } as any,
         isDataTipVisible: vi.fn().mockReturnValue(false),
         isDataTip: vi.fn().mockReturnValue(false),
         dataTipName: null,
         isDataTipSource: vi.fn(),
         isCurrentDataTip: vi.fn(),
         hasDataTipShowing: vi.fn(),
         getVSObjectId: vi.fn((n: string) => n),
      };
      const { comp } = makeComponent({ dataTipSvc: dataTipSvc as any });
      const obj = makeVSObject({ objectFormat: makeObjectFormat({ width: 300 }) });
      (obj as any).realWidth = 500;
      expect(comp.getActualWidth(obj)).toBe(300);
   });

   it("should return realWidth when object is a dataTip and realWidth is set", () => {
      const dataTipSvc = {
         showHideDataTip: { subscribe: vi.fn() } as any,
         isDataTipVisible: vi.fn().mockReturnValue(false),
         isDataTip: vi.fn().mockReturnValue(true),
         dataTipName: "Tip1",
         isDataTipSource: vi.fn(),
         isCurrentDataTip: vi.fn(),
         hasDataTipShowing: vi.fn(),
         getVSObjectId: vi.fn((n: string) => n),
      };
      const { comp } = makeComponent({ dataTipSvc: dataTipSvc as any });
      const obj = makeVSObject({ absoluteName: "Tip1", objectFormat: makeObjectFormat({ width: 200 }) });
      (obj as any).realWidth = 400;
      expect(comp.getActualWidth(obj)).toBe(400);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — isChartAnnotationSelected
// ---------------------------------------------------------------------------

describe("Group 7 — isChartAnnotationSelected: annotation name in selectedAnnotations", () => {
   it("should return true when annotation absoluteName is in selectedAnnotations", () => {
      const { comp } = makeComponent();
      const obj = makeVSObject({ selectedAnnotations: ["ann1", "ann2"] });
      const ann = { absoluteName: "ann1" } as any;
      expect(comp.isChartAnnotationSelected(ann, obj)).toBe(true);
   });

   it("should return false when annotation is not selected", () => {
      const { comp } = makeComponent();
      const obj = makeVSObject({ selectedAnnotations: ["ann2"] });
      const ann = { absoluteName: "ann1" } as any;
      expect(comp.isChartAnnotationSelected(ann, obj)).toBe(false);
   });

   it("should return false when ann is null", () => {
      const { comp } = makeComponent();
      const obj = makeVSObject({ selectedAnnotations: ["ann1"] });
      expect(comp.isChartAnnotationSelected(null, obj)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 8 — needsZIndexBoost
// ---------------------------------------------------------------------------

describe("Group 8 — needsZIndexBoost: boost for active data tip / pop component", () => {
   it("should return false by default (no active data tip)", () => {
      const { comp } = makeComponent();
      const obj = makeVSObject({ objectType: "VSChart" });
      expect(comp.needsZIndexBoost(obj)).toBe(false);
   });

   it("should return true when dataTipService.isCurrentDataTip returns true for this object", () => {
      const dataTipSvc = {
         showHideDataTip: { subscribe: vi.fn() } as any,
         isDataTipVisible: vi.fn().mockReturnValue(false),
         isDataTip: vi.fn().mockReturnValue(false),
         dataTipName: "ActiveTip",
         isDataTipSource: vi.fn(),
         isCurrentDataTip: vi.fn().mockReturnValue(true),
         hasDataTipShowing: vi.fn(),
         getVSObjectId: vi.fn((n: string) => n),
      };
      const { comp } = makeComponent({ dataTipSvc: dataTipSvc as any });
      const obj = makeVSObject({ absoluteName: "Chart1" });
      expect(comp.needsZIndexBoost(obj)).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — toolbarForceHidden
// ---------------------------------------------------------------------------

describe("Group 9 — toolbarForceHidden: delegates to miniToolbarService.isMiniToolbarHidden", () => {
   it("should return false when mini toolbar is not hidden", () => {
      const { comp } = makeComponent();
      const obj = makeVSObject({ absoluteName: "Chart1" });
      expect(comp.toolbarForceHidden(obj)).toBe(false);
   });

   it("should return true when miniToolbarService reports hidden for the object", () => {
      const miniToolbarSvc = {
         addContainerEvents: vi.fn().mockReturnValue({ add: vi.fn() }),
         isMiniToolbarHidden: vi.fn().mockReturnValue(true),
         getActionsWidth: vi.fn().mockReturnValue(200),
         getToolbarLeft: vi.fn().mockReturnValue(0),
         getToolbarWidth: vi.fn().mockReturnValue(100),
         handleMouseEnter: vi.fn(),
      };
      const { comp } = makeComponent({ miniToolbarSvc: miniToolbarSvc as any });
      const obj = makeVSObject({ absoluteName: "Chart1" });
      expect(comp.toolbarForceHidden(obj)).toBe(true);
   });
});
