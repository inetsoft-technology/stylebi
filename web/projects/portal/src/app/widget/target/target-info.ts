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

import { CategoricalColorModel } from "../../common/data/visual-frame-model";

export interface TargetInfo {
   measure: MeasureInfo;
   fieldLabel: string;
   genericLabel: string ;
   value: string;
   label: string;
   toValue: string;
   toLabel: string;
   labelFormats: string;
   lineStyle: number;
   lineColor: ColorInfo;
   fillAboveColor: ColorInfo;
   fillBelowColor: ColorInfo;
   alpha: string;
   fillBandColor: ColorInfo;
   chartScope: boolean;
   index: number;
   tabFlag: number;
   changed: boolean;
   targetString: string;
   strategyInfo: StrategyInfo;
   bandFill: CategoricalColorModel;
   supportFill: boolean;
}

export interface MeasureInfo {
   name: string;
   label: string;
   dateField: boolean;
   timeField?: boolean;
   groupOthers?: boolean;
}

export interface StrategyInfo {
   name: string;
   label?: string;
   value: string;
   percentageAggregateVal: string;
   standardIsSample: boolean;
}

export interface ColorInfo {
   type: string;
   color: string;
}