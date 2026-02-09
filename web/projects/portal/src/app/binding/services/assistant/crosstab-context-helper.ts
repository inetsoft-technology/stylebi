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

import { CrosstabBindingModel } from "../../data/table/crosstab-binding-model";
import { convertDataRefs, removeNullProps } from "./binding-tool";
import { CrosstabBindingFields } from "./types/crosstab-binding-info";

export function getCrosstabBindingContext(bindingModel: CrosstabBindingModel): string {
   let bindingFields: CrosstabBindingFields = {};

   if(bindingModel.rows && bindingModel.rows.length > 0) {
      bindingFields.row_fields = convertDataRefs(bindingModel.rows);
   }

   if(bindingModel.cols && bindingModel.cols.length > 0) {
      bindingFields.column_fields = convertDataRefs(bindingModel.cols);
   }

   if(bindingModel.aggregates && bindingModel.aggregates.length > 0) {
      bindingFields.aggregate_fields = convertDataRefs(bindingModel.aggregates);
   }

   return JSON.stringify(removeNullProps(bindingFields));
}