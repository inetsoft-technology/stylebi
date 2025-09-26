/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import { GraphTypes } from "../../../common/graph-types";
import { XConstants } from "../../../common/util/xconstants";

const DATE_LEVEL_MAP: Map<number, string> = new Map<number, string>([
   [XConstants.YEAR_DATE_GROUP, "Year"],
   [XConstants.QUARTER_DATE_GROUP, "Quarter"],
   [XConstants.MONTH_DATE_GROUP, "Month"],
   [XConstants.WEEK_DATE_GROUP, "Week"],
   [XConstants.DAY_DATE_GROUP, "Day"],
   [XConstants.QUARTER_OF_YEAR_DATE_GROUP, "Quarter of Year"],
   [XConstants.MONTH_OF_YEAR_DATE_GROUP, "Month of Year"],
   [XConstants.WEEK_OF_YEAR_DATE_GROUP, "Week of Year"],
   [XConstants.DAY_OF_MONTH_DATE_GROUP, "Day of Month"],
   [XConstants.DAY_OF_WEEK_DATE_GROUP, "Day of Week"],
   [XConstants.NONE_DATE_GROUP, "None"]
]);

const TIME_LEVEL_MAP: Map<number, string> = new Map<number, string>([
   [XConstants.HOUR_DATE_GROUP, "Hour"],
   [XConstants.MINUTE_DATE_GROUP, "Minute"],
   [XConstants.SECOND_DATE_GROUP, "Second"],
   [XConstants.HOUR_OF_DAY_DATE_GROUP, "Hour of Day"],
   [XConstants.MINUTE_OF_HOUR_DATE_GROUP, "Minute of Hour"],
   [XConstants.SECOND_OF_MINUTE_DATE_GROUP, "Second of Minute"],
   [XConstants.NONE_DATE_GROUP, "None"]
]);

const TIME_INSTANT_LEVEL_MAP: Map<number, string> = new Map<number, string>([
   [XConstants.YEAR_DATE_GROUP, "Year"],
   [XConstants.QUARTER_DATE_GROUP, "Quarter"],
   [XConstants.MONTH_DATE_GROUP, "Month"],
   [XConstants.WEEK_DATE_GROUP, "Week"],
   [XConstants.DAY_DATE_GROUP, "Day"],
   [XConstants.HOUR_DATE_GROUP, "Hour"],
   [XConstants.MINUTE_DATE_GROUP, "Minute"],
   [XConstants.SECOND_DATE_GROUP, "Second"],
   [XConstants.QUARTER_OF_YEAR_DATE_GROUP, "Quarter of Year"],
   [XConstants.MONTH_OF_YEAR_DATE_GROUP, "Month of Year"],
   [XConstants.WEEK_OF_YEAR_DATE_GROUP, "Week of Year"],
   [XConstants.DAY_OF_MONTH_DATE_GROUP, "Day of Month"],
   [XConstants.DAY_OF_WEEK_DATE_GROUP, "Day of Week"],
   [XConstants.HOUR_OF_DAY_DATE_GROUP, "Hour of Day"],
   [XConstants.MINUTE_OF_HOUR_DATE_GROUP, "Minute of Hour"],
   [XConstants.SECOND_OF_MINUTE_DATE_GROUP, "Second of Minute"],
   [XConstants.NONE_DATE_GROUP, "None"]
]);

const ALL_DATE_LEVEL_MAP: Map<number, string> = new Map<number, string>([
   ...DATE_LEVEL_MAP,
   ...TIME_LEVEL_MAP,
   ...TIME_INSTANT_LEVEL_MAP
]);

const CHART_TYPE_MAP: Map<any, string> = new Map([
   [GraphTypes.CHART_AUTO, "Auto"],
   [GraphTypes.CHART_BAR, "Bar"],
   [GraphTypes.CHART_3D_BAR, "3D Bar"],
   [GraphTypes.CHART_LINE, "Line"],
   [GraphTypes.CHART_STEP, "Step Line"],
   [GraphTypes.CHART_STEP_STACK, "Stack Step Line"],
   [GraphTypes.CHART_JUMP, "Jump"],
   [GraphTypes.CHART_STEP_AREA, "Step Area"],
   [GraphTypes.CHART_STEP_AREA_STACK, "Stack Step Area"],
   [GraphTypes.CHART_AREA, "Area"],
   [GraphTypes.CHART_POINT, "Point"],
   [GraphTypes.CHART_POINT_STACK, "Stack Point"],
   [GraphTypes.CHART_BAR_STACK, "Stack Bar"],
   [GraphTypes.CHART_3D_BAR_STACK, "3D Stack Bar"],
   [GraphTypes.CHART_LINE_STACK, "Stack Line"],
   [GraphTypes.CHART_AREA_STACK, "Stack Area"],
   [GraphTypes.CHART_PIE, "Pie"],
   [GraphTypes.CHART_DONUT, "Donut"],
   [GraphTypes.CHART_TREEMAP, "Treemap"],
   [GraphTypes.CHART_SUNBURST, "Sunburst"],
   [GraphTypes.CHART_CIRCLE_PACKING, "Circle Packing"],
   [GraphTypes.CHART_ICICLE, "Icicle"],
   [GraphTypes.CHART_3D_PIE, "3D Pie"],
   [GraphTypes.CHART_RADAR, "Radar"],
   [GraphTypes.CHART_FILL_RADAR, "Filled Radar"],
   [GraphTypes.CHART_STOCK, "Stock"],
   [GraphTypes.CHART_CANDLE, "Candle"],
   [GraphTypes.CHART_BOXPLOT, "Box Plot"],
   [GraphTypes.CHART_MEKKO, "Marimekko"],
   [GraphTypes.CHART_TREE, "Tree"],
   [GraphTypes.CHART_NETWORK, "Network"],
   [GraphTypes.CHART_CIRCULAR, "Circular Network"],
   [GraphTypes.CHART_FUNNEL, "Funnel"],
   [GraphTypes.CHART_GANTT, "Gantt"],
   [GraphTypes.CHART_WATERFALL, "Waterfall"],
   [GraphTypes.CHART_PARETO, "Pareto"],
   [GraphTypes.CHART_MAP, "Map"],
   [GraphTypes.CHART_INTERVAL, "Interval"],
   [GraphTypes.CHART_SCATTER_CONTOUR, "Scatter Contour"],
   [GraphTypes.CHART_MAP_CONTOUR, "Contour Map"]
]);

export function getGroupOptionLabel(option: string): string | undefined {
   if(!option) {
      return "";
   }

   let level = parseInt(option);
   return ALL_DATE_LEVEL_MAP.get(level);
}

export function getChartLabel(chartType: any): string | undefined {
   return CHART_TYPE_MAP.get(chartType);
}

export function getOrderDirection(order: number): string {
   if(order == XConstants.SORT_ASC) {
      return "asc";
   }
   else if(order == XConstants.SORT_DESC) {
      return "desc";
   }
   else if(order == XConstants.SORT_ORIGINAL) {
      return "original";
   }
   else if(order == XConstants.SORT_SPECIFIC) {
      return "specific";
   }
   else if(order == XConstants.SORT_VALUE_ASC) {
      return "asc by measure";
   }
   else if(order == XConstants.SORT_VALUE_DESC) {
      return "desc by measure";
   }

   return "";
}