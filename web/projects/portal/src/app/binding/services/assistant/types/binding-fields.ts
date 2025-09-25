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

interface BaseField {
   field_name: string;
   data_type: string;
}

interface BindingField extends BaseField {
   base_fields: BaseField[];
}

interface DimensionField extends BindingField {
   group?: string;
   sort?: SortInfo;
   topn?: TopNInfo;
}

interface AggregateField extends BindingField {
   aggregation?: string;
   aggregate_chart_type?: string;
}

interface SortInfo {
   direction: string;
   by_measure?: string;
}

interface TopNInfo {
   enabled: boolean;
   n: string;
   by_measure: string;
   reverse: boolean;
}
