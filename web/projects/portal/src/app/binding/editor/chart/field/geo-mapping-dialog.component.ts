/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { Component, EventEmitter, Input, OnInit, Output, ViewChild } from "@angular/core";
import { GeoProvider } from "../../../../common/data/geo-provider";
import { UIContextService } from "../../../../common/services/ui-context.service";
import { ScrollableTableDirective } from "../../../../widget/scrollable-table/scrollable-table.directive";
import { ModelService } from "../../../../widget/services/model.service";
import { ChartGeoRef } from "../../../data/chart/chart-geo-ref";
import { FeatureMappingInfo } from "../../../data/chart/feature-mapping-info";
import { GeoMap } from "../../../data/chart/geo-map";
import { GeoMappingDialogModel } from "../../../data/chart/geo-mapping-dialog-model";
import { MapFeature } from "../../../data/chart/map-feature";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../../../common/util/component-tool";

@Component({
   selector: "geo-mapping-dialog",
   templateUrl: "geo-mapping-dialog.component.html",
   styleUrls: ["geo-mapping-dialog.component.scss"]
})
export class GeoMappingDialog implements OnInit {
   @Input() provider: GeoProvider;
   filter: string = null;
   model: GeoMappingDialogModel = <GeoMappingDialogModel> {
      id: "1",
      selected: this.uiContextService.getDefaultTab("geo-mapping.show-matched", "true") == "true",
      selectedIndex: -1,
      algorithms: [],
      regions: [],
      cities: [],
      list: [],
      currentSelection:
         <{region: {label: string, data: number}, city: {label: string, data: string}}> {
            city: null,
            region: null
         }
   };
   algorithm: string;
   algorithms: string[];
   viewAlgorithms: string[];
   showAlgorithms: any[] = [];
   unmatchedValues: any;
   ounmatchedValues: any;
   autoMappings: any;
   manualMappings: any;
   matches: MapFeature[];
   dupValues: string[] = [];
   rautoMappings: string[] = [];
   allfeatures: {label: string, data: string}[];
   controller: string = "../api/composer/vs/geo-mapping-dialog-model/1";
   @Output() onCommit: EventEmitter<ChartGeoRef> = new EventEmitter<ChartGeoRef>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild(ScrollableTableDirective) scrollableTableDirective;

   constructor(private modelService: ModelService,
               private modalService: NgbModal,
               private uiContextService: UIContextService) {
   }

   ngOnInit(): void {
      this.provider.getMappingData().subscribe((data) => {
         let body = data && data.body ? data.body : {};
         this.populateModels(body);
      });
   }

   get chartGeoModel(): ChartGeoRef {
      return this.provider.getChartGeoModel();
   }

   initShowAlgorithms(): void {
      for(let i = 0; i < this.algorithms.length; i++) {
         this.showAlgorithms.push({value: this.algorithms[i],
                                   label: this.viewAlgorithms[i]});
      }
   }

   populateModels(data: any): void {
      this.algorithm = this.chartGeoModel.option.mapping.algorithm;
      this.algorithms = data.algorithms;
      this.viewAlgorithms = data.viewAlgorithms;
      this.unmatchedValues = data.unmatchedValue;
      this.ounmatchedValues = data.ounmatchedValue;
      this.autoMappings = data.features;
      this.manualMappings = this.getFeatureMapping(<FeatureMappingInfo> data.smapping);
      this.matches = this.getMatchList(data.manualMappings);
      this.initShowAlgorithms();

      this.populateMappingDialogModel();
   }

   getFeatureMapping(smapping: FeatureMappingInfo): any {
      let mapping = this.chartGeoModel.option.mapping;
      let manualMappings = mapping ? mapping.mappings : {};
      let smanuals = smapping ? smapping.mappings : {};

      for(let key in smanuals) {
         if(smanuals.hasOwnProperty(key)) {
            manualMappings[key] = smanuals[key];
         }
      }

      return manualMappings;
   }

   getMatchList(manualMappings: any): MapFeature[] {
      let arr: MapFeature[] = [];

      for(let key in this.autoMappings) {
         if(this.autoMappings.hasOwnProperty(key)) {
            arr.push(<MapFeature> this.autoMappings[key]);

            if(key.indexOf("(") >= 0 && key.indexOf(")") >= 0) {
               this.dupValues.push(key);
               let dvalue = key.substring(0, key.indexOf("("));
               this.dupValues.push(dvalue);
            }
         }
      }

      for(let manual in manualMappings) {
         if(manualMappings.hasOwnProperty(manual)) {
            arr.push(<MapFeature> {geoCode: manual, name: manualMappings[manual]});
         }
      }

      return arr;
   }

   private resetModel(): void {
      this.model.algorithms = this.algorithms;
      this.model.regions = this.getRegionList();
      this.model.list = this.getTableList(this.model.selected);

      if(!this.model.selected &&
         this.model.selectedIndex >= this.model.list.length) {
         this.model.selectedIndex = -1;
      }
   }

   private sortRegions(): void {
      this.model.regions.sort((region1: any, region2: any) => {
         if(region1.label > region2.label) {
            return 1;
         }
         else if (region1.label < region2.label) {
            return -1;
         }
         else {
            return 0;
         }
      });
   }

   populateMappingDialogModel(): void {
      this.resetModel();
      this.sortRegions();
      this.uiContextService.setDefaultTab("geo-mapping.show-matched", this.model.selected + "");

      if(this.scrollableTableDirective) {
         this.scrollableTableDirective.setColumnWidths(false);
      }
   }

   getRegionList(): {label: string, data: number}[] {
      let arr: {label: string, data: number}[] = [];

      for(let key in this.unmatchedValues) {
         if(this.unmatchedValues.hasOwnProperty(key)) {
            let value = this.unmatchedValues[key];
            arr.push({label: key, data: value});
         }
      }

      return arr;
   }

   getTableList(autoIncluded: boolean = false): GeoMap[] {
      let arr: GeoMap[] = [];

      for(let key in this.manualMappings) {
         if(this.manualMappings.hasOwnProperty(key)) {
            let value = this.manualMappings[key];
            let code = this.getGeocode(value, this.matches);
            let geoMap: GeoMap = <GeoMap> {region: key, city: code};

            if(!this.contains0(arr, geoMap)) {
               arr.push(geoMap);
            }
         }
      }

      if(!autoIncluded || this.autoMappings == null) {
         return arr;
      }

      for(let key in this.autoMappings) {
         if(this.autoMappings.hasOwnProperty(key)) {
            let value = this.autoMappings[key];
            let geoMap: GeoMap = <GeoMap> {region: key, city: value.name};

            if(!this.contains0(arr, geoMap)) {
               arr.push(geoMap);
            }
         }
      }

      arr.sort((a, b) => a.region > b.region ? 1 : a.region == b.region ? 0 : -1);
      return arr;
   }

   private getGeocode(geoCode: string, matchs: MapFeature[]) {
      for(let i = 0; i < matchs.length; i++) {
         if(geoCode == matchs[i].geoCode) {
            return matchs[i].name;
         }
      }

      return null;
   }

   changeAlgorithm(): void {
      this.unmatchedListChange(this.model.currentSelection.region);
   }

   unmatchedListChange(region: {label: string, data: number}): void {
      this.model.currentSelection.region = region;

      if(region) {
         this.provider.getLikelyFeatures(region.data, this.algorithm).subscribe((data) => {
            let MapFeatures: MapFeature[] = data && !!data.body ? data.body : data;
            this.allfeatures = this.getAllfeatures(MapFeatures);
            this.model.cities = this.getFilteredFeatures();

            // large list causes browser to freeze (48183)
            if(this.model.cities.length > 5000) {
               this.model.cities = this.model.cities.slice(0, 5000);
            }
         });
      }
   }

   getAllfeatures(mapFeatures: MapFeature[]): {label: string, data: string}[] {
      let features: {label: string, data: string}[] = [];

      for(let mapFeature of mapFeatures) {
         features.push({label: mapFeature.name, data: mapFeature.geoCode});
      }

      return features;
   }

   getFilteredFeatures(): {label: string, data: string}[] {
      if(this.filter == null || this.filter == "") {
         return this.allfeatures;
      }

      let fstr: string = this.filter.trim().toLowerCase();

      if(fstr.length == 0) {
         return this.allfeatures;
      }

      let arr: {label: string, data: string}[] = [];

      if(!this.allfeatures) {
         return arr;
      }

      for(let feature of this.allfeatures) {
         if(feature.label.toLowerCase().indexOf(fstr) >= 0) {
            arr.push(feature);
         }
      }

      return arr;
   }

   filterFeatures(): void {
      this.model.cities = this.getFilteredFeatures();
   }

   add(): void {
      let str: string = this.model.currentSelection.region.label;
      let name: string = this.model.currentSelection.city.label;
      let geoCode: string = this.model.currentSelection.city.data;
      this.manualMappings[str] = geoCode;
      this.matches.push(<MapFeature> {name: name, geoCode: geoCode});

      // remove value from unmatchedValue
      if(this.unmatchedValues[str] >= 0) {
         delete this.unmatchedValues[str];
      }

      let sidx = this.model.regions.findIndex(v => v.label == str);
      this.model.cities = [];
      this.populateMappingDialogModel();
      sidx = sidx < this.model.regions.length ? sidx : this.model.regions.length - 1;
      let nregion = sidx >= 0 ? this.model.regions[sidx] : null;

      if(nregion) {
         this.unmatchedListChange(nregion);
      }
      else {
         this.model.currentSelection.region = null;
      }

      this.model.currentSelection.city = null;
      this.filter = "";
   }

   remove(): void {
      let selectGeoMap: GeoMap = this.model.list[this.model.selectedIndex];
      let str: string = selectGeoMap.region;

      for(let dupValue of this.dupValues) {
         if(dupValue == str) {
            ComponentTool.showMessageDialog(
               this.modalService, "_#(js:Warning)",
               "_#(js:viewer.viewsheet.map.dupNotSupported)");
            return;
         }
      }

      let auto: boolean = true;

      if(this.manualMappings[str]) {
         delete this.manualMappings[str];
         auto = false;
      }

      if(auto) {
         this.rautoMappings.push(str);
      }

      if(this.ounmatchedValues[str] >= 0) {
         this.unmatchedValues[str] = this.ounmatchedValues[str];
      }

      this.populateMappingDialogModel();
      this.model.currentSelection.city = null;
      this.model.selectedIndex = -1;
   }

   update(): void {
      this.chartGeoModel.option.mapping.algorithm = this.algorithm;
      this.chartGeoModel.option.mapping.mappings = this.manualMappings;
      this.checkDuplicateMapping();
   }

   checkDuplicateMapping(): void {
      let msize = Object.keys(this.manualMappings).length;

      if(msize == 0) {
         this.chartGeoModel.option.mapping.dupMapping = null;
      }

      let map = {};

      for(let name in this.manualMappings) {
         if(this.manualMappings.hasOwnProperty(name)) {
            let geoCode = this.manualMappings[name];
            let values = map[geoCode];

            if(!values) {
               values = [];
               map[geoCode] = values;
            }

            if(values.indexOf(name) == -1) {
               values.push(name);
            }
         }
      }

      for(let name in this.autoMappings) {
         if(this.autoMappings.hasOwnProperty(name)) {
            if(this.rautoMappings.indexOf(name) != -1) {
               continue;
            }

            let geoCode = this.autoMappings[name].geoCode;
            let values = map[geoCode];

            if(!values) {
               values = [];
               map[geoCode] = values;
            }

            if(values.indexOf(name) == -1) {
               values.push(name);
            }
         }
      }

      let dupMapping = {};

      for(let geoCode in map) {
         if(map.hasOwnProperty(geoCode)) {
            let values = map[geoCode];

            if(values.length > 1) {
               let name = this.getGeocode(geoCode, this.matches);
               dupMapping[name] = values;
            }
         }
      }

      this.chartGeoModel.option.mapping.dupMapping =
         <{ [key: string]: Array<string>} >dupMapping;
   }

   ok(evt: any): void {
      this.update();
      this.onCommit.emit(this.chartGeoModel);
      evt.stopPropagation();
   }

   cancel(evt: any): void {
      evt.stopPropagation();
      this.onCancel.emit("cancel");
   }

   /**
    * Check if the mappings contains the specified value.
    */
   private contains0(arr: GeoMap[], obj: GeoMap): boolean {
      for(let i = 0; i < arr.length; i++) {
         if(obj.region == arr[i].region) {
            return true;
         }
      }

      if(this.unmatchedValues[obj.region] >= 0) {
         return true;
      }

      return false;
   }

   get cshid(): string {
      return "EditGeographic";
   }
}
