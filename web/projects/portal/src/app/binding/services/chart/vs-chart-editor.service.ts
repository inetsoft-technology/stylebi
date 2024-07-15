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
import { Observable } from "rxjs";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { ChartRef } from "../../../common/data/chart-ref";
import { DataRefType } from "../../../common/data/data-ref-type";
import { DndService } from "../../../common/dnd/dnd.service";
import { GraphTypes } from "../../../common/graph-types";
import { UIContextService } from "../../../common/services/ui-context.service";
import { ViewsheetClientService } from "../../../common/viewsheet-client";
import { ModelService } from "../../../widget/services/model.service";
import { ChartBindingModel } from "../../data/chart/chart-binding-model";
import { ChartGeoRef } from "../../data/chart/chart-geo-ref";
import { ChangeChartRefEvent } from "../../event/change-chart-ref-event";
import { ChangeChartTypeEvent } from "../../event/change-chart-type-event";
import { ChangeSeparateStatusEvent } from "../../event/change-separate-status-event";
import { GraphUtil } from "../../util/graph-util";
import { BindingService } from "../binding.service";
import { ChartEditorService } from "./chart-editor.service";
import { XSchema } from "../../../common/data/xschema";

export class VSChartEditorService extends ChartEditorService {
   constructor(bindingService: BindingService,
               protected modelService: ModelService,
               private clientService: ViewsheetClientService,
               protected uiContextService: UIContextService)
   {
      super(bindingService, modelService, uiContextService);
   }

   changeSeparateStatus(bindingModel: ChartBindingModel): void {
      let name: string = this.bindingService.assemblyName;
      let event: ChangeSeparateStatusEvent = new ChangeSeparateStatusEvent(name,
         bindingModel.multiStyles, !bindingModel.separated);

      this.clientService.sendEvent("/events/vs/chart/changeSeparateStatus", event);
   }

   swapXYBinding(): void {
      let name: string = this.bindingService.assemblyName;
      let event: ChangeSeparateStatusEvent = new ChangeSeparateStatusEvent(name,
         this.bindingModel.multiStyles, this.bindingModel.separated);

      this.clientService.sendEvent("/events/vs/chart/swapXYBinding", event);
   }

   /**
    * convert measure to dimension or convert dimension to measure
    */
   convert(fieldName: string, convertType: number, model: any): void {
   }

   changeChartType(chartType: number, multiStyles: boolean,
                   stackMeasures: boolean, refName: string): void
   {
      let name: string = this.bindingService.assemblyName;
      let event: ChangeChartTypeEvent = new ChangeChartTypeEvent(name, chartType,
         multiStyles, stackMeasures, this.bindingModel.separated, refName);

      this.clientService.sendEvent("/events/vs/chart/changeChartType", event);
   }

   populateMappingStatus(chartGeoModel: ChartGeoRef): Observable<any> {
      return this.modelService.putModel("../api/composer/getMappingStatus",
         this.bindingService.getURLParams());
   }

   getGeoData(chartGeoModel: ChartGeoRef, mapType: string): Observable<any> {
      let params = this.bindingService.getURLParams()
                     .set("refName", chartGeoModel.fullName || chartGeoModel.name);

      return this.modelService.getModel("../api/composer/getGeoData", params);
   }

   changeMapType(refName: string, mapType: string, layer: string): Observable<any> {
      let params = this.bindingService.getURLParams()
                     .set("refName", refName)
                     .set("type", mapType);

      if(layer) {
         params = params.set("layer", layer);
      }

      return this.modelService.getModel("../api/composer/changeMapType", params);
   }

   getMappingData(chartGeoModel: ChartGeoRef): Observable<any> {
      return this.modelService.putModel("../api/composer/getMappingData", chartGeoModel);
   }

   changeChartRef(ref: ChartRef, fieldType: string): void {
      let name: string = this.bindingService.assemblyName;
      let event: ChangeChartRefEvent = new ChangeChartRefEvent(name,
         ref ? ref.original : null, this.bindingModel, fieldType);

      this.clientService.sendEvent("/events/vs/chart/changeChartRef", event);
   }

   changeChartAesthetic(fieldType: string): void {
      let name: string = this.bindingService.assemblyName;
      let event: ChangeChartRefEvent = new ChangeChartRefEvent(name, null, this.bindingModel, fieldType);

      this.clientService.sendEvent("/events/vs/chart/changeChartAesthetic", event);
   }

   isDropPaneAccept(dservice: DndService, bindingModel: ChartBindingModel,
                    fieldType: string, chartType?: number): boolean
   {
      const trans: any = dservice.getTransfer();
      let dropType: string = fieldType;

      if(!trans || Object.keys(trans).length == 0) {
         return false;
      }

      let values: any = trans.column;
      values = !!values ? values : trans.dragSource && trans.dragSource.refs;

      if(!values) {
         return false;
      }

      for(let i = 0; i < values.length; i++) {
         if(!values[i]) {
            return false;
         }

         let val: any = values[i];
         let isDimension: boolean = true;

         // from tree, val is assetentry.
         if(val && val.path) {
            const entry = val as AssetEntry;
            const pathStr = entry.path.split("/");
            isDimension = pathStr.some(p => p == "_#(js:Dimensions)" || p == "_#(js:Dimension)");

            if(!isDimension && entry.properties != null) {
               isDimension = entry.properties["refType"] === `${DataRefType.DIMENSION}`;
            }
         }
         // from other region, val is chartref.
         else {
            isDimension = GraphUtil.isDimension(val);
         }

         if(!this.isFieldAcceptable(bindingModel, dropType, isDimension, val, chartType)) {
            return false;
         }
      }

      return true;
   }

   /**
    * Return if the field is drop acceptable by the target drop region.
    * @param  {ChartBindingModel} binding     the chart binding model.
    * @param  {string}            dropType    the drop region name.
    * @param  {boolean}           isDimension if drag field is dimension.
    * @param  {any}               val   the drag content from tree or other regions.
    * @return {boolean}                       true is acceptable, else false.
    */
   private isFieldAcceptable(binding: ChartBindingModel, dropType: string,
                             isDimension: boolean, val: any, chartType0?: number): boolean
   {
      if(binding == null || val == null) {
         return false;
      }

      const chartType = chartType0 || binding.chartType;

      if(GraphTypes.isGeo(chartType) &&
         (dropType == "xfields" || dropType == "yfields"))
      {
         return this.checkGeoColDropXYValid(isDimension, val);
      }

      if(GraphTypes.isMekko(chartType)) {
         if(dropType == "xfields" || dropType == "groupfields") {
            return isDimension;
         }

         if(dropType == "yfields") {
            return !isDimension;
         }
      }

      if((GraphTypes.isTreemap(chartType) || GraphTypes.isRelation(chartType) ||
          GraphTypes.isGantt(chartType)) &&
         (dropType == "xfields" || dropType == "yfields" || dropType == "groupfields"))
      {
         return isDimension;
      }

      if(GraphTypes.isInterval(chartType) && dropType == "size") {
         return !isDimension;
      }

      if(dropType == "xfields") {
         return this.isXPaneAccept(isDimension, binding);
      }
      else if(dropType == "yfields") {
         return this.isYPaneAccept(isDimension, chartType);
      }
      else if(dropType == "geofields") {
         return this.isGeoPaneAccept(val, isDimension, binding);
      }
      else if(dropType == "high" || dropType == "low" ||
              dropType == "open" || dropType == "close")
      {
         return !isDimension;
      }
      else if(dropType == "start" || dropType == "end" || dropType == "milestone") {
         return !isDimension && this.isDateMeasure(val, isDimension);
      }
      else if(dropType == "source" || dropType == "target") {
         return isDimension;
      }

      return true;
   }

   private isDateMeasure(entry: any, isDimension: boolean): boolean {
      return !isDimension &&
         (entry?.properties && XSchema.isDateType(entry.properties["dtype"]) ||
            entry.originalDataType && XSchema.isDateType(entry.originalDataType));
   }

   private isXPaneAccept(isDimension: boolean, bindingModel: ChartBindingModel): boolean {
      return isDimension || GraphUtil.supportsInvertedChart(bindingModel);
   }

   private isYPaneAccept(isDimension: boolean, chartType: number): boolean {
      return isDimension || !GraphTypes.isCandle(chartType) && !GraphTypes.isStock(chartType) &&
         !GraphTypes.isTreemap(chartType) && !GraphTypes.isFunnel(chartType);
   }

   private isGeoPaneAccept(entry: any, isDimension: boolean,
                           bindingModel: ChartBindingModel): boolean
   {
      if(!isDimension || !entry) {
         return false;
      }

      if(bindingModel.geoCols.some(c => c.name == entry.name)) {
         return true;
      }
      else if(entry.properties) {
         return entry.properties["isGeo"] === "true";
      }
      else if(!entry.option || entry.option.mapping && !entry.option.layerValue) {
         return false;
      }

      return true;
   }

   private checkGeoColDropXYValid(isDimension: boolean, entry: any): boolean  {
      if(isDimension) {
         return true;
      }

      const colName = entry ? entry.properties ? entry.properties.attribute : entry.name : null;
      return !(colName != null && this.findGeoColByName(colName) == null);
   }
}
