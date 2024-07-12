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
import { ChartBindingModel } from "../../binding/data/chart/chart-binding-model";
import { TableBindingModel } from "../../binding/data/table/table-binding-model";
import { CommonKVModel } from "../../common/data/common-kv-model";
import { FilterBindingModel } from "./filter-binding-model";

export interface WizardBindingModel {
   sourceName: string;
   assemblyType: number;
   autoOrder: boolean;
   showLegend: boolean;
   chartBindingModel: ChartBindingModel;
   tableBindingModel: TableBindingModel;
   filterBindingModel: FilterBindingModel;
   fixedFormulaMap: CommonKVModel<string, string>[];
}
