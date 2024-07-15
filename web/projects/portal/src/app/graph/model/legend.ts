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
import { DrillLevel } from "../../composer/data/vs/drill-level";
import { ChartObject } from "./chart-object";

export interface Legend extends ChartObject {
   titleLabel: string;
   titleVisible: boolean;
   aestheticType: string;
   targetFields: string[];
   background?: string;
   field?: string;
   drillLevels?: DrillLevel[];
   drillFilter?: boolean;
   nodeAesthetic?: boolean;
}
