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
import { GraphTypes } from "../../common/graph-types";
import { TailAxis } from "../../widget/tooltip/tooltip-tail-placement";

/** Types placed beside the mark, putting the tail on the box's left or right edge. Design-owned per-type choice. */
const HORIZONTAL_TAIL_TYPES = new Set<number>([
   GraphTypes.CHART_TREE,
   GraphTypes.CHART_NETWORK,
   GraphTypes.CHART_CIRCULAR,
   GraphTypes.CHART_TREEMAP,
   GraphTypes.CHART_ICICLE,
   GraphTypes.CHART_CIRCLE_PACKING,
   GraphTypes.CHART_MEKKO,
   GraphTypes.CHART_STEP,
   GraphTypes.CHART_JUMP
]);

export function tailAxisForChartType(chartType: number): TailAxis {
   return HORIZONTAL_TAIL_TYPES.has(chartType) ? "horizontal" : "vertical";
}
