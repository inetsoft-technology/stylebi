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
import {
   ColorFrameModel,
   LineFrameModel,
   ShapeFrameModel,
   SizeFrameModel,
   TextureFrameModel
} from "../../../common/data/visual-frame-model";
import { BAggregateRef } from "../b-aggregate-ref";
import { AestheticInfo } from "./aesthetic-info";
import { CalculateInfo } from "./calculate-info";
import { ChartAestheticModel } from "./chart-binding-model";
import { ChartRef } from "../../../common/data/chart-ref";
import { OriginalDescriptor } from "../../../common/data/original-descriptor";

export class ChartAggregateRef extends BAggregateRef implements ChartRef, ChartAestheticModel {
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
   discrete: boolean;
   summaryColorFrame: ColorFrameModel;
   summaryTextureFrame: TextureFrameModel;
   calculateInfo: CalculateInfo;
   buildInCalcs: CalculateInfo[];
   secondaryY: boolean;
   secondaryAxisSupported: boolean;
   aggregated: boolean;
   oriFullName: string;
   oriView: string;
   refConvertEnabled: boolean;
   classType: string;
   measure: boolean;
   defaultFormula: string;
   original: OriginalDescriptor;
}
