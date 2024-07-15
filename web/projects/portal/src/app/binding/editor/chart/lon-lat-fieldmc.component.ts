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
import { Component, Input } from "@angular/core";
import { GraphTypes } from "../../../common/graph-types";
import { ChartBindingModel } from "../../data/chart/chart-binding-model";
import { ChartRef } from "../../../common/data/chart-ref";

@Component({
   selector: "lon-lat-fieldmc",
   templateUrl: "lon-lat-fieldmc.component.html",
   styleUrls: ["../data-editor.component.scss"]
})
export class LonLatFieldmc {
   @Input() fieldType: string;
   @Input() bindingModel: ChartBindingModel;

   isMapChart(): boolean {
      return GraphTypes.isGeo(this.bindingModel.chartType);
   }

   get geoLabel(): string {
      let label: string = "";
      let fields: Array<ChartRef> = this.bindingModel.geoFields;
      if(fields.length == 0) {
         return "";
      }

      for(let i: number = 0; i < fields.length; i++) {
         let ref: ChartRef = fields[i];
         let caption = ref.caption;
         caption = caption == null || caption.length == 0 ? ref.columnValue : caption;

         label += caption ? caption : ref.name;

         if(i < fields.length - 1) {
            label += ", ";
         }
      }

      if(this.fieldType == "xfields") {
         return "Longitude(" + label + ")";
      }
      else if(this.fieldType == "yfields") {
         return "Latitude(" + label + ")";
      }
      else {
         return "";
      }
   }
}
