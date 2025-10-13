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
import { SourceInfo } from "./source-info";
import { DataRef } from "../../common/data/data-ref";

export class BindingModel {
   source: SourceInfo;
   sqlMergeable: boolean;
   availableFields: Array<DataRef>;
   type: string;
   tables: Array<SourceTable>;
}

export class SourceTable {
   name: string;
   columns: Array<SourceTableColumn>;
}

export class SourceTableColumn {
   name: string;
   dataType: string;
   description: string;
}