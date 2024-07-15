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
import { Rectangle } from "../../common/data/rectangle";
import { Axis } from "../../graph/model/axis";
import { Facet } from "../../graph/model/facet";
import { LegendContainer } from "../../graph/model/legend-container";
import { LegendOption } from "../../graph/model/legend-option";
import { Plot } from "../../graph/model/plot";
import { Title } from "../../graph/model/title";

export interface SetChartAreasCommand {
   invalid: boolean;
   initialWidthRatio: number;
   widthRatio: number;
   initialHeightRatio: number;
   heightRatio: number;
   resized: boolean;
   verticallyResizable: boolean;
   horizontallyResizable: boolean;
   maxHorizontalResize: number;
   maxVerticalResize: number;
   plot: Plot;
   titles: Title[];
   axes: Axis[];
   legends: LegendContainer[];
   facets: Facet[];
   legendsBounds: Rectangle;
   contentBounds: Rectangle;
   legendOption: LegendOption;
   stringDictionary?: string[];
   changedByScript?: boolean;
   completed?: boolean;
}
