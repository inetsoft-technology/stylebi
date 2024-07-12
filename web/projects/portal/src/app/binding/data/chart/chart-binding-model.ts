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
import { DataRef } from "../../../common/data/data-ref";
import {
   ColorFrameModel,
   LineFrameModel,
   ShapeFrameModel,
   SizeFrameModel,
   TextureFrameModel
} from "../../../common/data/visual-frame-model";
import { AestheticInfo } from "./aesthetic-info";
import { AllChartAggregateRef } from "./all-chart-aggregate-ref";
import { ChartRef } from "../../../common/data/chart-ref";
import { BindingModel } from "../binding-model";
import { DateComparableModel } from "../date-comparable-model";

export interface ChartAestheticModel {
   chartType: number;
   rtchartType: number;
   colorField: AestheticInfo;
   shapeField: AestheticInfo;
   sizeField: AestheticInfo;
   textField: AestheticInfo;
   colorFrame: ColorFrameModel;
   shapeFrame: ShapeFrameModel;
   lineFrame: LineFrameModel;
   textureFrame: TextureFrameModel;
   sizeFrame: SizeFrameModel;
}

export class ChartBindingModel extends BindingModel implements ChartAestheticModel, DateComparableModel {
   chartType: number;
   rtchartType: number;
   colorField: AestheticInfo;
   shapeField: AestheticInfo;
   sizeField: AestheticInfo;
   textField: AestheticInfo;
   colorFrame: ColorFrameModel;
   shapeFrame: ShapeFrameModel;
   lineFrame: LineFrameModel;
   textureFrame: TextureFrameModel;
   sizeFrame: SizeFrameModel;
   waterfall: boolean;
   multiStyles: boolean;
   separated: boolean;
   mapType: string;
   allChartAggregate: AllChartAggregateRef;
   xfields: ChartRef[];
   yfields: ChartRef[];
   geoFields: ChartRef[];
   groupFields: ChartRef[];
   supportsGroupFields: boolean;
   openField: ChartRef;
   closeField: ChartRef;
   highField: ChartRef;
   lowField: ChartRef;
   pathField: ChartRef;
   sourceField?: ChartRef;
   targetField?: ChartRef;
   startField?: ChartRef;
   endField?: ChartRef;
   milestoneField?: ChartRef;
   supportsPathField: boolean;
   pointLine: boolean;
   geoCols: DataRef[];
   stackMeasures: boolean;
   hasDateComparison: boolean;
   // relation chart
   nodeColorField: AestheticInfo;
   nodeSizeField: AestheticInfo;
   nodeColorFrame?: ColorFrameModel;
   nodeSizeFrame?: SizeFrameModel;
   wordCloud?: boolean;
}
