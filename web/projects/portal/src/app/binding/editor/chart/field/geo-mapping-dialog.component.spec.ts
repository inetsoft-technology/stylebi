/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { HttpResponse } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ComponentFixture, TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";
import { of as observableOf } from "rxjs";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { TestUtils } from "../../../../common/test/test-utils";
import { LargeFormFieldComponent } from "../../../../widget/large-form-field/large-form-field.component";
import { ModelService } from "../../../../widget/services/model.service";
import { ChartGeoRef } from "../../../data/chart/chart-geo-ref";
import { GeoMappingDialogModel } from "../../../data/chart/geo-mapping-dialog-model";
import { VSGeoProvider } from "../../vs-geo-provider";
import { GeoMappingDialog } from "./geo-mapping-dialog.component";

let createModel: () => GeoMappingDialogModel = () => {
   return{
      id: "1",
      selected: false,
      selectedIndex: -1,
      algorithms: ["None", "Distance", "Double Metaphaone", "Metaphone", "Soundex"],
      regions: [],
      cities: [],
      currentSelection: {
         city: null,
         region: null
      },
      list: [
         {region: "TX", city: "Texas"},
         {region: "FL", city: "AA"},
         {region: "IL", city: "BB"}]
   };
};

let createGeoModel: () => ChartGeoRef = () => {
   return Object.assign({
      option: {
         layerValue: "2",
         mapping: {
            algorithm: "Distance",
            dupMapping: null,
            id: "Built-in",
            layer: 2,
            mappings: {},
            type: "U.S."
         },
         order: 1
      },
   }, TestUtils.createMockChartDimensionRef());
};

let dataMapping = {
   "features": {
               "IL": {"name": "Illinois", "geoCode": "IL"},
               "TX": {"name": "Texas", "geoCode": "TX"},
               "FL": {"name": "Florida", "geoCode": "FL"}
               },
   "algorithms": ["None", "Distance", "Double Metaphone", "Metaphone", "Soundex"],
   "viewAlgorithms": ["None", "Distance", "Double Metaphone", "Metaphone", "Soundex"],
   "ounmatchedValue": {"FL": 4, "IL": 5, "TX": 12},
   "unmatchedValue": {},
   "manualMappings": {}
};

describe("geo mapping dialog component unit case", () => {
   let modelService: any;
   let fixture: ComponentFixture<GeoMappingDialog>;
   let geoMappingDialog: GeoMappingDialog;
   let geoProvider: any;
   let uiContextService: any;

   beforeEach(() => {
      modelService = {};
      geoProvider = {
         getMappingData: jest.fn(() => observableOf(new HttpResponse({body: dataMapping}))),
         getChartGeoModel: jest.fn(() => createGeoModel())
      };
      uiContextService = {
         isAdhoc: jest.fn(),
         getDefaultTab: jest.fn(),
         setDefaultTab: jest.fn()
      };

      TestBed.configureTestingModule({
         imports: [ReactiveFormsModule, FormsModule, NgbModule],
         declarations: [GeoMappingDialog, LargeFormFieldComponent],
         providers: [
            {provide: ModelService, useValue: modelService},
            {provide: VSGeoProvider, useValue: geoProvider},
            {provide: UIContextService, useValue: uiContextService}
         ],
         schemas: [NO_ERRORS_SCHEMA]
      }).compileComponents();

      fixture = TestBed.createComponent(GeoMappingDialog);
      geoMappingDialog = <GeoMappingDialog>fixture.componentInstance;
      geoMappingDialog.model = createModel();
      geoMappingDialog.provider = geoProvider;
   });

   //Bug #19051 remove button should be disale when no selected region code
   it("remove button status check", () => {
      fixture.detectChanges();
      geoMappingDialog.model.selected = true;
      geoMappingDialog.model.selectedIndex = 0;
      geoMappingDialog.populateMappingDialogModel();
      fixture.detectChanges();
      let addBtn = fixture.nativeElement.querySelector("button.add-btn_id");
      let removeBtn = fixture.nativeElement.querySelector("button.remove-btn_id");
      expect(addBtn.disabled).toBeTruthy();
      expect(removeBtn.disabled).toBeFalsy();

      geoMappingDialog.model.selectedIndex = 1;
      geoMappingDialog.model.selected = false;
      geoMappingDialog.populateMappingDialogModel();
      fixture.detectChanges();
      expect(addBtn.disabled).toBeTruthy();
      expect(removeBtn.disabled).toBeTruthy();
   });

   //Bug #19048 should refresh mapping data
   it("should refresh mapping data when switch algorithm", () => {
      const unmatchedListChange = jest.spyOn(geoMappingDialog, "unmatchedListChange");
      geoMappingDialog.changeAlgorithm();
      expect(unmatchedListChange).toHaveBeenCalled();
   });

   //Bug #19560 and Bug #19257
   it("should not pop up warning when city and region unmatched", () => {
      geoMappingDialog.model.selected = false;
      fixture.detectChanges();

      let alert = fixture.nativeElement.querySelector("div.alert-danger");
      expect(alert).toBeNull();
   });
});