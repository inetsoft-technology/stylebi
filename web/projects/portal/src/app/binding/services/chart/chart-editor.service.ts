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
import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { publishReplay, refCount } from "rxjs/operators";
import { ChartRef } from "../../../common/data/chart-ref";
import { DataRef } from "../../../common/data/data-ref";
import { DndService } from "../../../common/dnd/dnd.service";
import { UIContextService } from "../../../common/services/ui-context.service";
import { ChartConstants } from "../../../common/util/chart-constants";
import { ModelService } from "../../../widget/services/model.service";
import { ChartBindingModel } from "../../data/chart/chart-binding-model";
import { ChartGeoRef } from "../../data/chart/chart-geo-ref";
import { SourceInfo } from "../../data/source-info";
import { SourceInfoType } from "../../data/source-info-type";
import { BindingService } from "../binding.service";
import { ChartStylesModel } from "../../widget/chart-style-pane.component";

@Injectable()
export abstract class ChartEditorService {
   // true if the current format is from the text field on aesthetic pane
   public textFormat: boolean;
   private _measureName: string;
   private imageShapes: Observable<string[]>;
   private customChartTypes: Observable<string[]>;
   private customChartFrames: Observable<string[]>;
   private chartStyles: Observable<ChartStylesModel>;

   constructor(public bindingService: BindingService,
               protected modelService: ModelService,
               protected uiContextService: UIContextService)
   {
   }

   getChartStyles(): Observable<ChartStylesModel> {
      if(!this.chartStyles) {
         this.chartStyles = this.modelService.getModel<ChartStylesModel>("../api/chart/getAvailableChartStyles")
            .pipe(
               publishReplay(1),
               refCount()
            );
      }

      return this.chartStyles;
   }

   getCustomChartTypes(): Observable<string[]> {
      if(!this.customChartTypes) {
         this.customChartTypes = this.modelService.getModel<string[]>("../api/composer/customChartTypes").pipe(
            publishReplay(1),
            refCount()
         );
      }

      return this.customChartTypes;
   }

   getCustomChartFrames(): Observable<string[]> {
      if(!this.customChartFrames) {
         this.customChartFrames = this.modelService.getModel<string[]>("../api/composer/customChartFrames").pipe(
            publishReplay(1),
            refCount()
         );
      }

      return this.customChartFrames;
   }

   set measureName(_measureName: string) {
      this._measureName = _measureName;
   }

   get measureName(): string {
      return this._measureName;
   }

   get bindingModel(): ChartBindingModel {
      return <ChartBindingModel> this.bindingService.getBindingModel();
   }

   findGeoColByName(name: string): DataRef {
      let refs: DataRef[] = this.bindingModel.geoCols;

      if(!refs) {
         return null;
      }

      for(let ref of refs) {
         if(ref.name === name) {
            return ref;
         }
      }

      return null;
   }

   updateSourceInfo(name: string): void {
      let source: SourceInfo = <SourceInfo> {
         type: SourceInfoType.ASSET,
         prefix: "",
         source: name
      };

      this.bindingModel.source = source;
   }

   getDNDType(fieldType: string): number {
      if(fieldType == "xfields") {
         return ChartConstants.DROP_REGION_X;
      }
      else if(fieldType == "yfields") {
         return ChartConstants.DROP_REGION_Y;
      }
      else if(fieldType == "color") {
         return ChartConstants.DROP_REGION_COLOR;
      }
      else if(fieldType == "shape") {
         return ChartConstants.DROP_REGION_SHAPE;
      }
      else if(fieldType == "size") {
         return ChartConstants.DROP_REGION_SIZE;
      }
      else if(fieldType == "text") {
         return ChartConstants.DROP_REGION_TEXT;
      }
      else if(fieldType == "high") {
         return ChartConstants.DROP_REGION_HIGH;
      }
      else if(fieldType == "low") {
         return ChartConstants.DROP_REGION_LOW;
      }
      else if(fieldType == "open") {
         return ChartConstants.DROP_REGION_OPEN;
      }
      else if(fieldType == "close") {
         return ChartConstants.DROP_REGION_CLOSE;
      }
      else if(fieldType == "groupfields") {
         return ChartConstants.DROP_REGION_GROUP;
      }
      else if(fieldType == "path") {
         return ChartConstants.DROP_REGION_PATH;
      }
      else if(fieldType == "geofields") {
         return ChartConstants.DROP_REGION_GEO;
      }
      else if(fieldType == "source") {
         return ChartConstants.DROP_REGION_SOURCE;
      }
      else if(fieldType == "target") {
         return ChartConstants.DROP_REGION_TARGET;
      }
      else if(fieldType == "start") {
         return ChartConstants.DROP_REGION_START;
      }
      else if(fieldType == "end") {
         return ChartConstants.DROP_REGION_END;
      }
      else if(fieldType == "milestone") {
         return ChartConstants.DROP_REGION_MILESTONE;
      }

      return -1;
   }

   abstract convert(fieldName: string, convertType: number, model: any): void;
   abstract changeSeparateStatus(bindingModel: any): void;
   abstract swapXYBinding(): void;
   abstract changeChartType(chartType: number, multiStyles: boolean, stackMeasures: boolean,
      refName: string): void;
   abstract populateMappingStatus(geo: ChartGeoRef): Observable<any>;
   abstract getGeoData(chartGeoModel: ChartGeoRef, mapType: string): Observable<any>;
   abstract changeMapType(refName: string, mapType: string,
                          layer: string): Observable<any>;
   abstract getMappingData(chartGeoModel: ChartGeoRef): Observable<any>;
   abstract changeChartRef(ref: ChartRef, fieldType: string): void;
   abstract changeChartAesthetic(fieldType: string): void;
   abstract isDropPaneAccept(dservice: DndService, bindingModel: ChartBindingModel,
                             fieldType: string, chartType?: number): boolean;
}
