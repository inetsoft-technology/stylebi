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
 * GeoMappingDialog — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnInit/populateModels: algorithm, mappings, regions from provider data
 *   Group 2 [Risk 3] — add()/remove(): manualMappings mutation and unmatched list refresh
 *   Group 3 [Risk 3] — checkDuplicateMapping via update()/ok(): dupMapping detection
 *   Group 4 [Risk 2] — getFilteredFeatures(): filter string logic
 *   Group 5 [Risk 2] — ok()/cancel(): onCommit/onCancel emitters
 *   Group 6 [Risk 2] — changeAlgorithm/unmatchedListChange: algorithm switch refreshes cities
 *   Group 7 [Risk 3] — Cancel leaves the field's mappings untouched; OK saves them (Bug #78707)
 *
 * Old spec ported (Risk 2):
 *   Bug #19048: changeAlgorithm() must call unmatchedListChange
 *   Bug #19560/#19257: no danger alert when city and region unmatched
 *
 * Out of scope:
 *   remove() dup-value warning dialog — delegates to ComponentTool.showMessageDialog + NgbModal.
 *   ScrollableTableDirective column-width side effects — ViewChild not available in unit test.
 *   Button disabled DOM state — covered by old spec; TL pass focuses on mapping logic.
 */

import { HttpResponse } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of } from "rxjs";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { TestUtils } from "../../../../common/test/test-utils";
import { ModelService } from "../../../../widget/services/model.service";
import { ChartGeoRef } from "../../../data/chart/chart-geo-ref";
import { GeoProvider } from "../../../../common/data/geo-provider";
import { GeoMappingDialog } from "./geo-mapping-dialog.component";

const dataMapping = {
   features: {
      IL: { name: "Illinois", geoCode: "IL" },
      TX: { name: "Texas", geoCode: "TX" },
      FL: { name: "Florida", geoCode: "FL" },
   },
   algorithms: ["None", "Distance", "Double Metaphone", "Metaphone", "Soundex"],
   viewAlgorithms: ["None", "Distance", "Double Metaphone", "Metaphone", "Soundex"],
   ounmatchedValue: { FL: 4, IL: 5, TX: 12 },
   unmatchedValue: { CA: 1 },
   manualMappings: { CA: "California" },
   smapping: { mappings: { NY: "US-NY" } },
};

function createGeoModel(): ChartGeoRef {
   return Object.assign({
      option: {
         layerValue: "2",
         mapping: {
            algorithm: "Distance",
            dupMapping: null,
            id: "Built-in",
            layer: 2,
            mappings: {},
            type: "U.S.",
         },
         order: 1,
      },
   }, TestUtils.createMockChartDimensionRef());
}

const modelServiceMock = { getModel: vi.fn().mockReturnValue(of(null)) };
const uiContextMock = {
   getDefaultTab: vi.fn().mockReturnValue("true"),
   setDefaultTab: vi.fn(),
};
const modalMock = {
   open: vi.fn().mockReturnValue({ result: Promise.resolve("ok") }),
};

function createGeoProvider(geoModel: ChartGeoRef, data: any = dataMapping): GeoProvider {
   return {
      getBindingModel: vi.fn(),
      getChartGeoModel: vi.fn().mockReturnValue(geoModel),
      populateMappingStatus: vi.fn(),
      getGeoData: vi.fn(),
      changeMapType: vi.fn(),
      getLikelyFeatures: vi.fn().mockReturnValue(of(new HttpResponse({
         body: [
            { name: "Los Angeles", geoCode: "US-LA" },
            { name: "San Francisco", geoCode: "US-SF" },
         ],
      }))),
      getMappingData: vi.fn().mockReturnValue(of(new HttpResponse({ body: data }))),
   };
}

async function renderComponent(geoModel: ChartGeoRef = createGeoModel(), data: any = dataMapping) {
   const provider = createGeoProvider(geoModel, data);
   const { fixture } = await render(GeoMappingDialog, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: ModelService, useValue: modelServiceMock },
         { provide: UIContextService, useValue: uiContextMock },
         { provide: NgbModal, useValue: modalMock },
      ],
      componentProperties: { provider },
   });
   fixture.detectChanges();
   await fixture.whenStable();
   return { fixture, comp: fixture.componentInstance as GeoMappingDialog, provider, geoModel };
}

beforeEach(() => {
   uiContextMock.getDefaultTab.mockReturnValue("true");
   uiContextMock.setDefaultTab.mockClear();
   modalMock.open.mockClear();
});

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: ngOnInit / populateModels [Risk 3]
// ---------------------------------------------------------------------------

describe("GeoMappingDialog — ngOnInit and populateModels", () => {
   it("should populate algorithm, mappings, and regions from provider data on init", async () => {
      const { comp, provider } = await renderComponent();

      expect(provider.getMappingData).toHaveBeenCalled();
      expect(comp.algorithm).toBe("Distance");
      expect(comp.algorithms).toEqual(dataMapping.algorithms);
      expect(comp.manualMappings).toEqual(expect.objectContaining({ NY: "US-NY" }));
      expect(comp.model.regions.map(r => r.label)).toContain("CA");
      expect(comp.showAlgorithms).toHaveLength(dataMapping.algorithms.length);
   });
});

// ---------------------------------------------------------------------------
// Group 2: add() / remove() [Risk 3]
// ---------------------------------------------------------------------------

describe("GeoMappingDialog — add and remove", () => {
   it("should add a manual mapping and remove the region from unmatchedValues", async () => {
      const { comp } = await renderComponent();
      comp.unmatchedValues = { CA: 1 };
      comp.model.currentSelection = {
         region: { label: "CA", data: 1 },
         city: { label: "California", data: "US-CA" },
      };

      comp.add();

      expect(comp.manualMappings["CA"]).toBe("US-CA");
      expect(comp.unmatchedValues["CA"]).toBeUndefined();
      expect(comp.filter).toBe("");
   });

   it("should remove a manual mapping and restore unmatched value", async () => {
      const { comp } = await renderComponent();
      comp.manualMappings = { CA: "US-CA" };
      comp.ounmatchedValues = { CA: 1 };
      comp.unmatchedValues = {};
      comp.populateMappingDialogModel();
      comp.model.selectedIndex = comp.model.list.findIndex(m => m.region === "CA");

      comp.remove();

      expect(comp.manualMappings["CA"]).toBeUndefined();
      expect(comp.unmatchedValues["CA"]).toBe(1);
      expect(comp.model.selectedIndex).toBe(-1);
   });
});

// ---------------------------------------------------------------------------
// Group 3: checkDuplicateMapping via update()/ok() [Risk 3]
// ---------------------------------------------------------------------------

describe("GeoMappingDialog — duplicate mapping detection", () => {
   it("should detect duplicate geo codes via update()", async () => {
      const geoModel = createGeoModel();
      const { comp } = await renderComponent(geoModel);
      comp.manualMappings = { CA: "US-1", NV: "US-1" };
      comp.autoMappings = {};
      comp.matches = [
         { name: "California", geoCode: "US-1" },
         { name: "Nevada", geoCode: "US-1" },
      ];

      comp.update();

      expect(geoModel.option.mapping.dupMapping).toEqual({
         California: ["CA", "NV"],
      });
   });

   it("should clear dupMapping when there are no manual mappings", async () => {
      const geoModel = createGeoModel();
      geoModel.option.mapping.dupMapping = { Texas: ["TX", "TX2"] };
      const { comp } = await renderComponent(geoModel);
      comp.manualMappings = {};
      comp.autoMappings = {};

      comp.update();

      expect(geoModel.option.mapping.dupMapping).toBeNull();
   });

   it("should emit chartGeoModel on ok() after updating mappings", async () => {
      const geoModel = createGeoModel();
      const { comp } = await renderComponent(geoModel);
      const emitted: ChartGeoRef[] = [];
      comp.onCommit.subscribe(v => emitted.push(v));
      comp.manualMappings = { CA: "US-CA" };
      const evt = { stopPropagation: vi.fn() };

      comp.ok(evt);

      expect(geoModel.option.mapping.mappings).toEqual({ CA: "US-CA" });
      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(geoModel);
      expect(evt.stopPropagation).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 4: getFilteredFeatures() [Risk 2]
// ---------------------------------------------------------------------------

describe("GeoMappingDialog — getFilteredFeatures", () => {
   it("should return all features when filter is empty", async () => {
      const { comp } = await renderComponent();
      comp.allfeatures = [
         { label: "Los Angeles", data: "US-LA" },
         { label: "San Francisco", data: "US-SF" },
      ];
      comp.filter = "";

      expect(comp.getFilteredFeatures()).toEqual(comp.allfeatures);
   });

   it("should filter features by case-insensitive substring", async () => {
      const { comp } = await renderComponent();
      comp.allfeatures = [
         { label: "Los Angeles", data: "US-LA" },
         { label: "San Francisco", data: "US-SF" },
      ];
      comp.filter = " franc ";

      expect(comp.getFilteredFeatures()).toEqual([
         { label: "San Francisco", data: "US-SF" },
      ]);
   });

   it("should return empty array when allfeatures is undefined", async () => {
      const { comp } = await renderComponent();
      comp.allfeatures = undefined;
      comp.filter = "test";

      expect(comp.getFilteredFeatures()).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 5: ok() / cancel() [Risk 2]
// ---------------------------------------------------------------------------

describe("GeoMappingDialog — ok and cancel", () => {
   it("should emit onCancel when cancel() is called", async () => {
      const { comp } = await renderComponent();
      const emitted: string[] = [];
      comp.onCancel.subscribe(v => emitted.push(v));
      const evt = { stopPropagation: vi.fn() };

      comp.cancel(evt);

      expect(emitted).toEqual(["cancel"]);
      expect(evt.stopPropagation).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 6: changeAlgorithm / unmatchedListChange [Risk 2]
// ---------------------------------------------------------------------------

describe("GeoMappingDialog — changeAlgorithm and unmatchedListChange", () => {
   // 🔁 Regression-sensitive (Bug #19048): algorithm change must refresh likely features.
   it("should call unmatchedListChange when changeAlgorithm is invoked", async () => {
      const { comp } = await renderComponent();
      const spy = vi.spyOn(comp, "unmatchedListChange");
      comp.model.currentSelection.region = { label: "CA", data: 1 };

      comp.changeAlgorithm();

      expect(spy).toHaveBeenCalledWith(comp.model.currentSelection.region);
   });

   it("should load filtered cities when unmatchedListChange receives a region", async () => {
      const { comp, provider } = await renderComponent();

      comp.unmatchedListChange({ label: "CA", data: 1 });
      await vi.waitFor(() => expect(comp.model.cities.length).toBeGreaterThan(0));

      expect(provider.getLikelyFeatures).toHaveBeenCalledWith(1, "Distance");
      expect(comp.model.cities).toEqual([
         { label: "Los Angeles", data: "US-LA" },
         { label: "San Francisco", data: "US-SF" },
      ]);
   });

   // 🔁 Regression-sensitive (Bug #19560/#19257): no danger alert for unmatched selection.
   it("should not show danger alert when city and region are unmatched", async () => {
      const { fixture, comp } = await renderComponent();
      comp.model.selected = false;
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector("div.alert-danger")).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 7: Cancel leaves the field untouched, OK saves (Bug #78707) [Risk 3]
// ---------------------------------------------------------------------------

describe("GeoMappingDialog — Cancel and OK through the dialog buttons (Bug #78707)", () => {
   type Fixture = Awaited<ReturnType<typeof renderComponent>>["fixture"];

   function click(fixture: Fixture, el: Element): void {
      (el as HTMLElement).click();
      fixture.detectChanges();
   }

   function q(fixture: Fixture, css: string): HTMLElement[] {
      return Array.from(fixture.nativeElement.querySelectorAll(css));
   }

   function footerButton(fixture: Fixture, label: string): HTMLElement {
      return q(fixture, ".modal-footer button").find(b => b.textContent.includes(label));
   }

   function rows(fixture: Fixture): string[][] {
      return q(fixture, "tbody tr").map(tr =>
         Array.from(tr.querySelectorAll("td")).map(td => td.textContent.trim()));
   }

   function addFirstCity(fixture: Fixture, region: string): void {
      click(fixture, q(fixture, ".region-list .list-item")
         .find(e => e.textContent.trim() === region));
      click(fixture, q(fixture, ".city-list .list-item")[0]);
      click(fixture, fixture.nativeElement.querySelector(".add-btn_id"));
   }

   // add()/remove() edit the reply's unmatched lists, so each test gets its own copy
   const reply = (over: any = {}) => structuredClone({ ...dataMapping, ...over });
   const noSaved = (over: any = {}) => reply({ smapping: null, ...over });

   it("Add + Cancel leaves the field's mappings untouched", async () => {
      const geoModel = createGeoModel();
      const mappings = geoModel.option.mapping.mappings;
      const { fixture, comp } = await renderComponent(geoModel, noSaved());
      const cancelled: string[] = [];
      comp.onCancel.subscribe(v => cancelled.push(v));

      addFirstCity(fixture, "CA");
      expect(rows(fixture)).toContainEqual(["CA", "Los Angeles"]);
      click(fixture, footerButton(fixture, "Cancel"));

      expect(cancelled).toEqual(["cancel"]);
      expect(geoModel.option.mapping.mappings).toBe(mappings);
      expect(geoModel.option.mapping.mappings).toEqual({});
   });

   it("Remove + Cancel keeps the field's mapping", async () => {
      const geoModel = createGeoModel();
      geoModel.option.mapping.mappings = { Flordia: "FL" };
      const { fixture } = await renderComponent(geoModel, noSaved({
         manualMappings: { FL: "Florida" },
         unmatchedValue: {},
         ounmatchedValue: { Flordia: 2 },
      }));

      click(fixture, q(fixture, "tbody tr").find(tr => tr.textContent.includes("Flordia")));
      click(fixture, fixture.nativeElement.querySelector(".remove-btn_id"));
      expect(rows(fixture).map(r => r[0])).not.toContain("Flordia");
      click(fixture, footerButton(fixture, "Cancel"));

      expect(geoModel.option.mapping.mappings).toEqual({ Flordia: "FL" });
   });

   it("open + Cancel does not copy the server's saved mapping into the field", async () => {
      const geoModel = createGeoModel();
      const { fixture } = await renderComponent(geoModel, reply());

      expect(rows(fixture).map(r => r[0])).toContain("NY");
      click(fixture, footerButton(fixture, "Cancel"));

      expect(geoModel.option.mapping.mappings).toEqual({});
   });

   it("OK saves the edits with the field's and the server's saved mappings", async () => {
      const geoModel = createGeoModel();
      geoModel.option.mapping.mappings = { Texsa: "TX" };
      const { fixture, comp } = await renderComponent(geoModel,
         reply({ smapping: { mappings: { Flordia: "FL" } } }));
      const committed: ChartGeoRef[] = [];
      comp.onCommit.subscribe(v => committed.push(v));

      addFirstCity(fixture, "CA");
      click(fixture, footerButton(fixture, "OK"));

      expect(committed).toEqual([geoModel]);
      expect(geoModel.option.mapping.mappings)
         .toEqual({ Texsa: "TX", Flordia: "FL", CA: "US-LA" });
   });
});
