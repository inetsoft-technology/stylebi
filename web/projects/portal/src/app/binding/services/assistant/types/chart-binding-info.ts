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

import { BindingField } from "./binding-fields";

export interface ChartBindingFields {
   x_axis_fields?: BindingField[];
   y_axis_fields?: BindingField[];
   geo_fields?: BindingField[];
   group_fields?: BindingField[];
   color?: BindingField;
   shape?: BindingField;
   size?: BindingField;
   text?: BindingField;
   open?: BindingField;
   close?: BindingField;
   high?: BindingField;
   low?: BindingField;
   path?: BindingField;
   source?: BindingField;
   target?: BindingField;
   start?: BindingField;
   end?: BindingField;
   milestone?: BindingField;
   node_color?: BindingField;
   node_size?: BindingField;
}

export interface ChartBindingInfo {
   chartType: string;
   bindingFields: ChartBindingFields;
}
