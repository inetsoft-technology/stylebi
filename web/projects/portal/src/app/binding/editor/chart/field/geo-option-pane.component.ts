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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { GeoProvider } from "../../../../common/data/geo-provider";
import { ComponentTool } from "../../../../common/util/component-tool";
import { ChartGeoRef } from "../../../data/chart/chart-geo-ref";
import { FeatureMappingInfo } from "../../../data/chart/feature-mapping-info";
import { GeoMappingDialog } from "./geo-mapping-dialog.component";

@Component({
   selector: "geo-option-pane",
   templateUrl: "geo-option-pane.component.html",
   styleUrls: ["geo-option-pane.component.scss"]
})
export class GeoOptionPane implements OnInit {
   @Input() mapVisible: boolean;
   @Input() layerVisible: boolean;
   @Input() mappingVisible: boolean;
   @Input() provider: GeoProvider;
   @Input() refName: string;
   @Input() layer: string = null;
   @Output() onLoadData: EventEmitter<string> = new EventEmitter<string>();
   allMapping: boolean;
   mapType: string;
   typeModel: Array<{ label: string, data: string }>;
   layerMode: Array<{ label: string, data: string }>;
   mappingMode: Array<{ label: string, data: FeatureMappingInfo }>;
   likelyFeatureUrl: string = "";
   loaded: boolean = false;
   private _loading: boolean = false;

   get loading(): boolean {
      return this._loading;
   }

   constructor(private dialogService: NgbModal) {
   }

   ngOnInit() {
      this.layer = this.layer || !this.layerVisible ?
         this.layer : this.provider.getChartGeoModel().option.layerValue;
      this.populateGeoData();
   }

   get chartGeoModel(): ChartGeoRef {
      return this.provider.getChartGeoModel();
   }

   private populateMappingStatus(): void {
      if(!this.chartGeoModel) {
         return;
      }

      this.provider.populateMappingStatus().subscribe((data) => {
         this.allMapping = data.body == "true";
      });
   }

   private populateGeoData(): void {
      if(this.chartGeoModel.option) {
         this.mapType = this.chartGeoModel.option.mapping.type;
      }
      else {
         this.mapType = this.provider.getBindingModel().mapType;
      }

      this._loading = true;

      this.provider.getGeoData().subscribe((data) => {
         this._loading = false;
         this.populateModels(data && !!data.body ? data.body : data);
         this.mapChange(this.mapType, true);

         if(this.chartGeoModel) {
            this.layerChange(this.chartGeoModel.option.layerValue);
         }
      },
      () => {
         this._loading = false;
      });
   }

   private populateModels(data: any): void {
      if (data.mappingStatus != undefined) {
         this.allMapping = <boolean> data.mappingStatus;
      }

      this.mapType = <string> data.type;
      this.typeModel = [];
      this.layerMode = [];
      this.mappingMode = [];

      let types: any = data.types;
      let layers: any = data.layers;
      let mappings: any = data.mappings;
      let flag = false;

      for(let type in types) {
         if(types.hasOwnProperty(type)) {
            let value: string = <string> types[type];
            this.typeModel.push({label: type, data: value});
         }
      }

      for(let layer in layers) {
         if(layers.hasOwnProperty(layer)) {
            let value: string = <string> layers[layer];
            this.layerMode.push({label: layer, data: value});

            if(value == data.layer && this.chartGeoModel) {
               this.chartGeoModel.option.layerValue = value;
               flag = true;
            }
         }
      }

      if(!flag && this.chartGeoModel && this.layerMode.length != 0) {
         this.chartGeoModel.option.layerValue = this.layerMode[0].data;
      }

      flag = false;
      // save and restore manual mapping
      const omappings = this.chartGeoModel && this.chartGeoModel.option ?
         this.chartGeoModel.option.mapping.mappings : null;

      for(let mapping in mappings) {
         if(mappings.hasOwnProperty(mapping)) {
            let value: FeatureMappingInfo = <FeatureMappingInfo> mappings[mapping];
            this.mappingMode.push({label: mapping, data: value});

            if(this.chartGeoModel && this.chartGeoModel.option.mapping &&
               value.id == this.chartGeoModel.option.mapping.id) {
               this.chartGeoModel.option.mapping = value;
               flag = true;
            }
         }
      }

      if(!flag && this.chartGeoModel && this.mappingMode.length != 0) {
         this.chartGeoModel.option.mapping = this.mappingMode[0].data;
      }

      if(omappings) {
         this.chartGeoModel.option.mapping.mappings = omappings;
      }

      if(!this.loaded) {
         this.onLoadData.emit("");
      }
   }

   mapChange(type: string, isPopulate: boolean = false): void {
      if(!!this.chartGeoModel) {
         if(this.chartGeoModel.option) {
            this.chartGeoModel.option.mapping.type = type;
         }
         else {
            this.provider.getBindingModel().mapType = type;
         }

         this.changeMapType(this.refName, type, isPopulate ? this.layer : null);
      }
   }

   layerChange(layer: string): void {
      if(!!this.chartGeoModel) {
         this.changeMapType(this.refName, this.mapType, this.layer = layer);
      }
   }

   private changeMapType(refName: string, type: string, layer?: string): void {
      this.provider.changeMapType(refName, type, layer).subscribe((data) => {
         this.populateModels(!!data.body ? data.body : data);
         this.populateMappingStatus();
      });
   }

   mappingChange(mapping: FeatureMappingInfo): void {
      this.chartGeoModel.option.mapping = mapping;

      this.provider.getMappingData().subscribe((data: any) => {
         data = data && !!data.body ? data.body : data;
         let smapping = data.smapping;

         if(smapping) {
            this.chartGeoModel.option.mapping.mappings = smapping.mappings;
         }

         this.populateMappingStatus();
      });
   }

   showGeoMappingDialog(): void {
      let dialog: GeoMappingDialog = ComponentTool.showDialog(
         this.dialogService, GeoMappingDialog, (result: any) => {
            this.chartGeoModel.option.mapping = result.option.mapping;
            this.populateMappingStatus();
         }, {windowClass: "geo-mapping-dialog"} );

      dialog.provider = this.provider;
   }
}
