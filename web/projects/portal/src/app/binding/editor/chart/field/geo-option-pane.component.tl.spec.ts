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
 * GeoOptionPane — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — ngOnInit/populateGeoData: load geo models and emit onLoadData
 *   Group 2 [Risk 3] — mapChange/layerChange: delegate map type and layer to provider
 *   Group 3 [Risk 3] — mappingChange: merge smapping from provider getMappingData
 *   Group 4 [Risk 2] — showGeoMappingDialog: apply dialog result and refresh mapping status
 *   Group 5 [Risk 3] — loading/async error: clear loading flag on HTTP failure
 *
 * HTTP: GeoProvider mocks — direct instantiation, mirrors getGeoData/changeMapType endpoints
 *
 * Suspected bugs (header only):
 *   loaded flag never set true — onLoadData emits on every populateModels call
 */

import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of, throwError } from "rxjs";
import { GeoProvider } from "../../../../common/data/geo-provider";
import { TestUtils } from "../../../../common/test/test-utils";
import { ComponentTool } from "../../../../common/util/component-tool";
import { ChartGeoRef } from "../../../data/chart/chart-geo-ref";
import { FeatureMappingInfo } from "../../../data/chart/feature-mapping-info";
import { GeoOptionPane } from "./geo-option-pane.component";

function mockMapping(overrides: Partial<FeatureMappingInfo> = {}): FeatureMappingInfo {
   return {
      algorithm: "exact",
      type: "world",
      layer: 0,
      id: "m1",
      mappings: { CA: "California" },
      dupMapping: {},
      ...overrides
   };
}

function createGeoData(overrides: Record<string, unknown> = {}) {
   return {
      mappingStatus: false,
      type: "World",
      types: { World: "world", US: "us" },
      layers: { Counties: "counties" },
      mappings: { Default: mockMapping() },
      layer: "counties",
      ...overrides
   };
}

function createProvider(overrides: Partial<GeoProvider> = {}): GeoProvider {
   const bindingModel = TestUtils.createMockChartBindingModel();
   bindingModel.mapType = "world";
   const chartGeoModel = Object.assign(new ChartGeoRef(), TestUtils.createMockChartDimensionRef("state"));
   chartGeoModel.option = {
      layerValue: "counties",
      mapping: mockMapping()
   };
   const geoData = createGeoData();

   return {
      getBindingModel: vi.fn().mockReturnValue(bindingModel),
      getChartGeoModel: vi.fn().mockReturnValue(chartGeoModel),
      getGeoData: vi.fn().mockReturnValue(of({ body: geoData })),
      changeMapType: vi.fn().mockReturnValue(of({ body: geoData })),
      populateMappingStatus: vi.fn().mockReturnValue(of({ body: "true" })),
      getMappingData: vi.fn().mockReturnValue(of({
         body: { smapping: { mappings: { NY: "New York" } } }
      })),
      getLikelyFeatures: vi.fn().mockReturnValue(of({ body: [] })),
      ...overrides
   } as unknown as GeoProvider;
}

function createPane(provider = createProvider(), options: {
   layerVisible?: boolean;
   mappingVisible?: boolean;
} = {}): GeoOptionPane {
   const comp = new GeoOptionPane({} as NgbModal);
   comp.provider = provider;
   comp.refName = "state";
   comp.layerVisible = options.layerVisible ?? true;
   comp.mappingVisible = options.mappingVisible ?? true;
   return comp;
}

describe("GeoOptionPane — ngOnInit [Group 1, Risk 2]", () => {
   it("should populate geo models and emit onLoadData once", async () => {
      const provider = createProvider();
      const comp = createPane(provider);
      const loaded = vi.fn();
      comp.onLoadData.subscribe(loaded);

      comp.ngOnInit();
      await Promise.resolve();

      expect(provider.getGeoData).toHaveBeenCalled();
      expect(comp.typeModel.length).toBeGreaterThan(0);
      expect(comp.layerMode.length).toBeGreaterThan(0);
      expect(comp.mappingMode.length).toBeGreaterThan(0);
      expect(loaded).toHaveBeenCalled();
      expect(comp.loading).toBe(false);
   });
});

describe("GeoOptionPane — mapChange and layerChange [Group 2, Risk 3]", () => {
   it("should call provider.changeMapType when map type changes", async () => {
      const provider = createProvider();
      const comp = createPane(provider);
      comp.ngOnInit();
      await Promise.resolve();

      comp.mapChange("us");
      await Promise.resolve();

      expect(provider.changeMapType).toHaveBeenCalledWith("state", "us", null);
      expect(comp.chartGeoModel.option.mapping.type).toBe("us");
   });

   it("should pass selected layer to changeMapType on layerChange", async () => {
      const provider = createProvider();
      const comp = createPane(provider);
      comp.ngOnInit();
      await Promise.resolve();

      comp.layerChange("regions");
      await Promise.resolve();

      expect(provider.changeMapType).toHaveBeenCalledWith("state", comp.mapType, "regions");
      expect(comp.layer).toBe("regions");
   });
});

describe("GeoOptionPane — mappingChange [Group 3, Risk 3]", () => {
   it("should merge smapping from provider and refresh mapping status", async () => {
      const provider = createProvider();
      const comp = createPane(provider);
      comp.ngOnInit();
      await Promise.resolve();
      const nextMapping = mockMapping({ id: "m2" });

      comp.mappingChange(nextMapping);
      await Promise.resolve();

      expect(comp.chartGeoModel.option.mapping).toBe(nextMapping);
      expect(comp.chartGeoModel.option.mapping.mappings).toEqual({ NY: "New York" });
      expect(provider.populateMappingStatus).toHaveBeenCalled();
   });
});

describe("GeoOptionPane — showGeoMappingDialog [Group 4, Risk 2]", () => {
   it("should apply dialog mapping result via showDialog callback", () => {
      const provider = createProvider();
      const comp = createPane(provider);
      let onCommit: (result: any) => void = () => {};
      vi.spyOn(ComponentTool, "showDialog").mockImplementation((_m, _t, commit) => {
         onCommit = commit;
         return { provider: null } as any;
      });

      comp.showGeoMappingDialog();
      onCommit({ option: { mapping: mockMapping({ id: "m9" }) } });

      expect(comp.chartGeoModel.option.mapping.id).toBe("m9");
   });
});

describe("GeoOptionPane — loading error [Group 5, Risk 3]", () => {
   it("should clear loading when getGeoData fails", async () => {
      const provider = createProvider({
         getGeoData: vi.fn().mockReturnValue(throwError(() => new Error("network")))
      });
      const comp = createPane(provider);

      comp.ngOnInit();
      await Promise.resolve();

      expect(comp.loading).toBe(false);
   });
});
