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
import { AbstractBindingRef } from "./abstract-binding-ref";
import { NamedGroupInfo } from "./named-group-info";
import { SortOptionModel } from "./sort-option-model";
import { CalculateInfo } from "./chart/calculate-info";

export class BDimensionRef extends AbstractBindingRef {
   refType?: number;
   dataType: string;
   order: number;
   sortByCol: string;
   rankingOption: string;
   rankingN: string;
   rankingCol: string;
   groupOthers: boolean;
   others: boolean;
   dateLevel: string;
   dateInterval: number;
   timeSeries: boolean;
   namedGroupInfo: NamedGroupInfo;
   customNamedGroupInfo?: NamedGroupInfo;
   summarize: string;
   pageBreak: boolean;
   columnValue: string;
   caption: string;
   manualOrder: string[];
   classType: string;
   sortOptionModel: SortOptionModel;
   specificOrderType?: string;
   rankingAgg?: CalcAggregate;
   sortByColAgg?: CalcAggregate;
}

export interface CalcAggregate {
   baseAggregateName: string;
   calculateInfo: CalculateInfo;
}
