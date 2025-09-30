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
export interface WSInfo {
   tables?: WSTableInfo[];
}

export interface WSTableInfo {
   table_name?: string;
   columns?: WSColumnInfo[];
   subtables?: string[];
   groups?: WSGroupRef[];
   aggregates?: WSAggregateRef[];
   crosstab: boolean;
}

export interface WSColumnInfo {
   column_name: string;
   column_type: string;
   group: boolean;
   aggregate: boolean;
   sortType: string;
   description: string;
}

export interface WSGroupRef {
   column_name: string;
   column_type: string;
   base_column: string;
   group_level: string;
}

export interface WSAggregateRef {
   column_name: string;
   column_type: string;
   base_column: string;
   formula: string;
}

export interface WSScriptField {
   field_name: string;
}