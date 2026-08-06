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
import { makeComponent, makeVSObject, makeDataTipService } from "./vs-object-container.component.test-helpers";

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
