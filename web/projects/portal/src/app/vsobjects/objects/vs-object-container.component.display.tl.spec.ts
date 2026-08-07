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

   it("should return falsy when ann is null", () => {
      const { comp } = makeComponent();
      const obj = makeVSObject({ selectedAnnotations: ["ann1"] });
      // ann && ... short-circuits to null (not false) when ann is null — use toBeFalsy()
      expect(comp.isChartAnnotationSelected(null, obj)).toBeFalsy();
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

// ---------------------------------------------------------------------------
// Group 11 — anchored toolbar geometry (chart pilot), including max mode
// ---------------------------------------------------------------------------

// Final-review round, Important 4: the anchored branches of getToolbarTop/getToolbarLeft bypass
// miniToolbarService.getToolbarLeft(), the only place maxMode participates in strip placement. These
// pin why that is correct rather than an oversight: maxMode's sole effect there is to zero the
// embedded-viewsheet x offset inside a viewport clamp, and the anchored branch has neither term —
// it is expressed purely in the assembly's own objectFormat, which the server rewrites to origin
// (0, 0) plus maxSize for a maximised chart (VSChartModel.VSChartModelFactory.createModel).
describe("Group 11 — anchored toolbar geometry: objectFormat-only, max mode included", () => {
   const anchoredChart = (overrides: any = {}) => {
      const obj: any = makeVSObject({
         objectType: "VSChart",
         objectFormat: makeObjectFormat({ top: 0, left: 0, width: 1000, height: 600 }),
      });
      obj.paddingTop = 6;
      obj.paddingLeft = 4;
      obj.paddingRight = 8;
      return Object.assign(obj, overrides);
   };

   // getToolbarWidth()/getToolbarLeft() both probe the scroll container for a vertical scrollbar.
   const scrollless = { scrollHeight: 600, clientHeight: 600 } as any;

   beforeEach(() => {
      document.body.classList.add("viz-modern");
   });

   afterEach(() => {
      document.body.classList.remove("viz-modern");
   });

   it("anchors a maximised chart's strip inside the assembly, from objectFormat alone", () => {
      const { comp, miniToolbarSvc } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      // Max mode as the server reports it: objectFormat rewritten to (0, 0) + maxSize.
      const obj = anchoredChart({ maxMode: true });
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.isToolbarAnchored(obj)).toBe(true);
      expect(comp.getToolbarTop(obj, 0)).toBe(6);   // top 0 + paddingTop
      // Lane-relative, expressed in the assembly's own insets: left edge at the left inset, box
      // spanning inset to inset. Nothing here comes from a service mock's return value — the
      // earlier `1000 - 8 - 100` form spent a getToolbarWidth mock constant unrelated to the 1000px
      // assembly, so a formula that produced -8 with the real service still read as right-aligned.
      expect(comp.getToolbarLeft(obj, 0)).toBe(0 + 4);                    // left + paddingLeft
      expect(comp.getAnchoredToolbarWidth(obj)).toBe(1000 - 4 - 8);       // lane, inset to inset
      // Right edge = left + width, which must land exactly on the lane's right inset.
      expect(comp.getToolbarLeft(obj, 0) + comp.getAnchoredToolbarWidth(obj))
         .toBe(0 + 1000 - 8);
      // The strip width plays no part in the anchored geometry: the pill right-aligns itself
      // inside the lane-wide box (margin-left: auto), so no estimate is consulted.
      expect(miniToolbarSvc.getToolbarWidth).not.toHaveBeenCalled();
      // The clamp path — and with it the only consumer of maxMode — is not on the anchored route.
      expect(miniToolbarSvc.getToolbarLeft).not.toHaveBeenCalled();
   });

   it("keeps the anchored box inset-to-inset for an assembly away from the origin", () => {
      const { comp } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj = anchoredChart({
         objectFormat: makeObjectFormat({ top: 40, left: 250, width: 600, height: 300 }),
      });
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.getToolbarLeft(obj, 0)).toBe(250 + 4);
      expect(comp.getAnchoredToolbarWidth(obj)).toBe(600 - 4 - 8);
      expect(comp.getToolbarLeft(obj, 0) + comp.getAnchoredToolbarWidth(obj)).toBe(250 + 600 - 8);
   });

   it("treats missing paddings as zero", () => {
      const { comp } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj: any = makeVSObject({
         objectType: "VSChart",
         objectFormat: makeObjectFormat({ top: 0, left: 30, width: 400, height: 200 }),
      });
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.getToolbarLeft(obj, 0)).toBe(30);
      expect(comp.getAnchoredToolbarWidth(obj)).toBe(400);
   });

   it("computes the same anchored geometry out of max mode", () => {
      const { comp } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj = anchoredChart({ maxMode: false });
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.getToolbarTop(obj, 0)).toBe(6);
      expect(comp.getToolbarLeft(obj, 0)).toBe(0 + 4);
      expect(comp.getAnchoredToolbarWidth(obj)).toBe(1000 - 4 - 8);
   });

   it("still hands maxMode to the clamp path for a non-anchored assembly", () => {
      const { comp, miniToolbarSvc } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj: any = anchoredChart({ objectType: "VSTable", maxMode: true });
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.isToolbarAnchored(obj)).toBe(false);
      comp.getToolbarLeft(obj, 0);
      expect(miniToolbarSvc.getToolbarLeft).toHaveBeenCalled();
      const args = (miniToolbarSvc.getToolbarLeft as any).mock.calls[0];
      expect(args[args.length - 1]).toBe(true);
   });
});
