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
import { HyperlinkModel } from "../../common/data/hyperlink-model";
import { ChartModel } from "../../graph/model/chart-model";
import { VSFormatModel } from "./vs-format-model";
import { VSObjectModel } from "./vs-object-model";

export interface VSChartModel extends VSObjectModel, ChartModel {
   initialWidthRatio?: number;
   widthRatio?: number;
   initialHeightRatio?: number;
   heightRatio?: number;
   resized?: boolean;
   verticallyResizable?: boolean;
   horizontallyResizable?: boolean;
   maxHorizontalResize?: number;
   maxVerticalResize?: number;
   readonly hyperlinks: HyperlinkModel[];
   readonly showResizePreview: boolean;
   notAuto?: boolean;
   showPlotResizers?: boolean;
   titleFormat: VSFormatModel;
   title: string;
   titleVisible: boolean;
   titleSelected: boolean;
   multiSelect?: boolean;
   editedByWizard: boolean;
   paddingTop?: number;
   paddingLeft?: number;
   paddingBottom?: number;
   paddingRight?: number;
   axisFields: string[];
   plotHighlightEnabled: boolean;
   lastFlyover?: string;
   customPeriod: boolean;
   dateComparisonEnabled: boolean;
   dateComparisonDefined: boolean;
   appliedDateComparison: boolean;
   dateComparisonDescription: string;
   empty?: boolean;
   summarySortCol?: number;
   summarySortVal?: number;
   sendingFlyover?: boolean;
}
