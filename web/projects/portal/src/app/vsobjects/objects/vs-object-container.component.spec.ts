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
import { Subject } from "rxjs";
import { VSObjectContainer } from "./vs-object-container.component";
import { VSObjectModel } from "../model/vs-object-model";

// Bug #76461 / #76458: a max-mode (enlarged) assembly and a different assembly's active
// Data Tip popup both received the identical +99999 z-index boost from
// getContainerZIndex(), so whichever assembly's own base zIndex() happened to be numerically
// higher won the stacking comparison -- nothing guaranteed the Data Tip always rendered on
// top of a merely-enlarged sibling. These tests construct VSObjectContainer directly (no
// TestBed/template rendering needed -- needsZIndexBoost/getContainerZIndex/zIndex are plain
// methods) with minimal service mocks, mirroring vs-data-tip.directive.spec.ts's pattern.
describe("VSObjectContainer z-index boost tiering", () => {
   let container: VSObjectContainer;
   let dataTipService: any;
   let popService: any;

   function mockVsObject(name: string, zIndex: number, extra: any = {}): VSObjectModel {
      return <any> {
         absoluteName: name,
         container: null,
         objectType: "VSChart",
         objectFormat: {zIndex},
         ...extra
      };
   }

   beforeEach(() => {
      dataTipService = {
         dataTipName: null,
         isCurrentDataTip: vi.fn(() => false),
         isDataTipSource: vi.fn(() => false),
         showHideDataTip: new Subject<void>()
      };
      popService = {
         getPopComponent: vi.fn(() => null),
         hasPopUpComponentShowing: vi.fn(() => false),
         isPopComponent: vi.fn(() => false),
         isPopSource: vi.fn(() => false),
         componentPop: new Subject<string>()
      };

      const scaleService: any = {getScale: () => new Subject<number>()};
      const miniToolbarService: any = {};
      const contextProvider: any = {};
      const adhocFilterService: any = {};
      const changeDetectorRef: any = {};
      const element: any = {nativeElement: {}};
      const viewsheetClient: any = {};

      container = new VSObjectContainer(
         miniToolbarService, dataTipService, contextProvider, adhocFilterService, popService,
         changeDetectorRef, scaleService, element, viewsheetClient);
      container.vsInfo = <any> {vsObjects: []};
   });

   it("gives an active Data Tip a strictly higher z-index than a max-mode sibling with a higher base zIndex", () => {
      // Reproduces the reported bug directly: the enlarged chart's own base zIndex (10) is
      // *lower* than the data tip target's base zIndex (5) would need to be to win under the
      // pre-fix code, yet pre-fix code still let relative base values decide -- flip them so
      // the max-mode assembly's base is higher, which is exactly the scenario that used to
      // hide the popup.
      const maxModeChart = mockVsObject("Chart1", 10, {maxMode: true});
      const dataTipTarget = mockVsObject("Chart2", 5);

      dataTipService.dataTipName = "Chart2";
      dataTipService.isCurrentDataTip.mockImplementation((name: string) => name === "Chart2");

      expect(container.needsZIndexBoost(maxModeChart)).toBe(true);
      expect(container.needsZIndexBoost(dataTipTarget)).toBe(true);

      const maxModeContainerZIndex = container.getContainerZIndex(maxModeChart);
      const dataTipContainerZIndex = container.getContainerZIndex(dataTipTarget);

      expect(dataTipContainerZIndex).toBeGreaterThan(maxModeContainerZIndex);
   });

   it("still boosts a max-mode assembly, but only to the single (non-data-tip) tier when no data tip is active", () => {
      const maxModeChart = mockVsObject("Chart1", 10, {maxMode: true});

      expect(container.getContainerZIndex(maxModeChart))
         .toBe(10 + container.popUpContentBoostZIndex);
   });

   it("gives a plain (non-boosted, non-max-mode, non-data-tip) assembly its own zIndex unchanged", () => {
      const plain = mockVsObject("Chart1", 42);

      expect(container.getContainerZIndex(plain)).toBe(42);
   });

   it("boosts an embedded viewsheet containing the active data tip to the same higher tier as the data tip itself", () => {
      const embeddedVs = mockVsObject("VS1", 3, {objectType: "VSViewsheet"});
      dataTipService.dataTipName = "VS1.Chart2";

      expect(container.needsZIndexBoost(embeddedVs)).toBe(true);
      expect(container.getContainerZIndex(embeddedVs))
         .toBe(3 + container.popUpContentBoostZIndex * 2);
   });
});
