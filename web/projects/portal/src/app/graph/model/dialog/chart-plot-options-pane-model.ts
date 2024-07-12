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
import { MapboxStyle } from "./mapbox-style";

export interface ChartPlotOptionsPaneModel {
   alpha: number;
   alphaEnabled: boolean;
   showValues: boolean;
   showValuesVisible: boolean;
   showValuesEnabled: boolean;
   stackValues: boolean;
   stackValuesVisible: boolean;
   stackValuesEnabled: boolean;
   showReferenceLine: boolean;
   showReferenceLineVisible: boolean;
   keepElementInPlot: boolean;
   keepElementInPlotVisible: boolean;
   showPoints: boolean;
   showPointsVisible: boolean;
   showPointsLabel: string;
   oneLine: boolean;
   bandingXColor: string;
   bandingXSize: number;
   bandingXVisible: boolean;
   bandingXColorEnabled: boolean;
   bandingXSizeEnabled: boolean;
   bandingYColor: string;
   bandingYSize: number;
   bandingYVisible: boolean;
   bandingYColorEnabled: boolean;
   bandingYSizeEnabled: boolean;
   backgroundColor: string;
   backgroundEnabled: boolean;
   lineTabVisible: boolean;
   explodedPie: boolean;
   explodedPieVisible: boolean;
   fillTimeVisible: boolean;
   fillTimeGap: boolean;
   fillZero: boolean;
   fillGapWithDash?: boolean;
   fillGapWithDashVisible?: boolean;
   polygonColor: boolean;
   polygonColorVisible: boolean;
   hasXDimension: boolean;
   hasYDimension: boolean;
   mapEmptyColor: string;
   mapEmptyColorVisible: boolean;
   borderColor: string;
   borderColorVisible: boolean;
   paretoLineColor: string;
   paretoLineColorVisible: boolean;
   webMap: boolean;
   webMapVisible: boolean;
   webMapStyle: string;
   mapboxStyles: MapboxStyle[];
   contourEnabled: boolean;
   contourLevels: number;
   contourBandwidth: number;
   contourEdgeAlpha: number;
   contourCellSize: number;
   includeParentLabels: boolean;
   includeParentLabelsVisible: boolean;
   applyAestheticsToSource?: boolean;
   applyAestheticsToSourceVisible?: boolean;
   wordCloud?: boolean;
   wordCloudFontScale?: number;
   mapPolygon?: boolean;
   pieRatio?: number;
}
