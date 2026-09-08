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

      expect(comp.getContainerZIndex(maxModeChart)).toBe(10 + comp.popUpContentBoostZIndex);
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
      expect(comp.getContainerZIndex(embeddedVs)).toBe(3 + comp.popUpContentBoostZIndex * 2);
   });
});
