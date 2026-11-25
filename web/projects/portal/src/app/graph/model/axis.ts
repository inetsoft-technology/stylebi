/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { ChartObject } from "./chart-object";
import { DrillLevel } from "../../composer/data/vs/drill-level";

export interface Axis extends ChartObject {
   axisType: string;
   sortOp: string;
   axisOps: string[];
   axisFields: string[];
   /** The date level of each drillable field ("Month"), "" for a field with none. */
   axisLevels?: string[];
   /** The date level each field's drill up goes to, "" if unknown or not possible. */
   axisUpLevels?: string[];
   /** The date level each field's drill down goes to, "" if unknown or not possible. */
   axisDownLevels?: string[];
   axisSizes: number[];
   secondary: boolean;
   sortField?: string;
   drillLevels?: DrillLevel[];
   drillFilter?: boolean;
   sortFieldIsCalc?: boolean;
}
