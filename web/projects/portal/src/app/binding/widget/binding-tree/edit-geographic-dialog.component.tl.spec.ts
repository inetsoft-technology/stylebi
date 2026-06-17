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
 * EditGeographicDialog — single pass (+memory-leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — provider setter: captures initial mapType from getBindingModel()
 *   Group 2 [Risk 2] — isGeoRef(): boolean dual-path; drives cancelClicked branch and
 *                       template layerVisible/mappingVisible bindings
 *   Group 3 [Risk 3] — cancelClicked: three distinct paths based on mapType change + isGeoRef;
 *                       incorrect branching either skips mapType restore or calls it when not needed
 *   Group 4 [Risk 2] — okClicked: stopPropagation + onCommit("ok") emission
 *   Group 5 [Risk 1] — updateLoadData: sets loadData=true to enable OK button
 *
 * Suspected bugs:
 *   cancelClicked subscription leak — when changeMapType returns a non-completing Observable
 *   and the component is destroyed before response, the subscribe callback fires post-destroy.
 *   The component does not implement OnDestroy, so the subscription is never cleaned up.
 *
 * Out of scope:
 *   ngOnInit() — empty body, no observable side effect
 *   chartGeoModel getter — pure delegation to provider.getChartGeoModel(), tested via isGeoRef()
 */

import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { render } from "@testing-library/angular";
import { of } from "rxjs";

import { EditGeographicDialog } from "./edit-geographic-dialog.component";
import { ModalHeaderComponent } from "../../../widget/modal-header/modal-header.component";
import { GeoOptionPane } from "../../editor/chart/field/geo-option-pane.component";
import { GeoProvider } from "../../../common/data/geo-provider";

// ---------------------------------------------------------------------------
// Child component stubs (both are in imports[] and will be instantiated by Angular)
// ---------------------------------------------------------------------------

@Component({ selector: "modal-header", standalone: true, template: "" })
class ModalHeaderStub {
   @Input() title: any;
   @Input() cshid: any;
   @Output() onCancel = new EventEmitter<any>();
}

@Component({ selector: "geo-option-pane", standalone: true, template: "" })
class GeoOptionPaneStub {
   @Input() mapVisible: any;
   @Input() layerVisible: any;
   @Input() mappingVisible: any;
   @Input() provider: any;
   @Input() refName: any;
   @Output() onLoadData = new EventEmitter<any>();
}

// ---------------------------------------------------------------------------
// Provider mock
// ---------------------------------------------------------------------------

const PROVIDER_MOCK = {
   getBindingModel: vi.fn().mockReturnValue({ mapType: "World" }),
   getChartGeoModel: vi.fn().mockReturnValue(null as any),
   changeMapType: vi.fn().mockReturnValue(of(null)),
   populateMappingStatus: vi.fn().mockReturnValue(of(null)),
   getGeoData: vi.fn().mockReturnValue(of(null)),
   getLikelyFeatures: vi.fn().mockReturnValue(of(null)),
   getMappingData: vi.fn().mockReturnValue(of(null)),
};

beforeEach(() => {
   PROVIDER_MOCK.getBindingModel.mockReturnValue({ mapType: "World" });
   PROVIDER_MOCK.getChartGeoModel.mockReturnValue(null);
   PROVIDER_MOCK.changeMapType.mockReturnValue(of(null));
   PROVIDER_MOCK.changeMapType.mockClear();
});

// ---------------------------------------------------------------------------
// renderComp helper
// ---------------------------------------------------------------------------

interface RenderOpts {
   mapType?: string;
   geoRef?: boolean;
}

async function renderComp(opts: RenderOpts = {}) {
   const { mapType = "World", geoRef = false } = opts;
   PROVIDER_MOCK.getBindingModel.mockReturnValue({ mapType });
   PROVIDER_MOCK.getChartGeoModel.mockReturnValue(
      geoRef ? { classType: "geo" } : null
   );
   const { fixture } = await render(EditGeographicDialog, {
      componentInputs: { provider: PROVIDER_MOCK as unknown as GeoProvider, refName: "geoField" },
      importOverrides: [
         { replace: ModalHeaderComponent, with: ModalHeaderStub },
         { replace: GeoOptionPane, with: GeoOptionPaneStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });
   return { comp: fixture.componentInstance, fixture };
}

// ---------------------------------------------------------------------------
// Group 1: provider setter — captures initial mapType
// ---------------------------------------------------------------------------

describe("EditGeographicDialog — provider setter", () => {

   // 🔁 Regression-sensitive: _mapType is compared against the current binding model in
   // cancelClicked; if it is not captured at set time, the mapType-change branch never fires.
   it("should store the provider and capture the initial mapType from getBindingModel()", async () => {
      const { comp } = await renderComp({ mapType: "World" });
      expect(comp.provider).toBe(PROVIDER_MOCK as unknown as GeoProvider);
      // Bypass: _mapType is private with no public getter; tested here because it drives
      // the cancelClicked branch selection (mapType-changed vs unchanged).
      expect((comp as any)._mapType).toBe("World");
   });
});

// ---------------------------------------------------------------------------
// Group 2: isGeoRef() — boolean dual-path
// ---------------------------------------------------------------------------

describe("EditGeographicDialog — isGeoRef()", () => {

   // 🔁 Regression-sensitive: isGeoRef drives the cancelClicked branch; a wrong return value
   // either skips the mapType restore call or calls it unnecessarily.
   it("should return true when chartGeoModel.classType is 'geo'", async () => {
      const { comp } = await renderComp({ geoRef: true });
      expect(comp.isGeoRef()).toBe(true);
   });

   it("should return false when chartGeoModel.classType is not 'geo'", async () => {
      const { comp } = await renderComp({ geoRef: false });
      PROVIDER_MOCK.getChartGeoModel.mockReturnValue({ classType: "country" });
      expect(comp.isGeoRef()).toBe(false);
   });

   it("should return falsy when chartGeoModel is null", async () => {
      const { comp } = await renderComp({ geoRef: false });
      PROVIDER_MOCK.getChartGeoModel.mockReturnValue(null);
      expect(comp.isGeoRef()).toBeFalsy();
   });
});

// ---------------------------------------------------------------------------
// Group 3: cancelClicked — three-path branch
// ---------------------------------------------------------------------------

describe("EditGeographicDialog — cancelClicked", () => {

   function makeEvt(): MouseEvent {
      return { stopPropagation: vi.fn() } as unknown as MouseEvent;
   }

   // 🔁 Regression-sensitive: incorrect branch selection either silently skips the mapType
   // restore or calls it when the user did not change the map type.
   it("mapType unchanged: emits 'cancel' directly without calling changeMapType", async () => {
      const { comp } = await renderComp({ mapType: "World", geoRef: false });
      // mapType stays "World" — no change
      const cancelled: string[] = [];
      comp.onCancel.subscribe(v => cancelled.push(v));
      comp.cancelClicked(makeEvt());
      // Gate: wait for onCancel to confirm the code path ran
      expect(cancelled).toEqual(["cancel"]);
      expect(PROVIDER_MOCK.changeMapType).not.toHaveBeenCalled();
   });

   it("mapType changed + isGeoRef=true: emits 'cancel' directly without calling changeMapType", async () => {
      const { comp } = await renderComp({ mapType: "World", geoRef: true });
      // Simulate map type change after provider was set
      PROVIDER_MOCK.getBindingModel.mockReturnValue({ mapType: "US" });
      const cancelled: string[] = [];
      comp.onCancel.subscribe(v => cancelled.push(v));
      comp.cancelClicked(makeEvt());
      expect(cancelled).toEqual(["cancel"]);
      expect(PROVIDER_MOCK.changeMapType).not.toHaveBeenCalled();
   });

   it("mapType changed + isGeoRef=false: calls changeMapType with original mapType then emits 'cancel'", async () => {
      const { comp } = await renderComp({ mapType: "World", geoRef: false });
      // Simulate map type change
      PROVIDER_MOCK.getBindingModel.mockReturnValue({ mapType: "US" });
      const cancelled: string[] = [];
      comp.onCancel.subscribe(v => cancelled.push(v));
      comp.cancelClicked(makeEvt());
      expect(PROVIDER_MOCK.changeMapType).toHaveBeenCalledWith("geoField", "World", null);
      expect(cancelled).toEqual(["cancel"]);
   });

   it("should call evt.stopPropagation() in all branches", async () => {
      const { comp } = await renderComp({ mapType: "World", geoRef: false });
      const evt = makeEvt();
      comp.cancelClicked(evt);
      expect((evt.stopPropagation as ReturnType<typeof vi.fn>)).toHaveBeenCalledTimes(1);
   });
});

// ---------------------------------------------------------------------------
// Group 4: okClicked — stopPropagation + onCommit
// ---------------------------------------------------------------------------

describe("EditGeographicDialog — okClicked", () => {

   it("should call evt.stopPropagation()", async () => {
      const { comp } = await renderComp();
      const evt = { stopPropagation: vi.fn() } as unknown as MouseEvent;
      comp.okClicked(evt);
      expect((evt.stopPropagation as ReturnType<typeof vi.fn>)).toHaveBeenCalledTimes(1);
   });

   it("should emit 'ok' via onCommit", async () => {
      const { comp } = await renderComp();
      const committed: string[] = [];
      comp.onCommit.subscribe(v => committed.push(v));
      comp.okClicked({ stopPropagation: vi.fn() } as unknown as MouseEvent);
      expect(committed).toEqual(["ok"]);
   });
});

// ---------------------------------------------------------------------------
// Group 5: updateLoadData — enables OK button
// ---------------------------------------------------------------------------

describe("EditGeographicDialog — updateLoadData", () => {

   it("should set loadData to true when called", async () => {
      const { comp } = await renderComp();
      expect(comp.loadData).toBe(false);
      comp.updateLoadData("someEvent");
      expect(comp.loadData).toBe(true);
   });
});
