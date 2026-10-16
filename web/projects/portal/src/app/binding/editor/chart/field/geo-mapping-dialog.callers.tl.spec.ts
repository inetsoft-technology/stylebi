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
 * Bug #78707: Cancel in Edit Mapping, through the viewsheet Edit Geographic caller.
 *
 * Real NgbModal, EditGeographicDialog, GeoOptionPane, GeoMappingDialog and VSGeoProvider;
 * VSBindingPane.processOpenEditGeographicCommand runs on its real prototype with a fake
 * `this`. ModelService answers in the server's reply shapes; like
 * ChartController.getMappingData, a value the posted field maps is not unmatched.
 */

import { ApplicationRef } from "@angular/core";
import { HttpParams, HttpResponse } from "@angular/common/http";
import { TestBed } from "@angular/core/testing";
import { NgbConfig, NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { of } from "rxjs";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { TestUtils } from "../../../../common/test/test-utils";
import { VSBindingPane } from "../../../../vsview/edit/vs-binding-pane.component";
import { ModelService } from "../../../../widget/services/model.service";

const mapping = () => ({ id: "Built-in", type: "World", layer: 2, algorithm: "Distance",
                         mappings: {}, dupMapping: {} });

const modelService = {
   getModel: vi.fn((url: string) => of({
      type: "World", layer: "2", types: { World: "World" }, layers: { States: "2" },
      mappings: { "Built-in": mapping() }
   })),
   putModel: vi.fn((url: string, body: any, params: HttpParams) => {
      if(url.endsWith("/getMappingData")) {
         const mapped = !!body.option.mapping.mappings?.["Flordia"];

         return of(new HttpResponse({ body: {
            features: { IL: { name: "Illinois", geoCode: "IL" } },
            algorithms: ["None", "Distance"], viewAlgorithms: ["None", "Distance"],
            unmatchedValue: mapped ? { Texsa: 3 } : { Flordia: 2, Texsa: 3 },
            ounmatchedValue: { IL: 0, Flordia: 2, Texsa: 3 },
            manualMappings: mapped ? { FL: "Florida" } : {}
         }}));
      }

      if(url.endsWith("/getLikelyFeatures")) {
         return of(new HttpResponse({ body: params.get("row") === "2" ?
            [{ name: "Florida", geoCode: "FL" }] : [] }));
      }

      return of(new HttpResponse({ body: "false" }));
   })
};

describe("GeoMappingDialog Cancel through VS Edit Geographic (Bug #78707)", () => {
   let geo: any;
   let pane: any;
   let sent: [string, any][];

   async function settle(): Promise<void> {
      for(let i = 0; i < 5; i++) {
         TestBed.inject(ApplicationRef).tick();
         await new Promise(r => setTimeout(r));
      }
   }

   const topModal = (): HTMLElement => {
      const windows = document.querySelectorAll("ngb-modal-window");
      return windows[windows.length - 1] as HTMLElement;
   };

   const qa = (css: string): HTMLElement[] => Array.from(topModal().querySelectorAll(css));

   async function click(el: HTMLElement): Promise<void> {
      expect(el).toBeTruthy();
      el.click();
      await settle();
   }

   async function press(label: string): Promise<void> {
      await click(qa(".modal-footer button").find(b => b.textContent.includes(label)));
   }

   async function openEditMapping(): Promise<void> {
      await click(topModal().querySelector(".geo-mapping-icon"));
   }

   async function addFlordia(): Promise<void> {
      await click(qa(".region-list .list-item").find(e => e.textContent.trim() === "Flordia"));
      await click(qa(".city-list .list-item")[0]);
      await click(topModal().querySelector(".add-btn_id"));
   }

   async function removeFlordia(): Promise<void> {
      await click(qa("tbody tr").find(tr => tr.textContent.includes("Flordia")));
      await click(topModal().querySelector(".remove-btn_id"));
   }

   async function openEditGeographic(): Promise<void> {
      const bindingModel = { mapType: "World", geoCols: [geo] };
      (VSBindingPane.prototype as any).processOpenEditGeographicCommand.call(pane,
         { bindingModel, chartGeoRefModel: geo, measureName: null });
      await settle();
   }

   const sentMappings = () => sent.map(([url, event]) =>
      [url, event.model.geoCols[0].option.mapping.mappings]);

   beforeEach(() => {
      TestBed.configureTestingModule({
         providers: [
            { provide: ModelService, useValue: modelService },
            { provide: UIContextService, useValue: {
               getDefaultTab: vi.fn().mockReturnValue("false"), setDefaultTab: vi.fn()
            }}
         ]
      });
      TestBed.inject(NgbConfig).animation = false;
      sent = [];
      geo = Object.assign(TestUtils.createMockChartDimensionRef("City"), {
         classType: "geo", measure: false, fullName: "City", columnValue: "City",
         option: { layerValue: "2", mapping: mapping() }
      });
      pane = {
         assemblyName: "Chart1", params: new HttpParams().set("vsId", "vs1"),
         modalService: TestBed.inject(NgbModal), modelService,
         clientService: {
            // copy at send time, the dialogs keep the live ref
            sendEvent: vi.fn((url: string, event: any) =>
               sent.push([url, JSON.parse(JSON.stringify(event))]))
         }
      };
   });

   afterEach(() => {
      TestBed.inject(NgbModal).dismissAll();
      document.body.innerHTML = "";
   });

   it("Add + Cancel, then Edit Geographic OK sends no mapping", async () => {
      await openEditGeographic();
      await openEditMapping();
      await addFlordia();
      expect(qa("tbody tr").map(tr => tr.textContent)).toContainEqual(
         expect.stringContaining("Flordia"));
      await press("Cancel");
      await press("OK");

      expect(sentMappings()).toEqual([["/events/vs/chart/changeChartRef", {}]]);
   });

   it("a cancelled Remove keeps the mapping an earlier OK added", async () => {
      await openEditGeographic();
      await openEditMapping();
      await addFlordia();
      await press("OK");
      await openEditMapping();
      await removeFlordia();
      await press("Cancel");
      await press("OK");

      expect(sentMappings()).toEqual([["/events/vs/chart/changeChartRef", { Flordia: "FL" }]]);
   });
});
