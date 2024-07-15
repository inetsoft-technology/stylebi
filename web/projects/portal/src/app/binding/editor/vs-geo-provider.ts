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
import { HttpParams } from "@angular/common/http";
import { Observable } from "rxjs";
import { GeoProvider } from "../../common/data/geo-provider";
import { ModelService } from "../../widget/services/model.service";
import { ChartBindingModel } from "../data/chart/chart-binding-model";
import { ChartGeoRef } from "../data/chart/chart-geo-ref";

export class VSGeoProvider implements GeoProvider {
   constructor(private _bindingModel: ChartBindingModel, private params: HttpParams,
      private geoModel: ChartGeoRef, private modelService: ModelService,
      private refName: string)
   {
   }

   getChartGeoModel(): ChartGeoRef {
      return this.geoModel;
   }

   getBindingModel(): ChartBindingModel {
      return this._bindingModel;
   }

   populateMappingStatus(): Observable<any> {
      const param = this.params.set("refName", this.refName);
      return this.modelService.putModel("../api/composer/getMappingStatus",
         this.getChartGeoModel().option.mapping, param);
   }

   getGeoData(): Observable<any> {
      const param = this.params.set("refName", this.refName);
      return this.modelService.getModel("../api/composer/getGeoData", param);
   }

   changeMapType(refName: string, mapType: string, layer: string): Observable<any> {
      let param = this.params
         .set("refName", refName)
         .set("type", mapType);

      if(layer) {
         param = param.set("layer", layer);
      }
      else {
         this.getBindingModel().mapType = mapType;
      }

      return this.modelService.getModel("../api/composer/changeMapType", param);
   }

   getLikelyFeatures(row: number, algorithm: string): Observable<any> {
      const param = this.params
         .set("row", row + "")
         .set("algorithm", algorithm);

      return this.modelService.putModel("../api/composer/getLikelyFeatures",
         this.getChartGeoModel(), param);
   }

   getMappingData(): Observable<any> {
      return this.modelService.putModel("../api/composer/getMappingData",
         this.getChartGeoModel(), this.params);
   }
}
