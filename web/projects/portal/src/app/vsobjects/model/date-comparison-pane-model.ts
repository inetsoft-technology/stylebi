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
import { IntervalPaneModel } from "./interval-pane-model";
import { PeriodPaneModel } from "./period-pane-model";
import { VisualFrameModel } from "../../common/data/visual-frame-model";

export interface DateComparisonPaneModel {
   periodPaneModel: PeriodPaneModel;
   intervalPaneModel: IntervalPaneModel;
   comparisonOption: number;
   useFacet: boolean;
   onlyShowMostRecentDate: boolean;
   visualFrameModel: VisualFrameModel;
}

export const enum ComparisonOption {
   PERCENT = 1,
   CHANGE = 2,
   VALUE = 6,
   CHANGE_VALUE = 101,
   PERCENT_VALUE = 102
}
