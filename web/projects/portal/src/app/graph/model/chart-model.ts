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
import { Subject } from "rxjs";
import { FormatInfoModel } from "../../common/data/format-info-model";
import { Axis } from "./axis";
import { ChartSelection } from "./chart-selection";
import { Facet } from "./facet";
import { LegendContainer } from "./legend-container";
import { LegendOption } from "./legend-option";
import { Plot } from "./plot";
import { Title } from "./title";
import { RegionMeta } from "./region-meta";
import { Rectangle } from "../../common/data/rectangle";

export interface ChartModel {
   maxMode: boolean;
   brushed: boolean;
   zoomed: boolean;
   hasFlyovers: boolean;
   flyOnClick: boolean;
   axes: Axis[];
   facets: Facet[];
   legends: LegendContainer[];
   plot: Plot;
   titles: Title[];
   stringDictionary: string[];
   regionMetaDictionary: RegionMeta[];
   genTime: number;
   axisHidden: boolean;
   titleHidden: boolean;
   legendHidden: boolean;
   legendOption: LegendOption;
   chartSelection: ChartSelection;
   legendsBounds: Rectangle;
   contentBounds: Rectangle;
   chartType: number;
   multiStyles: boolean;
   enableAdhoc: boolean;
   clearCanvasSubject: Subject<any>;
   showValues: boolean;
   invalid?: boolean;
   changedByScript?: boolean;
   hasLegend?: boolean;
   readonly plotHighlightEnabled?: boolean;
   scatterMatrix?: boolean;
   wordCloud: boolean;
   webMap?: boolean;
   navEnabled?: boolean;
   noData?: boolean;
   errorFormat?: FormatInfoModel;
   readonly mapInfo?: boolean;
}
