/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { DateTipHelper } from "./data-tip/date-tip-helper";
import { makeComponent, makeDataTipService, makeObjectFormat, makeVSObject, makeVsInfo }
   from "./vs-object-container.component.test-helpers";

// Bug #76461 / #76458: a max-mode (enlarged) assembly and a different assembly's active
// Data Tip popup both received the identical +99999 z-index boost from
// getContainerZIndex(), so whichever assembly's own base zIndex() happened to be numerically
// higher won the stacking comparison -- nothing guaranteed the Data Tip always rendered on
// top of a merely-enlarged sibling.
describe("VSObjectContainer z-index boost tiering", () => {
   it("gives an active Data Tip a strictly higher z-index than a max-mode sibling with a higher base zIndex", () => {
      // Reproduces the reported bug directly: the enlarged chart's own base zIndex (10) is
      // *higher* than the data tip target's base zIndex (5) -- exactly the scenario that used
      // to let the max-mode chart win and hide the popup.
      const dataTipSvc = makeDataTipService({ dataTipName: "Chart2" });
      dataTipSvc.isCurrentDataTip = vi.fn((name: string) => name === "Chart2");
      const { comp } = makeComponent({ dataTipSvc });

      const maxModeChart = makeVSObject({
         absoluteName: "Chart1", objectFormat: { zIndex: 10 } as any,
         ...( { maxMode: true } as any),
      });
      const dataTipTarget = makeVSObject({ absoluteName: "Chart2", objectFormat: { zIndex: 5 } as any });

      expect(comp.needsZIndexBoost(maxModeChart)).toBe(true);
      expect(comp.needsZIndexBoost(dataTipTarget)).toBe(true);

      const maxModeContainerZIndex = comp.getContainerZIndex(maxModeChart);
      const dataTipContainerZIndex = comp.getContainerZIndex(dataTipTarget);

      expect(dataTipContainerZIndex).toBeGreaterThan(maxModeContainerZIndex);
   });

   it("still boosts a max-mode assembly, but only to the single (non-data-tip) tier when no data tip is active", () => {
      const { comp } = makeComponent();
      const maxModeChart = makeVSObject({
         absoluteName: "Chart1", objectFormat: { zIndex: 10 } as any, ...( { maxMode: true } as any),
      });

      expect(comp.getContainerZIndex(maxModeChart)).toBe(10 + DateTipHelper.getPopUpContentBoostZIndex());
   });

   it("gives a plain (non-boosted, non-max-mode, non-data-tip) assembly its own zIndex unchanged", () => {
      const { comp } = makeComponent();
      const plain = makeVSObject({ absoluteName: "Chart1", objectFormat: { zIndex: 42 } as any });

      expect(comp.getContainerZIndex(plain)).toBe(42);
   });

   it("boosts an embedded viewsheet containing the active data tip to the same higher tier as the data tip itself", () => {
      const dataTipSvc = makeDataTipService({ dataTipName: "VS1.Chart2" });
      const { comp } = makeComponent({ dataTipSvc });
      const embeddedVs = makeVSObject({
         absoluteName: "VS1", objectType: "VSViewsheet", objectFormat: { zIndex: 3 } as any,
      });

      expect(comp.needsZIndexBoost(embeddedVs)).toBe(true);
      expect(comp.getContainerZIndex(embeddedVs)).toBe(3 + DateTipHelper.getPopUpContentBoostZIndex() * 2);
   });
});

// Bug: the mini-toolbar of an assembly inside an embedded viewsheet is painted 28px *above*
// that viewsheet's box (MiniToolbar.topY / the forceAbove binding) but is ranked inside the
// embedded viewsheet assembly's own stacking context, so an outer assembly with a higher
// z-index painted over it. Verified in a browser against Examples/Hurricane: the toolbar stayed
// occluded even at z-index 999999, because no descendant z-index can escape its context --
// which is why the #75916 MINI_TOOLBAR_MIN_ZINDEX floor cannot address this case and the
// embedded viewsheet's own container has to be lifted instead.
describe("VSObjectContainer embedded-viewsheet hover boost", () => {
   const embeddedVs = () => makeVSObject({
      absoluteName: "Map1", objectType: "VSViewsheet", objectFormat: { zIndex: 6 } as any,
   });

   it("leaves an un-hovered embedded viewsheet at its authored z-index", () => {
      const { comp } = makeComponent();
      expect(comp.getContainerZIndex(embeddedVs())).toBe(6);
   });

   it("lifts a hovered embedded viewsheet above an outer sibling that outranks it", () => {
      const { comp } = makeComponent();
      const vs = embeddedVs();
      // Examples/Hurricane's real z-order: Map1=6 but Image1/Image2=1006/1007 and, in the
      // reported case, the banner text outranked the embedded viewsheet too.
      const outerSibling = makeVSObject({ absoluteName: "Text2", objectFormat: { zIndex: 1005 } as any });

      comp.onMouseEnter(vs, null);

      expect(comp.isHoveredEmbeddedViewsheet(vs)).toBe(true);
      expect(comp.getContainerZIndex(vs)).toBeGreaterThan(comp.getContainerZIndex(outerSibling));
      // the toolbar of an assembly inside it rides along on the lifted context
      expect(comp.getMiniToolbarZIndex(vs)).toBeGreaterThan(comp.getContainerZIndex(outerSibling));
   });

   it("restores the authored z-index on mouseleave so outer assemblies layer normally again", () => {
      const { comp } = makeComponent();
      const vs = embeddedVs();

      comp.onMouseEnter(vs, null);
      comp.onMouseLeave(vs);

      expect(comp.isHoveredEmbeddedViewsheet(vs)).toBe(false);
      expect(comp.getContainerZIndex(vs)).toBe(6);
   });

   it("does not boost a non-embedded-viewsheet assembly on hover", () => {
      const { comp } = makeComponent();
      const chart = makeVSObject({ absoluteName: "Chart1", objectFormat: { zIndex: 6 } as any });

      comp.onMouseEnter(chart, null);

      expect(comp.isHoveredEmbeddedViewsheet(chart)).toBe(false);
      expect(comp.getContainerZIndex(chart)).toBe(6);
   });

   it("only boosts the embedded viewsheet actually hovered", () => {
      const { comp } = makeComponent();
      const hovered = embeddedVs();
      const other = makeVSObject({
         absoluteName: "Viewsheet1", objectType: "VSViewsheet", objectFormat: { zIndex: 1008 } as any,
      });

      comp.onMouseEnter(hovered, null);

      expect(comp.getContainerZIndex(other)).toBe(1008);
      expect(comp.onMouseLeave(other as any)).toBeUndefined();
      // a mouseleave from a *different* embedded viewsheet must not clear the hovered one
      expect(comp.isHoveredEmbeddedViewsheet(hovered)).toBe(true);
   });

   it("keeps the hover boost below the pop-component/data-tip tiers", () => {
      const { comp } = makeComponent();
      const vs = embeddedVs();

      comp.onMouseEnter(vs, null);

      expect(comp.getContainerZIndex(vs)).toBeLessThan(6 + DateTipHelper.getPopUpContentBoostZIndex());
   });
});

// The lane rule lives here, at the one mount site that puts the strip inside the title lane. The
// five that float it (composer, binding pane, both wizard panes, embedded chart) render the same
// action list and must not lose their toolbar to a lane they never use, so asking this in the
// shared action layer is what emptied the composer's floating toolbar at dense.
describe("VSObjectContainer anchored-lane chrome suppression", () => {
   const anchored = (laneHeight: number, vizModern = true, objectType = "VSChart") =>
      makeVSObject({
         objectType,
         ...({ vizModern, titleVisible: true, titleFormat: { height: laneHeight } } as any),
      });

   it("suppresses the strip when the lane cannot hold it", () => {
      const { comp } = makeComponent();

      expect(comp.isChromeSuppressed(anchored(20))).toBe(false);
      expect(comp.isCornerKebab(anchored(20))).toBe(true);
      expect(comp.isChromeSuppressed(anchored(23))).toBe(false);
      expect(comp.isCornerKebab(anchored(23))).toBe(true);
   });

   it("draws the strip once the lane holds it, and anchors it", () => {
      const { comp } = makeComponent();

      expect(comp.isChromeSuppressed(anchored(24))).toBe(false);
      expect(comp.isChromeSuppressed(anchored(30))).toBe(false);
      expect(comp.isKebabResident(anchored(30))).toBe(true);
   });

   it("moves a hidden title, which resolves to a zero lane, to the corner", () => {
      const { comp } = makeComponent();
      const titleHidden = makeVSObject({
         ...({ vizModern: true, titleVisible: false, titleFormat: { height: 30 } } as any),
      });

      expect(comp.isChromeSuppressed(titleHidden)).toBe(false);
      expect(comp.isCornerKebab(titleHidden)).toBe(true);
   });

   it("still suppresses a card under the action floor", () => {
      const { comp } = makeComponent();
      const short = makeVSObject({
         objectFormat: makeObjectFormat({ height: 31 }),
         ...({ vizModern: true, titleVisible: false, titleFormat: { height: 30 } } as any),
      });

      expect(comp.isChromeSuppressed(short)).toBe(true);
      expect(comp.isCornerKebab(short)).toBe(false);
   });

   it("leaves a short lane alone when the gate is off", () => {
      const { comp } = makeComponent();

      expect(comp.isChromeSuppressed(anchored(20, false))).toBe(false);
   });

   it("leaves a type outside the anchored set alone at a short lane", () => {
      const { comp } = makeComponent();

      expect(comp.isChromeSuppressed(anchored(20, true, "VSRangeSlider"))).toBe(false);
      expect(comp.isChromeSuppressed(anchored(0, true, "VSText"))).toBe(false);
   });
});

// A shrunk table in a bottom-tabs tab publishes how far its card draws below objectFormat.top;
// a marked table's strip and focus outline follow the card, an unmarked table's stay put.
describe("VSObjectContainer chrome on a shifted table card", () => {
   const scrollless = { scrollHeight: 600, clientHeight: 600 } as any;

   const shiftedTable = (overrides: any = {}): any => Object.assign(makeVSObject({
      absoluteName: "Table1",
      objectType: "VSTable",
      objectFormat: makeObjectFormat({ top: 40, left: 250, width: 400, height: 300 }),
      container: "Tab1",
      containerType: "VSTab",
   }), {
      vizModern: true,
      shrink: true,
      titleVisible: true,
      titleFormat: { height: 30 },
      padding: { top: 12, left: 10, bottom: 12, right: 6 },
      objectHeight: 180,
      cardTopShift: 120,
   }, overrides);

   const viewerComponent = (obj: any) => {
      const { comp } = makeComponent({
         context: { viewer: true, preview: false, binding: false } as any,
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      comp.vsInfo = makeVsInfo([obj]);
      return comp;
   };

   it("anchors a marked table's strip in the title lane of the shifted card", () => {
      const obj = shiftedTable();
      const comp = viewerComponent(obj);

      expect(comp.isToolbarAnchored(obj)).toBe(true);
      // card top, then the lane's inset top and the centring (30 - 24) / 2
      expect(comp.getToolbarTop(obj, 0)).toBe(40 + 120 + 12 + 3);
   });

   it("keeps the strip on objectFormat.top when nothing or zero is published", () => {
      for(const cardTopShift of [undefined, 0]) {
         const obj = shiftedTable({ cardTopShift });
         const comp = viewerComponent(obj);

         expect(comp.getToolbarTop(obj, 0)).toBe(40 + 12 + 3);
         expect(comp.getVsObjectPosition(obj, 0, true)).toBe(40);
      }
   });

   it("leaves the strip's horizontal geometry alone", () => {
      const obj = shiftedTable({ realWidth: 320 });
      const comp = viewerComponent(obj);

      expect(comp.getToolbarLeft(obj, 0)).toBe(250 + 10);
      expect(comp.getAnchoredToolbarWidth(obj)).toBe(320 - 10 - 6);
   });

   it("puts a marked table's focus outline on the shifted card", () => {
      const obj = shiftedTable();
      const comp = viewerComponent(obj);

      expect(comp.getVsObjectPosition(obj, 0, true)).toBe(40 + 120);
      expect(comp.getVsObjectPosition(obj, 0, false)).toBe(250);
      expect(comp.getVsObjectHeight(obj)).toBe(300 - 120);
   });

   it("ends the outline on the card bottom for a crosstab with a hidden title", () => {
      // objectHeight is published before the crosstab takes its hidden title off the card
      const obj = shiftedTable({
         absoluteName: "Crosstab1", objectType: "VSCrosstab", titleVisible: false,
         objectHeight: 194, cardTopShift: 126,
      });
      const comp = viewerComponent(obj);

      expect(comp.getVsObjectPosition(obj, 0, true)).toBe(40 + 126);
      expect(comp.getVsObjectPosition(obj, 0, true) + comp.getVsObjectHeight(obj)).toBe(40 + 300);
   });

   it("still moves the outline at dense, where the lane holds only the corner kebab", () => {
      const obj = shiftedTable({ titleFormat: { height: 20 } });
      const comp = viewerComponent(obj);

      expect(comp.isChromeSuppressed(obj)).toBe(false);
      expect(comp.isCornerKebab(obj)).toBe(true);
      expect(comp.getVsObjectPosition(obj, 0, true)).toBe(40 + 120);
   });

   it("leaves an unmarked table's floating strip and outline where they were", () => {
      const obj = shiftedTable({ vizModern: false, padding: undefined, cardTopShift: 200 });
      const comp = viewerComponent(obj);

      expect(comp.isToolbarAnchored(obj)).toBe(false);
      expect(comp.getToolbarTop(obj, 0)).toBe(40);
      expect(comp.getVsObjectPosition(obj, 0, true)).toBe(40);
      expect(comp.getVsObjectHeight(obj)).toBe(180);
   });

   it("ignores a stale shift in max mode", () => {
      const obj = shiftedTable({ maxMode: true });
      const comp = viewerComponent(obj);

      expect(comp.getToolbarTop(obj, 0)).toBe(40 + 12 + 3);
      expect(comp.getVsObjectPosition(obj, 0, true)).toBeNull();
   });

   it("keeps the outline height rule for assemblies without a shift", () => {
      const comp = viewerComponent(shiftedTable());
      const list = makeVSObject({ objectType: "VSSelectionList", ...({ objectHeight: 30 } as any) });
      const chart = makeVSObject({ objectType: "VSChart" });

      expect(comp.getVsObjectHeight(list)).toBe(30);
      expect(comp.getVsObjectHeight(chart)).toBe(100);
      expect(comp.getVsObjectHeight(shiftedTable({ cardTopShift: undefined }))).toBe(180);
   });
});

describe("VSObjectContainer corner kebab", () => {
   const scrollless = { scrollHeight: 600, clientHeight: 600 } as any;

   const cornerObject = (overrides: any = {}): any => Object.assign(makeVSObject({
      absoluteName: "Chart1",
      objectType: "VSChart",
      objectFormat: makeObjectFormat({ top: 40, left: 250, width: 400, height: 300 }),
   }), {
      vizModern: true,
      titleVisible: false,
      titleFormat: { height: 30 },
      padding: { top: 12, left: 10, bottom: 12, right: 6 },
   }, overrides);

   const viewerComponent = (obj: any) => {
      const { comp } = makeComponent({
         context: { viewer: true, preview: false, binding: false } as any,
         vsObjectActions: [{ showingActions: [], toolbarActions: [] } as any],
      });
      comp.containerRef = scrollless;
      comp.vsInfo = makeVsInfo([obj]);
      return comp;
   };

   it("places an untitled chart's kebab in the corner", () => {
      const obj = cornerObject();
      const comp = viewerComponent(obj);

      expect(comp.isChromeSuppressed(obj)).toBe(false);
      expect(comp.isCornerKebab(obj)).toBe(true);
      expect(comp.getToolbarTop(obj, 0)).toBe(40 + 12 + 4);
      expect(comp.getToolbarLeft(obj, 0)).toBe(250 + 10);
      expect(comp.getCornerToolbarWidth(obj)).toBe(400 - 10 - 6 - 4);
   });

   it("uses the corner for a visible lane under 24px", () => {
      const obj = cornerObject({ titleVisible: true, titleFormat: { height: 20 } });
      const comp = viewerComponent(obj);

      expect(comp.isCornerKebab(obj)).toBe(true);
      expect(comp.isChromeSuppressed(obj)).toBe(false);
   });

   it("draws nothing for an untitled chart under 32px", () => {
      const obj = cornerObject({ objectFormat: makeObjectFormat({ height: 30 }) });
      const comp = viewerComponent(obj);

      expect(comp.isChromeSuppressed(obj)).toBe(true);
      expect(comp.isCornerKebab(obj)).toBe(false);
   });

   it("drops a shifted card's corner by its card shift", () => {
      const obj = cornerObject({ absoluteName: "Table1", objectType: "VSTable", cardTopShift: 120 });
      const comp = viewerComponent(obj);

      expect(comp.getToolbarTop(obj, 0)).toBe(40 + 120 + 12 + 4);
   });

   it("leaves an untitled max-mode selection list on the floating strip", () => {
      const obj = cornerObject({
         absoluteName: "SelectionList1", objectType: "VSSelectionList", maxMode: true,
      });
      const comp = viewerComponent(obj);

      expect(comp.isCornerKebab(obj)).toBe(false);
      expect(comp.isChromeSuppressed(obj)).toBe(false);
      expect(comp.isToolbarAnchored(obj)).toBe(false);
   });

   it("suppresses the corner on mobile", () => {
      const obj = cornerObject();
      const comp = viewerComponent(obj);
      comp.mobile = true;

      expect(comp.isChromeSuppressed(obj)).toBe(true);
      expect(comp.isCornerKebab(obj)).toBe(false);
   });

   it("leaves gate-off assemblies alone", () => {
      const obj = cornerObject({ vizModern: false });
      const comp = viewerComponent(obj);

      expect(comp.isChromeSuppressed(obj)).toBe(false);
      expect(comp.isCornerKebab(obj)).toBe(false);
   });

   it("marks only corner-placed assemblies for the reserve", () => {
      const untitled = cornerObject();
      const titled = cornerObject({ titleVisible: true, titleFormat: { height: 30 } });
      const dropdown = cornerObject({
         absoluteName: "SelectionList1", objectType: "VSSelectionList", dropdown: true,
         titleVisible: false,
      });

      expect(viewerComponent(untitled).isCornerKebab(untitled)).toBe(true);
      expect(viewerComponent(titled).isCornerKebab(titled)).toBe(false);
      expect(viewerComponent(dropdown).isCornerKebab(dropdown)).toBe(false);
   });
});
