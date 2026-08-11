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
 *   Group 11  isActivePopComponent — active pop component, self and grouped-child cases
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
// Group 11 — isActivePopComponent
// ---------------------------------------------------------------------------

describe("Group 11 — isActivePopComponent: active pop component, self and grouped-child cases", () => {
   it("should return false when no pop component is active", () => {
      const { comp } = makeComponent();
      const obj = makeVSObject({ absoluteName: "GroupContainer1" });
      expect(comp.isActivePopComponent(obj)).toBe(false);
   });

   it("should return true when the object itself is the active pop component", () => {
      const popSvc = {
         componentPop: { subscribe: vi.fn() } as any,
         getPopComponent: vi.fn().mockReturnValue("GroupContainer1"),
         isPopComponent: vi.fn((name: string) => name === "GroupContainer1"),
         isPopSource: vi.fn().mockReturnValue(false),
         hasPopUpComponentShowing: vi.fn().mockReturnValue(true),
      };
      const { comp } = makeComponent({ popSvc: popSvc as any });
      const obj = makeVSObject({ absoluteName: "GroupContainer1" });
      expect(comp.isActivePopComponent(obj)).toBe(true);
   });

   // Bug: "Group as popComponent, display blank" -- a group container's grouped
   // children must also get the z-index boost when the container is the active
   // pop component, otherwise they render underneath the container's own boosted,
   // opaque background and the pop-up appears empty.
   it("should return true for a grouped child when its container is the active pop component", () => {
      const popSvc = {
         componentPop: { subscribe: vi.fn() } as any,
         getPopComponent: vi.fn().mockReturnValue("GroupContainer1"),
         isPopComponent: vi.fn((name: string) => name === "GroupContainer1"),
         isPopSource: vi.fn().mockReturnValue(false),
         hasPopUpComponentShowing: vi.fn().mockReturnValue(true),
      };
      const { comp } = makeComponent({ popSvc: popSvc as any });
      const child = makeVSObject({ absoluteName: "Text2", container: "GroupContainer1" });
      expect(comp.isActivePopComponent(child)).toBe(true);
   });

   it("should return false for a grouped child when its container is not the active pop component", () => {
      const popSvc = {
         componentPop: { subscribe: vi.fn() } as any,
         getPopComponent: vi.fn().mockReturnValue("OtherContainer"),
         isPopComponent: vi.fn((name: string) => name === "OtherContainer"),
         isPopSource: vi.fn().mockReturnValue(false),
         hasPopUpComponentShowing: vi.fn().mockReturnValue(true),
      };
      const { comp } = makeComponent({ popSvc: popSvc as any });
      const child = makeVSObject({ absoluteName: "Text2", container: "GroupContainer1" });
      expect(comp.isActivePopComponent(child)).toBe(false);
   });

   it("should return false for an unrelated top-level object while a different pop component is active", () => {
      const popSvc = {
         componentPop: { subscribe: vi.fn() } as any,
         getPopComponent: vi.fn().mockReturnValue("GroupContainer1"),
         isPopComponent: vi.fn((name: string) => name === "GroupContainer1"),
         isPopSource: vi.fn().mockReturnValue(false),
         hasPopUpComponentShowing: vi.fn().mockReturnValue(true),
      };
      const { comp } = makeComponent({ popSvc: popSvc as any });
      const obj = makeVSObject({ absoluteName: "Table1", container: null });
      expect(comp.isActivePopComponent(obj)).toBe(false);
   });

   // A GroupContainer can contain a Tab (GroupContainer -> Tab -> children), so a
   // grandchild's immediate .container points at the Tab, not the outer GroupContainer
   // that is actually registered as the pop component. isActivePopComponent must walk
   // the full ancestor chain (like zIndex() does) to still boost this grandchild.
   it("should return true for a grandchild nested through an intermediate Tab when the outer group container is the active pop component", () => {
      const popSvc = {
         componentPop: { subscribe: vi.fn() } as any,
         getPopComponent: vi.fn().mockReturnValue("GroupContainer1"),
         isPopComponent: vi.fn((name: string) => name === "GroupContainer1"),
         isPopSource: vi.fn().mockReturnValue(false),
         hasPopUpComponentShowing: vi.fn().mockReturnValue(true),
      };
      const { comp } = makeComponent({ popSvc: popSvc as any });
      const tab = makeVSObject({ absoluteName: "Tab1", container: "GroupContainer1" });
      const grandchild = makeVSObject({ absoluteName: "Chart1", container: "Tab1" });
      comp.vsInfo = makeVsInfo([tab, grandchild]);
      expect(comp.isActivePopComponent(grandchild)).toBe(true);
   });

   it("should return false for a grandchild nested through an intermediate Tab when neither the tab nor its outer container is the active pop component", () => {
      const popSvc = {
         componentPop: { subscribe: vi.fn() } as any,
         getPopComponent: vi.fn().mockReturnValue("OtherContainer"),
         isPopComponent: vi.fn((name: string) => name === "OtherContainer"),
         isPopSource: vi.fn().mockReturnValue(false),
         hasPopUpComponentShowing: vi.fn().mockReturnValue(true),
      };
      const { comp } = makeComponent({ popSvc: popSvc as any });
      const tab = makeVSObject({ absoluteName: "Tab1", container: "GroupContainer1" });
      const grandchild = makeVSObject({ absoluteName: "Chart1", container: "Tab1" });
      comp.vsInfo = makeVsInfo([tab, grandchild]);
      expect(comp.isActivePopComponent(grandchild)).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 12 — anchored toolbar geometry (chart pilot + table family); max mode
// anchored for both, excluded only for the selection family
// ---------------------------------------------------------------------------

// The anchored branches of getToolbarTop/getToolbarLeft are expressed purely in the assembly's own
// objectFormat and bypass miniToolbarService.getToolbarLeft() entirely — the only place maxMode
// participates in strip placement. A maximised chart's objectFormat really is rewritten to origin
// (0, 0) plus maxSize (VSChartModel.VSChartModelFactory.createModel), and the table family carries
// no max-mode override either, so the plain anchored math already lands correctly for both. Only
// the selection family (VSSelectionList/VSSelectionTree) abandons objectFormat positioning in max
// mode — see VSSelection.topPosition, which falls through to null — putting VSSelectionBaseModel's
// TOP_PADDING/LEFT_PADDING constants in objectFormat instead, so anchoring is disabled for them and
// the strip falls back to the floating path, which already compensates for max mode via
// miniToolbarService.
describe("Group 12 — anchored toolbar geometry: chart and table anchored in max mode, selection family excluded", () => {
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
      const obj: any = anchoredChart({ objectType: "VSCalendar", maxMode: true });
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.isToolbarAnchored(obj)).toBe(false);
      comp.getToolbarLeft(obj, 0);
      expect(miniToolbarSvc.getToolbarLeft).toHaveBeenCalled();
      const args = (miniToolbarSvc.getToolbarLeft as any).mock.calls[0];
      expect(args[args.length - 1]).toBe(true);
   });

   // The table family declares no paddingTop/Left/Right — those fields are on vs-chart-model only —
   // so the || 0 fallbacks resolve a table to a strip flush inside the content box, spanning the
   // full width. That is the lane a table already has, which is what slice 1's rule asks for.
   it("anchors a table flush and full width, since no table model carries paddings", () => {
      const { comp } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj: any = makeVSObject({
         objectType: "VSTable",
         objectFormat: makeObjectFormat({ top: 40, left: 250, width: 600, height: 300 }),
      });
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.isToolbarAnchored(obj)).toBe(true);
      expect(comp.getToolbarTop(obj, 0)).toBe(40);
      expect(comp.getToolbarLeft(obj, 0)).toBe(250);
      expect(comp.getAnchoredToolbarWidth(obj)).toBe(600);
      expect(comp.getToolbarLeft(obj, 0) + comp.getAnchoredToolbarWidth(obj)).toBe(250 + 600);
   });

   // A maximised table carries no padding-constant override the way selection does — its
   // objectFormat is real coordinates in max mode too, so anchoring must stay on.
   it("anchors a maximised table's strip inside the assembly, from objectFormat alone", () => {
      const { comp } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj: any = makeVSObject({
         objectType: "VSTable",
         objectFormat: makeObjectFormat({ top: 40, left: 250, width: 600, height: 300 }),
      });
      obj.maxMode = true;
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.isToolbarAnchored(obj)).toBe(true);
      expect(comp.getToolbarTop(obj, 0)).toBe(40);
      expect(comp.getToolbarLeft(obj, 0)).toBe(250);
      expect(comp.getAnchoredToolbarWidth(obj)).toBe(600);
      expect(comp.getToolbarLeft(obj, 0) + comp.getAnchoredToolbarWidth(obj)).toBe(250 + 600);
   });

   // With the title hidden the strip overlays the column header row, whose last cell carries the
   // sort control at right: 2px, width: 20px (base-table.scss). The kebab landed exactly on it and
   // ate the click, so the column could not be sorted at all. The lane box gives that footprint up
   // so the pill right-aligns clear of it.
   it("reserves the sort control's footprint when the title is hidden", () => {
      const { comp } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj: any = makeVSObject({
         objectType: "VSTable",
         objectFormat: makeObjectFormat({ top: 40, left: 250, width: 600, height: 300 }),
      });
      obj.titleVisible = false;
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.getAnchoredToolbarWidth(obj)).toBe(600 - 22);
      expect(comp.getToolbarLeft(obj, 0) + comp.getAnchoredToolbarWidth(obj)).toBe(250 + 600 - 22);
   });

   it("reserves nothing when a title lane exists", () => {
      const { comp } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj: any = makeVSObject({
         objectType: "VSTable",
         objectFormat: makeObjectFormat({ top: 40, left: 250, width: 600, height: 300 }),
      });
      obj.titleVisible = true;
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.getAnchoredToolbarWidth(obj)).toBe(600);
   });

   // The reserve's whole purpose is the table's .vs-header-cell-button-sort, which a selection cell
   // has no equivalent of. A selection's right-edge occupant is the pending-Apply icon, and the
   // gated .pending-alert offset moves that clear of the pill instead — so the pill is flush here
   // in both title states, and the CSS offset stays one value rather than two.
   it("reserves nothing for a title-hidden selection list", () => {
      const { comp } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj: any = makeVSObject({
         objectType: "VSSelectionList",
         objectFormat: makeObjectFormat({ top: 40, left: 250, width: 600, height: 300 }),
      });
      obj.titleVisible = false;
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.getAnchoredToolbarWidth(obj)).toBe(600);
   });

   it("reserves nothing for a title-hidden selection tree either", () => {
      const { comp } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj: any = makeVSObject({
         objectType: "VSSelectionTree",
         objectFormat: makeObjectFormat({ top: 40, left: 250, width: 600, height: 300 }),
      });
      obj.titleVisible = false;
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.getAnchoredToolbarWidth(obj)).toBe(600);
   });

   // Selection carries no paddingTop/Left/Right either (those fields are on vs-chart-model only), so
   // the same || 0 fallbacks that give a table a flush, full-width lane give a selection list one
   // too: flush top/left, full width, right edge landing exactly on the assembly's own right edge.
   it("anchors a non-max-mode selection list", () => {
      const { comp } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj: any = makeVSObject({
         objectType: "VSSelectionList",
         objectFormat: makeObjectFormat({ top: 40, left: 250, width: 600, height: 300 }),
      });
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.isToolbarAnchored(obj)).toBe(true);
      expect(comp.getToolbarTop(obj, 0)).toBe(40);
      expect(comp.getToolbarLeft(obj, 0)).toBe(250);
      expect(comp.getAnchoredToolbarWidth(obj)).toBe(600);
      expect(comp.getToolbarLeft(obj, 0) + comp.getAnchoredToolbarWidth(obj)).toBe(250 + 600);
   });

   it("does not anchor a maximised selection list, which abandons objectFormat positioning", () => {
      const { comp } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj: any = makeVSObject({
         objectType: "VSSelectionList",
         objectFormat: makeObjectFormat({ top: 40, left: 250, width: 600, height: 300 }),
      });
      obj.maxMode = true;
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.isToolbarAnchored(obj)).toBe(false);
   });

   // Touch has no hover to reveal a non-resident strip, so isKebabResident must stay true here even
   // though isToolbarAnchored (its maxMode-excluding sibling) is false — it is the input the mini-
   // toolbar host uses on touch to keep the kebab reachable once anchoring, and the position it
   // implies, is off.
   it("still carries the resident-kebab design for a maximised selection list, unlike anchoring", () => {
      const { comp } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj: any = makeVSObject({
         objectType: "VSSelectionList",
         objectFormat: makeObjectFormat({ top: 40, left: 250, width: 600, height: 300 }),
      });
      obj.maxMode = true;
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.isToolbarAnchored(obj)).toBe(false);
      expect(comp.isKebabResident(obj)).toBe(true);
   });

   // objectFormat.top/left here are VSSelectionBaseModel's TOP_PADDING/LEFT_PADDING constants
   // (30/20 in the real server model; 40/250 in this fixture stand in for "some non-zero value
   // that isn't the assembly's true origin"), not real coordinates — the assembly's own rendering
   // ignores them and fills the container. getToolbarTop's plain floating math already lands
   // correctly off that stale top (see its own comment for why); getToolbarLeft still aims past
   // the assembly's own right edge (left + width) and leans on the existing overflow clamp to land
   // there instead of on the header's leading edge.
   it("floats a maximised selection list's strip from ordinary floating top, and past the right edge on left", () => {
      const { comp, miniToolbarSvc } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj: any = makeVSObject({
         objectType: "VSSelectionList",
         objectFormat: makeObjectFormat({ top: 40, left: 250, width: 600, height: 300 }),
      });
      obj.maxMode = true;
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.getToolbarTop(obj, 0)).toBe(40);   // ordinary floating top, no max-mode override
      comp.getToolbarLeft(obj, 0);
      expect(miniToolbarSvc.getToolbarLeft).toHaveBeenCalled();
      const args = (miniToolbarSvc.getToolbarLeft as any).mock.calls[0];
      expect(args[0]).toBe(250 + 600);   // left + width, past the visible edge
      expect(args[args.length - 1]).toBe(true);   // maxMode still reaches the clamp
   });

   it("does not anchor a calendar, whose rollout slice has not landed", () => {
      const { comp } = makeComponent({
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      const obj: any = makeVSObject({
         objectType: "VSCalendar",
         objectFormat: makeObjectFormat({ top: 40, left: 250, width: 600, height: 300 }),
      });
      comp.vsInfo = makeVsInfo([obj]);

      expect(comp.isToolbarAnchored(obj)).toBe(false);
   });
});
